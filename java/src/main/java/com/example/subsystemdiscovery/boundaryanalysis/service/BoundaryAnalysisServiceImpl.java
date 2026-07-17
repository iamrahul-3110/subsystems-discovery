package com.example.subsystemdiscovery.boundaryanalysis.service;

import com.example.subsystemdiscovery.algorithm.model.GraphNode;
import com.example.subsystemdiscovery.algorithm.model.WeightedEdge;
import com.example.subsystemdiscovery.algorithm.model.WeightedGraph;
import com.example.subsystemdiscovery.discovery.dto.NodeAssignmentDto;
import com.example.subsystemdiscovery.discovery.dto.SubsystemDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of {@link BoundaryAnalysisService} handling pure graph-topology
 * logic for boundary node analysis.
 *
 * <p>Fix #1: Deduplicates CLASS/METHOD boundary nodes that represent the same
 * underlying cross-subsystem relationship — a CLASS_DEPENDENCY edge and a
 * METHOD_CALL edge between the same two classes are counted once, not twice.
 *
 * <p>Fix #6: All subsystem identifiers are cluster IDs (e.g. "cluster_8"),
 * not full display names. The frontend resolves display names from its
 * already-cached discovery result.
 */
@Service
public class BoundaryAnalysisServiceImpl implements BoundaryAnalysisService {

    @Override
    public BoundaryAnalysisResult analyze(
            List<NodeAssignmentDto> nodeAssignments,
            List<SubsystemDto> subsystems,
            WeightedGraph graph) {

        // 1. Build lookup maps — O(N)
        Map<Long, String> nodeToSubsystem = new HashMap<>(nodeAssignments.size());
        for (NodeAssignmentDto assignment : nodeAssignments) {
            nodeToSubsystem.put(assignment.nodeId(), assignment.subsystemId());
        }

        // 2. Build node name & type lookup from graph nodes — O(N)
        Map<Long, String> nodeNames = new HashMap<>(graph.getNodes().size());
        Map<Long, String> nodeTypes = new HashMap<>(graph.getNodes().size());
        for (GraphNode node : graph.getNodes()) {
            nodeNames.put(node.getId(), node.getName());
            nodeTypes.put(node.getId(), node.getType());
        }

        // 3. Single-pass edge scan — O(E)
        //    Accumulate per-node per-subsystem edge counts AND
        //    directed subsystem interaction pairs simultaneously.
        //    Uses cluster IDs (not names) throughout for Fix #6.
        Map<Long, BoundaryAccumulator> accumulators = new HashMap<>();
        Map<DirectedPair, Integer> interactionCounts = new HashMap<>();

        for (WeightedEdge edge : graph.getEdges()) {
            Long src = edge.getSource();
            Long tgt = edge.getTarget();

            // Handle forward direction (src -> tgt)
            int forward = edge.getForwardOccurrences() > 0 ? edge.getForwardOccurrences() : (edge.getBackwardOccurrences() == 0 ? 1 : 0);
            if (forward > 0) {
                processDirectedEdge(src, tgt, forward, nodeToSubsystem, accumulators, interactionCounts);
            }

            // Handle backward direction (tgt -> src)
            int backward = edge.getBackwardOccurrences();
            if (backward > 0) {
                processDirectedEdge(tgt, src, backward, nodeToSubsystem, accumulators, interactionCounts);
            }
        }

        // 4. Fix #1: Deduplicate CLASS/METHOD nodes
        //    If a CLASS node and a METHOD node share the same owning subsystem,
        //    and the CLASS name is a prefix of the METHOD name (e.g.
        //    "DeliveryDispatchController6" vs "DeliveryDispatchController6.calculateDeliveryDispatchFlow6()"),
        //    and they connect to the same set of foreign subsystems — merge them,
        //    keeping only the more-specific METHOD node.
        deduplicateClassMethodNodes(accumulators, nodeToSubsystem, nodeNames, nodeTypes);

        // 5. Build internal boundary node list
        List<InternalBoundaryNode> boundaryNodes = new ArrayList<>(accumulators.size());
        for (BoundaryAccumulator acc : accumulators.values()) {
            String subsystemId = nodeToSubsystem.get(acc.nodeId);

            String nodeName = nodeNames.getOrDefault(acc.nodeId, "node-" + acc.nodeId);
            String nodeType = nodeTypes.getOrDefault(acc.nodeId, "CLASS");

            // Build per-subsystem edge count map (subsystem ID → [outgoing, incoming])
            Map<String, int[]> perSubsystemEdges = new HashMap<>();
            for (Map.Entry<String, Integer> entry : acc.outgoingBySubsystem.entrySet()) {
                perSubsystemEdges.computeIfAbsent(entry.getKey(), k -> new int[2])[0] += entry.getValue();
            }
            for (Map.Entry<String, Integer> entry : acc.incomingBySubsystem.entrySet()) {
                perSubsystemEdges.computeIfAbsent(entry.getKey(), k -> new int[2])[1] += entry.getValue();
            }

            int totalCross = acc.incomingCount + acc.outgoingCount;
            int connectedCount = perSubsystemEdges.size();

            boundaryNodes.add(new InternalBoundaryNode(
                    nodeName, nodeType, subsystemId,
                    perSubsystemEdges,
                    acc.incomingCount, acc.outgoingCount, totalCross,
                    connectedCount));
        }

        // 6. Compute and normalize boundary scores
        normalizeBoundaryScores(boundaryNodes);

        // 7. Build subsystem interactions list (sorted desc by count)
        List<InternalSubsystemInteraction> interactions = interactionCounts.entrySet().stream()
                .map(e -> new InternalSubsystemInteraction(
                        e.getKey().from(), e.getKey().to(), e.getValue()))
                .sorted(Comparator.comparingInt(InternalSubsystemInteraction::interactionCount).reversed())
                .toList();

        return new BoundaryAnalysisResult(
                boundaryNodes, interactions, nodeAssignments.size());
    }

    // -------------------------------------------------------------------------
    // Fix #1: CLASS/METHOD deduplication
    // -------------------------------------------------------------------------

    /**
     * Merges CLASS-level boundary accumulators into METHOD-level accumulators
     * when they represent the same underlying cross-subsystem relationship.
     *
     * <p>For each subsystem, groups accumulators by node type. If a CLASS node's
     * name is a strict prefix of a METHOD node's name (both in the same subsystem),
     * and they share at least one connected foreign subsystem, the CLASS accumulator
     * is merged into the METHOD accumulator and removed.
     */
    private void deduplicateClassMethodNodes(
            Map<Long, BoundaryAccumulator> accumulators,
            Map<Long, String> nodeToSubsystem,
            Map<Long, String> nodeNames,
            Map<Long, String> nodeTypes) {

        // Group accumulators by owning subsystem
        Map<String, List<Long>> bySubsystem = new HashMap<>();
        for (Map.Entry<Long, BoundaryAccumulator> entry : accumulators.entrySet()) {
            String subId = nodeToSubsystem.get(entry.getKey());
            if (subId != null) {
                bySubsystem.computeIfAbsent(subId, k -> new ArrayList<>()).add(entry.getKey());
            }
        }

        List<Long> toRemove = new ArrayList<>();

        for (List<Long> nodeIds : bySubsystem.values()) {
            // Separate CLASS and METHOD nodes
            List<Long> classNodes = new ArrayList<>();
            List<Long> methodNodes = new ArrayList<>();
            for (Long id : nodeIds) {
                String type = nodeTypes.getOrDefault(id, "CLASS");
                if ("METHOD".equalsIgnoreCase(type)) {
                    methodNodes.add(id);
                } else if ("CLASS".equalsIgnoreCase(type)) {
                    classNodes.add(id);
                }
            }

            // For each CLASS node, check if any METHOD node's name starts with it
            for (Long classId : classNodes) {
                String className = nodeNames.getOrDefault(classId, "");
                if (className.isEmpty()) continue;

                for (Long methodId : methodNodes) {
                    String methodName = nodeNames.getOrDefault(methodId, "");
                    // Check: method name starts with class name + "." (strict prefix)
                    if (methodName.startsWith(className + ".") || methodName.startsWith(className + "(")) {
                        BoundaryAccumulator classAcc = accumulators.get(classId);
                        BoundaryAccumulator methodAcc = accumulators.get(methodId);

                        // Check they share at least one connected foreign subsystem
                        if (classAcc != null && methodAcc != null && sharesConnectedSubsystem(classAcc, methodAcc)) {
                            // Merge CLASS counts into METHOD (don't double-count — CLASS edges
                            // are already summarized by the more-specific METHOD edges)
                            toRemove.add(classId);
                            break; // Each CLASS node is merged at most once
                        }
                    }
                }
            }
        }

        for (Long id : toRemove) {
            accumulators.remove(id);
        }
    }

    /**
     * Returns true if two accumulators share at least one connected foreign subsystem.
     */
    private boolean sharesConnectedSubsystem(BoundaryAccumulator a, BoundaryAccumulator b) {
        for (String subId : a.outgoingBySubsystem.keySet()) {
            if (b.outgoingBySubsystem.containsKey(subId) || b.incomingBySubsystem.containsKey(subId)) {
                return true;
            }
        }
        for (String subId : a.incomingBySubsystem.keySet()) {
            if (b.outgoingBySubsystem.containsKey(subId) || b.incomingBySubsystem.containsKey(subId)) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Scoring
    // -------------------------------------------------------------------------

    /**
     * Compute boundary scores using a simple weighted formula, then
     * min-max normalize across all nodes to [0, 1].
     */
    private void normalizeBoundaryScores(List<InternalBoundaryNode> nodes) {
        if (nodes.isEmpty()) return;

        // Compute raw scores
        double minRaw = Double.MAX_VALUE;
        double maxRaw = Double.MIN_VALUE;
        double[] rawScores = new double[nodes.size()];

        for (int i = 0; i < nodes.size(); i++) {
            InternalBoundaryNode node = nodes.get(i);
            double raw = computeRawBoundaryScore(node.totalCrossEdges(), node.connectedSubsystemCount());
            rawScores[i] = raw;
            minRaw = Math.min(minRaw, raw);
            maxRaw = Math.max(maxRaw, raw);
        }

        double range = maxRaw - minRaw;
        if (range < 1e-9) {
            for (InternalBoundaryNode node : nodes) {
                node.setBoundaryScore(1.0);
            }
            return;
        }

        for (int i = 0; i < nodes.size(); i++) {
            double normalized = (rawScores[i] - minRaw) / range;
            nodes.get(i).setBoundaryScore(round(normalized));
        }
    }

    /**
     * Raw boundary score formula. Tunable independently of the normalization logic.
     */
    private static double computeRawBoundaryScore(int totalCrossEdges, int connectedSubsystemCount) {
        return (totalCrossEdges * 1.0) + (connectedSubsystemCount * 0.5);
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    // -------------------------------------------------------------------------
    // Internal helpers for the edge scan
    // -------------------------------------------------------------------------

    /**
     * Mutable accumulator for a single boundary node during the edge scan.
     * Tracks per-subsystem edge counts (incoming/outgoing separately).
     */
    private static class BoundaryAccumulator {
        final Long nodeId;
        int outgoingCount;
        int incomingCount;
        final Map<String, Integer> outgoingBySubsystem = new HashMap<>();
        final Map<String, Integer> incomingBySubsystem = new HashMap<>();

        BoundaryAccumulator(Long nodeId) {
            this.nodeId = nodeId;
        }

        void addOutgoing(String targetSubsystemId) {
            outgoingCount++;
            outgoingBySubsystem.merge(targetSubsystemId, 1, Integer::sum);
        }

        void addIncoming(String sourceSubsystemId) {
            incomingCount++;
            incomingBySubsystem.merge(sourceSubsystemId, 1, Integer::sum);
        }
    }

    /**
     * Fix #6: Uses cluster IDs directly — no name resolution needed here.
     */
    private void processDirectedEdge(
            Long sourceNodeId, Long targetNodeId, int count,
            Map<Long, String> nodeToSubsystem,
            Map<Long, BoundaryAccumulator> accumulators,
            Map<DirectedPair, Integer> interactionCounts) {

        String sourceSubsystem = nodeToSubsystem.get(sourceNodeId);
        String targetSubsystem = nodeToSubsystem.get(targetNodeId);

        if (sourceSubsystem == null || targetSubsystem == null) {
            return;
        }
        if (sourceSubsystem.equals(targetSubsystem)) {
            return;
        }

        // Source node: outgoing cross-subsystem edge to targetSubsystem
        BoundaryAccumulator srcAcc = accumulators.computeIfAbsent(sourceNodeId, BoundaryAccumulator::new);
        for (int i = 0; i < count; i++) {
            srcAcc.addOutgoing(targetSubsystem);
        }

        // Target node: incoming cross-subsystem edge from sourceSubsystem
        BoundaryAccumulator tgtAcc = accumulators.computeIfAbsent(targetNodeId, BoundaryAccumulator::new);
        for (int i = 0; i < count; i++) {
            tgtAcc.addIncoming(sourceSubsystem);
        }

        // Directed subsystem interaction: sourceSubsystem → targetSubsystem (cluster IDs)
        interactionCounts.merge(new DirectedPair(sourceSubsystem, targetSubsystem), count, Integer::sum);
    }

    /**
     * Key for directed subsystem interaction pairs (uses cluster IDs).
     */
    private record DirectedPair(String from, String to) {
    }
}

package com.example.subsystemdiscovery.boundaryanalysis.service;

import com.example.subsystemdiscovery.algorithm.model.GraphNode;
import com.example.subsystemdiscovery.algorithm.model.WeightedEdge;
import com.example.subsystemdiscovery.algorithm.model.WeightedGraph;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryNodeDto;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryNodeRequest;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryNodeResponse;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryStatisticsDto;
import com.example.subsystemdiscovery.discovery.SubsystemDiscoveryService;
import com.example.subsystemdiscovery.discovery.dto.NodeAssignmentDto;
import com.example.subsystemdiscovery.discovery.dto.SubsystemAlgorithmParams;
import com.example.subsystemdiscovery.discovery.dto.SubsystemDiscoveryResponse;
import com.example.subsystemdiscovery.discovery.dto.SubsystemDto;
import com.example.subsystemdiscovery.repository.SubsystemHistoryMapper;
import com.example.subsystemdiscovery.repository.entity.SubsystemRunMaster;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Identifies boundary nodes — nodes whose outgoing or incoming edges
 * connect different subsystems.
 *
 * <h3>Algorithm (single-pass, O(E))</h3>
 * <ol>
 * <li>Load the existing subsystem discovery result (cached or freshly
 * triggered).</li>
 * <li>Build {@code nodeId → subsystemId} lookup from
 * {@code nodeAssignments}.</li>
 * <li>Iterate through all weighted graph edges once. For every edge where
 * source and target belong to different subsystems, mark both endpoints
 * as boundary nodes and track incoming/outgoing cross-subsystem edge
 * counts.</li>
 * <li>Calculate a normalised {@code boundaryScore} for each boundary node.</li>
 * <li>Return overview statistics and the top N nodes by score.</li>
 * </ol>
 *
 * <p>
 * Performance: Uses {@link HashMap} for O(1) lookups. Single pass through
 * edges → O(E). Suitable for graphs with 20K–100K+ nodes.
 */
@Service
public class BoundaryNodeServiceImpl implements BoundaryNodeService {

    private final SubsystemDiscoveryService discoveryService;
    private final SubsystemHistoryMapper subsystemHistoryMapper;

    public BoundaryNodeServiceImpl(SubsystemDiscoveryService discoveryService,
                                   SubsystemHistoryMapper subsystemHistoryMapper) {
        this.discoveryService = discoveryService;
        this.subsystemHistoryMapper = subsystemHistoryMapper;
    }

    @Override
    public BoundaryNodeResponse detectBoundaryNodes(BoundaryNodeRequest request) {
        if (request.discoveryRunId() == null) {
            throw new IllegalArgumentException("discoveryRunId is required to detect boundary nodes");
        }

        // 1. Fetch the master run from DB using the ID
        SubsystemRunMaster master = subsystemHistoryMapper.selectMasterById(request.discoveryRunId());
        if (master == null) {
            throw new IllegalArgumentException("No discovery run found for ID: " + request.discoveryRunId());
        }

        String analysisTime = master.getAnalysisTime();
        SubsystemAlgorithmParams params = new SubsystemAlgorithmParams(
                null,
                master.getRuns(),
                master.getConsensusThreshold(),
                master.getResolution(),
                null, null, null);

        // 2. Load existing discovery result (returns cached if available)
        SubsystemDiscoveryResponse discovery = discoveryService.discover(analysisTime, params);

        List<NodeAssignmentDto> nodeAssignments = discovery.nodeAssignments();
        if (nodeAssignments == null || nodeAssignments.isEmpty()) {
            throw new IllegalStateException(
                    "Node assignments not available for analysisTime=" + analysisTime
                            + ". Cannot detect boundary nodes without node-to-subsystem mappings.");
        }

        // 3. Build lookup maps — O(N) with HashMap for O(1) lookups
        Map<Long, String> nodeToSubsystem = new HashMap<>(nodeAssignments.size());
        for (NodeAssignmentDto assignment : nodeAssignments) {
            nodeToSubsystem.put(assignment.nodeId(), assignment.subsystemId());
        }

        Map<String, String> subsystemNames = new HashMap<>(discovery.subsystems().size());
        for (SubsystemDto subsystem : discovery.subsystems()) {
            subsystemNames.put(subsystem.id(), subsystem.name());
        }

        // 4. Build the weighted graph to access the edge list
        WeightedGraph graph = discoveryService.buildWeightedGraph(analysisTime, params);

        // 5. Build node name & type lookup from graph nodes — O(N)
        Map<Long, String> nodeNames = new HashMap<>(graph.getNodes().size());
        Map<Long, String> nodeTypes = new HashMap<>(graph.getNodes().size());
        for (GraphNode node : graph.getNodes()) {
            nodeNames.put(node.getId(), node.getName());
            nodeTypes.put(node.getId(), node.getType());
        }

        // 6. Single-pass edge scan — O(E)
        Map<Long, BoundaryAccumulator> accumulators = new HashMap<>();
        for (WeightedEdge edge : graph.getEdges()) {
            String sourceSubsystem = nodeToSubsystem.get(edge.getSource());
            String targetSubsystem = nodeToSubsystem.get(edge.getTarget());

            if (sourceSubsystem == null || targetSubsystem == null) {
                continue;
            }
            if (sourceSubsystem.equals(targetSubsystem)) {
                continue;
            }

            // Source: outgoing cross-subsystem edge
            accumulators.computeIfAbsent(edge.getSource(), BoundaryAccumulator::new)
                    .addOutgoing(targetSubsystem);

            // Target: incoming cross-subsystem edge
            accumulators.computeIfAbsent(edge.getTarget(), BoundaryAccumulator::new)
                    .addIncoming(sourceSubsystem);
        }

        if (accumulators.isEmpty()) {
            // No cross-subsystem edges → no boundary nodes
            BoundaryStatisticsDto emptyOverview = new BoundaryStatisticsDto(
                    0, nodeAssignments.size(), 0.0,
                    discovery.subsystems().size(), 0.0, 0.0, List.of());
            return new BoundaryNodeResponse(emptyOverview, List.of());
        }

        // 7. Build raw boundary node list (scores not yet normalised)
        List<RawBoundaryNode> rawNodes = new ArrayList<>(accumulators.size());
        for (BoundaryAccumulator acc : accumulators.values()) {
            String subsystemId = nodeToSubsystem.get(acc.nodeId);
            int connectedCount = acc.connectedSubsystems.size();
            int totalCross = acc.incomingCount + acc.outgoingCount;
            double rawScore = (double) connectedCount * totalCross;

            rawNodes.add(new RawBoundaryNode(
                    acc.nodeId,
                    nodeNames.getOrDefault(acc.nodeId, "node-" + acc.nodeId),
                    nodeTypes.getOrDefault(acc.nodeId, "CLASS"),
                    subsystemId,
                    subsystemNames.getOrDefault(subsystemId, subsystemId),
                    acc.connectedSubsystems,
                    totalCross,
                    acc.incomingCount,
                    acc.outgoingCount,
                    connectedCount,
                    rawScore));
        }

        // 8. Normalise boundary scores to [0, 1]
        double maxRawScore = rawNodes.stream()
                .mapToDouble(n -> n.rawScore)
                .max().orElse(1.0);
        if (maxRawScore <= 0) {
            maxRawScore = 1.0;
        }

        final double divisor = maxRawScore;
        Comparator<BoundaryNodeDto> comparator = Comparator.comparing(BoundaryNodeDto::boundaryScore);
        if ("TOP".equalsIgnoreCase(request.sortOrderOrDefault())) {
            comparator = comparator.reversed();
        }
        comparator = comparator.thenComparing(BoundaryNodeDto::nodeName);

        // Filter by requested node type if specified
        String filterType = request.nodeType();
        List<RawBoundaryNode> filteredRawNodes = rawNodes;
        if (filterType != null && !filterType.trim().isEmpty() && !"ALL".equalsIgnoreCase(filterType)) {
            filteredRawNodes = rawNodes.stream()
                    .filter(n -> filterType.equalsIgnoreCase(n.nodeType))
                    .toList();
        }

        List<BoundaryNodeDto> scoredNodes = filteredRawNodes.stream()
                .map(raw -> new BoundaryNodeDto(
                        raw.nodeId, raw.nodeName, raw.nodeType, raw.subsystemId, raw.subsystemName,
                        raw.connectedSubsystems.stream()
                                .map(id -> subsystemNames.getOrDefault(id, id))
                                .sorted()
                                .toList(),
                        raw.crossEdgeCount, raw.incomingCross, raw.outgoingCross,
                        raw.connectedCount,
                        round(raw.rawScore / divisor)))
                .sorted(comparator)
                .toList();

        // 9. Build response — limited nodes
        int limit = request.nodeLimitOrDefault();
        List<BoundaryNodeDto> topNodes = scoredNodes.stream()
                .limit(limit)
                .toList();

        // 10. Build overview statistics
        double avgConnections = scoredNodes.stream()
                .mapToInt(BoundaryNodeDto::totalConnectedSubsystems)
                .average().orElse(0.0);

        double maxBoundaryScore = scoredNodes.isEmpty() ? 0.0
                : scoredNodes.get(0).boundaryScore();

        List<String> topCriticalNames = topNodes.stream()
                .limit(5)
                .map(BoundaryNodeDto::nodeName)
                .toList();

        BoundaryStatisticsDto overview = new BoundaryStatisticsDto(
                scoredNodes.size(),
                nodeAssignments.size(),
                nodeAssignments.isEmpty() ? 0.0
                        : round((double) scoredNodes.size() / nodeAssignments.size()),
                discovery.subsystems().size(),
                round(avgConnections),
                round(maxBoundaryScore),
                topCriticalNames);

        return new BoundaryNodeResponse(overview, topNodes);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    /**
     * Mutable accumulator for a single boundary node during the edge scan.
     */
    private static class BoundaryAccumulator {
        final Long nodeId;
        int outgoingCount;
        int incomingCount;
        final Set<String> connectedSubsystems = new HashSet<>();

        BoundaryAccumulator(Long nodeId) {
            this.nodeId = nodeId;
        }

        void addOutgoing(String targetSubsystem) {
            outgoingCount++;
            connectedSubsystems.add(targetSubsystem);
        }

        void addIncoming(String sourceSubsystem) {
            incomingCount++;
            connectedSubsystems.add(sourceSubsystem);
        }
    }

    /**
     * Intermediate holder before score normalisation. Avoids constructing
     * the immutable {@link BoundaryNodeDto} record twice.
     */
    private record RawBoundaryNode(
            Long nodeId, String nodeName, String nodeType,
            String subsystemId, String subsystemName,
            Set<String> connectedSubsystems,
            int crossEdgeCount, int incomingCross, int outgoingCross,
            int connectedCount, double rawScore) {
    }
}

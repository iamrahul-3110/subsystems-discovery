package com.example.subsystemdiscovery.boundaryanalysis.service;

import com.example.subsystemdiscovery.algorithm.model.WeightedGraph;
import com.example.subsystemdiscovery.discovery.dto.NodeAssignmentDto;
import com.example.subsystemdiscovery.discovery.dto.SubsystemDto;

import java.util.List;
import java.util.Map;

/**
 * Interface for graph-topology logic for boundary node analysis.
 *
 * <p>Takes already-computed subsystem discovery data (node assignments,
 * subsystem names, weighted graph) and produces an internal analysis result.
 */
public interface BoundaryAnalysisService {

    /**
     * Analyze the graph to find boundary nodes and subsystem interactions.
     *
     * @param nodeAssignments node-to-subsystem mappings from discovery
     * @param subsystems      discovered subsystems (for name lookup)
     * @param graph           the weighted graph (for edge list and node metadata)
     * @return internal analysis result
     */
    BoundaryAnalysisResult analyze(
            List<NodeAssignmentDto> nodeAssignments,
            List<SubsystemDto> subsystems,
            WeightedGraph graph);

    // -------------------------------------------------------------------------
    // Nested model classes (public static implicitly)
    // -------------------------------------------------------------------------

    /**
     * Complete analysis result returned by {@link #analyze}.
     */
    record BoundaryAnalysisResult(
            List<InternalBoundaryNode> boundaryNodes,
            List<InternalSubsystemInteraction> subsystemInteractions,
            int totalNodeCount
    ) {
    }

    /**
     * Internal boundary node with mutable score.
     */
    static final class InternalBoundaryNode {
        private final String nodeName;
        private final String nodeType;
        private final String owningSubsystem;
        private final Map<String, int[]> perSubsystemEdges; // name → [outgoing, incoming]
        private final int incomingCrossEdges;
        private final int outgoingCrossEdges;
        private final int totalCrossEdges;
        private final int connectedSubsystemCount;
        private double boundaryScore;

        public InternalBoundaryNode(String nodeName, String nodeType, String owningSubsystem,
                                    Map<String, int[]> perSubsystemEdges,
                                    int incomingCrossEdges, int outgoingCrossEdges, int totalCrossEdges,
                                    int connectedSubsystemCount) {
            this.nodeName = nodeName;
            this.nodeType = nodeType;
            this.owningSubsystem = owningSubsystem;
            this.perSubsystemEdges = perSubsystemEdges;
            this.incomingCrossEdges = incomingCrossEdges;
            this.outgoingCrossEdges = outgoingCrossEdges;
            this.totalCrossEdges = totalCrossEdges;
            this.connectedSubsystemCount = connectedSubsystemCount;
        }

        public String nodeName() { return nodeName; }
        public String nodeType() { return nodeType; }
        public String owningSubsystem() { return owningSubsystem; }
        public Map<String, int[]> perSubsystemEdges() { return perSubsystemEdges; }
        public int incomingCrossEdges() { return incomingCrossEdges; }
        public int outgoingCrossEdges() { return outgoingCrossEdges; }
        public int totalCrossEdges() { return totalCrossEdges; }
        public int connectedSubsystemCount() { return connectedSubsystemCount; }
        public double boundaryScore() { return boundaryScore; }
        public void setBoundaryScore(double score) { this.boundaryScore = score; }
    }

    /**
     * Internal subsystem interaction pair.
     */
    record InternalSubsystemInteraction(
            String fromSubsystem,
            String toSubsystem,
            int interactionCount
    ) {
    }
}

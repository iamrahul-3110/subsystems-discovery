package com.example.subsystemdiscovery.boundaryanalysis.dto;

import java.util.List;

/**
 * Represents a single boundary node — a node whose edges cross subsystem boundaries.
 *
 * <p>Fields are computed on demand from the existing subsystem discovery result
 * and the weighted graph edge list. Nothing is persisted.
 */
public record BoundaryNodeDto(
        Long nodeId,
        String nodeName,
        String subsystemId,
        String subsystemName,
        List<String> connectedSubsystems,
        int crossSubsystemEdgeCount,
        int incomingCrossEdges,
        int outgoingCrossEdges,
        int totalConnectedSubsystems,
        double boundaryScore
) {
}

package com.example.subsystemdiscovery.boundaryanalysis.dto;

import java.util.List;

/**
 * Represents a single boundary node — a node whose edges cross subsystem boundaries.
 *
 * <p>{@code connectedSubsystems} contains aggregated {@code {subsystem, outgoingEdges, incomingEdges}}
 * pairs sorted descending by total edge count. No duplicate subsystem entries.
 *
 * <p>{@code owningSubsystem} is a cluster ID (e.g. "cluster_8"), not a display name.
 *
 * <p>{@code totalCrossEdges} has been removed — the frontend computes it as
 * {@code incomingCrossEdges + outgoingCrossEdges}.
 *
 * <p>Fields are computed on demand from the existing subsystem discovery result
 * and the weighted graph edge list. Nothing is persisted.
 */
public record BoundaryNodeDto(
        String nodeName,
        String nodeType,
        String owningSubsystem,
        List<ConnectedSubsystemDto> connectedSubsystems,
        int incomingCrossEdges,
        int outgoingCrossEdges,
        double boundaryScore
) {
}

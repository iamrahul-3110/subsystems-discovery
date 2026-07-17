package com.example.subsystemdiscovery.boundaryanalysis.dto;

/**
 * Aggregated connection from a boundary node to a specific target subsystem.
 *
 * <p>{@code subsystem} is a cluster ID (e.g. "cluster_8"), not a display name.
 * The frontend resolves display names from its cached discovery result.
 *
 * <p>The combined {@code edges} field has been removed — the frontend computes
 * it as {@code outgoingEdges + incomingEdges} if needed.
 */
public record ConnectedSubsystemDto(
        String subsystem,
        int outgoingEdges,
        int incomingEdges
) {
}

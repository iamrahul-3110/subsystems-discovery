package com.example.subsystemdiscovery.boundaryanalysis.dto;

/**
 * A directed subsystem-to-subsystem interaction pair with an edge count.
 *
 * <p>Direction matters architecturally: A→B and B→A are tracked separately.
 * Sorted descending by {@code interactionCount} in the API response.
 */
public record SubsystemInteractionDto(
        String fromSubsystem,
        String toSubsystem,
        int interactionCount
) {
}

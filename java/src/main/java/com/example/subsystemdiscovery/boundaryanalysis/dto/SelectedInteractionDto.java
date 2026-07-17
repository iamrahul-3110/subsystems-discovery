package com.example.subsystemdiscovery.boundaryanalysis.dto;

import java.util.List;

/**
 * Drill-down result when a specific subsystem pair is selected.
 *
 * <p>Contains the interaction metadata plus only those boundary nodes
 * that participate in the selected subsystem-to-subsystem relationship.
 */
public record SelectedInteractionDto(
        String fromSubsystem,
        String toSubsystem,
        int interactionCount,
        List<BoundaryNodeDto> boundaryNodes
) {
}

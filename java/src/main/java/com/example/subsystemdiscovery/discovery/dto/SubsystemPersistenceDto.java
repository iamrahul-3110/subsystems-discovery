package com.example.subsystemdiscovery.discovery.dto;

import java.util.List;

/**
 * Serialised form of a completed discovery run stored in {@code tb_gi_subsystems_history}.
 *
 * <p>{@code nodeAssignments} was added in v2 of this record to support boundary-node
 * detection. Rows persisted before this change will deserialize {@code nodeAssignments}
 * as {@code null}; the boundary-analysis service handles this case by requesting
 * a fresh discovery run.
 */
public record SubsystemPersistenceDto(
        List<SubsystemDto> subsystems,
        List<SubsystemLinkDto> subsystemLinks,
        List<NodeAssignmentDto> nodeAssignments
) {
}

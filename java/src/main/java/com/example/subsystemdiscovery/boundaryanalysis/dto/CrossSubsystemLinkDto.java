package com.example.subsystemdiscovery.boundaryanalysis.dto;

import java.util.Map;

/**
 * Describes one foreign subsystem reached by a boundary node.
 */
public record CrossSubsystemLinkDto(
        String subsystemId,
        String subsystemName,
        int crossEdgeCount,
        double crossEdgeWeight,
        String couplingStrength,
        Map<String, Integer> relationSummary
) {
}

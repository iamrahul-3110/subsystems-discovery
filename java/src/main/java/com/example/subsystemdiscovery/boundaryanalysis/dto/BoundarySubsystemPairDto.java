package com.example.subsystemdiscovery.boundaryanalysis.dto;

import java.util.List;
import java.util.Map;

public record BoundarySubsystemPairDto(
        String sourceSubsystemId,
        String sourceSubsystemName,
        String targetSubsystemId,
        String targetSubsystemName,
        int boundaryNodeCount,
        int crossEdgeCount,
        double crossEdgeWeight,
        String couplingStrength,
        Map<String, Integer> relationSummary,
        List<String> topBoundaryNodes
) {
}

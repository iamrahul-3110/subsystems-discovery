package com.example.subsystemdiscovery.boundaryanalysis.dto;

import java.util.List;

/**
 * A method, class, or package that hands off from one discovered subsystem to another.
 */
public record BoundaryNodeDto(
        Long nodeId,
        String name,
        String qualifiedName,
        String nodeType,
        String packageName,
        String homeSubsystemId,
        String homeSubsystemName,
        int totalDegree,
        int crossSubsystemEdges,
        double totalWeight,
        double crossSubsystemWeight,
        int foreignSubsystemCount,
        double boundaryScore,
        double importanceScore,
        String riskLevel,
        String boundaryRole,
        String recommendedAction,
        List<CrossSubsystemLinkDto> crossSubsystemLinks
) {
}

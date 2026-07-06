package com.example.subsystemdiscovery.boundaryanalysis.dto;

import java.util.Map;

public record BoundaryAnalysisStatsDto(
        int methodBoundaryCount,
        int classBoundaryCount,
        int packageBoundaryCount,
        int highRiskBoundaryCount,
        int mediumRiskBoundaryCount,
        int lowRiskBoundaryCount,
        int apiCandidateCount,
        int subsystemPairCount,
        double highestBoundaryScore,
        double averageBoundaryScore,
        double highestImportanceScore,
        double averageImportanceScore,
        Map<String, Integer> boundaryCountBySubsystem,
        Map<String, Integer> boundaryCountByPackage
) {
}

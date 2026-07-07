package com.example.subsystemdiscovery.boundaryanalysis.dto;

import java.util.List;

/**
 * Lightweight overview statistics for the boundary node detection response.
 * Provides summary numbers suitable for a frontend dashboard.
 */
public record BoundaryStatisticsDto(
        int totalBoundaryNodes,
        int totalNodes,
        double boundaryNodeRatio,
        int totalSubsystems,
        double averageCrossSubsystemConnections,
        double maximumBoundaryScore,
        List<String> topCriticalBoundaryNodes
) {
}

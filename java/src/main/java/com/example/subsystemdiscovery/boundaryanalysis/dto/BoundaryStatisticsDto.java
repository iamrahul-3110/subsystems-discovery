package com.example.subsystemdiscovery.boundaryanalysis.dto;

/**
 * Lightweight overview statistics for the boundary analysis response.
 * Contains exactly the 5 stats shown in the dashboard metrics grid.
 */
public record BoundaryStatisticsDto(
        int totalBoundaryNodes,
        double boundaryRatio,
        int totalSubsystemInteractions,
        double avgCrossSubsystemConnections,
        double highestBoundaryScore
) {
}

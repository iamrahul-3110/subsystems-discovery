package com.example.subsystemdiscovery.boundaryanalysis.dto;

import java.util.List;

/**
 * Top-level API response for boundary node detection.
 *
 * <p>{@code overview} contains summary statistics for the dashboard.
 * {@code boundaryNodes} contains the top N nodes ordered by boundary score.
 */
public record BoundaryNodeResponse(
        BoundaryStatisticsDto overview,
        List<BoundaryNodeDto> boundaryNodes
) {
}

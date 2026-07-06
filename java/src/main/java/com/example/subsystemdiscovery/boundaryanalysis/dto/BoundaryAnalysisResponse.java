package com.example.subsystemdiscovery.boundaryanalysis.dto;

import java.util.List;

/**
 * Response returned by boundary-node detection.
 *
 * <p>The node lists are sorted by {@code importanceScore} descending, then
 * boundary score, then cross-subsystem edge count. The full {@code boundaryNodes}
 * list contains every node that passed the configured threshold.
 */
public record BoundaryAnalysisResponse(
        Long discoveryRunId,
        int totalSubsystems,
        int totalNodes,
        int totalEdges,
        int boundaryNodeCount,
        double boundaryNodeRatio,
        BoundaryAnalysisStatsDto summary,
        List<BoundarySubsystemPairDto> subsystemPairHotspots,
        List<BoundaryNodeDto> topBoundaryNodes,
        List<BoundaryNodeDto> boundaryNodes,
        String aiSummary,
        String llmModel,
        String summaryType,
        boolean aiSummaryFallback
) {
}

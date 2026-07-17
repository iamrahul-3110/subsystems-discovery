package com.example.subsystemdiscovery.boundaryanalysis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Top-level API response for boundary node analysis.
 *
 * <p>{@code overview} — 5 summary stats for the dashboard.
 * <p>{@code topBoundaryNodes} — top N nodes ordered/filtered per request.
 * <p>{@code subsystemInteractions} — directed subsystem pairs sorted by count.
 * <p>{@code selectedInteraction} — populated only during drill-down, omitted from JSON when null.
 *
 * <p>Removed: {@code filters} (static enums now hardcoded on frontend),
 * {@code totalResults} (duplicated {@code overview.totalBoundaryNodes}).
 */
public record BoundaryNodeResponse(
        Long discoveryRunId,
        BoundaryStatisticsDto overview,
        List<BoundaryNodeDto> topBoundaryNodes,
        List<SubsystemInteractionDto> subsystemInteractions,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        SelectedInteractionDto selectedInteraction
) {
}

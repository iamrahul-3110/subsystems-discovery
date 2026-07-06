package com.example.subsystemdiscovery.boundaryanalysis.dto;

import com.example.subsystemdiscovery.discovery.dto.SubsystemAlgorithmParams;
import com.example.subsystemdiscovery.discovery.dto.SummaryType;

/**
 * Request body for boundary analysis.
 *
 * <p>Use {@code discoveryRunId} to analyze an existing subsystem discovery run.
 * If it is absent, provide {@code analysisTime} and optional subsystem algorithm
 * params to run discovery first and analyze the fresh result.
 */
public record BoundaryAnalysisRequest(
        Long discoveryRunId,
        String analysisTime,
        SubsystemAlgorithmParams params,
        Boolean includeAiSummary,
        String llmModel,
        SummaryType summaryType,
        Integer topNodeLimit,
        Integer hotspotLimit
) {
    public boolean includeAiSummaryOrDefault() {
        return Boolean.TRUE.equals(includeAiSummary);
    }

    public SummaryType summaryTypeOrDefault() {
        return summaryType == null ? SummaryType.MEDIUM_DETAILED : summaryType;
    }
}

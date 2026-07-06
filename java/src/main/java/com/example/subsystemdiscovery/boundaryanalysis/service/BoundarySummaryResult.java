package com.example.subsystemdiscovery.boundaryanalysis.service;

public record BoundarySummaryResult(
        String text,
        String llmModel,
        boolean fallback
) {
}

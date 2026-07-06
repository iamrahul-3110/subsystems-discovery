package com.example.subsystemdiscovery.boundaryanalysis.app;

import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryAnalysisRequest;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryAnalysisResponse;
import com.example.subsystemdiscovery.boundaryanalysis.service.BoundaryAnalysisService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class BoundaryAnalysisApp {

    private final BoundaryAnalysisService boundaryAnalysisService;

    public BoundaryAnalysisApp(BoundaryAnalysisService boundaryAnalysisService) {
        this.boundaryAnalysisService = boundaryAnalysisService;
    }

    public BoundaryAnalysisResponse run(BoundaryAnalysisRequest request) {
        validate(request);
        return boundaryAnalysisService.analyze(request);
    }

    public BoundaryAnalysisResponse runWithSummary(BoundaryAnalysisRequest request) {
        validate(request);
        BoundaryAnalysisRequest summaryRequest = new BoundaryAnalysisRequest(
                request.discoveryRunId(),
                request.analysisTime(),
                request.params(),
                true,
                request.llmModel(),
                request.summaryType(),
                request.topNodeLimit(),
                request.hotspotLimit()
        );
        return boundaryAnalysisService.analyze(summaryRequest);
    }

    private void validate(BoundaryAnalysisRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be null.");
        }
        if (request.discoveryRunId() == null && !StringUtils.hasText(request.analysisTime())) {
            throw new IllegalArgumentException("Either discoveryRunId or analysisTime must be provided.");
        }
    }
}

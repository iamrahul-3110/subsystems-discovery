package com.example.subsystemdiscovery.boundaryanalysis.service;

import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryAnalysisRequest;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryAnalysisResponse;

public interface BoundaryAnalysisService {

    BoundaryAnalysisResponse analyze(BoundaryAnalysisRequest request);
}

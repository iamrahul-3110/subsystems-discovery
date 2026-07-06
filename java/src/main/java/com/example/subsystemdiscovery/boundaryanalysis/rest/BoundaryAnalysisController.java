package com.example.subsystemdiscovery.boundaryanalysis.rest;

import com.example.subsystemdiscovery.boundaryanalysis.app.BoundaryAnalysisApp;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryAnalysisRequest;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryAnalysisResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/codeanalyzer/boundary")
public class BoundaryAnalysisController {

    private final BoundaryAnalysisApp boundaryAnalysisApp;

    public BoundaryAnalysisController(BoundaryAnalysisApp boundaryAnalysisApp) {
        this.boundaryAnalysisApp = boundaryAnalysisApp;
    }

    @PostMapping("/analyze")
    public ResponseEntity<BoundaryAnalysisResponse> analyze(@RequestBody BoundaryAnalysisRequest request) {
        return ResponseEntity.ok(boundaryAnalysisApp.run(request));
    }

    @PostMapping("/summary")
    public ResponseEntity<BoundaryAnalysisResponse> summarize(@RequestBody BoundaryAnalysisRequest request) {
        return ResponseEntity.ok(boundaryAnalysisApp.runWithSummary(request));
    }
}

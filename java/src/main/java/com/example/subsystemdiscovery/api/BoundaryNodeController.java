package com.example.subsystemdiscovery.api;

import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryNodeRequest;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryNodeResponse;
import com.example.subsystemdiscovery.boundaryanalysis.service.BoundaryNodeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for boundary node detection.
 *
 * <p>Boundary nodes are nodes whose outgoing or incoming edges connect
 * different subsystems. This endpoint computes them on demand from an
 * existing (or freshly triggered) subsystem discovery result.
 *
 * <pre>
 *  POST /api/codeanalyzer/subsystem/boundary-nodes
 *       Detect boundary nodes → BoundaryNodeResponse
 * </pre>
 */
@RestController
@RequestMapping("/api/codeanalyzer/subsystem")
@org.springframework.web.bind.annotation.CrossOrigin(origins = "*")
public class BoundaryNodeController {

    private final BoundaryNodeService boundaryNodeService;

    public BoundaryNodeController(BoundaryNodeService boundaryNodeService) {
        this.boundaryNodeService = boundaryNodeService;
    }

    /**
     * Identifies nodes that connect two or more subsystems.
     *
     * <p>Boundary nodes are computed from node assignments and graph edge list
     * corresponding to the given {@code discoveryRunId}.
     *
     * @param request request body containing discoveryRunId and topNodeLimit
     * @return overview statistics and top boundary nodes ordered by boundary score
     */
    @PostMapping("/boundary-nodes")
    public ResponseEntity<BoundaryNodeResponse> detectBoundaryNodes(
            @RequestBody BoundaryNodeRequest request) {

        return ResponseEntity.ok(
                boundaryNodeService.detectBoundaryNodes(request));
    }
}

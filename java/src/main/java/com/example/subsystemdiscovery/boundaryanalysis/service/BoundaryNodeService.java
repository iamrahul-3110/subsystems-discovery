package com.example.subsystemdiscovery.boundaryanalysis.service;

import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryNodeRequest;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryNodeResponse;

/**
 * Detects boundary nodes — nodes whose edges connect two or more subsystems.
 */
public interface BoundaryNodeService {

    /**
     * Identifies boundary nodes from an existing (or freshly triggered)
     * subsystem discovery result.
     *
     * @param request algorithm parameters containing discoveryRunId and display limits
     * @return overview statistics and top boundary nodes ordered by score
     */
    BoundaryNodeResponse detectBoundaryNodes(BoundaryNodeRequest request);
}

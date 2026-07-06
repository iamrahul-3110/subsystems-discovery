package com.example.subsystemdiscovery.boundaryanalysis.mapper;

import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryAnalysisStatsDto;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryNodeDto;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundarySubsystemPairDto;

import java.util.List;

public record BoundaryMappingResult(
        List<BoundaryNodeDto> boundaryNodes,
        List<BoundaryNodeDto> topBoundaryNodes,
        List<BoundarySubsystemPairDto> subsystemPairHotspots,
        BoundaryAnalysisStatsDto summary
) {
}

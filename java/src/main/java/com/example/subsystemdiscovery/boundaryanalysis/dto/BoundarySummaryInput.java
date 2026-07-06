package com.example.subsystemdiscovery.boundaryanalysis.dto;

import com.example.subsystemdiscovery.discovery.dto.SubsystemDto;
import com.example.subsystemdiscovery.discovery.dto.SubsystemLinkDto;

import java.util.List;

public record BoundarySummaryInput(
        Long discoveryRunId,
        String applicationKey,
        int totalSubsystems,
        int totalNodes,
        int totalEdges,
        int boundaryNodeCount,
        double boundaryNodeRatio,
        BoundaryAnalysisStatsDto summary,
        List<SubsystemDto> subsystems,
        List<SubsystemLinkDto> subsystemLinks,
        List<BoundarySubsystemPairDto> subsystemPairHotspots,
        List<BoundaryNodeDto> boundaryNodes
) {
}

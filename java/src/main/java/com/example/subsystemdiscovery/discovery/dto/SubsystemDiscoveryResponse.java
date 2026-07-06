package com.example.subsystemdiscovery.discovery.dto;

import java.util.List;

public record SubsystemDiscoveryResponse(
        Long discoveryRunId,
        Long applicationId,
        String applicationKey,
        AlgorithmInfoDto algorithm,
        SummaryDto summary,
        List<SubsystemDto> subsystems,
        List<SubsystemLinkDto> subsystemLinks,
        List<NodeAssignmentDto> nodeAssignments
) {
}


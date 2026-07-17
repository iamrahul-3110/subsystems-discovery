package com.example.subsystemdiscovery.discovery.dto;

public record NodeLinkDto(
        String sourceNodeName,
        String sourceSubsystemId,
        String targetNodeName,
        String targetSubsystemId,
        double weight,
        String relationType
) {
}

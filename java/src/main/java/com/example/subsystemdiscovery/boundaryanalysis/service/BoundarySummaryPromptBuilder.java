package com.example.subsystemdiscovery.boundaryanalysis.service;

import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryNodeDto;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundarySubsystemPairDto;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundarySummaryInput;
import com.example.subsystemdiscovery.discovery.dto.SubsystemDto;
import com.example.subsystemdiscovery.discovery.dto.SubsystemLinkDto;
import com.example.subsystemdiscovery.discovery.dto.SummaryType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class BoundarySummaryPromptBuilder {

    public String build(BoundarySummaryInput input, SummaryType summaryType) {
        SummaryType resolvedType = summaryType == null ? SummaryType.MEDIUM_DETAILED : summaryType;
        List<SubsystemDto> subsystems = input.subsystems() == null ? List.of() : input.subsystems();
        List<SubsystemLinkDto> links = input.subsystemLinks() == null ? List.of() : input.subsystemLinks();
        List<BoundaryNodeDto> nodes = input.boundaryNodes() == null ? List.of() : input.boundaryNodes();
        List<BoundarySubsystemPairDto> pairs = input.subsystemPairHotspots() == null
                ? List.of()
                : input.subsystemPairHotspots();

        int nodeLimit = switch (resolvedType) {
            case LESS_DETAILED -> 8;
            case COMPLETE_DETAILED -> 40;
            default -> 20;
        };
        int pairLimit = switch (resolvedType) {
            case LESS_DETAILED -> 5;
            case COMPLETE_DETAILED -> 25;
            default -> 12;
        };

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a senior software architect reviewing subsystem boundary nodes for microservice design and security review.\n\n");
        prompt.append("Base your answer only on the data below. Do not invent subsystem responsibilities or code details.\n\n");

        prompt.append("=== RUN CONTEXT ===\n")
                .append("Discovery run ID: ").append(input.discoveryRunId()).append("\n")
                .append("Application: ").append(StringUtils.hasText(input.applicationKey()) ? input.applicationKey() : "unknown").append("\n")
                .append("Subsystems: ").append(input.totalSubsystems()).append("\n")
                .append("Total nodes: ").append(input.totalNodes()).append("\n")
                .append("Total weighted graph edges: ").append(input.totalEdges()).append("\n")
                .append("Boundary nodes: ").append(input.boundaryNodeCount())
                .append(" (").append(Math.round(input.boundaryNodeRatio() * 1000.0) / 10.0).append("% of nodes)\n");

        if (input.summary() != null) {
            prompt.append("Risk mix: HIGH=").append(input.summary().highRiskBoundaryCount())
                    .append(", MEDIUM=").append(input.summary().mediumRiskBoundaryCount())
                    .append(", LOW=").append(input.summary().lowRiskBoundaryCount()).append("\n")
                    .append("Boundary node types: METHOD=").append(input.summary().methodBoundaryCount())
                    .append(", CLASS=").append(input.summary().classBoundaryCount())
                    .append(", PACKAGE=").append(input.summary().packageBoundaryCount()).append("\n")
                    .append("API candidates: ").append(input.summary().apiCandidateCount()).append("\n");
        }
        prompt.append("\n");

        prompt.append("=== SUBSYSTEMS ===\n");
        subsystems.stream().limit(20).forEach(subsystem -> {
            prompt.append("- ").append(subsystem.id()).append(" / ").append(subsystem.name())
                    .append(": nodes=").append(subsystem.nodeCount())
                    .append(", stability=").append(subsystem.stabilityScore());
            if (subsystem.topPackages() != null && !subsystem.topPackages().isEmpty()) {
                prompt.append(", packages=")
                        .append(subsystem.topPackages().stream().limit(4).collect(Collectors.joining(", ")));
            }
            prompt.append("\n");
        });
        prompt.append("\n");

        if (!links.isEmpty()) {
            prompt.append("=== EXISTING SUBSYSTEM COUPLING ===\n");
            links.stream().limit(pairLimit).forEach(link -> prompt.append("- ")
                    .append(link.source()).append(" -> ").append(link.target())
                    .append(": ").append(link.couplingStrength())
                    .append(", edges=").append(link.edgeCount()).append("\n"));
            prompt.append("\n");
        }

        prompt.append("=== TOP BOUNDARY HOTSPOTS ===\n");
        nodes.stream().limit(nodeLimit).forEach(node -> prompt.append("- ")
                .append(node.riskLevel()).append(" ")
                .append(node.nodeType()).append(" ")
                .append(node.name())
                .append(" | role=").append(node.boundaryRole())
                .append(" | home=").append(node.homeSubsystemName())
                .append(" | score=").append(node.boundaryScore())
                .append(" | importance=").append(node.importanceScore())
                .append(" | crossEdges=").append(node.crossSubsystemEdges())
                .append(" | foreignSubsystems=").append(node.foreignSubsystemCount())
                .append(" | bridges=")
                .append(node.crossSubsystemLinks() == null ? "" : node.crossSubsystemLinks().stream()
                        .limit(5)
                        .map(link -> link.subsystemName() + "(" + link.couplingStrength() + "/" + link.crossEdgeCount() + ")")
                        .collect(Collectors.joining(", ")))
                .append("\n"));
        prompt.append("\n");

        if (!pairs.isEmpty()) {
            prompt.append("=== SUBSYSTEM-PAIR HOTSPOTS ===\n");
            pairs.stream().limit(pairLimit).forEach(pair -> prompt.append("- ")
                    .append(pair.sourceSubsystemName()).append(" <-> ").append(pair.targetSubsystemName())
                    .append(" | ").append(pair.couplingStrength())
                    .append(" | boundaryNodes=").append(pair.boundaryNodeCount())
                    .append(" | crossEdges=").append(pair.crossEdgeCount())
                    .append(" | topNodes=").append(pair.topBoundaryNodes() == null ? "" : String.join(", ", pair.topBoundaryNodes()))
                    .append("\n"));
            prompt.append("\n");
        }

        prompt.append("=== TASK ===\n");
        switch (resolvedType) {
            case LESS_DETAILED -> prompt
                    .append("Write a concise boundary health assessment under 180 words.\n")
                    .append("Cover cohesion, coupling, API extraction candidates, microservice boundary fit, and security review priorities.\n");
            case COMPLETE_DETAILED -> prompt
                    .append("Write a detailed architecture review under 700 words with these sections:\n")
                    .append("1. Boundary Health Assessment\n")
                    .append("2. Cohesion and Coupling Concerns\n")
                    .append("3. API Contract and Microservice Extraction Candidates\n")
                    .append("4. Security Review and Trust-Boundary Priorities\n")
                    .append("5. Refactoring Roadmap with concrete architecture solutions\n");
            case CUSTOM, MEDIUM_DETAILED -> prompt
                    .append("Write a practical architecture summary under 400 words with these sections:\n")
                    .append("1. Boundary Health Assessment\n")
                    .append("2. Highest-Priority Boundary Nodes\n")
                    .append("3. API and Microservice Split Suggestions\n")
                    .append("4. Security Review Priorities\n")
                    .append("5. Recommended Architecture Actions\n");
        }

        prompt.append("\nRules:\n")
                .append("- Use plain engineering language.\n")
                .append("- Be specific about cohesion, coupling, APIs, microservices, and architecture changes.\n")
                .append("- Mention node names only when they appear in the data above.\n")
                .append("- Do not output JSON.\n");

        return prompt.toString();
    }
}

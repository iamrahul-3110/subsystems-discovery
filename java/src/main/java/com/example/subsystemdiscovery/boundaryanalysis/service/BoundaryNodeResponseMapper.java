package com.example.subsystemdiscovery.boundaryanalysis.service;

import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryNodeDto;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryNodeResponse;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryStatisticsDto;
import com.example.subsystemdiscovery.boundaryanalysis.dto.ConnectedSubsystemDto;
import com.example.subsystemdiscovery.boundaryanalysis.dto.SelectedInteractionDto;
import com.example.subsystemdiscovery.boundaryanalysis.dto.SubsystemInteractionDto;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Maps internal boundary analysis model objects to API response DTOs.
 *
 * <p>No business logic — just shaping. Keeps the service layer
 * focused on graph topology and the DTOs focused on API contracts.
 *
 * <p>Fix #4: {@link #toOverview} now accepts internal model objects so that
 * overview stats can be computed without constructing DTOs for every node.
 *
 * <p>Fix #5: Removed {@code edges} from {@link ConnectedSubsystemDto},
 * removed {@code totalCrossEdges} from {@link BoundaryNodeDto},
 * removed {@code totalResults} and {@code filters} from response.
 */
@Component
public class BoundaryNodeResponseMapper {

    /**
     * Map an internal boundary node to its API DTO.
     *
     * <p>Converts the {@code Map<String, int[]>} per-subsystem edges
     * into sorted {@code List<ConnectedSubsystemDto>} pairs.
     */
    public BoundaryNodeDto toDto(BoundaryAnalysisService.InternalBoundaryNode node) {
        // Convert perSubsystemEdges map → sorted list of ConnectedSubsystemDto
        List<ConnectedSubsystemDto> connectedSubsystems = node.perSubsystemEdges().entrySet()
                .stream()
                .map(e -> {
                    int[] counts = e.getValue();
                    int outgoing = counts[0];
                    int incoming = counts[1];
                    return new ConnectedSubsystemDto(e.getKey(), outgoing, incoming);
                })
                .sorted(Comparator.comparingInt((ConnectedSubsystemDto c) -> c.outgoingEdges() + c.incomingEdges()).reversed()
                        .thenComparing(ConnectedSubsystemDto::subsystem))
                .toList();

        return new BoundaryNodeDto(
                node.nodeName(),
                node.nodeType(),
                node.owningSubsystem(),
                connectedSubsystems,
                node.incomingCrossEdges(),
                node.outgoingCrossEdges(),
                node.boundaryScore()
        );
    }

    /**
     * Build the overview statistics directly from the internal analysis result.
     *
     * <p>Fix #4: Accepts internal nodes — no need to construct DTOs for all
     * nodes just to compute overview stats.
     */
    public BoundaryStatisticsDto toOverview(
            List<BoundaryAnalysisService.InternalBoundaryNode> allNodes,
            List<SubsystemInteractionDto> interactions,
            int totalNodeCount) {

        int totalBoundary = allNodes.size();
        double boundaryRatio = totalNodeCount > 0
                ? round((double) totalBoundary / totalNodeCount) : 0.0;

        double avgConnections = allNodes.stream()
                .mapToInt(BoundaryAnalysisService.InternalBoundaryNode::connectedSubsystemCount)
                .average().orElse(0.0);

        double highestScore = allNodes.stream()
                .mapToDouble(BoundaryAnalysisService.InternalBoundaryNode::boundaryScore)
                .max().orElse(0.0);

        return new BoundaryStatisticsDto(
                totalBoundary,
                boundaryRatio,
                interactions.size(),
                round(avgConnections),
                round(highestScore)
        );
    }

    /**
     * Build the subsystem interaction DTOs from internal model.
     */
    public List<SubsystemInteractionDto> toInteractionDtos(
            List<BoundaryAnalysisService.InternalSubsystemInteraction> interactions) {
        return interactions.stream()
                .map(i -> new SubsystemInteractionDto(
                        i.fromSubsystem(), i.toSubsystem(), i.interactionCount()))
                .toList();
    }

    /**
     * Build a drill-down result for a specific subsystem pair.
     */
    public SelectedInteractionDto toSelectedInteraction(
            String fromSubsystem,
            String toSubsystem,
            int interactionCount,
            List<BoundaryNodeDto> filteredNodes) {
        return new SelectedInteractionDto(
                fromSubsystem, toSubsystem, interactionCount, filteredNodes);
    }

    /**
     * Assemble the full API response envelope.
     *
     * <p>Fix #5: No longer includes {@code filters} or {@code totalResults}.
     */
    public BoundaryNodeResponse toResponse(
            Long discoveryRunId,
            BoundaryStatisticsDto overview,
            List<BoundaryNodeDto> topBoundaryNodes,
            List<SubsystemInteractionDto> subsystemInteractions,
            SelectedInteractionDto selectedInteraction) {
        return new BoundaryNodeResponse(
                discoveryRunId,
                overview,
                topBoundaryNodes,
                subsystemInteractions,
                selectedInteraction
        );
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}

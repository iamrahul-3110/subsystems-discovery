package com.example.subsystemdiscovery.boundaryanalysis.service;

import com.example.subsystemdiscovery.algorithm.model.WeightedGraph;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryNodeDto;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryNodeRequest;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryNodeResponse;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryStatisticsDto;
import com.example.subsystemdiscovery.boundaryanalysis.dto.SelectedInteractionDto;
import com.example.subsystemdiscovery.boundaryanalysis.dto.SubsystemInteractionDto;
import com.example.subsystemdiscovery.boundaryanalysis.dto.ConnectedSubsystemDto;
import com.example.subsystemdiscovery.discovery.SubsystemDiscoveryService;
import com.example.subsystemdiscovery.discovery.dto.NodeAssignmentDto;
import com.example.subsystemdiscovery.discovery.dto.SubsystemAlgorithmParams;
import com.example.subsystemdiscovery.discovery.dto.SubsystemDiscoveryResponse;
import com.example.subsystemdiscovery.repository.SubsystemHistoryMapper;
import com.example.subsystemdiscovery.repository.entity.SubsystemRunMaster;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Optimized orchestrator for boundary node detection.
 *
 * <ol>
 *   <li>Checks the ephemeral in-process Caffeine cache for already computed analysis results.</li>
 *   <li>On cache miss, loads the discovery response and graph once and computes the analysis.</li>
 *   <li>Applies filtering, sorting, and limiting directly on internal model objects before mapping to DTOs.</li>
 *   <li>Maps only the limited subset to response DTOs.</li>
 * </ol>
 */
@Service
public class BoundaryNodeServiceImpl implements BoundaryNodeService {

    private final SubsystemDiscoveryService discoveryService;
    private final SubsystemHistoryMapper subsystemHistoryMapper;
    private final BoundaryAnalysisService analysisService;
    private final BoundaryNodeResponseMapper responseMapper;
    private final BoundaryAnalysisCache analysisCache;

    public BoundaryNodeServiceImpl(SubsystemDiscoveryService discoveryService,
                                   SubsystemHistoryMapper subsystemHistoryMapper,
                                   BoundaryAnalysisService analysisService,
                                   BoundaryNodeResponseMapper responseMapper,
                                   BoundaryAnalysisCache analysisCache) {
        this.discoveryService = discoveryService;
        this.subsystemHistoryMapper = subsystemHistoryMapper;
        this.analysisService = analysisService;
        this.responseMapper = responseMapper;
        this.analysisCache = analysisCache;
    }

    @Override
    public BoundaryNodeResponse detectBoundaryNodes(BoundaryNodeRequest request) {
        if (request.discoveryRunId() == null) {
            throw new IllegalArgumentException("discoveryRunId is required to detect boundary nodes");
        }

        // 1. Check cache first (Fix #8)
        BoundaryAnalysisService.BoundaryAnalysisResult analysisResult = analysisCache.get(request.discoveryRunId());
        Set<String> discoverySubsystemIds;

        if (analysisResult == null) {
            // Cache miss: load master run and cached discovery result (Fix #2: single load)
            SubsystemRunMaster master = subsystemHistoryMapper.selectMasterById(request.discoveryRunId());
            if (master == null) {
                throw new IllegalArgumentException("No discovery run found for ID: " + request.discoveryRunId());
            }

            String analysisTime = master.getAnalysisTime();
            SubsystemAlgorithmParams params = new SubsystemAlgorithmParams(
                    null,
                    master.getRuns(),
                    master.getConsensusThreshold(),
                    master.getResolution(),
                    null, null, null);

            // Load discovery result and build weighted graph in one step
            SubsystemDiscoveryService.DiscoveryWithGraph discoveryWithGraph =
                    discoveryService.discoverWithGraph(analysisTime, params);
            SubsystemDiscoveryResponse discovery = discoveryWithGraph.response();
            WeightedGraph graph = discoveryWithGraph.graph();

            List<NodeAssignmentDto> nodeAssignments = discovery.nodeAssignments();
            if (nodeAssignments == null || nodeAssignments.isEmpty()) {
                throw new IllegalStateException(
                        "Node assignments not available for analysisTime=" + analysisTime
                                + ". Cannot detect boundary nodes without node-to-subsystem mappings.");
            }

            // Perform core graph topology analysis
            analysisResult = analysisService.analyze(nodeAssignments, discovery.subsystems(), graph);

            // Cache the result (Fix #8)
            analysisCache.put(request.discoveryRunId(), analysisResult);

            discoverySubsystemIds = discovery.subsystems().stream()
                    .map(com.example.subsystemdiscovery.discovery.dto.SubsystemDto::id)
                    .collect(Collectors.toSet());
        } else {
            // Cache hit: load discovery subsystem IDs from the cached run master configuration
            SubsystemRunMaster master = subsystemHistoryMapper.selectMasterById(request.discoveryRunId());
            String analysisTime = master.getAnalysisTime();
            SubsystemAlgorithmParams params = new SubsystemAlgorithmParams(
                    null,
                    master.getRuns(),
                    master.getConsensusThreshold(),
                    master.getResolution(),
                    null, null, null);
            SubsystemDiscoveryResponse discovery = discoveryService.discover(analysisTime, params);
            discoverySubsystemIds = discovery.subsystems().stream()
                    .map(com.example.subsystemdiscovery.discovery.dto.SubsystemDto::id)
                    .collect(Collectors.toSet());
        }

        // 2. Map interactions (uses cluster IDs directly for Fix #6)
        List<SubsystemInteractionDto> interactions =
                responseMapper.toInteractionDtos(analysisResult.subsystemInteractions());

        // Consistency check using cluster IDs
        for (BoundaryAnalysisService.InternalBoundaryNode node : analysisResult.boundaryNodes()) {
            if (!discoverySubsystemIds.contains(node.owningSubsystem())) {
                throw new IllegalStateException("Consistency error: Subsystem ID '" + node.owningSubsystem() 
                        + "' referenced in boundary node '" + node.nodeName() + "' is not present in discovery subsystems.");
            }
            for (String connSubsystemId : node.perSubsystemEdges().keySet()) {
                if (!discoverySubsystemIds.contains(connSubsystemId)) {
                    throw new IllegalStateException("Consistency error: Target Subsystem ID '" + connSubsystemId 
                            + "' referenced in boundary node '" + node.nodeName() + "' is not present in discovery subsystems.");
                }
            }
        }

        // 3. Filter the internal nodes directly (Fix #4)
        List<BoundaryAnalysisService.InternalBoundaryNode> filteredInternalNodes =
                applyNodeTypeFilter(analysisResult.boundaryNodes(), request.nodeType());

        // 4. Sort the internal nodes (Fix #4)
        List<BoundaryAnalysisService.InternalBoundaryNode> sortedInternalNodes =
                applySorting(filteredInternalNodes, request.sortOrderOrDefault());

        // 5. Handle drill-down or global view
        SelectedInteractionDto selectedInteraction = null;
        List<BoundaryNodeDto> topNodes;

        if (request.hasDrillDown()) {
            // Filter to nodes participating in the selected subsystem pair
            List<BoundaryAnalysisService.InternalBoundaryNode> drillDownInternalNodes = sortedInternalNodes.stream()
                    .filter(node -> participatesInInteraction(
                            node, request.fromSubsystem(), request.toSubsystem()))
                    .toList();

            // Find the interaction count for this pair
            int interactionCount = interactions.stream()
                    .filter(i -> i.fromSubsystem().equals(request.fromSubsystem())
                            && i.toSubsystem().equals(request.toSubsystem()))
                    .findFirst()
                    .map(SubsystemInteractionDto::interactionCount)
                    .orElse(0);

            // Apply limit and map ONLY the top nodes to DTO (Fix #4)
            List<BoundaryNodeDto> drillDownDtos = drillDownInternalNodes.stream()
                    .limit(request.nodeLimitOrDefault())
                    .map(node -> filterEdgesForDrillDown(
                            responseMapper.toDto(node), request.fromSubsystem(), request.toSubsystem()))
                    .toList();

            selectedInteraction = responseMapper.toSelectedInteraction(
                    request.fromSubsystem(), request.toSubsystem(),
                    interactionCount, drillDownDtos);

            topNodes = drillDownDtos;
        } else {
            // Global view: limit and map ONLY the top nodes to DTO (Fix #4)
            topNodes = sortedInternalNodes.stream()
                    .limit(request.nodeLimitOrDefault())
                    .map(responseMapper::toDto)
                    .toList();
        }

        // 6. Build overview from all internal nodes (before limit/drill-down)
        BoundaryStatisticsDto overview = responseMapper.toOverview(
                analysisResult.boundaryNodes(), interactions, analysisResult.totalNodeCount());

        return responseMapper.toResponse(
                request.discoveryRunId(), overview, topNodes,
                interactions, selectedInteraction);
    }

    private List<BoundaryAnalysisService.InternalBoundaryNode> applyNodeTypeFilter(
            List<BoundaryAnalysisService.InternalBoundaryNode> nodes, String nodeType) {
        if (nodeType == null || nodeType.trim().isEmpty() || "ALL".equalsIgnoreCase(nodeType)) {
            return nodes;
        }
        return nodes.stream()
                .filter(n -> nodeType.equalsIgnoreCase(n.nodeType()))
                .toList();
    }

    private List<BoundaryAnalysisService.InternalBoundaryNode> applySorting(
            List<BoundaryAnalysisService.InternalBoundaryNode> nodes, String sortOrder) {
        if (sortOrder == null) {
            sortOrder = "SCORE_DESC";
        }
        Comparator<BoundaryAnalysisService.InternalBoundaryNode> comparator = switch (sortOrder) {
            case "NODE_NAME_ASC" ->
                    Comparator.comparing(BoundaryAnalysisService.InternalBoundaryNode::nodeName);
            case "NODE_NAME_DESC" ->
                    Comparator.comparing(BoundaryAnalysisService.InternalBoundaryNode::nodeName).reversed();
            case "OUTGOING_ASC" ->
                    Comparator.comparingInt(BoundaryAnalysisService.InternalBoundaryNode::outgoingCrossEdges);
            case "OUTGOING_DESC" ->
                    Comparator.comparingInt(BoundaryAnalysisService.InternalBoundaryNode::outgoingCrossEdges).reversed();
            case "INCOMING_ASC" ->
                    Comparator.comparingInt(BoundaryAnalysisService.InternalBoundaryNode::incomingCrossEdges);
            case "INCOMING_DESC" ->
                    Comparator.comparingInt(BoundaryAnalysisService.InternalBoundaryNode::incomingCrossEdges).reversed();
            case "SCORE_ASC" ->
                    Comparator.comparingDouble(BoundaryAnalysisService.InternalBoundaryNode::boundaryScore);
            case "SCORE_DESC" ->
                    Comparator.comparingDouble(BoundaryAnalysisService.InternalBoundaryNode::boundaryScore).reversed();
            case "LEAST_CONNECTED" ->
                    Comparator.comparingDouble(BoundaryAnalysisService.InternalBoundaryNode::boundaryScore);
            case "MOST_INCOMING" ->
                    Comparator.comparingInt(BoundaryAnalysisService.InternalBoundaryNode::incomingCrossEdges).reversed();
            case "MOST_OUTGOING" ->
                    Comparator.comparingInt(BoundaryAnalysisService.InternalBoundaryNode::outgoingCrossEdges).reversed();
            default -> // MOST_CONNECTED or default
                    Comparator.comparingDouble(BoundaryAnalysisService.InternalBoundaryNode::boundaryScore).reversed();
        };

        if (!sortOrder.startsWith("NODE_NAME")) {
            comparator = comparator.thenComparing(BoundaryAnalysisService.InternalBoundaryNode::nodeName);
        }

        return nodes.stream().sorted(comparator).collect(Collectors.toList());
    }

    private boolean participatesInInteraction(
            BoundaryAnalysisService.InternalBoundaryNode node, String fromSubsystem, String toSubsystem) {
        String owning = node.owningSubsystem().trim();
        String from = fromSubsystem.trim();
        String to = toSubsystem.trim();

        if (owning.equalsIgnoreCase(from)) {
            return node.perSubsystemEdges().containsKey(to);
        }
        if (owning.equalsIgnoreCase(to)) {
            return node.perSubsystemEdges().containsKey(from);
        }
        return false;
    }

    private BoundaryNodeDto filterEdgesForDrillDown(
            BoundaryNodeDto node, String fromSubsystem, String toSubsystem) {
        int incoming = 0;
        int outgoing = 0;

        String owning = node.owningSubsystem().trim();
        String from = fromSubsystem.trim();
        String to = toSubsystem.trim();

        if (owning.equalsIgnoreCase(from)) {
            outgoing = node.connectedSubsystems().stream()
                    .filter(c -> c.subsystem().trim().equalsIgnoreCase(to))
                    .mapToInt(ConnectedSubsystemDto::outgoingEdges)
                    .sum();
            incoming = node.connectedSubsystems().stream()
                    .filter(c -> c.subsystem().trim().equalsIgnoreCase(to))
                    .mapToInt(ConnectedSubsystemDto::incomingEdges)
                    .sum();
        } else if (owning.equalsIgnoreCase(to)) {
            incoming = node.connectedSubsystems().stream()
                    .filter(c -> c.subsystem().trim().equalsIgnoreCase(from))
                    .mapToInt(ConnectedSubsystemDto::incomingEdges)
                    .sum();
            outgoing = node.connectedSubsystems().stream()
                    .filter(c -> c.subsystem().trim().equalsIgnoreCase(from))
                    .mapToInt(ConnectedSubsystemDto::outgoingEdges)
                    .sum();
        }

        return new BoundaryNodeDto(
                node.nodeName(),
                node.nodeType(),
                node.owningSubsystem(),
                node.connectedSubsystems(),
                incoming,
                outgoing,
                node.boundaryScore()
        );
    }
}

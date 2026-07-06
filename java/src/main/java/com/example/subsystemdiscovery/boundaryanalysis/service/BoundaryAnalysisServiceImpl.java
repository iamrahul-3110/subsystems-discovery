package com.example.subsystemdiscovery.boundaryanalysis.service;

import com.example.subsystemdiscovery.algorithm.WeightedGraphBuilder;
import com.example.subsystemdiscovery.algorithm.model.WeightedGraph;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryAnalysisRequest;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryAnalysisResponse;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryNodeDto;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundarySubsystemPairDto;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundarySummaryInput;
import com.example.subsystemdiscovery.boundaryanalysis.mapper.BoundaryMappingResult;
import com.example.subsystemdiscovery.boundaryanalysis.mapper.BoundaryNodeMapper;
import com.example.subsystemdiscovery.discovery.SubsystemDiscoveryService;
import com.example.subsystemdiscovery.discovery.dto.NodeAssignmentDto;
import com.example.subsystemdiscovery.discovery.dto.SubsystemAlgorithmParams;
import com.example.subsystemdiscovery.discovery.dto.SubsystemDiscoveryResponse;
import com.example.subsystemdiscovery.discovery.dto.SubsystemDto;
import com.example.subsystemdiscovery.discovery.dto.SubsystemLinkDto;
import com.example.subsystemdiscovery.discovery.dto.SubsystemPersistenceDto;
import com.example.subsystemdiscovery.discovery.dto.SummaryType;
import com.example.subsystemdiscovery.graph.GraphExtractionService;
import com.example.subsystemdiscovery.graph.dto.RawGraphDto;
import com.example.subsystemdiscovery.repository.SubsystemHistoryMapper;
import com.example.subsystemdiscovery.repository.TbNodeHistoryMapper;
import com.example.subsystemdiscovery.repository.entity.ApplicationMetadata;
import com.example.subsystemdiscovery.repository.entity.SubsystemRunMaster;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class BoundaryAnalysisServiceImpl implements BoundaryAnalysisService {

    private static final String BOUNDARY_SUMMARY_PREFIX = "BOUNDARY_";

    private final SubsystemDiscoveryService subsystemDiscoveryService;
    private final SubsystemHistoryMapper subsystemHistoryMapper;
    private final TbNodeHistoryMapper tbNodeHistoryMapper;
    private final GraphExtractionService graphExtractionService;
    private final WeightedGraphBuilder weightedGraphBuilder;
    private final BoundaryNodeMapper boundaryNodeMapper;
    private final BoundaryLlmSummaryService boundaryLlmSummaryService;
    private final ObjectMapper objectMapper;

    public BoundaryAnalysisServiceImpl(SubsystemDiscoveryService subsystemDiscoveryService,
                                       SubsystemHistoryMapper subsystemHistoryMapper,
                                       TbNodeHistoryMapper tbNodeHistoryMapper,
                                       GraphExtractionService graphExtractionService,
                                       WeightedGraphBuilder weightedGraphBuilder,
                                       BoundaryNodeMapper boundaryNodeMapper,
                                       BoundaryLlmSummaryService boundaryLlmSummaryService,
                                       ObjectMapper objectMapper) {
        this.subsystemDiscoveryService = subsystemDiscoveryService;
        this.subsystemHistoryMapper = subsystemHistoryMapper;
        this.tbNodeHistoryMapper = tbNodeHistoryMapper;
        this.graphExtractionService = graphExtractionService;
        this.weightedGraphBuilder = weightedGraphBuilder;
        this.boundaryNodeMapper = boundaryNodeMapper;
        this.boundaryLlmSummaryService = boundaryLlmSummaryService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public BoundaryAnalysisResponse analyze(BoundaryAnalysisRequest request) {
        AnalysisInputs inputs = request.discoveryRunId() != null
                ? inputsFromExistingRun(request.discoveryRunId(), request.params())
                : inputsFromFreshDiscovery(request.analysisTime(), request.params());

        BoundaryMappingResult mapping = boundaryNodeMapper.map(
                inputs.graph(),
                inputs.nodeAssignments(),
                inputs.subsystems(),
                request.topNodeLimit(),
                request.hotspotLimit()
        );

        BoundaryAnalysisResponse response = baseResponse(
                inputs.discoveryRunId(),
                inputs.subsystems(),
                inputs.graph(),
                mapping,
                null,
                null,
                null,
                false
        );

        if (request.includeAiSummaryOrDefault()) {
            return withAiSummary(response, inputs, request);
        }

        return response;
    }

    private AnalysisInputs inputsFromExistingRun(Long discoveryRunId, SubsystemAlgorithmParams params) {
        SubsystemRunMaster master = subsystemHistoryMapper.selectMasterById(discoveryRunId);
        if (master == null) {
            throw new IllegalArgumentException("No discovery run found for discoveryRunId=" + discoveryRunId);
        }

        SubsystemPersistenceDto persisted = readPersistedDiscovery(master, discoveryRunId);
        List<NodeAssignmentDto> nodeAssignments = safeList(persisted.nodeAssignments());
        if (CollectionUtils.isEmpty(nodeAssignments)) {
            throw new IllegalStateException(
                    "Node assignments are not available for discoveryRunId=" + discoveryRunId
                            + ". Re-run subsystem discovery so boundary analysis can reuse node assignments.");
        }

        ApplicationMetadata meta = resolveMetadata(master.getAnalysisTime());
        WeightedGraph graph = buildWeightedGraph(master.getAnalysisTime(), meta, params);

        return new AnalysisInputs(
                discoveryRunId,
                master.getAnalysisTime(),
                meta.applicationKey(),
                graph,
                safeList(persisted.subsystems()),
                safeList(persisted.subsystemLinks()),
                nodeAssignments
        );
    }

    private AnalysisInputs inputsFromFreshDiscovery(String analysisTime, SubsystemAlgorithmParams params) {
        SubsystemAlgorithmParams effectiveParams = params == null ? emptyParams() : params;
        SubsystemDiscoveryResponse discovery = subsystemDiscoveryService.discover(analysisTime, effectiveParams);
        List<NodeAssignmentDto> nodeAssignments = safeList(discovery.nodeAssignments());
        if (CollectionUtils.isEmpty(nodeAssignments)) {
            throw new IllegalStateException("Node assignments were unexpectedly empty after a fresh discovery run.");
        }

        ApplicationMetadata meta = resolveMetadata(analysisTime);
        WeightedGraph graph = buildWeightedGraph(analysisTime, meta, effectiveParams);

        return new AnalysisInputs(
                discovery.discoveryRunId(),
                analysisTime,
                meta.applicationKey(),
                graph,
                safeList(discovery.subsystems()),
                safeList(discovery.subsystemLinks()),
                nodeAssignments
        );
    }

    private BoundaryAnalysisResponse withAiSummary(BoundaryAnalysisResponse response,
                                                   AnalysisInputs inputs,
                                                   BoundaryAnalysisRequest request) {
        SummaryType summaryType = request.summaryTypeOrDefault();
        String llmModel = boundaryLlmSummaryService.resolveModel(request.llmModel());
        String cacheType = BOUNDARY_SUMMARY_PREFIX + summaryType.name();

        String summaryText = null;
        boolean fallback = false;
        if (response.discoveryRunId() != null) {
            summaryText = subsystemHistoryMapper.selectLlmSummaryByConfig(
                    response.discoveryRunId(), llmModel, cacheType);
        }

        if (!StringUtils.hasText(summaryText)) {
            BoundarySummaryInput summaryInput = new BoundarySummaryInput(
                    response.discoveryRunId(),
                    inputs.applicationKey(),
                    response.totalSubsystems(),
                    response.totalNodes(),
                    response.totalEdges(),
                    response.boundaryNodeCount(),
                    response.boundaryNodeRatio(),
                    response.summary(),
                    inputs.subsystems(),
                    inputs.subsystemLinks(),
                    response.subsystemPairHotspots(),
                    response.topBoundaryNodes()
            );
            BoundarySummaryResult generated = boundaryLlmSummaryService.summarise(
                    summaryInput, summaryType, llmModel);
            summaryText = generated.text();
            llmModel = generated.llmModel();
            fallback = generated.fallback();

            if (response.discoveryRunId() != null && StringUtils.hasText(summaryText)) {
                try {
                    subsystemHistoryMapper.insertLlmSummary(
                            response.discoveryRunId(), llmModel, cacheType, summaryText);
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to persist LLM boundary summary to the database", e);
                }
            }
        } else {
            fallback = summaryText.startsWith("LLM boundary-node summary not available.");
        }

        return baseResponse(
                response.discoveryRunId(),
                inputs.subsystems(),
                inputs.graph(),
                new BoundaryMappingResult(
                        response.boundaryNodes(),
                        response.topBoundaryNodes(),
                        response.subsystemPairHotspots(),
                        response.summary()
                ),
                summaryText,
                llmModel,
                summaryType.name(),
                fallback
        );
    }

    private BoundaryAnalysisResponse baseResponse(Long discoveryRunId,
                                                  List<SubsystemDto> subsystems,
                                                  WeightedGraph graph,
                                                  BoundaryMappingResult mapping,
                                                  String aiSummary,
                                                  String llmModel,
                                                  String summaryType,
                                                  boolean aiSummaryFallback) {
        int totalNodes = graph.getNodes() == null ? 0 : graph.getNodes().size();
        int totalEdges = graph.getEdges() == null ? 0 : graph.getEdges().size();
        List<BoundaryNodeDto> boundaryNodes = safeList(mapping.boundaryNodes());
        double boundaryNodeRatio = totalNodes == 0
                ? 0.0
                : round((double) boundaryNodes.size() / totalNodes);

        return new BoundaryAnalysisResponse(
                discoveryRunId,
                subsystems == null ? 0 : subsystems.size(),
                totalNodes,
                totalEdges,
                boundaryNodes.size(),
                boundaryNodeRatio,
                mapping.summary(),
                safeList(mapping.subsystemPairHotspots()),
                safeList(mapping.topBoundaryNodes()),
                boundaryNodes,
                aiSummary,
                llmModel,
                summaryType,
                aiSummaryFallback
        );
    }

    private SubsystemPersistenceDto readPersistedDiscovery(SubsystemRunMaster master, Long discoveryRunId) {
        String cachedJson = master.getDiscoveryResult();
        if (!StringUtils.hasText(cachedJson)) {
            cachedJson = subsystemHistoryMapper.selectHistoryResult(discoveryRunId);
        }
        if (!StringUtils.hasText(cachedJson)) {
            throw new IllegalStateException("No discovery result data found for discoveryRunId=" + discoveryRunId);
        }
        try {
            return objectMapper.readValue(cachedJson, SubsystemPersistenceDto.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialise discovery result for discoveryRunId=" + discoveryRunId, e);
        }
    }

    private WeightedGraph buildWeightedGraph(String analysisTime,
                                             ApplicationMetadata meta,
                                             SubsystemAlgorithmParams params) {
        List<RawGraphDto> rawGraphs = graphExtractionService.extract(
                meta.applicationId(),
                analysisTime,
                meta.applicationKey(),
                params != null ? params.graphTypes() : null,
                null
        );
        return weightedGraphBuilder.build(rawGraphs);
    }

    private ApplicationMetadata resolveMetadata(String analysisTime) {
        ApplicationMetadata meta = tbNodeHistoryMapper.selectApplicationMetadata(analysisTime);
        if (meta == null) {
            throw new IllegalArgumentException("Cannot resolve application metadata for analysisTime=" + analysisTime);
        }
        return meta;
    }

    private SubsystemAlgorithmParams emptyParams() {
        return new SubsystemAlgorithmParams(null, null, null, null, null, null, null);
    }

    private static <T> List<T> safeList(List<T> input) {
        return input == null ? List.of() : input;
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private record AnalysisInputs(
            Long discoveryRunId,
            String analysisTime,
            String applicationKey,
            WeightedGraph graph,
            List<SubsystemDto> subsystems,
            List<SubsystemLinkDto> subsystemLinks,
            List<NodeAssignmentDto> nodeAssignments
    ) {
    }
}

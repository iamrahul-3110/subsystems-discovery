package com.example.subsystemdiscovery.discovery;

import com.example.subsystemdiscovery.llm.LlmSubsystemDiscoveryService;
import com.example.subsystemdiscovery.llm.dto.LlmDiscoveryResponse;
import com.example.subsystemdiscovery.discovery.dto.SubsystemDto;
import com.example.subsystemdiscovery.discovery.dto.SubsystemAlgorithmParams;
import com.example.subsystemdiscovery.discovery.dto.SubsystemLinkDto;
import com.example.subsystemdiscovery.discovery.dto.SubsystemPersistenceDto;
import com.example.subsystemdiscovery.discovery.dto.SummaryType;
import com.example.subsystemdiscovery.repository.entity.ApplicationMetadata;
import com.example.subsystemdiscovery.repository.entity.SubsystemRunMaster;
import com.example.subsystemdiscovery.repository.SubsystemHistoryMapper;
import com.example.subsystemdiscovery.repository.TbNodeHistoryMapper;
import com.example.subsystemdiscovery.graph.GraphExtractionService;
import com.example.subsystemdiscovery.algorithm.WeightedGraphBuilder;
import com.example.subsystemdiscovery.algorithm.ClusterAggregationUtil;
import com.example.subsystemdiscovery.algorithm.LeidenAlgorithmUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SubsystemDiscoveryServiceTest {

    private GraphExtractionService graphExtractionService;
    private WeightedGraphBuilder weightedGraphBuilder;
    private LeidenAlgorithmUtil leidenAlgorithmUtil;
    private ClusterAggregationUtil clusterAggregationUtil;
    private SubsystemLabelService subsystemLabelService;
    private LlmSubsystemDiscoveryService llmSubsystemDiscoveryService;
    private TbNodeHistoryMapper tbNodeHistoryMapper;
    private SubsystemHistoryMapper subsystemHistoryMapper;
    private ObjectMapper objectMapper;

    private SubsystemDiscoveryService service;

    @BeforeEach
    public void setUp() {
        graphExtractionService = mock(GraphExtractionService.class);
        weightedGraphBuilder = mock(WeightedGraphBuilder.class);
        leidenAlgorithmUtil = mock(LeidenAlgorithmUtil.class);
        clusterAggregationUtil = mock(ClusterAggregationUtil.class);
        subsystemLabelService = mock(SubsystemLabelService.class);
        llmSubsystemDiscoveryService = mock(LlmSubsystemDiscoveryService.class);
        tbNodeHistoryMapper = mock(TbNodeHistoryMapper.class);
        subsystemHistoryMapper = mock(SubsystemHistoryMapper.class);
        objectMapper = new ObjectMapper();

        service = new SubsystemDiscoveryService(
                graphExtractionService,
                weightedGraphBuilder,
                leidenAlgorithmUtil,
                clusterAggregationUtil,
                subsystemLabelService,
                llmSubsystemDiscoveryService,
                tbNodeHistoryMapper,
                subsystemHistoryMapper,
                objectMapper
        );
    }

    @Test
    public void testGenerateSummaryScenario1_ExistingDiscovery_ExistingSummary() throws Exception {
        // Setup existing master run
        SubsystemRunMaster master = new SubsystemRunMaster();
        master.setDiscoveryRunId(100L);
        master.setAnalysisTime("2026-06-17 14:00:00");
        master.setRuns(10);
        master.setConsensusThreshold(0.7);
        master.setResolution(1.0);
        master.setTotalSubsystems(2);
        master.setAvgStabilityScore(0.85);

        SubsystemPersistenceDto persistenceDto = new SubsystemPersistenceDto(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );
        master.setDiscoveryResult(ZipUtils.zipString(objectMapper.writeValueAsString(persistenceDto), "result.json"));

        // Mock database calls
        when(subsystemHistoryMapper.selectMasterById(100L)).thenReturn(master);
        when(tbNodeHistoryMapper.selectApplicationMetadata(master.getAnalysisTime()))
                .thenReturn(new ApplicationMetadata(1L, "TEST_APP"));
        when(subsystemHistoryMapper.selectLlmSummaryByConfig(100L, "gemini-flash", "MEDIUM_DETAILED"))
                .thenReturn("Existing Summary content");

        // Invoke method
        String response = service.generateSummary(100L, "gemini-flash", SummaryType.MEDIUM_DETAILED);

        // Verify results
        assertNotNull(response);
        assertEquals("Existing Summary content", response);

        // Verify LLM generate and DB insert were NOT called
        verify(llmSubsystemDiscoveryService, never()).summarise(any(), any(), any());
        verify(subsystemHistoryMapper, never()).insertLlmSummary(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    public void testGenerateSummaryScenario2_ExistingDiscovery_NewSummary() throws Exception {
        // Setup existing master run
        SubsystemRunMaster master = new SubsystemRunMaster();
        master.setDiscoveryRunId(200L);
        master.setAnalysisTime("2026-06-17 15:00:00");
        master.setRuns(20);
        master.setConsensusThreshold(0.8);
        master.setResolution(1.2);
        master.setTotalSubsystems(4);
        master.setAvgStabilityScore(0.92);

        SubsystemPersistenceDto persistenceDto = new SubsystemPersistenceDto(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );
        master.setDiscoveryResult(ZipUtils.zipString(objectMapper.writeValueAsString(persistenceDto), "result.json"));

        // Mock database calls
        when(subsystemHistoryMapper.selectMasterById(200L)).thenReturn(master);
        when(tbNodeHistoryMapper.selectApplicationMetadata(master.getAnalysisTime()))
                .thenReturn(new ApplicationMetadata(1L, "TEST_APP"));
        when(subsystemHistoryMapper.selectLlmSummaryByConfig(200L, "gpt-4", "COMPLETE_DETAILED"))
                .thenReturn(null);
        when(llmSubsystemDiscoveryService.summarise(any(), any(), any()))
                .thenReturn("Newly generated LLM summary text");

        // Invoke method
        String response = service.generateSummary(200L, "gpt-4", SummaryType.COMPLETE_DETAILED);

        // Verify results
        assertNotNull(response);
        assertEquals("Newly generated LLM summary text", response);

        // Verify LLM summarise was invoked with correct arguments
        verify(llmSubsystemDiscoveryService, times(1))
                .summarise(any(), eq(SummaryType.COMPLETE_DETAILED), eq("gpt-4"));

        // Verify new summary was inserted into DB
        verify(subsystemHistoryMapper, times(1))
                .insertLlmSummary(200L, "gpt-4", "COMPLETE_DETAILED", "Newly generated LLM summary text");
    }

    @Test
    public void testDiscoverWithLlm_CachedRun() throws Exception {
        // Setup configuration parameters
        SubsystemAlgorithmParams params = new SubsystemAlgorithmParams(
                null, 10, 0.7, 1.0, "gemini-flash", SummaryType.MEDIUM_DETAILED, null
        );

        ApplicationMetadata meta = new ApplicationMetadata(1L, "TEST_APP");
        when(tbNodeHistoryMapper.selectApplicationMetadata("2026-06-17 14:00:00"))
                .thenReturn(meta);

        // Setup existing master run
        SubsystemRunMaster master = new SubsystemRunMaster();
        master.setDiscoveryRunId(300L);
        master.setAnalysisTime("2026-06-17 14:00:00");
        master.setRuns(10);
        master.setConsensusThreshold(0.7);
        master.setResolution(1.0);
        master.setTotalSubsystems(1);
        master.setAvgStabilityScore(0.9);

        SubsystemPersistenceDto persistenceDto = new SubsystemPersistenceDto(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );
        master.setDiscoveryResult(ZipUtils.zipString(objectMapper.writeValueAsString(persistenceDto), "result.json"));

        when(subsystemHistoryMapper.selectMasterByConfig("2026-06-17 14:00:00", 10, 0.7, 1.0))
                .thenReturn(master);

        // Mock LLM summary calls
        when(subsystemHistoryMapper.selectLlmSummaryByConfig(300L, "gemini-flash", "MEDIUM_DETAILED"))
                .thenReturn("Cached LLM summary explanation");

        // Invoke method
        LlmDiscoveryResponse response = service.discoverWithLlm("2026-06-17 14:00:00", params);

        // Verify results
        assertNotNull(response);
        assertEquals(300L, response.discoveryRunId());
        assertEquals("Cached LLM summary explanation", response.llmArchitecturalSummary());
        assertEquals(1L, response.applicationId());
        assertEquals("TEST_APP", response.applicationKey());
    }
}

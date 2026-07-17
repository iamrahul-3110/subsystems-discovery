package com.example.subsystemdiscovery.boundaryanalysis.service;

import com.example.subsystemdiscovery.algorithm.model.GraphNode;
import com.example.subsystemdiscovery.algorithm.model.RelationType;
import com.example.subsystemdiscovery.algorithm.model.WeightedEdge;
import com.example.subsystemdiscovery.algorithm.model.WeightedGraph;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryNodeDto;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryNodeRequest;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryNodeResponse;
import com.example.subsystemdiscovery.boundaryanalysis.dto.ConnectedSubsystemDto;
import com.example.subsystemdiscovery.boundaryanalysis.dto.SubsystemInteractionDto;
import com.example.subsystemdiscovery.discovery.SubsystemDiscoveryService;
import com.example.subsystemdiscovery.discovery.dto.NodeAssignmentDto;
import com.example.subsystemdiscovery.discovery.dto.SubsystemDiscoveryResponse;
import com.example.subsystemdiscovery.discovery.dto.SubsystemDto;
import com.example.subsystemdiscovery.repository.SubsystemHistoryMapper;
import com.example.subsystemdiscovery.repository.entity.SubsystemRunMaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BoundaryNodeServiceTest {

    private SubsystemDiscoveryService discoveryService;
    private SubsystemHistoryMapper historyMapper;
    private BoundaryAnalysisService analysisService;
    private BoundaryNodeResponseMapper responseMapper;
    private BoundaryNodeServiceImpl boundaryNodeService;

    @BeforeEach
    public void setUp() {
        discoveryService = mock(SubsystemDiscoveryService.class);
        historyMapper = mock(SubsystemHistoryMapper.class);
        analysisService = new BoundaryAnalysisServiceImpl();
        responseMapper = new BoundaryNodeResponseMapper();
        boundaryNodeService = new BoundaryNodeServiceImpl(
                discoveryService, historyMapper, analysisService, responseMapper);
    }

    @Test
    public void testDetectBoundaryNodes_HappyPath() {
        // Setup Run Master
        SubsystemRunMaster master = new SubsystemRunMaster();
        master.setDiscoveryRunId(1L);
        master.setAnalysisTime("2026-07-10 12:00:00");
        master.setRuns(5);
        master.setConsensusThreshold(0.8);
        master.setResolution(1.0);
        when(historyMapper.selectMasterById(1L)).thenReturn(master);

        // Setup Discovered Subsystems and Node Assignments
        SubsystemDto subA = new SubsystemDto("sub-A", "Checkout Operations", "Desc A", 0.95, 2, 2, 0.8, List.of(), List.of(), List.of(), Map.of());
        SubsystemDto subB = new SubsystemDto("sub-B", "Payments Operations", "Desc B", 0.90, 2, 2, 0.75, List.of(), List.of(), List.of(), Map.of());
        SubsystemDto subC = new SubsystemDto("sub-C", "Delivery Operations", "Desc C", 0.85, 1, 1, 0.70, List.of(), List.of(), List.of(), Map.of());

        List<NodeAssignmentDto> nodeAssignments = List.of(
                new NodeAssignmentDto(100L, "sub-A", 1.0),
                new NodeAssignmentDto(101L, "sub-A", 1.0),
                new NodeAssignmentDto(200L, "sub-B", 1.0),
                new NodeAssignmentDto(300L, "sub-C", 1.0)
        );

        SubsystemDiscoveryResponse discoveryResponse = new SubsystemDiscoveryResponse(
                1L, 10L, "APP", null, null,
                List.of(subA, subB, subC), List.of(), List.of(), nodeAssignments
        );
        when(discoveryService.discover(anyString(), any())).thenReturn(discoveryResponse);

        // Setup Graph structure:
        // Node 100 (sub-A) -> Node 200 (sub-B) [Cross-subsystem A -> B]
        // Node 100 (sub-A) -> Node 300 (sub-C) [Cross-subsystem A -> C]
        // Node 101 (sub-A) -> Node 200 (sub-B) [Cross-subsystem A -> B]
        WeightedGraph graph = new WeightedGraph();
        graph.getNodes().add(new GraphNode(100L, "k-100", "com.checkout.Engine", "com.checkout.Engine", "CLASS", "com.checkout"));
        graph.getNodes().add(new GraphNode(101L, "k-101", "com.checkout.Helper", "com.checkout.Helper", "CLASS", "com.checkout"));
        graph.getNodes().add(new GraphNode(200L, "k-200", "com.payments.Gateway", "com.payments.Gateway", "CLASS", "com.payments"));
        graph.getNodes().add(new GraphNode(300L, "k-300", "com.delivery.Tracker", "com.delivery.Tracker", "CLASS", "com.delivery"));

        WeightedEdge edge1 = new WeightedEdge(100L, 200L);
        edge1.addOccurrence(1.0, RelationType.CLASS_DEPENDENCY);
        WeightedEdge edge2 = new WeightedEdge(100L, 300L);
        edge2.addOccurrence(1.0, RelationType.CLASS_DEPENDENCY);
        WeightedEdge edge3 = new WeightedEdge(101L, 200L);
        edge3.addOccurrence(1.0, RelationType.CLASS_DEPENDENCY);

        graph.getEdges().addAll(List.of(edge1, edge2, edge3));
        when(discoveryService.buildWeightedGraph(anyString(), any())).thenReturn(graph);

        // 1. Run global detection request
        BoundaryNodeRequest request = new BoundaryNodeRequest(1L, 20, "MOST_CONNECTED", "ALL", null, null);
        BoundaryNodeResponse response = boundaryNodeService.detectBoundaryNodes(request);

        // Verify Overview Stats
        assertNotNull(response);
        assertEquals(1L, response.discoveryRunId());
        assertEquals(4, response.overview().totalBoundaryNodes()); // Node 100, 101, 200, 300
        assertEquals(1.25, response.overview().avgCrossSubsystemConnections(), 0.05);
        assertEquals(1.0, response.overview().highestBoundaryScore());
        assertEquals(4, response.totalResults());

        // Verify top nodes sorting and properties
        assertFalse(response.topBoundaryNodes().isEmpty());
        BoundaryNodeDto topNode = response.topBoundaryNodes().get(0);
        assertEquals("com.checkout.Engine", topNode.nodeName());
        assertEquals("Checkout Operations", topNode.owningSubsystem());
        assertEquals(2, topNode.connectedSubsystems().size()); // payments, delivery
        assertEquals(2, topNode.totalCrossEdges());

        // Ensure connectedSubsystems is aggregated (no duplicate names, contains edges count)
        ConnectedSubsystemDto conn = topNode.connectedSubsystems().get(0);
        assertNotNull(conn.subsystem());
        assertTrue(conn.edges() >= 1);

        // Verify Subsystem Interactions
        // Expected directed pairs:
        // Checkout Operations -> Payments Operations (count 2)
        // Checkout Operations -> Delivery Operations (count 1)
        List<SubsystemInteractionDto> interactions = response.subsystemInteractions();
        assertEquals(2, interactions.size());
        assertEquals("Checkout Operations", interactions.get(0).fromSubsystem());
        assertEquals("Payments Operations", interactions.get(0).toSubsystem());
        assertEquals(2, interactions.get(0).interactionCount());

        assertEquals("Checkout Operations", interactions.get(1).fromSubsystem());
        assertEquals("Delivery Operations", interactions.get(1).toSubsystem());
        assertEquals(1, interactions.get(1).interactionCount());
    }

    @Test
    public void testDetectBoundaryNodes_DrillDown() {
        // Setup Run Master
        SubsystemRunMaster master = new SubsystemRunMaster();
        master.setDiscoveryRunId(1L);
        master.setAnalysisTime("2026-07-10 12:00:00");
        master.setRuns(5);
        master.setConsensusThreshold(0.8);
        master.setResolution(1.0);
        when(historyMapper.selectMasterById(1L)).thenReturn(master);

        // Setup Discovered Subsystems and Node Assignments
        SubsystemDto subA = new SubsystemDto("sub-A", "Checkout Operations", "Desc A", 0.95, 2, 2, 0.8, List.of(), List.of(), List.of(), Map.of());
        SubsystemDto subB = new SubsystemDto("sub-B", "Payments Operations", "Desc B", 0.90, 2, 2, 0.75, List.of(), List.of(), List.of(), Map.of());
        SubsystemDto subC = new SubsystemDto("sub-C", "Delivery Operations", "Desc C", 0.85, 1, 1, 0.70, List.of(), List.of(), List.of(), Map.of());

        List<NodeAssignmentDto> nodeAssignments = List.of(
                new NodeAssignmentDto(100L, "sub-A", 1.0),
                new NodeAssignmentDto(200L, "sub-B", 1.0),
                new NodeAssignmentDto(300L, "sub-C", 1.0)
        );

        SubsystemDiscoveryResponse discoveryResponse = new SubsystemDiscoveryResponse(
                1L, 10L, "APP", null, null,
                List.of(subA, subB, subC), List.of(), List.of(), nodeAssignments
        );
        when(discoveryService.discover(anyString(), any())).thenReturn(discoveryResponse);

        WeightedGraph graph = new WeightedGraph();
        graph.getNodes().add(new GraphNode(100L, "k-100", "com.checkout.Engine", "com.checkout.Engine", "CLASS", "com.checkout"));
        graph.getNodes().add(new GraphNode(200L, "k-200", "com.payments.Gateway", "com.payments.Gateway", "CLASS", "com.payments"));
        graph.getNodes().add(new GraphNode(300L, "k-300", "com.delivery.Tracker", "com.delivery.Tracker", "CLASS", "com.delivery"));

        // Connections:
        // 100 -> 200 (Checkout -> Payments)
        // 100 -> 300 (Checkout -> Delivery)
        WeightedEdge edge1 = new WeightedEdge(100L, 200L);
        edge1.addOccurrence(1.0, RelationType.CLASS_DEPENDENCY);
        WeightedEdge edge2 = new WeightedEdge(100L, 300L);
        edge2.addOccurrence(1.0, RelationType.CLASS_DEPENDENCY);

        graph.getEdges().addAll(List.of(edge1, edge2));
        when(discoveryService.buildWeightedGraph(anyString(), any())).thenReturn(graph);

        // Perform drill-down for Checkout Operations -> Delivery Operations
        BoundaryNodeRequest drillDownRequest = new BoundaryNodeRequest(
                1L, 20, "MOST_CONNECTED", "ALL",
                "Checkout Operations", "Delivery Operations"
        );

        BoundaryNodeResponse response = boundaryNodeService.detectBoundaryNodes(drillDownRequest);

        // Verify Selected Interaction DTO is populated
        assertNotNull(response.selectedInteraction());
        assertEquals("Checkout Operations", response.selectedInteraction().fromSubsystem());
        assertEquals("Delivery Operations", response.selectedInteraction().toSubsystem());
        assertEquals(1, response.selectedInteraction().interactionCount());
        assertEquals(2, response.totalResults());

        // Assert that sum(edgesForPair) == interactionCount
        int edgesForPairSum = 0;
        for (BoundaryNodeDto node : response.selectedInteraction().boundaryNodes()) {
            if (node.owningSubsystem().equals("Checkout Operations")) {
                edgesForPairSum += node.connectedSubsystems().stream()
                        .filter(c -> c.subsystem().equals("Delivery Operations"))
                        .mapToInt(ConnectedSubsystemDto::outgoingEdges)
                        .sum();
            }
        }
        assertEquals(response.selectedInteraction().interactionCount(), edgesForPairSum);

        // Boundary nodes under selectedInteraction should only be those participating in this pair
        // Participators: Node 100 (Checkout) and Node 300 (Delivery)
        // Node 200 (Payments) should NOT be in selectedInteraction boundaryNodes list because it is not connected to Delivery!
        List<BoundaryNodeDto> drillDownNodes = response.selectedInteraction().boundaryNodes();
        assertEquals(2, drillDownNodes.size());
        boolean hasPayments = drillDownNodes.stream().anyMatch(n -> n.owningSubsystem().equals("Payments Operations"));
        assertFalse(hasPayments);

        assertTrue(drillDownNodes.stream().anyMatch(n -> n.nodeName().equals("com.checkout.Engine")));
        assertTrue(drillDownNodes.stream().anyMatch(n -> n.nodeName().equals("com.delivery.Tracker")));
    }

    @Test
    public void testReconciliationCheck_SingleDirectionAndMutual() {
        // Setup Run Master
        SubsystemRunMaster master = new SubsystemRunMaster();
        master.setDiscoveryRunId(2L);
        master.setAnalysisTime("2026-07-10 12:00:00");
        master.setRuns(5);
        master.setConsensusThreshold(0.8);
        master.setResolution(1.0);
        when(historyMapper.selectMasterById(2L)).thenReturn(master);

        // Setup Discovered Subsystems
        SubsystemDto subA = new SubsystemDto("sub-A", "Checkout Operations", "Desc A", 0.95, 2, 2, 0.8, List.of(), List.of(), List.of(), Map.of());
        SubsystemDto subB = new SubsystemDto("sub-B", "Payments Operations", "Desc B", 0.90, 2, 2, 0.75, List.of(), List.of(), List.of(), Map.of());

        List<NodeAssignmentDto> nodeAssignments = List.of(
                new NodeAssignmentDto(100L, "sub-A", 1.0),
                new NodeAssignmentDto(200L, "sub-B", 1.0)
        );

        SubsystemDiscoveryResponse discoveryResponse = new SubsystemDiscoveryResponse(
                2L, 10L, "APP", null, null,
                List.of(subA, subB), List.of(), List.of(), nodeAssignments
        );
        when(discoveryService.discover(anyString(), any())).thenReturn(discoveryResponse);

        // Setup Graph:
        // A -> B (2 edges)
        // B -> A (1 edge) (Mutual)
        WeightedGraph graph = new WeightedGraph();
        graph.getNodes().add(new GraphNode(100L, "k-100", "com.checkout.Engine", "com.checkout.Engine", "CLASS", "com.checkout"));
        graph.getNodes().add(new GraphNode(200L, "k-200", "com.payments.Gateway", "com.payments.Gateway", "CLASS", "com.payments"));

        WeightedEdge edge1 = new WeightedEdge(100L, 200L);
        edge1.addOccurrence(2.0, RelationType.CLASS_DEPENDENCY); // forward
        edge1.addForwardOccurrence();
        edge1.addForwardOccurrence();
        
        edge1.addOccurrence(1.0, RelationType.CLASS_DEPENDENCY); // backward
        edge1.addBackwardOccurrence();

        graph.getEdges().add(edge1);
        when(discoveryService.buildWeightedGraph(anyString(), any())).thenReturn(graph);

        // Test Outgoing (Checkout Operations -> Payments Operations)
        BoundaryNodeRequest requestForward = new BoundaryNodeRequest(
                2L, 20, "MOST_CONNECTED", "ALL",
                "Checkout Operations", "Payments Operations"
        );
        BoundaryNodeResponse respForward = boundaryNodeService.detectBoundaryNodes(requestForward);
        
        assertNotNull(respForward.selectedInteraction());
        assertEquals(2, respForward.selectedInteraction().interactionCount()); // 2 edges forward
        
        // Reconciliation check: sum(outgoing edges of fromSubsystem nodes) == interactionCount
        int forwardEdgesSum = respForward.selectedInteraction().boundaryNodes().stream()
                .filter(n -> n.owningSubsystem().equals("Checkout Operations"))
                .mapToInt(BoundaryNodeDto::outgoingCrossEdges)
                .sum();
        assertEquals(respForward.selectedInteraction().interactionCount(), forwardEdgesSum);

        // Verify incomingCrossEdges on Checkout node is non-zero
        BoundaryNodeDto checkoutNode = respForward.selectedInteraction().boundaryNodes().stream()
                .filter(n -> n.nodeName().equals("com.checkout.Engine"))
                .findFirst()
                .orElseThrow();
        assertEquals(1, checkoutNode.incomingCrossEdges());
        assertEquals(2, checkoutNode.outgoingCrossEdges());

        // Test Incoming (Payments Operations -> Checkout Operations)
        BoundaryNodeRequest requestBackward = new BoundaryNodeRequest(
                2L, 20, "MOST_CONNECTED", "ALL",
                "Payments Operations", "Checkout Operations"
        );
        BoundaryNodeResponse respBackward = boundaryNodeService.detectBoundaryNodes(requestBackward);

        assertNotNull(respBackward.selectedInteraction());
        assertEquals(1, respBackward.selectedInteraction().interactionCount()); // 1 edge backward

        // Reconciliation check: sum(outgoing edges of fromSubsystem nodes) == interactionCount
        int backwardEdgesSum = respBackward.selectedInteraction().boundaryNodes().stream()
                .filter(n -> n.owningSubsystem().equals("Payments Operations"))
                .mapToInt(BoundaryNodeDto::outgoingCrossEdges)
                .sum();
        assertEquals(respBackward.selectedInteraction().interactionCount(), backwardEdgesSum);
    }

    @Test
    public void testDetectBoundaryNodes_ConsistencyFailure() {
        // Setup Run Master
        SubsystemRunMaster master = new SubsystemRunMaster();
        master.setDiscoveryRunId(3L);
        master.setAnalysisTime("2026-07-10 12:00:00");
        master.setRuns(5);
        master.setConsensusThreshold(0.8);
        master.setResolution(1.0);
        when(historyMapper.selectMasterById(3L)).thenReturn(master);

        // Setup Discovered Subsystems
        SubsystemDto subA = new SubsystemDto("sub-A", "Checkout Operations", "Desc A", 0.95, 2, 2, 0.8, List.of(), List.of(), List.of(), Map.of());
        SubsystemDto subB = new SubsystemDto("sub-B", "Payments Operations", "Desc B", 0.90, 2, 2, 0.75, List.of(), List.of(), List.of(), Map.of());

        List<NodeAssignmentDto> nodeAssignments = List.of(
                // Node 100 is assigned to an invalid subsystem ID not present in the subsystems list
                new NodeAssignmentDto(100L, "sub-invalid", 1.0),
                new NodeAssignmentDto(200L, "sub-B", 1.0)
        );

        SubsystemDiscoveryResponse discoveryResponse = new SubsystemDiscoveryResponse(
                3L, 10L, "APP", null, null,
                List.of(subA, subB), List.of(), List.of(), nodeAssignments
        );
        when(discoveryService.discover(anyString(), any())).thenReturn(discoveryResponse);

        WeightedGraph graph = new WeightedGraph();
        graph.getNodes().add(new GraphNode(100L, "k-100", "com.checkout.Engine", "com.checkout.Engine", "CLASS", "com.checkout"));
        graph.getNodes().add(new GraphNode(200L, "k-200", "com.payments.Gateway", "com.payments.Gateway", "CLASS", "com.payments"));

        WeightedEdge edge = new WeightedEdge(100L, 200L);
        edge.addOccurrence(1.0, RelationType.CLASS_DEPENDENCY);
        graph.getEdges().add(edge);

        when(discoveryService.buildWeightedGraph(anyString(), any())).thenReturn(graph);

        BoundaryNodeRequest request = new BoundaryNodeRequest(3L, 20, "MOST_CONNECTED", "ALL", null, null);
        
        // Assert that the service throws IllegalStateException due to inconsistent subsystem name
        assertThrows(IllegalStateException.class, () -> boundaryNodeService.detectBoundaryNodes(request));
    }

    @Test
    public void testSerialization() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        List<ConnectedSubsystemDto> connected = List.of(new ConnectedSubsystemDto("Payments", 3, 2, 1));
        BoundaryNodeDto node = new BoundaryNodeDto("com.test.Node", "CLASS", "Checkout", connected, 1, 2, 3, 0.8);
        String json = mapper.writeValueAsString(node);
        System.out.println("JSON_SERIALIZATION_TEST_OUTPUT: " + json);
        assertTrue(json.contains("\"incomingCrossEdges\":1"));
        assertTrue(json.contains("\"outgoingCrossEdges\":2"));
    }
}

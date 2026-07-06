package com.example.subsystemdiscovery.boundaryanalysis.mapper;

import com.example.subsystemdiscovery.algorithm.model.GraphNode;
import com.example.subsystemdiscovery.algorithm.model.RelationType;
import com.example.subsystemdiscovery.algorithm.model.WeightedEdge;
import com.example.subsystemdiscovery.algorithm.model.WeightedGraph;
import com.example.subsystemdiscovery.boundaryanalysis.config.BoundaryAnalysisProperties;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryNodeDto;
import com.example.subsystemdiscovery.discovery.dto.NodeAssignmentDto;
import com.example.subsystemdiscovery.discovery.dto.SubsystemDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundaryNodeMapperTest {

    @Test
    void detectsWeightedCrossSubsystemBoundaryNodes() {
        BoundaryNodeMapper mapper = new BoundaryNodeMapper(new BoundaryAnalysisProperties());

        GraphNode method = new GraphNode(1L, "METHOD:a", "OrderService.placeOrder",
                "com.app.order.OrderService.placeOrder", "METHOD", "com.app.order");
        GraphNode paymentClass = new GraphNode(2L, "CLASS:b", "PaymentClient",
                "com.app.payment.PaymentClient", "CLASS", "com.app.payment");
        GraphNode orderClass = new GraphNode(3L, "CLASS:c", "OrderRepository",
                "com.app.order.OrderRepository", "CLASS", "com.app.order");

        WeightedEdge cross = new WeightedEdge(1L, 2L);
        cross.addOccurrence(5.0, RelationType.METHOD_CALL);

        WeightedEdge internal = new WeightedEdge(1L, 3L);
        internal.addOccurrence(4.0, RelationType.CLASS_DEPENDENCY);

        WeightedGraph graph = new WeightedGraph(
                List.of(method, paymentClass, orderClass),
                List.of(cross, internal)
        );

        List<NodeAssignmentDto> assignments = List.of(
                new NodeAssignmentDto(1L, "cluster_order", 1.0),
                new NodeAssignmentDto(2L, "cluster_payment", 1.0),
                new NodeAssignmentDto(3L, "cluster_order", 1.0)
        );
        List<SubsystemDto> subsystems = List.of(
                new SubsystemDto("cluster_order", "Order", "", 1.0, 2, 1, 0.5,
                        List.of("com.app.order"), List.of(), List.of(), Map.of()),
                new SubsystemDto("cluster_payment", "Payment", "", 1.0, 1, 0, 0.0,
                        List.of("com.app.payment"), List.of(), List.of(), Map.of())
        );

        BoundaryMappingResult result = mapper.map(graph, assignments, subsystems, 10, 10);

        assertEquals(2, result.boundaryNodes().size());
        assertEquals(1, result.subsystemPairHotspots().size());

        BoundaryNodeDto methodBoundary = result.boundaryNodes().stream()
                .filter(node -> node.nodeId().equals(1L))
                .findFirst()
                .orElseThrow();

        assertEquals("METHOD", methodBoundary.nodeType());
        assertEquals(2, methodBoundary.totalDegree());
        assertEquals(1, methodBoundary.crossSubsystemEdges());
        assertEquals(9.0, methodBoundary.totalWeight());
        assertEquals(5.0, methodBoundary.crossSubsystemWeight());
        assertEquals(0.556, methodBoundary.boundaryScore());
        assertEquals("Payment", methodBoundary.crossSubsystemLinks().get(0).subsystemName());
        assertEquals("LOW", methodBoundary.crossSubsystemLinks().get(0).couplingStrength());

        assertFalse(result.topBoundaryNodes().isEmpty());
        assertTrue(result.summary().methodBoundaryCount() >= 1);
    }
}

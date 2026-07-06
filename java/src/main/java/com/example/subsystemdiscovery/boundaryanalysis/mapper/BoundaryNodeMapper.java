package com.example.subsystemdiscovery.boundaryanalysis.mapper;

import com.example.subsystemdiscovery.algorithm.model.GraphNode;
import com.example.subsystemdiscovery.algorithm.model.RelationType;
import com.example.subsystemdiscovery.algorithm.model.WeightedEdge;
import com.example.subsystemdiscovery.algorithm.model.WeightedGraph;
import com.example.subsystemdiscovery.boundaryanalysis.config.BoundaryAnalysisProperties;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryAnalysisStatsDto;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundaryNodeDto;
import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundarySubsystemPairDto;
import com.example.subsystemdiscovery.boundaryanalysis.dto.CrossSubsystemLinkDto;
import com.example.subsystemdiscovery.discovery.dto.NodeAssignmentDto;
import com.example.subsystemdiscovery.discovery.dto.SubsystemDto;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Computes boundary nodes from the weighted graph and Leiden node assignments.
 */
@Component
public class BoundaryNodeMapper {

    private final BoundaryAnalysisProperties properties;

    public BoundaryNodeMapper(BoundaryAnalysisProperties properties) {
        this.properties = properties;
    }

    public BoundaryMappingResult map(WeightedGraph graph,
                                     List<NodeAssignmentDto> nodeAssignments,
                                     List<SubsystemDto> subsystems,
                                     Integer topNodeLimit,
                                     Integer hotspotLimit) {
        List<GraphNode> nodes = graph == null || graph.getNodes() == null ? List.of() : graph.getNodes();
        List<WeightedEdge> edges = graph == null || graph.getEdges() == null ? List.of() : graph.getEdges();
        List<NodeAssignmentDto> assignments = nodeAssignments == null ? List.of() : nodeAssignments;
        List<SubsystemDto> subsystemList = subsystems == null ? List.of() : subsystems;

        Map<Long, String> nodeToSubsystem = assignments.stream()
                .filter(a -> a.nodeId() != null && StringUtils.hasText(a.subsystemId()))
                .collect(Collectors.toMap(
                        NodeAssignmentDto::nodeId,
                        NodeAssignmentDto::subsystemId,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Map<String, String> subsystemNames = subsystemList.stream()
                .filter(s -> StringUtils.hasText(s.id()))
                .collect(Collectors.toMap(
                        SubsystemDto::id,
                        s -> StringUtils.hasText(s.name()) ? s.name() : s.id(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Map<Long, GraphNode> nodeById = nodes.stream()
                .filter(n -> n.getId() != null)
                .collect(Collectors.toMap(
                        GraphNode::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Map<Long, NodeAccumulator> nodeAccumulators = new HashMap<>();
        Map<String, PairAccumulator> pairAccumulators = new HashMap<>();

        for (WeightedEdge edge : edges) {
            Long source = edge.getSource();
            Long target = edge.getTarget();
            if (source == null || target == null || source.equals(target)) {
                continue;
            }

            int occurrences = Math.max(1, edge.getOccurrenceCount());
            double weight = edge.getWeight() > 0.0 ? edge.getWeight() : occurrences;
            Map<String, Integer> relationSummary = relationSummary(edge);

            NodeAccumulator sourceAcc = nodeAccumulators.computeIfAbsent(source, NodeAccumulator::new);
            NodeAccumulator targetAcc = nodeAccumulators.computeIfAbsent(target, NodeAccumulator::new);
            sourceAcc.addTotal(occurrences, weight);
            targetAcc.addTotal(occurrences, weight);

            String sourceSubsystem = nodeToSubsystem.get(source);
            String targetSubsystem = nodeToSubsystem.get(target);
            if (!StringUtils.hasText(sourceSubsystem)
                    || !StringUtils.hasText(targetSubsystem)
                    || sourceSubsystem.equals(targetSubsystem)) {
                continue;
            }

            sourceAcc.addCross(targetSubsystem, occurrences, weight, relationSummary);
            targetAcc.addCross(sourceSubsystem, occurrences, weight, relationSummary);

            String left = sourceSubsystem.compareTo(targetSubsystem) <= 0 ? sourceSubsystem : targetSubsystem;
            String right = sourceSubsystem.compareTo(targetSubsystem) <= 0 ? targetSubsystem : sourceSubsystem;
            pairAccumulators.computeIfAbsent(left + "--" + right, ignored -> new PairAccumulator(left, right))
                    .add(source, target, occurrences, weight, relationSummary);
        }

        List<BoundaryNodeDto> boundaryNodes = buildBoundaryNodes(
                nodeAccumulators,
                nodeById,
                nodeToSubsystem,
                subsystemNames
        );

        Comparator<BoundaryNodeDto> nodeComparator = boundaryNodeComparator();
        boundaryNodes.sort(nodeComparator);

        Map<Long, BoundaryNodeDto> boundaryNodeById = boundaryNodes.stream()
                .collect(Collectors.toMap(BoundaryNodeDto::nodeId, Function.identity(), (left, right) -> left));

        int resolvedTopNodeLimit = resolveLimit(topNodeLimit, properties.getDefaultTopNodeLimit());
        int resolvedHotspotLimit = resolveLimit(hotspotLimit, properties.getDefaultHotspotLimit());

        List<BoundaryNodeDto> topBoundaryNodes = boundaryNodes.stream()
                .limit(resolvedTopNodeLimit)
                .toList();

        List<BoundarySubsystemPairDto> pairHotspots = pairAccumulators.values().stream()
                .map(pair -> toPairDto(pair, subsystemNames, boundaryNodeById, nodeComparator))
                .sorted(Comparator
                        .comparingDouble(BoundarySubsystemPairDto::crossEdgeWeight).reversed()
                        .thenComparing(Comparator.comparingInt(BoundarySubsystemPairDto::crossEdgeCount).reversed())
                        .thenComparing(Comparator.comparingInt(BoundarySubsystemPairDto::boundaryNodeCount).reversed()))
                .limit(resolvedHotspotLimit)
                .toList();

        BoundaryAnalysisStatsDto stats = buildStats(boundaryNodes, pairAccumulators.size());

        return new BoundaryMappingResult(boundaryNodes, topBoundaryNodes, pairHotspots, stats);
    }

    private List<BoundaryNodeDto> buildBoundaryNodes(Map<Long, NodeAccumulator> nodeAccumulators,
                                                     Map<Long, GraphNode> nodeById,
                                                     Map<Long, String> nodeToSubsystem,
                                                     Map<String, String> subsystemNames) {
        List<BoundaryNodeDto> result = new ArrayList<>();

        for (NodeAccumulator accumulator : nodeAccumulators.values()) {
            if (accumulator.foreignSubsystems.isEmpty()) {
                continue;
            }

            int crossEdges = accumulator.crossEdgeCount();
            double crossWeight = accumulator.crossWeight();
            double boundaryScore = accumulator.totalWeight > 0.0
                    ? crossWeight / accumulator.totalWeight
                    : 0.0;

            if (crossEdges < properties.getMinCrossEdges()
                    || crossWeight < properties.getMinCrossWeight()
                    || boundaryScore < properties.getMinBoundaryScore()) {
                continue;
            }

            GraphNode node = nodeById.get(accumulator.nodeId);
            String nodeType = node == null ? "UNKNOWN" : normalizeType(node.getType());
            int foreignSubsystemCount = accumulator.foreignSubsystems.size();
            double importanceScore = importanceScore(nodeType, boundaryScore, crossWeight, foreignSubsystemCount);
            String riskLevel = riskLevel(importanceScore, boundaryScore, crossEdges, crossWeight, foreignSubsystemCount);
            String boundaryRole = boundaryRole(node, nodeType);

            String homeSubsystemId = nodeToSubsystem.get(accumulator.nodeId);
            List<CrossSubsystemLinkDto> links = accumulator.foreignSubsystems.entrySet().stream()
                    .map(entry -> toCrossSubsystemLink(entry.getKey(), entry.getValue(), subsystemNames))
                    .sorted(Comparator
                            .comparingDouble(CrossSubsystemLinkDto::crossEdgeWeight).reversed()
                            .thenComparing(Comparator.comparingInt(CrossSubsystemLinkDto::crossEdgeCount).reversed()))
                    .toList();

            result.add(new BoundaryNodeDto(
                    accumulator.nodeId,
                    node != null ? node.getName() : "node-" + accumulator.nodeId,
                    node != null ? node.getQualifiedName() : null,
                    nodeType,
                    node != null ? node.getPackageName() : null,
                    homeSubsystemId,
                    subsystemNames.getOrDefault(homeSubsystemId, homeSubsystemId),
                    accumulator.totalDegree,
                    crossEdges,
                    round(accumulator.totalWeight),
                    round(crossWeight),
                    foreignSubsystemCount,
                    round(boundaryScore),
                    round(importanceScore),
                    riskLevel,
                    boundaryRole,
                    recommendedAction(boundaryRole, riskLevel),
                    links
            ));
        }

        return result;
    }

    private CrossSubsystemLinkDto toCrossSubsystemLink(String subsystemId,
                                                       ForeignSubsystemAccumulator accumulator,
                                                       Map<String, String> subsystemNames) {
        return new CrossSubsystemLinkDto(
                subsystemId,
                subsystemNames.getOrDefault(subsystemId, subsystemId),
                accumulator.edgeCount,
                round(accumulator.edgeWeight),
                couplingStrength(accumulator.edgeWeight, accumulator.edgeCount),
                sortedSummary(accumulator.relationSummary)
        );
    }

    private BoundarySubsystemPairDto toPairDto(PairAccumulator pair,
                                               Map<String, String> subsystemNames,
                                               Map<Long, BoundaryNodeDto> boundaryNodeById,
                                               Comparator<BoundaryNodeDto> nodeComparator) {
        List<String> topNodes = pair.boundaryNodeIds.stream()
                .map(boundaryNodeById::get)
                .filter(Objects::nonNull)
                .sorted(nodeComparator)
                .limit(properties.getPairTopNodeLimit())
                .map(BoundaryNodeDto::name)
                .filter(StringUtils::hasText)
                .toList();

        int boundaryNodeCount = (int) pair.boundaryNodeIds.stream()
                .filter(boundaryNodeById::containsKey)
                .count();

        return new BoundarySubsystemPairDto(
                pair.sourceSubsystemId,
                subsystemNames.getOrDefault(pair.sourceSubsystemId, pair.sourceSubsystemId),
                pair.targetSubsystemId,
                subsystemNames.getOrDefault(pair.targetSubsystemId, pair.targetSubsystemId),
                boundaryNodeCount,
                pair.edgeCount,
                round(pair.edgeWeight),
                couplingStrength(pair.edgeWeight, pair.edgeCount),
                sortedSummary(pair.relationSummary),
                topNodes
        );
    }

    private BoundaryAnalysisStatsDto buildStats(List<BoundaryNodeDto> boundaryNodes, int subsystemPairCount) {
        int methodCount = countType(boundaryNodes, "METHOD");
        int classCount = countType(boundaryNodes, "CLASS");
        int packageCount = countType(boundaryNodes, "PACKAGE");
        int highRisk = countRisk(boundaryNodes, "HIGH");
        int mediumRisk = countRisk(boundaryNodes, "MEDIUM");
        int lowRisk = countRisk(boundaryNodes, "LOW");
        int apiCandidates = (int) boundaryNodes.stream()
                .filter(node -> node.boundaryRole() != null && node.boundaryRole().contains("API"))
                .count();

        double highestBoundaryScore = boundaryNodes.stream()
                .mapToDouble(BoundaryNodeDto::boundaryScore)
                .max()
                .orElse(0.0);
        double averageBoundaryScore = boundaryNodes.stream()
                .mapToDouble(BoundaryNodeDto::boundaryScore)
                .average()
                .orElse(0.0);
        double highestImportanceScore = boundaryNodes.stream()
                .mapToDouble(BoundaryNodeDto::importanceScore)
                .max()
                .orElse(0.0);
        double averageImportanceScore = boundaryNodes.stream()
                .mapToDouble(BoundaryNodeDto::importanceScore)
                .average()
                .orElse(0.0);

        return new BoundaryAnalysisStatsDto(
                methodCount,
                classCount,
                packageCount,
                highRisk,
                mediumRisk,
                lowRisk,
                apiCandidates,
                subsystemPairCount,
                round(highestBoundaryScore),
                round(averageBoundaryScore),
                round(highestImportanceScore),
                round(averageImportanceScore),
                topCounts(boundaryNodes, BoundaryNodeDto::homeSubsystemName),
                topCounts(boundaryNodes, BoundaryNodeDto::packageName)
        );
    }

    private Map<String, Integer> relationSummary(WeightedEdge edge) {
        Map<RelationType, Integer> relationTypes = edge.getRelationTypes() == null
                ? Map.of()
                : edge.getRelationTypes();
        if (relationTypes.isEmpty()) {
            return Map.of(RelationType.UNKNOWN.name(), Math.max(1, edge.getOccurrenceCount()));
        }
        Map<String, Integer> result = new HashMap<>();
        relationTypes.forEach((type, count) ->
                result.merge((type == null ? RelationType.UNKNOWN : type).name(), Math.max(1, count), Integer::sum));
        return result;
    }

    private double importanceScore(String nodeType,
                                   double boundaryScore,
                                   double crossWeight,
                                   int foreignSubsystemCount) {
        double weightSignal = Math.min(1.0, crossWeight / properties.getHighCouplingWeightThreshold());
        double fanoutSignal = Math.min(1.0, foreignSubsystemCount / 3.0);
        double typeMultiplier = switch (nodeType) {
            case "METHOD" -> 1.12;
            case "CLASS" -> 1.0;
            case "PACKAGE" -> 0.72;
            default -> 0.85;
        };
        double score = ((boundaryScore * 0.45) + (weightSignal * 0.35) + (fanoutSignal * 0.20))
                * typeMultiplier;
        return Math.min(1.0, score);
    }

    private String riskLevel(double importanceScore,
                             double boundaryScore,
                             int crossEdges,
                             double crossWeight,
                             int foreignSubsystemCount) {
        if (importanceScore >= properties.getHighRiskImportanceScore()
                || crossWeight >= properties.getHighCouplingWeightThreshold()
                || crossEdges >= properties.getHighCouplingEdgeThreshold()
                || (foreignSubsystemCount >= 3 && boundaryScore >= 0.40)) {
            return "HIGH";
        }
        if (importanceScore >= properties.getMediumRiskImportanceScore()
                || crossWeight >= properties.getMediumCouplingWeightThreshold()
                || crossEdges >= properties.getMediumCouplingEdgeThreshold()) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String boundaryRole(GraphNode node, String nodeType) {
        String name = node == null || node.getName() == null
                ? ""
                : node.getName().toLowerCase(Locale.ROOT);
        String qualifiedName = node == null || node.getQualifiedName() == null
                ? ""
                : node.getQualifiedName().toLowerCase(Locale.ROOT);
        String combined = name + " " + qualifiedName;

        if (combined.contains("controller") || combined.contains("endpoint")
                || combined.contains("resource") || combined.contains("gateway")
                || combined.contains("client") || combined.contains("api")) {
            return "API_BOUNDARY_CANDIDATE";
        }
        if ("METHOD".equals(nodeType)) {
            return "METHOD_HANDOFF";
        }
        if ("CLASS".equals(nodeType) && (combined.contains("service")
                || combined.contains("facade") || combined.contains("workflow")
                || combined.contains("orchestrator"))) {
            return "SERVICE_HANDOFF";
        }
        if ("PACKAGE".equals(nodeType)) {
            return "PACKAGE_BRIDGE";
        }
        if ("CLASS".equals(nodeType)) {
            return "CLASS_BRIDGE";
        }
        return "BOUNDARY_NODE";
    }

    private String recommendedAction(String boundaryRole, String riskLevel) {
        if ("API_BOUNDARY_CANDIDATE".equals(boundaryRole)) {
            return "Review as an explicit API contract and trust-boundary entry point.";
        }
        if ("SERVICE_HANDOFF".equals(boundaryRole)) {
            return "Consider a facade, anti-corruption layer, or event boundary to reduce direct subsystem coupling.";
        }
        if ("METHOD_HANDOFF".equals(boundaryRole)) {
            return "Check method cohesion and move cross-domain orchestration behind a stable service API.";
        }
        if ("PACKAGE_BRIDGE".equals(boundaryRole)) {
            return "Treat as a package-level signal and inspect contained classes before extracting services.";
        }
        if ("HIGH".equals(riskLevel)) {
            return "Prioritize for architecture and security review because it carries high cross-subsystem traffic.";
        }
        return "Document the dependency and monitor it during subsystem or microservice extraction.";
    }

    private String couplingStrength(double weight, int edgeCount) {
        if (weight >= properties.getHighCouplingWeightThreshold()
                || edgeCount >= properties.getHighCouplingEdgeThreshold()) {
            return "HIGH";
        }
        if (weight >= properties.getMediumCouplingWeightThreshold()
                || edgeCount >= properties.getMediumCouplingEdgeThreshold()) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private Comparator<BoundaryNodeDto> boundaryNodeComparator() {
        return Comparator
                .comparingDouble(BoundaryNodeDto::importanceScore).reversed()
                .thenComparing(Comparator.comparingDouble(BoundaryNodeDto::boundaryScore).reversed())
                .thenComparing(Comparator.comparingInt(BoundaryNodeDto::crossSubsystemEdges).reversed())
                .thenComparing(BoundaryNodeDto::name, Comparator.nullsLast(String::compareTo));
    }

    private int countType(List<BoundaryNodeDto> nodes, String type) {
        return (int) nodes.stream().filter(node -> type.equals(node.nodeType())).count();
    }

    private int countRisk(List<BoundaryNodeDto> nodes, String risk) {
        return (int) nodes.stream().filter(node -> risk.equals(node.riskLevel())).count();
    }

    private Map<String, Integer> topCounts(List<BoundaryNodeDto> nodes,
                                           Function<BoundaryNodeDto, String> classifier) {
        return nodes.stream()
                .map(classifier)
                .filter(StringUtils::hasText)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.summingInt(ignored -> 1)))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(properties.getSummaryMapLimit())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Map<String, Integer> sortedSummary(Map<String, Integer> input) {
        return input.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private String normalizeType(String type) {
        if (!StringUtils.hasText(type)) {
            return "UNKNOWN";
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if ("URI".equals(normalized) || "HTTP_API".equals(normalized)) {
            return "CLASS";
        }
        return normalized;
    }

    private int resolveLimit(Integer requested, int defaultValue) {
        if (requested == null || requested <= 0) {
            return defaultValue;
        }
        return Math.min(requested, properties.getMaxTopLimit());
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static class NodeAccumulator {
        private final Long nodeId;
        private int totalDegree;
        private double totalWeight;
        private final Map<String, ForeignSubsystemAccumulator> foreignSubsystems = new HashMap<>();

        private NodeAccumulator(Long nodeId) {
            this.nodeId = nodeId;
        }

        private void addTotal(int occurrences, double weight) {
            totalDegree += occurrences;
            totalWeight += weight;
        }

        private void addCross(String foreignSubsystemId,
                              int occurrences,
                              double weight,
                              Map<String, Integer> relationSummary) {
            foreignSubsystems.computeIfAbsent(foreignSubsystemId, ignored -> new ForeignSubsystemAccumulator())
                    .add(occurrences, weight, relationSummary);
        }

        private int crossEdgeCount() {
            return foreignSubsystems.values().stream().mapToInt(a -> a.edgeCount).sum();
        }

        private double crossWeight() {
            return foreignSubsystems.values().stream().mapToDouble(a -> a.edgeWeight).sum();
        }
    }

    private static class ForeignSubsystemAccumulator {
        private int edgeCount;
        private double edgeWeight;
        private final Map<String, Integer> relationSummary = new HashMap<>();

        private void add(int occurrences, double weight, Map<String, Integer> relations) {
            edgeCount += occurrences;
            edgeWeight += weight;
            relations.forEach((key, value) -> relationSummary.merge(key, value, Integer::sum));
        }
    }

    private static class PairAccumulator {
        private final String sourceSubsystemId;
        private final String targetSubsystemId;
        private int edgeCount;
        private double edgeWeight;
        private final Set<Long> boundaryNodeIds = new HashSet<>();
        private final Map<String, Integer> relationSummary = new HashMap<>();

        private PairAccumulator(String sourceSubsystemId, String targetSubsystemId) {
            this.sourceSubsystemId = sourceSubsystemId;
            this.targetSubsystemId = targetSubsystemId;
        }

        private void add(Long sourceNodeId,
                         Long targetNodeId,
                         int occurrences,
                         double weight,
                         Map<String, Integer> relations) {
            edgeCount += occurrences;
            edgeWeight += weight;
            boundaryNodeIds.add(sourceNodeId);
            boundaryNodeIds.add(targetNodeId);
            relations.forEach((key, value) -> relationSummary.merge(key, value, Integer::sum));
        }
    }
}

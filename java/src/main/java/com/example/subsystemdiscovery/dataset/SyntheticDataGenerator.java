package com.example.subsystemdiscovery.dataset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

@Service
public class SyntheticDataGenerator {

    private static final Logger log = LoggerFactory.getLogger(SyntheticDataGenerator.class);
    private static final DateTimeFormatter SNAPSHOT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final int BATCH_SIZE = 2_000;

    private final JdbcTemplate jdbcTemplate;

    public SyntheticDataGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public GeneratedDataset generateDataset(DomainTemplate template, int nodeCount) {
        validateNodeCount(nodeCount);
        log.info("Starting synthetic dataset generation for template={} with nodeCount={}", template, nodeCount);

        long applicationId = 1_000L + template.ordinal() + 1;
        String applicationKey = template.name().toLowerCase(Locale.ROOT) + "-graph-intelligence";
        String applicationName = displayName(template) + " Graph Intelligence POC";
        String analysisTime = LocalDateTime.now().format(SNAPSHOT_FORMAT);
        Random random = new Random(42L + template.ordinal() * 10_000L + nodeCount);

        upsertApplication(applicationId, applicationName, applicationKey);
        upsertSnapshot(applicationId, analysisTime);

        List<String> domains = domainsFor(template);
        GenerationResult result = generateNodes(applicationId, analysisTime, template, domains, nodeCount);
        
        insertClassAndPackageNodes(applicationId, analysisTime, nodeCount, result);

        int relationCount = generateRelations(applicationId, analysisTime, result, nodeCount, random);

        log.info("Successfully generated synthetic dataset: appId={}, nodeCount={}, relationCount={}",
                applicationId, nodeCount, relationCount);

        return new GeneratedDataset(
                applicationId,
                applicationKey,
                applicationName,
                analysisTime,
                nodeCount,
                relationCount,
                template.name()
        );
    }

    private void validateNodeCount(int nodeCount) {
        if (nodeCount != 100 && nodeCount != 1_000 && nodeCount != 10_000 && nodeCount != 50_000) {
            throw new IllegalArgumentException("nodeCount must be one of 100, 1000, 10000, 50000");
        }
    }

    private void upsertApplication(long applicationId, String applicationName, String applicationKey) {
        log.info("Upserting application master: ID={}, Key={}, Name={}", applicationId, applicationKey, applicationName);
        jdbcTemplate.update("""
                MERGE INTO tb_application_master (application_id, application_name, application_key)
                KEY(application_id)
                VALUES (?, ?, ?)
                """, applicationId, applicationName, applicationKey);
    }

    private void upsertSnapshot(long applicationId, String analysisTime) {
        log.info("Upserting snapshot: ID={}, analysisTime={}", applicationId, analysisTime);
        jdbcTemplate.update("""
                MERGE INTO tb_node_history_master (application_id, analysis_time, created_at)
                KEY(application_id, analysis_time)
                VALUES (?, CAST(? AS TIMESTAMP), CURRENT_TIMESTAMP)
                """, applicationId, analysisTime);
    }

    private GenerationResult generateNodes(long applicationId,
                                           String analysisTime,
                                           DomainTemplate template,
                                           List<String> domains,
                                           int nodeCount) {
        log.info("Generating {} nodes across {} business domains...", nodeCount, domains.size());
        String nodeSql = """
                INSERT INTO tb_node_history (
                    application_id, analysis_time, node_id, node_name, node_type, use_yn
                ) VALUES (?, CAST(? AS TIMESTAMP), ?, ?, ?, TRUE)
                """;
        String detailSql = """
                INSERT INTO tb_node_detail_history (
                    application_id, analysis_time, node_id, split_node_level, split_node_name, split_node_type
                ) VALUES (?, CAST(? AS TIMESTAMP), ?, ?, ?, ?)
                """;

        List<Object[]> nodeBatch = new ArrayList<>(BATCH_SIZE);
        List<Object[]> detailBatch = new ArrayList<>(BATCH_SIZE * 3);
        List<DomainRange> ranges = new ArrayList<>();

        java.util.Set<String> classSet = new java.util.LinkedHashSet<>();
        java.util.Set<String> packageSet = new java.util.LinkedHashSet<>();
        List<MethodMeta> methods = new ArrayList<>();

        int baseCount = nodeCount / domains.size();
        int remainder = nodeCount % domains.size();
        long nextNodeId = 1L;

        for (int domainIndex = 0; domainIndex < domains.size(); domainIndex++) {
            String domain = domains.get(domainIndex);
            int domainNodeCount = baseCount + (domainIndex < remainder ? 1 : 0);
            long start = nextNodeId;
            long end = start + domainNodeCount - 1;
            DomainRange range = new DomainRange(domain, toSlug(domain), start, end);
            ranges.add(range);

            for (long nodeId = start; nodeId <= end; nodeId++) {
                int localIndex = (int) (nodeId - start);
                String packageName = packageName(template, range.slug(), localIndex);
                String className = toPascal(domain) + componentSuffix(localIndex) + localIndex;
                String methodName = methodPrefix(localIndex) + toPascal(domain) + "Flow" + localIndex;
                String nodeName = packageName + "." + className + "." + methodName + "()";

                classSet.add(packageName + "." + className);
                packageSet.add(packageName);
                methods.add(new MethodMeta(nodeId, packageName + "." + className, packageName));

                nodeBatch.add(new Object[]{
                        applicationId, analysisTime, nodeId, nodeName, "METHOD"
                });
                detailBatch.add(new Object[]{
                        applicationId, analysisTime, nodeId, 0, methodName + "()", "METHOD"
                });
                detailBatch.add(new Object[]{
                        applicationId, analysisTime, nodeId, 1, className, "CLASS"
                });
                detailBatch.add(new Object[]{
                        applicationId, analysisTime, nodeId, 2, packageName, "PACKAGE"
                });

                if (nodeBatch.size() >= BATCH_SIZE) {
                    log.debug("Flushing node/detail batch (size={})", nodeBatch.size());
                    flush(nodeSql, nodeBatch);
                    flush(detailSql, detailBatch);
                }
            }
            nextNodeId = end + 1;
        }

        if (!nodeBatch.isEmpty()) {
            log.debug("Flushing final node/detail batch (size={})", nodeBatch.size());
        }
        flush(nodeSql, nodeBatch);
        flush(detailSql, detailBatch);
        log.info("Node and detail records successfully generated and saved.");
        
        return new GenerationResult(ranges, new ArrayList<>(classSet), new ArrayList<>(packageSet), methods);
    }

    private void insertClassAndPackageNodes(long applicationId, String analysisTime, int nodeCount, GenerationResult result) {
        log.info("Inserting {} Class nodes and {} Package nodes...", result.classList().size(), result.packageList().size());
        String nodeSql = """
                INSERT INTO tb_node_history (
                    application_id, analysis_time, node_id, node_name, node_type, use_yn
                ) VALUES (?, CAST(? AS TIMESTAMP), ?, ?, ?, TRUE)
                """;
        String detailSql = """
                INSERT INTO tb_node_detail_history (
                    application_id, analysis_time, node_id, split_node_level, split_node_name, split_node_type
                ) VALUES (?, CAST(? AS TIMESTAMP), ?, ?, ?, ?)
                """;

        List<Object[]> nodeBatch = new ArrayList<>(BATCH_SIZE);
        List<Object[]> detailBatch = new ArrayList<>(BATCH_SIZE * 3);

        int numMethods = nodeCount;
        int numClasses = result.classList().size();

        // 1. Class nodes
        for (int i = 0; i < numClasses; i++) {
            long classNodeId = numMethods + 1 + i;
            String classQualifiedName = result.classList().get(i);
            
            nodeBatch.add(new Object[]{
                    applicationId, analysisTime, classNodeId, classQualifiedName, "CLASS"
            });
            
            int lastDot = classQualifiedName.lastIndexOf('.');
            String className = classQualifiedName.substring(lastDot + 1);
            String packageName = classQualifiedName.substring(0, lastDot);
            
            detailBatch.add(new Object[]{
                    applicationId, analysisTime, classNodeId, 0, className, "CLASS"
            });
            detailBatch.add(new Object[]{
                    applicationId, analysisTime, classNodeId, 1, packageName, "PACKAGE"
            });

            if (nodeBatch.size() >= BATCH_SIZE) {
                flush(nodeSql, nodeBatch);
                flush(detailSql, detailBatch);
            }
        }

        // 2. Package nodes
        for (int i = 0; i < result.packageList().size(); i++) {
            long packageNodeId = numMethods + numClasses + 1 + i;
            String packageName = result.packageList().get(i);
            
            nodeBatch.add(new Object[]{
                    applicationId, analysisTime, packageNodeId, packageName, "PACKAGE"
            });
            
            detailBatch.add(new Object[]{
                    applicationId, analysisTime, packageNodeId, 0, packageName, "PACKAGE"
            });

            if (nodeBatch.size() >= BATCH_SIZE) {
                flush(nodeSql, nodeBatch);
                flush(detailSql, detailBatch);
            }
        }

        flush(nodeSql, nodeBatch);
        flush(detailSql, detailBatch);
    }    private int generateRelations(long applicationId,
                                  String analysisTime,
                                  GenerationResult result,
                                  int nodeCount,
                                  Random random) {
        log.info("Generating relations between domains...");
        String relationSql = """
                INSERT INTO tb_node_relation_history (
                    application_id, analysis_time, relation_id, source_node_id, target_node_id, relation_type
                ) VALUES (?, CAST(? AS TIMESTAMP), ?, ?, ?, ?)
                """;

        List<Object[]> relationBatch = new ArrayList<>(BATCH_SIZE);
        long relationId = 1L;
        java.util.Set<String> classRelations = new java.util.HashSet<>();

        // 1. Generate method-call relations
        for (int rangeIndex = 0; rangeIndex < result.ranges().size(); rangeIndex++) {
            DomainRange range = result.ranges().get(rangeIndex);
            for (long source = range.start(); source <= range.end(); source++) {
                int internalEdges = 3 + random.nextInt(5);
                for (int i = 0; i < internalEdges; i++) {
                    long target = randomNode(range, random);
                    if (target != source) {
                        relationBatch.add(relationRow(applicationId, analysisTime, relationId++, source, target));
                        trackClassRelation(source, target, result, classRelations);
                    }
                }

                if (random.nextDouble() < 0.22d) {
                    DomainRange externalRange = result.ranges().get((rangeIndex + 1 + random.nextInt(result.ranges().size() - 1)) % result.ranges().size());
                    long target = randomNode(externalRange, random);
                    relationBatch.add(relationRow(applicationId, analysisTime, relationId++, source, target));
                    trackClassRelation(source, target, result, classRelations);
                }

                if (relationBatch.size() >= BATCH_SIZE) {
                    log.debug("Flushing relation batch (size={})", relationBatch.size());
                    flush(relationSql, relationBatch);
                }
            }
        }

        java.util.Map<String, Integer> classToIndex = new java.util.HashMap<>();
        for (int i = 0; i < result.classList().size(); i++) {
            classToIndex.put(result.classList().get(i), i);
        }

        // 1.5. Generate CLASS_DEPENDENCY relations
        for (String rel : classRelations) {
            String[] parts = rel.split("->");
            String classA = parts[0];
            String classB = parts[1];
            Integer classAIdx = classToIndex.get(classA);
            Integer classBIdx = classToIndex.get(classB);
            if (classAIdx != null && classBIdx != null) {
                long classANodeId = nodeCount + 1 + classAIdx;
                long classBNodeId = nodeCount + 1 + classBIdx;
                relationBatch.add(new Object[]{
                        applicationId, analysisTime, relationId++, classANodeId, classBNodeId, "CLASS_DEPENDENCY"
                });
                if (relationBatch.size() >= BATCH_SIZE) {
                    flush(relationSql, relationBatch);
                }
            }
        }

        // 2. Generate CLASS_METHOD_OWNERSHIP relations (from Class to Method)
        for (MethodMeta method : result.methods()) {
            Integer classIdx = classToIndex.get(method.classQualifiedName());
            if (classIdx != null) {
                long classNodeId = nodeCount + 1 + classIdx;
                relationBatch.add(new Object[]{
                        applicationId, analysisTime, relationId++, classNodeId, method.nodeId(), "CLASS_METHOD_OWNERSHIP"
                });
                if (relationBatch.size() >= BATCH_SIZE) {
                    flush(relationSql, relationBatch);
                }
            }
        }

        // 3. Generate PACKAGE_CONTAINMENT relations (from Package to Class)
        java.util.Map<String, Integer> packageToIndex = new java.util.HashMap<>();
        for (int i = 0; i < result.packageList().size(); i++) {
            packageToIndex.put(result.packageList().get(i), i);
        }
        for (int i = 0; i < result.classList().size(); i++) {
            String classQualifiedName = result.classList().get(i);
            int lastDot = classQualifiedName.lastIndexOf('.');
            String packageName = classQualifiedName.substring(0, lastDot);
            
            Integer pkgIdx = packageToIndex.get(packageName);
            if (pkgIdx != null) {
                long classNodeId = nodeCount + 1 + i;
                long packageNodeId = nodeCount + result.classList().size() + 1 + pkgIdx;
                relationBatch.add(new Object[]{
                        applicationId, analysisTime, relationId++, packageNodeId, classNodeId, "PACKAGE_CONTAINMENT"
                });
                if (relationBatch.size() >= BATCH_SIZE) {
                    flush(relationSql, relationBatch);
                }
            }
        }

        if (!relationBatch.isEmpty()) {
            log.debug("Flushing final relation batch (size={})", relationBatch.size());
        }
        flush(relationSql, relationBatch);
        log.info("Relations successfully generated. Total relations: {}", relationId - 1);
        return Math.toIntExact(relationId - 1);
    }

    private void trackClassRelation(long source, long target, GenerationResult result, java.util.Set<String> classRelations) {
        MethodMeta sourceMethod = result.methods().get((int) (source - 1));
        MethodMeta targetMethod = result.methods().get((int) (target - 1));
        String classA = sourceMethod.classQualifiedName();
        String classB = targetMethod.classQualifiedName();
        if (!classA.equals(classB)) {
            classRelations.add(classA + "->" + classB);
        }
    }
    private Object[] relationRow(long applicationId,
                                 String analysisTime,
                                 long relationId,
                                 long source,
                                 long target) {
        return new Object[]{
                applicationId, analysisTime, relationId, source, target, "METHOD_CALL"
        };
    }

    private void flush(String sql, List<Object[]> batch) {
        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(sql, batch);
            batch.clear();
        }
    }

    private long randomNode(DomainRange range, Random random) {
        return range.start() + random.nextInt(Math.toIntExact(range.end() - range.start() + 1));
    }

    private String packageName(DomainTemplate template, String domainSlug, int index) {
        String layer = switch (index % 5) {
            case 0 -> "api";
            case 1 -> "service";
            case 2 -> "workflow";
            case 3 -> "data";
            default -> "integration";
        };
        return "com.graphintelligence." + template.name().toLowerCase(Locale.ROOT) + "." + domainSlug + "." + layer;
    }

    private String componentSuffix(int index) {
        return switch (index % 6) {
            case 0 -> "Controller";
            case 1 -> "Service";
            case 2 -> "Workflow";
            case 3 -> "Repository";
            case 4 -> "Client";
            default -> "Policy";
        };
    }

    private String methodPrefix(int index) {
        return switch (index % 7) {
            case 0 -> "orchestrate";
            case 1 -> "validate";
            case 2 -> "resolve";
            case 3 -> "persist";
            case 4 -> "publish";
            case 5 -> "hydrate";
            default -> "calculate";
        };
    }

    private List<String> domainsFor(DomainTemplate template) {
        return switch (template) {
            case AMAZON -> List.of(
                    "Customer Identity", "Product Catalog", "Search Ranking", "Recommendation Engine",
                    "Shopping Cart", "Checkout", "Payments", "Order Fulfillment",
                    "Inventory", "Shipping", "Reviews", "Notifications"
            );
            case SWIGGY -> List.of(
                    "Customer Identity", "Restaurant Onboarding", "Menu Catalog", "Cart",
                    "Order Placement", "Delivery Dispatch", "Payments", "Partner Operations",
                    "Live Tracking", "Promotions", "Ratings", "Notifications"
            );
            case BLINKIT -> List.of(
                    "Customer Identity", "Dark Store Inventory", "Product Catalog", "Cart",
                    "Checkout", "Payments", "Picker Assignment", "Rider Dispatch",
                    "Delivery Tracking", "Substitution", "Promotions", "Notifications"
            );
            case ZEPTO -> List.of(
                    "Customer Identity", "Micro Warehouse", "Product Catalog", "Cart",
                    "Checkout", "Payments", "Batch Picking", "Rider Allocation",
                    "ETA Prediction", "Inventory Replenishment", "Offers", "Notifications"
            );
            case MYNTRA -> List.of(
                    "Customer Identity", "Fashion Catalog", "Search Discovery", "Personalization",
                    "Wishlist", "Cart", "Payments", "Order Management",
                    "Returns", "Seller Operations", "Reviews", "Notifications"
            );
            case MAKEMYTRIP -> List.of(
                    "Customer Identity", "Flight Search", "Hotel Search", "Itinerary Planning",
                    "Booking", "Payments", "Refunds", "Offers",
                    "Reviews", "Loyalty", "Travel Alerts", "Notifications"
            );
        };
    }

    private String displayName(DomainTemplate template) {
        return switch (template) {
            case AMAZON -> "Amazon";
            case SWIGGY -> "Swiggy";
            case BLINKIT -> "Blinkit";
            case ZEPTO -> "Zepto";
            case MYNTRA -> "Myntra";
            case MAKEMYTRIP -> "MakeMyTrip";
        };
    }

    private String toSlug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    private String toPascal(String value) {
        StringBuilder result = new StringBuilder();
        for (String part : value.split("[^A-Za-z0-9]+")) {
            if (!part.isBlank()) {
                result.append(part.substring(0, 1).toUpperCase(Locale.ROOT))
                        .append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return result.isEmpty() ? "Domain" : result.toString();
    }

    private record DomainRange(String name, String slug, long start, long end) {
    }

    private record MethodMeta(long nodeId, String classQualifiedName, String packageName) {
    }

    private record GenerationResult(
            List<DomainRange> ranges,
            List<String> classList,
            List<String> packageList,
            List<MethodMeta> methods
    ) {
    }

    public record GeneratedDataset(
            Long applicationId,
            String applicationKey,
            String applicationName,
            String analysisTime,
            int nodeCount,
            int relationCount,
            String template
    ) {
    }
}

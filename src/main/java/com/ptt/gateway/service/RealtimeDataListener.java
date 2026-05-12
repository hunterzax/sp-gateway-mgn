package com.ptt.gateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptt.gateway.model.AuditLog;
import com.ptt.gateway.model.Calc;
import com.ptt.gateway.model.RealtimeTagHistogram;
import com.ptt.gateway.model.Status;
import com.ptt.gateway.model.Tag;
import com.ptt.gateway.repository.CalcRepository;
import com.ptt.gateway.repository.RealtimeTagHistogramRepository;
import com.ptt.gateway.repository.TagRepository;
import com.ptt.gateway.repository.ShipperTagsLinkRepository;
import com.ptt.gateway.repository.ShipperCalcLinkRepository;
import com.ptt.gateway.model.ShipperTagsLink;
import com.ptt.gateway.model.ShipperCalcLink;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class RealtimeDataListener {

    private final TagRepository tagRepository;
    private final CalcRepository calcRepository;
    private final RealtimeTagHistogramRepository histogramRepository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final AuditLogService auditLogService;
    private final FailRateService failRateService;
    private final ShipperTagsLinkRepository shipperTagsLinkRepository;
    private final ShipperCalcLinkRepository shipperCalcLinkRepository;

    // Cache: source tag name (Kafka) -> List<Entity>
    // Volatile references allow atomic swap in refreshCache(), eliminating the
    // non-atomic clear()+putAll() race window (CWE-366, issues #87628 #87629 #87685 #87660)
    private volatile Map<String, List<Tag>> tagCache = Collections.emptyMap();
    private volatile Map<String, List<Calc>> calcCache = Collections.emptyMap();

    // Cache: TagID -> List<ShipperID>
    private volatile Map<String, List<String>> tagShipperCache = Collections.emptyMap();
    private volatile Map<String, List<String>> calcShipperCache = Collections.emptyMap();

    @org.springframework.beans.factory.annotation.Value("${spring.kafka.bootstrap-servers}")
    private String kafkaBootstrapServers;

    @org.springframework.beans.factory.annotation.Value("${spring.kafka.template.default-topic}")
    private String kafkaTopic;

    // Matches the ingest string form: yyyy-MM-dd HH:mm:ss.SSS
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    /** Source systems often use timestamps without fractional seconds (e.g. {@code realtime_analog}). */
    private static final DateTimeFormatter TIMESTAMP_FORMATTER_NO_MS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Max characters to retain when sanitizing untrusted input for logging. */
    private static final int LOG_MAX_LENGTH = 200;

    private static final String UPDATE_TAG_SQL = "UPDATE tags SET point_value = ?, status = ?, last_data_date = ?, "
            +
            "min_value = CASE WHEN min_value IS NULL THEN ? ELSE LEAST(min_value, ?) END, " +
            "max_value = CASE WHEN max_value IS NULL THEN ? ELSE GREATEST(max_value, ?) END " +
            "WHERE tag_id = ?";

    private static final String UPDATE_CALC_SQL = "UPDATE calcs SET point_value = ?, status = ?, last_data_date = ?, "
            +
            "min_value = CASE WHEN min_value IS NULL THEN ? ELSE LEAST(min_value, ?) END, " +
            "max_value = CASE WHEN max_value IS NULL THEN ? ELSE GREATEST(max_value, ?) END " +
            "WHERE tag_id = ?";

    // ── CWE-117 remediation ──────────────────────────────────────────────
    /**
     * Strips CR/LF and control characters from untrusted input before it
     * reaches log statements, preventing log injection (CWE-117).
     */
    private static String sanitizeForLog(String input) {
        if (input == null) return "null";
        String safe = input.replaceAll("[\\r\\n\\t]", "_")
                           .replaceAll("[\\p{Cntrl}]", "");
        if (safe.length() > LOG_MAX_LENGTH) {
            safe = safe.substring(0, LOG_MAX_LENGTH) + "...";
        }
        return safe;
    }

    /**
     * Escapes a string for safe embedding inside a JSON string value.
     * Prevents broken JSON structure from untrusted data (CWE-117).
     */
    private static String escapeJsonValue(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\r", "\\r")
                    .replace("\n", "\\n")
                    .replace("\t", "\\t");
    }


    @PostConstruct
    public void init() {
        refreshCache();
        verifyKafkaConnection();
    }

    private void verifyKafkaConnection() {
        new Thread(() -> {
            try {
                // Wait for Spring Kafka to initialize
                Thread.sleep(3000);
                log.info("==========================================================");
                log.info("============= KAFKA CONNECTION STATUS CHECK ==============");
                log.info("🎯 TARGET BROKERS : {}", kafkaBootstrapServers);
                log.info("🎯 TARGET TOPIC   : {}", kafkaTopic);

                java.util.Properties properties = new java.util.Properties();
                properties.put(org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
                properties.put(org.apache.kafka.clients.admin.AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000");

                try (org.apache.kafka.clients.admin.AdminClient adminClient = org.apache.kafka.clients.admin.AdminClient.create(properties)) {
                    org.apache.kafka.clients.admin.DescribeClusterResult cluster = adminClient.describeCluster();
                    String clusterId = cluster.clusterId().get(5, java.util.concurrent.TimeUnit.SECONDS);
                    // Null-guard: nodes().get() can return null if cluster metadata is
                    // unavailable, and calling .size() on null would be a NullPointerException
                    // (CWE-476, issue #87655)
                    java.util.Collection<org.apache.kafka.common.Node> nodes =
                            cluster.nodes().get(5, java.util.concurrent.TimeUnit.SECONDS);
                    int nodeCount = nodes != null ? nodes.size() : 0;

                    log.info("==========================================================");
                    log.info("✅ STATUS         : SUCCESSFULLY CONNECTED");
                    log.info("✅ CLUSTER ID     : {}", clusterId);
                    log.info("✅ ACTIVE NODES   : {}", nodeCount);
                    log.info("==========================================================");
                } catch (Exception e) {
                    log.error("==========================================================");
                    log.error("❌ STATUS         : CONNECTION FAILED");
                    log.error("❌ REASON         : {}", e.getMessage());
                    log.error("==========================================================");
                }
            } catch (InterruptedException ie) {
                // Restore interrupt status so the thread lifecycle is not silently broken
                // (CWE-366, issue #87674)
                Thread.currentThread().interrupt();
                log.warn("Kafka connection check interrupted");
            } catch (Exception ex) {
                log.warn("Unexpected error during Kafka connection check", ex);
            }
        }).start();
    }

    @Scheduled(fixedRate = 60000)
    public void scheduledRefresh() {
        refreshCache();
        checkExpiration();
    }

    @org.springframework.transaction.annotation.Transactional
    public void checkExpiration() {
        LocalDateTime now = LocalDateTime.now();
        List<Tag> expiredTags = tagRepository.findByDeactivateDateBeforeAndStatusNot(now, Status.INACTIVE);
        if (!expiredTags.isEmpty()) {
            for (Tag tag : expiredTags) {
                tag.setStatus(Status.INACTIVE);
                try {
                    java.util.Map<String, Object> payload = new java.util.HashMap<>();
                    payload.put("tagID", tag.getTagID());
                    payload.put("value", tag.getPointValue());
                    payload.put("status", Status.INACTIVE.name());
                    payload.put("minValue", tag.getMinValue());
                    payload.put("maxValue", tag.getMaxValue());
                    payload.put("timestamp", now.toString());

                    messagingTemplate.convertAndSend("/topic/tags", payload);
                    messagingTemplate.convertAndSend("/topic/tags/" + tag.getTagID(), payload);
                } catch (Exception e) {
                    log.error("Failed to broadcast tag expiration", e);
                }
            }
            tagRepository.saveAll(expiredTags);
            log.info("Deactivated {} expired tags", expiredTags.size());
        }

        List<Calc> expiredCalcs = calcRepository.findByDeactivateDateBeforeAndStatusNot(now, Status.INACTIVE);
        if (!expiredCalcs.isEmpty()) {
            for (Calc calc : expiredCalcs) {
                calc.setStatus(Status.INACTIVE);
                try {
                    java.util.Map<String, Object> payload = new java.util.HashMap<>();
                    payload.put("tagID", calc.getTagID());
                    payload.put("value", calc.getPointValue());
                    payload.put("status", Status.INACTIVE.name());
                    payload.put("minValue", calc.getMinValue());
                    payload.put("maxValue", calc.getMaxValue());
                    payload.put("timestamp", now.toString());

                    messagingTemplate.convertAndSend("/topic/calcs", payload);
                    messagingTemplate.convertAndSend("/topic/calcs/" + calc.getTagID(), payload);
                } catch (Exception e) {
                    log.error("Failed to broadcast calc expiration", e);
                }
            }
            calcRepository.saveAll(expiredCalcs);
            log.info("Deactivated {} expired calcs", expiredCalcs.size());
        }
    }

    public synchronized void refreshCache() {
        log.info("Refreshing Metadata Cache...");
        try {
            // Build complete new maps first, then swap atomically via volatile write.
            // This eliminates the race window from the old clear()+putAll() approach
            // (CWE-366, issues #87628 #87629 #87685 #87660).

            List<Tag> allTags = tagRepository.findAll();
            Map<String, List<Tag>> newTagCache = new ConcurrentHashMap<>();
            for (Tag t : allTags) {
                if (t.getScadaTag() != null) {
                    newTagCache.computeIfAbsent(t.getScadaTag(), k -> new ArrayList<>()).add(t);
                }
            }

            List<Calc> allCalcs = calcRepository.findAll();
            Map<String, List<Calc>> newCalcCache = new ConcurrentHashMap<>();
            for (Calc c : allCalcs) {
                if (c.getTag() != null) {
                    newCalcCache.computeIfAbsent(c.getTag(), k -> new ArrayList<>()).add(c);
                }
            }

            List<ShipperTagsLink> allTagLinks = shipperTagsLinkRepository.findAll();
            Map<String, List<String>> newTagShipperCache = new ConcurrentHashMap<>();
            for (ShipperTagsLink link : allTagLinks) {
                if (link.getShipperManagement() != null && link.getTagID() != null) {
                    newTagShipperCache.computeIfAbsent(link.getTagID(), k -> new ArrayList<>())
                            .add(link.getShipperManagement().getShipperID());
                }
            }

            List<ShipperCalcLink> allCalcLinks = shipperCalcLinkRepository.findAll();
            Map<String, List<String>> newCalcShipperCache = new ConcurrentHashMap<>();
            for (ShipperCalcLink link : allCalcLinks) {
                if (link.getShipperManagement() != null && link.getTagID() != null) {
                    newCalcShipperCache.computeIfAbsent(link.getTagID(), k -> new ArrayList<>())
                            .add(link.getShipperManagement().getShipperID());
                }
            }

            // Atomic swap — readers always see a fully-populated map or the old one
            tagCache = newTagCache;
            calcCache = newCalcCache;
            tagShipperCache = newTagShipperCache;
            calcShipperCache = newCalcShipperCache;

            log.info("Cache Refreshed. Loaded {} tag keys and {} calc keys.", newTagCache.size(), newCalcCache.size());
            log.info("Shipper Cache Refreshed. Loaded {} tag->shipper mappings and {} calc->shipper mappings.",
                    newTagShipperCache.size(), newCalcShipperCache.size());
        } catch (Exception e) {
            log.error("Failed to refresh cache", e);
        }
    }

    @KafkaListener(topics = "${spring.kafka.template.default-topic}", groupId = "${spring.kafka.consumer.group-id}")
    @org.springframework.transaction.annotation.Transactional
    public void consume(List<String> messages) {
        log.info("Received Batch of {} Kafka Messages", messages.size());

        List<RealtimeTagHistogram> histogramsToSave = new ArrayList<>();
        Map<String, List<DataPoint>> groupedData = new HashMap<>();
        // Capture key count before processing so the catch block has a stable snapshot
        // even if groupedData is partially populated when an exception occurs (CWE-366)
        long groupedKeyCountSnapshot = 0;

        // messagesIn (success) and failed counted per unique source tag name — mutually
        // exclusive
        // Fail Rate = failed / (messagesIn + failed) × 100 (MessagesInPerSec formula)
        long batchTagSuccess = 0;
        long batchTagFail = 0;
        long batchCalcSuccess = 0;
        long batchCalcFail = 0;
        long parseErrors = 0;

        Map<String, Long> shipperTagSuccess = new HashMap<>();
        Map<String, Long> shipperTagFail = new HashMap<>();
        Map<String, Long> shipperCalcSuccess = new HashMap<>();
        Map<String, Long> shipperCalcFail = new HashMap<>();


        try {
            // 1. Parse and Group
            for (String message : messages) {
                try {
                    JsonNode root = objectMapper.readTree(message);
                    String tagName;
                    double curValue;
                    String timeStr;
                    String flagFresh = null;
                    String hillowState = null;

                    if (root.has("message") && root.path("message").isTextual()) {
                        // Handle real Kafka payload (nested JSON string)
                        String innerJsonStr = root.path("message").asText();
                        JsonNode innerNode = objectMapper.readTree(innerJsonStr);
                        tagName = innerNode.path("description").asText();
                        curValue = innerNode.path("value").asDouble();
                        timeStr = innerNode.path("time").asText();
                        if (innerNode.hasNonNull("flag_fresh")) {
                            flagFresh = innerNode.path("flag_fresh").asText();
                        }
                        if (innerNode.hasNonNull("hillow_state")) {
                            hillowState = innerNode.path("hillow_state").asText();
                        }
                    } else {
                        // Flat payload from ingest (realtime_analog layout)
                        tagName = root.path("tagname").asText();
                        curValue = root.path("cur_value").asDouble();
                        timeStr = root.path("time").asText();
                        if (root.hasNonNull("flag_fresh")) {
                            flagFresh = root.path("flag_fresh").asText();
                        }
                        if (root.hasNonNull("hillow_state")) {
                            hillowState = root.path("hillow_state").asText();
                        }
                    }

                    LocalDateTime timestamp = parseKafkaTimestamp(timeStr);

                    groupedData.computeIfAbsent(tagName, k -> new ArrayList<>())
                            .add(new DataPoint(curValue, timestamp, flagFresh, hillowState));

                } catch (Exception e) {
                    // CWE-117: sanitize raw Kafka message before logging
                    log.error("Error parsing message in batch: {}", sanitizeForLog(message), e);
                    parseErrors++; // counted once, added to both tag and calc fail at flush
                    String safeSnippet = escapeJsonValue(
                            message.substring(0, Math.min(message.length(), 100)));
                    auditLogService.log(AuditLog.builder()
                            .action("kafkaParseError")
                            .description("Failed to retrieve value from Tag: unknown (parse error)")
                            .logType("DATALINK")
                            .severity("Alert")
                            .source("KAFKA_LISTENER")
                            .createdBy("SYSTEM")
                            .statusType("FAILED")
                            .metadata("{\"rawMessage\": \"" + safeSnippet + "\"}")
                            .build());
                }
            }

            List<Object[]> batchArgsTags = new ArrayList<>();
            List<Object[]> batchArgsCalcs = new ArrayList<>();

            // Snapshot key count now that parsing is complete (stable for catch block)
            groupedKeyCountSnapshot = groupedData.size();

            // 2. Process Tags
            for (Map.Entry<String, List<DataPoint>> entry : groupedData.entrySet()) {
                String sourceTagName = entry.getKey();
                List<DataPoint> dataPoints = entry.getValue();

                double batchMin = Double.MAX_VALUE;
                double batchMax = Double.MIN_VALUE;
                LocalDateTime latestTime = LocalDateTime.MIN;
                double latestValue = 0.0;
                String latestFlagFresh = null;
                String latestHillowState = null;

                for (DataPoint dp : dataPoints) {
                    if (dp.value < batchMin)
                        batchMin = dp.value;
                    if (dp.value > batchMax)
                        batchMax = dp.value;
                    if (dp.timestamp.isAfter(latestTime)) {
                        latestTime = dp.timestamp;
                        latestValue = dp.value;
                        latestFlagFresh = dp.flagFresh;
                        latestHillowState = dp.hillowState;
                    }
                }

                // CACHE LOOKUP for Tags
                // Snapshot the volatile reference, then use getOrDefault for a safe read
                // that never mutates the shared map from the reader thread (CWE-366, issue #87628)
                Map<String, List<Tag>> tagCacheSnapshot = tagCache;
                List<Tag> tags = tagCacheSnapshot.getOrDefault(sourceTagName, null);

                if (tags == null || tags.isEmpty()) {
                    batchTagFail++; // source tag name could not be matched to any Tag entity
                    // CWE-117: sanitize sourceTagName before embedding in audit log
                    String safeTagName = escapeJsonValue(sourceTagName);
                    auditLogService.log(AuditLog.builder()
                            .action("kafkaTagNotFound")
                            .description("Cannot connect to Tag: " + sanitizeForLog(sourceTagName))
                            .logType("DATALINK")
                            .severity("High")
                            .source("KAFKA_LISTENER")
                            .createdBy("SYSTEM")
                            .statusType("FAILED")
                            .metadata("{\"sourceTag\": \"" + safeTagName + "\"}")
                            .build());
                } else {

                    boolean tagProcessed = false;
                    for (Tag tag : tags) {
                        if (tag.getStatus() == Status.INACTIVE)
                            continue;

                        for (DataPoint dp : dataPoints) {
                            histogramsToSave.add(RealtimeTagHistogram.builder()
                                    .tagID(tag.getTagID()).curValue(dp.value).timestamp(dp.timestamp).build());
                        }
                        String status = deriveStatus(latestTime, latestFlagFresh, latestHillowState).name();
                        batchArgsTags.add(new Object[] {
                                latestValue, status, Timestamp.valueOf(latestTime),
                                batchMin, batchMin, batchMax, batchMax, tag.getTagID()
                        });
                        tag.setLastDataDate(latestTime);
                        tagRepository.save(tag);
                        tagProcessed = true;
                        
                        // Per-shipper tagging
                        // Snapshot the volatile reference, then read from the snapshot to
                        // avoid iteration over a concurrently-swapped map (CWE-366, issue #87685)
                        Map<String, List<String>> tagShipperSnapshot = tagShipperCache;
                        List<String> shippers = tagShipperSnapshot.getOrDefault(tag.getTagID(), Collections.emptyList());
                        for (String shipperId : shippers) {
                            shipperTagSuccess.merge(shipperId, 1L, Long::sum);
                        }

                        try {
                            java.util.Map<String, Object> payload = new java.util.HashMap<>();
                            payload.put("tagID", tag.getTagID());
                            payload.put("value", latestValue);
                            payload.put("status", status);
                            payload.put("minValue", batchMin);
                            payload.put("maxValue", batchMax);
                            payload.put("timestamp", latestTime.toString());
                            messagingTemplate.convertAndSend("/topic/tags", payload);
                            messagingTemplate.convertAndSend("/topic/tags/" + tag.getTagID(), payload);
                        } catch (Exception e) {
                            log.error("Failed to broadcast tag update", e);
                        }
                    }
                    // 1 success per source tag name regardless of how many tag entities matched
                    if (tagProcessed)
                        batchTagSuccess++;
                    else {
                        batchTagFail++; // all matched tags were INACTIVE
                        Map<String, List<String>> tagShipperFailSnapshot = tagShipperCache;
                        for (Tag tag : tags) {
                            List<String> failShippers = tagShipperFailSnapshot.getOrDefault(tag.getTagID(), Collections.emptyList());
                            for (String shipperId : failShippers) {
                                shipperTagFail.merge(shipperId, 1L, Long::sum);
                            }
                        }
                    }
                }


                // CACHE LOOKUP for Calcs
                // Snapshot the volatile reference, then use getOrDefault for a safe read
                // that never mutates the shared map from the reader thread (CWE-366, issue #87629)
                Map<String, List<Calc>> calcCacheSnapshot = calcCache;
                List<Calc> calcs = calcCacheSnapshot.getOrDefault(sourceTagName, null);

                if (calcs != null) {
                    boolean calcProcessed = false;
                    for (Calc calc : calcs) {
                        if (calc.getStatus() == Status.INACTIVE)
                            continue;

                        for (DataPoint dp : dataPoints) {
                            histogramsToSave.add(RealtimeTagHistogram.builder()
                                    .tagID(calc.getTagID()).curValue(dp.value).timestamp(dp.timestamp).build());
                        }
                        String status = deriveStatus(latestTime, latestFlagFresh, latestHillowState).name();
                        batchArgsCalcs.add(new Object[] {
                                latestValue, status, Timestamp.valueOf(latestTime),
                                batchMin, batchMin, batchMax, batchMax, calc.getTagID()
                        });
                        calc.setLastDataDate(latestTime);
                        calcRepository.save(calc);
                        calcProcessed = true;
                        
                        // Per-shipper counting
                        // Snapshot the volatile reference, then read from the snapshot to
                        // avoid iteration over a concurrently-swapped map (CWE-366, issue #87660)
                        Map<String, List<String>> calcShipperSnapshot = calcShipperCache;
                        List<String> calcShippers = calcShipperSnapshot.getOrDefault(calc.getTagID(), Collections.emptyList());
                        for (String shipperId : calcShippers) {
                            shipperCalcSuccess.merge(shipperId, 1L, Long::sum);
                        }

                        try {
                            java.util.Map<String, Object> payload = new java.util.HashMap<>();
                            payload.put("tagID", calc.getTagID());
                            payload.put("value", latestValue);
                            payload.put("status", status);
                            payload.put("minValue", batchMin);
                            payload.put("maxValue", batchMax);
                            payload.put("timestamp", latestTime.toString());
                            messagingTemplate.convertAndSend("/topic/calcs", payload);
                            messagingTemplate.convertAndSend("/topic/calcs/" + calc.getTagID(), payload);
                        } catch (Exception e) {
                            log.error("Failed to broadcast calc update", e);
                        }
                    }
                    // 1 success per source tag name regardless of how many calc entities matched
                    if (calcProcessed)
                        batchCalcSuccess++;
                    else {
                        batchCalcFail++; // all matched calcs were INACTIVE
                        Map<String, List<String>> calcShipperFailSnapshot = calcShipperCache;
                        for (Calc calc : calcs) {
                            List<String> failCalcShippers = calcShipperFailSnapshot.getOrDefault(calc.getTagID(), Collections.emptyList());
                            for (String shipperId : failCalcShippers) {
                                shipperCalcFail.merge(shipperId, 1L, Long::sum);
                            }
                        }
                    }
                }
                // source tag name with no calc links is not counted as a calc fail
            }

            // 3. Bulk Insert Histograms
            if (!histogramsToSave.isEmpty())

            {
                histogramRepository.saveAll(histogramsToSave);
            }

            // 4. Execute JDBC Batch Updates
            if (!batchArgsTags.isEmpty()) {
                jdbcTemplate.batchUpdate(UPDATE_TAG_SQL, batchArgsTags);
            }
            if (!batchArgsCalcs.isEmpty()) {
                jdbcTemplate.batchUpdate(UPDATE_CALC_SQL, batchArgsCalcs);
            }

            log.info("Processed batch: {} histograms, {} tag updates, {} calc updates", histogramsToSave.size(),
                    batchArgsTags.size(), batchArgsCalcs.size());

            // Flush: messagesIn = source tags processed OK, failed = not found/INACTIVE +
            // parse errors
            // Formula: Fail Rate = failed / (messagesIn + failed) × 100
            failRateService.recordTagSuccess(batchTagSuccess);
            failRateService.recordTagFail(batchTagFail + parseErrors);
            failRateService.recordCalcSuccess(batchCalcSuccess);
            failRateService.recordCalcFail(batchCalcFail + parseErrors);
            
            // Per-Shipper calls
            failRateService.recordShipperStats(shipperTagSuccess, shipperTagFail, shipperCalcSuccess, shipperCalcFail);
            
            failRateService.broadcastAndPersist();

        } catch (Exception e) {
            log.error("Error processing batch", e);
            // Use the pre-captured snapshot instead of reading groupedData.size() here,
            // which would be an unreliable read on partially-populated state (CWE-366, issue #87673)
            failRateService.recordTagFail(groupedKeyCountSnapshot + parseErrors);
            failRateService.recordCalcFail(groupedKeyCountSnapshot + parseErrors);
            for (String sourceTagName : groupedData.keySet()) {
                // CWE-117: sanitize both sourceTagName and exception message
                String safeSrc = escapeJsonValue(sourceTagName);
                String safeErr = escapeJsonValue(e.getMessage());
                auditLogService.log(AuditLog.builder()
                        .action("kafkaBatchError")
                        .description("Failed to retrieve value from Tag: " + sanitizeForLog(sourceTagName))
                        .logType("DATALINK")
                        .severity("Alert")
                        .source("KAFKA_LISTENER")
                        .createdBy("SYSTEM")
                        .statusType("FAILED")
                        .metadata("{\"sourceTag\": \"" + safeSrc + "\", \"error\": \"" + safeErr + "\"}")
                        .build());
            }
        }
    }

    private com.ptt.gateway.model.Status calculateStatus(LocalDateTime timestamp) {
        LocalDateTime now = LocalDateTime.now();
        if (timestamp.isAfter(now.minusMinutes(3))) {
            return com.ptt.gateway.model.Status.ONLINE;
        } else if (timestamp.isAfter(now.minusMinutes(10))) {
            return com.ptt.gateway.model.Status.DELAY;
        } else {
            return com.ptt.gateway.model.Status.OFFLINE;
        }
    }

    /**
     * Combines {@code flag_fresh} / {@code hillow_state} with latency-based status.
     * Non-fresh or non-NORMAL alarm band overrides to {@link Status#ERROR}.
     */
    private com.ptt.gateway.model.Status deriveStatus(LocalDateTime timestamp, String flagFresh, String hillowState) {
        if (flagFresh != null && !flagFresh.isBlank() && !"YES".equalsIgnoreCase(flagFresh.trim())) {
            return com.ptt.gateway.model.Status.ERROR;
        }
        if (hillowState != null && !hillowState.isBlank() && !"NORMAL".equalsIgnoreCase(hillowState.trim())) {
            return com.ptt.gateway.model.Status.ERROR;
        }
        return calculateStatus(timestamp);
    }

    private LocalDateTime parseKafkaTimestamp(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) {
            throw new IllegalArgumentException("time is empty");
        }
        String t = timeStr.trim();
        if (t.contains("T") && t.endsWith("Z")) {
            return LocalDateTime.ofInstant(java.time.Instant.parse(t), java.time.ZoneId.systemDefault());
        }
        try {
            return LocalDateTime.parse(t, TIMESTAMP_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // e.g. "2020-09-16 15:28:52" without fractional seconds
        }
        try {
            return LocalDateTime.parse(t, TIMESTAMP_FORMATTER_NO_MS);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Unparseable time: " + timeStr, e);
        }
    }

    private static class DataPoint {
        final double value;
        final LocalDateTime timestamp;
        final String flagFresh;
        final String hillowState;

        DataPoint(double value, LocalDateTime timestamp, String flagFresh, String hillowState) {
            this.value = value;
            this.timestamp = timestamp;
            this.flagFresh = flagFresh;
            this.hillowState = hillowState;
        }
    }
}

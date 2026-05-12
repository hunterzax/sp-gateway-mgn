package com.ptt.gateway.service;

import com.ptt.gateway.model.DashboardFailRate;
import com.ptt.gateway.model.ShipperFailRate;
import com.ptt.gateway.model.ShipperDailyStats;
import com.ptt.gateway.repository.DashboardFailRateRepository;
import com.ptt.gateway.repository.ShipperFailRateRepository;
import com.ptt.gateway.repository.ShipperDailyStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks weekly Kafka fail-rate for Tag and Calc processing.
 *
 * Design (lightweight):
 * - Counters are kept in-memory (AtomicLong) for high-frequency updates.
 * - DB row (dashboard_fail_rate) is updated on every Kafka batch flush –
 * only 2 rows ever exist (type = TAG | CALC), so the UPDATE is O(1).
 * - A weekly cron (every Monday 00:00) resets both in-memory counters
 * and DB rows.
 *
 * Fail Rate % = (failed / (messagesIn + failed)) × 100
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FailRateService {

    private static final String TYPE_TAG = "TAG";
    private static final String TYPE_CALC = "CALC";

    private final DashboardFailRateRepository failRateRepository;
    private final ShipperFailRateRepository shipperFailRateRepository;
    private final ShipperDailyStatsRepository shipperDailyStatsRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // ── Global In-memory accumulators ─────────────────────────────────────────

    private final AtomicLong tagMessagesIn = new AtomicLong(0);
    private final AtomicLong tagFailed = new AtomicLong(0);
    private final AtomicLong calcMessagesIn = new AtomicLong(0);
    private final AtomicLong calcFailed = new AtomicLong(0);

    // ── Shipper In-memory accumulators ────────────────────────────────────────

    private final Map<String, AtomicLong> shipperMessagesIn = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> shipperFailed = new ConcurrentHashMap<>();

    // ── Record methods (called from RealtimeDataListener) ─────────────────────

    public void recordTagSuccess(long count) {
        tagMessagesIn.addAndGet(count);
    }

    public void recordTagFail(long count) {
        tagFailed.addAndGet(count);
    }

    public void recordCalcSuccess(long count) {
        calcMessagesIn.addAndGet(count);
    }

    public void recordCalcFail(long count) {
        calcFailed.addAndGet(count);
    }

    public void recordShipperStats(Map<String, Long> tagSuccess, Map<String, Long> tagFail, 
                                   Map<String, Long> calcSuccess, Map<String, Long> calcFail) {
        tagSuccess.forEach((shipperId, count) -> shipperMessagesIn.computeIfAbsent(shipperId, k -> new AtomicLong(0)).addAndGet(count));
        calcSuccess.forEach((shipperId, count) -> shipperMessagesIn.computeIfAbsent(shipperId, k -> new AtomicLong(0)).addAndGet(count));
        tagFail.forEach((shipperId, count) -> shipperFailed.computeIfAbsent(shipperId, k -> new AtomicLong(0)).addAndGet(count));
        calcFail.forEach((shipperId, count) -> shipperFailed.computeIfAbsent(shipperId, k -> new AtomicLong(0)).addAndGet(count));
    }

    // ── Broadcast + Persist (called after each successful Kafka batch) ─────────

    public void broadcastAndPersist() {
        long tIn = tagMessagesIn.get();
        long tFail = tagFailed.get();
        long cIn = calcMessagesIn.get();
        long cFail = calcFailed.get();

        double tagFailRate = computeFailRate(tIn, tFail);
        double calcFailRate = computeFailRate(cIn, cFail);

        LocalDate weekStart = currentWeekStart();
        LocalDateTime now = LocalDateTime.now();

        // Persist TAG row
        persistRow(TYPE_TAG, weekStart, tIn, tFail, tagFailRate, now);
        // Persist CALC row
        persistRow(TYPE_CALC, weekStart, cIn, cFail, calcFailRate, now);

        // Persist Shipper rows
        for (String shipperId : shipperMessagesIn.keySet()) {
            long sIn = shipperMessagesIn.get(shipperId).get();
            long sFail = shipperFailed.getOrDefault(shipperId, new AtomicLong(0)).get();
            double sFailRate = computeFailRate(sIn, sFail);
            persistShipperRow(shipperId, weekStart, sIn, sFail, sFailRate, now);
        }
        for (String shipperId : shipperFailed.keySet()) {
            if (!shipperMessagesIn.containsKey(shipperId)) {
                long sFail = shipperFailed.get(shipperId).get();
                persistShipperRow(shipperId, weekStart, 0, sFail, 100.0, now);
            }
        }

        // Broadcast over WebSocket (Global only, Shipper stats are fetched via REST)
        broadcastFailRate(tIn, tFail, tagFailRate, cIn, cFail, calcFailRate, weekStart, now);
    }

    // ── Weekly reset (every Monday 00:00) ─────────────────────────────────────

    @Scheduled(cron = "0 0 0 * * MON")
    public void resetWeeklyStats() {
        long tIn = tagMessagesIn.getAndSet(0);
        long tFail = tagFailed.getAndSet(0);
        long cIn = calcMessagesIn.getAndSet(0);
        long cFail = calcFailed.getAndSet(0);

        log.info("[FailRate] Weekly reset — TAG messagesIn={} failed={} | CALC messagesIn={} failed={}",
                tIn, tFail, cIn, cFail);

        LocalDate newWeekStart = currentWeekStart();
        LocalDateTime now = LocalDateTime.now();

        // Reset DB rows to 0 for the new week (Global)
        persistRow(TYPE_TAG, newWeekStart, 0, 0, 0.0, now);
        persistRow(TYPE_CALC, newWeekStart, 0, 0, 0.0, now);

        // Reset DB rows to 0 for the new week (Shippers)
        for (String shipperId : shipperMessagesIn.keySet()) {
            shipperMessagesIn.get(shipperId).set(0);
        }
        for (String shipperId : shipperFailed.keySet()) {
            shipperFailed.get(shipperId).set(0);
        }
        shipperFailRateRepository.deleteAll(); // Easy reset for weekly stats
        shipperMessagesIn.clear();
        shipperFailed.clear();

        // Broadcast zeroed-out stats
        broadcastFailRate(0, 0, 0.0, 0, 0, 0.0, newWeekStart, now);
    }

    // ── Daily snapshot & reset (every night at 00:00) ─────────────────────────

    @Scheduled(cron = "0 0 0 * * ?")
    public void snapshotDailyStats() {
        log.info("[FailRate] Daily snapshot & reset for Shippers started");
        LocalDate today = LocalDate.now();

        // Snapshot current values into ShipperDailyStats
        for (String shipperId : shipperMessagesIn.keySet()) {
            long messagesIn = shipperMessagesIn.get(shipperId).get();
            long failed = shipperFailed.getOrDefault(shipperId, new AtomicLong(0)).get();
            
            ShipperDailyStats stats = ShipperDailyStats.builder()
                .shipperId(shipperId)
                .recordDate(today)
                .messagesIn(messagesIn)
                .failed(failed)
                .build();
            shipperDailyStatsRepository.save(stats);
            
            // Reset in-memory for the new day
            shipperMessagesIn.get(shipperId).set(0);
        }

        for (String shipperId : shipperFailed.keySet()) {
            if (!shipperMessagesIn.containsKey(shipperId)) {
                long failed = shipperFailed.get(shipperId).get();
                ShipperDailyStats stats = ShipperDailyStats.builder()
                    .shipperId(shipperId)
                    .recordDate(today)
                    .messagesIn(0L)
                    .failed(failed)
                    .build();
                shipperDailyStatsRepository.save(stats);
            }
            // Reset in-memory for the new day
            shipperFailed.get(shipperId).set(0);
        }

        // Also reset the DB cumulative shipper records so they restart for the day
        shipperFailRateRepository.deleteAll();

        // Clean up history older than 7 days
        LocalDate oldDate = today.minusDays(7);
        shipperDailyStatsRepository.deleteByRecordDateBefore(oldDate);
        log.info("[FailRate] Daily snapshot complete. Cleared data before {}", oldDate);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Fail Rate % = (failed / (messagesIn + failed)) × 100
     *
     * messagesIn = messages successfully processed (Success Rate numerator)
     * failed = messages that could not be processed
     * Total = messagesIn + failed (mutually exclusive)
     *
     * Formula source: MessagesInPerSec / (MessagesInPerSec +
     * FailedProduceRequestsPerSec) × 100
     */
    private static double computeFailRate(long messagesIn, long failed) {
        long total = messagesIn + failed;
        return (total > 0) ? ((double) failed / total) * 100.0 : 0.0;
    }

    private static LocalDate currentWeekStart() {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private void persistRow(String type, LocalDate weekStart, long messagesIn,
            long failed, double failRate, LocalDateTime updatedAt) {
        try {
            DashboardFailRate row = failRateRepository.findById(type)
                    .orElse(DashboardFailRate.builder().type(type).build());
            row.setWeekStart(weekStart);
            row.setMessagesIn(messagesIn);
            row.setFailed(failed);
            row.setFailRate(failRate);
            row.setUpdatedAt(updatedAt);
            failRateRepository.save(row);
        } catch (Exception e) {
            log.error("[FailRate] Failed to persist row type={}", type, e);
        }
    }

    private void persistShipperRow(String shipperId, LocalDate weekStart, long messagesIn,
                                   long failed, double failRate, LocalDateTime updatedAt) {
        try {
            ShipperFailRate row = shipperFailRateRepository.findById(shipperId)
                    .orElse(ShipperFailRate.builder().shipperId(shipperId).build());
            row.setWeekStart(weekStart);
            row.setMessagesIn(messagesIn);
            row.setFailed(failed);
            row.setFailRate(failRate);
            row.setUpdatedAt(updatedAt);
            shipperFailRateRepository.save(row);
        } catch (Exception e) {
            log.error("[FailRate] Failed to persist shipper row id={}", shipperId, e);
        }
    }

    private void broadcastFailRate(long tIn, long tFail, double tRate,
            long cIn, long cFail, double cRate,
            LocalDate weekStart, LocalDateTime updatedAt) {
        try {
            Map<String, Object> tagMap = new HashMap<>();
            tagMap.put("messagesIn", tIn);
            tagMap.put("failed", tFail);
            tagMap.put("failRate", Math.round(tRate * 100.0) / 100.0);
            tagMap.put("weekStart", weekStart.toString());

            Map<String, Object> calcMap = new HashMap<>();
            calcMap.put("messagesIn", cIn);
            calcMap.put("failed", cFail);
            calcMap.put("failRate", Math.round(cRate * 100.0) / 100.0);
            calcMap.put("weekStart", weekStart.toString());

            Map<String, Object> payload = new HashMap<>();
            payload.put("tag", tagMap);
            payload.put("calc", calcMap);
            payload.put("timestamp", updatedAt.toString());

            messagingTemplate.convertAndSend("/topic/fail-rate", payload);
        } catch (Exception e) {
            log.error("[FailRate] Failed to broadcast fail-rate", e);
        }
    }
}

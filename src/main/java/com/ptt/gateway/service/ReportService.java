package com.ptt.gateway.service;

import com.ptt.gateway.model.ShipperFailRate;
import com.ptt.gateway.repository.ShipperCalcLinkRepository;
import com.ptt.gateway.repository.ShipperFailRateRepository;
import com.ptt.gateway.repository.ShipperRepository;
import com.ptt.gateway.repository.ShipperTagsLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import static com.ptt.gateway.util.LogSanitizer.sanitize;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ptt.gateway.model.ShipperDailyStats;
import com.ptt.gateway.repository.ShipperDailyStatsRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ShipperRepository shipperRepository;
    private final ShipperTagsLinkRepository shipperTagsLinkRepository;
    private final ShipperCalcLinkRepository shipperCalcLinkRepository;
    private final ShipperFailRateRepository shipperFailRateRepository;
    private final ShipperDailyStatsRepository shipperDailyStatsRepository;

    /**
     * Returns report stats for a single shipper:
     * - totalTag     : tag + calc links ทั้งหมดใน shipper นั้น
     * - activeTag    : tag + calc ที่ status != INACTIVE
     * - totalHitRate : messagesIn (TAG + CALC) จาก shipper_fail_rate
     * - totalFailRate: failed (TAG + CALC) จาก shipper_fail_rate
     * - hitRatePercent: (totalHitRate / (totalHitRate + totalFailRate)) * 100
     */
    public Map<String, Object> getShipperStats(String shipperId) {
        // Verify shipper exists
        if (!shipperRepository.existsById(shipperId)) {
            throw new IllegalArgumentException("Shipper not found: " + shipperId);
        }

        long totalTag = shipperTagsLinkRepository.countByShipperManagement_ShipperID(shipperId)
                + shipperCalcLinkRepository.countByShipperManagement_ShipperID(shipperId);

        long activeTag = shipperTagsLinkRepository.countActiveByShipperID(shipperId)
                + shipperCalcLinkRepository.countActiveByShipperID(shipperId);

        // Aggregate shipper fail-rate row
        long totalHitRate = 0;
        long totalFailed = 0;
        double hitRatePercent = 0.0;
        
        ShipperFailRate shipperFailRate = shipperFailRateRepository.findById(shipperId).orElse(null);
        if (shipperFailRate != null) {
            totalHitRate = shipperFailRate.getMessagesIn();
            totalFailed = shipperFailRate.getFailed();
            long totalMessages = totalHitRate + totalFailed;
            hitRatePercent = totalMessages > 0
                    ? Math.round(((double) totalHitRate / totalMessages) * 10000.0) / 100.0
                    : 0.0;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("shipperId",      shipperId);
        result.put("totalTag",       totalTag);
        result.put("activeTag",      activeTag);
        result.put("totalHitRate",   totalHitRate);
        result.put("totalFailRate",  totalFailed);
        result.put("hitRatePercent", hitRatePercent);

        log.debug("[Report] Shipper={} stats={}", sanitize(shipperId), sanitize(result));
        return result;
    }

    /**
     * Returns report stats for ALL shippers (used by the Reports overview page).
     */
    public List<Map<String, Object>> getAllShipperStats() {
        return shipperRepository.findAll().stream()
                .map(s -> getShipperStats(s.getShipperID()))
                .toList();
    }

    /**
     * Returns up to the last 7 days of daily hit/fail stats for a shipper
     */
    public List<ShipperDailyStats> getShipperHistory(String shipperId) {
        if (!shipperRepository.existsById(shipperId)) {
            throw new IllegalArgumentException("Shipper not found: " + shipperId);
        }
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(7);
        List<ShipperDailyStats> history = new java.util.ArrayList<>(
            shipperDailyStatsRepository.findByShipperIdAndRecordDateBetweenOrderByRecordDateAsc(shipperId, startDate, endDate)
        );

        // Fetch today's live stats (which haven't been snapshotted yet)
        Map<String, Object> liveStats = getShipperStats(shipperId);
        ShipperDailyStats todayLive = ShipperDailyStats.builder()
            .shipperId(shipperId)
            .recordDate(endDate)
            .messagesIn((Long) liveStats.get("totalHitRate"))
            .failed((Long) liveStats.get("totalFailRate"))
            .build();
            
        // If the snapshot just ran or there's an existing record for today, replace it. Otherwise append.
        if (!history.isEmpty() && history.get(history.size() - 1).getRecordDate().equals(endDate)) {
            history.set(history.size() - 1, todayLive);
        } else {
            history.add(todayLive);
        }

        return history;
    }
}

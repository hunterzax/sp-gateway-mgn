package com.ptt.gateway.controller;

import com.ptt.gateway.dto.ApiResponse;
import com.ptt.gateway.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import com.ptt.gateway.model.ShipperDailyStats;

@Slf4j
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * GET /api/report/shippers
     * Returns stats for ALL shippers (used by Reports overview page).
     *
     * Response per-shipper:
     *   shipperId       - shipper ID
     *   totalTag        - tag + calc count (รวมกัน)
     *   activeTag       - tag + calc ที่ status != INACTIVE
     *   totalHitRate    - message success count (รวม TAG + CALC สัปดาห์นี้)
     *   totalFailRate   - message failed count
     *   hitRatePercent  - % hit rate
     */
    @GetMapping("/shippers")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllShipperStats() {
        List<Map<String, Object>> stats = reportService.getAllShipperStats();
        return ResponseEntity.ok(ApiResponse.success(stats, "Report stats retrieved successfully"));
    }

    /**
     * GET /api/report/shippers/{shipperId}
     * Returns stats for a single shipper (used by ReportDetails page).
     */
    @GetMapping("/shippers/{shipperId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getShipperStats(
            @PathVariable String shipperId) {
        try {
            Map<String, Object> stats = reportService.getShipperStats(shipperId);
            return ResponseEntity.ok(ApiResponse.success(stats, "Shipper report stats retrieved successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * GET /api/report/shippers/{shipperId}/history
     * Returns up to 7 days of daily history for the shipper.
     */
    @GetMapping("/shippers/{shipperId}/history")
    public ResponseEntity<ApiResponse<List<ShipperDailyStats>>> getShipperHistory(
            @PathVariable String shipperId) {
        try {
            List<ShipperDailyStats> history = reportService.getShipperHistory(shipperId);
            return ResponseEntity.ok(ApiResponse.success(history, "Shipper history retrieved successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}

package com.ptt.gateway.controller;

import com.ptt.gateway.dto.ApiResponse;
import com.ptt.gateway.model.DashboardFailRate;
import com.ptt.gateway.model.Status;
import com.ptt.gateway.repository.CalcRepository;
import com.ptt.gateway.repository.DashboardFailRateRepository;
import com.ptt.gateway.repository.TagRepository;
import static com.ptt.gateway.util.LogSanitizer.sanitize;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final TagRepository tagRepository;
    private final CalcRepository calcRepository;
    private final DashboardFailRateRepository failRateRepository;

    /**
     * GET /api/dashboard/stats
     * Returns tag and calc counts grouped by Available / Active / Usage.
     *
     * Available = all records
     * Active = status != INACTIVE
     * Usage = status IN (ONLINE, DELAY)
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        List<Status> usageStatuses = List.of(Status.ONLINE, Status.DELAY);

        long tagAvailable = tagRepository.count();
        long tagActive = tagRepository.countByStatusNot(Status.INACTIVE);
        long tagUsage = tagRepository.countByStatusIn(usageStatuses);

        long calcAvailable = calcRepository.count();
        long calcActive = calcRepository.countByStatusNot(Status.INACTIVE);
        long calcUsage = calcRepository.countByStatusIn(usageStatuses);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("tagAvailable", tagAvailable);
        stats.put("tagActive", tagActive);
        stats.put("tagUsage", tagUsage);
        stats.put("calcAvailable", calcAvailable);
        stats.put("calcActive", calcActive);
        stats.put("calcUsage", calcUsage);

        log.debug("Dashboard stats: {}", sanitize(stats));
        return ResponseEntity.ok(ApiResponse.success(stats, "Dashboard stats retrieved successfully"));
    }

    /**
     * GET /api/dashboard/fail-rate
     * Returns current weekly fail-rate for Tag and Calc.
     * Use this once on page load; real-time updates come via WebSocket
     * /topic/fail-rate.
     */
    @GetMapping("/fail-rate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFailRate() {
        Map<String, Object> result = new LinkedHashMap<>();

        for (DashboardFailRate row : failRateRepository.findAll()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("messagesIn", row.getMessagesIn());
            entry.put("failed", row.getFailed());
            entry.put("failRate", Math.round(row.getFailRate() * 100.0) / 100.0);
            entry.put("weekStart", row.getWeekStart() != null ? row.getWeekStart().toString() : null);
            entry.put("updatedAt", row.getUpdatedAt() != null ? row.getUpdatedAt().toString() : null);
            result.put(row.getType().toLowerCase(), entry);
        }

        log.debug("Dashboard fail-rate: {}", sanitize(result));
        return ResponseEntity.ok(ApiResponse.success(result, "Dashboard fail-rate retrieved successfully"));
    }
}

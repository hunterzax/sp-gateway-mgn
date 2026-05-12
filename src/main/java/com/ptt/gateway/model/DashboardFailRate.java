package com.ptt.gateway.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Persists weekly Kafka fail-rate metrics for Tag and Calc processing.
 *
 * Only 2 rows ever exist in this table (type = 'TAG' | 'CALC').
 * Counters are accumulated in-memory (via FailRateService) and
 * flushed here on every successful Kafka batch.
 * Rows are reset every Monday 00:00 via a scheduled job.
 */
@Entity
@Table(name = "dashboard_fail_rate")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardFailRate {

    /** 'TAG' or 'CALC' */
    @Id
    @Column(name = "type", length = 10)
    private String type;

    /** Start of the current tracking week (Monday) */
    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "messages_in", nullable = false)
    private long messagesIn;

    @Column(name = "failed", nullable = false)
    private long failed;

    /** Pre-computed fail rate % = failed / (messagesIn + failed) * 100 */
    @Column(name = "fail_rate", nullable = false)
    private double failRate;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

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

@Entity
@Table(name = "shipper_fail_rate")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipperFailRate {

    /**
     * Primary Key: The Shipper ID
     */
    @Id
    @Column(name = "shipper_id", length = 50, nullable = false)
    private String shipperId;

    /**
     * Total messages successfully processed for tags/calcs linked to this shipper.
     */
    @Column(name = "messages_in", nullable = false)
    private Long messagesIn;

    /**
     * Total messages failed (not found, parse error, or INACTIVE status) for this shipper.
     */
    @Column(name = "failed", nullable = false)
    private Long failed;

    /**
     * Fail Rate % = (failed / (messagesIn + failed)) * 100
     */
    @Column(name = "fail_rate", nullable = false)
    private Double failRate;

    /**
     * Keep track of when this week started, used to reset counters every Monday 00:00.
     */
    @Column(name = "week_start")
    private LocalDate weekStart;

    /**
     * Last update timestamp.
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

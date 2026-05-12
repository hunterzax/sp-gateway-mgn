package com.ptt.gateway.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "shipper_daily_stats")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipperDailyStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shipper_id", nullable = false)
    private String shipperId;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Column(name = "messages_in")
    private Long messagesIn;

    @Column(name = "failed")
    private Long failed;
}

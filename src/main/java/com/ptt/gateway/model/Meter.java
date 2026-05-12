package com.ptt.gateway.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Table(name = "meters")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Meter {

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    @Id
    @Column(name = "meter_id", nullable = false, unique = true)
    private String meterId;

    @Column(name = "meter_name")
    private String meterName;

    @Column(name = "deactivation_date")
    private LocalDateTime deactivationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private MeterStatus status;
}

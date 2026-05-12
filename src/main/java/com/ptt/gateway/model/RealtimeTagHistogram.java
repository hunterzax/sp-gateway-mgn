package com.ptt.gateway.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "realtime_tag_histogram")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RealtimeTagHistogram {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tag_id", nullable = false)
    private String tagID; // Reference to Tag OR Calc ID

    @Column(name = "cur_value")
    private Double curValue;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;
}

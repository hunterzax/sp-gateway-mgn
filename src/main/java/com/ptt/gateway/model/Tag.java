package com.ptt.gateway.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tags")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tag {

    @Id
    @Column(name = "tag_id", unique = true, nullable = false)
    private String tagID;

    @Column(name = "description", unique = true)
    private String description;

    @Column(name = "scada_tag")
    private String scadaTag;

    @Column(name = "rtu_name")
    private String rtuName;

    @ManyToOne
    @JoinColumn(name = "meter_id")
    private Meter meter;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Status status;

    @Column(name = "point_value")
    private Double pointValue;

    @Column(name = "min_value")
    private Double minValue;

    @Column(name = "max_value")
    private Double maxValue;

    @Column(name = "first_activation_date")
    private java.time.LocalDateTime firstActivationDate;

    @Column(name = "deactivate_date")
    private java.time.LocalDateTime deactivateDate;

    @Column(name = "last_data_date")
    private java.time.LocalDateTime lastDataDate;

    @Version
    private Long version;
}

package com.ptt.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CalcCreateDTO {
    private String description;
    private String tag;
    private String meter;
    private Double pointValue;
    private Double minValue;
    private Double maxValue;
    private java.time.LocalDateTime firstActivationDate;
    private java.time.LocalDateTime deactivateDate;
    private String accumulatePeriod;
    private Boolean accumulateActive;
}

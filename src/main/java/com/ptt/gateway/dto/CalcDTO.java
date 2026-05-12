package com.ptt.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CalcDTO {
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private String tagID;
    private String description;
    private String tag;
    private com.ptt.gateway.model.Status status;
    private String meter;
    private Double pointValue;
    private Double minValue;
    private Double maxValue;
    private java.time.LocalDateTime firstActivationDate;
    private java.time.LocalDateTime deactivateDate;
    private String accumulatePeriod;
    private Boolean accumulateActive;
}

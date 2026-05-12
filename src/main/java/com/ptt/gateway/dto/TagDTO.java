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
public class TagDTO {
    @io.swagger.v3.oas.annotations.media.Schema(accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY)
    private String tagID;
    private String description;
    private String scadaTag;
    private String rtuName;
    private String meter;
    private com.ptt.gateway.model.Status status;
    private Double pointValue;
    private java.time.LocalDateTime firstActivationDate;
    private java.time.LocalDateTime deactivateDate;
    private Double minValue;
    private Double maxValue;
}

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
public class TagCreateDTO {
    private String description;
    private String scadaTag;
    private String rtuName;
    private String meter;
    private Double pointValue;
    private java.time.LocalDateTime firstActivationDate;
    private java.time.LocalDateTime deactivateDate;
}

package com.ptt.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for creating a new meter.
 * Only exposes fields that are safe for the client to set,
 * preventing mass-assignment of entity internals.
 * (CWE-915 remediation)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MeterCreateDTO {
    private String meterId;
    private String meterName;
    private LocalDateTime deactivationDate;
}

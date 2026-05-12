package com.ptt.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractListDTO {
    private Long contractID;
    private List<String> entryPoint;
    private List<String> exitPoint;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private List<EntryExitPointListDTO> entryExitPoints;
}

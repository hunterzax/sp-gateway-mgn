package com.ptt.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShipperManagementDTO {
    private String shipperID;
    private String shipperName;
    private String initials;
    private String shipperShortName;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean autoDeactivateDataLink;
    private List<ContractListDTO> contracts;
    private List<ContactListDTO> contacts;
    private List<ShipperTagsLinkDTO> tagsLinks;
    private List<ShipperCalcLinkDTO> calcLinks;
}

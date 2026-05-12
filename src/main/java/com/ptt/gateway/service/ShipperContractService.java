package com.ptt.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptt.gateway.dto.ContractListDTO;
import com.ptt.gateway.dto.EntryExitPointListDTO;
import com.ptt.gateway.model.AuditLog;
import com.ptt.gateway.model.ContractList;
import com.ptt.gateway.model.EntryExitPointList;
import com.ptt.gateway.model.ShipperManagement;
import com.ptt.gateway.repository.ContractListRepository;
import com.ptt.gateway.repository.ShipperRepository;
import com.ptt.gateway.util.Audited;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShipperContractService {

    private final ContractListRepository contractListRepository;
    private final ShipperRepository shipperRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ContractListDTO> getContractsByShipper(String shipperID) {
        return contractListRepository.findAllByShipperManagement_ShipperID(shipperID).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Audited(action = "addContractToShipper", descriptionTemplate = "{user} adds Contract to shipper : {resource}", resourceParam = "shipperID", resultIdField = "contractID")
    @Transactional
    public ContractListDTO addContract(String shipperID, ContractListDTO dto) {
        ShipperManagement shipper = shipperRepository.findById(shipperID)
                .orElseThrow(() -> new RuntimeException("Shipper not found: " + shipperID));

        ContractList contract = new ContractList();
        contract.setShipperManagement(shipper);
        contract.setStartDate(dto.getStartDate());
        contract.setEndDate(dto.getEndDate());
        contract.setStatus(dto.getStatus());

        if (dto.getEntryExitPoints() != null) {
            List<EntryExitPointList> points = dto.getEntryExitPoints().stream().map(pDto -> {
                EntryExitPointList p = new EntryExitPointList();
                p.setContractList(contract);
                p.setPointName(pDto.getPointName());
                p.setType(pDto.getType());
                return p;
            }).collect(Collectors.toList());
            contract.setEntryExitPoints(points);
        }

        ContractList saved = contractListRepository.save(contract);
        return mapToDTO(saved);
    }

    @Transactional
    public ContractListDTO updateContract(Long id, ContractListDTO dto) {
        ContractList contract = contractListRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contract not found: " + id));

        // Snapshot before
        Object beforeStartDate = contract.getStartDate();
        Object beforeEndDate = contract.getEndDate();
        Object beforeStatus = contract.getStatus();
        String shipperId = contract.getShipperManagement() != null
                ? contract.getShipperManagement().getShipperID()
                : "unknown";

        if (dto.getStartDate() != null)
            contract.setStartDate(dto.getStartDate());
        if (dto.getEndDate() != null)
            contract.setEndDate(dto.getEndDate());
        if (dto.getStatus() != null)
            contract.setStatus(dto.getStatus());

        if (dto.getEntryExitPoints() != null) {
            contract.getEntryExitPoints().clear();
            List<EntryExitPointList> points = dto.getEntryExitPoints().stream().map(pDto -> {
                EntryExitPointList p = new EntryExitPointList();
                p.setContractList(contract);
                p.setPointName(pDto.getPointName());
                p.setType(pDto.getType());
                return p;
            }).collect(Collectors.toList());
            contract.getEntryExitPoints().addAll(points);
        }

        ContractList updated = contractListRepository.save(contract);

        // Audit log with before/after metadata
        try {
            String username = resolveUsername();
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("contractId", id);
            meta.put("shipperId", shipperId);
            meta.put("startDate", Map.of("before", beforeStartDate, "after", updated.getStartDate()));
            meta.put("endDate", Map.of("before", beforeEndDate, "after", updated.getEndDate()));
            meta.put("status", Map.of("before", beforeStatus, "after", updated.getStatus()));
            String metadata = objectMapper.writeValueAsString(meta);
            auditLogService.log(AuditLog.builder()
                    .action("updateContract")
                    .description(username + " edits Contract : " + id)
                    .logType("ACTION")
                    .severity("INFO")
                    .source("BACKEND")
                    .createdBy(username)
                    .statusType("Action")
                    .metadata(metadata)
                    .build());
        } catch (Exception e) {
            log.warn("[AuditLog] Could not log updateContract: {}", e.getMessage());
        }

        return mapToDTO(updated);
    }

    @Transactional
    public void deleteContract(Long id) {
        ContractList contract = contractListRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contract not found: " + id));

        String shipperId = contract.getShipperManagement() != null
                ? contract.getShipperManagement().getShipperID()
                : "unknown";
        Object startDate = contract.getStartDate();
        Object endDate = contract.getEndDate();
        Object status = contract.getStatus();

        contractListRepository.deleteById(id);

        // Audit log
        try {
            String username = resolveUsername();
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("contractId", id);
            meta.put("shipperId", shipperId);
            meta.put("startDate", startDate);
            meta.put("endDate", endDate);
            meta.put("status", status);
            String metadata = objectMapper.writeValueAsString(meta);
            auditLogService.log(AuditLog.builder()
                    .action("deleteContractFromShipper")
                    .description(username + " removes Contract from shipper : " + shipperId)
                    .logType("ACTION")
                    .severity("WARNING")
                    .source("BACKEND")
                    .createdBy(username)
                    .statusType("Delete")
                    .metadata(metadata)
                    .build());
        } catch (Exception e) {
            log.warn("[AuditLog] Could not log deleteContract: {}", e.getMessage());
        }
    }

    private String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : "anonymous";
    }

    private ContractListDTO mapToDTO(ContractList c) {
        return ContractListDTO.builder()
                .contractID(c.getContractID())
                .startDate(c.getStartDate())
                .endDate(c.getEndDate())
                .status(c.getStatus())
                .entryExitPoints(c.getEntryExitPoints().stream().map(p -> EntryExitPointListDTO.builder()
                        .id(p.getId())
                        .pointName(p.getPointName())
                        .type(p.getType())
                        .build()).collect(Collectors.toList()))
                .build();
    }
}

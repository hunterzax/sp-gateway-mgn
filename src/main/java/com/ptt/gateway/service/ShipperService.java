package com.ptt.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptt.gateway.dto.*;
import com.ptt.gateway.model.*;
import com.ptt.gateway.repository.*;
import com.ptt.gateway.util.AuditMetadataBuilder;
import com.ptt.gateway.util.Audited;
import static com.ptt.gateway.util.LogSanitizer.sanitize;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShipperService {

    private final ShipperRepository shipperRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Audited(action = "createShipper", descriptionTemplate = "{user} adds shipper")
    @Transactional
    public ShipperManagementDTO createShipper(ShipperManagementDTO dto) {
        String nextId = generateNextShipperID();
        log.info("Generating new Shipper ID: {}", sanitize(nextId));

        ShipperManagement shipper = new ShipperManagement();
        shipper.setShipperID(nextId);
        shipper.setShipperName(dto.getShipperName());
        shipper.setInitials(dto.getInitials());
        shipper.setShipperShortName(dto.getShipperShortName());
        shipper.setStatus(dto.getStatus());
        shipper.setStartDate(dto.getStartDate());
        shipper.setEndDate(dto.getEndDate());
        shipper.setAutoDeactivateDataLink(dto.getAutoDeactivateDataLink());

        // Process children
        if (dto.getContracts() != null) {
            List<ContractList> contracts = dto.getContracts().stream().map(cDto -> {
                ContractList c = new ContractList();
                c.setShipperManagement(shipper);
                c.setStartDate(cDto.getStartDate());
                c.setEndDate(cDto.getEndDate());
                c.setStatus(cDto.getStatus());

                if (cDto.getEntryExitPoints() != null) {
                    List<EntryExitPointList> points = cDto.getEntryExitPoints().stream().map(pDto -> {
                        EntryExitPointList p = new EntryExitPointList();
                        p.setContractList(c);
                        p.setPointName(pDto.getPointName());
                        p.setType(pDto.getType());
                        return p;
                    }).collect(Collectors.toList());
                    c.setEntryExitPoints(points);
                }
                return c;
            }).collect(Collectors.toList());
            shipper.setContracts(contracts);
        }

        if (dto.getContacts() != null) {
            List<ContactList> contacts = dto.getContacts().stream().map(cDto -> {
                ContactList c = new ContactList();
                c.setShipperManagement(shipper);
                c.setFullName(cDto.getFullName());
                c.setSurName(cDto.getSurName());
                c.setEmail(cDto.getEmail());
                c.setPhoneNum(cDto.getPhoneNum());
                c.setStatus(cDto.getStatus());
                return c;
            }).collect(Collectors.toList());
            shipper.setContacts(contacts);
        }

        if (dto.getTagsLinks() != null) {
            List<ShipperTagsLink> tags = dto.getTagsLinks().stream().map(tDto -> {
                ShipperTagsLink t = new ShipperTagsLink();
                t.setShipperManagement(shipper);
                t.setTagID(tDto.getTagID());
                return t;
            }).collect(Collectors.toList());
            shipper.setTagsLinks(tags);
        }

        if (dto.getCalcLinks() != null) {
            List<ShipperCalcLink> calcs = dto.getCalcLinks().stream().map(cDto -> {
                ShipperCalcLink c = new ShipperCalcLink();
                c.setShipperManagement(shipper);
                c.setTagID(cDto.getTagID());
                return c;
            }).collect(Collectors.toList());
            shipper.setCalcLinks(calcs);
        }

        ShipperManagement saved = shipperRepository.save(shipper);
        return mapToDTO(saved);
    }

    @Transactional
    public ShipperManagementDTO updateShipper(String id, ShipperManagementDTO dto) {
        ShipperManagement shipper = shipperRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Shipper not found: " + id));

        // Snapshot before
        String beforeName = shipper.getShipperName();
        String beforeInitials = shipper.getInitials();
        String beforeShortName = shipper.getShipperShortName();
        Object beforeStatus = shipper.getStatus();
        Object beforeStart = shipper.getStartDate();
        Object beforeEnd = shipper.getEndDate();
        Boolean beforeAutoDeac = shipper.getAutoDeactivateDataLink();

        // Apply partial update
        if (dto.getShipperName() != null)
            shipper.setShipperName(dto.getShipperName());
        if (dto.getInitials() != null)
            shipper.setInitials(dto.getInitials());
        if (dto.getShipperShortName() != null)
            shipper.setShipperShortName(dto.getShipperShortName());
        if (dto.getStatus() != null)
            shipper.setStatus(dto.getStatus());
        if (dto.getStartDate() != null)
            shipper.setStartDate(dto.getStartDate());
        if (dto.getEndDate() != null)
            shipper.setEndDate(dto.getEndDate());
        if (dto.getAutoDeactivateDataLink() != null)
            shipper.setAutoDeactivateDataLink(dto.getAutoDeactivateDataLink());

        ShipperManagement saved = shipperRepository.save(shipper);

        // Audit log with before/after metadata
        try {
            String username = resolveUsername();
            String metadata = new AuditMetadataBuilder(objectMapper)
                    .add("shipperName", beforeName, saved.getShipperName())
                    .add("initials", beforeInitials, saved.getInitials())
                    .add("shipperShortName", beforeShortName, saved.getShipperShortName())
                    .add("status", beforeStatus, saved.getStatus())
                    .add("startDate", beforeStart, saved.getStartDate())
                    .add("endDate", beforeEnd, saved.getEndDate())
                    .add("autoDeactivateDataLink", beforeAutoDeac, saved.getAutoDeactivateDataLink())
                    .build();
            auditLogService.log(com.ptt.gateway.model.AuditLog.builder()
                    .action("updateShipper")
                    .description(username + " edits shipper : " + id)
                    .logType("ACTION")
                    .severity("INFO")
                    .source("BACKEND")
                    .createdBy(username)
                    .statusType("Action")
                    .metadata(metadata)
                    .build());
        } catch (Exception e) {
            log.warn("[AuditLog] Could not build shipper audit metadata: {}", e.getMessage());
        }

        return mapToDTO(saved);
    }

    @Transactional
    public void deleteShipper(String id) {
        ShipperManagement shipper = shipperRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Shipper not found: " + id));

        // Snapshot before delete
        String shipperName = shipper.getShipperName();
        String initials = shipper.getInitials();
        String shipperShortName = shipper.getShipperShortName();
        Object status = shipper.getStatus();
        Object startDate = shipper.getStartDate();
        Object endDate = shipper.getEndDate();
        Boolean autoDeac = shipper.getAutoDeactivateDataLink();

        shipperRepository.deleteById(id);

        // Audit log with pre-delete snapshot
        try {
            String username = resolveUsername();
            java.util.Map<String, Object> meta = new java.util.LinkedHashMap<>();
            meta.put("shipperID", id);
            meta.put("shipperName", shipperName);
            meta.put("initials", initials);
            meta.put("shipperShortName", shipperShortName);
            meta.put("status", status);
            meta.put("startDate", startDate);
            meta.put("endDate", endDate);
            meta.put("autoDeactivateDataLink", autoDeac);
            String metadata = objectMapper.writeValueAsString(meta);
            auditLogService.log(com.ptt.gateway.model.AuditLog.builder()
                    .action("deleteShipper")
                    .description(username + " deletes shipper: " + id)
                    .logType("ACTION")
                    .severity("WARNING")
                    .source("BACKEND")
                    .createdBy(username)
                    .statusType("Delete")
                    .metadata(metadata)
                    .build());
        } catch (Exception e) {
            log.warn("[AuditLog] Could not log deleteShipper: {}", e.getMessage());
        }
    }

    public synchronized String generateNextShipperID() {
        Optional<String> lastIdOpt = shipperRepository.findLastShipperID();
        if (lastIdOpt.isPresent()) {
            String lastId = lastIdOpt.get();
            try {
                int idNum = Integer.parseInt(lastId.substring(1));
                return String.format("S%03d", idNum + 1);
            } catch (NumberFormatException e) {
                log.error("Failed to parse last shipper ID: {}", sanitize(lastId));
                return "S001"; // Fallback
            }
        }
        return "S001";
    }

    @Transactional(readOnly = true)
    public Page<ShipperManagementDTO> smartSearch(String query, Pageable pageable) {
        return shipperRepository.smartSearch(query, pageable).map(this::mapToDTO);
    }

    @Transactional(readOnly = true)
    public List<ShipperManagementDTO> getAllShippers() {
        return shipperRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ShipperManagementDTO getShipper(String id) {
        return shipperRepository.findById(id)
                .map(this::mapToDTO)
                .orElseThrow(() -> new RuntimeException("Shipper not found: " + id));
    }

    private String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : "anonymous";
    }

    private ShipperManagementDTO mapToDTO(ShipperManagement s) {
        return ShipperManagementDTO.builder()
                .shipperID(s.getShipperID())
                .shipperName(s.getShipperName())
                .initials(s.getInitials())
                .shipperShortName(s.getShipperShortName())
                .status(s.getStatus())
                .startDate(s.getStartDate())
                .endDate(s.getEndDate())
                .autoDeactivateDataLink(s.getAutoDeactivateDataLink())
                .contracts(s.getContracts().stream().map(c -> ContractListDTO.builder()
                        .contractID(c.getContractID())
                        .startDate(c.getStartDate())
                        .endDate(c.getEndDate())
                        .status(c.getStatus())
                        .entryExitPoints(c.getEntryExitPoints().stream().map(p -> EntryExitPointListDTO.builder()
                                .id(p.getId())
                                .pointName(p.getPointName())
                                .type(p.getType())
                                .build()).collect(Collectors.toList()))
                        .build()).collect(Collectors.toList()))
                .contacts(s.getContacts().stream().map(c -> ContactListDTO.builder()
                        .contactID(c.getContactID())
                        .fullName(c.getFullName())
                        .surName(c.getSurName())
                        .email(c.getEmail())
                        .phoneNum(c.getPhoneNum())
                        .status(c.getStatus())
                        .build()).collect(Collectors.toList()))
                .tagsLinks(s.getTagsLinks().stream().map(t -> ShipperTagsLinkDTO.builder()
                        .id(t.getId())
                        .tagID(t.getTagID())
                        .build()).collect(Collectors.toList()))
                .calcLinks(s.getCalcLinks().stream().map(c -> ShipperCalcLinkDTO.builder()
                        .id(c.getId())
                        .tagID(c.getTagID())
                        .build()).collect(Collectors.toList()))
                .build();
    }
}

package com.ptt.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptt.gateway.dto.ShipperCalcLinkDTO;
import com.ptt.gateway.model.AuditLog;
import com.ptt.gateway.model.ShipperCalcLink;
import com.ptt.gateway.model.ShipperManagement;
import com.ptt.gateway.repository.ShipperCalcLinkRepository;
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
public class ShipperCalcLinkService {

    private final ShipperCalcLinkRepository calcLinkRepository;
    private final ShipperRepository shipperRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ShipperCalcLinkDTO> getCalcsByShipper(String shipperID) {
        return calcLinkRepository.findAllByShipperManagement_ShipperID(shipperID).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Audited(action = "addCalcToShipper", descriptionTemplate = "{user} adds Calc to shipper : {resource}", resourceParam = "shipperID")
    @Transactional
    public ShipperCalcLinkDTO addCalcLink(String shipperID, ShipperCalcLinkDTO dto) {
        ShipperManagement shipper = shipperRepository.findById(shipperID)
                .orElseThrow(() -> new RuntimeException("Shipper not found: " + shipperID));

        ShipperCalcLink calcLink = new ShipperCalcLink();
        calcLink.setShipperManagement(shipper);
        calcLink.setTagID(dto.getTagID());

        ShipperCalcLink saved = calcLinkRepository.save(calcLink);
        return mapToDTO(saved);
    }

    @Transactional
    public void deleteCalcLink(String id) {
        ShipperCalcLink calcLink = calcLinkRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Calc link not found: " + id));

        String shipperId = calcLink.getShipperManagement() != null
                ? calcLink.getShipperManagement().getShipperID()
                : "unknown";
        String calcId = calcLink.getTagID();

        calcLinkRepository.deleteById(id);

        // Audit log
        try {
            String username = resolveUsername();
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("calcId", calcId);
            meta.put("linkId", id);
            meta.put("shipperId", shipperId);
            String metadata = objectMapper.writeValueAsString(meta);
            auditLogService.log(AuditLog.builder()
                    .action("removeCalcFromShipper")
                    .description(username + " removes Calc from shipper : " + shipperId)
                    .logType("ACTION")
                    .severity("WARNING")
                    .source("BACKEND")
                    .createdBy(username)
                    .statusType("Delete")
                    .metadata(metadata)
                    .build());
        } catch (Exception e) {
            log.warn("[AuditLog] Could not log deleteCalcLink: {}", e.getMessage());
        }
    }

    private String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : "anonymous";
    }

    private ShipperCalcLinkDTO mapToDTO(ShipperCalcLink c) {
        return ShipperCalcLinkDTO.builder()
                .id(c.getId())
                .tagID(c.getTagID())
                .build();
    }
}

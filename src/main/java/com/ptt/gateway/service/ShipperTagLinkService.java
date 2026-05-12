package com.ptt.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptt.gateway.dto.ShipperTagsLinkDTO;
import com.ptt.gateway.model.AuditLog;
import com.ptt.gateway.model.ShipperManagement;
import com.ptt.gateway.model.ShipperTagsLink;
import com.ptt.gateway.repository.ShipperRepository;
import com.ptt.gateway.repository.ShipperTagsLinkRepository;
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
public class ShipperTagLinkService {

    private final ShipperTagsLinkRepository tagsLinkRepository;
    private final ShipperRepository shipperRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ShipperTagsLinkDTO> getTagsByShipper(String shipperID) {
        return tagsLinkRepository.findAllByShipperManagement_ShipperID(shipperID).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Audited(action = "addTagToShipper", descriptionTemplate = "{user} adds Tag to shipper : {resource}", resourceParam = "shipperID")
    @Transactional
    public ShipperTagsLinkDTO addTagLink(String shipperID, ShipperTagsLinkDTO dto) {
        ShipperManagement shipper = shipperRepository.findById(shipperID)
                .orElseThrow(() -> new RuntimeException("Shipper not found: " + shipperID));

        ShipperTagsLink tagLink = new ShipperTagsLink();
        tagLink.setShipperManagement(shipper);
        tagLink.setTagID(dto.getTagID());

        ShipperTagsLink saved = tagsLinkRepository.save(tagLink);
        return mapToDTO(saved);
    }

    @Transactional
    public void deleteTagLink(String id) {
        ShipperTagsLink tagLink = tagsLinkRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tag link not found: " + id));

        String shipperId = tagLink.getShipperManagement() != null
                ? tagLink.getShipperManagement().getShipperID()
                : "unknown";
        String tagId = tagLink.getTagID();

        tagsLinkRepository.deleteById(id);

        // Audit log
        try {
            String username = resolveUsername();
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("tagId", tagId);
            meta.put("linkId", id);
            meta.put("shipperId", shipperId);
            String metadata = objectMapper.writeValueAsString(meta);
            auditLogService.log(AuditLog.builder()
                    .action("removeTagFromShipper")
                    .description(username + " removes Tag from shipper : " + shipperId)
                    .logType("ACTION")
                    .severity("WARNING")
                    .source("BACKEND")
                    .createdBy(username)
                    .statusType("Delete")
                    .metadata(metadata)
                    .build());
        } catch (Exception e) {
            log.warn("[AuditLog] Could not log deleteTagLink: {}", e.getMessage());
        }
    }

    private String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : "anonymous";
    }

    private ShipperTagsLinkDTO mapToDTO(ShipperTagsLink t) {
        return ShipperTagsLinkDTO.builder()
                .id(t.getId())
                .tagID(t.getTagID())
                .build();
    }
}

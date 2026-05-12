package com.ptt.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptt.gateway.dto.ContactListDTO;
import com.ptt.gateway.model.AuditLog;
import com.ptt.gateway.model.ContactList;
import com.ptt.gateway.model.ShipperManagement;
import com.ptt.gateway.repository.ContactListRepository;
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
public class ShipperContactService {

    private final ContactListRepository contactListRepository;
    private final ShipperRepository shipperRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ContactListDTO> getContactsByShipper(String shipperID) {
        return contactListRepository.findAllByShipperManagement_ShipperID(shipperID).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Audited(action = "addContactToShipper", descriptionTemplate = "{user} adds Contact to shipper : {resource}", resourceParam = "shipperID", resultIdField = "contactID")
    @Transactional
    public ContactListDTO addContact(String shipperID, ContactListDTO dto) {
        ShipperManagement shipper = shipperRepository.findById(shipperID)
                .orElseThrow(() -> new RuntimeException("Shipper not found: " + shipperID));

        ContactList contact = new ContactList();
        contact.setShipperManagement(shipper);
        contact.setFullName(dto.getFullName());
        contact.setSurName(dto.getSurName());
        contact.setEmail(dto.getEmail());
        contact.setPhoneNum(dto.getPhoneNum());
        contact.setStatus(dto.getStatus());

        ContactList saved = contactListRepository.save(contact);
        return mapToDTO(saved);
    }

    @Transactional
    public ContactListDTO updateContact(String id, ContactListDTO dto) {
        ContactList contact = contactListRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contact not found: " + id));

        // Snapshot before
        String beforeFullName = contact.getFullName();
        String beforeSurName = contact.getSurName();
        String beforeEmail = contact.getEmail();
        String beforePhoneNum = contact.getPhoneNum();
        Object beforeStatus = contact.getStatus();
        String shipperId = contact.getShipperManagement() != null
                ? contact.getShipperManagement().getShipperID()
                : "unknown";

        if (dto.getFullName() != null)
            contact.setFullName(dto.getFullName());
        if (dto.getSurName() != null)
            contact.setSurName(dto.getSurName());
        if (dto.getEmail() != null)
            contact.setEmail(dto.getEmail());
        if (dto.getPhoneNum() != null)
            contact.setPhoneNum(dto.getPhoneNum());
        if (dto.getStatus() != null)
            contact.setStatus(dto.getStatus());

        ContactList updated = contactListRepository.save(contact);

        // Audit log with before/after metadata
        try {
            String username = resolveUsername();
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("contactId", id);
            meta.put("shipperId", shipperId);
            meta.put("fullName", Map.of("before", beforeFullName, "after", updated.getFullName()));
            meta.put("surName", Map.of("before", beforeSurName, "after", updated.getSurName()));
            meta.put("email", Map.of("before", beforeEmail, "after", updated.getEmail()));
            meta.put("phoneNum", Map.of("before", beforePhoneNum, "after", updated.getPhoneNum()));
            meta.put("status", Map.of("before", beforeStatus, "after", updated.getStatus()));
            String metadata = objectMapper.writeValueAsString(meta);
            auditLogService.log(AuditLog.builder()
                    .action("updateContact")
                    .description(username + " edits Contact : " + id)
                    .logType("ACTION")
                    .severity("INFO")
                    .source("BACKEND")
                    .createdBy(username)
                    .statusType("Action")
                    .metadata(metadata)
                    .build());
        } catch (Exception e) {
            log.warn("[AuditLog] Could not log updateContact: {}", e.getMessage());
        }

        return mapToDTO(updated);
    }

    @Transactional
    public void deleteContact(String id) {
        ContactList contact = contactListRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contact not found: " + id));

        String shipperId = contact.getShipperManagement() != null
                ? contact.getShipperManagement().getShipperID()
                : "unknown";
        String fullName = contact.getFullName();
        String email = contact.getEmail();

        contactListRepository.deleteById(id);

        // Audit log
        try {
            String username = resolveUsername();
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("contactId", id);
            meta.put("shipperId", shipperId);
            meta.put("fullName", fullName);
            meta.put("email", email);
            String metadata = objectMapper.writeValueAsString(meta);
            auditLogService.log(AuditLog.builder()
                    .action("deleteContactFromShipper")
                    .description(username + " removes Contact from shipper : " + shipperId)
                    .logType("ACTION")
                    .severity("WARNING")
                    .source("BACKEND")
                    .createdBy(username)
                    .statusType("Delete")
                    .metadata(metadata)
                    .build());
        } catch (Exception e) {
            log.warn("[AuditLog] Could not log deleteContact: {}", e.getMessage());
        }
    }

    private String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : "anonymous";
    }

    private ContactListDTO mapToDTO(ContactList c) {
        return ContactListDTO.builder()
                .contactID(c.getContactID())
                .fullName(c.getFullName())
                .surName(c.getSurName())
                .email(c.getEmail())
                .phoneNum(c.getPhoneNum())
                .status(c.getStatus())
                .build();
    }
}

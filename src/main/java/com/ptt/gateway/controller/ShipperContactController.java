package com.ptt.gateway.controller;

import com.ptt.gateway.dto.ApiResponse;
import com.ptt.gateway.dto.ContactListDTO;
import com.ptt.gateway.service.ShipperContactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
@Slf4j
public class ShipperContactController {

    private final ShipperContactService contactService;

    @GetMapping("/shipper/{shipperID}")
    public ResponseEntity<ApiResponse<List<ContactListDTO>>> getContactsByShipper(@PathVariable String shipperID) {
        log.info("Getting contacts for shipper: {}", shipperID);
        List<ContactListDTO> contacts = contactService.getContactsByShipper(shipperID);
        return ResponseEntity.ok(ApiResponse.success(contacts, "Contacts retrieved successfully"));
    }

    @PostMapping("/shipper/{shipperID}")
    public ResponseEntity<ApiResponse<ContactListDTO>> addContact(@PathVariable String shipperID,
            @RequestBody ContactListDTO dto) {
        log.info("Adding contact for shipper: {}", shipperID);
        ContactListDTO created = contactService.addContact(shipperID, dto);
        return ResponseEntity.status(201).body(ApiResponse.success(created, "Contact added successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ContactListDTO>> updateContact(@PathVariable String id,
            @RequestBody ContactListDTO dto) {
        log.info("Updating contact: {}", id);
        ContactListDTO updated = contactService.updateContact(id, dto);
        return ResponseEntity.ok(ApiResponse.success(updated, "Contact updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteContact(@PathVariable String id) {
        log.info("Deleting contact: {}", id);
        contactService.deleteContact(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Contact deleted successfully"));
    }
}

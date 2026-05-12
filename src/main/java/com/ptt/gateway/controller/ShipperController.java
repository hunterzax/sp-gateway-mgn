package com.ptt.gateway.controller;

import com.ptt.gateway.dto.ApiResponse;
import com.ptt.gateway.dto.PaginationDTO;
import com.ptt.gateway.dto.ShipperManagementDTO;
import com.ptt.gateway.service.ShipperService;
import static com.ptt.gateway.util.LogSanitizer.sanitize;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shippers")
@RequiredArgsConstructor
@Slf4j
public class ShipperController {

    private final ShipperService shipperService;

    @PostMapping
    public ResponseEntity<ApiResponse<ShipperManagementDTO>> createShipper(@RequestBody ShipperManagementDTO dto) {
        log.info("Creating shipper: {}", sanitize(dto.getShipperName()));
        ShipperManagementDTO created = shipperService.createShipper(dto);
        return ResponseEntity.status(201).body(ApiResponse.success(created, "Shipper created successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ShipperManagementDTO>>> getAllShippers() {
        log.info("Getting all shippers");
        List<ShipperManagementDTO> shippers = shipperService.getAllShippers();
        return ResponseEntity.ok(ApiResponse.success(shippers, "All shippers retrieved successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ShipperManagementDTO>> getShipper(@PathVariable String id) {
        log.info("Getting shipper: {}", sanitize(id));
        ShipperManagementDTO shipper = shipperService.getShipper(id);
        return ResponseEntity.ok(ApiResponse.success(shipper, "Shipper retrieved successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ShipperManagementDTO>> updateShipper(@PathVariable String id,
            @RequestBody ShipperManagementDTO dto) {
        log.info("Updating shipper: {}", sanitize(id));
        ShipperManagementDTO updated = shipperService.updateShipper(id, dto);
        return ResponseEntity.ok(ApiResponse.success(updated, "Shipper updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteShipper(@PathVariable String id) {
        log.info("Deleting shipper: {}", sanitize(id));
        shipperService.deleteShipper(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Shipper deleted successfully"));
    }

    @GetMapping("/smart")
    public ResponseEntity<ApiResponse<List<ShipperManagementDTO>>> smartSearch(
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("Smart Search Shippers with filter: {}", sanitize(filter));
        Page<ShipperManagementDTO> pageResult = shipperService.smartSearch(filter, PageRequest.of(page - 1, size));

        PaginationDTO pagination = PaginationDTO.builder()
                .page(pageResult.getNumber() + 1)
                .limit(pageResult.getSize())
                .totalData(pageResult.getTotalElements())
                .totalPage(pageResult.getTotalPages())
                .build();

        return ResponseEntity.ok(ApiResponse.success(pageResult.getContent(), "Search results retrieved", pagination));
    }
}

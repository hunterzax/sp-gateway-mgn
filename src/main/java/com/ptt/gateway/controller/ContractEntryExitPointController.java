package com.ptt.gateway.controller;

import com.ptt.gateway.dto.ApiResponse;
import com.ptt.gateway.dto.EntryExitPointListDTO;
import com.ptt.gateway.service.ContractEntryExitPointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
@Slf4j
public class ContractEntryExitPointController {

    private final ContractEntryExitPointService pointService;

    @GetMapping("/contract/{contractID}")
    public ResponseEntity<ApiResponse<List<EntryExitPointListDTO>>> getPointsByContract(@PathVariable Long contractID) {
        log.info("Getting points for contract: {}", contractID);
        List<EntryExitPointListDTO> points = pointService.getPointsByContract(contractID);
        return ResponseEntity.ok(ApiResponse.success(points, "Points retrieved successfully"));
    }

    @PostMapping("/contract/{contractID}")
    public ResponseEntity<ApiResponse<EntryExitPointListDTO>> addPoint(@PathVariable Long contractID,
            @RequestBody EntryExitPointListDTO dto) {
        log.info("Adding point for contract: {}", contractID);
        EntryExitPointListDTO created = pointService.addPoint(contractID, dto);
        return ResponseEntity.status(201).body(ApiResponse.success(created, "Point added successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<EntryExitPointListDTO>> updatePoint(@PathVariable String id,
            @RequestBody EntryExitPointListDTO dto) {
        log.info("Updating point: {}", id);
        EntryExitPointListDTO updated = pointService.updatePoint(id, dto);
        return ResponseEntity.ok(ApiResponse.success(updated, "Point updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePoint(@PathVariable String id) {
        log.info("Deleting point: {}", id);
        pointService.deletePoint(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Point deleted successfully"));
    }
}

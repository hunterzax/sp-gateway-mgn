package com.ptt.gateway.controller;

import com.ptt.gateway.dto.ApiResponse;
import com.ptt.gateway.dto.ContractListDTO;
import com.ptt.gateway.service.ShipperContractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
@Slf4j
public class ShipperContractController {

    private final ShipperContractService contractService;

    @GetMapping("/shipper/{shipperID}")
    public ResponseEntity<ApiResponse<List<ContractListDTO>>> getContractsByShipper(@PathVariable String shipperID) {
        log.info("Getting contracts for shipper: {}", shipperID);
        List<ContractListDTO> contracts = contractService.getContractsByShipper(shipperID);
        return ResponseEntity.ok(ApiResponse.success(contracts, "Contracts retrieved successfully"));
    }

    @PostMapping("/shipper/{shipperID}")
    public ResponseEntity<ApiResponse<ContractListDTO>> addContract(@PathVariable String shipperID,
            @RequestBody ContractListDTO dto) {
        log.info("Adding contract for shipper: {}", shipperID);
        ContractListDTO created = contractService.addContract(shipperID, dto);
        return ResponseEntity.status(201).body(ApiResponse.success(created, "Contract added successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ContractListDTO>> updateContract(@PathVariable Long id,
            @RequestBody ContractListDTO dto) {
        log.info("Updating contract: {}", id);
        ContractListDTO updated = contractService.updateContract(id, dto);
        return ResponseEntity.ok(ApiResponse.success(updated, "Contract updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteContract(@PathVariable Long id) {
        log.info("Deleting contract: {}", id);
        contractService.deleteContract(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Contract deleted successfully"));
    }
}

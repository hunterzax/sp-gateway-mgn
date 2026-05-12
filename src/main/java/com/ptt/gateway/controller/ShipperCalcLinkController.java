package com.ptt.gateway.controller;

import com.ptt.gateway.dto.ApiResponse;
import com.ptt.gateway.dto.ShipperCalcLinkDTO;
import com.ptt.gateway.service.ShipperCalcLinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shipper-calcs")
@RequiredArgsConstructor
@Slf4j
public class ShipperCalcLinkController {

    private final ShipperCalcLinkService calcLinkService;

    @GetMapping("/shipper/{shipperID}")
    public ResponseEntity<ApiResponse<List<ShipperCalcLinkDTO>>> getCalcsByShipper(@PathVariable String shipperID) {
        log.info("Getting calcs for shipper: {}", shipperID);
        List<ShipperCalcLinkDTO> calcs = calcLinkService.getCalcsByShipper(shipperID);
        return ResponseEntity.ok(ApiResponse.success(calcs, "Calc links retrieved successfully"));
    }

    @PostMapping("/shipper/{shipperID}")
    public ResponseEntity<ApiResponse<ShipperCalcLinkDTO>> addCalcLink(@PathVariable String shipperID,
            @RequestBody ShipperCalcLinkDTO dto) {
        log.info("Adding calc link for shipper: {}", shipperID);
        ShipperCalcLinkDTO created = calcLinkService.addCalcLink(shipperID, dto);
        return ResponseEntity.status(201).body(ApiResponse.success(created, "Calc link added successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCalcLink(@PathVariable String id) {
        log.info("Deleting calc link: {}", id);
        calcLinkService.deleteCalcLink(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Calc link deleted successfully"));
    }
}

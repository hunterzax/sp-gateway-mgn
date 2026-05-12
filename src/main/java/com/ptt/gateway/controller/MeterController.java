package com.ptt.gateway.controller;

import com.ptt.gateway.dto.ApiResponse;
import com.ptt.gateway.model.Meter;
import com.ptt.gateway.service.DataSourceService;
import static com.ptt.gateway.util.LogSanitizer.sanitize;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.ptt.gateway.dto.MeterUpdateDTO;
import com.ptt.gateway.model.MeterStatus;
import org.springframework.data.domain.Sort;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/data-sources/meter")
@RequiredArgsConstructor
@Slf4j
public class MeterController {

    private final DataSourceService dataSourceService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Meter>>> getAllMeters(
            @RequestParam(name = "status", required = false) MeterStatus status,
            @RequestParam(name = "deactivation_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deactivationDate,
            @RequestParam(name = "sort_by", defaultValue = "meterId") String sortBy,
            @RequestParam(name = "order", defaultValue = "asc") String order) {

        log.info("Fetching all meters with status: {}, date: {}, sort_by: {}, order: {}", status, deactivationDate,
                sanitize(sortBy), sanitize(order));

        Sort.Direction direction = order.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, sortBy);

        List<Meter> meters = dataSourceService.getAllMeters(status, deactivationDate, sort);
        return ResponseEntity.ok(ApiResponse.success(meters, "Meters retrieved successfully"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Meter>> createMeter(@RequestBody com.ptt.gateway.dto.MeterCreateDTO meterDTO) {
        log.info("Creating new meter");
        Meter created = dataSourceService.createMeter(meterDTO);
        return ResponseEntity.status(201).body(ApiResponse.success(created, "Meter created successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Meter>> updateMeter(@PathVariable("id") String id,
            @RequestBody MeterUpdateDTO meterDTO) {
        log.info("Updating meter: {}", sanitize(id));
        Meter updated = dataSourceService.updateMeter(id, meterDTO);
        return ResponseEntity.ok(ApiResponse.success(updated, "Meter updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMeter(@PathVariable("id") String id) {
        log.info("Deleting meter: {}", sanitize(id));
        dataSourceService.deleteMeter(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Meter deleted successfully"));
    }

    @PostMapping("/{id}/switch")
    public ResponseEntity<ApiResponse<Void>> switchMeter(@PathVariable("id") String id,
            @RequestParam(name = "active") boolean active) {
        log.info("Switching meter {} status to active: {}", sanitize(id), active);
        dataSourceService.switchMeterStatus(id, active);
        return ResponseEntity.ok(ApiResponse.success(null, "Meter status switched successfully"));
    }

}

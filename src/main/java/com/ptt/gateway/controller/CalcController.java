package com.ptt.gateway.controller;

import com.ptt.gateway.dto.ApiResponse;
import com.ptt.gateway.dto.CalcDTO;
import com.ptt.gateway.dto.PaginationDTO;
import com.ptt.gateway.service.DataSourceService;
import static com.ptt.gateway.util.LogSanitizer.sanitize;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/data-sources")
@RequiredArgsConstructor
@Slf4j
@Validated
public class CalcController {

    private final DataSourceService dataSourceService;

    /**
     * GET /api/data-sources/calc
     * Get all calc data sources with optional filtering and pagination
     */
    @GetMapping("/calc")
    public ResponseEntity<ApiResponse<List<CalcDTO>>> getCalcDataSources(
            @RequestParam(name = "id", required = false) String id,
            @RequestParam(name = "description", required = false) String description,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {

        log.info("Fetching calc data sources - id: {}, description: {}, page: {}, size: {}",
                sanitize(id), sanitize(description), page, size);

        Page<CalcDTO> calcPage = dataSourceService.getCalcDataSources(id, description, page, size);
        PaginationDTO pagination = dataSourceService.createPagination(calcPage);

        ApiResponse<List<CalcDTO>> response = ApiResponse.success(
                calcPage.getContent(),
                "Calc data sources retrieved successfully",
                pagination);

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/data-sources/calc
     * Create a new calc
     */
    @PostMapping("/calc")
    public ResponseEntity<ApiResponse<CalcDTO>> createCalc(@RequestBody com.ptt.gateway.dto.CalcCreateDTO calcDTO) {
        log.info("Creating new calc with description: {}", sanitize(calcDTO.getDescription()));
        CalcDTO createdCalc = dataSourceService.createCalc(calcDTO);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(ApiResponse.success(createdCalc, "Calc created successfully"));
    }

    /**
     * PUT /api/data-sources/calc/{id}
     * Update an existing calc
     */
    @PutMapping("/calc/{id}")
    public ResponseEntity<ApiResponse<CalcDTO>> updateCalc(
            @PathVariable("id") @Pattern(regexp = "^[a-zA-Z0-9_\\-]{1,100}$", message = "Invalid calc ID format") String id,
            @RequestBody CalcDTO calcDTO) {
        if (calcDTO.getTagID() != null && !id.equals(calcDTO.getTagID())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(org.springframework.http.HttpStatus.BAD_REQUEST.value(),
                            "ID in path does not match ID in request body"));
        }
        log.info("Updating calc with ID: {}", sanitize(id));
        CalcDTO updatedCalc = dataSourceService.updateCalc(id, calcDTO);
        return ResponseEntity.ok(ApiResponse.success(updatedCalc, "Calc updated successfully"));
    }

    /**
     * DELETE /api/data-sources/calc/{id}
     * Delete a calc
     */
    @DeleteMapping("/calc/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCalc(
            @PathVariable("id") @Pattern(regexp = "^[a-zA-Z0-9_\\-]{1,100}$", message = "Invalid calc ID format") String id) {
        log.info("Deleting calc with ID: {}", sanitize(id));
        dataSourceService.deleteCalc(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Calc deleted successfully"));
    }
}

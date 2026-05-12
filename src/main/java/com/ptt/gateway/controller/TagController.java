package com.ptt.gateway.controller;

import com.ptt.gateway.dto.ApiResponse;
import com.ptt.gateway.dto.PaginationDTO;
import com.ptt.gateway.dto.TagDTO;
import com.ptt.gateway.service.DataSourceService;
import static com.ptt.gateway.util.LogSanitizer.sanitize;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/data-sources")
@RequiredArgsConstructor
@Slf4j
public class TagController {

    private final DataSourceService dataSourceService;

    /**
     * GET /api/data-sources/tag
     * Get all tag data sources with optional filtering and pagination
     */
    @GetMapping("/tag")
    public ResponseEntity<ApiResponse<List<TagDTO>>> getTagDataSources(
            @RequestParam(name = "id", required = false) String id,
            @RequestParam(name = "description", required = false) String description,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {

        log.info("Fetching tag data sources - id: {}, description: {}, page: {}, size: {}",
                sanitize(id), sanitize(description), page, size);

        Page<TagDTO> tagPage = dataSourceService.getTagDataSources(id, description, page, size);
        PaginationDTO pagination = dataSourceService.createPagination(tagPage);

        ApiResponse<List<TagDTO>> response = ApiResponse.success(
                tagPage.getContent(),
                "Tag data sources retrieved successfully",
                pagination);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/data-sources/tag/smart
     * Smart search across all tag fields with pagination
     */
    @GetMapping("/tag/smart")
    public ResponseEntity<ApiResponse<List<TagDTO>>> smartSearchTags(
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {

        log.info("Smart searching tags - filter: {}, page: {}, size: {}", sanitize(filter), page, size);

        Page<TagDTO> tagPage = dataSourceService.smartSearchTags(filter, page, size);
        PaginationDTO pagination = dataSourceService.createPagination(tagPage);

        ApiResponse<List<TagDTO>> response = ApiResponse.success(
                tagPage.getContent(),
                "Tags retrieved successfully via smart search",
                pagination);

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/data-sources/tag
     * Create a new tag
     */
    @PostMapping("/tag")
    public ResponseEntity<ApiResponse<TagDTO>> createTag(@RequestBody com.ptt.gateway.dto.TagCreateDTO tagDTO) {
        log.info("Creating new tag");
        TagDTO createdTag = dataSourceService.createTag(tagDTO);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(ApiResponse.success(createdTag, "Tag created successfully"));
    }

    /**
     * PUT /api/data-sources/tag/{id}
     * Update an existing tag
     */
    @PutMapping("/tag/{id}")
    public ResponseEntity<ApiResponse<TagDTO>> updateTag(@PathVariable("id") String id,
            @RequestBody TagDTO tagDTO) {
        log.info("Updating tag with ID: {}", sanitize(id));
        TagDTO updatedTag = dataSourceService.updateTag(id, tagDTO);
        return ResponseEntity.ok(ApiResponse.success(updatedTag, "Tag updated successfully"));
    }

    /**
     * DELETE /api/data-sources/tag/{id}
     * Delete a tag
     */
    @DeleteMapping("/tag/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTag(@PathVariable("id") String id) {
        log.info("Deleting tag with ID: {}", sanitize(id));
        dataSourceService.deleteTag(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Tag deleted successfully"));
    }

    /**
     * GET /api/data-sources/scada-tag
     * Distinct source tag names (tagName, gw) from realtime data.
     */
    @GetMapping("/scada-tag")
    public ResponseEntity<ApiResponse<List<com.ptt.gateway.dto.ScadaTagDTO>>> getScadaTags(
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        log.info("Fetching distinct source tags - filter: {}, page: {}, size: {}", sanitize(filter), page, size);
        Page<com.ptt.gateway.dto.ScadaTagDTO> tagPage = dataSourceService.getScadaTags(filter, page, size);
        PaginationDTO pagination = dataSourceService.createPagination(tagPage);
        return ResponseEntity.ok(ApiResponse.success(tagPage.getContent(),
                "Source tags retrieved successfully", pagination));
    }

    /**
     * GET /api/data-sources/history
     * Get historical data for a tag
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<com.ptt.gateway.dto.HistoryDataDTO>>> getHistory(
            @RequestParam(name = "tagID") String tagID,
            @RequestParam(name = "start", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime start,
            @RequestParam(name = "end", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime end) {

        log.info("Fetching history for tag: {}, start: {}, end: {}", sanitize(tagID), start, end);
        List<com.ptt.gateway.dto.HistoryDataDTO> history = dataSourceService.getHistory(tagID, start, end);
        return ResponseEntity.ok(ApiResponse.success(history, "History data retrieved successfully"));
    }
}

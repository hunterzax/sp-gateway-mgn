package com.ptt.gateway.controller;

import com.ptt.gateway.dto.ApiResponse;
import com.ptt.gateway.dto.ShipperTagsLinkDTO;
import com.ptt.gateway.service.ShipperTagLinkService;
import static com.ptt.gateway.util.LogSanitizer.sanitize;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shipper-tags")
@RequiredArgsConstructor
@Slf4j
public class ShipperTagLinkController {

    private final ShipperTagLinkService tagLinkService;

    @GetMapping("/shipper/{shipperID}")
    public ResponseEntity<ApiResponse<List<ShipperTagsLinkDTO>>> getTagsByShipper(@PathVariable String shipperID) {
        log.info("Getting tags for shipper: {}", sanitize(shipperID));
        List<ShipperTagsLinkDTO> tags = tagLinkService.getTagsByShipper(shipperID);
        return ResponseEntity.ok(ApiResponse.success(tags, "Tags retrieved successfully"));
    }

    @PostMapping("/shipper/{shipperID}")
    public ResponseEntity<ApiResponse<ShipperTagsLinkDTO>> addTagLink(@PathVariable String shipperID,
            @RequestBody ShipperTagsLinkDTO dto) {
        log.info("Adding tag link for shipper: {}", sanitize(shipperID));
        ShipperTagsLinkDTO created = tagLinkService.addTagLink(shipperID, dto);
        return ResponseEntity.status(201).body(ApiResponse.success(created, "Tag link added successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTagLink(@PathVariable String id) {
        log.info("Deleting tag link: {}", sanitize(id));
        tagLinkService.deleteTagLink(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Tag link deleted successfully"));
    }
}

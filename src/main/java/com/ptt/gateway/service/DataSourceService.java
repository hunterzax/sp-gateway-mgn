package com.ptt.gateway.service;

import com.ptt.gateway.dto.CalcDTO;
import com.ptt.gateway.dto.PaginationDTO;
import com.ptt.gateway.dto.TagDTO;
import com.ptt.gateway.model.AuditLog;
import com.ptt.gateway.util.AuditMetadataBuilder;
import com.ptt.gateway.util.Audited;
import com.ptt.gateway.model.Calc;
import com.ptt.gateway.model.Tag;
import com.ptt.gateway.repository.CalcRepository;
import com.ptt.gateway.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ptt.gateway.dto.MeterUpdateDTO;
import com.ptt.gateway.model.MeterStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Sort;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataSourceService {

    private final TagRepository tagRepository;
    private final CalcRepository calcRepository;
    private final com.ptt.gateway.repository.MeterRepository meterRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final RealtimeDataListener realtimeDataListener;
    private final com.ptt.gateway.repository.RealtimeTagHistogramRepository histogramRepository;
    private final AuditLogService auditLogService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<com.ptt.gateway.dto.HistoryDataDTO> getHistory(String tagID, LocalDateTime start, LocalDateTime end) {
        if (tagID == null || tagID.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tag ID is required");
        }

        List<com.ptt.gateway.model.RealtimeTagHistogram> history;

        if (start == null && end == null) {
            history = histogramRepository.findByTagID(tagID);
        } else {
            if (start == null)
                start = LocalDateTime.of(1970, 1, 1, 0, 0);
            if (end == null)
                end = LocalDateTime.now();

            if (start.isAfter(end)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start time must be before end time");
            }
            history = histogramRepository.findByTagIDAndTimestampBetween(tagID, start, end);
        }

        return history.stream()
                .map(h -> new com.ptt.gateway.dto.HistoryDataDTO(h.getTimestamp(), h.getCurValue()))
                .sorted(java.util.Comparator.comparing(com.ptt.gateway.dto.HistoryDataDTO::getTimestamp))
                .collect(java.util.stream.Collectors.toList());
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        try {
            jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS calc_sequence START 1");
            jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS tag_sequence START 1");
            jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS meter_sequence START 1");

            // Sync meter_sequence with existing data
            try {
                // Find max numeric part of M... IDs
                Long maxId = jdbcTemplate.queryForObject(
                        "SELECT COALESCE(MAX(CAST(SUBSTRING(meter_id, 2) AS BIGINT)), 0) FROM meter WHERE meter_id LIKE 'M%'",
                        Long.class);
                // Set sequence to max + 1
                if (maxId != null && maxId > 0) {
                    jdbcTemplate.execute("SELECT setval('meter_sequence', " + maxId + ")");
                }
            } catch (Exception e) {
                log.warn("Could not sync meter_sequence: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.error("Error initializing sequences", e);
        }
    }

    @Transactional(readOnly = true)
    public Page<TagDTO> getTagDataSources(String id, String description, int page, int size) {
        int pageZeroBased = (page > 0) ? page - 1 : 0;
        Pageable pageable = PageRequest.of(pageZeroBased, size);
        String idPattern = (id != null && !id.isEmpty()) ? "%" + id.toLowerCase() + "%" : null;
        String descPattern = (description != null && !description.isEmpty()) ? "%" + description.toLowerCase() + "%"
                : null;

        Page<Tag> tagPage = tagRepository.findByFilters(idPattern, descPattern, pageable);

        return tagPage.map(this::convertToTagDTO);
    }

    @Transactional(readOnly = true)
    public Page<TagDTO> smartSearchTags(String query, int page, int size) {
        int pageZeroBased = (page > 0) ? page - 1 : 0;
        Pageable pageable = PageRequest.of(pageZeroBased, size);
        String queryPattern = (query != null && !query.isEmpty()) ? "%" + query.toLowerCase() + "%" : null;

        Page<Tag> tagPage = tagRepository.smartSearch(queryPattern, pageable);

        return tagPage.map(this::convertToTagDTO);
    }

    @Transactional(readOnly = true)
    public Page<CalcDTO> getCalcDataSources(String id, String description, int page, int size) {
        int pageZeroBased = (page > 0) ? page - 1 : 0;
        Pageable pageable = PageRequest.of(pageZeroBased, size);
        String idPattern = (id != null && !id.isEmpty()) ? "%" + id.toLowerCase() + "%" : null;
        String descPattern = (description != null && !description.isEmpty()) ? "%" + description.toLowerCase() + "%"
                : null;

        Page<Calc> calcPage = calcRepository.findByFilters(idPattern, descPattern, pageable);

        return calcPage.map(this::convertToCalcDTO);
    }

    @Transactional(readOnly = true)
    public List<Tag> getAllTags() {
        return tagRepository.findAllByOrderByTagID();
    }

    @Transactional(readOnly = true)
    public List<Calc> getAllCalcs() {
        return calcRepository.findAllByOrderByTagID();
    }

    @Transactional(readOnly = true)
    public List<com.ptt.gateway.model.Meter> getAllMeters(MeterStatus status, LocalDate deactivationDate, Sort sort) {
        Specification<com.ptt.gateway.model.Meter> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (deactivationDate != null) {
                // Assuming deactivationDate in DB is LocalDateTime, we compare the date part
                // This logic might need adjustment depending on exact requirement (exact match
                // of timestamp vs day)
                // "deactivation date" usually implies day.
                // Let's match the range of the day: [date 00:00:00, date 23:59:59.999999999]
                LocalDateTime startOfDay = deactivationDate.atStartOfDay();
                LocalDateTime endOfDay = deactivationDate.atTime(java.time.LocalTime.MAX);
                predicates.add(cb.between(root.get("deactivationDate"), startOfDay, endOfDay));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        if (sort != null) {
            return meterRepository.findAll(spec, sort);
        }
        return meterRepository.findAll(spec);
    }

    @Transactional
    public com.ptt.gateway.model.Meter createMeter(com.ptt.gateway.model.Meter meter) {
        // Auto-generate Meter ID
        Long nextVal = jdbcTemplate.queryForObject("SELECT nextval('meter_sequence')", Long.class);
        String nextId = "M" + nextVal;
        meter.setMeterId(nextId);

        if (meterRepository.existsById(meter.getMeterId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Meter ID already exists");
        }
        return meterRepository.save(meter);
    }

    /**
     * DTO-based overload that prevents mass-assignment (CWE-915).
     * Only the fields exposed on MeterCreateDTO are copied to the entity.
     */
    @Transactional
    public com.ptt.gateway.model.Meter createMeter(com.ptt.gateway.dto.MeterCreateDTO dto) {
        com.ptt.gateway.model.Meter meter = new com.ptt.gateway.model.Meter();
        meter.setMeterName(dto.getMeterName());
        meter.setDeactivationDate(dto.getDeactivationDate());
        // Status is server-controlled, default to ACTIVE for new meters
        meter.setStatus(MeterStatus.ACTIVE);
        return createMeter(meter);
    }

    @Transactional
    public com.ptt.gateway.model.Meter updateMeter(String id, MeterUpdateDTO meterDTO) {
        com.ptt.gateway.model.Meter existingMeter = meterRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meter not found"));

        if (meterDTO.getMeterName() != null) {
            existingMeter.setMeterName(meterDTO.getMeterName());
        }
        if (meterDTO.getDeactivationDate() != null) {
            existingMeter.setDeactivationDate(meterDTO.getDeactivationDate());
        }
        if (meterDTO.getStatus() != null) {
            existingMeter.setStatus(meterDTO.getStatus());
        }

        return meterRepository.save(existingMeter);
    }

    @Transactional
    public void deleteMeter(String id) {
        if (!meterRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Meter not found");
        }
        
        // Remove meter association from tags
        List<Tag> tags = tagRepository.findByMeter_MeterId(id);
        for (Tag tag : tags) {
            tag.setMeter(null);
        }
        if (!tags.isEmpty()) {
            tagRepository.saveAll(tags);
        }

        // Remove meter association from calcs
        List<Calc> calcs = calcRepository.findByMeter_MeterId(id);
        for (Calc calc : calcs) {
            calc.setMeter(null);
        }
        if (!calcs.isEmpty()) {
            calcRepository.saveAll(calcs);
        }

        meterRepository.deleteById(id);
        realtimeDataListener.refreshCache();
    }

    @Transactional
    public void switchMeterStatus(String meterId, boolean isActive) {
        com.ptt.gateway.model.Meter meter = meterRepository.findById(meterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meter not found"));

        meter.setStatus(isActive ? MeterStatus.ACTIVE : MeterStatus.INACTIVE);
        meterRepository.save(meter);

        List<Tag> tags = tagRepository.findByMeter_MeterId(meterId);
        List<Calc> calcs = calcRepository.findByMeter_MeterId(meterId);

        if (!isActive) {
            // Turning OFF: Force all to INACTIVE
            for (Tag t : tags)
                t.setStatus(com.ptt.gateway.model.Status.INACTIVE);
            for (Calc c : calcs)
                c.setStatus(com.ptt.gateway.model.Status.INACTIVE);
        } else {
            // Turning ON: Set to OFFLINE (Ready) ONLY if not expired (or INACTIVE checks)
            // User requirement: "if the tags / calcs deactivate date is active the meter
            // shouldn't able to active it as well"
            // So if deactivationDate is passed, it should remain INACTIVE (or whatever
            // state).
            // Logic: If NOT expired -> OFFLINE. If expired -> leave as is (likely INACTIVE
            // or
            // previous state)
            LocalDateTime now = LocalDateTime.now();

            for (Tag t : tags) {
                if (t.getDeactivateDate() == null || t.getDeactivateDate().isAfter(now)) {
                    // Only activate if not expired
                    t.setStatus(com.ptt.gateway.model.Status.OFFLINE);
                }
            }

            for (Calc c : calcs) {
                if (c.getDeactivateDate() == null || c.getDeactivateDate().isAfter(now)) {
                    c.setStatus(com.ptt.gateway.model.Status.OFFLINE);
                }
            }
        }

        tagRepository.saveAll(tags);
        calcRepository.saveAll(calcs);
        realtimeDataListener.refreshCache();
    }

    @Transactional
    public void updateTagPointValue(String tagID, Double newValue, com.ptt.gateway.model.Status status) {
        tagRepository.findById(tagID).ifPresent(tag -> {
            tag.setPointValue(newValue);
            tag.setStatus(status);
            tagRepository.save(tag);
            log.debug("Updated tag {} with value: {}", com.ptt.gateway.util.LogSanitizer.sanitize(tagID), newValue);
        });
    }

    @Transactional
    public void updateCalcPointValue(String tagID, Double newValue, com.ptt.gateway.model.Status status) {
        calcRepository.findById(tagID).ifPresent(calc -> {
            calc.setPointValue(newValue);
            calc.setStatus(status);
            calcRepository.save(calc);
            log.debug("Updated calc {} with value: {}", com.ptt.gateway.util.LogSanitizer.sanitize(tagID), newValue);
        });
    }

    public PaginationDTO createPagination(Page<?> page) {
        return PaginationDTO.builder()
                .page(page.getNumber() + 1)
                .totalPage(page.getTotalPages())
                .limit(page.getSize())
                .totalData(page.getTotalElements())
                .build();
    }

    private TagDTO convertToTagDTO(Tag tag) {
        return TagDTO.builder()
                .tagID(tag.getTagID())
                .description(tag.getDescription())
                .scadaTag(tag.getScadaTag())
                .rtuName(tag.getRtuName())
                .rtuName(tag.getRtuName())
                .meter(tag.getMeter() != null ? tag.getMeter().getMeterId() : null)
                .status(tag.getStatus())
                .pointValue(tag.getPointValue())
                .firstActivationDate(tag.getFirstActivationDate())
                .deactivateDate(tag.getDeactivateDate())
                .minValue(tag.getMinValue())
                .maxValue(tag.getMaxValue())
                .build();
    }

    private CalcDTO convertToCalcDTO(Calc calc) {
        return CalcDTO.builder()
                .tagID(calc.getTagID())
                .description(calc.getDescription())
                .tag(calc.getTag())
                .status(calc.getStatus())
                .tag(calc.getTag())
                .status(calc.getStatus())
                .meter(calc.getMeter() != null ? calc.getMeter().getMeterId() : null)
                .pointValue(calc.getPointValue())
                .minValue(calc.getMinValue())
                .maxValue(calc.getMaxValue())
                .firstActivationDate(calc.getFirstActivationDate())
                .deactivateDate(calc.getDeactivateDate())
                .accumulatePeriod(calc.getAccumulatePeriod())
                .accumulateActive(calc.getAccumulateActive())
                .build();
    }

    @Audited(action = "createTag", descriptionTemplate = "{user} add Tag : {id}", resultIdField = "tagID")
    @Transactional
    public TagDTO createTag(com.ptt.gateway.dto.TagCreateDTO tagDTO) {
        if (tagRepository.existsByDescription(tagDTO.getDescription())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Description already exists");
        }

        Long nextVal = jdbcTemplate.queryForObject("SELECT nextval('tag_sequence')", Long.class);
        String nextId = "T" + nextVal;

        Tag tag = new Tag();
        tag.setTagID(nextId);
        updateTagFromCreateDTO(tag, tagDTO);
        tag.setStatus(com.ptt.gateway.model.Status.OFFLINE);

        Tag savedTag = tagRepository.save(tag);
        realtimeDataListener.refreshCache();
        return convertToTagDTO(savedTag);
    }

    @Transactional
    public TagDTO updateTag(String id, TagDTO tagDTO) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tag not found with ID: " + id));

        if (tagDTO.getTagID() != null && !tagDTO.getTagID().equals(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tag ID cannot be changed");
        }

        if (tagRepository.existsByDescriptionAndTagIDNot(tagDTO.getDescription(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Description already exists");
        }

        // Snapshot before
        String beforeDesc = tag.getDescription();
        String beforeSourceTag = tag.getScadaTag();
        String beforeRtu = tag.getRtuName();
        Object beforeMeter = tag.getMeter() != null ? tag.getMeter().getMeterId() : null;
        Object beforeStatus = tag.getStatus();
        Object beforeDeac = tag.getDeactivateDate();

        updateTagFromDTO(tag, tagDTO);
        Tag updatedTag = tagRepository.save(tag);
        realtimeDataListener.refreshCache();

        // Audit log with before/after metadata
        try {
            String username = resolveUsername();
            String metadata = new AuditMetadataBuilder(objectMapper)
                    .add("description", beforeDesc, updatedTag.getDescription())
                    .add("sourceTag", beforeSourceTag, updatedTag.getScadaTag())
                    .add("rtuName", beforeRtu, updatedTag.getRtuName())
                    .add("meter", beforeMeter,
                            updatedTag.getMeter() != null ? updatedTag.getMeter().getMeterId() : null)
                    .add("status", beforeStatus, updatedTag.getStatus())
                    .add("deactivateDate", beforeDeac, updatedTag.getDeactivateDate())
                    .build();
            auditLogService.log(AuditLog.builder()
                    .action("updateTag")
                    .description(username + " edits Tag : " + id)
                    .logType("ACTION")
                    .severity("INFO")
                    .source("BACKEND")
                    .createdBy(username)
                    .statusType("Action")
                    .metadata(metadata)
                    .build());
        } catch (Exception e) {
            log.warn("[AuditLog] Could not build tag audit metadata: {}", e.getMessage());
        }

        return convertToTagDTO(updatedTag);
    }

    @Transactional
    public void deleteTag(String id) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tag not found with ID: " + id));

        // Snapshot before delete
        String description = tag.getDescription();
        String sourceTagName = tag.getScadaTag();
        String rtuName = tag.getRtuName();
        Object meter = tag.getMeter() != null ? tag.getMeter().getMeterId() : null;
        Object status = tag.getStatus();
        Object deactivateDate = tag.getDeactivateDate();

        tagRepository.deleteById(id);
        realtimeDataListener.refreshCache();

        // Audit log with pre-delete snapshot
        try {
            String username = resolveUsername();
            java.util.Map<String, Object> meta = new java.util.LinkedHashMap<>();
            meta.put("tagID", id);
            meta.put("description", description);
            meta.put("sourceTag", sourceTagName);
            meta.put("rtuName", rtuName);
            meta.put("meter", meter);
            meta.put("status", status);
            meta.put("deactivateDate", deactivateDate);
            String metadata = objectMapper.writeValueAsString(meta);
            auditLogService.log(AuditLog.builder()
                    .action("deleteTag")
                    .description(username + " deletes Tag : " + id)
                    .logType("ACTION")
                    .severity("WARNING")
                    .source("BACKEND")
                    .createdBy(username)
                    .statusType("Delete")
                    .metadata(metadata)
                    .build());
        } catch (Exception e) {
            log.warn("[AuditLog] Could not log deleteTag: {}", e.getMessage());
        }
    }

    private void updateTagFromCreateDTO(Tag tag, com.ptt.gateway.dto.TagCreateDTO dto) {
        tag.setDescription(dto.getDescription());
        tag.setScadaTag(dto.getScadaTag());
        tag.setRtuName(dto.getRtuName());
        tag.setRtuName(dto.getRtuName());
        tag.setMeter(resolveMeter(dto.getMeter()));
        tag.setPointValue(dto.getPointValue());
        tag.setFirstActivationDate(dto.getFirstActivationDate());
        tag.setDeactivateDate(dto.getDeactivateDate());
    }

    private void updateTagFromDTO(Tag tag, TagDTO dto) {
        tag.setDescription(dto.getDescription());
        tag.setScadaTag(dto.getScadaTag());
        tag.setRtuName(dto.getRtuName());
        tag.setRtuName(dto.getRtuName());
        tag.setMeter(resolveMeter(dto.getMeter()));
        tag.setStatus(dto.getStatus());
        tag.setPointValue(dto.getPointValue());
        tag.setPointValue(dto.getPointValue());
        tag.setFirstActivationDate(dto.getFirstActivationDate());
        tag.setDeactivateDate(dto.getDeactivateDate());
        // tagID is usually not updated, or handled separately for create
    }

    private void updateCalcFromCreateDTO(Calc calc, com.ptt.gateway.dto.CalcCreateDTO dto) {
        calc.setDescription(dto.getDescription());
        calc.setTag(dto.getTag());
        calc.setTag(dto.getTag());
        calc.setMeter(resolveMeter(dto.getMeter()));
        calc.setPointValue(dto.getPointValue());
        calc.setFirstActivationDate(dto.getFirstActivationDate());
        calc.setDeactivateDate(dto.getDeactivateDate());
        calc.setAccumulatePeriod(dto.getAccumulatePeriod());
        calc.setAccumulateActive(dto.getAccumulateActive());
    }

    private void updateCalcFromDTO(Calc calc, CalcDTO dto) {
        calc.setDescription(dto.getDescription());
        calc.setTag(dto.getTag());
        calc.setTag(dto.getTag());
        calc.setMeter(resolveMeter(dto.getMeter()));
        calc.setStatus(dto.getStatus());
        calc.setPointValue(dto.getPointValue());
        calc.setFirstActivationDate(dto.getFirstActivationDate());
        calc.setDeactivateDate(dto.getDeactivateDate());
        calc.setAccumulatePeriod(dto.getAccumulatePeriod());
        calc.setAccumulateActive(dto.getAccumulateActive());
    }

    @Audited(action = "createCalc", descriptionTemplate = "{user} add Cal Tag : {id}", resultIdField = "tagID")
    @Transactional
    public CalcDTO createCalc(com.ptt.gateway.dto.CalcCreateDTO calcDTO) {
        if (calcRepository.existsByDescription(calcDTO.getDescription())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Description already exists");
        }

        Long nextVal = jdbcTemplate.queryForObject("SELECT nextval('calc_sequence')", Long.class);
        String nextId = "C" + nextVal;

        Calc calc = new Calc();
        calc.setTagID(nextId);
        updateCalcFromCreateDTO(calc, calcDTO);
        calc.setStatus(com.ptt.gateway.model.Status.OFFLINE);

        Calc savedCalc = calcRepository.save(calc);
        realtimeDataListener.refreshCache();
        return convertToCalcDTO(savedCalc);
    }

    @Transactional
    public CalcDTO updateCalc(String id, CalcDTO calcDTO) {
        Calc calc = calcRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Calc not found with ID: " + id));

        if (calcRepository.existsByDescriptionAndTagIDNot(calcDTO.getDescription(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Description already exists");
        }

        // Snapshot before
        String beforeDesc = calc.getDescription();
        String beforeTag = calc.getTag();
        Object beforeMeter = calc.getMeter() != null ? calc.getMeter().getMeterId() : null;
        Object beforeStatus = calc.getStatus();
        Object beforeDeac = calc.getDeactivateDate();
        Object beforePeriod = calc.getAccumulatePeriod();
        Boolean beforeAccum = calc.getAccumulateActive();

        updateCalcFromDTO(calc, calcDTO);
        Calc updatedCalc = calcRepository.save(calc);
        realtimeDataListener.refreshCache();

        // Audit log with before/after metadata
        try {
            String username = resolveUsername();
            String metadata = new AuditMetadataBuilder(objectMapper)
                    .add("description", beforeDesc, updatedCalc.getDescription())
                    .add("tag", beforeTag, updatedCalc.getTag())
                    .add("meter", beforeMeter,
                            updatedCalc.getMeter() != null ? updatedCalc.getMeter().getMeterId() : null)
                    .add("status", beforeStatus, updatedCalc.getStatus())
                    .add("deactivateDate", beforeDeac, updatedCalc.getDeactivateDate())
                    .add("accumulatePeriod", beforePeriod, updatedCalc.getAccumulatePeriod())
                    .add("accumulateActive", beforeAccum, updatedCalc.getAccumulateActive())
                    .build();
            auditLogService.log(AuditLog.builder()
                    .action("updateCalc")
                    .description(username + " edits Cal Tag : " + id)
                    .logType("ACTION")
                    .severity("INFO")
                    .source("BACKEND")
                    .createdBy(username)
                    .statusType("Action")
                    .metadata(metadata)
                    .build());
        } catch (Exception e) {
            log.warn("[AuditLog] Could not build calc audit metadata: {}", e.getMessage());
        }

        return convertToCalcDTO(updatedCalc);
    }

    @Transactional
    public void deleteCalc(String id) {
        Calc calc = calcRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Calc not found with ID: " + id));

        // Snapshot before delete
        String description = calc.getDescription();
        String tag = calc.getTag();
        Object meter = calc.getMeter() != null ? calc.getMeter().getMeterId() : null;
        Object status = calc.getStatus();
        Object deactivateDate = calc.getDeactivateDate();
        Object accumulatePeriod = calc.getAccumulatePeriod();
        Boolean accumulateActive = calc.getAccumulateActive();

        calcRepository.deleteById(id);
        realtimeDataListener.refreshCache();

        // Audit log with pre-delete snapshot
        try {
            String username = resolveUsername();
            java.util.Map<String, Object> meta = new java.util.LinkedHashMap<>();
            meta.put("tagID", id);
            meta.put("description", description);
            meta.put("tag", tag);
            meta.put("meter", meter);
            meta.put("status", status);
            meta.put("deactivateDate", deactivateDate);
            meta.put("accumulatePeriod", accumulatePeriod);
            meta.put("accumulateActive", accumulateActive);
            String metadata = objectMapper.writeValueAsString(meta);
            auditLogService.log(AuditLog.builder()
                    .action("deleteCalc")
                    .description(username + " deletes Cal Tag : " + id)
                    .logType("ACTION")
                    .severity("WARNING")
                    .source("BACKEND")
                    .createdBy(username)
                    .statusType("Delete")
                    .metadata(metadata)
                    .build());
        } catch (Exception e) {
            log.warn("[AuditLog] Could not log deleteCalc: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<com.ptt.gateway.dto.ScadaTagDTO> getScadaTags(String query, int page, int size) {
        StringBuilder sql = new StringBuilder("SELECT DISTINCT tagname, gw FROM public.realtime_value");
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM (SELECT DISTINCT tagname, gw FROM public.realtime_value");

        // Define argument list
        java.util.List<Object> args = new java.util.ArrayList<>();

        if (query != null && !query.isEmpty()) {
            // Prevent Wildcard (LIKE) Injection by escaping special characters
            String escapedQuery = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            String likePattern = "%" + escapedQuery + "%";
            sql.append(" WHERE tagname LIKE ? ESCAPE '\\'");
            countSql.append(" WHERE tagname LIKE ? ESCAPE '\\'");
            args.add(likePattern);
        }

        countSql.append(") as temp");
        sql.append(" ORDER BY tagname LIMIT ? OFFSET ?");

        // Execute count query
        Integer total = jdbcTemplate.queryForObject(countSql.toString(), Integer.class, args.toArray());
        long totalRows = (total != null) ? total : 0;

        // Add limit and offset for data query
        int pageZeroBased = (page > 0) ? page - 1 : 0;
        args.add(size);
        args.add(pageZeroBased * size);

        try {
            List<com.ptt.gateway.dto.ScadaTagDTO> results = jdbcTemplate.query(sql.toString(),
                    args.toArray(),
                    (rs, rowNum) -> new com.ptt.gateway.dto.ScadaTagDTO(
                            rs.getString("tagname"),
                            rs.getInt("gw")));

            return new PageImpl<>(results, PageRequest.of(pageZeroBased, size), totalRows);
        } catch (Exception e) {
            log.error("Error executing SQL: {}", sql, e);
            throw e;
        }
    }

    private com.ptt.gateway.model.Meter resolveMeter(String meterId) {
        if (meterId == null || meterId.trim().isEmpty()) {
            return null;
        }
        return meterRepository.findById(meterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Meter not found: " + meterId));
    }

    private String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : "anonymous";
    }

}

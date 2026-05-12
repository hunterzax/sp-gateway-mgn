package com.ptt.gateway.service;

import com.ptt.gateway.model.Calc;
import com.ptt.gateway.model.Status;
import com.ptt.gateway.model.Tag;
import com.ptt.gateway.repository.CalcRepository;
import com.ptt.gateway.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import static com.ptt.gateway.util.LogSanitizer.sanitize;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatusMonitorService {

    private final TagRepository tagRepository;
    private final CalcRepository calcRepository;

    @Scheduled(fixedRate = 60000) // Run every 1 minute
    public void checkStaleData() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime delayThreshold = now.minusMinutes(3);
        LocalDateTime offlineThreshold = now.minusMinutes(10);

        log.debug("Checking for stale data. DELAY < {}, OFFLINE < {}", delayThreshold, offlineThreshold);

        // 1. Check for DELAY (Last Data older than 3 mins, currently ONLINE)
        // Tags
        List<Tag> delayTags = tagRepository.findByStatusAndLastDataDateBefore(Status.ONLINE, delayThreshold);
        for (Tag tag : delayTags) {
            log.info("Tag {} is stale > 3m (last data: {}). Setting to DELAY.", sanitize(tag.getTagID()), tag.getLastDataDate());
            tag.setStatus(Status.DELAY);
            tagRepository.save(tag);
        }
        // Calcs
        List<Calc> delayCalcs = calcRepository.findByStatusAndLastDataDateBefore(Status.ONLINE, delayThreshold);
        for (Calc calc : delayCalcs) {
            log.info("Calc {} is stale > 3m (last data: {}). Setting to DELAY.", sanitize(calc.getTagID()),
                    calc.getLastDataDate());
            calc.setStatus(Status.DELAY);
            calcRepository.save(calc);
        }

        // 2. Check for OFFLINE (Last Data older than 10 mins, currently ONLINE or
        // DELAY)
        // We need to check both statuses. Since the repository method takes a single
        // status, we might need to modify it or call it twice.
        // Calling it twice for simplicity.

        // Tags (Check DELAY -> OFFLINE)
        List<Tag> offlineTagsDelay = tagRepository.findByStatusAndLastDataDateBefore(Status.DELAY, offlineThreshold);
        for (Tag tag : offlineTagsDelay) {
            log.info("Tag {} is stale > 10m (last data: {}). Setting to OFFLINE.", sanitize(tag.getTagID()),
                    tag.getLastDataDate());
            tag.setStatus(Status.OFFLINE);
            tagRepository.save(tag);
        }
        // Tags (Check ONLINE -> OFFLINE, in case it jumped straight to > 10m)
        List<Tag> offlineTagsOnline = tagRepository.findByStatusAndLastDataDateBefore(Status.ONLINE, offlineThreshold);
        for (Tag tag : offlineTagsOnline) {
            log.info("Tag {} is expired > 10m (last data: {}). Setting to OFFLINE.", sanitize(tag.getTagID()),
                    tag.getLastDataDate());
            tag.setStatus(Status.OFFLINE);
            tagRepository.save(tag);
        }

        // Calcs (Check DELAY -> OFFLINE)
        List<Calc> offlineCalcsDelay = calcRepository.findByStatusAndLastDataDateBefore(Status.DELAY, offlineThreshold);
        for (Calc calc : offlineCalcsDelay) {
            log.info("Calc {} is stale > 10m (last data: {}). Setting to OFFLINE.", sanitize(calc.getTagID()),
                    calc.getLastDataDate());
            calc.setStatus(Status.OFFLINE);
            calcRepository.save(calc);
        }
        // Calcs (Check ONLINE -> OFFLINE)
        List<Calc> offlineCalcsOnline = calcRepository.findByStatusAndLastDataDateBefore(Status.ONLINE,
                offlineThreshold);
        for (Calc calc : offlineCalcsOnline) {
            log.info("Calc {} is expired > 10m (last data: {}). Setting to OFFLINE.", sanitize(calc.getTagID()),
                    calc.getLastDataDate());
            calc.setStatus(Status.OFFLINE);
            calcRepository.save(calc);
        }
    }
}

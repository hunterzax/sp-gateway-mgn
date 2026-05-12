package com.ptt.gateway.repository;

import com.ptt.gateway.model.RealtimeTagHistogram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RealtimeTagHistogramRepository extends JpaRepository<RealtimeTagHistogram, Long> {
    List<RealtimeTagHistogram> findByTagID(String tagID);

    List<RealtimeTagHistogram> findByTagIDAndTimestampBetween(String tagID, LocalDateTime start, LocalDateTime end);
}

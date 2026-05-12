package com.ptt.gateway.repository;

import com.ptt.gateway.model.ShipperDailyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ShipperDailyStatsRepository extends JpaRepository<ShipperDailyStats, Long> {
    List<ShipperDailyStats> findByShipperIdAndRecordDateBetweenOrderByRecordDateAsc(String shipperId, LocalDate startDate, LocalDate endDate);
    void deleteByRecordDateBefore(LocalDate cutoffDate);
}

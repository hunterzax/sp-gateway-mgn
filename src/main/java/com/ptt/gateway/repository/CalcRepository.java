package com.ptt.gateway.repository;

import com.ptt.gateway.model.Calc;
import com.ptt.gateway.model.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface CalcRepository extends JpaRepository<Calc, String>, JpaSpecificationExecutor<Calc> {

        long countByStatusNot(Status status);

        long countByStatusIn(java.util.List<Status> statuses);

        @Query("SELECT c FROM Calc c WHERE " +
                        "(:id IS NULL OR LOWER(c.tagID) LIKE :id) AND " +
                        "(:description IS NULL OR LOWER(c.description) LIKE :description)")
        Page<Calc> findByFilters(
                        @Param("id") String id,
                        @Param("description") String description,
                        Pageable pageable);

        boolean existsByDescription(String description);

        boolean existsByDescriptionAndTagIDNot(String description, String tagID);

        List<Calc> findByTag(String tag);

        @org.springframework.data.jpa.repository.Modifying
        @Query("UPDATE Calc c SET c.pointValue = :val, " +
                        "c.status = 'ONLINE', " +
                        "c.lastDataDate = :timestamp, " +
                        "c.minValue = CASE WHEN c.minValue IS NULL THEN :val ELSE LEAST(c.minValue, :val) END, " +
                        "c.maxValue = CASE WHEN c.maxValue IS NULL THEN :val ELSE GREATEST(c.maxValue, :val) END " +
                        "WHERE c.tagID = :id")
        void updateStatistics(@Param("id") String id, @Param("val") Double val,
                        @Param("timestamp") java.time.LocalDateTime timestamp);

        @org.springframework.data.jpa.repository.Modifying
        @Query("UPDATE Calc c SET c.pointValue = :val, " +
                        "c.status = 'ONLINE', " +
                        "c.lastDataDate = :timestamp, " +
                        "c.minValue = CASE WHEN c.minValue IS NULL THEN :minVal ELSE LEAST(c.minValue, :minVal) END, " +
                        "c.maxValue = CASE WHEN c.maxValue IS NULL THEN :maxVal ELSE GREATEST(c.maxValue, :maxVal) END "
                        +
                        "WHERE c.tagID = :id")
        void updateStatisticsBatch(@Param("id") String id, @Param("val") Double val, @Param("minVal") Double minVal,
                        @Param("maxVal") Double maxVal, @Param("timestamp") java.time.LocalDateTime timestamp);

        List<Calc> findAllByOrderByTagID();

        List<Calc> findByStatusAndLastDataDateBefore(Status status, LocalDateTime dateTime);

        List<Calc> findByMeter_MeterId(String meterId);

        List<Calc> findByDeactivateDateBeforeAndStatusNot(LocalDateTime date, Status status);
}

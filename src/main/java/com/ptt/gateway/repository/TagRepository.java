package com.ptt.gateway.repository;

import com.ptt.gateway.model.Tag;
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
public interface TagRepository extends JpaRepository<Tag, String>, JpaSpecificationExecutor<Tag> {

        long countByStatusNot(Status status);

        long countByStatusIn(java.util.List<Status> statuses);

        @Query("SELECT t FROM Tag t WHERE " +
                        "(:id IS NULL OR LOWER(t.tagID) LIKE :id) AND " +
                        "(:description IS NULL OR LOWER(t.description) LIKE :description)")
        Page<Tag> findByFilters(
                        @Param("id") String id,
                        @Param("description") String description,
                        Pageable pageable);

        boolean existsByDescription(String description);

        boolean existsByDescriptionAndTagIDNot(String description, String tagID);

        List<Tag> findByScadaTag(String sourceTagName);

        @org.springframework.data.jpa.repository.Modifying
        @Query("UPDATE Tag t SET t.pointValue = :val, " +
                        "t.status = 'ONLINE', " +
                        "t.lastDataDate = :timestamp, " +
                        "t.minValue = CASE WHEN t.minValue IS NULL THEN :val ELSE LEAST(t.minValue, :val) END, " +
                        "t.maxValue = CASE WHEN t.maxValue IS NULL THEN :val ELSE GREATEST(t.maxValue, :val) END " +
                        "WHERE t.tagID = :id")
        void updateStatistics(@Param("id") String id, @Param("val") Double val,
                        @Param("timestamp") java.time.LocalDateTime timestamp);

        @org.springframework.data.jpa.repository.Modifying
        @Query("UPDATE Tag t SET t.pointValue = :val, " +
                        "t.status = 'ONLINE', " +
                        "t.lastDataDate = :timestamp, " +
                        "t.minValue = CASE WHEN t.minValue IS NULL THEN :minVal ELSE LEAST(t.minValue, :minVal) END, " +
                        "t.maxValue = CASE WHEN t.maxValue IS NULL THEN :maxVal ELSE GREATEST(t.maxValue, :maxVal) END "
                        +
                        "WHERE t.tagID = :id")
        void updateStatisticsBatch(@Param("id") String id, @Param("val") Double val, @Param("minVal") Double minVal,
                        @Param("maxVal") Double maxVal, @Param("timestamp") java.time.LocalDateTime timestamp);

        List<Tag> findAllByOrderByTagID();

        List<Tag> findByStatusAndLastDataDateBefore(Status status, LocalDateTime dateTime);

        List<Tag> findByMeter_MeterId(String meterId);

        List<Tag> findByDeactivateDateBeforeAndStatusNot(LocalDateTime date, Status status);

        @Query("SELECT t FROM Tag t LEFT JOIN t.meter m WHERE " +
                        "(:query IS NULL OR " +
                        "LOWER(t.tagID) LIKE :query OR " +
                        "LOWER(t.description) LIKE :query OR " +
                        "LOWER(t.scadaTag) LIKE :query OR " +
                        "LOWER(t.rtuName) LIKE :query OR " +
                        "LOWER(m.meterId) LIKE :query)")
        Page<Tag> smartSearch(@Param("query") String query, Pageable pageable);
}

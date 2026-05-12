package com.ptt.gateway.repository;

import com.ptt.gateway.model.ShipperManagement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShipperRepository extends JpaRepository<ShipperManagement, String> {

    @Query("SELECT s FROM ShipperManagement s WHERE " +
            "(:query IS NULL OR " +
            "LOWER(s.shipperID) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(s.shipperName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(s.initials) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(s.shipperShortName) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<ShipperManagement> smartSearch(@Param("query") String query, Pageable pageable);

    @Query("SELECT s.shipperID FROM ShipperManagement s ORDER BY s.shipperID DESC LIMIT 1")
    Optional<String> findLastShipperID();
}

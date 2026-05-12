package com.ptt.gateway.repository;

import com.ptt.gateway.model.ShipperTagsLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShipperTagsLinkRepository extends JpaRepository<ShipperTagsLink, String> {
    List<ShipperTagsLink> findAllByShipperManagement_ShipperID(String shipperID);

    long countByShipperManagement_ShipperID(String shipperID);

    /** Count tags linked to shipper where tag status != INACTIVE */
    @Query("SELECT COUNT(tl) FROM ShipperTagsLink tl " +
           "JOIN Tag t ON t.tagID = tl.tagID " +
           "WHERE tl.shipperManagement.shipperID = :shipperID " +
           "AND t.status <> com.ptt.gateway.model.Status.INACTIVE")
    long countActiveByShipperID(@Param("shipperID") String shipperID);
}

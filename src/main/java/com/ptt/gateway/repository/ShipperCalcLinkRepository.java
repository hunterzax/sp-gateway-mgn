package com.ptt.gateway.repository;

import com.ptt.gateway.model.ShipperCalcLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShipperCalcLinkRepository extends JpaRepository<ShipperCalcLink, String> {
    List<ShipperCalcLink> findAllByShipperManagement_ShipperID(String shipperID);

    long countByShipperManagement_ShipperID(String shipperID);

    /** Count calcs linked to shipper where calc status != INACTIVE */
    @Query("SELECT COUNT(cl) FROM ShipperCalcLink cl " +
           "JOIN Calc c ON c.tagID = cl.tagID " +
           "WHERE cl.shipperManagement.shipperID = :shipperID " +
           "AND c.status <> com.ptt.gateway.model.Status.INACTIVE")
    long countActiveByShipperID(@Param("shipperID") String shipperID);
}

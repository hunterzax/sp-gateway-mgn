package com.ptt.gateway.repository;

import com.ptt.gateway.model.ContractList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContractListRepository extends JpaRepository<ContractList, Long> {
    List<ContractList> findAllByShipperManagement_ShipperID(String shipperID);
}

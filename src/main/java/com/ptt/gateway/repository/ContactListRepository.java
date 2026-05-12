package com.ptt.gateway.repository;

import com.ptt.gateway.model.ContactList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContactListRepository extends JpaRepository<ContactList, String> {
    List<ContactList> findAllByShipperManagement_ShipperID(String shipperID);
}

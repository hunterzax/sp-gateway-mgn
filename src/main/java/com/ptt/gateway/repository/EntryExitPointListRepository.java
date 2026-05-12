package com.ptt.gateway.repository;

import com.ptt.gateway.model.EntryExitPointList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EntryExitPointListRepository extends JpaRepository<EntryExitPointList, String> {
    List<EntryExitPointList> findAllByContractList_ContractID(Long contractID);
}

package com.ptt.gateway.repository;

import com.ptt.gateway.model.Meter;
import com.ptt.gateway.model.MeterStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MeterRepository
        extends JpaRepository<Meter, String>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Meter> {
    List<Meter> findByStatus(MeterStatus status);
}

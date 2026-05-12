package com.ptt.gateway.repository;

import com.ptt.gateway.model.ShipperFailRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShipperFailRateRepository extends JpaRepository<ShipperFailRate, String> {
}

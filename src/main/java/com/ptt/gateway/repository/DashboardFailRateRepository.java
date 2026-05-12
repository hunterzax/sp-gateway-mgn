package com.ptt.gateway.repository;

import com.ptt.gateway.model.DashboardFailRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DashboardFailRateRepository extends JpaRepository<DashboardFailRate, String> {
}

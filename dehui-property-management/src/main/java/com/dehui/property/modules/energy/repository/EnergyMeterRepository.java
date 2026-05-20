package com.dehui.property.modules.energy.repository;

import com.dehui.property.modules.energy.entity.EnergyMeter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnergyMeterRepository extends JpaRepository<EnergyMeter, Long> {
    Optional<EnergyMeter> findByMeterNo(String meterNo);

    List<EnergyMeter> findByStatusOrderByMeterNoAsc(String status);
}

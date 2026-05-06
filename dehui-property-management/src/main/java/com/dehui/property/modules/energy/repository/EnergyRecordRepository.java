package com.dehui.property.modules.energy.repository;

import com.dehui.property.modules.energy.entity.EnergyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnergyRecordRepository extends JpaRepository<EnergyRecord, Long> {
}

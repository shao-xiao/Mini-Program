package com.dehui.property.modules.energy.repository;

import com.dehui.property.modules.energy.entity.EnergyReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface EnergyReadingRepository extends JpaRepository<EnergyReading, Long>, JpaSpecificationExecutor<EnergyReading> {
    boolean existsByMeterIdAndPeriodMonth(Long meterId, String periodMonth);

    boolean existsByMeterIdAndPeriodMonthAndIdNot(Long meterId, String periodMonth, Long id);

    Optional<EnergyReading> findTopByMeterIdOrderByPeriodMonthDesc(Long meterId);

    Optional<EnergyReading> findTopByMeterIdAndPeriodMonthLessThanOrderByPeriodMonthDesc(Long meterId, String periodMonth);

    List<EnergyReading> findTop3ByMeterIdAndPeriodMonthLessThanOrderByPeriodMonthDesc(Long meterId, String periodMonth);

    List<EnergyReading> findTop4ByMeterIdAndPeriodMonthLessThanOrderByPeriodMonthDesc(Long meterId, String periodMonth);

    List<EnergyReading> findByTenantIdOrderByPeriodMonthDesc(Long tenantId);
}

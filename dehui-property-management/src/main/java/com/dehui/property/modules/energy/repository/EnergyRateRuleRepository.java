package com.dehui.property.modules.energy.repository;

import com.dehui.property.modules.energy.entity.EnergyRateRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnergyRateRuleRepository extends JpaRepository<EnergyRateRule, Long> {
    Optional<EnergyRateRule> findFirstByEnergyTypeAndStatusOrderByUpdatedTimeDesc(String energyType, String status);

    List<EnergyRateRule> findByEnergyTypeOrderByUpdatedTimeDesc(String energyType);
}

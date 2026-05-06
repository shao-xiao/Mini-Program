package com.dehui.property.modules.feerule.repository;

import com.dehui.property.modules.feerule.entity.FeeRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeeRuleRepository extends JpaRepository<FeeRule, Long> {

    List<FeeRule> findByStatus(String status);

    List<FeeRule> findByContractId(Long contractId);
}
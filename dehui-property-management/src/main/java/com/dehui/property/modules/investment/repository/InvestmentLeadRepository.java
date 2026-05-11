package com.dehui.property.modules.investment.repository;

import com.dehui.property.modules.investment.entity.InvestmentLead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvestmentLeadRepository extends JpaRepository<InvestmentLead, Long> {
    List<InvestmentLead> findAllByOrderByCreatedTimeDesc();
}

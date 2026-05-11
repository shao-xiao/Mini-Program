package com.dehui.property.modules.investment.repository;

import com.dehui.property.modules.investment.entity.InvestmentContent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvestmentContentRepository extends JpaRepository<InvestmentContent, Long> {
    List<InvestmentContent> findByStatusOrderBySortOrderAscPublishTimeDesc(String status);
}

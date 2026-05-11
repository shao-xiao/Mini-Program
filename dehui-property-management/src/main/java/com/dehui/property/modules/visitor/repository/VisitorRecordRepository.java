package com.dehui.property.modules.visitor.repository;

import com.dehui.property.modules.visitor.entity.VisitorRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VisitorRecordRepository extends JpaRepository<VisitorRecord, Long> {

    List<VisitorRecord> findByStatus(String status);

    List<VisitorRecord> findByTenantId(Long tenantId);

    List<VisitorRecord> findByMobileUserIdOrderByVisitTimeDesc(Long mobileUserId);

    Long countByStatus(String status);
}

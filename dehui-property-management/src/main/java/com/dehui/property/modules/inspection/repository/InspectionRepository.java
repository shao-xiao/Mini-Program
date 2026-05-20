package com.dehui.property.modules.inspection.repository;

import com.dehui.property.modules.inspection.entity.InspectionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InspectionRepository extends JpaRepository<InspectionRecord, Long> {
    List<InspectionRecord> findByDeletedAtIsNullOrderByInspectionDateDescCreatedTimeDesc();
}

package com.dehui.property.modules.inspection.repository;

import com.dehui.property.modules.inspection.entity.InspectionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionRepository extends JpaRepository<InspectionRecord, Long> {
}

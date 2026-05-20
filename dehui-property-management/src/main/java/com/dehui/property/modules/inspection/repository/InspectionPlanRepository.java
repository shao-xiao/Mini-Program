package com.dehui.property.modules.inspection.repository;

import com.dehui.property.modules.inspection.entity.InspectionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InspectionPlanRepository extends JpaRepository<InspectionPlan, Long> {
    List<InspectionPlan> findByDeletedAtIsNullOrderByPlannedDateDescCreatedTimeDesc();
}

package com.dehui.property.modules.aiassistant.repository;

import com.dehui.property.modules.aiassistant.entity.AiDailyReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AiDailyReportRepository extends JpaRepository<AiDailyReport, Long> {
    Optional<AiDailyReport> findByReportDate(LocalDate reportDate);

    List<AiDailyReport> findAllByOrderByReportDateDesc();
}

package com.dehui.property.modules.visitor.service;

import com.dehui.property.modules.visitor.entity.VisitorRecord;
import com.dehui.property.modules.visitor.repository.VisitorRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VisitorRecordService {

    private final VisitorRecordRepository visitorRecordRepository;

    public VisitorRecord create(VisitorRecord record) {
        if (record.getVisitTime() == null) {
            record.setVisitTime(LocalDateTime.now());
        }

        if (record.getStatus() == null || record.getStatus().isBlank()) {
            record.setStatus("REGISTERED");
        }

        if (record.getSource() == null || record.getSource().isBlank()) {
            record.setSource("ADMIN");
        }

        return visitorRecordRepository.save(record);
    }

    public List<VisitorRecord> list() {
        return visitorRecordRepository.findAll();
    }

    public VisitorRecord get(Long id) {
        return visitorRecordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("访客记录不存在"));
    }

    public List<VisitorRecord> listByTenant(Long tenantId) {
        return visitorRecordRepository.findByTenantId(tenantId);
    }

    public VisitorRecord enter(Long id) {
        VisitorRecord record = get(id);
        record.setStatus("ENTERED");
        return visitorRecordRepository.save(record);
    }

    public VisitorRecord leave(Long id) {
        VisitorRecord record = get(id);
        record.setStatus("LEFT");
        record.setLeaveTime(LocalDateTime.now());
        return visitorRecordRepository.save(record);
    }

    public VisitorRecord cancel(Long id) {
        VisitorRecord record = get(id);
        record.setStatus("CANCELLED");
        return visitorRecordRepository.save(record);
    }
}

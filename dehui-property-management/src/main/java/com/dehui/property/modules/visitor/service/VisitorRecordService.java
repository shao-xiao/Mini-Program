package com.dehui.property.modules.visitor.service;

import com.dehui.property.common.ExcelExportUtil;
import com.dehui.property.common.OperationDict;
import com.dehui.property.modules.tenant.repository.TenantRepository;
import com.dehui.property.modules.visitor.entity.VisitorRecord;
import com.dehui.property.modules.visitor.repository.VisitorRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class VisitorRecordService {
    private final VisitorRecordRepository visitorRecordRepository;
    private final TenantRepository tenantRepository;

    @Transactional
    public VisitorRecord create(VisitorRecord record) {
        if (record.getVisitTime() == null) {
            record.setVisitTime(LocalDateTime.now());
        }
        if (record.getStatus() == null || record.getStatus().isBlank()) {
            record.setStatus("REGISTERED");
        } else {
            record.setStatus(OperationDict.visitorStatus(record.getStatus()));
        }
        if (record.getSource() == null || record.getSource().isBlank()) {
            record.setSource("ADMIN");
        }
        return visitorRecordRepository.save(record);
    }

    @Transactional
    public VisitorRecord update(Long id, VisitorRecord next) {
        VisitorRecord record = get(id);
        record.setVisitorName(next.getVisitorName());
        record.setVisitorPhone(next.getVisitorPhone());
        record.setIdCardNo(next.getIdCardNo());
        record.setCarPlateNo(next.getCarPlateNo());
        record.setTenantId(next.getTenantId());
        record.setVisitedPerson(next.getVisitedPerson());
        record.setVisitReason(next.getVisitReason());
        record.setVisitTime(next.getVisitTime());
        record.setRemark(next.getRemark());
        if (next.getStatus() != null) {
            record.setStatus(OperationDict.visitorStatus(next.getStatus()));
        }
        return visitorRecordRepository.save(record);
    }

    public List<VisitorRecord> list(String visitorName, String visitorPhone, String carPlateNo, Long tenantId,
                                    String visitedPerson, String source, String status, LocalDateTime startTime,
                                    LocalDateTime endTime) {
        String normalizedStatus = status == null || status.isBlank() ? null : OperationDict.visitorStatus(status);
        return visitorRecordRepository.findByDeletedAtIsNullOrderByVisitTimeDescCreatedTimeDesc()
                .stream()
                .filter(item -> contains(item.getVisitorName(), visitorName))
                .filter(item -> contains(item.getVisitorPhone(), visitorPhone))
                .filter(item -> contains(item.getCarPlateNo(), carPlateNo))
                .filter(item -> tenantId == null || tenantId.equals(item.getTenantId()))
                .filter(item -> contains(item.getVisitedPerson(), visitedPerson))
                .filter(item -> isBlank(source) || source.equals(item.getSource()))
                .filter(item -> normalizedStatus == null || normalizedStatus.equals(OperationDict.visitorStatus(item.getStatus())))
                .filter(item -> startTime == null || (item.getVisitTime() != null && !item.getVisitTime().isBefore(startTime)))
                .filter(item -> endTime == null || (item.getVisitTime() != null && !item.getVisitTime().isAfter(endTime)))
                .toList();
    }

    public List<VisitorRecord> list() {
        return list(null, null, null, null, null, null, null, null, null);
    }

    public VisitorRecord get(Long id) {
        return visitorRecordRepository.findById(id)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new RuntimeException("访客记录不存在"));
    }

    public List<VisitorRecord> listByTenant(Long tenantId) {
        return visitorRecordRepository.findByTenantId(tenantId)
                .stream()
                .filter(item -> item.getDeletedAt() == null)
                .toList();
    }

    @Transactional
    public VisitorRecord approve(Long id, String reviewedBy) {
        VisitorRecord record = get(id);
        record.setStatus("REGISTERED");
        record.setReviewedBy(reviewedBy == null || reviewedBy.isBlank() ? "system" : reviewedBy);
        record.setReviewTime(LocalDateTime.now());
        record.setRejectReason(null);
        return visitorRecordRepository.save(record);
    }

    @Transactional
    public VisitorRecord reject(Long id, String reviewedBy, String reason) {
        VisitorRecord record = get(id);
        record.setStatus("REJECTED");
        record.setReviewedBy(reviewedBy == null || reviewedBy.isBlank() ? "system" : reviewedBy);
        record.setReviewTime(LocalDateTime.now());
        record.setRejectReason(reason);
        return visitorRecordRepository.save(record);
    }

    @Transactional
    public VisitorRecord enter(Long id) {
        VisitorRecord record = get(id);
        record.setStatus("ENTERED");
        record.setEnterTime(LocalDateTime.now());
        return visitorRecordRepository.save(record);
    }

    @Transactional
    public VisitorRecord leave(Long id) {
        VisitorRecord record = get(id);
        record.setStatus("LEFT");
        record.setLeaveTime(LocalDateTime.now());
        return visitorRecordRepository.save(record);
    }

    @Transactional
    public VisitorRecord cancel(Long id) {
        VisitorRecord record = get(id);
        record.setStatus("CANCELLED");
        return visitorRecordRepository.save(record);
    }

    @Transactional
    public void delete(Long id) {
        VisitorRecord record = get(id);
        record.setDeletedAt(LocalDateTime.now());
        visitorRecordRepository.save(record);
    }

    public byte[] export(String visitorName, String visitorPhone, String carPlateNo, Long tenantId,
                         String visitedPerson, String source, String status, LocalDateTime startTime,
                         LocalDateTime endTime) {
        List<String> headers = List.of("访客姓名", "手机号", "车牌号", "租户", "被访人", "来访事由", "来源", "预约时间", "入场时间", "离场时间", "状态", "备注");
        List<List<Object>> rows = list(visitorName, visitorPhone, carPlateNo, tenantId, visitedPerson, source, status, startTime, endTime)
                .stream()
                .map(item -> List.<Object>of(
                        safe(item.getVisitorName()),
                        safe(item.getVisitorPhone()),
                        safe(item.getCarPlateNo()),
                        item.getTenantId() == null ? "" : tenantRepository.findById(item.getTenantId()).map(t -> t.getTenantName()).orElse("租户ID " + item.getTenantId()),
                        safe(item.getVisitedPerson()),
                        safe(item.getVisitReason()),
                        sourceLabel(item.getSource()),
                        item.getVisitTime() == null ? "" : item.getVisitTime(),
                        item.getEnterTime() == null ? "" : item.getEnterTime(),
                        item.getLeaveTime() == null ? "" : item.getLeaveTime(),
                        OperationDict.visitorStatusLabel(item.getStatus()),
                        safe(item.getRemark())
                ))
                .toList();
        return ExcelExportUtil.export("访客管理", headers, rows);
    }

    private String sourceLabel(String source) {
        return "MINIPROGRAM".equals(source) ? "小程序" : "后台";
    }

    private boolean contains(String source, String keyword) {
        if (isBlank(keyword)) {
            return true;
        }
        return source != null && source.toLowerCase(Locale.ROOT).contains(keyword.trim().toLowerCase(Locale.ROOT));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}

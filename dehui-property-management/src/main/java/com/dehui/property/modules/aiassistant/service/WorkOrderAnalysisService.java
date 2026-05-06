package com.dehui.property.modules.aiassistant.service;

import com.dehui.property.modules.workorder.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkOrderAnalysisService {

    private final WorkOrderRepository workOrderRepository;

    public Map<String, Object> analyze() {
        Long created = workOrderRepository.countByStatus("CREATED");
        Long assigned = workOrderRepository.countByStatus("ASSIGNED");
        Long processing = workOrderRepository.countByStatus("PROCESSING");
        Long completed = workOrderRepository.countByStatus("COMPLETED");
        Long closed = workOrderRepository.countByStatus("CLOSED");
        Long high = workOrderRepository.countByPriority("HIGH");

        String summary = "当前工单：处理中" + processing + "个，已完成" + completed + "个，高优先级" + high + "个。";

        return Map.of(
                "created", created,
                "assigned", assigned,
                "processing", processing,
                "completed", completed,
                "closed", closed,
                "highPriority", high,
                "summary", summary
        );
    }
}
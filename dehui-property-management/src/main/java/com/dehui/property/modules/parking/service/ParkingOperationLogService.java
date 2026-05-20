package com.dehui.property.modules.parking.service;

import com.dehui.property.modules.parking.dto.ParkingOperationLogResponse;
import com.dehui.property.modules.parking.entity.ParkingOperationLog;
import com.dehui.property.modules.parking.repository.ParkingOperationLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ParkingOperationLogService {
    private final ParkingOperationLogRepository repository;
    private final ObjectMapper objectMapper;

    public void write(String targetType, Long targetId, String action, Object before, Object after, String operatorName) {
        ParkingOperationLog log = new ParkingOperationLog();
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setAction(action);
        log.setBeforeJson(toJson(before));
        log.setAfterJson(toJson(after));
        log.setOperatorName(isBlank(operatorName) ? "system" : operatorName);
        repository.save(log);
    }

    public List<ParkingOperationLogResponse> list(String targetType, Long targetId) {
        return repository.findByTargetTypeAndTargetIdOrderByCreatedTimeDesc(targetType, targetId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private ParkingOperationLogResponse toResponse(ParkingOperationLog log) {
        ParkingOperationLogResponse response = new ParkingOperationLogResponse();
        response.setId(log.getId());
        response.setTargetType(log.getTargetType());
        response.setTargetId(log.getTargetId());
        response.setAction(log.getAction());
        response.setActionText(actionText(log.getAction()));
        response.setBeforeJson(log.getBeforeJson());
        response.setAfterJson(log.getAfterJson());
        response.setOperatorId(log.getOperatorId());
        response.setOperatorName(log.getOperatorName());
        response.setCreatedTime(log.getCreatedTime());
        return response;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private String actionText(String action) {
        return switch (action == null ? "" : action) {
            case "CREATE_SPACE" -> "新增车位";
            case "UPDATE_SPACE" -> "编辑车位";
            case "DELETE_SPACE" -> "删除车位";
            case "BIND" -> "绑定车位";
            case "RELEASE" -> "释放车位";
            case "GENERATE_BILL" -> "生成停车账单";
            case "SYNC_BILL" -> "同步账单中心";
            case "PAY_BILL" -> "停车账单收款";
            case "VOID_BILL" -> "作废停车账单";
            default -> action;
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

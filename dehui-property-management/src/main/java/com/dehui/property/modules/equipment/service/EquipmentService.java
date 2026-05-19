package com.dehui.property.modules.equipment.service;

import com.dehui.property.common.Result;
import com.dehui.property.modules.equipment.dto.EquipmentCreateRequest;
import com.dehui.property.modules.equipment.dto.EquipmentResponse;
import com.dehui.property.modules.equipment.entity.Equipment;
import com.dehui.property.modules.equipment.repository.EquipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EquipmentService {
    private final EquipmentRepository equipmentRepository;

    public Result<List<EquipmentResponse>> findAll() {
        List<EquipmentResponse> responses = equipmentRepository.findAll()
                .stream()
                .map(this::toEquipmentResponse)
                .collect(Collectors.toList());
        return Result.success(responses);
    }

    public Result<EquipmentResponse> findById(Long id) {
        return equipmentRepository.findById(id)
                .map(equipment -> Result.success(toEquipmentResponse(equipment)))
                .orElseGet(() -> Result.error("设备不存在"));
    }

    @Transactional
    public Result<EquipmentResponse> create(EquipmentCreateRequest request) {
        Equipment equipment = new Equipment();
        equipment.setEquipmentName(request.getEquipmentName());
        equipment.setEquipmentCode(request.getEquipmentCode());
        equipment.setEquipmentType(request.getEquipmentType());
        equipment.setLocation(request.getLocation());
        equipment.setManufacturer(request.getManufacturer());
        equipment.setModel(request.getModel());
        equipment.setInstallDate(request.getInstallDate());
        equipment.setRemark(request.getRemark());
        equipment.setStatus("NORMAL");

        Equipment saved = equipmentRepository.save(equipment);
        log.info("设备已创建: id={}, name={}", saved.getId(), saved.getEquipmentName());

        return Result.success(toEquipmentResponse(saved));
    }

    @Transactional
    public Result<EquipmentResponse> update(Long id, EquipmentCreateRequest request) {
        return equipmentRepository.findById(id)
                .map(equipment -> {
                    equipment.setEquipmentName(request.getEquipmentName());
                    equipment.setEquipmentCode(request.getEquipmentCode());
                    equipment.setEquipmentType(request.getEquipmentType());
                    equipment.setLocation(request.getLocation());
                    equipment.setManufacturer(request.getManufacturer());
                    equipment.setModel(request.getModel());
                    equipment.setInstallDate(request.getInstallDate());
                    equipment.setRemark(request.getRemark());

                    Equipment saved = equipmentRepository.save(equipment);
                    log.info("设备信息已更新: id={}, name={}", saved.getId(), saved.getEquipmentName());

                    return Result.success(toEquipmentResponse(saved));
                })
                .orElseGet(() -> Result.error("设备不存在"));
    }

    @Transactional
    public Result<EquipmentResponse> updateStatus(Long id, String status) {
        return equipmentRepository.findById(id)
                .map(equipment -> {
                    if (!isValidStatus(status)) {
                        return Result.<EquipmentResponse>error("无效的设备状态，支持：NORMAL、FAULT、MAINTENANCE、DISABLED");
                    }

                    equipment.setStatus(status);
                    Equipment saved = equipmentRepository.save(equipment);

                    log.info("设备状态已更新: id={}, status={}", saved.getId(), status);

                    return Result.success(toEquipmentResponse(saved));
                })
                .orElseGet(() -> Result.error("设备不存在"));
    }

    private boolean isValidStatus(String status) {
        return "NORMAL".equals(status)
                || "FAULT".equals(status)
                || "MAINTENANCE".equals(status)
                || "DISABLED".equals(status);
    }

    private EquipmentResponse toEquipmentResponse(Equipment equipment) {
        EquipmentResponse response = new EquipmentResponse();
        response.setId(equipment.getId());
        response.setEquipmentName(equipment.getEquipmentName());
        response.setEquipmentCode(equipment.getEquipmentCode());
        response.setEquipmentType(equipment.getEquipmentType());
        response.setLocation(equipment.getLocation());
        response.setStatus(equipment.getStatus());
        response.setManufacturer(equipment.getManufacturer());
        response.setModel(equipment.getModel());
        response.setInstallDate(equipment.getInstallDate());
        response.setRemark(equipment.getRemark());
        response.setCreatedTime(equipment.getCreatedTime());
        response.setUpdatedTime(equipment.getUpdatedTime());
        return response;
    }
}

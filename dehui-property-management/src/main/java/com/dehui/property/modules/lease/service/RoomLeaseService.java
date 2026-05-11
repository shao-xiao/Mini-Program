package com.dehui.property.modules.lease.service;

import com.dehui.property.common.Result;
import com.dehui.property.modules.building.entity.Room;
import com.dehui.property.modules.building.repository.RoomRepository;
import com.dehui.property.modules.contract.entity.Contract;
import com.dehui.property.modules.contract.repository.ContractRepository;
import com.dehui.property.modules.lease.dto.RoomLeaseCreateRequest;
import com.dehui.property.modules.lease.dto.RoomLeaseResponse;
import com.dehui.property.modules.lease.entity.RoomLease;
import com.dehui.property.modules.lease.repository.RoomLeaseRepository;
import com.dehui.property.modules.tenant.entity.Tenant;
import com.dehui.property.modules.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomLeaseService {
    private final RoomLeaseRepository roomLeaseRepository;
    private final RoomRepository roomRepository;
    private final TenantRepository tenantRepository;
    private final ContractRepository contractRepository;

    @Transactional
    public Result<RoomLeaseResponse> checkin(Long roomId, RoomLeaseCreateRequest request) {
        // 1. 校验房间存在
        Room room = roomRepository.findById(roomId)
                .orElse(null);
        if (room == null) {
            return Result.error("房间不存在");
        }

        // 2. 校验租户存在
        if (!tenantRepository.existsById(request.getTenantId())) {
            return Result.error("租户不存在");
        }

        Contract contract = null;
        if (request.getContractId() != null) {
            contract = contractRepository.findById(request.getContractId()).orElse(null);
            if (contract == null) {
                return Result.error("合同不存在");
            }
            if (!"ACTIVE".equals(contract.getStatus())) {
                return Result.error("合同未生效，无法办理入驻");
            }
            if (contract.getLeaseId() != null) {
                return Result.error("该合同已办理入驻");
            }
            if (!request.getTenantId().equals(contract.getTenantId()) || !roomId.equals(contract.getRoomId())) {
                return Result.error("入驻租户或房间与合同不一致");
            }
        }

        // 3. 校验房间状态为 AVAILABLE
        if (!"AVAILABLE".equals(room.getStatus())) {
            return Result.error("房间当前不可用，状态为：" + room.getStatus());
        }

        // 4. 校验同一房间同一时间无 ACTIVE 租约
        List<RoomLease> activeLeases = roomLeaseRepository.findByRoomIdAndStatusOrderByStartDateDesc(roomId, "ACTIVE");
        if (!activeLeases.isEmpty()) {
            return Result.error("该房间已有在租租约");
        }

        // 5. 创建租约
        RoomLease lease = new RoomLease();
        lease.setTenantId(request.getTenantId());
        lease.setRoomId(roomId);
        lease.setStartDate(request.getStartDate());
        lease.setEndDate(request.getEndDate());
        lease.setStatus("ACTIVE");
        lease.setRemark(request.getRemark());
        RoomLease saved = roomLeaseRepository.save(lease);

        if (contract != null) {
            contract.setLeaseId(saved.getId());
            contractRepository.save(contract);
        }

        // 6. 更新房间状态为 RENTED
        room.setStatus("RENTED");
        roomRepository.save(room);

        return Result.success(toResponse(saved));
    }

    @Transactional
    public Result<Void> checkout(Long roomId) {
        // 1. 校验房间存在
        Room room = roomRepository.findById(roomId)
                .orElse(null);
        if (room == null) {
            return Result.error("房间不存在");
        }

        // 2. 查找 ACTIVE 租约
        List<RoomLease> activeLeases = roomLeaseRepository.findByRoomIdAndStatusOrderByStartDateDesc(roomId, "ACTIVE");
        if (activeLeases.isEmpty()) {
            return Result.error("该房间没有在租租约，无法退租");
        }

        // 3. 结束租约
        RoomLease activeLease = activeLeases.get(0);
        activeLease.setStatus("ENDED");
        activeLease.setEndDate(LocalDate.now());
        roomLeaseRepository.save(activeLease);

        // 4. 更新房间状态为 AVAILABLE
        room.setStatus("AVAILABLE");
        roomRepository.save(room);

        return Result.success(null);
    }

    public Result<RoomLeaseResponse> getCurrentLease(Long roomId) {
        if (!roomRepository.existsById(roomId)) {
            return Result.error("房间不存在");
        }
        List<RoomLease> activeLeases = roomLeaseRepository.findByRoomIdAndStatusOrderByStartDateDesc(roomId, "ACTIVE");
        if (activeLeases.isEmpty()) {
            return Result.error("该房间当前无租户");
        }
        return Result.success(toResponse(activeLeases.get(0)));
    }

    public Result<List<RoomLeaseResponse>> getTenantLeases(Long tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            return Result.error("租户不存在");
        }
        List<RoomLease> leases = roomLeaseRepository.findByTenantIdOrderByStartDateDesc(tenantId);
        List<RoomLeaseResponse> responses = leases.stream().map(this::toResponse).toList();
        return Result.success(responses);
    }

    private RoomLeaseResponse toResponse(RoomLease lease) {
        RoomLeaseResponse response = new RoomLeaseResponse();
        response.setId(lease.getId());
        response.setTenantId(lease.getTenantId());
        response.setRoomId(lease.getRoomId());
        response.setStartDate(lease.getStartDate());
        response.setEndDate(lease.getEndDate());
        response.setStatus(lease.getStatus());
        response.setRemark(lease.getRemark());
        response.setCreatedTime(lease.getCreatedTime());
        response.setUpdatedTime(lease.getUpdatedTime());
        return response;
    }
}

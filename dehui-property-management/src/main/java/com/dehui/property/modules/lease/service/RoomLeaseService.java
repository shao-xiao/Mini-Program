package com.dehui.property.modules.lease.service;

import com.dehui.property.common.Result;
import com.dehui.property.modules.building.entity.Room;
import com.dehui.property.modules.building.repository.RoomRepository;
import com.dehui.property.modules.contract.dto.ContractActionRequest;
import com.dehui.property.modules.contract.entity.Contract;
import com.dehui.property.modules.contract.repository.ContractRepository;
import com.dehui.property.modules.contract.service.ContractService;
import com.dehui.property.modules.lease.dto.RoomLeaseCreateRequest;
import com.dehui.property.modules.lease.dto.RoomLeaseResponse;
import com.dehui.property.modules.lease.entity.Occupancy;
import com.dehui.property.modules.lease.entity.RoomLease;
import com.dehui.property.modules.lease.repository.OccupancyRepository;
import com.dehui.property.modules.lease.repository.RoomLeaseRepository;
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
    private final OccupancyRepository occupancyRepository;
    private final RoomRepository roomRepository;
    private final TenantRepository tenantRepository;
    private final ContractRepository contractRepository;
    private final ContractService contractService;

    @Transactional
    public Result<RoomLeaseResponse> checkin(Long roomId, RoomLeaseCreateRequest request) {
        if (request.getContractId() == null) {
            return Result.error("请选择合同后办理入驻");
        }
        Contract contract = contractRepository.findById(request.getContractId()).orElse(null);
        if (contract == null) {
            return Result.error("合同不存在");
        }
        if (!roomId.equals(contract.getRoomId())) {
            return Result.error("入住房间与合同不一致");
        }

        ContractActionRequest actionRequest = new ContractActionRequest();
        actionRequest.setActionDate(request.getStartDate());
        actionRequest.setRemark(request.getRemark());
        Result<?> result = contractService.checkIn(contract.getId(), actionRequest);
        if (result.getCode() != 200) {
            return Result.error(result.getMessage());
        }

        return occupancyRepository.findByContractIdAndStatus(contract.getId(), "ACTIVE")
                .map(occupancy -> Result.success(toResponse(occupancy)))
                .orElseGet(() -> Result.error("入驻记录创建失败"));
    }

    @Transactional
    public Result<Void> checkout(Long roomId) {
        List<Occupancy> occupancies = occupancyRepository.findByRoomIdAndStatusOrderByCheckInDateDesc(roomId, "ACTIVE");
        if (!occupancies.isEmpty()) {
            Occupancy occupancy = occupancies.get(0);
            ContractActionRequest request = new ContractActionRequest();
            request.setActionDate(LocalDate.now());
            request.setReason("退租");
            Result<?> result = contractService.terminate(occupancy.getContractId(), request);
            return result.getCode() == 200 ? Result.success(null) : Result.error(result.getMessage());
        }

        return checkoutLegacy(roomId);
    }

    public Result<RoomLeaseResponse> getCurrentLease(Long roomId) {
        List<Occupancy> occupancies = occupancyRepository.findByRoomIdAndStatusOrderByCheckInDateDesc(roomId, "ACTIVE");
        if (!occupancies.isEmpty()) {
            return Result.success(toResponse(occupancies.get(0)));
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
        List<RoomLeaseResponse> occupancies = occupancyRepository.findByTenantIdOrderByCheckInDateDesc(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
        if (!occupancies.isEmpty()) {
            return Result.success(occupancies);
        }
        return Result.success(roomLeaseRepository.findByTenantIdOrderByStartDateDesc(tenantId)
                .stream()
                .map(this::toResponse)
                .toList());
    }

    private Result<Void> checkoutLegacy(Long roomId) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            return Result.error("房间不存在");
        }
        List<RoomLease> activeLeases = roomLeaseRepository.findByRoomIdAndStatusOrderByStartDateDesc(roomId, "ACTIVE");
        if (activeLeases.isEmpty()) {
            return Result.error("该房间没有在租记录，无法退租");
        }
        RoomLease activeLease = activeLeases.get(0);
        activeLease.setStatus("ENDED");
        activeLease.setEndDate(LocalDate.now());
        roomLeaseRepository.save(activeLease);
        room.setStatus("VACANT");
        roomRepository.save(room);
        return Result.success(null);
    }

    private RoomLeaseResponse toResponse(Occupancy occupancy) {
        RoomLeaseResponse response = new RoomLeaseResponse();
        response.setId(occupancy.getId());
        response.setTenantId(occupancy.getTenantId());
        response.setRoomId(occupancy.getRoomId());
        response.setStartDate(occupancy.getCheckInDate());
        response.setEndDate(occupancy.getCheckoutDate() == null ? occupancy.getPlannedEndDate() : occupancy.getCheckoutDate());
        response.setStatus("ACTIVE".equals(occupancy.getStatus()) ? "ACTIVE" : "ENDED");
        response.setRemark(occupancy.getRemark());
        response.setCreatedTime(occupancy.getCreatedTime());
        response.setUpdatedTime(occupancy.getUpdatedTime());
        return response;
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

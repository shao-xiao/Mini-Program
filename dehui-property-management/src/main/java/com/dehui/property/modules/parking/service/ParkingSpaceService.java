package com.dehui.property.modules.parking.service;

import com.dehui.property.modules.parking.entity.ParkingSpace;
import com.dehui.property.modules.parking.repository.ParkingSpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ParkingSpaceService {

    private final ParkingSpaceRepository parkingSpaceRepository;

    public ParkingSpace create(ParkingSpace parkingSpace) {
        validateArea(parkingSpace.getArea());

        if (parkingSpace.getStatus() == null || parkingSpace.getStatus().isBlank()) {
            parkingSpace.setStatus("AVAILABLE");
        }

        if (parkingSpace.getSpaceType() == null || parkingSpace.getSpaceType().isBlank()) {
            parkingSpace.setSpaceType("NORMAL");
        }
        normalizeSpaceType(parkingSpace);

        return parkingSpaceRepository.save(parkingSpace);
    }

    public ParkingSpace update(Long id, ParkingSpace request) {
        ParkingSpace space = get(id);
        validateArea(request.getArea());

        space.setSpaceCode(request.getSpaceCode());
        space.setArea(request.getArea());
        space.setSpaceType(request.getSpaceType());
        space.setStatus(request.getStatus());
        space.setTenantId(request.getTenantId());
        space.setPlateNumber(request.getPlateNumber());
        space.setRemark(request.getRemark());

        if (space.getSpaceType() == null || space.getSpaceType().isBlank()) {
            space.setSpaceType("NORMAL");
        }
        normalizeSpaceType(space);
        if (space.getStatus() == null || space.getStatus().isBlank()) {
            space.setStatus(space.getPlateNumber() == null || space.getPlateNumber().isBlank()
                    ? "AVAILABLE"
                    : "OCCUPIED");
        }
        if (!"OCCUPIED".equals(space.getStatus())) {
            space.setTenantId(null);
            space.setPlateNumber(null);
            if ("VIP".equals(space.getSpaceType())) {
                space.setSpaceType("NORMAL");
            }
        }

        return parkingSpaceRepository.save(space);
    }

    public List<ParkingSpace> list() {
        return parkingSpaceRepository.findAll();
    }

    public ParkingSpace get(Long id) {
        return parkingSpaceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("车位不存在"));
    }

    public ParkingSpace bindToTenant(Long id, Long tenantId, Boolean vip, String plateNumber) {
        ParkingSpace space = get(id);

        if ("DISABLED".equals(space.getStatus())) {
            throw new RuntimeException("该车位已停用，无法绑定");
        }

        if ("OCCUPIED".equals(space.getStatus())) {
            throw new RuntimeException("该车位已被占用，请先释放后再绑定");
        }

        boolean bindVip = Boolean.TRUE.equals(vip);

        if (!bindVip && (tenantId == null || tenantId <= 0)) {
            throw new RuntimeException("请选择租户或VIP");
        }

        if (plateNumber == null || plateNumber.isBlank()) {
            throw new RuntimeException("车牌号不能为空");
        }

        space.setTenantId(bindVip ? null : tenantId);
        space.setPlateNumber(plateNumber.trim());
        if (bindVip) {
            space.setSpaceType("VIP");
        }
        space.setStatus("OCCUPIED");

        return parkingSpaceRepository.save(space);
    }

    public ParkingSpace release(Long id) {
        ParkingSpace space = get(id);

        space.setTenantId(null);
        space.setPlateNumber(null);
        space.setStatus("AVAILABLE");
        if ("VIP".equals(space.getSpaceType())) {
            space.setSpaceType("NORMAL");
        }

        return parkingSpaceRepository.save(space);
    }

    public ParkingSpace updateStatus(Long id, String status) {
        ParkingSpace space = get(id);
        space.setStatus(status);
        return parkingSpaceRepository.save(space);
    }

    public Long countAvailable() {
        return parkingSpaceRepository.countByStatus("AVAILABLE");
    }

    public Long countOccupied() {
        return parkingSpaceRepository.countByStatus("OCCUPIED");
    }

    public void delete(Long id) {
        ParkingSpace space = get(id);
        parkingSpaceRepository.delete(space);
    }
    
    public java.util.Map<String, Object> stats() {
    Long total = parkingSpaceRepository.count();
    Long available = parkingSpaceRepository.countByStatus("AVAILABLE");
    Long occupied = parkingSpaceRepository.countByStatus("OCCUPIED");
    Long disabled = parkingSpaceRepository.countByStatus("DISABLED");

    double occupancyRate = total == 0 ? 0 : occupied * 100.0 / total;

        return java.util.Map.of(
            "total", total,
            "available", available,
            "occupied", occupied,
            "disabled", disabled,
            "occupancyRate", occupancyRate
        );
    }

    private void validateArea(String area) {
        if (area == null || area.isBlank()) {
            throw new RuntimeException("车位区域不能为空");
        }
        if (!List.of("A", "B", "C", "D").contains(area)) {
            throw new RuntimeException("车位区域只能是A、B、C、D");
        }
    }

    private void normalizeSpaceType(ParkingSpace space) {
        String type = space.getSpaceType();
        if (type == null || type.isBlank()) {
            space.setSpaceType("NORMAL");
            return;
        }
        if ("FIXED".equals(type)) {
            space.setSpaceType("NORMAL");
        } else if ("TEMP".equals(type)) {
            space.setSpaceType("TEMPORARY");
        }
    }
}

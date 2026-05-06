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
        if (parkingSpace.getStatus() == null || parkingSpace.getStatus().isBlank()) {
            parkingSpace.setStatus("AVAILABLE");
        }

        if (parkingSpace.getSpaceType() == null || parkingSpace.getSpaceType().isBlank()) {
            parkingSpace.setSpaceType("FIXED");
        }

        return parkingSpaceRepository.save(parkingSpace);
    }

    public List<ParkingSpace> list() {
        return parkingSpaceRepository.findAll();
    }

    public ParkingSpace get(Long id) {
        return parkingSpaceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("车位不存在"));
    }

    public ParkingSpace bindToTenant(Long id, Long tenantId, String plateNumber) {
        ParkingSpace space = get(id);

        if ("DISABLED".equals(space.getStatus())) {
            throw new RuntimeException("该车位已停用，无法绑定");
        }

        space.setTenantId(tenantId);
        space.setPlateNumber(plateNumber);
        space.setStatus("OCCUPIED");

        return parkingSpaceRepository.save(space);
    }

    public ParkingSpace release(Long id) {
        ParkingSpace space = get(id);

        space.setTenantId(null);
        space.setPlateNumber(null);
        space.setStatus("AVAILABLE");

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
}
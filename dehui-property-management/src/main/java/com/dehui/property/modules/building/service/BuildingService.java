package com.dehui.property.modules.building.service;

import com.dehui.property.common.Result;
import com.dehui.property.modules.building.dto.BuildingCreateRequest;
import com.dehui.property.modules.building.dto.BuildingResponse;
import com.dehui.property.modules.building.dto.BuildingUpdateRequest;
import com.dehui.property.modules.building.dto.BuildingStatsResponse;
import com.dehui.property.modules.building.dto.FloorCreateRequest;
import com.dehui.property.modules.building.dto.FloorResponse;
import com.dehui.property.modules.building.dto.FloorStatsResponse;
import com.dehui.property.modules.building.dto.FloorUpdateRequest;
import com.dehui.property.modules.building.dto.RoomCreateRequest;
import com.dehui.property.modules.building.dto.RoomResponse;
import com.dehui.property.modules.building.dto.RoomUpdateRequest;
import com.dehui.property.modules.building.entity.Building;
import com.dehui.property.modules.building.entity.Floor;
import com.dehui.property.modules.building.entity.Room;
import com.dehui.property.modules.building.repository.BuildingRepository;
import com.dehui.property.modules.building.repository.FloorRepository;
import com.dehui.property.modules.building.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BuildingService {
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final RoomRepository roomRepository;

    public Page<BuildingResponse> findAll(int page, int size) {
        return buildingRepository.findAll(PageRequest.of(page, size))
                .map(this::toBuildingResponse);
    }

    public Result<BuildingResponse> findById(Long id) {
        return buildingRepository.findById(id)
                .map(building -> Result.success(toBuildingResponse(building)))
                .orElseGet(() -> Result.error("楼栋不存在"));
    }

    public BuildingResponse save(BuildingCreateRequest request) {
        Building building = new Building();
        applyBuildingCreate(building, request);
        return toBuildingResponse(buildingRepository.save(building));
    }

    public Result<BuildingResponse> update(Long id, BuildingUpdateRequest request) {
        return buildingRepository.findById(id)
                .map(existing -> {
                    applyBuildingUpdate(existing, request);
                    return Result.success(toBuildingResponse(buildingRepository.save(existing)));
                })
                .orElseGet(() -> Result.error("楼栋不存在"));
    }

    @Transactional
    public Result<Void> delete(Long id) {
        return buildingRepository.findById(id)
                .map(existing -> {
                    if (buildingRepository.countFloorsByBuildingId(id) > 0) {
                        return Result.<Void>error("该楼宇下存在楼层，无法删除");
                    }
                    buildingRepository.delete(existing);
                    return Result.<Void>success(null);
                })
                .orElseGet(() -> Result.error("楼栋不存在"));
    }

    public Result<List<FloorResponse>> listFloors(Long buildingId) {
        if (!buildingRepository.existsById(buildingId)) {
            return Result.error("楼栋不存在");
        }
        List<FloorResponse> floors = floorRepository.findByBuildingIdOrderByFloorNumberAsc(buildingId)
                .stream()
                .map(this::toFloorResponse)
                .toList();
        return Result.success(floors);
    }

    public Result<FloorResponse> findFloorById(Long buildingId, Long floorId) {
        if (!buildingRepository.existsById(buildingId)) {
            return Result.error("楼栋不存在");
        }
        return floorRepository.findById(floorId)
                .filter(existing -> existing.getBuilding().getId().equals(buildingId))
                .map(floor -> Result.success(toFloorResponse(floor)))
                .orElseGet(() -> Result.error("楼层不存在"));
    }

    public Result<FloorResponse> createFloor(Long buildingId, FloorCreateRequest request) {
        return buildingRepository.findById(buildingId)
                .map(building -> {
                    Floor floor = new Floor();
                    floor.setBuilding(building);
                    applyFloorCreate(floor, request);
                    return Result.success(toFloorResponse(floorRepository.save(floor)));
                })
                .orElseGet(() -> Result.error("楼栋不存在"));
    }

    public Result<FloorResponse> updateFloor(Long buildingId, Long floorId, FloorUpdateRequest request) {
        if (!buildingRepository.existsById(buildingId)) {
            return Result.error("楼栋不存在");
        }
        return floorRepository.findById(floorId)
                .filter(existing -> existing.getBuilding().getId().equals(buildingId))
                .map(existing -> {
                    applyFloorUpdate(existing, request);
                    return Result.success(toFloorResponse(floorRepository.save(existing)));
                })
                .orElseGet(() -> Result.error("楼层不存在"));
    }

    @Transactional(readOnly = true)
    public Result<BuildingStatsResponse> getBuildingStats(Long buildingId) {
        log.debug("getBuildingStats called with buildingId={}", buildingId);
        try {
            return buildingRepository.findById(buildingId)
                    .map(building -> {
                        Long bId = building.getId();
                        String bName = building.getBuildingName();
                        java.time.LocalDateTime ct = building.getCreatedTime();
                        java.time.LocalDateTime ut = building.getUpdatedTime();
                        log.debug("Building found: id={}, name={}", bId, bName);
                        BuildingStatsResponse stats = new BuildingStatsResponse();
                        stats.setBuildingId(bId);
                        stats.setBuildingName(bName);
                        stats.setFloorCount(buildingRepository.countFloorsByBuildingId(bId));
                        stats.setRoomCount(buildingRepository.countRoomsByBuildingId(bId));
                        stats.setTotalArea(buildingRepository.sumRoomAreaByBuildingId(bId));
                        stats.setAvailableCount(buildingRepository.countRoomsByBuildingIdAndStatus(bId, "AVAILABLE"));
                        stats.setRentedCount(buildingRepository.countRoomsByBuildingIdAndStatus(bId, "RENTED"));
                        stats.setMaintenanceCount(buildingRepository.countRoomsByBuildingIdAndStatus(bId, "MAINTENANCE"));
                        stats.setReservedCount(buildingRepository.countRoomsByBuildingIdAndStatus(bId, "RESERVED"));
                        stats.setDisabledCount(buildingRepository.countRoomsByBuildingIdAndStatus(bId, "DISABLED"));
                        Long totalRooms = buildingRepository.countRoomsByBuildingId(bId);
                        Long rented = buildingRepository.countRoomsByBuildingIdAndStatus(bId, "RENTED");
                        Long available = buildingRepository.countRoomsByBuildingIdAndStatus(bId, "AVAILABLE");
                        if (totalRooms > 0) {
                            stats.setRentalRate(Math.round(rented * 10000.0 / totalRooms) / 100.0);
                            stats.setVacancyRate(Math.round(available * 10000.0 / totalRooms) / 100.0);
                        } else {
                            stats.setRentalRate(0.0);
                            stats.setVacancyRate(0.0);
                        }
                        stats.setCreatedTime(ct);
                        stats.setUpdatedTime(ut);
                        return Result.success(stats);
                    })
                    .orElseGet(() -> {
                        log.warn("Building with id={} not found in database", buildingId);
                        return Result.error("楼栋不存在");
                    });
        } catch (Exception e) {
            log.error("Error in getBuildingStats for buildingId={}: {}", buildingId, e.getMessage(), e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Result<FloorStatsResponse> getFloorStats(Long buildingId, Long floorId) {
        log.debug("getFloorStats called with buildingId={}, floorId={}", buildingId, floorId);
        try {
            if (!buildingRepository.existsById(buildingId)) {
                return Result.error("楼栋不存在");
            }
            return floorRepository.findById(floorId)
                    .filter(existing -> existing.getBuilding().getId().equals(buildingId))
                    .map(floor -> {
                        Long fId = floor.getId();
                        Integer fNum = floor.getFloorNumber();
                        String fName = floor.getFloorName();
                        java.time.LocalDateTime ct = floor.getCreatedTime();
                        java.time.LocalDateTime ut = floor.getUpdatedTime();
                        log.debug("Floor found: id={}, number={}", fId, fNum);
                        FloorStatsResponse stats = new FloorStatsResponse();
                        stats.setFloorId(fId);
                        stats.setBuildingId(buildingId);
                        stats.setFloorNumber(fNum);
                        stats.setFloorName(fName);
                        stats.setRoomCount(floorRepository.countRoomsByFloorId(fId));
                        stats.setTotalArea(floorRepository.sumRoomAreaByFloorId(fId));
                        stats.setAvailableCount(floorRepository.countRoomsByFloorIdAndStatus(fId, "AVAILABLE"));
                        stats.setRentedCount(floorRepository.countRoomsByFloorIdAndStatus(fId, "RENTED"));
                        stats.setMaintenanceCount(floorRepository.countRoomsByFloorIdAndStatus(fId, "MAINTENANCE"));
                        stats.setReservedCount(floorRepository.countRoomsByFloorIdAndStatus(fId, "RESERVED"));
                        stats.setDisabledCount(floorRepository.countRoomsByFloorIdAndStatus(fId, "DISABLED"));
                        stats.setCreatedTime(ct);
                        stats.setUpdatedTime(ut);
                        return Result.success(stats);
                    })
                    .orElseGet(() -> {
                        log.warn("Floor with id={} not found or does not belong to building {}", floorId, buildingId);
                        return Result.error("楼层不存在");
                    });
        } catch (Exception e) {
            log.error("Error in getFloorStats for buildingId={}, floorId={}: {}", buildingId, floorId, e.getMessage(), e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @Transactional
    public Result<Void> deleteFloor(Long buildingId, Long floorId) {
        if (!buildingRepository.existsById(buildingId)) {
            return Result.error("楼栋不存在");
        }
        return floorRepository.findById(floorId)
                .filter(existing -> existing.getBuilding().getId().equals(buildingId))
                .map(existing -> {
                    if (floorRepository.countRoomsByFloorId(floorId) > 0) {
                        return Result.<Void>error("该楼层下存在房间，无法删除");
                    }
                    floorRepository.delete(existing);
                    return Result.<Void>success(null);
                })
                .orElseGet(() -> Result.error("楼层不存在"));
    }

    public Result<List<RoomResponse>> listRooms(Long buildingId, Long floorId) {
        Result<Floor> floorResult = getFloor(buildingId, floorId);
        if (floorResult.getCode() != 200) {
            return Result.error(floorResult.getMessage());
        }
        List<RoomResponse> rooms = roomRepository.findByFloorIdOrderByRoomNumberAsc(floorId)
                .stream()
                .map(this::toRoomResponse)
                .toList();
        return Result.success(rooms);
    }

    public Result<RoomResponse> findRoomById(Long buildingId, Long floorId, Long roomId) {
        Result<Floor> floorResult = getFloor(buildingId, floorId);
        if (floorResult.getCode() != 200) {
            return Result.error(floorResult.getMessage());
        }
        return roomRepository.findById(roomId)
                .filter(existing -> existing.getFloor().getId().equals(floorId))
                .map(room -> Result.success(toRoomResponse(room)))
                .orElseGet(() -> Result.error("房间不存在"));
    }

    public Result<RoomResponse> createRoom(Long buildingId, Long floorId, RoomCreateRequest request) {
        Result<Floor> floorResult = getFloor(buildingId, floorId);
        if (floorResult.getCode() != 200) {
            return Result.error(floorResult.getMessage());
        }
        Room room = new Room();
        room.setFloor(floorResult.getData());
        applyRoomCreate(room, request);
        return Result.success(toRoomResponse(roomRepository.save(room)));
    }

    public Result<RoomResponse> updateRoom(Long buildingId, Long floorId, Long roomId, RoomUpdateRequest request) {
        Result<Floor> floorResult = getFloor(buildingId, floorId);
        if (floorResult.getCode() != 200) {
            return Result.error(floorResult.getMessage());
        }
        return roomRepository.findById(roomId)
                .filter(existing -> existing.getFloor().getId().equals(floorId))
                .map(existing -> {
                    applyRoomUpdate(existing, request);
                    return Result.success(toRoomResponse(roomRepository.save(existing)));
                })
                .orElseGet(() -> Result.error("房间不存在"));
    }

    public Result<RoomResponse> updateRoomStatus(Long buildingId, Long floorId, Long roomId, String status) {
        Result<Floor> floorResult = getFloor(buildingId, floorId);
        if (floorResult.getCode() != 200) {
            return Result.error(floorResult.getMessage());
        }
        return roomRepository.findById(roomId)
                .filter(existing -> existing.getFloor().getId().equals(floorId))
                .map(existing -> {
                    existing.setStatus(status);
                    return Result.success(toRoomResponse(roomRepository.save(existing)));
                })
                .orElseGet(() -> Result.error("房间不存在"));
    }

    public Result<Void> deleteRoom(Long buildingId, Long floorId, Long roomId) {
        Result<Floor> floorResult = getFloor(buildingId, floorId);
        if (floorResult.getCode() != 200) {
            return Result.error(floorResult.getMessage());
        }
        return roomRepository.findById(roomId)
                .filter(existing -> existing.getFloor().getId().equals(floorId))
                .map(existing -> {
                    roomRepository.delete(existing);
                    return Result.<Void>success(null);
                })
                .orElseGet(() -> Result.error("房间不存在"));
    }

    private Result<Floor> getFloor(Long buildingId, Long floorId) {
        if (!buildingRepository.existsById(buildingId)) {
            return Result.error("楼栋不存在");
        }
        return floorRepository.findById(floorId)
                .filter(existing -> existing.getBuilding().getId().equals(buildingId))
                .map(Result::success)
                .orElseGet(() -> Result.error("楼层不存在"));
    }

    private void applyBuildingCreate(Building building, BuildingCreateRequest request) {
        building.setBuildingName(request.getBuildingName());
        building.setBuildingCode(request.getBuildingCode());
        building.setAddress(request.getAddress());
        building.setTotalFloors(request.getTotalFloors());
        building.setDescription(request.getDescription());
        building.setStatus(request.getStatus() == null ? "AVAILABLE" : request.getStatus());
    }

    private void applyBuildingUpdate(Building building, BuildingUpdateRequest request) {
        building.setBuildingName(request.getBuildingName());
        building.setBuildingCode(request.getBuildingCode());
        building.setAddress(request.getAddress());
        building.setTotalFloors(request.getTotalFloors());
        building.setDescription(request.getDescription());
        building.setStatus(request.getStatus());
    }

    private void applyFloorCreate(Floor floor, FloorCreateRequest request) {
        floor.setFloorNumber(request.getFloorNumber());
        floor.setFloorName(request.getFloorName());
        floor.setTotalArea(request.getTotalArea());
    }

    private void applyFloorUpdate(Floor floor, FloorUpdateRequest request) {
        floor.setFloorNumber(request.getFloorNumber());
        floor.setFloorName(request.getFloorName());
        floor.setTotalArea(request.getTotalArea());
    }

    private void applyRoomCreate(Room room, RoomCreateRequest request) {
        room.setRoomNumber(request.getRoomNumber());
        room.setArea(request.getArea());
        room.setRoomType(request.getRoomType());
        room.setStatus(request.getStatus() == null ? "AVAILABLE" : request.getStatus());
    }

    private void applyRoomUpdate(Room room, RoomUpdateRequest request) {
        room.setRoomNumber(request.getRoomNumber());
        room.setArea(request.getArea());
        room.setRoomType(request.getRoomType());
        room.setStatus(request.getStatus());
    }

    private BuildingResponse toBuildingResponse(Building building) {
        BuildingResponse response = new BuildingResponse();
        response.setId(building.getId());
        response.setBuildingName(building.getBuildingName());
        response.setBuildingCode(building.getBuildingCode());
        response.setAddress(building.getAddress());
        response.setTotalFloors(building.getTotalFloors());
        response.setDescription(building.getDescription());
        response.setStatus(building.getStatus());
        response.setCreatedTime(building.getCreatedTime());
        response.setUpdatedTime(building.getUpdatedTime());
        return response;
    }

    private FloorResponse toFloorResponse(Floor floor) {
        FloorResponse response = new FloorResponse();
        response.setId(floor.getId());
        response.setBuildingId(floor.getBuilding().getId());
        response.setFloorNumber(floor.getFloorNumber());
        response.setFloorName(floor.getFloorName());
        response.setTotalArea(floor.getTotalArea());
        response.setCreatedTime(floor.getCreatedTime());
        response.setUpdatedTime(floor.getUpdatedTime());
        return response;
    }

    private RoomResponse toRoomResponse(Room room) {
        RoomResponse response = new RoomResponse();
        response.setId(room.getId());
        response.setFloorId(room.getFloor().getId());
        response.setRoomNumber(room.getRoomNumber());
        response.setArea(room.getArea());
        response.setRoomType(room.getRoomType());
        response.setStatus(room.getStatus());
        response.setCreatedTime(room.getCreatedTime());
        response.setUpdatedTime(room.getUpdatedTime());
        return response;
    }
}

package com.dehui.property.modules.building.service;

import com.dehui.property.common.Result;
import com.dehui.property.modules.asset.repository.AssetRepository;
import com.dehui.property.modules.building.dto.BuildingCreateRequest;
import com.dehui.property.modules.building.dto.BuildingResponse;
import com.dehui.property.modules.building.dto.BuildingStatsResponse;
import com.dehui.property.modules.building.dto.BuildingUpdateRequest;
import com.dehui.property.modules.building.dto.FloorBatchGenerateRequest;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BuildingService {
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final RoomRepository roomRepository;
    private final AssetRepository assetRepository;

    public Page<BuildingResponse> findAll(int page, int size) {
        return buildingRepository.findByDeletedAtIsNull(PageRequest.of(page, size))
                .map(this::toBuildingResponse);
    }

    public Result<BuildingResponse> findById(Long id) {
        return buildingRepository.findById(id)
                .filter(this::notDeleted)
                .map(building -> Result.success(toBuildingResponse(building)))
                .orElseGet(() -> Result.error("楼宇不存在"));
    }

    public BuildingResponse save(BuildingCreateRequest request) {
        Building building = new Building();
        applyBuildingCreate(building, request);
        return toBuildingResponse(buildingRepository.save(building));
    }

    public Result<BuildingResponse> update(Long id, BuildingUpdateRequest request) {
        return buildingRepository.findById(id)
                .filter(this::notDeleted)
                .map(existing -> {
                    applyBuildingUpdate(existing, request);
                    return Result.success(toBuildingResponse(buildingRepository.save(existing)));
                })
                .orElseGet(() -> Result.error("楼宇不存在"));
    }

    @Transactional
    public Result<Void> delete(Long id) {
        return buildingRepository.findById(id)
                .filter(this::notDeleted)
                .map(existing -> {
                    existing.setDeletedAt(LocalDateTime.now());
                    existing.setStatus("DISABLED");
                    buildingRepository.save(existing);
                    return Result.<Void>success(null);
                })
                .orElseGet(() -> Result.error("楼宇不存在"));
    }

    public Result<List<FloorResponse>> listFloors(Long buildingId) {
        Result<Building> building = getBuilding(buildingId);
        if (building.getCode() != 200) {
            return Result.error(building.getMessage());
        }
        List<FloorResponse> floors = floorRepository.findByBuildingIdAndDeletedAtIsNullOrderBySortOrderAscFloorNumberAsc(buildingId)
                .stream()
                .map(this::toFloorResponse)
                .toList();
        return Result.success(floors);
    }

    public Result<FloorResponse> findFloorById(Long buildingId, Long floorId) {
        Result<Floor> floor = getFloor(buildingId, floorId);
        if (floor.getCode() != 200) {
            return Result.error(floor.getMessage());
        }
        return Result.success(toFloorResponse(floor.getData()));
    }

    public Result<FloorResponse> createFloor(FloorCreateRequest request) {
        if (request.getBuildingId() == null) {
            return Result.error("楼宇ID不能为空");
        }
        return createFloor(request.getBuildingId(), request);
    }

    public Result<FloorResponse> createFloor(Long buildingId, FloorCreateRequest request) {
        Result<Building> building = getBuilding(buildingId);
        if (building.getCode() != 200) {
            return Result.error(building.getMessage());
        }
        Floor floor = new Floor();
        floor.setBuilding(building.getData());
        applyFloorCreate(floor, request);
        return Result.success(toFloorResponse(floorRepository.save(floor)));
    }

    public Result<FloorResponse> updateFloor(Long floorId, FloorUpdateRequest request) {
        Long buildingId = request.getBuildingId();
        if (buildingId == null) {
            return Result.error("楼宇ID不能为空");
        }
        return updateFloor(buildingId, floorId, request);
    }

    public Result<FloorResponse> updateFloor(Long buildingId, Long floorId, FloorUpdateRequest request) {
        Result<Floor> floorResult = getFloor(buildingId, floorId);
        if (floorResult.getCode() != 200) {
            return Result.error(floorResult.getMessage());
        }
        Floor existing = floorResult.getData();
        applyFloorUpdate(existing, request);
        return Result.success(toFloorResponse(floorRepository.save(existing)));
    }

    @Transactional
    public Result<Void> deleteFloor(Long floorId) {
        return floorRepository.findById(floorId)
                .filter(this::notDeleted)
                .map(existing -> softDeleteFloor(existing))
                .orElseGet(() -> Result.error("楼层不存在"));
    }

    @Transactional
    public Result<Void> deleteFloor(Long buildingId, Long floorId) {
        Result<Floor> floorResult = getFloor(buildingId, floorId);
        if (floorResult.getCode() != 200) {
            return Result.error(floorResult.getMessage());
        }
        return softDeleteFloor(floorResult.getData());
    }

    @Transactional
    public Result<List<FloorResponse>> batchGenerateFloors(FloorBatchGenerateRequest request) {
        Result<Building> buildingResult = getBuilding(request.getBuildingId());
        if (buildingResult.getCode() != 200) {
            return Result.error(buildingResult.getMessage());
        }

        Building building = buildingResult.getData();
        List<Floor> existing = floorRepository.findByBuildingIdAndDeletedAtIsNullOrderBySortOrderAscFloorNumberAsc(building.getId());
        List<Floor> created = new ArrayList<>();

        int basementStart = request.getBasementStart() == null ? 2 : request.getBasementStart();
        int basementEnd = request.getBasementEnd() == null ? 1 : request.getBasementEnd();
        for (int level = basementStart; level >= basementEnd; level--) {
            created.add(createGeneratedFloorIfMissing(building, existing, -level, "B" + level, request));
        }

        int aboveStart = request.getAboveStart() == null ? 1 : request.getAboveStart();
        int aboveEnd = request.getAboveEnd() == null ? 9 : request.getAboveEnd();
        for (int level = aboveStart; level <= aboveEnd; level++) {
            created.add(createGeneratedFloorIfMissing(building, existing, level, level + "F", request));
        }

        List<FloorResponse> responses = created.stream()
                .filter(item -> item != null)
                .sorted(Comparator.comparing(Floor::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(Floor::getFloorNumber))
                .map(this::toFloorResponse)
                .toList();
        return Result.success(responses);
    }

    public Result<List<RoomResponse>> listRooms(Long buildingId, Long floorId, String status) {
        Result<Building> building = getBuilding(buildingId);
        if (building.getCode() != 200) {
            return Result.error(building.getMessage());
        }
        if (floorId != null) {
            Result<Floor> floor = getFloor(buildingId, floorId);
            if (floor.getCode() != 200) {
                return Result.error(floor.getMessage());
            }
        }

        List<Room> rooms;
        if (floorId != null && !isBlank(status)) {
            rooms = roomRepository.findByBuildingIdAndFloorIdAndStatusAndDeletedAtIsNullOrderByRoomNumberAsc(buildingId, floorId, status);
        } else if (floorId != null) {
            rooms = roomRepository.findByBuildingIdAndFloorIdAndDeletedAtIsNullOrderByRoomNumberAsc(buildingId, floorId);
        } else if (!isBlank(status)) {
            rooms = roomRepository.findByBuildingIdAndStatusAndDeletedAtIsNullOrderByRoomNumberAsc(buildingId, status);
        } else {
            rooms = roomRepository.findByBuildingIdAndDeletedAtIsNullOrderByRoomNumberAsc(buildingId);
        }
        return Result.success(rooms.stream().map(this::toRoomResponse).toList());
    }

    public Result<List<RoomResponse>> listRooms(Long buildingId, Long floorId) {
        return listRooms(buildingId, floorId, null);
    }

    public Result<RoomResponse> findRoomById(Long buildingId, Long floorId, Long roomId) {
        Result<Room> room = getRoom(buildingId, floorId, roomId);
        if (room.getCode() != 200) {
            return Result.error(room.getMessage());
        }
        return Result.success(toRoomResponse(room.getData()));
    }

    public Result<RoomResponse> createRoom(RoomCreateRequest request) {
        if (request.getBuildingId() == null || request.getFloorId() == null) {
            return Result.error("楼宇和楼层不能为空");
        }
        return createRoom(request.getBuildingId(), request.getFloorId(), request);
    }

    public Result<RoomResponse> createRoom(Long buildingId, Long floorId, RoomCreateRequest request) {
        Result<Floor> floorResult = getFloor(buildingId, floorId);
        if (floorResult.getCode() != 200) {
            return Result.error(floorResult.getMessage());
        }
        Room room = new Room();
        room.setBuilding(floorResult.getData().getBuilding());
        room.setFloor(floorResult.getData());
        applyRoomCreate(room, request);
        return Result.success(toRoomResponse(roomRepository.save(room)));
    }

    public Result<RoomResponse> updateRoom(Long roomId, RoomUpdateRequest request) {
        if (request.getBuildingId() == null || request.getFloorId() == null) {
            return Result.error("楼宇和楼层不能为空");
        }
        return updateRoom(request.getBuildingId(), request.getFloorId(), roomId, request);
    }

    public Result<RoomResponse> updateRoom(Long buildingId, Long floorId, Long roomId, RoomUpdateRequest request) {
        Result<Floor> floorResult = getFloor(buildingId, floorId);
        if (floorResult.getCode() != 200) {
            return Result.error(floorResult.getMessage());
        }
        return roomRepository.findById(roomId)
                .filter(this::notDeleted)
                .map(existing -> {
                    existing.setBuilding(floorResult.getData().getBuilding());
                    existing.setFloor(floorResult.getData());
                    applyRoomUpdate(existing, request);
                    return Result.success(toRoomResponse(roomRepository.save(existing)));
                })
                .orElseGet(() -> Result.error("房间不存在"));
    }

    public Result<RoomResponse> updateRoomStatus(Long buildingId, Long floorId, Long roomId, String status) {
        Result<Room> room = getRoom(buildingId, floorId, roomId);
        if (room.getCode() != 200) {
            return Result.error(room.getMessage());
        }
        Room existing = room.getData();
        existing.setStatus(status);
        return Result.success(toRoomResponse(roomRepository.save(existing)));
    }

    @Transactional
    public Result<Void> deleteRoom(Long roomId) {
        return roomRepository.findById(roomId)
                .filter(this::notDeleted)
                .map(this::softDeleteRoom)
                .orElseGet(() -> Result.error("房间不存在"));
    }

    @Transactional
    public Result<Void> deleteRoom(Long buildingId, Long floorId, Long roomId) {
        Result<Room> room = getRoom(buildingId, floorId, roomId);
        if (room.getCode() != 200) {
            return Result.error(room.getMessage());
        }
        return softDeleteRoom(room.getData());
    }

    @Transactional(readOnly = true)
    public Result<BuildingStatsResponse> getBuildingStats(Long buildingId) {
        Result<Building> buildingResult = getBuilding(buildingId);
        if (buildingResult.getCode() != 200) {
            return Result.error(buildingResult.getMessage());
        }

        Building building = buildingResult.getData();
        Long bId = building.getId();
        BuildingStatsResponse stats = new BuildingStatsResponse();
        stats.setBuildingId(bId);
        stats.setBuildingName(building.getBuildingName());
        stats.setFloorCount(buildingRepository.countFloorsByBuildingId(bId));
        stats.setRoomCount(buildingRepository.countRoomsByBuildingId(bId));
        stats.setTotalArea(buildingRepository.sumRoomAreaByBuildingId(bId));
        stats.setAvailableCount(buildingRepository.countRoomsByBuildingIdAndStatus(bId, "AVAILABLE"));
        stats.setRentedCount(buildingRepository.countRoomsByBuildingIdAndStatus(bId, "RENTED"));
        stats.setMaintenanceCount(buildingRepository.countRoomsByBuildingIdAndStatus(bId, "RENOVATING"));
        stats.setReservedCount(buildingRepository.countRoomsByBuildingIdAndStatus(bId, "RESERVED"));
        stats.setDisabledCount(buildingRepository.countRoomsByBuildingIdAndStatus(bId, "DISABLED"));
        Long totalRooms = stats.getRoomCount();
        Long rented = stats.getRentedCount();
        Long available = stats.getAvailableCount();
        if (totalRooms != null && totalRooms > 0) {
            stats.setRentalRate(Math.round(rented * 10000.0 / totalRooms) / 100.0);
            stats.setVacancyRate(Math.round(available * 10000.0 / totalRooms) / 100.0);
        } else {
            stats.setRentalRate(0.0);
            stats.setVacancyRate(0.0);
        }
        stats.setCreatedTime(building.getCreatedTime());
        stats.setUpdatedTime(building.getUpdatedTime());
        return Result.success(stats);
    }

    @Transactional(readOnly = true)
    public Result<FloorStatsResponse> getFloorStats(Long buildingId, Long floorId) {
        Result<Floor> floorResult = getFloor(buildingId, floorId);
        if (floorResult.getCode() != 200) {
            return Result.error(floorResult.getMessage());
        }

        Floor floor = floorResult.getData();
        Long fId = floor.getId();
        FloorStatsResponse stats = new FloorStatsResponse();
        stats.setFloorId(fId);
        stats.setBuildingId(buildingId);
        stats.setFloorNumber(floor.getFloorNumber());
        stats.setFloorName(floor.getFloorName());
        stats.setRoomCount(floorRepository.countRoomsByFloorId(fId));
        stats.setTotalArea(floorRepository.sumRoomAreaByFloorId(fId));
        stats.setAvailableCount(floorRepository.countRoomsByFloorIdAndStatus(fId, "AVAILABLE"));
        stats.setRentedCount(floorRepository.countRoomsByFloorIdAndStatus(fId, "RENTED"));
        stats.setMaintenanceCount(floorRepository.countRoomsByFloorIdAndStatus(fId, "RENOVATING"));
        stats.setReservedCount(floorRepository.countRoomsByFloorIdAndStatus(fId, "RESERVED"));
        stats.setDisabledCount(floorRepository.countRoomsByFloorIdAndStatus(fId, "DISABLED"));
        stats.setCreatedTime(floor.getCreatedTime());
        stats.setUpdatedTime(floor.getUpdatedTime());
        return Result.success(stats);
    }

    private Result<Building> getBuilding(Long buildingId) {
        return buildingRepository.findById(buildingId)
                .filter(this::notDeleted)
                .map(Result::success)
                .orElseGet(() -> Result.error("楼宇不存在"));
    }

    private Result<Floor> getFloor(Long buildingId, Long floorId) {
        Result<Building> building = getBuilding(buildingId);
        if (building.getCode() != 200) {
            return Result.error(building.getMessage());
        }
        return floorRepository.findById(floorId)
                .filter(this::notDeleted)
                .filter(existing -> existing.getBuilding().getId().equals(buildingId))
                .map(Result::success)
                .orElseGet(() -> Result.error("楼层不存在或不属于所选楼宇"));
    }

    private Result<Room> getRoom(Long buildingId, Long floorId, Long roomId) {
        Result<Floor> floor = getFloor(buildingId, floorId);
        if (floor.getCode() != 200) {
            return Result.error(floor.getMessage());
        }
        return roomRepository.findById(roomId)
                .filter(this::notDeleted)
                .filter(existing -> existing.getFloor().getId().equals(floorId))
                .filter(existing -> resolveRoomBuildingId(existing).equals(buildingId))
                .map(Result::success)
                .orElseGet(() -> Result.error("房间不存在或不属于所选楼宇/楼层"));
    }

    private Result<Void> softDeleteFloor(Floor floor) {
        floor.setDeletedAt(LocalDateTime.now());
        floor.setStatus("DISABLED");
        floorRepository.save(floor);
        return Result.success(null);
    }

    private Result<Void> softDeleteRoom(Room room) {
        room.setDeletedAt(LocalDateTime.now());
        if (assetRepository.countByRoomIdAndDeletedAtIsNull(room.getId()) > 0) {
            room.setStatus("DISABLED");
        } else if (isBlank(room.getStatus())) {
            room.setStatus("DISABLED");
        }
        roomRepository.save(room);
        return Result.success(null);
    }

    private Floor createGeneratedFloorIfMissing(Building building, List<Floor> existing, Integer floorNumber,
                                                String floorName, FloorBatchGenerateRequest request) {
        boolean exists = existing.stream().anyMatch(item -> floorNumber.equals(item.getFloorNumber()));
        if (exists) {
            return null;
        }
        Floor floor = new Floor();
        floor.setBuilding(building);
        floor.setFloorNumber(floorNumber);
        floor.setFloorName(floorName);
        floor.setSortOrder(floorNumber < 0 ? 100 + floorNumber : 100 + floorNumber);
        floor.setTotalArea(request.getTotalArea());
        floor.setStatus(isBlank(request.getStatus()) ? "ACTIVE" : request.getStatus());
        return floorRepository.save(floor);
    }

    private void applyBuildingCreate(Building building, BuildingCreateRequest request) {
        building.setBuildingName(request.getBuildingName());
        building.setBuildingCode(request.getBuildingCode());
        building.setAddress(request.getAddress());
        building.setTotalFloors(request.getTotalFloors());
        building.setDescription(request.getDescription());
        building.setStatus(isBlank(request.getStatus()) ? "ACTIVE" : request.getStatus());
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
        floor.setSortOrder(request.getSortOrder() == null ? request.getFloorNumber() : request.getSortOrder());
        floor.setTotalArea(request.getTotalArea());
        floor.setDescription(request.getDescription());
        floor.setStatus(isBlank(request.getStatus()) ? "ACTIVE" : request.getStatus());
    }

    private void applyFloorUpdate(Floor floor, FloorUpdateRequest request) {
        floor.setFloorNumber(request.getFloorNumber());
        floor.setFloorName(request.getFloorName());
        floor.setSortOrder(request.getSortOrder() == null ? request.getFloorNumber() : request.getSortOrder());
        floor.setTotalArea(request.getTotalArea());
        floor.setDescription(request.getDescription());
        floor.setStatus(request.getStatus());
    }

    private void applyRoomCreate(Room room, RoomCreateRequest request) {
        room.setRoomNumber(request.getRoomNumber());
        room.setRoomName(request.getRoomName());
        room.setArea(request.getArea());
        room.setRoomType(request.getRoomType());
        room.setStatus(isBlank(request.getStatus()) ? "AVAILABLE" : request.getStatus());
        room.setDescription(request.getDescription());
    }

    private void applyRoomUpdate(Room room, RoomUpdateRequest request) {
        room.setRoomNumber(request.getRoomNumber());
        room.setRoomName(request.getRoomName());
        room.setArea(request.getArea());
        room.setRoomType(request.getRoomType());
        room.setStatus(request.getStatus());
        room.setDescription(request.getDescription());
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
        response.setDeletedAt(building.getDeletedAt());
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
        response.setSortOrder(floor.getSortOrder());
        response.setTotalArea(floor.getTotalArea());
        response.setDescription(floor.getDescription());
        response.setStatus(floor.getStatus());
        response.setDeletedAt(floor.getDeletedAt());
        response.setCreatedTime(floor.getCreatedTime());
        response.setUpdatedTime(floor.getUpdatedTime());
        return response;
    }

    private RoomResponse toRoomResponse(Room room) {
        RoomResponse response = new RoomResponse();
        response.setId(room.getId());
        Long buildingId = resolveRoomBuildingId(room);
        response.setBuildingId(buildingId);
        if (room.getBuilding() != null) {
            response.setBuildingName(room.getBuilding().getBuildingName());
        } else if (room.getFloor() != null && room.getFloor().getBuilding() != null) {
            response.setBuildingName(room.getFloor().getBuilding().getBuildingName());
        }
        response.setFloorId(room.getFloor().getId());
        response.setFloorName(room.getFloor().getFloorName());
        response.setFloorNumber(room.getFloor().getFloorNumber());
        response.setRoomNumber(room.getRoomNumber());
        response.setRoomName(room.getRoomName());
        response.setArea(room.getArea());
        response.setRoomType(room.getRoomType());
        response.setRoomTypeText(roomTypeText(room.getRoomType()));
        response.setStatus(room.getStatus());
        response.setStatusText(roomStatusText(room.getStatus()));
        response.setDescription(room.getDescription());
        response.setDeletedAt(room.getDeletedAt());
        response.setCreatedTime(room.getCreatedTime());
        response.setUpdatedTime(room.getUpdatedTime());
        return response;
    }

    private Long resolveRoomBuildingId(Room room) {
        if (room.getBuilding() != null) {
            return room.getBuilding().getId();
        }
        return room.getFloor().getBuilding().getId();
    }

    private boolean notDeleted(Building building) {
        return building.getDeletedAt() == null;
    }

    private boolean notDeleted(Floor floor) {
        return floor.getDeletedAt() == null;
    }

    private boolean notDeleted(Room room) {
        return room.getDeletedAt() == null;
    }

    private String roomTypeText(String type) {
        return switch (type == null ? "" : type) {
            case "OFFICE" -> "办公";
            case "WAREHOUSE" -> "仓储";
            case "EQUIPMENT" -> "设备间";
            case "PARKING" -> "车位";
            case "PUBLIC_AREA" -> "公区";
            default -> type;
        };
    }

    private String roomStatusText(String status) {
        return switch (status == null ? "" : status) {
            case "AVAILABLE" -> "空置";
            case "RENTED" -> "已出租";
            case "RESERVED" -> "预留";
            case "RENOVATING" -> "装修中";
            case "DISABLED" -> "停用";
            default -> status;
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

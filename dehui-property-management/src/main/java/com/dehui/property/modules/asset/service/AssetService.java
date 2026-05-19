package com.dehui.property.modules.asset.service;

import com.dehui.property.common.Result;
import com.dehui.property.modules.asset.dto.AssetMaintenanceRequest;
import com.dehui.property.modules.asset.dto.AssetOperationLogResponse;
import com.dehui.property.modules.asset.dto.AssetRequest;
import com.dehui.property.modules.asset.dto.AssetResponse;
import com.dehui.property.modules.asset.dto.AssetStatusRequest;
import com.dehui.property.modules.asset.dto.AssetTransferRequest;
import com.dehui.property.modules.asset.entity.Asset;
import com.dehui.property.modules.asset.entity.AssetOperationLog;
import com.dehui.property.modules.asset.repository.AssetOperationLogRepository;
import com.dehui.property.modules.asset.repository.AssetRepository;
import com.dehui.property.modules.building.entity.Building;
import com.dehui.property.modules.building.entity.Floor;
import com.dehui.property.modules.building.entity.Room;
import com.dehui.property.modules.building.repository.BuildingRepository;
import com.dehui.property.modules.building.repository.FloorRepository;
import com.dehui.property.modules.building.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssetService {
    private final AssetRepository assetRepository;
    private final AssetOperationLogRepository logRepository;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final RoomRepository roomRepository;

    public Result<List<AssetResponse>> findAll(String keyword, String assetType, String assetCategory, String status,
                                               Long buildingId, Long floorId, Long roomId) {
        List<AssetResponse> assets = assetRepository.findByDeletedAtIsNullOrderByCreatedTimeDesc()
                .stream()
                .filter(asset -> matchesKeyword(asset, keyword))
                .filter(asset -> isBlank(assetType) || assetType.equals(asset.getAssetType()))
                .filter(asset -> isBlank(assetCategory) || assetCategory.equals(asset.getAssetCategory()))
                .filter(asset -> isBlank(status) || status.equals(asset.getStatus()))
                .filter(asset -> buildingId == null || buildingId.equals(asset.getBuilding().getId()))
                .filter(asset -> floorId == null || floorId.equals(asset.getFloor().getId()))
                .filter(asset -> roomId == null || (asset.getRoom() != null && roomId.equals(asset.getRoom().getId())))
                .map(this::toResponse)
                .toList();
        return Result.success(assets);
    }

    public Result<AssetResponse> findById(Long id) {
        return assetRepository.findByIdAndDeletedAtIsNull(id)
                .map(asset -> Result.success(toResponse(asset)))
                .orElseGet(() -> Result.error("资产不存在"));
    }

    @Transactional
    public Result<AssetResponse> create(AssetRequest request) {
        String assetCode = normalize(request.getAssetCode());
        if (assetRepository.existsByAssetCodeAndDeletedAtIsNull(assetCode)) {
            return Result.error("资产编号已存在");
        }

        Result<LocationRefs> refs = resolveLocation(request.getBuildingId(), request.getFloorId(), request.getRoomId());
        if (refs.getCode() != 200) {
            return Result.error(refs.getMessage());
        }

        Asset asset = new Asset();
        asset.setAssetCode(assetCode);
        applyRequest(asset, request, refs.getData());
        asset.setStatus(isBlank(request.getStatus()) ? "IN_USE" : request.getStatus());

        Asset saved = assetRepository.save(asset);
        writeLog(saved, "CREATE", null, saved.getStatus(), null, locationText(saved), null,
                "新增资产", null);
        return Result.success(toResponse(saved));
    }

    @Transactional
    public Result<AssetResponse> update(Long id, AssetRequest request) {
        return assetRepository.findByIdAndDeletedAtIsNull(id)
                .map(asset -> {
                    String assetCode = normalize(request.getAssetCode());
                    assetRepository.findByAssetCodeAndDeletedAtIsNull(assetCode)
                            .filter(existing -> !existing.getId().equals(id))
                            .ifPresent(existing -> {
                                throw new IllegalArgumentException("资产编号已存在");
                            });

                    Result<LocationRefs> refs = resolveLocation(request.getBuildingId(), request.getFloorId(), request.getRoomId());
                    if (refs.getCode() != 200) {
                        return Result.<AssetResponse>error(refs.getMessage());
                    }

                    String oldStatus = asset.getStatus();
                    String oldLocation = locationText(asset);
                    asset.setAssetCode(assetCode);
                    applyRequest(asset, request, refs.getData());
                    if (!isBlank(request.getStatus())) {
                        asset.setStatus(request.getStatus());
                    }
                    Asset saved = assetRepository.save(asset);
                    writeLog(saved, "UPDATE", oldStatus, saved.getStatus(), oldLocation, locationText(saved), null,
                            "修改资产信息", null);
                    return Result.success(toResponse(saved));
                })
                .orElseGet(() -> Result.error("资产不存在"));
    }

    @Transactional
    public Result<Void> delete(Long id) {
        return assetRepository.findByIdAndDeletedAtIsNull(id)
                .map(asset -> {
                    String oldStatus = asset.getStatus();
                    asset.setDeletedAt(LocalDateTime.now());
                    asset.setStatus("DISABLED");
                    Asset saved = assetRepository.save(asset);
                    writeLog(saved, "STATUS_CHANGE", oldStatus, saved.getStatus(), locationText(saved), locationText(saved),
                            null, "软删除资产", null);
                    return Result.<Void>success();
                })
                .orElseGet(() -> Result.error("资产不存在"));
    }

    @Transactional
    public Result<AssetResponse> updateStatus(Long id, AssetStatusRequest request) {
        if (!isValidStatus(request.getStatus())) {
            return Result.error("无效资产状态");
        }
        return assetRepository.findByIdAndDeletedAtIsNull(id)
                .map(asset -> {
                    String oldStatus = asset.getStatus();
                    asset.setStatus(request.getStatus());
                    Asset saved = assetRepository.save(asset);
                    String operationType = "SCRAPPED".equals(saved.getStatus()) ? "SCRAP" : "STATUS_CHANGE";
                    writeLog(saved, operationType, oldStatus, saved.getStatus(), locationText(saved), locationText(saved),
                            request.getOperator(), request.getDescription(), null);
                    return Result.success(toResponse(saved));
                })
                .orElseGet(() -> Result.error("资产不存在"));
    }

    @Transactional
    public Result<AssetResponse> transfer(Long id, AssetTransferRequest request) {
        return assetRepository.findByIdAndDeletedAtIsNull(id)
                .map(asset -> {
                    Result<LocationRefs> refs = resolveLocation(request.getBuildingId(), request.getFloorId(), request.getRoomId());
                    if (refs.getCode() != 200) {
                        return Result.<AssetResponse>error(refs.getMessage());
                    }
                    String oldLocation = locationText(asset);
                    asset.setBuilding(refs.getData().building());
                    asset.setFloor(refs.getData().floor());
                    asset.setRoom(refs.getData().room());
                    asset.setLocationDesc(request.getLocationDesc());
                    Asset saved = assetRepository.save(asset);
                    writeLog(saved, "TRANSFER", saved.getStatus(), saved.getStatus(), oldLocation, locationText(saved),
                            request.getOperator(), request.getDescription(), null);
                    return Result.success(toResponse(saved));
                })
                .orElseGet(() -> Result.error("资产不存在"));
    }

    @Transactional
    public Result<AssetResponse> maintenance(Long id, AssetMaintenanceRequest request) {
        return assetRepository.findByIdAndDeletedAtIsNull(id)
                .map(asset -> {
                    LocalDate maintenanceDate = request.getMaintenanceDate() == null ? LocalDate.now() : request.getMaintenanceDate();
                    asset.setLastMaintenanceDate(maintenanceDate);
                    if (request.getNextMaintenanceDate() != null) {
                        asset.setNextMaintenanceDate(request.getNextMaintenanceDate());
                    } else if (asset.getMaintenanceCycleDays() != null && asset.getMaintenanceCycleDays() > 0) {
                        asset.setNextMaintenanceDate(maintenanceDate.plusDays(asset.getMaintenanceCycleDays()));
                    }
                    Asset saved = assetRepository.save(asset);
                    writeLog(saved, "MAINTENANCE", saved.getStatus(), saved.getStatus(), locationText(saved), locationText(saved),
                            request.getOperator(), request.getDescription(), request.getAttachmentUrl());
                    return Result.success(toResponse(saved));
                })
                .orElseGet(() -> Result.error("资产不存在"));
    }

    public Result<List<AssetOperationLogResponse>> logs(Long assetId) {
        if (!assetRepository.existsById(assetId)) {
            return Result.error("资产不存在");
        }
        return Result.success(logRepository.findByAssetIdOrderByOperationTimeDesc(assetId)
                .stream()
                .map(this::toLogResponse)
                .toList());
    }

    public Map<String, Object> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", assetRepository.countByDeletedAtIsNull());
        data.put("inUse", assetRepository.countByStatusAndDeletedAtIsNull("IN_USE"));
        data.put("idle", assetRepository.countByStatusAndDeletedAtIsNull("IDLE"));
        data.put("maintenance", assetRepository.countByStatusAndDeletedAtIsNull("MAINTENANCE"));
        data.put("disabled", assetRepository.countByStatusAndDeletedAtIsNull("DISABLED"));
        data.put("scrapped", assetRepository.countByStatusAndDeletedAtIsNull("SCRAPPED"));
        data.put("maintenanceDue", assetRepository.findByNextMaintenanceDateLessThanEqualAndDeletedAtIsNull(LocalDate.now()).size());
        data.put("warrantyExpiring", assetRepository.findByWarrantyEndDateBetweenAndDeletedAtIsNull(LocalDate.now(), LocalDate.now().plusDays(30)).size());
        return data;
    }

    public Map<String, Long> countBy(String field) {
        return assetRepository.findByDeletedAtIsNullOrderByCreatedTimeDesc()
                .stream()
                .collect(Collectors.groupingBy(asset -> switch (field) {
                    case "type" -> defaultKey(asset.getAssetType());
                    case "status" -> defaultKey(asset.getStatus());
                    case "floor" -> asset.getFloor().getFloorName() == null
                            ? String.valueOf(asset.getFloor().getFloorNumber())
                            : asset.getFloor().getFloorName();
                    default -> defaultKey(asset.getAssetCategory());
                }, LinkedHashMap::new, Collectors.counting()));
    }

    public List<AssetResponse> warrantyExpiring() {
        return assetRepository.findByWarrantyEndDateBetweenAndDeletedAtIsNull(LocalDate.now(), LocalDate.now().plusDays(30))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<AssetResponse> maintenanceDue() {
        return assetRepository.findByNextMaintenanceDateLessThanEqualAndDeletedAtIsNull(LocalDate.now())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void applyRequest(Asset asset, AssetRequest request, LocationRefs refs) {
        asset.setAssetName(request.getAssetName());
        asset.setAssetCategory(request.getAssetCategory());
        asset.setAssetType(request.getAssetType());
        asset.setManufacturer(request.getManufacturer());
        asset.setModel(request.getModel());
        asset.setSerialNo(request.getSerialNo());
        asset.setBuilding(refs.building());
        asset.setFloor(refs.floor());
        asset.setRoom(refs.room());
        asset.setLocationDesc(request.getLocationDesc());
        asset.setInstallDate(request.getInstallDate());
        asset.setWarrantyStartDate(request.getWarrantyStartDate());
        asset.setWarrantyEndDate(request.getWarrantyEndDate());
        asset.setResponsiblePerson(request.getResponsiblePerson());
        asset.setMaintenanceCycleDays(request.getMaintenanceCycleDays());
        asset.setLastMaintenanceDate(request.getLastMaintenanceDate());
        asset.setNextMaintenanceDate(request.getNextMaintenanceDate());
        asset.setRemark(request.getRemark());
    }

    private Result<LocationRefs> resolveLocation(Long buildingId, Long floorId, Long roomId) {
        Building building = buildingRepository.findById(buildingId).filter(item -> item.getDeletedAt() == null).orElse(null);
        if (building == null) {
            return Result.error("楼宇不存在");
        }
        Floor floor = floorRepository.findById(floorId).filter(item -> item.getDeletedAt() == null).orElse(null);
        if (floor == null || !Objects.equals(floor.getBuilding().getId(), buildingId)) {
            return Result.error("楼层不存在或不属于所选楼宇");
        }
        Room room = null;
        if (roomId != null) {
            room = roomRepository.findById(roomId).filter(item -> item.getDeletedAt() == null).orElse(null);
            Long roomBuildingId = room == null ? null : resolveRoomBuildingId(room);
            if (room == null || !Objects.equals(room.getFloor().getId(), floorId) || !Objects.equals(roomBuildingId, buildingId)) {
                return Result.error("房间不存在或不属于所选楼宇/楼层");
            }
        }
        return Result.success(new LocationRefs(building, floor, room));
    }

    private void writeLog(Asset asset, String operationType, String oldStatus, String newStatus, String oldLocation,
                          String newLocation, String operator, String description, String attachmentUrl) {
        AssetOperationLog log = new AssetOperationLog();
        log.setAsset(asset);
        log.setOperationType(operationType);
        log.setOldStatus(oldStatus);
        log.setNewStatus(newStatus);
        log.setOldLocation(oldLocation);
        log.setNewLocation(newLocation);
        log.setOperator(isBlank(operator) ? "system" : operator);
        log.setOperationTime(LocalDateTime.now());
        log.setDescription(description);
        log.setAttachmentUrl(attachmentUrl);
        logRepository.save(log);
    }

    private AssetResponse toResponse(Asset asset) {
        AssetResponse response = new AssetResponse();
        response.setId(asset.getId());
        response.setAssetCode(asset.getAssetCode());
        response.setAssetName(asset.getAssetName());
        response.setAssetCategory(asset.getAssetCategory());
        response.setAssetType(asset.getAssetType());
        response.setManufacturer(asset.getManufacturer());
        response.setModel(asset.getModel());
        response.setSerialNo(asset.getSerialNo());
        response.setBuildingId(asset.getBuilding().getId());
        response.setBuildingName(asset.getBuilding().getBuildingName());
        response.setFloorId(asset.getFloor().getId());
        response.setFloorName(asset.getFloor().getFloorName());
        response.setFloorNumber(asset.getFloor().getFloorNumber());
        if (asset.getRoom() != null) {
            response.setRoomId(asset.getRoom().getId());
            response.setRoomName(asset.getRoom().getRoomName());
            response.setRoomNumber(asset.getRoom().getRoomNumber());
        }
        response.setLocationDesc(asset.getLocationDesc());
        response.setLocationText(locationText(asset));
        response.setStatus(asset.getStatus());
        response.setStatusText(statusText(asset.getStatus()));
        response.setInstallDate(asset.getInstallDate());
        response.setWarrantyStartDate(asset.getWarrantyStartDate());
        response.setWarrantyEndDate(asset.getWarrantyEndDate());
        response.setResponsiblePerson(asset.getResponsiblePerson());
        response.setMaintenanceCycleDays(asset.getMaintenanceCycleDays());
        response.setLastMaintenanceDate(asset.getLastMaintenanceDate());
        response.setNextMaintenanceDate(asset.getNextMaintenanceDate());
        response.setRemark(asset.getRemark());
        response.setCreatedTime(asset.getCreatedTime());
        response.setUpdatedTime(asset.getUpdatedTime());
        return response;
    }

    private AssetOperationLogResponse toLogResponse(AssetOperationLog log) {
        AssetOperationLogResponse response = new AssetOperationLogResponse();
        response.setId(log.getId());
        response.setAssetId(log.getAsset().getId());
        response.setOperationType(log.getOperationType());
        response.setOperationTypeText(operationTypeText(log.getOperationType()));
        response.setOldStatus(log.getOldStatus());
        response.setNewStatus(log.getNewStatus());
        response.setOldLocation(log.getOldLocation());
        response.setNewLocation(log.getNewLocation());
        response.setOperator(log.getOperator());
        response.setOperationTime(log.getOperationTime());
        response.setDescription(log.getDescription());
        response.setAttachmentUrl(log.getAttachmentUrl());
        response.setCreatedTime(log.getCreatedTime());
        return response;
    }

    private boolean matchesKeyword(Asset asset, String keyword) {
        if (isBlank(keyword)) {
            return true;
        }
        String value = keyword.trim().toLowerCase(Locale.ROOT);
        return contains(asset.getAssetCode(), value)
                || contains(asset.getAssetName(), value)
                || contains(asset.getSerialNo(), value)
                || contains(asset.getLocationDesc(), value);
    }

    private boolean contains(String source, String keyword) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String locationText(Asset asset) {
        StringBuilder builder = new StringBuilder();
        builder.append(asset.getBuilding().getBuildingName());
        builder.append(" / ");
        builder.append(asset.getFloor().getFloorName() == null ? asset.getFloor().getFloorNumber() + "F" : asset.getFloor().getFloorName());
        if (asset.getRoom() != null) {
            builder.append(" / ");
            builder.append(asset.getRoom().getRoomName() == null ? asset.getRoom().getRoomNumber() : asset.getRoom().getRoomName());
        }
        if (!isBlank(asset.getLocationDesc())) {
            builder.append(" / ");
            builder.append(asset.getLocationDesc());
        }
        return builder.toString();
    }

    private Long resolveRoomBuildingId(Room room) {
        if (room.getBuilding() != null) {
            return room.getBuilding().getId();
        }
        return room.getFloor().getBuilding().getId();
    }

    private boolean isValidStatus(String status) {
        return "IN_USE".equals(status)
                || "IDLE".equals(status)
                || "MAINTENANCE".equals(status)
                || "DISABLED".equals(status)
                || "SCRAPPED".equals(status);
    }

    private String statusText(String status) {
        return switch (status == null ? "" : status) {
            case "IN_USE" -> "使用中";
            case "IDLE" -> "闲置";
            case "MAINTENANCE" -> "维修中";
            case "DISABLED" -> "停用";
            case "SCRAPPED" -> "报废";
            default -> status;
        };
    }

    private String operationTypeText(String type) {
        return switch (type == null ? "" : type) {
            case "CREATE" -> "新增资产";
            case "UPDATE" -> "修改信息";
            case "STATUS_CHANGE" -> "状态变更";
            case "TRANSFER" -> "位置转移";
            case "MAINTENANCE" -> "维修/维保";
            case "INSPECTION" -> "巡检";
            case "SCRAP" -> "报废";
            default -> type;
        };
    }

    private String defaultKey(String value) {
        return isBlank(value) ? "未填写" : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record LocationRefs(Building building, Floor floor, Room room) {
    }
}

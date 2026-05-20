package com.dehui.property.modules.parking.service;

import com.dehui.property.modules.parking.dto.ParkingAssignmentResponse;
import com.dehui.property.modules.parking.dto.ParkingBindRequest;
import com.dehui.property.modules.parking.dto.ParkingOperationLogResponse;
import com.dehui.property.modules.parking.dto.ParkingReleaseRequest;
import com.dehui.property.modules.parking.dto.ParkingSpaceRequest;
import com.dehui.property.modules.parking.dto.ParkingSpaceResponse;
import com.dehui.property.modules.parking.entity.ParkingAssignment;
import com.dehui.property.modules.parking.entity.ParkingSpace;
import com.dehui.property.modules.parking.repository.ParkingAssignmentRepository;
import com.dehui.property.modules.parking.repository.ParkingBillRepository;
import com.dehui.property.modules.parking.repository.ParkingSpaceRepository;
import com.dehui.property.modules.tenant.entity.Tenant;
import com.dehui.property.modules.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ParkingSpaceService {
    private static final String STATUS_AVAILABLE = "AVAILABLE";
    private static final String STATUS_OCCUPIED = "OCCUPIED";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String STATUS_ACTIVE = "active";
    private static final BigDecimal DEFAULT_LEGACY_MONTHLY_FEE = new BigDecimal("300.00");

    private final ParkingSpaceRepository parkingSpaceRepository;
    private final ParkingAssignmentRepository assignmentRepository;
    private final ParkingBillRepository parkingBillRepository;
    private final TenantRepository tenantRepository;
    private final ParkingOperationLogService logService;

    public List<ParkingSpaceResponse> list(String keyword, String status, String area) {
        return parkingSpaceRepository.findByDeletedAtIsNullOrderBySortOrderAscIdAsc()
                .stream()
                .filter(space -> isBlank(status) || normalizeStatus(status).equals(space.getStatus()))
                .filter(space -> isBlank(area) || area.equals(space.getArea()))
                .map(this::toResponse)
                .filter(response -> matchesKeyword(response, keyword))
                .toList();
    }

    public ParkingSpaceResponse getResponse(Long id) {
        return toResponse(getActiveSpace(id));
    }

    @Transactional
    public ParkingSpaceResponse create(ParkingSpaceRequest request) {
        String spaceCode = normalizedSpaceCode(request);
        if (isBlank(spaceCode)) {
            throw new RuntimeException("车位编号不能为空");
        }
        if (parkingSpaceRepository.existsBySpaceCodeAndDeletedAtIsNull(spaceCode)) {
            throw new RuntimeException("车位编号已存在");
        }

        ParkingSpace space = new ParkingSpace();
        space.setSpaceCode(spaceCode);
        applySpaceFields(space, request);
        space.setStatus(STATUS_AVAILABLE);
        ParkingSpace saved = parkingSpaceRepository.save(space);
        logService.write("space", saved.getId(), "CREATE_SPACE", null, toResponse(saved), null);
        return toResponse(saved);
    }

    @Transactional
    public ParkingSpaceResponse update(Long id, ParkingSpaceRequest request) {
        ParkingSpace space = getActiveSpace(id);
        ParkingSpaceResponse before = toResponse(space);
        String nextCode = normalizedSpaceCode(request);
        if (isBlank(nextCode)) {
            throw new RuntimeException("车位编号不能为空");
        }
        parkingSpaceRepository.findBySpaceCodeAndDeletedAtIsNull(nextCode)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new RuntimeException("车位编号已存在");
                });

        space.setSpaceCode(nextCode);
        applySpaceFields(space, request);
        ParkingSpace saved = parkingSpaceRepository.save(space);
        ParkingSpaceResponse after = toResponse(saved);
        logService.write("space", saved.getId(), "UPDATE_SPACE", before, after, null);
        return after;
    }

    @Transactional
    public ParkingSpaceResponse bind(Long id, ParkingBindRequest request) {
        ParkingSpace space = getActiveSpace(id);
        if (!STATUS_AVAILABLE.equals(space.getStatus())) {
            throw new RuntimeException("只有空闲车位可以绑定");
        }
        if (assignmentRepository.findFirstBySpaceIdAndStatus(space.getId(), STATUS_ACTIVE).isPresent()) {
            throw new RuntimeException("该车位已存在有效绑定关系");
        }

        ParkingSpaceResponse before = toResponse(space);
        ParkingAssignment assignment = buildAssignment(space, request);
        ParkingAssignment savedAssignment = assignmentRepository.save(assignment);

        space.setStatus(STATUS_OCCUPIED);
        space.setTenantId("tenant".equals(savedAssignment.getPartyType()) ? savedAssignment.getPartyId() : null);
        space.setPlateNumber(savedAssignment.getPlateNo());
        ParkingSpace savedSpace = parkingSpaceRepository.save(space);

        ParkingSpaceResponse after = toResponse(savedSpace);
        logService.write("space", savedSpace.getId(), "BIND", before, after, request.getOperatorName());
        logService.write("assignment", savedAssignment.getId(), "BIND", null, toAssignmentResponse(savedAssignment), request.getOperatorName());
        return after;
    }

    @Transactional
    public ParkingSpaceResponse release(Long id, ParkingReleaseRequest request) {
        ParkingSpace space = getActiveSpace(id);
        ParkingAssignment assignment = assignmentRepository.findFirstBySpaceIdAndStatus(space.getId(), STATUS_ACTIVE)
                .orElseThrow(() -> new RuntimeException("该车位不存在有效绑定关系"));

        ParkingSpaceResponse beforeSpace = toResponse(space);
        ParkingAssignmentResponse beforeAssignment = toAssignmentResponse(assignment);

        assignment.setStatus("ended");
        assignment.setEndDate(request == null || request.getEndDate() == null ? LocalDate.now() : request.getEndDate());
        assignment.setReleasedBy(request == null || isBlank(request.getOperatorName()) ? "system" : request.getOperatorName());
        assignmentRepository.save(assignment);

        space.setStatus(STATUS_AVAILABLE);
        space.setTenantId(null);
        space.setPlateNumber(null);
        ParkingSpace savedSpace = parkingSpaceRepository.save(space);

        String operator = request == null ? null : request.getOperatorName();
        logService.write("space", savedSpace.getId(), "RELEASE", beforeSpace, toResponse(savedSpace), operator);
        logService.write("assignment", assignment.getId(), "RELEASE", beforeAssignment, toAssignmentResponse(assignment), operator);
        return toResponse(savedSpace);
    }

    @Transactional
    public ParkingSpaceResponse updateStatus(Long id, String status) {
        ParkingSpace space = getActiveSpace(id);
        String normalized = normalizeStatus(status);
        if (STATUS_OCCUPIED.equals(normalized)) {
            throw new RuntimeException("占用状态只能通过绑定接口生成");
        }
        if (STATUS_AVAILABLE.equals(normalized) && assignmentRepository.findFirstBySpaceIdAndStatus(space.getId(), STATUS_ACTIVE).isPresent()) {
            throw new RuntimeException("存在有效绑定关系，不能直接启用为空闲");
        }
        ParkingSpaceResponse before = toResponse(space);
        space.setStatus(normalized);
        ParkingSpace saved = parkingSpaceRepository.save(space);
        logService.write("space", saved.getId(), "UPDATE_SPACE", before, toResponse(saved), null);
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        ParkingSpace space = getActiveSpace(id);
        if (STATUS_OCCUPIED.equals(space.getStatus()) || assignmentRepository.findFirstBySpaceIdAndStatus(space.getId(), STATUS_ACTIVE).isPresent()) {
            throw new RuntimeException("占用中的车位不能删除");
        }

        boolean hasHistory = assignmentRepository.countBySpaceId(id) > 0
                || parkingBillRepository.countByParkingSpaceId(id) > 0
                || parkingBillRepository.countBySpaceId(id) > 0;
        ParkingSpaceResponse before = toResponse(space);
        if (hasHistory) {
            space.setDeletedAt(LocalDateTime.now());
            space.setStatus(STATUS_DISABLED);
            ParkingSpace saved = parkingSpaceRepository.save(space);
            logService.write("space", id, "DELETE_SPACE", before, toResponse(saved), null);
        } else {
            parkingSpaceRepository.delete(space);
            logService.write("space", id, "DELETE_SPACE", before, Map.of("deleted", true), null);
        }
    }

    public List<ParkingAssignmentResponse> listAssignments(Long spaceId, String status, String partyType) {
        return assignmentRepository.findAll()
                .stream()
                .filter(item -> spaceId == null || spaceId.equals(item.getSpaceId()))
                .filter(item -> isBlank(status) || normalizeLower(status).equals(item.getStatus()))
                .filter(item -> isBlank(partyType) || normalizeLower(partyType).equals(item.getPartyType()))
                .sorted(Comparator.comparing(
                        ParkingAssignment::getCreatedTime,
                        Comparator.nullsFirst(Comparator.naturalOrder())
                ).reversed())
                .map(this::toAssignmentResponse)
                .toList();
    }

    public List<ParkingOperationLogResponse> history(Long id) {
        getActiveOrDeletedSpace(id);
        List<ParkingOperationLogResponse> logs = new ArrayList<>(logService.list("space", id));
        logs.addAll(assignmentRepository.findBySpaceIdOrderByCreatedTimeDesc(id)
                .stream()
                .flatMap(assignment -> logService.list("assignment", assignment.getId()).stream())
                .toList());
        return logs.stream()
                .sorted(Comparator.comparing(
                        ParkingOperationLogResponse::getCreatedTime,
                        Comparator.nullsFirst(Comparator.naturalOrder())
                ).reversed())
                .toList();
    }

    public Long countAvailable() {
        return parkingSpaceRepository.countByStatusAndDeletedAtIsNull(STATUS_AVAILABLE);
    }

    public Long countOccupied() {
        return parkingSpaceRepository.countByStatusAndDeletedAtIsNull(STATUS_OCCUPIED);
    }

    public Map<String, Object> stats() {
        List<ParkingSpace> spaces = parkingSpaceRepository.findByDeletedAtIsNullOrderBySortOrderAscIdAsc();
        long total = spaces.size();
        long available = spaces.stream().filter(item -> STATUS_AVAILABLE.equals(item.getStatus())).count();
        long occupied = spaces.stream().filter(item -> STATUS_OCCUPIED.equals(item.getStatus())).count();
        long disabled = spaces.stream().filter(item -> STATUS_DISABLED.equals(item.getStatus())).count();
        double occupancyRate = total == 0 ? 0 : occupied * 100.0 / total;
        return Map.of(
                "total", total,
                "available", available,
                "occupied", occupied,
                "disabled", disabled,
                "occupancyRate", occupancyRate
        );
    }

    public ParkingSpace getActiveSpace(Long id) {
        return parkingSpaceRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("车位不存在"));
    }

    private ParkingSpace getActiveOrDeletedSpace(Long id) {
        return parkingSpaceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("车位不存在"));
    }

    private void applySpaceFields(ParkingSpace space, ParkingSpaceRequest request) {
        space.setArea(normalize(request.getArea()));
        space.setFloor(normalize(request.getFloor()));
        space.setSpaceType(normalizeSpaceType(firstNonBlank(request.getType(), request.getSpaceType())));
        space.setRemark(normalize(request.getRemark()));
        space.setSortOrder(request.getSortOrder());
        if (isBlank(space.getArea())) {
            throw new RuntimeException("车位区域不能为空");
        }
    }

    private ParkingAssignment buildAssignment(ParkingSpace space, ParkingBindRequest request) {
        if (request == null) {
            throw new RuntimeException("绑定信息不能为空");
        }
        String partyType = normalizeLower(request.getPartyType());
        if (isBlank(partyType)) {
            partyType = request.getTenantId() != null ? "tenant" : "external";
        }
        if (!List.of("tenant", "vip", "external", "internal", "other").contains(partyType)) {
            throw new RuntimeException("使用方类型无效");
        }

        Long partyId = "tenant".equals(partyType) ? firstNonNull(request.getPartyId(), request.getTenantId()) : request.getPartyId();
        String partyName = normalize(firstNonBlank(request.getPartyNameSnapshot(), request.getPartyName()));
        if ("tenant".equals(partyType)) {
            if (partyId == null) {
                throw new RuntimeException("请选择租户");
            }
            Tenant tenant = tenantRepository.findById(partyId).orElseThrow(() -> new RuntimeException("租户不存在"));
            partyName = tenant.getTenantName();
        }
        if (isBlank(partyName)) {
            throw new RuntimeException("使用方名称不能为空");
        }

        String plateNo = normalize(firstNonBlank(request.getPlateNo(), request.getPlateNumber()));
        if (isBlank(plateNo)) {
            throw new RuntimeException("车牌号不能为空");
        }

        String billingType = normalizeLower(request.getBillingType());
        if (isBlank(billingType)) {
            billingType = "monthly";
        }
        if (!List.of("monthly", "free", "temporary").contains(billingType)) {
            throw new RuntimeException("收费类型无效");
        }
        BigDecimal monthlyFee = request.getMonthlyFee() == null ? BigDecimal.ZERO : request.getMonthlyFee();
        if ("monthly".equals(billingType) && monthlyFee.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("月租金额必须大于0");
        }

        ParkingAssignment assignment = new ParkingAssignment();
        assignment.setSpaceId(space.getId());
        assignment.setPartyType(partyType);
        assignment.setPartyId(partyId);
        assignment.setPartyNameSnapshot(partyName);
        assignment.setPlateNo(plateNo);
        assignment.setStartDate(request.getStartDate() == null ? LocalDate.now() : request.getStartDate());
        assignment.setMonthlyFee(monthlyFee);
        assignment.setBillingType(billingType);
        assignment.setStatus(STATUS_ACTIVE);
        assignment.setCreatedBy(isBlank(request.getOperatorName()) ? "system" : request.getOperatorName());
        assignment.setRemark(request.getRemark());
        return assignment;
    }

    public ParkingSpaceResponse toResponse(ParkingSpace space) {
        ParkingSpaceResponse response = new ParkingSpaceResponse();
        response.setId(space.getId());
        response.setSpaceNo(space.getSpaceCode());
        response.setSpaceCode(space.getSpaceCode());
        response.setArea(space.getArea());
        response.setFloor(space.getFloor());
        response.setType(space.getSpaceType());
        response.setSpaceType(space.getSpaceType());
        response.setTypeText(spaceTypeText(space.getSpaceType()));
        response.setStatus(space.getStatus());
        response.setStatusText(statusText(space.getStatus()));
        response.setRemark(space.getRemark());
        response.setSortOrder(space.getSortOrder());
        response.setTenantId(space.getTenantId());
        response.setPlateNumber(space.getPlateNumber());
        response.setDeletedAt(space.getDeletedAt());
        response.setCreatedTime(space.getCreatedTime());
        response.setUpdatedTime(space.getUpdatedTime());
        assignmentRepository.findFirstBySpaceIdAndStatus(space.getId(), STATUS_ACTIVE)
                .ifPresent(assignment -> {
                    ParkingAssignmentResponse active = toAssignmentResponse(assignment);
                    response.setActiveAssignment(active);
                    response.setPartyType(active.getPartyType());
                    response.setPartyTypeText(active.getPartyTypeText());
                    response.setPartyId(active.getPartyId());
                    response.setPartyNameSnapshot(active.getPartyNameSnapshot());
                    response.setPlateNo(active.getPlateNo());
                    response.setPlateNumber(active.getPlateNo());
                    response.setMonthlyFee(active.getMonthlyFee());
                    response.setBillingType(active.getBillingType());
                    response.setTenantId(active.getTenantId());
                });
        return response;
    }

    public ParkingAssignmentResponse toAssignmentResponse(ParkingAssignment assignment) {
        ParkingAssignmentResponse response = new ParkingAssignmentResponse();
        response.setId(assignment.getId());
        response.setSpaceId(assignment.getSpaceId());
        parkingSpaceRepository.findById(assignment.getSpaceId()).ifPresent(space -> response.setSpaceNo(space.getSpaceCode()));
        response.setPartyType(assignment.getPartyType());
        response.setPartyTypeText(partyTypeText(assignment.getPartyType()));
        response.setPartyId(assignment.getPartyId());
        response.setTenantId("tenant".equals(assignment.getPartyType()) ? assignment.getPartyId() : null);
        response.setPartyNameSnapshot(assignment.getPartyNameSnapshot());
        response.setPlateNo(assignment.getPlateNo());
        response.setStartDate(assignment.getStartDate());
        response.setEndDate(assignment.getEndDate());
        response.setMonthlyFee(assignment.getMonthlyFee());
        response.setBillingType(assignment.getBillingType());
        response.setBillingTypeText(billingTypeText(assignment.getBillingType()));
        response.setStatus(assignment.getStatus());
        response.setStatusText(assignmentStatusText(assignment.getStatus()));
        response.setCreatedBy(assignment.getCreatedBy());
        response.setReleasedBy(assignment.getReleasedBy());
        response.setRemark(assignment.getRemark());
        response.setCreatedTime(assignment.getCreatedTime());
        response.setUpdatedTime(assignment.getUpdatedTime());
        return response;
    }

    private boolean matchesKeyword(ParkingSpaceResponse response, String keyword) {
        if (isBlank(keyword)) {
            return true;
        }
        String value = keyword.trim().toLowerCase(Locale.ROOT);
        return contains(response.getSpaceNo(), value)
                || contains(response.getPartyNameSnapshot(), value)
                || contains(response.getPlateNo(), value)
                || contains(response.getArea(), value)
                || contains(response.getFloor(), value);
    }

    private boolean contains(String source, String keyword) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String normalizedSpaceCode(ParkingSpaceRequest request) {
        return normalize(firstNonBlank(request.getSpaceNo(), request.getSpaceCode()));
    }

    private String normalizeSpaceType(String value) {
        String type = normalizeUpper(value);
        if (isBlank(type) || "FIXED".equals(type)) {
            return "NORMAL";
        }
        if ("TEMP".equals(type)) {
            return "TEMPORARY";
        }
        if ("VIP".equals(type)) {
            return "NORMAL";
        }
        if (!List.of("NORMAL", "CHARGING_FAST", "CHARGING_SLOW", "MECHANICAL", "TEMPORARY").contains(type)) {
            throw new RuntimeException("车位类型无效");
        }
        return type;
    }

    private String normalizeStatus(String value) {
        String status = normalizeUpper(value);
        if (isBlank(status)) {
            return STATUS_AVAILABLE;
        }
        if (!List.of(STATUS_AVAILABLE, STATUS_OCCUPIED, "MAINTENANCE", STATUS_DISABLED).contains(status)) {
            throw new RuntimeException("车位状态无效");
        }
        return status;
    }

    private String spaceTypeText(String type) {
        return switch (type == null ? "" : type) {
            case "NORMAL" -> "普通车位";
            case "CHARGING_FAST" -> "充电车位（快充）";
            case "CHARGING_SLOW" -> "充电车位（慢充）";
            case "MECHANICAL" -> "机械车位";
            case "TEMPORARY" -> "临停车位";
            default -> isBlank(type) ? "-" : type;
        };
    }

    private String statusText(String status) {
        return switch (status == null ? "" : status) {
            case "AVAILABLE" -> "空闲";
            case "OCCUPIED" -> "占用";
            case "MAINTENANCE" -> "维护中";
            case "DISABLED" -> "停用";
            default -> isBlank(status) ? "-" : status;
        };
    }

    private String partyTypeText(String type) {
        return switch (type == null ? "" : type) {
            case "tenant" -> "租户";
            case "vip" -> "VIP";
            case "external" -> "外部客户";
            case "internal" -> "内部员工";
            case "other" -> "其他";
            default -> isBlank(type) ? "-" : type;
        };
    }

    private String billingTypeText(String type) {
        return switch (type == null ? "" : type) {
            case "monthly" -> "月租";
            case "free" -> "免费";
            case "temporary" -> "临停";
            default -> isBlank(type) ? "-" : type;
        };
    }

    private String assignmentStatusText(String status) {
        return switch (status == null ? "" : status) {
            case "active" -> "使用中";
            case "ended" -> "已结束";
            default -> isBlank(status) ? "-" : status;
        };
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeUpper(String value) {
        return normalize(value) == null ? null : normalize(value).toUpperCase(Locale.ROOT);
    }

    private String normalizeLower(String value) {
        return normalize(value) == null ? null : normalize(value).toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String first, String second) {
        return !isBlank(first) ? first : second;
    }

    private Long firstNonNull(Long first, Long second) {
        return first != null ? first : second;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

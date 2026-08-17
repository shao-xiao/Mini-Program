package com.dehui.property.modules.contract.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.ContactValidators;
import com.dehui.property.common.JdbcPagination;
import com.dehui.property.common.JdbcMaps;
import com.dehui.property.common.PageResponse;
import com.dehui.property.modules.customfield.application.CustomFieldProjectionService;
import com.dehui.property.modules.contract.application.ContractApplicationService;
import com.dehui.property.modules.contract.dto.ContractCreateRequest;
import com.dehui.property.modules.contract.dto.ContractHistoryResponse;
import com.dehui.property.modules.contract.dto.ContractResponse;
import com.dehui.property.modules.contract.dto.ContractSummaryResponse;
import com.dehui.property.modules.contract.dto.ContractTransitionRequest;
import com.dehui.property.modules.contract.dto.ContractUpdateRequest;
import com.dehui.property.modules.lease.application.LeaseApplicationService;
import com.dehui.property.modules.lease.dto.LeaseActionRequest;
import com.dehui.property.modules.lease.dto.LeaseResponse;
import com.dehui.property.modules.mobile.controller.MobileApiSupport;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ContractController {

    private static final String ELECTRONIC_CONTRACT = "ELECTRONIC_CONTRACT";
    private static final String SUPPLEMENTARY_AGREEMENT = "SUPPLEMENTARY_AGREEMENT";

    private final JdbcTemplate jdbcTemplate;
    private final ContractApplicationService contractService;
    private final LeaseApplicationService leaseService;
    private final CustomFieldProjectionService customFieldProjectionService;
    private String uploadDir = "uploads";

    public ContractController(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null, null, null);
    }

    public ContractController(
            JdbcTemplate jdbcTemplate,
            ContractApplicationService contractService,
            LeaseApplicationService leaseService) {
        this(jdbcTemplate, contractService, leaseService, null);
    }

    @Autowired
    public ContractController(
            JdbcTemplate jdbcTemplate,
            ContractApplicationService contractService,
            LeaseApplicationService leaseService,
            CustomFieldProjectionService customFieldProjectionService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.contractService = contractService;
        this.leaseService = leaseService;
        this.customFieldProjectionService = customFieldProjectionService;
    }

    @Value("${dehui.file.upload-dir:uploads}")
    public void setUploadDir(String uploadDir) {
        if (uploadDir != null && !uploadDir.isBlank()) {
            this.uploadDir = uploadDir;
        }
    }

    @GetMapping("/contracts")
    public ApiResponse<List<Map<String, Object>>> contracts() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT c.id,
                       c.code,
                       c.code AS contractNumber,
                       c.remark AS contractName,
                       c.tenant_id AS tenantId,
                       t.name AS tenantName,
                       t.contact_person AS contactPerson,
                       t.contact_phone AS contactPhone,
                       t.contact_email AS contactEmail,
                       c.room_id AS roomId,
                       r.room_no AS roomName,
                       c.start_date AS startDate,
                       c.end_date AS endDate,
                       c.rent_amount AS rentAmount,
                       c.deposit_amount AS depositAmount,
                       c.effective_date AS effectiveDate,
                       c.signed_at AS signedAt,
                       c.currency,
                       c.current_revision_no AS currentRevisionNo,
                       c.renewal_of_contract_id AS renewalOfContractId,
                       c.version,
                       r.rent_status AS roomRentStatus,
                       c.status,
                       c.remark,
                       f.original_name AS contractFileName,
                       f.mime_type AS contractMimeType,
                       CASE WHEN f.id IS NULL THEN 0 ELSE 1 END AS hasContractFile
                FROM contract c
                LEFT JOIN tenant t ON t.id = c.tenant_id
                LEFT JOIN building_room r ON r.id = c.room_id
                LEFT JOIN contract_attachment ca ON ca.contract_id = c.id AND ca.attachment_type = 'ELECTRONIC_CONTRACT' AND ca.deleted = 0
                LEFT JOIN file_upload f ON f.id = ca.file_id AND f.deleted = 0
                WHERE c.deleted = 0
                ORDER BY c.updated_at DESC, c.id DESC
                """
        ).stream().<Map<String, Object>>map(LinkedHashMap::new).toList();
        decorateContractRooms(rows);
        return ApiResponse.success(rows.stream().map(row -> {
            Map<String, Object> copy = new LinkedHashMap<>(row);
            prepareContractFileDownloadFields(copy, false);
            return copy;
        }).toList());
    }

    @GetMapping("/contracts/pending-checkin")
    public ApiResponse<List<Map<String, Object>>> pendingCheckinContracts() {
        Set<Long> eligibleContractIds = new LinkedHashSet<>(jdbcTemplate.queryForList(
                """
                SELECT c.id
                FROM contract c
                JOIN tenant t ON t.id = c.tenant_id
                  AND t.deleted = 0
                  AND t.status IN ('ACTIVE', 'ENABLED')
                WHERE c.deleted = 0 AND c.status = 'ACTIVE'
                """,
                Long.class
        ));
        return ApiResponse.success(contracts().data().stream()
                .filter(contract -> "ACTIVE".equals(String.valueOf(contract.get("status"))))
                .filter(contract -> contract.get("id") instanceof Number id
                        && eligibleContractIds.contains(id.longValue()))
                .toList());
    }

    @GetMapping("/contracts/search")
    public ApiResponse<PageResponse<ContractResponse>> searchContracts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDateTo,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDirection,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.success(contractService.search(
                keyword, status, tenantId, endDateFrom, endDateTo,
                sortField, sortDirection, page, pageSize
        ));
    }

    @GetMapping("/contracts/summary")
    public ApiResponse<ContractSummaryResponse> contractSummary(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long tenantId
    ) {
        return ApiResponse.success(contractService.summary(keyword, status, tenantId));
    }

    @GetMapping("/contracts/{id}/history")
    public ApiResponse<List<ContractHistoryResponse>> contractHistory(@PathVariable Long id) {
        return ApiResponse.success(contractService.history(id));
    }

    @PostMapping("/contracts")
    public ApiResponse<ContractResponse> createContractV2(
            @Valid @RequestBody ContractCreateRequest request,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(contractService.create(request, principal.userId()));
    }

    @PutMapping("/contracts/{id}")
    public ApiResponse<ContractResponse> updateContractV2(
            @PathVariable Long id,
            @Valid @RequestBody ContractUpdateRequest request,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(contractService.update(id, request, principal.userId()));
    }

    @PostMapping("/contracts/{id}/submit")
    public ApiResponse<ContractResponse> submitContract(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ContractTransitionRequest request,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(contractService.submit(
                id, request == null ? ContractTransitionRequest.empty() : request, principal.userId()
        ));
    }

    @PostMapping("/contracts/{id}/approve")
    public ApiResponse<ContractResponse> approveContract(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ContractTransitionRequest request,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(contractService.approve(
                id, request == null ? ContractTransitionRequest.empty() : request, principal.userId()
        ));
    }

    @PostMapping("/contracts/{id}/activate")
    public ApiResponse<ContractResponse> activateContractV2(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ContractTransitionRequest request,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(contractService.activate(
                id, request == null ? ContractTransitionRequest.empty() : request, principal.userId()
        ));
    }

    @PostMapping("/contracts/{id}/terminate")
    public ApiResponse<ContractResponse> terminateContractV2(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ContractTransitionRequest request,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(contractService.terminate(
                id, request == null ? ContractTransitionRequest.empty() : request, principal.userId()
        ));
    }

    @PostMapping("/contracts/{id}/cancel")
    public ApiResponse<ContractResponse> cancelContractV2(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ContractTransitionRequest request,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(contractService.cancel(
                id, request == null ? ContractTransitionRequest.empty() : request, principal.userId()
        ));
    }

    @PostMapping("/contracts/{id}/check-in")
    public ApiResponse<LeaseResponse> legacyContractCheckIn(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) LeaseActionRequest request,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(leaseService.checkInByContract(id, request, principal.userId()));
    }

    @PostMapping("/contracts/{id}/reactivate")
    public ApiResponse<ContractResponse> legacyReactivateContract(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ContractTransitionRequest request,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) {
        return ApiResponse.success(contractService.activate(
                id, request == null ? ContractTransitionRequest.empty() : request, principal.userId()
        ));
    }

    public ApiResponse<Void> createContract(@RequestBody Map<String, Object> body) {
        ContactInfo contactInfo = validateContactInfo(body);
        Long tenantId = resolveTenantId(body, contactInfo);
        List<Long> roomIds = requiredRoomIds(body);
        Long roomId = roomIds.getFirst();
        String contractCode = JdbcMaps.strOr(body, JdbcMaps.code("CON"), "contractNumber", "code");
        jdbcTemplate.update(
                """
                INSERT INTO contract (code, tenant_id, room_id, start_date, end_date, rent_amount, deposit_amount, status, created_by, updated_by, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                contractCode,
                tenantId,
                roomId,
                JdbcMaps.date(body, JdbcMaps.today(), "startDate", "start_date"),
                JdbcMaps.date(body, JdbcMaps.today(), "endDate", "end_date"),
                JdbcMaps.decimal(body, BigDecimal.ZERO, "rentAmount", "rent"),
                JdbcMaps.decimal(body, BigDecimal.ZERO, "depositAmount", "deposit"),
                JdbcMaps.strOr(body, "DRAFT", "status"),
                0L,
                0L,
                JdbcMaps.str(body, "remark", "contractName")
        );
        syncContractRooms(findContractIdByCode(contractCode), roomIds);
        return ApiResponse.success();
    }

    public ApiResponse<Void> updateContract(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ContactInfo contactInfo = validateContactInfo(body);
        Long tenantId = resolveTenantId(body, contactInfo);
        List<Long> roomIds = requiredRoomIds(body);
        Long roomId = roomIds.getFirst();
        int updated = jdbcTemplate.update(
                """
                UPDATE contract
                SET code = ?, tenant_id = ?, room_id = ?, start_date = ?, end_date = ?, rent_amount = ?, deposit_amount = ?, status = ?, updated_by = ?, remark = ?
                WHERE id = ? AND deleted = 0
                """,
                JdbcMaps.strOr(body, JdbcMaps.code("CON"), "contractNumber", "code"),
                tenantId,
                roomId,
                JdbcMaps.date(body, JdbcMaps.today(), "startDate", "start_date"),
                JdbcMaps.date(body, JdbcMaps.today(), "endDate", "end_date"),
                JdbcMaps.decimal(body, BigDecimal.ZERO, "rentAmount", "rent"),
                JdbcMaps.decimal(body, BigDecimal.ZERO, "depositAmount", "deposit"),
                JdbcMaps.strOr(body, "DRAFT", "status"),
                0L,
                JdbcMaps.str(body, "remark", "contractName"),
                id
        );
        if (updated == 0) {
            throw new BusinessException(404, "Contract not found");
        }
        syncContractRooms(id, roomIds);
        return ApiResponse.success();
    }

    public ApiResponse<Void> activate(@PathVariable Long id) {
        updateStatus(id, "ACTIVE");
        return ApiResponse.success();
    }

    public ApiResponse<Void> checkIn(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        updateRoomRentStatus(id, "RENTED");
        updateStatus(id, "ACTIVE");
        return ApiResponse.success();
    }

    public ApiResponse<Void> terminate(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        updateRoomRentStatus(id, "VACANT");
        updateStatus(id, "TERMINATED");
        return ApiResponse.success();
    }

    public ApiResponse<Void> cancel(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        updateRoomRentStatus(id, "VACANT");
        updateStatus(id, "CANCELLED");
        return ApiResponse.success();
    }

    public ApiResponse<Void> reactivate(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        updateRoomRentStatus(id, "RENTED");
        updateStatus(id, "ACTIVE");
        return ApiResponse.success();
    }

    @Deprecated(forRemoval = false)
    @PostMapping("/contracts/generate-bills")
    public ApiResponse<Map<String, Object>> generateBills() {
        throw new BusinessException(410, "合同自动出账入口已停用，请在财务管理的账单页面新增账单");
    }

    @Transactional
    @PostMapping("/contracts/{id}/file")
    public ApiResponse<Void> uploadElectronicContract(
            @PathVariable Long id,
            @RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        ensureContractExists(id);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请上传电子合同 PDF 文件");
        }
        saveElectronicContractFile(id, file);
        auditContractAttachment(id, ELECTRONIC_CONTRACT, 0L);
        return ApiResponse.success();
    }

    @GetMapping("/contracts/{id}/file/download")
    public ResponseEntity<Resource> downloadElectronicContract(@PathVariable Long id) {
        return contractFileDownloadResponse(id, null);
    }

    @GetMapping("/mobile/contracts")
    public ApiResponse<Map<String, Object>> mobileContracts(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        Long tenantId = MobileApiSupport.requireTenantId(principal);
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        String statusFilter = "";
        if (status != null && !status.isBlank()) {
            statusFilter = " AND c.status = ?";
            args.add(status.trim());
        }
        PageResponse<Map<String, Object>> contractPage = JdbcPagination.query(
                jdbcTemplate,
                """
                SELECT c.id,
                       c.code,
                       c.code AS contractNumber,
                       c.remark AS contractName,
                       t.name AS tenantName,
                       r.room_no AS roomName,
                       c.start_date AS startDate,
                       c.end_date AS endDate,
                       c.rent_amount AS rentAmount,
                       c.deposit_amount AS depositAmount,
                       c.status,
                       c.remark,
                       f.original_name AS contractFileName,
                       f.mime_type AS contractMimeType,
                       CASE WHEN f.id IS NULL THEN 0 ELSE 1 END AS hasContractFile
                FROM contract c
                LEFT JOIN tenant t ON t.id = c.tenant_id
                LEFT JOIN building_room r ON r.id = c.room_id
                LEFT JOIN contract_attachment ca ON ca.contract_id = c.id AND ca.attachment_type = 'ELECTRONIC_CONTRACT' AND ca.deleted = 0
                LEFT JOIN file_upload f ON f.id = ca.file_id AND f.deleted = 0
                WHERE c.deleted = 0 AND c.tenant_id = ?
                """ + statusFilter + "\n" + """
                ORDER BY c.start_date DESC, c.updated_at DESC, c.id DESC
                """,
                "SELECT COUNT(*) FROM contract c WHERE c.deleted = 0 AND c.tenant_id = ?" + statusFilter,
                args,
                page,
                pageSize
        );
        List<Map<String, Object>> contracts = contractPage.records().stream()
                .<Map<String, Object>>map(LinkedHashMap::new)
                .toList();
        decorateContractRooms(contracts);
        decorateContractAttachments(contracts, true);
        List<Map<String, Object>> decoratedContracts = contracts.stream().map(row -> {
            Map<String, Object> copy = new LinkedHashMap<>(row);
            prepareContractFileDownloadFields(copy, true);
            return copy;
        }).toList();
        if (customFieldProjectionService != null) {
            decoratedContracts = customFieldProjectionService.attach("contracts", decoratedContracts);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("profile", MobileApiSupport.profile(jdbcTemplate, principal));
        response.put("records", decoratedContracts);
        response.put("contracts", decoratedContracts);
        response.put("page", contractPage.page());
        response.put("pageSize", contractPage.pageSize());
        response.put("total", contractPage.total());
        return ApiResponse.success(response);
    }

    public ApiResponse<Map<String, Object>> mobileContracts(AuthPrincipal principal) {
        return mobileContracts(principal, null, null, null);
    }

    @GetMapping("/mobile/contracts/{id}")
    public ApiResponse<Map<String, Object>> mobileContractDetail(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id) {
        Long tenantId = MobileApiSupport.requireTenantId(principal);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT c.id,
                       c.code AS contractNumber,
                       c.remark AS contractName,
                       t.name AS tenantName,
                       c.start_date AS startDate,
                       c.end_date AS endDate,
                       c.status,
                       c.rent_amount AS rentAmount,
                       c.deposit_amount AS depositAmount,
                       c.currency,
                       f.original_name AS contractFileName,
                       f.mime_type AS contractMimeType,
                       CASE WHEN f.id IS NULL THEN 0 ELSE 1 END AS hasContractFile
                FROM contract c
                INNER JOIN tenant t
                  ON t.id = c.tenant_id
                 AND t.deleted = 0
                LEFT JOIN contract_attachment attachment_record
                  ON attachment_record.contract_id = c.id
                 AND attachment_record.attachment_type = 'ELECTRONIC_CONTRACT'
                 AND attachment_record.deleted = 0
                LEFT JOIN file_upload f
                  ON f.id = attachment_record.file_id
                 AND f.deleted = 0
                WHERE c.id = ?
                  AND c.tenant_id = ?
                  AND c.deleted = 0
                LIMIT 1
                """,
                id,
                tenantId
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "合同不存在或不在当前租户范围");
        }
        Map<String, Object> detail = new LinkedHashMap<>(rows.getFirst());
        prepareContractFileDownloadFields(detail, true);
        detail.put("rooms", jdbcTemplate.queryForList(
                """
                SELECT room_record.id,
                       building_record.name AS buildingName,
                       floor_record.name AS floorName,
                       room_record.room_no AS roomNumber,
                       room_record.area,
                       room_record.property_fee_area AS propertyFeeArea
                FROM building_room room_record
                LEFT JOIN building building_record
                  ON building_record.id = room_record.building_id
                 AND building_record.deleted = 0
                LEFT JOIN building_floor floor_record
                  ON floor_record.id = room_record.floor_id
                 AND floor_record.deleted = 0
                WHERE room_record.deleted = 0
                  AND (
                    room_record.id = (
                        SELECT legacy_contract.room_id
                        FROM contract legacy_contract
                        WHERE legacy_contract.id = ? AND legacy_contract.deleted = 0
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM contract_room link_record
                        WHERE link_record.contract_id = ?
                          AND link_record.room_id = room_record.id
                          AND link_record.deleted = 0
                    )
                  )
                ORDER BY building_record.name, floor_record.name, room_record.room_no
                """,
                id,
                id
        ));
        detail.put("attachments", contractAttachments(id, true));
        if (customFieldProjectionService != null) {
            detail = customFieldProjectionService.attach("contracts", List.of(detail)).getFirst();
        } else {
            detail.put("customFields", Map.of());
        }
        return ApiResponse.success(detail);
    }

    @GetMapping("/mobile/contracts/{id}/file/download")
    public ResponseEntity<Resource> mobileDownloadElectronicContract(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id) {
        Long tenantId = MobileApiSupport.requireTenantId(principal);
        return contractFileDownloadResponse(id, tenantId);
    }

    @GetMapping("/contracts/{id}/attachments")
    public ApiResponse<List<Map<String, Object>>> contractAttachments(@PathVariable Long id) {
        ensureContractExists(id);
        return ApiResponse.success(contractAttachments(id, false));
    }

    @Transactional
    @PostMapping("/contracts/{id}/attachments")
    public ApiResponse<Map<String, Object>> uploadContractAttachment(
            @PathVariable Long id,
            @RequestParam(value = "attachmentType", required = false) String attachmentType,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal
    ) throws IOException {
        ensureContractExists(id);
        String normalizedType = normalizeAttachmentType(attachmentType);
        if (ELECTRONIC_CONTRACT.equals(normalizedType)) {
            saveElectronicContractFile(id, file);
        } else {
            saveAdditionalContractFile(id, normalizedType, file, principal.userId());
        }
        auditContractAttachment(id, normalizedType, principal.userId());
        List<Map<String, Object>> attachments = contractAttachments(id, false);
        return ApiResponse.success(Map.of(
                "attachments", attachments,
                "attachmentCount", attachments.size()
        ));
    }

    @GetMapping("/contracts/{id}/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> downloadContractAttachment(
            @PathVariable Long id,
            @PathVariable Long attachmentId
    ) {
        return contractAttachmentDownloadResponse(id, attachmentId, null);
    }

    @GetMapping("/mobile/contracts/{id}/attachments")
    public ApiResponse<List<Map<String, Object>>> mobileContractAttachments(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id
    ) {
        Long tenantId = MobileApiSupport.requireTenantId(principal);
        ensureTenantContract(id, tenantId);
        return ApiResponse.success(contractAttachments(id, true));
    }

    @GetMapping("/mobile/contracts/{id}/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> mobileDownloadContractAttachment(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @PathVariable Long attachmentId
    ) {
        Long tenantId = MobileApiSupport.requireTenantId(principal);
        return contractAttachmentDownloadResponse(id, attachmentId, tenantId);
    }

    private void updateStatus(Long id, String status) {
        int updated = jdbcTemplate.update("UPDATE contract SET status = ?, updated_by = ? WHERE id = ? AND deleted = 0", status, 0L, id);
        if (updated == 0) {
            throw new BusinessException(404, "Contract not found");
        }
    }

    private void updateRoomRentStatus(Long contractId, String rentStatus) {
        for (Long roomId : contractRoomIds(contractId)) {
            jdbcTemplate.update("UPDATE building_room SET rent_status = ?, updated_by = ? WHERE id = ? AND deleted = 0", rentStatus, 0L, roomId);
        }
    }

    private List<Long> contractRoomIds(Long contractId) {
        List<Long> roomIds = currentContractRoomIds(contractId);
        if (!roomIds.isEmpty()) {
            return roomIds;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT room_id FROM contract WHERE id = ? AND deleted = 0", contractId);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "Contract not found");
        }
        Long roomId = toLong(rows.getFirst().get("room_id"));
        return roomId == null ? List.of() : List.of(roomId);
    }

    private List<Long> currentContractRoomIds(Long contractId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT room_id FROM contract_room WHERE contract_id = ? AND deleted = 0 ORDER BY id",
                contractId
        );
        List<Long> roomIds = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long roomId = toLong(row.get("room_id"));
            if (roomId != null) {
                roomIds.add(roomId);
            }
        }
        return roomIds;
    }

    private void syncContractRooms(Long contractId, List<Long> roomIds) {
        Set<Long> desired = new LinkedHashSet<>(roomIds);
        Set<Long> current = new LinkedHashSet<>(currentContractRoomIds(contractId));

        for (Long existingRoomId : current) {
            if (!desired.contains(existingRoomId)) {
                jdbcTemplate.update(
                        "UPDATE contract_room SET deleted = 1, updated_by = ? WHERE contract_id = ? AND room_id = ? AND deleted = 0",
                        0L,
                        contractId,
                        existingRoomId
                );
            }
        }

        for (Long roomId : desired) {
            if (!current.contains(roomId)) {
                jdbcTemplate.update(
                        "INSERT INTO contract_room (contract_id, room_id, created_by, updated_by) VALUES (?, ?, ?, ?)",
                        contractId,
                        roomId,
                        0L,
                        0L
                );
            }
        }
    }

    private Long findContractIdByCode(String contractCode) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id FROM contract WHERE code = ? AND deleted = 0 ORDER BY id DESC LIMIT 1",
                contractCode
        );
        if (rows.isEmpty()) {
            throw new BusinessException(500, "Contract create failed");
        }
        return toLong(rows.getFirst().get("id"));
    }

    private List<Long> requiredRoomIds(Map<String, Object> body) {
        Set<Long> roomIds = new LinkedHashSet<>();
        Object rawRoomIds = body == null ? null : body.get("roomIds");
        if (rawRoomIds instanceof Collection<?> values) {
            for (Object value : values) {
                Long roomId = toLong(value);
                if (roomId != null) {
                    roomIds.add(roomId);
                }
            }
        } else if (rawRoomIds instanceof Object[] values) {
            for (Object value : values) {
                Long roomId = toLong(value);
                if (roomId != null) {
                    roomIds.add(roomId);
                }
            }
        } else if (rawRoomIds != null) {
            String text = String.valueOf(rawRoomIds).trim();
            if (!text.isEmpty()) {
                for (String part : text.split(",")) {
                    Long roomId = toLong(part);
                    if (roomId != null) {
                        roomIds.add(roomId);
                    }
                }
            }
        }

        Long singleRoomId = JdbcMaps.longVal(body, "roomId", "room_id");
        if (roomIds.isEmpty() && singleRoomId != null) {
            roomIds.add(singleRoomId);
        }
        if (roomIds.isEmpty()) {
            throw new BusinessException(400, "Room is required");
        }
        return new ArrayList<>(roomIds);
    }

    private void decorateContractRooms(List<Map<String, Object>> contracts) {
        if (contracts == null || contracts.isEmpty()) {
            return;
        }

        List<Long> contractIds = new ArrayList<>();
        for (Map<String, Object> contract : contracts) {
            Long contractId = toLong(contract.get("id"));
            if (contractId != null) {
                contractIds.add(contractId);
            }
        }
        if (contractIds.isEmpty()) {
            return;
        }

        String placeholders = String.join(",", contractIds.stream().map(id -> "?").toList());
        List<Map<String, Object>> roomRows = jdbcTemplate.queryForList(
                """
                SELECT cr.contract_id AS contractId,
                       r.id AS roomId,
                       r.room_no AS roomName,
                       r.floor_id AS floorId,
                       f.floor_no AS floorNo,
                       f.name AS floorName,
                       r.area AS area
                FROM contract_room cr
                INNER JOIN building_room r ON r.id = cr.room_id AND r.deleted = 0
                LEFT JOIN building_floor f ON f.id = r.floor_id AND f.deleted = 0
                WHERE cr.deleted = 0 AND cr.contract_id IN (
                """ + placeholders + """
                )
                ORDER BY cr.contract_id, f.floor_no, r.room_no, r.id
                """,
                contractIds.toArray()
        );

        Map<Long, List<Map<String, Object>>> roomsByContract = new HashMap<>();
        for (Map<String, Object> room : roomRows) {
            Long contractId = toLong(room.get("contractId"));
            if (contractId != null) {
                roomsByContract.computeIfAbsent(contractId, key -> new ArrayList<>()).add(new LinkedHashMap<>(room));
            }
        }

        for (Map<String, Object> contract : contracts) {
            Long contractId = toLong(contract.get("id"));
            List<Map<String, Object>> linkedRooms = contractId == null ? List.of() : roomsByContract.getOrDefault(contractId, List.of());
            if (linkedRooms.isEmpty()) {
                linkedRooms = fallbackContractRoom(contract);
            }
            List<Long> roomIds = new ArrayList<>();
            List<Long> floorIds = new ArrayList<>();
            List<String> roomNames = new ArrayList<>();
            for (Map<String, Object> room : linkedRooms) {
                Long roomId = toLong(room.get("roomId"));
                Long floorId = toLong(room.get("floorId"));
                String roomName = stringValue(room.get("roomName"));
                if (roomId != null) {
                    roomIds.add(roomId);
                }
                if (floorId != null && !floorIds.contains(floorId)) {
                    floorIds.add(floorId);
                }
                if (roomName != null) {
                    roomNames.add(roomName);
                }
            }
            contract.put("rooms", linkedRooms);
            contract.put("roomIds", roomIds);
            contract.put("floorIds", floorIds);
            contract.put("roomNames", roomNames);
            contract.put("roomSummary", roomSummary(linkedRooms));
            if (!roomIds.isEmpty()) {
                contract.put("roomId", roomIds.getFirst());
            }
        }
    }

    private List<Map<String, Object>> fallbackContractRoom(Map<String, Object> contract) {
        Long roomId = toLong(contract.get("roomId"));
        if (roomId == null) {
            return List.of();
        }
        Map<String, Object> room = new LinkedHashMap<>();
        room.put("roomId", roomId);
        room.put("roomName", contract.get("roomName"));
        return List.of(room);
    }

    private String roomSummary(List<Map<String, Object>> rooms) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> room : rooms) {
            String floorText = floorText(room);
            String roomText = stringValue(room.get("roomName"));
            if (roomText == null) {
                Long roomId = toLong(room.get("roomId"));
                roomText = roomId == null ? "-" : "房间ID:" + roomId;
            }
            grouped.computeIfAbsent(floorText, key -> new ArrayList<>()).add(roomText);
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            if (entry.getKey().isBlank()) {
                parts.add(String.join("、", entry.getValue()));
            } else {
                parts.add(entry.getKey() + " " + String.join("、", entry.getValue()));
            }
        }
        return parts.isEmpty() ? null : String.join("；", parts);
    }

    private String floorText(Map<String, Object> room) {
        String floorName = stringValue(room.get("floorName"));
        if (floorName != null) {
            return floorName;
        }
        Long floorNo = toLong(room.get("floorNo"));
        return floorNo == null ? "" : floorNo + "F";
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : Long.valueOf(text);
    }

    private void ensureContractExists(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id FROM contract WHERE id = ? AND deleted = 0 LIMIT 1",
                id
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "Contract not found");
        }
    }

    private void saveElectronicContractFile(Long contractId, MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? "contract-" + contractId + ".pdf"
                : file.getOriginalFilename();
        if (!originalName.toLowerCase().endsWith(".pdf")) {
            throw new BusinessException(400, "仅支持 PDF 电子合同");
        }

        jdbcTemplate.update(
                """
                UPDATE contract_attachment
                SET status = 'REPLACED', deleted = 1, updated_by = ?
                WHERE contract_id = ? AND attachment_type = ? AND deleted = 0
                """,
                0L,
                contractId,
                ELECTRONIC_CONTRACT
        );

        Path directory = uploadRoot().resolve(Path.of("private", "contracts"));
        Files.createDirectories(directory);
        String storedName = JdbcMaps.code("CON-FILE") + ".pdf";
        Path target = directory.resolve(storedName).normalize();
        file.transferTo(target);

        String fileCode = JdbcMaps.code("FILE");
        String storagePath = Path.of("private", "contracts", storedName).toString().replace('\\', '/');
        jdbcTemplate.update(
                """
                INSERT INTO file_upload (code, biz_type, biz_id, original_name, stored_name, storage_path, mime_type, file_size, status, created_by, updated_by)
                VALUES (?, 'CONTRACT_ELECTRONIC', ?, ?, ?, ?, ?, ?, 'ENABLED', ?, ?)
                """,
                fileCode,
                contractId,
                originalName,
                storedName,
                storagePath,
                file.getContentType() == null ? "application/pdf" : file.getContentType(),
                file.getSize(),
                0L,
                0L
        );
        Long fileId = jdbcTemplate.queryForObject(
                "SELECT id FROM file_upload WHERE code = ? AND deleted = 0 LIMIT 1",
                Long.class,
                fileCode
        );
        jdbcTemplate.update(
                "INSERT INTO contract_attachment (contract_id, file_id, attachment_type, status, created_by, updated_by) VALUES (?, ?, ?, 'ENABLED', ?, ?)",
                contractId,
                fileId,
                ELECTRONIC_CONTRACT,
                0L,
                0L
        );
    }

    private void saveAdditionalContractFile(
            Long contractId,
            String attachmentType,
            MultipartFile file,
            Long operatorId
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请上传合同附件 PDF 文件");
        }
        String originalName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? "contract-" + contractId + "-attachment.pdf"
                : Path.of(file.getOriginalFilename()).getFileName().toString();
        if (!originalName.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")
                || (file.getContentType() != null && !"application/pdf".equalsIgnoreCase(file.getContentType()))) {
            throw new BusinessException(400, "合同附件仅支持扩展名和 MIME 一致的 PDF 文件");
        }
        Path directory = uploadRoot().resolve(Path.of("private", "contracts"));
        Files.createDirectories(directory);
        String storedName = JdbcMaps.code("CON-ATTACH") + ".pdf";
        Path target = directory.resolve(storedName).normalize();
        file.transferTo(target);

        String fileCode = JdbcMaps.code("FILE");
        String storagePath = Path.of("private", "contracts", storedName).toString().replace('\\', '/');
        jdbcTemplate.update(
                """
                INSERT INTO file_upload (
                    code, biz_type, biz_id, original_name, stored_name, storage_path,
                    mime_type, file_size, status, created_by, updated_by
                )
                VALUES (?, 'CONTRACT_ATTACHMENT', ?, ?, ?, ?, 'application/pdf', ?, 'ENABLED', ?, ?)
                """,
                fileCode,
                contractId,
                originalName,
                storedName,
                storagePath,
                file.getSize(),
                operatorId,
                operatorId
        );
        Long fileId = jdbcTemplate.queryForObject(
                "SELECT id FROM file_upload WHERE code = ? AND deleted = 0 LIMIT 1",
                Long.class,
                fileCode
        );
        jdbcTemplate.update(
                """
                INSERT INTO contract_attachment (
                    contract_id, file_id, attachment_type, status, created_by, updated_by
                )
                VALUES (?, ?, ?, 'ENABLED', ?, ?)
                """,
                contractId,
                fileId,
                attachmentType,
                operatorId,
                operatorId
        );
    }

    private String normalizeAttachmentType(String attachmentType) {
        String normalized = attachmentType == null
                ? ELECTRONIC_CONTRACT
                : attachmentType.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "MAIN", "MAIN_CONTRACT", "ELECTRONIC_CONTRACT" -> ELECTRONIC_CONTRACT;
            case "SUPPLEMENT", "SUPPLEMENTARY", "SUPPLEMENTARY_AGREEMENT" -> SUPPLEMENTARY_AGREEMENT;
            default -> throw new BusinessException(400, "合同附件类型无效");
        };
    }

    private List<Map<String, Object>> contractAttachments(Long contractId, boolean mobile) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT attachment_record.id AS attachmentId,
                       attachment_record.file_id AS fileId,
                       attachment_record.attachment_type AS attachmentType,
                       attachment_record.status,
                       file_record.original_name AS fileName,
                       file_record.mime_type AS mimeType,
                       file_record.file_size AS fileSize,
                       attachment_record.created_at AS uploadedAt
                FROM contract_attachment attachment_record
                INNER JOIN file_upload file_record
                  ON file_record.id = attachment_record.file_id
                 AND file_record.deleted = 0
                 AND file_record.status IN ('ACTIVE', 'ENABLED')
                WHERE attachment_record.contract_id = ?
                  AND attachment_record.deleted = 0
                  AND attachment_record.status IN ('ACTIVE', 'ENABLED')
                ORDER BY CASE WHEN attachment_record.attachment_type = 'ELECTRONIC_CONTRACT' THEN 0 ELSE 1 END,
                         attachment_record.created_at DESC,
                         attachment_record.id DESC
                """,
                contractId
        ).stream().<Map<String, Object>>map(LinkedHashMap::new).toList();
        String prefix = mobile ? "/mobile" : "";
        rows.forEach(row -> row.put(
                "downloadUrl",
                prefix + "/contracts/" + contractId + "/attachments/" + row.get("attachmentId") + "/download"
        ));
        return rows;
    }

    private void decorateContractAttachments(List<Map<String, Object>> contracts, boolean mobile) {
        List<Long> contractIds = contracts.stream()
                .map(row -> row.get("id"))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::longValue)
                .distinct()
                .toList();
        if (contractIds.isEmpty()) return;
        String placeholders = String.join(",", java.util.Collections.nCopies(contractIds.size(), "?"));
        String attachmentSql = """
                SELECT attachment_record.contract_id AS contractId,
                       attachment_record.id AS attachmentId,
                       attachment_record.file_id AS fileId,
                       attachment_record.attachment_type AS attachmentType,
                       attachment_record.status,
                       file_record.original_name AS fileName,
                       file_record.mime_type AS mimeType,
                       file_record.file_size AS fileSize,
                       attachment_record.created_at AS uploadedAt
                FROM contract_attachment attachment_record
                INNER JOIN file_upload file_record
                  ON file_record.id = attachment_record.file_id
                 AND file_record.deleted = 0
                 AND file_record.status IN ('ACTIVE', 'ENABLED')
                WHERE attachment_record.contract_id IN (%s)
                  AND attachment_record.deleted = 0
                  AND attachment_record.status IN ('ACTIVE', 'ENABLED')
                ORDER BY attachment_record.contract_id,
                         CASE WHEN attachment_record.attachment_type = 'ELECTRONIC_CONTRACT' THEN 0 ELSE 1 END,
                         attachment_record.created_at DESC,
                         attachment_record.id DESC
                """.formatted(placeholders);
        List<Map<String, Object>> attachments = jdbcTemplate.queryForList(
                attachmentSql,
                contractIds.toArray()
        );
        Map<Long, List<Map<String, Object>>> byContract = new LinkedHashMap<>();
        String prefix = mobile ? "/mobile" : "";
        for (Map<String, Object> attachment : attachments) {
            Long contractId = ((Number) attachment.get("contractId")).longValue();
            Map<String, Object> item = new LinkedHashMap<>(attachment);
            item.put(
                    "downloadUrl",
                    prefix + "/contracts/" + contractId + "/attachments/" + item.get("attachmentId") + "/download"
            );
            byContract.computeIfAbsent(contractId, ignored -> new ArrayList<>()).add(item);
        }
        contracts.forEach(contract -> {
            Long contractId = contract.get("id") instanceof Number number ? number.longValue() : null;
            List<Map<String, Object>> items = contractId == null
                    ? List.of()
                    : byContract.getOrDefault(contractId, List.of());
            contract.put("attachments", items);
            contract.put("attachmentCount", items.size());
        });
    }

    private ResponseEntity<Resource> contractAttachmentDownloadResponse(
            Long contractId,
            Long attachmentId,
            Long tenantId
    ) {
        List<Object> args = new ArrayList<>();
        args.add(contractId);
        args.add(attachmentId);
        String tenantFilter = "";
        if (tenantId != null) {
            tenantFilter = " AND contract_record.tenant_id = ?";
            args.add(tenantId);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT file_record.original_name AS fileName,
                       file_record.storage_path AS storagePath,
                       file_record.mime_type AS mimeType
                FROM contract contract_record
                INNER JOIN contract_attachment attachment_record
                  ON attachment_record.contract_id = contract_record.id
                 AND attachment_record.id = ?
                 AND attachment_record.deleted = 0
                 AND attachment_record.status IN ('ACTIVE', 'ENABLED')
                INNER JOIN file_upload file_record
                  ON file_record.id = attachment_record.file_id
                 AND file_record.deleted = 0
                 AND file_record.status IN ('ACTIVE', 'ENABLED')
                WHERE contract_record.id = ?
                  AND contract_record.deleted = 0
                """ + tenantFilter + "\n" + """
                LIMIT 1
                """,
                orderedAttachmentArgs(args).toArray()
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "合同附件不存在或不在当前租户范围");
        }
        Map<String, Object> file = rows.getFirst();
        Path path = resolveStoragePath(String.valueOf(file.get("storagePath")));
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new BusinessException(404, "合同附件文件不存在");
        }
        String fileName = String.valueOf(file.getOrDefault("fileName", "contract-" + contractId + ".pdf"));
        String mimeType = String.valueOf(file.getOrDefault("mimeType", "application/pdf"));
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedName)
                .body(new FileSystemResource(path));
    }

    private List<Object> orderedAttachmentArgs(List<Object> args) {
        List<Object> ordered = new ArrayList<>();
        ordered.add(args.get(1));
        ordered.add(args.get(0));
        if (args.size() > 2) ordered.add(args.get(2));
        return ordered;
    }

    private void ensureTenantContract(Long contractId, Long tenantId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM contract WHERE id = ? AND tenant_id = ? AND deleted = 0",
                Long.class,
                contractId,
                tenantId
        );
        if (count == null || count == 0) {
            throw new BusinessException(404, "合同不存在或不在当前租户范围");
        }
    }

    private void auditContractAttachment(Long contractId, String attachmentType, Long operatorId) {
        jdbcTemplate.update(
                """
                INSERT INTO domain_audit_log (
                    aggregate_type, aggregate_id, action, before_snapshot,
                    after_snapshot, operator_id, request_id
                )
                VALUES (
                    'CONTRACT', ?, 'CONTRACT_ATTACHMENT_UPLOADED', JSON_OBJECT(),
                    JSON_OBJECT('attachmentType', ?), ?, ?
                )
                """,
                contractId,
                attachmentType,
                operatorId,
                java.util.UUID.randomUUID().toString()
        );
    }

    private void prepareContractFileDownloadFields(Map<String, Object> contract, boolean mobile) {
        boolean hasContractFile = isTruthy(contract.get("hasContractFile")) && contract.get("contractFileName") != null;
        contract.put("hasContractFile", hasContractFile);
        if (hasContractFile && contract.get("id") != null) {
            String prefix = mobile ? "/mobile" : "";
            contract.put("contractDownloadUrl", prefix + "/contracts/" + contract.get("id") + "/file/download");
        } else {
            contract.put("contractDownloadUrl", null);
        }
    }

    private ResponseEntity<Resource> contractFileDownloadResponse(Long contractId, Long tenantId) {
        Map<String, Object> file = findContractFile(contractId, tenantId);
        Path path = resolveStoragePath(String.valueOf(file.get("storagePath")));
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new BusinessException(404, "Electronic contract file not found");
        }
        String fileName = String.valueOf(file.getOrDefault("fileName", "contract-" + contractId + ".pdf"));
        String mimeType = file.get("mimeType") == null ? "application/pdf" : String.valueOf(file.get("mimeType"));
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedName)
                .body(new FileSystemResource(path));
    }

    private Map<String, Object> findContractFile(Long contractId, Long tenantId) {
        List<Object> args = new ArrayList<>();
        args.add(contractId);
        String tenantFilter = "";
        if (tenantId != null) {
            tenantFilter = " AND c.tenant_id = ?\n";
            args.add(tenantId);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT f.original_name AS fileName,
                       f.storage_path AS storagePath,
                       f.mime_type AS mimeType
                FROM contract c
                INNER JOIN contract_attachment ca ON ca.contract_id = c.id AND ca.attachment_type = 'ELECTRONIC_CONTRACT' AND ca.deleted = 0
                INNER JOIN file_upload f ON f.id = ca.file_id AND f.deleted = 0
                WHERE c.id = ? AND c.deleted = 0
                """ + tenantFilter + "\n" + """
                ORDER BY ca.id DESC, f.id DESC
                LIMIT 1
                """,
                args.toArray()
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "Electronic contract file not found");
        }
        return rows.getFirst();
    }

    private Path resolveStoragePath(String storagePath) {
        Path path = Path.of(storagePath);
        return path.isAbsolute() ? path.normalize() : uploadRoot().resolve(path).normalize();
    }

    private Path uploadRoot() {
        return Path.of(uploadDir == null || uploadDir.isBlank() ? "uploads" : uploadDir).toAbsolutePath().normalize();
    }

    private boolean isTruthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private Long resolveTenantId(Map<String, Object> body, ContactInfo contactInfo) {
        Long tenantId = JdbcMaps.longVal(body, "tenantId", "tenant_id");
        if (tenantId != null) {
            updateTenantContactFields(tenantId, body, contactInfo);
            return tenantId;
        }

        String tenantName = JdbcMaps.requiredStr(body, "Tenant is required", "tenantName", "name");
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                "SELECT id FROM tenant WHERE name = ? AND deleted = 0 ORDER BY id ASC LIMIT 1",
                tenantName
        );
        if (!existing.isEmpty()) {
            Long existingTenantId = ((Number) existing.getFirst().get("id")).longValue();
            updateTenantContactFields(existingTenantId, body, contactInfo);
            return existingTenantId;
        }

        jdbcTemplate.update(
                """
                INSERT INTO tenant (code, name, contact_person, contact_phone, contact_email, status, created_by, updated_by, remark)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """,
                JdbcMaps.code("TEN"),
                tenantName,
                contactInfo.contactPerson(),
                contactInfo.contactPhone(),
                contactInfo.contactEmail(),
                0L,
                0L,
                JdbcMaps.str(body, "tenantRemark")
        );

        List<Map<String, Object>> created = jdbcTemplate.queryForList(
                "SELECT id FROM tenant WHERE name = ? AND deleted = 0 ORDER BY id DESC LIMIT 1",
                tenantName
        );
        if (created.isEmpty()) {
            throw new BusinessException(500, "Tenant create failed");
        }
        return ((Number) created.getFirst().get("id")).longValue();
    }

    private void updateTenantContactFields(Long tenantId, Map<String, Object> body, ContactInfo contactInfo) {
        if (contactInfo.contactPerson() == null && contactInfo.contactPhone() == null && contactInfo.contactEmail() == null) {
            return;
        }
        jdbcTemplate.update(
                """
                UPDATE tenant
                SET contact_person = COALESCE(?, contact_person),
                    contact_phone = COALESCE(?, contact_phone),
                    contact_email = COALESCE(?, contact_email),
                    updated_by = ?
                WHERE id = ? AND deleted = 0
                """,
                contactInfo.contactPerson(),
                contactInfo.contactPhone(),
                contactInfo.contactEmail(),
                0L,
                tenantId
        );
    }

    private ContactInfo validateContactInfo(Map<String, Object> body) {
        return new ContactInfo(
                JdbcMaps.str(body, "contactPerson"),
                ContactValidators.optionalPhone(JdbcMaps.str(body, "contactPhone", "phone")),
                ContactValidators.optionalEmail(JdbcMaps.str(body, "contactEmail", "email"))
        );
    }

    private record ContactInfo(String contactPerson, String contactPhone, String contactEmail) {
    }
}

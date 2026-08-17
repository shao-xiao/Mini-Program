package com.dehui.property.modules.bill.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcPagination;
import com.dehui.property.common.JdbcMaps;
import com.dehui.property.common.PageResponse;
import com.dehui.property.common.WorkspaceOrderBy;
import com.dehui.property.modules.asset.application.AssetChargeBasisService;
import com.dehui.property.modules.bill.application.BillCommandService;
import com.dehui.property.modules.customfield.application.CustomFieldProjectionService;
import com.dehui.property.modules.customfield.application.CustomFieldQueryCompiler.CompiledQuery;
import com.dehui.property.modules.customfield.application.CustomFieldQueryCompiler.NativeOrderPolicy;
import com.dehui.property.modules.customfield.application.CustomFieldQueryService;
import com.dehui.property.modules.mobile.controller.MobileApiSupport;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class BillController {

    private static final String BILL_STATUS_EXPRESSION = "CASE WHEN b.pay_status NOT IN ('PAID', 'CANCELLED') AND b.due_date < CURRENT_DATE THEN 'OVERDUE' ELSE b.pay_status END";
    private static final String BILL_OVERDUE_DAYS_EXPRESSION = "CASE WHEN b.pay_status NOT IN ('PAID', 'CANCELLED') AND b.due_date < CURRENT_DATE THEN DATEDIFF(CURRENT_DATE, b.due_date) ELSE 0 END";
    private static final String BILL_INVOICE_STATUS_EXPRESSION = "CASE WHEN i.id IS NULL THEN 'NONE' ELSE i.invoice_status END";
    private static final Map<String, String> BILL_ORDER_FIELDS = Map.ofEntries(
            Map.entry("billNumber", "b.code"),
            Map.entry("title", "b.title"),
            Map.entry("tenantName", "t.name"),
            Map.entry("contractNumber", "c.code"),
            Map.entry("billType", "b.bill_type"),
            Map.entry("sourceType", "b.source_type"),
            Map.entry("periodStart", "b.period_start"),
            Map.entry("periodEnd", "b.period_end"),
            Map.entry("amount", "b.amount"),
            Map.entry("paidAmount", "b.paid_amount"),
            Map.entry("dueDate", "b.due_date"),
            Map.entry("overdueDays", BILL_OVERDUE_DAYS_EXPRESSION),
            Map.entry("invoiceStatus", BILL_INVOICE_STATUS_EXPRESSION),
            Map.entry("auditStatus", "b.audit_status"),
            Map.entry("status", BILL_STATUS_EXPRESSION)
    );

    private final JdbcTemplate jdbcTemplate;
    private final CustomFieldQueryService customFieldQueryService;
    private final CustomFieldProjectionService customFieldProjectionService;
    private final BillCommandService billCommandService;
    private final AssetChargeBasisService assetChargeBasisService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String uploadDir = "uploads";

    public BillController(JdbcTemplate jdbcTemplate) {
        this(
                jdbcTemplate,
                null,
                null,
                new BillCommandService(jdbcTemplate),
                new AssetChargeBasisService(jdbcTemplate, new ObjectMapper())
        );
    }

    @Autowired
    public BillController(
            JdbcTemplate jdbcTemplate,
            CustomFieldQueryService customFieldQueryService,
            CustomFieldProjectionService customFieldProjectionService,
            BillCommandService billCommandService) {
        this.jdbcTemplate = jdbcTemplate;
        this.customFieldQueryService = customFieldQueryService;
        this.customFieldProjectionService = customFieldProjectionService;
        this.billCommandService = billCommandService;
        this.assetChargeBasisService = new AssetChargeBasisService(jdbcTemplate, new ObjectMapper());
    }

    public BillController(
            JdbcTemplate jdbcTemplate,
            CustomFieldQueryService customFieldQueryService,
            CustomFieldProjectionService customFieldProjectionService,
            BillCommandService billCommandService,
            AssetChargeBasisService assetChargeBasisService) {
        this.jdbcTemplate = jdbcTemplate;
        this.customFieldQueryService = customFieldQueryService;
        this.customFieldProjectionService = customFieldProjectionService;
        this.billCommandService = billCommandService;
        this.assetChargeBasisService = assetChargeBasisService;
    }

    @Value("${dehui.file.upload-dir:uploads}")
    public void setUploadDir(String uploadDir) {
        if (uploadDir != null && !uploadDir.isBlank()) {
            this.uploadDir = uploadDir;
        }
    }

    @GetMapping("/bills")
    public ApiResponse<PageResponse<Map<String, Object>>> bills(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) String billType,
            @RequestParam(required = false) String auditStatus,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String billNumber,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String tenantName,
            @RequestParam(required = false) String contractNumber,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String invoiceStatus,
            @RequestParam(required = false) BigDecimal amount,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateBefore,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateAfter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateTo,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) String remark,
            @RequestParam(required = false) String customFilters,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDirection,
            @RequestParam(required = false) String sorts,
            @RequestParam(required = false) String groupBy,
             @RequestParam(required = false) Integer page,
             @RequestParam(required = false) Integer pageSize) {
        validateFinanceScope(tenantId, contractId);
        String selectSql = """
                SELECT b.id,
                       b.code,
                       b.code AS billNumber,
                       b.tenant_id AS tenantId,
                       t.name AS tenantName,
                       b.contract_id AS contractId,
                       c.code AS contractNumber,
                       b.source_type AS sourceType,
                       b.bill_type AS billType,
                       b.title,
                       b.amount,
                       b.calculated_total AS calculatedTotal,
                       b.adjustment_total AS adjustmentTotal,
                       b.final_amount AS finalAmount,
                       b.paid_amount AS paidAmount,
                       b.period_start AS periodStart,
                       b.period_end AS periodEnd,
                       b.due_date AS dueDate,
                       b.audit_status AS auditStatus,
                       b.workflow_status AS workflowStatus,
                       b.version,
                       b.pay_status AS payStatus,
                       b.pay_status AS status,
                       CASE WHEN b.pay_status NOT IN ('PAID', 'CANCELLED') AND b.due_date < CURRENT_DATE THEN 1 ELSE 0 END AS overdue,
                       CASE WHEN b.pay_status NOT IN ('PAID', 'CANCELLED') AND b.due_date < CURRENT_DATE THEN DATEDIFF(CURRENT_DATE, b.due_date) ELSE 0 END AS overdueDays,
                       b.remark,
                       i.id AS invoiceId,
                       CASE WHEN i.id IS NULL THEN 'NONE' ELSE i.invoice_status END AS invoiceStatus,
                       f.original_name AS invoiceFileName,
                       CASE WHEN f.id IS NULL THEN 0 ELSE 1 END AS hasInvoiceFile
                FROM bill b
                LEFT JOIN tenant t ON t.id = b.tenant_id
                LEFT JOIN contract c ON c.id = b.contract_id AND c.deleted = 0
                LEFT JOIN bill_invoice i
                       ON i.id = (
                           SELECT invoice_record.id
                           FROM bill_invoice invoice_record
                           WHERE invoice_record.bill_id = b.id
                             AND invoice_record.deleted = 0
                             AND invoice_record.status = 'ACTIVE'
                           ORDER BY invoice_record.version_no DESC, invoice_record.id DESC
                           LIMIT 1
                       )
                LEFT JOIN bill_invoice_attachment ia ON ia.invoice_id = i.id AND ia.deleted = 0
                LEFT JOIN file_upload f ON f.id = ia.file_id AND f.deleted = 0
                WHERE b.deleted = 0
                """;
        String countSql = """
                SELECT COUNT(DISTINCT b.id)
                FROM bill b
                LEFT JOIN tenant t ON t.id = b.tenant_id
                LEFT JOIN contract c ON c.id = b.contract_id AND c.deleted = 0
                WHERE b.deleted = 0
                """;
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder();
        appendBillFilters(
                where, args, keyword, tenantId, contractId, billType, auditStatus, status,
                billNumber, title, tenantName, contractNumber, sourceType, invoiceStatus,
                amount, minAmount, maxAmount, dueDate, dueDateBefore, dueDateAfter,
                dueDateFrom, dueDateTo, overdue, remark
        );
        CompiledQuery customQuery = compileCustomQuery(
                customFilters,
                groupBy,
                sorts,
                NativeOrderPolicy.of(BILL_ORDER_FIELDS, sortField, sortDirection, "b.due_date", "b.id")
        );
        if (customQuery != null && customQuery.hasPredicates()) {
            where.append(" AND ").append(customQuery.predicateSql());
            args.addAll(customQuery.predicateArguments());
        }
        String orderBy = customQuery == null || !customQuery.hasOrderings()
                ? WorkspaceOrderBy.build(BILL_ORDER_FIELDS, groupBy, sorts, sortField, sortDirection, "b.due_date", "b.id")
                : String.join(", ", customQuery.orderExpressions());
        PageResponse<Map<String, Object>> result = JdbcPagination.query(
                jdbcTemplate,
                selectSql + where + " ORDER BY " + orderBy,
                countSql + where,
                args,
                customQuery == null ? List.of() : customQuery.orderArguments(),
                page,
                pageSize
        );
        if (customFieldProjectionService != null) {
            result = customFieldProjectionService.attach("bills", result);
        }
        return ApiResponse.success(result);
    }

    public ApiResponse<PageResponse<Map<String, Object>>> bills(
            String keyword,
            Long tenantId,
            String billType,
            String auditStatus,
            String status,
            String billNumber,
            String title,
            String tenantName,
            String contractNumber,
            String sourceType,
            String invoiceStatus,
            BigDecimal amount,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            LocalDate dueDate,
            LocalDate dueDateBefore,
            LocalDate dueDateAfter,
            LocalDate dueDateFrom,
            LocalDate dueDateTo,
            Boolean overdue,
            String remark,
            String sortField,
            String sortDirection,
            String sorts,
            String groupBy,
            Integer page,
            Integer pageSize) {
        return bills(
                keyword, tenantId, null, billType, auditStatus, status, billNumber, title, tenantName,
                contractNumber, sourceType, invoiceStatus, amount, minAmount, maxAmount, dueDate,
                dueDateBefore, dueDateAfter, dueDateFrom, dueDateTo, overdue, remark, null,
                sortField, sortDirection, sorts, groupBy, page, pageSize
        );
    }

    public ApiResponse<PageResponse<Map<String, Object>>> bills(
            String keyword,
            Long tenantId,
            String billType,
            String auditStatus,
            String status,
            Integer page,
            Integer pageSize) {
        return bills(
                keyword, tenantId, null, billType, auditStatus, status,
                null, null, null, null, null, null,
                null, null, null,
                null, null, null, null, null,
                null, null,
                null,
                null, null, null, null,
                page, pageSize
        );
    }

    @GetMapping("/bills/summary")
    public ApiResponse<Map<String, Object>> billSummary(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) String billType,
            @RequestParam(required = false) String auditStatus,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String billNumber,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String tenantName,
            @RequestParam(required = false) String contractNumber,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String invoiceStatus,
            @RequestParam(required = false) BigDecimal amount,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateBefore,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateAfter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateTo,
            @RequestParam(required = false) Boolean overdue,
             @RequestParam(required = false) String remark,
             @RequestParam(required = false) String customFilters) {
        validateFinanceScope(tenantId, contractId);
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        appendBillFilters(
                where, args, keyword, tenantId, contractId, billType, auditStatus, status,
                billNumber, title, tenantName, contractNumber, sourceType, invoiceStatus,
                amount, minAmount, maxAmount, dueDate, dueDateBefore, dueDateAfter,
                dueDateFrom, dueDateTo, overdue, remark
        );
        CompiledQuery customQuery = compileCustomQuery(customFilters, null, null, null);
        if (customQuery != null && customQuery.hasPredicates()) {
            where.append(" AND ").append(customQuery.predicateSql());
            args.addAll(customQuery.predicateArguments());
        }
        Map<String, Object> summary = jdbcTemplate.queryForMap(
                """
                SELECT COUNT(*) AS totalCount,
                       COALESCE(SUM(CASE WHEN b.pay_status <> 'CANCELLED' THEN b.amount ELSE 0 END), 0) AS totalAmount,
                       COALESCE(SUM(CASE WHEN b.pay_status <> 'CANCELLED' THEN b.paid_amount ELSE 0 END), 0) AS paidAmount,
                       COALESCE(SUM(CASE WHEN b.pay_status NOT IN ('PAID', 'CANCELLED') THEN b.amount - b.paid_amount ELSE 0 END), 0) AS unpaidAmount,
                       COALESCE(SUM(CASE WHEN b.pay_status = 'PAID' THEN 1 ELSE 0 END), 0) AS paidCount,
                       COALESCE(SUM(CASE WHEN b.pay_status NOT IN ('PAID', 'CANCELLED') THEN 1 ELSE 0 END), 0) AS unpaidCount,
                       COALESCE(SUM(CASE WHEN b.pay_status NOT IN ('PAID', 'CANCELLED') AND b.due_date < CURRENT_DATE THEN 1 ELSE 0 END), 0) AS overdueCount,
                       COALESCE(SUM(CASE WHEN b.pay_status NOT IN ('PAID', 'CANCELLED') AND b.due_date < CURRENT_DATE THEN b.amount - b.paid_amount ELSE 0 END), 0) AS overdueAmount
                FROM bill b
                LEFT JOIN tenant t ON t.id = b.tenant_id
                LEFT JOIN contract c ON c.id = b.contract_id AND c.deleted = 0
                WHERE b.deleted = 0
                """ + where,
                args.toArray()
        );
        return ApiResponse.success(summary);
    }

    public ApiResponse<Map<String, Object>> billSummary(
            String keyword,
            Long tenantId,
            String billType,
            String auditStatus,
            String status,
            String billNumber,
            String title,
            String tenantName,
            String contractNumber,
            String sourceType,
            String invoiceStatus,
            BigDecimal amount,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            LocalDate dueDate,
            LocalDate dueDateBefore,
            LocalDate dueDateAfter,
            LocalDate dueDateFrom,
            LocalDate dueDateTo,
            Boolean overdue,
            String remark) {
        return billSummary(
                keyword, tenantId, null, billType, auditStatus, status, billNumber, title, tenantName,
                contractNumber, sourceType, invoiceStatus, amount, minAmount, maxAmount, dueDate,
                dueDateBefore, dueDateAfter, dueDateFrom, dueDateTo, overdue, remark, null
        );
    }

    public ApiResponse<Map<String, Object>> billSummary(
            String keyword,
            Long tenantId,
            String billType,
            String auditStatus,
            String status) {
        return billSummary(
                keyword, tenantId, null, billType, auditStatus, status,
                null, null, null, null, null, null,
                null, null, null,
                null, null, null, null, null,
                null, null, null
        );
    }

    @GetMapping("/bills/tenant-directory")
    public ApiResponse<List<Map<String, Object>>> billTenantDirectory(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT t.id,
                       t.code AS tenantCode,
                       t.name AS tenantName,
                       t.status,
                       t.contact_person AS contactPerson,
                       t.contact_phone AS contactPhone,
                       COALESCE(contract_stats.active_contract_count, 0) AS activeContractCount,
                       COALESCE(bill_stats.unpaid_bill_count, 0) AS unpaidBillCount,
                       COALESCE(bill_stats.overdue_bill_count, 0) AS overdueBillCount,
                       COALESCE(bill_stats.unpaid_amount, 0) AS unpaidAmount
                FROM tenant t
                LEFT JOIN (
                    SELECT tenant_id, COUNT(*) AS active_contract_count
                    FROM contract
                    WHERE deleted = 0 AND status = 'ACTIVE'
                    GROUP BY tenant_id
                ) contract_stats ON contract_stats.tenant_id = t.id
                LEFT JOIN (
                    SELECT tenant_id,
                           SUM(CASE WHEN pay_status NOT IN ('PAID', 'CANCELLED') THEN 1 ELSE 0 END) AS unpaid_bill_count,
                           SUM(CASE WHEN pay_status NOT IN ('PAID', 'CANCELLED') AND due_date < CURRENT_DATE THEN 1 ELSE 0 END) AS overdue_bill_count,
                           COALESCE(SUM(CASE WHEN pay_status NOT IN ('PAID', 'CANCELLED') THEN amount - paid_amount ELSE 0 END), 0) AS unpaid_amount
                    FROM bill
                    WHERE deleted = 0
                    GROUP BY tenant_id
                ) bill_stats ON bill_stats.tenant_id = t.id
                WHERE t.deleted = 0
                ORDER BY t.updated_at DESC, t.id DESC
                """
        ).stream().<Map<String, Object>>map(LinkedHashMap::new).toList();
        if (!hasPermission(principal, "tenant:view")) {
            rows.forEach(row -> {
                row.remove("contactPerson");
                row.remove("contactPhone");
            });
        }
        return ApiResponse.success(rows);
    }

    @GetMapping("/bills/contracts")
    public ApiResponse<List<Map<String, Object>>> billContracts(
            @RequestParam(required = false) Long tenantId) {
        List<Object> args = new ArrayList<>();
        String tenantFilter = "";
        if (tenantId != null) {
            tenantFilter = " AND c.tenant_id = ?";
            args.add(tenantId);
        }
        return ApiResponse.success(jdbcTemplate.queryForList(
                """
                SELECT c.id,
                       c.tenant_id AS tenantId,
                       c.code AS contractNumber,
                       c.start_date AS startDate,
                       c.end_date AS endDate,
                       c.rent_amount AS rentAmount,
                       c.status,
                       COALESCE(
                           NULLIF(GROUP_CONCAT(DISTINCT linked_room.room_no ORDER BY linked_room.room_no SEPARATOR '、'), ''),
                           legacy_room.room_no
                       ) AS roomSummary
                FROM contract c
                LEFT JOIN contract_room cr ON cr.contract_id = c.id AND cr.deleted = 0
                LEFT JOIN building_room linked_room ON linked_room.id = cr.room_id AND linked_room.deleted = 0
                LEFT JOIN building_room legacy_room ON legacy_room.id = c.room_id AND legacy_room.deleted = 0
                WHERE c.deleted = 0
                """ + tenantFilter + """
                GROUP BY c.id, c.tenant_id, c.code, c.start_date, c.end_date, c.rent_amount, c.status, legacy_room.room_no
                ORDER BY c.updated_at DESC, c.id DESC
                """,
                args.toArray()
        ));
    }

    @GetMapping("/bills/payments")
    public ApiResponse<PageResponse<Map<String, Object>>> billPayments(
            @RequestParam(required = false) Long tenantId,
             @RequestParam(required = false) Long contractId,
             @RequestParam(required = false) Integer page,
             @RequestParam(required = false) Integer pageSize) {
        validateFinanceScope(tenantId, contractId);
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        appendFinanceScope(where, args, tenantId, contractId, "p.tenant_id", "b.contract_id");
        return ApiResponse.success(JdbcPagination.query(
                jdbcTemplate,
                """
                SELECT p.id,
                       p.code AS paymentNumber,
                       p.bill_id AS billId,
                       b.code AS billNumber,
                       p.tenant_id AS tenantId,
                       t.name AS tenantName,
                       b.contract_id AS contractId,
                       c.code AS contractNumber,
                       p.amount,
                       p.pay_channel AS payChannel,
                       p.transaction_no AS transactionNumber,
                       p.paid_at AS paidAt,
                       p.created_by AS operatorId,
                       COALESCE(u.real_name, u.username) AS operatorName,
                       p.status
                FROM bill_payment_record p
                INNER JOIN bill b ON b.id = p.bill_id AND b.deleted = 0
                LEFT JOIN tenant t ON t.id = p.tenant_id AND t.deleted = 0
                LEFT JOIN contract c ON c.id = b.contract_id AND c.deleted = 0
                LEFT JOIN sys_user u ON u.id = p.created_by AND u.deleted = 0
                WHERE p.deleted = 0
                """ + where + " ORDER BY p.paid_at DESC, p.id DESC",
                """
                SELECT COUNT(*)
                FROM bill_payment_record p
                INNER JOIN bill b ON b.id = p.bill_id AND b.deleted = 0
                WHERE p.deleted = 0
                """ + where,
                args,
                page,
                pageSize
        ));
    }

    @GetMapping("/bills/invoices")
    public ApiResponse<PageResponse<Map<String, Object>>> billInvoices(
            @RequestParam(required = false) Long tenantId,
             @RequestParam(required = false) Long contractId,
             @RequestParam(required = false) Integer page,
             @RequestParam(required = false) Integer pageSize) {
        validateFinanceScope(tenantId, contractId);
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        appendFinanceScope(where, args, tenantId, contractId, "i.tenant_id", "b.contract_id");
        return ApiResponse.success(JdbcPagination.query(
                jdbcTemplate,
                """
                SELECT i.id,
                       i.code AS invoiceNumber,
                       i.bill_id AS billId,
                       b.code AS billNumber,
                       i.tenant_id AS tenantId,
                       t.name AS tenantName,
                       b.contract_id AS contractId,
                       c.code AS contractNumber,
                       i.invoice_title AS invoiceTitle,
                       i.tax_no AS taxNumber,
                       i.amount,
                       i.invoice_status AS invoiceStatus,
                       i.issued_at AS issuedAt,
                       f.original_name AS invoiceFileName,
                       CASE WHEN f.id IS NULL THEN 0 ELSE 1 END AS hasInvoiceFile,
                       i.status
                FROM bill_invoice i
                INNER JOIN bill b ON b.id = i.bill_id AND b.deleted = 0
                LEFT JOIN tenant t ON t.id = i.tenant_id AND t.deleted = 0
                LEFT JOIN contract c ON c.id = b.contract_id AND c.deleted = 0
                LEFT JOIN bill_invoice_attachment ia ON ia.invoice_id = i.id AND ia.deleted = 0
                LEFT JOIN file_upload f ON f.id = ia.file_id AND f.deleted = 0
                WHERE i.deleted = 0
                """ + where + " ORDER BY i.issued_at DESC, i.id DESC",
                """
                SELECT COUNT(DISTINCT i.id)
                FROM bill_invoice i
                INNER JOIN bill b ON b.id = i.bill_id AND b.deleted = 0
                WHERE i.deleted = 0
                """ + where,
                args,
                page,
                pageSize
        ));
    }

    private CompiledQuery compileCustomQuery(
            String customFilters,
            String groupBy,
            String sorts,
            NativeOrderPolicy orderPolicy
    ) {
        if (customFieldQueryService == null) return null;
        return customFieldQueryService.compile(
                "bills",
                customFilters,
                groupBy,
                sorts,
                orderPolicy
        );
    }

    @GetMapping("/bills/charge-bases")
    public ApiResponse<List<Map<String, Object>>> chargeBases(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String billingPeriod,
            @RequestParam(required = false) Long tenantId) {
        return ApiResponse.success(assetChargeBasisService.list(status, sourceType, billingPeriod, tenantId));
    }

    @GetMapping("/bills/{id}")
    public ApiResponse<Map<String, Object>> billDetail(@PathVariable Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT b.id,
                       b.code AS billNumber,
                       b.tenant_id AS tenantId,
                       tenant_record.name AS tenantName,
                       b.contract_id AS contractId,
                       contract_record.code AS contractNumber,
                       b.source_type AS sourceType,
                       b.bill_type AS billType,
                       b.title,
                       b.billing_period AS billingPeriod,
                       b.bill_date AS billDate,
                       b.period_start AS periodStart,
                       b.period_end AS periodEnd,
                       b.due_date AS dueDate,
                       b.calculated_total AS calculatedTotal,
                       b.adjustment_total AS adjustmentTotal,
                       b.final_amount AS finalAmount,
                       b.paid_amount AS paidAmount,
                       GREATEST(b.final_amount - b.paid_amount, 0) AS unpaidAmount,
                       b.workflow_status AS workflowStatus,
                       b.audit_status AS auditStatus,
                       b.pay_status AS payStatus,
                       b.invoice_status AS invoiceStatus,
                       b.version,
                       b.review_opinion AS reviewOpinion,
                       b.reviewed_at AS reviewedAt,
                       b.void_reason AS voidReason,
                       b.remark,
                       b.created_at AS createdAt,
                       b.updated_at AS updatedAt
                FROM bill b
                LEFT JOIN tenant tenant_record
                       ON tenant_record.id = b.tenant_id AND tenant_record.deleted = 0
                LEFT JOIN contract contract_record
                       ON contract_record.id = b.contract_id AND contract_record.deleted = 0
                WHERE b.id = ? AND b.deleted = 0
                LIMIT 1
                """,
                id
        );
        if (rows.isEmpty()) throw new BusinessException(404, "账单不存在");
        Map<String, Object> detail = new LinkedHashMap<>(rows.getFirst());
        detail.put("lineItems", billCommandService.lines(id));
        detail.put("invoices", invoiceRows(id, null));
        detail.put("auditLogs", billCommandService.auditLogs(id));
        return ApiResponse.success(detail);
    }

    @GetMapping("/finance/dashboard")
    public ApiResponse<Map<String, Object>> financeDashboard(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String billingPeriod) {
        StringBuilder scope = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (tenantId != null) {
            scope.append(" AND b.tenant_id = ? ");
            args.add(tenantId);
        }
        if (billingPeriod != null && !billingPeriod.isBlank()) {
            scope.append(" AND b.billing_period = ? ");
            args.add(billingPeriod.trim());
        }
        Map<String, Object> summary = jdbcTemplate.queryForMap(
                """
                SELECT COALESCE(SUM(CASE WHEN b.workflow_status = 'APPROVED' THEN b.final_amount ELSE 0 END), 0) AS receivableAmount,
                       COALESCE(SUM(CASE WHEN b.workflow_status = 'APPROVED' THEN b.paid_amount ELSE 0 END), 0) AS receivedAmount,
                       COALESCE(SUM(CASE WHEN b.workflow_status = 'APPROVED' THEN GREATEST(b.final_amount - b.paid_amount, 0) ELSE 0 END), 0) AS unpaidAmount,
                       COALESCE(SUM(CASE
                           WHEN b.workflow_status = 'APPROVED'
                            AND b.pay_status NOT IN ('PAID', 'CANCELLED')
                            AND b.due_date < CURRENT_DATE
                           THEN GREATEST(b.final_amount - b.paid_amount, 0) ELSE 0 END), 0) AS overdueAmount,
                       COALESCE(SUM(CASE WHEN b.workflow_status = 'PENDING_REVIEW' THEN 1 ELSE 0 END), 0) AS pendingReviewCount,
                       COALESCE(SUM(CASE
                           WHEN b.workflow_status = 'APPROVED'
                            AND b.invoice_status <> 'INVOICED'
                           THEN 1 ELSE 0 END), 0) AS pendingInvoiceCount,
                       0 AS invoicedAmount
                FROM bill b
                WHERE b.deleted = 0
                  AND b.workflow_status <> 'VOID'
                """ + scope,
                args.toArray()
        );
        String invoiceScope = scope.toString().replace("b.", "invoice_bill.");
        BigDecimal invoicedAmount = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(invoice_record.amount), 0)
                FROM bill_invoice invoice_record
                INNER JOIN bill invoice_bill
                        ON invoice_bill.id = invoice_record.bill_id
                       AND invoice_bill.deleted = 0
                WHERE invoice_record.deleted = 0
                  AND invoice_record.status = 'ACTIVE'
                  AND invoice_record.invoice_status IN ('INVOICED', 'ISSUED')
                """ + invoiceScope,
                BigDecimal.class,
                args.toArray()
        );
        summary = new LinkedHashMap<>(summary);
        summary.put("invoicedAmount", invoicedAmount == null ? BigDecimal.ZERO : invoicedAmount);
        List<Map<String, Object>> monthly = jdbcTemplate.queryForList(
                """
                SELECT b.billing_period AS dimension,
                       COALESCE(SUM(CASE WHEN b.workflow_status = 'APPROVED' THEN b.final_amount ELSE 0 END), 0) AS receivableAmount,
                       COALESCE(SUM(CASE WHEN b.workflow_status = 'APPROVED' THEN b.paid_amount ELSE 0 END), 0) AS receivedAmount,
                       COALESCE(SUM(CASE WHEN b.workflow_status = 'APPROVED' THEN GREATEST(b.final_amount - b.paid_amount, 0) ELSE 0 END), 0) AS unpaidAmount
                FROM bill b
                WHERE b.deleted = 0 AND b.workflow_status <> 'VOID'
                """ + scope + """
                GROUP BY b.billing_period
                ORDER BY b.billing_period DESC
                LIMIT 12
                """,
                args.toArray()
        );
        List<Map<String, Object>> byTenant = jdbcTemplate.queryForList(
                """
                SELECT b.tenant_id AS tenantId,
                       tenant_record.name AS tenantName,
                       COALESCE(SUM(CASE WHEN b.workflow_status = 'APPROVED' THEN b.final_amount ELSE 0 END), 0) AS receivableAmount,
                       COALESCE(SUM(CASE WHEN b.workflow_status = 'APPROVED' THEN b.paid_amount ELSE 0 END), 0) AS receivedAmount,
                       COALESCE(SUM(CASE WHEN b.workflow_status = 'APPROVED' THEN GREATEST(b.final_amount - b.paid_amount, 0) ELSE 0 END), 0) AS unpaidAmount
                FROM bill b
                LEFT JOIN tenant tenant_record
                       ON tenant_record.id = b.tenant_id AND tenant_record.deleted = 0
                WHERE b.deleted = 0 AND b.workflow_status <> 'VOID'
                """ + scope + """
                GROUP BY b.tenant_id, tenant_record.name
                ORDER BY unpaidAmount DESC, b.tenant_id
                """,
                args.toArray()
        );
        String lineScope = scope.toString().replace("b.", "bill_record.");
        List<Map<String, Object>> byChargeType = jdbcTemplate.queryForList(
                """
                SELECT line_record.charge_item_type AS chargeItemType,
                       COALESCE(SUM(line_record.calculated_amount), 0) AS calculatedAmount,
                       COALESCE(SUM(line_record.adjustment_amount), 0) AS adjustmentAmount,
                       COALESCE(SUM(line_record.final_amount), 0) AS finalAmount
                FROM bill_line_item line_record
                INNER JOIN bill bill_record
                        ON bill_record.id = line_record.bill_id
                       AND bill_record.deleted = 0
                WHERE line_record.deleted = 0
                  AND line_record.status = 'ACTIVE'
                  AND bill_record.workflow_status = 'APPROVED'
                """ + lineScope + """
                GROUP BY line_record.charge_item_type
                ORDER BY finalAmount DESC
                """,
                args.toArray()
        );
        return ApiResponse.success(Map.of(
                "summary", summary,
                "monthly", monthly,
                "byTenant", byTenant,
                "byChargeType", byChargeType
        ));
    }

    @PostMapping("/bills/charge-bases/property/sync")
    public ApiResponse<Map<String, Object>> syncPropertyChargeBases(
            @RequestBody Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        String billingPeriod = JdbcMaps.requiredStr(body, "账期不能为空", "billingPeriod", "month");
        int confirmed = assetChargeBasisService.syncPropertyBases(
                billingPeriod,
                principal.userId()
        );
        return ApiResponse.success(Map.of("confirmedCount", confirmed, "billingPeriod", billingPeriod));
    }

    @PostMapping("/bills/charge-bases/parking/sync")
    public ApiResponse<Map<String, Object>> syncParkingChargeBases(
            @RequestBody Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        String billingPeriod = JdbcMaps.requiredStr(body, "账期不能为空", "billingPeriod", "month");
        int confirmed = assetChargeBasisService.syncParkingBases(
                billingPeriod,
                principal.userId()
        );
        return ApiResponse.success(Map.of("confirmedCount", confirmed, "billingPeriod", billingPeriod));
    }

    @PostMapping("/bills/charge-bases/generate")
    public ApiResponse<Map<String, Object>> generateBillsFromChargeBases(
            @RequestBody Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        Object rawIds = body == null ? null : body.get("ids");
        if (!(rawIds instanceof List<?> values)) {
            throw new BusinessException(400, "请选择计费依据");
        }
        List<Long> ids = values.stream()
                .map(this::longValue)
                .filter(java.util.Objects::nonNull)
                .toList();
        String dueDateValue = JdbcMaps.str(body, "dueDate");
        LocalDate dueDate = dueDateValue == null ? null : LocalDate.parse(dueDateValue);
        return ApiResponse.success(assetChargeBasisService.generateBills(ids, dueDate, principal.userId()));
    }

    @PostMapping("/bills")
    public ApiResponse<Map<String, Object>> createBill(
            @RequestBody Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        Long billId = billCommandService.createManualDraft(body, principal.userId());
        return ApiResponse.success(Map.of("id", billId, "workflowStatus", "DRAFT"));
    }

    public ApiResponse<Void> createBill(Map<String, Object> body) {
        Long tenantId = JdbcMaps.requiredLong(body, "Tenant is required", "tenantId", "tenant_id");
        Long contractId = JdbcMaps.longVal(body, "contractId", "contract_id");
        validateContractTenant(tenantId, contractId);
        jdbcTemplate.update(
                """
                INSERT INTO bill (code, tenant_id, contract_id, source_type, source_id, bill_type, title, amount, paid_amount, period_start, period_end, due_date, audit_status, pay_status, status, created_by, updated_by, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, 'PENDING', 'UNPAID', 'ACTIVE', ?, ?, ?)
                """,
                JdbcMaps.strOr(body, JdbcMaps.code("BIL"), "billNumber", "code"),
                tenantId,
                contractId,
                JdbcMaps.strOr(body, "MANUAL", "sourceType"),
                JdbcMaps.longVal(body, "sourceId"),
                JdbcMaps.strOr(body, "OTHER", "billType"),
                JdbcMaps.str(body, "title"),
                billCommandService.requirePositiveAmount(body),
                JdbcMaps.date(body, null, "periodStart", "period_start"),
                JdbcMaps.date(body, null, "periodEnd", "period_end"),
                JdbcMaps.date(body, JdbcMaps.today(), "dueDate", "due_date"),
                0L,
                0L,
                JdbcMaps.str(body, "remark")
        );
        return ApiResponse.success();
    }

    private void appendBillFilters(
            StringBuilder where,
            List<Object> args,
            String keyword,
            Long tenantId,
            Long contractId,
            String billType,
            String auditStatus,
            String status,
            String billNumber,
            String title,
            String tenantName,
            String contractNumber,
            String sourceType,
            String invoiceStatus,
            BigDecimal amount,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            LocalDate dueDate,
            LocalDate dueDateBefore,
            LocalDate dueDateAfter,
            LocalDate dueDateFrom,
            LocalDate dueDateTo,
            Boolean overdue,
            String remark) {
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (b.code LIKE ? OR b.title LIKE ? OR t.name LIKE ? OR c.code LIKE ? OR b.bill_type LIKE ? OR b.source_type LIKE ?)");
            String likeKeyword = "%" + keyword.trim() + "%";
            for (int index = 0; index < 6; index++) {
                args.add(likeKeyword);
            }
        }
        if (tenantId != null) {
            where.append(" AND b.tenant_id = ?");
            args.add(tenantId);
        }
        if (contractId != null) {
            where.append(" AND b.contract_id = ?");
            args.add(contractId);
        }
        appendLikeFilter(where, args, "b.code", billNumber);
        appendLikeFilter(where, args, "b.title", title);
        appendLikeFilter(where, args, "t.name", tenantName);
        appendLikeFilter(where, args, "c.code", contractNumber);
        appendEqualsFilter(where, args, "b.bill_type", billType);
        appendEqualsFilter(where, args, "b.audit_status", auditStatus);
        appendEqualsFilter(where, args, "b.source_type", sourceType);
        if (status != null && !status.isBlank()) {
            if ("OVERDUE".equalsIgnoreCase(status.trim())) {
                appendOverdueFilter(where, true);
            } else {
                appendEqualsFilter(where, args, "b.pay_status", status);
            }
        }
        if (invoiceStatus != null && !invoiceStatus.isBlank()) {
            if ("NONE".equalsIgnoreCase(invoiceStatus.trim()) || "NOT_INVOICED".equalsIgnoreCase(invoiceStatus.trim())) {
                where.append(" AND NOT EXISTS (SELECT 1 FROM bill_invoice bi WHERE bi.bill_id = b.id AND bi.deleted = 0)");
            } else if ("INVOICED".equalsIgnoreCase(invoiceStatus.trim())) {
                where.append(" AND EXISTS (SELECT 1 FROM bill_invoice bi WHERE bi.bill_id = b.id AND bi.deleted = 0)");
            } else {
                where.append(" AND EXISTS (SELECT 1 FROM bill_invoice bi WHERE bi.bill_id = b.id AND bi.deleted = 0 AND bi.invoice_status = ?)");
                args.add(invoiceStatus.trim());
            }
        }
        if (amount != null) {
            where.append(" AND b.amount = ?");
            args.add(amount);
        }
        if (minAmount != null) {
            where.append(" AND b.amount >= ?");
            args.add(minAmount);
        }
        if (maxAmount != null) {
            where.append(" AND b.amount <= ?");
            args.add(maxAmount);
        }
        appendDateFilter(where, args, "b.due_date", "=", dueDate);
        appendDateFilter(where, args, "b.due_date", "<", dueDateBefore);
        appendDateFilter(where, args, "b.due_date", ">", dueDateAfter);
        appendDateFilter(where, args, "b.due_date", ">=", dueDateFrom);
        appendDateFilter(where, args, "b.due_date", "<=", dueDateTo);
        if (overdue != null) {
            appendOverdueFilter(where, overdue);
        }
        appendLikeFilter(where, args, "b.remark", remark);
    }

    private void appendLikeFilter(StringBuilder where, List<Object> args, String expression, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        where.append(" AND ").append(expression).append(" LIKE ?");
        args.add("%" + value.trim() + "%");
    }

    private void appendEqualsFilter(StringBuilder where, List<Object> args, String expression, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        where.append(" AND ").append(expression).append(" = ?");
        args.add(value.trim());
    }

    private void appendDateFilter(StringBuilder where, List<Object> args, String expression, String operator, LocalDate value) {
        if (value == null) {
            return;
        }
        where.append(" AND ").append(expression).append(" ").append(operator).append(" ?");
        args.add(value);
    }

    private void appendOverdueFilter(StringBuilder where, boolean overdue) {
        if (overdue) {
            where.append(" AND b.pay_status NOT IN ('PAID', 'CANCELLED') AND b.due_date < CURRENT_DATE");
            return;
        }
        where.append(" AND (b.pay_status IN ('PAID', 'CANCELLED') OR b.due_date IS NULL OR b.due_date >= CURRENT_DATE)");
    }

    @PutMapping("/bills/{id}")
    public ApiResponse<Void> updateBill(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        billCommandService.updateDraft(id, body, principal.userId());
        return ApiResponse.success();
    }

    public ApiResponse<Void> updateBill(Long id, Map<String, Object> body) {
        ensureBillCanChange(id, "已收款账单不能编辑");
        String billNumber = editableBillNumber(body);
        Long tenantId = JdbcMaps.requiredLong(body, "Tenant is required", "tenantId", "tenant_id");
        Long contractId = JdbcMaps.longVal(body, "contractId", "contract_id");
        validateContractTenant(tenantId, contractId);
        int updated;
        try {
            updated = jdbcTemplate.update(
                    """
                    UPDATE bill
                    SET code = COALESCE(?, code),
                        tenant_id = ?,
                        contract_id = ?,
                        source_type = ?,
                        source_id = ?,
                        bill_type = ?,
                        title = ?,
                        period_start = ?,
                        period_end = ?,
                        amount = ?,
                        due_date = ?,
                        audit_status = 'PENDING',
                        pay_status = 'UNPAID',
                        status = 'ACTIVE',
                        remark = ?,
                        updated_by = ?
                    WHERE id = ? AND deleted = 0
                    """,
                    billNumber,
                    tenantId,
                    contractId,
                    JdbcMaps.strOr(body, "MANUAL", "sourceType"),
                    JdbcMaps.longVal(body, "sourceId"),
                    JdbcMaps.strOr(body, "OTHER", "billType"),
                    JdbcMaps.str(body, "title"),
                    JdbcMaps.date(body, null, "periodStart", "period_start"),
                    JdbcMaps.date(body, null, "periodEnd", "period_end"),
                    billCommandService.requirePositiveAmount(body),
                    JdbcMaps.date(body, JdbcMaps.today(), "dueDate", "due_date"),
                    JdbcMaps.str(body, "remark"),
                    0L,
                    id
            );
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "账单编号已存在");
        }
        if (updated == 0) {
            throw new BusinessException(404, "Bill not found");
        }
        return ApiResponse.success();
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String editableBillNumber(Map<String, Object> body) {
        boolean explicitlyProvided = body != null && (body.containsKey("billNumber") || body.containsKey("code"));
        if (!explicitlyProvided) {
            return null;
        }
        String billNumber = JdbcMaps.str(body, "billNumber", "code");
        if (billNumber == null) {
            throw new BusinessException(400, "账单编号不能为空");
        }
        if (billNumber.length() > 64) {
            throw new BusinessException(400, "账单编号不能超过64个字符");
        }
        return billNumber;
    }

    @PostMapping("/bills/{id}/submit")
    public ApiResponse<Void> submitReview(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        billCommandService.submitReview(
                id,
                longValue(body == null ? null : body.get("version")),
                principal.userId()
        );
        return ApiResponse.success();
    }

    @PostMapping("/bills/{id}/approve")
    public ApiResponse<Void> approve(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        billCommandService.approve(
                id,
                longValue(body == null ? null : body.get("version")),
                JdbcMaps.str(body, "auditRemark", "remark"),
                principal.userId()
        );
        return ApiResponse.success();
    }

    @PostMapping("/bills/{id}/reject")
    public ApiResponse<Void> reject(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        billCommandService.reject(
                id,
                longValue(body == null ? null : body.get("version")),
                JdbcMaps.str(body, "auditRemark", "remark"),
                principal.userId()
        );
        return ApiResponse.success();
    }

    @PutMapping("/bills/{id}/lines/{lineId}/adjustment")
    public ApiResponse<Void> adjustLine(
            @PathVariable Long id,
            @PathVariable Long lineId,
            @RequestBody Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        billCommandService.adjustLine(
                id,
                lineId,
                JdbcMaps.decimal(body, BigDecimal.ZERO, "adjustmentAmount"),
                JdbcMaps.str(body, "adjustmentReason", "reason"),
                principal.userId()
        );
        return ApiResponse.success();
    }

    @PostMapping("/bills/{id}/adjustments")
    public ApiResponse<Map<String, Object>> createAdjustmentBill(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        Long adjustmentBillId = billCommandService.createAdjustmentDraft(
                id,
                JdbcMaps.decimal(body, BigDecimal.ZERO, "amount", "adjustmentAmount"),
                JdbcMaps.str(body, "reason", "adjustmentReason"),
                body.get("dueDate") == null ? null : LocalDate.parse(String.valueOf(body.get("dueDate"))),
                principal.userId()
        );
        return ApiResponse.success(Map.of(
                "id", adjustmentBillId,
                "referenceBillId", id,
                "workflowStatus", "DRAFT"
        ));
    }

    @PostMapping("/bills/{id}/void")
    public ApiResponse<Void> voidBill(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        billCommandService.voidBill(
                id,
                longValue(body.get("version")),
                JdbcMaps.requiredStr(body, "作废原因不能为空", "reason", "voidReason"),
                principal.userId()
        );
        return ApiResponse.success();
    }

    @PostMapping("/bills/{id}/pay")
    public ApiResponse<Void> pay(
            @PathVariable Long id,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        billCommandService.confirmOfflinePayment(id, principal == null ? null : principal.userId());
        return ApiResponse.success();
    }

    @Transactional
    @PostMapping("/bills/{id}/invoice")
    public ApiResponse<Void> uploadInvoice(
            @PathVariable Long id,
            @RequestParam(value = "file") MultipartFile file,
            @RequestParam(required = false) String invoiceNumber,
            @RequestParam(required = false) String invoiceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate invoiceDate,
            @RequestParam(required = false) BigDecimal invoiceAmount,
            @RequestParam(required = false) BigDecimal taxAmount,
            @RequestParam(required = false) String remark,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) throws IOException {
        List<Map<String, Object>> bills = jdbcTemplate.queryForList(
                """
                SELECT tenant_id AS tenantId, final_amount AS finalAmount, workflow_status AS workflowStatus,
                       status, invoice_status AS invoiceStatus
                FROM bill
                WHERE id = ? AND deleted = 0
                FOR UPDATE
                """,
                id
        );
        if (bills.isEmpty()) throw new BusinessException(404, "账单不存在");
        Map<String, Object> bill = bills.getFirst();
        if (!"APPROVED".equals(String.valueOf(bill.get("workflowStatus")))
                || !"ACTIVE".equals(String.valueOf(bill.get("status")))) {
            throw new BusinessException(409, "只有已审核且有效的账单可以上传发票");
        }
        validateInvoiceFile(file);
        BigDecimal approvedAmount = toDecimal(bill.get("finalAmount")).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal existingAmount = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(amount), 0)
                FROM bill_invoice
                WHERE bill_id = ? AND deleted = 0
                  AND status = 'ACTIVE'
                  AND invoice_status IN ('INVOICED', 'ISSUED')
                """,
                BigDecimal.class,
                id
        );
        if (existingAmount == null) existingAmount = BigDecimal.ZERO;
        BigDecimal amountToIssue = invoiceAmount == null
                ? approvedAmount.subtract(existingAmount)
                : invoiceAmount.setScale(2, java.math.RoundingMode.HALF_UP);
        if (amountToIssue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "发票金额必须大于0");
        }
        if (existingAmount.add(amountToIssue).compareTo(approvedAmount) > 0) {
            throw new BusinessException(409, "有效发票总金额不能超过已审核账单金额");
        }
        Integer versionNo = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version_no), 0) + 1 FROM bill_invoice WHERE bill_id = ?",
                Integer.class,
                id
        );
        Long previousInvoiceId = jdbcTemplate.query(
                "SELECT id FROM bill_invoice WHERE bill_id = ? ORDER BY version_no DESC, id DESC LIMIT 1",
                resultSet -> resultSet.next() ? resultSet.getLong(1) : null,
                id
        );
        String invoiceCode = JdbcMaps.code("INV");
        jdbcTemplate.update(
                """
                INSERT INTO bill_invoice (
                    code, bill_id, tenant_id, invoice_title, invoice_type, tax_no,
                    invoice_date, amount, tax_amount, invoice_status, issued_at, status,
                    version_no, previous_invoice_id, uploaded_by, uploaded_at,
                    created_by, updated_by, remark
                )
                SELECT ?, ?, ?, tenant_record.name, ?, tenant_record.credit_code,
                       ?, ?, ?, 'INVOICED', CURRENT_TIMESTAMP, 'ACTIVE',
                       ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, ?
                FROM tenant tenant_record
                WHERE tenant_record.id = ? AND tenant_record.deleted = 0
                """,
                invoiceCode,
                id,
                bill.get("tenantId"),
                invoiceType == null || invoiceType.isBlank() ? "ELECTRONIC" : invoiceType.trim(),
                invoiceDate == null ? LocalDate.now() : invoiceDate,
                amountToIssue,
                taxAmount == null ? BigDecimal.ZERO : taxAmount,
                versionNo,
                previousInvoiceId,
                principal.userId(),
                principal.userId(),
                principal.userId(),
                remark,
                bill.get("tenantId")
        );
        Long invoiceId = jdbcTemplate.queryForObject(
                "SELECT id FROM bill_invoice WHERE code = ? AND deleted = 0 LIMIT 1",
                Long.class,
                invoiceCode
        );
        Long fileId = saveInvoiceFile(id, invoiceId, invoiceCode, file, principal.userId());
        jdbcTemplate.update(
                "UPDATE bill_invoice SET code = COALESCE(?, code), file_id = ? WHERE id = ?",
                invoiceNumber == null || invoiceNumber.isBlank() ? null : invoiceNumber.trim(),
                fileId,
                invoiceId
        );
        BigDecimal issuedTotal = existingAmount.add(amountToIssue);
        String billInvoiceStatus = issuedTotal.compareTo(approvedAmount) >= 0
                ? "INVOICED"
                : "PARTIALLY_INVOICED";
        jdbcTemplate.update(
                "UPDATE bill SET invoice_status = ?, version = version + 1, updated_by = ? WHERE id = ?",
                billInvoiceStatus,
                principal.userId(),
                id
        );
        jdbcTemplate.update(
                """
                INSERT INTO bill_audit_log (
                    bill_id, action, before_snapshot, after_snapshot, reason, operator_id
                )
                VALUES (?, 'UPLOAD_INVOICE', NULL, ?, ?, ?)
                """,
                id,
                json(Map.of(
                        "invoiceId", invoiceId,
                        "invoiceNumber", invoiceNumber == null ? invoiceCode : invoiceNumber,
                        "invoiceAmount", amountToIssue,
                        "fileId", fileId
                )),
                remark,
                principal.userId()
        );
        return ApiResponse.success();
    }

    @Transactional
    @DeleteMapping("/bills/{id}/invoice")
    public ApiResponse<Void> deleteInvoice(
            @PathVariable Long id,
            @RequestParam(required = false) Long invoiceId,
            @RequestParam(required = false) String reason,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        Long targetInvoiceId = invoiceId;
        if (targetInvoiceId == null) {
            targetInvoiceId = jdbcTemplate.query(
                    """
                    SELECT id FROM bill_invoice
                    WHERE bill_id = ? AND deleted = 0 AND status = 'ACTIVE'
                    ORDER BY version_no DESC, id DESC LIMIT 1
                    """,
                    resultSet -> resultSet.next() ? resultSet.getLong(1) : null,
                    id
            );
        }
        if (targetInvoiceId == null) throw new BusinessException(404, "有效发票不存在");
        int updated = jdbcTemplate.update(
                """
                UPDATE bill_invoice
                SET invoice_status = 'VOID', status = 'VOID',
                    voided_by = ?, voided_at = CURRENT_TIMESTAMP, void_reason = ?,
                    updated_by = ?
                WHERE id = ? AND bill_id = ? AND deleted = 0 AND status = 'ACTIVE'
                """,
                principal.userId(),
                reason == null || reason.isBlank() ? "发票上传错误，保留历史版本" : reason.trim(),
                principal.userId(),
                targetInvoiceId,
                id
        );
        if (updated != 1) throw new BusinessException(409, "发票状态已变化，请刷新后重试");
        updateBillInvoiceStatus(id, principal.userId());
        jdbcTemplate.update(
                """
                INSERT INTO bill_audit_log (bill_id, action, before_snapshot, after_snapshot, reason, operator_id)
                VALUES (?, 'VOID_INVOICE', ?, ?, ?, ?)
                """,
                id,
                json(Map.of("invoiceId", targetInvoiceId, "status", "ACTIVE")),
                json(Map.of("invoiceId", targetInvoiceId, "status", "VOID")),
                reason,
                principal.userId()
        );
        return ApiResponse.success();
    }

    public ApiResponse<Void> deleteInvoice(Long id) {
        int updated = jdbcTemplate.update(
                "UPDATE bill_invoice SET deleted = 1, updated_by = ? WHERE bill_id = ? AND deleted = 0",
                0L,
                id
        );
        if (updated < 0) {
            throw new BusinessException(409, "发票状态更新失败");
        }
        return ApiResponse.success();
    }

    @Transactional
    @DeleteMapping("/bills/{id}")
    public ApiResponse<Void> deleteBill(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        Long version = longValue(body == null ? null : body.get("version"));
        String reason = JdbcMaps.str(body, "reason");
        if (version == null || reason == null || reason.isBlank()) {
            throw new BusinessException(400, "兼容作废接口必须提供账单版本和真实原因；建议使用 POST /bills/{id}/void");
        }
        billCommandService.voidBill(id, version, reason.trim(), principal.userId());
        return ApiResponse.success();
    }

    @DeleteMapping("/bills/{id}/record")
    public ApiResponse<Void> deleteBillRecord(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        if (principal == null || !"SYSTEM".equals(principal.userType())) {
            throw BusinessException.forbidden("仅后台系统账号可以删除账单记录");
        }
        if (!hasPermission(principal, "bill:delete")) {
            throw BusinessException.forbidden("无账单记录删除权限");
        }
        billCommandService.deleteBillRecord(
                id,
                longValue(body == null ? null : body.get("version")),
                JdbcMaps.str(body, "reason"),
                JdbcMaps.str(body, "confirmBillNumber", "billNumber"),
                principal.userId(),
                hasExactSensitivePermission(principal, "bill:delete-approved")
        );
        return ApiResponse.success();
    }

    @PostMapping("/bills/{id}/record/check")
    public ApiResponse<Map<String, Object>> checkBillRecordDeletion(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal) {
        if (principal == null || !"SYSTEM".equals(principal.userType())) {
            throw BusinessException.forbidden("仅后台系统账号可以核验账单删除资格");
        }
        if (!hasPermission(principal, "bill:delete")) {
            throw BusinessException.forbidden("无账单记录删除权限");
        }
        return ApiResponse.success(billCommandService.deletionEligibility(
                id,
                longValue(body == null ? null : body.get("version")),
                hasExactSensitivePermission(principal, "bill:delete-approved")
        ));
    }

    @GetMapping("/bills/{id}/invoice/download")
    public ResponseEntity<Resource> downloadInvoice(@PathVariable Long id) {
        return invoiceDownloadResponse(id, null, null);
    }

    @GetMapping("/payments")
    public ApiResponse<List<Map<String, Object>>> payments() {
        return ApiResponse.success(jdbcTemplate.queryForList("SELECT * FROM bill_payment_record WHERE deleted = 0 ORDER BY id DESC"));
    }

    @GetMapping("/invoices")
    public ApiResponse<List<Map<String, Object>>> invoices() {
        return ApiResponse.success(jdbcTemplate.queryForList("SELECT * FROM bill_invoice WHERE deleted = 0 ORDER BY id DESC"));
    }

    @GetMapping("/mobile/bills")
    public ApiResponse<Map<String, Object>> mobileBills(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String billType,
             @RequestParam(required = false) String category,
             @RequestParam(required = false) Integer page,
             @RequestParam(required = false) Integer pageSize) {
        requireTenantBillManager(principal);
        Long tenantId = MobileApiSupport.requireTenantId(principal);
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        String statusFilter = "";
        if (status != null && !status.isBlank()) {
            statusFilter = switch (status.trim().toUpperCase()) {
                case "UNPAID" -> " AND b.pay_status NOT IN ('PAID', 'CANCELLED')";
                case "PAID" -> " AND b.pay_status = 'PAID'";
                case "OVERDUE" -> """
                         AND b.pay_status NOT IN ('PAID', 'CANCELLED')
                         AND b.due_date < CURRENT_DATE
                        """;
                case "CANCELLED" -> " AND b.pay_status = 'CANCELLED'";
                default -> throw new BusinessException(400, "Unsupported mobile bill status");
            };
        }
        String billTypeFilter = "";
        if (billType != null && !billType.isBlank()) {
            billTypeFilter = " AND b.bill_type = ?";
            args.add(billType.trim());
        }
        String categoryFilter = mobileBillCategoryFilter(category);
        String whereSql = " WHERE b.deleted = 0 AND b.workflow_status = 'APPROVED' AND b.tenant_id = ?"
                + statusFilter + billTypeFilter + categoryFilter + "\n";
        PageResponse<Map<String, Object>> billPage = JdbcPagination.query(
                jdbcTemplate,
                """
                SELECT b.id,
                       b.code,
                       b.code AS billNumber,
                       t.name AS tenantName,
                       b.source_type AS sourceType,
                       b.bill_type AS billType,
                       b.title,
                       b.amount,
                       b.calculated_total AS calculatedTotal,
                       b.adjustment_total AS adjustmentTotal,
                       b.final_amount AS finalAmount,
                       b.paid_amount AS paidAmount,
                        GREATEST(b.final_amount - b.paid_amount, 0) AS unpaidAmount,
                        b.period_start AS periodStart,
                        b.period_end AS periodEnd,
                        b.billing_period AS billingPeriod,
                        b.bill_date AS billDate,
                        b.due_date AS dueDate,
                       b.audit_status AS auditStatus,
                       b.pay_status AS payStatus,
                       b.pay_status AS status,
                       b.pay_status AS statusText,
                       CASE
                         WHEN b.pay_status NOT IN ('PAID', 'CANCELLED') AND b.due_date < CURRENT_DATE THEN 1
                         ELSE 0
                       END AS overdue,
                       CASE
                         WHEN b.pay_status NOT IN ('PAID', 'CANCELLED') AND b.due_date < CURRENT_DATE
                           THEN DATEDIFF(CURRENT_DATE, b.due_date)
                         ELSE 0
                       END AS overdueDays,
                       b.remark,
                        b.created_at AS createdAt,
                       i.id AS invoiceId,
                       CASE WHEN i.id IS NULL THEN 'NONE' ELSE i.invoice_status END AS invoiceStatus,
                       i.issued_at AS invoiceIssuedAt,
                        f.original_name AS invoiceFileName,
                        f.mime_type AS invoiceMimeType,
                        CASE WHEN f.id IS NULL THEN 0 ELSE 1 END AS hasInvoiceFile,
                        payment_record.paid_at AS paidAt,
                        payment_record.pay_channel AS payChannel
                 FROM bill b
                LEFT JOIN tenant t ON t.id = b.tenant_id
                LEFT JOIN bill_invoice i
                       ON i.id = (
                           SELECT invoice_record.id
                           FROM bill_invoice invoice_record
                           WHERE invoice_record.bill_id = b.id
                             AND invoice_record.deleted = 0
                             AND invoice_record.status = 'ACTIVE'
                           ORDER BY invoice_record.version_no DESC, invoice_record.id DESC
                           LIMIT 1
                       )
                 LEFT JOIN bill_invoice_attachment ia ON ia.invoice_id = i.id AND ia.deleted = 0
                 LEFT JOIN file_upload f ON f.id = ia.file_id AND f.deleted = 0
                 LEFT JOIN bill_payment_record payment_record
                        ON payment_record.id = (
                            SELECT latest_payment.id
                            FROM bill_payment_record latest_payment
                            WHERE latest_payment.bill_id = b.id
                              AND latest_payment.deleted = 0
                              AND latest_payment.status = 'PAID'
                            ORDER BY latest_payment.paid_at DESC, latest_payment.id DESC
                            LIMIT 1
                        )
                 """ + whereSql + """
                 ORDER BY CASE
                            WHEN b.pay_status NOT IN ('PAID', 'CANCELLED') AND b.due_date < CURRENT_DATE THEN 0
                            WHEN b.pay_status NOT IN ('PAID', 'CANCELLED') THEN 1
                            WHEN b.pay_status = 'PAID' THEN 2
                            ELSE 3
                          END,
                          CASE WHEN b.pay_status NOT IN ('PAID', 'CANCELLED') THEN b.due_date END ASC,
                          CASE WHEN b.pay_status = 'PAID' THEN b.due_date END DESC,
                          b.updated_at DESC,
                          b.id DESC
                 """,
                "SELECT COUNT(*) FROM bill b" + whereSql,
                args,
                page,
                pageSize
        );
        List<Map<String, Object>> bills = billPage.records().stream().map(row -> {
            Map<String, Object> copy = new LinkedHashMap<>(row);
            prepareInvoiceDownloadFields(copy, true);
            return copy;
        }).toList();
        if (customFieldProjectionService != null) {
            bills = customFieldProjectionService.attach("bills", bills);
        }
        PageResponse<Map<String, Object>> responsePage = new PageResponse<>(
                bills, billPage.total(), billPage.page(), billPage.pageSize()
        );
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("profile", MobileApiSupport.profile(jdbcTemplate, principal));
        response.put("summary", mobileBillSummary(tenantId));
        response.put("records", bills);
        response.put("bills", bills);
        response.put("page", responsePage.page());
        response.put("pageSize", responsePage.pageSize());
        response.put("total", responsePage.total());
        return ApiResponse.success(response);
    }

    public ApiResponse<Map<String, Object>> mobileBills(AuthPrincipal principal, String status) {
        return mobileBills(principal, status, null, null, null, null);
    }

    private String mobileBillCategoryFilter(String category) {
        if (category == null || category.isBlank()) {
            return "";
        }
        String rent = """
                (b.source_type IN ('PROPERTY', 'RENT', 'CONTRACT', 'LEASE')
                 OR b.bill_type IN ('RENT', 'PROPERTY', 'PROPERTY_FEE', 'MANAGEMENT', 'LEASE'))
                """;
        String energy = """
                (b.source_type = 'ENERGY'
                 OR b.bill_type IN ('ENERGY', 'ELECTRIC', 'ELECTRICITY', 'WATER', 'GAS', 'UTILITY'))
                """;
        String parking = "(b.source_type = 'PARKING' OR b.bill_type = 'PARKING')";
        return switch (category.trim().toLowerCase()) {
            case "rent" -> " AND " + rent;
            case "energy" -> " AND " + energy;
            case "parking" -> " AND " + parking;
            case "other" -> " AND NOT (" + rent + " OR " + energy + " OR " + parking + ")";
            default -> throw new BusinessException(400, "Unsupported mobile bill category");
        };
    }

    @GetMapping("/mobile/bills/{id}")
    public ApiResponse<Map<String, Object>> mobileBillDetail(
             @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
             @PathVariable Long id) {
        requireTenantBillManager(principal);
        Long tenantId = MobileApiSupport.requireTenantId(principal);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT b.id,
                       b.code AS billNumber,
                       b.bill_type AS billType,
                       b.title,
                       b.period_start AS periodStart,
                       b.period_end AS periodEnd,
                       b.amount,
                       b.calculated_total AS calculatedTotal,
                       b.adjustment_total AS adjustmentTotal,
                       b.final_amount AS finalAmount,
                       b.paid_amount AS paidAmount,
                       GREATEST(b.final_amount - b.paid_amount, 0) AS unpaidAmount,
                       b.pay_status AS status,
                       b.audit_status AS auditStatus,
                       b.due_date AS dueDate,
                       CASE
                         WHEN b.pay_status NOT IN ('PAID', 'CANCELLED') AND b.due_date < CURRENT_DATE
                           THEN DATEDIFF(CURRENT_DATE, b.due_date)
                         ELSE 0
                       END AS overdueDays,
                       CAST(0 AS DECIMAL(12,2)) AS lateFee,
                       b.source_type AS sourceType,
                       CASE WHEN invoice_record.id IS NULL THEN 'NONE' ELSE invoice_record.invoice_status END AS invoiceStatus,
                       b.created_at AS createdAt,
                       b.remark
                FROM bill b
                LEFT JOIN bill_invoice invoice_record
                  ON invoice_record.id = (
                      SELECT latest_invoice.id
                      FROM bill_invoice latest_invoice
                      WHERE latest_invoice.bill_id = b.id
                        AND latest_invoice.deleted = 0
                        AND latest_invoice.status = 'ACTIVE'
                      ORDER BY latest_invoice.version_no DESC, latest_invoice.id DESC
                      LIMIT 1
                  )
                WHERE b.id = ?
                  AND b.tenant_id = ?
                  AND b.deleted = 0
                  AND b.workflow_status = 'APPROVED'
                LIMIT 1
                """,
                id,
                tenantId
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "账单不存在或不在当前租户范围");
        }
        Map<String, Object> detail = new LinkedHashMap<>(rows.getFirst());
        List<Map<String, Object>> sourceDetails = jdbcTemplate.queryForList(
                """
                SELECT basis_record.source_type AS sourceType,
                       basis_record.billing_period AS billingPeriod,
                       basis_record.quantity,
                       basis_record.unit,
                       basis_record.unit_price_snapshot AS unitPriceSnapshot,
                       basis_record.amount,
                       basis_record.snapshot_json AS snapshot
                FROM asset_charge_basis basis_record
                WHERE basis_record.bill_id = ?
                  AND basis_record.tenant_id = ?
                  AND basis_record.deleted = 0
                ORDER BY basis_record.id
                """,
                id,
                tenantId
        );
        List<Map<String, Object>> lineItems = billCommandService.lines(id);
        detail.put("lineItems", lineItems);
        detail.put("chargeItems", lineItems);
        detail.put("sourceDetails", sourceDetails);
        detail.put("sourceDetail", sourceDetails.isEmpty() ? null : sourceDetails.getFirst());
        detail.put("invoices", invoiceRows(id, tenantId));
        if (customFieldProjectionService != null) {
            detail = customFieldProjectionService.attach("bills", List.of(detail)).getFirst();
        } else {
            detail.put("customFields", Map.of());
        }
        return ApiResponse.success(detail);
    }

    @GetMapping("/mobile/bills/{id}/invoice/download")
    public ResponseEntity<Resource> mobileDownloadInvoice(
             @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
             @PathVariable Long id) {
        requireTenantBillManager(principal);
        Long tenantId = MobileApiSupport.requireTenantId(principal);
        return invoiceDownloadResponse(id, null, tenantId);
    }

    @GetMapping("/mobile/bills/{id}/invoices")
    public ApiResponse<List<Map<String, Object>>> mobileInvoices(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id) {
        requireTenantBillManager(principal);
        Long tenantId = MobileApiSupport.requireTenantId(principal);
        requireTenantBill(id, tenantId);
        return ApiResponse.success(invoiceRows(id, tenantId));
    }

    @GetMapping("/mobile/bills/{id}/invoices/{invoiceId}/download")
    public ResponseEntity<Resource> mobileDownloadInvoice(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @PathVariable Long invoiceId) {
        requireTenantBillManager(principal);
        Long tenantId = MobileApiSupport.requireTenantId(principal);
        return invoiceDownloadResponse(id, invoiceId, tenantId);
    }

    private Map<String, Object> billSummary(List<Map<String, Object>> bills) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal paidAmount = BigDecimal.ZERO;
        BigDecimal unpaidAmount = BigDecimal.ZERO;
        long paidCount = 0;
        long unpaidCount = 0;
        long overdueCount = 0;
        String today = JdbcMaps.today();
        for (Map<String, Object> bill : bills) {
            BigDecimal amount = toDecimal(bill.get("amount"));
            BigDecimal paid = toDecimal(bill.get("paidAmount"));
            BigDecimal unpaid = amount.subtract(paid);
            totalAmount = totalAmount.add(amount);
            paidAmount = paidAmount.add(paid);
            unpaidAmount = unpaidAmount.add(unpaid);
            if ("PAID".equals(String.valueOf(bill.get("status")))) {
                paidCount++;
            } else {
                unpaidCount++;
                Object dueDate = bill.get("dueDate");
                boolean overdue = dueDate != null && String.valueOf(dueDate).compareTo(today) < 0;
                bill.put("overdue", overdue);
                if (overdue) {
                    overdueCount++;
                }
            }
            bill.put("unpaidAmount", unpaid);
            bill.put("title", bill.get("billType"));
        }
        return Map.of(
                "totalAmount", totalAmount,
                "unpaidAmount", unpaidAmount,
                "overdueAmount", unpaidAmount,
                "paidAmount", paidAmount,
                "totalCount", bills.size(),
                "unpaidCount", unpaidCount,
                "overdueCount", overdueCount,
                "paidCount", paidCount
        );
    }

    private BigDecimal toDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }

    private void ensureBillCanChange(Long id, String paidMessage) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT pay_status AS payStatus, paid_amount AS paidAmount FROM bill WHERE id = ? AND deleted = 0 LIMIT 1",
                id
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "Bill not found");
        }
        Map<String, Object> bill = rows.getFirst();
        BigDecimal paidAmount = toDecimal(bill.get("paidAmount"));
        String payStatus = String.valueOf(bill.get("payStatus"));
        if ("PAID".equals(payStatus) || paidAmount.compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(400, paidMessage);
        }
    }

    private void updateBill(Long id, String setClause, Object value) {
        String sql = "UPDATE bill SET " + setClause + ", updated_by = ? WHERE id = ? AND deleted = 0";
        int updated = value == null ? jdbcTemplate.update(sql, 0L, id) : jdbcTemplate.update(sql, value, 0L, id);
        if (updated == 0) {
            throw new BusinessException(404, "Bill not found");
        }
    }

    private Long saveInvoiceFile(
            Long billId,
            Long invoiceId,
            String invoiceCode,
            MultipartFile file,
            Long operatorId) throws IOException {
        String originalName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? invoiceCode + ".pdf"
                : file.getOriginalFilename();
        String extension = invoiceExtension(originalName);
        Path directory = uploadRoot().resolve(Path.of("private", "invoices"));
        Files.createDirectories(directory);
        String storedName = JdbcMaps.code("INV-FILE") + "." + extension;
        Path target = directory.resolve(storedName).normalize();
        if (!target.startsWith(directory.normalize())) {
            throw new BusinessException(400, "发票文件名不安全");
        }
        file.transferTo(target);

        String fileCode = JdbcMaps.code("FILE");
        String storagePath = Path.of("private", "invoices", storedName).toString().replace('\\', '/');
        jdbcTemplate.update(
                """
                INSERT INTO file_upload (code, biz_type, biz_id, original_name, stored_name, storage_path, mime_type, file_size, status, created_by, updated_by)
                VALUES (?, 'BILL_INVOICE', ?, ?, ?, ?, ?, ?, 'ENABLED', ?, ?)
                """,
                fileCode,
                billId,
                originalName,
                storedName,
                storagePath,
                file.getContentType() == null ? "application/pdf" : file.getContentType(),
                file.getSize(),
                operatorId,
                operatorId
        );
        Long fileId = jdbcTemplate.queryForObject("SELECT id FROM file_upload WHERE code = ? AND deleted = 0 LIMIT 1", Long.class, fileCode);
        jdbcTemplate.update(
                "INSERT INTO bill_invoice_attachment (invoice_id, file_id, status, created_by, updated_by) VALUES (?, ?, 'ENABLED', ?, ?)",
                invoiceId,
                fileId,
                operatorId,
                operatorId
        );
        return fileId;
    }

    private void prepareInvoiceDownloadFields(Map<String, Object> bill, boolean mobile) {
        boolean hasInvoiceFile = isTruthy(bill.get("hasInvoiceFile")) && bill.get("invoiceFileName") != null;
        bill.put("hasInvoiceFile", hasInvoiceFile);
        bill.put("hasInvoice", bill.get("invoiceId") != null);
        if (hasInvoiceFile && bill.get("id") != null) {
            String prefix = mobile ? "/mobile" : "";
            bill.put("invoiceDownloadUrl", prefix + "/bills/" + bill.get("id") + "/invoice/download");
        } else {
            bill.put("invoiceDownloadUrl", null);
        }
    }

    private ResponseEntity<Resource> invoiceDownloadResponse(Long billId, Long invoiceId, Long tenantId) {
        Map<String, Object> file = findInvoiceFile(billId, invoiceId, tenantId);
        Path path = resolveStoragePath(String.valueOf(file.get("storagePath")));
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new BusinessException(404, "Invoice file not found");
        }
        String fileName = String.valueOf(file.getOrDefault("fileName", "invoice-" + billId + ".pdf"));
        String mimeType = file.get("mimeType") == null ? "application/pdf" : String.valueOf(file.get("mimeType"));
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .body(new FileSystemResource(path));
    }

    private Map<String, Object> findInvoiceFile(Long billId, Long invoiceId, Long tenantId) {
        List<Object> args = new ArrayList<>();
        args.add(billId);
        String invoiceFilter = "";
        if (invoiceId != null) {
            invoiceFilter = " AND i.id = ?\n";
            args.add(invoiceId);
        }
        String tenantFilter = "";
        if (tenantId != null) {
            tenantFilter = " AND b.tenant_id = ?\n";
            args.add(tenantId);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT f.original_name AS fileName,
                       f.storage_path AS storagePath,
                       f.mime_type AS mimeType
                FROM bill b
                INNER JOIN bill_invoice i
                        ON i.bill_id = b.id
                       AND i.deleted = 0
                       AND i.status = 'ACTIVE'
                       AND i.invoice_status IN ('INVOICED', 'ISSUED')
                INNER JOIN bill_invoice_attachment ia ON ia.invoice_id = i.id AND ia.deleted = 0
                INNER JOIN file_upload f ON f.id = ia.file_id AND f.deleted = 0
                WHERE b.id = ? AND b.deleted = 0
                  AND b.workflow_status = 'APPROVED'
                """ + invoiceFilter + tenantFilter + """
                ORDER BY i.version_no DESC, i.id DESC, f.id DESC
                LIMIT 1
                """,
                args.toArray()
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "Invoice file not found");
        }
        return rows.getFirst();
    }

    private Map<String, Object> mobileBillSummary(Long tenantId) {
        return jdbcTemplate.queryForMap(
                """
                SELECT COALESCE(SUM(final_amount), 0) AS totalAmount,
                       COALESCE(SUM(paid_amount), 0) AS paidAmount,
                       COALESCE(SUM(GREATEST(final_amount - paid_amount, 0)), 0) AS unpaidAmount,
                       COALESCE(SUM(CASE
                          WHEN pay_status NOT IN ('PAID', 'CANCELLED') AND due_date < CURRENT_DATE
                            THEN GREATEST(final_amount - paid_amount, 0)
                         ELSE 0 END), 0) AS overdueAmount,
                       COUNT(*) AS totalCount,
                       SUM(CASE WHEN pay_status = 'PAID' THEN 1 ELSE 0 END) AS paidCount,
                       SUM(CASE WHEN pay_status NOT IN ('PAID', 'CANCELLED') THEN 1 ELSE 0 END) AS unpaidCount,
                       SUM(CASE
                         WHEN pay_status NOT IN ('PAID', 'CANCELLED') AND due_date < CURRENT_DATE THEN 1
                         ELSE 0 END) AS overdueCount
                FROM bill
                WHERE tenant_id = ? AND deleted = 0 AND workflow_status = 'APPROVED'
                """,
                tenantId
        );
    }

    private List<Map<String, Object>> invoiceRows(Long billId, Long tenantId) {
        List<Object> args = new ArrayList<>();
        args.add(billId);
        String tenantFilter = "";
        if (tenantId != null) {
            tenantFilter = " AND invoice_record.tenant_id = ?";
            args.add(tenantId);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT invoice_record.id,
                       invoice_record.code AS invoiceNumber,
                       invoice_record.invoice_type AS invoiceType,
                       invoice_record.invoice_date AS invoiceDate,
                       invoice_record.amount AS invoiceAmount,
                       invoice_record.amount,
                       invoice_record.tax_amount AS taxAmount,
                       invoice_record.file_id AS fileId,
                       invoice_record.version_no AS versionNo,
                       invoice_record.invoice_status AS invoiceStatus,
                       invoice_record.status,
                       invoice_record.issued_at AS issuedAt,
                       invoice_record.uploaded_at AS uploadedAt,
                       invoice_record.remark,
                       file_record.original_name AS fileName,
                       file_record.original_name AS invoiceFileName,
                       file_record.mime_type AS mimeType,
                       CASE WHEN file_record.id IS NULL THEN 0 ELSE 1 END AS hasFile
                FROM bill_invoice invoice_record
                LEFT JOIN file_upload file_record
                       ON file_record.id = invoice_record.file_id
                      AND file_record.deleted = 0
                WHERE invoice_record.bill_id = ?
                  AND invoice_record.deleted = 0
                """ + tenantFilter + """
                ORDER BY invoice_record.version_no DESC, invoice_record.id DESC
                """,
                args.toArray()
        ).stream().<Map<String, Object>>map(LinkedHashMap::new).toList();
        if (tenantId != null) {
            rows.forEach(row -> {
                row.remove("fileId");
                boolean downloadable = "ACTIVE".equals(String.valueOf(row.get("status")))
                        && ("INVOICED".equals(String.valueOf(row.get("invoiceStatus")))
                        || "ISSUED".equals(String.valueOf(row.get("invoiceStatus"))))
                        && Integer.valueOf(1).equals(((Number) row.get("hasFile")).intValue());
                row.put("downloadUrl", downloadable
                        ? "/mobile/bills/" + billId + "/invoices/" + row.get("id") + "/download"
                        : null);
            });
        }
        return rows;
    }

    private void updateBillInvoiceStatus(Long billId, Long operatorId) {
        Map<String, Object> totals = jdbcTemplate.queryForMap(
                """
                SELECT b.final_amount AS finalAmount,
                       COALESCE(SUM(CASE
                           WHEN invoice_record.status = 'ACTIVE'
                            AND invoice_record.invoice_status IN ('INVOICED', 'ISSUED')
                           THEN invoice_record.amount ELSE 0 END), 0) AS invoiceAmount
                FROM bill b
                LEFT JOIN bill_invoice invoice_record
                       ON invoice_record.bill_id = b.id
                      AND invoice_record.deleted = 0
                WHERE b.id = ? AND b.deleted = 0
                GROUP BY b.id, b.final_amount
                """,
                billId
        );
        BigDecimal finalAmount = toDecimal(totals.get("finalAmount"));
        BigDecimal invoiceAmount = toDecimal(totals.get("invoiceAmount"));
        String status = invoiceAmount.compareTo(BigDecimal.ZERO) <= 0
                ? "NOT_INVOICED"
                : invoiceAmount.compareTo(finalAmount) >= 0 ? "INVOICED" : "PARTIALLY_INVOICED";
        jdbcTemplate.update(
                "UPDATE bill SET invoice_status = ?, version = version + 1, updated_by = ? WHERE id = ?",
                status,
                operatorId,
                billId
        );
    }

    private void validateInvoiceFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new BusinessException(400, "请选择发票文件");
        if (file.getSize() > 20L * 1024L * 1024L) {
            throw new BusinessException(400, "发票文件不能超过20MB");
        }
        String originalName = file.getOriginalFilename();
        String extension = invoiceExtension(originalName);
        String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        byte[] header = file.getBytes();
        boolean signatureMatches = switch (extension) {
            case "pdf" -> startsWith(header, new byte[]{'%', 'P', 'D', 'F'});
            case "ofd" -> startsWith(header, new byte[]{'P', 'K'});
            case "jpg", "jpeg" -> startsWith(header, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
            case "png" -> startsWith(header, new byte[]{(byte) 0x89, 'P', 'N', 'G'});
            default -> false;
        };
        boolean mimeMatches = switch (extension) {
            case "pdf" -> "application/pdf".equals(mime);
            case "ofd" -> List.of("application/ofd", "application/octet-stream", "application/zip").contains(mime);
            case "jpg", "jpeg" -> "image/jpeg".equals(mime);
            case "png" -> "image/png".equals(mime);
            default -> false;
        };
        if (!signatureMatches || !mimeMatches) {
            throw new BusinessException(400, "发票扩展名、MIME类型或文件内容不匹配");
        }
    }

    private String invoiceExtension(String originalName) {
        if (originalName == null || originalName.isBlank() || !originalName.contains(".")) {
            throw new BusinessException(400, "发票文件缺少有效扩展名");
        }
        String safeName = Path.of(originalName).getFileName().toString();
        String extension = safeName.substring(safeName.lastIndexOf('.') + 1).toLowerCase();
        if (!List.of("pdf", "ofd", "jpg", "jpeg", "png").contains(extension)) {
            throw new BusinessException(400, "仅支持 PDF、OFD、JPG、JPEG、PNG 发票");
        }
        return extension;
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes == null || bytes.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if (bytes[index] != prefix[index]) return false;
        }
        return true;
    }

    private void requireTenantBillManager(AuthPrincipal principal) {
        MobileApiSupport.requireCapability(principal, "bill.view");
        if (!MobileApiSupport.isTenantManager(jdbcTemplate, principal)) {
            throw BusinessException.forbidden("仅租户负责人可以查看企业账单和发票");
        }
    }

    private void requireTenantBill(Long billId, Long tenantId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM bill
                WHERE id = ? AND tenant_id = ? AND deleted = 0
                  AND workflow_status = 'APPROVED'
                """,
                Integer.class,
                billId,
                tenantId
        );
        if (count == null || count == 0) {
            throw new BusinessException(404, "账单不存在或不在当前租户范围");
        }
    }

    private String json(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new BusinessException(500, "审计快照序列化失败");
        }
    }

    private void appendFinanceScope(
            StringBuilder where,
            List<Object> args,
            Long tenantId,
            Long contractId,
            String tenantExpression,
            String contractExpression) {
        if (tenantId != null) {
            where.append(" AND ").append(tenantExpression).append(" = ?");
            args.add(tenantId);
        }
        if (contractId != null) {
            where.append(" AND ").append(contractExpression).append(" = ?");
            args.add(contractId);
        }
    }

    private void validateContractTenant(Long tenantId, Long contractId) {
        if (contractId == null) return;
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM contract WHERE id = ? AND tenant_id = ? AND deleted = 0",
                Integer.class,
                contractId,
                tenantId
        );
        if (count == null || count == 0) {
            throw new BusinessException(400, "合同不属于当前租户");
        }
    }

    private void validateFinanceScope(Long tenantId, Long contractId) {
        if (tenantId != null && contractId != null) {
            validateContractTenant(tenantId, contractId);
        }
    }

    private boolean hasPermission(AuthPrincipal principal, String required) {
        if (principal == null) return false;
        if (principal.roles() != null && principal.roles().contains("SUPER_ADMIN")) return true;
        List<String> permissions = principal.permissions() == null ? List.of() : principal.permissions();
        if (permissions.contains(required)) return true;
        int colonIndex = required.indexOf(':');
        return colonIndex > 0 && permissions.contains(required.substring(0, colonIndex) + ":*");
    }

    private boolean hasExactSensitivePermission(AuthPrincipal principal, String required) {
        if (principal == null) return false;
        if (principal.roles() != null && principal.roles().contains("SUPER_ADMIN")) return true;
        List<String> permissions = principal.permissions() == null ? List.of() : principal.permissions();
        return permissions.contains(required);
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
}

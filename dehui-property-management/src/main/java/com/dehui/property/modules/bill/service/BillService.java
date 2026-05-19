package com.dehui.property.modules.bill.service;

import com.dehui.property.common.Result;
import com.dehui.property.modules.bill.dto.BillAuditRequest;
import com.dehui.property.modules.bill.dto.BillCreateRequest;
import com.dehui.property.modules.bill.dto.BillResponse;
import com.dehui.property.modules.bill.entity.Bill;
import com.dehui.property.modules.bill.repository.BillRepository;
import com.dehui.property.modules.contract.entity.Contract;
import com.dehui.property.modules.contract.repository.ContractRepository;
import com.dehui.property.modules.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillService {
    private static final DateTimeFormatter BILL_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final BigDecimal LATE_FEE_RATE = new BigDecimal("0.003");
    private static final String INVOICE_STATUS_INVOICED = "INVOICED";
    private static final String INVOICE_STATUS_UNINVOICED = "UNINVOICED";

    private final BillRepository billRepository;
    private final ContractRepository contractRepository;
    private final TenantRepository tenantRepository;

    public Result<List<BillResponse>> findAll(Long tenantId, String status, String auditStatus, String billType) {
        return findAll(tenantId, status, auditStatus, billType, null);
    }

    public Result<List<BillResponse>> findAll(Long tenantId, String status, String auditStatus, String billType, String keyword) {
        List<BillResponse> responses = billRepository.findAll()
                .stream()
                .filter(bill -> tenantId == null || tenantId.equals(bill.getTenantId()))
                .filter(bill -> isBlank(billType) || billType.equals(bill.getBillType()))
                .filter(bill -> matchesAuditStatus(bill, auditStatus))
                .filter(bill -> matchesStatus(bill, status))
                .sorted(Comparator.comparing(
                        Bill::getCreatedTime,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).reversed())
                .map(this::toResponse)
                .filter(response -> matchesKeyword(response, keyword))
                .toList();
        return Result.success(responses);
    }

    public Result<List<BillResponse>> findAll() {
        return findAll(null, null, null, null, null);
    }

    public Result<List<BillResponse>> findByTenantId(Long tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            return Result.error("租户不存在");
        }
        return findAll(tenantId, null, null, null, null);
    }

    public Result<List<BillResponse>> findByContractId(Long contractId) {
        if (!contractRepository.existsById(contractId)) {
            return Result.error("合同不存在");
        }
        List<BillResponse> responses = billRepository.findByContractIdOrderByPeriodStartDesc(contractId)
                .stream()
                .map(this::toResponse)
                .toList();
        return Result.success(responses);
    }

    public Result<List<BillResponse>> findByStatus(String status) {
        return findAll(null, status, null, null, null);
    }

    @Transactional
    public Result<BillResponse> create(BillCreateRequest request) {
        String billNumber = normalizeBillNumber(request.getBillNumber());
        if (billRepository.existsByBillNumber(billNumber)) {
            return Result.error("账单编号已存在");
        }

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
                return Result.error("合同未生效，无法创建账单");
            }
            if (!request.getTenantId().equals(contract.getTenantId())) {
                return Result.error("租户与合同不匹配");
            }
            if (billRepository.existsByContractIdAndBillTypeAndPeriodStart(
                    request.getContractId(),
                    request.getBillType(),
                    request.getPeriodStart()
            )) {
                return Result.error("该合同在本账期内已有同类型账单");
            }
        }

        Result<Void> validation = validateBillFields(request.getAmount(), request.getPeriodStart(), request.getPeriodEnd(), request.getDueDate());
        if (validation.getCode() != 200) {
            return Result.error(validation.getMessage());
        }

        Bill bill = new Bill();
        bill.setBillNumber(billNumber);
        bill.setTenantId(request.getTenantId());
        bill.setContractId(request.getContractId());
        bill.setRoomId(request.getRoomId());
        bill.setBillType(request.getBillType());
        bill.setTitle(defaultTitle(request.getTitle(), request.getBillType(), request.getPeriodStart()));
        bill.setPeriodStart(request.getPeriodStart());
        bill.setPeriodEnd(request.getPeriodEnd());
        bill.setAmount(request.getAmount());
        bill.setPaidAmount(BigDecimal.ZERO);
        bill.setDueDate(request.getDueDate());
        bill.setStatus("UNPAID");
        bill.setAuditStatus("PENDING");
        bill.setSourceType("MANUAL");
        bill.setInvoiceStatus(INVOICE_STATUS_UNINVOICED);
        bill.setRemark(request.getRemark());
        Bill saved = billRepository.save(bill);
        log.info("账单已创建并进入待审核: id={}, number={}, contractId={}", saved.getId(), saved.getBillNumber(), contract == null ? null : contract.getId());

        return Result.success(toResponse(saved));
    }

    @Transactional
    public Result<BillResponse> approve(Long id, String approvedBy, BillAuditRequest request) {
        return billRepository.findById(id)
                .map(bill -> {
                    if ("CANCELLED".equals(bill.getStatus())) {
                        return Result.<BillResponse>error("已取消账单不能发布");
                    }
                    bill.setAuditStatus("APPROVED");
                    bill.setAuditRemark(request == null ? null : request.getAuditRemark());
                    bill.setApprovedBy(isBlank(approvedBy) ? "system" : approvedBy);
                    bill.setApprovedTime(LocalDateTime.now());
                    Bill saved = billRepository.save(bill);
                    log.info("账单审核通过: id={}, number={}, approvedBy={}", saved.getId(), saved.getBillNumber(), saved.getApprovedBy());
                    return Result.success(toResponse(saved));
                })
                .orElseGet(() -> Result.error("账单不存在"));
    }

    @Transactional
    public Result<BillResponse> reject(Long id, String approvedBy, BillAuditRequest request) {
        return billRepository.findById(id)
                .map(bill -> {
                    if ("PAID".equals(bill.getStatus())) {
                        return Result.<BillResponse>error("已收款账单不能驳回");
                    }
                    bill.setAuditStatus("REJECTED");
                    bill.setAuditRemark(request == null ? null : request.getAuditRemark());
                    bill.setApprovedBy(isBlank(approvedBy) ? "system" : approvedBy);
                    bill.setApprovedTime(LocalDateTime.now());
                    Bill saved = billRepository.save(bill);
                    log.info("账单已驳回: id={}, number={}, approvedBy={}", saved.getId(), saved.getBillNumber(), saved.getApprovedBy());
                    return Result.success(toResponse(saved));
                })
                .orElseGet(() -> Result.error("账单不存在"));
    }

    @Transactional
    public Result<BillResponse> pay(Long id) {
        return billRepository.findById(id)
                .map(bill -> {
                    if ("PAID".equals(bill.getStatus())) {
                        return Result.<BillResponse>error("账单已支付");
                    }
                    if ("CANCELLED".equals(bill.getStatus())) {
                        return Result.<BillResponse>error("已取消账单不能收款");
                    }
                    if (!isApproved(bill)) {
                        return Result.<BillResponse>error("账单审核通过后才能确认收款");
                    }
                    bill.setPaidAmount(defaultAmount(bill.getAmount()));
                    bill.setPaidTime(LocalDateTime.now());
                    bill.setStatus("PAID");
                    Bill saved = billRepository.save(bill);
                    log.info("账单已收款: id={}, number={}", saved.getId(), saved.getBillNumber());
                    return Result.success(toResponse(saved));
                })
                .orElseGet(() -> Result.error("账单不存在"));
    }

    @Transactional
    public Result<BillResponse> uploadInvoice(Long id, MultipartFile file, String uploadedBy) {
        if (file == null || file.isEmpty()) {
            return Result.error("请选择发票PDF文件");
        }
        if (!isPdf(file)) {
            return Result.error("只能上传PDF发票文件");
        }

        return billRepository.findById(id)
                .map(bill -> {
                    try {
                        Path uploadDir = invoiceRoot().resolve(String.valueOf(id));
                        Files.createDirectories(uploadDir);

                        String storedName = UUID.randomUUID() + ".pdf";
                        Path target = uploadDir.resolve(storedName).normalize();
                        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

                        deleteStoredInvoice(bill.getInvoiceFilePath(), target);

                        bill.setInvoiceStatus(INVOICE_STATUS_INVOICED);
                        bill.setInvoiceFilePath(Paths.get("uploads", "invoices", String.valueOf(id), storedName).toString().replace("\\", "/"));
                        bill.setInvoiceFileName(sanitizeFileName(file.getOriginalFilename()));
                        bill.setInvoiceUploadedAt(LocalDateTime.now());
                        bill.setInvoiceUploadedBy(isBlank(uploadedBy) ? "system" : uploadedBy);
                        return Result.success(toResponse(billRepository.save(bill)));
                    } catch (IOException ex) {
                        log.error("发票上传失败: billId={}", id, ex);
                        return Result.<BillResponse>error("发票上传失败");
                    }
                })
                .orElseGet(() -> Result.error("账单不存在"));
    }

    @Transactional
    public Result<Void> deleteInvoice(Long id) {
        return billRepository.findById(id)
                .map(bill -> {
                    deleteStoredInvoice(bill.getInvoiceFilePath(), null);
                    bill.setInvoiceStatus(INVOICE_STATUS_UNINVOICED);
                    bill.setInvoiceFilePath(null);
                    bill.setInvoiceFileName(null);
                    bill.setInvoiceUploadedAt(null);
                    bill.setInvoiceUploadedBy(null);
                    billRepository.save(bill);
                    return Result.<Void>success();
                })
                .orElseGet(() -> Result.error("账单不存在"));
    }

    public Result<InvoiceFile> loadInvoiceFile(Long id) {
        return billRepository.findById(id)
                .map(bill -> {
                    if (!INVOICE_STATUS_INVOICED.equals(defaultInvoiceStatus(bill)) || isBlank(bill.getInvoiceFilePath())) {
                        return Result.<InvoiceFile>error("该账单未上传发票");
                    }
                    Path path = resolveInvoicePath(bill.getInvoiceFilePath());
                    if (path == null || !Files.exists(path)) {
                        return Result.<InvoiceFile>error("发票文件不存在");
                    }
                    return Result.success(new InvoiceFile(path, defaultInvoiceFileName(bill)));
                })
                .orElseGet(() -> Result.error("账单不存在"));
    }

    public byte[] exportExcel(Long tenantId, String status, String auditStatus, String billType, String keyword) {
        List<BillResponse> rows = findAll(tenantId, status, auditStatus, billType, keyword).getData();

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("账单");
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            String[] headers = {
                    "账单编号", "租户", "合同", "账单类型", "账期开始", "账期结束", "到期日",
                    "应收金额", "已收金额", "未收金额", "逾期天数", "滞纳金", "是否已开票", "账单状态", "创建时间"
            };

            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            for (int i = 0; i < rows.size(); i++) {
                BillResponse item = rows.get(i);
                Row row = sheet.createRow(i + 1);
                setCell(row, 0, item.getBillNumber());
                setCell(row, 1, item.getTenantName());
                setCell(row, 2, item.getContractNumber());
                setCell(row, 3, item.getBillTypeText());
                setCell(row, 4, formatDate(item.getPeriodStart()));
                setCell(row, 5, formatDate(item.getPeriodEnd()));
                setCell(row, 6, formatDate(item.getDueDate()));
                setCell(row, 7, item.getAmount());
                setCell(row, 8, item.getPaidAmount());
                setCell(row, 9, item.getUnpaidAmount());
                setCell(row, 10, item.getOverdueDays());
                setCell(row, 11, item.getLateFee());
                setCell(row, 12, item.getInvoiceStatusText());
                setCell(row, 13, item.getStatusText());
                setCell(row, 14, formatDateTime(item.getCreatedTime()));
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 512, 12000));
            }

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException("导出Excel失败", ex);
        }
    }

    public BillResponse toResponse(Bill bill) {
        BillResponse response = new BillResponse();
        response.setId(bill.getId());
        response.setBillNumber(bill.getBillNumber());
        response.setBillNo(bill.getBillNumber());
        response.setTenantId(bill.getTenantId());
        if (bill.getTenantId() != null) {
            tenantRepository.findById(bill.getTenantId())
                    .ifPresent(tenant -> response.setTenantName(tenant.getTenantName()));
        }
        response.setContractId(bill.getContractId());
        response.setRoomId(bill.getRoomId());
        if (bill.getContractId() != null) {
            contractRepository.findById(bill.getContractId())
                    .ifPresent(contract -> {
                        response.setContractNumber(contract.getContractNumber());
                        response.setContractName(contract.getContractName());
                    });
        }
        response.setBillType(bill.getBillType());
        response.setBillTypeText(toBillTypeText(bill.getBillType()));
        response.setTitle(defaultTitle(bill.getTitle(), bill.getBillType(), bill.getPeriodStart()));
        response.setPeriodStart(bill.getPeriodStart());
        response.setPeriodEnd(bill.getPeriodEnd());
        response.setAmount(defaultAmount(bill.getAmount()));
        response.setPaidAmount(defaultAmount(bill.getPaidAmount()));
        response.setUnpaidAmount(unpaidAmount(bill));
        response.setDueDate(bill.getDueDate());
        response.setOverdueDays(overdueDays(bill));
        response.setLateFee(lateFee(bill));
        response.setStatus(bill.getStatus());
        response.setStatusText(toStatusText(bill.getStatus()));
        response.setAuditStatus(effectiveAuditStatus(bill));
        response.setAuditStatusText(toAuditStatusText(response.getAuditStatus()));
        response.setAuditRemark(bill.getAuditRemark());
        response.setApprovedBy(bill.getApprovedBy());
        response.setApprovedTime(bill.getApprovedTime());
        response.setSourceType(bill.getSourceType());
        response.setSourceTypeText(toSourceTypeText(bill.getSourceType()));
        response.setSourceId(bill.getSourceId());
        response.setInvoiceStatus(defaultInvoiceStatus(bill));
        response.setInvoiceStatusText(toInvoiceStatusText(response.getInvoiceStatus()));
        response.setInvoiceFileName(bill.getInvoiceFileName());
        response.setInvoiceDownloadUrl(INVOICE_STATUS_INVOICED.equals(response.getInvoiceStatus())
                ? "/bills/" + bill.getId() + "/invoice/download"
                : null);
        response.setInvoiceUploadedAt(bill.getInvoiceUploadedAt());
        response.setInvoiceUploadedBy(bill.getInvoiceUploadedBy());
        response.setRemark(bill.getRemark());
        response.setOverdue(isOverdue(bill));
        response.setPaidTime(bill.getPaidTime());
        response.setCreatedTime(bill.getCreatedTime());
        response.setUpdatedTime(bill.getUpdatedTime());
        return response;
    }

    public boolean isApproved(Bill bill) {
        return "APPROVED".equals(effectiveAuditStatus(bill));
    }

    private Result<Void> validateBillFields(BigDecimal amount, LocalDate periodStart, LocalDate periodEnd, LocalDate dueDate) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Result.error("账单金额必须大于0");
        }
        if (periodStart == null) {
            return Result.error("账期开始日期不能为空");
        }
        if (periodEnd == null) {
            return Result.error("账期结束日期不能为空");
        }
        if (periodEnd.isBefore(periodStart)) {
            return Result.error("账期结束日不能早于账期开始日");
        }
        if (dueDate == null) {
            return Result.error("到期日期不能为空");
        }
        if (dueDate.isBefore(periodStart)) {
            return Result.error("到期日不能早于账期开始日");
        }
        return Result.success();
    }

    private boolean matchesStatus(Bill bill, String status) {
        if (isBlank(status)) {
            return true;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if ("OVERDUE".equals(normalized)) {
            return isOverdue(bill);
        }
        return normalized.equals(bill.getStatus());
    }

    private boolean matchesAuditStatus(Bill bill, String auditStatus) {
        if (isBlank(auditStatus)) {
            return true;
        }
        return auditStatus.trim().toUpperCase(Locale.ROOT).equals(effectiveAuditStatus(bill));
    }

    private boolean matchesKeyword(BillResponse response, String keyword) {
        if (isBlank(keyword)) {
            return true;
        }
        String value = keyword.trim().toLowerCase(Locale.ROOT);
        return contains(response.getBillNumber(), value)
                || contains(response.getTitle(), value)
                || contains(response.getTenantName(), value)
                || contains(response.getContractNumber(), value)
                || contains(response.getBillType(), value)
                || contains(response.getBillTypeText(), value);
    }

    private boolean contains(String source, String keyword) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private boolean isOverdue(Bill bill) {
        return !"PAID".equals(bill.getStatus())
                && !"CANCELLED".equals(bill.getStatus())
                && overdueDays(bill) > 0;
    }

    private Integer overdueDays(Bill bill) {
        if (bill.getDueDate() == null || "CANCELLED".equals(bill.getStatus())) {
            return 0;
        }
        LocalDate referenceDate;
        if ("PAID".equals(bill.getStatus())) {
            referenceDate = bill.getPaidTime() == null
                    ? (bill.getUpdatedTime() == null ? LocalDate.now() : bill.getUpdatedTime().toLocalDate())
                    : bill.getPaidTime().toLocalDate();
        } else {
            referenceDate = LocalDate.now();
        }
        long days = ChronoUnit.DAYS.between(bill.getDueDate(), referenceDate);
        return (int) Math.max(days, 0);
    }

    private BigDecimal unpaidAmount(Bill bill) {
        if ("PAID".equals(bill.getStatus()) || "CANCELLED".equals(bill.getStatus())) {
            return BigDecimal.ZERO;
        }
        return defaultAmount(bill.getAmount()).subtract(defaultAmount(bill.getPaidAmount())).max(BigDecimal.ZERO);
    }

    private BigDecimal lateFee(Bill bill) {
        if ("CANCELLED".equals(bill.getStatus())) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return unpaidAmount(bill)
                .multiply(LATE_FEE_RATE)
                .multiply(BigDecimal.valueOf(overdueDays(bill)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String effectiveAuditStatus(Bill bill) {
        return isBlank(bill.getAuditStatus()) ? "APPROVED" : bill.getAuditStatus();
    }

    private String normalizeBillNumber(String billNumber) {
        if (!isBlank(billNumber)) {
            return billNumber.trim();
        }
        String candidate;
        do {
            candidate = "BILL-" + LocalDate.now().format(BILL_DATE_FORMATTER)
                    + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        } while (billRepository.existsByBillNumber(candidate));
        return candidate;
    }

    private String defaultTitle(String title, String billType, LocalDate periodStart) {
        if (!isBlank(title)) {
            return title.trim();
        }
        String month = periodStart == null ? "" : periodStart.format(DateTimeFormatter.ofPattern("yyyy年MM月")) + " ";
        return month + toBillTypeText(billType) + "账单";
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String defaultInvoiceStatus(Bill bill) {
        return isBlank(bill.getInvoiceStatus()) ? INVOICE_STATUS_UNINVOICED : bill.getInvoiceStatus();
    }

    private String defaultInvoiceFileName(Bill bill) {
        return isBlank(bill.getInvoiceFileName()) ? "invoice-" + bill.getBillNumber() + ".pdf" : bill.getInvoiceFileName();
    }

    private boolean isPdf(MultipartFile file) {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        return filename.endsWith(".pdf") || "application/pdf".equals(contentType);
    }

    private String sanitizeFileName(String filename) {
        if (isBlank(filename)) {
            return "invoice.pdf";
        }
        String normalized = filename.replace("\\", "/");
        return Paths.get(normalized).getFileName().toString();
    }

    private Path invoiceRoot() {
        return Paths.get("uploads", "invoices").toAbsolutePath().normalize();
    }

    private Path resolveInvoicePath(String filePath) {
        if (isBlank(filePath)) {
            return null;
        }
        Path path = Paths.get(filePath).toAbsolutePath().normalize();
        Path root = invoiceRoot();
        return path.startsWith(root) ? path : null;
    }

    private void deleteStoredInvoice(String filePath, Path exceptPath) {
        Path path = resolveInvoicePath(filePath);
        if (path == null || (exceptPath != null && path.equals(exceptPath))) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("删除旧发票文件失败: {}", path, ex);
        }
    }

    private String toBillTypeText(String billType) {
        if ("RENT".equals(billType)) {
            return "租金";
        }
        if ("PROPERTY".equals(billType) || "PROPERTY_FEE".equals(billType)) {
            return "物业费";
        }
        if ("UTILITY".equals(billType)) {
            return "水电煤";
        }
        if ("WATER".equals(billType)) {
            return "水费";
        }
        if ("ELECTRICITY".equals(billType)) {
            return "电费";
        }
        if ("GAS".equals(billType)) {
            return "燃气费";
        }
        if ("PARKING".equals(billType)) {
            return "停车费";
        }
        if ("MEETING".equals(billType) || "MEETING_ROOM".equals(billType)) {
            return "会议室";
        }
        if ("WORK_ORDER".equals(billType) || "REPAIR".equals(billType)) {
            return "维修/工单服务费";
        }
        if ("CLEANING".equals(billType)) {
            return "保洁费";
        }
        if ("DEPOSIT".equals(billType)) {
            return "押金";
        }
        if ("LATE_FEE".equals(billType)) {
            return "滞纳金";
        }
        if ("ADJUSTMENT".equals(billType)) {
            return "调账补差";
        }
        if ("OTHER".equals(billType)) {
            return "其他";
        }
        return isBlank(billType) ? "账单" : billType;
    }

    private String toStatusText(String status) {
        if ("PAID".equals(status)) {
            return "已缴";
        }
        if ("UNPAID".equals(status)) {
            return "待缴";
        }
        if ("OVERDUE".equals(status)) {
            return "已逾期";
        }
        if ("CANCELLED".equals(status)) {
            return "已取消";
        }
        return isBlank(status) ? "未知" : status;
    }

    private String toAuditStatusText(String status) {
        if ("PENDING".equals(status)) {
            return "待审核";
        }
        if ("APPROVED".equals(status)) {
            return "已发布";
        }
        if ("REJECTED".equals(status)) {
            return "已驳回";
        }
        return isBlank(status) ? "已发布" : status;
    }

    private String toInvoiceStatusText(String status) {
        return INVOICE_STATUS_INVOICED.equals(status) ? "已开票" : "未开票";
    }

    private String toSourceTypeText(String sourceType) {
        if ("MANUAL".equals(sourceType)) {
            return "手工账单";
        }
        if ("CONTRACT".equals(sourceType)) {
            return "合同自动出账";
        }
        if ("FEE_RULE".equals(sourceType)) {
            return "周期收费";
        }
        if ("ENERGY".equals(sourceType)) {
            return "能耗抄表";
        }
        if ("PARKING".equals(sourceType)) {
            return "停车账单";
        }
        if ("MEETING_ROOM".equals(sourceType)) {
            return "会议室预约";
        }
        if ("WORK_ORDER".equals(sourceType)) {
            return "工单服务";
        }
        if ("DEV_FIXTURE".equals(sourceType)) {
            return "开发测试";
        }
        return isBlank(sourceType) ? "历史账单" : sourceType;
    }

    private void setCell(Row row, int index, Object value) {
        Cell cell = row.createCell(index);
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof BigDecimal amount) {
            cell.setCellValue(amount.doubleValue());
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private String formatDate(LocalDate date) {
        return date == null ? "" : date.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "" : dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record InvoiceFile(Path path, String fileName) {
    }
}

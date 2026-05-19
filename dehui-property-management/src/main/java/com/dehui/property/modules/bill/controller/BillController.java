package com.dehui.property.modules.bill.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.bill.dto.BillAuditRequest;
import com.dehui.property.modules.bill.dto.BillCreateRequest;
import com.dehui.property.modules.bill.dto.BillResponse;
import com.dehui.property.modules.bill.service.BillService;
import com.dehui.property.modules.system.entity.SysUser;
import com.dehui.property.modules.system.service.SystemUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/bills")
@RequiredArgsConstructor
public class BillController {
    private final BillService billService;
    private final SystemUserService systemUserService;

    @PostMapping
    public Result<BillResponse> create(@Valid @RequestBody BillCreateRequest request) {
        return billService.create(request);
    }

    @GetMapping
    public Result<List<BillResponse>> list(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String auditStatus,
            @RequestParam(required = false) String billType,
            @RequestParam(required = false) String keyword) {
        return billService.findAll(tenantId, status, auditStatus, billType, keyword);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String auditStatus,
            @RequestParam(required = false) String billType,
            @RequestParam(required = false) String keyword) {
        byte[] content = billService.exportExcel(tenantId, status, auditStatus, billType, keyword);
        String filename = "bills-" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }

    @GetMapping("/tenants/{tenantId}/bills")
    public Result<List<BillResponse>> getTenantBills(@PathVariable Long tenantId) {
        return billService.findByTenantId(tenantId);
    }

    @PostMapping("/{id}/pay")
    public Result<BillResponse> pay(@PathVariable Long id) {
        return billService.pay(id);
    }

    @PostMapping("/{id}/invoice")
    public Result<BillResponse> uploadInvoice(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam("file") MultipartFile file) {
        return billService.uploadInvoice(id, file, currentUsername(token));
    }

    @GetMapping("/{id}/invoice/download")
    public ResponseEntity<?> downloadInvoice(@PathVariable Long id) throws IOException {
        Result<BillService.InvoiceFile> result = billService.loadInvoiceFile(id);
        if (result.getCode() != 200) {
            return ResponseEntity.status(404).body(Result.error(404, result.getMessage()));
        }
        return invoiceResponse(result.getData());
    }

    @DeleteMapping("/{id}/invoice")
    public Result<Void> deleteInvoice(@PathVariable Long id) {
        return billService.deleteInvoice(id);
    }

    @PostMapping("/{id}/approve")
    public Result<BillResponse> approve(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody(required = false) BillAuditRequest request) {
        return billService.approve(id, currentUsername(token), request);
    }

    @PostMapping("/{id}/reject")
    public Result<BillResponse> reject(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody(required = false) BillAuditRequest request) {
        return billService.reject(id, currentUsername(token), request);
    }

    @GetMapping("/contracts/{contractId}")
    public Result<List<BillResponse>> getByContract(@PathVariable Long contractId) {
        return billService.findByContractId(contractId);
    }

    @GetMapping("/status/{status}")
    public Result<List<BillResponse>> getByStatus(@PathVariable String status) {
        return billService.findByStatus(status);
    }

    private String currentUsername(String token) {
        String normalized = normalizeToken(token);
        SysUser user = normalized == null ? null : systemUserService.getByToken(normalized);
        if (user == null) {
            return "system";
        }
        return user.getRealName() == null || user.getRealName().isBlank()
                ? user.getUsername()
                : user.getRealName();
    }

    private String normalizeToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        return token;
    }

    private ResponseEntity<byte[]> invoiceResponse(BillService.InvoiceFile invoiceFile) throws IOException {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(invoiceFile.fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(Files.readAllBytes(invoiceFile.path()));
    }
}

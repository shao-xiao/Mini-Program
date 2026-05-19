package com.dehui.property.modules.mobile.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.bill.service.BillService;
import com.dehui.property.modules.mobile.dto.MobileBillListResponse;
import com.dehui.property.modules.mobile.service.MobileBillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@RestController
@RequestMapping("/mobile/bills")
@RequiredArgsConstructor
public class MobileBillController {

    private final MobileBillService mobileBillService;

    @GetMapping
    public Result<MobileBillListResponse> list(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String status) {
        return mobileBillService.list(normalizeToken(token), status);
    }

    @GetMapping("/{id}/invoice/download")
    public ResponseEntity<?> downloadInvoice(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id) throws IOException {
        Result<BillService.InvoiceFile> result = mobileBillService.loadInvoiceFile(normalizeToken(token), id);
        if (result.getCode() != 200) {
            int status = result.getCode() == 403 ? 403 : 404;
            return ResponseEntity.status(status).body(Result.error(status, result.getMessage()));
        }
        BillService.InvoiceFile invoiceFile = result.getData();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(invoiceFile.fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(Files.readAllBytes(invoiceFile.path()));
    }

    private String normalizeToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        return token;
    }
}

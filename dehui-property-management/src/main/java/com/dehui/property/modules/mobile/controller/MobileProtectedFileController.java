package com.dehui.property.modules.mobile.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.modules.mobile.service.MobileStaffFileService;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MobileProtectedFileController {

    private final MobileStaffFileService fileService;

    public MobileProtectedFileController(MobileStaffFileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/mobile/staff/files")
    public ApiResponse<Map<String, Object>> upload(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestParam("file") MultipartFile file
    ) {
        return ApiResponse.success(fileService.uploadDraft(principal, file));
    }

    @PostMapping("/mobile/files")
    public ApiResponse<Map<String, Object>> uploadFieldFile(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "IMAGE") String fieldType
    ) {
        return ApiResponse.success(fileService.uploadDraft(principal, file, fieldType));
    }

    @GetMapping("/mobile/files/{id}")
    public ResponseEntity<org.springframework.core.io.Resource> download(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id
    ) {
        MobileStaffFileService.FileDownload download = fileService.requireDownload(principal, id);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(download.mimeType());
        } catch (RuntimeException ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(download.originalName(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(download.resource());
    }
}

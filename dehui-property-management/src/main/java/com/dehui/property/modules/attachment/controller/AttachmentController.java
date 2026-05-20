package com.dehui.property.modules.attachment.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.attachment.dto.AttachmentResponse;
import com.dehui.property.modules.attachment.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/attachments")
@RequiredArgsConstructor
public class AttachmentController {
    private final AttachmentService attachmentService;

    @GetMapping
    public Result<List<AttachmentResponse>> list(@RequestParam String bizType, @RequestParam Long bizId) {
        return Result.success(attachmentService.list(bizType, bizId));
    }

    @PostMapping
    public Result<AttachmentResponse> upload(
            @RequestParam String bizType,
            @RequestParam Long bizId,
            @RequestParam(defaultValue = "GENERAL") String fileCategory,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return attachmentService.upload(bizType, bizId, fileCategory, normalizeToken(token), file);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        return attachmentService.delete(id);
    }

    private String normalizeToken(String token) {
        if (token == null || token.isBlank()) {
            return "system";
        }
        return token.startsWith("Bearer ") ? token.substring(7) : token;
    }
}

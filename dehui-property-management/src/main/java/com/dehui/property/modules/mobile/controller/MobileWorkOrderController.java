package com.dehui.property.modules.mobile.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.mobile.dto.MobileWorkOrderHomeResponse;
import com.dehui.property.modules.mobile.dto.MobileWorkOrderRequest;
import com.dehui.property.modules.mobile.dto.MobileWorkOrderResponse;
import com.dehui.property.modules.mobile.service.MobileWorkOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/mobile/workorders")
@RequiredArgsConstructor
public class MobileWorkOrderController {

    private final MobileWorkOrderService mobileWorkOrderService;

    @GetMapping
    public Result<MobileWorkOrderHomeResponse> home(@RequestHeader("Authorization") String token) {
        return mobileWorkOrderService.home(normalizeToken(token));
    }

    @PostMapping
    public Result<MobileWorkOrderResponse> create(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody MobileWorkOrderRequest request) {
        return mobileWorkOrderService.create(normalizeToken(token), request);
    }

    @PatchMapping("/{id}/cancel")
    public Result<MobileWorkOrderResponse> cancel(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id) {
        return mobileWorkOrderService.cancel(normalizeToken(token), id);
    }

    @PostMapping("/{id}/images")
    public Result<MobileWorkOrderResponse> uploadImage(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        return mobileWorkOrderService.uploadImage(normalizeToken(token), id, file);
    }

    private String normalizeToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        return token;
    }
}

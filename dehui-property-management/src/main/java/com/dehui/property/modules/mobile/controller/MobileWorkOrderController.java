package com.dehui.property.modules.mobile.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.mobile.dto.MobileWorkOrderHomeResponse;
import com.dehui.property.modules.mobile.dto.MobileWorkOrderEvaluationRequest;
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

    @GetMapping("/{id}")
    public Result<MobileWorkOrderResponse> detail(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id) {
        return mobileWorkOrderService.detail(normalizeToken(token), id);
    }

    @PostMapping
    public Result<MobileWorkOrderResponse> create(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody MobileWorkOrderRequest request) {
        return mobileWorkOrderService.create(normalizeToken(token), request);
    }

    @RequestMapping(value = "/{id}/cancel", method = {RequestMethod.PATCH, RequestMethod.POST})
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

    @RequestMapping(value = "/{id}/confirm", method = {RequestMethod.PATCH, RequestMethod.POST})
    public Result<MobileWorkOrderResponse> confirm(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id) {
        return mobileWorkOrderService.confirm(normalizeToken(token), id);
    }

    @RequestMapping(value = "/{id}/evaluation", method = {RequestMethod.PATCH, RequestMethod.POST})
    public Result<MobileWorkOrderResponse> evaluate(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id,
            @Valid @RequestBody MobileWorkOrderEvaluationRequest request) {
        return mobileWorkOrderService.evaluate(normalizeToken(token), id, request);
    }

    private String normalizeToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        return token;
    }
}

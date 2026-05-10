package com.dehui.property.modules.mobile.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.mobile.dto.MobileWorkOrderHomeResponse;
import com.dehui.property.modules.mobile.dto.MobileWorkOrderRequest;
import com.dehui.property.modules.mobile.dto.MobileWorkOrderResponse;
import com.dehui.property.modules.mobile.service.MobileWorkOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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

    private String normalizeToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        return token;
    }
}

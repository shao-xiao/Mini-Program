package com.dehui.property.modules.mobile.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.mobile.dto.MobileBillListResponse;
import com.dehui.property.modules.mobile.service.MobileBillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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

    private String normalizeToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        return token;
    }
}

package com.dehui.property.modules.mobile.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.energy.dto.EnergyBillResponse;
import com.dehui.property.modules.energy.dto.EnergyReadingResponse;
import com.dehui.property.modules.energy.dto.EnergyStatsResponse;
import com.dehui.property.modules.mobile.service.MobileEnergyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/mini/energy")
@RequiredArgsConstructor
public class MiniEnergyController {
    private final MobileEnergyService mobileEnergyService;

    @GetMapping("/readings")
    public Result<List<EnergyReadingResponse>> readings(@RequestHeader("Authorization") String token,
                                                        @RequestParam(required = false) String meterType,
                                                        @RequestParam(required = false) String periodMonth) {
        return mobileEnergyService.readings(normalizeToken(token), meterType, periodMonth);
    }

    @GetMapping("/bills")
    public Result<List<EnergyBillResponse>> bills(@RequestHeader("Authorization") String token) {
        return mobileEnergyService.bills(normalizeToken(token));
    }

    @GetMapping("/stats")
    public Result<EnergyStatsResponse> stats(@RequestHeader("Authorization") String token,
                                             @RequestParam(required = false) String meterType,
                                             @RequestParam(required = false) String periodMonth) {
        return mobileEnergyService.stats(normalizeToken(token), meterType, periodMonth);
    }

    private String normalizeToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        return token;
    }
}

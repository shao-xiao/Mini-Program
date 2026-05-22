package com.dehui.property.modules.investment.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InvestmentController {

    @GetMapping("/investment/displays")
    public ApiResponse<Void> displays() {
        throw BusinessException.notImplemented("招商展示");
    }

    @GetMapping("/investment/leads")
    public ApiResponse<Void> leads() {
        throw BusinessException.notImplemented("招商线索");
    }

    @GetMapping("/mobile/investment/overview")
    public ApiResponse<Void> mobileOverview() {
        throw BusinessException.notImplemented("移动端招商展示");
    }
}

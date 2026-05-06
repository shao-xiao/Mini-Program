package com.dehui.property.modules.feerule.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.bill.dto.BillResponse;
import com.dehui.property.modules.feerule.dto.FeeRuleCreateRequest;
import com.dehui.property.modules.feerule.dto.FeeRuleResponse;
import com.dehui.property.modules.feerule.service.FeeRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/feerules")
@RequiredArgsConstructor
public class FeeRuleController {

    private final FeeRuleService feeRuleService;

    @GetMapping
    public Result<List<FeeRuleResponse>> findAll() {
        return feeRuleService.findAll();
    }

    @PostMapping
    public Result<FeeRuleResponse> create(@RequestBody FeeRuleCreateRequest request) {
        return feeRuleService.create(request);
    }

    @PostMapping("/{id}/generate-bill")
    public Result<BillResponse> generateBill(@PathVariable Long id) {
        return feeRuleService.generateBill(id);
    }
}

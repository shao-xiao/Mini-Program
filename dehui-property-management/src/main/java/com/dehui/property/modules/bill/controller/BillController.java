package com.dehui.property.modules.bill.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.bill.dto.BillCreateRequest;
import com.dehui.property.modules.bill.dto.BillResponse;
import com.dehui.property.modules.bill.service.BillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/bills")
@RequiredArgsConstructor
public class BillController {
    private final BillService billService;

    @PostMapping
    public Result<BillResponse> create(@Valid @RequestBody BillCreateRequest request) {
        return billService.create(request);
    }

    @GetMapping
    public Result<List<BillResponse>> list() {
        return billService.findAll();
    }

    @GetMapping("/tenants/{tenantId}/bills")
    public Result<List<BillResponse>> getTenantBills(@PathVariable Long tenantId) {
        return billService.findByTenantId(tenantId);
    }

    @PostMapping("/{id}/pay")
    public Result<BillResponse> pay(@PathVariable Long id) {
        return billService.pay(id);
    }

    @GetMapping("/contracts/{contractId}")
    public Result<List<BillResponse>> getByContract(@PathVariable Long contractId) {
        return billService.findByContractId(contractId);
    }

    @GetMapping("/status/{status}")
    public Result<List<BillResponse>> getByStatus(@PathVariable String status) {
        return billService.findByStatus(status);
    }
}
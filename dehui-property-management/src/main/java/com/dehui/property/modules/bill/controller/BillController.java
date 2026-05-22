package com.dehui.property.modules.bill.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BillController {

    @GetMapping("/bills")
    public ApiResponse<Void> bills() {
        throw BusinessException.notImplemented("账单");
    }

    @GetMapping("/payments")
    public ApiResponse<Void> payments() {
        throw BusinessException.notImplemented("支付记录");
    }

    @GetMapping("/invoices")
    public ApiResponse<Void> invoices() {
        throw BusinessException.notImplemented("开票记录");
    }

    @GetMapping("/mobile/bills")
    public ApiResponse<Void> mobileBills() {
        throw BusinessException.notImplemented("移动端账单");
    }
}

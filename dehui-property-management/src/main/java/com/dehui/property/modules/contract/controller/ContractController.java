package com.dehui.property.modules.contract.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ContractController {

    @GetMapping("/contracts")
    public ApiResponse<Void> contracts() {
        throw BusinessException.notImplemented("合同");
    }
}

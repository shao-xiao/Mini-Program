package com.dehui.property.modules.investment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InvestmentLeadResponse {
    private Long id;
    private String status;
    private String message;
}

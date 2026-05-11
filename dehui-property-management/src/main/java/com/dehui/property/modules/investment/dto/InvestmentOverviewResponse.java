package com.dehui.property.modules.investment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class InvestmentOverviewResponse {
    private String title;
    private String subtitle;
    private String address;
    private String contactPhone;
    private List<String> highlights;
    private List<String> policies;
}

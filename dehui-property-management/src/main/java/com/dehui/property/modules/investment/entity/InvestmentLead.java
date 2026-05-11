package com.dehui.property.modules.investment.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "investment_lead")
public class InvestmentLead extends BaseEntity {
    private String name;
    private String phone;
    private String companyName;
    private Double desiredArea;
    private String intendedUse;
    private String preferredVisitTime;
    private Long roomId;
    private String roomNumber;
    private String source;
    private String status;
    private String remark;
}

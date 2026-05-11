package com.dehui.property.modules.contract.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "contract")
public class Contract extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String contractNumber;
    
    private String contractName;
    
    private Long tenantId;
    
    private Long roomId;
    
    private Long leaseId;

    private LocalDate startDate;
    
    private LocalDate endDate;
    
    private BigDecimal rentAmount;

    private BigDecimal propertyFeeAmount;

    private BigDecimal depositAmount;

    private String paymentCycle;

    private Integer billingDay;

    private Integer dueDay;

    private String paymentTerms;

    private String billingRule;
    
    private String status;

    private String remark;
}

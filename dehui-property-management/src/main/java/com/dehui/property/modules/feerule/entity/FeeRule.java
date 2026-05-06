package com.dehui.property.modules.feerule.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "t_fee_rule")
@EqualsAndHashCode(callSuper = true)
public class FeeRule extends BaseEntity {

    private String ruleName;

    private Long tenantId;

    private Long contractId;

    private String feeType;

    private BigDecimal amount;

    private String cycle;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer generateDay;

    private String status;

    private String remark;
}
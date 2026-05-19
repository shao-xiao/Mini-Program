package com.dehui.property.modules.contract.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "contract_events")
public class ContractEvent extends BaseEntity {
    @Column(nullable = false)
    private Long contractId;

    @Column(nullable = false)
    private String action;

    private String beforeStatus;

    private String afterStatus;

    private Long operatorId;

    private String operatorName;

    private String remark;
}

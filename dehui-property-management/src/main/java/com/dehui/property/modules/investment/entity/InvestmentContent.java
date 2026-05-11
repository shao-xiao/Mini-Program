package com.dehui.property.modules.investment.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "investment_content")
public class InvestmentContent extends BaseEntity {
    private String title;
    private String content;
    private String contentType;
    private String status;
    private Integer sortOrder;
    private LocalDateTime publishTime;
}

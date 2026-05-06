package com.dehui.property.modules.visitor.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "visitor_record")
@EqualsAndHashCode(callSuper = true)
public class VisitorRecord extends BaseEntity {

    private String visitorName;

    private String visitorPhone;

    private String idCardNo; // 可选

    private Long tenantId; // 可选

    private String visitedPerson;

    private String visitReason;

    private LocalDateTime visitTime;

    private LocalDateTime leaveTime;

    private String status; // REGISTERED / ENTERED / LEFT / CANCELLED

    private String remark;
}
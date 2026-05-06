package com.dehui.property.modules.announcement.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "announcement")
@EqualsAndHashCode(callSuper = true)
public class Announcement extends BaseEntity {

    private String title;

    private String content;

    private String type; // NOTICE / MAINTENANCE / PAYMENT / EVENT

    private String status; // DRAFT / PUBLISHED / ARCHIVED

    private LocalDateTime publishTime;
}
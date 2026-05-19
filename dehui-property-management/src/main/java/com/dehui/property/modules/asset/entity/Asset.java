package com.dehui.property.modules.asset.entity;

import com.dehui.property.common.BaseEntity;
import com.dehui.property.modules.building.entity.Building;
import com.dehui.property.modules.building.entity.Floor;
import com.dehui.property.modules.building.entity.Room;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "asset")
public class Asset extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String assetCode;

    @Column(nullable = false)
    private String assetName;

    private String assetCategory;

    private String assetType;

    private String manufacturer;

    private String model;

    private String serialNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    private Floor floor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    private String locationDesc;

    @Column(nullable = false)
    private String status;

    private LocalDate installDate;

    private LocalDate warrantyStartDate;

    private LocalDate warrantyEndDate;

    private String responsiblePerson;

    private Integer maintenanceCycleDays;

    private LocalDate lastMaintenanceDate;

    private LocalDate nextMaintenanceDate;

    private String remark;

    private LocalDateTime deletedAt;
}

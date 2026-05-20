package com.dehui.property.modules.parking.service;

import com.dehui.property.modules.parking.entity.ParkingAssignment;
import com.dehui.property.modules.parking.entity.ParkingSpace;
import com.dehui.property.modules.parking.repository.ParkingAssignmentRepository;
import com.dehui.property.modules.parking.repository.ParkingSpaceRepository;
import com.dehui.property.modules.tenant.entity.Tenant;
import com.dehui.property.modules.tenant.repository.TenantRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class ParkingCompatibilityInitializer {
    private static final BigDecimal DEFAULT_MONTHLY_FEE = new BigDecimal("300.00");

    private final ParkingSpaceRepository parkingSpaceRepository;
    private final ParkingAssignmentRepository assignmentRepository;
    private final TenantRepository tenantRepository;

    @PostConstruct
    @Transactional
    public void backfillAssignments() {
        for (ParkingSpace space : parkingSpaceRepository.findAll()) {
            if (!"OCCUPIED".equals(space.getStatus())) {
                continue;
            }
            if (assignmentRepository.findFirstBySpaceIdAndStatus(space.getId(), "active").isPresent()) {
                continue;
            }
            ParkingAssignment assignment = new ParkingAssignment();
            assignment.setSpaceId(space.getId());
            assignment.setPlateNo(blankToDefault(space.getPlateNumber(), "未登记"));
            assignment.setStartDate(space.getCreatedTime() == null ? LocalDate.now() : space.getCreatedTime().toLocalDate());
            assignment.setBillingType("monthly");
            assignment.setMonthlyFee(DEFAULT_MONTHLY_FEE);
            assignment.setStatus("active");
            assignment.setCreatedBy("compatibility");

            if (space.getTenantId() != null) {
                Tenant tenant = tenantRepository.findById(space.getTenantId()).orElse(null);
                if (tenant != null) {
                    assignment.setPartyType("tenant");
                    assignment.setPartyId(tenant.getId());
                    assignment.setPartyNameSnapshot(tenant.getTenantName());
                }
            }

            if (assignment.getPartyType() == null) {
                if ("VIP".equals(space.getSpaceType())) {
                    assignment.setPartyType("vip");
                    assignment.setPartyNameSnapshot("VIP");
                } else {
                    assignment.setPartyType("external");
                    assignment.setPartyNameSnapshot("外部车位使用方");
                }
            }

            assignmentRepository.save(assignment);
            log.info("补齐旧车位绑定关系: spaceId={}, party={}", space.getId(), assignment.getPartyNameSnapshot());
        }
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}

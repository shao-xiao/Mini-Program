package com.dehui.property.modules.tenant.dto;

import com.dehui.property.modules.tenant.entity.Tenant;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class TenantOverviewResponse {
    private Tenant tenant;
    private List<ContactView> contacts = new ArrayList<>();
    private List<ContractView> contracts = new ArrayList<>();
    private List<OccupancyView> occupancies = new ArrayList<>();
    private BillSummary billSummary = new BillSummary();
    private List<BillView> recentBills = new ArrayList<>();
    private MiniProgramVisibility miniProgramVisibility = new MiniProgramVisibility();

    @Data
    public static class ContactView {
        private Long id;
        private String name;
        private String phone;
        private String role;
        private String roleText;
        private Boolean isPrimary;
        private String status;
        private String lastBoundAt;
        private String lastLoginAtText;
        private Boolean boundMiniProgram;
        private Boolean canViewBills;
    }

    @Data
    public static class ContractView {
        private Long id;
        private String contractNumber;
        private String contractName;
        private Long roomId;
        private String roomName;
        private LocalDate startDate;
        private LocalDate endDate;
        private BigDecimal rentAmount;
        private BigDecimal propertyFeeAmount;
        private BigDecimal depositAmount;
        private String status;
        private String statusText;
    }

    @Data
    public static class OccupancyView {
        private Long id;
        private Long contractId;
        private String contractNumber;
        private Long roomId;
        private String roomName;
        private LocalDate checkInDate;
        private LocalDate plannedEndDate;
        private LocalDate checkoutDate;
        private String status;
    }

    @Data
    public static class BillSummary {
        private long pendingAuditCount;
        private long publishedCount;
        private long unpaidCount;
        private long paidCount;
        private long overdueCount;
        private BigDecimal totalAmount = BigDecimal.ZERO;
        private BigDecimal unpaidAmount = BigDecimal.ZERO;
    }

    @Data
    public static class BillView {
        private Long id;
        private String billNumber;
        private String billType;
        private String billTypeText;
        private String title;
        private BigDecimal amount;
        private BigDecimal paidAmount;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private LocalDate dueDate;
        private String status;
        private String statusText;
        private String auditStatus;
        private String auditStatusText;
        private Boolean visibleToTenantMiniProgram;
    }

    @Data
    public static class MiniProgramVisibility {
        private Boolean hasActiveContact;
        private Integer boundContactCount;
        private Integer visibleContactCount;
        private List<ContactView> visibleContacts = new ArrayList<>();
        private String message;
    }
}

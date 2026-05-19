package com.dehui.property.modules.tenant.service;

import com.dehui.property.common.Result;
import com.dehui.property.modules.bill.entity.Bill;
import com.dehui.property.modules.bill.repository.BillRepository;
import com.dehui.property.modules.building.entity.Room;
import com.dehui.property.modules.building.repository.RoomRepository;
import com.dehui.property.modules.contract.entity.Contract;
import com.dehui.property.modules.contract.repository.ContractRepository;
import com.dehui.property.modules.lease.entity.Occupancy;
import com.dehui.property.modules.lease.repository.OccupancyRepository;
import com.dehui.property.modules.mobile.entity.TenantContact;
import com.dehui.property.modules.mobile.repository.TenantContactRepository;
import com.dehui.property.modules.mobile.repository.WechatUserRepository;
import com.dehui.property.modules.tenant.dto.TenantOverviewResponse;
import com.dehui.property.modules.tenant.entity.Tenant;
import com.dehui.property.modules.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantService {
    private final TenantRepository tenantRepository;
    private final TenantContactRepository tenantContactRepository;
    private final WechatUserRepository wechatUserRepository;
    private final ContractRepository contractRepository;
    private final OccupancyRepository occupancyRepository;
    private final BillRepository billRepository;
    private final RoomRepository roomRepository;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public List<Tenant> findAll() {
        return tenantRepository.findAll();
    }

    public Tenant save(Tenant tenant) {
        return tenantRepository.save(tenant);
    }

    public Result<TenantOverviewResponse> overview(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            return Result.error("租户不存在");
        }

        TenantOverviewResponse response = new TenantOverviewResponse();
        response.setTenant(tenant);

        List<TenantOverviewResponse.ContactView> contacts = tenantContactRepository.findByTenantIdOrderByIsPrimaryDescIdAsc(tenantId)
                .stream()
                .map(contact -> toContactView(tenantId, contact))
                .toList();
        response.setContacts(contacts);

        List<Contract> contracts = contractRepository.findByTenantIdOrderByCreatedTimeDesc(tenantId);
        response.setContracts(contracts.stream().map(this::toContractView).toList());

        response.setOccupancies(occupancyRepository.findByTenantIdOrderByCheckInDateDesc(tenantId)
                .stream()
                .map(occupancy -> toOccupancyView(occupancy, contracts))
                .toList());

        List<Bill> bills = billRepository.findByTenantIdOrderByCreatedTimeDesc(tenantId);
        response.setBillSummary(toBillSummary(bills));
        response.setRecentBills(bills.stream()
                .sorted(Comparator.comparing(Bill::getCreatedTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(20)
                .map(bill -> toBillView(bill, contacts))
                .toList());
        response.setMiniProgramVisibility(toMiniProgramVisibility(contacts));
        return Result.success(response);
    }

    private TenantOverviewResponse.ContactView toContactView(Long tenantId, TenantContact contact) {
        TenantOverviewResponse.ContactView view = new TenantOverviewResponse.ContactView();
        view.setId(contact.getId());
        view.setName(contact.getName());
        view.setPhone(contact.getPhone());
        view.setRole(contact.getRole());
        view.setRoleText(roleText(contact.getRole()));
        view.setIsPrimary(contact.getIsPrimary());
        view.setStatus(contact.getStatus());
        view.setLastBoundAt(contact.getLastBindTime() == null ? null : contact.getLastBindTime().format(DATE_TIME_FORMATTER));
        view.setLastLoginAtText(contact.getLastLoginAt() == null ? null : contact.getLastLoginAt().format(DATE_TIME_FORMATTER));
        boolean bound = contact.getPhone() != null
                && wechatUserRepository.findByBoundTenantIdAndPhone(tenantId, contact.getPhone()).isPresent();
        view.setBoundMiniProgram(bound);
        view.setCanViewBills(bound && "ACTIVE".equals(contact.getStatus()));
        return view;
    }

    private TenantOverviewResponse.ContractView toContractView(Contract contract) {
        TenantOverviewResponse.ContractView view = new TenantOverviewResponse.ContractView();
        view.setId(contract.getId());
        view.setContractNumber(contract.getContractNumber());
        view.setContractName(contract.getContractName());
        view.setRoomId(contract.getRoomId());
        view.setRoomName(roomName(contract.getRoomId()));
        view.setStartDate(contract.getStartDate());
        view.setEndDate(contract.getEndDate());
        view.setRentAmount(defaultAmount(contract.getRentAmount()));
        view.setPropertyFeeAmount(defaultAmount(contract.getPropertyFeeAmount()));
        view.setDepositAmount(defaultAmount(contract.getDepositAmount()));
        view.setStatus(contract.getStatus());
        view.setStatusText(contractStatusText(contract.getStatus()));
        return view;
    }

    private TenantOverviewResponse.OccupancyView toOccupancyView(Occupancy occupancy, List<Contract> contracts) {
        TenantOverviewResponse.OccupancyView view = new TenantOverviewResponse.OccupancyView();
        view.setId(occupancy.getId());
        view.setContractId(occupancy.getContractId());
        contracts.stream()
                .filter(contract -> contract.getId().equals(occupancy.getContractId()))
                .findFirst()
                .ifPresent(contract -> view.setContractNumber(contract.getContractNumber()));
        view.setRoomId(occupancy.getRoomId());
        view.setRoomName(roomName(occupancy.getRoomId()));
        view.setCheckInDate(occupancy.getCheckInDate());
        view.setPlannedEndDate(occupancy.getPlannedEndDate());
        view.setCheckoutDate(occupancy.getCheckoutDate());
        view.setStatus(occupancy.getStatus());
        return view;
    }

    private TenantOverviewResponse.BillSummary toBillSummary(List<Bill> bills) {
        TenantOverviewResponse.BillSummary summary = new TenantOverviewResponse.BillSummary();
        LocalDate today = LocalDate.now();
        for (Bill bill : bills) {
            BigDecimal amount = defaultAmount(bill.getAmount());
            BigDecimal paid = defaultAmount(bill.getPaidAmount());
            summary.setTotalAmount(summary.getTotalAmount().add(amount));
            if ("PENDING".equals(effectiveAuditStatus(bill))) {
                summary.setPendingAuditCount(summary.getPendingAuditCount() + 1);
            }
            if ("APPROVED".equals(effectiveAuditStatus(bill))) {
                summary.setPublishedCount(summary.getPublishedCount() + 1);
            }
            if ("UNPAID".equals(bill.getStatus())) {
                summary.setUnpaidCount(summary.getUnpaidCount() + 1);
                summary.setUnpaidAmount(summary.getUnpaidAmount().add(amount.subtract(paid).max(BigDecimal.ZERO)));
            }
            if ("PAID".equals(bill.getStatus())) {
                summary.setPaidCount(summary.getPaidCount() + 1);
            }
            if (!"PAID".equals(bill.getStatus()) && !"CANCELLED".equals(bill.getStatus())
                    && bill.getDueDate() != null && bill.getDueDate().isBefore(today)) {
                summary.setOverdueCount(summary.getOverdueCount() + 1);
            }
        }
        return summary;
    }

    private TenantOverviewResponse.BillView toBillView(Bill bill, List<TenantOverviewResponse.ContactView> contacts) {
        TenantOverviewResponse.BillView view = new TenantOverviewResponse.BillView();
        view.setId(bill.getId());
        view.setBillNumber(bill.getBillNumber());
        view.setBillType(bill.getBillType());
        view.setBillTypeText(billTypeText(bill.getBillType()));
        view.setTitle(bill.getTitle());
        view.setAmount(defaultAmount(bill.getAmount()));
        view.setPaidAmount(defaultAmount(bill.getPaidAmount()));
        view.setPeriodStart(bill.getPeriodStart());
        view.setPeriodEnd(bill.getPeriodEnd());
        view.setDueDate(bill.getDueDate());
        view.setStatus(bill.getStatus());
        view.setStatusText(billStatusText(bill.getStatus()));
        view.setAuditStatus(effectiveAuditStatus(bill));
        view.setAuditStatusText(auditStatusText(view.getAuditStatus()));
        view.setVisibleToTenantMiniProgram("APPROVED".equals(view.getAuditStatus())
                && contacts.stream().anyMatch(TenantOverviewResponse.ContactView::getCanViewBills));
        return view;
    }

    private TenantOverviewResponse.MiniProgramVisibility toMiniProgramVisibility(List<TenantOverviewResponse.ContactView> contacts) {
        TenantOverviewResponse.MiniProgramVisibility visibility = new TenantOverviewResponse.MiniProgramVisibility();
        List<TenantOverviewResponse.ContactView> activeContacts = contacts.stream()
                .filter(contact -> "ACTIVE".equals(contact.getStatus()))
                .toList();
        List<TenantOverviewResponse.ContactView> visibleContacts = contacts.stream()
                .filter(TenantOverviewResponse.ContactView::getCanViewBills)
                .toList();
        visibility.setHasActiveContact(!activeContacts.isEmpty());
        visibility.setBoundContactCount((int) contacts.stream().filter(TenantOverviewResponse.ContactView::getBoundMiniProgram).count());
        visibility.setVisibleContactCount(visibleContacts.size());
        visibility.setVisibleContacts(visibleContacts);
        if (activeContacts.isEmpty()) {
            visibility.setMessage("暂无有效联系人；请先维护租户联系人账号。");
        } else if (visibleContacts.isEmpty()) {
            visibility.setMessage("账单可发布，但暂无已绑定小程序联系人；请让联系人使用手机号和初始密码绑定。");
        } else {
            visibility.setMessage("已发布账单可由已绑定联系人在小程序查看。");
        }
        return visibility;
    }

    private String roomName(Long roomId) {
        if (roomId == null) {
            return "-";
        }
        return roomRepository.findById(roomId)
                .map(this::formatRoom)
                .orElse("房间ID:" + roomId);
    }

    private String formatRoom(Room room) {
        if (room.getRoomName() != null && !room.getRoomName().isBlank()) {
            return room.getRoomName();
        }
        return room.getRoomNumber() == null ? "房间ID:" + room.getId() : room.getRoomNumber();
    }

    private String effectiveAuditStatus(Bill bill) {
        return bill.getAuditStatus() == null || bill.getAuditStatus().isBlank() ? "APPROVED" : bill.getAuditStatus();
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String roleText(String role) {
        return switch (role == null ? "" : role) {
            case "OWNER_CONTACT" -> "主联系人";
            case "FINANCE_CONTACT" -> "财务联系人";
            case "ADMIN_CONTACT" -> "行政联系人";
            case "MAINTAIN_CONTACT" -> "报修联系人";
            default -> role;
        };
    }

    private String contractStatusText(String status) {
        return switch (status == null ? "" : status) {
            case "DRAFT" -> "草稿";
            case "PENDING" -> "待生效";
            case "ACTIVE" -> "履约中";
            case "EXPIRED" -> "已到期";
            case "TERMINATED" -> "已终止";
            case "CANCELLED" -> "已作废";
            default -> status;
        };
    }

    private String billTypeText(String type) {
        return switch (type == null ? "" : type) {
            case "RENT" -> "租金";
            case "PROPERTY", "PROPERTY_FEE" -> "物业费";
            case "DEPOSIT" -> "押金";
            case "WATER" -> "水费";
            case "ELECTRICITY" -> "电费";
            case "GAS" -> "燃气费";
            case "PARKING" -> "停车费";
            default -> type;
        };
    }

    private String billStatusText(String status) {
        return switch (status == null ? "" : status) {
            case "UNPAID" -> "待缴";
            case "PAID" -> "已缴";
            case "OVERDUE" -> "已逾期";
            case "CANCELLED" -> "已取消";
            default -> status;
        };
    }

    private String auditStatusText(String status) {
        return switch (status == null ? "" : status) {
            case "PENDING" -> "待审核";
            case "APPROVED" -> "已发布";
            case "REJECTED" -> "已驳回";
            default -> status == null || status.isBlank() ? "已发布" : status;
        };
    }
}

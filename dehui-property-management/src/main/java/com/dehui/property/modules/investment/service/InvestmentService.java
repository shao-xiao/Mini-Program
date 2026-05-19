package com.dehui.property.modules.investment.service;

import com.dehui.property.modules.building.entity.Building;
import com.dehui.property.modules.building.entity.Floor;
import com.dehui.property.modules.building.entity.Room;
import com.dehui.property.modules.building.repository.RoomRepository;
import com.dehui.property.modules.contract.dto.ContractCreateRequest;
import com.dehui.property.modules.contract.dto.ContractResponse;
import com.dehui.property.modules.contract.service.ContractService;
import com.dehui.property.modules.investment.dto.InvestmentLeadRequest;
import com.dehui.property.modules.investment.dto.InvestmentLeadAdminResponse;
import com.dehui.property.modules.investment.dto.InvestmentLeadConvertContractRequest;
import com.dehui.property.modules.investment.dto.InvestmentLeadResponse;
import com.dehui.property.modules.investment.dto.InvestmentOverviewResponse;
import com.dehui.property.modules.investment.dto.InvestmentRoomResponse;
import com.dehui.property.modules.investment.entity.InvestmentLead;
import com.dehui.property.modules.investment.entity.InvestmentContent;
import com.dehui.property.modules.investment.repository.InvestmentContentRepository;
import com.dehui.property.modules.investment.repository.InvestmentLeadRepository;
import com.dehui.property.modules.tenant.entity.Tenant;
import com.dehui.property.modules.tenant.repository.TenantRepository;
import com.dehui.property.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class InvestmentService {

    private final RoomRepository roomRepository;
    private final InvestmentContentRepository investmentContentRepository;
    private final InvestmentLeadRepository investmentLeadRepository;
    private final TenantRepository tenantRepository;
    private final ContractService contractService;

    public InvestmentOverviewResponse overview() {
        List<InvestmentContent> contents = investmentContentRepository.findByStatusOrderBySortOrderAscPublishTimeDesc("PUBLISHED");
        if (!contents.isEmpty()) {
            String title = firstContent(contents, "OVERVIEW", "德汇创新中心");
            String subtitle = firstContent(contents, "INTRO", "面向科技创新、研发办公、企业总部和成长型团队的综合办公空间。");
            String address = firstContent(contents, "ADDRESS", "德汇创新中心");
            String contactPhone = firstContent(contents, "CONTACT", "400-000-0000");
            List<String> highlights = contentList(contents, "HIGHLIGHT");
            List<String> policies = contentList(contents, "POLICY");
            return new InvestmentOverviewResponse(
                    title,
                    subtitle,
                    address,
                    contactPhone,
                    highlights.isEmpty() ? defaultHighlights() : highlights,
                    policies.isEmpty() ? defaultPolicies() : policies
            );
        }

        return new InvestmentOverviewResponse(
                "德汇创新中心",
                "面向科技创新、研发办公、企业总部和成长型团队的综合办公空间。",
                "德汇创新中心",
                "400-000-0000",
                defaultHighlights(),
                defaultPolicies()
        );
    }

    public InvestmentContent createContent(InvestmentContent content) {
        if (content.getStatus() == null || content.getStatus().isBlank()) {
            content.setStatus("DRAFT");
        }
        if (content.getSortOrder() == null) {
            content.setSortOrder(100);
        }
        return investmentContentRepository.save(content);
    }

    public List<InvestmentContent> listContents() {
        return investmentContentRepository.findAll()
                .stream()
                .sorted(Comparator
                        .comparing(InvestmentContent::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(InvestmentContent::getCreatedTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public InvestmentContent publishContent(Long id) {
        InvestmentContent content = investmentContentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("招商内容不存在"));
        content.setStatus("PUBLISHED");
        content.setPublishTime(LocalDateTime.now());
        return investmentContentRepository.save(content);
    }

    @Transactional(readOnly = true)
    public List<InvestmentRoomResponse> availableRooms() {
        return roomRepository.findAll()
                .stream()
                .filter(room -> "AVAILABLE".equals(room.getStatus()))
                .sorted(Comparator
                        .comparing((Room room) -> room.getFloor().getBuilding().getBuildingName(), Comparator.nullsLast(String::compareTo))
                        .thenComparing(room -> room.getFloor().getFloorNumber(), Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(Room::getRoomNumber, Comparator.nullsLast(String::compareTo)))
                .map(this::toRoomResponse)
                .toList();
    }

    @Transactional
    public InvestmentLeadResponse createLead(InvestmentLeadRequest request) {
        InvestmentLead lead = new InvestmentLead();
        lead.setName(request.getName());
        lead.setPhone(request.getPhone());
        lead.setCompanyName(request.getCompanyName());
        lead.setDesiredArea(request.getDesiredArea());
        lead.setIntendedUse(request.getIntendedUse());
        lead.setPreferredVisitTime(request.getPreferredVisitTime());
        lead.setRoomId(request.getRoomId());
        lead.setSource("MINIPROGRAM");
        lead.setStatus("NEW");
        lead.setRemark(request.getRemark());

        if (request.getRoomId() != null) {
            roomRepository.findById(request.getRoomId()).ifPresent(room -> lead.setRoomNumber(room.getRoomNumber()));
        }

        InvestmentLead saved = investmentLeadRepository.save(lead);
        return new InvestmentLeadResponse(saved.getId(), saved.getStatus(), "招商顾问会尽快与您联系");
    }

    @Transactional(readOnly = true)
    public List<InvestmentLeadAdminResponse> listLeads() {
        return investmentLeadRepository.findAllByOrderByCreatedTimeDesc()
                .stream()
                .map(this::toLeadResponse)
                .toList();
    }

    @Transactional
    public InvestmentLeadAdminResponse updateLeadStatus(Long id, String status) {
        InvestmentLead lead = investmentLeadRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("招商线索不存在"));
        lead.setStatus(normalizeLeadStatus(status));
        return toLeadResponse(investmentLeadRepository.save(lead));
    }

    @Transactional
    public Tenant convertToTenant(Long id) {
        InvestmentLead lead = investmentLeadRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("招商线索不存在"));
        String tenantName = lead.getCompanyName() == null || lead.getCompanyName().isBlank()
                ? lead.getName()
                : lead.getCompanyName();
        if (tenantName == null || tenantName.isBlank()) {
            throw new IllegalArgumentException("线索缺少租户名称");
        }
        Tenant tenant = tenantRepository.findFirstByTenantName(tenantName.trim())
                .orElseGet(() -> {
                    Tenant created = new Tenant();
                    created.setTenantName(tenantName.trim());
                    created.setContactPerson(lead.getName());
                    created.setContactPhone(lead.getPhone());
                    created.setStatus("ACTIVE");
                    return tenantRepository.save(created);
                });
        lead.setStatus("CONVERTED");
        investmentLeadRepository.save(lead);
        return tenant;
    }

    @Transactional
    public Result<ContractResponse> convertToContract(Long id, InvestmentLeadConvertContractRequest request) {
        InvestmentLead lead = investmentLeadRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("招商线索不存在"));
        if (lead.getRoomId() == null) {
            return Result.error("线索缺少意向房间，无法转合同");
        }
        String tenantName = lead.getCompanyName() == null || lead.getCompanyName().isBlank()
                ? lead.getName()
                : lead.getCompanyName();
        if (tenantName == null || tenantName.isBlank()) {
            return Result.error("线索缺少租户名称");
        }

        InvestmentLeadConvertContractRequest safeRequest = request == null ? new InvestmentLeadConvertContractRequest() : request;
        LocalDate startDate = safeRequest.getStartDate() == null ? LocalDate.now() : safeRequest.getStartDate();
        ContractCreateRequest contractRequest = new ContractCreateRequest();
        contractRequest.setContractNumber(defaultText(safeRequest.getContractNumber(), "ZL-LEAD-" + lead.getId() + "-" + System.currentTimeMillis()));
        contractRequest.setContractName(defaultText(safeRequest.getContractName(), tenantName + "租赁合同"));
        contractRequest.setTenantName(tenantName);
        contractRequest.setContactPerson(lead.getName());
        contractRequest.setContactPhone(lead.getPhone());
        contractRequest.setRoomId(lead.getRoomId());
        contractRequest.setStartDate(startDate);
        contractRequest.setEndDate(safeRequest.getEndDate() == null ? startDate.plusYears(1).minusDays(1) : safeRequest.getEndDate());
        contractRequest.setRentAmount(safeRequest.getRentAmount() == null ? java.math.BigDecimal.ZERO : safeRequest.getRentAmount());
        contractRequest.setPropertyFeeAmount(safeRequest.getPropertyFeeAmount() == null ? java.math.BigDecimal.ZERO : safeRequest.getPropertyFeeAmount());
        contractRequest.setDepositAmount(safeRequest.getDepositAmount() == null ? java.math.BigDecimal.ZERO : safeRequest.getDepositAmount());
        contractRequest.setPaymentCycle(defaultText(safeRequest.getPaymentCycle(), "MONTHLY"));
        contractRequest.setAdvanceBillDays(safeRequest.getAdvanceBillDays() == null ? 7 : safeRequest.getAdvanceBillDays());
        contractRequest.setRemark(defaultText(safeRequest.getRemark(), buildLeadRemark(lead)));

        Result<ContractResponse> result = contractService.create(contractRequest);
        if (result.getCode() == 200) {
            lead.setStatus("CONVERTED");
            investmentLeadRepository.save(lead);
        }
        return result;
    }

    private InvestmentRoomResponse toRoomResponse(Room room) {
        Floor floor = room.getFloor();
        Building building = floor == null ? null : floor.getBuilding();
        return new InvestmentRoomResponse(
                room.getId(),
                room.getRoomNumber(),
                room.getArea(),
                room.getRoomType(),
                building == null ? "" : building.getBuildingName(),
                floor == null ? null : floor.getFloorNumber(),
                floor == null ? "" : floor.getFloorName(),
                "可租"
        );
    }

    private InvestmentLeadAdminResponse toLeadResponse(InvestmentLead lead) {
        return new InvestmentLeadAdminResponse(
                lead.getId(),
                lead.getName(),
                lead.getPhone(),
                lead.getCompanyName(),
                lead.getDesiredArea(),
                lead.getIntendedUse(),
                lead.getPreferredVisitTime(),
                lead.getRoomId(),
                lead.getRoomNumber(),
                lead.getSource(),
                lead.getStatus(),
                lead.getRemark(),
                lead.getCreatedTime(),
                lead.getUpdatedTime()
        );
    }

    private String firstContent(List<InvestmentContent> contents, String type, String fallback) {
        return contents.stream()
                .filter(item -> type.equals(item.getContentType()))
                .map(InvestmentContent::getContent)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(fallback);
    }

    private List<String> contentList(List<InvestmentContent> contents, String type) {
        return contents.stream()
                .filter(item -> type.equals(item.getContentType()))
                .map(item -> item.getTitle() != null && !item.getTitle().isBlank() ? item.getTitle() : item.getContent())
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private List<String> defaultHighlights() {
        return List.of("多面积段可选", "成熟物业服务", "会议室与停车配套", "适合研发办公与企业总部");
    }

    private List<String> defaultPolicies() {
        return List.of("可预约看房", "租赁方案一企一议", "重点企业可洽谈优惠政策");
    }

    private String normalizeLeadStatus(String status) {
        if (status == null) {
            return "NEW";
        }
        return switch (status) {
            case "NEW", "FOLLOWING", "VIEWED", "NEGOTIATING", "CONVERTED", "INVALID" -> status;
            case "CONTACTED" -> "FOLLOWING";
            case "CLOSED" -> "INVALID";
            default -> status;
        };
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String buildLeadRemark(InvestmentLead lead) {
        StringBuilder builder = new StringBuilder("由招商线索转合同");
        if (lead.getDesiredArea() != null) {
            builder.append("；意向面积：").append(lead.getDesiredArea()).append("㎡");
        }
        if (lead.getIntendedUse() != null && !lead.getIntendedUse().isBlank()) {
            builder.append("；用途：").append(lead.getIntendedUse());
        }
        if (lead.getRemark() != null && !lead.getRemark().isBlank()) {
            builder.append("；备注：").append(lead.getRemark());
        }
        return builder.toString();
    }
}

package com.dehui.property.modules.tenant.dto;

import com.dehui.property.common.PageResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class TenantArchiveDtos {

    private TenantArchiveDtos() {
    }

    public record DirectoryItem(
            Long id,
            String tenantCode,
            String tenantName,
            String companyShortName,
            String unifiedCreditCode,
            String contactName,
            String phone,
            String email,
            String status,
            long activeContractCount,
            long roomCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record PersonSummary(
            Long id,
            String name,
            String phone
    ) {
    }

    public record Overview(
            Long id,
            String tenantCode,
            String companyName,
            String companyShortName,
            String unifiedCreditCode,
            String invoiceTitle,
            String taxNumber,
            Long logoFileId,
            String logoFileName,
            String logoUrl,
            String status,
            String buildingSummary,
            String floorSummary,
            String roomSummary,
            BigDecimal totalArea,
            long activeContractCount,
            long activeRoomCount,
            long activeBuildingCount,
            LocalDate activeLeaseStartDate,
            LocalDate activeLeaseEndDate,
            PersonSummary serviceManager,
            PersonSummary emergencyContact,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record ContactItem(
            Long id,
            Long tenantId,
            String name,
            String phone,
            String email,
            String position,
            String role,
            boolean primary,
            boolean emergency,
            String status,
            String bindingStatus,
            long boundMobileUserCount,
            LocalDateTime lastLoginAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record RoomSpaceItem(
            String rowKey,
            Long roomId,
            String buildingName,
            String floorName,
            String roomNumber,
            String roomType,
            BigDecimal area,
            Long contractId,
            String contractNumber,
            LocalDate contractStartDate,
            LocalDate contractEndDate,
            String contractStatus,
            String occupancyStatus,
            boolean current
    ) {
    }

    public record ParkingSpaceItem(
            String rowKey,
            Long assignmentId,
            Long parkingSpaceId,
            String spaceNumber,
            String area,
            String spaceType,
            LocalDate startDate,
            LocalDate endDate,
            String plateNumber,
            String boundContactName,
            String status,
            boolean current,
            String parkingBillUrl
    ) {
    }

    public record Spaces(
            PageResponse<RoomSpaceItem> rooms,
            PageResponse<ParkingSpaceItem> parkingSpaces,
            long currentRoomCount,
            long historicalRoomCount,
            long currentParkingCount,
            long historicalParkingCount
    ) {
    }

    public record ContractAttachmentItem(
            Long attachmentId,
            Long fileId,
            String attachmentType,
            String fileName,
            String mimeType,
            long fileSize,
            String status,
            LocalDateTime uploadedAt,
            String downloadUrl
    ) {
    }

    public record ContractItem(
            Long id,
            String contractNumber,
            String contractType,
            String tenantName,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            List<Long> roomIds,
            List<String> roomNames,
            String roomSummary,
            BigDecimal totalArea,
            BigDecimal rentAmount,
            BigDecimal depositAmount,
            boolean expiringSoon,
            List<ContractAttachmentItem> attachments,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            long version
    ) {
    }

    public record BillItem(
            Long id,
            String billNumber,
            String billingPeriod,
            String feeTypeSummary,
            BigDecimal calculatedAmount,
            BigDecimal adjustmentAmount,
            BigDecimal finalAmount,
            BigDecimal paidAmount,
            BigDecimal unpaidAmount,
            String workflowStatus,
            String payStatus,
            LocalDate dueDate,
            String invoiceStatus,
            Long contractId,
            String contractNumber,
            boolean contractLinked,
            String contractLinkLabel
    ) {
    }

    public record ServiceManagerItem(
            Long id,
            String name,
            String phone,
            String email
    ) {
    }
}

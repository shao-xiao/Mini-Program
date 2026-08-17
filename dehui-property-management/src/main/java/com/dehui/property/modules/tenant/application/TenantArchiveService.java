package com.dehui.property.modules.tenant.application;

import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcPagination;
import com.dehui.property.common.PageResponse;
import com.dehui.property.modules.tenant.dto.TenantArchiveDtos.BillItem;
import com.dehui.property.modules.tenant.dto.TenantArchiveDtos.ContactItem;
import com.dehui.property.modules.tenant.dto.TenantArchiveDtos.ContractAttachmentItem;
import com.dehui.property.modules.tenant.dto.TenantArchiveDtos.ContractItem;
import com.dehui.property.modules.tenant.dto.TenantArchiveDtos.DirectoryItem;
import com.dehui.property.modules.tenant.dto.TenantArchiveDtos.Overview;
import com.dehui.property.modules.tenant.dto.TenantArchiveDtos.ParkingSpaceItem;
import com.dehui.property.modules.tenant.dto.TenantArchiveDtos.PersonSummary;
import com.dehui.property.modules.tenant.dto.TenantArchiveDtos.RoomSpaceItem;
import com.dehui.property.modules.tenant.dto.TenantArchiveDtos.ServiceManagerItem;
import com.dehui.property.modules.tenant.dto.TenantArchiveDtos.Spaces;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantArchiveService {

    private static final String ACTIVE_CONTRACT = """
            contract_record.status = 'ACTIVE'
            AND contract_record.start_date <= CURRENT_DATE
            AND contract_record.end_date >= CURRENT_DATE
            """;

    private final JdbcTemplate jdbcTemplate;

    public PageResponse<DirectoryItem> directory(
            String keyword,
            String status,
            Integer page,
            Integer pageSize
    ) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE tenant_record.deleted = 0");
        if (keyword != null && !keyword.isBlank()) {
            String value = "%" + keyword.trim() + "%";
            where.append("""
                     AND (
                        tenant_record.name LIKE ?
                        OR tenant_record.short_name LIKE ?
                        OR tenant_record.code LIKE ?
                        OR tenant_record.credit_code LIKE ?
                        OR EXISTS (
                            SELECT 1
                            FROM tenant_contact contact_record
                            WHERE contact_record.tenant_id = tenant_record.id
                              AND contact_record.deleted = 0
                              AND (
                                  contact_record.name LIKE ?
                                  OR contact_record.phone LIKE ?
                              )
                        )
                     )
                    """);
            args.addAll(List.of(value, value, value, value, value, value));
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND tenant_record.status = ?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }

        return JdbcPagination.query(
                jdbcTemplate,
                """
                SELECT tenant_record.id,
                       tenant_record.code AS tenant_code,
                       tenant_record.name AS tenant_name,
                       tenant_record.short_name,
                       tenant_record.credit_code,
                       COALESCE(primary_contact.name, tenant_record.contact_person) AS contact_name,
                       COALESCE(primary_contact.phone, tenant_record.contact_phone) AS contact_phone,
                       COALESCE(primary_contact.email, tenant_record.contact_email) AS contact_email,
                       tenant_record.status,
                       COALESCE(contract_summary.active_contract_count, 0) AS active_contract_count,
                       COALESCE(contract_summary.room_count, 0) AS room_count,
                       tenant_record.created_at,
                       tenant_record.updated_at
                FROM tenant tenant_record
                LEFT JOIN tenant_contact primary_contact
                  ON primary_contact.active_responsible_tenant_id = tenant_record.id
                LEFT JOIN (
                    SELECT contract_record.tenant_id,
                           COUNT(DISTINCT contract_record.id) AS active_contract_count,
                           COUNT(DISTINCT room_record.id) AS room_count
                    FROM contract contract_record
                    LEFT JOIN (
                        SELECT contract_id, room_id
                        FROM contract_room
                        WHERE deleted = 0
                        UNION
                        SELECT id AS contract_id, room_id
                        FROM contract
                        WHERE deleted = 0
                    ) room_link
                      ON room_link.contract_id = contract_record.id
                    LEFT JOIN building_room room_record
                      ON room_record.id = room_link.room_id
                     AND room_record.deleted = 0
                    WHERE contract_record.deleted = 0
                      AND contract_record.status = 'ACTIVE'
                      AND contract_record.start_date <= CURRENT_DATE
                      AND contract_record.end_date >= CURRENT_DATE
                    GROUP BY contract_record.tenant_id
                ) contract_summary
                  ON contract_summary.tenant_id = tenant_record.id
                """ + where + "\n" + """
                ORDER BY tenant_record.updated_at DESC, tenant_record.id DESC
                """,
                "SELECT COUNT(*) FROM tenant tenant_record" + where,
                args,
                page,
                pageSize,
                (rs, rowNum) -> new DirectoryItem(
                        rs.getLong("id"),
                        rs.getString("tenant_code"),
                        rs.getString("tenant_name"),
                        rs.getString("short_name"),
                        rs.getString("credit_code"),
                        rs.getString("contact_name"),
                        rs.getString("contact_phone"),
                        rs.getString("contact_email"),
                        rs.getString("status"),
                        rs.getLong("active_contract_count"),
                        rs.getLong("room_count"),
                        dateTime(rs, "created_at"),
                        dateTime(rs, "updated_at")
                )
        );
    }

    public Overview overview(Long tenantId) {
        ensureTenantExists(tenantId);
        Map<String, Object> tenant = jdbcTemplate.queryForMap(
                """
                SELECT tenant_record.id,
                       tenant_record.code,
                       tenant_record.name,
                       tenant_record.short_name,
                       tenant_record.credit_code,
                       COALESCE(NULLIF(tenant_record.invoice_title, ''), tenant_record.name) AS invoice_title,
                       COALESCE(NULLIF(tenant_record.tax_number, ''), tenant_record.credit_code) AS tax_number,
                       logo_file.id AS logo_file_id,
                       logo_file.original_name AS logo_file_name,
                       tenant_record.status,
                       tenant_record.created_at,
                       tenant_record.updated_at,
                       manager.id AS manager_id,
                       manager.real_name AS manager_name,
                       manager.phone AS manager_phone,
                       emergency.id AS emergency_id,
                       emergency.name AS emergency_name,
                       emergency.phone AS emergency_phone
                FROM tenant tenant_record
                LEFT JOIN sys_user manager
                  ON manager.id = tenant_record.service_manager_user_id
                 AND manager.deleted = 0
                 AND manager.status IN ('ACTIVE', 'ENABLED')
                LEFT JOIN file_upload logo_file
                  ON logo_file.id = tenant_record.logo_file_id
                 AND logo_file.biz_type = 'TENANT_LOGO'
                 AND logo_file.biz_id = tenant_record.id
                 AND logo_file.workspace_key = 'tenants'
                 AND logo_file.access_scope = 'INTERNAL'
                 AND logo_file.status = 'ENABLED'
                 AND logo_file.deleted = 0
                LEFT JOIN tenant_contact emergency
                  ON emergency.active_emergency_tenant_id = tenant_record.id
                WHERE tenant_record.id = ?
                  AND tenant_record.deleted = 0
                """,
                tenantId
        );
        Map<String, Object> lease = jdbcTemplate.queryForMap(
                """
                SELECT contract_summary.active_contract_count,
                       COUNT(room_summary.room_id) AS active_room_count,
                       COUNT(DISTINCT room_summary.building_id) AS active_building_count,
                       GROUP_CONCAT(DISTINCT room_summary.building_name ORDER BY room_summary.building_name SEPARATOR '、') AS building_summary,
                       GROUP_CONCAT(DISTINCT room_summary.floor_name ORDER BY room_summary.floor_name SEPARATOR '、') AS floor_summary,
                       GROUP_CONCAT(DISTINCT room_summary.room_no ORDER BY room_summary.room_no SEPARATOR '、') AS room_summary,
                       COALESCE(SUM(room_summary.area), 0) AS total_area,
                       contract_summary.lease_start_date,
                       contract_summary.lease_end_date
                FROM (
                    SELECT COUNT(*) AS active_contract_count,
                           MIN(start_date) AS lease_start_date,
                           MAX(end_date) AS lease_end_date
                    FROM contract
                    WHERE tenant_id = ?
                      AND deleted = 0
                      AND status = 'ACTIVE'
                      AND start_date <= CURRENT_DATE
                      AND end_date >= CURRENT_DATE
                ) contract_summary
                LEFT JOIN (
                    SELECT DISTINCT room_record.id AS room_id,
                           room_record.building_id,
                           building_record.name AS building_name,
                           floor_record.name AS floor_name,
                           room_record.room_no,
                           COALESCE(room_record.property_fee_area, room_record.area, 0) AS area
                    FROM contract contract_record
                    INNER JOIN (
                        SELECT contract_id, room_id FROM contract_room WHERE deleted = 0
                        UNION
                        SELECT id AS contract_id, room_id FROM contract WHERE deleted = 0
                    ) room_link ON room_link.contract_id = contract_record.id
                    INNER JOIN building_room room_record
                      ON room_record.id = room_link.room_id
                     AND room_record.deleted = 0
                    LEFT JOIN building building_record
                      ON building_record.id = room_record.building_id
                     AND building_record.deleted = 0
                    LEFT JOIN building_floor floor_record
                      ON floor_record.id = room_record.floor_id
                     AND floor_record.deleted = 0
                    WHERE contract_record.tenant_id = ?
                      AND contract_record.deleted = 0
                      AND contract_record.status = 'ACTIVE'
                      AND contract_record.start_date <= CURRENT_DATE
                      AND contract_record.end_date >= CURRENT_DATE
                ) room_summary ON 1 = 1
                GROUP BY contract_summary.active_contract_count,
                         contract_summary.lease_start_date,
                         contract_summary.lease_end_date
                """,
                tenantId,
                tenantId
        );
        long activeContractCount = number(lease.get("active_contract_count"));
        return new Overview(
                number(tenant.get("id")),
                string(tenant.get("code")),
                string(tenant.get("name")),
                string(tenant.get("short_name")),
                string(tenant.get("credit_code")),
                string(tenant.get("invoice_title")),
                string(tenant.get("tax_number")),
                numberOrNull(tenant.get("logo_file_id")),
                string(tenant.get("logo_file_name")),
                tenant.get("logo_file_id") == null ? "" : "/api/files/" + tenant.get("logo_file_id"),
                string(tenant.get("status")),
                string(lease.get("building_summary")),
                string(lease.get("floor_summary")),
                string(lease.get("room_summary")),
                decimal(lease.get("total_area")),
                activeContractCount,
                number(lease.get("active_room_count")),
                number(lease.get("active_building_count")),
                localDate(lease.get("lease_start_date")),
                localDate(lease.get("lease_end_date")),
                person(tenant, "manager"),
                person(tenant, "emergency"),
                localDateTime(tenant.get("created_at")),
                localDateTime(tenant.get("updated_at"))
        );
    }

    public PageResponse<ContactItem> contacts(
            Long tenantId,
            String keyword,
            String status,
            Integer page,
            Integer pageSize
    ) {
        ensureTenantExists(tenantId);
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        StringBuilder where = new StringBuilder("""
                 WHERE contact_record.tenant_id = ?
                   AND contact_record.deleted = 0
                """);
        if (keyword != null && !keyword.isBlank()) {
            String value = "%" + keyword.trim() + "%";
            where.append(" AND (contact_record.name LIKE ? OR contact_record.phone LIKE ? OR contact_record.position LIKE ?)");
            args.addAll(List.of(value, value, value));
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND contact_record.status = ?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
        return JdbcPagination.query(
                jdbcTemplate,
                """
                SELECT contact_record.id,
                       contact_record.tenant_id,
                       contact_record.name,
                       contact_record.phone,
                       contact_record.email,
                       contact_record.position,
                       contact_record.role,
                       contact_record.is_primary,
                       contact_record.is_emergency,
                       contact_record.status,
                       CASE
                           WHEN COALESCE(binding_summary.bound_count, 0) > 0 THEN 'BOUND'
                           ELSE 'UNBOUND'
                       END AS binding_status,
                       COALESCE(binding_summary.bound_count, 0) AS bound_count,
                       binding_summary.last_login_at,
                       contact_record.created_at,
                       contact_record.updated_at
                FROM tenant_contact contact_record
                LEFT JOIN (
                    SELECT bind_record.tenant_contact_id,
                           COUNT(DISTINCT bind_record.mobile_user_id) AS bound_count,
                           MAX(mobile_record.last_login_at) AS last_login_at
                    FROM mobile_user_bind bind_record
                    INNER JOIN mobile_user mobile_record
                      ON mobile_record.id = bind_record.mobile_user_id
                     AND mobile_record.deleted = 0
                     AND mobile_record.status IN ('ACTIVE', 'ENABLED')
                    WHERE bind_record.deleted = 0
                      AND bind_record.status = 'ENABLED'
                      AND bind_record.bind_type = 'TENANT'
                    GROUP BY bind_record.tenant_contact_id
                ) binding_summary
                  ON binding_summary.tenant_contact_id = contact_record.id
                """ + where + """
                ORDER BY contact_record.is_primary DESC,
                         contact_record.is_emergency DESC,
                         contact_record.updated_at DESC,
                         contact_record.id DESC
                """,
                "SELECT COUNT(*) FROM tenant_contact contact_record" + where,
                args,
                page,
                pageSize,
                (rs, rowNum) -> new ContactItem(
                        rs.getLong("id"),
                        rs.getLong("tenant_id"),
                        rs.getString("name"),
                        rs.getString("phone"),
                        rs.getString("email"),
                        rs.getString("position"),
                        rs.getString("role"),
                        rs.getBoolean("is_primary"),
                        rs.getBoolean("is_emergency"),
                        rs.getString("status"),
                        rs.getString("binding_status"),
                        rs.getLong("bound_count"),
                        dateTime(rs, "last_login_at"),
                        dateTime(rs, "created_at"),
                        dateTime(rs, "updated_at")
                )
        );
    }

    public Spaces spaces(
            Long tenantId,
            Boolean current,
            Long contractId,
            Integer page,
            Integer pageSize
    ) {
        ensureTenantExists(tenantId);
        PageResponse<RoomSpaceItem> rooms = roomSpaces(tenantId, current, contractId, page, pageSize);
        PageResponse<ParkingSpaceItem> parking = parkingSpaces(tenantId, current, page, pageSize);
        long currentRooms = count("""
                SELECT COUNT(*)
                FROM (
                    SELECT DISTINCT contract_record.id, room_link.room_id
                    FROM contract contract_record
                    INNER JOIN (
                        SELECT contract_id, room_id FROM contract_room WHERE deleted = 0
                        UNION
                        SELECT id AS contract_id, room_id FROM contract WHERE deleted = 0
                    ) room_link ON room_link.contract_id = contract_record.id
                    WHERE contract_record.tenant_id = ?
                      AND contract_record.deleted = 0
                      AND contract_record.status = 'ACTIVE'
                      AND contract_record.start_date <= CURRENT_DATE
                      AND contract_record.end_date >= CURRENT_DATE
                ) current_room
                """, tenantId);
        long allRooms = count("""
                SELECT COUNT(*)
                FROM (
                    SELECT DISTINCT contract_record.id, room_link.room_id
                    FROM contract contract_record
                    INNER JOIN (
                        SELECT contract_id, room_id FROM contract_room WHERE deleted = 0
                        UNION
                        SELECT id AS contract_id, room_id FROM contract WHERE deleted = 0
                    ) room_link ON room_link.contract_id = contract_record.id
                    WHERE contract_record.tenant_id = ? AND contract_record.deleted = 0
                ) all_room
                """, tenantId);
        long currentParking = count("""
                SELECT COUNT(*) FROM parking_assignment
                WHERE tenant_id = ? AND deleted = 0
                  AND status IN ('ACTIVE', 'ENABLED')
                  AND start_date <= CURRENT_DATE
                  AND (end_date IS NULL OR end_date >= CURRENT_DATE)
                """, tenantId);
        long allParking = count(
                "SELECT COUNT(*) FROM parking_assignment WHERE tenant_id = ? AND deleted = 0",
                tenantId
        );
        return new Spaces(
                rooms,
                parking,
                currentRooms,
                Math.max(0, allRooms - currentRooms),
                currentParking,
                Math.max(0, allParking - currentParking)
        );
    }

    public PageResponse<ContractItem> contracts(
            Long tenantId,
            String status,
            Integer page,
            Integer pageSize
    ) {
        ensureTenantExists(tenantId);
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        StringBuilder where = new StringBuilder(" WHERE contract_record.tenant_id = ? AND contract_record.deleted = 0");
        appendContractStatus(where, args, status);
        PageResponse<ContractItem> headers = JdbcPagination.query(
                jdbcTemplate,
                """
                SELECT contract_record.id,
                       contract_record.code,
                       tenant_record.name AS tenant_name,
                       contract_record.status,
                       contract_record.start_date,
                       contract_record.end_date,
                       contract_record.rent_amount,
                       contract_record.deposit_amount,
                       contract_record.version,
                       contract_record.created_at,
                       contract_record.updated_at,
                       GROUP_CONCAT(DISTINCT room_record.id ORDER BY room_record.id) AS room_ids,
                       GROUP_CONCAT(DISTINCT room_record.room_no ORDER BY room_record.room_no SEPARATOR '、') AS room_names,
                       COALESCE(SUM(CASE
                           WHEN room_record.id IS NULL THEN 0
                           ELSE COALESCE(room_record.property_fee_area, room_record.area, 0)
                       END), 0) AS total_area
                FROM contract contract_record
                INNER JOIN tenant tenant_record
                  ON tenant_record.id = contract_record.tenant_id
                 AND tenant_record.deleted = 0
                LEFT JOIN (
                    SELECT contract_id, room_id FROM contract_room WHERE deleted = 0
                    UNION
                    SELECT id AS contract_id, room_id FROM contract WHERE deleted = 0
                ) room_link
                  ON room_link.contract_id = contract_record.id
                LEFT JOIN building_room room_record
                  ON room_record.id = room_link.room_id
                 AND room_record.deleted = 0
                """ + where + "\n" + """
                GROUP BY contract_record.id
                ORDER BY contract_record.start_date DESC, contract_record.updated_at DESC, contract_record.id DESC
                """,
                "SELECT COUNT(*) FROM contract contract_record" + where,
                args,
                page,
                pageSize,
                (rs, rowNum) -> new ContractItem(
                        rs.getLong("id"),
                        rs.getString("code"),
                        "LEASE",
                        rs.getString("tenant_name"),
                        rs.getString("status"),
                        date(rs, "start_date"),
                        date(rs, "end_date"),
                        longList(rs.getString("room_ids")),
                        stringList(rs.getString("room_names"), "、"),
                        valueOr(rs.getString("room_names"), "未关联房间"),
                        rs.getBigDecimal("total_area"),
                        rs.getBigDecimal("rent_amount"),
                        rs.getBigDecimal("deposit_amount"),
                        isExpiringSoon(date(rs, "end_date")),
                        List.of(),
                        dateTime(rs, "created_at"),
                        dateTime(rs, "updated_at"),
                        rs.getLong("version")
                )
        );
        Map<Long, List<ContractAttachmentItem>> attachments = attachmentsByContract(
                headers.records().stream().map(ContractItem::id).toList()
        );
        List<ContractItem> records = headers.records().stream().map(item -> new ContractItem(
                item.id(),
                item.contractNumber(),
                item.contractType(),
                item.tenantName(),
                item.status(),
                item.startDate(),
                item.endDate(),
                item.roomIds(),
                item.roomNames(),
                item.roomSummary(),
                item.totalArea(),
                item.rentAmount(),
                item.depositAmount(),
                item.expiringSoon(),
                attachments.getOrDefault(item.id(), List.of()),
                item.createdAt(),
                item.updatedAt(),
                item.version()
        )).toList();
        return new PageResponse<>(records, headers.total(), headers.page(), headers.pageSize());
    }

    public PageResponse<BillItem> bills(
            Long tenantId,
            String status,
            String invoiceStatus,
            Long contractId,
            Integer page,
            Integer pageSize
    ) {
        ensureTenantExists(tenantId);
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        StringBuilder where = new StringBuilder(" WHERE bill_record.tenant_id = ? AND bill_record.deleted = 0");
        if (contractId != null) {
            where.append(" AND bill_record.contract_id = ?");
            args.add(contractId);
        }
        if (status != null && !status.isBlank()) {
            switch (status.trim().toUpperCase(Locale.ROOT)) {
                case "UNPAID" -> where.append(" AND bill_record.pay_status NOT IN ('PAID', 'CANCELLED')");
                case "PAID" -> where.append(" AND bill_record.pay_status = 'PAID'");
                case "OVERDUE" -> where.append(" AND bill_record.pay_status NOT IN ('PAID', 'CANCELLED') AND bill_record.due_date < CURRENT_DATE");
                default -> {
                    where.append(" AND bill_record.pay_status = ?");
                    args.add(status.trim().toUpperCase(Locale.ROOT));
                }
            }
        }
        if (invoiceStatus != null && !invoiceStatus.isBlank()) {
            where.append(" AND bill_record.invoice_status = ?");
            args.add(invoiceStatus.trim().toUpperCase(Locale.ROOT));
        }
        return JdbcPagination.query(
                jdbcTemplate,
                """
                SELECT bill_record.id,
                       bill_record.code,
                       bill_record.billing_period,
                       COALESCE(line_summary.fee_types, bill_record.bill_type) AS fee_types,
                       bill_record.calculated_total,
                       bill_record.adjustment_total,
                       bill_record.final_amount,
                       bill_record.paid_amount,
                       GREATEST(bill_record.final_amount - bill_record.paid_amount, 0) AS unpaid_amount,
                       bill_record.workflow_status,
                       bill_record.pay_status,
                       bill_record.due_date,
                       bill_record.invoice_status,
                       bill_record.contract_id,
                       contract_record.code AS contract_number
                FROM bill bill_record
                LEFT JOIN contract contract_record
                  ON contract_record.id = bill_record.contract_id
                 AND contract_record.deleted = 0
                LEFT JOIN (
                    SELECT line_record.bill_id,
                           GROUP_CONCAT(DISTINCT line_record.charge_item_type ORDER BY line_record.charge_item_type SEPARATOR '、') AS fee_types
                    FROM bill_line_item line_record
                    WHERE line_record.deleted = 0
                      AND line_record.status = 'ACTIVE'
                    GROUP BY line_record.bill_id
                ) line_summary
                  ON line_summary.bill_id = bill_record.id
                """ + where + "\n" + """
                ORDER BY bill_record.due_date DESC, bill_record.updated_at DESC, bill_record.id DESC
                """,
                "SELECT COUNT(*) FROM bill bill_record" + where,
                args,
                page,
                pageSize,
                (rs, rowNum) -> {
                    Long linkedContractId = nullableLong(rs, "contract_id");
                    String contractNumber = rs.getString("contract_number");
                    return new BillItem(
                            rs.getLong("id"),
                            rs.getString("code"),
                            rs.getString("billing_period"),
                            rs.getString("fee_types"),
                            rs.getBigDecimal("calculated_total"),
                            rs.getBigDecimal("adjustment_total"),
                            rs.getBigDecimal("final_amount"),
                            rs.getBigDecimal("paid_amount"),
                            rs.getBigDecimal("unpaid_amount"),
                            rs.getString("workflow_status"),
                            rs.getString("pay_status"),
                            date(rs, "due_date"),
                            rs.getString("invoice_status"),
                            linkedContractId,
                            contractNumber,
                            linkedContractId != null,
                            linkedContractId == null
                                    ? "租户账单，未明确关联具体合同"
                                    : valueOr(contractNumber, "已关联合同")
                    );
                }
        );
    }

    public List<ServiceManagerItem> serviceManagers(String keyword) {
        List<Object> args = new ArrayList<>();
        String keywordFilter = "";
        if (keyword != null && !keyword.isBlank()) {
            keywordFilter = " AND (user_record.real_name LIKE ? OR user_record.phone LIKE ? OR user_record.email LIKE ?)";
            String value = "%" + keyword.trim() + "%";
            args.addAll(List.of(value, value, value));
        }
        args.add(50);
        return jdbcTemplate.query(
                """
                SELECT DISTINCT user_record.id,
                       user_record.real_name,
                       user_record.phone,
                       user_record.email
                FROM sys_user user_record
                WHERE user_record.deleted = 0
                  AND user_record.status IN ('ACTIVE', 'ENABLED')
                  AND EXISTS (
                      SELECT 1
                      FROM sys_user_role user_role
                      INNER JOIN sys_role role_record
                        ON role_record.id = user_role.role_id
                       AND role_record.deleted = 0
                       AND role_record.status IN ('ACTIVE', 'ENABLED')
                      LEFT JOIN sys_role_permission role_permission
                        ON role_permission.role_id = role_record.id
                       AND role_permission.deleted = 0
                       AND role_permission.status IN ('ACTIVE', 'ENABLED')
                      LEFT JOIN sys_permission permission_record
                        ON permission_record.id = role_permission.permission_id
                       AND permission_record.deleted = 0
                       AND permission_record.status IN ('ACTIVE', 'ENABLED')
                      WHERE user_role.user_id = user_record.id
                        AND user_role.deleted = 0
                        AND user_role.status IN ('ACTIVE', 'ENABLED')
                        AND (
                            role_record.code = 'SUPER_ADMIN'
                            OR permission_record.code IN ('tenant:view', 'tenant:update')
                        )
                  )
                """ + keywordFilter + """
                ORDER BY user_record.real_name, user_record.id
                LIMIT ?
                """,
                (rs, rowNum) -> new ServiceManagerItem(
                        rs.getLong("id"),
                        rs.getString("real_name"),
                        rs.getString("phone"),
                        rs.getString("email")
                ),
                args.toArray()
        );
    }

    private PageResponse<RoomSpaceItem> roomSpaces(
            Long tenantId,
            Boolean current,
            Long contractId,
            Integer page,
            Integer pageSize
    ) {
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        StringBuilder where = new StringBuilder(" WHERE contract_record.tenant_id = ? AND contract_record.deleted = 0");
        if (contractId != null) {
            where.append(" AND contract_record.id = ?");
            args.add(contractId);
        }
        appendCurrentContractFilter(where, current);
        String recordSql = """
                SELECT CONCAT(contract_record.id, ':', room_record.id) AS row_key,
                       room_record.id AS room_id,
                       building_record.name AS building_name,
                       floor_record.name AS floor_name,
                       room_record.room_no,
                       room_record.usage_type,
                       COALESCE(room_record.property_fee_area, room_record.area, 0) AS area,
                       contract_record.id AS contract_id,
                       contract_record.code AS contract_number,
                       contract_record.start_date,
                       contract_record.end_date,
                       contract_record.status AS contract_status,
                       COALESCE((
                           SELECT lease_room_status.status
                           FROM lease_room lease_room_status
                           INNER JOIN lease lease_status
                             ON lease_status.id = lease_room_status.lease_id
                            AND lease_status.deleted = 0
                           WHERE lease_status.contract_id = contract_record.id
                             AND lease_room_status.room_id = room_record.id
                             AND lease_room_status.deleted = 0
                           ORDER BY lease_status.updated_at DESC, lease_status.id DESC
                           LIMIT 1
                       ), room_record.rent_status) AS occupancy_status,
                       CASE WHEN %s THEN 1 ELSE 0 END AS is_current
                FROM contract contract_record
                INNER JOIN (
                    SELECT contract_id, room_id FROM contract_room WHERE deleted = 0
                    UNION
                    SELECT id AS contract_id, room_id FROM contract WHERE deleted = 0
                ) room_link
                  ON room_link.contract_id = contract_record.id
                INNER JOIN building_room room_record
                  ON room_record.id = room_link.room_id
                 AND room_record.deleted = 0
                LEFT JOIN building building_record
                  ON building_record.id = room_record.building_id
                 AND building_record.deleted = 0
                LEFT JOIN building_floor floor_record
                  ON floor_record.id = room_record.floor_id
                 AND floor_record.deleted = 0
                """.formatted(ACTIVE_CONTRACT) + where + "\n" + """
                ORDER BY is_current DESC, contract_record.start_date DESC, building_record.name, floor_record.name, room_record.room_no
                """;
        return JdbcPagination.query(
                jdbcTemplate,
                recordSql,
                """
                SELECT COUNT(*)
                FROM contract contract_record
                INNER JOIN (
                    SELECT contract_id, room_id FROM contract_room WHERE deleted = 0
                    UNION
                    SELECT id AS contract_id, room_id FROM contract WHERE deleted = 0
                ) room_link
                  ON room_link.contract_id = contract_record.id
                """ + where,
                args,
                page,
                pageSize,
                (rs, rowNum) -> new RoomSpaceItem(
                        rs.getString("row_key"),
                        rs.getLong("room_id"),
                        rs.getString("building_name"),
                        rs.getString("floor_name"),
                        rs.getString("room_no"),
                        rs.getString("usage_type"),
                        rs.getBigDecimal("area"),
                        rs.getLong("contract_id"),
                        rs.getString("contract_number"),
                        date(rs, "start_date"),
                        date(rs, "end_date"),
                        rs.getString("contract_status"),
                        rs.getString("occupancy_status"),
                        rs.getBoolean("is_current")
                )
        );
    }

    private PageResponse<ParkingSpaceItem> parkingSpaces(
            Long tenantId,
            Boolean current,
            Integer page,
            Integer pageSize
    ) {
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        StringBuilder where = new StringBuilder(" WHERE assignment_record.tenant_id = ? AND assignment_record.deleted = 0");
        if (current != null) {
            if (current) {
                where.append("""
                         AND assignment_record.status IN ('ACTIVE', 'ENABLED')
                         AND assignment_record.start_date <= CURRENT_DATE
                         AND (assignment_record.end_date IS NULL OR assignment_record.end_date >= CURRENT_DATE)
                        """);
            } else {
                where.append("""
                         AND NOT (
                             assignment_record.status IN ('ACTIVE', 'ENABLED')
                             AND assignment_record.start_date <= CURRENT_DATE
                             AND (assignment_record.end_date IS NULL OR assignment_record.end_date >= CURRENT_DATE)
                         )
                        """);
            }
        }
        return JdbcPagination.query(
                jdbcTemplate,
                """
                SELECT CONCAT('P:', assignment_record.id) AS row_key,
                       assignment_record.id AS assignment_id,
                       parking_record.id AS parking_space_id,
                       parking_record.space_no,
                       parking_record.location,
                       parking_record.space_type,
                       assignment_record.start_date,
                       assignment_record.end_date,
                       assignment_record.car_plate_no,
                       contact_record.name AS contact_name,
                       assignment_record.status,
                       CASE
                           WHEN assignment_record.status IN ('ACTIVE', 'ENABLED')
                            AND assignment_record.start_date <= CURRENT_DATE
                            AND (assignment_record.end_date IS NULL OR assignment_record.end_date >= CURRENT_DATE)
                           THEN 1 ELSE 0
                       END AS is_current
                FROM parking_assignment assignment_record
                INNER JOIN parking_space parking_record
                  ON parking_record.id = assignment_record.parking_space_id
                 AND parking_record.deleted = 0
                LEFT JOIN tenant_contact contact_record
                  ON contact_record.id = assignment_record.tenant_contact_id
                 AND contact_record.deleted = 0
                """ + where + "\n" + """
                ORDER BY is_current DESC, assignment_record.start_date DESC, assignment_record.id DESC
                """,
                "SELECT COUNT(*) FROM parking_assignment assignment_record" + where,
                args,
                page,
                pageSize,
                (rs, rowNum) -> new ParkingSpaceItem(
                        rs.getString("row_key"),
                        rs.getLong("assignment_id"),
                        rs.getLong("parking_space_id"),
                        rs.getString("space_no"),
                        rs.getString("location"),
                        rs.getString("space_type"),
                        date(rs, "start_date"),
                        date(rs, "end_date"),
                        rs.getString("car_plate_no"),
                        rs.getString("contact_name"),
                        rs.getString("status"),
                        rs.getBoolean("is_current"),
                        "/tenant/parking-bills?tenantId=" + tenantId
                )
        );
    }

    private Map<Long, List<ContractAttachmentItem>> attachmentsByContract(List<Long> contractIds) {
        if (contractIds.isEmpty()) return Map.of();
        String placeholders = contractIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String attachmentSql = """
                SELECT attachment_record.id AS attachment_id,
                       attachment_record.contract_id,
                       attachment_record.file_id,
                       attachment_record.attachment_type,
                       attachment_record.status,
                       file_record.original_name,
                       file_record.mime_type,
                       file_record.file_size,
                       file_record.created_at
                FROM contract_attachment attachment_record
                INNER JOIN file_upload file_record
                  ON file_record.id = attachment_record.file_id
                 AND file_record.deleted = 0
                 AND file_record.status IN ('ACTIVE', 'ENABLED')
                WHERE attachment_record.contract_id IN (%s)
                  AND attachment_record.deleted = 0
                  AND attachment_record.status IN ('ACTIVE', 'ENABLED')
                ORDER BY attachment_record.contract_id,
                         CASE WHEN attachment_record.attachment_type = 'ELECTRONIC_CONTRACT' THEN 0 ELSE 1 END,
                         attachment_record.created_at DESC,
                         attachment_record.id DESC
                """.formatted(placeholders);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                attachmentSql,
                contractIds.toArray()
        );
        Map<Long, List<ContractAttachmentItem>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long contractId = number(row.get("contract_id"));
            Long attachmentId = number(row.get("attachment_id"));
            result.computeIfAbsent(contractId, ignored -> new ArrayList<>()).add(new ContractAttachmentItem(
                    attachmentId,
                    number(row.get("file_id")),
                    string(row.get("attachment_type")),
                    string(row.get("original_name")),
                    string(row.get("mime_type")),
                    number(row.get("file_size")),
                    string(row.get("status")),
                    localDateTime(row.get("created_at")),
                    "/contracts/" + contractId + "/attachments/" + attachmentId + "/download"
            ));
        }
        return result;
    }

    private void appendContractStatus(StringBuilder where, List<Object> args, String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) return;
        switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "CURRENT" -> where.append(" AND contract_record.status = 'ACTIVE' AND contract_record.start_date <= CURRENT_DATE AND contract_record.end_date >= CURRENT_DATE");
            case "HISTORY" -> where.append(" AND (contract_record.status IN ('TERMINATED', 'CANCELLED', 'EXPIRED') OR contract_record.end_date < CURRENT_DATE)");
            case "EXPIRING" -> where.append(" AND contract_record.status = 'ACTIVE' AND contract_record.end_date BETWEEN CURRENT_DATE AND DATE_ADD(CURRENT_DATE, INTERVAL 90 DAY)");
            default -> {
                where.append(" AND contract_record.status = ?");
                args.add(status.trim().toUpperCase(Locale.ROOT));
            }
        }
    }

    private void appendCurrentContractFilter(StringBuilder where, Boolean current) {
        if (current == null) return;
        if (current) {
            where.append(" AND ").append(ACTIVE_CONTRACT);
        } else {
            where.append(" AND NOT (").append(ACTIVE_CONTRACT).append(")");
        }
    }

    private void ensureTenantExists(Long tenantId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tenant WHERE id = ? AND deleted = 0",
                Long.class,
                tenantId
        );
        if (count == null || count == 0) {
            throw new BusinessException(404, "租户不存在");
        }
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private PersonSummary person(Map<String, Object> row, String prefix) {
        Long id = numberOrNull(row.get(prefix + "_id"));
        return id == null ? null : new PersonSummary(
                id,
                string(row.get(prefix + "_name")),
                string(row.get(prefix + "_phone"))
        );
    }

    private boolean isExpiringSoon(LocalDate endDate) {
        return endDate != null
                && !endDate.isBefore(LocalDate.now())
                && !endDate.isAfter(LocalDate.now().plusDays(90));
    }

    private static LocalDate date(ResultSet rs, String column) throws SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private static LocalDateTime dateTime(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static List<Long> longList(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(Long::valueOf).toList();
    }

    private static List<String> stringList(String value, String separator) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(separator)).filter(item -> !item.isBlank()).toList();
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static Long numberOrNull(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        return BigDecimal.ZERO;
    }

    private static LocalDate localDate(Object value) {
        if (value instanceof Date date) return date.toLocalDate();
        if (value instanceof LocalDate date) return date;
        return null;
    }

    private static LocalDateTime localDateTime(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        if (value instanceof LocalDateTime dateTime) return dateTime;
        return null;
    }
}

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

public class DevSeed500Modules {
    private static final int TARGET = 500;
    private static final String PREFIX = "SEED500-20260627";
    private static final String DEFAULT_URL = "jdbc:mysql://127.0.0.1:3306/dehui_property_dev?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
    private static final String DEFAULT_USER = "dehui";
    private static final String DEFAULT_PASSWORD = "dehui_dev_password";
    private static final String PASSWORD_HASH = "$2a$10$XqhZR.pObyI//tr.gLRq7.Joa1SjTQRO0USIJcDEo4UtUarcndRBW";

    private final Connection conn;
    private final Map<String, Set<String>> tableColumns = new LinkedHashMap<>();

    public static void main(String[] args) throws Exception {
        String url = firstNonBlank(System.getenv("DEHUI_DB_URL"), DEFAULT_URL);
        String user = firstNonBlank(System.getenv("DEHUI_DB_USER"), DEFAULT_USER);
        String password = firstNonBlank(System.getenv("DEHUI_DB_PASSWORD"), DEFAULT_PASSWORD);
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            conn.setAutoCommit(false);
            DevSeed500Modules seeder = new DevSeed500Modules(conn);
            seeder.run();
            conn.commit();
        }
    }

    private DevSeed500Modules(Connection conn) {
        this.conn = conn;
    }

    private void run() throws SQLException {
        seedSystemUsers();
        seedMobileUsers();
        seedBuildingsFloorsRooms();
        seedTenants();
        seedContractsAndBills();
        seedParking();
        seedMeetings();
        seedEnergy();
        seedOperations();
        seedInvestment();
        seedNotices();
        seedEquipment();
        printCounts();
    }

    private void seedSystemUsers() throws SQLException {
        if (!hasTable("sys_user")) return;
        for (int i = 1; i <= TARGET; i++) {
            Map<String, Object> row = base("sys_user", i);
            row.put("code", code("USR", i));
            row.put("username", "seed_user_" + n(i));
            row.put("password_hash", PASSWORD_HASH);
            row.put("real_name", "测试用户" + n(i));
            row.put("phone", "170" + String.format("%08d", i));
            row.put("email", "seed_user_" + n(i) + "@example.local");
            row.put("status", "ENABLED");
            row.put("remark", "500条模块测试数据-系统用户");
            upsert("sys_user", row, "real_name", "phone", "email", "status", "remark");
        }

        Long roleId = firstId("sys_role", "code IN ('STAFF','MANAGER','ADMIN')");
        if (roleId != null && hasTable("sys_user_role")) {
            for (Long userId : idsByCodePrefix("sys_user", code("USR", 0).substring(0, code("USR", 0).length() - 3), TARGET)) {
                Map<String, Object> rel = timestamps(new LinkedHashMap<>());
                rel.put("user_id", userId);
                rel.put("role_id", roleId);
                rel.put("status", "ENABLED");
                rel.put("deleted", 0);
                upsert("sys_user_role", rel, "status", "deleted");
            }
        }
    }

    private void seedMobileUsers() throws SQLException {
        if (!hasTable("mobile_user")) return;
        for (int i = 1; i <= TARGET; i++) {
            Map<String, Object> row = base("mobile_user", i);
            row.put("code", code("MOB", i));
            row.put("nickname", "测试小程序用户" + n(i));
            row.put("phone", "171" + String.format("%08d", i));
            row.put("user_type", i % 3 == 0 ? "STAFF" : "TENANT");
            row.put("status", "ENABLED");
            row.put("remark", "500条模块测试数据-小程序用户");
            upsert("mobile_user", row, "nickname", "phone", "user_type", "status", "remark");
        }
    }

    private void seedBuildingsFloorsRooms() throws SQLException {
        for (int i = 1; i <= TARGET; i++) {
            if (hasTable("building")) {
                Map<String, Object> b = base("building", i);
                b.put("code", code("BLD", i));
                b.put("name", "测试楼宇" + n(i));
                b.put("address", "德汇创新中心测试园区 " + i + " 号");
                b.put("total_floor", 20 + (i % 10));
                b.put("status", "ENABLED");
                b.put("remark", "500条模块测试数据-楼宇");
                upsert("building", b, "name", "address", "total_floor", "status", "remark");
            }
        }
        List<Long> buildings = idsByCodePrefix("building", codePrefix("BLD"), TARGET);
        for (int i = 1; i <= TARGET; i++) {
            Long buildingId = pick(buildings, i);
            if (buildingId != null && hasTable("building_floor")) {
                Map<String, Object> f = base("building_floor", i);
                f.put("code", code("FLR", i));
                f.put("building_id", buildingId);
                f.put("floor_no", 1000 + i);
                f.put("name", "测试楼层" + n(i));
                f.put("status", "ENABLED");
                f.put("remark", "500条模块测试数据-楼层");
                upsert("building_floor", f, "building_id", "floor_no", "name", "status", "remark");
            }
        }
        List<Long> floors = idsByCodePrefix("building_floor", codePrefix("FLR"), TARGET);
        for (int i = 1; i <= TARGET; i++) {
            Long buildingId = pick(buildings, i);
            Long floorId = pick(floors, i);
            if (buildingId != null && floorId != null && hasTable("building_room")) {
                Map<String, Object> r = base("building_room", i);
                r.put("code", code("ROM", i));
                r.put("building_id", buildingId);
                r.put("floor_id", floorId);
                r.put("room_no", "SEED-" + n(i));
                r.put("area", bd(80 + (i % 220)));
                r.put("usage_type", i % 5 == 0 ? "RETAIL" : "OFFICE");
                r.put("rent_status", i % 4 == 0 ? "VACANT" : "RENTED");
                r.put("status", "ENABLED");
                r.put("remark", "500条模块测试数据-房间");
                upsert("building_room", r, "building_id", "floor_id", "room_no", "area", "usage_type", "rent_status", "status", "remark");
            }
        }
    }

    private void seedTenants() throws SQLException {
        if (!hasTable("tenant")) return;
        for (int i = 1; i <= TARGET; i++) {
            Map<String, Object> t = base("tenant", i);
            t.put("code", code("TEN", i));
            t.put("name", "测试租户公司" + n(i));
            t.put("contact_person", "联系人" + n(i));
            t.put("contact_phone", "172" + String.format("%08d", i));
            t.put("contact_email", "tenant_" + n(i) + "@example.local");
            t.put("credit_code", "91310000SEED" + String.format("%010d", i));
            t.put("status", "ENABLED");
            t.put("remark", "500条模块测试数据-租户");
            upsert("tenant", t, "name", "contact_person", "contact_phone", "contact_email", "status", "remark");
        }
        List<Long> tenants = idsByCodePrefix("tenant", codePrefix("TEN"), TARGET);
        for (int i = 1; i <= TARGET && hasTable("tenant_contact"); i++) {
            Long tenantId = pick(tenants, i);
            if (tenantId == null) continue;
            Map<String, Object> c = base("tenant_contact", i);
            c.put("code", code("TCO", i));
            c.put("tenant_id", tenantId);
            c.put("name", "租户联系人" + n(i));
            c.put("phone", "173" + String.format("%08d", i));
            c.put("email", "tenant_contact_" + n(i) + "@example.local");
            c.put("password_hash", PASSWORD_HASH);
            c.put("role", i % 3 == 0 ? "财务" : "行政");
            c.put("is_primary", 1);
            c.put("status", "ENABLED");
            c.put("remark", "500条模块测试数据-租户联系人");
            upsert("tenant_contact", c, "tenant_id", "name", "phone", "email", "password_hash", "role", "is_primary", "status", "remark");
        }
        bindMobileUsersToTenants();
    }

    private void bindMobileUsersToTenants() throws SQLException {
        if (!hasTable("mobile_user_bind")) return;
        List<Long> users = idsByCodePrefix("mobile_user", codePrefix("MOB"), TARGET);
        List<Long> tenants = idsByCodePrefix("tenant", codePrefix("TEN"), TARGET);
        List<Long> contacts = idsByCodePrefix("tenant_contact", codePrefix("TCO"), TARGET);
        for (int i = 1; i <= TARGET; i++) {
            Long userId = pick(users, i);
            Long tenantId = pick(tenants, i);
            Long contactId = pick(contacts, i);
            if (userId == null || tenantId == null) continue;
            Map<String, Object> bind = base("mobile_user_bind", i);
            bind.remove("code");
            bind.put("mobile_user_id", userId);
            bind.put("bind_type", "TENANT");
            bind.put("tenant_id", tenantId);
            bind.put("tenant_contact_id", contactId);
            bind.put("status", "ENABLED");
            bind.put("remark", "500条模块测试数据-小程序绑定");
            upsert("mobile_user_bind", bind, "tenant_contact_id", "status", "remark");
        }
    }

    private void seedContractsAndBills() throws SQLException {
        List<Long> tenants = idsByCodePrefix("tenant", codePrefix("TEN"), TARGET);
        List<Long> rooms = idsByCodePrefix("building_room", codePrefix("ROM"), TARGET);
        if (hasTable("contract")) {
            for (int i = 1; i <= TARGET; i++) {
                Long tenantId = pick(tenants, i);
                Long roomId = pick(rooms, i);
                if (tenantId == null || roomId == null) continue;
                Map<String, Object> con = base("contract", i);
                con.put("code", code("CON", i));
                con.put("tenant_id", tenantId);
                con.put("room_id", roomId);
                con.put("start_date", Date.valueOf(LocalDate.of(2026, 1, 1).plusDays(i % 120)));
                con.put("end_date", Date.valueOf(LocalDate.of(2027, 12, 31).plusDays(i % 120)));
                con.put("rent_amount", bd(6000 + i * 10));
                con.put("deposit_amount", bd(12000 + i * 10));
                con.put("status", i % 6 == 0 ? "EXPIRED" : "ACTIVE");
                con.put("remark", "500条模块测试数据-合同租约");
                upsert("contract", con, "tenant_id", "room_id", "start_date", "end_date", "rent_amount", "deposit_amount", "status", "remark");
            }
        }
        List<Long> contracts = idsByCodePrefix("contract", codePrefix("CON"), TARGET);
        if (hasTable("bill")) {
            for (int i = 1; i <= TARGET; i++) {
                Long tenantId = pick(tenants, i);
                Long contractId = pick(contracts, i);
                if (tenantId == null) continue;
                Map<String, Object> bill = base("bill", i);
                bill.put("code", code("BIL", i));
                bill.put("tenant_id", tenantId);
                bill.put("contract_id", contractId);
                bill.put("source_type", "CONTRACT");
                bill.put("source_id", contractId);
                bill.put("bill_type", i % 4 == 0 ? "PROPERTY_FEE" : "RENT");
                bill.put("title", "测试账单" + n(i));
                bill.put("amount", bd(1200 + i));
                bill.put("paid_amount", i % 3 == 0 ? bd(1200 + i) : bd(0));
                bill.put("period_start", Date.valueOf(LocalDate.of(2026, ((i - 1) % 12) + 1, 1)));
                bill.put("period_end", Date.valueOf(LocalDate.of(2026, ((i - 1) % 12) + 1, 1).plusMonths(1).minusDays(1)));
                bill.put("due_date", Date.valueOf(LocalDate.of(2026, ((i - 1) % 12) + 1, 25)));
                bill.put("audit_status", i % 2 == 0 ? "APPROVED" : "PENDING");
                bill.put("pay_status", i % 3 == 0 ? "PAID" : "UNPAID");
                bill.put("status", "ENABLED");
                bill.put("remark", "500条模块测试数据-账单");
                upsert("bill", bill, "tenant_id", "contract_id", "source_type", "source_id", "bill_type", "title", "amount", "paid_amount", "period_start", "period_end", "due_date", "audit_status", "pay_status", "status", "remark");
            }
        }
        seedBillChildren(tenants);
    }

    private void seedBillChildren(List<Long> tenants) throws SQLException {
        List<Long> bills = idsByCodePrefix("bill", codePrefix("BIL"), TARGET);
        for (int i = 1; i <= TARGET && hasTable("bill_payment_record"); i++) {
            Long billId = pick(bills, i);
            Long tenantId = pick(tenants, i);
            if (billId == null || tenantId == null) continue;
            Map<String, Object> p = base("bill_payment_record", i);
            p.put("code", code("PAY", i));
            p.put("bill_id", billId);
            p.put("tenant_id", tenantId);
            p.put("pay_channel", i % 2 == 0 ? "BANK" : "CASH");
            p.put("amount", bd(800 + i));
            p.put("transaction_no", "TXN-" + PREFIX + "-" + n(i));
            p.put("paid_at", ts(LocalDateTime.of(2026, 6, 1, 9, 0).plusHours(i)));
            p.put("status", i % 3 == 0 ? "SUCCESS" : "PENDING");
            p.put("remark", "500条模块测试数据-收款记录");
            upsert("bill_payment_record", p, "bill_id", "tenant_id", "pay_channel", "amount", "paid_at", "status", "remark");
        }
        for (int i = 1; i <= TARGET && hasTable("bill_invoice"); i++) {
            Long billId = pick(bills, i);
            Long tenantId = pick(tenants, i);
            if (billId == null || tenantId == null) continue;
            Map<String, Object> inv = base("bill_invoice", i);
            inv.put("code", code("INV", i));
            inv.put("bill_id", billId);
            inv.put("tenant_id", tenantId);
            inv.put("invoice_title", "测试租户公司" + n(i));
            inv.put("tax_no", "TAX" + PREFIX + n(i));
            inv.put("amount", bd(800 + i));
            inv.put("invoice_status", i % 2 == 0 ? "ISSUED" : "PENDING");
            inv.put("issued_at", ts(LocalDateTime.of(2026, 6, 5, 10, 0).plusHours(i)));
            inv.put("status", "ENABLED");
            inv.put("remark", "500条模块测试数据-发票");
            upsert("bill_invoice", inv, "bill_id", "tenant_id", "invoice_title", "tax_no", "amount", "invoice_status", "issued_at", "status", "remark");
        }
    }

    private void seedParking() throws SQLException {
        for (int i = 1; i <= TARGET && hasTable("parking_space"); i++) {
            Map<String, Object> ps = base("parking_space", i);
            ps.put("code", code("PKS", i));
            ps.put("space_no", "P-SEED-" + n(i));
            ps.put("location", "地下车库 B" + ((i % 3) + 1));
            ps.put("space_type", i % 8 == 0 ? "CHARGING" : "STANDARD");
            ps.put("rent_status", i % 4 == 0 ? "VACANT" : "RENTED");
            ps.put("status", "ENABLED");
            ps.put("remark", "500条模块测试数据-车位");
            upsert("parking_space", ps, "space_no", "location", "space_type", "rent_status", "status", "remark");
        }
        List<Long> spaces = idsByCodePrefix("parking_space", codePrefix("PKS"), TARGET);
        List<Long> tenants = idsByCodePrefix("tenant", codePrefix("TEN"), TARGET);
        for (int i = 1; i <= TARGET && hasTable("parking_assignment"); i++) {
            Long spaceId = pick(spaces, i);
            Long tenantId = pick(tenants, i);
            if (spaceId == null || tenantId == null) continue;
            Map<String, Object> a = base("parking_assignment", i);
            a.put("code", code("PKA", i));
            a.put("parking_space_id", spaceId);
            a.put("tenant_id", tenantId);
            a.put("car_plate_no", "沪A" + String.format("%05d", i));
            a.put("start_date", Date.valueOf(LocalDate.of(2026, 1, 1).plusDays(i % 60)));
            a.put("end_date", Date.valueOf(LocalDate.of(2026, 12, 31).plusDays(i % 60)));
            a.put("status", "ENABLED");
            a.put("remark", "500条模块测试数据-车位绑定");
            upsert("parking_assignment", a, "parking_space_id", "tenant_id", "car_plate_no", "start_date", "end_date", "status", "remark");
        }
        List<Long> assignments = idsByCodePrefix("parking_assignment", codePrefix("PKA"), TARGET);
        for (int i = 1; i <= TARGET && hasTable("parking_bill"); i++) {
            Long assignmentId = pick(assignments, i);
            Long tenantId = pick(tenants, i);
            if (assignmentId == null || tenantId == null) continue;
            Map<String, Object> b = base("parking_bill", i);
            b.put("code", code("PKB", i));
            b.put("assignment_id", assignmentId);
            b.put("tenant_id", tenantId);
            b.put("amount", bd(300 + (i % 100)));
            b.put("due_date", Date.valueOf(LocalDate.of(2026, ((i - 1) % 12) + 1, 20)));
            b.put("pay_status", i % 3 == 0 ? "PAID" : "UNPAID");
            b.put("status", "ENABLED");
            b.put("remark", "500条模块测试数据-停车账单");
            upsert("parking_bill", b, "assignment_id", "tenant_id", "amount", "due_date", "pay_status", "status", "remark");
        }
    }

    private void seedMeetings() throws SQLException {
        for (int i = 1; i <= TARGET && hasTable("meeting_room"); i++) {
            Map<String, Object> mr = base("meeting_room", i);
            mr.put("code", code("MTR", i));
            mr.put("name", "测试会议室" + n(i));
            mr.put("capacity", 8 + (i % 60));
            mr.put("location", "会议中心 " + ((i % 20) + 1) + " 层");
            mr.put("hourly_price", bd(80 + (i % 200)));
            mr.put("status", "ENABLED");
            mr.put("remark", "500条模块测试数据-会议室");
            upsert("meeting_room", mr, "name", "capacity", "location", "hourly_price", "status", "remark");
        }
        List<Long> rooms = idsByCodePrefix("meeting_room", codePrefix("MTR"), TARGET);
        List<Long> tenants = idsByCodePrefix("tenant", codePrefix("TEN"), TARGET);
        for (int i = 1; i <= TARGET && hasTable("meeting_booking"); i++) {
            Long roomId = pick(rooms, i);
            Long tenantId = pick(tenants, i);
            if (roomId == null) continue;
            LocalDateTime start = LocalDateTime.of(2026, 7, 1, 9, 0).plusHours(i * 2L);
            Map<String, Object> bk = base("meeting_booking", i);
            bk.put("code", code("MTB", i));
            bk.put("meeting_room_id", roomId);
            bk.put("tenant_id", tenantId);
            bk.put("start_time", ts(start));
            bk.put("end_time", ts(start.plusHours(2)));
            bk.put("booking_status", i % 3 == 0 ? "CONFIRMED" : "PENDING");
            bk.put("status", "ENABLED");
            bk.put("remark", "500条模块测试数据-会议预约");
            upsert("meeting_booking", bk, "meeting_room_id", "tenant_id", "start_time", "end_time", "booking_status", "status", "remark");
        }
        List<Long> bookings = idsByCodePrefix("meeting_booking", codePrefix("MTB"), TARGET);
        List<Long> bills = idsByCodePrefix("bill", codePrefix("BIL"), TARGET);
        for (int i = 1; i <= TARGET && hasTable("meeting_bill"); i++) {
            Long bookingId = pick(bookings, i);
            if (bookingId == null) continue;
            Map<String, Object> mb = base("meeting_bill", i);
            mb.put("code", code("MTL", i));
            mb.put("booking_id", bookingId);
            mb.put("bill_id", pick(bills, i));
            mb.put("amount", bd(160 + (i % 400)));
            mb.put("status", "ENABLED");
            mb.put("remark", "500条模块测试数据-会议账单");
            upsert("meeting_bill", mb, "booking_id", "bill_id", "amount", "status", "remark");
        }
    }

    private void seedEnergy() throws SQLException {
        List<Long> buildings = idsByCodePrefix("building", codePrefix("BLD"), TARGET);
        List<Long> floors = idsByCodePrefix("building_floor", codePrefix("FLR"), TARGET);
        List<Long> rooms = idsByCodePrefix("building_room", codePrefix("ROM"), TARGET);
        List<Long> tenants = idsByCodePrefix("tenant", codePrefix("TEN"), TARGET);
        for (int i = 1; i <= TARGET && hasTable("energy_meter"); i++) {
            String type = i % 3 == 0 ? "GAS" : (i % 3 == 1 ? "ELECTRIC" : "WATER");
            Map<String, Object> m = base("energy_meter", i);
            m.put("code", code("ENM", i));
            m.put("meter_no", "SEED-METER-" + n(i));
            m.put("meter_name", (type.equals("ELECTRIC") ? "电表" : type.equals("WATER") ? "水表" : "燃气表") + n(i));
            m.put("meter_type", type);
            m.put("building_id", pick(buildings, i));
            m.put("floor_id", pick(floors, i));
            m.put("room_id", pick(rooms, i));
            m.put("tenant_id", pick(tenants, i));
            m.put("unit", type.equals("ELECTRIC") ? "kWh" : type.equals("WATER") ? "m3" : "m3");
            m.put("multiplier", bd(1));
            m.put("status", "ENABLED");
            m.put("remark", "500条模块测试数据-水电燃气表具");
            upsert("energy_meter", m, "meter_no", "meter_name", "meter_type", "building_id", "floor_id", "room_id", "tenant_id", "unit", "multiplier", "status", "remark");
        }
        List<Long> meters = idsByCodePrefix("energy_meter", codePrefix("ENM"), TARGET);
        List<Long> bills = idsByCodePrefix("bill", codePrefix("BIL"), TARGET);
        for (int i = 1; i <= TARGET && hasTable("energy_reading"); i++) {
            Long meterId = pick(meters, i);
            if (meterId == null) continue;
            BigDecimal previous = bd(1000 + i);
            BigDecimal current = bd(1000 + i + 20 + (i % 90));
            BigDecimal usage = current.subtract(previous);
            Map<String, Object> r = base("energy_reading", i);
            r.put("code", code("ENR", i));
            r.put("meter_id", meterId);
            r.put("period_month", "2026-06");
            r.put("reading_date", Date.valueOf(LocalDate.of(2026, 6, 26)));
            r.put("previous_reading", previous);
            r.put("current_reading", current);
            r.put("usage_amount", usage);
            r.put("multiplier", bd(1));
            r.put("unit_price", bd(i % 3 == 1 ? 1.0 : 3.5));
            r.put("settlement_amount", usage.multiply((BigDecimal) r.get("unit_price")));
            r.put("operator_name", "admin");
            r.put("bill_status", i % 2 == 0 ? "GENERATED" : "NOT_GENERATED");
            r.put("bill_id", pick(bills, i));
            r.put("abnormal_flag", i % 25 == 0 ? 1 : 0);
            r.put("abnormal_reason", i % 25 == 0 ? "用量波动较大" : null);
            r.put("abnormal_status", i % 25 == 0 ? "PENDING" : "NORMAL");
            r.put("status", "ACTIVE");
            r.put("remark", "500条模块测试数据-抄表记录");
            upsert("energy_reading", r, "meter_id", "period_month", "reading_date", "previous_reading", "current_reading", "usage_amount", "multiplier", "unit_price", "settlement_amount", "operator_name", "bill_status", "bill_id", "abnormal_flag", "abnormal_reason", "abnormal_status", "status", "remark");
        }
    }

    private void seedOperations() throws SQLException {
        List<Long> tenants = idsByCodePrefix("tenant", codePrefix("TEN"), TARGET);
        List<Long> rooms = idsByCodePrefix("building_room", codePrefix("ROM"), TARGET);
        List<Long> users = idsByCodePrefix("sys_user", codePrefix("USR"), TARGET);
        for (int i = 1; i <= TARGET && hasTable("work_order"); i++) {
            Map<String, Object> w = base("work_order", i);
            w.put("code", code("WOR", i));
            w.put("tenant_id", pick(tenants, i));
            w.put("room_id", pick(rooms, i));
            w.put("reporter_name", "报修人" + n(i));
            w.put("reporter_phone", "174" + String.format("%08d", i));
            w.put("title", "测试工单" + n(i));
            w.put("category", i % 2 == 0 ? "维修报修" : "环境保洁");
            w.put("priority", i % 10 == 0 ? "HIGH" : "NORMAL");
            w.put("work_status", i % 4 == 0 ? "COMPLETED" : "SUBMITTED");
            w.put("assignee_id", pick(users, i));
            w.put("status", "ENABLED");
            w.put("remark", "500条模块测试数据-工单");
            upsert("work_order", w, "tenant_id", "room_id", "reporter_name", "reporter_phone", "title", "category", "priority", "work_status", "assignee_id", "status", "remark");
        }
        List<Long> orders = idsByCodePrefix("work_order", codePrefix("WOR"), TARGET);
        for (int i = 1; i <= TARGET && hasTable("work_order_record"); i++) {
            Map<String, Object> wr = timestamps(new LinkedHashMap<>());
            wr.put("work_order_id", pick(orders, i));
            wr.put("operator_id", pick(users, i));
            wr.put("action", i % 4 == 0 ? "CLOSE" : "CREATE");
            wr.put("content", "测试工单处理记录 " + n(i));
            wr.put("operated_at", ts(LocalDateTime.of(2026, 6, 1, 8, 0).plusHours(i)));
            wr.put("status", "ENABLED");
            wr.put("deleted", 0);
            if (wr.get("work_order_id") != null && !exists("work_order_record", "content = ?", wr.get("content"))) {
                upsert("work_order_record", wr, "operator_id", "action", "content", "operated_at", "status", "deleted");
            }
        }

        for (int i = 1; i <= TARGET && hasTable("inspection_record"); i++) {
            Map<String, Object> ins = base("inspection_record", i);
            ins.put("code", code("INS", i));
            ins.put("inspection_date", Date.valueOf(LocalDate.of(2026, 6, 1).plusDays(i % 27)));
            ins.put("inspector", "巡检员" + n((i % 20) + 1));
            ins.put("inspection_type", i % 3 == 0 ? "SPECIAL" : "DAILY");
            ins.put("area", "测试区域" + ((i % 30) + 1));
            ins.put("target", "测试巡检对象" + n(i));
            ins.put("result", i % 20 == 0 ? "ABNORMAL" : "NORMAL");
            ins.put("problem_description", i % 20 == 0 ? "发现异常，需要跟进" : null);
            ins.put("action_taken", i % 20 == 0 ? "已派单处理" : null);
            ins.put("inspection_status", i % 20 == 0 ? "OPEN" : "CLOSED");
            ins.put("status", "ACTIVE");
            ins.put("remark", "500条模块测试数据-巡检");
            upsert("inspection_record", ins, "inspection_date", "inspector", "inspection_type", "area", "target", "result", "problem_description", "action_taken", "inspection_status", "status", "remark");
        }

        for (int i = 1; i <= TARGET && hasTable("visitor_appointment"); i++) {
            Map<String, Object> v = base("visitor_appointment", i);
            v.put("code", code("VIS", i));
            v.put("tenant_id", pick(tenants, i));
            v.put("visitor_name", "测试访客" + n(i));
            v.put("visitor_phone", "175" + String.format("%08d", i));
            v.put("car_plate_no", i % 2 == 0 ? "沪V" + String.format("%05d", i) : null);
            v.put("visit_time", ts(LocalDateTime.of(2026, 7, 1, 9, 0).plusHours(i)));
            v.put("appointment_status", i % 3 == 0 ? "APPROVED" : "PENDING");
            v.put("status", "ENABLED");
            v.put("remark", "500条模块测试数据-访客预约");
            upsert("visitor_appointment", v, "tenant_id", "visitor_name", "visitor_phone", "car_plate_no", "visit_time", "appointment_status", "status", "remark");
        }
        List<Long> visits = idsByCodePrefix("visitor_appointment", codePrefix("VIS"), TARGET);
        for (int i = 1; i <= TARGET && hasTable("visitor_pass_record"); i++) {
            Map<String, Object> pass = timestamps(new LinkedHashMap<>());
            pass.put("appointment_id", pick(visits, i));
            pass.put("pass_type", i % 2 == 0 ? "IN" : "OUT");
            pass.put("pass_time", ts(LocalDateTime.of(2026, 7, 1, 10, 0).plusHours(i)));
            pass.put("gate_name", "测试门岗" + ((i % 4) + 1));
            pass.put("operator_id", pick(users, i));
            pass.put("status", "ENABLED");
            pass.put("deleted", 0);
            if (pass.get("appointment_id") != null && !exists("visitor_pass_record", "appointment_id = ? AND pass_time = ? AND gate_name = ?", pass.get("appointment_id"), pass.get("pass_time"), pass.get("gate_name"))) {
                upsert("visitor_pass_record", pass, "pass_type", "pass_time", "gate_name", "operator_id", "status", "deleted");
            }
        }

        for (int i = 1; i <= TARGET && hasTable("staff_checkin"); i++) {
            Long userId = pick(users, i);
            if (userId == null) continue;
            Map<String, Object> s = base("staff_checkin", i);
            s.put("code", code("CHK", i));
            s.put("sys_user_id", userId);
            s.put("checkin_type", i % 2 == 0 ? "ON_DUTY" : "OFF_DUTY");
            s.put("checkin_time", ts(LocalDateTime.of(2026, 6, 1, 8, 30).plusHours(i)));
            s.put("location", "德汇创新中心");
            s.put("longitude", new BigDecimal("121.473701"));
            s.put("latitude", new BigDecimal("31.230416"));
            s.put("status", "ENABLED");
            s.put("remark", "500条模块测试数据-员工签到");
            upsert("staff_checkin", s, "sys_user_id", "checkin_type", "checkin_time", "location", "longitude", "latitude", "status", "remark");
        }
    }

    private void seedInvestment() throws SQLException {
        for (int i = 1; i <= TARGET && hasTable("investment_display"); i++) {
            Map<String, Object> d = base("investment_display", i);
            d.put("code", code("IVD", i));
            d.put("title", "测试招商内容" + n(i));
            d.put("content", "德汇创新中心测试招商内容，用于列表、筛选、分页和详情测试。" + n(i));
            d.put("publish_status", i % 3 == 0 ? "DRAFT" : "PUBLISHED");
            d.put("sort_order", i);
            d.put("status", "ENABLED");
            d.put("remark", "500条模块测试数据-招商内容");
            upsert("investment_display", d, "title", "content", "publish_status", "sort_order", "status", "remark");
        }
        for (int i = 1; i <= TARGET && hasTable("investment_lead"); i++) {
            Map<String, Object> l = base("investment_lead", i);
            l.put("code", code("IVL", i));
            l.put("name", "招商线索联系人" + n(i));
            l.put("phone", "176" + String.format("%08d", i));
            l.put("company_name", "测试招商客户" + n(i));
            l.put("intended_room", "SEED-" + n(i));
            l.put("desired_area", bd(100 + (i % 500)));
            l.put("intended_use", i % 3 == 0 ? "展示展厅" : "办公");
            l.put("source", i % 2 == 0 ? "MINIPROGRAM" : "PHONE");
            l.put("lead_status", i % 4 == 0 ? "FOLLOWING" : "NEW");
            l.put("status", "ENABLED");
            l.put("remark", "500条模块测试数据-招商线索");
            upsert("investment_lead", l, "name", "phone", "company_name", "intended_room", "desired_area", "intended_use", "source", "lead_status", "status", "remark");
        }
        List<Long> leads = idsByCodePrefix("investment_lead", codePrefix("IVL"), TARGET);
        List<Long> users = idsByCodePrefix("sys_user", codePrefix("USR"), TARGET);
        for (int i = 1; i <= TARGET && hasTable("investment_follow_record"); i++) {
            Map<String, Object> f = timestamps(new LinkedHashMap<>());
            f.put("lead_id", pick(leads, i));
            f.put("follower_id", pick(users, i));
            f.put("follow_type", i % 2 == 0 ? "PHONE" : "VISIT");
            f.put("content", "测试招商跟进记录 " + n(i));
            f.put("next_follow_at", ts(LocalDateTime.of(2026, 7, 1, 9, 0).plusDays(i % 60)));
            f.put("status", "ENABLED");
            f.put("deleted", 0);
            if (f.get("lead_id") != null && !exists("investment_follow_record", "content = ?", f.get("content"))) {
                upsert("investment_follow_record", f, "follower_id", "follow_type", "content", "next_follow_at", "status", "deleted");
            }
        }
    }

    private void seedNotices() throws SQLException {
        for (int i = 1; i <= TARGET && hasTable("notice"); i++) {
            Map<String, Object> n = base("notice", i);
            n.put("code", code("NTC", i));
            n.put("title", "测试公告" + n(i));
            n.put("content", "这是一条用于公告管理分页、搜索、发布状态测试的数据。" + n(i));
            n.put("notice_type", i % 3 == 0 ? "MAINTENANCE" : "GENERAL");
            n.put("publish_status", i % 2 == 0 ? "PUBLISHED" : "DRAFT");
            n.put("published_at", ts(LocalDateTime.of(2026, 6, 1, 9, 0).plusHours(i)));
            n.put("status", "ENABLED");
            n.put("remark", "500条模块测试数据-公告");
            upsert("notice", n, "title", "content", "notice_type", "publish_status", "published_at", "status", "remark");
        }
    }

    private void seedEquipment() throws SQLException {
        List<Long> rooms = idsByCodePrefix("building_room", codePrefix("ROM"), TARGET);
        for (int i = 1; i <= TARGET && hasTable("equipment"); i++) {
            Map<String, Object> e = base("equipment", i);
            e.put("code", code("EQP", i));
            e.put("equipment_name", "测试设备" + n(i));
            e.put("equipment_code", "EQ-SEED-" + n(i));
            e.put("equipment_type", i % 3 == 0 ? "FIRE" : (i % 3 == 1 ? "POWER" : "HVAC"));
            e.put("location", "测试房间ID " + Objects.toString(pick(rooms, i), "-"));
            e.put("manufacturer", "测试厂商");
            e.put("model", "MODEL-" + n(i));
            e.put("install_date", Date.valueOf(LocalDate.of(2025, 1, 1).plusDays(i)));
            e.put("status", i % 20 == 0 ? "REPAIRING" : "NORMAL");
            e.put("remark", "500条模块测试数据-设备台账");
            upsert("equipment", e, "equipment_name", "equipment_code", "equipment_type", "location", "manufacturer", "model", "install_date", "status", "remark");
        }
    }

    private void printCounts() throws SQLException {
        List<String> tables = List.of(
                "sys_user", "mobile_user", "building", "building_floor", "building_room",
                "tenant", "tenant_contact", "contract", "bill", "bill_payment_record", "bill_invoice",
                "parking_space", "parking_assignment", "parking_bill",
                "meeting_room", "meeting_booking", "meeting_bill",
                "energy_meter", "energy_reading", "work_order", "work_order_record",
                "inspection_record", "visitor_appointment", "visitor_pass_record",
                "investment_display", "investment_lead", "investment_follow_record",
                "notice", "staff_checkin", "equipment"
        );
        System.out.println("Seed completed. Current active row counts:");
        for (String table : tables) {
            if (!hasTable(table)) continue;
            System.out.printf("%-28s %d%n", table, countActive(table));
        }
    }

    private Map<String, Object> base(String table, int i) {
        Map<String, Object> row = timestamps(new LinkedHashMap<>());
        row.put("code", code(table.toUpperCase(), i));
        row.put("created_by", 0L);
        row.put("updated_by", 0L);
        row.put("deleted", 0);
        return row;
    }

    private Map<String, Object> timestamps(Map<String, Object> row) {
        Timestamp now = ts(LocalDateTime.now());
        row.put("created_at", now);
        row.put("updated_at", now);
        return row;
    }

    private void upsert(String table, Map<String, Object> values, String... updateColumns) throws SQLException {
        if (!hasTable(table)) return;
        Set<String> columns = columns(table);
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (columns.contains(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        if (filtered.isEmpty()) return;

        StringJoiner colNames = new StringJoiner(", ");
        StringJoiner placeholders = new StringJoiner(", ");
        for (String col : filtered.keySet()) {
            colNames.add("`" + col + "`");
            placeholders.add("?");
        }
        List<String> updates = new ArrayList<>();
        for (String col : updateColumns) {
            if (filtered.containsKey(col) && columns.contains(col)) {
                updates.add("`" + col + "` = VALUES(`" + col + "`)");
            }
        }
        if (updates.isEmpty()) {
            updates.add("`updated_at` = `updated_at`");
        }
        String sql = "INSERT INTO `" + table + "` (" + colNames + ") VALUES (" + placeholders + ") ON DUPLICATE KEY UPDATE " + String.join(", ", updates);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Object value : filtered.values()) {
                ps.setObject(idx++, value);
            }
            ps.executeUpdate();
        }
    }

    private boolean hasTable(String table) throws SQLException {
        return !columns(table).isEmpty();
    }

    private Set<String> columns(String table) throws SQLException {
        if (tableColumns.containsKey(table)) return tableColumns.get(table);
        Set<String> cols = new LinkedHashSet<>();
        String sql = "SELECT column_name FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? ORDER BY ordinal_position";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) cols.add(rs.getString(1));
            }
        }
        tableColumns.put(table, cols);
        return cols;
    }

    private List<Long> idsByCodePrefix(String table, String prefix, int limit) throws SQLException {
        List<Long> ids = new ArrayList<>();
        if (!hasTable(table) || !columns(table).contains("code")) return ids;
        String sql = "SELECT id FROM `" + table + "` WHERE code LIKE ? AND deleted = 0 ORDER BY id LIMIT ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, prefix + "%");
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    private Long firstId(String table, String where) throws SQLException {
        if (!hasTable(table)) return null;
        String sql = "SELECT id FROM `" + table + "` WHERE deleted = 0 AND " + where + " ORDER BY id LIMIT 1";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : null;
        }
    }

    private long countActive(String table) throws SQLException {
        String sql = columns(table).contains("deleted") ? "SELECT COUNT(*) FROM `" + table + "` WHERE deleted = 0" : "SELECT COUNT(*) FROM `" + table + "`";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private boolean exists(String table, String where, Object... params) throws SQLException {
        if (!hasTable(table)) return false;
        String sql = "SELECT 1 FROM `" + table + "` WHERE deleted = 0 AND " + where + " LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static Long pick(List<Long> ids, int i) {
        if (ids == null || ids.isEmpty()) return null;
        return ids.get((i - 1) % ids.size());
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }

    private static Timestamp ts(LocalDateTime value) {
        return Timestamp.valueOf(value);
    }

    private static String code(String module, int i) {
        return PREFIX + "-" + module + "-" + n(i);
    }

    private static String codePrefix(String module) {
        return PREFIX + "-" + module + "-";
    }

    private static String n(int i) {
        return String.format("%03d", i);
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

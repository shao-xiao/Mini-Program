import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

public class DevDelete400TestData {
    private static final int LIMIT = 400;
    private static final String SEED_PREFIX = "SEED500-20260627";
    private static final String DEFAULT_URL = "jdbc:mysql://127.0.0.1:3306/dehui_property_dev?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
    private static final String DEFAULT_USER = "dehui";
    private static final String DEFAULT_PASSWORD = "dehui_dev_password";

    private final Connection conn;

    public static void main(String[] args) throws Exception {
        String url = firstNonBlank(System.getenv("DEHUI_DB_URL"), DEFAULT_URL);
        String user = firstNonBlank(System.getenv("DEHUI_DB_USER"), DEFAULT_USER);
        String password = firstNonBlank(System.getenv("DEHUI_DB_PASSWORD"), DEFAULT_PASSWORD);

        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            conn.setAutoCommit(false);
            DevDelete400TestData cleaner = new DevDelete400TestData(conn);
            cleaner.run();
            conn.commit();
        }
    }

    private DevDelete400TestData(Connection conn) {
        this.conn = conn;
    }

    private void run() throws SQLException {
        Map<String, Integer> deleted = new LinkedHashMap<>();

        delete(deleted, "visitor_pass_record", "appointment_id", "visitor_appointment", "p.code LIKE ?", SEED_PREFIX + "-VIS-%");
        delete(deleted, "investment_follow_record", "lead_id", "investment_lead", "p.code LIKE ?", SEED_PREFIX + "-IVL-%");
        delete(deleted, "work_order_record", "work_order_id", "work_order", "p.code LIKE ?", SEED_PREFIX + "-WOR-%");
        delete(deleted, "bill_payment_record", "bill_id", "bill", "p.code LIKE ?", SEED_PREFIX + "-BIL-%");
        delete(deleted, "bill_invoice", "bill_id", "bill", "p.code LIKE ?", SEED_PREFIX + "-BIL-%");
        delete(deleted, "meeting_bill", "booking_id", "meeting_booking", "p.code LIKE ?", SEED_PREFIX + "-MTB-%");
        delete(deleted, "energy_reading", "code LIKE ? OR code LIKE ?", SEED_PREFIX + "-ENR-%", "BULK-TEST-%");
        delete(deleted, "parking_bill", "code LIKE ?", SEED_PREFIX + "-PKB-%");
        delete(deleted, "meeting_booking", "code LIKE ?", SEED_PREFIX + "-MTB-%");
        delete(deleted, "work_order", "code LIKE ?", SEED_PREFIX + "-WOR-%");
        delete(deleted, "inspection_record", "code LIKE ?", SEED_PREFIX + "-INS-%");
        delete(deleted, "visitor_appointment", "code LIKE ?", SEED_PREFIX + "-VIS-%");
        delete(deleted, "investment_lead", "code LIKE ?", SEED_PREFIX + "-IVL-%");
        delete(deleted, "investment_display", "code LIKE ?", SEED_PREFIX + "-IVD-%");
        delete(deleted, "notice", "code LIKE ?", SEED_PREFIX + "-NTC-%");
        delete(deleted, "staff_checkin", "code LIKE ?", SEED_PREFIX + "-CHK-%");
        delete(deleted, "parking_assignment", "code LIKE ?", SEED_PREFIX + "-PKA-%");
        delete(deleted, "parking_space", "code LIKE ?", SEED_PREFIX + "-PKS-%");
        delete(deleted, "meeting_room", "code LIKE ?", SEED_PREFIX + "-MTR-%");
        delete(deleted, "energy_meter", "code LIKE ? OR meter_no LIKE ?", SEED_PREFIX + "-ENM-%", "BULK-TEST-%");
        delete(deleted, "equipment", "code LIKE ? OR equipment_code LIKE ?", SEED_PREFIX + "-EQP-%", "EQ-SEED-%");
        delete(deleted, "contract", "code LIKE ?", SEED_PREFIX + "-CON-%");
        delete(deleted, "bill", "code LIKE ?", SEED_PREFIX + "-BIL-%");
        delete(deleted, "tenant_contact", "code LIKE ?", SEED_PREFIX + "-TCO-%");
        delete(deleted, "mobile_user_bind", "remark LIKE ?", "500%");
        delete(deleted, "tenant", "code LIKE ?", SEED_PREFIX + "-TEN-%");
        delete(deleted, "building_room", "code LIKE ?", SEED_PREFIX + "-ROM-%");
        delete(deleted, "building_floor", "code LIKE ?", SEED_PREFIX + "-FLR-%");
        delete(deleted, "building", "code LIKE ?", SEED_PREFIX + "-BLD-%");
        delete(deleted, "sys_user_role", "user_id", "sys_user", "p.code LIKE ?", SEED_PREFIX + "-USR-%");
        delete(deleted, "sys_user", "code LIKE ?", SEED_PREFIX + "-USR-%");
        delete(deleted, "mobile_user", "code LIKE ?", SEED_PREFIX + "-MOB-%");

        System.out.println("Deleted rows, limited to " + LIMIT + " per table:");
        for (Map.Entry<String, Integer> entry : deleted.entrySet()) {
            System.out.printf("%-28s %d%n", entry.getKey(), entry.getValue());
        }
    }

    private void delete(Map<String, Integer> deleted, String table, String where, Object... params) throws SQLException {
        if (!hasTable(table) || !hasColumn(table, "deleted")) {
            return;
        }
        String sql = "UPDATE `" + table + "` SET deleted = 1 WHERE deleted = 0 AND id IN (" +
                "SELECT id FROM (SELECT id FROM `" + table + "` WHERE deleted = 0 AND (" + where + ") ORDER BY id LIMIT " + LIMIT + ") t" +
                ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            deleted.put(table, ps.executeUpdate());
        }
    }

    private void delete(Map<String, Integer> deleted, String table, String fkColumn, String parentTable, String parentWhere, Object... params) throws SQLException {
        if (!hasTable(table) || !hasTable(parentTable) || !hasColumn(table, "deleted") || !hasColumn(table, fkColumn)) {
            return;
        }
        String sql = "UPDATE `" + table + "` SET deleted = 1 WHERE deleted = 0 AND id IN (" +
                "SELECT id FROM (SELECT c.id FROM `" + table + "` c INNER JOIN `" + parentTable + "` p ON p.id = c.`" + fkColumn + "` " +
                "WHERE c.deleted = 0 AND p.deleted = 0 AND (" + parentWhere + ") ORDER BY c.id LIMIT " + LIMIT + ") t" +
                ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            deleted.put(table, ps.executeUpdate());
        }
    }

    private void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    private boolean hasTable(String table) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private boolean hasColumn(String table, String column) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.LinkedHashMap;
import java.util.Map;

public class DevDeleteEnergyEquipment400 {
    private static final int LIMIT = 400;
    private static final String SEED_PREFIX = "SEED500-20260627";
    private static final String URL = "jdbc:mysql://127.0.0.1:3306/dehui_property_dev?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
    private static final String USER = "dehui";
    private static final String PASSWORD = "dehui_dev_password";

    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            conn.setAutoCommit(false);
            Map<String, Integer> deleted = new LinkedHashMap<>();
            delete(conn, deleted, "energy_reading", "code LIKE ? OR code LIKE ?", SEED_PREFIX + "-ENR-%", "BULK-TEST-%");
            delete(conn, deleted, "energy_meter", "code LIKE ? OR meter_no LIKE ?", SEED_PREFIX + "-ENM-%", "BULK-TEST-%");
            delete(conn, deleted, "equipment", "code LIKE ? OR equipment_code LIKE ?", SEED_PREFIX + "-EQP-%", "EQ-SEED-%");
            conn.commit();
            System.out.println("Deleted energy/equipment rows, limited to " + LIMIT + " per table:");
            for (Map.Entry<String, Integer> entry : deleted.entrySet()) {
                System.out.printf("%-18s %d%n", entry.getKey(), entry.getValue());
            }
        }
    }

    private static void delete(Connection conn, Map<String, Integer> deleted, String table, String where, Object... params) throws Exception {
        String sql = "UPDATE `" + table + "` SET deleted = 1 WHERE deleted = 0 AND id IN (" +
                "SELECT id FROM (SELECT id FROM `" + table + "` WHERE deleted = 0 AND (" + where + ") ORDER BY id LIMIT " + LIMIT + ") t" +
                ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            deleted.put(table, ps.executeUpdate());
        }
    }
}

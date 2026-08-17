import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class CheckTestCounts {
    private static final String URL = "jdbc:mysql://127.0.0.1:3306/dehui_property_dev?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
    private static final String USER = "dehui";
    private static final String PASSWORD = "dehui_dev_password";

    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD)) {
            String[] tables = {
                    "energy_meter", "energy_reading", "equipment", "bill", "tenant", "building",
                    "parking_bill", "meeting_room", "work_order", "investment_lead"
            };
            for (String table : tables) {
                printColumns(c, table);
                printActive(c, table);
                printSamples(c, table);
            }
        }
    }

    private static void printColumns(Connection c, String table) throws Exception {
        String sql = "SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? ORDER BY ordinal_position";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                System.out.print(table + " cols:");
                while (rs.next()) {
                    System.out.print(" " + rs.getString(1));
                }
                System.out.println();
            }
        }
    }

    private static void printActive(Connection c, String table) throws Exception {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM `" + table + "` WHERE deleted=0")) {
            rs.next();
            System.out.println(table + " active=" + rs.getLong(1));
        } catch (Exception ex) {
            System.out.println(table + " active=ERR " + ex.getMessage());
        }
    }

    private static void printSamples(Connection c, String table) throws Exception {
        String sql = "SELECT id, code FROM `" + table + "` WHERE deleted=0 ORDER BY id DESC LIMIT 5";
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            System.out.print(table + " samples:");
            while (rs.next()) {
                System.out.print(" [" + rs.getLong("id") + "," + rs.getString("code") + "]");
            }
            System.out.println();
        }
    }
}

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class CheckRbacEffect {
    private static final String URL = "jdbc:mysql://127.0.0.1:3306/dehui_property_dev?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
    private static final String USER = "dehui";
    private static final String PASSWORD = "dehui_dev_password";

    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD)) {
            printUsers(c);
            printRoleMenus(c);
        }
    }

    private static void printUsers(Connection c) throws Exception {
        String sql = """
                SELECT u.id, u.username, u.real_name, u.status,
                       GROUP_CONCAT(r.code ORDER BY r.code) roles
                FROM sys_user u
                LEFT JOIN sys_user_role ur ON ur.user_id = u.id AND ur.deleted = 0
                LEFT JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0
                WHERE u.deleted = 0
                GROUP BY u.id, u.username, u.real_name, u.status
                ORDER BY u.id
                LIMIT 20
                """;
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            System.out.println("users:");
            while (rs.next()) {
                System.out.printf("%-4d %-16s %-20s %-10s %s%n",
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("real_name"),
                        rs.getString("status"),
                        rs.getString("roles"));
            }
        }
    }

    private static void printRoleMenus(Connection c) throws Exception {
        String sql = """
                SELECT r.code role_code,
                       COUNT(DISTINCT m.id) menu_count,
                       GROUP_CONCAT(m.code ORDER BY m.sort_order, m.id SEPARATOR ',') menus
                FROM sys_role r
                LEFT JOIN sys_role_menu rm ON rm.role_id = r.id AND rm.deleted = 0 AND rm.status = 'ENABLED'
                LEFT JOIN sys_menu m ON m.id = rm.menu_id AND m.deleted = 0 AND m.status = 'ENABLED'
                WHERE r.deleted = 0 AND r.status = 'ENABLED'
                GROUP BY r.id, r.code
                ORDER BY r.code
                """;
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            System.out.println("role menus:");
            while (rs.next()) {
                System.out.printf("%-18s count=%-3d %s%n",
                        rs.getString("role_code"),
                        rs.getInt("menu_count"),
                        rs.getString("menus"));
            }
        }
    }
}

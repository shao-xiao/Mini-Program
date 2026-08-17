import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class CheckMobileWorkOrderFlow {
    private static final String URL = "jdbc:mysql://127.0.0.1:3306/dehui_property_dev?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
    private static final String USER = "dehui";
    private static final String PASSWORD = "dehui_dev_password";

    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD)) {
            print(c, "internal binds", """
                    SELECT mu.id mobile_user_id, mu.code, mu.user_type, b.sys_user_id, su.username
                    FROM mobile_user mu
                    INNER JOIN mobile_user_bind b ON b.mobile_user_id = mu.id AND b.bind_type = 'INTERNAL' AND b.deleted = 0
                    LEFT JOIN sys_user su ON su.id = b.sys_user_id
                    WHERE mu.deleted = 0
                    ORDER BY mu.id
                    LIMIT 10
                    """);
            print(c, "assigned workorders", """
                    SELECT w.id, w.code, w.title, w.work_status, w.assignee_id, su.username
                    FROM work_order w
                    LEFT JOIN sys_user su ON su.id = w.assignee_id
                    WHERE w.deleted = 0 AND w.assignee_id IS NOT NULL
                    ORDER BY w.updated_at DESC, w.id DESC
                    LIMIT 10
                    """);
        }
    }

    private static void print(Connection c, String title, String sql) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            System.out.println(title + ":");
            int columns = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                StringBuilder line = new StringBuilder();
                for (int i = 1; i <= columns; i++) {
                    if (i > 1) line.append(" | ");
                    line.append(rs.getMetaData().getColumnLabel(i)).append("=").append(rs.getObject(i));
                }
                System.out.println(line);
            }
        }
    }
}

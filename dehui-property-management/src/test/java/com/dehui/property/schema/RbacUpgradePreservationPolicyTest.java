package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class RbacUpgradePreservationPolicyTest {

    private static final int POLICY_START_VERSION = 61;
    private static final Path MIGRATION_DIRECTORY =
            Path.of("src/main/resources/db/migration");
    private static final Pattern MIGRATION_FILE =
            Pattern.compile("^V(\\d+)__.+\\.sql$", Pattern.CASE_INSENSITIVE);
    private static final String PROTECTED_TABLE =
            "(?:`?[a-zA-Z0-9_]+`?\\.)?`?(?:sys_user_role|sys_role_menu|sys_role_permission)`?";
    private static final Pattern DESTRUCTIVE_MUTATION = Pattern.compile(
            "(?is)\\b(?:"
                    + "DELETE\\s+FROM\\s+"
                    + "|UPDATE\\s+"
                    + "|REPLACE\\s+INTO\\s+"
                    + "|TRUNCATE\\s+(?:TABLE\\s+)?"
                    + "|DROP\\s+TABLE\\s+(?:IF\\s+EXISTS\\s+)?"
                    + ")"
                    + PROTECTED_TABLE
    );
    private static final Pattern ASSIGNMENT_INSERT = Pattern.compile(
            "(?is)\\bINSERT\\s+(IGNORE\\s+)?INTO\\s+" + PROTECTED_TABLE
    );

    @Test
    void migrationsAfterPolicyBaselinePreserveExistingRbacAssignments() throws Exception {
        for (Path migration : protectedMigrations()) {
            assertAssignmentSafe(
                    Files.readString(migration),
                    migration.getFileName().toString()
            );
        }
    }

    @Test
    void policyRejectsDestructiveOrOverwritingAssignmentMigrations() {
        List<String> unsafeSql = List.of(
                "DELETE FROM sys_user_role WHERE user_id = 1;",
                "UPDATE sys_role_permission SET deleted = 1;",
                "TRUNCATE TABLE sys_role_menu;",
                "REPLACE INTO sys_user_role (user_id, role_id) VALUES (1, 2);",
                "INSERT INTO sys_role_permission (role_id, permission_id) VALUES (1, 2);",
                "INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (1, 2);",
                """
                INSERT INTO sys_role_menu (role_id, menu_id)
                VALUES (1, 2)
                ON DUPLICATE KEY UPDATE deleted = 0;
                """
        );

        unsafeSql.forEach(sql -> assertThrows(
                AssertionError.class,
                () -> assertAssignmentSafe(sql, "unsafe.sql")
        ));
    }

    @Test
    void policyAllowsCatalogChangesAndAdditiveAssignmentCompatibility() {
        assertDoesNotThrow(() -> assertAssignmentSafe(
                """
                INSERT INTO sys_permission (code, name)
                VALUES ('example:view', '查看示例')
                ON DUPLICATE KEY UPDATE name = VALUES(name);

                INSERT IGNORE INTO sys_role_permission
                    (role_id, permission_id, status, created_by, updated_by)
                SELECT source.role_id, 99, 'ENABLED', 0, 0
                FROM sys_role_permission source
                WHERE source.permission_id = 10
                  AND NOT EXISTS (
                      SELECT 1
                      FROM sys_role_permission existing
                      WHERE existing.role_id = source.role_id
                        AND existing.permission_id = 99
                  );
                """,
                "safe.sql"
        ));
    }

    @Test
    void applicationExplicitlyDisablesAutomaticSqlInitialization() throws IOException {
        String application = Files.readString(
                Path.of("src/main/resources/application.yml")
        ).replace("\r\n", "\n").toLowerCase(Locale.ROOT);

        assertTrue(application.contains("sql:\n    init:\n      mode: never"));
        assertTrue(Files.notExists(Path.of("src/main/resources/data.sql")));
        assertTrue(Files.notExists(Path.of("src/main/resources/schema.sql")));
    }

    private static List<Path> protectedMigrations() throws IOException {
        try (Stream<Path> files = Files.list(MIGRATION_DIRECTORY)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> migrationVersion(path) >= POLICY_START_VERSION)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private static int migrationVersion(Path path) {
        Matcher matcher = MIGRATION_FILE.matcher(path.getFileName().toString());
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    private static void assertAssignmentSafe(String sql, String source) {
        String executableSql = sql
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)--.*$", " ");

        Matcher destructive = DESTRUCTIVE_MUTATION.matcher(executableSql);
        if (destructive.find()) {
            throw new AssertionError(
                    source + " must not mutate existing RBAC assignments: "
                            + destructive.group().trim()
            );
        }

        for (String statement : executableSql.split(";")) {
            Matcher insert = ASSIGNMENT_INSERT.matcher(statement);
            if (!insert.find()) {
                continue;
            }
            if (insert.group(1) == null || !statement.toUpperCase(Locale.ROOT).contains("NOT EXISTS")) {
                throw new AssertionError(
                        source
                                + " may add RBAC compatibility assignments only with "
                                + "INSERT IGNORE and a NOT EXISTS history check"
                );
            }
        }
    }
}

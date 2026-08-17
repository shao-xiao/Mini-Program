package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class BusinessDataUpgradePreservationPolicyTest {

    private static final int POLICY_START_VERSION = 61;
    private static final Path MIGRATION_DIRECTORY =
            Path.of("src/main/resources/db/migration");
    private static final Path PROTECTED_TABLE_CATALOG =
            Path.of("scripts/protected_asset_business_tables.txt");
    private static final List<String> PROTECTED_TABLES = loadProtectedTables();
    private static final Map<String, Set<String>> REQUIRED_TABLES_BY_DOMAIN = Map.of(
            "spaces", Set.of("building", "building_floor", "building_room"),
            "equipment", Set.of("equipment"),
            "energy", Set.of(
                    "energy_bill_operation_log",
                    "energy_meter",
                    "energy_meter_binding",
                    "energy_price_setting",
                    "energy_reading",
                    "energy_reading_audit"
            ),
            "parking", Set.of(
                    "parking_assignment",
                    "parking_bill",
                    "parking_space",
                    "parking_space_type_option"
            ),
            "meeting", Set.of(
                    "meeting_bill",
                    "meeting_booking",
                    "meeting_rate_rule",
                    "meeting_room"
            ),
            "finance", Set.of(
                    "asset_charge_basis",
                    "bill",
                    "bill_audit_log",
                    "bill_invoice",
                    "bill_invoice_attachment",
                    "bill_line_item",
                    "bill_payment_record",
                    "billing_plan",
                    "billing_plan_item",
                    "fee_rule",
                    "fee_rule_version"
            ),
            "shared-fields-and-files", Set.of(
                    "custom_field_definition",
                    "custom_field_definition_history",
                    "custom_field_option",
                    "custom_field_value",
                    "file_upload",
                    "module_field_setting"
            )
    );
    private static final Pattern VERSIONED_MIGRATION_FILE = Pattern.compile(
            "^V(\\d+)(?:[._][0-9]+)*__.+\\.sql$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern REPEATABLE_MIGRATION_FILE =
            Pattern.compile("^R__.+\\.sql$", Pattern.CASE_INSENSITIVE);
    private static final String PROTECTED_TABLE_NAME = PROTECTED_TABLES.stream()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .map(Pattern::quote)
            .collect(Collectors.joining("|", "(?:", ")"));
    private static final String PROTECTED_TABLE =
            "(?:`?[a-zA-Z0-9_]+`?\\.)?`?"
                    + PROTECTED_TABLE_NAME
                    + "`?(?![a-zA-Z0-9_])";
    private static final Pattern DATA_MUTATION = Pattern.compile(
            "(?is)\\b(?:"
                    + "INSERT(?:\\s+(?:LOW_PRIORITY|DELAYED|HIGH_PRIORITY|IGNORE))*\\s+INTO"
                    + "|REPLACE(?:\\s+(?:LOW_PRIORITY|DELAYED))*\\s+INTO"
                    + "|UPDATE(?:\\s+(?:LOW_PRIORITY|IGNORE))*"
                    + "|DELETE\\s+FROM"
                    + "|DELETE\\s+`?[a-zA-Z0-9_]+`?\\s+FROM"
                    + "|TRUNCATE(?:\\s+TABLE)?"
                    + ")\\s+"
                    + PROTECTED_TABLE
    );
    private static final Pattern DELETE_USING = Pattern.compile(
            "(?is)\\bDELETE\\s+FROM\\s+`?[a-zA-Z0-9_]+`?"
                    + "\\s+USING\\s+"
                    + PROTECTED_TABLE
    );
    private static final Pattern MUTATING_STATEMENT_REFERENCING_PROTECTED =
            Pattern.compile(
                    "(?is)^\\s*(?:UPDATE|DELETE\\b).*?"
                            + PROTECTED_TABLE
            );
    private static final Pattern LOAD_DATA = Pattern.compile(
            "(?is)\\bLOAD\\s+DATA\\b.*?\\bINTO\\s+TABLE\\s+"
                    + PROTECTED_TABLE
    );
    private static final Pattern DROP_TABLE = Pattern.compile(
            "(?is)\\bDROP\\s+(?:TEMPORARY\\s+)?TABLE\\s+"
                    + "(?:IF\\s+EXISTS\\s+)?.*?\\b"
                    + PROTECTED_TABLE_NAME
                    + "\\b"
    );
    private static final Pattern DESTRUCTIVE_ALTER = Pattern.compile(
            "(?is)\\bALTER\\s+TABLE\\s+"
                    + PROTECTED_TABLE
                    + "\\s+.*?\\b(?:"
                    + "DROP|TRUNCATE|RENAME|EXCHANGE|MODIFY|CHANGE|CONVERT"
                    + "|DISCARD|IMPORT|FORCE"
                    + ")\\b"
    );
    private static final Pattern RENAME_TABLE = Pattern.compile(
            "(?is)\\bRENAME\\s+TABLE\\b.*?"
                    + PROTECTED_TABLE
    );
    private static final Pattern ALTER_RENAME_TO_PROTECTED = Pattern.compile(
            "(?is)\\bALTER\\s+TABLE\\s+.*?\\bRENAME\\s+(?:TO\\s+)?"
                    + PROTECTED_TABLE
    );
    private static final Pattern DROP_INDEX = Pattern.compile(
            "(?is)\\bDROP\\s+INDEX\\s+`?[a-zA-Z0-9_]+`?\\s+ON\\s+"
                    + PROTECTED_TABLE
    );
    private static final Pattern CREATE_PROTECTED_TABLE = Pattern.compile(
            "(?is)\\bCREATE\\s+(?:OR\\s+REPLACE\\s+)?"
                    + "(?:TEMPORARY\\s+)?TABLE\\s+"
                    + "(?:IF\\s+NOT\\s+EXISTS\\s+)?"
                    + PROTECTED_TABLE
    );

    @Test
    void migrationsAfterPolicyBaselineDoNotMutateAssetOrFinanceBusinessData()
            throws Exception {
        for (Path migration : protectedMigrations()) {
            assertBusinessDataSafe(
                    Files.readString(migration),
                    migration.getFileName().toString()
            );
        }
    }

    @Test
    void protectedCatalogCoversAllFiveAssetModulesFinanceAndSharedRecords() {
        REQUIRED_TABLES_BY_DOMAIN.forEach((domain, tables) -> assertTrue(
                PROTECTED_TABLES.containsAll(tables),
                () -> domain + " protection is incomplete"
        ));
        assertTrue(
                PROTECTED_TABLES.size() == Set.copyOf(PROTECTED_TABLES).size(),
                "protected table catalog must not contain duplicates"
        );
    }

    @Test
    void policyRejectsAutomaticChangesForEveryProtectedTable() {
        for (String table : PROTECTED_TABLES) {
            List<String> unsafeSql = List.of(
                    "DELETE FROM `" + table + "` WHERE id = 1;",
                    "UPDATE IGNORE `" + table + "` SET id = id;",
                    "INSERT IGNORE INTO `" + table + "` (id) VALUES (1);",
                    "REPLACE INTO `" + table + "` (id) VALUES (1);",
                    "TRUNCATE TABLE `" + table + "`;",
                    "DROP TABLE IF EXISTS `" + table + "`;",
                    "ALTER TABLE `" + table + "` DROP COLUMN legacy_value;",
                    "ALTER TABLE `" + table + "` RENAME TO `" + table + "_old`;",
                    "ALTER TABLE `" + table + "` MODIFY COLUMN id BIGINT;",
                    "ALTER TABLE `" + table + "` CHANGE COLUMN id legacy_id BIGINT;",
                    "ALTER TABLE `" + table + "` CONVERT TO CHARACTER SET latin1;",
                    "DROP INDEX `idx_legacy` ON `" + table + "`;",
                    "RENAME TABLE `" + table + "` TO `" + table + "_old`;",
                    "CREATE OR REPLACE TABLE `" + table + "` (id BIGINT);"
            );

            unsafeSql.forEach(sql -> assertThrows(
                    AssertionError.class,
                    () -> assertBusinessDataSafe(sql, "unsafe.sql"),
                    sql
            ));
        }
    }

    @Test
    void policyRejectsQualifiedBulkAndPartitionMutations() {
        List<String> unsafeSql = List.of(
                "DELETE room FROM building_room room WHERE room.deleted = 1;",
                "DELETE FROM meter USING dehui_property_dev.energy_meter meter;",
                "UPDATE LOW_PRIORITY `dehui_property_dev`.`parking_space` SET deleted = 1;",
                "LOAD DATA INFILE 'demo.csv' INTO TABLE meeting_booking;",
                "ALTER TABLE asset_charge_basis TRUNCATE PARTITION p_old;",
                "ALTER TABLE bill EXCHANGE PARTITION p_old WITH TABLE bill_archive;",
                "DROP TEMPORARY TABLE meeting_room;",
                """
                UPDATE staging_room source_record
                JOIN building_room protected_record ON protected_record.id = source_record.id
                SET protected_record.area = 0;
                """,
                """
                DELETE protected_record
                FROM staging_room source_record
                JOIN building_room protected_record ON protected_record.id = source_record.id;
                """,
                """
                RENAME TABLE staging_room TO building_room,
                             building_room TO building_room_backup;
                """,
                "ALTER TABLE staging_room RENAME TO building_room;",
                "/*!50000 UPDATE bill SET deleted = 1 */;"
        );

        unsafeSql.forEach(sql -> assertThrows(
                AssertionError.class,
                () -> assertBusinessDataSafe(sql, "unsafe.sql"),
                sql
        ));
    }

    @Test
    void migrationSelectionIncludesFutureVersionVariantsAndRepeatables() {
        assertFalse(shouldProtectMigration("V60__legacy.sql"));
        assertTrue(shouldProtectMigration("V61__baseline.sql"));
        assertTrue(shouldProtectMigration("V61.1__follow_up.sql"));
        assertTrue(shouldProtectMigration("V61_2__follow_up.sql"));
        assertTrue(shouldProtectMigration("R__refresh_reference_view.sql"));
        assertFalse(shouldProtectMigration("README.md"));
    }

    @Test
    void policyAllowsAdditiveSchemaEvolutionReadOnlyQueriesAndBackupTables() {
        assertDoesNotThrow(() -> assertBusinessDataSafe(
                """
                ALTER TABLE energy_meter
                    ADD COLUMN future_reference VARCHAR(64) NULL
                    COMMENT 'DROP is documentation here, not an instruction';

                ALTER TABLE bill
                    ADD INDEX idx_bill_future_reference (code);

                ALTER TABLE building_room
                    ADD CONSTRAINT chk_future_area CHECK (property_fee_area >= 0);

                SELECT COUNT(*) FROM energy_reading;

                INSERT INTO sys_permission (code, name)
                VALUES ('example:view', 'DELETE FROM bill is documentation');

                UPDATE bill_archive SET archived = 1;
                ALTER TABLE energy_meter_backup DROP COLUMN obsolete_value;

                /* UPDATE parking_space SET deleted = 1; */
                -- DELETE FROM meeting_booking;
                SELECT COUNT(*) FROM meeting_booking;
                """,
                "safe.sql"
        ));
    }

    private static List<String> loadProtectedTables() {
        try {
            return Files.readAllLines(PROTECTED_TABLE_CATALOG).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .filter(line -> !line.startsWith("#"))
                    .toList();
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static List<Path> protectedMigrations() throws IOException {
        try (Stream<Path> files = Files.walk(MIGRATION_DIRECTORY)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> shouldProtectMigration(path.getFileName().toString()))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    private static boolean shouldProtectMigration(String fileName) {
        if (REPEATABLE_MIGRATION_FILE.matcher(fileName).matches()) {
            return true;
        }
        Matcher matcher = VERSIONED_MIGRATION_FILE.matcher(fileName);
        return matcher.matches()
                && Integer.parseInt(matcher.group(1)) >= POLICY_START_VERSION;
    }

    private static void assertBusinessDataSafe(String sql, String source) {
        String executableSql = normalizeSql(sql);

        for (String statement : executableSql.split(";")) {
            Matcher dataMutation = DATA_MUTATION.matcher(statement);
            if (dataMutation.find()) {
                throw new AssertionError(
                        source
                                + " must not automatically mutate asset or finance business data: "
                                + dataMutation.group().trim()
                );
            }

            Matcher mutationReference =
                    MUTATING_STATEMENT_REFERENCING_PROTECTED.matcher(statement);
            if (mutationReference.find()) {
                throw new AssertionError(
                        source
                                + " must not reference protected business data "
                                + "from an UPDATE or DELETE statement"
                );
            }

            Matcher deleteUsing = DELETE_USING.matcher(statement);
            if (deleteUsing.find()) {
                throw new AssertionError(
                        source + " must not delete protected asset or finance business data"
                );
            }

            Matcher loadData = LOAD_DATA.matcher(statement);
            if (loadData.find()) {
                throw new AssertionError(
                        source + " must not bulk-load protected asset or finance business data"
                );
            }

            Matcher dropTable = DROP_TABLE.matcher(statement);
            if (dropTable.find()) {
                throw new AssertionError(
                        source + " must not drop a protected business table"
                );
            }

            Matcher destructiveAlter = DESTRUCTIVE_ALTER.matcher(statement);
            if (destructiveAlter.find()) {
                throw new AssertionError(
                        source
                                + " must not remove columns, indexes, constraints, or partitions "
                                + "from a protected business table"
                );
            }

            Matcher renameTable = RENAME_TABLE.matcher(statement);
            if (renameTable.find()) {
                throw new AssertionError(
                        source + " must not rename a protected business table"
                );
            }

            Matcher dropIndex = DROP_INDEX.matcher(statement);
            if (dropIndex.find()) {
                throw new AssertionError(
                        source + " must not remove indexes from a protected business table"
                );
            }

            Matcher alterRenameToProtected =
                    ALTER_RENAME_TO_PROTECTED.matcher(statement);
            if (alterRenameToProtected.find()) {
                throw new AssertionError(
                        source + " must not rename another table to a protected table name"
                );
            }

            Matcher createProtectedTable = CREATE_PROTECTED_TABLE.matcher(statement);
            if (createProtectedTable.find()) {
                throw new AssertionError(
                        source + " must not recreate a protected business table"
                );
            }
        }
    }

    private static String normalizeSql(String sql) {
        String executableSql = sql
                .replaceAll("(?is)/\\*!\\s*\\d*\\s*(.*?)\\*/", " $1 ")
                .replaceAll("(?s)'(?:''|\\\\.|[^'])*'", "''")
                .replaceAll("(?s)\"(?:\"\"|\\\\.|[^\"])*\"", "\"\"")
                .replaceAll("(?s)/\\*(?!\\!).*?\\*/", " ")
                .replaceAll("(?m)--(?=[\\t\\x0B\\f\\r ])[^\\r\\n]*", " ")
                .replaceAll("(?m)#.*$", " ");
        return executableSql;
    }
}

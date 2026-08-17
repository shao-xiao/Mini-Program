package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ContractLeaseV37MigrationTest {

    private static final Path MIGRATION_DIRECTORY = Path.of("src", "main", "resources", "db", "migration");

    @Test
    void v37CreatesContractLeaseAuditAndOutboxTables() throws Exception {
        String sql = normalizedMigration();

        for (String table : new String[] {
                "contract_revision",
                "contract_status_history",
                "lease",
                "lease_room",
                "lease_event",
                "lease_status_history",
                "domain_audit_log",
                "outbox_event"
        }) {
            assertTrue(createsTable(sql, table), "V37 must create " + table);
        }
    }

    @Test
    void v37AddsOptimisticVersionsAndBusinessUniqueness() throws Exception {
        String sql = normalizedMigration();

        assertTrue(Pattern.compile("alter table contract.*?add column version bigint", Pattern.DOTALL).matcher(sql).find(),
                "contract aggregate must gain an optimistic-lock version");
        assertTrue(tableBody(sql, "lease").contains("version bigint"), "lease must have an optimistic-lock version");
        assertTrue(tableBody(sql, "lease_room").contains("version bigint"), "lease_room must have an optimistic-lock version");

        assertTrue(Pattern.compile("unique (?:key|index) [^,)]*\\([^)]*contract_id[^)]*revision_no[^)]*\\)")
                        .matcher(tableBody(sql, "contract_revision")).find(),
                "contract revision number must be unique inside a contract");
        assertTrue(Pattern.compile("unique (?:key|index) [^,)]*\\([^)]*lease_no[^)]*\\)")
                        .matcher(tableBody(sql, "lease")).find(),
                "lease_no must be protected by a unique key");
        assertTrue(Pattern.compile("unique (?:key|index) [^,)]*\\([^)]*event_id[^)]*\\)")
                        .matcher(tableBody(sql, "outbox_event")).find(),
                "outbox event identity must be unique for safe publication retries");
    }

    @Test
    void v37IndexesAggregateQueriesAndRoomOccupancyChecks() throws Exception {
        String sql = normalizedMigration();

        assertIndexMentions(tableBody(sql, "contract_revision"), "contract_id");
        assertIndexMentions(tableBody(sql, "contract_status_history"), "contract_id");
        assertIndexMentions(tableBody(sql, "lease"), "contract_id");
        assertIndexMentions(tableBody(sql, "lease"), "tenant_id");
        assertIndexMentions(tableBody(sql, "lease"), "status");
        assertIndexMentions(tableBody(sql, "lease_room"), "lease_id");
        assertIndexMentions(tableBody(sql, "lease_room"), "room_id");
        assertIndexMentions(tableBody(sql, "lease_event"), "lease_id");
        assertIndexMentions(tableBody(sql, "lease_status_history"), "lease_id");
        assertIndexMentions(tableBody(sql, "domain_audit_log"), "aggregate_id");
        assertIndexMentions(tableBody(sql, "outbox_event"), "status");
    }

    @Test
    void v37KeepsLegacyContractRoomIdForForwardCompatibleRollout() throws Exception {
        String sql = normalizedMigration();

        assertFalse(Pattern.compile("drop\\s+column\\s+room_id").matcher(sql).find(),
                "V37 must retain legacy contract.room_id");
        assertFalse(Pattern.compile("drop\\s+(?:table\\s+)?contract_room").matcher(sql).find(),
                "V37 must retain the existing contract_room compatibility relation");
        assertFalse(sql.contains("truncate "), "V37 must not clean historical data");
        assertFalse(sql.contains("delete from "), "V37 must not clean historical data");
    }

    private static boolean createsTable(String sql, String table) {
        return Pattern.compile("create table (?:if not exists )?" + Pattern.quote(table) + "\\s*\\(")
                .matcher(sql)
                .find();
    }

    private static String tableBody(String sql, String table) {
        Pattern tablePattern = Pattern.compile(
                "create table (?:if not exists )?" + Pattern.quote(table) + "\\s*\\((.*?)\\)\\s*engine",
                Pattern.DOTALL
        );
        var matcher = tablePattern.matcher(sql);
        assertTrue(matcher.find(), "V37 must contain DDL for " + table);
        return matcher.group(1);
    }

    private static void assertIndexMentions(String tableBody, String column) {
        assertTrue(Pattern.compile("(?:key|index) [^,)]*\\([^)]*" + Pattern.quote(column) + "[^)]*\\)")
                        .matcher(tableBody).find(),
                "expected an index covering " + column);
    }

    private static String normalizedMigration() throws Exception {
        Path migration;
        try (Stream<Path> files = Files.list(MIGRATION_DIRECTORY)) {
            migration = files
                    .filter(path -> path.getFileName().toString().startsWith("V37__"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("V37 Contract/Lease migration must exist"));
        }
        return Files.readString(migration)
                .replace("`", "")
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}

package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class FeeBillingV38MigrationTest {

    private static final Path MIGRATIONS = Path.of("src", "main", "resources", "db", "migration");

    @Test
    void v38CreatesFeeRuleVersionAndBillingPlanAggregates() throws Exception {
        String sql = migration();
        for (String table : new String[]{"fee_rule", "fee_rule_version", "billing_plan", "billing_plan_item"}) {
            assertTrue(createsTable(sql, table), "V38 must create " + table);
        }
    }

    @Test
    void v38ProtectsRuleVersionsAndBusinessIdentifiers() throws Exception {
        String sql = migration();
        assertTrue(uniqueKey(tableBody(sql, "fee_rule"), "rule_code"), "rule_code must be unique");
        assertTrue(uniqueKey(tableBody(sql, "fee_rule_version"), "fee_rule_id", "version_no"),
                "a rule version number must be unique inside its aggregate");
        assertTrue(uniqueKey(tableBody(sql, "billing_plan"), "plan_no"), "plan_no must be unique");
        assertTrue(uniqueKey(tableBody(sql, "billing_plan_item"), "billing_plan_id", "fee_rule_version_id"),
                "a version may occur only once in one plan");
        assertTrue(tableBody(sql, "fee_rule").contains("version bigint"), "fee_rule needs optimistic locking");
        assertTrue(tableBody(sql, "billing_plan").contains("version bigint"), "billing_plan needs optimistic locking");
    }

    @Test
    void v38IndexesEffectiveRangeAndWorkspaceFilters() throws Exception {
        String sql = migration();
        assertIndex(tableBody(sql, "fee_rule"), "status");
        assertIndex(tableBody(sql, "fee_rule_version"), "effective_from");
        assertIndex(tableBody(sql, "billing_plan"), "tenant_id");
        assertIndex(tableBody(sql, "billing_plan"), "contract_id");
        assertIndex(tableBody(sql, "billing_plan"), "lease_id");
        assertIndex(tableBody(sql, "billing_plan"), "status");
        assertIndex(tableBody(sql, "billing_plan_item"), "fee_rule_version_id");
    }

    @Test
    void v38SeedsExactPermissionsWithoutDestructiveDataChanges() throws Exception {
        String sql = migration();
        for (String permission : new String[]{
                "fee-rule:view", "fee-rule:create", "fee-rule:update", "fee-rule:activate",
                "billing-plan:view", "billing-plan:create", "billing-plan:update", "billing-plan:terminate"
        }) {
            assertTrue(sql.contains("'" + permission + "'"), "V38 must seed " + permission);
        }
        assertTrue(sql.contains("'feerule:view'"), "legacy fee-rule authorization must be mapped forward");
        assertFalse(sql.contains("truncate "), "V38 must not clean business data");
        assertFalse(sql.contains("delete from "), "V38 must not delete business data");
        assertFalse(sql.contains("drop table "), "V38 must remain additive");
    }

    @Test
    void v38AlignsWorkspaceMenusWithViewPermissions() throws Exception {
        String sql = migration();
        assertTrue(sql.contains("'lease:fee-rule-v2'"));
        assertTrue(sql.contains("'/fee-rules'"));
        assertTrue(sql.contains("'lease:billing-plan'"));
        assertTrue(sql.contains("'/billing-plans'"));
        assertTrue(sql.contains("insert into sys_role_menu"));
        assertTrue(sql.contains("when 'fee-rule:view' then 'lease:fee-rule-v2'"));
        assertTrue(sql.contains("when 'billing-plan:view' then 'lease:billing-plan'"));
        assertTrue(sql.contains("parent_menu.code = 'lease'"), "workspace roles also need the parent lease menu");
    }

    private static String migration() throws Exception {
        Path path;
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            path = files.filter(file -> file.getFileName().toString().startsWith("V38__"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("V38 FeeRule/BillingPlan migration must exist"));
        }
        return Files.readString(path).replace("`", "").replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static boolean createsTable(String sql, String table) {
        return Pattern.compile("create table (?:if not exists )?" + table + "\\s*\\(").matcher(sql).find();
    }

    private static String tableBody(String sql, String table) {
        var matcher = Pattern.compile("create table (?:if not exists )?" + table + "\\s*\\((.*?)\\)\\s*engine",
                Pattern.DOTALL).matcher(sql);
        assertTrue(matcher.find(), "V38 must contain DDL for " + table);
        return matcher.group(1);
    }

    private static boolean uniqueKey(String body, String... columns) {
        StringBuilder expression = new StringBuilder("unique (?:key|index) [^,)]*\\(");
        for (String column : columns) expression.append("[^)]*").append(column);
        expression.append("[^)]*\\)");
        return Pattern.compile(expression.toString()).matcher(body).find();
    }

    private static void assertIndex(String body, String column) {
        assertTrue(Pattern.compile("(?:key|index) [^,)]*\\([^)]*" + column + "[^)]*\\)").matcher(body).find(),
                "expected index covering " + column);
    }
}

package com.dehui.property.modules.customfield;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class CustomFieldServerWorkspaceIntegrationGuardTest {

    private static final Map<String, String> SERVER_WORKSPACE_SOURCES = Map.ofEntries(
            Map.entry("rooms", "com/dehui/property/modules/building/controller/BuildingController.java"),
            Map.entry("equipment", "com/dehui/property/modules/building/controller/EquipmentController.java"),
            Map.entry("leases", "com/dehui/property/modules/lease/infrastructure/repository/LeaseRepository.java"),
            Map.entry("fee-rules", "com/dehui/property/modules/billing/infrastructure/repository/FeeRuleRepository.java"),
            Map.entry("billing-plans", "com/dehui/property/modules/billing/infrastructure/repository/BillingPlanRepository.java"),
            Map.entry("investment-leads", "com/dehui/property/modules/investment/controller/InvestmentController.java"),
            Map.entry("investment-contents", "com/dehui/property/modules/investment/controller/InvestmentController.java"),
            Map.entry("investment-listings", "com/dehui/property/modules/investment/controller/InvestmentController.java"),
            Map.entry("bills", "com/dehui/property/modules/bill/controller/BillController.java"),
            Map.entry("parking-spaces", "com/dehui/property/modules/parking/controller/ParkingController.java"),
            Map.entry("workorders", "com/dehui/property/modules/workorder/controller/WorkOrderController.java"),
            Map.entry("inspections", "com/dehui/property/modules/inspection/controller/InspectionController.java"),
            Map.entry("energy-meters", "com/dehui/property/modules/energy/controller/EnergyController.java"),
            Map.entry("energy-records", "com/dehui/property/modules/energy/controller/EnergyController.java"),
            Map.entry("mobile-users", "com/dehui/property/modules/mobile/controller/MobileUserAdminController.java")
    );

    @Test
    void everyServerWorkspaceCompilesQueriesAndProjectsValuesInItsRealListAdapter() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        for (Map.Entry<String, String> entry : SERVER_WORKSPACE_SOURCES.entrySet()) {
            String source = Files.readString(sourceRoot.resolve(entry.getValue()));
            String workspaceKey = entry.getKey();
            assertTrue(
                    Pattern.compile("(?:compileCustomQuery|customFieldQueryService\\.compile)\\(\\s*\\\""
                                    + Pattern.quote(workspaceKey) + "\\\"", Pattern.DOTALL)
                            .matcher(source).find(),
                    workspaceKey + " must compile platform custom-field query conditions"
            );
            assertTrue(
                    Pattern.compile("customFieldProjectionService\\.(?:attach|values)\\(\\s*\\\""
                                    + Pattern.quote(workspaceKey) + "\\\"", Pattern.DOTALL)
                            .matcher(source).find(),
                    workspaceKey + " must project platform custom-field values into list records"
            );
        }
    }

    @Test
    void everyServerWorkspaceFallsBackWhenCustomQueryHasNoOrdering() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        Set<String> checkedSources = new HashSet<>();
        Pattern unsafeTernary = Pattern.compile(
                "String\\s+orderBy\\s*=\\s*customQuery\\s*==\\s*null\\s*\\?"
        );
        Pattern unsafeConditional = Pattern.compile(
                "if\\s*\\(\\s*customQuery\\s*!=\\s*null\\s*\\)\\s*\\{[^}]*orderExpressions\\(\\)",
                Pattern.DOTALL
        );

        for (Map.Entry<String, String> entry : SERVER_WORKSPACE_SOURCES.entrySet()) {
            if (!checkedSources.add(entry.getValue())) {
                continue;
            }
            String source = Files.readString(sourceRoot.resolve(entry.getValue()));
            if (!source.contains("orderExpressions()")) {
                continue;
            }
            assertFalse(
                    unsafeTernary.matcher(source).find() || unsafeConditional.matcher(source).find(),
                    entry.getValue() + " must fall back when the custom-field ordering plan is empty"
            );
        }
    }
}

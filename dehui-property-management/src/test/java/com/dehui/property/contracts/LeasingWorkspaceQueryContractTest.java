package com.dehui.property.contracts;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LeasingWorkspaceQueryContractTest {

    private static final Path MODULES = Path.of("src", "main", "java", "com", "dehui", "property", "modules");

    @Test
    void leasingSearchEndpointsAcceptWorkspaceOrderingWithoutRemovingLegacyParameters() throws Exception {
        assertController("lease", "LeaseController.java");
        assertController("billing", "FeeRuleController.java");
        assertController("billing", "BillingPlanController.java");
    }

    @Test
    void leasingRepositoriesUseTheSharedWhitelistOrderingBuilder() throws Exception {
        assertRepository("lease", "LeaseRepository.java");
        assertRepository("billing", "FeeRuleRepository.java");
        assertRepository("billing", "BillingPlanRepository.java");
    }

    private static void assertController(String module, String file) throws Exception {
        String source = Files.readString(MODULES.resolve(module).resolve("controller").resolve(file));
        assertTrue(source.contains("String sortField"), file + " must keep legacy sortField");
        assertTrue(source.contains("String sortDirection"), file + " must keep legacy sortDirection");
        assertTrue(source.contains("String sorts"), file + " must accept workspace sorts");
        assertTrue(source.contains("String groupBy"), file + " must accept workspace groupBy");
    }

    private static void assertRepository(String module, String file) throws Exception {
        String source = Files.readString(
                MODULES.resolve(module).resolve("infrastructure").resolve("repository").resolve(file)
        );
        assertTrue(source.contains("WorkspaceOrderBy.build("), file + " must use whitelist ordering");
        assertTrue(source.contains("SORT_FIELDS"), file + " must declare a field whitelist");
    }
}

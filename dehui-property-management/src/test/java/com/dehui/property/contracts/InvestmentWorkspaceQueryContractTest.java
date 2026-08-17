package com.dehui.property.contracts;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InvestmentWorkspaceQueryContractTest {

    private static final Path CONTROLLER = Path.of(
            "src", "main", "java", "com", "dehui", "property", "modules",
            "investment", "controller", "InvestmentController.java"
    );

    @Test
    void investmentListsAcceptWorkspaceOrderingAndKeepTheirExistingPaths() throws Exception {
        String source = Files.readString(CONTROLLER);

        assertTrue(source.contains("/investment/contents"));
        assertTrue(source.contains("/investment/listings"));
        assertTrue(source.contains("/investment/leads"));
        assertTrue(source.contains("String sorts"));
        assertTrue(source.contains("String groupBy"));
        assertTrue(source.contains("WorkspaceOrderBy.build("));
    }

    @Test
    void investmentSummaryEndpointsUseTheSameFilterBuildersAsTheirLists() throws Exception {
        String source = Files.readString(CONTROLLER);

        assertTrue(source.contains("/investment/listings/summary"));
        assertTrue(source.contains("/investment/leads/summary"));
        assertTrue(source.contains("listingWhere(keyword, publishStatus, buildingId, floorId"));
        assertTrue(source.contains("leadWhere(keyword, status, source"));
    }
}

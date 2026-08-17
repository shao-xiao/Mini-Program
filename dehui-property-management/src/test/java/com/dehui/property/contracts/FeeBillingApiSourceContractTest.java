package com.dehui.property.contracts;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class FeeBillingApiSourceContractTest {

    private static final Path MODULES = Path.of("src", "main", "java", "com", "dehui", "property", "modules");

    @Test
    void feeRuleApiUsesExplicitDtosPagingSummaryAndPrincipal() throws Exception {
        assertApi("billing", "FeeRuleController.java", "FeeRule", "/fee-rules/search", "/fee-rules/summary");
        String source = controller("billing", "FeeRuleController.java");
        assertTrue(source.contains("/{id}/versions"), "version history must be queryable");
        assertTrue(source.contains("/{id}/activate"), "activation must use an explicit business action");
    }

    @Test
    void billingPlanApiUsesExplicitDtosPagingSummaryAndPrincipal() throws Exception {
        assertApi("billing", "BillingPlanController.java", "BillingPlan", "/billing-plans/search", "/billing-plans/summary");
        String source = controller("billing", "BillingPlanController.java");
        assertTrue(source.contains("/{id}/activate"), "plan activation must use an explicit business action");
        assertTrue(source.contains("/{id}/terminate"), "plan termination must use an explicit business action");
    }

    @Test
    void legacyFeeRuleControllerDelegatesToTheV2ApplicationService() throws Exception {
        String source = Files.readString(MODULES.resolve("legacy").resolve("LegacyWriteController.java"));
        assertTrue(source.contains("FeeRuleApplicationService"), "legacy /feerules must delegate to V2 service");
        assertFalse(source.contains("@GetMapping({\"/assets\", \"/feerules\"})"),
                "fee-rule compatibility should not share the asset empty-list handler");
        assertFalse(source.contains("@PostMapping({\"/assets\", \"/feerules\"})"),
                "fee-rule compatibility should not share the asset 501 handler");
    }

    private static void assertApi(String module, String file, String prefix, String search, String summary)
            throws Exception {
        String source = controller(module, file);
        Path dtoDir = MODULES.resolve(module).resolve("dto");
        try (Stream<Path> files = Files.list(dtoDir)) {
            List<String> names = files.map(path -> path.getFileName().toString()).toList();
            assertTrue(names.stream().anyMatch(name -> name.startsWith(prefix) && name.endsWith("Request.java")));
            assertTrue(names.stream().anyMatch(name -> name.startsWith(prefix) && name.endsWith("Response.java")));
        }
        assertTrue(source.contains('"' + search + '"'));
        assertTrue(source.contains('"' + summary + '"'));
        assertTrue(Pattern.compile("PageResponse\\s*<\\s*" + prefix + "Response\\s*>").matcher(source).find());
        assertTrue(source.contains(prefix + "SummaryResponse"));
        assertTrue(source.contains("@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE)"));
        assertTrue(source.contains("principal.userId()"));
        assertFalse(Pattern.compile("Map\\s*<\\s*String").matcher(source).find(), "new controller must not use map DTOs");
    }

    private static String controller(String module, String file) throws Exception {
        Path path = MODULES.resolve(module).resolve("controller").resolve(file);
        assertTrue(Files.exists(path), path + " must exist");
        return Files.readString(path);
    }
}

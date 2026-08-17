package com.dehui.property.contracts;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ContractLeaseApiSourceContractTest {

    private static final Path MODULES = Path.of("src", "main", "java", "com", "dehui", "property", "modules");

    @Test
    void contractV2ApiUsesDtosPagingSummaryAndAuthenticatedOperator() throws Exception {
        assertModuleApiContract(
                "contract",
                "ContractController.java",
                List.of("/contracts/search", "/contracts/summary")
        );
    }

    @Test
    void leaseApiUsesDtosPagingSummaryAndAuthenticatedOperator() throws Exception {
        assertModuleApiContract(
                "lease",
                "LeaseController.java",
                List.of("/leases/search", "/leases/summary")
        );
    }

    @Test
    void leaseControllerPublishesExplicitRoomLifecycleActions() throws Exception {
        String source = controllerSource("lease", "LeaseController.java");

        for (String route : List.of("/{id}/check-in", "/{id}/check-out", "/{id}/renew", "/{id}/terminate")) {
            assertTrue(source.contains('"' + route + '"'), "LeaseController must publish " + route);
        }
    }

    @Test
    void legacyContractCheckInCreatesACompatibilityLeaseWhenNoneExists() throws Exception {
        String service = Files.readString(MODULES.resolve("lease")
                .resolve("application")
                .resolve("LeaseApplicationService.java"));

        assertTrue(service.contains("createCompatibilityLeaseForContract"),
                "legacy contract check-in must bridge historical contracts to the Lease aggregate");
        assertTrue(service.contains("uniqueActionableLeaseIdForContract(contractId)"),
                "legacy contract check-in must reject ambiguous existing Lease aggregates");
    }

    private static void assertModuleApiContract(String module, String controller, List<String> queryRoutes)
            throws Exception {
        String source = controllerSource(module, controller);
        List<Path> dtoFiles = dtoFiles(module);
        List<Path> requestDtos = dtoFiles.stream()
                .filter(path -> path.getFileName().toString().endsWith("Request.java"))
                .toList();
        List<Path> responseDtos = dtoFiles.stream()
                .filter(path -> path.getFileName().toString().endsWith("Response.java"))
                .toList();

        assertFalse(requestDtos.isEmpty(), module + " must define explicit request DTOs");
        assertFalse(responseDtos.isEmpty(), module + " must define explicit response DTOs");
        assertTrue(requestDtos.stream().map(ContractLeaseApiSourceContractTest::simpleName).anyMatch(source::contains),
                controller + " must accept an explicit request DTO");
        assertTrue(responseDtos.stream().map(ContractLeaseApiSourceContractTest::simpleName).anyMatch(source::contains),
                controller + " must return an explicit response DTO");

        for (String route : queryRoutes) {
            assertTrue(source.contains('"' + route + '"'), controller + " must publish " + route);
        }
        assertTrue(Pattern.compile("PageResponse\\s*<\\s*[A-Za-z0-9_$.]+Response\\s*>").matcher(source).find(),
                controller + " search must return PageResponse with an explicit response DTO");
        assertTrue(Pattern.compile("ApiResponse\\s*<\\s*[A-Za-z0-9_$.]*SummaryResponse\\s*>").matcher(source).find(),
                controller + " summary must return an explicit summary DTO");
        assertTrue(source.contains("@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE)"),
                controller + " mutations must receive the authenticated principal");
        assertTrue(source.contains("principal.userId()"),
                controller + " must derive operator identity from the authenticated principal");

        for (Path requestDto : requestDtos) {
            String dtoSource = Files.readString(requestDto);
            assertFalse(Pattern.compile("\\b(operatorId|userId|createdBy|updatedBy)\\b").matcher(dtoSource).find(),
                    requestDto.getFileName() + " must not accept a client-supplied operator identity");
        }
    }

    private static String controllerSource(String module, String controller) throws IOException {
        Path path = MODULES.resolve(module).resolve("controller").resolve(controller);
        assertTrue(Files.exists(path), path + " must exist");
        return Files.readString(path);
    }

    private static List<Path> dtoFiles(String module) throws IOException {
        Path directory = MODULES.resolve(module).resolve("dto");
        assertTrue(Files.isDirectory(directory), directory + " must exist");
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".java")).toList();
        }
    }

    private static String simpleName(Path path) {
        return path.getFileName().toString().replaceFirst("\\.java$", "");
    }
}

package com.dehui.property.contracts;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TenantWorkspaceApiSourceContractTest {

    private static final Path MODULES = Path.of("src", "main", "java", "com", "dehui", "property", "modules");

    @Test
    void contractHistoryAndExactBillScopeUseRealServerQueries() throws Exception {
        String contractController = source("contract", "controller", "ContractController.java");
        String contractRepository = source("contract", "infrastructure", "repository", "ContractRepository.java");
        String billController = source("bill", "controller", "BillController.java");

        assertTrue(contractController.contains("@GetMapping(\"/contracts/{id}/history\")"));
        assertTrue(contractController.contains("合同自动出账入口已停用"));
        assertTrue(contractController.contains("@Deprecated(forRemoval = false)\n    @PostMapping(\"/contracts/generate-bills\")"));
        assertTrue(contractRepository.contains("FROM contract_status_history h"));
        assertTrue(contractRepository.contains("FROM lease_status_history h"));
        assertTrue(billController.contains("@RequestParam(required = false) Long contractId"));
        assertTrue(billController.contains("AND b.contract_id = ?"));
        assertTrue(billController.contains("appendBillFilters(\n                where, args, keyword, tenantId, contractId"));
    }

    private String source(String... path) throws Exception {
        return Files.readString(MODULES.resolve(Path.of("", path))).replace("\r\n", "\n");
    }
}

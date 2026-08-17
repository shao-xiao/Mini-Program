package com.dehui.property.modules.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.modules.contract.controller.ContractController;
import com.dehui.property.security.AuthPrincipal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;

class ContractControllerElectronicContractTest {

    @TempDir
    Path uploadDir;

    @Test
    void uploadElectronicContractStoresPdfAndReplacesExistingAttachment() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList("SELECT id FROM contract WHERE id = ? AND deleted = 0 LIMIT 1", 3L))
                .thenReturn(List.of(Map.of("id", 3L)));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(99L);

        ContractController controller = new ContractController(jdbcTemplate);
        controller.setUploadDir(uploadDir.toString());

        ApiResponse<Void> response = controller.uploadElectronicContract(
                3L,
                new MockMultipartFile("file", "租赁合同.pdf", "application/pdf", "contract-pdf".getBytes(StandardCharsets.UTF_8))
        );

        assertEquals(200, response.code());
        verify(jdbcTemplate).update("""
                UPDATE contract_attachment
                SET status = 'REPLACED', deleted = 1, updated_by = ?
                WHERE contract_id = ? AND attachment_type = ? AND deleted = 0
                """,
                0L, 3L, "ELECTRONIC_CONTRACT");
        verify(jdbcTemplate).update(
                eq("INSERT INTO contract_attachment (contract_id, file_id, attachment_type, status, created_by, updated_by) VALUES (?, ?, ?, 'ENABLED', ?, ?)"),
                eq(3L),
                eq(99L),
                eq("ELECTRONIC_CONTRACT"),
                eq(0L),
                eq(0L)
        );
        assertTrue(Files.list(uploadDir.resolve("private").resolve("contracts")).findAny().isPresent());
    }

    @Test
    void mobileContractsOnlyExposeTenantOwnedElectronicContracts() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(Map.of(
                "id", 21L,
                "contractNumber", "CON-001",
                "tenantName", "德汇咖啡服务有限公司",
                "roomName", "B-101",
                "status", "ACTIVE",
                "contractFileName", "CON-001.pdf",
                "hasContractFile", 1
        )));

        ApiResponse<Map<String, Object>> response = new ContractController(jdbcTemplate)
                .mobileContracts(tenantPrincipal());

        assertEquals(200, response.code());
        List<Map<String, Object>> contracts = (List<Map<String, Object>>) response.data().get("contracts");
        assertEquals(1, contracts.size());
        assertEquals(true, contracts.getFirst().get("hasContractFile"));
        assertEquals("/mobile/contracts/21/file/download", contracts.getFirst().get("contractDownloadUrl"));
    }

    @Test
    void mobileContractDownloadStreamsTenantOwnedFile() throws Exception {
        Path contract = Files.createTempFile("contract-21-", ".pdf");
        Files.writeString(contract, "contract-pdf", StandardCharsets.UTF_8);

        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "fileName", "CON-001.pdf",
                "storagePath", contract.toString(),
                "mimeType", "application/pdf"
        )));

        ResponseEntity<Resource> response = new ContractController(jdbcTemplate)
                .mobileDownloadElectronicContract(tenantPrincipal(), 21L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("contract-pdf", new String(response.getBody().getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    private AuthPrincipal tenantPrincipal() {
        return new AuthPrincipal(7L, "mobile:7", "TENANT", 8L, List.of("MOBILE_USER"), List.of("mobile:view"));
    }
}

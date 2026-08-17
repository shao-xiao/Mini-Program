package com.dehui.property.modules.bill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.asset.application.AssetChargeBasisService;
import com.dehui.property.modules.bill.application.BillCommandService;
import com.dehui.property.modules.bill.controller.BillController;
import com.dehui.property.security.AuthPrincipal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class BillControllerDeletionTest {

    @Test
    void recordDeletionDelegatesTheConfirmedContractToTheTransactionalService() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        BillCommandService commandService = mock(BillCommandService.class);
        BillController controller = new BillController(
                jdbcTemplate,
                null,
                null,
                commandService,
                mock(AssetChargeBasisService.class)
        );
        AuthPrincipal principal = new AuthPrincipal(
                7_001L,
                "finance-admin",
                "SYSTEM",
                null,
                List.of("ADMIN"),
                List.of("bill:delete")
        );

        ApiResponse<Void> response = controller.deleteBillRecord(
                9L,
                Map.of(
                        "version", 3L,
                        "reason", "清理隔离测试账单",
                        "confirmBillNumber", "BIL-MANUAL-001"
                ),
                principal
        );

        assertEquals(200, response.code());
        verify(commandService).deleteBillRecord(
                9L,
                3L,
                "清理隔离测试账单",
                "BIL-MANUAL-001",
                7_001L,
                false
        );
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void mobileIdentityCannotReuseItsTokenOnTheAdminDeletionRoute() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        BillCommandService commandService = mock(BillCommandService.class);
        BillController controller = new BillController(
                jdbcTemplate,
                null,
                null,
                commandService,
                mock(AssetChargeBasisService.class)
        );
        AuthPrincipal mobileStaff = new AuthPrincipal(
                7_002L,
                "mobile-staff",
                "STAFF",
                null,
                List.of("STAFF"),
                List.of("bill:delete")
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> controller.deleteBillRecord(
                        9L,
                        Map.of(
                                "version", 3L,
                                "reason", "不应执行",
                                "confirmBillNumber", "BIL-MANUAL-001"
                        ),
                        mobileStaff
                )
        );

        assertEquals(403, exception.getCode());
        verify(commandService, never()).deleteBillRecord(
                9L,
                3L,
                "不应执行",
                "BIL-MANUAL-001",
                7_002L,
                false
        );
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void approvedDeletionCapabilityIsPassedToTheLockedServiceDecision() {
        BillCommandService commandService = mock(BillCommandService.class);
        BillController controller = new BillController(
                mock(JdbcTemplate.class), null, null, commandService, mock(AssetChargeBasisService.class)
        );
        AuthPrincipal principal = new AuthPrincipal(
                7_003L,
                "approved-cleanup-operator",
                "SYSTEM",
                null,
                List.of("FINANCE"),
                List.of("bill:delete", "bill:delete-approved")
        );

        controller.deleteBillRecord(
                10L,
                Map.of("version", 4L, "reason", "清理已审核测试账单", "confirmBillNumber", "BIL-TEST-010"),
                principal
        );

        verify(commandService).deleteBillRecord(
                10L, 4L, "清理已审核测试账单", "BIL-TEST-010", 7_003L, true
        );
    }

    @Test
    void legacyBillWildcardDoesNotImplicitlyGrantApprovedRecordDeletion() {
        BillCommandService commandService = mock(BillCommandService.class);
        BillController controller = new BillController(
                mock(JdbcTemplate.class), null, null, commandService, mock(AssetChargeBasisService.class)
        );
        AuthPrincipal legacyWildcardPrincipal = new AuthPrincipal(
                7_005L, "legacy-bill-admin", "SYSTEM", null,
                List.of("ADMIN"), List.of("bill:*")
        );

        controller.deleteBillRecord(
                12L,
                Map.of("version", 6L, "reason", "尝试清理已审核账单", "confirmBillNumber", "BIL-TEST-012"),
                legacyWildcardPrincipal
        );

        verify(commandService).deleteBillRecord(
                12L, 6L, "尝试清理已审核账单", "BIL-TEST-012", 7_005L, false
        );
    }

    @Test
    void preflightReturnsTheServerOwnedBlockedReasonBeforeAnyConfirmationPrompt() {
        BillCommandService commandService = mock(BillCommandService.class);
        BillController controller = new BillController(
                mock(JdbcTemplate.class), null, null, commandService, mock(AssetChargeBasisService.class)
        );
        AuthPrincipal principal = new AuthPrincipal(
                7_004L, "draft-cleanup-operator", "SYSTEM", null,
                List.of("FINANCE"), List.of("bill:delete")
        );
        when(commandService.deletionEligibility(11L, 5L, false)).thenReturn(Map.of(
                "allowed", false,
                "blockedCode", "BUSINESS_RULE_BLOCKED",
                "blockedReason", "账单存在收款历史，不能删除"
        ));

        ApiResponse<Map<String, Object>> response = controller.checkBillRecordDeletion(
                11L, Map.of("version", 5L), principal
        );

        assertEquals(200, response.code());
        assertEquals(false, response.data().get("allowed"));
        assertEquals("账单存在收款历史，不能删除", response.data().get("blockedReason"));
        verify(commandService).deletionEligibility(11L, 5L, false);
    }

    @Test
    void legacyDeleteAliasRequiresVersionAndRealVoidReason() {
        BillCommandService commandService = mock(BillCommandService.class);
        BillController controller = new BillController(
                mock(JdbcTemplate.class), null, null, commandService, mock(AssetChargeBasisService.class)
        );
        AuthPrincipal principal = new AuthPrincipal(
                7_006L, "void-operator", "SYSTEM", null,
                List.of("FINANCE"), List.of("bill:void")
        );

        BusinessException missingContract = assertThrows(
                BusinessException.class,
                () -> controller.deleteBill(13L, null, principal)
        );
        assertEquals(400, missingContract.getCode());

        controller.deleteBill(13L, Map.of("version", 7L, "reason", "录入错误，按作废保留"), principal);
        verify(commandService).voidBill(13L, 7L, "录入错误，按作废保留", 7_006L);
    }
}

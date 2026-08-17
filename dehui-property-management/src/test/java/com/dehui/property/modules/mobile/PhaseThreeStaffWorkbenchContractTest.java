package com.dehui.property.modules.mobile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dehui.property.modules.mobile.service.MobileCapabilityMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class PhaseThreeStaffWorkbenchContractTest {

    @Test
    void readAndWritePermissionsMapToExactStaffCapabilities() {
        List<String> capabilities = MobileCapabilityMapper.fromPermissionCodes(List.of(
                "staff-mobile:access",
                "staff-mobile:inspection:view",
                "staff-mobile:inspection:write",
                "staff-mobile:workorder:view",
                "staff-mobile:workorder:write",
                "staff-mobile:energy:view",
                "staff-mobile:energy:write",
                "staff-mobile:meeting:view",
                "staff-mobile:meeting:write",
                "staff-mobile:visitor:view",
                "staff-mobile:visitor:write",
                "staff-mobile:investment:view",
                "staff-mobile:investment:write"
        ));
        for (String capability : List.of(
                "inspection.staff.view", "inspection.staff.create", "inspection.staff.update",
                "workorder.staff.view", "workorder.staff.start", "workorder.staff.complete",
                "energy.staff.view", "energy.staff.reading.create",
                "meeting.staff.view", "meeting.staff.manage",
                "visitor.staff.view", "visitor.staff.verify",
                "investment.lead.staff.view", "investment.lead.staff.update",
                "investment.lead.staff.follow", "investment.lead.staff.convert"
        )) {
            assertTrue(capabilities.contains(capability), capability);
        }
    }

    @Test
    void roleNamesStillGrantNoWorkbenchAccess() {
        assertFalse(MobileCapabilityMapper.hasWorkbenchAccess(
                List.of("ENGINEER", "PROPERTY_MANAGER", "SECURITY", "STAFF")
        ));
    }

    @Test
    void readOnlyPermissionMatrixNeverLeaksWriteCapabilities() {
        List<String> capabilities = MobileCapabilityMapper.fromPermissionCodes(List.of(
                "staff-mobile:access",
                "staff-mobile:meeting:view",
                "staff-mobile:investment:view"
        ));
        assertTrue(capabilities.contains("meeting.staff.view"));
        assertTrue(capabilities.contains("investment.lead.staff.view"));
        assertFalse(capabilities.contains("meeting.staff.manage"));
        assertFalse(capabilities.contains("investment.lead.staff.update"));
        assertFalse(capabilities.contains("investment.lead.staff.follow"));
        assertFalse(capabilities.contains("investment.lead.staff.convert"));
    }

    @Test
    void workbenchOnlyAggregatesAuthorizedModules() throws Exception {
        String source = source("modules/mobile/controller/MobileStaffController.java");
        String interceptor = source("security/AuthInterceptor.java");
        assertTrue(source.contains("@GetMapping(\"/mobile/staff/workbench\")"));
        assertTrue(interceptor.contains("/api/mobile/staff/inspection-types"));
        assertTrue(interceptor.contains("/api/mobile/staff/inspection-options"));
        assertTrue(source.contains("if (has(principal, \"workorder.staff.view\"))"));
        assertTrue(source.contains("if (has(principal, \"inspection.staff.view\"))"));
        assertTrue(source.contains("if (has(principal, \"energy.staff.view\"))"));
        assertTrue(source.contains("if (has(principal, \"meeting.staff.view\"))"));
        assertTrue(source.contains("if (has(principal, \"visitor.staff.view\"))"));
        assertTrue(source.contains("if (has(principal, \"investment.lead.staff.view\"))"));
        assertTrue(source.contains("assignee_id = ?"));
    }

    @Test
    void validStaffBindingWithoutMappedModulesKeepsStaffIdentityAndGetsEmptyWorkbench() throws Exception {
        String support = source("modules/mobile/controller/MobileApiSupport.java");
        String interceptor = source("security/AuthInterceptor.java");
        assertTrue(support.contains("&& truthy(row.get(\"mobileEnabled\"))"));
        assertTrue(support.contains("&& truthy(row.get(\"hasMobileWorkbenchAccess\")))"));
        assertTrue(interceptor.contains(
                "if (uri.equals(\"/api/mobile/staff/workbench\") && \"GET\".equalsIgnoreCase(method))"
        ));
        assertTrue(interceptor.contains("return \"STAFF\".equals(principal.userType());"));
    }

    @Test
    void inspectionsDeriveInspectorAndKeepAppendOnlyHistory() throws Exception {
        String source = source("modules/mobile/controller/MobileStaffController.java");
        int updateStart = source.indexOf("@PatchMapping(\"/mobile/staff/inspections/{id}\")");
        int closeStart = source.indexOf("@PostMapping(\"/mobile/staff/inspections/{id}/close\")", updateStart);
        String updateInspection = source.substring(updateStart, closeStart);
        assertTrue(source.contains("inspector_user_id"));
        assertTrue(source.contains("staffName(staffUserId)"));
        assertTrue(source.contains("异常巡检必须填写问题描述"));
        assertTrue(source.contains("INSERT INTO inspection_record_history"));
        assertTrue(source.contains("detail.put(\"images\", attachments)"));
        assertFalse(source.contains("WHERE id = ? AND inspector_user_id = ? AND deleted = 0"));
        assertFalse(updateInspection.contains("remark ="));
        assertTrue(updateInspection.contains("textValue(body, \"feedback\", \"remark\")"));
    }

    @Test
    void inspectionSystemImageFieldIsClaimedAndExposedToBothDetailSurfaces() throws Exception {
        String mobile = source("modules/mobile/controller/MobileStaffController.java");
        String admin = source("modules/inspection/controller/InspectionController.java");
        assertTrue(mobile.contains("inspectionFileIds(canonical)"));
        assertTrue(mobile.contains("inspectionFileIds(body)"));
        assertTrue(mobile.contains("customFieldFileIds(body.get(\"images\"))"));
        assertTrue(admin.contains("record.put(\"images\", attachments)"));
        assertTrue(admin.contains("CONCAT('/api/files/', f.id) AS url"));
    }

    @Test
    void inspectionResolutionImagesAppendToTheirOwnHistoryEntry() throws Exception {
        String mobile = source("modules/mobile/controller/MobileStaffController.java");
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V63__add_inspection_history_attachments.sql"
        ));
        assertTrue(mobile.contains("inspectionResolutionFileIds(body)"));
        assertTrue(mobile.contains("attachInspectionHistoryFiles"));
        assertTrue(mobile.contains("inspection_history_attachment"));
        assertTrue(mobile.contains("\"resolutionImages\""));
        assertTrue(mobile.contains("FROM inspection_attachment attachment"));
        assertTrue(sql.contains("CREATE TABLE inspection_history_attachment"));
        assertTrue(sql.contains("inspection_history_id"));
        assertFalse(sql.contains("DELETE FROM"));
        assertFalse(sql.contains("UPDATE inspection_history_attachment"));
        assertFalse(sql.contains("DROP TABLE"));
    }

    @Test
    void inspectionFormOptionsComeFromRbacStaffAndRealAssetLocations() throws Exception {
        String source = source("modules/mobile/controller/MobileStaffController.java");
        assertTrue(source.contains("@GetMapping(\"/mobile/staff/inspection-options\")"));
        assertTrue(source.contains("permission_record.code IN ("));
        assertTrue(source.contains("'staff-mobile:inspection:write'"));
        assertTrue(source.contains("staff.mobile_enabled = 1"));
        assertTrue(source.contains("building_room room"));
        assertTrue(source.contains("building_floor floor"));
        assertTrue(source.contains("inspectorCode"));
        assertTrue(source.contains("resolveInspectionInspector"));
        assertTrue(source.contains("validateInspectionArea"));
        assertTrue(source.contains("? \" staff.id = ?\" : \" staff.code = ?\""));
        assertFalse(source.contains("requiredLong(canonical, \"inspectorId\""));
    }

    @Test
    void workordersRequireAssignmentAndLegalStateTransitions() throws Exception {
        String source = source("modules/workorder/controller/WorkOrderController.java");
        assertTrue(source.contains("work_status = 'ASSIGNED'"));
        assertTrue(source.contains("work_status = 'PROCESSING'"));
        assertTrue(source.contains("assignee_id = ?"));
        assertTrue(source.contains("工单必须处于处理中才能完成"));
    }

    @Test
    void workorderProgressRecordsAndImagesAppendToHistory() throws Exception {
        String source = source("modules/mobile/controller/MobileStaffController.java");
        String detailSource = source("modules/workorder/controller/WorkOrderController.java");
        assertTrue(source.contains("@PostMapping(\"/mobile/staff/workorders/{id}/records\")"));
        assertTrue(source.contains("'PROGRESS'"));
        assertTrue(source.contains("work_order_record_id"));
        assertTrue(source.contains("'PROCESS_IMAGE'"));
        assertTrue(detailSource.contains("detail.put(\"images\", attachments.stream()"));
        assertTrue(detailSource.contains("attachment.attachment_type AS attachmentType"));
        assertTrue(detailSource.contains("attachment.work_order_record_id IS NULL"));
        assertTrue(detailSource.contains("records.forEach(record -> record.put(\"attachments\""));
    }

    @Test
    void energyUsageAndSettlementAreServerCalculated() throws Exception {
        String source = source("modules/mobile/controller/MobileStaffController.java");
        assertTrue(source.contains("currentReading.subtract(previous)"));
        assertTrue(source.contains(".multiply(multiplier)"));
        assertTrue(source.contains("usage.multiply(unitPrice)"));
        assertTrue(source.contains("confirmEnergyBasis"));
        assertTrue(source.contains("该表具同月份已存在抄表记录"));
    }

    @Test
    void meetingActionsUseSpecificCapabilitiesAndStateMachine() throws Exception {
        String source = source("modules/mobile/controller/MobileStaffController.java");
        assertTrue(source.contains("meeting.staff.confirm"));
        assertTrue(source.contains("meeting.staff.cancel"));
        assertTrue(source.contains("meeting.staff.complete"));
        assertTrue(source.contains("当前会议预约状态不能执行该操作"));
    }

    @Test
    void visitorVerificationIsIdempotentAndAudited() throws Exception {
        String source = source("modules/mobile/controller/MobileStaffController.java");
        assertTrue(source.contains("if (\"ENTERED\".equals(current))"));
        assertTrue(source.contains("if (\"LEFT\".equals(current))"));
        assertTrue(source.contains("INSERT INTO visitor_pass_record"));
        assertTrue(source.contains("operator_id"));
    }

    @Test
    void investmentAppointmentsReuseLeadAndFollowTables() throws Exception {
        String source = source("modules/mobile/controller/MobileStaffController.java");
        assertTrue(source.contains("FROM investment_lead lead_record"));
        assertTrue(source.contains("INSERT INTO investment_follow_record"));
        assertTrue(source.contains("INSERT INTO investment_lead_status_history"));
        assertFalse(source.contains("CREATE TABLE viewing_appointment"));
    }

    @Test
    void internalFilesRequireBusinessAuthorization() throws Exception {
        String source = source("modules/mobile/service/MobileStaffFileService.java");
        String fileController = source("modules/file/controller/FileController.java");
        assertTrue(source.contains("access_scope = 'INTERNAL'"));
        assertTrue(source.contains("owner_sys_user_id"));
        assertTrue(source.contains("inspection.staff.view"));
        assertTrue(source.contains("assignee_id = ?"));
        assertTrue(source.contains("submitter_mobile_user_id = ?"));
        assertTrue(fileController.contains("mobileStaffFileService.requireDownload(principal, id)"));
        assertTrue(fileController.contains("\"SYSTEM\".equals(principal.userType())"));
        assertTrue(fileController.contains("exception.getCode() != 404"));
    }

    @Test
    void imageCustomFieldsAreClaimedAndHydratedThroughProtectedUrls() throws Exception {
        String controller = source("modules/mobile/controller/MobileStaffController.java");
        String repository = source("modules/customfield/infrastructure/CustomFieldRepository.java");
        assertTrue(controller.contains("customFieldFileIds(body.get(\"customFields\"))"));
        assertTrue(controller.contains("\"INSPECTION\", id, staffUserId"));
        assertTrue(controller.contains("\"WORK_ORDER\", id, staffUserId"));
        assertTrue(controller.contains("\"ENERGY_READING\", id, staffUserId"));
        assertTrue(repository.contains("access_scope"));
        assertTrue(repository.contains("\"/api/mobile/files/\" + id"));
    }

    @Test
    void migrationIsAdditiveAndPreservesHistory() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V49__close_staff_mobile_workbench.sql"
        ));
        assertTrue(sql.contains("inspection_record_history"));
        assertTrue(sql.contains("work_order_record_id"));
        assertTrue(sql.contains("access_scope"));
        assertFalse(sql.contains("DELETE FROM"));
        assertFalse(sql.contains("DROP TABLE"));
    }

    @Test
    void mobileAuthorizationMigrationCreatesIndependentPermissionDomain() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V55__separate_property_mobile_workbench_permissions.sql"
        ));
        String boundaryFix = Files.readString(Path.of(
                "src/main/resources/db/migration/V56__keep_tenant_and_finance_roles_out_of_property_mobile.sql"
        ));
        assertTrue(sql.contains("staff-mobile:access"));
        assertTrue(sql.contains("staff-mobile:workorder:view"));
        assertTrue(sql.contains("staff-mobile:visitor:write"));
        assertFalse(sql.contains("DELETE FROM"));
        assertFalse(sql.contains("DROP TABLE"));
        assertTrue(boundaryFix.contains("'TENANT'"));
        assertTrue(boundaryFix.contains("'FINANCE'"));
        assertTrue(boundaryFix.contains("permission_record.module = 'staff-mobile'"));
    }

    @Test
    void schemaUsesCanonicalInspectionColumnsAndServerDerivedFields() throws Exception {
        String source = source("modules/customfield/application/ModuleFieldRegistry.java");
        assertTrue(source.contains("\"location\", \"area\""));
        assertTrue(source.contains("\"inspectionObject\", \"target\""));
        assertTrue(source.contains("\"handlingMeasures\", \"action_taken\""));
        assertTrue(source.contains("\"inspector\", \"inspector\", true, false"));
        assertTrue(source.contains("option(\"OPEN\", \"待处理\")"));
        assertTrue(source.contains("option(\"CLOSED\", \"已关闭\")"));
        assertTrue(source.contains("\"unitPrice\", \"unit_price\", false, true"));
    }

    @Test
    void staffInspectionCreateUsesTheConfiguredTypeDictionary() throws Exception {
        String registry = source("modules/customfield/application/ModuleFieldRegistry.java");
        String controller = source("modules/mobile/controller/MobileStaffController.java");

        assertTrue(registry.contains("field(\"inspectionType\", \"巡检类型\", \"SELECT\""));
        assertTrue(registry.contains("type.put(\"optionsSource\", \"/mobile/staff/inspection-types\")"));
        assertTrue(controller.contains("@GetMapping(\"/mobile/staff/inspection-types\")"));
        assertTrue(controller.contains("WHERE status = 'ENABLED' AND deleted = 0"));
        assertTrue(controller.contains("CHAR_LENGTH(TRIM(code)) <= 32"));
        assertTrue(controller.contains("InspectionTypeValueResolver.resolve("));
    }

    @Test
    void staffInspectionCreateAcceptsInspectorTextButKeepsIdentityServerDerived() throws Exception {
        String registry = source("modules/customfield/application/ModuleFieldRegistry.java");
        String controller = source("modules/mobile/controller/MobileStaffController.java");

        assertTrue(registry.contains("field(\"inspector\", \"巡检人\", \"TEXT\""));
        assertTrue(registry.contains("configureValidation(inspector, Map.of(\"maxLength\", 64))"));
        assertTrue(controller.contains("InspectionInspectorValueResolver.resolve("));
        assertTrue(controller.contains("Long inspectorUserId = staffUserId;"));
    }

    private String source(String relative) throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/dehui/property/" + relative
        ));
    }
}

package com.dehui.property.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkspaceOrderByTest {

    private static final Map<String, String> FIELDS = Map.of(
            "status", "record.status",
            "name", "record.name",
            "updatedAt", "record.updated_at"
    );

    @Test
    void groupOrderingPrecedesExplicitSortAndDuplicateFieldsAreIgnored() {
        String orderBy = WorkspaceOrderBy.build(
                FIELDS,
                "status:asc",
                "status:desc,name:desc",
                null,
                null,
                "record.updated_at",
                "record.id"
        );

        assertEquals("record.status ASC, record.name DESC, record.id DESC", orderBy);
    }

    @Test
    void legacyOrderingRemainsCompatibleWhenWorkspaceOrderingIsAbsent() {
        assertEquals(
                "record.name ASC, record.id DESC",
                WorkspaceOrderBy.build(
                        FIELDS, null, null, "name", "asc", "record.updated_at", "record.id"
                )
        );
        assertEquals(
                "record.updated_at DESC, record.id DESC",
                WorkspaceOrderBy.build(
                        FIELDS, null, null, "unknown", "invalid", "record.updated_at", "record.id"
                )
        );
        assertEquals(
                "record.updated_at DESC, record.id DESC",
                WorkspaceOrderBy.build(
                        FIELDS, null, null, null, null, "record.updated_at", "record.id"
                )
        );
    }

    @Test
    void unknownFieldsAndInvalidDirectionsNeverReachSql() {
        String orderBy = WorkspaceOrderBy.build(
                FIELDS,
                "status:sideways,unknown:asc",
                "name:desc;DROP TABLE tenant,updatedAt:desc",
                null,
                null,
                "record.updated_at",
                "record.id"
        );

        assertEquals("record.updated_at DESC, record.id DESC", orderBy);
    }
}

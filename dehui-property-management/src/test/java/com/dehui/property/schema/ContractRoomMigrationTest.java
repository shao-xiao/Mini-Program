package com.dehui.property.schema;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ContractRoomMigrationTest {

    @Test
    void migrationCreatesContractRoomLinkTable() throws IOException {
        String sql = readMigration();

        assertTrue(sql.contains("CREATE TABLE contract_room"), "contract_room table must be created");
        assertTrue(sql.contains("contract_id BIGINT NOT NULL"), "contract_id column must exist");
        assertTrue(sql.contains("room_id BIGINT NOT NULL"), "room_id column must exist");
        assertTrue(sql.contains("idx_contract_room_contract"), "contract index must exist");
        assertTrue(sql.contains("idx_contract_room_room"), "room index must exist");
        assertTrue(sql.contains("INSERT IGNORE INTO contract_room"), "existing contract.room_id data must be backfilled");
    }

    private String readMigration() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/db/migration/V23__contract_room_link.sql")) {
            assertNotNull(input, "contract-room migration must exist");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

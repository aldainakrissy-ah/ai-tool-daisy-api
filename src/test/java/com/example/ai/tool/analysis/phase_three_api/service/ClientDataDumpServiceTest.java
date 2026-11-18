package com.example.ai.tool.analysis.phase_three_api.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** The dump of given clients only accepts a plain schema name, since it goes into native SQL. */
class ClientDataDumpServiceTest {

    @Test
    void aSchemaNameThatIsNotPlainIsRefusedBeforeAnyQuery() {
        ClientDataDumpService service = new ClientDataDumpService();  // no EntityManager: no query may run
        Map<String, Object> client = Map.of("id", 1, "schema_name", "public; drop table clients");

        assertThrows(IllegalArgumentException.class, () -> service.getClientDataDump(List.of(client)));
    }
}

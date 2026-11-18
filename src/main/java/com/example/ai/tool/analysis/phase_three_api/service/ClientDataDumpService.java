package com.example.ai.tool.analysis.phase_three_api.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ClientDataDumpService {

    private static final Logger logger = LoggerFactory.getLogger(ClientDataDumpService.class);

    @PersistenceContext(unitName = "phase_three_api")
    private EntityManager entityManager;

    private static final List<String> TABLES_WITH_CLIENT_ID = Arrays.asList(
        "client_addictions", "client_allergies", "client_allergy_miscs", "client_bios",
        "client_bloodline_diseases", "client_career_and_education_details", "client_family_details",
        "client_food_intolerance_miscs", "client_food_intolerances", "client_food_supplements",
        "client_general_info_form_statuses", "client_important_note_to_professionals",
        "client_involved_health_professionals", "client_medications", "client_messages",
        "client_new_activities", "client_other_medications", "client_personal_contact_details",
        "client_prescribed_medications", "client_request_for_helps", "client_surgeries",
        "client_vaccinations", "client_vice_frequencies"
    );

    private static final java.util.regex.Pattern SCHEMA_NAME = java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    // Special handling for these tables
    private static final List<String> TABLES_WITHOUT_CLIENT_ID = Arrays.asList(
        "client_bio_females", "client_gids"
    );

    /** The data dump of every portal client with this email; empty when there is none. Does not log the email. */
    @Transactional(readOnly = true)
    public Map<String, List<Map<String, Object>>> getClientDataDump(String email) {
        List<Map<String, Object>> clients = findClientsByEmail(email);
        if (clients.isEmpty()) {
            logger.warn("No client found for the given email");
            return Collections.emptyMap();
        }
        return getClientDataDump(clients);
    }

    /**
     * The portal clients with this email, in every schema with a clients table (two queries): the {@code clients}
     * rows of {@link #getClientDataDump(String)}, so a caller can check them before the dump. Does not log the email.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> findClientsByEmail(String email) {
        return databaseCall("the client lookup", () -> findClients(email));
    }

    /**
     * The data dump of clients as {@link #findClientsByEmail(String)} returns them (the schema name must be a plain
     * schema name). Does not log the email.
     */
    @Transactional(readOnly = true)
    public Map<String, List<Map<String, Object>>> getClientDataDump(List<Map<String, Object>> clients) {
        return databaseCall("the client data dump", () -> dump(clients));
    }

    /** Runs a lookup; a database error becomes one RuntimeException with what failed (no personal data). */
    private static <T> T databaseCall(String what, java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("An error occurred during {}", what, e);
            throw new RuntimeException("Failed to retrieve client data due to a database error (" + what + ").", e);
        }
    }

    private List<Map<String, Object>> findClients(String email) {
        // Find all schemas that have a 'clients' table
        List<String> schemas = findSchemasWithClientsTable();
        if (schemas.isEmpty()) {
            logger.warn("No schemas found with a 'clients' table.");
            return Collections.emptyList();
        }

        String clientQueryStr = schemas.stream()
            .map(schema -> String.format("SELECT CAST(id AS TEXT), CAST(client_gid_id AS TEXT), first_name, family_name, '%s' as schema_name FROM %s.clients WHERE lower(email) = lower(:email)", schema, schema))
            .collect(Collectors.joining(" UNION ALL "));

        Query clientQuery = entityManager.createNativeQuery(clientQueryStr);
        clientQuery.setParameter("email", email);
        List<Object[]> rows = clientQuery.getResultList();

        List<Map<String, Object>> clients = new ArrayList<>();
        for (Object[] clientData : rows) {
            Map<String, Object> clientInfo = new HashMap<>();
            clientInfo.put("id", Long.parseLong((String) clientData[0]));
            clientInfo.put("client_gid_id", clientData[1] != null ? String.valueOf(clientData[1]) : null);
            clientInfo.put("first_name", clientData[2]);
            clientInfo.put("family_name", clientData[3]);
            clientInfo.put("email", email);
            clientInfo.put("schema_name", clientData[4]);
            clients.add(clientInfo);
        }
        return clients;
    }

    private Map<String, List<Map<String, Object>>> dump(List<Map<String, Object>> clients) {
        Map<String, List<Map<String, Object>>> allData = new HashMap<>();
        for (Map<String, Object> clientInfo : clients) {
            Long clientId = ((Number) clientInfo.get("id")).longValue();
            String clientGidId = clientInfo.get("client_gid_id") == null ? null : String.valueOf(clientInfo.get("client_gid_id"));
            String schemaName = String.valueOf(clientInfo.get("schema_name"));
            if (!SCHEMA_NAME.matcher(schemaName).matches()) {  // it goes into native SQL: only a plain schema name
                throw new IllegalArgumentException("Not a schema name: " + schemaName);
            }
            logger.info("Processing data for client: {}", String.format("schema '%s', clientId '%d'", schemaName, clientId));

            // Handle base client table data
            allData.computeIfAbsent("clients", k -> new ArrayList<>()).add(clientInfo);

            // Query all tables that have a direct client_id relationship
            for (String tableName : TABLES_WITH_CLIENT_ID) {
                allData.computeIfAbsent(tableName, k -> new ArrayList<>())
                       .addAll(queryTableByClientId(tableName, schemaName, clientId));
            }

            // Special handling for client_gids
            if (clientGidId != null) {
                 allData.computeIfAbsent("client_gids", k -> new ArrayList<>())
                       .addAll(queryTableByColumn( "client_gids", schemaName, "id", Long.parseLong(clientGidId)));
            }

            // Special handling for client_bio_females (joins through client_bios)
            Long clientBioId = getClientBioId(schemaName, clientId);
            if (clientBioId != null) {
                allData.computeIfAbsent("client_bio_females", k -> new ArrayList<>())
                       .addAll(queryTableByColumn("client_bio_females", schemaName, "client_bio_id", clientBioId));
            }
        }
        return allData;
    }

    private List<Map<String, Object>> queryTableByClientId(String tableName, String schemaName, Long clientId) {
        return queryTableByColumn(tableName, schemaName, "client_id", clientId);
    }
    
    private List<Map<String, Object>> queryTableByColumn(String tableName, String schemaName, String columnName, Object value) {
        String sql = String.format("SELECT * FROM %s.%s WHERE %s = :value", schemaName, tableName, columnName);
        try {
            Query query = entityManager.createNativeQuery(sql);
            query.setParameter("value", value);
            List<Object[]> resultList = query.getResultList();
            if (resultList.isEmpty()) {
                return Collections.emptyList();
            }
            // Get column names for mapping
            List<String> columnNames = getColumnNames(schemaName, tableName);
            return mapResult(resultList, columnNames);
        } catch (Exception e) {
            logger.error("Failed to query table '{}' in schema '{}'", tableName, schemaName, e);
            return Collections.emptyList(); // Return empty list for this table on error
        }
    }

    private Long getClientBioId(String schemaName, Long clientId) {
        try {
            Query query = entityManager.createNativeQuery("SELECT id FROM " + schemaName + ".client_bios WHERE client_id = :clientId");
            query.setParameter("clientId", clientId);
            List<Number> results = query.getResultList();
            return results.isEmpty() ? null : results.get(0).longValue();
        } catch (Exception e) {
            logger.error("Could not retrieve client_bio_id for client {} in schema {}", clientId, schemaName, e);
            return null;
        }
    }

    private List<String> getColumnNames(String schemaName, String tableName) {
        Query query = entityManager.createNativeQuery(
            "SELECT column_name FROM information_schema.columns " +
            "WHERE table_schema = :schemaName AND table_name = :tableName " +
            "ORDER BY ordinal_position"
        );
        query.setParameter("schemaName", schemaName);
        query.setParameter("tableName", tableName);
        return (List<String>) query.getResultList();
    }
    
    private List<Map<String, Object>> mapResult(List<Object[]> resultList, List<String> columnNames) {
        List<Map<String, Object>> mappedResults = new ArrayList<>();
        for (Object[] row : resultList) {
            Map<String, Object> rowMap = new HashMap<>();
            for (int i = 0; i < columnNames.size(); i++) {
                rowMap.put(columnNames.get(i), row[i]);
            }
            mappedResults.add(rowMap);
        }
        return mappedResults;
    }

    private List<String> findSchemasWithClientsTable() {
        String schemaQueryStr = "SELECT table_schema FROM information_schema.tables WHERE table_name = 'clients' AND table_schema NOT IN ('pg_catalog', 'information_schema')";
        Query schemaQuery = entityManager.createNativeQuery(schemaQueryStr);
        return (List<String>) schemaQuery.getResultList();
    }
}

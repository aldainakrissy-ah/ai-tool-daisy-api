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

    // Special handling for these tables
    private static final List<String> TABLES_WITHOUT_CLIENT_ID = Arrays.asList(
        "client_bio_females", "client_gids"
    );

    @Transactional(readOnly = true)
    public Map<String, List<Map<String, Object>>> getClientDataDump(String email) {
        logger.info("Starting data dump for email: {}", email);
        Map<String, List<Map<String, Object>>> allData = new HashMap<>();

        try {
            // Find all schemas that have a 'clients' table
            List<String> schemas = findSchemasWithClientsTable();
            if (schemas.isEmpty()) {
                logger.warn("No schemas found with a 'clients' table.");
                return Collections.emptyMap();
            }

            String clientQueryStr = schemas.stream()
                .map(schema -> String.format("SELECT CAST(id AS TEXT), CAST(client_gid_id AS TEXT), first_name, family_name, '%s' as schema_name FROM %s.clients WHERE lower(email) = lower(:email)", schema, schema))
                .collect(Collectors.joining(" UNION ALL "));

            Query clientQuery = entityManager.createNativeQuery(clientQueryStr);
            clientQuery.setParameter("email", email);
            List<Object[]> clients = clientQuery.getResultList();

            if (clients.isEmpty()) {
                logger.warn("No client found with email: {}", email);
                return Collections.emptyMap();
            }

            for (Object[] clientData : clients) {
                Long clientId = Long.parseLong((String) clientData[0]);
                String clientGidId = clientData[1] != null ? String.valueOf(clientData[1]) : null;
                String schemaName = (String) clientData[4];
                String clientIdentifier = String.format("schema '%s', clientId '%d'", schemaName, clientId);

                logger.info("Processing data for client: {}", clientIdentifier);

                // Handle base client table data
                Map<String, Object> clientInfo = new HashMap<>();
                clientInfo.put("id", clientId);
                clientInfo.put("client_gid_id", clientGidId);
                clientInfo.put("first_name", clientData[2]);
                clientInfo.put("family_name", clientData[3]);
                clientInfo.put("email", email);
                clientInfo.put("schema_name", schemaName);
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

        } catch (Exception e) {
            logger.error("An error occurred during data dump for email: {}", email, e);
            throw new RuntimeException("Failed to retrieve client data dump due to a database error.", e);
        }

        logger.info("Successfully completed data dump for email: {}", email);
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

package com.example.ai.tool.analysis.phase_three_api.service;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ai.tool.analysis.phase_three_api.entity.Client;
import com.example.ai.tool.analysis.phase_three_api.pojo.ClientGidWithClientInfo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

@Service
public class ClientService {

    private static final Logger logger = LoggerFactory.getLogger(ClientService.class);

    @PersistenceContext(unitName = "phase_three_api")
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<Client> findAllClientsFromAllSchemas() {
        logger.info("Attempting to find all clients from all schemas.");
        try {
            // Step 1: Find all schemas that contain a 'clients' table
            String schemaQueryStr = "SELECT table_schema FROM information_schema.tables WHERE table_name = 'clients' AND table_schema NOT IN ('pg_catalog', 'information_schema')";
            Query schemaQuery = entityManager.createNativeQuery(schemaQueryStr);
            @SuppressWarnings("unchecked")
            List<String> schemas = (List<String>) schemaQuery.getResultList();

            if (schemas.isEmpty()) {
                logger.warn("No schemas found containing a 'clients' table. Returning empty list.");
                return List.of();
            }
            logger.info("Found {} schemas containing 'clients' table: {}", schemas.size(), schemas);

            // Step 2: Dynamically build the UNION ALL query WITH TYPE CASTING
            String unionQuery = schemas.stream()
                    .map(schema -> "SELECT id, CAST(client_gid_id AS TEXT) as client_gid_id, email, first_name, family_name FROM "
                            + schema + ".clients")
                    .collect(Collectors.joining(" UNION ALL "));

            logger.debug("Executing dynamic union query: {}", unionQuery);

            // Step 3: Execute the query and map the result to the Client entity
            Query finalQuery = entityManager.createNativeQuery(unionQuery, Client.class);

            @SuppressWarnings("unchecked")
            List<Client> result = finalQuery.getResultList();
            logger.info("Successfully retrieved {} clients from all schemas.", result.size());
            return result;

        } catch (Exception e) {
            logger.error("An error occurred while fetching clients from all schemas", e);
            // Re-throwing the exception is important so that a proper 500 error is returned
            throw new RuntimeException("Failed to retrieve client data due to a database error.", e);
        }
    }

    @Transactional(readOnly = true)
    public List<ClientGidWithClientInfo> findAllClientGidsWithClientInfoFromAllSchemas() {
        logger.info("Attempting to find all client_gids with client info from all schemas.");
        try {
            // Step 1: Find all schemas that contain both 'client_gids' and 'clients' tables
            String schemaQueryStr = "SELECT table_schema FROM information_schema.tables WHERE table_name IN ('client_gids', 'clients') AND table_schema NOT IN ('pg_catalog', 'information_schema') GROUP BY table_schema HAVING COUNT(DISTINCT table_name) = 2";
            Query schemaQuery = entityManager.createNativeQuery(schemaQueryStr);
            @SuppressWarnings("unchecked")
            List<String> schemas = (List<String>) schemaQuery.getResultList();

            if (schemas.isEmpty()) {
                logger.warn("No schemas found containing both 'client_gids' and 'clients' tables. Returning empty list.");
                return List.of();
            }
            logger.info("Found {} schemas containing both 'client_gids' and 'clients' tables: {}", schemas.size(), schemas);

            // Step 2: Dynamically build the UNION ALL query
            String unionQuery = schemas.stream()
                    .map(schema -> "SELECT CAST(c.id AS TEXT) as client_id, CAST(c.email AS TEXT), CAST(c.first_name AS TEXT), CAST(c.family_name AS TEXT), CAST(cg.id AS TEXT), CAST(cg.professional_id AS TEXT), CAST(c.client_gid_id AS TEXT) FROM "
                            + schema + ".clients c LEFT JOIN " + schema + ".client_gids cg ON CAST(TRIM(CAST(c.client_gid_id AS TEXT)) AS BIGINT) = cg.id")
                    .collect(Collectors.joining(" UNION ALL "));

            logger.debug("Executing dynamic union query: {}", unionQuery);

            // Step 3: Execute the query
            Query finalQuery = entityManager.createNativeQuery(unionQuery);

            @SuppressWarnings("unchecked")
            List<Object[]> result = finalQuery.getResultList();
            logger.info("Successfully retrieved {} client_gids with client info from all schemas.", result.size());

            return result.stream()
                    .map(row -> {
                        try {
                            return ClientGidWithClientInfo.builder()
                                    .clientId(row[0] != null ? Long.parseLong((String) row[0]) : null)
                                    .email((String) row[1])
                                    .firstName((String) row[2])
                                    .familyName((String) row[3])
                                    .id(row[4] != null ? Long.parseLong((String) row[4]) : null)
                                    .professionalId((String) row[5])
                                    .clientGidId((String) row[6])
                                    .build();
                        } catch (Exception e) {
                            logger.error("Error mapping row to ClientGidWithClientInfo. Row data: {}", java.util.Arrays.toString(row), e);
                            return null;
                        }
                    })
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("An error occurred while fetching client_gids with client info from all schemas", e);
            throw new RuntimeException("Failed to retrieve client_gid data due to a database error.", e);
        }
    }
}

package com.example.ai.tool.analysis.phase_three_api.service;

import com.example.ai.tool.analysis.phase_three_api.entity.Client;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

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
}

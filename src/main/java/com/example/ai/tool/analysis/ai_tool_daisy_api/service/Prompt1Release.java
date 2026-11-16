package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Everything of Jan's that one Prompt 1 run reads, from one release: the turn configuration, the system
 * message, doc 18, the output schema, the B2 matrix and the document versions of the reference documents.
 * A run takes its release at submit and keeps it to the end, so activating another release changes no run
 * that is already queued or running. The texts are confidential: never log them.
 *
 * @param id                the release ID in the release store; null for the local files
 * @param name              the release name, e.g. P1-v0.136 (the turn configuration's release for local files)
 * @param documentVersions  document code → version of the reference documents (the knowledge tables)
 */
public record Prompt1Release(String id, String name, Prompt1TurnConfig config, String systemMessage,
                             String systemMessageFile, String doc18, String schemaText,
                             Map<String, List<String>> b2Matrix, Map<String, String> documentVersions) {

    public static final String PROMPT = "P1";

    /** The file roles of a Prompt 1 release; each appears exactly once (reference documents once per code). */
    public static final String SYSTEM_MESSAGE = "system_message";
    public static final String DOC18 = "doc18";
    public static final String SCHEMA = "schema";
    public static final String B2_MATRIX = "b2_matrix";
    public static final String TURN_CONFIG = "turn_config";
    public static final String REFERENCE = "reference";
    public static final List<String> FILE_ROLES = List.of(SYSTEM_MESSAGE, DOC18, SCHEMA, B2_MATRIX, TURN_CONFIG);

    /** The reference documents the turn route reads by key. */
    public static final List<String> REFERENCE_CODES = List.of("01", "02", "03", "05", "06", "07", "R3", "R4", "R5");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Builds a release from its file texts and checks that they can be read: the turn configuration, the
     * schema as JSON and the B2 matrix against the configuration's B2 order.
     *
     * @throws IllegalArgumentException with the file that cannot be read
     */
    public static Prompt1Release of(String id, String name, byte[] turnConfig, String systemMessage,
                                    String systemMessageFile, String doc18, String schemaText, String b2MatrixText,
                                    Map<String, String> documentVersions) {
        Prompt1TurnConfig config;
        try {
            config = Prompt1TurnConfig.parse(turnConfig);
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Turn configuration cannot be read: " + e.getMessage(), e);
        }
        try {
            MAPPER.readTree(schemaText);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Output schema is not valid JSON: " + e.getOriginalMessage(), e);
        }
        Map<String, List<String>> matrix;
        try {
            matrix = Prompt1TurnOutput.b2Matrix(b2MatrixText, config.b2Order());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("B2 matrix cannot be read: " + e.getMessage(), e);
        }
        return new Prompt1Release(id, name == null ? config.release() : name, config, systemMessage, systemMessageFile,
                doc18, schemaText, matrix, Map.copyOf(documentVersions));
    }
}

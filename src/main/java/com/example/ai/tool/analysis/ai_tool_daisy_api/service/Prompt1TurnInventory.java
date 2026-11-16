package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1ExperimentService.IntakeFile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * SM Step 0: the technical observation inventory (numbering and serialising, no judgement), ported
 * from Jan's {@code inventory.py}. Every answered question and every structured field of the client
 * files becomes one numbered observation; question templates and empty answers are left out.
 * The output holds client data: never log it.
 */
public final class Prompt1TurnInventory {

    static final String MANIFEST_HEADER = "source_id\tsource_name\tsource_type\tsource_order\tdocument_type";
    static final String OBSERVATIONS_HEADER =
            "observation_id\tsource_id\trecord_type\tsource_locator\tsource_context\tsource_label\tsource_value";
    static final String AUDIT_HEADER = "status\ttotal_observation_count\tsource_id\tobservation_count";

    private static final Set<String> EXCLUDED = Set.of("template_placeholder", "template_options", "template_only");
    private static final Set<String> NULL_VALUES = Set.of("None", "null", "");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** The three server files of Step 0, as complete TSV texts. */
    public record Inventory(String manifest, String observations, String audit, int observationCount) {
    }

    private Prompt1TurnInventory() {
    }

    /** Builds the inventory from the client files, in upload order (SRC_01, SRC_02, …). */
    public static Inventory build(List<IntakeFile> files) {
        List<List<String>> manifest = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            IntakeFile file = files.get(i);
            String sourceId = String.format("SRC_%02d", i + 1);
            JsonNode doc = parse(file);
            String documentType = doc.path("document_type").isMissingNode() ? Prompt1Tsv.EMPTY
                    : text(doc.get("document_type"));
            manifest.add(List.of(sourceId, file.name(), sourceType(doc), String.valueOf(i + 1), documentType));
            rows.addAll(observations(sourceId, doc));
        }
        List<List<String>> observations = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = new ArrayList<>();
            row.add(String.format("OBS_%03d", i + 1));
            row.addAll(rows.get(i));
            observations.add(row);
        }
        List<List<String>> audit = new ArrayList<>();
        for (List<String> source : manifest) {
            long count = observations.stream().filter(row -> row.get(1).equals(source.get(0))).count();
            audit.add(List.of("closed", String.valueOf(observations.size()), source.get(0), String.valueOf(count)));
        }
        return new Inventory(tsv(MANIFEST_HEADER, manifest), tsv(OBSERVATIONS_HEADER, observations),
                tsv(AUDIT_HEADER, audit), observations.size());
    }

    /**
     * Refuses an inventory a run cannot work with: no source with document_type intake questionnaire, or not a
     * single filled answer (e.g. a file without structured_data). Checked before a run is created.
     */
    public static void requireClientData(Inventory inventory) {
        boolean questionnaire = Prompt1Tsv.rows(inventory.manifest()).stream()
                .anyMatch(source -> "intake_questionnaire".equals(source.get("source_type")));
        if (!questionnaire) {
            throw new IllegalArgumentException("No intake questionnaire among the client files "
                    + "(document_type must name the questionnaire or intake)");
        }
        boolean answered = Prompt1Tsv.rows(inventory.observations()).stream()
                .anyMatch(observation -> !isNullField(observation)
                        && !Prompt1Tsv.EMPTY.equals(observation.getOrDefault("source_value", "").strip()));
        if (!answered) {
            throw new IllegalArgumentException("The client files hold no filled answers (structured_data is missing or empty)");
        }
    }

    /** An empty intake field: a structured field whose value is null (never evidence; turn A1 leaves it out). */
    public static boolean isNullField(Map<String, String> observation) {
        return "structured_field".equals(observation.get("record_type"))
                && NULL_VALUES.contains(observation.getOrDefault("source_value", "").strip());
    }

    /** Only from explicit structure: the document_type field. */
    static String sourceType(JsonNode doc) {
        String kind = doc.path("document_type").isMissingNode() ? "" : text(doc.get("document_type")).toLowerCase(Locale.ROOT);
        if (kind.equals("general_client_info") || kind.equals("personal_data")) {
            return "personal_data";
        }
        if (kind.contains("questionnaire") || kind.contains("vragenlijst") || kind.contains("intake")) {
            return "intake_questionnaire";
        }
        if (kind.contains("consult")) {
            return "intake_consult";
        }
        return "other_client_source";
    }

    /** The rows of one source (without observation_id), in Jan's traversal order. */
    static List<List<String>> observations(String sourceId, JsonNode doc) {
        List<List<String>> rows = new ArrayList<>();
        JsonNode data = doc.get("structured_data");
        if (data != null && data.isObject()) {
            walk(sourceId, data, "structured_data", null, rows);
        }
        return rows;
    }

    private static void walk(String sourceId, JsonNode node, String path, String title, List<List<String>> rows) {
        Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String p = path + "." + field.getKey();
            JsonNode value = field.getValue();
            if (value.isArray()) {
                JsonNode nodeTitle = node.get("title");
                walkList(sourceId, value, p, nodeTitle != null ? text(nodeTitle) : title, rows);
            } else if (value.isObject()) {
                walk(sourceId, value, p, title, rows);
            } else {
                rows.add(row(sourceId, "structured_field", p, path, field.getKey(), scalar(value)));
            }
        }
    }

    private static void walkList(String sourceId, JsonNode array, String path, String title, List<List<String>> rows) {
        for (int i = 0; i < array.size(); i++) {
            JsonNode value = array.get(i);
            String p = path + "[" + i + "]";
            if (isQuestion(value)) {
                String status = value.has("capture_status") ? text(value.get("capture_status")) : "";
                if (EXCLUDED.contains(status) || text(value.get("answer")).strip().isEmpty()) {
                    continue;
                }
                ObjectNode qa = MAPPER.createObjectNode();
                qa.set("question", value.get("question"));
                qa.set("answer", value.get("answer"));
                rows.add(row(sourceId, "question_response", p, title, text(value.get("id")), json(qa)));
            } else if (value.isObject() && (value.has("questions") || value.has("subsections"))) {
                JsonNode nodeTitle = value.get("title");
                String t = nodeTitle != null ? text(nodeTitle) : title;
                Iterator<Map.Entry<String, JsonNode>> fields = value.properties().iterator();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    if (field.getValue().isArray()) {
                        walkList(sourceId, field.getValue(), p + "." + field.getKey(), t, rows);
                    }
                }
            } else if (value.isObject()) {
                rows.add(row(sourceId, "structured_field", p, path, String.valueOf(i), json(value)));
            } else if (value.isArray()) {
                walkList(sourceId, value, p, title, rows);
            } else {
                rows.add(row(sourceId, "structured_field", p, path, String.valueOf(i), scalar(value)));
            }
        }
    }

    private static boolean isQuestion(JsonNode value) {
        return value.isObject() && value.has("question") && value.has("answer") && value.has("id");
    }

    private static List<String> row(String sourceId, String type, String locator, String context, String label,
                                    String value) {
        return Arrays.asList(sourceId, type, locator, context, label, value);
    }

    /** A string as it is; anything else as compact JSON (Jan's {@code cj}). */
    private static String scalar(JsonNode value) {
        return value.isTextual() ? value.textValue() : json(value);
    }

    /** Python's {@code str(v)} for the values that matter here: text as it is, null as "None", the rest as JSON. */
    private static String text(JsonNode value) {
        if (value == null || value.isNull()) {
            return "None";
        }
        if (value.isBoolean()) {
            return value.booleanValue() ? "True" : "False";
        }
        return value.isTextual() ? value.textValue() : json(value);
    }

    private static String json(JsonNode value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise an intake value", e);
        }
    }

    private static JsonNode parse(IntakeFile file) {
        try {
            String text = new String(file.content(), StandardCharsets.UTF_8);
            if (text.startsWith("﻿")) {
                text = text.substring(1);
            }
            return MAPPER.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Client file " + file.name() + " is not valid JSON", e);
        }
    }

    /** Jan's {@code inventory.tsv}: an empty or missing value is "—"; tabs and line breaks become spaces. */
    private static String tsv(String header, List<List<String>> rows) {
        List<List<String>> cells = new ArrayList<>();
        for (List<String> row : rows) {
            cells.add(row.stream().map(value -> Prompt1Tsv.cell(value)).toList());
        }
        return Prompt1Tsv.text(header, cells);
    }
}

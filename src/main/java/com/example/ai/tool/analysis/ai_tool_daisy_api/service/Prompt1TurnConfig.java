package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The fixed tables and texts of Jan's turn-based Prompt 1 route for one release, kept as data
 * (e.g. {@code turns-v0136.json}, a file of the release, never in the repository) so that most of
 * his weekly updates are edits to a file, not to code. Ported from {@code daisy_preload.py}: {@code SM_SECTIONS}, {@code S0_DROP},
 * {@code S0_D_ONLY}, {@code HEADERS}, {@code TURNS}, the doc 18 parts per turn and the turn texts.
 *
 * @param smSections   top-level SM sections each turn receives (§0 always; turn "C" covers every C_&lt;heta&gt;)
 * @param s0Drop       §0 lines left out of every turn (the backend does tools, retrieval, files)
 * @param s0DOnly      §0 lines on the final JSON; left out of every turn, because the backend writes it (v04z)
 * @param headers      working file → its doc 18 header line (tab-separated)
 * @param turns        turn → steps, the files the model returns, the steps closed on ingest
 * @param cFiles       the files a C turn returns, without header rows
 * @param doc18Headings turn → the doc 18 '###' sections it receives, besides {@code doc18General}
 * @param b2Order      the B2 questionnaires in doc 07 order (rows 1–9 and 11–20; YSQ, row 10, has its own trigger)
 * @param qScope       questionnaire → its short observation scope (observable_dimensions, covered_information)
 * @param neNeed       the need text for a HETA that is indirect only through a canonical signal
 * @param common       the shared turn text, as lines (joined with \n)
 * @param cInstruction the C turn text, as lines (joined with \n)
 * @param schemaLabel  the schema name shown above the output schema in turn D
 * @param correction   the message that sends a refused answer back ({error})
 * @param continuation the message that asks for the rest of a cut-off answer ({name}, {line})
 * @param texts        the per-turn instruction lines; {name} marks a value filled in at run time
 */
public record Prompt1TurnConfig(String release, List<String> ies, Set<String> sigAbsent,
                                Map<String, List<Integer>> smSections, List<String> s0Drop, List<String> s0DOnly,
                                Map<String, String> headers, Map<String, Turn> turns, List<String> cFiles,
                                String doc18General, Map<String, List<String>> doc18Headings, List<String> b2Order,
                                Map<String, String> qScope, String neNeed,
                                String promptHeader, String schemaLabel, String correction, String continuation,
                                List<String> common, List<String> cInstruction, Map<String, String> texts) {

    /** One turn: the steps it executes, the files the model returns, the steps closed when it is ingested. */
    public record Turn(String steps, List<String> files, String closeSteps) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    /**
     * Reads a turn configuration from a file path. The configuration holds Jan's turn texts, so it is never
     * part of the code: it comes from a release in the release store, or locally from this path.
     */
    public static Prompt1TurnConfig load(String path) throws IOException {
        if (path == null || path.isBlank()) {
            throw new IOException("No turn configuration configured (experiment.prompt1.turns.config)");
        }
        Path file = Path.of(path.trim());
        if (!Files.isRegularFile(file)) {
            throw new IOException("Turn configuration not found: " + file.getFileName());
        }
        return parse(Files.readAllBytes(file));
    }

    /** Reads a turn configuration from its JSON (e.g. a release file) and checks it. */
    public static Prompt1TurnConfig parse(byte[] json) throws IOException {
        return validate(MAPPER.readValue(json, Prompt1TurnConfig.class));
    }

    /** Every file a turn returns has a header, and every turn has its SM sections. */
    static Prompt1TurnConfig validate(Prompt1TurnConfig config) {
        for (Map.Entry<String, Turn> turn : config.turns().entrySet()) {
            if (!config.smSections().containsKey(turn.getKey())) {
                throw new IllegalArgumentException("Turn " + turn.getKey() + " has no smSections entry");
            }
            for (String file : turn.getValue().files()) {
                if (file.endsWith(".tsv") && !config.headers().containsKey(file)) {
                    throw new IllegalArgumentException("Turn " + turn.getKey() + " returns " + file + " without a header");
                }
            }
        }
        for (String file : config.cFiles()) {
            if (!config.headers().containsKey(file)) {
                throw new IllegalArgumentException("C turns return " + file + " without a header");
            }
        }
        if (!config.smSections().containsKey("C")) {
            throw new IllegalArgumentException("smSections has no entry for the C turns");
        }
        return config;
    }

    /** The turn key used for the tables: every C_&lt;heta&gt; turn is "C". */
    public static String tableKey(String turn) {
        return turn.startsWith("C_") ? "C" : turn;
    }

    public String commonText() {
        return String.join("\n", common);
    }

    public String cInstructionText() {
        return String.join("\n", cInstruction);
    }

    /** A per-turn text with its {name} values filled in. */
    public String text(String name, Map<String, String> values) {
        String text = texts.get(name);
        if (text == null) {
            throw new IllegalArgumentException("No turn text '" + name + "' in the turn configuration");
        }
        for (Map.Entry<String, String> value : values.entrySet()) {
            text = text.replace("{" + value.getKey() + "}", value.getValue());
        }
        return text;
    }

    public String text(String name) {
        return text(name, Map.of());
    }

    public List<String> headerColumns(String file) {
        String header = headers.get(file);
        if (header == null) {
            throw new IllegalArgumentException("No doc 18 header for " + file);
        }
        return List.of(header.split("\t", -1));
    }
}

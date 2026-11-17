package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Jan's release files for the tests. They are confidential, so they are not in the repository: the tests read the
 * turn configuration from PROMPT1_TURNS_CONFIG (a local copy of e.g. turns-v0139.json) and the other release files
 * from KNOWLEDGE_MARKDOWN_DIR, and are skipped when these are not set, like the replay tests.
 */
final class Prompt1TestFiles {

    /** The files of the current release (P1-v0.139, backend v07). */
    static final String SYSTEM_MESSAGE = "DAISY_Prompt_1_v0.139_compact.md";
    static final String DOC18 = "18_werkbestanden_format.md";
    static final String SCHEMA = "Structured_JSON_Prompt_1_production_v1.json";
    static final String B2_MATRIX = "07_B2_matrix.tsv";

    private Prompt1TestFiles() {
    }

    /** A folder named by an environment variable; the test is skipped when it is not set. */
    static Path dir(String variable) {
        String value = System.getenv(variable);
        Assumptions.assumeTrue(value != null && Files.isDirectory(Path.of(value)), variable + " not set");
        return Path.of(value);
    }

    static Path turnConfigFile() {
        String path = System.getenv("PROMPT1_TURNS_CONFIG");
        Assumptions.assumeTrue(path != null && !path.isBlank() && Files.isRegularFile(Path.of(path)),
                "PROMPT1_TURNS_CONFIG not set: Jan's turn configuration is not in the repository");
        return Path.of(path);
    }

    static Prompt1TurnConfig turnConfig() throws IOException {
        return Prompt1TurnConfig.load(turnConfigFile().toString());
    }

    /** The current release from the local files, as a run uses it (document versions as given). */
    static Prompt1Release release(Path sources, Map<String, String> documentVersions) throws IOException {
        return Prompt1Release.of(null, "P1-v0.139", Files.readAllBytes(turnConfigFile()),
                Files.readString(sources.resolve(SYSTEM_MESSAGE)), SYSTEM_MESSAGE,
                Files.readString(sources.resolve(DOC18)), Files.readString(sources.resolve(SCHEMA)),
                Files.readString(sources.resolve(B2_MATRIX)), documentVersions);
    }

    /** The JSON Schema of the production output.json. */
    static JsonNode productionSchema(Path sources) throws IOException {
        return Prompt1ProductionJson.schema(Files.readString(sources.resolve(SCHEMA)));
    }
}

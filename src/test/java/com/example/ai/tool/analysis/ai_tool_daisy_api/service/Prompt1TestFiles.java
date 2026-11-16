package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Jan's turn configuration for the tests. It holds his turn texts, so it is not in the repository: the
 * tests read it from PROMPT1_TURNS_CONFIG (the path of a local copy of e.g. turns-v0136.json) and
 * are skipped when it is not set, like the replay tests.
 */
final class Prompt1TestFiles {

    private Prompt1TestFiles() {
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
}

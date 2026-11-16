package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class Prompt1TurnSystemMessageTest {

    private static final String SM_FILE = "DAISY_Prompt_1_v0.136_compact.md";
    private static final String DOC18_FILE = "18_werkbestanden_format.md";

    @Test
    void turnConfigurationLoadsFromTheClasspath() throws Exception {
        Prompt1TurnConfig config = Prompt1TestFiles.turnConfig();
        assertEquals(19, config.ies().size());
        assertEquals(List.of(), config.turns().get("B").files(), "turn B has no model call (v04z)");
        assertEquals(List.of("b2_needs.tsv", "b2_coverage.tsv"), config.turns().get("D").files());
        assertEquals(19, config.b2Order().size());
        assertEquals(config.b2Order().size(), config.qScope().size());
        assertEquals(List.of("heta_ref", "canonical_id", "route", "state", "meaning_ids", "link_ids", "gap", "basis"),
                config.headerColumns("heta_cells.tsv"));
        assertTrue(config.commonText().endsWith("No other text.\n"));
        assertTrue(config.cInstructionText().endsWith("RUN DATA\n"));
        assertEquals("C", Prompt1TurnConfig.tableKey("C_22.27"));
    }

    @Test
    void section0LinesThatNoLongerMatchStopTheCut() throws Exception {
        Prompt1TurnConfig config = Prompt1TestFiles.turnConfig();
        String sm = "HEADER\n====\n0. GLOBAL\n====\n- something else\n====\n1. CLIENT\n====\ntext\n";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Prompt1TurnSystemMessage.forTurn(sm, "A1", config));
        assertTrue(e.getMessage().contains("found 0 times"));
    }

    /**
     * Our SM cut per turn equals the .sm.txt files Jan's backend sent in his run (pg_run31), and every
     * turn prompt ends with our doc 18 part. Runs when KNOWLEDGE_MARKDOWN_DIR (Jan's files) and
     * PROMPT1_REPLAY_DIR (pg_run31) are set; skipped otherwise.
     */
    @Test
    void cutMatchesJansRun() throws Exception {
        Path sources = dir("KNOWLEDGE_MARKDOWN_DIR");
        Path replay = dir("PROMPT1_REPLAY_DIR");
        Prompt1TurnConfig config = Prompt1TestFiles.turnConfig();
        String sm = Files.readString(sources.resolve(SM_FILE));
        String doc18 = Files.readString(sources.resolve(DOC18_FILE));

        for (String turn : List.of("A1", "A2", "A3", "C_22.27", "C_22.65")) {
            String expected = Files.readString(replay.resolve("prompts/" + turn + ".sm.txt"));
            String actual = Prompt1TurnSystemMessage.forTurn(sm, turn, config);
            assertEquals(expected, actual, "SM cut of turn " + turn);
            System.out.printf("SM cut %s: %,d characters (full SM %,d)%n", turn, actual.length(), sm.length());
        }
        for (String turn : List.of("A1", "A2", "A3")) {
            String prompt = Files.readString(replay.resolve("prompts/" + turn + ".txt"));
            String part = String.join("\n", Prompt1TurnSystemMessage.doc18Part(doc18, turn, config));
            assertTrue(prompt.endsWith(part + "\n"), "doc 18 part of turn " + turn);
        }
    }

    private static Path dir(String variable) {
        String value = System.getenv(variable);
        assumeTrue(value != null && Files.isDirectory(Path.of(value)), variable + " not set");
        return Path.of(value);
    }
}

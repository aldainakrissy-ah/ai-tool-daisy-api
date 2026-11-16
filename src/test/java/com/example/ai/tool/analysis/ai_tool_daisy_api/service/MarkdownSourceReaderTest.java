package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.service.KnowledgeSectionSplitter.SourceChunk;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.KnowledgeSectionSplitter.SplitResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MarkdownSourceReaderTest {

    private final KnowledgeSectionSplitter splitter = new KnowledgeSectionSplitter();

    @Test
    void buildsHeadingPathsFromHeadingLevels() {
        List<SourceChunk> chunks = MarkdownSourceReader.chunksByHeading(List.of(
                "Title text",
                "# B. Core Concepts",
                "## 112.  Integrative Endoteleons",
                "### Introduction",
                "Intro text",
                "## 113. Symbolic Symbion",
                "Symbion text"));

        assertEquals(List.of("", "B. Core Concepts", "B. Core Concepts > 112. Integrative Endoteleons",
                        "B. Core Concepts > 112. Integrative Endoteleons > Introduction",
                        "B. Core Concepts > 113. Symbolic Symbion"),
                chunks.stream().map(chunk -> chunk.headingPath()).toList());
        assertEquals("Intro text", chunks.get(3).body());
    }

    /**
     * Runs against the original reference documents when KNOWLEDGE_MARKDOWN_DIR points to them;
     * skipped otherwise.
     */
    @Test
    void realDirectoryContainsEveryExpectedKey() throws Exception {
        String dir = System.getenv("KNOWLEDGE_MARKDOWN_DIR");
        assumeTrue(dir != null && Files.isDirectory(Path.of(dir)), "KNOWLEDGE_MARKDOWN_DIR not set");

        List<DoclingChunkReader.SourceDocument> documents = new MarkdownSourceReader().read(Path.of(dir));
        // the current release (v0.136); older versions are in the archive since 12 Nov
        assertEquals(List.of("01 v4", "02 v04", "03 v05", "05 v6", "06 v12", "07 v9", "R3 v06", "R4 v5", "R5 v2"),
                documents.stream().map(d -> d.documentCode() + " " + d.version()).toList());

        int longestKey = 0;
        for (DoclingChunkReader.SourceDocument document : documents) {
            SplitResult result = splitter.split(document.documentCode(), document.chunks());
            Set<String> keys = new HashSet<>();
            result.sections().forEach(s -> keys.add(s.key()));
            for (String key : keys) {
                longestKey = Math.max(longestKey, key.length());
            }
            List<String> missing = KnowledgeLoaderService.missingExpectedKeys(
                    document.documentCode(), document.version(), keys);

            System.out.printf("%s %s: %d sections, %d duplicate key(s) %s, missing %s%n",
                    document.documentCode(), document.version(), result.sections().size(),
                    result.duplicateKeys().size(), result.duplicateKeys(), missing);
            assertTrue(missing.isEmpty(), document.documentCode() + " " + document.version() + " is missing " + missing);
            assertTrue(result.duplicateKeys().isEmpty() || !Set.of("R3", "R4", "R5", "05", "06").contains(document.documentCode()),
                    document.documentCode() + " has duplicate keys " + result.duplicateKeys());

            String label = document.documentCode() + " " + document.version();
            switch (label) {
                case "R3 v3" -> assertTrue(keys.contains("R3:HETA lookup"));
                case "R3 v06" -> {
                    assertTrue(keys.containsAll(List.of("R3:22.27.4:22.7", "R3:22.27.4:SIG", "R3:22.64.4:22.7")));
                    assertFalse(keys.contains("R3:22.64.4:SIG"));
                    String opening = result.sections().stream().filter(s -> s.key().equals("R3:22.27.4"))
                            .findFirst().orElseThrow().content();
                    assertFalse(opening.contains("KEY:"), "R3:22.27.4 holds only the opening of .4");
                }
                case "R4 v5" -> assertTrue(keys.contains("R4:Lookup — 19 own IE entries"));
                case "R5 v1" -> assertTrue(keys.containsAll(List.of("R5:PREAMBLE", "R5:Use",
                        "R5:How to read the element rows (KEY: R5:ELEM)", "R5:How to read the A2 rows (KEY: R5:A2)")));
                case "02 v04" -> {
                    String b112 = result.sections().stream().filter(s -> s.key().equals("02:B.112"))
                            .findFirst().orElseThrow().content();
                    assertTrue(b112.contains("Introduction") && !b112.contains("KEY:"),
                            "02:B.112 should hold its subsections without KEY lines");
                    assertTrue(keys.contains("02:C. Derived Concepts > 113. Symbolic Symbion"),
                            "heading sections outside keyed passages should remain");
                }
                default -> {
                }
            }
        }
        System.out.println("longest key: " + longestKey);
        assertTrue(longestKey <= 512, "a section key exceeds the 512-character column");
    }
}

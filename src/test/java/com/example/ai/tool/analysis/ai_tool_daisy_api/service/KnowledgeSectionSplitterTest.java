package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.service.KnowledgeSectionSplitter.ParsedSection;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.KnowledgeSectionSplitter.SourceChunk;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.KnowledgeSectionSplitter.SplitResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class KnowledgeSectionSplitterTest {

    private final KnowledgeSectionSplitter splitter = new KnowledgeSectionSplitter();

    @Test
    void splitsKeyedDocumentAtKeyLinesAcrossChunks() {
        List<SourceChunk> chunks = List.of(
                new SourceChunk(0, "R4", "Technical extract header"),
                new SourceChunk(1, "R4 > 22.11", "KEY: R4:22.11:01 | IE 22.11 Attention Field | Functional Role\n"
                        + "The Attention Field governs the orientation of focus."),
                new SourceChunk(2, "R4 > 22.11", "It integrates sensory processing.\n"
                        + "KEY: R4:22.11:02 | IE 22.11 Attention Field | Multilevel Scope\nScope text."));

        SplitResult result = splitter.split("R4", chunks);

        assertEquals(List.of("R4:PREAMBLE", "R4:22.11:01", "R4:22.11:02"),
                result.sections().stream().map(section -> section.key()).toList());
        ParsedSection role = result.sections().get(1);
        assertEquals("IE 22.11 Attention Field | Functional Role", role.heading());
        assertEquals("The Attention Field governs the orientation of focus.\nIt integrates sensory processing.",
                role.content());
        assertEquals(2, role.seq());
        assertTrue(result.duplicateKeys().isEmpty());
    }

    @Test
    void doesNotTreatAKeyMentionInsideALineAsANewSection() {
        List<SourceChunk> chunks = List.of(new SourceChunk(0, "R3",
                "Each own entry starts with KEY: R3:<ref> and ends at the next entry.\n"
                        + "KEY: R3:22.62.2 | HETA 22.62 BDNF | Functional Role\nBDNF supports plasticity."));

        SplitResult result = splitter.split("R3", chunks);

        assertEquals(List.of("R3:PREAMBLE", "R3:22.62.2"),
                result.sections().stream().map(section -> section.key()).toList());
        assertEquals("BDNF supports plasticity.", result.sections().get(1).content());
    }

    @Test
    void numbersRepeatedKeysAndReportsThem() {
        List<SourceChunk> chunks = List.of(new SourceChunk(0, "R4",
                "KEY: R4:22.2:01 | IE 22.2 | Functional Role\nFirst\nKEY: R4:22.2:01 | IE 22.2 | Functional Role\nSecond"));

        SplitResult result = splitter.split("R4", chunks);

        assertEquals(List.of("R4:22.2:01", "R4:22.2:01#2"),
                result.sections().stream().map(section -> section.key()).toList());
        assertEquals(List.of("R4:22.2:01"), result.duplicateKeys());
    }

    @Test
    void splitsHetaModulesAndQuestionnaireSections() {
        SplitResult modules = splitter.split("05", List.of(new SourceChunk(0, "",
                "**Modulaire Daisy-vragensets per HETA**\nDoel: keuzelijst\n"
                        + "**HETA: ACTH, HETA-JSON identifier= HETA-1-ACT**\nModule ACTH\n"
                        + "**HETA: Noradrenaline, HETA-JSON identifier= HETA-15-NOR**\nModule NOR\n"
                        + "**HETA: CRH, HETA-JSON identifier = HETA-6-CRH**\nModule CRH\n"
                        + "**HETA: BDNF (Brain-Derived Neurotrophic Factor). HETA-JSON identifier =**\n"
                        + "HETA-42-BDNF\nModule BDNF")));
        assertEquals(List.of("05:PREAMBLE", "05:HETA-1-ACT", "05:HETA-15-NOR", "05:HETA-6-CRH", "05:HETA-42-BDNF"),
                modules.sections().stream().map(section -> section.key()).toList());
        assertEquals("Module ACTH", modules.sections().get(1).content());

        SplitResult questionnaires = splitter.split("07", List.of(new SourceChunk(0, "",
                "Intro\n**SECTION B1 — Ethosvarianten**\nB1 rows\n**SECTION B2 — Aanvullende vragenlijsten**\nB2 rows")));
        assertEquals(List.of("07:PREAMBLE", "07:SECTION B1", "07:SECTION B2"),
                questionnaires.sections().stream().map(section -> section.key()).toList());
        assertEquals("SECTION B1 — Ethosvarianten", questionnaires.sections().get(1).heading());
    }

    @Test
    void keyedReferenceDocumentDropsKeyedHeadingsAndKeepsNamedSectionsSeparate() {
        SplitResult result = splitter.split("R5", List.of(new SourceChunk(0, "", String.join("\n",
                "# R5 — IE Elements",
                "Canonical source: R4.",
                "## Use",
                "Step 4 reads R5:ELEM.",
                "## 22.7. Activity Field",
                "",
                "KEY: R5:ELEM:22.7 | IE 22.7 Activity Field | Elements",
                "- 22.7-A1 | element",
                "### Note inside the entry",
                "Kept in content.",
                "## 22.7. Activity Field A2",
                "KEY: R5:A2:22.7 | IE 22.7 Activity Field | A2",
                "- FIELD_22.7_1",
                "END: R5",
                ""))));

        assertEquals(List.of("R5:PREAMBLE", "R5:Use", "R5:ELEM:22.7", "R5:A2:22.7"),
                result.sections().stream().map(section -> section.key()).toList());
        assertEquals("# R5 — IE Elements\nCanonical source: R4.", result.sections().get(0).content());
        assertEquals("- 22.7-A1 | element\n### Note inside the entry\nKept in content.",
                result.sections().get(2).content());
        assertEquals("- FIELD_22.7_1", result.sections().get(3).content());
    }

    @Test
    void endMarkerClosesTheEntryAndStartsATrailer() {
        SplitResult result = splitter.split("R3", List.of(new SourceChunk(0, "", String.join("\n",
                "KEY: R3:22.66.8 | HETA 22.66 | Boundary Note",
                "Last HETA text.",
                "",
                "END: R3:HETA_ENTRIES",
                "",
                "## 22.21 The Core Integrative Endoteleons",
                "KEY: R3:22.21 | Chapter 22.21 | The Core Integrative Endoteleons",
                "Chapter text."))));

        assertEquals(List.of("R3:22.66.8", "R3:22.21"),
                result.sections().stream().map(section -> section.key()).toList());
        assertEquals("Last HETA text.", result.sections().get(0).content());
    }

    @Test
    void usesKeyLinesInModuleDocumentWhenPresent() {
        SplitResult result = splitter.split("05", List.of(new SourceChunk(0, "", String.join("\n",
                "**Modulaire Daisy-vragensets per HETA**",
                "KEY: 05:HETA-1-ACT | HETA ACTH | module",
                "**HETA: ACTH, HETA-JSON identifier= HETA-1-ACT**",
                "Module ACTH",
                "KEY: 05:END | end of modules",
                "[VERIFY: ...]"))));

        assertEquals(List.of("05:PREAMBLE", "05:HETA-1-ACT", "05:TRAILER"),
                result.sections().stream().map(section -> section.key()).toList());
        assertEquals("**HETA: ACTH, HETA-JSON identifier= HETA-1-ACT**\nModule ACTH",
                result.sections().get(1).content());
    }

    @Test
    void acceptsLegacyModuleLineWithIdentifierAfterTheBold() {
        SplitResult result = splitter.split("05", List.of(new SourceChunk(0, "", String.join("\n",
                "**HETA: Prostaglandins, HETA-JSON identifier =** HETA-41-PROS",
                "Module PROS",
                "**HETA: Nitric Oxide (NO), HETA-JSON identifier = HETA-43-NO**",
                "Module NO"))));

        assertEquals(List.of("05:HETA-41-PROS", "05:HETA-43-NO"),
                result.sections().stream().map(section -> section.key()).toList());
        assertEquals("HETA: Prostaglandins, HETA-JSON identifier = HETA-41-PROS", result.sections().get(0).heading());
    }

    @Test
    void keyedConceptDocumentTakesKeyedSpansWithSubheadingsAndKeepsHeadingSectionsAround() {
        SplitResult result = splitter.split("02", List.of(new SourceChunk(0, "", String.join("\n",
                "# B. Core Concepts",
                "Intro of B.",
                "## 112.  Integrative Endoteleons",
                "",
                "KEY: 02:B.112 | B. Core Concepts 112. Integrative Endoteleons",
                "",
                "### Introduction",
                "IE intro.",
                "### Definition",
                "IE definition.",
                "",
                "KEY: 02:END",
                "",
                "## 113. Symbolic Symbion",
                "Symbion text."))));

        assertEquals(List.of("02:B. Core Concepts", "02:B. Core Concepts > 112. Integrative Endoteleons",
                        "02:B.112", "02:B. Core Concepts > 113. Symbolic Symbion"),
                result.sections().stream().map(section -> section.key()).toList());
        ParsedSection keyed = result.sections().get(2);
        assertEquals("B. Core Concepts 112. Integrative Endoteleons", keyed.heading());
        assertEquals("### Introduction\nIE intro.\n### Definition\nIE definition.", keyed.content());
        assertEquals("Symbion text.", result.sections().get(3).content());
        assertTrue(result.duplicateKeys().isEmpty());
    }

    @Test
    void groupsOtherDocumentsByHeadingPath() {
        List<SourceChunk> chunks = List.of(
                new SourceChunk(0, "1. Protocols > 1.2 TCG", "Part one"),
                new SourceChunk(1, "1. Protocols > 1.2 TCG", "Part two"),
                new SourceChunk(2, "1. Protocols > 1.3 LST", "LST text"));

        SplitResult result = splitter.split("01", chunks);

        assertEquals(List.of("01:1. Protocols > 1.2 TCG", "01:1. Protocols > 1.3 LST"),
                result.sections().stream().map(section -> section.key()).toList());
        assertEquals("1.2 TCG", result.sections().get(0).heading());
        assertEquals("Part one\nPart two", result.sections().get(0).content());
    }

    /**
     * Runs against a real Docling build when KNOWLEDGE_CHUNKS_FILE points to one; skipped otherwise.
     */
    @Test
    void realBuildContainsEveryExpectedKey() throws Exception {
        String file = System.getenv("KNOWLEDGE_CHUNKS_FILE");
        assumeTrue(file != null && Files.isRegularFile(Path.of(file)), "KNOWLEDGE_CHUNKS_FILE not set");

        DoclingChunkReader reader = new DoclingChunkReader(new ObjectMapper());
        for (DoclingChunkReader.SourceDocument document : reader.read(Path.of(file))) {
            SplitResult result = splitter.split(document.documentCode(), document.chunks());
            Set<String> keys = new HashSet<>();
            result.sections().forEach(s -> keys.add(s.key()));
            List<String> missing = KnowledgeLoaderService.missingExpectedKeys(
                    document.documentCode(), document.version(), keys);

            System.out.printf("%s %s: %d sections, %d duplicate key(s), missing %s%n",
                    document.documentCode(), document.version(), result.sections().size(),
                    result.duplicateKeys().size(), missing);
            assertTrue(missing.isEmpty(), document.documentCode() + " is missing " + missing);
            if ("05".equals(document.documentCode())) {
                long modules = keys.stream().filter(k -> k.startsWith("05:HETA-")).count();
                assertEquals(43, modules, "document 05 should hold one module per HETA");
            }
        }
    }
}

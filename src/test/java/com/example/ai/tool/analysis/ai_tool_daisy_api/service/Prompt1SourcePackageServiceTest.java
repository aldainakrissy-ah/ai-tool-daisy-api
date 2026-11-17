package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.service.KnowledgeSectionSplitter.ParsedSection;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1SourcePackageService.DocumentSections;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1SourcePackageService.KeyText;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Reading source keys from the knowledge snapshot, as the turn-based route does. */
class Prompt1SourcePackageServiceTest {

    @Test
    void pinnedVersionWinsOverActiveAndMissingPinFails() {
        DocumentSections v5 = new DocumentSections("05", "v5", "05_v5.md", false, List.of());
        DocumentSections v6 = new DocumentSections("05", "v6", "05_v6.md", true, List.of());

        assertEquals("v6", Prompt1SourcePackageService.selectVersions(List.of(v5, v6), Map.of()).get("05").version());
        assertEquals("v5", Prompt1SourcePackageService.selectVersions(List.of(v5, v6),
                Prompt1SourcePackageService.parseVersionProfile("05:V5")).get("05").version());
        assertThrows(IllegalArgumentException.class, () -> Prompt1SourcePackageService.selectVersions(
                List.of(v5, v6), Map.of("05", "v4")));
    }

    @Test
    void aKeyIsReadWithItsKeyLineAndAMissingKeyIsNotFound() {
        DocumentSections r4 = new DocumentSections("R4", "v5", "R4.md", true,
                List.of(new ParsedSection("R4:22.2:01", "IE 22.2 Functional Role", 1, "text")));
        List<KeyText> keys = Prompt1SourcePackageService.readKeys(Map.of("R4", r4),
                List.of("R4:22.2:01", "R4:22.3:01", "R9:x"));

        assertEquals("KEY: R4:22.2:01 | IE 22.2 Functional Role\ntext", keys.get(0).text());
        assertEquals("text", keys.get(0).content());
        assertFalse(keys.get(1).found());
        assertEquals("R4.md", keys.get(1).fileName(), "a missing key still names the document read");
        assertFalse(keys.get(2).found());
        assertNull(keys.get(2).fileName(), "an unknown document");
    }

    /** The keys the turn route reads exist in the current reference documents (KNOWLEDGE_MARKDOWN_DIR). */
    @Test
    void realDocumentsHoldTheKeysTheTurnRouteReads() throws Exception {
        String dir = System.getenv("KNOWLEDGE_MARKDOWN_DIR");
        assumeTrue(dir != null && Files.isDirectory(Path.of(dir)), "KNOWLEDGE_MARKDOWN_DIR not set");
        Map<String, DocumentSections> latest =
                Prompt1SourcePackageService.selectVersions(readLikeTheLoader(Path.of(dir)), Map.of());
        List<String> keys = new ArrayList<>(Prompt1TurnMessages.A1_STEP1_KEYS);
        keys.addAll(Prompt1TurnMessages.A1_STEP2_KEYS);
        keys.addAll(List.of("R4:INDEX:22.7", "R4:22.7:01", "R5:ELEM:22.7", "R5:A2:22.7", "R3:22.27.2", "R3:22.27.4:22.7",
                "R3:22.27.4:SIG", "R3:22.27.5.2", "06:22.27", "05:HETA-26-DOP", "07:B1", "07:B2"));
        List<String> missing = Prompt1SourcePackageService.readKeys(latest, keys).stream()
                .filter(key -> !key.found()).map(key -> key.key()).toList();
        assertEquals(List.of(), missing);
        assertTrue(Prompt1SourcePackageService.r4Lookup(latest).found(), "the R4 lookup table (Step 4)");
        assertEquals("v9", latest.get("07").version());
    }

    /** Reads and splits every file, marking the highest version of each document active. */
    static List<DocumentSections> readLikeTheLoader(Path dir) throws Exception {
        KnowledgeSectionSplitter splitter = new KnowledgeSectionSplitter();
        List<DoclingChunkReader.SourceDocument> sources = new MarkdownSourceReader().read(dir);
        Map<String, String> highest = new HashMap<>();
        for (DoclingChunkReader.SourceDocument source : sources) {
            highest.merge(source.documentCode(), source.version(), (a, b) ->
                    KnowledgeLoaderService.versionNumber(a) >= KnowledgeLoaderService.versionNumber(b) ? a : b);
        }
        List<DocumentSections> documents = new ArrayList<>();
        for (DoclingChunkReader.SourceDocument source : sources) {
            documents.add(new DocumentSections(source.documentCode(), source.version(), source.fileName(),
                    source.version().equals(highest.get(source.documentCode())),
                    splitter.split(source.documentCode(), source.chunks()).sections()));
        }
        return documents;
    }
}

package com.example.ai.tool.analysis.ai_tool_daisy_api.controller;

import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.KnowledgeDocumentDto;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.KnowledgeLoadReport;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.KnowledgeSectionDto;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.KnowledgeLoaderService;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.KnowledgeService;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1SourcePackageService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * REST controller for the Prompt 1 knowledge tables: loading the reference documents and
 * reading sections by their exact key.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai-tool-daisy/knowledge")
@AllArgsConstructor
@CrossOrigin(origins = "*")
public class KnowledgeController {

    private final KnowledgeLoaderService knowledgeLoaderService;
    private final KnowledgeService knowledgeService;
    private final Prompt1SourcePackageService prompt1SourcePackageService;

    /**
     * Loads the reference documents from the configured Markdown directory, or from the chunks
     * file when no directory is configured. The report's {@code sourceFile} and
     * {@code sourceBuild} show which source was used.
     *
     * @param force reload a version that is already loaded with different content
     * @return the load report, including any expected keys that are missing
     */
    @PostMapping("/load")
    public ResponseEntity<KnowledgeLoadReport> load(@RequestParam(defaultValue = "false") boolean force)
            throws IOException {
        KnowledgeLoadReport report = knowledgeLoaderService.isMarkdownSourceConfigured()
                ? knowledgeLoaderService.loadFromMarkdownDirectory(force)
                : knowledgeLoaderService.loadFromChunksFile(force);
        prompt1SourcePackageService.clearSnapshot();
        return ResponseEntity.ok(report);
    }

    /** Lists every loaded document version. */
    @GetMapping("/documents")
    public ResponseEntity<List<KnowledgeDocumentDto>> getDocuments() {
        return ResponseEntity.ok(knowledgeService.getDocuments());
    }

    /** Lists the section keys and headings of the active version of a document. */
    @GetMapping("/documents/{documentCode}/sections")
    public ResponseEntity<List<KnowledgeSectionDto>> getSectionIndex(@PathVariable String documentCode) {
        return ResponseEntity.ok(knowledgeService.getActiveSectionIndex(documentCode));
    }

    /**
     * Returns one section by its exact key from the active document version,
     * e.g. {@code ?key=R4:22.11:01} or {@code ?key=R3:22.62.2}.
     */
    @GetMapping("/sections")
    public ResponseEntity<KnowledgeSectionDto> getSection(@RequestParam String key) {
        return knowledgeService.getActiveSection(key)
                .map(section -> ResponseEntity.ok(section))
                .orElseGet(() -> {
                    log.warn("Knowledge section not found: {}", key);
                    return ResponseEntity.notFound().build();
                });
    }
}

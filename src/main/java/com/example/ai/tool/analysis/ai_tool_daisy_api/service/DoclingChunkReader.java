package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Reads a Docling chunk build (chunks.jsonl) and groups its chunks per source document.
 * <p>
 * Each chunk carries its own text without overlap in {@code body}, its position in
 * {@code meta.ordinal} and its heading path in {@code meta.section}. The overlap text in
 * {@code text} is ignored, so the bodies joined in order reproduce the document once.
 */
@Component
@RequiredArgsConstructor
public class DoclingChunkReader {

    private final ObjectMapper objectMapper;

    public record SourceDocument(String documentCode, String version, String fileName,
                                 String sourceHash, String sourceBuild,
                                 List<KnowledgeSectionSplitter.SourceChunk> chunks) {
    }

    /**
     * Reads every chunk in the file and returns one {@link SourceDocument} per document code and
     * version, in the order the documents first appear, with chunks sorted by ordinal.
     */
    public List<SourceDocument> read(Path chunksFile) throws IOException {
        Map<String, SourceDocument> documents = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(chunksFile, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                JsonNode chunk = objectMapper.readTree(line);
                JsonNode meta = chunk.path("meta");
                String code = requireText(meta, "doc_id", lineNumber);
                String version = requireText(meta, "version", lineNumber);

                SourceDocument document = documents.computeIfAbsent(code + "|" + version, k ->
                        new SourceDocument(code, version, meta.path("source").asText(""),
                                meta.path("sha256").asText(""), meta.path("build").asText(""),
                                new ArrayList<>()));
                document.chunks().add(new KnowledgeSectionSplitter.SourceChunk(
                        meta.path("ordinal").asInt(), meta.path("section").asText(""),
                        chunk.path("body").asText("")));
            }
        }
        documents.values().forEach(d ->
                d.chunks().sort((a, b) -> Integer.compare(a.ordinal(), b.ordinal())));
        return new ArrayList<>(documents.values());
    }

    private String requireText(JsonNode node, String field, int lineNumber) {
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Chunk on line " + lineNumber + " has no meta." + field);
        }
        return value;
    }
}

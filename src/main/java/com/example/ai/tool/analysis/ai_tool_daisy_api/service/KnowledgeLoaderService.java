package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.KnowledgeDocumentEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.KnowledgeSectionEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.KnowledgeLoadReport;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.KnowledgeLoadReport.DocumentResult;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.KnowledgeLoadReport.Status;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.KnowledgeDocumentRepository;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.KnowledgeSectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads the Prompt 1 reference documents into the knowledge tables, one keyed section per row.
 * <p>
 * A document version is loaded once. Reloading the same version with the same source hash is a
 * no-op; a different source hash under the same version is reported as a conflict unless
 * {@code force} is set. After a load, the highest loaded version of each document code is the
 * active one (v6 above v5, v12 above v11); older versions stay available for pinned runs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeLoaderService {

    private static final Pattern VERSION_NUMBER = Pattern.compile("(\\d+)");
    private static final int EXPECTED_HETA_COUNT = 43;
    /** HETAs that R3 v06 gives no teleonic signature ({@code .4:SIG}); its absence there is not a gap. */
    static final Set<String> R3_WITHOUT_SIGNATURE = Set.of("22.61", "22.64");

    private final DoclingChunkReader chunkReader;
    private final MarkdownSourceReader markdownReader;
    private final KnowledgeSectionSplitter splitter;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeSectionRepository sectionRepository;

    @Value("${knowledge.source.chunks-file:}")
    private String chunksFile;

    @Value("${knowledge.source.markdown-dir:}")
    private String markdownDir;

    public boolean isMarkdownSourceConfigured() {
        return markdownDir != null && !markdownDir.isBlank();
    }

    @Transactional
    public KnowledgeLoadReport loadFromChunksFile(boolean force) throws IOException {
        if (chunksFile == null || chunksFile.isBlank()) {
            throw new IllegalArgumentException("knowledge.source.chunks-file is not configured");
        }
        Path path = Path.of(chunksFile);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Knowledge chunks file not found: " + path);
        }
        return loadDocuments(path.toString(), chunkReader.read(path), force);
    }

    /** Loads every reference document (.md) in {@code knowledge.source.markdown-dir}. */
    @Transactional
    public KnowledgeLoadReport loadFromMarkdownDirectory(boolean force) throws IOException {
        if (!isMarkdownSourceConfigured()) {
            throw new IllegalArgumentException("knowledge.source.markdown-dir is not configured");
        }
        Path path = Path.of(markdownDir);
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Knowledge markdown directory not found: " + path);
        }
        return loadDocuments(path.toString(), markdownReader.read(path), force);
    }

    /**
     * Loads the reference documents of an uploaded release (read from the zip, never from disk). A version
     * already loaded with the same content is left as it is; check {@link #conflicts} first.
     */
    @Transactional
    public KnowledgeLoadReport loadReleaseDocuments(String releaseName, List<DoclingChunkReader.SourceDocument> documents) {
        return loadDocuments("release " + releaseName, documents, false);
    }

    /**
     * The documents whose code and version are already loaded with different content: loading them would be
     * refused (a changed document needs a new version).
     */
    public List<String> conflicts(List<DoclingChunkReader.SourceDocument> documents) {
        List<String> conflicts = new ArrayList<>();
        for (DoclingChunkReader.SourceDocument document : documents) {
            documentRepository.findByDocumentCodeAndVersion(document.documentCode(), document.version())
                    .filter(loaded -> !loaded.getSourceHash().equals(document.sourceHash()))
                    .ifPresent(loaded -> conflicts.add(document.documentCode() + " " + document.version()));
        }
        return conflicts;
    }

    private KnowledgeLoadReport loadDocuments(String source, List<DoclingChunkReader.SourceDocument> documents,
                                              boolean force) {
        log.info("Loading {} knowledge document(s) from {}", documents.size(), source);
        KnowledgeLoadReport report = KnowledgeLoadReport.builder()
                .sourceFile(source)
                .sourceBuild(documents.isEmpty() ? null : documents.get(0).sourceBuild())
                .build();
        Set<String> codes = new LinkedHashSet<>();
        for (DoclingChunkReader.SourceDocument document : documents) {
            report.getDocuments().add(loadDocument(document, force));
            codes.add(document.documentCode());
        }
        codes.forEach(code -> activateHighestVersion(code));

        log.info("Knowledge load finished: complete={}", report.isComplete());
        return report;
    }

    private DocumentResult loadDocument(DoclingChunkReader.SourceDocument source, boolean force) {
        KnowledgeSectionSplitter.SplitResult split = splitter.split(source.documentCode(), source.chunks());
        Set<String> keys = new HashSet<>();
        split.sections().forEach(s -> keys.add(s.key()));

        DocumentResult result = DocumentResult.builder()
                .documentCode(source.documentCode())
                .version(source.version())
                .fileName(source.fileName())
                .sectionCount(split.sections().size())
                .missingKeys(missingExpectedKeys(source.documentCode(), source.version(), keys))
                .duplicateKeys(split.duplicateKeys())
                .build();

        Optional<KnowledgeDocumentEntity> existing =
                documentRepository.findByDocumentCodeAndVersion(source.documentCode(), source.version());
        if (existing.isPresent() && !force) {
            if (existing.get().getSourceHash().equals(source.sourceHash())) {
                result.setStatus(Status.UNCHANGED);
                result.setMessage("Already loaded");
            } else {
                result.setStatus(Status.CONFLICT);
                result.setMessage("Version " + source.version() + " is already loaded with different content;"
                        + " load it under a new version or reload with force");
            }
            return result;
        }

        KnowledgeDocumentEntity document = existing.orElseGet(() -> new KnowledgeDocumentEntity());
        if (existing.isPresent()) {
            sectionRepository.deleteByDocument(document);
        }
        document.setDocumentCode(source.documentCode());
        document.setVersion(source.version());
        document.setFileName(source.fileName());
        document.setSourceHash(source.sourceHash());
        document.setSourceBuild(source.sourceBuild());
        document.setSectionCount(split.sections().size());
        document = documentRepository.save(document);

        List<KnowledgeSectionEntity> sections = new ArrayList<>(split.sections().size());
        for (KnowledgeSectionSplitter.ParsedSection section : split.sections()) {
            sections.add(KnowledgeSectionEntity.builder()
                    .document(document)
                    .sectionKey(section.key())
                    .heading(section.heading())
                    .seq(section.seq())
                    .content(section.content())
                    .contentHash(Hashes.sha256(section.content()))
                    .build());
        }
        sectionRepository.saveAll(sections);

        result.setStatus(Status.LOADED);
        log.info("Loaded {} {}: {} sections, {} missing key(s), {} duplicate key(s)",
                source.documentCode(), source.version(), sections.size(),
                result.getMissingKeys().size(), result.getDuplicateKeys().size());
        return result;
    }

    /** Makes the highest loaded version of a document code the active one. */
    private void activateHighestVersion(String documentCode) {
        List<KnowledgeDocumentEntity> versions = documentRepository.findByDocumentCode(documentCode);
        versions.stream()
                .max(Comparator.comparingInt((KnowledgeDocumentEntity d) -> versionNumber(d.getVersion()))
                        .thenComparing(d -> d.getVersion()))
                .ifPresent(highest -> {
                    for (KnowledgeDocumentEntity version : versions) {
                        boolean shouldBeActive = version.getId().equals(highest.getId());
                        if (version.isActive() != shouldBeActive) {
                            version.setActive(shouldBeActive);
                            documentRepository.save(version);
                        }
                    }
                });
    }

    /** The number in a version label ("v03" → 3, "V11" → 11); -1 when there is none. */
    static int versionNumber(String version) {
        Matcher m = VERSION_NUMBER.matcher(version == null ? "" : version);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /**
     * Keys that Prompt 1 looks up directly and that must therefore exist after loading:
     * the 19 IE Functional Roles and reverse-index blocks in R4, the 43 HETA Functional Roles
     * and the general chapters in R3, the 19 element and A2 blocks in R5 (plus R5:README from v2),
     * the 43 HETA records in keyed 06 (V12 and later), one module per HETA in 05, the three
     * questionnaire sections in 07, and the named concept passages in keyed 01–03 (01 v4, 02 v04 and
     * 03 v05 on). Keys introduced in a later version are only expected from that version.
     */
    static List<String> missingExpectedKeys(String documentCode, String version, Set<String> keys) {
        List<String> expected = new ArrayList<>();
        switch (documentCode) {
            case "R4" -> {
                for (int i = 2; i <= 20; i++) {
                    expected.add("R4:22." + i + ":01");
                    expected.add("R4:INDEX:22." + i);
                }
            }
            case "R3" -> {
                expected.addAll(List.of("R3:22.0", "R3:22.1", "R3:22.21"));
                for (int i = 24; i <= 66; i++) {
                    expected.add("R3:22." + i + ".2");
                    if (versionNumber(version) >= 6) {
                        // v06 splits .4 per IE; Step 9 of SM v0.127 reads .4, .4:SIG and .5.2 for every candidate
                        expected.add("R3:22." + i + ".4");
                        expected.add("R3:22." + i + ".5.2");
                        if (!R3_WITHOUT_SIGNATURE.contains("22." + i)) {
                            expected.add("R3:22." + i + ".4:SIG");
                        }
                    }
                }
            }
            case "R5" -> {
                if (versionNumber(version) >= 2) {
                    expected.add("R5:README");
                }
                for (int i = 2; i <= 20; i++) {
                    expected.add("R5:ELEM:22." + i);
                    expected.add("R5:A2:22." + i);
                }
            }
            case "01" -> {
                if (versionNumber(version) < 4) {
                    return List.of();
                }
                expected.addAll(List.of("01:1.2", "01:1.3", "01:2.1", "01:2.2"));
            }
            case "02" -> {
                if (versionNumber(version) < 4) {
                    return List.of();
                }
                expected.addAll(List.of("02:B.2", "02:B.4", "02:B.5", "02:B.7", "02:B.55.1", "02:B.55.2",
                        "02:B.112", "02:C.131"));
            }
            case "03" -> {
                if (versionNumber(version) < 5) {
                    return List.of();
                }
                expected.addAll(List.of("03:1.138", "03:1.185", "03:1.264", "03:1.338", "03:1.380"));
            }
            case "06" -> {
                if (versionNumber(version) < 12) {
                    return List.of();
                }
                for (int i = 24; i <= 66; i++) {
                    expected.add("06:22." + i);
                }
            }
            case "05" -> {
                long modules = keys.stream().filter(k -> k.startsWith("05:HETA-")).count();
                return modules == EXPECTED_HETA_COUNT ? List.of()
                        : List.of("05:HETA-* (" + modules + " of " + EXPECTED_HETA_COUNT + " modules found)");
            }
            case "07" -> expected.addAll(versionNumber(version) >= 8
                    ? List.of("07:B1", "07:B2", "07:B3")
                    : List.of("07:SECTION B1", "07:SECTION B2", "07:SECTION B3"));
            default -> {
                return List.of();
            }
        }
        return expected.stream().filter(k -> !keys.contains(k)).toList();
    }
}

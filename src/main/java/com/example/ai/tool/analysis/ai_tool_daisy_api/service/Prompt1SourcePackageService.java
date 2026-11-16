package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.KnowledgeDocumentEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.KnowledgeDocumentRepository;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.KnowledgeSectionRepository;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.KnowledgeSectionSplitter.ParsedSection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Reads the Prompt 1 reference passages by exact key from the knowledge tables, as Jan's server
 * does for the turn-based route: the KEY line ("KEY: &lt;key&gt; | &lt;heading&gt;") followed by the
 * section text.
 * <p>
 * Keys are read from an in-memory snapshot of every loaded document version, read once from the
 * knowledge tables and cleared after each load. By default the active (highest) version of each
 * document is used; {@code experiment.prompt1.document-versions} pins others, e.g. {@code 07:v8}.
 * A run pins the versions it started with, so a knowledge reload during the run changes nothing.
 * <p>
 * The package builders of the earlier two-call route were retired on 13 Nov 2026 (backup kept outside the repo).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Prompt1SourcePackageService {

    private static final Set<String> HEADING_BASED_CODES = Set.of("01", "02", "03");
    private static final String R4_LOOKUP_KEY = "R4:Lookup — 19 own IE entries";

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeSectionRepository sectionRepository;

    @Value("${experiment.prompt1.document-versions:}")
    private String documentVersions;

    private volatile List<DocumentSections> snapshot;

    /**
     * One loaded document version with its sections in source order, and the sections by key (the first
     * section with a key wins, as a scan in source order would find it).
     */
    public record DocumentSections(String documentCode, String version, String fileName, boolean active,
                                   List<ParsedSection> sections, Map<String, ParsedSection> byKey) {

        public DocumentSections(String documentCode, String version, String fileName, boolean active,
                                List<ParsedSection> sections) {
            this(documentCode, version, fileName, active, List.copyOf(sections), index(sections));
        }

        private static Map<String, ParsedSection> index(List<ParsedSection> sections) {
            Map<String, ParsedSection> byKey = new HashMap<>();
            sections.forEach(section -> byKey.putIfAbsent(section.key(), section));
            return Collections.unmodifiableMap(byKey);
        }
    }

    /**
     * The document versions a run reads from, selected once at its start: a run keeps reading this
     * selection even when the knowledge tables are reloaded during the run.
     */
    public Map<String, DocumentSections> documents(Map<String, String> pinnedVersions) {
        return Collections.unmodifiableMap(selectDocuments(pinnedVersions));
    }

    /**
     * One source key as the turn-based route reads it: like Jan's server, the KEY line
     * ("KEY: &lt;key&gt; | &lt;heading&gt;") followed by the section text; {@code text} is null when the key
     * does not exist in the selected document version.
     */
    public record KeyText(String key, String text, String content, String fileName, String version) {
        public boolean found() {
            return text != null;
        }
    }

    /** Reads source keys from the given pinned document versions (null = the versions selected now), in order. */
    public List<KeyText> readKeys(List<String> keys, Map<String, String> pinnedVersions) {
        return readKeys(selectDocuments(pinnedVersions), keys);
    }

    /**
     * The R4 lookup table (Step 4): its heading and the table, without a KEY line (Jan's backend reads it
     * as a section, not by key). Jan's index flattens the table into "column = value" text; we send the
     * original markdown table.
     */
    public static KeyText r4Lookup(Map<String, DocumentSections> documents) {
        KeyText key = readKeys(documents, List.of(R4_LOOKUP_KEY)).get(0);
        if (!key.found()) {
            return key;
        }
        String heading = R4_LOOKUP_KEY.substring("R4:".length());
        return new KeyText(key.key(), (heading + "\n\n" + key.content()).strip(), key.content(), key.fileName(), key.version());
    }

    /** Reads source keys from a document selection (see {@link #documents}), in order. */
    public static List<KeyText> readKeys(Map<String, DocumentSections> documents, List<String> keys) {
        List<KeyText> out = new ArrayList<>();
        for (String key : keys) {
            int colon = key.indexOf(':');
            DocumentSections document = colon < 0 ? null : documents.get(key.substring(0, colon));
            Optional<ParsedSection> section = document == null ? Optional.empty()
                    : Optional.ofNullable(document.byKey().get(key));
            if (document == null) {
                out.add(new KeyText(key, null, null, null, null));
                continue;
            }
            if (section.isEmpty()) {
                out.add(new KeyText(key, null, null, document.fileName(), document.version()));
                continue;
            }
            String content = section.get().content();
            String text = isHeadingPathKey(document.documentCode(), key)
                    ? content.strip()
                    : ("KEY: " + key + " | " + section.get().heading() + "\n" + content).strip();
            out.add(new KeyText(key, text, content, document.fileName(), document.version()));
        }
        return out;
    }

    /**
     * Sections of 01–03 and unkeyed 06 found by heading path have no KEY line; keyed sections (no
     * spaces in the key, e.g. {@code 02:B.112}, {@code 06:22.62}) are shown with their KEY line.
     */
    private static boolean isHeadingPathKey(String documentCode, String key) {
        return (HEADING_BASED_CODES.contains(documentCode) || "06".equals(documentCode))
                && key.substring(documentCode.length() + 1).contains(" ");
    }

    /** Document code → version a run started now would use; a run pins these. */
    public Map<String, String> selectedDocumentVersions() {
        Map<String, String> versions = new TreeMap<>();
        selectDocuments(null).forEach((code, document) -> versions.put(code, document.version()));
        return versions;
    }

    /** Without pins: {@code document-versions}, otherwise the active versions. With pins: exactly those. */
    private Map<String, DocumentSections> selectDocuments(Map<String, String> pinnedVersions) {
        if (pinnedVersions != null) {
            return selectVersions(getSnapshot(), pinnedVersions);
        }
        return selectVersions(getSnapshot(), parseVersionProfile(documentVersions));
    }

    /**
     * Drops the snapshot so the next read uses the knowledge tables again. It takes the same lock as
     * {@link #getSnapshot()}, so a read that started before a reload finishes first and is then dropped,
     * never cached after the reload.
     */
    public synchronized void clearSnapshot() {
        snapshot = null;
    }

    List<DocumentSections> getSnapshot() {
        List<DocumentSections> current = snapshot;
        if (current == null) {
            synchronized (this) {
                current = snapshot;
                if (current == null) {
                    current = readSnapshot();
                    snapshot = current;
                }
            }
        }
        return current;
    }

    private List<DocumentSections> readSnapshot() {
        List<DocumentSections> documents = new ArrayList<>();
        for (KnowledgeDocumentEntity document : documentRepository.findAllByOrderByDocumentCodeAscVersionAsc()) {
            List<ParsedSection> sections = sectionRepository.findByDocumentOrderBySeqAsc(document).stream()
                    .map(s -> new ParsedSection(s.getSectionKey(), s.getHeading(), s.getSeq(), s.getContent()))
                    .toList();
            documents.add(new DocumentSections(document.getDocumentCode(), document.getVersion(),
                    document.getFileName(), document.isActive(), sections));
        }
        log.info("Loaded knowledge snapshot: {} document version(s)", documents.size());
        return documents;
    }

    /** Parses {@code 05:v5,06:v11} into document code → version. */
    static Map<String, String> parseVersionProfile(String profile) {
        Map<String, String> versions = new LinkedHashMap<>();
        if (profile == null || profile.isBlank()) {
            return versions;
        }
        for (String entry : profile.split(",")) {
            String[] parts = entry.trim().split(":", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new IllegalArgumentException("Invalid document version entry '" + entry
                        + "'; expected <code>:<version>, e.g. 05:v5");
            }
            versions.put(parts[0].trim(), parts[1].trim().toLowerCase(Locale.ROOT));
        }
        return versions;
    }

    /** Picks one version per document code: the pinned one if any, otherwise the active one. */
    static Map<String, DocumentSections> selectVersions(List<DocumentSections> documents, Map<String, String> pinned) {
        Map<String, DocumentSections> selected = new TreeMap<>();
        for (DocumentSections document : documents) {
            String pin = pinned.get(document.documentCode());
            boolean wanted = pin == null ? document.active() : pin.equalsIgnoreCase(document.version());
            if (wanted) {
                selected.put(document.documentCode(), document);
            }
        }
        List<String> missingPins = pinned.entrySet().stream()
                .filter(pin -> !selected.containsKey(pin.getKey()))
                .map(pin -> pin.getKey() + " " + pin.getValue())
                .toList();
        if (!missingPins.isEmpty()) {
            throw new IllegalArgumentException("Pinned document version(s) not loaded: " + missingPins);
        }
        return selected;
    }
}

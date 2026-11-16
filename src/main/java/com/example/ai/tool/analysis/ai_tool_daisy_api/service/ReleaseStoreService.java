package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.PromptReleaseEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.PromptReleaseEntity.Status;
import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.PromptReleaseFileEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.ReleaseReport;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.ReleaseReport.Check;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.ReleaseReport.FileInfo;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.PromptReleaseFileRepository;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.PromptReleaseRepository;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.KnowledgeSectionSplitter.ParsedSection;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1SourcePackageService.DocumentSections;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1SourcePackageService.KeyText;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1Release.*;

/**
 * The release store: Jan's prompt files are uploaded as one zip per release, checked, and kept only in the
 * database (never in git, a jar or an image).
 * <p>
 * A package is a zip with {@code manifest.json} and the files it lists:
 * <pre>
 * { "prompt": "P1", "release": "P1-v0.136", "changes": "…",
 *   "files": [ { "file": "DAISY_Prompt_1_v0.136_compact.md", "role": "system_message", "version": "v0.136" },
 *              { "file": "01_BiomatrixAI_Primer_v4.md", "role": "reference" }, … ] }
 * </pre>
 * Upload checks everything in memory first; a package that fails a check is refused and nothing is stored.
 * A package that passes becomes a DRAFT (its reference documents are loaded into the knowledge tables).
 * Activating a release retires the one active before it; rolling back is activating an older one.
 * Only Prompt 1 releases for now (there is no plan yet for Prompts 2 and 3; the tables are per prompt).
 * File contents are never logged and never returned.
 */
@Slf4j
@Service
public class ReleaseStoreService {

    static final String MANIFEST = "manifest.json";
    static final int MAX_FILES = 200;
    static final long MAX_UNZIPPED_BYTES = 200L * 1024 * 1024;
    private static final Pattern PROMPT_CODE = Pattern.compile("^P\\d{1,2}$");
    private static final Pattern RELEASE_NAME = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");
    private static final Set<String> ROLES = Set.of(SYSTEM_MESSAGE, DOC18, SCHEMA, B2_MATRIX, TURN_CONFIG, REFERENCE);
    private static final ObjectMapper STRICT = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    /** The manifest of a release package. */
    record Manifest(String prompt, String release, String changes, List<ManifestFile> files) {
    }

    /** One file of the package; code and version are optional for a reference document (taken from its name). */
    record ManifestFile(String file, String role, String code, String version) {
    }

    /** The upload is not a release package at all (not a zip, no manifest, too large). */
    public static class BadPackageException extends RuntimeException {
        BadPackageException(String message) {
            super(message);
        }
    }

    /** What the Prompt 1 checks found, to store when every check passed. */
    private record Prompt1Upload(List<DoclingChunkReader.SourceDocument> documents, Map<String, String> versions) {
    }

    private final PromptReleaseRepository releaseRepository;
    private final PromptReleaseFileRepository fileRepository;
    private final KnowledgeLoaderService knowledgeLoader;
    private final MarkdownSourceReader markdownReader;
    private final KnowledgeSectionSplitter splitter;
    private final Prompt1SourcePackageService sourcePackageService;
    private final ObjectMapper objectMapper;

    public ReleaseStoreService(PromptReleaseRepository releaseRepository, PromptReleaseFileRepository fileRepository,
                               KnowledgeLoaderService knowledgeLoader, MarkdownSourceReader markdownReader,
                               KnowledgeSectionSplitter splitter, Prompt1SourcePackageService sourcePackageService,
                               ObjectMapper objectMapper) {
        this.releaseRepository = releaseRepository;
        this.fileRepository = fileRepository;
        this.knowledgeLoader = knowledgeLoader;
        this.markdownReader = markdownReader;
        this.splitter = splitter;
        this.sourcePackageService = sourcePackageService;
        this.objectMapper = objectMapper;
    }

    /**
     * Checks a release package and stores it as a DRAFT when every check passed; otherwise nothing is stored
     * and the report (status REJECTED) says what failed.
     *
     * @throws BadPackageException when the upload is not a readable package
     */
    @Transactional
    public ReleaseReport upload(byte[] zip) {
        Map<String, byte[]> entries = unzip(zip);
        byte[] manifestBytes = entries.remove(MANIFEST);
        if (manifestBytes == null) {
            throw new BadPackageException("The package has no " + MANIFEST);
        }
        Manifest manifest;
        try {
            manifest = STRICT.readValue(manifestBytes, Manifest.class);
        } catch (IOException e) {
            throw new BadPackageException(MANIFEST + " cannot be read: " + e.getMessage());
        }
        String prompt = manifest.prompt() == null ? "" : manifest.prompt().strip().toUpperCase(Locale.ROOT);
        String name = manifest.release() == null ? "" : manifest.release().strip();
        List<ManifestFile> listed = manifest.files() == null ? List.of() : manifest.files();

        List<Check> checks = new ArrayList<>();
        checks.add(new Check("manifest", PROMPT_CODE.matcher(prompt).matches() && RELEASE_NAME.matcher(name).matches(),
                "prompt " + (prompt.isEmpty() ? "missing" : prompt) + ", release " + (name.isEmpty() ? "missing" : name)
                        + " (release: letters, digits, '.', '_', '-', at most 64)"));
        Optional<PromptReleaseEntity> existing = releaseRepository.findByPromptAndName(prompt, name);
        checks.add(new Check("release name is new", existing.isEmpty(), existing.isEmpty() ? name
                : name + " was already uploaded (" + existing.get().getStatus() + "); give a new release a new name"));
        List<FileInfo> files = checkListedFiles(listed, entries, checks);

        Prompt1Upload prompt1 = null;
        if (PROMPT.equals(prompt)) {
            prompt1 = checkPrompt1(name, listed, entries, checks);
        } else {
            checks.add(new Check("supported prompt", false, "only Prompt 1 (P1) releases are stored for now"));
        }

        boolean passed = checks.stream().allMatch(check -> check.ok());
        String changes = manifest.changes();
        if (!passed) {
            log.warn("Release upload {} {} refused: {} of {} checks failed", prompt, name,
                    checks.stream().filter(check -> !check.ok()).count(), checks.size());
            return new ReleaseReport(null, prompt, name, "REJECTED", false, null, null, changes,
                    prompt1 == null ? null : prompt1.versions(), checks, files);
        }

        PromptReleaseEntity release = PromptReleaseEntity.builder()
                .id(UUID.randomUUID().toString())
                .prompt(prompt)
                .name(name)
                .status(Status.DRAFT)
                .manifest(new String(manifestBytes, StandardCharsets.UTF_8))
                .documentVersions(prompt1 == null ? null : toJson(prompt1.versions()))
                .checkReport(toJson(checks))
                .packageHash(Hashes.sha256(zip))
                .createdAt(LocalDateTime.now())
                .build();
        releaseRepository.save(release);
        for (ManifestFile file : listed) {
            byte[] bytes = entries.get(file.file());
            boolean reference = REFERENCE.equals(file.role());
            FileInfo info = files.stream().filter(candidate -> candidate.file().equals(file.file())).findFirst().orElseThrow();
            fileRepository.save(PromptReleaseFileEntity.builder()
                    .releaseId(release.getId())
                    .role(file.role())
                    .fileName(file.file())
                    .documentCode(info.documentCode())
                    .version(info.version())
                    .sha256(info.sha256())
                    .sizeBytes(bytes.length)
                    .content(reference ? null : new String(bytes, StandardCharsets.UTF_8))
                    .build());
        }
        if (prompt1 != null) {
            knowledgeLoader.loadReleaseDocuments(name, prompt1.documents());
            sourcePackageService.clearSnapshot();
        }
        log.info("Release {} {} stored as DRAFT: {} file(s)", prompt, name, listed.size());
        return report(release, true);
    }

    /** Every listed file is in the zip, once, with a known role; every file in the zip is listed. */
    private List<FileInfo> checkListedFiles(List<ManifestFile> listed, Map<String, byte[]> entries, List<Check> checks) {
        List<String> problems = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<FileInfo> files = new ArrayList<>();
        for (ManifestFile file : listed) {
            if (file == null || !notBlank(file.file())) {
                problems.add("a file entry without a file name");
                continue;
            }
            if (!seen.add(file.file())) {
                problems.add(file.file() + " is listed twice");
            }
            if (!ROLES.contains(file.role())) {
                problems.add(file.file() + ": unknown role '" + file.role() + "' (expected one of " + new TreeSet<>(ROLES) + ")");
            }
            byte[] bytes = entries.get(file.file());
            if (bytes == null) {
                problems.add(file.file() + " is listed but not in the zip");
                continue;
            }
            String code = file.code();
            String version = file.version();
            if (REFERENCE.equals(file.role()) && MarkdownSourceReader.isReferenceDocument(file.file())) {
                DoclingChunkReader.SourceDocument document = markdownReader.read(file.file(), bytes);
                if (code != null && !code.equals(document.documentCode())
                        || version != null && !version.equalsIgnoreCase(document.version())) {
                    problems.add(file.file() + ": manifest says " + code + " " + version + ", the file name says "
                            + document.documentCode() + " " + document.version());
                }
                code = document.documentCode();
                version = document.version();
            }
            files.add(new FileInfo(file.file(), file.role(), code, version, Hashes.sha256(bytes), bytes.length));
        }
        List<String> unlisted = entries.keySet().stream().filter(file -> !seen.contains(file)).sorted().toList();
        if (!unlisted.isEmpty()) {
            problems.add("in the zip but not in the manifest: " + unlisted);
        }
        checks.add(new Check("files match the manifest", problems.isEmpty(),
                problems.isEmpty() ? listed.size() + " files" : String.join("; ", problems)));
        return files;
    }

    /**
     * The Prompt 1 checks: one file per role and one reference document per code the route reads; the files
     * can be read; the system message can be cut for every turn; doc 18 has every heading; every reference
     * document has the keys Prompt 1 looks up; and no document version is already loaded with other content.
     */
    private Prompt1Upload checkPrompt1(String name, List<ManifestFile> listed, Map<String, byte[]> entries,
                                       List<Check> checks) {
        Map<String, ManifestFile> byRole = new HashMap<>();
        List<String> problems = new ArrayList<>();
        List<DoclingChunkReader.SourceDocument> documents = new ArrayList<>();
        for (ManifestFile file : listed) {
            if (file == null || file.file() == null || !entries.containsKey(file.file())) {
                continue;
            }
            if (REFERENCE.equals(file.role())) {
                if (!MarkdownSourceReader.isReferenceDocument(file.file())) {
                    problems.add(file.file() + " is not named like a reference document (e.g. 01_…_v4.md)");
                } else {
                    documents.add(markdownReader.read(file.file(), entries.get(file.file())));
                }
            } else if (byRole.put(file.role(), file) != null) {
                problems.add("more than one " + file.role() + " file");
            }
        }
        FILE_ROLES.stream().filter(role -> !byRole.containsKey(role)).forEach(role -> problems.add("no " + role + " file"));
        Map<String, String> versions = new TreeMap<>();
        for (DoclingChunkReader.SourceDocument document : documents) {
            if (versions.put(document.documentCode(), document.version()) != null) {
                problems.add("more than one version of document " + document.documentCode());
            }
        }
        REFERENCE_CODES.stream().filter(code -> !versions.containsKey(code))
                .forEach(code -> problems.add("no reference document " + code));
        checks.add(new Check("Prompt 1 files", problems.isEmpty(), problems.isEmpty()
                ? FILE_ROLES.size() + " prompt files, reference documents " + versions : String.join("; ", problems)));
        if (!problems.isEmpty()) {
            return new Prompt1Upload(documents, versions);
        }

        Prompt1Release release;
        try {
            release = Prompt1Release.of(null, name, entries.get(byRole.get(TURN_CONFIG).file()),
                    text(entries, byRole.get(SYSTEM_MESSAGE)), byRole.get(SYSTEM_MESSAGE).file(),
                    text(entries, byRole.get(DOC18)), text(entries, byRole.get(SCHEMA)),
                    text(entries, byRole.get(B2_MATRIX)), versions);
            checks.add(new Check("turn configuration, schema and B2 matrix can be read", true,
                    "turn configuration " + release.config().release() + ", B2 matrix " + release.b2Matrix().size() + " rows"));
            checks.addAll(Prompt1ReleaseService.checkTexts(release));
        } catch (IllegalArgumentException e) {
            checks.add(new Check("turn configuration, schema and B2 matrix can be read", false, e.getMessage()));
        }

        Map<String, DocumentSections> sections = new TreeMap<>();
        List<String> missingKeys = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();
        for (DoclingChunkReader.SourceDocument document : documents) {
            KnowledgeSectionSplitter.SplitResult split = splitter.split(document.documentCode(), document.chunks());
            Set<String> keys = new HashSet<>();
            split.sections().forEach(section -> keys.add(section.key()));
            KnowledgeLoaderService.missingExpectedKeys(document.documentCode(), document.version(), keys)
                    .forEach(key -> missingKeys.add(key));
            if (!split.duplicateKeys().isEmpty()) {
                duplicates.add(document.documentCode() + " " + split.duplicateKeys().size());
            }
            List<ParsedSection> parsed = split.sections();
            sections.put(document.documentCode(), new DocumentSections(document.documentCode(), document.version(),
                    document.fileName(), true, parsed));
        }
        checks.add(new Check("reference documents have the keys Prompt 1 looks up", missingKeys.isEmpty(),
                missingKeys.isEmpty() ? "all expected keys found" + (duplicates.isEmpty() ? ""
                        : "; duplicate keys (first one is used): " + duplicates)
                        : missingKeys.size() + " missing, e.g. " + missingKeys.subList(0, Math.min(10, missingKeys.size()))));
        List<String> routeKeys = new ArrayList<>(Prompt1TurnMessages.A1_STEP1_KEYS);
        routeKeys.addAll(Prompt1TurnMessages.A1_STEP2_KEYS);
        List<String> notFound = Prompt1SourcePackageService.readKeys(sections, routeKeys).stream()
                .filter(key -> !key.found()).map(key -> key.key()).toList();
        KeyText lookup = Prompt1SourcePackageService.r4Lookup(sections);
        List<String> notFoundAll = new ArrayList<>(notFound);
        if (!lookup.found()) {
            notFoundAll.add(lookup.key());
        }
        checks.add(new Check("first-turn keys and the R4 lookup are found", notFoundAll.isEmpty(),
                notFoundAll.isEmpty() ? (routeKeys.size() + 1) + " keys" : "not found: " + notFoundAll));
        List<String> conflicts = knowledgeLoader.conflicts(documents);
        checks.add(new Check("document versions are new or unchanged", conflicts.isEmpty(), conflicts.isEmpty()
                ? "no conflict with the knowledge tables"
                : "already loaded with other content (a changed document needs a new version): " + conflicts));
        return new Prompt1Upload(documents, versions);
    }

    /** Makes a release the ACTIVE one of its prompt; the release active before it is RETIRED. */
    @Transactional
    public Optional<ReleaseReport> activate(String releaseId) {
        Optional<PromptReleaseEntity> found = releaseRepository.findById(releaseId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        PromptReleaseEntity release = found.get();
        if (release.getStatus() != Status.ACTIVE) {
            for (PromptReleaseEntity active : releaseRepository.findByPromptAndStatus(release.getPrompt(), Status.ACTIVE)) {
                active.setStatus(Status.RETIRED);
                releaseRepository.save(active);
                log.info("Release {} {} retired", active.getPrompt(), active.getName());
            }
            release.setStatus(Status.ACTIVE);
            release.setActivatedAt(LocalDateTime.now());
            releaseRepository.save(release);
            log.info("Release {} {} activated", release.getPrompt(), release.getName());
        }
        return Optional.of(report(release, true));
    }

    /** The releases of a prompt (all prompts when null), newest first, without check details and files. */
    public List<ReleaseReport> list(String prompt) {
        List<PromptReleaseEntity> releases = prompt == null || prompt.isBlank()
                ? releaseRepository.findAllByOrderByCreatedAtDesc()
                : releaseRepository.findByPromptOrderByCreatedAtDesc(prompt.strip().toUpperCase(Locale.ROOT));
        return releases.stream().map(release -> report(release, false).summary()).toList();
    }

    public Optional<ReleaseReport> get(String releaseId) {
        return releaseRepository.findById(releaseId).map(release -> report(release, true));
    }

    private ReleaseReport report(PromptReleaseEntity release, boolean withDetails) {
        List<FileInfo> files = withDetails
                ? fileRepository.findByReleaseIdOrderByFileNameAsc(release.getId()).stream()
                .map(file -> new FileInfo(file.getFileName(), file.getRole(), file.getDocumentCode(), file.getVersion(),
                        file.getSha256(), file.getSizeBytes())).toList()
                : null;
        List<Check> checks = withDetails ? fromJson(release.getCheckReport(), new TypeReference<List<Check>>() {
        }) : null;
        Map<String, String> versions = fromJson(release.getDocumentVersions(), new TypeReference<Map<String, String>>() {
        });
        String changes = null;
        try {
            changes = objectMapper.readTree(release.getManifest()).path("changes").asText(null);
        } catch (JsonProcessingException e) {
            log.warn("Release {} {}: manifest cannot be read", release.getPrompt(), release.getName());
        }
        return new ReleaseReport(release.getId(), release.getPrompt(), release.getName(), release.getStatus().name(), true,
                release.getCreatedAt(), release.getActivatedAt(), changes, versions, checks, files);
    }

    /**
     * The files of a zip by file name (folders inside the zip are ignored), with limits on the number of files
     * and the unzipped size.
     */
    static Map<String, byte[]> unzip(byte[] zip) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        long total = 0;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = in.getNextEntry()) != null) {
                String path = entry.getName().replace('\\', '/');
                String file = path.substring(path.lastIndexOf('/') + 1);
                if (entry.isDirectory() || path.startsWith("__MACOSX/") || file.isEmpty() || file.startsWith(".")) {
                    continue;
                }
                if (entries.size() >= MAX_FILES) {
                    throw new BadPackageException("The package has more than " + MAX_FILES + " files");
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int read;
                while ((read = in.read(buffer)) > 0) {
                    total += read;
                    if (total > MAX_UNZIPPED_BYTES) {
                        throw new BadPackageException("The package is larger than " + (MAX_UNZIPPED_BYTES >> 20) + " MB unzipped");
                    }
                    out.write(buffer, 0, read);
                }
                if (entries.putIfAbsent(file, out.toByteArray()) != null) {
                    throw new BadPackageException("The package has two files named " + file);
                }
            }
        } catch (IOException e) {
            throw new BadPackageException("The upload is not a readable zip: " + e.getMessage());
        }
        if (entries.isEmpty()) {
            throw new BadPackageException("The upload is empty or not a zip");
        }
        return entries;
    }

    private static String text(Map<String, byte[]> entries, ManifestFile file) {
        return new String(entries.get(file.file()), StandardCharsets.UTF_8);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise release data", e);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.warn("Stored release data cannot be read: {}", e.getOriginalMessage());
            return null;
        }
    }
}

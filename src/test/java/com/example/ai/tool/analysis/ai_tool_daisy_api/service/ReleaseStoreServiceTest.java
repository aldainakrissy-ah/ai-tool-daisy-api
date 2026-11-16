package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.PromptReleaseEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.PromptReleaseEntity.Status;
import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.PromptReleaseFileEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.ReleaseReport;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.PromptReleaseFileRepository;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.PromptReleaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The release store: package checks, nothing stored for a refused package, activation and rollback, and
 * Jan's v0.136 files as a release that a run reads back unchanged (when KNOWLEDGE_MARKDOWN_DIR and
 * PROMPT1_TURNS_CONFIG are set). No file content is printed.
 */
class ReleaseStoreServiceTest {

    private static final String SM = "DAISY_Prompt_1_v0.136_compact.md";
    private static final String DOC18 = "18_werkbestanden_format.md";
    private static final String SCHEMA = "Structured_JSON_Prompt_1_v0.46d-lean (used by claude).json";
    private static final String MATRIX = "07_B2_matrix.tsv";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, PromptReleaseEntity> releases = new LinkedHashMap<>();
    private final List<PromptReleaseFileEntity> files = new ArrayList<>();
    private KnowledgeLoaderService knowledgeLoader;
    private Prompt1SourcePackageService sourcePackageService;
    private PromptReleaseRepository releaseRepository;
    private PromptReleaseFileRepository fileRepository;
    private ReleaseStoreService store;

    @BeforeEach
    void setUp() {
        releaseRepository = mock(PromptReleaseRepository.class);
        when(releaseRepository.save(any())).thenAnswer(call -> {
            PromptReleaseEntity release = call.getArgument(0);
            releases.put(release.getId(), release);
            return release;
        });
        when(releaseRepository.findById(anyString())).thenAnswer(call -> Optional.ofNullable(releases.get(call.<String>getArgument(0))));
        when(releaseRepository.findByPromptAndName(anyString(), anyString())).thenAnswer(call -> releases.values().stream()
                .filter(r -> r.getPrompt().equals(call.getArgument(0)) && r.getName().equals(call.getArgument(1))).findFirst());
        when(releaseRepository.findByPromptAndStatus(anyString(), any())).thenAnswer(call -> releases.values().stream()
                .filter(r -> r.getPrompt().equals(call.getArgument(0)) && r.getStatus() == call.getArgument(1)).toList());
        when(releaseRepository.findFirstByPromptAndStatus(anyString(), any())).thenAnswer(call -> releases.values().stream()
                .filter(r -> r.getPrompt().equals(call.getArgument(0)) && r.getStatus() == call.getArgument(1)).findFirst());
        fileRepository = mock(PromptReleaseFileRepository.class);
        when(fileRepository.save(any())).thenAnswer(call -> {
            files.add(call.getArgument(0));
            return call.getArgument(0);
        });
        when(fileRepository.findByReleaseIdOrderByFileNameAsc(anyString())).thenAnswer(call -> files.stream()
                .filter(f -> f.getReleaseId().equals(call.getArgument(0)))
                .sorted(Comparator.comparing(f -> f.getFileName())).toList());
        knowledgeLoader = mock(KnowledgeLoaderService.class);
        when(knowledgeLoader.conflicts(anyList())).thenReturn(List.of());
        sourcePackageService = mock(Prompt1SourcePackageService.class);
        store = new ReleaseStoreService(releaseRepository, fileRepository, knowledgeLoader, new MarkdownSourceReader(),
                new KnowledgeSectionSplitter(), sourcePackageService, objectMapper);
    }

    static byte[] zip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** A stored release, as an earlier upload left it. */
    private String stored(String name, Status status) {
        PromptReleaseEntity release = PromptReleaseEntity.builder().id(UUID.randomUUID().toString()).prompt("P1")
                .name(name).status(status).manifest("{}").createdAt(java.time.LocalDateTime.now()).build();
        releases.put(release.getId(), release);
        return release.getId();
    }

    @Test
    void anUploadThatIsNotAReleasePackageIsRefused() throws Exception {
        assertThrows(ReleaseStoreService.BadPackageException.class, () -> store.upload(utf8("not a zip")));
        assertThrows(ReleaseStoreService.BadPackageException.class,
                () -> store.upload(zip(Map.of("readme.txt", utf8("no manifest")))));
        assertThrows(ReleaseStoreService.BadPackageException.class,
                () -> store.upload(zip(Map.of("manifest.json", utf8("{not json")))));
        assertThrows(ReleaseStoreService.BadPackageException.class,
                () -> store.upload(zip(Map.of("a/x.md", utf8("1"), "b/x.md", utf8("2"), "manifest.json", utf8("{}")))),
                "two files with one name in different folders");
        assertTrue(releases.isEmpty());
    }

    @Test
    void folderNamesInsideTheZipAreIgnored() throws Exception {
        Map<String, byte[]> entries = ReleaseStoreService.unzip(zip(Map.of("P1-v0.136/manifest.json", utf8("{}"),
                "__MACOSX/._manifest.json", utf8("x"), "P1-v0.136/.DS_Store", utf8("x"))));
        assertEquals(Set.of("manifest.json"), entries.keySet());
    }

    @Test
    void onlyPrompt1ReleasesAreStored() throws Exception {
        ReleaseReport other = store.upload(zip(Map.of("manifest.json",
                utf8("{\"prompt\":\"P2\",\"release\":\"P2-v4\",\"files\":[]}"))));

        assertFalse(other.passed());
        assertEquals("REJECTED", other.status());
        assertTrue(other.checks().stream().anyMatch(c -> !c.ok() && c.name().equals("supported prompt")));
        assertTrue(releases.isEmpty());
    }

    @Test
    void aPackageThatFailsACheckStoresNothing() throws Exception {
        ReleaseReport incomplete = store.upload(zip(Map.of(
                "manifest.json", utf8("{\"prompt\":\"P1\",\"release\":\"P1-test\",\"files\":["
                        + "{\"file\":\"sm.md\",\"role\":\"system_message\"},{\"file\":\"extra.md\",\"role\":\"notes\"}]}"),
                "sm.md", utf8("text"), "unlisted.md", utf8("x"), "extra.md", utf8("x"))));
        assertFalse(incomplete.passed());
        String failed = incomplete.checks().stream().filter(check -> !check.ok()).map(check -> check.detail()).toList().toString();
        assertTrue(failed.contains("unknown role 'notes'"), failed);
        assertTrue(failed.contains("unlisted.md"), failed);
        assertTrue(failed.contains("no doc18 file"), failed);
        assertTrue(failed.contains("no reference document R3"), failed);

        assertTrue(releases.isEmpty(), "a refused package stores nothing");
        assertTrue(files.isEmpty());
        verify(knowledgeLoader, never()).loadReleaseDocuments(anyString(), anyList());
    }

    @Test
    void aReleaseNameIsUsedOnce() throws Exception {
        stored("P1-v0.136", Status.ACTIVE);

        ReleaseReport again = store.upload(zip(Map.of("manifest.json",
                utf8("{\"prompt\":\"P1\",\"release\":\"P1-v0.136\",\"files\":[]}"))));

        assertFalse(again.checks().stream().filter(c -> c.name().equals("release name is new")).findFirst().orElseThrow().ok());
        assertEquals(1, releases.size());
    }

    @Test
    void activatingRetiresTheActiveReleaseAndRollingBackActivatesAnOlderOne() {
        String v136 = stored("P1-v0.136", Status.DRAFT);
        String v137 = stored("P1-v0.137", Status.DRAFT);

        store.activate(v136);
        assertEquals(Status.ACTIVE, releases.get(v136).getStatus());
        store.activate(v137);
        assertEquals(Status.RETIRED, releases.get(v136).getStatus());
        assertEquals(Status.ACTIVE, releases.get(v137).getStatus());

        store.activate(v136);  // rollback
        assertEquals(Status.ACTIVE, releases.get(v136).getStatus());
        assertEquals(Status.RETIRED, releases.get(v137).getStatus());
        assertNotNull(releases.get(v136).getActivatedAt());
        assertTrue(store.activate("unknown").isEmpty());
    }

    /** Jan's v0.136 files as one package, as the manifest in sourceData/releases lists them. */
    private static Map<String, byte[]> janPackage(Path sources) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        StringBuilder manifest = new StringBuilder("{\"prompt\":\"P1\",\"release\":\"P1-v0.136\",\"changes\":\"test\",\"files\":[");
        Map<String, String> roles = new LinkedHashMap<>();
        roles.put(SM, "system_message");
        roles.put(DOC18, "doc18");
        roles.put(SCHEMA, "schema");
        roles.put(MATRIX, "b2_matrix");
        try (var listing = Files.list(sources)) {
            listing.map(path -> path.getFileName().toString()).filter(name -> MarkdownSourceReader.isReferenceDocument(name))
                    .sorted().forEach(name -> roles.put(name, "reference"));
        }
        for (Map.Entry<String, String> role : roles.entrySet()) {
            entries.put(role.getKey(), Files.readAllBytes(sources.resolve(role.getKey())));
            manifest.append("{\"file\":\"").append(role.getKey()).append("\",\"role\":\"").append(role.getValue()).append("\"},");
        }
        entries.put("turns-v0136.json", Files.readAllBytes(Prompt1TestFiles.turnConfigFile()));
        manifest.append("{\"file\":\"turns-v0136.json\",\"role\":\"turn_config\"}]}");
        entries.put("manifest.json", utf8(manifest.toString()));
        return entries;
    }

    private static Path sources() {
        String dir = System.getenv("KNOWLEDGE_MARKDOWN_DIR");
        Assumptions.assumeTrue(dir != null && Files.isDirectory(Path.of(dir)), "KNOWLEDGE_MARKDOWN_DIR not set");
        return Path.of(dir);
    }

    @Test
    void jansReleasePassesEveryCheckAndARunReadsItBackUnchanged() throws Exception {
        Path sources = sources();
        ReleaseReport report = store.upload(zip(janPackage(sources)));

        assertTrue(report.passed(), () -> report.checks().stream().filter(c -> !c.ok()).toList().toString());
        assertEquals("DRAFT", report.status());
        assertEquals(Map.of("01", "v4", "02", "v04", "03", "v05", "05", "v6", "06", "v12", "07", "v9",
                "R3", "v06", "R4", "v5", "R5", "v2"), report.documentVersions());
        assertEquals(14, report.files().size());
        assertTrue(files.stream().filter(f -> f.getRole().equals("reference")).allMatch(f -> f.getContent() == null),
                "reference documents go to the knowledge tables, not into the release files");
        verify(knowledgeLoader).loadReleaseDocuments(eq("P1-v0.136"), argThat(documents -> documents.size() == 9));
        verify(sourcePackageService).clearSnapshot();

        store.activate(report.id());
        Prompt1Release release = new Prompt1ReleaseService(releaseRepository, fileRepository, sourcePackageService,
                objectMapper).current();
        assertEquals("P1-v0.136", release.name());
        assertEquals(Files.readString(sources.resolve(SM)), release.systemMessage(), "the system message is unchanged");
        assertEquals(Files.readString(sources.resolve(DOC18)), release.doc18());
        assertEquals(Files.readString(sources.resolve(SCHEMA)), release.schemaText());
        assertEquals(Prompt1TestFiles.turnConfig(), release.config());
        assertEquals(report.documentVersions(), release.documentVersions());
    }

    @Test
    void aSystemMessageThatNoLongerMatchesTheTurnConfigurationIsRefused() throws Exception {
        Map<String, byte[]> entries = janPackage(sources());
        String firstDroppedLine = Prompt1TestFiles.turnConfig().s0Drop().get(0);
        String systemMessage = new String(entries.get(SM), StandardCharsets.UTF_8);
        entries.put(SM, utf8(systemMessage.replace("\n" + firstDroppedLine, "\nCHANGED " + firstDroppedLine)));

        ReleaseReport report = store.upload(zip(entries));

        assertFalse(report.passed());
        assertFalse(report.checks().stream().filter(c -> c.name().equals("system message cut per turn")).findFirst()
                .orElseThrow().ok());
        assertTrue(releases.isEmpty());
    }

    @Test
    void aChangedDocumentUnderAnExistingVersionIsRefused() throws Exception {
        when(knowledgeLoader.conflicts(anyList())).thenReturn(List.of("R3 v06"));

        ReleaseReport report = store.upload(zip(janPackage(sources())));

        assertFalse(report.passed());
        assertTrue(report.checks().stream().anyMatch(c -> !c.ok() && c.detail().contains("R3 v06")));
        assertTrue(releases.isEmpty());
    }
}

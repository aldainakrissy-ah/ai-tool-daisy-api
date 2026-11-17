package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.PromptReleaseEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.PromptReleaseFileEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.ReleaseReport.Check;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.PromptReleaseFileRepository;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.PromptReleaseRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1Release.*;

/**
 * The Prompt 1 release a run uses: the ACTIVE P1 release of the release store, or a given release (e.g. a
 * DRAFT to test before activating it). Without any active release, the local files of the
 * {@code experiment.prompt1.turns.*} settings are used (for development; a server has no such files).
 * A stored release never changes, so it is read from the database once and then kept in memory.
 */
@Slf4j
@Service
public class Prompt1ReleaseService {

    private final PromptReleaseRepository releaseRepository;
    private final PromptReleaseFileRepository fileRepository;
    private final Prompt1SourcePackageService sourcePackageService;
    private final ObjectMapper objectMapper;

    @Value("${experiment.prompt1.turns.config:}")
    private String turnConfigFile;

    @Value("${experiment.prompt1.turns.system-message-file:}")
    private String systemMessageFile;

    @Value("${experiment.prompt1.turns.doc18-file:}")
    private String doc18File;

    @Value("${experiment.prompt1.turns.schema-file:}")
    private String schemaFile;

    @Value("${experiment.prompt1.turns.b2-matrix-file:}")
    private String b2MatrixFile;

    /** Stored releases already read, by ID. */
    private final Map<String, Prompt1Release> stored = new ConcurrentHashMap<>();

    public Prompt1ReleaseService(PromptReleaseRepository releaseRepository, PromptReleaseFileRepository fileRepository,
                                 Prompt1SourcePackageService sourcePackageService, ObjectMapper objectMapper) {
        this.releaseRepository = releaseRepository;
        this.fileRepository = fileRepository;
        this.sourcePackageService = sourcePackageService;
        this.objectMapper = objectMapper;
    }

    /**
     * The release a run started now uses: the active P1 release, otherwise the local files.
     *
     * @throws IllegalStateException when there is neither (Prompt 1 is not ready to run)
     */
    public Prompt1Release current() {
        Optional<PromptReleaseEntity> active =
                releaseRepository.findFirstByPromptAndStatus(PROMPT, PromptReleaseEntity.Status.ACTIVE);
        return active.isPresent() ? fromStore(active.get()) : fromFiles();
    }

    /**
     * A given P1 release of the store, in any state (a DRAFT can be tried before it is activated).
     *
     * @throws IllegalArgumentException when there is no P1 release with this ID
     */
    public Prompt1Release byId(String releaseId) {
        PromptReleaseEntity release = releaseRepository.findById(releaseId)
                .filter(found -> PROMPT.equals(found.getPrompt()))
                .orElseThrow(() -> new IllegalArgumentException("No Prompt 1 release " + releaseId));
        return fromStore(release);
    }

    Prompt1Release fromStore(PromptReleaseEntity release) {
        Prompt1Release cached = stored.get(release.getId());
        if (cached != null) {
            return cached;
        }
        Map<String, PromptReleaseFileEntity> byRole = new HashMap<>();
        for (PromptReleaseFileEntity file : fileRepository.findByReleaseIdOrderByFileNameAsc(release.getId())) {
            if (FILE_ROLES.contains(file.getRole())) {
                byRole.put(file.getRole(), file);
            }
        }
        List<String> missing = FILE_ROLES.stream().filter(role -> !byRole.containsKey(role)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Release " + release.getName() + " has no file for " + missing);
        }
        Prompt1Release built;
        try {
            built = Prompt1Release.of(release.getId(), release.getName(),
                    byRole.get(TURN_CONFIG).getContent().getBytes(StandardCharsets.UTF_8),
                    byRole.get(SYSTEM_MESSAGE).getContent(), byRole.get(SYSTEM_MESSAGE).getFileName(),
                    byRole.get(DOC18).getContent(), byRole.get(SCHEMA).getContent(), byRole.get(B2_MATRIX).getContent(),
                    documentVersions(release));
        } catch (IllegalArgumentException e) {
            // e.g. a release made for an older backend (P1-v0.136 after the v07 deploy): not a bad request, Prompt 1 is not ready
            throw new Prompt1NotReadyException("Prompt 1 is not ready to run: release " + release.getName()
                    + " cannot be used: " + e.getMessage(), e);
        }
        stored.put(release.getId(), built);
        log.info("Prompt 1 release {} read from the release store", release.getName());
        return built;
    }

    private Map<String, String> documentVersions(PromptReleaseEntity release) {
        try {
            return objectMapper.readValue(release.getDocumentVersions(), new TypeReference<Map<String, String>>() {
            });
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new IllegalStateException("Release " + release.getName() + " has no readable document versions", e);
        }
    }

    /** The local files of the experiment.prompt1.turns.* settings, with the document versions selected now. */
    Prompt1Release fromFiles() {
        try {
            return Prompt1Release.of(null, null,
                    Files.readAllBytes(path(turnConfigFile, "turn configuration (experiment.prompt1.turns.config)")),
                    read(systemMessageFile, "system message (experiment.prompt1.turns.system-message-file)"),
                    fileName(systemMessageFile),
                    read(doc18File, "doc 18 (experiment.prompt1.turns.doc18-file)"),
                    read(schemaFile, "output schema (experiment.prompt1.turns.schema-file)"),
                    read(b2MatrixFile, "B2 matrix (experiment.prompt1.turns.b2-matrix-file)"),
                    sourcePackageService.selectedDocumentVersions());
        } catch (IOException | RuntimeException e) {
            throw new Prompt1NotReadyException("Prompt 1 is not ready to run: no active release in the release store, "
                    + "and the local files cannot be used: " + e.getMessage(), e);
        }
    }

    /**
     * Checks the prompt texts against the turn configuration: the system message can be cut for every turn
     * (every §0 line the configuration names is found exactly once) and doc 18 has every heading a turn reads.
     */
    public static List<Check> checkTexts(Prompt1Release release) {
        List<Check> checks = new ArrayList<>();
        Prompt1TurnConfig config = release.config();
        List<String> problems = new ArrayList<>();
        for (String turn : config.smSections().keySet()) {
            try {
                Prompt1TurnSystemMessage.forTurn(release.systemMessage(), turn.equals("C") ? "C_x" : turn, config);
            } catch (RuntimeException e) {
                problems.add(turn + ": " + e.getMessage());
            }
        }
        checks.add(new Check("system message cut per turn", problems.isEmpty(),
                problems.isEmpty() ? config.smSections().size() + " turns" : String.join("; ", problems)));

        List<String> headingLines = release.doc18().replace("\r\n", "\n").lines()
                .filter(line -> line.startsWith("#")).toList();
        Set<String> wanted = new LinkedHashSet<>();
        wanted.add(config.doc18General());
        config.doc18Headings().values().forEach(headings -> wanted.addAll(headings));
        List<String> notFound = wanted.stream()
                .filter(heading -> headingLines.stream().noneMatch(line -> line.startsWith(heading))).toList();
        checks.add(new Check("doc 18 headings per turn", notFound.isEmpty(),
                notFound.isEmpty() ? wanted.size() + " headings" : "not found: " + notFound));

        // v07: the production schema can check an output.json (an empty object misses every required field)
        List<String> errors = Prompt1ProductionJson.schemaErrors(JsonNodeFactory.instance.objectNode(), release.productionSchema());
        checks.add(new Check("production schema checks output.json", !errors.isEmpty(), errors.isEmpty()
                ? "the schema accepts an empty output.json" : "an empty output.json is refused (" + errors.size() + " problem(s))"));
        return checks;
    }

    private static Path path(String file, String what) {
        if (file == null || file.isBlank()) {
            throw new IllegalStateException("no " + what + " configured");
        }
        return Path.of(file.trim());
    }

    private static String read(String file, String what) throws IOException {
        return Files.readString(path(file, what), StandardCharsets.UTF_8);
    }

    private static String fileName(String file) {
        return file == null || file.isBlank() ? null : Path.of(file.trim()).getFileName().toString();
    }
}

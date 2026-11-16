package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.Prompt1RunEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.Prompt1RunEntity.Status;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1ExperimentResult;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1ExperimentResult.CallUsage;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1RunDto;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.Prompt1RunRepository;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1ExperimentService.IntakeFile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Background runs of Prompt 1 on Jan's turn-based route: submitting returns a run ID at once, a
 * small worker pool runs Prompt 1, and the run's status and result are read back by ID.
 * <p>
 * Guards, so a background run is as consistent and traceable as a synchronous one:
 * <ul>
 *   <li>the document versions are pinned at submit; a knowledge reload during the run does not
 *       change the packages it uses;</li>
 *   <li>every run stores its settings, document versions, response IDs and usage;</li>
 *   <li>the result JSON is stored only when the run is VALID; otherwise the run is FAILED with
 *       the reason;</li>
 *   <li>the intake file contents are held in memory only, never stored; runs left QUEUED or
 *       RUNNING when the app stopped are marked INTERRUPTED at the next start.</li>
 * </ul>
 * The worker pool is private to this service on purpose: a shared executor bean would replace
 * the one Spring uses for {@code @Async}. With several app instances, INTERRUPTED marking at
 * start-up would also hit the other instances' runs; this is a single-instance design for now.
 */
@Slf4j
@Service
public class Prompt1RunService {

    private static final int MAX_LIST_LIMIT = 100;

    /** The worker queue is full; the caller should try again later. */
    public static class QueueFullException extends RuntimeException {
        QueueFullException(String message) {
            super(message);
        }
    }

    private final Prompt1ReleaseService releaseService;
    private final Prompt1RunRepository runRepository;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    /** The route of every run (stored per run; the two-call route was retired on 13 Nov 2026). */
    public static final String FLOW_TURNS = "turns";

    private final Prompt1TurnRunService turnRunService;

    @Autowired
    public Prompt1RunService(Prompt1ReleaseService releaseService,
                             Prompt1RunRepository runRepository,
                             ObjectMapper objectMapper,
                             Prompt1TurnRunService turnRunService,
                             @Value("${experiment.prompt1.runs.concurrency:2}") int concurrency,
                             @Value("${experiment.prompt1.runs.queue-capacity:50}") int queueCapacity) {
        this(releaseService, runRepository, objectMapper, turnRunService, newExecutor(concurrency, queueCapacity));
    }

    Prompt1RunService(Prompt1ReleaseService releaseService, Prompt1RunRepository runRepository,
                      ObjectMapper objectMapper, Prompt1TurnRunService turnRunService, Executor executor) {
        this.releaseService = releaseService;
        this.runRepository = runRepository;
        this.objectMapper = objectMapper;
        this.turnRunService = turnRunService;
        this.executor = executor;
    }

    private static ThreadPoolExecutor newExecutor(int concurrency, int queueCapacity) {
        AtomicInteger number = new AtomicInteger();
        return new ThreadPoolExecutor(concurrency, concurrency, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), task -> {
                    Thread thread = new Thread(task, "prompt1-run-" + number.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    @PreDestroy
    void shutdown() {
        if (executor instanceof ExecutorService service) {
            service.shutdownNow();
        }
    }

    public Prompt1RunDto submit(List<IntakeFile> files, String preset) {
        return submit(files, preset, null);
    }

    /**
     * Queues a run and returns it as QUEUED. The run keeps the release it was submitted with, also when
     * another release is activated before it starts.
     *
     * @param preset    a model-and-effort plan for this run (quality, standard, economy); null = the configured plan
     * @param releaseId a release of the release store to run with (e.g. a DRAFT to try); null = the active one
     * @throws IllegalArgumentException when no intake file is given, a file is empty or the release is unknown
     * @throws IllegalStateException    when there is no release to run with
     * @throws QueueFullException       when the worker queue is full
     */
    public Prompt1RunDto submit(List<IntakeFile> files, String preset, String releaseId) {
        Prompt1ExperimentService.requireIntake(files);
        turnRunService.checkReady(files);  // bad intake JSON: refused now, not in the background
        Prompt1Release release = releaseId == null || releaseId.isBlank()
                ? releaseService.current() : releaseService.byId(releaseId.strip());
        String runPreset = preset == null || preset.isBlank() ? null : preset.strip();
        Map<String, String> versions = release.documentVersions();
        Prompt1TurnRunService.TurnSettings settings = turnRunService.settings(runPreset, release);

        Prompt1RunEntity run = Prompt1RunEntity.builder()
                .id(UUID.randomUUID().toString())
                .status(Status.QUEUED)
                .flow(FLOW_TURNS)
                .createdAt(LocalDateTime.now())
                .model(settings.model())
                .reasoningEffort(settings.effort())
                .packageProfile("TURNS " + settings.release())
                .releaseId(release.id())
                .releaseName(release.id() == null ? null : release.name())
                .systemMessageFile(settings.systemMessageFile())
                .documentVersions(toJson(versions))
                .intakeFileNames(toJson(files.stream().map(file -> file.name()).toList()))
                .intakeHash(intakeHash(files))
                .build();
        runRepository.save(run);

        List<IntakeFile> intake = List.copyOf(files);
        String runId = run.getId();
        try {
            executor.execute(() -> execute(runId, intake, release, runPreset));
        } catch (RejectedExecutionException e) {
            run.setStatus(Status.FAILED);
            run.setError("The run queue is full; try again later.");
            run.setFinishedAt(LocalDateTime.now());
            runRepository.save(run);
            throw new QueueFullException("The Prompt 1 run queue is full; try again later.");
        }
        log.info("Prompt 1 run {} queued: {} intake file(s), release {}, document versions {}", runId, files.size(),
                release.name(), versions);
        return toDto(run, false);
    }

    /** Runs Prompt 1 for a queued run and records the outcome. Called on a worker thread. */
    void execute(String runId, List<IntakeFile> files, Prompt1Release release, String preset) {
        Prompt1RunEntity run = runRepository.findById(runId).orElse(null);
        if (run == null) {
            log.warn("Prompt 1 run {} disappeared before it started", runId);
            return;
        }
        run.setStatus(Status.RUNNING);
        run.setStartedAt(LocalDateTime.now());
        runRepository.save(run);
        log.info("Prompt 1 run {} started", runId);
        try {
            apply(run, turnRunService.run(runId, files, release, preset));
        } catch (Exception e) {
            log.error("Prompt 1 run {} failed: {}", runId, e.getMessage(), e);
            run.setStatus(Status.FAILED);
            run.setError(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        run.setFinishedAt(LocalDateTime.now());
        runRepository.save(run);
        log.info("Prompt 1 run {} finished: {}", runId, run.getStatus());
    }

    /** Copies the experiment result onto the run; VALID only when Prompt 1 closed through Step 14 without error. */
    void apply(Prompt1RunEntity run, Prompt1ExperimentResult result) {
        run.setModel(result.getModel());
        run.setReasoningEffort(result.getReasoningEffort());
        run.setPackageProfile(result.getPackageProfile());
        run.setSystemMessageFile(result.getSystemMessageFile());
        run.setSystemMessageHash(result.getSystemMessageHash());
        run.setSchemaName(result.getSchemaName());
        run.setSchemaHash(result.getSchemaHash());
        run.setDocumentVersions(toJson(result.getDocumentVersions()));
        run.setPrimaryIeRef(result.getPrimaryIeRef());
        List<CallUsage> calls = result.getCalls();
        CallUsage total = result.getTotal();
        if (total != null) {
            run.setInputTokens(total.getInputTokens());
            run.setCachedInputTokens(total.getCachedInputTokens());
            run.setOutputTokens(total.getOutputTokens());
            run.setEstimatedCostUsd(total.getEstimatedCostUsd());
        }
        run.setCalls(toJson(calls));
        run.setNotes(toJson(result.getNotes()));

        JsonNode output = result.getResult();
        boolean valid = result.getError() == null && output != null
                && "valid".equals(output.path("run_status").asText(null));
        if (valid) {
            run.setStatus(Status.VALID);
            run.setResult(toJson(output));
            run.setError(null);
        } else {
            run.setStatus(Status.FAILED);
            run.setResult(null);
            run.setError(failureReason(result));
        }
    }

    /** The clearest available reason why a run is not VALID. */
    static String failureReason(Prompt1ExperimentResult result) {
        if (result.getError() != null) {
            return result.getError();
        }
        JsonNode output = result.getResult();
        if (output != null) {
            String stage = firstNonBlank(output.path("failure").path("stage").asText(""),
                    output.path("failure_stage").asText(""));
            String reason = firstNonBlank(output.path("failure").path("reason").asText(""),
                    output.path("failure_reason").asText(""));
            if (!stage.isEmpty() || !reason.isEmpty()) {
                return "Prompt 1 run_status " + output.path("run_status").asText("?") + ": " + stage + " / " + reason;
            }
        }
        List<String> notes = result.getNotes();
        return notes.isEmpty() ? "The run did not complete through Step 14." : notes.get(notes.size() - 1);
    }

    private static String firstNonBlank(String first, String second) {
        return !first.isBlank() ? first : second;
    }

    public Optional<Prompt1RunDto> get(String runId) {
        return runRepository.findById(runId).map(run -> toDto(run, true));
    }

    /** The latest runs, newest first, without their result JSON. */
    public List<Prompt1RunDto> list(int limit) {
        int size = Math.max(1, Math.min(limit, MAX_LIST_LIMIT));
        return runRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, size)).stream()
                .map(run -> toDto(run, false))
                .toList();
    }

    /** Runs the app did not finish (it stopped or restarted) can never finish now. */
    @EventListener(ApplicationReadyEvent.class)
    public void markUnfinishedRunsInterrupted() {
        List<Prompt1RunEntity> unfinished = runRepository.findByStatusIn(List.of(Status.QUEUED, Status.RUNNING));
        for (Prompt1RunEntity run : unfinished) {
            run.setStatus(Status.INTERRUPTED);
            run.setFinishedAt(LocalDateTime.now());
            run.setError("The application stopped before this run finished. Submit it again.");
            runRepository.save(run);
        }
        if (!unfinished.isEmpty()) {
            log.warn("Marked {} unfinished Prompt 1 run(s) as INTERRUPTED", unfinished.size());
        }
    }

    private Prompt1RunDto toDto(Prompt1RunEntity run, boolean withResult) {
        return Prompt1RunDto.builder()
                .runId(run.getId())
                .status(run.getStatus().name())
                .flow(run.getFlow() == null ? "two-call" : run.getFlow())
                .createdAt(run.getCreatedAt())
                .startedAt(run.getStartedAt())
                .finishedAt(run.getFinishedAt())
                .model(run.getModel())
                .reasoningEffort(run.getReasoningEffort())
                .packageProfile(run.getPackageProfile())
                .releaseName(run.getReleaseName())
                .systemMessageFile(run.getSystemMessageFile())
                .systemMessageHash(run.getSystemMessageHash())
                .schemaName(run.getSchemaName())
                .schemaHash(run.getSchemaHash())
                .documentVersions(fromJson(run.getDocumentVersions(), new TypeReference<LinkedHashMap<String, String>>() {
                }, new LinkedHashMap<>()))
                .intakeFiles(fromJson(run.getIntakeFileNames(), new TypeReference<List<String>>() {
                }, new ArrayList<>()))
                .primaryIeRef(run.getPrimaryIeRef())
                .inputTokens(run.getInputTokens())
                .cachedInputTokens(run.getCachedInputTokens())
                .outputTokens(run.getOutputTokens())
                .estimatedCostUsd(run.getEstimatedCostUsd())
                .calls(fromJson(run.getCalls(), new TypeReference<List<CallUsage>>() {
                }, new ArrayList<>()))
                .notes(fromJson(run.getNotes(), new TypeReference<List<String>>() {
                }, new ArrayList<>()))
                .error(run.getError())
                .result(withResult && run.getResult() != null ? readTree(run.getResult()) : null)
                .build();
    }

    /** SHA-256 over the intake file names and contents, in upload order. */
    static String intakeHash(List<IntakeFile> files) {
        List<byte[]> parts = new ArrayList<>();
        for (IntakeFile file : files) {
            parts.add(String.valueOf(file.name()).getBytes(StandardCharsets.UTF_8));
            parts.add(new byte[]{0});
            parts.add(file.content());
            parts.add(new byte[]{0});
        }
        return Hashes.sha256(parts);
    }

    private String toJson(Object value) {
        try {
            return value == null ? null : objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise run data: " + e.getOriginalMessage(), e);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.warn("Could not read stored run data: {}", e.getOriginalMessage());
            return fallback;
        }
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            log.warn("Could not read stored run result: {}", e.getOriginalMessage());
            return null;
        }
    }
}

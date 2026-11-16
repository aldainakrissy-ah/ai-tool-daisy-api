package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.Prompt1RunFileEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.Prompt1RunFileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The files of turn-based Prompt 1 runs, stored per run in {@code prompt1_run_file}:
 * <ul>
 *   <li>the working files (doc 18), including the two server files Jan's validator reads:
 *       {@code tasks.tsv} (which steps are closed, in order) and {@code retrieval_log.tsv} (every source key read);</li>
 *   <li>under {@code prompts/}: the prompts sent to the model ({@link #writePrompt}, only with
 *       {@code prompt1.run-files.store-prompts}) and the model's answers ({@link #writeAnswer}, always).</li>
 * </ul>
 * While a run is active ({@link #open} … {@link #close}) its working files are also held in memory: every
 * write goes to the database and the memory copy, and reads come from memory. A turn reads the files many
 * times, so this saves the database reads; prompts and answers are never held in memory. Writes of one run
 * are serialised on that run (runs never wait on each other).
 */
@Service
public class Prompt1RunFiles {

    static final String TASKS = "tasks.tsv";
    static final String RETRIEVAL_LOG = "retrieval_log.tsv";
    static final String RETRIEVAL_LOG_HEADER = "seq\tstep\ttool\tquery\tsource_doc\tchunk_ids\tpurpose\tgap";
    /** Folder prefix of the stored turn prompts and model answers, e.g. prompts/A1.txt, prompts/antwoord_A1.txt. */
    static final String PROMPTS = "prompts/";
    private static final List<String> TASK_STEPS = List.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
            "11", "12", "13", "14", "eindcontrole");

    private final Prompt1RunFileRepository repository;
    /** Run ID → its working files (name → content), only while the run is active. */
    private final Map<String, Map<String, String>> active = new ConcurrentHashMap<>();

    /**
     * Store the full prompt texts sent to the model (about two thirds of a run's stored data). The working
     * files and the model's answers are always stored; the prompts can be rebuilt from them and the release.
     */
    @Value("${prompt1.run-files.store-prompts:true}")
    private boolean storePrompts = true;

    public Prompt1RunFiles(Prompt1RunFileRepository repository) {
        this.repository = repository;
    }

    /** Starts holding the run's working files in memory (loads any already stored, e.g. after a restart). */
    public void open(String runId) {
        active.put(runId, new ConcurrentHashMap<>(loadWorkingFiles(runId)));
    }

    /** Stops holding the run's files in memory (the database keeps them). */
    public void close(String runId) {
        active.remove(runId);
    }

    /** The lock for one run's writes: its memory copy while active, otherwise this service. */
    private Object lockFor(String runId) {
        Map<String, String> cache = active.get(runId);
        return cache != null ? cache : this;
    }

    private Map<String, String> cached(String runId, String name) {
        return name.startsWith(PROMPTS) ? null : active.get(runId);
    }

    private Map<String, String> loadWorkingFiles(String runId) {
        Map<String, String> files = new LinkedHashMap<>();
        repository.findByRunIdAndNameNotLikeOrderByNameAsc(runId, PROMPTS + "%")
                .forEach(file -> files.put(file.getName(), file.getContent()));
        return files;
    }

    public Optional<String> read(String runId, String name) {
        Map<String, String> cache = cached(runId, name);
        if (cache != null) {
            return Optional.ofNullable(cache.get(name));
        }
        return repository.findByRunIdAndName(runId, name).map(file -> file.getContent());
    }

    /** The file's text; fails when the run has no such file (a turn cannot run without its input). */
    public String require(String runId, String name) {
        return read(runId, name).orElseThrow(() -> new IllegalStateException(
                "Working file " + name + " is missing in run " + runId));
    }

    public boolean has(String runId, String name) {
        return read(runId, name).isPresent();
    }

    /** All working files of the run (not the stored prompts and answers), by name: name → content. */
    public Map<String, String> workingFiles(String runId) {
        Map<String, String> cache = active.get(runId);
        return cache != null ? new TreeMap<>(cache) : loadWorkingFiles(runId);
    }

    /** File names of the run, alphabetical (no file content is loaded). */
    public List<String> list(String runId) {
        return repository.findNamesOfRun(runId);
    }

    @Transactional
    public void write(String runId, String name, String content) {
        synchronized (lockFor(runId)) {
            Prompt1RunFileEntity file = repository.findByRunIdAndName(runId, name)
                    .orElseGet(() -> Prompt1RunFileEntity.builder().runId(runId).name(name).build());
            file.setContent(content);
            file.setUpdatedAt(LocalDateTime.now());
            repository.save(file);
            Map<String, String> cache = cached(runId, name);
            if (cache != null) {
                cache.put(name, content);
            }
        }
    }

    /** A prompt sent to the model, stored as {@code prompts/<fileName>} only when store-prompts is on. */
    public void writePrompt(String runId, String fileName, String content) {
        if (storePrompts) {
            write(runId, PROMPTS + fileName, content);
        }
    }

    /** A model answer (or a refused, cut-off or repair answer), always stored as {@code prompts/<fileName>}. */
    public void writeAnswer(String runId, String fileName, String content) {
        write(runId, PROMPTS + fileName, content);
    }

    @Transactional
    public void append(String runId, String name, String content) {
        synchronized (lockFor(runId)) {
            write(runId, name, read(runId, name).orElse("") + content);
        }
    }

    /**
     * Marks the given steps closed in {@code tasks.tsv}, each with the next sequence number, in the
     * given order; the file starts with every step "not_started" (Jan's {@code apply_close_steps}).
     */
    @Transactional
    public void closeSteps(String runId, String steps) {
        List<String> wanted = new ArrayList<>();
        for (String step : steps.replace(';', ',').split(",")) {
            if (!step.isBlank()) {
                wanted.add(step.strip().toLowerCase(Locale.ROOT));
            }
        }
        if (wanted.isEmpty()) {
            return;
        }
        List<String> unknown = wanted.stream().filter(step -> !TASK_STEPS.contains(step)).toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown step(s) to close: " + unknown);
        }
        synchronized (lockFor(runId)) {
            List<Map<String, String>> rows = Prompt1Tsv.rows(read(runId, TASKS).orElse(null));
            if (rows.isEmpty()) {
                for (int i = 0; i < TASK_STEPS.size(); i++) {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("task_id", String.format("T%02d", i));
                    row.put("step", TASK_STEPS.get(i));
                    row.put("status", "not_started");
                    row.put("seq", Prompt1Tsv.EMPTY);
                    rows.add(row);
                }
            }
            int next = rows.stream().map(row -> row.getOrDefault("seq", "").strip())
                    .filter(seq -> seq.matches("\\d+")).mapToInt(seq -> Integer.parseInt(seq)).max().orElse(0) + 1;
            for (String step : wanted) {
                Map<String, String> row = rows.stream()
                        .filter(candidate -> step.equals(candidate.getOrDefault("step", "").strip().toLowerCase(Locale.ROOT)))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Step " + step + " is not in tasks.tsv"));
                if (!"closed".equals(row.get("status"))) {
                    row.put("status", "closed");
                    row.put("seq", String.valueOf(next++));
                }
            }
            List<String> columns = new ArrayList<>(rows.get(0).keySet());
            List<List<String>> lines = new ArrayList<>();
            for (Map<String, String> row : rows) {
                lines.add(columns.stream().map(column -> Prompt1Tsv.cell(row.get(column))).toList());
            }
            write(runId, TASKS, Prompt1Tsv.text(String.join("\t", columns), lines));
        }
    }

    /** One read of a source key for a step: the query, the document file read, and the key found (or an error). */
    public record Retrieval(String query, String sourceDoc, String chunkIds) {
    }

    /** One row in {@code retrieval_log.tsv} for a source key the backend read for a step. */
    public void logRetrieval(String runId, String step, String query, String sourceDoc, String chunkIds) {
        logRetrievals(runId, step, List.of(new Retrieval(query, sourceDoc, chunkIds)));
    }

    /** The rows of one batch of key reads, appended to {@code retrieval_log.tsv} with one read and one write. */
    @Transactional
    public void logRetrievals(String runId, String step, List<Retrieval> reads) {
        if (reads.isEmpty()) {
            return;
        }
        synchronized (lockFor(runId)) {
            String existing = read(runId, RETRIEVAL_LOG).orElse(null);
            long seq = existing == null ? 1 : existing.lines().filter(line -> !line.isBlank()).count();
            StringBuilder rows = new StringBuilder(existing == null ? RETRIEVAL_LOG_HEADER + "\n" : existing);
            for (Retrieval r : reads) {
                rows.append(String.join("\t", String.valueOf(seq++), Prompt1Tsv.cell(step), "read_keys",
                        Prompt1Tsv.cell(r.query()), Prompt1Tsv.cell(r.sourceDoc()), Prompt1Tsv.cell(r.chunkIds()),
                        "initial", Prompt1Tsv.EMPTY)).append('\n');
            }
            write(runId, RETRIEVAL_LOG, rows.toString());
        }
    }
}

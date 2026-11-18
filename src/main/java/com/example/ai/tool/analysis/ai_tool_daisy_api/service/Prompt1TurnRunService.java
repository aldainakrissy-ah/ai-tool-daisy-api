package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1ExperimentResult;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1ExperimentResult.CallUsage;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1ExperimentService.IntakeFile;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1SourcePackageService.KeyText;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1TurnAnswers.FileWrite;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1TurnAnswers.Ingested;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1TurnAnswers.RefusedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs Prompt 1 the turn-based way (Jan's PRELOAD design, ported from {@code daisy_run.py}):
 * Step 0 inventory, then turns A1 (Steps 1–3), A2 (Step 4, then the backend's IE membership),
 * A3 (Step 5), B (Steps 6–8, with Steps 6–7 by the backend), one C per candidate HETA (Steps 9–10,
 * in parallel) and D (Steps 11–14 and the final JSON). Each turn is one call without tools: the
 * system message cut to the turn's sections as instructions, the turn message as input. Every
 * answer is checked; a refused answer is sent back in the same conversation for correction, at most
 * {@code max-tries - 1} times. All working files, prompts and answers are stored per run.
 */
@Slf4j
@Service
public class Prompt1TurnRunService {

    /** One model call; an implementation other than OpenAI replays recorded answers in tests. */
    public interface Model {
        Answer ask(String turn, String label, String instructions, String input, String previousResponseId);
    }

    /** A model answer with its response ID (for corrections) and usage. */
    public record Answer(String text, String responseId, CallUsage usage) {
    }

    /** A turn could not be completed; the run stops there. */
    public static class TurnFailedException extends RuntimeException {
        public TurnFailedException(String turn, String message) {
            super("Turn " + turn + ": " + message);
        }
    }

    /** The settings a turn-based run started now would use. */
    public record TurnSettings(String model, String effort, String release, String systemMessageFile) {
    }

    private static final Pattern FENCE_NAME = Pattern.compile("```\\s*([\\w.]+)");

    private final Prompt1OpenAiCaller openAiCaller;
    private final Prompt1SourcePackageService sourcePackageService;
    private final Prompt1RunFiles runFiles;

    @Value("${experiment.prompt1.turns.effort:A1=high,A2=high,rest=medium}")
    private String effort;

    @Value("${experiment.prompt1.turns.c-concurrency:5}")
    private int cConcurrency;

    @Value("${experiment.prompt1.turns.max-tries:3}")
    private int maxTries;

    @Value("${experiment.prompt1.model:gpt-5.6-luna}")
    private String model;

    /** Model per turn, e.g. "A1=gpt-6.1-terra,A2=gpt-6.1-terra,rest=gpt-6-luna"; empty = experiment.prompt1.model for every turn. */
    @Value("${experiment.prompt1.turns.model:}")
    private String turnModels;

    /** A named model-and-effort plan (quality, standard, economy); overrides the model and effort settings when set. */
    @Value("${experiment.prompt1.turns.preset:}")
    private String preset;

    /**
     * The model and reasoning effort per turn (A1, A2, A3, B, C, D) of a run. Presets put the strong
     * model and effort where the judgement matters most (A1, A2) and save on the lighter turns.
     */
    public record Plan(String name, String modelSpec, String effortSpec, Map<String, String> models,
                       Map<String, String> efforts) {
    }

    /** name → {model spec, effort spec}. */
    static final Map<String, String[]> PRESETS = Map.of(
            "quality", new String[]{"gpt-6.1-terra", "A1=xhigh,A2=xhigh,A3=medium,B=medium,C=medium,D=low"},
            "standard", new String[]{"gpt-6.1-terra", "A1=high,A2=high,rest=medium"},
            // A3 stays on the strong model: Step 5 makes the partial-fit choice (7.1); gpt-6-luna at low effort
            // returned an empty selection there (live run 48bb265c, 11 Nov 2026)
            "economy", new String[]{"A1=gpt-6.1-terra,A2=gpt-6.1-terra,A3=gpt-6.1-terra,rest=gpt-6-luna",
                    "A1=high,A2=high,A3=medium,B=medium,C=medium,D=low"});

    /** The clock for CURRENT_RUN_DATE (a replay test sets the date of the run it replays). */
    private java.time.Clock clock = java.time.Clock.systemDefaultZone();

    public Prompt1TurnRunService(Prompt1OpenAiCaller openAiCaller, Prompt1SourcePackageService sourcePackageService,
                                 Prompt1RunFiles runFiles) {
        this.openAiCaller = openAiCaller;
        this.sourcePackageService = sourcePackageService;
        this.runFiles = runFiles;
    }

    /**
     * Checks the intake at submit time, so a bad request is refused at once instead of failing later in the
     * background: the files parse into the Step 0 inventory with an intake questionnaire and at least one
     * filled answer (IllegalArgumentException when not, so no paid call is made for an empty intake). The
     * release files are checked when the release is read ({@link Prompt1ReleaseService}).
     */
    public void checkReady(List<IntakeFile> files) {
        Prompt1TurnInventory.requireClientData(Prompt1TurnInventory.build(files));
    }

    /**
     * @param requestedPreset a preset for this run (quality, standard, economy), or null for the configured plan
     * @param release         the release the run uses
     */
    public TurnSettings settings(String requestedPreset, Prompt1Release release) {
        Plan plan = plan(requestedPreset);
        return new TurnSettings(plan.modelSpec(), plan.effortSpec(),
                plan.name() == null ? release.name() : release.name() + ", preset " + plan.name(),
                release.systemMessageFile());
    }

    /** The plan of a run: the requested preset, otherwise the configured preset, otherwise the model and effort settings. */
    Plan plan(String requestedPreset) {
        String name = requestedPreset != null && !requestedPreset.isBlank() ? requestedPreset.strip().toLowerCase(Locale.ROOT)
                : preset == null || preset.isBlank() ? null : preset.strip().toLowerCase(Locale.ROOT);
        String modelSpec;
        String effortSpec;
        if (name != null) {
            String[] chosen = PRESETS.get(name);
            if (chosen == null) {
                throw new IllegalArgumentException("Unknown preset '" + name + "'; expected one of " + new TreeSet<>(PRESETS.keySet()));
            }
            modelSpec = chosen[0];
            effortSpec = chosen[1];
        } else {
            modelSpec = turnModels == null || turnModels.isBlank() ? model : turnModels.strip();
            effortSpec = effort;
        }
        return new Plan(name, modelSpec, effortSpec, modelPlan(modelSpec), effortPlan(effortSpec));
    }

    /**
     * Runs every turn for the client files on OpenAI; a failed turn ends the run with the error in the result.
     *
     * @param release         the release of the run (its files and document versions)
     * @param requestedPreset a preset for this run, or null for the configured plan
     * @param onPhase         told when each phase starts (A1, A2, A3, B, C, D), e.g. to show a run's progress; a
     *                        failing listener does not stop the run
     */
    public Prompt1ExperimentResult run(String runId, List<IntakeFile> files, Prompt1Release release,
                                       String requestedPreset, Consumer<String> onPhase) {
        Plan plan = plan(requestedPreset);
        return run(runId, files, release, (turn, label, instructions, input, previous) -> {
            String key = Prompt1TurnConfig.tableKey(turn);
            String turnModel = plan.models().get(key);
            return openAiCaller.ask(turn, label, instructions, input, previous,
                    turnModel == null || turnModel.isBlank() ? model : turnModel, plan.efforts().get(key));
        }, plan, onPhase);
    }

    Prompt1ExperimentResult run(String runId, List<IntakeFile> files, Prompt1Release release, Model ask) {
        return run(runId, files, release, ask, plan(null), phase -> { });
    }

    Prompt1ExperimentResult run(String runId, List<IntakeFile> files, Prompt1Release release, Model ask, Plan plan,
                                Consumer<String> onPhase) {
        Prompt1TurnConfig config = release.config();
        Map<String, String> documentVersions = release.documentVersions();
        Prompt1ExperimentResult result = Prompt1ExperimentResult.builder()
                .model(plan.modelSpec())
                .reasoningEffort(plan.effortSpec())
                .packageProfile("TURNS " + release.name() + (plan.name() == null ? "" : ", preset " + plan.name()))
                .systemMessageFile(release.systemMessageFile())
                .systemMessageHash(Hashes.sha256(release.systemMessage()))
                .schemaName(config.schemaLabel())
                .schemaHash(Hashes.sha256(release.schemaText()))
                .documentVersions(documentVersions)
                .build();
        Map<String, List<String>> matrix = release.b2Matrix();
        Run run = new Run(runId, release, ask, result, onPhase);
        runFiles.open(runId);  // the working files are read many times per turn: keep them in memory during the run
        try {
            run.execute(files, matrix);
        } catch (TurnFailedException e) {
            log.warn("Prompt 1 turn run {} stopped: {}", runId, e.getMessage());
            result.setError(e.getMessage());
        } catch (RuntimeException e) {
            // an unexpected error must not lose the calls, tokens and cost already spent
            log.error("Prompt 1 turn run {} failed unexpectedly", runId, e);
            result.setError("Unexpected error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            runFiles.close(runId);
        }
        result.setTotal(Prompt1ExperimentService.total(result.getCalls()));
        return result;
    }

    /** The state of one turn-based run. */
    private final class Run {
        private final String runId;
        private final Prompt1TurnConfig config;
        private final String systemMessage;
        private final String doc18;
        /** The JSON Schema output.json must conform to (the release's production schema). */
        private final com.fasterxml.jackson.databind.JsonNode productionSchema;
        private final Model ask;
        /** The document versions this run reads, selected once at its start (a knowledge reload does not change them). */
        private final Map<String, Prompt1SourcePackageService.DocumentSections> documents;
        private final Prompt1ExperimentResult result;
        private final Prompt1TurnMessages.Sources sources;
        private final Consumer<String> onPhase;
        private List<IntakeFile> intake = List.of();

        /** The SM cut per turn type (A1, A2, A3, C): the same for the turn, its corrections, repair and continuations. */
        private final Map<String, String> smCuts = new java.util.concurrent.ConcurrentHashMap<>();

        private String instructionsFor(String turn) {
            return smCuts.computeIfAbsent(Prompt1TurnConfig.tableKey(turn),
                    key -> Prompt1TurnSystemMessage.forTurn(systemMessage, turn, config));
        }

        /** The run check (Jan's validate_run.py) after these turns, up to this step (Jan's CHECK). */
        private static final Map<String, Integer> CHECK = Map.of("A1", 3, "A2", 4, "A3", 5, "B", 8, "D", 14);
        /** The check of the turn before; its problems are not this turn's to correct (Jan's PREV). */
        private static final Map<String, Integer> PREV = Map.of("A2", 3, "A3", 4, "B", 5, "D", 10);

        /** The run check up to a step (null = the whole run), with source keys read without logging. */
        private Prompt1RunCheck.Result runCheck(Integer stop) {
            return Prompt1RunCheck.check(config, files(), stop, intake, productionSchema,
                    key -> Prompt1SourcePackageService.readKeys(documents, List.of(key)).get(0).text());
        }

        /** The problems already present before this turn (the check of the turn before), or null. */
        private Prompt1RunCheck.Result baseline(String turn) {
            return PREV.containsKey(turn) ? runCheck(PREV.get(turn)) : null;
        }

        /** The problems this turn added, numbered as Jan's runner sends them ("" = fine); warnings go to the notes. */
        private String checkTurn(String turn, Prompt1RunCheck.Result before) {
            if (!CHECK.containsKey(turn)) {
                return "";
            }
            Prompt1RunCheck.Result after = runCheck(CHECK.get(turn));
            if (!after.warnings().isEmpty()) {
                String warnings = String.join("; ", after.warnings());
                result.getNotes().add(turn + ": check up to step " + CHECK.get(turn) + ", " + after.warnings().size()
                        + " warning(s): " + (warnings.length() > 600 ? warnings.substring(0, 600) + " …" : warnings));
            }
            List<String> added = Prompt1RunCheck.newProblems(before, after);
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < added.size(); i++) {
                out.append(i == 0 ? "" : "\n").append(i + 1).append(". ").append(added.get(i));
            }
            String text = out.toString();
            return text.length() > 6000 ? text.substring(text.length() - 6000) : text;
        }

        Run(String runId, Prompt1Release release, Model ask, Prompt1ExperimentResult result, Consumer<String> onPhase) {
            this.runId = runId;
            this.onPhase = onPhase;
            this.config = release.config();
            this.systemMessage = release.systemMessage();
            this.doc18 = release.doc18();
            this.productionSchema = release.productionSchema();
            this.ask = ask;
            this.documents = sourcePackageService.documents(release.documentVersions());  // fixed for the whole run
            this.result = result;
            this.sources = new Prompt1TurnMessages.Sources() {
                @Override
                public List<KeyText> keys(String step, List<String> keys) {
                    List<KeyText> texts = Prompt1SourcePackageService.readKeys(documents, keys);
                    runFiles.logRetrievals(runId, step, texts.stream().map(key -> retrieval(key)).toList());
                    return texts;
                }

                @Override
                public KeyText r4Lookup(String step) {
                    KeyText lookup = Prompt1SourcePackageService.r4Lookup(documents);
                    runFiles.logRetrieval(runId, step, "section=" + lookup.key().substring("R4:".length()),
                            lookup.fileName(), lookup.found() ? lookup.key() : "ERROR: not found");
                    return lookup;
                }
            };
        }

        /** Tells the listener that a phase starts; its failure is logged, never the run's. */
        private void phase(String phase) {
            try {
                onPhase.accept(phase);
            } catch (RuntimeException e) {
                log.warn("Prompt 1 run {}: could not record phase {}: {}", runId, phase, e.getMessage());
            }
        }

        /** The retrieval_log.tsv row of one key read. */
        private Prompt1RunFiles.Retrieval retrieval(KeyText key) {
            return new Prompt1RunFiles.Retrieval("KEY: " + key.key(), key.fileName(), key.found() ? key.key() : "ERROR: not found");
        }

        void execute(List<IntakeFile> files, Map<String, List<String>> matrix) {
            intake = files;
            Prompt1TurnInventory.Inventory inventory = Prompt1TurnInventory.build(files);
            runFiles.write(runId, "manifest.tsv", inventory.manifest());
            runFiles.write(runId, "observations.tsv", inventory.observations());
            runFiles.write(runId, "observation_audit.tsv", inventory.audit());
            String date = LocalDate.now(clock).toString();

            turn("A1", () -> Prompt1TurnMessages.a1(config, runId, date, inventory.manifest(), inventory.observations(),
                    sources.keys("1", Prompt1TurnMessages.A1_STEP1_KEYS), sources.keys("2", Prompt1TurnMessages.A1_STEP2_KEYS),
                    doc18Part("A1")), answer -> Prompt1TurnAnswers.ingest(config, "A1", answer, files()));

            turn("A2", () -> Prompt1TurnMessages.a2(config, runId, files(), sources, doc18Part("A2")), answer -> {
                Ingested ingested = Prompt1TurnAnswers.ingest(config, "A2", answer, files());
                Map<String, String> after = files();
                ingested.files().forEach(write -> after.put(write.name(), write.content()));
                Map<String, String> elem = new HashMap<>();
                // the server rule reads R5:ELEM itself (already logged with the A2 message)
                for (KeyText key : Prompt1SourcePackageService.readKeys(documents, config.ies().stream().map(ie -> "R5:ELEM:" + ie).toList())) {
                    elem.put(key.key().substring("R5:ELEM:".length()), key.found() ? key.text() : "");
                }
                Prompt1TurnRules.Membership membership = Prompt1TurnRules.deriveIeCandidates(config.ies(), elem,
                        after.get("ie_core_filter.tsv"), after.get("ie_ftf_cells.tsv"), after.get("cts_admission.tsv"),
                        after.get("priority.tsv"));
                if (!membership.complete()) {
                    throw Prompt1TurnAnswers.membershipMissing(membership.missing());
                }
                List<FileWrite> writes = new ArrayList<>(ingested.files());
                writes.add(new FileWrite("ie_membership.tsv", membership.membershipTsv(), false));
                writes.add(new FileWrite("a1_ie_candidates.tsv", membership.candidatesTsv(), false));
                return new Ingested(writes, ingested.closeSteps(), ingested.notes());
            });

            turn("A3", () -> Prompt1TurnMessages.a3(config, runId, files(), sources, doc18Part("A3")),
                    answer -> Prompt1TurnAnswers.ingest(config, "A3", answer, files()));

            Prompt1TurnRules.Selection selection;
            List<String> hetas;
            phase("B");  // B starts here: the selection and the A3 candidates are its first backend rules
            try {
                selection = Prompt1TurnRules.selection(runFiles.require(runId, "a1_selection.tsv"));
                KeyText index = sources.keys("7", List.of("R4:INDEX:" + selection.primary())).get(0);
                hetas = Prompt1TurnRules.a3Candidates(selection.primary(), index.found() ? index.text() : "");
            } catch (IllegalStateException e) {
                throw new TurnFailedException("B", e.getMessage());
            }
            String primary = selection.primary();
            result.setPrimaryIeRef(primary);
            // Step 7 (a fixed rule): the A3 candidate set. Step 6 (A2) is removed since SM v0.138 (backend v07, D14).
            stepsSevenAndEightByBackend(selection,
                    Prompt1TurnRules.a3CandidatesTsv(config.headers().get("a3_candidates.tsv"), primary, hetas));

            runCTurns(hetas);

            stepDByBackend(matrix, date);

            Prompt1RunCheck.Result last = runCheck(null);
            result.getNotes().add("check of the whole run: " + (last.problems().isEmpty() ? "compleet"
                    : "onvolledig, " + last.problems().size() + " punt(en): " + String.join("; ", last.problems()))
                    + (last.warnings().isEmpty() ? "" : "; " + last.warnings().size() + " waarschuwing(en)"));
        }

        /**
         * Turn D without a model call (Jan's v06): b2_needs.tsv and b2_coverage.tsv from the closed C
         * files and the B2 matrix, then the D ingest numbers the GAPs and assembles the final JSON.
         */
        private void stepDByBackend(Map<String, List<String>> matrix, String date) {
            phase("D");
            Prompt1RunCheck.Result before = baseline("D");
            Map<String, String> files = files();
            Prompt1TurnOutput.B2 b2;
            Ingested ingested;
            try {
                b2 = Prompt1TurnOutput.b2FromMatrix(config, files.get("a3_decisions.tsv"), files.get("a3_candidates.tsv"),
                        files.get("heta_canon.tsv"), files.get("heta_cells.tsv"), matrix);
                runFiles.writeAnswer(runId, "antwoord_D.txt", b2.answer());
                Map<String, String> keyTexts = new HashMap<>();
                // the key reads of D, logged afterwards in one write per step (same rows, same order, as one write per read)
                List<Map.Entry<String, Prompt1RunFiles.Retrieval>> reads = new ArrayList<>();
                try {
                    ingested = Prompt1TurnAnswers.ingestD(config, b2.answer(), files, runId, date, key -> {
                        if (!keyTexts.containsKey(key)) {
                            String step = key.startsWith("07:B1") ? "12" : key.startsWith("07:B2") ? "13" : "14";
                            KeyText text = Prompt1SourcePackageService.readKeys(documents, List.of(key)).get(0);
                            reads.add(Map.entry(step, retrieval(text)));
                            keyTexts.put(key, text.found() ? text.text() : null);
                        }
                        return keyTexts.get(key);
                    }, productionSchema);
                } finally {
                    for (int i = 0; i < reads.size(); ) {
                        String step = reads.get(i).getKey();
                        List<Prompt1RunFiles.Retrieval> batch = new ArrayList<>();
                        for (; i < reads.size() && reads.get(i).getKey().equals(step); i++) {
                            batch.add(reads.get(i).getValue());
                        }
                        runFiles.logRetrievals(runId, step, batch);
                    }
                }
            } catch (RuntimeException e) {
                throw new TurnFailedException("D", e.getMessage());
            }
            for (FileWrite write : ingested.files()) {
                runFiles.write(runId, write.name(), write.content());
            }
            runFiles.closeSteps(runId, ingested.closeSteps());
            b2.notes().forEach(note -> result.getNotes().add("D: " + note));
            ingested.notes().forEach(note -> result.getNotes().add("D: " + note));
            result.setResult(ingested.output());
            result.setProductionResult(ingested.production());
            log.info("Prompt 1 turn run {}: D by the backend, {} need(s), run_status {}", runId,
                    ingested.output().path("b2").path("needs").size(), ingested.output().path("run_status").asText());
            stopOnBackendProblems("D", before);
        }

        /** The run check after a backend step; there is no model to correct a backend step, so problems stop the run. */
        private void stopOnBackendProblems(String turn, Prompt1RunCheck.Result before) {
            String problems = checkTurn(turn, before);
            if (!problems.isEmpty()) {
                throw new TurnFailedException(turn, "the run check finds these problems after the backend step:\n" + problems);
            }
        }

        /**
         * Turn B without a model call (Jan's backend rules for Steps 7 and 8; Step 6 is removed since v07): the
         * candidate set and the evidence basis are written by the backend, with the empty Step 9–10 files.
         */
        private void stepsSevenAndEightByBackend(Prompt1TurnRules.Selection selection, String a3Candidates) {
            Prompt1RunCheck.Result before = baseline("B");
            Map<String, String> files = files();
            String basis;
            List<String> skipped = new ArrayList<>();
            try {
                basis = Prompt1TurnRules.evidenceBasis(config.headers().get("a3_evidence_basis.tsv"), selection,
                        files.get("a1_ie_candidates.tsv"), files.get("ie_core_filter.tsv"), files.get("priority.tsv"),
                        files.get("directions.tsv"), files.get("links.tsv"),
                        files.get("ie_membership.tsv"), files.get("meanings.tsv"), skipped);
                if (!skipped.isEmpty()) {
                    result.getNotes().add("B: context zonder vastgestelde verbinding (geen link, andere observatie) niet in "
                            + "de bewijsbasis: " + String.join(", ", skipped));
                }
            } catch (RuntimeException e) {
                throw new TurnFailedException("B", "Step 8 rule failed: " + e.getMessage());
            }
            runFiles.write(runId, "a3_candidates.tsv", a3Candidates);
            runFiles.write(runId, "a3_evidence_basis.tsv", basis);
            for (String name : config.cFiles()) {
                if (!files.containsKey(name)) {
                    runFiles.write(runId, name, config.headers().get(name) + "\n");
                }
            }
            runFiles.closeSteps(runId, config.turns().get("B").closeSteps());
            stopOnBackendProblems("B", before);
            int rows = Prompt1Tsv.rows(basis).size();
            result.getNotes().add("B: Steps 7–8 by the backend (no model call; Step 6 removed since v07); evidence basis "
                    + rows + " meanings");
            log.info("Prompt 1 turn run {}: B by the backend, evidence basis {} meanings", runId, rows);
        }

        /** All C prompts first (their key reads are logged in order), then the calls in parallel, then ingest in order. */
        private void runCTurns(List<String> hetas) {
            phase("C");
            Map<String, String> prompts = new LinkedHashMap<>();
            Map<String, List<String>> signals = new HashMap<>();
            Map<String, String> names = new HashMap<>();
            for (String heta : hetas) {
                String prompt = Prompt1TurnMessages.c(config, runId, heta, files(), sources);
                prompts.put(heta, prompt);
                runFiles.writePrompt(runId, "C_" + heta + ".txt", prompt);
                // the server rules read R3 themselves (no retrieval log): the signal list and the HETA name (12.4)
                List<KeyText> r3 = Prompt1SourcePackageService.readKeys(documents, List.of("R3:" + heta + ".5.2", "R3:" + heta + ".2"));
                signals.put(heta, r3.get(0).found() ? Prompt1TurnRules.r3Signals(r3.get(0).content()) : List.of());
                names.put(heta, Prompt1TurnOutput.r3HetaName(heta, r3.get(1).text()));
            }
            String instructions = instructionsFor("C");
            ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Math.min(cConcurrency, hetas.size())), task -> {
                Thread thread = new Thread(task, "prompt1-c-turn");
                thread.setDaemon(true);
                return thread;
            });
            Map<String, Future<Answer>> calls = new LinkedHashMap<>();
            try {
                for (String heta : hetas) {
                    String turn = "C_" + heta;
                    calls.put(heta, pool.submit(() -> ask.ask(turn, turn, instructions, prompts.get(heta), null)));
                }
                for (String heta : hetas) {
                    Answer answer;
                    try {
                        answer = calls.get(heta).get();
                    } catch (ExecutionException e) {
                        Throwable cause = e.getCause();
                        throw cause instanceof TurnFailedException failed ? failed
                                : new TurnFailedException("C_" + heta, "call failed: " + cause.getMessage());
                    }
                    result.getCalls().add(answer.usage());
                    String turn = "C_" + heta;
                    ingestWithCorrections(turn, prompts.get(heta), answer,
                            text -> Prompt1TurnAnswers.ingestC(config, heta, text, files(), signals.get(heta), names.get(heta)));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TurnFailedException("C", "interrupted");
            } finally {
                pool.shutdownNow();
            }
        }

        private void turn(String turn, java.util.function.Supplier<String> message, Function<String, Ingested> ingest) {
            phase(turn);
            String prompt;
            try {
                prompt = message.get();
            } catch (IllegalStateException e) {
                throw new TurnFailedException(turn, e.getMessage());
            }
            runFiles.writePrompt(runId, turn + ".txt", prompt);
            String instructions = instructionsFor(turn);
            Answer answer = ask.ask(turn, turn, instructions, prompt, null);
            result.getCalls().add(answer.usage());
            ingestWithCorrections(turn, prompt, answer, ingest);
        }

        /**
         * Ingests an answer; a refused answer goes back to the model with the reason (same
         * conversation) and the corrected blocks replace the earlier ones (Jan's ingest loop).
         */
        private void ingestWithCorrections(String turn, String prompt, Answer first, Function<String, Ingested> ingest) {
            String text = first.text();
            String responseId = first.responseId();
            boolean repaired = false;
            Prompt1RunCheck.Result before = baseline(turn);
            for (int attempt = 1; attempt <= maxTries; attempt++) {
                Answer completed = complete(turn, text, responseId, attempt);
                text = completed.text();
                responseId = completed.responseId();
                try {
                    Ingested ingested = ingest.apply(text);
                    for (FileWrite write : ingested.files()) {
                        if (write.append()) {
                            runFiles.append(runId, write.name(), write.content());
                        } else {
                            runFiles.write(runId, write.name(), write.content());
                        }
                    }
                    if (!ingested.closeSteps().isEmpty()) {
                        runFiles.closeSteps(runId, ingested.closeSteps());
                    }
                    runFiles.writeAnswer(runId, "antwoord_" + turn + ".txt", text);
                    ingested.notes().forEach(note -> result.getNotes().add(turn + ": " + note));
                    if (ingested.output() != null) {
                        result.setResult(ingested.output());
                        result.setProductionResult(ingested.production());
                        CallUsage last = result.getCalls().get(result.getCalls().size() - 1);
                        last.setRunStatus(ingested.output().path("run_status").asText(null));
                        last.setCompletedThroughStep(ingested.output().path("completed_through_step").isInt()
                                ? ingested.output().path("completed_through_step").asInt() : null);
                    }
                    String problems = checkTurn(turn, before);  // Jan's validate_run.py --stop N after this turn
                    if (!problems.isEmpty()) {
                        throw new RefusedException("the run check (validate_run.py) found these problems in your files:\n" + problems);
                    }
                    log.info("Prompt 1 turn run {}: {} accepted (try {}), {} file(s)", runId, turn, attempt,
                            ingested.files().size());
                    return;
                } catch (RefusedException e) {
                    String reason = e.getMessage().length() > 6000 ? e.getMessage().substring(0, 6000) : e.getMessage();
                    runFiles.writeAnswer(runId, "antwoord_" + turn + "_poging" + attempt + ".txt", text);  // keep the refused answer
                    if ((turn.equals("A1") || turn.equals("A2")) && !repaired && attempt < maxTries && responseId != null) {
                        repaired = true;  // v05: first a small repair call; if that does not help, a full correction follows
                        String merged = repair(turn, prompt, text, reason, attempt);
                        if (merged != null) {
                            text = merged;
                            continue;
                        }
                    }
                    if (attempt == maxTries || responseId == null) {
                        throw new TurnFailedException(turn, "answer refused after " + attempt + " tries: " + reason);
                    }
                    log.info("Prompt 1 turn run {}: {} refused (try {}), asking for a correction", runId, turn, attempt);
                    result.getNotes().add(turn + ": refused (try " + attempt + "): "
                            + (reason.length() > 500 ? reason.substring(0, 500) + " …" : reason));
                    Map<String, String> previous = Prompt1TurnAnswers.blocks(text);
                    Answer correction = ask.ask(turn, turn + " correction " + attempt,
                            instructionsFor(turn),
                            config.correction().replace("{error}", reason), responseId);
                    result.getCalls().add(correction.usage());
                    Answer done = complete(turn, correction.text(), correction.responseId(), attempt + 1);
                    responseId = done.responseId();
                    Map<String, String> corrected = Prompt1TurnAnswers.blocks(done.text());
                    if (corrected.isEmpty()) {
                        text = done.text();
                    } else {
                        Map<String, String> merged = new LinkedHashMap<>(previous);
                        merged.putAll(corrected);
                        List<String> blocks = new ArrayList<>();
                        merged.forEach((name, body) -> blocks.add("```" + name + "\n" + Prompt1Tsv.rstrip(body, "\n") + "\n```"));
                        text = String.join("\n\n", blocks);
                        result.getNotes().add(turn + ": correction " + attempt + " gave " + String.join(", ", corrected.keySet()));
                    }
                }
            }
        }

        /**
         * v05 (Jan's D8): the refused answer is repaired in a small new call with the same SM cut: only the
         * problems and the rows they concern. Returns the merged answer, or null when the repair gave no
         * usable rows (the full correction follows).
         */
        private String repair(String turn, String turnPrompt, String text, String problems, int attempt) {
            log.info("Prompt 1 turn run {}: {} refused (try {}), asking for a small repair", runId, turn, attempt);
            result.getNotes().add(turn + ": refused (try " + attempt + "), repair turn: "
                    + (problems.length() > 500 ? problems.substring(0, 500) + " …" : problems));
            String prompt = Prompt1TurnMessages.repair(config, turn, runId, problems, text, turnPrompt);
            runFiles.writePrompt(runId, turn + "_repair" + attempt + ".txt", prompt);
            Answer patch = ask.ask(turn, turn + " repair " + attempt, instructionsFor(turn),
                    prompt, null);
            result.getCalls().add(patch.usage());
            runFiles.writeAnswer(runId, "antwoord_" + turn + "_repair" + attempt + ".txt", patch.text());
            Prompt1TurnAnswers.Merged merged = Prompt1TurnAnswers.mergePatch(text, patch.text());
            if (merged.notes().isEmpty()) {
                result.getNotes().add(turn + ": repair turn gave no usable rows; full correction follows");
                return null;
            }
            result.getNotes().add(turn + ": repair applied: " + String.join("; ", merged.notes()));
            return merged.answer();
        }

        /** An answer that stops inside a block (odd number of fences) is continued in the same conversation, at most twice. */
        private Answer complete(String turn, String text, String responseId, int attempt) {
            for (int k = 1; k <= 2; k++) {
                if (countFences(text) % 2 == 0 || responseId == null) {
                    break;
                }
                String body = text.contains("\n") ? text.substring(0, text.lastIndexOf('\n')) : "";
                Matcher names = FENCE_NAME.matcher(body);
                String name = "the first block";
                while (names.find()) {
                    name = names.group(1);
                }
                List<String> lines = body.stripTrailing().lines().toList();
                String last = lines.isEmpty() ? "" : lines.get(lines.size() - 1);
                log.info("Prompt 1 turn run {}: {} stopped inside {}; asking to continue ({})", runId, turn, name, k);
                runFiles.writeAnswer(runId, "antwoord_" + turn + "_afgebroken" + attempt + "_" + k + ".txt", text);
                Answer more = ask.ask(turn, turn + " continuation " + k,
                        instructionsFor(turn),
                        config.continuation().replace("{name}", name).replace("{line}", last), responseId);
                result.getCalls().add(more.usage());
                responseId = more.responseId();
                String rest = more.text().replaceFirst("^\n+", "");
                String start = "```" + name;
                text = rest.startsWith(start) && body.contains(start)
                        ? body.substring(0, body.lastIndexOf(start)) + rest
                        : Prompt1Tsv.rstrip(body, "\n") + "\n" + rest;
            }
            return new Answer(text, responseId, null);
        }

        private Map<String, String> files() {
            return runFiles.workingFiles(runId);
        }

        private List<String> doc18Part(String turn) {
            return Prompt1TurnSystemMessage.doc18Part(doc18, turn, config);
        }
    }

    /** "high" for every turn, or per turn: "A1=high,A2=high,rest=medium" (turns A1, A2, A3, B, C, D). */
    static Map<String, String> effortPlan(String spec) {
        Map<String, String> plan = turnPlan(spec, "effort");
        plan.values().stream().filter(value -> !value.isEmpty())
                .forEach(value -> Prompt1ExperimentService.parseReasoningEffort(value));
        return plan;
    }

    /** One model for every turn, or per turn: "A1=gpt-6.1-terra,A2=gpt-6.1-terra,rest=gpt-6-luna". */
    static Map<String, String> modelPlan(String spec) {
        Map<String, String> plan = turnPlan(spec, "model");
        if (plan.values().stream().anyMatch(value -> value.isEmpty())) {
            throw new IllegalArgumentException("Turn model '" + spec + "' leaves a turn without a model; add rest=<model>");
        }
        return plan;
    }

    /** A value per turn (A1, A2, A3, B, C, D): one value for all, or "A1=x,A2=y,rest=z". */
    static Map<String, String> turnPlan(String spec, String what) {
        List<String> turns = List.of("A1", "A2", "A3", "B", "C", "D");
        String value = spec == null ? "" : spec.strip();
        Map<String, String> plan = new LinkedHashMap<>();
        if (!value.contains("=")) {
            turns.forEach(turn -> plan.put(turn, value));
            return plan;
        }
        String rest = "";
        Map<String, String> given = new HashMap<>();
        for (String part : value.split(",")) {
            String[] kv = part.split("=", 2);
            String key = kv[0].strip();
            String entry = kv.length > 1 ? kv[1].strip() : "";
            if (entry.isEmpty() || (!turns.contains(key) && !key.equals("rest"))) {
                throw new IllegalArgumentException("Turn " + what + " '" + part.strip() + "' not understood; use e.g. "
                        + "A1=x,A2=y,rest=z (turns A1, A2, A3, B, C, D, rest)");
            }
            if (key.equals("rest")) {
                rest = entry;
            } else {
                given.put(key, entry);
            }
        }
        for (String turn : turns) {
            plan.put(turn, given.getOrDefault(turn, rest));
        }
        return plan;
    }

    private static int countFences(String text) {
        int count = 0;
        int i = text.indexOf("```");
        while (i >= 0) {
            count++;
            i = text.indexOf("```", i + 3);
        }
        return count;
    }

}

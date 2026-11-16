package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.Prompt1RunFileEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1ExperimentResult;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1ExperimentResult.CallUsage;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.Prompt1RunFileRepository;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1ExperimentService.IntakeFile;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1SourcePackageService.DocumentSections;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The whole turn-based run on Jan's run (pg_run31, release v06), with a model that answers each turn with Jan's
 * recorded answer: inventory, A1, A2 (+ membership), A3, B by the backend (Steps 6–8), ten C turns in parallel,
 * D by the backend (B2 matrix, final JSON). The run must give his working files and his final JSON. No OpenAI call; client
 * data is never printed.
 */
class Prompt1TurnRunServiceReplayTest {

    private Path sources;
    private Path replay;
    private Path intake;
    private Prompt1RunFiles runFiles;
    private Prompt1TurnRunService service;
    private Prompt1Release release;
    private final Map<String, Prompt1RunFileEntity> stored = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        sources = dir("KNOWLEDGE_MARKDOWN_DIR");
        replay = dir("PROMPT1_REPLAY_DIR");
        intake = dir("PROMPT1_INTAKE_DIR");
        Map<String, DocumentSections> documents = Prompt1SourcePackageService.selectVersions(
                Prompt1SourcePackageServiceTest.readLikeTheLoader(sources), Map.of());

        Prompt1SourcePackageService packages = mock(Prompt1SourcePackageService.class);
        when(packages.readKeys(anyList(), any())).thenAnswer(call -> Prompt1SourcePackageService.readKeys(documents, call.getArgument(0)));
        when(packages.documents(any())).thenReturn(documents);

        Prompt1RunFileRepository repository = mock(Prompt1RunFileRepository.class);
        when(repository.findByRunIdAndName(anyString(), anyString()))
                .thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0) + "/" + call.getArgument(1))));
        when(repository.save(any(Prompt1RunFileEntity.class))).thenAnswer(call -> {
            Prompt1RunFileEntity file = call.getArgument(0);
            stored.put(file.getRunId() + "/" + file.getName(), file);
            return file;
        });
        when(repository.findNamesOfRun(anyString())).thenAnswer(call -> stored.values().stream()
                .filter(file -> file.getRunId().equals(call.getArgument(0))).map(file -> file.getName()).sorted().toList());
        when(repository.findByRunIdOrderByNameAsc(anyString())).thenAnswer(call -> stored.values().stream()
                .filter(file -> file.getRunId().equals(call.getArgument(0)))
                .sorted(Comparator.comparing(file -> file.getName())).toList());
        when(repository.findByRunIdAndNameNotLikeOrderByNameAsc(anyString(), anyString())).thenAnswer(call -> stored.values().stream()
                .filter(file -> file.getRunId().equals(call.getArgument(0))
                        && !file.getName().startsWith(call.<String>getArgument(1).replace("%", "")))
                .sorted(Comparator.comparing(file -> file.getName())).toList());
        runFiles = new Prompt1RunFiles(repository);

        service = new Prompt1TurnRunService(null, packages, runFiles, new ObjectMapper());
        release = Prompt1Release.of(null, "P1-v0.136", Files.readAllBytes(Prompt1TestFiles.turnConfigFile()),
                Files.readString(sources.resolve("DAISY_Prompt_1_v0.136_compact.md")), "DAISY_Prompt_1_v0.136_compact.md",
                Files.readString(sources.resolve("18_werkbestanden_format.md")),
                Files.readString(sources.resolve("Structured_JSON_Prompt_1_v0.46d-lean (used by claude).json")),
                Files.readString(sources.resolve("07_B2_matrix.tsv")), Map.of());
        String runDate = new ObjectMapper().readTree(Files.readString(replay.resolve("output.json"))).path("current_run_date").asText();
        ReflectionTestUtils.setField(service, "clock", java.time.Clock.fixed(
                java.time.LocalDate.parse(runDate).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(), java.time.ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "effort", "A1=high,A2=high,rest=medium");
        ReflectionTestUtils.setField(service, "cConcurrency", 5);
        ReflectionTestUtils.setField(service, "maxTries", 3);
        ReflectionTestUtils.setField(service, "model", "replay");
    }

    /** Jan's recorded answer per turn; the instructions must be Jan's SM cut for that turn. */
    private Prompt1TurnRunService.Model recorded(List<String> labels, Map<String, String> overrides) {
        return (turn, label, instructions, input, previous) -> {
            labels.add(label);
            try {
                if (previous == null) {
                    assertEquals(Files.readString(replay.resolve("prompts/" + turn + ".sm.txt")), instructions, "SM cut " + turn);
                }
                String answer = previous == null && overrides.containsKey(turn) ? overrides.get(turn)
                        : Files.readString(replay.resolve("prompts/antwoord_" + turn + ".txt"));
                return new Prompt1TurnRunService.Answer(answer, "resp_" + label.replace(' ', '_'),
                        CallUsage.builder().call(label).inputTokens(input.length() / 4).build());
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        };
    }

    private List<IntakeFile> klaas() throws Exception {
        return List.of(file("Klaas_persoonlijke_gegevens zonder hulpvraag.json"), file("Klaas_intake_vragenlijst.json"));
    }

    @Test
    void theWholeRunReproducesJansRun() throws Exception {
        String run = replay.getFileName().toString();
        List<String> labels = new CopyOnWriteArrayList<>();
        Prompt1ExperimentResult result = service.run(run, klaas(), release, recorded(labels, Map.of()));

        assertNull(result.getError(), result.getError());
        assertEquals("valid", result.getResult().path("run_status").asText());
        assertEquals("22.7", result.getPrimaryIeRef());
        assertFalse(labels.contains("B") || labels.contains("D"), "turns B and D make no model call (v04z, v06)");
        assertEquals(13, labels.size(), "A1, A2, A3, 10 × C, without corrections");
        assertEquals(13, result.getCalls().size());

        List<String> compared = new ArrayList<>();
        try (var list = Files.list(replay)) {
            for (Path file : list.filter(path -> path.toString().endsWith(".tsv")).sorted().toList()) {
                String name = file.getFileName().toString();
                if (List.of("retrieval_log.tsv", "usage.tsv").contains(name)) {
                    continue;
                }
                sameText(name, Files.readString(file), runFiles.require(run, name));
                compared.add(name);
            }
        }
        assertEquals(new ObjectMapper().readTree(Files.readString(replay.resolve("output.json"))), result.getResult(),
                "output.json");
        sameText("antwoord_D.txt", Files.readString(replay.resolve("prompts/antwoord_D.txt")),
                runFiles.require(run, "prompts/antwoord_D.txt"));
        assertTrue(runFiles.has(run, "prompts/C_22.27.txt"));
        String finalCheck = result.getNotes().stream().filter(note -> note.startsWith("check of the whole run: ")).findFirst().orElseThrow();
        assertTrue(finalCheck.startsWith("check of the whole run: compleet"), finalCheck);
        System.out.println("run check (validate_run.py port): " + finalCheck.substring(0, Math.min(80, finalCheck.length())));
        result.getNotes().stream().filter(note -> note.contains("warning(s)")).forEach(note -> System.out.println("  " + note));
        long keyReads = runFiles.require(run, "retrieval_log.tsv").lines().count() - 1;
        System.out.printf("Replay through the runner: %d model calls, %d working files identical to Jan's, the backend's "
                + "D answer and output.json identical, %d key reads logged%n", labels.size(), compared.size(), keyReads);
    }

    @Test
    void aRefusedAnswerIsCorrectedInTheSameConversation() throws Exception {
        String broken = "```a1_selection.tsv\nstep\tie_ref\tfinding\n7.2D\t22.7\tprimary\n```";
        List<String> labels = new CopyOnWriteArrayList<>();
        Map<String, String> correctionInstructions = new ConcurrentHashMap<>();
        Prompt1TurnRunService.Model recorded = recorded(labels, Map.of("A3", broken));
        Prompt1ExperimentResult result = service.run("run2", klaas(), release, (turn, label, instructions, input, previous) -> {
            if (previous != null && instructions != null) {
                correctionInstructions.put(label, instructions);
            }
            return recorded.ask(turn, label, instructions, input, previous);
        });

        assertNull(result.getError(), result.getError());
        assertTrue(labels.contains("A3 correction 1"));
        // the Responses API does not carry instructions over from the previous response: the SM cut is sent again
        assertEquals(Files.readString(replay.resolve("prompts/A3.sm.txt")), correctionInstructions.get("A3 correction 1"));
        assertEquals(14, labels.size(), "13 calls and one correction");
        assertTrue(runFiles.has("run2", "prompts/antwoord_A3_poging1.txt"), "the refused answer is kept");
        assertTrue(result.getNotes().stream().anyMatch(note -> note.startsWith("A3: correction 1 gave a1_selection.tsv")));
    }

    /** v05: a refused A2 answer (one required cell row missing) is fixed by the small repair call, not a full correction. */
    @Test
    void aRefusedA2AnswerIsFixedByTheRepairTurn() throws Exception {
        String a2 = Files.readString(replay.resolve("prompts/antwoord_A2.txt"));
        String header = "seq\tie_ref\tftf_id\telement_id\tstatus\tmeaning_ids\tlink_ids\tmissing\tbasis\tcounterclaims";
        String removed = a2.lines().filter(line -> line.startsWith("2\t22.2\tFTF_017\t22.2-A1-Q1\t")).findFirst().orElseThrow();
        String broken = a2.replace(removed + "\n", "");
        List<String> labels = new CopyOnWriteArrayList<>();
        List<String> repairPrompts = new CopyOnWriteArrayList<>();
        Prompt1TurnRunService.Model model = (turn, label, instructions, input, previous) -> {
            if (label.equals("A2 repair 1")) {
                labels.add(label);
                repairPrompts.add(input);
                assertNull(previous, "a repair is a new call");
                return new Prompt1TurnRunService.Answer("```ie_ftf_cells.tsv\n" + header + "\n" + removed + "\n```",
                        "resp_repair", CallUsage.builder().call(label).build());
            }
            return recorded(labels, Map.of("A2", broken)).ask(turn, label, instructions, input, previous);
        };

        Prompt1ExperimentResult result = service.run("run5", klaas(), release, model);

        assertNull(result.getError(), result.getError());
        assertEquals("valid", result.getResult().path("run_status").asText());
        assertTrue(labels.contains("A2 repair 1"));
        assertFalse(labels.contains("A2 correction 1"), "no full correction needed");
        assertEquals(14, labels.size(), "13 calls and one repair");
        assertTrue(repairPrompts.get(0).startsWith("DAISY PROMPT 1 — REPAIR OF TURN A2 (PRELOAD)"));
        assertTrue(repairPrompts.get(0).contains("ie_ftf_cells.tsv: ie_ref, ftf_id, element_id"));
        assertTrue(result.getNotes().contains("A2: repair applied: ie_ftf_cells.tsv: 0 gewijzigd, 1 toegevoegd, 0 verwijderd"));
        assertTrue(runFiles.has("run5", "prompts/A2_repair1.txt"));
        assertTrue(runFiles.has("run5", "prompts/antwoord_A2_poging1.txt"), "the refused answer is kept");
        assertEquals(result.getResult().path("a3"), new ObjectMapper().readTree(Files.readString(replay.resolve("output.json"))).path("a3"));
    }

    /** store-prompts=false: no prompt texts are stored, the answers and working files are, and the repair turn still works. */
    @Test
    void withoutStoredPromptsTheRunStillRepairsAndKeepsAnswersAndFiles() throws Exception {
        ReflectionTestUtils.setField(runFiles, "storePrompts", false);
        String a2 = Files.readString(replay.resolve("prompts/antwoord_A2.txt"));
        String header = "seq\tie_ref\tftf_id\telement_id\tstatus\tmeaning_ids\tlink_ids\tmissing\tbasis\tcounterclaims";
        String removed = a2.lines().filter(line -> line.startsWith("2\t22.2\tFTF_017\t22.2-A1-Q1\t")).findFirst().orElseThrow();
        List<String> labels = new CopyOnWriteArrayList<>();
        List<String> repairPrompts = new CopyOnWriteArrayList<>();
        Prompt1TurnRunService.Model model = (turn, label, instructions, input, previous) -> {
            if (label.equals("A2 repair 1")) {
                repairPrompts.add(input);
                return new Prompt1TurnRunService.Answer("```ie_ftf_cells.tsv\n" + header + "\n" + removed + "\n```",
                        "resp_repair", CallUsage.builder().call(label).build());
            }
            return recorded(labels, Map.of("A2", a2.replace(removed + "\n", ""))).ask(turn, label, instructions, input, previous);
        };

        Prompt1ExperimentResult result = service.run("run7", klaas(), release, model);

        assertEquals("valid", result.getResult().path("run_status").asText());
        assertTrue(repairPrompts.get(0).contains("=== WORKING-FILE FORMAT"), "the repair prompt is built from the sent A2 prompt");
        List<String> stored = runFiles.list("run7");
        assertTrue(stored.stream().noneMatch(name -> name.matches("prompts/(A1|A2|A3|C_.*|A2_repair1)\\.txt")),
                "no prompt texts stored: " + stored.stream().filter(name -> name.startsWith("prompts/")).toList());
        assertTrue(stored.containsAll(List.of("prompts/antwoord_A1.txt", "prompts/antwoord_A2.txt", "prompts/antwoord_D.txt",
                "meanings.tsv", "a3_decisions.tsv", "output.json")), "answers and working files are stored");
    }

    /** The run check after A1 (Jan's excerpt check): an excerpt that is not in its source goes to the repair turn. */
    @Test
    void anExcerptNotInTheSourceIsCaughtByTheRunCheck() throws Exception {
        String a1 = Files.readString(replay.resolve("prompts/antwoord_A1.txt"));
        String meanings = Prompt1TurnAnswers.blocks(a1).get("meanings.tsv");
        List<String> lines = meanings.lines().toList();
        int excerpt = List.of(lines.get(0).split("\t", -1)).indexOf("source_excerpt");
        String original = lines.get(1);
        String[] cells = original.split("\t", -1);
        String meaningId = cells[0];
        cells[excerpt] = "zzz verzonnen tekst zzz";
        String broken = a1.replace(original, String.join("\t", cells));
        List<String> labels = new CopyOnWriteArrayList<>();
        List<String> repairPrompts = new CopyOnWriteArrayList<>();
        Prompt1TurnRunService.Model model = (turn, label, instructions, input, previous) -> {
            if (label.equals("A1 repair 1")) {
                labels.add(label);
                repairPrompts.add(input);
                return new Prompt1TurnRunService.Answer("```meanings.tsv\n" + lines.get(0) + "\n" + original + "\n```",
                        "resp_repair", CallUsage.builder().call(label).build());
            }
            return recorded(labels, Map.of("A1", broken)).ask(turn, label, instructions, input, previous);
        };

        Prompt1ExperimentResult result = service.run("run6", klaas(), release, model);

        assertNull(result.getError(), result.getError());
        assertTrue(labels.contains("A1 repair 1"));
        assertTrue(repairPrompts.get(0).contains(meaningId + ": excerpt niet in de bron"), "the problem names the meaning");
        assertTrue(result.getNotes().contains("A1: repair applied: meanings.tsv: 1 gewijzigd, 0 toegevoegd, 0 verwijderd"));
        assertEquals("valid", result.getResult().path("run_status").asText());
    }

    @Test
    void anAnswerStillRefusedAfterTheLastTryStopsTheRun() throws Exception {
        String broken = "```a1_selection.tsv\nstep\tie_ref\tfinding\n7.2D\t22.7\tprimary\n```";
        List<String> labels = new CopyOnWriteArrayList<>();
        Prompt1TurnRunService.Model alwaysBroken = (turn, label, instructions, input, previous) -> {
            if (turn.equals("A3")) {
                labels.add(label);
                return new Prompt1TurnRunService.Answer(broken, "resp_" + labels.size(), CallUsage.builder().call(label).build());
            }
            return recorded(labels, Map.of()).ask(turn, label, instructions, input, previous);
        };

        Prompt1ExperimentResult result = service.run("run3", klaas(), release, alwaysBroken);

        assertNotNull(result.getError());
        assertTrue(result.getError().startsWith("Turn A3: answer refused after 3 tries: a1_selection.tsv: header differs"),
                result.getError());
        assertTrue(labels.containsAll(List.of("A3", "A3 correction 1", "A3 correction 2")));
        assertFalse(runFiles.has("run3", "a3_evidence_basis.tsv"), "the run stopped before B");
    }

    /** v05b: background polling with a stub clock (no OpenAI call, no real waiting). */
    @Test
    void backgroundPollingWaitsRetriesAFailedCheckAndCancelsAfterTheLimit() {
        long[] clock = {0};
        Prompt1OpenAiCaller.Sleeper sleeper = millis -> clock[0] += millis;
        List<Long> beats = new ArrayList<>();

        // answer after polling; one failed check in between is repeated
        java.util.Iterator<String> statuses = List.of("in_progress", "FAIL", "in_progress", "completed").iterator();
        String done = Prompt1OpenAiCaller.poll("A1", "queued", previous -> {
                    String next = statuses.next();
                    if (next.equals("FAIL")) {
                        throw new IllegalStateException("network");
                    }
                    return next;
                }, status -> status, status -> fail("no cancel expected"), 15_000, 45 * 60_000L, () -> clock[0], sleeper,
                minutes -> beats.add(minutes));
        assertEquals("completed", done);
        assertEquals(60_000, clock[0], "four checks of 15 s");

        // no answer within the limit: the job is cancelled and the turn fails
        clock[0] = 0;
        List<String> cancelled = new ArrayList<>();
        Prompt1TurnRunService.TurnFailedException e = assertThrows(Prompt1TurnRunService.TurnFailedException.class,
                () -> Prompt1OpenAiCaller.poll("A1", "queued",
                previous -> "in_progress", status -> status, status -> cancelled.add(status), 15_000, 45 * 60_000L,
                () -> clock[0], sleeper, minutes -> beats.add(minutes)));
        assertTrue(e.getMessage().contains("no answer after 45 minutes"));
        assertEquals(List.of("in_progress"), cancelled);
        assertTrue(beats.containsAll(List.of(1L, 44L)), "a sign of life every minute");

        // ten failed checks in a row give up
        clock[0] = 0;
        assertThrows(IllegalStateException.class, () -> Prompt1OpenAiCaller.poll("A1", "queued", previous -> {
            throw new IllegalStateException("network");
        }, status -> status, status -> { }, 15_000, 45 * 60_000L, () -> clock[0], sleeper, minutes -> { }));
    }

    @Test
    void effortPlanPerTurn() {
        assertEquals(Map.of("A1", "high", "A2", "high", "A3", "medium", "B", "medium", "C", "medium", "D", "medium"),
                Prompt1TurnRunService.effortPlan("A1=high,A2=high,rest=medium"));
        assertEquals("xhigh", Prompt1TurnRunService.effortPlan("xhigh").get("C"));
        assertThrows(IllegalArgumentException.class, () -> Prompt1TurnRunService.effortPlan("E=high"));
    }

    private static void sameText(String file, String expected, String actual) {
        List<String> a = expected.replace("\r\n", "\n").strip().lines().filter(line -> !line.isBlank()).toList();
        List<String> b = actual.replace("\r\n", "\n").strip().lines().filter(line -> !line.isBlank()).toList();
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            if (!a.get(i).equals(b.get(i))) {
                fail(file + " differs at line " + (i + 1));
            }
        }
        assertEquals(a.size(), b.size(), file + " line count");
    }

    private IntakeFile file(String name) throws Exception {
        return new IntakeFile(name, Files.readAllBytes(intake.resolve(name)));
    }

    private static Path dir(String variable) {
        String value = System.getenv(variable);
        assumeTrue(value != null && Files.isDirectory(Path.of(value)), variable + " not set");
        return Path.of(value);
    }
}

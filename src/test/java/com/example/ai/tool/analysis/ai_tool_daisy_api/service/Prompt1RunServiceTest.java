package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.Prompt1RunEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1ExperimentResult;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1ExperimentResult.CallUsage;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1RunDto;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.Prompt1RunRepository;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1ExperimentService.IntakeFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Background runs of the turn-based route: queueing, pinned versions, outcomes and restarts. */
class Prompt1RunServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Prompt1RunEntity> table = new LinkedHashMap<>();
    private final List<Runnable> queued = new ArrayList<>();

    private Prompt1TurnRunService turnRunService;
    private Prompt1SourcePackageService sourcePackageService;
    private Prompt1RunRepository runRepository;

    @BeforeEach
    void setUp() throws Exception {
        turnRunService = mock(Prompt1TurnRunService.class);
        sourcePackageService = mock(Prompt1SourcePackageService.class);
        runRepository = mock(Prompt1RunRepository.class);
        when(runRepository.save(any())).thenAnswer(invocation -> {
            Prompt1RunEntity run = invocation.getArgument(0);
            table.put(run.getId(), run);
            return run;
        });
        when(runRepository.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(table.get((String) invocation.getArgument(0))));
        when(runRepository.findByStatusIn(any())).thenAnswer(invocation -> {
            Collection<Prompt1RunEntity.Status> statuses = invocation.getArgument(0);
            return table.values().stream().filter(run -> statuses.contains(run.getStatus())).toList();
        });
        when(sourcePackageService.selectedDocumentVersions()).thenReturn(Map.of("01", "v4", "R4", "v5"));
        when(turnRunService.currentSettings(any())).thenReturn(new Prompt1TurnRunService.TurnSettings(
                "gpt-6.1-terra", "A1=high,A2=high,rest=medium", "v0.136", "DAISY_Prompt_1_v0.136_compact.md"));
    }

    private Prompt1RunService service(Executor executor) {
        return new Prompt1RunService(sourcePackageService, runRepository, objectMapper, turnRunService, executor);
    }

    private static List<IntakeFile> intake() {
        return List.of(new IntakeFile("personal.json", "{\"a\":1}".getBytes(StandardCharsets.UTF_8)),
                new IntakeFile("intake.json", "{\"b\":2}".getBytes(StandardCharsets.UTF_8)));
    }

    private Prompt1ExperimentResult result(String json, int calls) throws Exception {
        Prompt1ExperimentResult result = Prompt1ExperimentResult.builder()
                .model("gpt-6.1-terra")
                .reasoningEffort("A1=high,A2=high,rest=medium")
                .packageProfile("TURNS v0.136")
                .systemMessageFile("DAISY_Prompt_1_v0.136_compact.md")
                .systemMessageHash("sm-hash")
                .schemaName("v0.46d-lean")
                .schemaHash("schema-hash")
                .documentVersions(new LinkedHashMap<>(Map.of("01", "v4", "R4", "v5")))
                .primaryIeRef("22.7")
                .result(json == null ? null : objectMapper.readTree(json))
                .build();
        for (int i = 1; i <= calls; i++) {
            result.getCalls().add(CallUsage.builder().call("turn " + i).responseId("resp_" + i).inputTokens(100)
                    .outputTokens(10).estimatedCostUsd(0.01).build());
        }
        result.setTotal(Prompt1ExperimentService.total(result.getCalls()));
        return result;
    }

    @Test
    void validRunStoresResultSettingsAndPinnedVersions() throws Exception {
        when(turnRunService.run(anyString(), anyList(), anyMap(), nullable(String.class))).thenReturn(result("{\"run_status\":\"valid\"}", 13));
        Prompt1RunService service = service(task -> task.run());

        Prompt1RunDto submitted = service.submit(intake(), "standard");
        Prompt1RunDto run = service.get(submitted.getRunId()).orElseThrow();

        assertEquals("VALID", run.getStatus());
        assertEquals("turns", run.getFlow());
        assertEquals("sm-hash", run.getSystemMessageHash());
        assertEquals(Map.of("01", "v4", "R4", "v5"), run.getDocumentVersions());
        assertEquals(List.of("personal.json", "intake.json"), run.getIntakeFiles());
        assertEquals(0.13, run.getEstimatedCostUsd(), 1e-9);
        assertEquals(13, run.getCalls().size());
        assertNotNull(run.getResult());
        assertNull(run.getError());
        assertNotNull(run.getFinishedAt());
        verify(turnRunService).run(eq(submitted.getRunId()), argThat(files -> files.size() == 2),
                eq(Map.of("01", "v4", "R4", "v5")), eq("standard"));
        verify(turnRunService).currentSettings("standard");
    }

    @Test
    void documentVersionsArePinnedAtSubmitNotAtStart() throws Exception {
        when(turnRunService.run(anyString(), anyList(), anyMap(), nullable(String.class))).thenReturn(result("{\"run_status\":\"valid\"}", 13));
        Prompt1RunService service = service(task -> queued.add(task));

        String runId = service.submit(intake(), null).getRunId();
        assertEquals("QUEUED", service.get(runId).orElseThrow().getStatus());

        // a knowledge reload activates new versions before the worker picks the run up
        when(sourcePackageService.selectedDocumentVersions()).thenReturn(Map.of("01", "v5", "R4", "v6"));
        queued.forEach(task -> task.run());

        verify(turnRunService).run(eq(runId), anyList(), eq(Map.of("01", "v4", "R4", "v5")), (String) isNull());
    }

    @Test
    void aStoppedRunIsFailedWithItsReasonAndStoresNoResult() throws Exception {
        Prompt1ExperimentResult stopped = result(null, 3);
        stopped.setError("Turn A3: answer refused after 3 tries: a1_selection.tsv: header differs");
        when(turnRunService.run(anyString(), anyList(), anyMap(), nullable(String.class))).thenReturn(stopped);
        Prompt1RunService service = service(task -> task.run());

        Prompt1RunDto run = service.get(service.submit(intake(), null).getRunId()).orElseThrow();

        assertEquals("FAILED", run.getStatus());
        assertNull(run.getResult());
        assertEquals("Turn A3: answer refused after 3 tries: a1_selection.tsv: header differs", run.getError());
        assertEquals(3, run.getCalls().size(), "the calls made before the stop are kept");
    }

    @Test
    void invalidPrompt1OutcomeIsFailedWithItsReason() throws Exception {
        when(turnRunService.run(anyString(), anyList(), anyMap(), nullable(String.class))).thenReturn(result("{\"run_status\":\"invalid\","
                + "\"failure_stage\":\"A1.primary\",\"failure_reason\":\"no_primary_eligible_canonical_IE\"}", 13));
        Prompt1RunService service = service(task -> task.run());

        Prompt1RunDto run = service.get(service.submit(intake(), null).getRunId()).orElseThrow();

        assertEquals("FAILED", run.getStatus());
        assertEquals("Prompt 1 run_status invalid: A1.primary / no_primary_eligible_canonical_IE", run.getError());
        assertNull(run.getResult());
    }

    @Test
    void exceptionMarksTheRunFailed() throws Exception {
        when(turnRunService.run(anyString(), anyList(), anyMap(), nullable(String.class))).thenThrow(new IllegalArgumentException("B2 matrix missing"));
        Prompt1RunService service = service(task -> task.run());

        Prompt1RunDto run = service.get(service.submit(intake(), null).getRunId()).orElseThrow();

        assertEquals("FAILED", run.getStatus());
        assertTrue(run.getError().contains("B2 matrix missing"));
        assertNotNull(run.getFinishedAt());
    }

    @Test
    void fullQueueFailsTheRunAndReportsIt() {
        Prompt1RunService service = service(task -> {
            throw new RejectedExecutionException("full");
        });

        assertThrows(Prompt1RunService.QueueFullException.class, () -> service.submit(intake(), null));
        Prompt1RunEntity run = table.values().iterator().next();
        assertEquals(Prompt1RunEntity.Status.FAILED, run.getStatus());
        assertTrue(run.getError().contains("queue is full"));
    }

    @Test
    void emptyIntakeIsRejectedBeforeAnythingIsStored() {
        Prompt1RunService service = service(task -> task.run());

        assertThrows(IllegalArgumentException.class, () -> service.submit(List.of(), null));
        assertThrows(IllegalArgumentException.class,
                () -> service.submit(List.of(new IntakeFile("empty.json", new byte[0])), null));
        assertTrue(table.isEmpty());
    }

    @Test
    void unfinishedRunsAreMarkedInterruptedAtStartup() {
        table.put("queued", Prompt1RunEntity.builder().id("queued").status(Prompt1RunEntity.Status.QUEUED).build());
        table.put("running", Prompt1RunEntity.builder().id("running").status(Prompt1RunEntity.Status.RUNNING).build());
        table.put("valid", Prompt1RunEntity.builder().id("valid").status(Prompt1RunEntity.Status.VALID).build());

        service(task -> task.run()).markUnfinishedRunsInterrupted();

        assertEquals(Prompt1RunEntity.Status.INTERRUPTED, table.get("queued").getStatus());
        assertEquals(Prompt1RunEntity.Status.INTERRUPTED, table.get("running").getStatus());
        assertTrue(table.get("running").getError().contains("Submit it again"));
        assertEquals(Prompt1RunEntity.Status.VALID, table.get("valid").getStatus());
    }

    @Test
    void intakeHashDependsOnNamesContentsAndOrder() {
        List<IntakeFile> files = intake();
        List<IntakeFile> reversed = List.of(files.get(1), files.get(0));

        assertEquals(Prompt1RunService.intakeHash(files), Prompt1RunService.intakeHash(intake()));
        assertNotEquals(Prompt1RunService.intakeHash(files), Prompt1RunService.intakeHash(reversed));
    }

    @Test
    void storedResultIsReturnedOnlyBySingleRunLookup() throws Exception {
        when(turnRunService.run(anyString(), anyList(), anyMap(), nullable(String.class))).thenReturn(result("{\"run_status\":\"valid\"}", 13));
        when(runRepository.findAllByOrderByCreatedAtDesc(any())).thenAnswer(invocation -> new ArrayList<>(table.values()));
        Prompt1RunService service = service(task -> task.run());
        String runId = service.submit(intake(), null).getRunId();

        JsonNode single = service.get(runId).orElseThrow().getResult();
        assertNotNull(single);
        assertNull(service.list(20).get(0).getResult());
    }
}

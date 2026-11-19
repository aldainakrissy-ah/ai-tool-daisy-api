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
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Background runs of the turn-based route: queueing, pinned versions, outcomes and restarts. */
class Prompt1RunServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Prompt1RunEntity> table = new LinkedHashMap<>();
    private final List<Runnable> queued = new ArrayList<>();

    private Prompt1TurnRunService turnRunService;
    private Prompt1ReleaseService releaseService;
    private Prompt1RunRepository runRepository;
    private Prompt1ResultStore resultStore;

    /** A release as the release store gives it (the texts are not read here). */
    private static Prompt1Release release(String id, String name, Map<String, String> versions) {
        return new Prompt1Release(id, name, null, "sm", "DAISY_Prompt_1_v0.139_compact.md", "doc18", "{}", null,
                Map.of(), versions);
    }

    private final Prompt1Release active = release("rel-136", "P1-v0.136", Map.of("01", "v4", "R4", "v5"));

    @BeforeEach
    void setUp() throws Exception {
        turnRunService = mock(Prompt1TurnRunService.class);
        releaseService = mock(Prompt1ReleaseService.class);
        runRepository = mock(Prompt1RunRepository.class);
        resultStore = mock(Prompt1ResultStore.class);
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
        when(releaseService.current()).thenReturn(active);
        when(turnRunService.settings(nullable(String.class), any(Prompt1Release.class))).thenReturn(new Prompt1TurnRunService.TurnSettings(
                "gpt-6.1-terra", "A1=high,A2=high,rest=medium", "P1-v0.136", "DAISY_Prompt_1_v0.136_compact.md"));
    }

    /** Stubs the turn run, whatever its arguments. */
    private OngoingStubbing<Prompt1ExperimentResult> whenRun() {
        return when(turnRunService.run(anyString(), anyList(), any(Prompt1Release.class), nullable(String.class), any()));
    }

    private Prompt1RunService service(Executor executor) {
        return new Prompt1RunService(releaseService, runRepository, objectMapper, turnRunService, resultStore, executor);
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
        whenRun().thenReturn(result("{\"run_status\":\"valid\"}", 13));
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
        assertEquals("P1-v0.136", run.getReleaseName());
        verify(turnRunService).run(eq(submitted.getRunId()), argThat(files -> files.size() == 2),
                eq(active), eq("standard"), any());
        verify(turnRunService).settings("standard", active);
        verifyNoInteractions(resultStore);  // experiment runs are not shown in the apps
    }

    @Test
    void theReleaseIsPinnedAtSubmitNotAtStart() throws Exception {
        whenRun().thenReturn(result("{\"run_status\":\"valid\"}", 13));
        Prompt1RunService service = service(task -> queued.add(task));

        String runId = service.submit(intake(), null).getRunId();
        assertEquals("QUEUED", service.get(runId).orElseThrow().getStatus());

        // another release is activated before the worker picks the run up
        when(releaseService.current()).thenReturn(release("rel-137", "P1-v0.137", Map.of("01", "v5", "R4", "v6")));
        queued.forEach(task -> task.run());

        verify(turnRunService).run(eq(runId), anyList(), eq(active), (String) isNull(), any());
        assertEquals(Map.of("01", "v4", "R4", "v5"), service.get(runId).orElseThrow().getDocumentVersions());
    }

    @Test
    void aGivenReleaseIsUsedInsteadOfTheActiveOne() throws Exception {
        Prompt1Release draft = release("rel-draft", "P1-v0.137", Map.of("01", "v5"));
        when(releaseService.byId("rel-draft")).thenReturn(draft);
        whenRun().thenReturn(result("{\"run_status\":\"valid\"}", 13));
        Prompt1RunService service = service(task -> task.run());

        String runId = service.submit(intake(), null, "rel-draft").getRunId();

        verify(turnRunService).run(eq(runId), anyList(), eq(draft), (String) isNull(), any());
        verify(releaseService, never()).current();
    }

    @Test
    void withoutAReleaseNoRunIsCreated() {
        when(releaseService.current()).thenThrow(new IllegalStateException("Prompt 1 is not ready to run: no active release"));
        Prompt1RunService service = service(task -> task.run());

        assertThrows(IllegalStateException.class, () -> service.submit(intake(), null));
        assertTrue(table.isEmpty());
    }

    @Test
    void aStoppedRunIsFailedWithItsReasonAndStoresNoResult() throws Exception {
        Prompt1ExperimentResult stopped = result(null, 3);
        stopped.setError("Turn A3: answer refused after 3 tries: a1_selection.tsv: header differs");
        whenRun().thenReturn(stopped);
        Prompt1RunService service = service(task -> task.run());

        Prompt1RunDto run = service.get(service.submit(intake(), null).getRunId()).orElseThrow();

        assertEquals("FAILED", run.getStatus());
        assertNull(run.getResult());
        assertEquals("Turn A3: answer refused after 3 tries: a1_selection.tsv: header differs", run.getError());
        assertEquals(3, run.getCalls().size(), "the calls made before the stop are kept");
    }

    @Test
    void invalidPrompt1OutcomeIsFailedWithItsReason() throws Exception {
        whenRun().thenReturn(result("{\"run_status\":\"invalid\","
                + "\"failure_stage\":\"A1.primary\",\"failure_reason\":\"no_primary_eligible_canonical_IE\"}", 13));
        Prompt1RunService service = service(task -> task.run());

        Prompt1RunDto run = service.get(service.submit(intake(), null).getRunId()).orElseThrow();

        assertEquals("FAILED", run.getStatus());
        assertEquals("Prompt 1 run_status invalid: A1.primary / no_primary_eligible_canonical_IE", run.getError());
        assertNull(run.getResult());
    }

    @Test
    void exceptionMarksTheRunFailed() throws Exception {
        whenRun().thenThrow(new IllegalArgumentException("B2 matrix missing"));
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
        whenRun().thenReturn(result("{\"run_status\":\"valid\"}", 13));
        when(runRepository.findAllByOrderByCreatedAtDesc(any())).thenAnswer(invocation -> new ArrayList<>(table.values()));
        Prompt1RunService service = service(task -> task.run());
        String runId = service.submit(intake(), null).getRunId();

        JsonNode single = service.get(runId).orElseThrow().getResult();
        assertNotNull(single);
        assertNull(service.list(20).get(0).getResult());
    }

    // ---- runs started for a client (POST /analyze/prompt1/runs)

    private final Prompt1RunService.Client client = new Prompt1RunService.Client("c-1", "Test Client", "p-1", "Prof");

    /** A VALID result with a control and production JSON just big enough for the apps' format. */
    private Prompt1ExperimentResult validClientResult() throws Exception {
        Prompt1ExperimentResult result = result("{\"run_status\":\"valid\",\"a1\":{\"primary_ie\":{\"ie_ref\":\"22.7\","
                + "\"ie_title\":\"Titel\"},\"secondary_ie\":[]},\"b3\":[]}", 13);
        result.setProductionResult(objectMapper.readTree("{\"prompt_id\":\"Prompt_1\",\"date\":\"2026-11-18\","
                + "\"client-name\":\"Test Client\",\"A1\":{\"teleonic_pattern\":\"p\"},\"A3\":{\"direct\":[],\"indirect\":[]},"
                + "\"B1\":[],\"B2\":[],\"B3\":[]}"));
        return result;
    }

    /** The client files as the intake builder returns them, built for this client name. */
    private static Prompt1IntakeBuilder.ClientFiles files(String clientName) {
        return new Prompt1IntakeBuilder.ClientFiles(intake(), clientName);
    }

    @Test
    void aStartWithoutAClientNameKeepsTheNameTheFilesWereBuiltFor() throws Exception {
        whenRun().thenReturn(validClientResult());
        Prompt1RunService service = service(task -> task.run());
        Prompt1RunService.Client fromIntakeApp = new Prompt1RunService.Client("c-1", null, null, "client");

        String runId = service.submitForClient(fromIntakeApp, null, () -> files("Portal Name")).run().getRunId();

        assertEquals("Portal Name", service.status(runId).orElseThrow().getClientName());
        verify(resultStore).save(eq("client"), eq("Portal Name"), eq("Prompt_1"), anyString());
    }

    @Test
    void clientRunStoresTheClientItsPhasesAndTheResultForTheApps() throws Exception {
        Prompt1ExperimentResult valid = validClientResult();
        whenRun()
                .thenAnswer(invocation -> {
                    Consumer<String> onPhase = invocation.getArgument(4);
                    onPhase.accept("A1");
                    onPhase.accept("D");
                    return valid;
                });
        Prompt1RunService service = service(task -> task.run());

        Prompt1RunService.ClientSubmit submitted = service.submitForClient(client, null, () -> files("Test Client"));

        assertFalse(submitted.existing());
        String runId = submitted.run().getRunId();
        Prompt1RunDto run = service.status(runId).orElseThrow();
        assertEquals("VALID", run.getStatus());
        assertEquals("c-1", run.getClientId());
        assertEquals("Test Client", run.getClientName());
        assertEquals("Prof", run.getProfessionalName());
        assertEquals("D", run.getCurrentTurn());
        assertNull(run.getResult());  // the status read holds no client text
        assertNull(run.getError());
        verify(runRepository).updateCurrentTurn(runId, "A1");
        verify(runRepository).updateCurrentTurn(runId, "D");
        verify(resultStore).save(eq("Prof"), eq("Test Client"), eq("Prompt_1"),
                argThat(json -> json.contains("\"primary_ie_ref\":\"22.7\"") && json.contains("\"client-name\":\"Test Client\"")));
    }

    @Test
    void aQueuedOrRunningRunOfTheClientIsReturnedWithoutBuildingTheIntake() {
        Prompt1RunEntity running = Prompt1RunEntity.builder().id("r-1").status(Prompt1RunEntity.Status.RUNNING)
                .flow("turns").clientId("c-1").build();
        when(runRepository.findFirstByClientIdAndStatusInOrderByCreatedAtDesc(eq("c-1"), any()))
                .thenReturn(Optional.of(running));
        Prompt1RunService service = service(task -> task.run());

        Prompt1RunService.ClientSubmit submitted = service.submitForClient(client, null,
                () -> fail("the intake must not be built when a run is already queued or running"));

        assertTrue(submitted.existing());
        assertEquals("r-1", submitted.run().getRunId());
        verify(runRepository, never()).save(any());
        verifyNoInteractions(turnRunService);
    }

    @Test
    void aSecondRequestWhileTheFirstIsStartingIs409() throws Exception {
        whenRun()
                .thenReturn(validClientResult());
        Prompt1RunService service = service(task -> queued.add(task));

        Prompt1RunService.ClientSubmit first = service.submitForClient(client, null, () -> {
            // a second request for the same client arrives while the first builds the intake
            Prompt1ClientDataException e = assertThrows(Prompt1ClientDataException.class,
                    () -> service.submitForClient(client, null, () -> files("Test Client")));
            assertEquals(HttpStatus.CONFLICT, e.getStatus());
            return files("Test Client");
        });

        assertFalse(first.existing());
        assertEquals(1, table.size());
        // once started, the next request goes through the guard again (here: no active run in the mock)
        assertFalse(service.submitForClient(client, null, () -> files("Test Client")).existing());
    }

    @Test
    void aFailedSaveForTheAppsKeepsTheRunValidAndSaysWhy() throws Exception {
        whenRun()
                .thenReturn(validClientResult());
        doThrow(new IllegalStateException("database down")).when(resultStore).save(any(), any(), any(), any());
        Prompt1RunService service = service(task -> task.run());

        String runId = service.submitForClient(client, null, () -> files("Test Client")).run().getRunId();

        Prompt1RunDto run = service.status(runId).orElseThrow();
        assertEquals("VALID", run.getStatus());
        assertTrue(run.getError().contains("could not be saved"), run.getError());
    }

    @Test
    void noUsableReleaseIsRefusedBeforeTheClientFilesAreBuilt() {
        when(releaseService.current()).thenThrow(new Prompt1NotReadyException("no active release", null));
        Prompt1RunService service = service(task -> task.run());

        assertThrows(Prompt1NotReadyException.class, () -> service.submitForClient(client, null,
                () -> fail("the client files must not be built when Prompt 1 is not ready")));
        verify(runRepository, never()).save(any());
    }
}

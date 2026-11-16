package com.example.ai.tool.analysis.ai_tool_daisy_api.controller;

import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1RunDto;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1ExperimentService.IntakeFile;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1RunFiles;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1RunService;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1TurnPreviewService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Experiment endpoints for Prompt 1 on Jan's turn-based route, with the sources read by key from
 * the knowledge tables instead of File Search. A run is submitted in the background
 * ({@code POST /prompt1/runs}) and read back by ID. The synchronous two-call endpoint
 * ({@code POST /prompt1}) was retired on 13 Nov 2026.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai-tool-daisy/experiments")
@AllArgsConstructor
@CrossOrigin(origins = "*")
public class Prompt1ExperimentController {

    private final Prompt1RunService prompt1RunService;
    private final Prompt1TurnPreviewService prompt1TurnPreviewService;
    private final Prompt1RunFiles prompt1RunFiles;

    /**
     * Free preview of the turn-based route (Jan's PRELOAD design): the Step 0 inventory of the
     * files, the system message size per turn, and turn A1 as it would be sent. No OpenAI call.
     *
     * @param withText also return the A1 system message and message (they hold client data)
     */
    @PostMapping("/prompt1/turns/preview")
    public ResponseEntity<Prompt1TurnPreviewService.Preview> previewTurns(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(defaultValue = "false") boolean withText) throws IOException {
        log.info("Prompt 1 turn preview requested: {} file(s), withText={}", files.size(), withText);
        return ResponseEntity.ok(prompt1TurnPreviewService.previewA1(intakeFiles(files), withText));
    }

    /**
     * Queues a background run and returns it at once as QUEUED (HTTP 202).
     *
     * @param files  client intake files (personal data first, then the intake questionnaire)
     * @param preset optional model-and-effort plan: quality, standard or economy
     */
    @PostMapping("/prompt1/runs")
    public ResponseEntity<?> submitRun(@RequestParam("files") List<MultipartFile> files,
                                       @RequestParam(required = false) String preset) throws IOException {
        log.info("Prompt 1 background run requested: {} file(s), preset={}", files.size(), preset);
        try {
            return ResponseEntity.accepted().body(prompt1RunService.submit(intakeFiles(files), preset));
        } catch (Prompt1RunService.QueueFullException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Service Unavailable", "message", e.getMessage()));
        }
    }

    /** One run with its status, usage, notes and, once VALID, the final Prompt 1 JSON. */
    @GetMapping("/prompt1/runs/{runId}")
    public ResponseEntity<Prompt1RunDto> getRun(@PathVariable String runId) {
        return prompt1RunService.get(runId)
                .map(run -> ResponseEntity.ok(run))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** The working files, prompts and answers a run has stored (names only). */
    @GetMapping("/prompt1/runs/{runId}/files")
    public ResponseEntity<List<String>> runFiles(@PathVariable String runId) {
        return prompt1RunService.get(runId).isPresent()
                ? ResponseEntity.ok(prompt1RunFiles.list(runId))
                : ResponseEntity.notFound().build();
    }

    /** One stored file of a run, e.g. name=a3_decisions.tsv or name=prompts/A1.txt (holds client data). */
    @GetMapping(value = "/prompt1/runs/{runId}/file", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> runFile(@PathVariable String runId, @RequestParam String name) {
        return prompt1RunFiles.read(runId, name)
                .map(content -> ResponseEntity.ok(content))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** The latest runs, newest first, without their result JSON. */
    @GetMapping("/prompt1/runs")
    public ResponseEntity<List<Prompt1RunDto>> listRuns(@RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(prompt1RunService.list(limit));
    }

    private static List<IntakeFile> intakeFiles(List<MultipartFile> files) throws IOException {
        List<IntakeFile> intake = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            intake.add(IntakeFile.of(file));
        }
        return intake;
    }
}

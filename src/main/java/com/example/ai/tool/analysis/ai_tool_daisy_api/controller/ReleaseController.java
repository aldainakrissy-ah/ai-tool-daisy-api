package com.example.ai.tool.analysis.ai_tool_daisy_api.controller;

import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.ReleaseReport;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.ReleaseStoreService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * The release store for the prompt files (API key required, like every endpoint). A release is uploaded as
 * one zip with a manifest.json, checked and stored as a DRAFT, then activated; rolling back is activating
 * an older release. Responses show file names, roles, versions, hashes and sizes, never file content.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai-tool-daisy/releases")
@AllArgsConstructor
public class ReleaseController {

    private final ReleaseStoreService releaseStoreService;

    /**
     * Uploads a release package. 201 with the report when every check passed (status DRAFT); 422 with the
     * report when a check failed (nothing stored); 400 when the upload is not a release package.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) throws IOException {
        log.info("Release upload: {} bytes", file.getSize());
        try {
            ReleaseReport report = releaseStoreService.upload(file.getBytes());
            return ResponseEntity.status(report.passed() ? HttpStatus.CREATED : HttpStatus.UNPROCESSABLE_ENTITY).body(report);
        } catch (ReleaseStoreService.BadPackageException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Bad Request", "message", e.getMessage()));
        }
    }

    /** The releases of one prompt (e.g. P1), or of all prompts, newest first. */
    @GetMapping
    public List<ReleaseReport> list(@RequestParam(required = false) String prompt) {
        return releaseStoreService.list(prompt);
    }

    /** One release with its checks and files. */
    @GetMapping("/{releaseId}")
    public ResponseEntity<ReleaseReport> get(@PathVariable String releaseId) {
        return releaseStoreService.get(releaseId)
                .map(report -> ResponseEntity.ok(report))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Makes the release the active one of its prompt; the one active before it is retired. */
    @PostMapping("/{releaseId}/activate")
    public ResponseEntity<ReleaseReport> activate(@PathVariable String releaseId) {
        return releaseStoreService.activate(releaseId)
                .map(report -> ResponseEntity.ok(report))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

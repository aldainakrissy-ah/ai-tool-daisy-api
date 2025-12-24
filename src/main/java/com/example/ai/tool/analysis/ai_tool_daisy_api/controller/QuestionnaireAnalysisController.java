package com.example.ai.tool.analysis.ai_tool_daisy_api.controller;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.Prompt1ResultEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1Result;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.QuestionnaireAnalysisService;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.DatabaseHealthService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST controller for handling questionnaire analysis requests.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai-tool-daisy")
@AllArgsConstructor
@CrossOrigin(origins = "*")
public class QuestionnaireAnalysisController {

    private final QuestionnaireAnalysisService questionnaireAnalysisService;
    private final DatabaseHealthService databaseHealthService;

    /**
     * Analyzes an uploaded PDF questionnaire file using AI.
     * Extracts text content, sends to OpenAI for teleonic analysis, and persists
     * results.
     *
     * @param file the uploaded PDF file containing healthcare questionnaire data
     * @return {@link ResponseEntity} with {@link Prompt1Result} containing the AI
     *         analysis
     */
    @PostMapping("/analyze")
    public ResponseEntity<Prompt1Result> analyzePdf(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            log.warn("Received empty or null file upload request");
            return ResponseEntity.badRequest().build();
        }

        log.info("Received PDF for analysis: {} ({} bytes)",
                file.getOriginalFilename(), file.getSize());

        try {
            Prompt1Result result = questionnaireAnalysisService.generatePreIntakeAnalysis(file);
            log.info("Successfully analyzed PDF for professional: {}, client: {}",
                    result.getProfessionalName(), result.getClientName());
            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            log.error("Invalid file upload: {}", e.getMessage());
            return ResponseEntity.badRequest().build();

        } catch (Exception e) {
            log.error("Failed to analyze PDF: {}", file.getOriginalFilename(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Endpoint to retrieve Prompt1 results by professional ID.
     *
     * @param professionalId the patient ID to search for.
     * @return a {@link ResponseEntity} containing a list of
     *         {@link Prompt1ResultEntity} or a not found status.
     */
    @GetMapping("/analysis/{professionalId}")
    public ResponseEntity<List<Prompt1ResultEntity>> getResultsByProfessionalId(
            @PathVariable("professionalId") String professionalId) {
        List<Prompt1ResultEntity> result = questionnaireAnalysisService.getPreIntakeResult(professionalId);
        if (result == null || result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/analysis/{professionalId}/client/{patientId}")
    public ResponseEntity<List<Prompt1ResultEntity>> getResultByProfessionalIdAndPatientId(
            @PathVariable("professionalId") String professionalId,
            @PathVariable("patientId") String patientId) {
        List<Prompt1ResultEntity> result = questionnaireAnalysisService
                .getPreIntakeResultByProfessionalIdAndPatientId(professionalId, patientId);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/analysis/save", consumes = "application/json")
    public ResponseEntity<Void> savePreIntakeResult(@RequestBody Prompt1Result prompt1Result) {
        if (prompt1Result == null) {
            log.warn("Received null Prompt1Result payload");
            return ResponseEntity.badRequest().build();
        }
        log.info("Saving Prompt1 result for professional id: {}", prompt1Result.getProfessionalName());
        try {
            questionnaireAnalysisService.savePrompt1Result(prompt1Result);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to save Prompt1 result for professionalId={}, {}",
                    prompt1Result.getProfessionalName(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Health check endpoint to monitor database status.
     *
     * @return {@link ResponseEntity} with the database health status.
     */
    @GetMapping("/health/database")
    public ResponseEntity<String> checkDatabaseHealth() {
        try {
            DatabaseHealthService.DatabaseHealthStatus healthStatus = databaseHealthService.getDatabaseHealthStatus();

            if (healthStatus.isOverallHealthy()) {
                String message = String.format("Database health: Primary=%s, Second=%s (enabled=%s)",
                        healthStatus.isPrimaryDatabaseHealthy() ? "healthy" : "unhealthy",
                        healthStatus.isSecondDatabaseHealthy() ? "healthy" : "unhealthy",
                        healthStatus.isSecondDatabaseEnabled());
                return ResponseEntity.ok(message);
            } else {
                String message = String.format("Database not healthy: Primary=%s, Second=%s (enabled=%s)",
                        healthStatus.isPrimaryDatabaseHealthy() ? "healthy" : "unhealthy",
                        healthStatus.isSecondDatabaseHealthy() ? "healthy" : "unhealthy",
                        healthStatus.isSecondDatabaseEnabled());
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(message);
            }
        } catch (Exception e) {
            log.error("Failed to check database health: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error checking database health: " + e.getMessage());
        }
    }
}

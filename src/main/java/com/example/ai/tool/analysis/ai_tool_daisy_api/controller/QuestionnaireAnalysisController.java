package com.example.ai.tool.analysis.ai_tool_daisy_api.controller;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.Prompt1ResultEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.AiAnalysisResult;
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
     * @return {@link ResponseEntity} with {@link AiAnalysisResult} containing the AI
     *         analysis
     */
    @PostMapping("/analyze")
    public ResponseEntity<AiAnalysisResult> analyzePdf(@RequestParam("file") MultipartFile file) {

        AiAnalysisResult result = questionnaireAnalysisService.generatePreIntakeAnalysis(file);
        log.info("Successfully analyzed PDF for professional: {}, client: {}",
                result.getProfessionalName(), result.getClientName());
            return ResponseEntity.ok(result);

    }

    /**
     * Endpoint to retrieve Prompt1 results by professional ID.
     *
     * @param professionalId the patient ID to search for.
     * @return a {@link ResponseEntity} containing a list of
     *         {@link Prompt1ResultEntity} or a not found status.
     */
    @GetMapping("/analysis/professional/{professionalId}")
    public ResponseEntity<List<Prompt1ResultEntity>> getResultsByProfessionalId(
            @PathVariable("professionalId") String professionalId) {
        List<Prompt1ResultEntity> result = questionnaireAnalysisService.getPreIntakeResult(professionalId);
        if (result == null || result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Endpoint to retrieve aiAnalysisResults results by professional ID and patient ID.
     *
     * @param professionalId the professional ID to search for.
     * @param patientId the patient ID to search for.
     * @return a {@link ResponseEntity} containing a list of
     *         {@link Prompt1ResultEntity}.
     */
    @GetMapping("/analysis/professional/{professionalId}/client/{patientId}")
    public ResponseEntity<List<Prompt1ResultEntity>> getResultByProfessionalIdAndPatientId(
            @PathVariable("professionalId") String professionalId,
            @PathVariable("patientId") String patientId) {
        List<Prompt1ResultEntity> result = questionnaireAnalysisService
                .getPreIntakeResultByProfessionalIdAndPatientId(professionalId, patientId);

        return ResponseEntity.ok(result);
    }

    /**
     * Endpoint to retrieve all aiAnalysisResults results.
     *
     * @return a {@link ResponseEntity} containing a list of
     *         {@link Prompt1ResultEntity}.
     */
    @GetMapping("/analysis/all")
    public ResponseEntity<List<Prompt1ResultEntity>> getAllResults() {
        List<Prompt1ResultEntity> result = questionnaireAnalysisService.getAllPreIntakeResults();
        return ResponseEntity.ok(result);
    }

    /**
     * Endpoint to save aiAnalysisResults result.
     *
     * @param prompt1Result the {@link AiAnalysisResult} to be saved.
     * @return a {@link ResponseEntity} indicating the result of the save operation.
     */
    @PostMapping(value = "/analysis/save", consumes = "application/json")
    public ResponseEntity<Void> savePreIntakeResult(@RequestBody AiAnalysisResult prompt1Result) {
        log.info("Saving Prompt1 result for professional id: {}", prompt1Result.getProfessionalName());
            questionnaireAnalysisService.savePrompt1Result(prompt1Result);
            return ResponseEntity.ok().build();

    }

    /**
     * Endpoint to retrieve aiAnalysisResults by patient ID.
     *
     * @param patientId the patient ID to search for.
     * @return a {@link ResponseEntity} containing a list of
     *         {@link Prompt1ResultEntity} or a not found status.
     */
    @GetMapping("/analysis/patient/{patientId}")
    public ResponseEntity<List<Prompt1ResultEntity>> getResultByPatientId(
            @PathVariable("patientId") String patientId) {
        List<Prompt1ResultEntity> result = questionnaireAnalysisService
                .getPreIntakeResultByPatientId(patientId);
        if (result == null || result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Endpoint to retrieve aiAnalysisResults by patient ID and prompt type.
     *
     * @param patientId the patient ID to search for.
     * @param promptType the prompt type to filter results.
     * @return a {@link ResponseEntity} containing a list of
     *         {@link Prompt1ResultEntity} or a not found status.
     */
    @GetMapping("/analysis/patient/{patientId}/prompt-type/{promptType}")
    public ResponseEntity<List<Prompt1ResultEntity>> getResultByPatientIdAndPromptType(
            @PathVariable("patientId") String patientId,
            @PathVariable("promptType") String promptType) {
        List<Prompt1ResultEntity> result = questionnaireAnalysisService
                .getResultByPatientIdAndPromptType(patientId, promptType);
        if (result == null || result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
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

package com.example.ai.tool.analysis.ai_tool_daisy_api.controller;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.Prompt1ResultEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1Result;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.QuestionnaireAnalysisService;
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

    /**
     * Endpoint to upload and process a PDF file.
     *
     * @param file the uploaded PDF file as a {@link MultipartFile}.
     * @return a {@link ResponseEntity} containing the result of the processing or an error message.
     */
    @PostMapping("/analyze")
    public ResponseEntity<Prompt1Result> readPdf(@RequestParam("file") MultipartFile file) {
        log.info("Received file: {}", file.getOriginalFilename());
        Prompt1Result responseFuture = questionnaireAnalysisService.generatePreIntakeAnalysis(file);
        return ResponseEntity.ok(responseFuture);
    }

    /**
     * Endpoint to retrieve Prompt1 results by professional ID.
     *
     * @param professionalId the patient ID to search for.
     * @return a {@link ResponseEntity} containing a list of {@link Prompt1ResultEntity} or a not found status.
     */
    @GetMapping("/analysis/{professionalId}")
    public ResponseEntity<List<Prompt1ResultEntity>> getResultsByProfessionalId(@PathVariable("professionalId") String professionalId) {
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
        List<Prompt1ResultEntity> result = questionnaireAnalysisService.getPreIntakeResultByProfessionalIdAndPatientId(professionalId, patientId);
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
        log.info("Saving Prompt1 result for professional id: {}", prompt1Result.getProfessionalId());
        try {
            questionnaireAnalysisService.savePrompt1Result(prompt1Result);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to save Prompt1 result for professionalId={}, {}",
                    prompt1Result.getProfessionalId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}


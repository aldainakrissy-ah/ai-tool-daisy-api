package com.example.ai.tool.analysis.ai_tool_daisy_api.controller;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.Prompt1ResultEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1Result;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.QuestionnaireAnalysisService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * REST controller for handling questionnaire analysis requests.
 */
@Slf4j
@RestController
@RequestMapping("/ai/tool/daisy/")
@AllArgsConstructor
public class QuestionnaireAnalysisController {

    private final QuestionnaireAnalysisService questionnaireAnalysisService;

    /**
     * Endpoint to upload and process a PDF file.
     *
     * @param file the uploaded PDF file as a {@link MultipartFile}.
     * @return a {@link ResponseEntity} containing the result of the processing or an error message.
     */
    @PostMapping("/read/pdf")
    public ResponseEntity<Prompt1Result> readPdf(@RequestParam("file") MultipartFile file) throws ExecutionException, InterruptedException {
        log.info("Received file: {}", file.getOriginalFilename());
        CompletableFuture<Prompt1Result> responseFuture = questionnaireAnalysisService.generatePreIntakeAnalysis(file);
        return ResponseEntity.ok(responseFuture.get());
    }

    /**
     * Endpoint to retrieve Prompt1 results by patient ID.
     *
     * @param patientId the patient ID to search for.
     * @return a {@link ResponseEntity} containing a list of {@link Prompt1ResultEntity} or a not found status.
     */
    @GetMapping("/results/{patientId}")
    public ResponseEntity<List<Prompt1ResultEntity>> getPrompt1ResultByPatientId(@PathVariable("patientId") String patientId) {
        List<Prompt1ResultEntity> result = questionnaireAnalysisService.getPrompt1ResultByPatientId(patientId);
        if (result == null || result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }
}


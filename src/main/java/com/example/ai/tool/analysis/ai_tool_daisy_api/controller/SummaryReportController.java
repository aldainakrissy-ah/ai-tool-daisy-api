package com.example.ai.tool.analysis.ai_tool_daisy_api.controller;

import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.AiAnalysisResult;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.SummaryReportResult;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.SummaryReportService;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


/**
 * REST controller for handling summary report related requests.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai-tool-daisy")
@AllArgsConstructor
@CrossOrigin(origins = "*")
public class SummaryReportController {

    private final SummaryReportService summaryReportService;

    /**
     * Endpoint to generate a summary report based on AI analysis results.
     *
     * @param analysisResult the AI analysis result containing information about the professional, client, and document
     * @return a {@link ResponseEntity} containing the generated {@link SummaryReportResult} or a bad request status
     */
    @PostMapping("/summary-report")
    public ResponseEntity<SummaryReportResult> generateSummaryReport(@RequestBody AiAnalysisResult analysisResult) throws JsonProcessingException {
        if (analysisResult == null) {
            log.warn("Received null analysis result for summary report generation");
            return ResponseEntity.badRequest().build();
        }
        SummaryReportResult result = summaryReportService.generateSummaryReport(analysisResult);
        log.info("Successfully generated summary report for Professional: {}, Client: {}",
                result.getProfessionalName(), result.getClientName());

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * Endpoint to retrieve a summary report by document ID.
     *
     * @param documentId the ID of the document for which to retrieve the summary report
     * @return a {@link ResponseEntity} containing the {@link SummaryReportResult} or a not found status
     */
    @GetMapping("/summary-report/{documentId}")
    public ResponseEntity<SummaryReportResult> getSummaryReportByDocumentId(@PathVariable String documentId) throws JsonProcessingException {
        SummaryReportResult result = summaryReportService.getSummaryReportByDocumentId(documentId);
        return ResponseEntity.ok(result);
    }
}



package com.example.ai.tool.analysis.ai_tool_daisy_api.controller;

import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.AiAnalysisResult;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.SummaryReportResult;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.SummaryReportService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @PostMapping("/summary-report")
    public ResponseEntity<SummaryReportResult> generateSummaryReport(@RequestBody AiAnalysisResult analysisResult) {
        if (analysisResult == null) {
            log.warn("Received null analysis result for summary report generation");
            return ResponseEntity.badRequest().build();
        }
        SummaryReportResult result = summaryReportService.generateSummaryReport(analysisResult);
        log.info("Successfully generated summary report for Professional: {}, Client: {}",
                result.getProfessionalName(), result.getClientName());

        return ResponseEntity.ok(result);
    }
}



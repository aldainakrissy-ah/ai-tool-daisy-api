package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.SummaryReportEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.AiAnalysisResult;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.SummaryReportResult;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.SummaryReportRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.UUID;

/**
 * Service class responsible for generating summary reports based on AI analysis results.
 * This service interacts with the OpenAI API to create comprehensive summary reports
 * using predefined prompts and tools.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryReportService {

    private final OpenAIClient client;
    private final SummaryReportRepository summaryReportRepository;
    private static final String SUMMARY_REPORT_PROMPT_ID = "pmpt_69a1749bd50c819495ce2b629e5d98ab0d3efb8f8be9b2e0";
    private static final String SUMMARY_REPORT_PROMPT_VERSION = "9";
    private static final String VECTOR_STORE_ID = "vs_69796126184c819184a8093782ac87c7";

    /**
     * Generates a summary report based on the provided AI analysis result.
     * This method validates the input, constructs the necessary parameters for the OpenAI API call,
     * and processes the response to extract and return the summary report result.
     *
     * @param result the {@link AiAnalysisResult} containing the analysis data to be summarized
     * @return a {@link SummaryReportResult} containing the generated summary report and associated metadata
     * @throws IllegalArgumentException if the input result is null or missing required fields
     */
    @Transactional
    public SummaryReportResult generateSummaryReport(AiAnalysisResult result) throws JsonProcessingException {
        if(result == null) {
            throw new IllegalArgumentException("AiAnalysisResult cannot be null");
        }
        if(result.getProfessionalName() == null || result.getClientName() == null) {
            throw new IllegalArgumentException("Professional name and client name cannot be null");
        }

            String aiResultJson = result.toJson();
            //build parameters for OpenAI response creation
            ResponseCreateParams responseParams = buildResponseParams(aiResultJson);
            Response response = client.responses().create(responseParams);
            response.output();

            if(response.output().isEmpty()) {
                log.error("No output received from OpenAI for summary report generation");
                throw new RuntimeException("No valid response received from OpenAI API");
            }
            String summaryReportText = extractResponseText(response);

            log.info("Successfully generated summary report for Professional: {}, Client: {}",
                    result.getProfessionalName(), result.getClientName());

            // Generate document ID and save to database
            String documentId = "doc_" + UUID.randomUUID();
            saveSummaryReportToDatabase(documentId, summaryReportText, result.getPromptId());

            // Parse and return result with document ID
            SummaryReportResult summaryReportResult = SummaryReportResult.fromJson(summaryReportText);
            summaryReportResult.setDocumentId(documentId);

            return summaryReportResult;
        }


    /**
     * Builds the parameters required to create a response using the OpenAI API.
     * This includes setting the prompt, tools, and other configurations for generating the summary report.
     *
     * @param aiResultJson the JSON string representation of the AI analysis result to be used as input for the response generation
     * @return a {@link ResponseCreateParams} object containing all necessary parameters for creating a response
     */
    private ResponseCreateParams buildResponseParams(String aiResultJson) {
        ResponsePrompt prompt = ResponsePrompt.builder()
                .id(SUMMARY_REPORT_PROMPT_ID)
                .version(SUMMARY_REPORT_PROMPT_VERSION)
                .build();

        FileSearchTool fileSearchTool = FileSearchTool.builder()
                .addVectorStoreId(VECTOR_STORE_ID)
                .build();

        return ResponseCreateParams.builder()
                .temperature(0.0)
                .topP(1.0)
                .prompt(prompt)
                .tools(Collections.singletonList(Tool.ofFileSearch(fileSearchTool)))
                .store(true)
                .maxOutputTokens(6000)
                .include(Collections.singletonList(ResponseIncludable.FILE_SEARCH_CALL_RESULTS))
                .input(aiResultJson)
                .build();
    }

    /**
     * Extracts the response text from the OpenAI API response object.
     * This method navigates through the response structure to find the first available output text.
     *
     * @param response the {@link Response} object returned by the OpenAI API after creating a response
     * @return a string containing the extracted response text
     * @throws RuntimeException if no valid response is found
     */
    private String extractResponseText(Response response) {
        return response.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(msg -> msg.content().stream())
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No valid response text found in OpenAI API response"))
                .asOutputText()
                .text();
    }

    /**
     * Saves the summary report to the database.
     * Creates a {@link SummaryReportEntity} with the provided data and persists it.
     *
     * @param documentId the unique identifier for the summary report document
     * @param summaryReportJson the JSON string representation of the summary report
     * @param promptType the prompt type/ID used to generate the report
     */
    @Transactional
    private void saveSummaryReportToDatabase(String documentId, String summaryReportJson, String promptType) {

        SummaryReportEntity summaryReportEntity = SummaryReportEntity.builder()
                .documentId(documentId)
                .summaryReportJson(summaryReportJson)
                .promptType(promptType)
                .build();

        summaryReportRepository.save(summaryReportEntity);

        log.info("Successfully saved summary report to database with document ID: {}", documentId);
    }

    /**
     * Retrieves a summary report from the database based on the provided document ID.
     * This method queries the database for a {@link SummaryReportEntity} with the matching document ID,
     * extracts the summary report JSON, and converts it into a {@link SummaryReportResult} object.
     *
     * @param documentId the unique identifier for the summary report document to be retrieved
     * @return a {@link SummaryReportResult} containing the summary report data
     * @throws IllegalArgumentException if the document ID is null, empty, or no report is found
     * @throws JsonProcessingException if the JSON deserialization fails
     */
    @Transactional(readOnly = true)
    public SummaryReportResult getSummaryReportByDocumentId(String documentId) throws JsonProcessingException {
        if (documentId == null || documentId.trim().isEmpty()) {
            throw new IllegalArgumentException("Document ID cannot be null or empty");
        }

        SummaryReportEntity entity = summaryReportRepository.findByDocumentId(documentId);

        if (entity == null) {
            log.warn("Summary report not found for document ID: {}", documentId);
            throw new IllegalArgumentException("Summary report with document ID '" + documentId + "' not found");
        }

        log.info("Successfully retrieved summary report for document ID: {}", documentId);
        return SummaryReportResult.fromJson(entity.getSummaryReportJson());
    }
}
package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.Prompt1ResultEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.AiAnalysisResult;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.Prompt1ResultRepository;
import com.openai.client.OpenAIClient;
import com.openai.errors.OpenAIException;
import com.openai.models.responses.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for processing healthcare questionnaires using OpenAI's Response API.
 * Handles PDF extraction, AI analysis, and database persistence of results.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionnaireAnalysisService {

    private final OpenAIClient client;
    private final Prompt1ResultRepository prompt1ResultRepository;

    private static final String OPENAI_ERROR_MESSAGE = "OpenAI API analysis failed";
    private static final String NO_RESPONSE_ERROR = "No valid response received from OpenAI";
    private static final String PROMPT_ID = "pmpt_691db1ed039881908a001922e53791060b45ef8ce91f9109";
    private static final String PROMPT_VERSION = "174";
    private static final long MAX_TOTAL_SIZE = 10 * 1024 * 1024;
    private static final String VECTOR_STORE_ID = "vs_69796126184c819184a8093782ac87c7";

    /**
     * Analyzes multiple PDF files containing healthcare questionnaire data using OpenAI's Response API.
     * Combines the content of all PDFs into a single input for a comprehensive analysis in one API call.
     *
     * @param files the uploaded PDF files to analyze
     * @param promptType the type of prompt to use for analysis (e.g., "Prompt_1", "Prompt_2", "Prompt_3")
     * @return an {@link AiAnalysisResult} containing the combined analysis results for all files
     */
    public AiAnalysisResult generateCombinedPreIntakeAnalysis(List<MultipartFile> files, String promptType) {

        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("At least one PDF file must be provided");
        }
        if (promptType == null || promptType.isBlank()) {
            throw new IllegalArgumentException("Prompt type must be provided");
        }

        log.info("Starting combined analysis for {} file(s) with prompt type: {}", files.size(), promptType);

        long totalSize = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (totalSize > MAX_TOTAL_SIZE) {
            throw new IllegalArgumentException("Total size of uploaded files exceeds the maximum allowed limit of 10 MB");
        }
        files.forEach(this::validateFile);

        try {
            AiAnalysisResult result = analyzeWithOpenAI(files, promptType);
            persistResult(result);

            log.info("Combined analysis completed - Professional: {}, Patient: {}",
                    result.getProfessionalName(), result.getClientName());
            return result;

        } catch (OpenAIException e) {
            log.error("OpenAI API error during combined analysis", e);
            throw new RuntimeException(OPENAI_ERROR_MESSAGE + ": " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error during combined analysis", e);
            throw new RuntimeException("Analysis failed: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<Prompt1ResultEntity> getPreIntakeResult(String professionalId) {
        log.info("Fetching results for professional ID: {}", professionalId);
        return prompt1ResultRepository.findByProfessionalId(professionalId);
    }

    @Transactional(readOnly = true)
    public List<Prompt1ResultEntity> getPreIntakeResultByProfessionalIdAndPatientId(
            String professionalId, String patientId) {
        log.info("Fetching result for professional: {}, patient: {}", professionalId, patientId);
        return prompt1ResultRepository.findByProfessionalIdAndPatientId(professionalId, patientId);
    }

    @Transactional(readOnly = true)
    public List<Prompt1ResultEntity> getAllPreIntakeResults() {
        log.info("Fetching all pre-intake results");
        return prompt1ResultRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Prompt1ResultEntity> getPreIntakeResultByPatientId(String patientId) {
        log.info("Fetching result for patient: {}", patientId);
        return prompt1ResultRepository.findByPatientId(patientId);
    }

    @Transactional(readOnly = true)
    public List<Prompt1ResultEntity> getResultByPatientIdAndPromptType(String patientId, String promptType) {
        log.info("Fetching result for patient: {} with prompt type: {}", patientId, promptType);
        return prompt1ResultRepository.findByPatientIdAndPromptType(patientId, promptType);
    }

    @Transactional
    public void savePrompt1Result(AiAnalysisResult aiAnalysisResult) {
        log.info("Saving result for professional: {}, patient: {}",
                aiAnalysisResult.getProfessionalName(), aiAnalysisResult.getClientName());
        persistResult(aiAnalysisResult);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("PDF file must be provided and cannot be empty");
        }

        if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("File must be a PDF document");
        }
        if (file.getSize() > MAX_TOTAL_SIZE) {
            throw new IllegalArgumentException("Individual file size cannot exceed 10 MB");
        }
    }

    private AiAnalysisResult analyzeWithOpenAI(List<MultipartFile> files, String promptType) {
        ResponseCreateParams params = buildResponseParams(files, promptType);
        Response response = client.responses().create(params);

        log.debug("Received response from OpenAI API");

        String openAIResponse = extractResponseText(response);
        if (openAIResponse.trim().isEmpty()) {
            log.error("OpenAI returned empty response");
            throw new RuntimeException("OpenAI returned empty response");
        }

        return AiAnalysisResult.fromJson(openAIResponse);
    }

    /**
     * Builds ResponseCreateParams for OpenAI API call, passing PDFs directly as base64-encoded file data.
     * This avoids server-side text extraction and lets the model interpret the raw PDF layout.
     *
     * @param files      The uploaded PDF files to include in the request
     * @param promptType The type of prompt (e.g., "Prompt_1", "Prompt_2", "Prompt_3")
     * @return Configured ResponseCreateParams
     */
    private ResponseCreateParams buildResponseParams(List<MultipartFile> files, String promptType) {
        ResponsePrompt prompt = ResponsePrompt.builder()
                .id(PROMPT_ID)
                .version(PROMPT_VERSION)
                .build();

        FileSearchTool fileSearchTool = FileSearchTool.builder()
                .addVectorStoreId(VECTOR_STORE_ID)
                .build();

        List<ResponseInputContent> contentItems = files.stream()
                .map(file -> {
                    String base64Data = encodePdfToBase64(file);
                    return ResponseInputContent.ofInputFile(
                            ResponseInputFile.builder()
                                    .fileData("data:application/pdf;base64," + base64Data)
                                    .filename(file.getOriginalFilename())
                                    .build()
                    );
                })
                .collect(Collectors.toList());

        contentItems.add(ResponseInputContent.ofInputText(
                ResponseInputText.builder()
                        .text(promptType)
                        .build()
        ));

        ResponseInputItem inputItem = ResponseInputItem.ofMessage(
                ResponseInputItem.Message.builder()
                        .role(ResponseInputItem.Message.Role.USER)
                        .content(contentItems)
                        .build()
        );

        return ResponseCreateParams.builder()
                .temperature(0.0)
                .topP(1.0)
                .prompt(prompt)
                .tools(Collections.singletonList(Tool.ofFileSearch(fileSearchTool)))
                .store(true)
                .maxOutputTokens(6000)
                .include(Collections.singletonList(ResponseIncludable.FILE_SEARCH_CALL_RESULTS))
                .input(ResponseCreateParams.Input.ofResponse(Collections.singletonList(inputItem)))
                .build();
    }

    /**
     * Encodes a PDF MultipartFile to a Base64 string for direct inclusion in the OpenAI API request.
     *
     * @param file the PDF file to encode
     * @return Base64-encoded string of the PDF bytes
     */
    private String encodePdfToBase64(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            log.debug("Encoded '{}' to base64 ({} bytes)", file.getOriginalFilename(), bytes.length);
            return Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            log.error("Failed to encode PDF to base64: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("PDF encoding failed: " + e.getMessage(), e);
        }
    }


    private String extractResponseText(Response response) {
        return response.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(msg -> msg.content().stream())
                .findFirst()
                .orElseThrow(() -> new RuntimeException(NO_RESPONSE_ERROR))
                .asOutputText()
                .text();
    }

    void persistResult(AiAnalysisResult result) {
        try {
            Prompt1ResultEntity entity = new Prompt1ResultEntity();
            entity.setProfessionalId(result.getProfessionalName());
            entity.setPatientId(result.getClientName());
            entity.setPromptType(result.getPromptId());
            entity.setResultJson(result.toJson());
            prompt1ResultRepository.save(entity);

            log.debug("Persisted result to database for professional: {}, patient: {}",
                    result.getProfessionalName(), result.getClientName());

        } catch (Exception e) {
            log.error("Failed to persist result for professional: {}, patient: {}",
                    result.getProfessionalName(), result.getClientName(), e);
            throw new RuntimeException("Database persistence failed", e);
        }
    }
}

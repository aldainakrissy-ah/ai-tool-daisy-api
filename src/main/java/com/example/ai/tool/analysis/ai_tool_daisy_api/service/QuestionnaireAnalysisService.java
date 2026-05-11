package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.Prompt1ResultEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.AiAnalysisResult;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.Prompt1ResultRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${openai.questionnaire-analysis.prompt-id}")
    private String promptId;

    @Value("${openai.questionnaire-analysis.prompt-version}")
    private String promptVersion;

    @Value("${openai.vector-store-id}")
    private String vectorStoreId;

    /**
     * Analyzes multiple files (PDF or Word documents) containing healthcare questionnaire data using OpenAI's Response API.
     * Combines the content of all files into a single input for a comprehensive analysis in one API call.
     *
     * @param files the uploaded PDF or Word document files to analyze
     * @param promptType the type of prompt to use for analysis (e.g., "Prompt_1", "Prompt_2", "Prompt_3")
     * @return an {@link AiAnalysisResult} containing the combined analysis results for all files
     */
    public AiAnalysisResult generateCombinedPreIntakeAnalysis(List<MultipartFile> files, String promptType) throws JsonProcessingException {

        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("At least one PDF file must be provided");
        }
        if (promptType == null || promptType.isBlank()) {
            throw new IllegalArgumentException("Prompt type must be provided");
        }

        log.info("Starting combined analysis for {} file(s) with prompt type: {}", files.size(), promptType);

        files.forEach(this::validateFile);

            AiAnalysisResult result = analyzeWithOpenAI(files, promptType);
            persistResult(result);

            log.info("Combined analysis completed - Professional: {}, Patient: {}",
                    result.getProfessionalName(), result.getClientName());
            return result;

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
    public void savePrompt1Result(AiAnalysisResult aiAnalysisResult) throws JsonProcessingException {
        log.info("Saving result for professional: {}, patient: {}",
                aiAnalysisResult.getProfessionalName(), aiAnalysisResult.getClientName());
        persistResult(aiAnalysisResult);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must be provided and cannot be empty");
        }

        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("File must have a valid filename");
        }
        
        String lowerFilename = filename.toLowerCase();
        boolean isValidFormat = lowerFilename.endsWith(".pdf") || 
                                lowerFilename.endsWith(".doc") || 
                                lowerFilename.endsWith(".docx");
        
        if (!isValidFormat) {
            throw new IllegalArgumentException("File must be a PDF or Word document (.pdf, .doc, .docx)");
        }
    }

    private AiAnalysisResult analyzeWithOpenAI(List<MultipartFile> files, String promptType) throws JsonProcessingException {
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
     * Builds ResponseCreateParams for OpenAI API call, passing files directly as base64-encoded file data.
     * This avoids server-side text extraction and lets the model interpret the raw file layout.
     *
     * @param files The uploaded PDF or Word document files to include in the request
     * @param promptType The type of prompt (e.g., "Prompt_1", "Prompt_2", "Prompt_3")
     * @return Configured ResponseCreateParams
     */
    private ResponseCreateParams buildResponseParams(List<MultipartFile> files, String promptType) {
        ResponsePrompt prompt = ResponsePrompt.builder()
                .id(promptId)
                .version(promptVersion)
                .build();

        FileSearchTool fileSearchTool = FileSearchTool.builder()
                .addVectorStoreId(vectorStoreId)
                .build();

        List<ResponseInputContent> contentItems = files.stream()
                .map(file -> {
                    String base64Data;
                    try {
                        base64Data = encodePdfToBase64(file);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to encode PDF file: " + file.getOriginalFilename(), e);
                    }
                    return ResponseInputContent.ofInputFile(
                            ResponseInputFile.builder()
                                    .fileData("data:application/pdf;base64," + base64Data)
                                    .filename(Objects.requireNonNull(file.getOriginalFilename()))
                                    .build()
                    );
                })
                .collect(Collectors.toList());

        contentItems.add(ResponseInputContent.ofInputText(
                ResponseInputText.builder()
                        .text("Extract and analyze the following PDFs according to the prompt type: " + promptType)
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
     * Encodes a file (PDF or Word document) to a Base64 string for direct inclusion in the OpenAI API request.
     *
     * @param file the file to encode (PDF, .doc, or .docx)
     * @return Base64-encoded string of the file bytes
     * @throws IOException if encoding fails
     */
    private String encodePdfToBase64(MultipartFile file) throws IOException {
            byte[] bytes = file.getBytes();
            log.debug("Encoded '{}' to base64 ({} bytes)", file.getOriginalFilename(), bytes.length);
            return Base64.getEncoder().encodeToString(bytes);

    }

    /**
     * Extracts the response text from the OpenAI API response object.
     *
     * @param response the Response object from OpenAI
     * @return the extracted response text
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
     * Persists the analysis result to the database.
     *
     * @param result the AiAnalysisResult to persist
     * @throws JsonProcessingException 
     */
    void persistResult(AiAnalysisResult result) throws JsonProcessingException {
        Prompt1ResultEntity entity = new Prompt1ResultEntity();
        entity.setProfessionalId(result.getProfessionalName());
        entity.setPatientId(result.getClientName());
        entity.setPromptType(result.getPromptId());
        entity.setResultJson(result.toJson());
        prompt1ResultRepository.save(entity);

        log.debug("Persisted result to database for professional: {}, patient: {}",
                result.getProfessionalName(), result.getClientName());
    }
}

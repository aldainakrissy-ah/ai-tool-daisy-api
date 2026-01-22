package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.Prompt1ResultEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.AiAnalysisResult;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.Prompt1ResultRepository;
import com.openai.client.OpenAIClient;
import com.openai.errors.OpenAIException;
import com.openai.models.responses.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

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

    private static final String PDF_ERROR_MESSAGE = "Failed to process PDF file";
    private static final String EMPTY_CONTENT_ERROR = "Extracted PDF content is empty";
    private static final String OPENAI_ERROR_MESSAGE = "OpenAI API analysis failed";
    private static final String NO_RESPONSE_ERROR = "No valid response received from OpenAI";
    private static final String PROMPT_ID = "pmpt_69530227d8048195b28d15776355cad8048112a0d46452c6";
    private static final String PROMPT_VERSION = "82";
    private static final String VECTOR_STORE_ID = "vs_68ca996f20ec8191974741691b169cae";


    public AiAnalysisResult generatePreIntakeAnalysis(MultipartFile file) {
        validateFile(file);
        log.info("Starting analysis for file: {}", file.getOriginalFilename());

        try {
            String content = extractPdfContent(file);
            AiAnalysisResult result = analyzeWithOpenAI(content);
            persistResult(result);

            log.info("Analysis completed - Professional: {}, Patient: {}",
                    result.getProfessionalName(), result.getClientName());
            return result;

        } catch (OpenAIException e) {
            log.error("OpenAI API error for file: {}", file.getOriginalFilename(), e);
            throw new RuntimeException(OPENAI_ERROR_MESSAGE + ": " + e.getMessage(), e);
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
    }

    private String extractPdfContent(MultipartFile file) {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            String content = new PDFTextStripper().getText(document);

            if (content == null || content.trim().isEmpty()) {
                throw new RuntimeException(EMPTY_CONTENT_ERROR);
            }

            log.debug("Extracted {} characters from PDF", content.length());
            return content;

        } catch (IOException e) {
            log.error("PDF extraction failed for file: {}", file.getOriginalFilename(), e);
            throw new RuntimeException(PDF_ERROR_MESSAGE + ": " + file.getOriginalFilename(), e);
        }
    }

    private AiAnalysisResult analyzeWithOpenAI(String content) {
        ResponseCreateParams params = buildResponseParams(content);
        Response response = client.responses().create(params);

        log.debug("Received response from OpenAI API");

        String openAIResponse = extractResponseText(response);
        if (openAIResponse.trim().isEmpty()) {
            log.error("OpenAI returned empty response");
            throw new RuntimeException("OpenAI returned empty response");
        }

        return AiAnalysisResult.fromJson(openAIResponse);
    }

    private ResponseCreateParams buildResponseParams(String content) {
        ResponsePrompt prompt = ResponsePrompt.builder()
                .id(PROMPT_ID)
                .version(PROMPT_VERSION)
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
                .input("Extract and analyze the following healthcare questionnaire data:\n\n" + content)
                .build();
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

    private void persistResult(AiAnalysisResult result) {
        try {
            Prompt1ResultEntity entity = new Prompt1ResultEntity();
            entity.setProfessionalId(result.getProfessionalName());
            entity.setPatientId(result.getClientName());
            entity.setPromptType(result.getPromptType());
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

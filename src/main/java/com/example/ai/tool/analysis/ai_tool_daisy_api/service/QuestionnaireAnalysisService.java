package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.constant.QuestionnaireInstructions;
import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.Prompt1ResultEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1Result;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.Prompt1ResultRepository;
import com.openai.client.OpenAIClient;
import com.openai.errors.OpenAIException;

import com.openai.models.ChatModel;
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

    private static final String VECTOR_STORE_ID = "vs_68ca996f20ec8191974741691b169cae";
    private static final String PROMPT_ID = "pmpt_691ba79e09b08194a5ba301215448deb029b9fd4f52a8b2e";
    private static final String PROMPT_VERSION = "8";

    /**
     * Processes an uploaded PDF file containing healthcare questionnaire data.
     * Extracts text, sends to OpenAI for analysis, and persists the results.
     *
     * @param file the PDF file to process
     * @return analyzed questionnaire results
     * @throws IllegalArgumentException if the file is null or empty
     * @throws RuntimeException if PDF processing or AI analysis fails
     */
    @Transactional
    public Prompt1Result generatePreIntakeAnalysis(MultipartFile file) {
        validateFile(file);
        log.info("Processing file: {}", file.getOriginalFilename());

        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            String pdfContent = extractPdfContent(document);
            Prompt1Result result = analyzeWithOpenAI(pdfContent);
            persistResult(result);

            log.info("Successfully processed analysis for professional: {}, patient: {}",
                    result.getProfessionalName(), result.getClientName());

            return result;

        } catch (IOException e) {
            log.error("Error reading PDF file: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("Failed to read PDF file: " + e.getMessage(), e);
        } catch (OpenAIException e) {
            log.error("Error communicating with OpenAI API", e);
            throw new RuntimeException("OpenAI API error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all pre-intake analysis results for a specific professional.
     *
     * @param professionalId the professional's unique identifier
     * @return list of analysis results
     */
    @Transactional(readOnly = true)
    public List<Prompt1ResultEntity> getPreIntakeResult(String professionalId) {
        log.info("Fetching results for professional ID: {}", professionalId);
        return prompt1ResultRepository.findByProfessionalId(professionalId);
    }

    /**
     * Retrieves pre-intake analysis results for a specific professional and patient.
     *
     * @param professionalId the professional's unique identifier
     * @param patientId the patient's unique identifier
     * @return list of analysis results matching both IDs
     */
    @Transactional(readOnly = true)
    public List<Prompt1ResultEntity> getPreIntakeResultByProfessionalIdAndPatientId(
            String professionalId, String patientId) {
        log.info("Fetching result for professional: {}, patient: {}", professionalId, patientId);
        return prompt1ResultRepository.findByProfessionalIdAndPatientId(professionalId, patientId);
    }

    /**
     * Persists a Prompt1Result to the database.
     *
     * @param prompt1Result the analysis result to save
     */
    @Transactional
    public void savePrompt1Result(Prompt1Result prompt1Result) {
        log.info("Saving result for professional: {}, patient: {}",
                prompt1Result.getProfessionalName(), prompt1Result.getClientName());
        persistResult(prompt1Result);
    }

    // Private helper methods

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("PDF file cannot be null or empty");
        }
    }

    private String extractPdfContent(PDDocument document) throws IOException {
        PDFTextStripper pdfStripper = new PDFTextStripper();
        return pdfStripper.getText(document);
    }

    private Prompt1Result analyzeWithOpenAI(String content) {
        ResponsePrompt prompt = ResponsePrompt.builder()
                .id(PROMPT_ID)
                .version(PROMPT_VERSION)
                .build();

        log.debug("Using Prompt ID: {}, Version: {}", PROMPT_ID, PROMPT_VERSION);

        StructuredResponseCreateParams<Prompt1Result> params = StructuredResponseCreateParams
                .<Prompt1Result>builder()
                .model(ChatModel.GPT_5)
                .prompt(prompt)
                .addFileSearchTool(Collections.singletonList(VECTOR_STORE_ID))
                .input(QuestionnaireInstructions.PROMPT1_DAISY + content)
                .text(Prompt1Result.class)
                .build();

        StructuredResponse<Prompt1Result> response = client.responses().create(params);
        log.debug("Received response from OpenAI");

        return response.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(msg -> msg.content().stream())
                .map(StructuredResponseOutputMessage.Content::asOutputText)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No valid response from OpenAI API"));
    }

    private void persistResult(Prompt1Result result) {
        Prompt1ResultEntity entity = new Prompt1ResultEntity();
        entity.setProfessionalId(result.getProfessionalName());
        entity.setPatientId(result.getClientName());
        entity.setResultJson(result.toJson());
        prompt1ResultRepository.save(entity);
    }
}
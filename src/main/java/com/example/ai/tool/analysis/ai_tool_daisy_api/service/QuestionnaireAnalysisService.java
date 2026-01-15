package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.Prompt1ResultEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1Result;
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
 * Handles PDF extraction, teleonic AI analysis, and database persistence of results.
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


    /**
     * Processes uploaded PDF file containing healthcare questionnaire data.
     * Extracts text, analyzes with OpenAI, and persists results.
     *
     * @param file the uploaded PDF file
     * @return analyzed questionnaire results
     * @throws IllegalArgumentException if file is null or empty
     * @throws RuntimeException if PDF processing or AI analysis fails
     */
    @Transactional
    public Prompt1Result generatePreIntakeAnalysis(MultipartFile file) {
        validateFile(file);

        log.info("Starting analysis for file: {}", file.getOriginalFilename());

        try {
            String content = extractPdfContent(file);
            Prompt1Result result = analyzeWithOpenAI(content);
            persistResult(result);

            log.info("Analysis completed - Professional: {}, Patient: {}",
                    result.getProfessionalName(), result.getClientName());
            return result;

        } catch (OpenAIException e) {
            log.error("OpenAI API error for file: {}", file.getOriginalFilename(), e);
            throw new RuntimeException(OPENAI_ERROR_MESSAGE + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("Unexpected error processing file: {}", file.getOriginalFilename(), e);
            throw e;
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
     * Retrieves all pre-intake analysis results.
     *
     * @return list of all analysis results
     */
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


    /**
     * Validates that the uploaded file is not null or empty.
     *
     * @param file the file to validate
     * @throws IllegalArgumentException if file is invalid
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("PDF file must be provided and cannot be empty");
        }

        if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("File must be a PDF document");
        }
    }

    /**
     * Extracts text content from a PDF file.
     *
     * @param file the PDF file to extract text from
     * @return extracted text content
     * @throws RuntimeException if PDF extraction fails or content is empty
     */
    private String extractPdfContent(MultipartFile file) {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String content = stripper.getText(document);

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

    /**
     * Sends extracted content to OpenAI for teleonic analysis.
     * Uses structured output with strict JSON schema to prevent hallucinations and ensure schema compliance.
     * Configuration matches the DAISY BiomatrixAI Framework requirements.
     *
     * @param content the extracted PDF text content
     * @return analyzed result as Prompt1Result
     * @throws RuntimeException if OpenAI analysis fails or returns no response
     */
    private Prompt1Result analyzeWithOpenAI(String content) {

        ResponsePrompt prompt = ResponsePrompt.builder().id("pmpt_69530227d8048195b28d15776355cad8048112a0d46452c6")
                .version("65")
                .build();

        List<ResponseIncludable> includes = Collections.singletonList(ResponseIncludable.FILE_SEARCH_CALL_RESULTS);

        FileSearchTool fileSearchTool = FileSearchTool.builder()
                .addVectorStoreId("vs_68ca996f20ec8191974741691b169cae")
                .build();

        List<Tool> tools = Collections.singletonList(Tool.ofFileSearch(fileSearchTool));
        ResponseCreateParams params = ResponseCreateParams
                .builder()
                .temperature(0.0)
                .topP(1.0)
                .prompt(prompt)
                .tools(tools)
                .store(true)
                .maxOutputTokens(6000)
                .include(includes)
                .input("PROMPT 1: Pre-Intake " +
                        "QUESTIONNAIRE DATA:\n" + content)
                .build();

        System.out.println("params: " + params);

        Response response = client.responses().create(params);

        log.debug("Received response from OpenAI API");

        String res =  response.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(msg -> msg.content().stream()).findFirst()
                .orElseThrow(() -> new RuntimeException(NO_RESPONSE_ERROR)).asOutputText().text();
        return Prompt1Result.fromJson(res);
    }

    /**
     * Persists analysis result to database.
     *
     * @param result the analysis result to persist
     * @throws RuntimeException if persistence fails
     */
    private void persistResult(Prompt1Result result) {
        try {
            Prompt1ResultEntity entity = new Prompt1ResultEntity();
            entity.setProfessionalId(result.getProfessionalName());
            entity.setPatientId(result.getClientName());
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
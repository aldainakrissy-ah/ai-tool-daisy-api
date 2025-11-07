package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Service class responsible for processing PDF files and interacting with the OpenAI API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionnaireAnalysisService {

    private final OpenAIClient client;

    private final Prompt1ResultRepository prompt1ResultRepository;

    public static final String FILE_ID = "vs_68ca996f20ec8191974741691b169cae";

    /**
     * Processes the uploaded PDF file, extracts its content, and sends it to the OpenAI API for analysis.
     *
     * @param file the uploaded PDF file as a {@link MultipartFile}.
     * @return the response from the OpenAI API as a {@link String}.
     */
    @Transactional
    @Async
    public Prompt1Result generatePreIntakeAnalysis(MultipartFile file, String professionalId, String patientId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("PDF file cannot be empty");
        }

        log.info("Processing file: {}", file.getOriginalFilename());

        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper pdfStripper = new PDFTextStripper();
            String content = pdfStripper.getText(document);

            StructuredResponseCreateParams<Prompt1Result> params = StructuredResponseCreateParams.<Prompt1Result>builder()
                    .model(ChatModel.GPT_5)
                    .addFileSearchTool(Collections.singletonList(FILE_ID))
                    .input("Extract intake and demographic data; produce one TPD hypothesis; map up to 3 primary fields; report only concretely triggered clusters using validated HETA mappings; follow the template-only output and validation checks.. Here is the content:\n" + content)
                    .text(Prompt1Result.class)
                    .build();

            StructuredResponse<Prompt1Result> response = client.responses().create(params);
            log.debug("OpenAI response: {}", response);

            return response.output().stream()
                    .flatMap(item -> item.message().stream())
                    .flatMap(msg -> msg.content().stream())
                    .map(StructuredResponseOutputMessage.Content::asOutputText).findFirst().map(prompt1Result -> {
                        Prompt1ResultEntity prompt1ResultEntity = new Prompt1ResultEntity();
                        prompt1ResultEntity.setProfessionalId(professionalId);
                        prompt1ResultEntity.setPatientId(patientId);
                        prompt1ResultEntity.setResultJson(prompt1Result.toJson());
                        prompt1ResultRepository.saveAsJson(prompt1Result.getProfessionalId(), prompt1Result.getPatientId(), prompt1Result.toJson());
                        return prompt1Result;
                    }).orElseThrow(() -> new RuntimeException("No valid response from OpenAI API"));


        } catch (IOException | OpenAIException e) {
            log.error("Error processing PDF file for healthcare analysis", e);
            throw new RuntimeException("Error processing PDF file for healthcare analysis: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves Prompt1 results by patient ID from the database.
     *
     * @param professionalId the patient ID to search for.
     * @return a list of {@link Prompt1ResultEntity} matching the patient ID.
     */
    @Transactional
    public List<Prompt1ResultEntity> getPreIntakeResult(String professionalId) {
        log.info("Fetching Prompt1 results for professional id: {}", professionalId);
        return prompt1ResultRepository.findByProfessionalId(professionalId);
    }
    /**
     * Retrieves Prompt1 result by professional ID and patient ID from the database.
     *
     * @param professionalId the professional ID to search for.
     * @param patientId the patient ID to search for.
     * @return a list of {@link Prompt1ResultEntity} matching the professional ID and patient ID.
     */
    @Transactional
    public List<Prompt1ResultEntity> getPreIntakeResultByProfessionalIdAndPatientId(String professionalId, String patientId) {
        log.info("Fetching Prompt1 result for professional id: {} and patient id: {}", professionalId, patientId);
        return prompt1ResultRepository.findByProfessionalIdAndPatientId(professionalId, patientId);
    }

    /**
     * Saves a Prompt1 result to the database.
     *
     * @param prompt1Result the {@link Prompt1Result} to be saved.
     */
    @Transactional
    public void savePrompt1Result(Prompt1Result prompt1Result) {
        log.info("Saving Prompt1 result for professional id: {} and patient id: {}", prompt1Result.getProfessionalId(), prompt1Result.getPatientId());
        Prompt1ResultEntity prompt1ResultEntity = new  Prompt1ResultEntity();
        prompt1ResultEntity.setProfessionalId(prompt1Result.getProfessionalId());
        prompt1ResultEntity.setPatientId(prompt1Result.getPatientId());
        prompt1ResultEntity.setResultJson(prompt1Result.toJson());
        prompt1ResultRepository.save(prompt1ResultEntity);
    }
}
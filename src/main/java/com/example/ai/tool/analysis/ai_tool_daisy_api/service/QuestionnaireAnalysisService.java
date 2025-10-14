package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.Prompt1ResultEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1Result;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.Prompt1ResultRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.errors.OpenAIException;
import com.openai.models.ChatModel;

import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import com.openai.models.responses.StructuredResponseOutputMessage;
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
import java.util.concurrent.CompletableFuture;

/**
 * Service class responsible for processing PDF files and interacting with the OpenAI API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionnaireAnalysisService {

    private final OpenAIClient client;

    private final Prompt1ResultRepository prompt1ResultRepository;

    private final ObjectMapper objectMapper;

    public static final String FILE_ID = "vs_68ca996f20ec8191974741691b169cae";

    /**
     * Processes the uploaded PDF file, extracts its content, and sends it to the OpenAI API for analysis.
     *
     * @param file the uploaded PDF file as a {@link MultipartFile}.
     * @return the response from the OpenAI API as a {@link String}.
     */
    @Async
    @Transactional
    public CompletableFuture<Prompt1Result> generatePreIntakeAnalysis(MultipartFile file) {
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
                    .input("Process the intake questionnaire and demographic data with prompt 1. Here is the content:\n" + content)
                    .text(Prompt1Result.class)
                    .build();
            StructuredResponse<Prompt1Result> response = client.responses().create(params);
            log.debug("OpenAI response: {}", response);

            return CompletableFuture.supplyAsync(() -> response.output().stream()
                    .flatMap(item -> item.message().stream())
                    .flatMap(msg -> msg.content().stream())
                    .map(StructuredResponseOutputMessage.Content::asOutputText)
                    .findFirst().map(text -> {
                        Prompt1ResultEntity entity = new Prompt1ResultEntity();
                        entity.setPatientId(text.getPatientId());
                        try {
                            entity.setResultJson(objectMapper.writeValueAsString(text));
                        } catch (JsonProcessingException e) {
                            log.error("Error serializing Prompt1Result to JSON", e);
                            throw new RuntimeException("Serialization error", e);
                        }
                        prompt1ResultRepository.save(entity);
                        return text;
                    })
                    .orElseThrow(() -> new RuntimeException("No valid response from OpenAI"))).thenApply(result -> result);


        } catch (IOException | OpenAIException e) {
            log.error("Error processing PDF file for healthcare analysis", e);
            throw new RuntimeException("Error processing PDF file for healthcare analysis: " + e.getMessage(), e);
        }
    }
    /**
     * Retrieves Prompt1 results by patient ID from the database.
     *
     * @param patientId the patient ID to search for.
     * @return a list of {@link Prompt1ResultEntity} matching the patient ID.
     */
    @Transactional
    public List<Prompt1ResultEntity> getPrompt1ResultByPatientId(String patientId) {
        log.info("Fetching Prompt1 results for patient id: {}", patientId);
        return prompt1ResultRepository.findByPatientId(patientId);
    }
}
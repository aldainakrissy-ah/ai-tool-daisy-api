package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.Prompt1ResultEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.AiAnalysisResult;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1Result;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt2Result;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.Prompt1ResultRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
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
    
    // Dedicated credentials for Prompt 1 analysis
    @Value("${openai.prompt1.prompt-id}")
    private String prompt1Id;
    @Value("${openai.prompt1.prompt-version}")
    private String prompt1Version;

    // Dedicated credentials for Prompt 2 analysis
    @Value("${openai.prompt2.prompt-id}")
    private String prompt2Id;
    @Value("${openai.prompt2.prompt-version}")
    private String prompt2Version;

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

    /**
     * Analyzes files using the dedicated Prompt 1 credentials.
     *
     * @param files the uploaded PDF or Word document files to analyze
     * @return prompt 1 analysis result
     * @throws JsonProcessingException if JSON processing fails
     */
    public Prompt1Result generatePrompt1Analysis(List<MultipartFile> files) throws JsonProcessingException {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("At least one file must be provided");
        }

        log.info("Starting Prompt1 analysis for {} file(s)", files.size());
        files.forEach(this::validateFile);

        Prompt1Result result = analyzeWithPrompt1(files);
        if (result.getPromptId() == null) {
            result.setPromptId("Prompt_1");
        }
        persistPrompt1Result(result);
        log.info("Prompt1 analysis completed - Professional: {}, Patient: {}",
                result.getProfessionalName(), result.getClientName());
        return result;
    }

    private Prompt1Result analyzeWithPrompt1(List<MultipartFile> files) throws JsonProcessingException {
        ResponseCreateParams params = buildPrompt1ResponseParams(files);
        Response response = client.responses().create(params);

        String openAIResponse = extractResponseText(response);
        if (openAIResponse.trim().isEmpty()) {
            throw new RuntimeException("OpenAI returned empty response for Prompt1");
        }
        return Prompt1Result.fromJson(openAIResponse);
    }

    private ResponseCreateParams buildPrompt1ResponseParams(List<MultipartFile> files) {
        ResponsePrompt prompt = ResponsePrompt.builder()
                .id(prompt1Id)
                .version(prompt1Version)
                .build();

        return buildAnalysisResponseParams(files, prompt,
                "Extract and analyze the following PDFs according to the prompt type: Prompt_1");
    }

    /**
     * Analyzes files using the dedicated Prompt 2 credentials.
     *
     * @param files the uploaded PDF or Word document files to analyze
     * @return prompt 2 analysis result
     * @throws JsonProcessingException if JSON processing fails
     */
    public Prompt2Result generatePrompt2Analysis(List<MultipartFile> files) throws JsonProcessingException {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("At least one file must be provided");
        }

        log.info("Starting Prompt2 analysis for {} file(s)", files.size());
        files.forEach(this::validateFile);

        Prompt2Result result = analyzeWithPrompt2(files);
        if (result.getPromptId() == null) {
            result.setPromptId("Prompt_2");
        }
        persistPrompt2Result(result);
        log.info("Prompt2 analysis completed - Professional: {}, Patient: {}",
                result.getProfessionalName(), result.getClientName());
        return result;
    }

    private Prompt2Result analyzeWithPrompt2(List<MultipartFile> files) throws JsonProcessingException {
        ResponseCreateParams params = buildPrompt2ResponseParams(files);
        Response response = client.responses().create(params);

        String openAIResponse = extractResponseText(response);
        if (openAIResponse.trim().isEmpty()) {
            throw new RuntimeException("OpenAI returned empty response for Prompt2");
        }
        return Prompt2Result.fromJson(openAIResponse);
    }

    private ResponseCreateParams buildPrompt2ResponseParams(List<MultipartFile> files) {
        ResponsePrompt prompt = ResponsePrompt.builder()
                .id(prompt2Id)
                .version(prompt2Version)
                .build();

        return buildAnalysisResponseParams(files, prompt,
                "Extract and analyze the following PDFs according to the prompt type: Prompt_2");
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
     * Builds ResponseCreateParams for OpenAI API call. See {@link #buildAnalysisResponseParams}
     * for how each file is converted to input content.
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

        return buildAnalysisResponseParams(files, prompt,
                "Extract and analyze the following PDFs according to the prompt type: " + promptType);
    }

    /**
     * Builds ResponseCreateParams shared across all prompt types. Differs only by which
     * {@link ResponsePrompt} (id/version) and instruction text the caller supplies.
     * <p>
     * Each file is sent the cheapest way that preserves analysis quality: text-only PDFs are
     * extracted to plain text (a fraction of the tokens of vision parsing), while PDFs containing
     * charts/graphs and Word documents are sent as base64 file data for the model to interpret visually.
     *
     * @param files the uploaded PDF or Word document files to include in the request
     * @param prompt the resolved prompt id/version to call
     * @param instructionText instruction appended after the file content items
     * @return Configured ResponseCreateParams
     */
    private ResponseCreateParams buildAnalysisResponseParams(List<MultipartFile> files, ResponsePrompt prompt, String instructionText) {
        FileSearchTool fileSearchTool = FileSearchTool.builder()
                .addVectorStoreId(vectorStoreId)
                .build();

        List<ResponseInputContent> contentItems = files.stream()
                .map(this::toResponseInputContent)
                .collect(Collectors.toList());

        contentItems.add(ResponseInputContent.ofInputText(
                ResponseInputText.builder()
                        .text(instructionText)
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
                .input(ResponseCreateParams.Input.ofResponse(Collections.singletonList(inputItem)))
                .build();
    }

    /**
     * Converts a file to the cheapest input content item that preserves analysis quality.
     * Text-only PDFs are sent as extracted plain text; PDFs containing raster images
     * (charts/graphs) and non-PDF files are sent as base64 file data for vision parsing.
     *
     * @param file the uploaded PDF or Word document file
     * @return the resolved {@link ResponseInputContent} for this file
     */
    private ResponseInputContent toResponseInputContent(MultipartFile file) {
        String filename = Objects.requireNonNull(file.getOriginalFilename());

        if (isPdf(filename)) {
            String extractedText = tryExtractPlainText(file);
            if (extractedText != null) {
                return ResponseInputContent.ofInputText(
                        ResponseInputText.builder()
                                .text("=== " + filename + " ===\n" + extractedText)
                                .build()
                );
            }
        }
        return toFileInputContent(file, filename);
    }

    private boolean isPdf(String filename) {
        return filename.toLowerCase().endsWith(".pdf");
    }

    /**
     * Extracts plain text from a PDF, but only when the PDF contains no raster images.
     * A PDF with embedded images (charts, graphs, scanned pages) needs vision parsing to be
     * interpreted correctly, so this returns null to signal the caller to fall back to
     * sending the raw file instead.
     *
     * @param file the PDF file to inspect
     * @return the extracted text, or null if the PDF is image/chart-heavy or extraction fails
     */
    private String tryExtractPlainText(MultipartFile file) {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            if (containsRasterImage(document)) {
                log.debug("'{}' contains chart/graph images; sending as file for vision parsing", file.getOriginalFilename());
                return null;
            }

            String text = new PDFTextStripper().getText(document).trim();
            if (text.isEmpty()) {
                return null;
            }

            log.debug("Using extracted text for '{}' ({} chars)", file.getOriginalFilename(), text.length());
            return text;
        } catch (IOException e) {
            log.warn("Failed to inspect '{}' for text extraction, falling back to file input: {}",
                    file.getOriginalFilename(), e.getMessage());
            return null;
        }
    }

    private boolean containsRasterImage(PDDocument document) throws IOException {
        for (PDPage page : document.getPages()) {
            PDResources resources = page.getResources();
            if (resources == null) {
                continue;
            }
            for (COSName xObjectName : resources.getXObjectNames()) {
                if (resources.getXObject(xObjectName) instanceof PDImageXObject) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Encodes a file to a Base64 data URI for direct inclusion in the OpenAI API request.
     *
     * @param file the file to encode (PDF, .doc, or .docx)
     * @param filename the file's original filename, used to resolve its MIME type
     * @return the resolved {@link ResponseInputContent} carrying the base64 file data
     */
    private ResponseInputContent toFileInputContent(MultipartFile file, String filename) {
        String base64Data;
        try {
            base64Data = Base64.getEncoder().encodeToString(file.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode file: " + filename, e);
        }
        log.debug("Encoded '{}' to base64 for vision parsing", filename);

        return ResponseInputContent.ofInputFile(
                ResponseInputFile.builder()
                        .fileData("data:" + resolveMimeType(filename) + ";base64," + base64Data)
                        .filename(filename)
                        .build()
        );
    }

    /**
     * Resolves the MIME type from a filename's extension. The OpenAI API needs the correct
     * MIME type in the data URI to parse Word documents - PDFs were previously sent with the
     * same hardcoded "application/pdf" type regardless of actual file type.
     */
    private String resolveMimeType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (lower.endsWith(".doc")) {
            return "application/msword";
        }
        return "application/pdf";
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
        persistEntity(result.getProfessionalName(), result.getClientName(), result.getPromptId(), result.toJson());
    }

    void persistPrompt1Result(Prompt1Result result) throws JsonProcessingException {
        persistEntity(result.getProfessionalName(), result.getClientName(), result.getPromptId(), result.toJson());
    }

    void persistPrompt2Result(Prompt2Result result) throws JsonProcessingException {
        persistEntity(result.getProfessionalName(), result.getClientName(), result.getPromptId(), result.toJson());
    }

    private void persistEntity(String professionalName, String clientName, String promptType, String resultJson) {
        Prompt1ResultEntity entity = new Prompt1ResultEntity();
        entity.setProfessionalId(professionalName);
        entity.setPatientId(clientName);
        entity.setPromptType(promptType);
        entity.setResultJson(resultJson);
        prompt1ResultRepository.save(entity);

        log.debug("Persisted result to database for professional: {}, patient: {}", professionalName, clientName);
    }
}

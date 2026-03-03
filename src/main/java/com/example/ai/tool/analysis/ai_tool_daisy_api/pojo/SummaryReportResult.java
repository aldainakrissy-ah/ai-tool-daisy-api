package com.example.ai.tool.analysis.ai_tool_daisy_api.pojo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * POJO representing the AI summary report result for Prompt 2.
 * Contains comprehensive analysis including integrative fields, HETA analysis,
 * questionnaire findings, and various analytical modules.
 * <p>
 * This class supports JSON serialization/deserialization and follows the DAISY BiomatrixAI
 * Framework schema for healthcare analysis reporting.
 * </p>
 *
 * @author DAISY AI Analysis API
 * @version 1.0
 * @since 2026-03-02
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SummaryReportResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier for the summary report document.
     * Generated automatically when the report is created and saved.
     * Format: SUMMARY_REPORT_{YEAR}_{CLIENT}_{PROFESSIONAL}_{TIMESTAMP}
     * Example: SUMMARY_REPORT_2026_ThorwaldVene_WimGelderblom_20260302143052
     */
    @JsonProperty("document_id")
    private String documentId;

    /**
     * Unique identifier for the prompt used in the analysis (e.g., "Prompt_2").
     * This field identifies which specific prompt version was executed.
     */
    @JsonProperty("prompt_id")
    private String promptId;

    /**
     * Client/patient name identifier.
     * Used to track analysis results for specific clients.
     * This field is required and cannot be blank.
     */
    @NotBlank(message = "Client name is required")
    @JsonProperty("client_name")
    private String clientName;

    /**
     * Professional/practitioner name identifier.
     * Used to track analysis results for specific healthcare professionals.
     */
    @JsonProperty("professional_name")
    private String professionalName;

    /**
     * Executive summary providing a high-level overview of the analysis.
     * Summarizes key findings and patterns identified in the client's data.
     */
    @JsonProperty("executive_summary")
    private String executiveSummary;

    /**
     * List of integrative fields (Integrative Endoteleons) identified in the analysis.
     * Each field represents a systemic coordination pattern in the individual's health.
     */
    @Valid
    @JsonProperty("integrative_fields")
    private List<IntegrativeField> integrativeFields;

    /**
     * HETA (Health Endoteleon Tuning Agents) analysis results.
     * Contains detailed analysis of specific HETAs and their roles in the client's health pattern.
     */
    @Valid
    @JsonProperty("heta_analysis")
    private List<HetaAnalysisItem> hetaAnalysis;

    /**
     * Questionnaire findings and interpretations.
     * Analysis of responses from various HETA-related questionnaire modules.
     */
    @Valid
    @JsonProperty("questionnaire_findings")
    private List<QuestionnaireFinding> questionnaireFindings;

    /**
     * Summary results from various analysis modules (HMA, HRV, LAB).
     * Provides integrated interpretation from multiple data sources.
     */
    @Valid
    @JsonProperty("analysis_modules")
    private AnalysisModules analysisModules;

    /**
     * Key findings from the overall analysis.
     * Highlights the most significant patterns and observations.
     */
    @JsonProperty("key_findings")
    private List<String> keyFindings;

    /**
     * Focus points for intake consultation.
     * Specific areas to verify or explore further during client intake.
     */
    @JsonProperty("intake_focus_points")
    private List<String> intakeFocusPoints;

    /**
     * Limitations of the current analysis.
     * Identifies gaps in data or areas where conclusions should be treated with caution.
     */
    @JsonProperty("limitations")
    private List<String> limitations;

    /**
     * Thread-safe ObjectMapper configured for SummaryReportResult serialization/deserialization.
     * Configuration:
     * - Ignores unknown properties to handle API evolution
     * - Accepts single values as arrays for flexible input handling
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

    /*
     * Represents an Integrative Field (Integrative Endoteleon) identified in the analysis.
     * Integrative fields coordinate systemic responses across multiple physiological systems.
     */

    /**
     * Represents an Integrative Field (Integrative Endoteleon) identified in the analysis.
     * Integrative fields coordinate systemic responses across multiple physiological systems.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class IntegrativeField {

        /**
         * Title of the Integrative Endoteleon.
         * Example: "Stress Adaptation Integrative Field Endoteleon of the Individual"
         */
        @JsonProperty("ie_title")
        private String ieTitle;

        /**
         * Description of the integrative field's function and coordination role.
         * Explains how this field harmonizes physiological processes.
         */
        @JsonProperty("description")
        private String description;

        /**
         * Explanation of this field's role in the current analysis.
         * Describes why this field is relevant to the client's health pattern.
         */
        @JsonProperty("role_in_analysis")
        private String roleInAnalysis;

        /**
         * Reference identifier for documentation source.
         * Example: "04_BiomatrixAI_Appendix_22"
         */
        @JsonProperty("document_reference_id")
        private String documentReferenceId;
    }

    /**
     * Represents a HETA (Health Endoteleon Tuning Agent) analysis item.
     * HETAs are specific biochemical agents that influence health patterns.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HetaAnalysisItem {

        /**
         * Unique identifier for the HETA.
         * Example: "HETA-5-COR" for Cortisol, "HETA-14-MEL" for Melatonine
         */
        @JsonProperty("heta_id")
        private String hetaId;

        /**
         * Common name of the HETA.
         * Examples: "Cortisol", "Melatonine", "Noradrenaline"
         */
        @JsonProperty("name")
        private String name;

        /**
         * Functional context describing the HETA's role in the body.
         * Explains the biochemical and physiological functions.
         */
        @JsonProperty("functional_context")
        private String functionalContext;

        /**
         * Observed role of this HETA in the client's current health pattern.
         * Describes how this HETA is expressed in the specific analysis.
         */
        @JsonProperty("observed_role")
        private String observedRole;

        /**
         * Reference identifier for documentation source.
         * Example: "06_Gevalideerde_HETA_lijst_V10"
         */
        @JsonProperty("document_reference_id")
        private String documentReferenceId;
    }

    /**
     * Represents findings from a specific questionnaire module.
     * Links questionnaire responses to their interpretations.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QuestionnaireFinding {

        /**
         * Identifier for the questionnaire module.
         * Examples: "heta-module:ACTH", "heta-module:Melatonine"
         */
        @JsonProperty("questionnaire")
        private String questionnaire;

        /**
         * Interpretation of the questionnaire results.
         * Explains patterns and their significance in the health analysis.
         */
        @JsonProperty("interpretation")
        private String interpretation;

        /**
         * Reference identifier for documentation source.
         * Example: "07_Overzicht_aanvullende_vragenlijsten_v5"
         */
        @JsonProperty("document_reference_id")
        private String documentReferenceId;
    }

    /**
     * Summary results from various analysis modules.
     * Combines findings from HMA, HRV, and laboratory analyses.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AnalysisModules {

        /**
         * Summary of Hair Mineral Analysis (HMA) findings.
         * Describes mineral patterns, ratios, and their implications for stress adaptation.
         */
        @JsonProperty("hma_summary")
        private String hmaSummary;

        /**
         * Summary of Heart Rate Variability (HRV) analysis.
         * Describes autonomic nervous system balance and recovery patterns.
         */
        @JsonProperty("hrv_summary")
        private String hrvSummary;

        /**
         * Summary of laboratory test results.
         * May be empty if no lab data is available for this analysis.
         */
        @JsonProperty("lab_summary")
        private String labSummary;
    }

    /*
     * Serializes this SummaryReportResult object to JSON string.
     * Includes contextual logging for debugging and traceability.
     *
     * @return JSON string representation of this object
     * @throws JsonSerializationException if JSON serialization fails
     */

    /**
     * Serializes this SummaryReportResult object to JSON string.
     * Includes contextual logging for debugging and traceability.
     *
     * @return JSON string representation of this object
     *
     */
    public String toJson() throws JsonProcessingException {

            String json = MAPPER.writeValueAsString(this);
            log.debug("Successfully serialized SummaryReportResult for client: {}, professional: {}, prompt: {}",
                    clientName, professionalName, promptId);
            return json;
        }

    /**
     * Deserializes JSON string to SummaryReportResult object.
     * Validates input and includes contextual logging for debugging.
     *
     * @param json JSON string to deserialize
     * @return SummaryReportResult object created from JSON
     * @throws IllegalArgumentException if json parameter is null or empty
     */
    public static SummaryReportResult fromJson(String json) throws JsonProcessingException {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON string cannot be null or empty");
        }

            SummaryReportResult result = MAPPER.readValue(json, SummaryReportResult.class);
            log.debug("Successfully deserialized SummaryReportResult for client: {}, professional: {}, prompt: {}",
                    result.getClientName(), result.getProfessionalName(), result.getPromptId());
            return result;
    }
}

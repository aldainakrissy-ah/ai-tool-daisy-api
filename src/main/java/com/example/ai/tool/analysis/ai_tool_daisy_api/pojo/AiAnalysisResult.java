package com.example.ai.tool.analysis.ai_tool_daisy_api.pojo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
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
 * POJO representing the AI analysis result for Phase 1 pre-intake questionnaire.
 * Contains teleonic analysis sections (A1-A6) and recommended questionnaires (B1-B3).
 * <p>
 * This class supports JSON serialization/deserialization and follows the DAISY BiomatrixAI
 * Framework schema for healthcare questionnaire analysis.
 * </p>
 *
 * @author DAISY AI Analysis API
 * @version 1.0
 * @since 2026-01-22
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiAnalysisResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier for the prompt used in the analysis (e.g., "Prompt_1").
     * This field identifies which specific prompt version was executed.
     */
    @JsonProperty("prompt_id")
    private String promptId;

    /**
     * Client/patient name identifier.
     * Used to track analysis results for specific clients.
     */
    @NotBlank(message = "Client name is required")
    @JsonProperty("client-name")
    private String clientName;

    /**
     * Professional/practitioner name identifier.
     * Used to track analysis results for specific healthcare professionals.
     */
    @JsonProperty("professional-name")
    private String professionalName;

    /**
     * Section A1: Primary and secondary Integrative Endoteleons with teleonic pattern analysis.
     */
    @Valid
    @JsonProperty("A1")
    private A1 a1;

    /**
     * Section A2: List of Integrative Endoteleons identified in the analysis.
     */
    @JsonProperty("A2")
    private List<String> a2;

    /**
     * Section A3: HETA (Health Endoteleon Tuning Agents) recommendations.
     */
    @Valid
    @JsonProperty("A3")
    private A3 a3;

    /**
     * Section A4a: Modular analysis component.
     */
    @Valid
    @JsonProperty("A4a")
    private A4a a4a;

    /**
     * Section A4b: Modular analysis component.
     */
    @Valid
    @JsonProperty("A4b")
    private A4b a4b;

    /**
     * Section A4c: Modular analysis component.
     */
    @Valid
    @JsonProperty("A4c")
    private A4c a4c;

    /**
     * Section A4d: Modular analysis component.
     */
    @Valid
    @JsonProperty("A4d")
    private A4d a4d;

    /**
     * Section A5: Missing data overview and analysis recommendations.
     */
    @Valid
    @JsonProperty("A5")
    private A5 a5;

    /**
     * Section A6: Advanced analysis and recommendations.
     */
    @Valid
    @JsonProperty("A6")
    private A6 a6;

    /**
     * Section B1: Recommended Ethos variant questionnaires (Adult, Child, or Hybrid).
     */
    @Valid
    @JsonProperty("B1")
    private List<B1> b1;

    /**
     * Section B2: Additional validated questionnaires.
     */
    @Valid
    @JsonProperty("B2")
    private List<B2> b2;

    /**
     * Section B3: HETA modular questionnaires.
     */
    @Valid
    @JsonProperty("B3")
    private List<B3> b3;

    /**
     * Thread-safe ObjectMapper configured for Prompt1Result serialization/deserialization.
     * Configuration:
     * - Ignores unknown properties to handle API evolution
     * - Accepts single values as arrays for flexible input handling
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

    /**
     * Section A1: Primary and secondary Integrative Endoteleons with narrative analysis.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A1 {
        @JsonProperty("primary_ie")
        private String primaryIe;

        @JsonProperty("secondary_ie")
        private List<String> secondaryIe;

        @JsonProperty("teleonic_pattern")
        private String teleonicPattern;

        @JsonProperty("secondary_dynamics")
        private List<String> secondaryDynamics;

        @JsonProperty("uncertainty_level")
        private Double uncertaintyLevel;

        @JsonProperty("uncertainty_label")
        private String uncertaintyLabel;
    }

    /**
     * Section A3: HETA (Health Endoteleon Tuning Agents) recommendations.
     * Contains list of HETA identifiers.
     * Supports both legacy format (heta) and new format (direct/indirect).
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A3 {
        /**
         * Legacy format: Simple list of HETA identifiers (for backward compatibility).
         */
        @JsonProperty("heta")
        private List<String> heta;

        /**
         * New format: Direct HETA interventions with immediate impact.
         */
        @JsonProperty("direct")
        private List<String> direct;

        /**
         * New format: Indirect HETA interventions with secondary impact.
         */
        @JsonProperty("indirect")
        private List<String> indirect;
    }

    /**
     * Section A4a: QUEST module data.
     * Contains questionnaire analysis status and items.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A4a {
        @JsonProperty("module_id")
        private String moduleId;

        @JsonProperty("status")
        private String status;

        @JsonProperty("items")
        private List<A4aItem> items;

        @JsonProperty("uncertainty_label")
        private String uncertaintyLabel;
    }

    /**
     * Item within A4a QUEST module.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A4aItem {
        @JsonProperty("qest_json_identifier")
        private String qestJsonIdentifier;

        @JsonProperty("teleonic_field")
        private String teleonicField;

        @JsonProperty("heta_context")
        private List<String> hetaContext;

        @JsonProperty("direction")
        private String direction;

        @JsonProperty("direction_note")
        private String directionNote;

        @JsonProperty("amplitude")
        private String amplitude;

        @JsonProperty("amplitude_note")
        private String amplitudeNote;

        @JsonProperty("congruence")
        private String congruence;

        @JsonProperty("congruence_note")
        private String congruenceNote;

        @JsonProperty("narrative")
        private List<String> narrative;
    }

    /**
     * Section A4b: HMA (Hair Mineral Analysis) module data.
     * Contains detailed sections and summary components for mineral analysis.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A4b {
        @JsonProperty("module_id")
        private String moduleId;

        @JsonProperty("status")
        private String status;

        @JsonProperty("sections")
        private A4bSections sections;

        @JsonProperty("summary_components")
        private A4bSummaryComponents summaryComponents;

        @JsonProperty("uncertainty_label")
        private String uncertaintyLabel;
    }

    /**
     * Sections within A4b HMA module.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A4bSections {
        @JsonProperty("epistemic_positioning")
        private String epistemicPositioning;

        @JsonProperty("lab_report_context")
        private String labReportContext;

        @JsonProperty("overall_pattern")
        private String overallPattern;

        @JsonProperty("eap_profile_narrative")
        private String eapProfileNarrative;

        @JsonProperty("eap_index_client_explanation")
        private String eapIndexClientExplanation;

        @JsonProperty("pattern_minerals_ratios")
        private String patternMineralsRatios;

        @JsonProperty("stress_context")
        private String stressContext;

        @JsonProperty("followup_exploration")
        private String followupExploration;

        @JsonProperty("summary")
        private String summary;

        @JsonProperty("closing")
        private String closing;
    }

    /**
     * Summary components within A4b HMA module.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A4bSummaryComponents {
        @JsonProperty("eap_profile")
        private String eapProfile;

        @JsonProperty("eap_index")
        private String eapIndex;

        @JsonProperty("intensity")
        private Integer intensity;

        @JsonProperty("direction")
        private String direction;

        @JsonProperty("buffer")
        private String buffer;

        @JsonProperty("pattern_markers")
        private List<String> patternMarkers;

        @JsonProperty("followup_flag")
        private String followupFlag;
    }

    /**
     * Section A4c: HRV (Heart Rate Variability) module data.
     * Contains detailed sections for HRV analysis.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A4c {
        @JsonProperty("module_id")
        private String moduleId;

        @JsonProperty("status")
        private String status;

        @JsonProperty("sections")
        private A4cSections sections;

        @JsonProperty("uncertainty_label")
        private String uncertaintyLabel;
    }

    /**
     * Sections within A4c HRV module.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A4cSections {
        @JsonProperty("epistemic_positioning")
        private String epistemicPositioning;

        @JsonProperty("measurement_context")
        private String measurementContext;

        @JsonProperty("global_load_recovery")
        private String globalLoadRecovery;

        @JsonProperty("night_recovery_sleep_rhythm")
        private String nightRecoverySleepRhythm;

        @JsonProperty("activity_day_distribution")
        private String activityDayDistribution;

        @JsonProperty("rap_profile")
        private String rapProfile;

        @JsonProperty("rap_index")
        private Integer rapIndex;

        @JsonProperty("summary")
        private String summary;
    }

    /**
     * Section A4d: LAB (Laboratory) module data.
     * Contains detailed sections for laboratory analysis.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A4d {
        @JsonProperty("module_id")
        private String moduleId;

        @JsonProperty("status")
        private String status;

        @JsonProperty("sections")
        private A4dSections sections;

        @JsonProperty("uncertainty_label")
        private String uncertaintyLabel;
    }

    /**
     * Sections within A4d LAB module.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A4dSections {
        @JsonProperty("epistemic_positioning")
        private String epistemicPositioning;

        @JsonProperty("lab_reports")
        private List<Object> labReports;

        @JsonProperty("integrated_interpretation")
        private String integratedInterpretation;

        @JsonProperty("overall_summary")
        private String overallSummary;
    }

    /**
     * Section A5: Missing data overview and analysis recommendations.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A5 {
        @JsonProperty("module_id")
        private String moduleId;

        @JsonProperty("ID")
        private String id;

        @JsonProperty("label")
        private String label;

        @JsonProperty("mode")
        private String mode;

        @JsonProperty("items")
        private List<A5Item> items;


        @JsonProperty("uncertainty_label")
        private String uncertaintyLabel;
    }

    /**
     * Item within A5 missing data overview.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A5Item {
        @JsonProperty("item_type")
        private String itemType;

        @JsonProperty("lab_type")
        private String labType;

        @JsonProperty("text")
        private String text;

        @JsonProperty("label")
        private String label;
    }

    /**
     * Section A6: Advanced analysis and recommendations.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A6 {
        @JsonProperty("status")
        private String status;

        @JsonProperty("A6a")
        private A6a a6a;

        @JsonProperty("A6b")
        private A6b a6b;

        @JsonProperty("uncertainty_label")
        private String uncertaintyLabel;
    }

    /**
     * Section A6a: Headings structure.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A6a {
        @JsonProperty("H0")
        private String h0;

        @JsonProperty("H1")
        private String h1;

        @JsonProperty("H2")
        private String h2;

        @JsonProperty("H3")
        private String h3;

        @JsonProperty("H4")
        private String h4;

        @JsonProperty("H5")
        private String h5;

        @JsonProperty("H6")
        private String h6;

        @JsonProperty("H7")
        private String h7;

        @JsonProperty("H8")
        private String h8;
    }

    /**
     * Section A6b: Analysis and recovery plan details.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A6b {
        @JsonProperty("A6b0_epistemic_positioning")
        private String a6b0EpistemicPositioning;

        @JsonProperty("A6b1_problem_statement")
        private String a6b1ProblemStatement;

        @JsonProperty("A6b2_recovery_priorities")
        private String a6b2RecoveryPriorities;

        @JsonProperty("A6b3_heta_recovery_order")
        private String a6b3HetaRecoveryOrder;

        @JsonProperty("A6b4_conditions_uncertainties")
        private String a6b4ConditionsUncertainties;

        @JsonProperty("A6b5_discipline_translations")
        private A6b5DisciplineTranslations a6b5DisciplineTranslations;

        @JsonProperty("A6b6_out_of_scope")
        private String a6b6OutOfScope;
    }

    /**
     * Section A6b5: Discipline translations.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A6b5DisciplineTranslations {
        @JsonProperty("bioregulatory")
        private String bioregulatory;

        @JsonProperty("nutritional")
        private String nutritional;

        @JsonProperty("homeopathic")
        private String homeopathic;

        @JsonProperty("acupuncture_energetic")
        private String acupunctureEnergetic;

        @JsonProperty("medical_avig")
        private String medicalAvig;
    }

    /**
     * Section B1: Recommended Ethos variant (Adult, Child, or Hybrid).
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class B1 {
        @JsonProperty("nr")
        private Integer nr;

        @JsonProperty("label")
        private String label;

        @JsonProperty("ID")
        private String id;

        @JsonProperty("qest_json")
        private String qestJson;
    }

    /**
     * Section B2: Additional validated questionnaires.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class B2 {
        @JsonProperty("nr")
        private Integer nr;

        @JsonProperty("label")
        private String label;

        @JsonProperty("ID")
        private String id;

        @JsonProperty("qest_json")
        private String qestJson;
    }

    /**
     * Section B3: HETA modular questionnaires.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class B3 {
        @JsonProperty("nr")
        private Integer nr;

        @JsonProperty("heta_id")
        private String hetaId;

        @JsonProperty("heta_json")
        private String hetaJson;


        @JsonProperty("tier")
        private String tier;

        @JsonProperty("source_ie")
        private String sourceIe;
    }

    /**
     * Serializes Prompt1Result object to JSON string.
     *
     * @return JSON string representation
     * @throws RuntimeException if JSON serialization fails
     */
    public String toJson() throws JsonProcessingException {

            String json = MAPPER.writeValueAsString(this);
            log.debug("Successfully serialized Prompt1Result for client: {}, professional: {}",
                     clientName, professionalName);
            return json;

    }

    /**
     * Deserializes JSON string to Prompt1Result object.
     *
     * @param json JSON string to deserialize
     * @return Prompt1Result object
     * @throws JsonProcessingException 
     * @throws JsonMappingException 
     * @throws RuntimeException if JSON deserialization fails
     * @throws IllegalArgumentException if json parameter is null or empty
     */
    public static AiAnalysisResult fromJson(String json) throws JsonMappingException, JsonProcessingException {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON string cannot be null or empty");
        }
            AiAnalysisResult result = MAPPER.readValue(json, AiAnalysisResult.class);
            log.debug("Successfully deserialized Prompt1Result for client: {}, professional: {}",
                     result.getClientName(), result.getProfessionalName());
            return result;

    }
}

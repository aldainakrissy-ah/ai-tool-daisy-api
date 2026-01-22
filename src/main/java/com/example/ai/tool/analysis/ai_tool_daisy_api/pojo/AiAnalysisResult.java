package com.example.ai.tool.analysis.ai_tool_daisy_api.pojo;

import com.example.ai.tool.analysis.ai_tool_daisy_api.exception.JsonSerializationException;
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
     * Type of prompt used for analysis (e.g., "PROMPT 1", "PROMPT 2", "PROMPT 3").
     * This field identifies which analysis prompt was executed.
     */
    @JsonProperty("prompt-type")
    private String promptType;

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
     * Section A2: Additional analysis markers or identifiers.
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
     * Contains direct and indirect HETA arrays.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A3 {
        @JsonProperty("direct")
        private List<String> direct;

        @JsonProperty("indirect")
        private List<String> indirect;
    }

    /**
     * Section A4a: Placeholder for future data structure.
     * Currently empty object in JSON responses.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A4a {
        @JsonProperty("module_id")
        private String moduleId;

        @JsonProperty("status")
        private String status;

        @JsonProperty("narrative")
        private String narrative;

        @JsonProperty("uncertainty_label")
        private String uncertaintyLabel;
    }

    /**
     * Section A4b: Placeholder for future data structure.
     * Currently empty object in JSON responses.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A4b {
        @JsonProperty("module_id")
        private String moduleId;

        @JsonProperty("status")
        private String status;

        @JsonProperty("narrative")
        private String narrative;

        @JsonProperty("uncertainty_label")
        private String uncertaintyLabel;
    }

    /**
     * Section A4c: Placeholder for future data structure.
     * Currently empty object in JSON responses.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A4c {
        @JsonProperty("module_id")
        private String moduleId;

        @JsonProperty("status")
        private String status;

        @JsonProperty("narrative")
        private String narrative;

        @JsonProperty("uncertainty_label")
        private String uncertaintyLabel;
    }

    /**
     * Section A4d: Placeholder for future data structure.
     * Currently empty object in JSON responses.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A4d {
        @JsonProperty("module_id")
        private String moduleId;

        @JsonProperty("status")
        private String status;

        @JsonProperty("narrative")
        private String narrative;

        @JsonProperty("uncertainty_label")
        private String uncertaintyLabel;
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

        @JsonProperty("primary_ie")
        private String primaryIe;

        @JsonProperty("secondary_ie")
        private List<String> secondaryIe;

        @JsonProperty("supporting_modules")
        private List<String> supportingModules;

        @JsonProperty("narrative_core")
        private String narrativeCore;

        @JsonProperty("narrative_full")
        private String narrativeFull;

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
        @JsonProperty("label")
        private String label;
    }

    /**
     * Section A6: Placeholder for future data structure.
     * Currently empty object in JSON responses.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A6 {
        @JsonProperty("module_id")
        private String moduleId;

        @JsonProperty("ID")
        private String id;

        @JsonProperty("label")
        private String label;

        @JsonProperty("primary_ie")
        private String primaryIe;

        @JsonProperty("supporting_hetas")
        private List<SupportingHeta> supportingHetas;

        @JsonProperty("a4_references")
        private List<String> a4References;

        @JsonProperty("advice_core")
        private String adviceCore;

        @JsonProperty("advice_details")
        private String adviceDetails;

        @JsonProperty("uncertainty_label")
        private String uncertaintyLabel;
    }

    /**
     * Supporting HETA item for A6 section.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SupportingHeta {
        @JsonProperty("heta_id")
        private String hetaId;

        @JsonProperty("heta_json")
        private String hetaJson;
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

        @JsonProperty("label")
        private String label;

        @JsonProperty("tier")
        private String tier;

        @JsonProperty("source_ie")
        private String sourceIe;
    }

    /**
     * Serializes Prompt1Result object to JSON string.
     *
     * @return JSON string representation
     * @throws JsonSerializationException if JSON serialization fails
     */
    public String toJson() {
        try {
            String json = MAPPER.writeValueAsString(this);
            log.debug("Successfully serialized Prompt1Result for client: {}, professional: {}",
                     clientName, professionalName);
            return json;
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize Prompt1Result to JSON for client: {}, professional: {}",
                     clientName, professionalName, e);
            throw new JsonSerializationException("Failed to serialize Prompt1Result to JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Deserializes JSON string to Prompt1Result object.
     *
     * @param json JSON string to deserialize
     * @return Prompt1Result object
     * @throws JsonSerializationException if JSON deserialization fails
     * @throws IllegalArgumentException if json parameter is null or empty
     */
    public static AiAnalysisResult fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON string cannot be null or empty");
        }

        try {
            AiAnalysisResult result = MAPPER.readValue(json, AiAnalysisResult.class);
            log.debug("Successfully deserialized Prompt1Result for client: {}, professional: {}",
                     result.getClientName(), result.getProfessionalName());
            return result;
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize JSON to Prompt1Result: {}", e.getMessage(), e);
            throw new JsonSerializationException("Failed to deserialize JSON to Prompt1Result: " + e.getMessage(), e);
        }
    }
}

package com.example.ai.tool.analysis.ai_tool_daisy_api.pojo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * POJO representing the AI analysis result for Phase 1 pre-intake questionnaire.
 * Contains teleonic analysis sections (A1-A3) and recommended questionnaires (B1-B3).
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Prompt1Result {

    @JsonProperty("client-name")
    private String clientName;

    @JsonProperty("professional-name")
    private String professionalName;

    @JsonProperty("A1")
    private A1 a1;

    @JsonProperty("A2")
    private List<String> a2;

    @JsonProperty("A3")
    private A3 a3;

    @JsonProperty("A4a")
    private A4a a4a;

    @JsonProperty("A4b")
    private A4b a4b;

    @JsonProperty("A4c")
    private A4c a4c;

    @JsonProperty("A4d")
    private A4d a4d;

    @JsonProperty("A5")
    private A5 a5;

    @JsonProperty("A6")
    private A6 a6;

    @JsonProperty("B1")
    private List<B1> b1;

    @JsonProperty("B2")
    private List<B2> b2;

    @JsonProperty("B3")
    private List<B3> b3;

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

        @JsonProperty("heta_json_code")
        private String hetaJsonCode;
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
     * @throws RuntimeException if JSON serialization fails
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize Prompt1Result to JSON: " + e.getMessage(), e);
        }
    }

    public static Prompt1Result fromJson(String json) {
        try {
            return MAPPER.readValue(json, Prompt1Result.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize JSON to Prompt1Result: " + e.getMessage(), e);
        }
    }
}

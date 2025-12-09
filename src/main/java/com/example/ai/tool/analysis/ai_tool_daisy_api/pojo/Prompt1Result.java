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
    private A2 a2;

    @JsonProperty("A3")
    private A3 a3;

    @JsonProperty("B1")
    private B1 b1;

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
        @JsonProperty("primary_IE")
        private String primaryIE;

        @JsonProperty("secondary_IEs")
        private List<String> secondaryIEs;

        @JsonProperty("narrative")
        private String narrative;
    }

    /**
     * Section A2: Teleonic clusters derived from Integrative Endoteleons.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A2 {
        @JsonProperty("teleonic_clusters")
        private List<A2TeleonicCluster> teleonicClusters;
    }

    /**
     * Individual teleonic cluster within A2 section.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A2TeleonicCluster {
        @JsonProperty("IE")
        private String ie;

        @JsonProperty("teleonic_cluster")
        private String teleonicCluster;
    }

    /**
     * Section A3: HETA (Health Endoteleon Tuning Agents) recommendations.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A3 {
        @JsonProperty("direct")
        private List<HetaItem> direct;

        @JsonProperty("indirect")
        private List<HetaItem> indirect;
    }

    /**
     * Individual HETA item with id, label and json reference.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HetaItem {
        @JsonProperty("id")
        private String id;

        @JsonProperty("label")
        private String label;

        @JsonProperty("json_ref")
        private String jsonRef;
    }

    /**
     * Section B1: Recommended Ethos variant (Adult, Child, or Hybrid).
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class B1 {
        @JsonProperty("variant_id")
        private String variantId;

        @JsonProperty("name")
        private String name;

        @JsonProperty("rational")
        private String rational;
    }

    /**
     * Section B2: Additional validated questionnaires.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class B2 {
        @JsonProperty("id")
        private String id;

        @JsonProperty("label")
        private String label;

        @JsonProperty("json_ref")
        private String jsonRef;
    }

    /**
     * Section B3: HETA modular questionnaires.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class B3 {
        @JsonProperty("id")
        private String id;

        @JsonProperty("label")
        private String label;

        @JsonProperty("json_ref")
        private String jsonRef;
    }

    /**
     * Deserializes JSON string to Prompt1Result object.
     *
     * @param json JSON string to deserialize
     * @return Prompt1Result object
     * @throws RuntimeException if JSON parsing fails
     */
    public static Prompt1Result fromJson(String json) {
        try {
            return MAPPER.readValue(json, Prompt1Result.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON to Prompt1Result: " + e.getMessage(), e);
        }
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
}

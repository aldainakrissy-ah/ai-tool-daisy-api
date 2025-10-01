package com.example.ai.tool.analysis.ai_tool_daisy_api.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Prompt1Result {
    @JsonProperty("patient_id")
    private String patientId;
    private Analysis analysis;
    private Routing routing;


    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Analysis {
        @JsonProperty("preliminary_tpd_hypothesis")
        private String preliminaryTpdHypothesis; // single value

        @JsonProperty("dysregulation_fields")
        private List<String> dysregulationFields;

        @JsonProperty("signs_symptoms_clusters")
        private List<String> signsSymptomsClusters;

        @JsonProperty("heta_terms_usage")
        private String hetaTermsUsage; // single value

        @JsonProperty("data_gaps")
        private List<String> dataGaps;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Routing {
        @JsonProperty("additional_questionnaires")
        private List<String> additionalQuestionnaires;

        @JsonProperty("ethos_variant")
        private EthosVariant ethosVariant;

        @JsonProperty("heta_modules")
        private List<String> hetaModules;
    }

    public enum EthosVariant {
        Adult,
        Child,
        Hybrid
    }

    private static final ObjectMapper mapper = new ObjectMapper();

    public static Prompt1Result fromJson(String json) {
        try {
            return mapper.readValue(json, Prompt1Result.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON to Prompt1Result", e);
        }
    }
}

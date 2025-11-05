package com.example.ai.tool.analysis.ai_tool_daisy_api.pojo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Prompt1Result {

    @JsonProperty("professional_id")
    private String professionalId;

    @JsonProperty("patient_id")
    private String patientId;

    private Analysis analysis;

    private Routing routing;

    private static ObjectMapper MAPPER = new ObjectMapper();


    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Analysis {
        @JsonProperty("preliminary_tpd_hypothesis")
        private String preliminaryTpdHypothesis;

        @JsonProperty("dysregulation_fields")
        private List<String> dysregulationFields;

        /**
         * New: structured S&S clusters with number, cluster label, and core signals.
         */
        @JsonProperty("signs_symptoms_clusters")
        private List<SignsSymptomsCluster> signsSymptomsClusters;

        /**
         * New: HETA coverage report with level + HETAs + short justification.
         */
        @JsonProperty("heta_coverage_report")
        private List<HetaCoverageItem> hetaCoverageReport;

        /**
         * New: Optional additional data (triggered measurements).
         * e.g., "HMA-analyse", "HRV-profiel", "Vitamine D-status"
         */
        @JsonProperty("optional_additional_data")
        private List<String> optionalAdditionalData;

        /**
         * Previously "data_gaps"; keep same key for compatibility.
         * e.g., "HMA-data ontbreekt", "HRV-metingen nog niet aangeleverd", "Geen recente Vitamine-D uitslag"
         */
        @JsonProperty("data_gaps")
        private List<String> dataGaps;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SignsSymptomsCluster {
        /** Example: 1, 2, 3... */
        @JsonProperty("nr")
        private Integer nr;

        /** Example: "Slaap/doorslaap-architectuur (nachtcontinuïteit)" */
        @JsonProperty("cluster")
        private String cluster;

        /** Example list of short strings extracted from intake */
        @JsonProperty("core_signals")
        private List<String> coreSignals;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HetaCoverageItem {
        /**
         * Allowed: HIGH / MEDIUM / LOW
         * (maps from "Niveau: hoog/ gemiddeld/ laag")
         */
        @JsonProperty("level")
        private CoverageLevel level;

        /** List of HETA names (no spray names) */
        @JsonProperty("hetas")
        private List<String> hetas;

        /** Short justification text for this level */
        @JsonProperty("justification")
        private String justification;
    }

    public enum CoverageLevel {
        @JsonProperty("HIGH") HIGH,
        @JsonProperty("MEDIUM") MEDIUM,
        @JsonProperty("LOW") LOW
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Routing {

        /**
         * Your new sample provides a descriptive Ethos text.
         * Keep it a String to allow either "Volwassene/Kind/Hybride"
         * or a fuller description like "Ethos Scan – waarden-gedrag congruentie..."
         */
        @JsonProperty("ethos_variant")
        private String ethosVariant;

        /**
         * New: structured list of additional questionnaires with ID + name + short note + trigger.
         * (Section 6.2 in your sample)
         */
        @JsonProperty("additional_questionnaires")
        private List<Questionnaire> additionalQuestionnaires;

        /**
         * New: HETA questionnaires (advised) with small notes (Section 6.3).
         */
        @JsonProperty("heta_questionnaires")
        private List<HetaQuestionnaire> hetaQuestionnaires;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Questionnaire {
        /** e.g., 1, 2, 3 (optional) */
        @JsonProperty("nr")
        private Integer nr;

        /** e.g., "psqi", "fss", "food-diary-24h" */
        @JsonProperty("id")
        private String id;

        /** e.g., "Pittsburgh Sleep Quality Index (PSQI)" */
        @JsonProperty("name")
        private String name;

        /** Short explanation/justification */
        @JsonProperty("note")
        private String note;

        /** Trigger condition text */
        @JsonProperty("trigger")
        private String trigger;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HetaQuestionnaire {
        /** e.g., 1, 2, 3 (optional) */
        @JsonProperty("nr")
        private Integer nr;

        /** e.g., "Histamine", "NPY (Resilience/settlingveld)", "Glycine" */
        @JsonProperty("heta")
        private String heta;

        /** Short explanation */
        @JsonProperty("note")
        private String note;
    }

    public static Prompt1Result fromJson(String json) {
        try {
            return MAPPER.readValue(json, Prompt1Result.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON to Prompt1Result", e);
        }
    }

    public String toJson() {
        try {

            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize Prompt1Result to JSON", e);
        }
    }
}

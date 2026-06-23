package com.example.ai.tool.analysis.ai_tool_daisy_api.pojo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Slf4j
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Prompt2Result implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

    @JsonProperty("prompt_id")
    private String promptId;

    @JsonProperty("current_date")
    private String currentDate;

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

    @JsonProperty("A4a")
    private A4a a4a;

    @JsonProperty("A4b")
    private A4b a4b;

    @JsonProperty("A4c")
    private A4c a4c;

    @JsonProperty("A4d")
    private A4d a4d;

    @JsonProperty("A4e")
    private A4e a4e;

    @JsonProperty("A5")
    private A5 a5;

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A1 {
        @JsonProperty("primary_ie_ref")
        private String primaryIeRef;

        @JsonProperty("primary_ie_title")
        private String primaryIeTitle;

        @JsonProperty("secondary_ie")
        private List<IeRefTitleItem> secondaryIe;

        @JsonProperty("teleonic_pattern")
        private String teleonicPattern;

        @JsonProperty("secondary_dynamics")
        private List<String> secondaryDynamics;

        @JsonProperty("uncertainty_level")
        private Double uncertaintyLevel;

        @JsonProperty("uncertainty_label")
        private String uncertaintyLabel;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class IeRefTitleItem {
        @JsonProperty("ie_ref")
        private String ieRef;

        @JsonProperty("ie_title")
        private String ieTitle;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A2 {
        @JsonProperty("ie_clusters")
        private List<IeClusterItem> ieClusters;

        @JsonProperty("fields")
        private List<FieldItem> fields;

        @JsonProperty("domains")
        private List<DomainItem> domains;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class IeClusterItem {
        @JsonProperty("source_ie_ref")
        private String sourceIeRef;

        @JsonProperty("source_ie_title")
        private String sourceIeTitle;

        @JsonProperty("priority")
        private String priority;

        @JsonProperty("cluster_description")
        private String clusterDescription;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FieldItem {
        @JsonProperty("field_id")
        private String fieldId;

        @JsonProperty("field_label")
        private String fieldLabel;

        @JsonProperty("source_ie_ref")
        private String sourceIeRef;

        @JsonProperty("source_ie_title")
        private String sourceIeTitle;

        @JsonProperty("field_role")
        private String fieldRole;

        @JsonProperty("field_description")
        private String fieldDescription;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DomainItem {
        @JsonProperty("domain_id")
        private String domainId;

        @JsonProperty("source_field_id")
        private String sourceFieldId;

        @JsonProperty("source_ie_ref")
        private String sourceIeRef;

        @JsonProperty("source_ie_title")
        private String sourceIeTitle;

        @JsonProperty("domain_label")
        private String domainLabel;

        @JsonProperty("domain_description")
        private String domainDescription;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A3 {
        @JsonProperty("direct")
        private List<String> direct;

        @JsonProperty("indirect")
        private List<String> indirect;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A4a {
        @JsonProperty("module_id")
        private String moduleId;

        @JsonProperty("status")
        private String status;

        @JsonProperty("A4a1_ethos")
        private QuestItemsModule a4a1Ethos;

        @JsonProperty("A4a2_qest")
        private QuestItemsModule a4a2Qest;

        @JsonProperty("A4a3_heta")
        private QuestItemsModule a4a3Heta;

        @JsonProperty("uncertainty_label")
        private String uncertaintyLabel;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QuestItemsModule {
        @JsonProperty("module_id")
        private String moduleId;

        @JsonProperty("status")
        private String status;

        @JsonProperty("items")
        private List<QuestItem> items;

        @JsonProperty("uncertainty_label")
        private String uncertaintyLabel;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QuestItem {
        @JsonProperty("qest_json_identifier")
        private String qestJsonIdentifier;

        @JsonProperty("heta_json_identifier")
        private String hetaJsonIdentifier;

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
        private String rapIndex;

        @JsonProperty("summary")
        private String summary;
    }

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

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A4e {
        @JsonProperty("module_id")
        private String moduleId;

        @JsonProperty("status")
        private String status;

        @JsonProperty("dominant_observation_patterns")
        private List<ObservationPattern> dominantObservationPatterns;

        @JsonProperty("uncertainty_label")
        private String uncertaintyLabel;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ObservationPattern {
        @JsonProperty("pattern_label")
        private String patternLabel;

        @JsonProperty("supporting_sources")
        private List<String> supportingSources;

        @JsonProperty("description")
        private String description;
    }

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

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A5Item {
        @JsonProperty("item_type")
        private String itemType;

        @JsonProperty("attention_category")
        private String attentionCategory;

        @JsonProperty("lab_type")
        private String labType;

        @JsonProperty("text")
        private String text;
    }

    public String toJson() throws JsonProcessingException {
        String json = MAPPER.writeValueAsString(this);
        log.debug("Serialized Prompt2Result for client: {}, professional: {}", clientName, professionalName);
        return json;
    }

    public static Prompt2Result fromJson(String json) throws JsonMappingException, JsonProcessingException {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON string cannot be null or empty");
        }
        Prompt2Result result = MAPPER.readValue(json, Prompt2Result.class);
        log.debug("Deserialized Prompt2Result for client: {}, professional: {}",
                result.getClientName(), result.getProfessionalName());
        return result;
    }
}

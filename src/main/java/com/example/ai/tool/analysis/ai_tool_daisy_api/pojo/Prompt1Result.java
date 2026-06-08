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
public class Prompt1Result implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

    @JsonProperty("prompt_id")
    private String promptId;

    @JsonProperty("date")
    private String date;

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
    private List<QuestionnaireItem> b1;

    @JsonProperty("B2")
    private List<QuestionnaireItem> b2;

    @JsonProperty("B3")
    private List<B3Item> b3;

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
        @JsonProperty("ie_ref")
        private String ieRef;

        @JsonProperty("ie_title")
        private String ieTitle;

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
    public static class QuestionnaireItem {
        @JsonProperty("nr")
        private Integer nr;

        @JsonProperty("label")
        private String label;

        @JsonProperty("ID")
        private String id;

        @JsonProperty("qest_json")
        private String qestJson;
    }


    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class B3Item {
        @JsonProperty("nr")
        private Integer nr;

        @JsonProperty("heta_id")
        private String hetaId;

        @JsonProperty("heta_json")
        private String hetaJson;

        @JsonProperty("tier")
        private String tier;

        @JsonProperty("source_ie_ref")
        private String sourceIeRef;

        @JsonProperty("source_ie_title")
        private String sourceIeTitle;
    }

    public String toJson() throws JsonProcessingException {
        String json = MAPPER.writeValueAsString(this);
        log.debug("Serialized Prompt1Result for client: {}, professional: {}", clientName, professionalName);
        return json;
    }

    public static Prompt1Result fromJson(String json) throws JsonMappingException, JsonProcessingException {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON string cannot be null or empty");
        }
        Prompt1Result result = MAPPER.readValue(json, Prompt1Result.class);
        log.debug("Deserialized Prompt1Result for client: {}, professional: {}",
                result.getClientName(), result.getProfessionalName());
        return result;
    }
}

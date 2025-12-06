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

    @JsonProperty("client-name")
    private String clientName;

    @JsonProperty("professional-name")
    private String professionalName;

    @JsonProperty("A1")
    private A1 a1;

    @JsonProperty("A2")
    private List<A2> a2;

    @JsonProperty("A3")
    private A3 a3;

    @JsonProperty("A4a")
    private A4 a4a;

    @JsonProperty("A4b")
    private A4 a4b;

    @JsonProperty("A4c")
    private A4 a4c;

    @JsonProperty("A4d")
    private A4 a4d;

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

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A4 {
        @JsonProperty("status")
        private String status;

        @JsonProperty("narrative")
        private String narrative;

        @JsonProperty("data")
        private String data;
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

        @JsonProperty("items")
        private List<A5Item> items;

        @JsonProperty("narrative")
        private String narrative;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A5Item {
        @JsonProperty("analysis_id")
        private String analysisId;

        @JsonProperty("label")
        private String label;

        @JsonProperty("recommended_reason")
        private String recommendedReason;

        @JsonProperty("required_fields")
        private List<String> requiredFields;

        @JsonProperty("uncertainty_reason")
        private String uncertaintyReason;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A6 {
        @JsonProperty("status")
        private String status;

        @JsonProperty("narrative")
        private String narrative;

        @JsonProperty("data")
        private String data;
    }

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

        @JsonProperty("qest_json_code")
        private String qestJsonCode;

        @JsonProperty("toelichting")
        private String toelichting;

        @JsonProperty("status")
        private String status;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A1 {
        @JsonProperty("primary_IE")
        private String primaryIE;

        @JsonProperty("secondary_IEs")
        private List<String> secondaryIEs;

        @JsonProperty("uncertainty")
        private String uncertainty;

        @JsonProperty("narrative")
        private String narrative;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A2 {
        @JsonProperty("teleonic_cluster")
        private String teleonicCluster;

        @JsonProperty("derived_from_IE")
        private String derivedFromIE;

        @JsonProperty("projection")
        private String projection;
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class A3 {
        @JsonProperty("direct")
        private List<HetaItem> direct;

        @JsonProperty("indirect")
        private List<HetaItem> indirect;

        @JsonProperty("narrative")
        private String narrative;
    }

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

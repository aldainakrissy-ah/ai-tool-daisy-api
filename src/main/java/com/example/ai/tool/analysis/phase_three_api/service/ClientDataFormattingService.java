package com.example.ai.tool.analysis.phase_three_api.service;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ClientDataFormattingService {

    public Map<String, Object> formatClientData(Map<String, List<Map<String, Object>>> rawData) {
        if (rawData == null || rawData.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> formattedData = new LinkedHashMap<>();

        // General Client Info (for the header)
        List<Map<String, Object>> clients = rawData.getOrDefault("clients", Collections.emptyList());
        if (!clients.isEmpty()) {
            Map<String, Object> client = clients.get(0);
            formattedData.put("title", "General client info for " + client.get("first_name") + " " + client.get("family_name"));
        }

        // Format each section
        formattedData.put("contact_details", formatSimpleSection(rawData.get("client_personal_contact_details")));
        formattedData.put("bio", formatBioSection(rawData.get("client_bios"), clients));
        formattedData.put("education_profession", formatSimpleSection(rawData.get("client_career_and_education_details")));
        formattedData.put("family_details", formatSimpleSection(rawData.get("client_family_details")));
        formattedData.put("request_for_help", getFirstFieldValue(rawData.get("client_request_for_helps"), "description"));
        formattedData.put("food_supplements", getFirstFieldValue(rawData.get("client_food_supplements"), "content"));

        // Sections with potentially multiple entries
        formattedData.put("allergies", formatListOfMaps(rawData.get("client_allergies"), "option_allergy_id"));
        formattedData.put("food_intolerances", formatListOfMaps(rawData.get("client_food_intolerances"), "option_food_intolerance_id"));
        formattedData.put("addictions", formatListOfMaps(rawData.get("client_addictions"), "option_addiction_id"));
        
        formattedData.put("vice_frequencies", formatListOfMaps(rawData.get("client_vice_frequencies"), "type", "frequency"));
        
        formattedData.put("specialists", formatListOfMaps(rawData.get("client_involved_health_professionals"), "for_what", "since_when", "name", "address", "telephone_country_code", "telephone_number"));
        formattedData.put("medications", getFirstFieldValue(rawData.get("client_other_medications"), "content"));
        
        formattedData.put("vaccinations", formatListOfMaps(rawData.get("client_vaccinations"), "option_vaccination_id", "when", "physiological_response"));
        
        formattedData.put("family_bloodline_diseases", formatListOfMaps(rawData.get("client_bloodline_diseases"), "disease", "family_members"));
        
        formattedData.put("surgeries", formatListOfMaps(rawData.get("client_surgeries"), "for_what", "when"));
        
        formattedData.put("important_notes", formatListOfMaps(rawData.get("client_important_note_to_professionals"), "note", "year_of_event"));

        return formattedData;
    }

    private Map<String, Object> formatSimpleSection(List<Map<String, Object>> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return Collections.emptyMap();
        }
        // Return the first record as a map
        return new LinkedHashMap<>(dataList.get(0));
    }

    private Map<String, Object> formatBioSection(List<Map<String, Object>> bioList, List<Map<String, Object>> clientList) {
        if (bioList == null || bioList.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> bio = new LinkedHashMap<>(bioList.get(0));
        if (clientList != null && !clientList.isEmpty()) {
            bio.put("email", clientList.get(0).get("email"));
            bio.put("first_name", clientList.get(0).get("first_name"));
            bio.put("family_name", clientList.get(0).get("family_name"));
        }
        return bio;
    }

    private Object getFirstFieldValue(List<Map<String, Object>> dataList, String fieldName) {
        if (dataList == null || dataList.isEmpty()) {
            return "None";
        }
        return dataList.get(0).getOrDefault(fieldName, "N/A");
    }

    private List<Map<String, Object>> formatListOfMaps(List<Map<String, Object>> dataList, String... fields) {
        if (dataList == null || dataList.isEmpty()) {
            return Collections.emptyList();
        }
        return dataList.stream().map(row -> {
            Map<String, Object> formattedRow = new LinkedHashMap<>();
            for (String field : fields) {
                formattedRow.put(field, row.get(field));
            }
            return formattedRow;
        }).collect(Collectors.toList());
    }
}

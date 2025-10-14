package com.example.ai.tool.analysis.ai_tool_daisy_api.constant;

public final class QuestionnaireInstructions {

    public static final String PROMPT1_INSTRUCTION = "{\n" +
            "  \"patient_id\": \"Extraheer de patiëntnaam uit de inhoud.\",\n" +
            "  \"analysis\": {\n" +
            "    \"preliminary_tpd_hypothesis\": \"Formuleer een voorlopige TPD-hypothese\",\n" +
            "    \"dysregulation_fields\": [\"Identificeer de vermoedelijke teleonische dysregulatievelden.\"],\n" +
            "    \"signs_symptoms_clusters\": [\"Beschrijf de belangrijkste Signs & Symptoms-clusters.\"],\n" +
            "    \"heta_terms_usage\": \"Gebruik HETA-termen alleen als inhoudelijke duiding, niet als nieuwe voorstellen.\",\n" +
            "    \"data_gaps\": [\"Identificeer eventuele significante datalacunes.\"]\n" +
            "  },\n" +
            "  \"routing\": {\n" +
            "    \"additional_questionnaires\": [\"Stel voor welke aanvullende gevalideerde vragenlijstcodes noodzakelijk zijn.\"],\n" +
            "    \"ethos_variant\": \"Bepaal welke ETHOS-variant (Volwassene, Kind, Hybride) het meest passend is.\",\n" +
            "    \"heta_modules\": [\"Stel voor welke HETA-modules (Daisy-modulaire vragenlijsten) geactiveerd moeten worden.\"]\n" +
            "  }\n" +
            "}";
}

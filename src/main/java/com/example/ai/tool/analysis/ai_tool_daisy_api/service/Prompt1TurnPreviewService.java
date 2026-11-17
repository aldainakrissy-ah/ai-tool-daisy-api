package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1ExperimentService.IntakeFile;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1SourcePackageService.KeyText;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * A free look at the turn-based route before it runs: the Step 0 inventory of the uploaded client
 * files, the system message cut per turn, and the complete first turn (A1) as it would be sent,
 * built from our knowledge tables. No OpenAI call and nothing stored.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Prompt1TurnPreviewService {

    private static final List<String> TURNS = List.of("A1", "A2", "A3", "B", "C", "D");

    private final Prompt1SourcePackageService sourcePackageService;
    private final Prompt1ReleaseService releaseService;

    /** The preview of the A1 turn; texts only when asked for (they hold client data). */
    public record Preview(String release, String systemMessageFile, int systemMessageCharacters,
                          Map<String, Integer> systemMessageCharactersPerTurn, int observationCount,
                          List<String> emptyIntakeFields, Map<String, String> documentVersions,
                          List<String> keysFound, List<String> keysNotFound, int a1MessageCharacters,
                          int a1EstimatedInputTokens, String a1SystemMessage, String a1Message) {
    }

    /** @param releaseId a release of the release store (e.g. a DRAFT); null = the release a run would use now */
    public Preview previewA1(List<IntakeFile> files, boolean withText, String releaseId) {
        Prompt1ExperimentService.requireIntake(files);
        Prompt1Release release = releaseId == null || releaseId.isBlank()
                ? releaseService.current() : releaseService.byId(releaseId.strip());
        Prompt1TurnConfig config = release.config();
        String systemMessage = release.systemMessage();
        String doc18 = release.doc18();

        Map<String, Integer> perTurn = new LinkedHashMap<>();
        for (String turn : TURNS) {
            perTurn.put(turn, Prompt1TurnSystemMessage.forTurn(systemMessage, turn.equals("C") ? "C_x" : turn, config).length());
        }
        Prompt1TurnInventory.Inventory inventory = Prompt1TurnInventory.build(files);
        Map<String, String> versions = release.documentVersions();
        List<KeyText> step1 = sourcePackageService.readKeys(Prompt1TurnMessages.A1_STEP1_KEYS, versions);
        List<KeyText> step2 = sourcePackageService.readKeys(Prompt1TurnMessages.A1_STEP2_KEYS, versions);
        String a1System = Prompt1TurnSystemMessage.forTurn(systemMessage, "A1", config);
        String a1 = Prompt1TurnMessages.a1(config, "preview", LocalDate.now().toString(), inventory.manifest(),
                inventory.observations(), step1, step2, Prompt1TurnSystemMessage.doc18Part(doc18, "A1", config));

        List<String> found = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        for (KeyText key : concat(step1, step2)) {
            (key.found() ? found : notFound).add(key.key());
        }
        List<String> nulls = Prompt1Tsv.rows(inventory.observations()).stream()
                .filter(row -> Prompt1TurnInventory.isNullField(row)).map(row -> row.get("observation_id")).toList();
        log.info("Prompt 1 turn preview: {} observations, A1 {} characters, keys found {}/{}", inventory.observationCount(),
                a1.length(), found.size(), found.size() + notFound.size());
        return new Preview(release.name(), release.systemMessageFile(), systemMessage.length(),
                perTurn, inventory.observationCount(), nulls, versions, found, notFound, a1.length(),
                (a1System.length() + a1.length()) / 4, withText ? a1System : null, withText ? a1 : null);
    }

    private static List<KeyText> concat(List<KeyText> a, List<KeyText> b) {
        List<KeyText> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }
}

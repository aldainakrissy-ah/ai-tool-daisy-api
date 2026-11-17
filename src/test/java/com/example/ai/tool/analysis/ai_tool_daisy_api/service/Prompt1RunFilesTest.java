package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.Prompt1RunFileEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.Prompt1RunFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Prompt1RunFilesTest {

    private final Map<String, Prompt1RunFileEntity> stored = new TreeMap<>();
    private Prompt1RunFiles files;

    private Prompt1RunFileRepository repository;

    @Test
    void anActiveRunReadsItsWorkingFilesFromMemoryButStillWritesEveryChange() {
        files.write("run1", "meanings.tsv", "old\n");
        files.open("run1");
        files.write("run1", "meanings.tsv", "new\n");
        files.write("run1", "prompts/A1.txt", "a large prompt\n");
        org.mockito.Mockito.clearInvocations(repository);

        assertEquals("new\n", files.require("run1", "meanings.tsv"));
        assertEquals(Map.of("meanings.tsv", "new\n"), files.workingFiles("run1"), "no prompts among the working files");
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).findByRunIdAndName("run1", "meanings.tsv");
        assertEquals("new\n", stored.get("run1/meanings.tsv").getContent(), "the database has the change too");

        files.close("run1");
        assertEquals("new\n", files.require("run1", "meanings.tsv"), "after the run: read from the database");
        org.mockito.Mockito.verify(repository).findByRunIdAndName("run1", "meanings.tsv");
    }

    @BeforeEach
    void setUp() {
        repository = mock(Prompt1RunFileRepository.class);
        when(repository.findByRunIdAndName(anyString(), anyString()))
                .thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0) + "/" + call.getArgument(1))));
        when(repository.upsert(anyString(), anyString(), anyString(), any())).thenAnswer(call -> {
            stored.put(call.getArgument(0) + "/" + call.getArgument(1), Prompt1RunFileEntity.builder()
                    .runId(call.getArgument(0)).name(call.getArgument(1)).content(call.getArgument(2))
                    .updatedAt(call.getArgument(3)).build());
            return 1;
        });
        when(repository.findNamesOfRun(anyString())).thenAnswer(call -> stored.values().stream()
                .filter(file -> file.getRunId().equals(call.getArgument(0))).map(file -> file.getName()).sorted().toList());
        when(repository.findByRunIdOrderByNameAsc(anyString())).thenAnswer(call -> stored.values().stream()
                .filter(file -> file.getRunId().equals(call.getArgument(0))).toList());
        when(repository.findByRunIdAndNameNotLikeOrderByNameAsc(anyString(), anyString())).thenAnswer(call -> stored.values().stream()
                .filter(file -> file.getRunId().equals(call.getArgument(0))
                        && !file.getName().startsWith(call.<String>getArgument(1).replace("%", "")))
                .sorted(java.util.Comparator.comparing(file -> file.getName())).toList());
        files = new Prompt1RunFiles(repository);
    }

    @Test
    void closingStepsNumbersThemInOrderAndKeepsEarlierOnes() {
        files.closeSteps("run1", "0,1,2,3");
        files.closeSteps("run1", "4");
        files.closeSteps("run1", "3,eindcontrole");

        List<Map<String, String>> rows = Prompt1Tsv.rows(files.require("run1", "tasks.tsv"));
        assertEquals(16, rows.size());
        assertEquals(List.of("task_id", "step", "status", "seq"), new ArrayList<>(rows.get(0).keySet()));
        assertEquals("T00", rows.get(0).get("task_id"));
        assertEquals("1", rows.get(0).get("seq"));
        assertEquals("closed", rows.get(4).get("status"));
        assertEquals("5", rows.get(4).get("seq"));
        assertEquals("4", rows.get(3).get("seq"), "a closed step keeps its first number");
        assertEquals("not_started", rows.get(5).get("status"));
        assertEquals("—", rows.get(5).get("seq"));
        assertEquals("T15", rows.get(15).get("task_id"));
        assertEquals("6", rows.get(15).get("seq"));
        assertThrows(IllegalArgumentException.class, () -> files.closeSteps("run1", "15"));
    }

    @Test
    void retrievalLogNumbersEveryKeyRead() {
        files.logRetrieval("run1", "1", "KEY: 01:1.2", "01_BiomatrixAI_Primer_v4.md", "01:1.2");
        files.logRetrieval("run1", "9", "KEY: R3:22.27.4:SIG", "R3_HETA_Full_Entry_Retrieval_Mirror_v06.md", "");

        List<Map<String, String>> rows = Prompt1Tsv.rows(files.require("run1", "retrieval_log.tsv"));
        assertEquals(2, rows.size());
        assertEquals("1", rows.get(0).get("seq"));
        assertEquals("2", rows.get(1).get("seq"));
        assertEquals("9", rows.get(1).get("step"));
        assertEquals("KEY: R3:22.27.4:SIG", rows.get(1).get("query"));
        assertEquals("—", rows.get(1).get("chunk_ids"));
    }

    @Test
    void filesAreKeptPerRun() {
        files.write("run1", "meanings.tsv", "a\n");
        files.append("run1", "meanings.tsv", "b\n");
        files.write("run2", "meanings.tsv", "other\n");

        assertEquals("a\nb\n", files.require("run1", "meanings.tsv"));
        assertEquals(List.of("meanings.tsv"), files.list("run2"));
        assertFalse(files.has("run1", "links.tsv"));
        assertThrows(IllegalStateException.class, () -> files.require("run1", "links.tsv"));
    }

    @Test
    void tsvHelpersReadListsAndNumbers() {
        assertEquals(List.of("M001", "M002"), Prompt1Tsv.ids(" M001, —,M002 ;"));
        assertEquals(16, Prompt1Tsv.number("FD_016"));
        assertNull(Prompt1Tsv.number("—"));
        assertEquals("—", Prompt1Tsv.cell(""));
        assertEquals("a b", Prompt1Tsv.cell("a\tb"));
    }
}

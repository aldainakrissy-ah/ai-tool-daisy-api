package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.Prompt1RunEntity.Status;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.Prompt1RunFileRepository;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.Prompt1RunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** The daily clean-up deletes the files of finished runs after the retention period, never of an open run. */
class Prompt1RunFileCleanupTest {

    @Test
    void filesOfFinishedRunsOlderThanTheRetentionAreDeleted() {
        Prompt1RunRepository runs = mock(Prompt1RunRepository.class);
        Prompt1RunFileRepository files = mock(Prompt1RunFileRepository.class);
        LocalDateTime now = LocalDateTime.of(2026, 11, 14, 3, 30);
        when(runs.findIdsFinishedBefore(now.minusDays(30), List.of(Status.VALID, Status.FAILED, Status.INTERRUPTED)))
                .thenReturn(List.of("old1", "old2"));
        when(files.deleteAllOfRun(anyString())).thenReturn(24);
        Prompt1RunFileCleanup cleanup = new Prompt1RunFileCleanup(runs, files);
        ReflectionTestUtils.setField(cleanup, "retentionDays", 30);

        assertEquals(2, cleanup.deleteRunFilesFinishedBefore(now));
        verify(files).deleteAllOfRun("old1");
        verify(files).deleteAllOfRun("old2");
        verifyNoMoreInteractions(files);
    }

    @Test
    void aRetentionOfZeroKeepsEverything() {
        Prompt1RunRepository runs = mock(Prompt1RunRepository.class);
        Prompt1RunFileRepository files = mock(Prompt1RunFileRepository.class);
        Prompt1RunFileCleanup cleanup = new Prompt1RunFileCleanup(runs, files);
        ReflectionTestUtils.setField(cleanup, "retentionDays", 0);

        assertEquals(0, cleanup.deleteRunFilesFinishedBefore(LocalDateTime.now()));
        verify(runs, never()).findIdsFinishedBefore(any(), any());
        verifyNoInteractions(files);
    }
}

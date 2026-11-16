package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.Prompt1RunEntity.Status;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.Prompt1RunFileRepository;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.Prompt1RunRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Deletes the stored working files, prompts and answers of finished Prompt 1 runs after a retention
 * period. They are needed during a run (Jan's protocol between turns, the backend rules, the run check)
 * and kept for a while for recovery, debugging and traceability; the run summary ({@code prompt1_run}:
 * status, tokens, cost, notes, result) and the UI result are kept. A run that is still queued or running
 * is never touched. Logs show counts only, never content.
 */
@Slf4j
@Service
public class Prompt1RunFileCleanup {

    private static final List<Status> FINISHED = List.of(Status.VALID, Status.FAILED, Status.INTERRUPTED);

    private final Prompt1RunRepository runRepository;
    private final Prompt1RunFileRepository fileRepository;

    /** Days after a run ended before its files are deleted; 0 or less switches the clean-up off. */
    @Value("${prompt1.run-files.retention-days:30}")
    private int retentionDays;

    public Prompt1RunFileCleanup(Prompt1RunRepository runRepository, Prompt1RunFileRepository fileRepository) {
        this.runRepository = runRepository;
        this.fileRepository = fileRepository;
    }

    /**
     * Daily, at the time of {@code prompt1.run-files.cleanup-cron} (default 03:30). Transactional here as well:
     * the call below is a self-call, which does not pass through Spring's proxy, so its own annotation would not
     * start the transaction the delete query needs.
     */
    @Scheduled(cron = "${prompt1.run-files.cleanup-cron:0 30 3 * * *}")
    @Transactional
    public void deleteExpiredRunFiles() {
        deleteRunFilesFinishedBefore(LocalDateTime.now());
    }

    /**
     * Deletes the files of every finished run that ended more than {@code retention-days} before the given time.
     *
     * @return the number of runs whose files were deleted
     */
    @Transactional
    public int deleteRunFilesFinishedBefore(LocalDateTime now) {
        if (retentionDays <= 0) {
            return 0;
        }
        List<String> runs = runRepository.findIdsFinishedBefore(now.minusDays(retentionDays), FINISHED);
        int files = 0;
        for (String runId : runs) {
            files += fileRepository.deleteAllOfRun(runId);
        }
        if (!runs.isEmpty()) {
            log.info("Prompt 1 run-file clean-up: {} file(s) of {} run(s) older than {} days deleted", files, runs.size(),
                    retentionDays);
        }
        return runs.size();
    }
}

package com.example.ai.tool.analysis.ai_tool_daisy_api.repository;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.Prompt1RunFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface Prompt1RunFileRepository extends JpaRepository<Prompt1RunFileEntity, Long> {
    Optional<Prompt1RunFileEntity> findByRunIdAndName(String runId, String name);
    List<Prompt1RunFileEntity> findByRunIdOrderByNameAsc(String runId);

    /** The working files of a run without the stored prompts and answers (names starting with the given prefix). */
    List<Prompt1RunFileEntity> findByRunIdAndNameNotLikeOrderByNameAsc(String runId, String excludedPattern);

    /** The file names of a run, alphabetical, without loading their content. */
    @Query("select f.name from Prompt1RunFileEntity f where f.runId = :runId order by f.name")
    List<String> findNamesOfRun(@Param("runId") String runId);

    /** Deletes every stored file of a run in one statement (no file content is loaded); returns the number deleted. */
    @Modifying
    @Query("delete from Prompt1RunFileEntity f where f.runId = :runId")
    int deleteAllOfRun(@Param("runId") String runId);
}

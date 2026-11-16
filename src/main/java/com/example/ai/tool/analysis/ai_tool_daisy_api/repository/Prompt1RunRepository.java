package com.example.ai.tool.analysis.ai_tool_daisy_api.repository;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.Prompt1RunEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface Prompt1RunRepository extends JpaRepository<Prompt1RunEntity, String> {
    List<Prompt1RunEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<Prompt1RunEntity> findByStatusIn(Collection<Prompt1RunEntity.Status> statuses);

    /** The IDs of the runs with one of these statuses that finished before the given time (no result text loaded). */
    @Query("select r.id from Prompt1RunEntity r where r.finishedAt < :before and r.status in :statuses")
    List<String> findIdsFinishedBefore(@Param("before") LocalDateTime before,
                                       @Param("statuses") Collection<Prompt1RunEntity.Status> statuses);
}

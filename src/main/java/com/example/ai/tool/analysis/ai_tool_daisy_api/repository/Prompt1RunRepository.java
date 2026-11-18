package com.example.ai.tool.analysis.ai_tool_daisy_api.repository;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.Prompt1RunEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface Prompt1RunRepository extends JpaRepository<Prompt1RunEntity, String> {
    List<Prompt1RunEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<Prompt1RunEntity> findByStatusIn(Collection<Prompt1RunEntity.Status> statuses);

    /** The newest run of a client with one of these statuses (the duplicate guard: QUEUED or RUNNING). */
    Optional<Prompt1RunEntity> findFirstByClientIdAndStatusInOrderByCreatedAtDesc(
            String clientId, Collection<Prompt1RunEntity.Status> statuses);

    List<Prompt1RunEntity> findByClientIdOrderByCreatedAtDesc(String clientId, Pageable pageable);

    List<Prompt1RunEntity> findByProfessionalNameOrderByCreatedAtDesc(String professionalName, Pageable pageable);

    /** Records the phase a run has reached, without loading or writing the rest of the run. */
    @Modifying
    @Transactional
    @Query("update Prompt1RunEntity r set r.currentTurn = :turn where r.id = :id")
    void updateCurrentTurn(@Param("id") String id, @Param("turn") String turn);

    /** The IDs of the runs with one of these statuses that finished before the given time (no result text loaded). */
    @Query("select r.id from Prompt1RunEntity r where r.finishedAt < :before and r.status in :statuses")
    List<String> findIdsFinishedBefore(@Param("before") LocalDateTime before,
                                       @Param("statuses") Collection<Prompt1RunEntity.Status> statuses);
}

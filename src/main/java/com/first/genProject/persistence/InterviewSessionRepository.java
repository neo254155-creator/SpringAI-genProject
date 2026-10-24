package com.first.genProject.persistence;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface InterviewSessionRepository extends JpaRepository<InterviewSessionEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InterviewSessionEntity s where s.id = :id")
    Optional<InterviewSessionEntity> findForUpdate(@Param("id") UUID id);
}

package com.first.genProject.persistence;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface InterviewSessionRepository extends JpaRepository<InterviewSessionEntity, UUID> {
    Optional<InterviewSessionEntity> findByIdAndOwnerId(UUID id, UUID ownerId);
    org.springframework.data.domain.Page<InterviewSessionEntity> findByOwnerId(UUID ownerId, org.springframework.data.domain.Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InterviewSessionEntity s where s.id = :id and s.ownerId = :owner")
    Optional<InterviewSessionEntity> findOwnedForUpdate(@Param("id") UUID id, @Param("owner") UUID owner);
}

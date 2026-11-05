package com.first.genProject.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface StudyDocumentRepository extends JpaRepository<StudyDocumentEntity, UUID> {
    Optional<StudyDocumentEntity> findByIdAndOwnerId(UUID id, UUID ownerId);
    org.springframework.data.domain.Page<StudyDocumentEntity> findByOwnerId(UUID ownerId, org.springframework.data.domain.Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from StudyDocumentEntity d where d.id = :id and d.ownerId = :owner")
    Optional<StudyDocumentEntity> findOwnedForUpdate(@Param("id") UUID id, @Param("owner") UUID owner);
}

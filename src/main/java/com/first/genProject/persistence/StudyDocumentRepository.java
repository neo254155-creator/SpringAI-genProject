package com.first.genProject.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface StudyDocumentRepository extends JpaRepository<StudyDocumentEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from StudyDocumentEntity d where d.id = :id")
    Optional<StudyDocumentEntity> findForUpdate(@Param("id") UUID id);
}

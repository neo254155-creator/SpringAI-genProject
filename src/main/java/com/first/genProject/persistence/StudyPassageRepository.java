package com.first.genProject.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyPassageRepository extends JpaRepository<StudyPassageEntity, UUID> {
    List<StudyPassageEntity> findByDocument_IdOrderByPassageNumberAsc(UUID documentId);
}

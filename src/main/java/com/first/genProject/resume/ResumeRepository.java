package com.first.genProject.resume;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeRepository extends JpaRepository<ResumeEntity, UUID> {
    java.util.Optional<ResumeEntity> findByIdAndOwnerId(UUID id, UUID ownerId);
}

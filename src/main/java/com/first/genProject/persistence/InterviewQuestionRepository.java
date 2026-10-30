package com.first.genProject.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestionEntity, UUID> {
    Page<InterviewQuestionEntity> findBySession_Id(UUID sessionId, Pageable pageable);
    long countBySession_Id(UUID sessionId);
}

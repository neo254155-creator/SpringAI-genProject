package com.first.genProject.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewAnswerRepository extends JpaRepository<InterviewAnswerEntity, UUID> {
}

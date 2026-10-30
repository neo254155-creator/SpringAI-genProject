package com.first.genProject.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentSearchIndexRepository extends JpaRepository<DocumentSearchIndexEntity, UUID> { }

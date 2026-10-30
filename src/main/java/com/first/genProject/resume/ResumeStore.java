package com.first.genProject.resume;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.first.genProject.exception.InterviewNotFoundException;

@Service
public class ResumeStore {
    private final ResumeRepository repository;
    public ResumeStore(ResumeRepository repository) { this.repository = repository; }
    @Transactional
    public ResumeProfile save(String filename, List<ResumeSkill> skills) {
        return repository.save(new ResumeEntity(filename, skills)).view();
    }
    @Transactional(readOnly = true)
    public ResumeProfile get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new InterviewNotFoundException("Resume")).view();
    }
}

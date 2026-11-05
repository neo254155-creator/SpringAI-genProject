package com.first.genProject.resume;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.first.genProject.exception.InterviewNotFoundException;

@Service
public class ResumeStore {
    private final ResumeRepository repository;
    private final com.first.genProject.auth.CurrentAccount current;
    public ResumeStore(ResumeRepository repository, com.first.genProject.auth.CurrentAccount current) { this.repository = repository; this.current = current; }
    @Transactional
    public ResumeProfile save(String filename, List<ResumeSkill> skills) {
        var entity = new ResumeEntity(filename, skills); entity.setOwnerId(current.id());
        return repository.save(entity).view();
    }
    @Transactional(readOnly = true)
    public ResumeProfile get(UUID id) {
        return repository.findByIdAndOwnerId(id, current.id()).orElseThrow(() -> new InterviewNotFoundException("Resume")).view();
    }
}

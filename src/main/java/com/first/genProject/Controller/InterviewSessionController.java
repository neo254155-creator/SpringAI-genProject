package com.first.genProject.Controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.first.genProject.dto.*;
import com.first.genProject.service.InterviewHistoryStore;
import com.first.genProject.service.InterviewSessionService;

@RestController
@RequestMapping("/interview")
public class InterviewSessionController {
    private final InterviewSessionService service;
    private final InterviewHistoryStore store;

    public InterviewSessionController(InterviewSessionService service, InterviewHistoryStore store) {
        this.service = service;
        this.store = store;
    }

    @PostMapping("/sessions")
    public ResponseEntity<SessionResponse> create(@RequestBody InterviewSelection selection) {
        var session = service.create(selection);
        return ResponseEntity.created(URI.create("/interview/sessions/" + session.id())).body(session);
    }

    @GetMapping("/sessions")
    public PageResponse<SessionResponse> sessions(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return store.sessions(page, size);
    }

    @GetMapping("/sessions/{id}")
    public SessionResponse session(@PathVariable UUID id) { return store.session(id); }

    @PostMapping("/sessions/{id}/questions")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public StoredQuestionResponse generateQuestion(@PathVariable UUID id) {
        return service.generateQuestion(id);
    }

    @GetMapping("/sessions/{id}/questions")
    public PageResponse<StoredQuestionResponse> questions(@PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return store.questions(id, page, size);
    }

    @PostMapping("/questions/{id}/answers")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public StoredAnswerResponse answer(@PathVariable UUID id, @RequestBody AnswerSubmission submission) {
        return service.submitAnswer(id, submission);
    }

    @GetMapping("/questions/{id}/answers")
    public PageResponse<StoredAnswerResponse> answers(@PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return store.answers(id, page, size);
    }

    @PostMapping("/sessions/{id}/complete")
    public SessionResponse complete(@PathVariable UUID id) { return store.complete(id); }

    @GetMapping("/progress")
    public List<TopicProgress> progress() { return store.progress(); }
}

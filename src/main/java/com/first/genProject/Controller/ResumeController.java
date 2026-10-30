package com.first.genProject.Controller;

import java.net.URI;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.first.genProject.dto.SessionResponse;
import com.first.genProject.resume.*;

@RestController
@RequestMapping("/resumes")
public class ResumeController {
    public record PastedResume(String text) { }
    private final ResumeService service;
    private final ResumeStore store;
    public ResumeController(ResumeService service, ResumeStore store) { this.service = service; this.store = store; }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResumeProfile> upload(@RequestParam MultipartFile file) { return created(service.upload(file)); }
    @PostMapping("/text")
    public ResponseEntity<ResumeProfile> paste(@RequestBody PastedResume request) {
        if (request == null) throw new com.first.genProject.exception.InvalidInputException("Resume text is required.");
        return created(service.paste(request.text()));
    }
    @GetMapping("/{id}")
    public ResumeProfile get(@PathVariable UUID id) { return store.get(id); }
    @PostMapping("/{id}/sessions")
    public ResponseEntity<SessionResponse> start(@PathVariable UUID id, @RequestBody ResumeSessionRequest request) {
        var session = service.start(id, request);
        return ResponseEntity.created(URI.create("/interview/sessions/" + session.id())).body(session);
    }
    private ResponseEntity<ResumeProfile> created(ResumeProfile profile) {
        return ResponseEntity.created(URI.create("/resumes/" + profile.id())).body(profile);
    }
}

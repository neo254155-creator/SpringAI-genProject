package com.first.genProject.Controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

import com.first.genProject.dto.InterviewQuestion;
import com.first.genProject.dto.InterviewSelection;
import com.first.genProject.service.InterviewService;

@RestController
@RequestMapping("/interview")
public class InterviewController {
    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @GetMapping("/options")
    public Map<String, List<String>> options() {
        return Map.of("tracks", InterviewSelection.TRACKS, "topics", InterviewSelection.TOPICS,
                "levels", InterviewSelection.LEVELS);
    }

    @PostMapping("/questions")
    public InterviewQuestion question(@RequestBody InterviewSelection selection) {
        selection.validate();
        return interviewService.generateQuestion(selection);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidSelection(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> invalidBody() {
        return ResponseEntity.badRequest().body(Map.of("error", "A valid JSON selection is required"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> providerFailure() {
        return ResponseEntity.status(502).body(Map.of("error", "Could not generate a question. Please try again."));
    }
}

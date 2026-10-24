package com.first.genProject.Controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.first.genProject.dto.InterviewAnswerRequest;
import com.first.genProject.dto.InterviewFeedback;
import com.first.genProject.service.InterviewFeedbackService;

@RestController
public class InterviewFeedbackController {
    private final InterviewFeedbackService feedbackService;

    public InterviewFeedbackController(InterviewFeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping(value = "/interview/feedback", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public InterviewFeedback feedback(@RequestBody InterviewAnswerRequest request) {
        return feedbackService.evaluateAnswer(request);
    }
}

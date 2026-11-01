package com.first.genProject.Controller;

import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import com.first.genProject.dto.DocumentAnswerResponse;
import com.first.genProject.dto.DocumentSearchRequest;
import com.first.genProject.service.DocumentAnswerService;

@RestController
@RequestMapping("/documents/{id}")
public class DocumentAnswerController {
    private final DocumentAnswerService service;

    public DocumentAnswerController(DocumentAnswerService service) { this.service = service; }

    @PostMapping("/answer")
    public DocumentAnswerResponse answer(@PathVariable UUID id, @RequestBody DocumentSearchRequest request) {
        return service.answer(id, request);
    }
}

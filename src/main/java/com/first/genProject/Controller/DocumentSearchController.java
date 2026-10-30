package com.first.genProject.Controller;

import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import com.first.genProject.dto.*;
import com.first.genProject.service.DocumentSearchService;

@RestController
@RequestMapping("/documents/{id}")
public class DocumentSearchController {
    private final DocumentSearchService service;

    public DocumentSearchController(DocumentSearchService service) { this.service = service; }

    @PostMapping("/index")
    public DocumentIndexResponse index(@PathVariable UUID id) { return service.index(id); }

    @PostMapping("/search")
    public DocumentSearchResponse search(@PathVariable UUID id, @RequestBody DocumentSearchRequest request) {
        return service.search(id, request);
    }
}

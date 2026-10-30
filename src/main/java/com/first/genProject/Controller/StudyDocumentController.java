package com.first.genProject.Controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.first.genProject.dto.*;
import com.first.genProject.service.StudyDocumentService;

@RestController
@RequestMapping("/documents")
public class StudyDocumentController {
    private final StudyDocumentService service;

    public StudyDocumentController(StudyDocumentService service) { this.service = service; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StudyDocumentResponse> upload(@RequestParam String title,
            @RequestParam MultipartFile file) {
        var document = service.upload(title, file);
        return ResponseEntity.created(URI.create("/documents/" + document.id())).body(document);
    }

    @GetMapping
    public PageResponse<StudyDocumentResponse> documents(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.documents(page, size);
    }

    @GetMapping("/{id}")
    public StudyDocumentResponse document(@PathVariable UUID id) { return service.document(id); }

    @GetMapping("/{id}/passages")
    public List<StudyPassageResponse> passages(@PathVariable UUID id) { return service.passages(id); }
}

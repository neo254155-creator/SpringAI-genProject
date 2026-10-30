package com.first.genProject.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.first.genProject.dto.*;
import com.first.genProject.exception.*;
import com.first.genProject.persistence.*;

@Service
public class StudyDocumentService {
    public static final int MAX_BYTES = 65_536;
    private static final int PASSAGE_LENGTH = 1200;
    private final StudyDocumentRepository documents;
    private final StudyPassageRepository passages;

    public StudyDocumentService(StudyDocumentRepository documents, StudyPassageRepository passages) {
        this.documents = documents;
        this.passages = passages;
    }

    @Transactional
    public StudyDocumentResponse upload(String title, MultipartFile file) {
        if (title == null || title.isBlank() || title.strip().length() > 200 || title.indexOf('\0') >= 0) {
            throw new InvalidInputException("title must contain between 1 and 200 characters");
        }
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new InvalidInputException("Upload a nonempty text file of at most 65536 bytes");
        }
        String filename = file.getOriginalFilename();
        if (filename == null) filename = "";
        // Keep a display name only; never interpret uploaded filenames as filesystem paths.
        filename = filename.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        String extension = filename.toLowerCase(Locale.ROOT);
        if (filename.length() > 255 || filename.chars().anyMatch(Character::isISOControl)
                || !(extension.endsWith(".txt") || extension.endsWith(".md"))) {
            throw new InvalidInputException("Upload a .txt or .md file with a filename of at most 255 characters");
        }
        String text = readText(file);
        if (text.isBlank() || text.chars().anyMatch(c -> Character.isISOControl(c)
                && c != '\n' && c != '\r' && c != '\t')) {
            throw new InvalidInputException("The file must contain readable, nonblank UTF-8 text");
        }
        var document = documents.save(new StudyDocumentEntity(title.strip(), filename));
        int number = 1;
        for (int start = 0; start < text.length();) {
            int end = Math.min(start + PASSAGE_LENGTH, text.length());
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) end--;
            passages.save(new StudyPassageEntity(document, number++, text.substring(start, end)));
            start = end;
        }
        return view(document);
    }

    @Transactional(readOnly = true)
    public StudyDocumentResponse document(UUID id) { return view(load(id)); }

    @Transactional(readOnly = true)
    public List<StudyPassageResponse> passages(UUID id) {
        load(id);
        return passages.findByDocument_IdOrderByPassageNumberAsc(id).stream()
                .map(p -> new StudyPassageResponse(p.getId(), p.getPassageNumber(), p.getPassageText())).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<StudyDocumentResponse> documents(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidInputException("page must be nonnegative and size must be between 1 and 100");
        }
        var result = documents.findAll(PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt", "id"))).map(this::view);
        return new PageResponse<>(result.getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    private String readText(MultipartFile file) {
        try (var input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new InvalidInputException("File exceeds 65536 bytes");
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            return text.startsWith("\uFEFF") ? text.substring(1) : text;
        } catch (CharacterCodingException exception) {
            throw new InvalidInputException("The file must use UTF-8 encoding");
        } catch (IOException exception) {
            throw new InvalidInputException("The uploaded file could not be read");
        }
    }

    private StudyDocumentEntity load(UUID id) {
        return documents.findById(id).orElseThrow(() -> new InterviewNotFoundException("Document"));
    }

    private StudyDocumentResponse view(StudyDocumentEntity document) {
        return new StudyDocumentResponse(document.getId(), document.getTitle(), document.getFilename(), document.getCreatedAt());
    }
}

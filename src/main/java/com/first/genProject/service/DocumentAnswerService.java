package com.first.genProject.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import com.first.genProject.dto.DocumentAnswerResponse;
import com.first.genProject.dto.DocumentSearchRequest;
import com.first.genProject.dto.DocumentSearchResponse;
import com.first.genProject.exception.AiProviderException;
import com.first.genProject.exception.InvalidInputException;
import tools.jackson.databind.json.JsonMapper;

/** Read-only retrieval followed by a chat call, with no database transaction held open. */
@Service
public class DocumentAnswerService {
    public record GeneratedAnswer(String answer, List<UUID> passageIds, Boolean insufficientEvidence) { }

    private static final String NOT_ENOUGH_EVIDENCE =
            "The retrieved notes do not contain enough information to answer this question.";
    private final DocumentSearchService search;
    private final ChatClient chat;
    private final JsonMapper json = JsonMapper.builder().build();
    private final BeanOutputConverter<GeneratedAnswer> converter = new BeanOutputConverter<>(GeneratedAnswer.class);
    private final SystemPromptTemplate system = new SystemPromptTemplate(new ClassPathResource("Prompt/DocumentAnswerSystem"));
    private final PromptTemplate user = new PromptTemplate(new ClassPathResource("Prompt/DocumentAnswerUser"));

    public DocumentAnswerService(DocumentSearchService search, ChatClient chat) {
        this.search = search;
        this.chat = chat;
    }

    public DocumentAnswerResponse answer(UUID id, DocumentSearchRequest request) {
        if (request == null) throw new InvalidInputException("Document question is required");
        var retrieved = search.search(id, request.validated());
        if (retrieved.matches().isEmpty()) return insufficient(retrieved);

        final GeneratedAnswer generated;
        try {
            String evidence = json.writeValueAsString(Map.of("query", retrieved.query(),
                    "passages", retrieved.matches().stream().map(p -> Map.of(
                            "passageId", p.passageId(), "number", p.number(), "text", InputSummary.shorten(p.text(), 500))).toList()));
            var prompt = new Prompt(system.createMessage(Map.of("format", converter.getFormat())),
                    user.createMessage(Map.of("evidence", evidence)));
            String output = chat.prompt(prompt).call().content();
            if (output == null || output.isBlank() || output.length() > 16000) throw new AiProviderException();
            generated = converter.convert(output);
        } catch (RuntimeException exception) {
            if (exception instanceof com.first.genProject.exception.InvalidInputException invalid) throw invalid;
            throw new AiProviderException(exception);
        }

        if (generated == null || generated.insufficientEvidence() == null || generated.passageIds() == null
                || generated.passageIds().size() > retrieved.matches().size()
                || generated.passageIds().stream().anyMatch(java.util.Objects::isNull)
                || new HashSet<>(generated.passageIds()).size() != generated.passageIds().size()) {
            throw new AiProviderException();
        }
        if (generated.insufficientEvidence()) {
            if (!generated.passageIds().isEmpty()) throw new AiProviderException();
            // Discard generated prose when evidence is insufficient rather than returning an unsupported answer.
            return insufficient(retrieved);
        }
        if (generated.answer() == null || generated.answer().isBlank() || generated.answer().length() > 6000
                || generated.passageIds().isEmpty()) throw new AiProviderException();
        var sources = retrieved.matches().stream()
                .collect(Collectors.toMap(DocumentSearchResponse.Match::passageId, Function.identity()));
        if (generated.passageIds().stream().anyMatch(p -> !sources.containsKey(p))) throw new AiProviderException();
        return new DocumentAnswerResponse(retrieved.documentId(), retrieved.title(), retrieved.query(),
                generated.answer().strip(), false, generated.passageIds().stream().map(sources::get).toList());
    }

    private DocumentAnswerResponse insufficient(DocumentSearchResponse retrieved) {
        return new DocumentAnswerResponse(retrieved.documentId(), retrieved.title(), retrieved.query(),
                NOT_ENOUGH_EVIDENCE, true, List.of());
    }
}

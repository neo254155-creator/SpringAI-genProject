package com.first.genProject.resume;

import java.util.*;
import java.util.regex.Pattern;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.*;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.first.genProject.dto.InterviewSelection;
import com.first.genProject.dto.SessionResponse;
import com.first.genProject.exception.*;
import com.first.genProject.service.InterviewHistoryStore;

@Service
public class ResumeService {
    public record ExtractedSkills(List<ResumeSkill> skills) { }
    private final ResumeTextReader reader;
    private final ResumeStore store;
    private final InterviewHistoryStore interviews;
    private final ChatClient chat;
    private final BeanOutputConverter<ExtractedSkills> converter = new BeanOutputConverter<>(ExtractedSkills.class);
    private final SystemPromptTemplate system = new SystemPromptTemplate(new ClassPathResource("Prompt/ResumeSkillsSystem"));
    private final PromptTemplate user = new PromptTemplate(new ClassPathResource("Prompt/ResumeSkillsUser"));
    public ResumeService(ResumeTextReader reader, ResumeStore store, InterviewHistoryStore interviews, ChatClient chat) {
        this.reader = reader; this.store = store; this.interviews = interviews; this.chat = chat;
    }
    public ResumeProfile upload(MultipartFile file) {
        var read = reader.read(file);
        return extract(read.filename(), read.text());
    }
    public ResumeProfile paste(String text) { return extract("Pasted resume", ResumeTextReader.validateText(text)); }
    private ResumeProfile extract(String filename, String text) {
        final List<ResumeSkill> skills;
        try {
            String output = chat.prompt(new Prompt(system.createMessage(Map.of("format", converter.getFormat())),
                    user.createMessage(Map.of("resumeText", text)))).call().content();
            if (output == null || output.isBlank()) throw new AiProviderException();
            var result = converter.convert(output);
            skills = verifySkills(text, result == null ? null : result.skills());
        } catch (RuntimeException exception) { throw new AiProviderException(exception); }
        // The raw file/full resume text is not persisted; only validated skill excerpts are saved.
        return store.save(filename, skills);
    }
    public static List<ResumeSkill> verifySkills(String text, List<ResumeSkill> skills) {
        if (skills == null || skills.size() > 12) throw new AiProviderException();
        var names = new HashSet<String>();
        var verified = new ArrayList<ResumeSkill>();
        String normalizedText = normalize(text);
        for (var skill : skills) {
            if (skill == null || skill.name() == null || skill.evidence() == null) throw new AiProviderException();
            String name = skill.name().strip(), evidence = skill.evidence().strip();
            if (name.isBlank() || name.length() > 80 || evidence.isBlank() || evidence.length() > 240
                    || name.chars().anyMatch(Character::isISOControl)
                    || !names.add(normalize(name)) || !normalizedText.contains(normalize(evidence))
                    || !Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(normalize(name)) + "(?![\\p{L}\\p{N}])")
                            .matcher(normalize(evidence)).find()) throw new AiProviderException();
            verified.add(new ResumeSkill(name, evidence));
        }
        return List.copyOf(verified);
    }
    private static String normalize(String value) { return value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }
    public SessionResponse start(UUID id, ResumeSessionRequest request) {
        if (request == null || !InterviewSelection.LEVELS.contains(request.level() == null ? "" : request.level())
                || request.skills() == null || request.skills().isEmpty() || request.skills().size() > 12
                || request.skills().stream().anyMatch(Objects::isNull)
                || new HashSet<>(request.skills()).size() != request.skills().size())
            throw new InvalidInputException("Confirm 1–12 extracted skills and choose beginner, intermediate, or advanced.");
        var profile = store.get(id);
        var selected = request.skills().stream().map(name -> profile.skills().stream()
                .filter(s -> s.name().equals(name)).findFirst().orElseThrow(() ->
                    new InvalidInputException("Only skills extracted from this resume can be selected. Review or upload another resume."))).toList();
        return interviews.createResume(new ResumeInterviewContext(profile.id(), profile.filename(), selected), request.level());
    }
}

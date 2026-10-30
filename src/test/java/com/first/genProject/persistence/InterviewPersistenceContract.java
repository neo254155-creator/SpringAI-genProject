package com.first.genProject.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.first.genProject.support.FeedbackFixtures.VALID_FEEDBACK;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import com.first.genProject.dto.*;
import com.first.genProject.exception.*;
import com.first.genProject.service.*;
import com.first.genProject.support.StubChatModel;

@SpringBootTest(properties = {
        "spring.ai.openai.api-key=offline-test-key",
        "spring.ai.openai.chat.model=offline-test-model"
})
@AutoConfigureMockMvc
@Import(InterviewPersistenceContract.OfflineAiConfiguration.class)
abstract class InterviewPersistenceContract {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired InterviewSessionService service;
    @Autowired InterviewHistoryStore store;
    @Autowired TransactionCheckingModel model;
    @Autowired com.first.genProject.resume.ResumeService resumeService;
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void cleanDatabase() {
        for (String table : new String[] {"feedback_correct_points", "feedback_missing_points",
                "interview_feedback", "interview_answers", "interview_questions", "interview_sessions", "resume_profiles"}) {
            jdbc.update("delete from " + table);
        }
        model.calls = 0;
        model.failure = null;
        model.beforeReply = null;
        model.response = "How does dependency injection work?";
    }

    @Test
    void resumeExtractionConfirmationInterviewFeedbackAndSkillProgressWorkTogether() throws Exception {
        model.response = """
                {"skills":[{"name":"Python","evidence":"Built tools with Python."},
                {"name":"PostgreSQL","evidence":"Used PostgreSQL for storage."}]}
                """;
        String body = mvc.perform(post("/resumes/text").contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Built tools with Python. Used PostgreSQL for storage.\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.skills[0].name").value("Python"))
                .andReturn().getResponse().getContentAsString();
        UUID resumeId = UUID.fromString(json.readTree(body).get("id").asString());
        String sessionBody = mvc.perform(post("/resumes/{id}/sessions", resumeId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"skills\":[\"Python\",\"PostgreSQL\"],\"level\":\"intermediate\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.resume.skills.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        UUID sessionId = UUID.fromString(json.readTree(sessionBody).get("id").asString());
        model.response = "How does a Python generator evaluate values lazily?";
        var first = service.generateQuestion(sessionId);
        assertEquals("Python", first.skill());
        assertTrue(model.prompt.getUserMessage().getText().contains("Built tools with Python."));
        assertFalse(model.prompt.getSystemMessage().getText().contains("Java technical interviewer"));
        model.response = VALID_FEEDBACK.replace("Explain constructor injection", "Explain lazy iteration");
        service.submitAnswer(first.id(), new AnswerSubmission("A generator yields values when requested."));
        assertTrue(model.prompt.getUserMessage().getText().contains("Topic: Python"));
        model.response = "Explain how you would describe yield to a teammate.";
        var followUp = service.generateQuestion(sessionId);
        assertEquals("Python", followUp.skill());
        assertEquals("clarity", followUp.focus().criterion());
        model.response = VALID_FEEDBACK.replace("\"score\": 4", "\"score\": 5")
                .replace("\"score\": 2", "\"score\": 3").replace("\"score\": 1", "\"score\": 2");
        service.submitAnswer(followUp.id(), new AnswerSubmission("A clear, complete explanation."));
        model.response = "When would you add an index in PostgreSQL?";
        assertEquals("PostgreSQL", service.generateQuestion(sessionId).skill());
        mvc.perform(get("/interview/sessions/{id}/resume", sessionId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.session.resume.resumeId").value(resumeId.toString()))
                .andExpect(jsonPath("$.question.skill").value("PostgreSQL"));
        mvc.perform(get("/interview/progress")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].topic").value("Python"));
        store.complete(sessionId);
        assertThrows(InterviewStateException.class, () -> service.generateQuestion(sessionId));
    }

    @Test
    void fabricatedExtractionAndUnconfirmedSkillsCannotStartAnInterview() throws Exception {
        model.response = "{\"skills\":[{\"name\":\"Kubernetes\",\"evidence\":\"Used Kubernetes\"}]}";
        mvc.perform(post("/resumes/text").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"I use Python\"}"))
                .andExpect(status().isBadGateway());
        assertEquals(0L, jdbc.queryForObject("select count(*) from resume_profiles", Long.class));
        model.response = "{\"skills\":[{\"name\":\"Python\",\"evidence\":\"I use Python\"}]}";
        var profile = resumeService.paste("I use Python");
        int calls = model.calls;
        mvc.perform(post("/resumes/{id}/sessions", profile.id()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"skills\":[\"Java\"],\"level\":\"beginner\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/resumes/{id}/sessions", profile.id()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"skills\":[],\"level\":\"beginner\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(calls, model.calls);
        assertEquals(0L, jdbc.queryForObject("select count(*) from interview_sessions", Long.class));
    }

    @Test
    void confirmedSubsetIsPersistedAndExtractionWithoutSkillsHasNoDefaults() throws Exception {
        model.response = "{\"skills\":[]}";
        assertTrue(resumeService.paste("I enjoy learning new things.").skills().isEmpty());
        model.response = "{\"skills\":[{\"name\":\"Python\",\"evidence\":\"Python and SQL\"},{\"name\":\"SQL\",\"evidence\":\"Python and SQL\"}]}";
        var profile = resumeService.paste("Python and SQL");
        var session = resumeService.start(profile.id(), new com.first.genProject.resume.ResumeSessionRequest(java.util.List.of("SQL"), "advanced"));
        var saved = store.session(session.id());
        assertEquals(1, saved.resume().skills().size());
        assertEquals("SQL", saved.resume().skills().getFirst().name());
        model.response = "What tradeoffs arise with SQL transaction isolation?";
        assertEquals("SQL", service.generateQuestion(session.id()).skill());
    }

    @Test
    void nextQuestionUsesLatestFeedbackAndPersistsItsFocus() throws Exception {
        var first = createQuestion("Spring Boot");
        assertNull(first.focus());
        model.response = VALID_FEEDBACK;
        service.submitAnswer(first.id(), new AnswerSubmission("Private learner answer"));
        model.response = "How would you describe constructor injection clearly to a teammate?";
        var next = service.generateQuestion(first.sessionId());
        assertEquals("clarity", next.focus().criterion());
        assertTrue(next.focus().reason().contains("1/2"));
        String prompt = model.prompt.getUserMessage().getText();
        assertTrue(prompt.contains("Practice focus: clarity"));
        assertTrue(prompt.contains("Explain constructor injection"));
        assertTrue(prompt.contains(first.question()));
        assertFalse(prompt.contains("Private learner answer"));
        mvc.perform(get("/interview/sessions/{id}/resume", first.sessionId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.question.focus.criterion").value("clarity"));
        mvc.perform(get("/interview/sessions/{id}/questions", first.sessionId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].focus.criterion").value("clarity"));
        // The most recent attempt replaces earlier evidence; a perfect result has no weakness.
        model.response = VALID_FEEDBACK.replace("\"score\": 4", "\"score\": 5")
                .replace("\"score\": 2", "\"score\": 3").replace("\"score\": 1", "\"score\": 2");
        service.submitAnswer(first.id(), new AnswerSubmission("Improved answer"));
        model.response = "When would a prototype bean be appropriate?";
        assertNull(service.generateQuestion(first.sessionId()).focus());
        assertTrue(model.prompt.getUserMessage().getText().contains("Practice focus: General practice"));
    }

    @Test
    void adaptiveEvidenceIsSessionScopedAndRecentQuestionsAreBounded() {
        var first = createQuestion("Spring Boot");
        model.response = VALID_FEEDBACK;
        service.submitAnswer(first.id(), new AnswerSubmission("Answer"));
        var other = createSession("Spring Boot");
        model.response = "What is a Spring bean?";
        assertNull(service.generateQuestion(other.id()).focus());
        assertFalse(model.prompt.getUserMessage().getText().contains("Explain constructor injection"));
        for (int i = 0; i < 7; i++) store.saveQuestion(first.sessionId(), "Recent question " + i);
        assertEquals(5, store.generationContext(first.sessionId()).recentQuestions().size());
        assertFalse(store.generationContext(first.sessionId()).recentQuestions().contains(first.question()));
    }

    @Test
    void repeatedQuestionsAndLateGenerationAfterCompletionAreNotSaved() {
        var first = createQuestion("Spring Boot");
        model.response = "  HOW DOES DEPENDENCY INJECTION WORK?  ";
        assertThrows(AiProviderException.class, () -> service.generateQuestion(first.sessionId()));
        assertEquals(1, store.questions(first.sessionId(), 0, 20).totalElements());
        model.response = "What happens during bean initialization?";
        model.beforeReply = () -> store.complete(first.sessionId());
        assertThrows(InterviewStateException.class, () -> service.generateQuestion(first.sessionId()));
        assertEquals(1, store.questions(first.sessionId(), 0, 20).totalElements());
    }

    @Test
    void resumeReturnsSavedStateWithoutAiAndCountsDistinctAnsweredQuestions() throws Exception {
        var session = createSession("Spring Boot");
        mvc.perform(get("/interview/sessions/{id}/resume", session.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.session.status").value("ACTIVE"))
                .andExpect(jsonPath("$.questionsAnswered").value(0))
                .andExpect(jsonPath("$.question").isEmpty()).andExpect(jsonPath("$.answer").isEmpty());
        var question = store.saveQuestion(session.id(), "Explain Spring beans");
        model.response = VALID_FEEDBACK;
        service.submitAnswer(question.id(), new AnswerSubmission("First attempt"));
        var latest = service.submitAnswer(question.id(), new AnswerSubmission("Second attempt"));
        int calls = model.calls;
        mvc.perform(get("/interview/sessions/{id}/resume", session.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.question.id").value(question.id().toString()))
                .andExpect(jsonPath("$.answer.id").value(latest.id().toString()))
                .andExpect(jsonPath("$.answer.answer").value("Second attempt"))
                .andExpect(jsonPath("$.answer.feedback.score").value(7))
                .andExpect(jsonPath("$.questionsAnswered").value(1));
        var pending = store.saveQuestion(session.id(), "Explain bean scopes");
        mvc.perform(get("/interview/sessions/{id}/resume", session.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.question.id").value(pending.id().toString()))
                .andExpect(jsonPath("$.answer").isEmpty()).andExpect(jsonPath("$.questionsAnswered").value(1));
        store.complete(session.id());
        mvc.perform(get("/interview/sessions/{id}/resume", session.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.session.status").value("COMPLETED"));
        assertEquals(calls, model.calls);
        assertEquals(2, rowCount("interview_questions"));
        assertEquals(2, rowCount("interview_answers"));
    }

    @Test
    void missingResumeSessionReturns404() throws Exception {
        mvc.perform(get("/interview/sessions/{id}/resume", UUID.randomUUID()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        assertEquals(0, model.calls);
    }

    @Test
    void apiCreatesAndReloadsCompleteHistory() throws Exception {
        String body = mvc.perform(post("/interview/sessions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"track\":\"Java\",\"topic\":\"Spring Boot\",\"level\":\"intermediate\"}"))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("ACTIVE")).andReturn().getResponse().getContentAsString();
        UUID sessionId = UUID.fromString(json.readTree(body).get("id").asText());
        assertEquals(0, model.calls);
        body = mvc.perform(post("/interview/sessions/{id}/questions", sessionId))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID questionId = UUID.fromString(json.readTree(body).get("id").asText());
        model.response = VALID_FEEDBACK;
        mvc.perform(post("/interview/questions/{id}/answers", questionId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\"Spring injects dependencies.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rubricVersion").value("java-interview-v1"))
                .andExpect(jsonPath("$.feedback.score").value(7));
        assertTrue(model.prompt.getUserMessage().getText().contains("How does dependency injection work?"));
        mvc.perform(get("/interview/sessions/{id}", sessionId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.topic").value("Spring Boot"));
        mvc.perform(get("/interview/sessions/{id}/questions", sessionId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(questionId.toString()));
        mvc.perform(get("/interview/questions/{id}/answers", questionId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].answer").value("Spring injects dependencies."))
                .andExpect(jsonPath("$.content[0].feedback.correctPoints[0]").value("Identifies dependency injection"))
                .andExpect(jsonPath("$.content[0].feedback.missingPoints[0]").value("Explain constructor injection"))
                .andExpect(jsonPath("$.content[0].feedback.rubric.correctness.reason").value("Minor technical imprecision"));
        mvc.perform(get("/interview/progress")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].attempts").value(1))
                .andExpect(jsonPath("$[0].averageScore").value(7.0));
        assertEquals(1, rowCount("interview_sessions"));
        assertEquals(1, rowCount("interview_answers"));
        assertEquals(1, rowCount("interview_feedback"));
    }

    @Test
    void failedGenerationDoesNotSaveQuestion() {
        var session = createSession("Spring Boot");
        model.failure = new IllegalStateException("provider failure");
        assertThrows(AiProviderException.class, () -> service.generateQuestion(session.id()));
        assertEquals(0, rowCount("interview_questions"));
        assertEquals(1, rowCount("interview_sessions"));
    }

    @Test
    void failedFeedbackDoesNotSaveAnswerOrProgress() {
        var question = createQuestion("Spring Boot");
        model.response = "invalid provider output";
        assertThrows(AiProviderException.class,
                () -> service.submitAnswer(question.id(), new AnswerSubmission("My answer")));
        assertEquals(0, rowCount("interview_answers"));
        assertEquals(0, rowCount("interview_feedback"));
        assertTrue(store.progress().isEmpty());
    }

    @Test
    void databaseFailureRollsBackAnswerAndFeedbackTogether() {
        var question = createQuestion("Spring Boot");
        model.response = VALID_FEEDBACK;
        var valid = new InterviewFeedbackService(ChatClient.create(model)).evaluateAnswer(
                new InterviewAnswerRequest("Java", "Spring Boot", "intermediate", question.question(), "Answer"));
        var inconsistent = new InterviewFeedback(valid.correctPoints(), valid.missingPoints(), valid.explanation(),
                valid.rubric(), 8, 10); // Components total 7: database must reject this.
        assertThrows(DataIntegrityViolationException.class, () -> store.saveAnswer(question.id(), "Answer", inconsistent));
        assertEquals(0, rowCount("interview_answers"));
        assertEquals(0, rowCount("interview_feedback"));
        assertEquals(0, rowCount("feedback_correct_points"));
    }

    @Test
    void progressAggregatesAttemptsAndKeepsTopicsSeparate() {
        var question = createQuestion("Spring Boot");
        model.response = VALID_FEEDBACK;
        service.submitAnswer(question.id(), new AnswerSubmission("First attempt"));
        model.response = VALID_FEEDBACK.replace("\"score\": 4", "\"score\": 5")
                .replace("\"score\": 2", "\"score\": 3").replace("\"score\": 1", "\"score\": 2");
        service.submitAnswer(question.id(), new AnswerSubmission("Second attempt"));
        var coreQuestion = createQuestion("Core Java");
        model.response = VALID_FEEDBACK;
        service.submitAnswer(coreQuestion.id(), new AnswerSubmission("Another topic"));
        createSession("Core Java"); // An empty session must not inflate progress.
        var progress = store.progress();
        assertEquals(2, progress.size());
        var spring = progress.stream().filter(p -> p.topic().equals("Spring Boot")).findFirst().orElseThrow();
        assertEquals(2, spring.attempts());
        assertEquals(1, spring.questionsAnswered());
        assertEquals(8.5, spring.averageScore());
        assertEquals(10, spring.bestScore());
        assertNotNull(spring.lastPracticedAt());
        assertEquals(2, store.answers(question.id(), 0, 20).totalElements());
    }

    @Test
    void completionIsIdempotentAndPreventsFurtherAiCalls() throws Exception {
        var question = createQuestion("Spring Boot");
        var completed = store.complete(question.sessionId());
        assertEquals("COMPLETED", completed.status());
        assertEquals(completed.completedAt(), store.complete(question.sessionId()).completedAt());
        int calls = model.calls;
        mvc.perform(post("/interview/sessions/{id}/questions", question.sessionId()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SESSION_COMPLETED"));
        mvc.perform(post("/interview/questions/{id}/answers", question.id()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"answer\":\"Answer\"}")).andExpect(status().isConflict());
        assertEquals(calls, model.calls);
        assertEquals(1, store.questions(question.sessionId(), 0, 20).totalElements());
    }

    @Test
    void completingWhileAiRunsPreventsLateAnswerSave() {
        var question = createQuestion("Spring Boot");
        model.response = VALID_FEEDBACK;
        model.beforeReply = () -> store.complete(question.sessionId());
        assertThrows(InterviewStateException.class,
                () -> service.submitAnswer(question.id(), new AnswerSubmission("My answer")));
        assertEquals(0, rowCount("interview_answers"));
        assertEquals("COMPLETED", store.session(question.sessionId()).status());
    }

    @Test
    void rejectsMissingResourcesBadInputAndPagination() throws Exception {
        UUID missing = UUID.randomUUID();
        mvc.perform(get("/interview/sessions/{id}", missing)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(post("/interview/sessions/{id}/questions", missing)).andExpect(status().isNotFound());
        mvc.perform(post("/interview/questions/{id}/answers", missing).contentType(MediaType.APPLICATION_JSON)
                .content("{\"answer\":\"Answer\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/interview/sessions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/interview/sessions/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(get("/interview/sessions").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/interview/sessions").param("size", "101")).andExpect(status().isBadRequest());
        assertEquals(0, model.calls);
    }

    @Test
    void sessionHistoryIsPaginated() throws Exception {
        createSession("Spring Boot");
        createSession("Core Java");
        createSession("Spring Boot");
        mvc.perform(get("/interview/sessions").param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(3));
        assertTrue(store.progress().isEmpty());
    }

    @Test
    void oversizedQuestionAndBlankAnswerAreNotStored() throws Exception {
        var session = createSession("Spring Boot");
        model.response = "q".repeat(2001);
        assertThrows(AiProviderException.class, () -> service.generateQuestion(session.id()));
        assertEquals(0, rowCount("interview_questions"));
        var question = createQuestion("Spring Boot");
        int calls = model.calls;
        mvc.perform(post("/interview/questions/{id}/answers", question.id()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"answer\":\" \"}")).andExpect(status().isBadRequest());
        assertEquals(calls, model.calls);
        assertEquals(0, rowCount("interview_answers"));
    }

    private SessionResponse createSession(String topic) {
        return service.create(new InterviewSelection("Java", topic, "intermediate"));
    }

    private StoredQuestionResponse createQuestion(String topic) {
        model.response = "How does dependency injection work?";
        return service.generateQuestion(createSession(topic).id());
    }

    private long rowCount(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    static class TransactionCheckingModel extends StubChatModel {
        Runnable beforeReply;
        @Override
        public ChatResponse call(Prompt prompt) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                    "Do not hold database transactions open during AI calls");
            if (beforeReply != null) {
                Runnable action = beforeReply;
                beforeReply = null;
                action.run();
            }
            return super.call(prompt);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OfflineAiConfiguration {
        @Bean @Primary
        TransactionCheckingModel offlineChatModel() { return new TransactionCheckingModel(); }
    }
}

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
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void cleanDatabase() {
        for (String table : new String[] {"feedback_correct_points", "feedback_missing_points",
                "interview_feedback", "interview_answers", "interview_questions", "interview_sessions"}) {
            jdbc.update("delete from " + table);
        }
        model.calls = 0;
        model.failure = null;
        model.beforeReply = null;
        model.response = "How does dependency injection work?";
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

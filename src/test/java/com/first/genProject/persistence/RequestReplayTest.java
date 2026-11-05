package com.first.genProject.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.first.genProject.support.FeedbackFixtures.VALID_FEEDBACK;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import com.first.genProject.config.RetryRequestFilter;
import com.first.genProject.dto.*;
import com.first.genProject.service.*;

@org.springframework.security.test.context.support.WithMockUser(username = com.first.genProject.support.AccountTestConfiguration.ID)
@Import({com.first.genProject.support.AccountTestConfiguration.class, InterviewPersistenceContract.OfflineAiConfiguration.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.ai.openai.api-key=offline-test-key", "spring.ai.openai.chat.model=offline-test-model",
        "spring.ai.openai.base-url=http://127.0.0.1:1" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties", properties =
        "spring.datasource.url=jdbc:h2:mem:request-replay-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")

class RequestReplayTest {
    private static HttpServer provider;
    private static final AtomicInteger providerRequests = new AtomicInteger();
    @Autowired org.springframework.ai.openai.OpenAiChatModel configuredModel;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired Environment environment;
    @Autowired WebApplicationContext context;
    @Autowired InterviewSessionService service;
    @Autowired InterviewPersistenceContract.TransactionCheckingModel model;
    private final JsonMapper json = JsonMapper.builder().build();
    private static final String SELECTION = "{\"track\":\"Java\",\"topic\":\"Spring Boot\",\"level\":\"beginner\"}";

    @DynamicPropertySource
    static void fakeProvider(DynamicPropertyRegistry registry) throws Exception {
        provider = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/", exchange -> {
            providerRequests.incrementAndGet();
            byte[] body = "{\"error\":{\"message\":\"Fixture only\",\"type\":\"server_error\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        provider.start();
        registry.add("spring.ai.openai.base-url", () -> "http://127.0.0.1:" + provider.getAddress().getPort());
    }

    @AfterAll static void stopProvider() { if (provider != null) provider.stop(0); }

    @Test
    void configuredSdkMakesOnlyOneAttemptOnATransientProviderFailure() {
        providerRequests.set(0);
        assertThrows(RuntimeException.class, () -> configuredModel.call(new org.springframework.ai.chat.prompt.Prompt("Offline fixture")));
        assertEquals(1, providerRequests.get(), "The SDK must not silently retry a paid call");
    }

    @BeforeEach
    void reset() {
        for (String table : new String[] { "request_replays", "feedback_correct_points", "feedback_missing_points",
                "interview_feedback", "interview_answers", "interview_questions", "interview_sessions", "resume_profiles" })
            jdbc.update("delete from " + table);
        model.calls = 0; model.failure = null; model.beforeReply = null;
        model.response = "How does dependency injection work?";
    }

    @Test
    void lostSessionResponseReplaysTheSameResourceAndLocation() throws Exception {
        String key = UUID.randomUUID().toString();
        var first = mvc.perform(post("/interview/sessions").header("Idempotency-Key", key)
                .contentType("application/json").content(SELECTION)).andExpect(status().isCreated()).andReturn();
        mvc.perform(post("/interview/sessions").header("Idempotency-Key", key)
                .contentType("application/json").content(SELECTION)).andExpect(status().isCreated())
                .andExpect(header().string("Location", first.getResponse().getHeader("Location")))
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(content().json(first.getResponse().getContentAsString()));
        assertEquals(1L, count("interview_sessions")); assertEquals(0, model.calls);
        assertNotEquals(key, jdbc.queryForObject("select key_hash from request_replays", String.class));
    }

    @Test
    void changedInputAndRouteCannotReuseAKey() throws Exception {
        String key = UUID.randomUUID().toString();
        mvc.perform(post("/interview/sessions").header("Idempotency-Key", key)
                .contentType("application/json").content(SELECTION)).andExpect(status().isCreated());
        mvc.perform(post("/interview/sessions").header("Idempotency-Key", key)
                .contentType("application/json").content(SELECTION.replace("beginner", "advanced")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REQUEST_KEY_REUSED"));
        mvc.perform(post("/resumes/text").header("Idempotency-Key", key)
                .contentType("application/json").content("{\"text\":\"I use Python\"}"))
                .andExpect(status().isConflict());
        assertEquals(1L, count("interview_sessions")); assertEquals(0, model.calls);
    }

    @Test
    void multipartRetriesWithDifferentBoundariesDoNotRepeatExtraction() throws Exception {
        model.response = "{\"skills\":[{\"name\":\"Python\",\"evidence\":\"I use Python\"}]}";
        String key = UUID.randomUUID().toString();
        var client = HttpClient.newBuilder().cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
        String csrf = login(client);
        var first = upload(client, key, "boundary-one", "I use Python", csrf);
        var second = upload(client, key, "boundary-two", "I use Python", csrf);
        assertEquals(201, first.statusCode()); assertEquals(first.body(), second.body());
        assertEquals(201, second.statusCode());
        assertEquals("true", second.headers().firstValue("Idempotency-Replayed").orElseThrow());
        assertEquals(409, upload(client, key, "boundary-three", "I use Python differently", csrf).statusCode());
        assertEquals(1, model.calls); assertEquals(1L, count("resume_profiles"));
    }

    @Test
    void aFreshFilterUsesThePersistedQuestionResult() throws Exception {
        var session = session(); String key = UUID.randomUUID().toString();
        String route = "/interview/sessions/" + session.id() + "/questions";
        String first = mvc.perform(post(route).header("Idempotency-Key", key)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var fresh = new RetryRequestFilter(new RequestLedger(jdbc));
        var request = new MockHttpServletRequest("POST", route);
        request.addHeader("Idempotency-Key", key); request.setUserPrincipal(() -> com.first.genProject.support.AccountTestConfiguration.ID);
        var response = new MockHttpServletResponse();
        fresh.doFilter(request, response, (req, res) -> fail("A fresh filter must replay without executing the action"));
        assertEquals(201, response.getStatus()); assertEquals(first, response.getContentAsString());
        assertEquals("true", response.getHeader("Idempotency-Replayed"));
        assertEquals(1, model.calls); assertEquals(1L, count("interview_questions"));
        mvc.perform(get("/interview/sessions/{id}/resume", session.id()).header("Idempotency-Key", key))
                .andExpect(status().isOk()).andExpect(header().string("Idempotency-Status", "completed"));
        assertEquals(1, model.calls, "Opening saved progress only reconciles the key; it never calls AI");
    }

    @Test
    void answerReplayDoesNotInflateProgressButAnExplicitNewAttemptCan() throws Exception {
        var question = service.generateQuestion(session().id()); model.calls = 0; model.response = VALID_FEEDBACK;
        String route = "/interview/questions/" + question.id() + "/answers", key = UUID.randomUUID().toString();
        String body = "{\"answer\":\"I use constructor injection.\"}";
        String first = mvc.perform(post(route).header("Idempotency-Key", key).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(post(route).header("Idempotency-Key", key).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(content().json(first));
        mvc.perform(get("/interview/progress")).andExpect(jsonPath("$[0].attempts").value(1));
        assertEquals(1, model.calls);
        mvc.perform(post(route).header("Idempotency-Key", UUID.randomUUID()).contentType("application/json").content(body))
                .andExpect(status().isCreated());
        mvc.perform(get("/interview/progress")).andExpect(jsonPath("$[0].attempts").value(2));
        assertEquals(2, model.calls);
    }

    @Test
    void concurrentDuplicatesAreBlockedBeforeASecondModelCall() throws Exception {
        String route = "/interview/sessions/" + session().id() + "/questions", key = UUID.randomUUID().toString();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        model.beforeReply = () -> {
            entered.countDown();
            try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
        };
        var first = CompletableFuture.supplyAsync(() -> {
            try { return mvc.perform(post(route).header("Idempotency-Key", key)).andReturn().getResponse().getStatus(); }
            catch (Exception error) { throw new CompletionException(error); }
        });
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            mvc.perform(post(route).header("Idempotency-Key", key)).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("REQUEST_PENDING"));
        } finally { release.countDown(); }
        assertEquals(201, first.get(10, TimeUnit.SECONDS)); assertEquals(1, model.calls);
        assertEquals(1L, count("interview_questions"));
    }

    @Test
    void knownProviderFailuresReplayUntilAnExplicitNewRequest() throws Exception {
        String route = "/interview/sessions/" + session().id() + "/questions", key = UUID.randomUUID().toString();
        model.failure = new IllegalStateException("private provider details");
        for (int i = 0; i < 2; i++) mvc.perform(post(route).header("Idempotency-Key", key))
                .andExpect(status().isBadGateway()).andExpect(header().string("Idempotency-Status", "failed"));
        assertEquals(1, model.calls); assertEquals(0L, count("interview_questions"));
        model.failure = null;
        mvc.perform(post(route).header("Idempotency-Key", UUID.randomUUID())).andExpect(status().isCreated());
        assertEquals(2, model.calls);
    }

    @Test
    void anUnknownServerOutcomeNeverExecutesAgain() throws Exception {
        var filter = new RetryRequestFilter(new RequestLedger(jdbc)); String key = UUID.randomUUID().toString();
        var first = rawRequest(key); var response = new MockHttpServletResponse();
        filter.doFilter(first, response, (request, result) -> {
            var http = (jakarta.servlet.http.HttpServletResponse) result;
            http.setStatus(500); http.getWriter().write("{\"code\":\"INTERNAL_ERROR\"}");
        });
        assertEquals("UNKNOWN", jdbc.queryForObject("select state from request_replays", String.class));
        var replay = new MockHttpServletResponse();
        filter.doFilter(rawRequest(key), replay, (request, result) -> fail("Unknown outcome must not execute again"));
        assertEquals(409, replay.getStatus()); assertTrue(replay.getContentAsString().contains("REQUEST_PENDING"));
    }

    @Test
    void malformedKeysAreRejectedBeforeStorageOrAi() throws Exception {
        mvc.perform(post("/resumes/text").header("Idempotency-Key", "not-a-uuid").contentType("application/json")
                .content("{\"text\":\"I use Python\"}")).andExpect(status().isBadRequest());
        assertEquals(0, model.calls); assertEquals(0L, count("request_replays"));
    }

    private SessionResponse session() { return service.create(new InterviewSelection("Java", "Spring Boot", "beginner")); }
    private long count(String table) { return jdbc.queryForObject("select count(*) from " + table, Long.class); }
    private MockHttpServletRequest rawRequest(String key) {
        var request = new MockHttpServletRequest("POST", "/interview/sessions");
        request.addHeader("Idempotency-Key", key); request.setContentType("application/json");
        request.setContent(SELECTION.getBytes(StandardCharsets.UTF_8)); return request;
    }
    private String login(HttpClient client) throws Exception {
        String base = "http://127.0.0.1:" + environment.getProperty("local.server.port");
        var token = json.readTree(client.send(HttpRequest.newBuilder(URI.create(base + "/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
        String body = "{\"username\":\"offline-fixture\",\"password\":\"" + com.first.genProject.support.AccountTestConfiguration.PASSWORD + "\"}";
        var result = client.send(HttpRequest.newBuilder(URI.create(base + "/auth/login"))
                .header("Content-Type", "application/json").header(token.get("headerName").asString(), token.get("token").asString())
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, result.statusCode());
        return json.readTree(client.send(HttpRequest.newBuilder(URI.create(base + "/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString()).body()).get("token").asString();
    }
    private HttpResponse<String> upload(HttpClient client, String key, String boundary, String text, String csrf) throws Exception {
        String body = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"resume.txt\"\r\n"
                + "Content-Type: text/plain\r\n\r\n" + text + "\r\n--" + boundary + "--\r\n";
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                + environment.getProperty("local.server.port") + "/resumes"))
                .timeout(java.time.Duration.ofSeconds(10)).header("Idempotency-Key", key)
                .header("X-CSRF-TOKEN", csrf)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}

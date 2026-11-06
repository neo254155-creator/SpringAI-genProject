package com.first.genProject.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.json.JsonMapper;
import com.first.genProject.support.StubChatModel;
import com.first.genProject.embedding.PassageEmbeddingClient;
import static com.first.genProject.support.FeedbackFixtures.VALID_FEEDBACK;

@SpringBootTest(properties = {"spring.ai.openai.api-key=offline-test-key", "spring.ai.openai.chat.model=offline-test-model",
        "spring.ai.openai.base-url=http://127.0.0.1:1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties", properties =
        "spring.datasource.url=jdbc:h2:mem:accounts-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Import(AccountSecurityTest.Offline.class)
class AccountSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired Accounts accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired StubChatModel model;
    @Autowired Embeddings embeddings;
    private final JsonMapper json = JsonMapper.builder().build();
    private static final String PASSWORD = "Offline test password 123!";

    @Test void onlySuccessfulPublicFilesReceiveEdgeCacheHeaders() throws Exception {
        for (String path:List.of("/", "/index.html", "/login.html", "/privacy.html", "/ui/design.css", "/ui/landing.js", "/resume-setup.js")) {
            mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control","public, max-age=0, must-revalidate"))
                    .andExpect(header().string("Vercel-CDN-Cache-Control",org.hamcrest.Matchers.containsString("max-age=")))
                    .andExpect(header().doesNotExist("Set-Cookie"));
        }
        for (String path:List.of("/auth/me", "/auth/csrf", "/frontend-config", "/dashboard.html", "/interview/sessions", "/ui/missing.js")) {
            mvc.perform(get(path)).andExpect(header().doesNotExist("Vercel-CDN-Cache-Control"))
                    .andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")));
        }
        mvc.perform(get("/ui/design.css").header("Authorization","Bearer unused"))
                .andExpect(header().doesNotExist("Vercel-CDN-Cache-Control"));
        mvc.perform(get("/ui/design.css").header("Range","bytes=0-10"))
                .andExpect(header().doesNotExist("Vercel-CDN-Cache-Control"));
        assertEquals(0,model.calls);
    }
    @Test void publicFileErrorsAndCookieResponsesAreNeverMarkedCacheable() throws Exception {
        for (int code:List.of(200,302,500)) {
            var request=new MockHttpServletRequest("GET","/login.html");
            var response=new MockHttpServletResponse();
            new com.first.genProject.config.PublicAssetCacheFilter().doFilter(request,response,(req,res)->{
                var http=(jakarta.servlet.http.HttpServletResponse)res;
                http.setStatus(code);
                if(code==200)http.addHeader("Set-Cookie","fixture=private; HttpOnly");
            });
            assertNull(response.getHeader("Vercel-CDN-Cache-Control"));
            assertEquals(code,response.getStatus());
        }
    }
    @BeforeEach void reset() {
        for (String table : List.of("request_replays", "document_search_indexes", "study_passages", "study_documents",
                "feedback_correct_points", "feedback_missing_points", "interview_feedback", "interview_answers",
                "interview_questions", "interview_sessions", "resume_profiles")) jdbc.update("delete from " + table);
        jdbc.update("update account_bootstrap set owner_id = null"); jdbc.update("delete from app_users");
        model.calls = 0; model.failure = null; embeddings.calls = 0;
    }

    @Test void anonymousAccessAndMissingCsrfNeverReachAi() throws Exception {
        mvc.perform(get("/interview/sessions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/documents")).andExpect(status().isUnauthorized());
        mvc.perform(get("/practice.html")).andExpect(status().isFound());
        mvc.perform(post("/resumes/text").with(csrf()).contentType("application/json").content("{\"text\":\"Python\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/register").contentType("application/json").content(credentials("learner")))
                .andExpect(status().isForbidden());
        assertEquals(0, model.calls); assertEquals(0, embeddings.calls);
    }

    @Test void firstLocalAccountClaimsExistingRowsExactlyOnce() throws Exception {
        UUID resume = UUID.randomUUID(), session = UUID.randomUUID(), document = UUID.randomUUID();
        jdbc.update("insert into resume_profiles(id,filename,skills_json,created_at) values (?,?,?,CURRENT_TIMESTAMP)", resume,"legacy.txt","[]");
        jdbc.update("insert into interview_sessions(id,track,topic,difficulty,status,created_at,version) values (?,?,?,?,?,CURRENT_TIMESTAMP,0)",
                session,"Java","Core Java","beginner","ACTIVE");
        jdbc.update("insert into study_documents(id,title,filename,created_at) values (?,?,?,CURRENT_TIMESTAMP)",document,"Legacy notes","notes.txt");
        var owner = member("owner"); var second = member("second");
        for (String table : List.of("resume_profiles","interview_sessions","study_documents"))
            assertEquals(owner.id(), jdbc.queryForObject("select owner_id from " + table, UUID.class));
        mvc.perform(get("/resumes/" + resume).with(user(owner.id().toString()))).andExpect(status().isOk());
        mvc.perform(get("/interview/sessions").with(user(owner.id().toString()))).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/interview/sessions").with(user(second.id().toString()))).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/documents/" + document).with(user(second.id().toString()))).andExpect(status().isNotFound());
        UUID later = UUID.randomUUID();
        jdbc.update("insert into study_documents(id,title,filename,created_at) values (?,?,?,CURRENT_TIMESTAMP)", later,"Unassigned later","later.txt");
        member("third");
        assertNull(jdbc.queryForObject("select owner_id from study_documents where id = ?", UUID.class, later));
        assertEquals(owner.id(), jdbc.queryForObject("select owner_id from account_bootstrap where id = 1", UUID.class));
    }

    @Test void remoteAndForwardedVisitorsCannotClaimTheFirstAccount() throws Exception {
        mvc.perform(post("/auth/register").with(csrf()).with(request -> {request.setRemoteAddr("203.0.113.5");return request;})
                .contentType("application/json").content(credentials("remote"))).andExpect(status().isForbidden());
        mvc.perform(post("/auth/register").with(csrf()).header("X-Forwarded-For","203.0.113.5")
                .contentType("application/json").content(credentials("forwarded"))).andExpect(status().isForbidden());
        assertEquals(0L,jdbc.queryForObject("select count(*) from app_users",Long.class));
    }

    @Test void loginRotatesTheSessionAndCsrfAndLogoutInvalidatesIt() throws Exception {
        var owner = member("owner");
        String encoded = jdbc.queryForObject("select password_hash from app_users",String.class);
        assertNotEquals(PASSWORD,encoded);assertTrue(passwords.matches(PASSWORD,encoded));
        var csrfResult=mvc.perform(get("/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session=(MockHttpSession)csrfResult.getRequest().getSession(false);String oldId=session.getId();
        var token=json.readTree(csrfResult.getResponse().getContentAsString());
        mvc.perform(post("/auth/login").session(session).header(token.get("headerName").asString(),token.get("token").asString())
                .contentType("application/json").content(credentials("OWNER"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(owner.id().toString())).andExpect(jsonPath("$.password").doesNotExist());
        assertNotEquals(oldId,session.getId());
        mvc.perform(get("/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("owner"));
        mvc.perform(post("/auth/logout").session(session).header(token.get("headerName").asString(),token.get("token").asString()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertTrue(session.isInvalid());mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test void duplicateNamesWeakPasswordsAndWrongLoginAreSafeErrors() throws Exception {
        member("learner");
        mvc.perform(post("/auth/register").with(csrf()).contentType("application/json").content(credentials("LEARNER")))
                .andExpect(status().isConflict());
        mvc.perform(post("/auth/register").with(csrf()).contentType("application/json")
                .content("{\"username\":\"short\",\"password\":\"short\"}")).andExpect(status().isBadRequest());
        for(String name:List.of("learner","unknown")) mvc.perform(post("/auth/login").with(csrf()).contentType("application/json")
                .content("{\"username\":\""+name+"\",\"password\":\"wrong\"}")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password."));
    }

    @Test void everyResourceAndProgressIsScopedBeforePaidWork() throws Exception {
        var a=member("alice");var b=member("bob");String aid=a.id().toString(),bid=b.id().toString();
        model.response="{\"skills\":[{\"name\":\"Python\",\"evidence\":\"I use Python\"}]}";
        String resume=id(mvc.perform(post("/resumes/text").with(user(aid)).with(csrf()).contentType("application/json")
                .content("{\"text\":\"I use Python\"}")).andExpect(status().isCreated()));
        String session=id(mvc.perform(post("/resumes/"+resume+"/sessions").with(user(aid)).with(csrf()).contentType("application/json")
                .content("{\"skills\":[\"Python\"],\"level\":\"beginner\"}")).andExpect(status().isCreated()));
        model.response="How does a Python generator work?";
        String question=id(mvc.perform(post("/interview/sessions/"+session+"/questions").with(user(aid)).with(csrf())).andExpect(status().isCreated()));
        model.response=VALID_FEEDBACK;
        mvc.perform(post("/interview/questions/"+question+"/answers").with(user(aid)).with(csrf()).contentType("application/json")
                .content("{\"answer\":\"Values are produced lazily.\"}")).andExpect(status().isCreated());
        String document=id(mvc.perform(multipart("/documents").file(new MockMultipartFile("file","notes.txt","text/plain",
                "Python generators produce values lazily.".getBytes(StandardCharsets.UTF_8))).param("title","My notes")
                .with(user(aid)).with(csrf())).andExpect(status().isCreated()));
        int calls=model.calls;
        for(String route:List.of("/resumes/"+resume,"/interview/sessions/"+session,"/interview/sessions/"+session+"/resume",
                "/interview/sessions/"+session+"/questions","/interview/questions/"+question+"/answers","/documents/"+document,
                "/documents/"+document+"/passages")) mvc.perform(get(route).with(user(bid))).andExpect(status().isNotFound());
        for(String route:List.of("/interview/sessions/"+session+"/questions","/interview/sessions/"+session+"/complete",
                "/documents/"+document+"/index"))mvc.perform(post(route).with(user(bid)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/resumes/"+resume+"/sessions").with(user(bid)).with(csrf()).contentType("application/json")
                .content("{\"skills\":[\"Python\"],\"level\":\"beginner\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/interview/questions/"+question+"/answers").with(user(bid)).with(csrf()).contentType("application/json")
                .content("{\"answer\":\"Try another account\"}")).andExpect(status().isNotFound());
        for(String action:List.of("search","answer")) mvc.perform(post("/documents/"+document+"/"+action).with(user(bid)).with(csrf())
                .contentType("application/json").content("{\"query\":\"generators\"}")).andExpect(status().isNotFound());
        mvc.perform(get("/interview/sessions").with(user(bid))).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/documents").with(user(bid))).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/interview/progress").with(user(bid))).andExpect(content().json("[]"));
        mvc.perform(get("/interview/progress").with(user(aid))).andExpect(jsonPath("$[0].attempts").value(1));
        assertEquals(calls,model.calls);assertEquals(0,embeddings.calls);
    }

    @Test void replayKeysCannotReplayAnotherUsersResponse() throws Exception {
        var a=member("alice");var b=member("bob");String key=UUID.randomUUID().toString();
        String selection="{\"track\":\"Java\",\"topic\":\"Core Java\",\"level\":\"beginner\"}";
        String aid=id(mvc.perform(post("/interview/sessions").with(user(a.id().toString())).with(csrf()).header("Idempotency-Key",key)
                .contentType("application/json").content(selection)).andExpect(status().isCreated()));
        String bid=id(mvc.perform(post("/interview/sessions").with(user(b.id().toString())).with(csrf()).header("Idempotency-Key",key)
                .contentType("application/json").content(selection)).andExpect(status().isCreated()));
        assertNotEquals(aid,bid);assertEquals(2L,jdbc.queryForObject("select count(*) from request_replays",Long.class));
        mvc.perform(post("/interview/sessions").with(csrf()).header("Idempotency-Key",key).contentType("application/json").content(selection))
                .andExpect(status().isUnauthorized());
    }

    @Test void evenTheLegacyChatGetRequiresCsrfBeforeBilling() throws Exception {
        var owner=member("owner");String id=owner.id().toString();
        mvc.perform(get("/chat").param("q","Explain Python").with(user(id))).andExpect(status().isForbidden());
        assertEquals(0,model.calls);model.response="A local fixture reply.";
        mvc.perform(get("/chat").param("q","Explain Python").with(user(id)).with(csrf())).andExpect(status().isOk());
        assertEquals(1,model.calls);
    }

    @Test void simultaneousFirstRegistrationsHaveOnlyOneLegacyOwner() throws Exception {
        UUID legacy=UUID.randomUUID();
        jdbc.update("insert into study_documents(id,title,filename,created_at) values (?,?,?,CURRENT_TIMESTAMP)",legacy,"Old notes","notes.txt");
        var first=java.util.concurrent.CompletableFuture.supplyAsync(()->member("first"));
        var second=java.util.concurrent.CompletableFuture.supplyAsync(()->member("second"));
        var a=first.get(10,java.util.concurrent.TimeUnit.SECONDS);var b=second.get(10,java.util.concurrent.TimeUnit.SECONDS);
        UUID owner=jdbc.queryForObject("select owner_id from account_bootstrap",UUID.class);
        assertTrue(Set.of(a.id(),b.id()).contains(owner));
        assertEquals(owner,jdbc.queryForObject("select owner_id from study_documents where id = ?",UUID.class,legacy));
        assertEquals(2L,jdbc.queryForObject("select count(*) from app_users",Long.class));
    }

    @Test void loginAttemptBudgetIsEnforcedWithoutStoringCredentials() throws Exception {
        var filter=new AccountSecurity.LoginThrottle();var count=new java.util.concurrent.atomic.AtomicInteger();
        for(int i=0;i<31;i++){
            var request=new MockHttpServletRequest("POST","/auth/login");request.setRemoteAddr("203.0.113.90");
            var response=new MockHttpServletResponse();filter.doFilter(request,response,(req,res)->count.incrementAndGet());
            if(i==30)assertEquals(429,response.getStatus());
        }
        assertEquals(30,count.get());
    }

    private Accounts.View member(String name){return accounts.register(new Accounts.Credentials(name,PASSWORD),"127.0.0.1");}
    private String credentials(String name){return "{\"username\":\""+name+"\",\"password\":\""+PASSWORD+"\"}";}
    private String id(ResultActions response)throws Exception{return json.readTree(response.andReturn().getResponse().getContentAsString()).get("id").asString();}
    static class Embeddings implements PassageEmbeddingClient{
        int calls;public String modelKey(){return "offline";}public List<double[]> embed(List<String> values){calls++;return values.stream().map(v->new double[]{1,0}).toList();}
    }
    @TestConfiguration(proxyBeanMethods=false)static class Offline{
        @Bean @Primary StubChatModel offlineModel(){return new StubChatModel();}
        @Bean @Primary Embeddings offlineEmbeddings(){return new Embeddings();}
    }
}

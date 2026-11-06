package com.first.genProject.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;
import com.first.genProject.auth.CurrentAccount;
import com.first.genProject.exception.*;
import com.first.genProject.config.QuotaAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import com.first.genProject.support.StubChatModel;
import com.first.genProject.embedding.*;

class AiQuotaTest {
    JdbcTemplate jdbc; DriverManagerDataSource source; JdbcTransactionManager manager; CurrentAccount account;
    @BeforeEach void setup() throws Exception {
        source=new DriverManagerDataSource("jdbc:h2:mem:quota"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc=new JdbcTemplate(source); manager=new JdbcTransactionManager(source);
        try(var connection=source.getConnection()) { ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V9__ai_quotas.sql")); }
        account=mock(CurrentAccount.class); when(account.id()).thenReturn(UUID.randomUUID());
    }
    AiQuota quota(int perAccount,int global,long characters) { return new AiQuota(jdbc,manager,account,perAccount,global,characters,characters*10); }
    @Test void failuresCountAndDailyResetDoesNotRequireRestart() {
        var q=quota(2,10,1000);
        assertThrows(IllegalStateException.class,()->q.call(20,()->{throw new IllegalStateException();}));
        assertEquals("ok",q.call(20,()->"ok"));
        assertEquals(AiProviderException.Reason.QUOTA_LIMIT,assertThrows(AiProviderException.class,()->q.call(20,()->"blocked")).reason());
        jdbc.update("UPDATE ai_quotas SET usage_day = DATE '2000-01-01'");
        assertEquals("reset",q.call(20,()->"reset"));
    }
    @Test void globalBudgetCannotBeBypassedUsingAnotherAccount() {
        var q=quota(10,1,1000);q.call(20,()->"ok");when(account.id()).thenReturn(UUID.randomUUID());
        assertEquals(AiProviderException.Reason.QUOTA_LIMIT,assertThrows(AiProviderException.class,()->q.call(20,()->"blocked")).reason());
        assertEquals(1,jdbc.queryForObject("SELECT requests FROM ai_quotas WHERE scope = 'global'",Integer.class));
    }
    @Test void characterBudgetAndOversizeAreRejectedBeforeProvider() {
        var q=quota(10,10,30);q.call(25,()->"ok");
        assertThrows(AiProviderException.class,()->q.call(6,()->fail("Provider must not run")));
        assertThrows(InvalidInputException.class,()->q.call(20001,()->fail("Provider must not run")));
    }
    @Test void separateInstancesShareConcurrencyAndReleaseOnFailure() throws Exception {
        var first=quota(10,10,1000);var second=quota(10,10,1000);
        var started=new CountDownLatch(1);var finish=new CountDownLatch(1);
        var running=CompletableFuture.runAsync(()->first.call(10,()->{started.countDown();try{finish.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}return "ok";}));
        try { assertTrue(started.await(5,TimeUnit.SECONDS));
            assertEquals(AiProviderException.Reason.BUSY,assertThrows(AiProviderException.class,()->second.call(10,()->"blocked")).reason());
        } finally { finish.countDown();running.get(5,TimeUnit.SECONDS); }
        assertEquals("released",second.call(10,()->"released"));
    }
    @Test void advisorProtectsActualChatCallsAndDoesNotLeakProviderInput() {
        var model=new StubChatModel();var client=ChatClient.builder(model).defaultAdvisors(new QuotaAdvisor(quota(1,10,50000))).build();
        client.prompt("First question").call().content();
        assertThrows(AiProviderException.class,()->client.prompt("Next question").call().content());assertEquals(1,model.calls);
        assertThrows(InvalidInputException.class,()->client.prompt("x".repeat(20001)).call().content());assertEquals(1,model.calls);
    }
    @Test void embeddingBatchesAreBoundedAndConsumeTheSameBudget() {
        var attempts=new ArrayList<Integer>();
        PassageEmbeddingClient delegate=new PassageEmbeddingClient(){public String modelKey(){return "fixture";}public List<double[]> embed(List<String> t){attempts.add(t.size());return t.stream().map(v->new double[]{1,0}).toList();}};
        var client=new QuotaEmbeddingClient(delegate,quota(2,10,50000));
        assertEquals(17,client.embed(Collections.nCopies(17,"x".repeat(1200))).size());assertEquals(List.of(16,1),attempts);
        assertThrows(AiProviderException.class,()->client.embed(List.of("more")));assertEquals(2,attempts.size());
    }
    @Test void summaryKeepsTechnicalSourceLinesWithinBudget() {
        String source=("General description of work.\n").repeat(600)+"Technical skills: Python, PostgreSQL, Docker.\nBuilt a Spring Boot project.";
        String summary=InputSummary.resume(source);
        assertTrue(summary.length()<=12000);assertTrue(summary.contains("Python, PostgreSQL, Docker"));
        for(String line:summary.lines().toList())assertTrue(source.contains(line));
        assertEquals("I do not know.",InputSummary.resume("I do not know."));
    }
}

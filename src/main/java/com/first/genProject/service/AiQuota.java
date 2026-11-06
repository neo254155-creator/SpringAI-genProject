package com.first.genProject.service;

import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import com.first.genProject.auth.CurrentAccount;
import com.first.genProject.exception.AiProviderException;
import static com.first.genProject.exception.AiProviderException.Reason.*;

/** Reservations count attempts, including provider failures. No transaction spans a provider call. */
@Service
public class AiQuota {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final CurrentAccount current;
    private final int accountRequests, globalRequests;
    private final long accountCharacters, globalCharacters;
    public AiQuota(JdbcTemplate jdbc, PlatformTransactionManager manager, CurrentAccount current,
            @Value("${ai.quota.account-daily-requests:30}") int accountRequests,
            @Value("${ai.quota.global-daily-requests:200}") int globalRequests,
            @Value("${ai.quota.account-daily-characters:120000}") long accountCharacters,
            @Value("${ai.quota.global-daily-characters:800000}") long globalCharacters) {
        if (accountRequests < 1 || globalRequests < 1 || accountCharacters < 1 || globalCharacters < 1)
            throw new IllegalArgumentException("AI quota limits must be positive");
        this.jdbc=jdbc; this.current=current; this.accountRequests=accountRequests; this.globalRequests=globalRequests;
        this.accountCharacters=accountCharacters; this.globalCharacters=globalCharacters;
        tx=new TransactionTemplate(manager); tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public <T> T call(int characters, Supplier<T> provider) {
        if (characters < 1 || characters > 20000) throw new com.first.genProject.exception.InvalidInputException(
                "AI input must contain between 1 and 20000 characters. Shorten the input.");
        String owner=current.id().toString(), token=UUID.randomUUID().toString();
        try { tx.executeWithoutResult(status -> reserve(owner, token, characters)); }
        catch (org.springframework.dao.DataAccessException unavailable) { throw new AiProviderException(STORAGE_UNAVAILABLE, null); }
        try { return provider.get(); }
        finally {
            try { tx.executeWithoutResult(status -> jdbc.update(
                    "UPDATE ai_quotas SET lease_token = NULL, lease_until = NULL WHERE scope = ? AND lease_token = ?", owner, token)); }
            catch (org.springframework.dao.DataAccessException unavailable) {
                org.slf4j.LoggerFactory.getLogger(AiQuota.class).warn("AI quota lease release failed; concurrent requests remain blocked until lease expiry");
            }
        }
    }
    private void reserve(String owner, String token, int characters) {
        // Global lock also serializes first-time account creation across instances.
        jdbc.queryForObject("SELECT scope FROM ai_quotas WHERE scope = 'global' FOR UPDATE", String.class);
        var now=jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", OffsetDateTime.class);
        var day=now.withOffsetSameInstant(ZoneOffset.UTC).toLocalDate();
        if (jdbc.queryForObject("SELECT COUNT(*) FROM ai_quotas WHERE scope = ?", Integer.class, owner)==0)
            jdbc.update("INSERT INTO ai_quotas(scope, usage_day) VALUES (?, ?)", owner, day);
        for (String scope: List.of("global", owner)) {
            var row=jdbc.queryForMap("SELECT usage_day, requests, input_characters, lease_until FROM ai_quotas WHERE scope = ?", scope);
            if (scope.equals(owner) && row.get("lease_until") != null) {
                var until=jdbc.queryForObject("SELECT lease_until FROM ai_quotas WHERE scope = ?", OffsetDateTime.class, owner);
                if (until.isAfter(now)) throw new AiProviderException(BUSY, null);
            }
            boolean today=jdbc.queryForObject("SELECT usage_day FROM ai_quotas WHERE scope = ?", java.sql.Date.class, scope).toLocalDate().equals(day);
            int used=today ? ((Number)row.get("requests")).intValue():0;
            long consumed=today ? ((Number)row.get("input_characters")).longValue():0;
            int maxRequests=scope.equals("global")?globalRequests:accountRequests;
            long maxCharacters=scope.equals("global")?globalCharacters:accountCharacters;
            if (used>=maxRequests || characters>maxCharacters-consumed) throw new AiProviderException(QUOTA_LIMIT, null);
            jdbc.update("UPDATE ai_quotas SET usage_day = ?, requests = ?, input_characters = ? WHERE scope = ?",
                    day, used+1, consumed+characters, scope);
        }
        // Longer than the provider timeout; process crashes conservatively retain the lease.
        jdbc.update("UPDATE ai_quotas SET lease_token = ?, lease_until = ? WHERE scope = ?", token, now.plusMinutes(15), owner);
    }
}

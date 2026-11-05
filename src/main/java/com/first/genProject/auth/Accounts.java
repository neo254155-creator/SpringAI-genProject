package com.first.genProject.auth;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class Accounts implements UserDetailsService {
    public record Credentials(String username, String password) { }
    public record View(UUID id, String username) { }
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final TransactionTemplate transactions;

    public Accounts(JdbcTemplate jdbc, PasswordEncoder passwords, PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.passwords = passwords; transactions = new TransactionTemplate(manager);
    }

    public View register(Credentials input, String remoteAddress) {
        String username = username(input == null ? null : input.username());
        String password = input == null ? null : input.password();
        if (password == null || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a password with at least 12 characters and at most 72 UTF-8 bytes.");
        String encoded = passwords.encode(password);
        return transactions.execute(status -> {
            UUID firstOwner = jdbc.queryForObject("SELECT owner_id FROM account_bootstrap WHERE id = 1 FOR UPDATE", UUID.class);
            if (firstOwner == null && !Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1").contains(remoteAddress))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Create the first account from the server's localhost address.");
            if (jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE username = ?", Long.class, username) != 0)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is unavailable. If you already created it, sign in.");
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO app_users (id, username, password_hash) VALUES (?, ?, ?)", id, username, encoded);
            if (firstOwner == null) {
                for (String table : List.of("resume_profiles", "interview_sessions", "study_documents"))
                    jdbc.update("UPDATE " + table + " SET owner_id = ? WHERE owner_id IS NULL", id);
                jdbc.update("UPDATE account_bootstrap SET owner_id = ? WHERE id = 1", id);
            }
            return new View(id, username);
        });
    }

    public static String username(String value) {
        if (value == null || !value.strip().matches("[A-Za-z0-9][A-Za-z0-9._-]{2,39}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a 3–40 character username with letters, numbers, dots, underscores or hyphens.");
        return value.strip().toLowerCase(Locale.ROOT);
    }

    @Override public UserDetails loadUserByUsername(String value) {
        String normalized;
        try { normalized = username(value); }
        catch (ResponseStatusException invalid) { throw new UsernameNotFoundException("Invalid credentials"); }
        return jdbc.query("SELECT id, password_hash FROM app_users WHERE username = ?", (row, index) ->
                User.withUsername(row.getObject(1, UUID.class).toString()).password(row.getString(2)).roles("USER").build(), normalized)
                .stream().findFirst().orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    public View view(UUID id) {
        return jdbc.query("SELECT username FROM app_users WHERE id = ?", (row, index) -> new View(id, row.getString(1)), id)
                .stream().findFirst().orElseThrow(() -> new UsernameNotFoundException("Account not found"));
    }
}

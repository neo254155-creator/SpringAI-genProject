package com.first.genProject.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Short autocommit statements only: never hold a transaction open around AI. */
@Service
public class RequestLedger {
    public record Entry(String requestHash, String state, Integer status, String body,
                        String contentType, String location) { }
    public record Claim(boolean owner, Entry entry) { }
    private final JdbcTemplate jdbc;

    public RequestLedger(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Claim claim(String keyHash, String requestHash) {
        Entry saved = find(keyHash);
        if (saved != null) return new Claim(false, saved);
        try {
            jdbc.update("INSERT INTO request_replays (key_hash, request_hash, state) VALUES (?, ?, 'RUNNING')",
                    keyHash, requestHash);
            return new Claim(true, null);
        } catch (DuplicateKeyException concurrentClaim) {
            return new Claim(false, find(keyHash));
        }
    }

    public Entry find(String keyHash) {
        return jdbc.query("SELECT request_hash, state, response_status, response_body, content_type, location "
                + "FROM request_replays WHERE key_hash = ?", (row, index) -> new Entry(
                row.getString(1), row.getString(2), row.getObject(3, Integer.class), row.getString(4),
                row.getString(5), row.getString(6)), keyHash).stream().findFirst().orElse(null);
    }

    public void finish(String keyHash, String state, int status, String body, String contentType, String location) {
        int updated = jdbc.update("UPDATE request_replays SET state = ?, response_status = ?, response_body = ?, "
                + "content_type = ?, location = ? WHERE key_hash = ? AND state = 'RUNNING'",
                state, status, body, contentType, location, keyHash);
        if (updated != 1) throw new IllegalStateException("Request replay state could not be confirmed");
    }
}

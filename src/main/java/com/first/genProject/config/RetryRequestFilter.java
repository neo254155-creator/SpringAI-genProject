package com.first.genProject.config;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.regex.Pattern;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.databind.json.JsonMapper;
import com.first.genProject.service.RequestLedger;

/** Optional Idempotency-Key contract for mutations in the interview/notes journeys. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class RetryRequestFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RetryRequestFilter.class);
    private static final Pattern ROUTES = Pattern.compile("/(?:resumes(?:/text|/[^/]+/sessions)?|"
            + "interview/sessions(?:/[^/]+/(?:questions|complete))?|interview/questions/[^/]+/answers|"
            + "documents(?:/[^/]+/(?:index|search|answer))?)");
    private static final Pattern KEY = Pattern.compile("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");
    private static final Pattern RESUME = Pattern.compile("/interview/sessions/[^/]+/resume");
    private static final int MAX_BODY = 2 * 1024 * 1024, MAX_RESPONSE = 128 * 1024;
    private static final Set<String> SAFE_PROVIDER_FAILURES = Set.of(
            "AI_PROVIDER_ERROR", "EMBEDDING_PROVIDER_ERROR", "EMBEDDINGS_UNAVAILABLE", "DOCUMENT_INDEX_NOT_READY");
    private final RequestLedger ledger;
    private final JsonMapper json = JsonMapper.builder().build();

    public RetryRequestFilter(RequestLedger ledger) { this.ledger = ledger; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String route = request.getRequestURI().substring(request.getContextPath().length());
        return request.getHeader("Idempotency-Key") == null || !("POST".equals(request.getMethod()) && ROUTES.matcher(route).matches()
                || "GET".equals(request.getMethod()) && RESUME.matcher(route).matches());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader("Idempotency-Key");
        if (!KEY.matcher(key).matches()) {
            problem(response, 400, "INVALID_REQUEST_KEY", "Use a UUID for Idempotency-Key.", "failed"); return;
        }
        String principal = request.getUserPrincipal() == null ? "shared" : request.getUserPrincipal().getName();
        String keyHash = digest((principal + "\0" + key.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
        if ("GET".equals(request.getMethod())) {
            // Reconcile a lost question response when the user opens saved progress.
            // This GET only reads; it cannot start or repeat an AI action.
            MessageDigest expected = sha256();
            String path = request.getRequestURI().replaceFirst("/resume$", "/questions");
            frame(expected, ("POST " + path + "?").getBytes(StandardCharsets.UTF_8));
            frame(expected, new byte[0]); frame(expected, new byte[0]);
            try {
                var entry = ledger.find(keyHash);
                if (entry != null && entry.requestHash().equals(HexFormat.of().formatHex(expected.digest())))
                    response.setHeader("Idempotency-Status", entry.state().equals("COMPLETE") ? "completed"
                            : entry.state().equals("FAILED") ? "failed" : "pending");
            } catch (DataAccessException unavailable) { response.setHeader("Idempotency-Status", "pending"); }
            chain.doFilter(request, response); return;
        }
        Prepared prepared;
        try { prepared = prepare(request); }
        catch (IllegalStateException tooLarge) {
            problem(response, 413, "REQUEST_TOO_LARGE", "The uploaded request is too large.", "failed"); return;
        } catch (ServletException | IOException unreadable) {
            problem(response, 400, "INVALID_INPUT", "The uploaded request could not be read.", "failed"); return;
        }
        RequestLedger.Claim claim;
        try { claim = ledger.claim(keyHash, prepared.hash()); }
        catch (DataAccessException unavailable) {
            problem(response, 503, "RETRY_STORAGE_UNAVAILABLE", "Retry protection could not reach storage. Please try later.", "pending"); return;
        }
        if (!claim.owner()) { replay(response, claim.entry(), prepared.hash()); return; }
        var captured = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(prepared.request(), captured);
            byte[] bytes = captured.getContentAsByteArray();
            String body = new String(bytes, StandardCharsets.UTF_8);
            int status = captured.getStatus();
            String state = status >= 200 && status < 300 ? "COMPLETE"
                    : definitiveFailure(status, body) ? "FAILED" : "UNKNOWN";
            if (bytes.length > MAX_RESPONSE) state = "UNKNOWN";
            try {
                ledger.finish(keyHash, state, status, state.equals("UNKNOWN") ? null : body,
                        captured.getContentType(), captured.getHeader("Location"));
            } catch (DataAccessException | IllegalStateException unconfirmed) {
                // Leave the durable RUNNING claim in place. Never guess that repeating AI is safe.
                state = "UNKNOWN";
                log.warn("Request replay outcome could not be saved; automatic re-execution remains blocked");
            }
            captured.setHeader("Idempotency-Status", state.equals("COMPLETE") ? "completed"
                    : state.equals("FAILED") ? "failed" : "pending");
        } finally {
            // If the chain throws, RUNNING survives and future attempts stay blocked.
            captured.copyBodyToResponse();
        }
    }

    private boolean definitiveFailure(int status, String body) {
        if (status >= 400 && status < 500) return true;
        try { return SAFE_PROVIDER_FAILURES.contains(json.readTree(body).path("code").asString()); }
        catch (RuntimeException unreadable) { return false; }
    }

    private void replay(HttpServletResponse response, RequestLedger.Entry entry, String hash) throws IOException {
        if (entry == null || !entry.requestHash().equals(hash)) {
            problem(response, 409, "REQUEST_KEY_REUSED", "This request key was used for different input. Reopen saved progress before continuing.", "pending");
        } else if (!Set.of("COMPLETE", "FAILED").contains(entry.state())) {
            problem(response, 409, "REQUEST_PENDING", "This request is running or its result is unconfirmed. Wait, then reopen saved progress before trying again.", "pending");
        } else {
            response.setStatus(entry.status());
            response.setContentType(entry.contentType() == null ? "application/json" : entry.contentType());
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Idempotency-Status", entry.state().equals("COMPLETE") ? "completed" : "failed");
            response.setHeader("Idempotency-Replayed", "true");
            if (entry.location() != null) response.setHeader("Location", entry.location());
            response.getOutputStream().write(entry.body().getBytes(StandardCharsets.UTF_8));
        }
    }

    private void problem(HttpServletResponse response, int status, String code, String detail, String state) throws IOException {
        response.setStatus(status); response.setContentType("application/problem+json");
        response.setHeader("Idempotency-Status", state);
        response.getOutputStream().write(json.writeValueAsBytes(Map.of("status", status, "code", code, "detail", detail)));
    }

    private record Prepared(HttpServletRequest request, String hash) { }
    private Prepared prepare(HttpServletRequest request) throws IOException, ServletException {
        MessageDigest hash = sha256();
        frame(hash, (request.getMethod() + " " + request.getRequestURI() + "?"
                + Objects.toString(request.getQueryString(), "")).getBytes(StandardCharsets.UTF_8));
        String contentType = Objects.toString(request.getContentType(), "");
        if (contentType.toLowerCase(Locale.ROOT).startsWith("multipart/form-data")) {
            var parts = new ArrayList<Piece>();
            if (request instanceof MultipartHttpServletRequest multipart) {
                for (var field : multipart.getParameterMap().entrySet()) for (String value : field.getValue())
                    parts.add(new Piece(field.getKey(), "", "", value.getBytes(StandardCharsets.UTF_8)));
                for (var field : multipart.getMultiFileMap().entrySet()) for (var file : field.getValue())
                    parts.add(new Piece(field.getKey(), Objects.toString(file.getOriginalFilename(), ""),
                            Objects.toString(file.getContentType(), ""), bounded(file.getInputStream())));
            } else {
                for (Part part : request.getParts()) parts.add(new Piece(part.getName(),
                        Objects.toString(part.getSubmittedFileName(), ""), Objects.toString(part.getContentType(), ""),
                        bounded(part.getInputStream())));
            }
            parts.sort(Comparator.comparing(Piece::name).thenComparing(Piece::filename)
                    .thenComparing(piece -> digest(piece.bytes())));
            long total = 0;
            for (var part : parts) {
                total += part.bytes().length;
                if (total > MAX_BODY) throw new IllegalStateException("Too large");
                frame(hash, (part.name() + "\0" + part.filename() + "\0" + part.type()).getBytes(StandardCharsets.UTF_8));
                frame(hash, part.bytes());
            }
            return new Prepared(request, HexFormat.of().formatHex(hash.digest()));
        }
        byte[] bytes = bounded(request.getInputStream());
        frame(hash, contentType.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)); frame(hash, bytes);
        return new Prepared(new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                var input = new ByteArrayInputStream(bytes);
                return new ServletInputStream() {
                    public int read() { return input.read(); }
                    public boolean isFinished() { return input.available() == 0; }
                    public boolean isReady() { return true; }
                    public void setReadListener(ReadListener listener) {
                        try { if (isFinished()) listener.onAllDataRead(); else listener.onDataAvailable(); }
                        catch (IOException failure) { listener.onError(failure); }
                    }
                };
            }
            @Override public BufferedReader getReader() {
                return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
            }
        }, HexFormat.of().formatHex(hash.digest()));
    }

    private record Piece(String name, String filename, String type, byte[] bytes) { }
    private static byte[] bounded(InputStream input) throws IOException {
        try (input) {
            byte[] bytes = input.readNBytes(MAX_BODY + 1);
            if (bytes.length > MAX_BODY) throw new IllegalStateException("Too large");
            return bytes;
        }
    }
    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void frame(MessageDigest hash, byte[] bytes) {
        hash.update(ByteBuffer.allocate(4).putInt(bytes.length).array()); hash.update(bytes);
    }
    private static String digest(byte[] bytes) { return HexFormat.of().formatHex(sha256().digest(bytes)); }
}

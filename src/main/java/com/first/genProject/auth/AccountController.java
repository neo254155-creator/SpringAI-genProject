package com.first.genProject.auth;

import jakarta.servlet.http.*;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class AccountController {
    private final Accounts accounts;
    private final CurrentAccount current;
    private final AuthenticationManager authentication;
    private final SecurityContextRepository contexts;
    private final SessionAuthenticationStrategy sessions;
    public AccountController(Accounts accounts, CurrentAccount current, AuthenticationManager authentication,
            SecurityContextRepository contexts, SessionAuthenticationStrategy sessions) {
        this.accounts = accounts; this.current = current; this.authentication = authentication;
        this.contexts = contexts; this.sessions = sessions;
    }
    @GetMapping("/csrf") public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }
    @GetMapping("/me") public Accounts.View me() { return accounts.view(current.id()); }
    @PostMapping("/register") public ResponseEntity<Accounts.View> register(@RequestBody Accounts.Credentials input, HttpServletRequest request) {
        String address = request.getHeader("Forwarded") != null || request.getHeader("X-Forwarded-For") != null
                || request.getHeader("X-Forwarded-Host") != null ? "forwarded" : request.getRemoteAddr();
        return ResponseEntity.status(201).body(accounts.register(input, address));
    }
    @PostMapping("/login") public Accounts.View login(@RequestBody Accounts.Credentials input,
            HttpServletRequest request, HttpServletResponse response) {
        if (input == null || input.username() == null || input.password() == null
                || input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
        try {
            var authenticated = authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(input.username(), input.password()));
            sessions.onAuthentication(authenticated, request, response);
            var context = SecurityContextHolder.createEmptyContext(); context.setAuthentication(authenticated);
            SecurityContextHolder.setContext(context); contexts.saveContext(context, request, response);
            return accounts.view(current.id());
        } catch (AuthenticationException invalid) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
        }
    }
}

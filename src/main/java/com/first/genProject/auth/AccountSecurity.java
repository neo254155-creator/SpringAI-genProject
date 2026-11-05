package com.first.genProject.auth;

import java.util.*;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.*;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.savedrequest.NullRequestCache;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class AccountSecurity {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean AuthenticationManager authenticationManager(Accounts accounts, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(accounts); provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
    @Bean SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
    }
    @Bean CsrfTokenRepository csrfTokenRepository() { return new HttpSessionCsrfTokenRepository(); }
    @Bean SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository repository) {
        return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(repository)));
    }
    @Bean SecurityFilterChain security(HttpSecurity http, SecurityContextRepository contexts,
            CsrfTokenRepository csrf, SessionAuthenticationStrategy sessions) throws Exception {
        return http.authorizeHttpRequests(rules -> rules.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/", "/index.html", "/login.html", "/privacy.html", "/frontend-config", "/auth/csrf", "/auth/register", "/auth/login", "/error",
                                "/ui/**", "/*.js", "/favicon.ico").permitAll().anyRequest().authenticated())
                .csrf(config -> config.csrfTokenRepository(csrf).requireCsrfProtectionMatcher(request ->
                        CsrfFilter.DEFAULT_CSRF_MATCHER.matches(request) || Set.of("GET", "HEAD").contains(request.getMethod())
                                && request.getRequestURI().substring(request.getContextPath().length()).equals("/chat")))
                .securityContext(config -> config.securityContextRepository(contexts))
                .sessionManagement(config -> config.sessionAuthenticationStrategy(sessions))
                .requestCache(config -> config.requestCache(new NullRequestCache()))
                .exceptionHandling(config -> config.authenticationEntryPoint((request, response, error) -> {
                    if (request.getMethod().equals("GET") && request.getRequestURI().endsWith(".html"))
                        response.sendRedirect(request.getContextPath() + "/login.html?next="
                                + java.net.URLEncoder.encode(request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString()), java.nio.charset.StandardCharsets.UTF_8));
                    else problem(response, 401, "SIGN_IN_REQUIRED", "Please sign in to continue.");
                }).accessDeniedHandler((request, response, error) -> problem(response, 403,
                        error instanceof CsrfException ? "CSRF_INVALID" : "ACCESS_DENIED",
                        error instanceof CsrfException ? "Your session security token expired. Please try again." : "This action is not allowed.")))
                .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .addFilterBefore(new LoginThrottle(), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .logout(config -> config.logoutUrl("/auth/logout").deleteCookies("INTERVIE_SESSION")
                        .logoutSuccessHandler((request, response, auth) -> response.setStatus(204)))
                .build();
    }
    public static void problem(HttpServletResponse response, int status, String code, String detail) throws java.io.IOException {
        response.setStatus(status); response.setContentType("application/problem+json");
        response.getOutputStream().write(JsonMapper.builder().build().writeValueAsBytes(Map.of("status", status, "code", code, "detail", detail)));
    }

    /** A bounded, per-process safeguard; use a shared edge limiter when scaling. */
    static class LoginThrottle extends org.springframework.web.filter.OncePerRequestFilter {
        private record Window(long until, int attempts) { }
        private final Map<String, Window> windows = new HashMap<>();
        @Override protected boolean shouldNotFilter(HttpServletRequest request) {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            return !request.getMethod().equals("POST") || !Set.of("/auth/login", "/auth/register").contains(path);
        }
        private synchronized boolean allowed(String address) {
            long now = System.currentTimeMillis();
            windows.entrySet().removeIf(entry -> entry.getValue().until() <= now);
            Window old = windows.get(address);
            if (old == null && windows.size() >= 10000 || old != null && old.attempts() >= 30) return false;
            windows.put(address, new Window(old == null ? now + 300000 : old.until(), old == null ? 1 : old.attempts() + 1));
            return true;
        }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, jakarta.servlet.FilterChain chain)
                throws java.io.IOException, jakarta.servlet.ServletException {
            if (allowed(request.getRemoteAddr())) chain.doFilter(request, response);
            else { response.setHeader("Retry-After", "300"); problem(response, 429, "AUTH_RATE_LIMIT", "Too many sign-in attempts. Please wait a few minutes."); }
        }
    }
}

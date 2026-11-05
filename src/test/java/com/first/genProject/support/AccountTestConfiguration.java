package com.first.genProject.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.UUID;

@TestConfiguration(proxyBeanMethods = false)
public class AccountTestConfiguration {
    public static final String ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    public static final String PASSWORD = "Offline test password 123!";
    @Bean ApplicationRunner fixtureAccount(JdbcTemplate jdbc, PasswordEncoder passwords) {
        return args -> {
            UUID id = UUID.fromString(ID);
            if (jdbc.queryForObject("select count(*) from app_users where id = ?", Long.class, id) == 0)
                jdbc.update("insert into app_users(id,username,password_hash) values (?,?,?)", id, "offline-fixture", passwords.encode(PASSWORD));
            jdbc.update("update account_bootstrap set owner_id = ? where owner_id is null", id);
        };
    }
    @Bean MockMvcBuilderCustomizer signedInFixtureRequests() {
        return builder -> builder.defaultRequest(get("/").with(user(ID)).with(csrf()))
                .alwaysDo(result -> org.springframework.security.core.context.SecurityContextHolder.setContext(
                        org.springframework.security.test.context.TestSecurityContextHolder.getContext()));
    }
}

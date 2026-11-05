package com.first.genProject.auth;

import java.util.UUID;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentAccount {
    public UUID id() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            if (authentication != null && authentication.isAuthenticated()) return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException ignored) { }
        throw new AuthenticationCredentialsNotFoundException("Please sign in.");
    }
}

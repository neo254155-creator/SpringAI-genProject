package com.first.genProject.live;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import com.first.genProject.service.AiService;

/** Explicit opt-in: ./mvnw -Plive-groq test. Uses real provider credits. */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = "\\S+")
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
@SpringBootTest(properties = {
        "spring.ai.openai.base-url=https://api.groq.com/openai/v1",
        "spring.ai.openai.api-key=${GROQ_API_KEY}",
        "spring.ai.openai.chat.model=${GROQ_MODEL:openai/gpt-oss-120b}",
        "spring.ai.openai.max-retries=0"
})
class GroqLiveTest {
    @Autowired
    private AiService service;

    @Test
    void returnsNonEmptyResponseFromGroq() {
        String response = service.chat("In one short sentence, what is a Java interface?");
        assertNotNull(response);
        assertFalse(response.isBlank());
    }
}

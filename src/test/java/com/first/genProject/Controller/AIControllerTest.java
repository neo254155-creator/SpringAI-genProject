package com.first.genProject.Controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AIControllerTest {
    @Test
    void chatReturnsTemplateResponse() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new AIController(() -> "Spring explanation")).build();
        mvc.perform(get("/chat")).andExpect(status().isOk())
                .andExpect(content().string("Spring explanation"));
    }
}

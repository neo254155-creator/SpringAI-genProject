package com.first.genProject.Controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FrontendConfigControllerTest {
    @Test
    void exposesOnlyPublicOriginAndSupportsSameOrigin() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FrontendConfigController("")).build();
        mvc.perform(get("/frontend-config")).andExpect(status().isOk())
                .andExpect(content().json("{\"apiBaseUrl\":\"\"}"));
        var configured = MockMvcBuilders.standaloneSetup(new FrontendConfigController(" https://api.example.com ")).build();
        configured.perform(get("/frontend-config")).andExpect(status().isOk())
                .andExpect(content().json("{\"apiBaseUrl\":\"https://api.example.com\"}"));
    }
}

package com.first.genProject.Controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes only the public API origin; never provider or database configuration. */
@RestController
public class FrontendConfigController {
    private final String apiBaseUrl;

    public FrontendConfigController(@Value("${FRONTEND_API_BASE_URL:}") String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl.strip();
    }

    @GetMapping("/frontend-config")
    public Map<String, String> config() {
        return Map.of("apiBaseUrl", apiBaseUrl);
    }
}

package com.sigeu.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "sigeu.rate-limit.enabled=true",
        "sigeu.rate-limit.auth-per-minute=3"
})
class RateLimitFilterTests {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void authEndpointReturnsTooManyRequestsWhenRateLimitIsExceeded() throws Exception {
        String body = """
                {
                  "username": "missing",
                  "password": "Abc12345"
                }
                """;

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .header("X-Forwarded-For", "203.0.113.42")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isNotFound());
        }

        mockMvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", "203.0.113.42")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isTooManyRequests());
    }
}

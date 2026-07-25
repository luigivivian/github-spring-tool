package dev.luigivivian.githubtool.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.luigivivian.githubtool.config.SecurityConfig;
import dev.luigivivian.githubtool.dto.ActivityResponse;
import dev.luigivivian.githubtool.dto.LanguageShare;
import dev.luigivivian.githubtool.dto.LanguagesResponse;
import dev.luigivivian.githubtool.service.InsightsService;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InsightsController.class)
@Import(SecurityConfig.class)
class InsightsControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InsightsService insightsService;

    @Test
    void languagesEndpointReturnsShares() throws Exception {
        when(insightsService.languages("octocat")).thenReturn(new LanguagesResponse(
                List.of(new LanguageShare("Java", 90.0, 900L),
                        new LanguageShare("Other", 10.0, 100L)),
                NOW, false));

        mockMvc.perform(get("/api/users/octocat/languages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.languages[0].language").value("Java"))
                .andExpect(jsonPath("$.languages[0].percent").value(90.0))
                .andExpect(jsonPath("$.fromCache").value(false));
    }

    @Test
    void activityEndpointReturnsWeeksAndPendingFlag() throws Exception {
        when(insightsService.activity("octocat")).thenReturn(new ActivityResponse(
                Collections.nCopies(52, 3), true, NOW, false));

        mockMvc.perform(get("/api/users/octocat/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeks.length()").value(52))
                .andExpect(jsonPath("$.pending").value(true));
    }

    @Test
    void invalidUsernameRejectedWith400() throws Exception {
        when(insightsService.languages(anyString())).thenThrow(new AssertionError("not reached"));

        mockMvc.perform(get("/api/users/bad--name/languages"))
                .andExpect(status().isBadRequest());
    }
}

package dev.luigivivian.githubtool.controller;

import dev.luigivivian.githubtool.dto.ActivityResponse;
import dev.luigivivian.githubtool.dto.LanguagesResponse;
import dev.luigivivian.githubtool.service.InsightsService;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Validated
public class InsightsController {

    private final InsightsService insightsService;

    public InsightsController(InsightsService insightsService) {
        this.insightsService = insightsService;
    }

    @GetMapping("/users/{username}/languages")
    public LanguagesResponse getLanguages(
            @PathVariable @Pattern(regexp = SearchController.USERNAME_REGEX) String username) {
        return insightsService.languages(username);
    }

    @GetMapping("/users/{username}/activity")
    public ActivityResponse getActivity(
            @PathVariable @Pattern(regexp = SearchController.USERNAME_REGEX) String username) {
        return insightsService.activity(username);
    }
}

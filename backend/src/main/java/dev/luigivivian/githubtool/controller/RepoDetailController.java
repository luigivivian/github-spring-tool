package dev.luigivivian.githubtool.controller;

import dev.luigivivian.githubtool.dto.RepoDetailResponse;
import dev.luigivivian.githubtool.service.RepoDetailService;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Validated
public class RepoDetailController {

    static final String REPO_REGEX = "^[A-Za-z0-9._-]{1,100}$";

    private final RepoDetailService repoDetailService;

    public RepoDetailController(RepoDetailService repoDetailService) {
        this.repoDetailService = repoDetailService;
    }

    @GetMapping("/repos/{owner}/{repo}")
    public RepoDetailResponse getRepoDetail(
            @PathVariable @Pattern(regexp = SearchController.USERNAME_REGEX) String owner,
            @PathVariable @Pattern(regexp = REPO_REGEX) String repo) {
        return repoDetailService.detail(owner, repo);
    }
}

package dev.luigivivian.githubtool.controller;

import dev.luigivivian.githubtool.dto.SnapshotResponse;
import dev.luigivivian.githubtool.service.SnapshotService;

import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Validated
public class SearchController {

    static final String USERNAME_REGEX = "^[a-zA-Z0-9](?:[a-zA-Z0-9]|-(?=[a-zA-Z0-9])){0,38}$";

    private final SnapshotService snapshotService;

    public SearchController(SnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @GetMapping("/users/{username}")
    public SnapshotResponse getUserSnapshot(
            @PathVariable @Pattern(regexp = USERNAME_REGEX) String username,
            @RequestParam(defaultValue = "false") boolean refresh) {
        return snapshotService.getSnapshot(username, refresh);
    }
}

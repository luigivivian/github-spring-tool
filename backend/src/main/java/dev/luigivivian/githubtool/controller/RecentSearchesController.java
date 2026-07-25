package dev.luigivivian.githubtool.controller;

import dev.luigivivian.githubtool.dto.RecentSearch;
import dev.luigivivian.githubtool.service.SnapshotService;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RecentSearchesController {

    private final SnapshotService snapshotService;

    public RecentSearchesController(SnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @GetMapping("/searches/recent")
    public List<RecentSearch> getRecentSearches() {
        return snapshotService.recentSearches();
    }
}

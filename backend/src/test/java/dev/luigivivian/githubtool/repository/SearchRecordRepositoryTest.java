package dev.luigivivian.githubtool.repository;

import dev.luigivivian.githubtool.entity.SearchRecord;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
class SearchRecordRepositoryTest {

    @Autowired
    private SearchRecordRepository repository;

    @Test
    void findRecentDeduplicatesByUsernameAndOrdersByLatest() {
        Instant base = Instant.parse("2026-07-24T12:00:00Z");
        repository.save(new SearchRecord("alice", base.minusSeconds(300)));
        repository.save(new SearchRecord("bob", base.minusSeconds(200)));
        repository.save(new SearchRecord("alice", base.minusSeconds(100)));

        List<SearchRecordRepository.RecentSearchView> recent =
                repository.findRecent(PageRequest.of(0, 10));

        assertThat(recent).hasSize(2);
        assertThat(recent.get(0).getUsername()).isEqualTo("alice");
        assertThat(recent.get(0).getSearchedAt()).isEqualTo(base.minusSeconds(100));
        assertThat(recent.get(1).getUsername()).isEqualTo("bob");
    }

    @Test
    void findRecentCapsAtPageSize() {
        Instant base = Instant.parse("2026-07-24T12:00:00Z");
        for (int i = 0; i < 15; i++) {
            repository.save(new SearchRecord("user-" + i, base.plusSeconds(i)));
        }

        List<SearchRecordRepository.RecentSearchView> recent =
                repository.findRecent(PageRequest.of(0, 10));

        assertThat(recent).hasSize(10);
        assertThat(recent.get(0).getUsername()).isEqualTo("user-14");
    }
}

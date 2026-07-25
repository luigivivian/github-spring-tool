package dev.luigivivian.githubtool.repository;

import dev.luigivivian.githubtool.entity.SearchRecord;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SearchRecordRepository extends JpaRepository<SearchRecord, Long> {

    interface RecentSearchView {
        String getUsername();

        Instant getSearchedAt();
    }

    @Query("""
            select s.username as username, max(s.searchedAt) as searchedAt
            from SearchRecord s
            group by s.username
            order by max(s.searchedAt) desc
            """)
    List<RecentSearchView> findRecent(Pageable pageable);
}

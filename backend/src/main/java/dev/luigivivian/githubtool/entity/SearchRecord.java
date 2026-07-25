package dev.luigivivian.githubtool.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "search_record",
        indexes = @jakarta.persistence.Index(name = "idx_search_username_time",
                columnList = "username, searchedAt"))
public class SearchRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 39)
    private String username;

    @Column(nullable = false)
    private Instant searchedAt;

    protected SearchRecord() {
    }

    public SearchRecord(String username, Instant searchedAt) {
        this.username = username;
        this.searchedAt = searchedAt;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public Instant getSearchedAt() {
        return searchedAt;
    }
}

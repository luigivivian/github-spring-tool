package dev.luigivivian.githubtool.repository;

import dev.luigivivian.githubtool.entity.Snapshot;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SnapshotRepository extends JpaRepository<Snapshot, Long> {

    Optional<Snapshot> findByUsername(String username);
}

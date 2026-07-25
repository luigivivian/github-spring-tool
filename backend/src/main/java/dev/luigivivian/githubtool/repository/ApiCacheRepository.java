package dev.luigivivian.githubtool.repository;

import dev.luigivivian.githubtool.entity.ApiCache;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiCacheRepository extends JpaRepository<ApiCache, Long> {

    Optional<ApiCache> findByCacheKey(String cacheKey);
}

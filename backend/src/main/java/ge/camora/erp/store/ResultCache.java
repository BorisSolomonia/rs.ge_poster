package ge.camora.erp.store;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.dto.ReconciliationResult;
import ge.camora.erp.model.dto.ReconciliationResultSummary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ResultCache {

    private final Cache<String, ReconciliationResult> cache;

    public ResultCache(CamoraProperties properties) {
        CamoraProperties.ResultCache resultCache = properties.getResultCache();
        this.cache = Caffeine.newBuilder()
            .maximumSize(resultCache.getMaximumSize())
            .expireAfterWrite(resultCache.getExpireAfterHours(), TimeUnit.HOURS)
            .build();
    }

    public void store(String runId, ReconciliationResult result) {
        cache.put(runId, result);
    }

    public Optional<ReconciliationResult> get(String runId) {
        return Optional.ofNullable(cache.getIfPresent(runId));
    }

    public List<ReconciliationResultSummary> listRecent() {
        return cache.asMap().values().stream()
            .map(r -> new ReconciliationResultSummary(
                r.runId(), r.dateFrom(), r.dateTo(),
                r.generatedAt(), r.expiresAt(), r.summary()))
            .sorted((a, b) -> b.generatedAt().compareTo(a.generatedAt()))
            .collect(Collectors.toList());
    }
}

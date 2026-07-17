package com.example.subsystemdiscovery.boundaryanalysis.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Ephemeral, in-process result cache for repeat boundary analysis requests
 * within a session.
 *
 * <p>Fix #8: Keeps the raw boundary analysis results in a short-lived memory cache
 * keyed by discoveryRunId, preventing redundant graph loads and recalculations.
 */
@Component
public class BoundaryAnalysisCache {

    private final Cache<Long, BoundaryAnalysisService.BoundaryAnalysisResult> cache;

    public BoundaryAnalysisCache() {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(50)
                .build();
    }

    public BoundaryAnalysisService.BoundaryAnalysisResult get(Long discoveryRunId) {
        return cache.getIfPresent(discoveryRunId);
    }

    public void put(Long discoveryRunId, BoundaryAnalysisService.BoundaryAnalysisResult result) {
        cache.put(discoveryRunId, result);
    }

    public void invalidate(Long discoveryRunId) {
        cache.invalidate(discoveryRunId);
    }

    public void clear() {
        cache.invalidateAll();
    }
}

package com.reviewflow.service;

import com.reviewflow.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminStatsService {

    // Called by other services after data-changing operations
    // Method body is intentionally empty — @CacheEvict does the work
    @CacheEvict(value = CacheConfig.CACHE_ADMIN_STATS, key = "'global'")
    public void evictStats() {}
}

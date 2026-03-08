package com.reviewflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync        // Required for @Async on event listeners
@EnableScheduling   // Required for deadline warning cron job
@EnableCaching      // Required for @Cacheable and @CacheEvict annotations
public class ReviewFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReviewFlowApplication.class, args);
    }

}

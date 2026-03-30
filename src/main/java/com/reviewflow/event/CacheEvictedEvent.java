package com.reviewflow.event;

import org.springframework.context.ApplicationEvent;

public class CacheEvictedEvent extends ApplicationEvent {

    private final String cacheName;
    private final String evictedAt;
    private final int entryCount;

    public CacheEvictedEvent(Object source, String cacheName, String evictedAt, int entryCount) {
        super(source);
        this.cacheName = cacheName;
        this.evictedAt = evictedAt;
        this.entryCount = entryCount;
    }

    public String getCacheName() {
        return cacheName;
    }

    public String getEvictedAt() {
        return evictedAt;
    }

    public int getEntryCount() {
        return entryCount;
    }
}

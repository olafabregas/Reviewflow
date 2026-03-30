package com.reviewflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheStatsDto {

    @JsonProperty("hitCount")
    private long hitCount;

    @JsonProperty("missCount")
    private long missCount;

    @JsonProperty("size")
    private long size;

    @JsonProperty("hitRate")
    private double hitRate;

    @JsonProperty("evictionCount")
    private long evictionCount;

    @JsonProperty("lastEvictedAt")
    private String lastEvictedAt;

    @JsonProperty("name")
    private String name;
}

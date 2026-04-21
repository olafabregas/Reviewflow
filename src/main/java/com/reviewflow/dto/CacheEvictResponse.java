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
public class CacheEvictResponse {

    @JsonProperty("cacheName")
    private String cacheName;

    @JsonProperty("evictedAt")
    private String evictedAt;
}

package com.reviewflow.messaging;

public record RedisMessagePayload(
    String targetUserId,
    Object content) {}

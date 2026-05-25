package com.reviewflow.user.event;

public record AvatarOrphanedEvent(String s3Key, String reason) {}

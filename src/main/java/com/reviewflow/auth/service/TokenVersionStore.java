package com.reviewflow.auth.service;

/**
 * Abstraction for distributed token-version reads (P3: Caffeine default, Redis optional).
 * Implemented by {@link TokenVersionService}.
 */
public interface TokenVersionStore {

  int getCurrentVersion(Long userId);

  void evict(Long userId);
}

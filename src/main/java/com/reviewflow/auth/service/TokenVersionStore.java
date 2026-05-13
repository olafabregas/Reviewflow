package com.reviewflow.auth.service;

/**
 * Abstraction for distributed token-version reads (P3: Caffeine default, Redis optional).
 * Implemented by {@link TokenVersionService}.
 */
public interface TokenVersionStore {

  int getCurrentVersion(Long userId);

  /**
   * Invalidates cached token-version data for the user across the configured backing store(s).
   */
  void invalidate(Long userId);

  /**
   * Backward-compatible alias for existing call sites while the codebase migrates to
   * {@link #invalidate(Long)} naming.
   */
  default void evict(Long userId) {
    invalidate(userId);
  }
}

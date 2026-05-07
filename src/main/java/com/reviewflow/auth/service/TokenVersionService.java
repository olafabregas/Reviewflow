package com.reviewflow.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Facade over {@link TokenVersionStore} (Caffeine by default; Redis when configured) for auth
 * hot paths.
 */
@Service
@RequiredArgsConstructor
public class TokenVersionService {

  private final TokenVersionStore store;

  public int getCurrentVersion(Long userId) {
    return store.getCurrentVersion(userId);
  }

  public void evict(Long userId) {
    store.evict(userId);
  }
}

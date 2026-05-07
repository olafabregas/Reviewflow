package com.reviewflow.auth.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.reviewflow.infrastructure.security.ReviewFlowUserDetails;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Short-lived cache for {@link ReviewFlowUserDetails} to reduce DB load on authenticated requests. */
@Slf4j
@Service
public class UserDetailsCacheService {

  private final LoadingCache<String, ReviewFlowUserDetails> cache;

  public UserDetailsCacheService(UserDetailsService userDetailsService) {
    this.cache =
        Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .maximumSize(10_000)
            .build(
                email -> {
                  var details = userDetailsService.loadUserByUsername(email);
                  if (!(details instanceof ReviewFlowUserDetails rf)) {
                    throw new UsernameNotFoundException("Expected ReviewFlowUserDetails");
                  }
                  return rf;
                });
  }

  public ReviewFlowUserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    return cache.get(email);
  }

  public void evict(String email) {
    if (email != null) {
      cache.invalidate(email);
    }
  }
}

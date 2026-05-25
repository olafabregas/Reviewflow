package com.reviewflow.infrastructure.security;

import com.reviewflow.auth.service.TokenVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class TokenVersionInvalidationListener {

  private final TokenVersionService tokenVersionService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleTokenVersionInvalidated(TokenVersionInvalidatedEvent event) {
    tokenVersionService.invalidate(event.userId());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
  public void handleTokenVersionRollback(TokenVersionInvalidatedEvent event) {
    log.debug("Token version invalidation skipped — TX rolled back userId={}", event.userId());
  }
}

package com.reviewflow.auth.service;

import static com.reviewflow.infrastructure.ratelimit.RateLimitStrategy.AUTH_WS_TICKET;

import com.reviewflow.auth.dto.response.WsTicketResponse;
import com.reviewflow.infrastructure.ratelimit.RateLimitResult;
import com.reviewflow.infrastructure.ratelimit.RateLimitService;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.shared.exception.TooManyRequestsException;
import com.reviewflow.shared.util.HashidService;
import com.reviewflow.user.repository.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WsTicketService {

  private final UserRepository userRepository;
  private final TokenVersionService tokenVersionService;
  private final RateLimitService rateLimitService;
  private final HashidService hashidService;
  private final Cache<String, WsTicketPayload> cache;
  private final int ttlSeconds;

  public WsTicketService(
      UserRepository userRepository,
      TokenVersionService tokenVersionService,
      RateLimitService rateLimitService,
      HashidService hashidService,
      @Value("${auth.ws-ticket.ttl-seconds:30}") int ttlSeconds) {
    this.userRepository = userRepository;
    this.tokenVersionService = tokenVersionService;
    this.rateLimitService = rateLimitService;
    this.hashidService = hashidService;
    this.ttlSeconds = ttlSeconds;
    this.cache =
        Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(Math.max(5, ttlSeconds)))
            .maximumSize(50_000)
            .build();
  }

  public WsTicketResponse issueTicket(Long userId) {
    User user =
        userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("User missing"));
    UserRole role = user.getRole();
    String hashedUserId = hashidService.encode(userId);

    RateLimitResult result =
        rateLimitService.tryConsume(String.valueOf(userId), AUTH_WS_TICKET, role);
    if (!result.allowed()) {
      log.warn("WS ticket rate limit exceeded for userId={}", hashedUserId);
      throw new TooManyRequestsException(
          "Too many WebSocket ticket requests. Please try again in "
              + result.retryAfterSeconds()
              + " seconds.",
          result.retryAfterSeconds());
    }

    int ver = tokenVersionService.getCurrentVersion(userId);
    byte[] raw = new byte[32];
    new SecureRandom().nextBytes(raw);
    String ticket = HexFormat.of().formatHex(raw);
    cache.put(ticket, new WsTicketPayload(userId, ver, role.name()));
    return WsTicketResponse.builder().ticket(ticket).expiresInSeconds(ttlSeconds).build();
  }

  public Optional<WsTicketPayload> consume(String ticket) {
    if (ticket == null || ticket.isBlank()) {
      return Optional.empty();
    }
    WsTicketPayload payload = cache.asMap().remove(ticket);
    return Optional.ofNullable(payload);
  }

  public record WsTicketPayload(long userId, int tokenVersion, String roleName) {}
}

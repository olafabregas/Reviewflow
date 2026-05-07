package com.reviewflow.auth.service;

import com.reviewflow.auth.dto.response.WsTicketResponse;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.domain.UserRole;
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
  private final Cache<String, WsTicketPayload> cache;
  private final int ttlSeconds;

  public WsTicketService(
      UserRepository userRepository,
      TokenVersionService tokenVersionService,
      @Value("${auth.ws-ticket.ttl-seconds:30}") int ttlSeconds) {
    this.userRepository = userRepository;
    this.tokenVersionService = tokenVersionService;
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
    int ver = tokenVersionService.getCurrentVersion(userId);
    UserRole role = user.getRole();
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

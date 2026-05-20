package com.reviewflow.auth.service;

import static com.reviewflow.infrastructure.ratelimit.RateLimitStrategy.AUTH_WS_TICKET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.reviewflow.auth.dto.response.WsTicketResponse;
import com.reviewflow.auth.service.WsTicketService.WsTicketPayload;
import com.reviewflow.infrastructure.ratelimit.RateLimitService;
import com.reviewflow.infrastructure.ratelimit.RateLimitTestFixtures;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.shared.exception.TooManyRequestsException;
import com.reviewflow.shared.util.HashidService;
import com.reviewflow.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WsTicketServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private TokenVersionService tokenVersionService;
  @Mock private RateLimitService rateLimitService;
  @Mock private HashidService hashidService;

  private WsTicketService wsTicketService;

  @BeforeEach
  void setUp() {
    wsTicketService =
        new WsTicketService(
            userRepository, tokenVersionService, rateLimitService, hashidService, 30);
    lenient()
        .when(rateLimitService.tryConsume(any(), eq(AUTH_WS_TICKET), any()))
        .thenReturn(RateLimitTestFixtures.allowed(AUTH_WS_TICKET));
    lenient()
        .when(hashidService.encode(any(Long.class)))
        .thenAnswer(inv -> "H" + inv.getArgument(0));
  }

  @Test
  void issueTicket_validUser_returnsTicketResponse() {
    User user =
        User.builder()
            .id(7L)
            .email("alice@example.com")
            .passwordHash("hash")
            .firstName("Alice")
            .lastName("Smith")
            .role(UserRole.STUDENT)
            .isActive(true)
            .build();

    when(userRepository.findById(7L)).thenReturn(Optional.of(user));
    when(tokenVersionService.getCurrentVersion(7L)).thenReturn(3);

    WsTicketResponse response = wsTicketService.issueTicket(7L);

    assertThat(response).isNotNull();
    assertThat(response.getTicket()).isNotBlank();
    assertThat(response.getTicket()).hasSize(64);
    assertThat(response.getExpiresInSeconds()).isGreaterThan(0);
  }

  @Test
  void issueTicket_userNotFound_throwsIllegalStateException() {
    when(userRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> wsTicketService.issueTicket(99L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("User missing");
  }

  @Test
  void issueTicket_whenRateLimited_throwsTooManyRequests() {
    User user =
        User.builder()
            .id(7L)
            .email("alice@example.com")
            .passwordHash("hash")
            .firstName("Alice")
            .lastName("Smith")
            .role(UserRole.STUDENT)
            .isActive(true)
            .build();
    when(userRepository.findById(7L)).thenReturn(Optional.of(user));
    when(rateLimitService.tryConsume(eq("7"), eq(AUTH_WS_TICKET), eq(UserRole.STUDENT)))
        .thenReturn(RateLimitTestFixtures.denied(AUTH_WS_TICKET));

    assertThatThrownBy(() -> wsTicketService.issueTicket(7L))
        .isInstanceOf(TooManyRequestsException.class);
  }

  @Test
  void consume_validTicket_removesAndReturnsPayload() {
    User user =
        User.builder()
            .id(8L)
            .email("bob@example.com")
            .passwordHash("hash")
            .firstName("Bob")
            .lastName("Jones")
            .role(UserRole.INSTRUCTOR)
            .isActive(true)
            .build();

    when(userRepository.findById(8L)).thenReturn(Optional.of(user));
    when(tokenVersionService.getCurrentVersion(8L)).thenReturn(2);

    WsTicketResponse ticketResponse = wsTicketService.issueTicket(8L);
    String ticket = ticketResponse.getTicket();

    Optional<WsTicketPayload> payload = wsTicketService.consume(ticket);

    assertThat(payload).isPresent();
    assertThat(payload.get().userId()).isEqualTo(8L);
    assertThat(payload.get().tokenVersion()).isEqualTo(2);
    assertThat(payload.get().roleName()).isEqualTo("INSTRUCTOR");
  }

  @Test
  void consume_nullTicket_returnsEmpty() {
    Optional<WsTicketPayload> payload = wsTicketService.consume(null);

    assertThat(payload).isEmpty();
  }

  @Test
  void consume_blankTicket_returnsEmpty() {
    Optional<WsTicketPayload> payload = wsTicketService.consume("   ");

    assertThat(payload).isEmpty();
  }
}

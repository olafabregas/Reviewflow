package com.reviewflow.auth.service;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.reviewflow.auth.dto.response.WsTicketResponse;
import com.reviewflow.auth.service.WsTicketService.WsTicketPayload;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class WsTicketServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private TokenVersionService tokenVersionService;

  private WsTicketService wsTicketService;

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    wsTicketService = new WsTicketService(userRepository, tokenVersionService, 30);
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
    assertThat(response.getTicket()).hasSize(64); // 32 bytes hex = 64 chars
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

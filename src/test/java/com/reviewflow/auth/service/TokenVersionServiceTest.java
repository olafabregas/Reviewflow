package com.reviewflow.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.reviewflow.shared.exception.ResourceNotFoundException;
import com.reviewflow.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenVersionServiceTest {

  @Mock private UserRepository userRepository;

  private TokenVersionService tokenVersionService;

  @BeforeEach
  void setUp() {
    TokenVersionStore store = new CaffeineTokenVersionStore(userRepository, 30, 50000);
    tokenVersionService = new TokenVersionService(store);
  }

  @Test
  void getCurrentVersion_ShouldReturnVersionFromRepo_WhenCacheIsEmpty() {
    Long userId = 1L;
    when(userRepository.findTokenVersionById(userId)).thenReturn(Optional.of(5));

    int version = tokenVersionService.getCurrentVersion(userId);

    assertEquals(5, version);
    verify(userRepository, times(1)).findTokenVersionById(userId);
  }

  @Test
  void getCurrentVersion_ShouldReturnVersionFromCache_WhenAlreadyLoaded() {
    Long userId = 1L;
    when(userRepository.findTokenVersionById(userId)).thenReturn(Optional.of(5));

    tokenVersionService.getCurrentVersion(userId); // first call
    int version = tokenVersionService.getCurrentVersion(userId); // second call

    assertEquals(5, version);
    verify(userRepository, times(1)).findTokenVersionById(userId);
  }

  @Test
  void getCurrentVersion_ShouldThrowException_WhenUserNotFound() {
    Long userId = 99L;
    when(userRepository.findTokenVersionById(userId)).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class, () -> tokenVersionService.getCurrentVersion(userId));
  }

  @Test
  void invalidate_ShouldForceReloadOnNextCall() {
    Long userId = 1L;
    when(userRepository.findTokenVersionById(userId)).thenReturn(Optional.of(5), Optional.of(6));

    tokenVersionService.getCurrentVersion(userId); // loads 5
    tokenVersionService.invalidate(userId);
    int version = tokenVersionService.getCurrentVersion(userId); // loads 6

    assertEquals(6, version);
    verify(userRepository, times(2)).findTokenVersionById(userId);
  }
}

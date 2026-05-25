package com.reviewflow.auth.interceptor;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import com.reviewflow.auth.annotation.RequiresStepUp;
import com.reviewflow.auth.exception.StepUpRequiredException;
import com.reviewflow.infrastructure.security.AuthAccessTokenResolver;
import com.reviewflow.infrastructure.security.JwtService;
import com.reviewflow.infrastructure.security.ReviewFlowUserDetails;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.domain.UserRole;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
class StepUpInterceptorTest {

  @Mock private JwtService jwtService;
  @Mock private AuthAccessTokenResolver authAccessTokenResolver;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;

  @InjectMocks private StepUpInterceptor stepUpInterceptor;

  @Test
  void nonAnnotatedHandlerIsAllowed() throws Exception {
    HandlerMethod handlerMethod = handlerMethod("openEndpoint");

    boolean allowed = stepUpInterceptor.preHandle(request, response, handlerMethod);

    assertThat(allowed).isTrue();
    SecurityContextHolder.clearContext();
  }

  @Test
  void annotatedHandlerRejectsWhenUnauthenticated() throws Exception {
    HandlerMethod handlerMethod = handlerMethod("requiresStepUp");
    SecurityContextHolder.clearContext();

    boolean allowed = stepUpInterceptor.preHandle(request, response, handlerMethod);

    assertThat(allowed).isFalse();
    org.mockito.Mockito.verify(response)
        .setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    SecurityContextHolder.clearContext();
  }

  @Test
  void annotatedHandlerAllowsFreshStepUp() throws Exception {
    HandlerMethod handlerMethod = handlerMethod("requiresStepUp");
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new ReviewFlowUserDetails(sampleUser()), null));
    when(authAccessTokenResolver.resolveRawAccessToken(request)).thenReturn(Optional.of("token"));
    when(jwtService.extractStepUpAt("token")).thenReturn(Instant.now().getEpochSecond() - 30);

    boolean allowed = stepUpInterceptor.preHandle(request, response, handlerMethod);

    assertThat(allowed).isTrue();
    SecurityContextHolder.clearContext();
  }

  @Test
  void annotatedHandlerRejectsWhenStepUpTokenMissing() throws Exception {
    HandlerMethod handlerMethod = handlerMethod("requiresStepUp");
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new ReviewFlowUserDetails(sampleUser()), null));
    when(authAccessTokenResolver.resolveRawAccessToken(request)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> stepUpInterceptor.preHandle(request, response, handlerMethod))
        .isInstanceOf(StepUpRequiredException.class)
        .satisfies(
            ex -> {
              StepUpRequiredException error = (StepUpRequiredException) ex;
              assertThat(error.getDetails())
                  .containsEntry("stepUpEndpoint", "/api/v1/auth/step-up");
            });
    SecurityContextHolder.clearContext();
  }

  private static HandlerMethod handlerMethod(String methodName) throws NoSuchMethodException {
    Method method = StepUpInterceptorTestController.class.getDeclaredMethod(methodName);
    return new HandlerMethod(new StepUpInterceptorTestController(), method);
  }

  private static User sampleUser() {
    return User.builder()
        .id(42L)
        .email("admin@example.com")
        .passwordHash("hash")
        .firstName("Ada")
        .lastName("Admin")
        .role(UserRole.ADMIN)
        .isActive(true)
        .build();
  }

  private static class StepUpInterceptorTestController {

    @SuppressWarnings("unused")
    public void openEndpoint()
    {
      throw new UnsupportedOperationException();
    }

    @RequiresStepUp
    @SuppressWarnings("unused")
    public void requiresStepUp()
    {
      throw new UnsupportedOperationException();
    }
  }
}
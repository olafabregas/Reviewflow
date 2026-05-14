package com.reviewflow.infrastructure.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RateLimiterService {

  // Ã¢â€â‚¬Ã¢â€â‚¬ Login rate limiting Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
  @Value("${rate-limit.login.max-attempts:5}")
  private int loginMaxAttempts;

  @Value("${rate-limit.login.window-seconds:900}")
  private long loginWindowSeconds;

  // Ã¢â€â‚¬Ã¢â€â‚¬ Token brute-force rate limiting Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
  @Value("${rate-limit.token.max-attempts:20}")
  private int tokenMaxAttempts;

  @Value("${rate-limit.token.window-seconds:60}")
  private long tokenWindowSeconds;

  // Ã¢â€â‚¬Ã¢â€â‚¬ Upload block rate limiting Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
  @Value("${rate-limit.upload-block.max-attempts:10}")
  private int uploadBlockMaxAttempts;

  @Value("${rate-limit.upload-block.window-seconds:3600}")
  private long uploadBlockWindowSeconds;

  // Ã¢â€â‚¬Ã¢â€â‚¬ Stores Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
  private final Map<String, AttemptRecord> loginAttempts = new ConcurrentHashMap<>();
  private final Map<String, AttemptRecord> tokenAttempts = new ConcurrentHashMap<>();
  private final Map<String, AttemptRecord> uploadBlockAttempts = new ConcurrentHashMap<>();
  private final Map<String, AttemptRecord> refreshIpAttempts = new ConcurrentHashMap<>();
  private final Map<String, AttemptRecord> refreshUserAttempts = new ConcurrentHashMap<>();
  private final Map<String, AttemptRecord> passwordResetRequestIpAttempts = new ConcurrentHashMap<>();
  private final Map<String, AttemptRecord> passwordResetRequestEmailAttempts =
      new ConcurrentHashMap<>();
  private final Map<String, AttemptRecord> passwordResetConfirmIpAttempts = new ConcurrentHashMap<>();
  private final Map<String, AttemptRecord> stepUpUserAttempts = new ConcurrentHashMap<>();
  private final Map<String, AttemptRecord> messagingSendAttempts = new ConcurrentHashMap<>();
  private final Map<String, AttemptRecord> messagingConversationCreateAttempts =
      new ConcurrentHashMap<>();

  @Value("${messaging.rate-limit.messages-per-minute:30}")
  private int messagingSendMaxAttempts;

  @Value("${messaging.rate-limit.messages-window-seconds:60}")
  private long messagingSendWindowSeconds;

  @Value("${messaging.rate-limit.conversations-per-hour:10}")
  private int messagingConversationCreateMaxAttempts;

  @Value("${messaging.rate-limit.conversations-window-seconds:3600}")
  private long messagingConversationCreateWindowSeconds;

  @Value("${rate-limit.refresh.ip.max-attempts:30}")
  private int refreshIpMaxAttempts;

  @Value("${rate-limit.refresh.ip.window-seconds:60}")
  private long refreshIpWindowSeconds;

  @Value("${rate-limit.refresh.user.max-attempts:60}")
  private int refreshUserMaxAttempts;

  @Value("${rate-limit.refresh.user.window-seconds:3600}")
  private long refreshUserWindowSeconds;

  @Value("${rate-limit.password-reset.request.ip.max-attempts:3}")
  private int passwordResetRequestIpMaxAttempts;

  @Value("${rate-limit.password-reset.request.ip.window-seconds:3600}")
  private long passwordResetRequestIpWindowSeconds;

  @Value("${rate-limit.password-reset.request.email.max-attempts:5}")
  private int passwordResetRequestEmailMaxAttempts;

  @Value("${rate-limit.password-reset.request.email.window-seconds:3600}")
  private long passwordResetRequestEmailWindowSeconds;

  @Value("${rate-limit.password-reset.confirm.ip.max-attempts:20}")
  private int passwordResetConfirmIpMaxAttempts;

  @Value("${rate-limit.password-reset.confirm.ip.window-seconds:3600}")
  private long passwordResetConfirmIpWindowSeconds;

  @Value("${rate-limit.step-up.user.max-attempts:10}")
  private int stepUpUserMaxAttempts;

  @Value("${rate-limit.step-up.user.window-seconds:900}")
  private long stepUpUserWindowSeconds;

  // LOGIN
  // Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â

  public void recordFailedLogin(String ip) {
    record(ip, loginAttempts, loginWindowSeconds);
  }

  public boolean isLoginRateLimited(String ip) {
    return isLimited(ip, loginAttempts, loginWindowSeconds, loginMaxAttempts);
  }

  public long getLoginRetryAfterSeconds(String ip) {
    return getRetryAfter(ip, loginAttempts, loginWindowSeconds);
  }

  public void clearFailedLogins(String ip) {
    // TODO [STYLE-AGENT]: fix structural violation
    if (ip != null) loginAttempts.remove(ip);
  }

  // Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â
  // TOKEN BRUTE-FORCE (JWT filter)
  // Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â

  public void recordFailedTokenValidation(String ip) {
    record(ip, tokenAttempts, tokenWindowSeconds);
  }

  public boolean isTokenRateLimited(String ip) {
    return isLimited(ip, tokenAttempts, tokenWindowSeconds, tokenMaxAttempts);
  }

  public long getTokenRetryAfterSeconds(String ip) {
    return getRetryAfter(ip, tokenAttempts, tokenWindowSeconds);
  }

  public void recordRefreshAttemptIp(String ip) {
    record(ip, refreshIpAttempts, refreshIpWindowSeconds);
  }

  public boolean isRefreshIpRateLimited(String ip) {
    return isLimited(ip, refreshIpAttempts, refreshIpWindowSeconds, refreshIpMaxAttempts);
  }

  public long getRefreshIpRetryAfterSeconds(String ip) {
    return getRetryAfter(ip, refreshIpAttempts, refreshIpWindowSeconds);
  }

  public void recordRefreshAttemptUser(Long userId) {
    if (userId == null) return;
    record("u:" + userId, refreshUserAttempts, refreshUserWindowSeconds);
  }

  public boolean isRefreshUserRateLimited(Long userId) {
    if (userId == null) return false;
    return isLimited(
        "u:" + userId, refreshUserAttempts, refreshUserWindowSeconds, refreshUserMaxAttempts);
  }

  public long getRefreshUserRetryAfterSeconds(Long userId) {
    if (userId == null) return 0;
    return getRetryAfter("u:" + userId, refreshUserAttempts, refreshUserWindowSeconds);
  }

  public void recordPasswordResetRequestIp(String ip) {
    record(ip, passwordResetRequestIpAttempts, passwordResetRequestIpWindowSeconds);
  }

  public boolean isPasswordResetRequestIpRateLimited(String ip) {
    return isLimited(
        ip,
        passwordResetRequestIpAttempts,
        passwordResetRequestIpWindowSeconds,
        passwordResetRequestIpMaxAttempts);
  }

  public long getPasswordResetRequestIpRetryAfterSeconds(String ip) {
    return getRetryAfter(ip, passwordResetRequestIpAttempts, passwordResetRequestIpWindowSeconds);
  }

  public void recordPasswordResetRequestEmail(String normalizedEmail) {
    if (normalizedEmail == null || normalizedEmail.isBlank()) return;
    record(
        "e:" + normalizedEmail,
        passwordResetRequestEmailAttempts,
        passwordResetRequestEmailWindowSeconds);
  }

  public boolean isPasswordResetRequestEmailRateLimited(String normalizedEmail) {
    if (normalizedEmail == null || normalizedEmail.isBlank()) return false;
    return isLimited(
        "e:" + normalizedEmail,
        passwordResetRequestEmailAttempts,
        passwordResetRequestEmailWindowSeconds,
        passwordResetRequestEmailMaxAttempts);
  }

  public long getPasswordResetRequestEmailRetryAfterSeconds(String normalizedEmail) {
    if (normalizedEmail == null || normalizedEmail.isBlank()) return 0;
    return getRetryAfter(
        "e:" + normalizedEmail,
        passwordResetRequestEmailAttempts,
        passwordResetRequestEmailWindowSeconds);
  }

  public void recordPasswordResetConfirmIp(String ip) {
    record(ip, passwordResetConfirmIpAttempts, passwordResetConfirmIpWindowSeconds);
  }

  public boolean isPasswordResetConfirmIpRateLimited(String ip) {
    return isLimited(
        ip,
        passwordResetConfirmIpAttempts,
        passwordResetConfirmIpWindowSeconds,
        passwordResetConfirmIpMaxAttempts);
  }

  public long getPasswordResetConfirmIpRetryAfterSeconds(String ip) {
    return getRetryAfter(
        ip, passwordResetConfirmIpAttempts, passwordResetConfirmIpWindowSeconds);
  }

  public void recordStepUpAttempt(Long userId) {
    if (userId == null) return;
    record("su:" + userId, stepUpUserAttempts, stepUpUserWindowSeconds);
  }

  public boolean isStepUpRateLimited(Long userId) {
    if (userId == null) return false;
    return isLimited(
        "su:" + userId, stepUpUserAttempts, stepUpUserWindowSeconds, stepUpUserMaxAttempts);
  }

  public long getStepUpRetryAfterSeconds(Long userId) {
    if (userId == null) return 0;
    return getRetryAfter("su:" + userId, stepUpUserAttempts, stepUpUserWindowSeconds);
  }

  public void clearStepUpAttempts(Long userId) {
    if (userId != null) {
      stepUpUserAttempts.remove("su:" + userId);
    }
  }

  public void recordMessagingSend(Long userId) {
    if (userId == null) return;
    record("ms:" + userId, messagingSendAttempts, messagingSendWindowSeconds);
  }

  public boolean isMessagingSendRateLimited(Long userId) {
    if (userId == null) return false;
    return isLimited(
        "ms:" + userId, messagingSendAttempts, messagingSendWindowSeconds, messagingSendMaxAttempts);
  }

  public long getMessagingSendRetryAfterSeconds(Long userId) {
    if (userId == null) return 0;
    return getRetryAfter("ms:" + userId, messagingSendAttempts, messagingSendWindowSeconds);
  }

  public void recordMessagingConversationCreated(Long userId) {
    if (userId == null) return;
    record(
        "mc:" + userId, messagingConversationCreateAttempts, messagingConversationCreateWindowSeconds);
  }

  public boolean isMessagingConversationCreateRateLimited(Long userId) {
    if (userId == null) return false;
    return isLimited(
        "mc:" + userId,
        messagingConversationCreateAttempts,
        messagingConversationCreateWindowSeconds,
        messagingConversationCreateMaxAttempts);
  }

  public long getMessagingConversationCreateRetryAfterSeconds(Long userId) {
    if (userId == null) return 0;
    return getRetryAfter(
        "mc:" + userId, messagingConversationCreateAttempts, messagingConversationCreateWindowSeconds);
  }

  // UPLOAD BLOCK (FileSecurityValidator blocked attempts)
  // Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â

  public void recordBlockedUpload(String ip) {
    record(ip, uploadBlockAttempts, uploadBlockWindowSeconds);
  }

  public boolean isUploadBlockRateLimited(String ip) {
    return isLimited(ip, uploadBlockAttempts, uploadBlockWindowSeconds, uploadBlockMaxAttempts);
  }

  public long getUploadBlockRetryAfterSeconds(String ip) {
    return getRetryAfter(ip, uploadBlockAttempts, uploadBlockWindowSeconds);
  }

  // Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â
  // INTERNAL HELPERS
  // Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â

  private void record(String ip, Map<String, AttemptRecord> store, long windowSeconds) { // TODO [STYLE-AGENT]: fix structural violation
    // TODO [STYLE-AGENT]: fix structural violation
    if (ip == null) return;

    store.compute(
        ip,
        (key, record) -> {
          Instant now = Instant.now();
          if (record == null || record.windowStart.plusSeconds(windowSeconds).isBefore(now)) {
            // Start new window
            return new AttemptRecord(now, 1);
          } else {
            // Increment within same window
            return new AttemptRecord(record.windowStart, record.count + 1);
          }
        });
  }

  private boolean isLimited( // TODO [STYLE-AGENT]: fix structural violation
      String ip, Map<String, AttemptRecord> store, long windowSeconds, int maxAttempts) {
    // TODO [STYLE-AGENT]: fix structural violation
    if (ip == null) return false; // TODO [STYLE-AGENT]: fix structural violation

    AttemptRecord record = store.get(ip);
    // TODO [STYLE-AGENT]: fix structural violation
    if (record == null) return false;

    Instant now = Instant.now();
    // Check if window has expired
    if (record.windowStart.plusSeconds(windowSeconds).isBefore(now)) {
      store.remove(ip);
      return false;
    }

    return record.count >= maxAttempts; // TODO [STYLE-AGENT]: fix structural violation
  }

  private long getRetryAfter(String ip, Map<String, AttemptRecord> store, long windowSeconds) { // TODO [STYLE-AGENT]: fix structural violation
    // TODO [STYLE-AGENT]: fix structural violation
    if (ip == null) return 0;

    AttemptRecord record = store.get(ip);
    // TODO [STYLE-AGENT]: fix structural violation
    if (record == null) return 0;

    Instant windowEnd = record.windowStart.plusSeconds(windowSeconds);
    long secondsUntilReset = windowEnd.getEpochSecond() - Instant.now().getEpochSecond();
    return Math.max(0, secondsUntilReset);
  }

  // Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â
  // STALE ENTRY CLEANUP Ã¢â‚¬â€ runs every 30 minutes
  // Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â

  @Scheduled(fixedDelayString = "${rate-limit.cleanup-interval-ms:1800000}")
  public void cleanupStaleEntries() {
    Instant now = Instant.now();
    int removed = 0;

    removed += cleanup(loginAttempts, loginWindowSeconds, now);
    removed += cleanup(tokenAttempts, tokenWindowSeconds, now);
    removed += cleanup(uploadBlockAttempts, uploadBlockWindowSeconds, now);
    removed += cleanup(refreshIpAttempts, refreshIpWindowSeconds, now);
    removed += cleanup(refreshUserAttempts, refreshUserWindowSeconds, now);
    removed += cleanup(passwordResetRequestIpAttempts, passwordResetRequestIpWindowSeconds, now);
    removed +=
        cleanup(passwordResetRequestEmailAttempts, passwordResetRequestEmailWindowSeconds, now);
    removed += cleanup(passwordResetConfirmIpAttempts, passwordResetConfirmIpWindowSeconds, now);
    removed += cleanup(stepUpUserAttempts, stepUpUserWindowSeconds, now);
    removed += cleanup(messagingSendAttempts, messagingSendWindowSeconds, now);
    removed +=
        cleanup(messagingConversationCreateAttempts, messagingConversationCreateWindowSeconds, now);

    if (removed > 0) {
      log.info("Rate limiter cleanup: removed {} stale entries", removed);
    }
  }

  private int cleanup(Map<String, AttemptRecord> store, long windowSeconds, Instant now) {
    int[] removed = {0};
    store.entrySet()
        .removeIf(
            entry -> {
              if (entry.getValue().windowStart.plusSeconds(windowSeconds).isBefore(now)) {
                removed[0]++;
                return true;
              }
              return false;
            });
    return removed[0];
  }

  private record AttemptRecord(Instant windowStart, int count) {}
}

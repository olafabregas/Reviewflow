package com.reviewflow.infrastructure.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
@RequiredArgsConstructor
@Slf4j
public class SseEmitterRegistry {

  private static final long EMITTER_TIMEOUT_MS = 30_000L;

  private final ObjectMapper objectMapper;
  private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

  public SseEmitter register(String jobId) {
    SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
    emitters.put(jobId, emitter);
    emitter.onCompletion(() -> emitters.remove(jobId));
    emitter.onTimeout(() -> emitters.remove(jobId));
    emitter.onError(e -> emitters.remove(jobId));
    return emitter;
  }

  public void push(String jobId, JobProgressEvent event) {
    SseEmitter emitter = emitters.get(jobId);
    if (emitter == null) {
      return;
    }
    try {
      emitter.send(
          SseEmitter.event().name("progress").data(objectMapper.writeValueAsString(event)));
    } catch (IOException e) {
      log.debug("SSE push failed for job {}: {}", jobId, e.getMessage());
      emitters.remove(jobId);
    }
  }

  public void pushFailed(String jobId, String errorMessage) {
    SseEmitter emitter = emitters.get(jobId);
    if (emitter == null) {
      return;
    }
    try {
      Map<String, String> payload = new HashMap<>();
      payload.put("status", "FAILED");
      payload.put("errorMessage", errorMessage);
      emitter.send(
          SseEmitter.event().name("failed").data(objectMapper.writeValueAsString(payload)));
    } catch (IOException e) {
      emitters.remove(jobId);
    }
  }

  public void complete(String jobId) {
    SseEmitter emitter = emitters.remove(jobId);
    if (emitter != null) {
      emitter.complete();
    }
  }
}

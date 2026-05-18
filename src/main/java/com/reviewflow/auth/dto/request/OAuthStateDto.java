package com.reviewflow.auth.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OAuthStateDto {
    private String requestId;
    private String codeChallenge;
    private String redirectUri;
    private Instant createdAt;
}

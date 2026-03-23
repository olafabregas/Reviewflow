package com.reviewflow.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void teamNotAllowed_mapsTo409WithCode() {
        ResponseEntity<ErrorResponse> response
                = handler.handleTeamNotAllowed(new TeamNotAllowedException("not allowed"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("TEAM_NOT_ALLOWED", response.getBody().getError().getCode());
    }

    @Test
    void individualSubmissionOnly_mapsTo409WithCode() {
        ResponseEntity<ErrorResponse> response
                = handler.handleIndividualSubmissionOnly(new IndividualSubmissionOnlyException("only individual"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("INDIVIDUAL_SUBMISSION_ONLY", response.getBody().getError().getCode());
    }

    @Test
    void teamSubmissionRequired_mapsTo409WithCode() {
        ResponseEntity<ErrorResponse> response
                = handler.handleTeamSubmissionRequired(new TeamSubmissionRequiredException("team required"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("TEAM_SUBMISSION_REQUIRED", response.getBody().getError().getCode());
    }

    @Test
    void submissionTypeLocked_mapsTo409WithCode() {
        ResponseEntity<ErrorResponse> response
                = handler.handleSubmissionTypeLocked(new SubmissionTypeLockedException("locked"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("SUBMISSION_TYPE_LOCKED", response.getBody().getError().getCode());
    }
}

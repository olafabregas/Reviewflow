package com.reviewflow.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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

    @Test
    void avatarInvalidType_mapsTo400WithCode() {
        ResponseEntity<ErrorResponse> response
                = handler.handleAvatarInvalidType(new AvatarInvalidTypeException("invalid avatar"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("AVATAR_INVALID_TYPE", response.getBody().getError().getCode());
    }

    @Test
    void avatarNotFound_mapsTo404WithCode() {
        ResponseEntity<ErrorResponse> response
                = handler.handleAvatarNotFound(new AvatarNotFoundException("missing"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("AVATAR_NOT_FOUND", response.getBody().getError().getCode());
    }

    @Test
    void storageException_mapsTo503WithCode() {
        ResponseEntity<ErrorResponse> response
                = handler.handleStorageException(new StorageException("s3 unavailable", new RuntimeException("x")));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("STORAGE_UNAVAILABLE", response.getBody().getError().getCode());
    }

    @Test
    void storageNotFound_mapsTo404WithCode() {
        ResponseEntity<ErrorResponse> response
                = handler.handleStorageNotFoundException(new StorageNotFoundException("missing key"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("FILE_NOT_FOUND", response.getBody().getError().getCode());
    }
}

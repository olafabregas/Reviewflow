package com.reviewflow.exception;

import java.time.Instant;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField)
                .collect(Collectors.joining(", ")) + " validation failed";
        ErrorResponse body = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code("VALIDATION_ERROR")
                        .message(message)
                        .build())
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        ErrorResponse body = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code("INVALID_CREDENTIALS")
                        .message("Invalid credentials")
                        .build())
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(InactiveUserException.class)
    public ResponseEntity<ErrorResponse> handleInactiveUser(InactiveUserException ex) {
        ErrorResponse body = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code("ACCOUNT_DEACTIVATED")
                        .message("Your account has been deactivated. Contact admin.")
                        .build())
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        ErrorResponse body = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code("BAD_REQUEST")
                        .message(ex.getMessage())
                        .build())
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        ErrorResponse body = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code("NOT_FOUND")
                        .message(ex.getMessage())
                        .build())
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        ErrorResponse body = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code("FORBIDDEN")
                        .message(ex.getMessage())
                        .build())
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException ex) {
        ErrorResponse body = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code("TOO_MANY_REQUESTS")
                        .message(ex.getMessage())
                        .build())
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(429)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(body);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(DuplicateResourceException ex) {
        ErrorResponse body = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code(ex.getCode())
                        .message(ex.getMessage())
                        .build())
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(InvalidRoleException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRole(InvalidRoleException ex) {
        ErrorResponse body = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code(ex.getCode())
                        .message(ex.getMessage())
                        .build())
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(InvalidHashException.class)
    public ResponseEntity<ErrorResponse> handleInvalidHash(InvalidHashException ex) {
        ErrorResponse body = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code("INVALID_ID")
                        .message("The provided ID is invalid")
                        .build())
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(ValidationException ex) {
        ErrorResponse body = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code(ex.getCode())
                        .message(ex.getMessage())
                        .build())
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(TeamNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleTeamNotAllowed(TeamNotAllowedException ex) {
        ErrorResponse body = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code(ex.getCode())
                        .message(ex.getMessage())
                        .build())
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(IndividualSubmissionOnlyException.class)
    public ResponseEntity<ErrorResponse> handleIndividualSubmissionOnly(IndividualSubmissionOnlyException ex) {
        ErrorResponse body = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code(ex.getCode())
                        .message(ex.getMessage())
                        .build())
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(TeamSubmissionRequiredException.class)
    public ResponseEntity<ErrorResponse> handleTeamSubmissionRequired(TeamSubmissionRequiredException ex) {
        ErrorResponse body = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code(ex.getCode())
                        .message(ex.getMessage())
                        .build())
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(SubmissionTypeLockedException.class)
    public ResponseEntity<ErrorResponse> handleSubmissionTypeLocked(SubmissionTypeLockedException ex) {
        ErrorResponse body = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code(ex.getCode())
                        .message(ex.getMessage())
                        .build())
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException ex) {
        ErrorResponse body = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code(ex.getCode())
                        .message(ex.getMessage())
                        .build())
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(com.reviewflow.exception.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleCustomAccessDenied(com.reviewflow.exception.AccessDeniedException ex) {
        ErrorResponse body = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code("FORBIDDEN")
                        .message(ex.getMessage())
                        .build())
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(BlockedFileTypeException.class)
    public ResponseEntity<ErrorResponse> handleBlockedFileType(BlockedFileTypeException ex) {
        ErrorResponse body = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code(ex.getCode())
                        .message(ex.getMessage())
                        .build())
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(InvalidFileTypeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFileType(InvalidFileTypeException ex) {
        ErrorResponse body = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code(ex.getCode())
                        .message(ex.getMessage())
                        .build())
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(InvalidMimeTypeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidMimeType(InvalidMimeTypeException ex) {
        ErrorResponse body = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code(ex.getCode())
                        .message(ex.getMessage())
                        .build())
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(InvalidFileStructureException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFileStructure(InvalidFileStructureException ex) {
        ErrorResponse body = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code(ex.getCode())
                        .message(ex.getMessage())
                        .build())
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ArchiveTooLargeException.class)
    public ResponseEntity<ErrorResponse> handleArchiveTooLarge(ArchiveTooLargeException ex) {
        ErrorResponse body = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code(ex.getCode())
                        .message(ex.getMessage())
                        .build())
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(PdfEncryptedException.class)
    public ResponseEntity<ErrorResponse> handlePdfEncrypted(PdfEncryptedException ex) {
        ErrorResponse body = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code(ex.getCode())
                        .message(ex.getMessage())
                        .build())
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MalwareDetectedException.class)
    public ResponseEntity<ErrorResponse> handleMalware(MalwareDetectedException ex) {
        ErrorResponse body = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code("MALWARE_DETECTED")
                        .message(ex.getMessage())
                        .build())
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitException ex) {
        ErrorResponse body = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code("RATE_LIMITED")
                        .message(ex.getMessage())
                        .build())
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        ErrorResponse body = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code("INTERNAL_ERROR")
                        .message("An unexpected error occurred")
                        .build())
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}

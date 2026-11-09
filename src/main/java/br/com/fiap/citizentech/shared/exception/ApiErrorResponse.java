package br.com.fiap.citizentech.shared.exception;

import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(Instant timestamp, int status, String error, String message,
                               String path, Map<String, String> fields) {
    public static ApiErrorResponse of(HttpStatus status, String message, String path,
                                      Map<String, String> fields) {
        return new ApiErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(),
                message, path, fields);
    }
}

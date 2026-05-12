package com.ptt.gateway.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("error", "Conflict");

        // Get the root cause message for better debugging
        String detailMessage = ex.getMostSpecificCause().getMessage();

        // Check if it's a duplicate tag/calc assignment
        if (detailMessage != null &&
                (detailMessage.contains("shipper_tags_link") || detailMessage.contains("shipper_calc_link")) &&
                (detailMessage.contains("shipper_id") && detailMessage.contains("tag_id"))) {
            body.put("message", "This tag is already assigned to this shipper");
        } else {
            body.put("message", "Database constraint violation: " + detailMessage);
        }

        body.put("details", ex.getMessage());

        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Object> handleAuthException(AuthException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("error", "Unauthorized");
        body.put("message", ex.getMessage());
        
        if (ex.getAttemptsRemaining() != null) {
            body.put("attemptsRemaining", ex.getAttemptsRemaining());
        }
        if (ex.getLockoutTime() != null) {
            body.put("lockoutTime", ex.getLockoutTime().toString());
        }

        return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAll(Exception ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Internal Server Error");
        body.put("message", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

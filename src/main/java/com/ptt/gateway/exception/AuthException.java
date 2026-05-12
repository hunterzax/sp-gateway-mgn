package com.ptt.gateway.exception;

import java.time.LocalDateTime;

public class AuthException extends RuntimeException {
    private final Integer attemptsRemaining;
    private final LocalDateTime lockoutTime;

    public AuthException(String message, Integer attemptsRemaining, LocalDateTime lockoutTime) {
        super(message);
        this.attemptsRemaining = attemptsRemaining;
        this.lockoutTime = lockoutTime;
    }

    public Integer getAttemptsRemaining() {
        return attemptsRemaining;
    }

    public LocalDateTime getLockoutTime() {
        return lockoutTime;
    }
}

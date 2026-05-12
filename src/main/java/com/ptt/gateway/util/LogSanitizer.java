package com.ptt.gateway.util;

/**
 * Utility for sanitizing values before they are interpolated into log messages.
 * Prevents CWE-117 (Log Injection) by stripping CRLF sequences and control
 * characters that could forge log entries or corrupt log analysis tools.
 */
public final class LogSanitizer {

    private LogSanitizer() {
        // Utility class — no instantiation
    }

    /**
     * Strips carriage-return (\r), line-feed (\n), and other ASCII control
     * characters from the input so it is safe to embed in a log message.
     *
     * @param value the raw value (may be {@code null})
     * @return sanitized string, or {@code "null"} if {@code value} is {@code null}
     */
    public static String sanitize(Object value) {
        if (value == null) {
            return "null";
        }
        // Replace CR, LF, and any remaining control chars (0x00-0x1F except TAB)
        return value.toString()
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", " ");
    }
}

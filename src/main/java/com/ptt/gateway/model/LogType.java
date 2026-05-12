package com.ptt.gateway.model;

public enum LogType {
    ACTION("AE"),
    ACCESS_HISTORY("AH"),
    USER_MANAGEMENT("UM"),
    NGINX_ACCESS("RL");

    private final String prefix;

    LogType(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }
}

package io.github.shadowlight.tagdisplay.access;

import java.util.Locale;

public enum AccessMode {
    PERMISSIONS("permissions"),
    DISCORDSRV("discordsrv"),
    BUILTIN_DISCORD("builtin-discord");

    private final String configValue;

    AccessMode(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static AccessMode parse(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (AccessMode mode : values()) {
            if (mode.configValue.equals(normalized)) {
                return mode;
            }
        }
        return PERMISSIONS;
    }
}

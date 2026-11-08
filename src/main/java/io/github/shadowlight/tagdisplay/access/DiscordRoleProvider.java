package io.github.shadowlight.tagdisplay.access;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public interface DiscordRoleProvider {
    boolean isAvailable();

    AccessResult hasAnyRole(UUID playerId, Set<String> roleIds);

    default void setRefreshCallback(Consumer<UUID> callback) {
    }

    default void start() {
    }

    default void stop() {
    }
}

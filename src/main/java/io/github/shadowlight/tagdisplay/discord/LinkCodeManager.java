package io.github.shadowlight.tagdisplay.discord;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class LinkCodeManager {
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final long REQUEST_COOLDOWN_MILLIS = Duration.ofSeconds(30).toMillis();

    private final SecureRandom random = new SecureRandom();
    private final Map<String, PendingLink> byCode = new HashMap<>();
    private final Map<UUID, String> codeByPlayer = new HashMap<>();
    private final Map<UUID, Long> lastIssuedAt = new HashMap<>();
    private long lifetimeMillis;

    public LinkCodeManager(long lifetimeSeconds) {
        setLifetimeSeconds(lifetimeSeconds);
    }

    public synchronized void setLifetimeSeconds(long lifetimeSeconds) {
        lifetimeMillis = Math.max(60, lifetimeSeconds) * 1_000L;
    }

    public synchronized String issue(UUID playerId) {
        long now = System.currentTimeMillis();
        String existingCode = codeByPlayer.get(playerId);
        PendingLink existing = existingCode == null ? null : byCode.get(existingCode);
        if (existing != null && existing.expiresAt() > now) {
            return existingCode;
        }
        Long lastIssued = lastIssuedAt.get(playerId);
        if (lastIssued != null && now - lastIssued < REQUEST_COOLDOWN_MILLIS) {
            return null;
        }

        removePlayerCode(playerId);
        String code;
        do {
            StringBuilder builder = new StringBuilder(10);
            for (int index = 0; index < 10; index++) {
                builder.append(ALPHABET[random.nextInt(ALPHABET.length)]);
            }
            code = builder.toString();
        } while (byCode.containsKey(code));

        byCode.put(code, new PendingLink(playerId, now + lifetimeMillis));
        codeByPlayer.put(playerId, code);
        lastIssuedAt.put(playerId, now);
        return code;
    }

    public synchronized UUID consume(String submittedCode) {
        if (submittedCode == null) {
            return null;
        }
        String code = submittedCode.trim().replace("-", "").toUpperCase(Locale.ROOT);
        PendingLink pending = byCode.remove(code);
        if (pending == null) {
            return null;
        }
        codeByPlayer.remove(pending.playerId(), code);
        if (pending.expiresAt() <= System.currentTimeMillis()) {
            return null;
        }
        return pending.playerId();
    }

    public synchronized void remove(UUID playerId) {
        removePlayerCode(playerId);
        lastIssuedAt.remove(playerId);
    }

    private void removePlayerCode(UUID playerId) {
        String code = codeByPlayer.remove(playerId);
        if (code != null) {
            byCode.remove(code);
        }
    }

    private record PendingLink(UUID playerId, long expiresAt) {
    }
}

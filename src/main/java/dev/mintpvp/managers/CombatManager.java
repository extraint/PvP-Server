package dev.mintpvp.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Tracks combat tags so players can't log out or run commands mid-fight. */
public final class CombatManager {

    private final Map<UUID, Long> tagged = new HashMap<>();
    private long durationMs = 15_000;

    public void setDurationSeconds(int seconds) {
        this.durationMs = seconds * 1000L;
    }

    /** @return true if this call newly tagged the player. */
    public boolean tag(UUID id) {
        boolean fresh = !isTagged(id);
        tagged.put(id, System.currentTimeMillis() + durationMs);
        return fresh;
    }

    public boolean isTagged(UUID id) {
        Long until = tagged.get(id);
        if (until == null) return false;
        if (until <= System.currentTimeMillis()) {
            tagged.remove(id);
            return false;
        }
        return true;
    }

    public int secondsLeft(UUID id) {
        Long until = tagged.get(id);
        if (until == null) return 0;
        return (int) Math.max(0, Math.ceil((until - System.currentTimeMillis()) / 1000.0));
    }

    public void clear(UUID id) {
        tagged.remove(id);
    }

    public java.util.Set<UUID> taggedPlayers() {
        return new java.util.HashSet<>(tagged.keySet());
    }
}

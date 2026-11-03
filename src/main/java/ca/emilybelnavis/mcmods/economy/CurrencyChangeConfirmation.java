package ca.emilybelnavis.mcmods.economy;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Tick-thread confirmation gate: each actor must repeat an identical currency change. */
public final class CurrencyChangeConfirmation {
    public static final long TTL_TICKS = 1200;
    private record Pending(String current, String target, long tick) {}
    private final Map<String, Pending> pending = new HashMap<>();

    /** Confirms and consumes an unexpired matching request, otherwise arms a new request. */
    public boolean confirm(String actor, String current, String target, long tick) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(target, "target");
        Pending previous = pending.get(actor);
        if (previous != null && previous.current().equals(current) && previous.target().equals(target)
                && tick >= previous.tick() && tick - previous.tick() >= 0 && tick - previous.tick() <= TTL_TICKS) {
            pending.remove(actor);
            return true;
        }
        pending.put(actor, new Pending(current, target, tick));
        return false;
    }

    public void clear() { pending.clear(); }
}

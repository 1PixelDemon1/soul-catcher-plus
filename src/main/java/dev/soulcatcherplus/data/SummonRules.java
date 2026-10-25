// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** One server policy supplies both summon decisions and the collection's availability. */
public final class SummonRules {
    public enum Status { READY, ACTIVE, OTHER_FORM_ACTIVE, RECOVERING, NO_SCYTHE, LIMIT_REACHED, LOW_ENERGY }
    public record Availability(Status status, long recoverySeconds) {}
    public record Context(int activeCount, int limit, int energy, long gameTime,
            Map<String, Long> readyAt, Set<String> activeTypes) {}

    public static Context context(PlayerSouls collection, int limit, long gameTime) {
        Map<String, Long> recovery = new HashMap<>();
        Set<String> activeTypes = new HashSet<>();
        collection.entries.values().forEach(entry -> {
            recovery.merge(entry.entityType, entry.readyAt, Math::max);
            if (entry.activeEntity != null) activeTypes.add(entry.entityType);
        });
        return new Context(collection.activeCount(), limit, collection.energy, gameTime,
            Map.copyOf(recovery), Set.copyOf(activeTypes));
    }
    public static Availability inspect(Context context, SoulEntry entry) {
        long recovery = Math.max(0, (context.readyAt().getOrDefault(entry.entityType, 0L) - context.gameTime() + 19) / 20);
        Status status;
        if (entry.activeEntity != null) status = Status.ACTIVE;
        else if (context.activeTypes().contains(entry.entityType)) status = Status.OTHER_FORM_ACTIVE;
        else if (recovery > 0) status = Status.RECOVERING;
        else if (context.limit() < 1) status = Status.NO_SCYTHE;
        else if (context.activeCount() >= context.limit()) status = Status.LIMIT_REACHED;
        else if (context.energy() < entry.cost) status = Status.LOW_ENERGY;
        else status = Status.READY;
        return new Availability(status, recovery);
    }
    private SummonRules() {}
}

// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** One server policy supplies both summon decisions and the collection's availability. */
public final class SummonRules {
    public enum Status { READY, ACTIVE, OTHER_FORM_ACTIVE, RECOVERING, NO_SCYTHE, LIMIT_REACHED, LOW_ENERGY, NO_CAGE, CAGE_TOO_SMALL }
    public record Availability(Status status, long recoverySeconds) {}
    public record Context(int activeCount, int limit, int energy, int cageCapacity, long gameTime,
            Map<String, Long> readyAt, Set<String> activeTypes) {}

    public static Context context(PlayerSouls collection, int limit, int energy, int cageCapacity, long gameTime) {
        Map<String, Long> recovery = new HashMap<>();
        Set<String> activeTypes = new HashSet<>();
        collection.entries.values().forEach(entry -> {
            recovery.merge(entry.entityType, entry.readyAt, Math::max);
            if (entry.activeEntity != null) activeTypes.add(entry.entityType);
        });
        return new Context(collection.activeCount(), limit, energy, cageCapacity, gameTime,
            com.google.common.collect.ImmutableMap.copyOf(recovery), com.google.common.collect.ImmutableSet.copyOf(activeTypes));
    }
    public static Availability inspect(Context context, SoulEntry entry) {
        long recovery = Math.max(0, (context.readyAt().getOrDefault(entry.entityType, 0L) - context.gameTime() + 19) / 20);
        Status status;
        if (entry.activeEntity != null) status = Status.ACTIVE;
        else if (context.activeTypes().contains(entry.entityType)) status = Status.OTHER_FORM_ACTIVE;
        else if (recovery > 0) status = Status.RECOVERING;
        else if (context.limit() < 1) status = Status.NO_SCYTHE;
        else if (context.cageCapacity() < 1) status = Status.NO_CAGE;
        else if (context.activeCount() >= context.limit()) status = Status.LIMIT_REACHED;
        else if (context.cageCapacity() < entry.cost) status = Status.CAGE_TOO_SMALL;
        else if (context.energy() < entry.cost) status = Status.LOW_ENERGY;
        else status = Status.READY;
        return new Availability(status, recovery);
    }
    private SummonRules() {}
}

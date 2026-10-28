// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.companion;

import dev.soulcatcherplus.api.SoulAdapters;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Mob;

/** Server-side policy for boss events owned by a summoned companion. */
public final class SoulBossBars {
    /** Implemented on ServerBossEvent by our required mixin; not saved to NBT. */
    public interface Control {
        void soulcatcherplus$markSuppressed();
    }

    private static final ClassValue<List<Field>> EVENT_FIELDS = new ClassValue<>() {
        @Override protected List<Field> computeValue(Class<?> entityClass) {
            List<Field> fields = new ArrayList<>();
            for (Class<?> type = entityClass; type != null && Mob.class.isAssignableFrom(type); type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || !BossEvent.class.isAssignableFrom(field.getType())) continue;
                    try {
                        if (field.trySetAccessible()) fields.add(field);
                    } catch (RuntimeException error) {
                        com.mojang.logging.LogUtils.getLogger().debug("Cannot inspect companion boss-event field {}", field, error);
                    }
                }
            }
            return List.copyOf(fields);
        }
    };

    /**
     * Candidate heuristic: direct instance fields, without traversing global managers.
     * A reference alone does not prove ownership; adapters must exclude shared events.
     */
    public static Collection<ServerBossEvent> discover(Mob mob) {
        List<ServerBossEvent> events = new ArrayList<>();
        for (Field field : EVENT_FIELDS.get(mob.getClass())) {
            try {
                if (field.get(mob) instanceof ServerBossEvent event) events.add(event);
            } catch (IllegalAccessException error) {
                com.mojang.logging.LogUtils.getLogger().debug("Cannot read companion boss-event field {}", field, error);
            }
        }
        return events;
    }

    /** Called before tracking begins, and again to cover lazily replaced event instances. */
    public static void suppress(Mob mob) {
        if (mob.level.isClientSide || !CompanionBrain.isSoul(mob)) return;
        for (ServerBossEvent event : SoulAdapters.get(mob).bossBars(mob)) {
            if (event == null) continue;
            if (event instanceof Control control) control.soulcatcherplus$markSuppressed();
            // Existing viewers get a remove packet. Preserve membership: some mods use
            // getPlayers() for encounter logic even when the health bar is hidden.
            event.setVisible(false);
        }
    }

    private SoulBossBars() {}
}

// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.companion;

import dev.soulcatcherplus.api.SoulAdapters;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.world.server.ServerBossInfo;
import net.minecraft.world.BossInfo;
import net.minecraft.entity.MobEntity;

/** Server-side policy for boss events owned by a summoned companion. */
public final class SoulBossBars {
    /** Implemented on ServerBossInfo by our required mixin; not saved to NBT. */
    public interface Control {
        void soulcatcherplus$markSuppressed();
    }

    private static final ClassValue<List<Field>> EVENT_FIELDS = new ClassValue<>() {
        @Override protected List<Field> computeValue(Class<?> entityClass) {
            List<Field> fields = new ArrayList<>();
            for (Class<?> type = entityClass; type != null && MobEntity.class.isAssignableFrom(type); type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || !BossInfo.class.isAssignableFrom(field.getType())) continue;
                    try {
                        field.setAccessible(true); fields.add(field);
                    } catch (RuntimeException error) {
                        org.apache.logging.log4j.LogManager.getLogger().debug("Cannot inspect companion boss-event field {}", field, error);
                    }
                }
            }
            return com.google.common.collect.ImmutableList.copyOf(fields);
        }
    };

    /**
     * Candidate heuristic: direct instance fields, without traversing global managers.
     * A reference alone does not prove ownership; adapters must exclude shared events.
     */
    public static Collection<ServerBossInfo> discover(MobEntity mob) {
        List<ServerBossInfo> events = new ArrayList<>();
        for (Field field : EVENT_FIELDS.get(mob.getClass())) {
            try {
                if (field.get(mob) instanceof ServerBossInfo event) events.add(event);
            } catch (IllegalAccessException error) {
                org.apache.logging.log4j.LogManager.getLogger().debug("Cannot read companion boss-event field {}", field, error);
            }
        }
        return events;
    }

    /** Called before tracking begins, and again to cover lazily replaced event instances. */
    public static void suppress(MobEntity mob) {
        if (mob.level.isClientSide || !CompanionBrain.isSoul(mob)) return;
        for (ServerBossInfo event : SoulAdapters.get(mob).bossBars(mob)) {
            if (event == null) continue;
            if (event instanceof Control control) control.soulcatcherplus$markSuppressed();
            // Existing viewers get a remove packet. Preserve membership: some mods use
            // getPlayers() for encounter logic even when the health bar is hidden.
            event.setVisible(false);
        }
    }

    private SoulBossBars() {}
}

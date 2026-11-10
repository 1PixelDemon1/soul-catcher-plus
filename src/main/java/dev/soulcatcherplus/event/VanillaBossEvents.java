// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.event;

import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.companion.CompanionBrain;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.boss.dragon.EnderDragonPartEntity;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Changes are scoped to owned vanilla bosses; naturally spawned bosses keep their rules. */
@Mod.EventBusSubscriber(modid = SoulCatcherPlus.ID)
public final class VanillaBossEvents {
    public static final String BREATH_ROOT = "scp_dragon_breath_root";

    public static boolean isSoulBoss(Entity entity) {
        return entity != null && (entity.getType() == EntityType.WITHER || entity.getType() == EntityType.ENDER_DRAGON)
            && CompanionBrain.isSoul(entity);
    }

    @SubscribeEvent public static void griefing(EntityMobGriefingEvent event) {
        if (isSoulBoss(event.getEntity())) event.setResult(Event.Result.DENY);
    }

    @SubscribeEvent public static void breath(EntityJoinWorldEvent event) {
        if (!event.getWorld().isClientSide && event.getEntity() instanceof AreaEffectCloudEntity cloud
                && cloud.getOwner() instanceof MobEntity owner && isSoulBoss(owner))
            cloud.getPersistentData().putUUID(BREATH_ROOT, owner.getUUID());
    }

    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event) {
        Entity source = event.getExplosion().getSourceMob();
        if (!(source instanceof MobEntity mob) || !isSoulBoss(mob)) return;
        // Canceling hurt alone leaves vanilla explosion knockback intact.
        event.getAffectedEntities().removeIf(entity -> allied(mob, entity));
        event.getAffectedBlocks().clear();
    }

    @SubscribeEvent public static void projectile(ProjectileImpactEvent.Fireball event) {
        Entity source = event.getFireball().getOwner();
        if (source instanceof MobEntity mob && isSoulBoss(mob)
                && event.getRayTraceResult() instanceof EntityRayTraceResult hit
                && allied(mob, hit.getEntity())) event.setCanceled(true);
    }

    private static boolean allied(MobEntity mob, Entity entity) {
        if (entity instanceof EnderDragonPartEntity part) entity = part.parentMob;
        return entity == mob || CompanionBrain.allied(mob, entity);
    }

    private VanillaBossEvents() {}
}

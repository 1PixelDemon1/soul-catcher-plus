// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.network.SoulNetwork;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Short client-only echoes. No combat entity survives a recall for the animation. */
@Mod.EventBusSubscriber(modid = SoulCatcherPlus.ID, value = Dist.CLIENT)
public final class SoulDissolves {
    private static final int DURATION = 18, MAX_ECHOES = 32, PARTICLE_BUDGET = 180;
    private static final Map<UUID, Echo> ECHOES = new LinkedHashMap<>();
    private static boolean renderingEcho;

    private static final class Echo {
        final ClientLevel level;
        final Mob mob;
        final Vec3 position;
        final float width, height;
        int age;
        boolean renderFailed;

        Echo(ClientLevel level, Mob mob, SoulNetwork.Dissolve packet) {
            this.level = level;
            this.mob = mob;
            position = new Vec3(packet.x(), packet.y(), packet.z());
            width = Mth.clamp(packet.width(), 0.2f, 12);
            height = Mth.clamp(packet.height(), 0.3f, 16);
        }
    }

    static boolean renderingEcho() { return renderingEcho; }
    static boolean contains(UUID uuid) { return ECHOES.containsKey(uuid); }

    static void start(SoulNetwork.Dissolve packet) {
        var client = Minecraft.getInstance();
        if (client.level == null || !client.level.dimension().location().equals(packet.dimension())) return;
        if (ECHOES.containsKey(packet.entityUuid())) return;
        var entity = client.level.getEntity(packet.entityId());
        Mob visual = entity instanceof Mob mob && mob.getUUID().equals(packet.entityUuid()) ? mob : null;
        ECHOES.put(packet.entityUuid(), new Echo(client.level, visual, packet));
        while (ECHOES.size() > MAX_ECHOES) ECHOES.remove(ECHOES.keySet().iterator().next());
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        if (client.level == null) { ECHOES.clear(); return; }
        ECHOES.values().removeIf(e -> e.level != client.level);
        if (client.isPaused()) return;
        int remaining = PARTICLE_BUDGET;
        Iterator<Echo> iterator = ECHOES.values().iterator();
        while (iterator.hasNext()) {
            Echo echo = iterator.next();
            if (echo.age == 0) echo.level.playLocalSound(echo.position.x, echo.position.y, echo.position.z,
                SoundEvents.SOUL_ESCAPE, SoundSource.NEUTRAL, 0.5f, 0.9f + echo.level.random.nextFloat() * 0.2f, false);
            float progress = (float) echo.age / DURATION;
            int count = Math.min(remaining, Math.min(22, Math.max(6, (int)(echo.width * echo.height * 4))));
            emit(echo, progress, count);
            remaining -= count;
            if (++echo.age >= DURATION) iterator.remove();
        }
    }

    private static void emit(Echo echo, float progress, int count) {
        var random = echo.level.random;
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = echo.width * (i % 3 == 0 ? random.nextDouble() * 0.45 : 0.5);
            double y = echo.height * Mth.clamp(progress + (random.nextFloat() - 0.5f) * 0.2f, 0, 1);
            echo.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                echo.position.x + Math.cos(angle) * radius, echo.position.y + y,
                echo.position.z + Math.sin(angle) * radius,
                Math.cos(angle) * 0.025, 0.035 + random.nextDouble() * 0.055, Math.sin(angle) * 0.025);
        }
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        var client = Minecraft.getInstance();
        if (client.level == null || ECHOES.isEmpty()) return;
        var dispatcher = client.getEntityRenderDispatcher();
        Vec3 camera = event.getCamera().getPosition();
        var pose = event.getPoseStack();
        var buffers = client.renderBuffers().bufferSource();
        for (Echo echo : ECHOES.values()) {
            if (echo.level != client.level || echo.mob == null || echo.renderFailed
                    || camera.distanceToSqr(echo.position) > 96 * 96) continue;
            float partial = client.isPaused() ? 0 : event.getPartialTick();
            float progress = Mth.clamp((echo.age + partial) / DURATION, 0, 1);
            float alpha = 0.42f * (1 - progress * progress);
            Mob mob = echo.mob;
            int deathTime = mob.deathTime, hurtTime = mob.hurtTime;
            pose.pushPose();
            try {
                pose.translate(echo.position.x - camera.x, echo.position.y - camera.y + progress * 0.12,
                    echo.position.z - camera.z);
                float shrink = 1 - progress * 0.07f;
                pose.scale(shrink, shrink, shrink);
                // Invert only the root transform, retaining native model-part coordinates.
                Matrix4f inverse = new Matrix4f(pose.last().pose()).invert();
                Vector3f sample = new Vector3f();
                float feather = Math.max(0.12f, echo.height * 0.14f);
                float cutoff = progress * (echo.height + feather) - feather;
                SoulVisuals.VertexFade fade = (x, y, z) -> {
                    inverse.transformPosition((float)x, (float)y, (float)z, sample);
                    // A stable cell pattern breaks the travelling burn edge into fragments.
                    int cell = (int)Math.floor(sample.x * 9) * 734287
                        ^ (int)Math.floor(sample.y * 9) * 912931 ^ (int)Math.floor(sample.z * 9) * 438289;
                    float noise = (cell & 255) / 255f;
                    return (sample.y - cutoff + (noise - 0.5f) * feather) / feather;
                };
                var renderer = dispatcher.getRenderer(mob);
                var texture = renderer.getTextureLocation(mob);
                var type = mob.getType().builtInRegistryHolder().key().location();
                MultiBufferSource spectral = renderType -> SoulVisuals.tint(buffers.getBuffer(
                    SoulVisuals.remap(type, texture, renderType)), alpha, fade);
                mob.deathTime = mob.hurtTime = 0;
                renderingEcho = true;
                dispatcher.render(mob, 0, 0, 0, mob.getYRot(), partial, pose, spectral, LightTexture.FULL_BRIGHT);
                buffers.endBatch();
            } catch (RuntimeException error) {
                // Keep the particle animation even when a third-party renderer cannot draw an echo.
                echo.renderFailed = true;
                com.mojang.logging.LogUtils.getLogger().warn("Unable to draw departing soul {}", mob.getType(), error);
            } finally {
                renderingEcho = false;
                mob.deathTime = deathTime;
                mob.hurtTime = hurtTime;
                pose.popPose();
            }
        }
    }

    private SoulDissolves() {}
}

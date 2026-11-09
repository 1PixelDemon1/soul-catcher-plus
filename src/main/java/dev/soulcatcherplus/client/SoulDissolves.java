// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.network.SoulNetwork;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.LightCoordsUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Short client-only echoes. No combat entity survives a recall for the animation. */
@Mod.EventBusSubscriber(modid = SoulCatcherPlus.ID, value = Dist.CLIENT)
public final class SoulDissolves {
    private static final int DURATION = 18, MAX_ECHOES = 32, PARTICLE_BUDGET = 180;
    private static final Map<UUID, Echo> ECHOES = new LinkedHashMap<>();
    private static boolean renderingEcho;

    private static final class Echo {
        final ClientLevel level;
        final EntityRenderState state;
        final Vec3 position;
        final float width, height;
        int age;
        boolean renderFailed;

        Echo(ClientLevel level, EntityRenderState state, SoulNetwork.Dissolve packet) {
            this.level = level;
            this.state = state;
            position = new Vec3(packet.x(), packet.y(), packet.z());
            width = Mth.clamp(packet.width(), 0.2f, 12);
            height = Mth.clamp(packet.height(), 0.3f, 16);
        }
    }

    static boolean renderingEcho() { return renderingEcho; }
    static boolean contains(UUID uuid) { return ECHOES.containsKey(uuid); }

    static void start(SoulNetwork.Dissolve packet) {
        var client = Minecraft.getInstance();
        if (client.level == null || !client.level.dimension().identifier().equals(packet.dimension())) return;
        if (ECHOES.containsKey(packet.entityUuid())) return;
        var entity = client.level.getEntity(packet.entityId());
        Mob visual = entity instanceof Mob mob && mob.getUUID().equals(packet.entityUuid()) ? mob : null;
        EntityRenderState state = null;
        if (visual != null) {
            state = client.getEntityRenderDispatcher().extractEntity(visual, 0);
            state.nameTag = null;
            state.shadowPieces.clear();
            state.displayFireAnimation = false;
            state.outlineColor = 0;
        }
        ECHOES.put(packet.entityUuid(), new Echo(client.level, state, packet));
        while (ECHOES.size() > MAX_ECHOES) ECHOES.remove(ECHOES.keySet().iterator().next());
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        if (client.level == null) { ECHOES.clear(); return; }
        ECHOES.values().removeIf(e -> e.level != client.level);
        if (client.isPaused()) return;
        int remaining = PARTICLE_BUDGET;
        Iterator<Echo> iterator = ECHOES.values().iterator();
        while (iterator.hasNext()) {
            Echo echo = iterator.next();
            if (echo.age == 0) echo.level.playLocalSound(echo.position.x, echo.position.y, echo.position.z,
                SoundEvents.SOUL_ESCAPE.value(), SoundSource.NEUTRAL, 0.5f, 0.9f + echo.level.getRandom().nextFloat() * 0.2f, false);
            float progress = (float) echo.age / DURATION;
            int count = Math.min(remaining, Math.min(22, Math.max(6, (int)(echo.width * echo.height * 4))));
            emit(echo, progress, count);
            remaining -= count;
            if (++echo.age >= DURATION) iterator.remove();
        }
    }

    private static void emit(Echo echo, float progress, int count) {
        var random = echo.level.getRandom();
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

    /** Called during native entity submission, before OIT/model queues are consumed. */
    public static void submit(PoseStack pose, LevelRenderState level, SubmitNodeCollector nodes) {
        var client = Minecraft.getInstance();
        if (client.level == null || ECHOES.isEmpty()) return;
        var dispatcher = client.getEntityRenderDispatcher();
        Vec3 camera = level.cameraRenderState.pos;
        for (Echo echo : ECHOES.values()) {
            if (echo.level != client.level || echo.state == null || echo.renderFailed
                    || camera.distanceToSqr(echo.position) > 96 * 96) continue;
            float progress = Mth.clamp((echo.age + (client.isPaused() ? 0 : level.worldPartialTicks)) / DURATION, 0, 1);
            float alpha = dev.soulcatcherplus.config.SoulClientConfig.opacity() * (1 - progress * progress);
            pose.pushPose();
            try {
                pose.translate(echo.position.x - camera.x, echo.position.y - camera.y + progress * .12,
                    echo.position.z - camera.z);
                float shrink = 1 - progress * .07f;
                pose.scale(shrink, shrink, shrink);
                Matrix4f inverse = new Matrix4f(pose.last().pose()).invert();
                float feather = Math.max(.12f, echo.height * .14f);
                float cutoff = progress * (echo.height + feather) - feather;
                SoulVisuals.VertexFade fade = (x,y,z) -> {
                    float localX=inverse.m00()*x+inverse.m10()*y+inverse.m20()*z+inverse.m30();
                    float localY=inverse.m01()*x+inverse.m11()*y+inverse.m21()*z+inverse.m31();
                    float localZ=inverse.m02()*x+inverse.m12()*y+inverse.m22()*z+inverse.m32();
                    int cell = (int)Math.floor(localX * 9) * 734287
                        ^ (int)Math.floor(localY * 9) * 912931 ^ (int)Math.floor(localZ * 9) * 438289;
                    float noise = (cell & 255) / 255f;
                    return (localY - cutoff + (noise - .5f) * feather) / feather;
                };
                var spectral = new SoulSubmitNodes(nodes, SoulVisuals.appearance(echo.state, alpha, fade));
                renderingEcho = true;
                dispatcher.submit(echo.state, level.cameraRenderState, 0, 0, 0, pose, spectral);
            } catch (RuntimeException error) {
                echo.renderFailed = true;
                org.apache.logging.log4j.LogManager.getLogger().warn("Unable to submit departing soul {}", echo.state.entityType, error);
            } finally {
                renderingEcho = false;
                pose.popPose();
            }
        }
    }

    private SoulDissolves() {}
}

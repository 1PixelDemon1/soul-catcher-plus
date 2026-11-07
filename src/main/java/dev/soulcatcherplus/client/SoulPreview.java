// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import net.minecraft.client.renderer.RenderHelper;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.util.math.vector.Quaternion;
import net.minecraft.util.math.vector.Matrix4f;
import dev.soulcatcherplus.network.SoulNetwork;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.MobEntity;
import net.minecraftforge.registries.ForgeRegistries;

/** Detached visual entities: never inserted into a level and never AI-ticked. */
final class SoulPreview {
    private final boolean spectral;
    private final Map<UUID, MobEntity> cache = new LinkedHashMap<>();
    private final Set<UUID> failed = new HashSet<>();
    private UUID requested;
    private int requestTick;
    private float rotation = 155;
    private float zoom = 1;
    private boolean manuallyRotated;

    SoulPreview() { this(false); }
    SoulPreview(boolean spectral) { this.spectral = spectral; }

    void request(UUID entry, int tick) {
        if (entry == null || cache.containsKey(entry) || failed.contains(entry)) return;
        if (entry.equals(requested) && tick - requestTick < 40) return;
        requested = entry;
        requestTick = tick;
        SoulNetwork.CHANNEL.sendToServer(new SoulNetwork.PreviewRequest(entry));
    }
    void receive(SoulNetwork.Preview packet) {
        var client = Minecraft.getInstance();
        if (client.level == null) return;
        try {
            var type = ForgeRegistries.ENTITIES.getValue(new ResourceLocation(packet.entityType()));
            if (type == null || !(type.create(client.level) instanceof MobEntity mob)) {
                failed.add(packet.entry()); return;
            }
            mob.load(packet.appearance().copy());
            // A detached preview never joins a level or runs entity ticks. The marker
            // lets it use the exact same dispatcher tint/provider as a summoned soul.
            if (spectral) mob.getPersistentData().putUUID(dev.soulcatcherplus.companion.CompanionBrain.OWNER, new UUID(0, 0));
            mob.setNoAi(true);
            mob.setHealth(mob.getMaxHealth());
            mob.setPos(0, 0, 0);
            // Captured poses should not make the model face away from the viewer.
            mob.yRot = 0; mob.yRotO = 0;
            mob.yBodyRot = mob.yBodyRotO = mob.yHeadRot = mob.yHeadRotO = 0;
            mob.xRot = 0;
            cache.put(packet.entry(), mob);
            while (cache.size() > 12) cache.remove(cache.keySet().iterator().next());
        } catch (RuntimeException error) {
            failed.add(packet.entry());
            org.apache.logging.log4j.LogManager.getLogger().warn("Unable to create soul preview: {}", packet.entityType(), error);
        }
    }
    void tick() {
        if (!manuallyRotated) rotation += 0.35f;
        // Advance cosmetic age only; calling Entity.tick() could run third-party side effects.
        cache.values().forEach(mob -> mob.tickCount++);
    }
    void turn(double delta) { rotation += (float) delta * 0.8f; manuallyRotated = true; }
    void zoom(double delta) { zoom = net.minecraft.util.math.MathHelper.clamp(zoom + (float) delta * 0.08f, 0.65f, 1.45f); }
    void resetView() { rotation = 155; zoom = 1; manuallyRotated = false; }
    boolean failed(UUID entry) { return failed.contains(entry); }
    boolean available(UUID entry) { return cache.containsKey(entry) && !failed.contains(entry); }
    void clear() { cache.clear(); failed.clear(); requested = null; }

    void render(SoulGui graphics, UUID entry, int centerX, int baseline, int width, int height, float partialTick) {
        MobEntity mob = cache.get(entry);
        if (mob == null || failed.contains(entry)) return;
        var client = Minecraft.getInstance();
        var dispatcher = client.getEntityRenderDispatcher();
        Quaternion previousCamera = dispatcher.cameraOrientation().copy();
        MatrixStack pose = new MatrixStack();
        pose.last().pose().multiply(graphics.pose().last().pose());
        pose.last().normal().load(graphics.pose().last().normal());
        // Reserve visible space around the model, including protruding weapons and horns.
        // Width/depth jointly bound the footprint while it rotates.
        float usableWidth = width * 0.66f;
        float usableHeight = height * 0.70f;
        float footprint = Math.max(1.0f, mob.getBbWidth() * 1.65f);
        float scale = Math.min(usableWidth / footprint, usableHeight / Math.max(0.75f, mob.getBbHeight()));
        scale = Math.max(0.5f, Math.min(58, scale)) * zoom;
        pose.translate(centerX, baseline, 60);
        pose.last().pose().multiply(Matrix4f.createScaleMatrix(scale, scale, -scale));
        pose.mulPose(Vector3f.ZP.rotationDegrees(180));
        pose.mulPose(Vector3f.YP.rotationDegrees(rotation));
        RenderHelper.turnBackOn();
        dispatcher.setRenderShadow(false);
        dispatcher.overrideCameraOrientation(Quaternion.ONE.copy());
        try {
            RenderSystem.runAsFancy(() -> dispatcher.render(mob, 0, 0, 0, 0, partialTick,
                pose, graphics.bufferSource(), 15728880));
            graphics.flush();
        } catch (RuntimeException error) {
            failed.add(entry);
            org.apache.logging.log4j.LogManager.getLogger().warn("Unable to render soul preview: {}", mob.getType(), error);
        } finally {
            dispatcher.overrideCameraOrientation(previousCamera);
            dispatcher.setRenderShadow(true);
            RenderHelper.setupFor3DItems();
            // Flush the detached model before the screen draws its remaining panels.
            graphics.flush();
        }
    }
}

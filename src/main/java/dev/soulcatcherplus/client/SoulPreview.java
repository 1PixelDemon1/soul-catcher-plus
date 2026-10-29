// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.soulcatcherplus.network.SoulNetwork;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.core.registries.BuiltInRegistries;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Detached visual entities: never inserted into a level and never AI-ticked. */
final class SoulPreview {
    private final Map<UUID, Mob> cache = new LinkedHashMap<>();
    private final Set<UUID> failed = new HashSet<>();
    private UUID requested;
    private int requestTick;
    private float rotation = 155;
    private float zoom = 1;
    private boolean manuallyRotated;

    void request(UUID entry, int tick) {
        if (entry == null || cache.containsKey(entry) || failed.contains(entry)) return;
        if (entry.equals(requested) && tick - requestTick < 40) return;
        requested = entry;
        requestTick = tick;
        SoulNetwork.sendToServer(new SoulNetwork.PreviewRequest(entry));
    }
    void receive(SoulNetwork.Preview packet) {
        var client = Minecraft.getInstance();
        if (client.level == null) return;
        try {
            var type = BuiltInRegistries.ENTITY_TYPE.getOptional(new ResourceLocation(packet.entityType())).orElse(null);
            if (type == null || !(type.create(client.level) instanceof Mob mob)) {
                failed.add(packet.entry()); return;
            }
            mob.load(packet.appearance().copy());
            mob.setNoAi(true);
            mob.setHealth(mob.getMaxHealth());
            mob.setPos(0, 0, 0);
            // Captured poses should not make the model face away from the viewer.
            mob.setYRot(0); mob.yRotO = 0;
            mob.yBodyRot = mob.yBodyRotO = mob.yHeadRot = mob.yHeadRotO = 0;
            mob.setXRot(0);
            cache.put(packet.entry(), mob);
            while (cache.size() > 12) cache.remove(cache.keySet().iterator().next());
        } catch (RuntimeException error) {
            failed.add(packet.entry());
            com.mojang.logging.LogUtils.getLogger().warn("Unable to create soul preview: {}", packet.entityType(), error);
        }
    }
    void tick() {
        if (!manuallyRotated) rotation += 0.35f;
        // Advance cosmetic age only; calling Entity.tick() could run third-party side effects.
        cache.values().forEach(mob -> mob.tickCount++);
    }
    void turn(double delta) { rotation += (float) delta * 0.8f; manuallyRotated = true; }
    void zoom(double delta) { zoom = net.minecraft.util.Mth.clamp(zoom + (float) delta * 0.08f, 0.65f, 1.45f); }
    void resetView() { rotation = 155; zoom = 1; manuallyRotated = false; }
    boolean failed(UUID entry) { return failed.contains(entry); }
    boolean available(UUID entry) { return cache.containsKey(entry) && !failed.contains(entry); }
    void clear() { cache.clear(); failed.clear(); requested = null; }

    void render(GuiGraphics graphics, UUID entry, int centerX, int baseline, int width, int height, float partialTick) {
        Mob mob = cache.get(entry);
        if (mob == null || failed.contains(entry)) return;
        var client = Minecraft.getInstance();
        var dispatcher = client.getEntityRenderDispatcher();
        Quaternionf previousCamera = new Quaternionf(dispatcher.cameraOrientation());
        PoseStack pose = new PoseStack();
        pose.last().pose().set(graphics.pose().last().pose());
        pose.last().normal().set(graphics.pose().last().normal());
        // Reserve visible space around the model, including protruding weapons and horns.
        // Width/depth jointly bound the footprint while it rotates.
        float usableWidth = width * 0.66f;
        float usableHeight = height * 0.70f;
        float footprint = Math.max(1.0f, mob.getBbWidth() * 1.65f);
        float scale = Math.min(usableWidth / footprint, usableHeight / Math.max(0.75f, mob.getBbHeight()));
        scale = Math.max(0.5f, Math.min(58, scale)) * zoom;
        pose.translate(centerX, baseline, 60);
        pose.mulPoseMatrix(new Matrix4f().scaling(scale, scale, -scale));
        pose.mulPose(Axis.ZP.rotationDegrees(180));
        pose.mulPose(Axis.YP.rotationDegrees(rotation));
        Lighting.setupForEntityInInventory();
        dispatcher.setRenderShadow(false);
        dispatcher.overrideCameraOrientation(new Quaternionf());
        try {
            RenderSystem.runAsFancy(() -> dispatcher.render(mob, 0, 0, 0, 0, partialTick,
                pose, graphics.bufferSource(), LightTexture.FULL_BRIGHT));
            graphics.flush();
        } catch (RuntimeException error) {
            failed.add(entry);
            com.mojang.logging.LogUtils.getLogger().warn("Unable to render soul preview: {}", mob.getType(), error);
        } finally {
            dispatcher.overrideCameraOrientation(previousCamera);
            dispatcher.setRenderShadow(true);
            Lighting.setupFor3DItems();
            // Flush the detached model before the screen draws its remaining panels.
            graphics.flush();
        }
    }
}

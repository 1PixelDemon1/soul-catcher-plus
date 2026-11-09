// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin.client;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.soulcatcherplus.client.SoulDissolves;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LevelRenderer.class)
public abstract class SoulDissolveRenderMixin {
    @Inject(method="submitEntities",at=@At("TAIL"))
    private void soulcatcherplus$echoes(PoseStack pose,LevelRenderState level,SubmitNodeCollector nodes,CallbackInfo callback) {
        SoulDissolves.submit(pose,level,nodes);
    }
}

// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin.client;
import java.util.UUID;
import dev.soulcatcherplus.client.SoulRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
@Mixin(EntityRenderState.class)
public abstract class SoulRenderStateMixin implements SoulRenderState {
    @Unique private UUID soulcatcherplus$uuid;
    @Unique private boolean soulcatcherplus$soul;
    public UUID soulcatcherplus$uuid() { return soulcatcherplus$uuid; }
    public boolean soulcatcherplus$isSoul() { return soulcatcherplus$soul; }
    public void soulcatcherplus$capture(UUID uuid, boolean soul) { soulcatcherplus$uuid=uuid; soulcatcherplus$soul=soul; }
}

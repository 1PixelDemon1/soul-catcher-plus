// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin;

import dev.soulcatcherplus.entity.RavenItemDelivery;
import net.minecraft.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemEntity.class, remap = false)
public abstract class RavenDeliveredItemMixin {
    // Entity ForgeData is not copied by native stack merging: keep the pickup guard attached.
    // Both names verified against the Forge 1.16.5 mapped JAR and SRG mapping.
    @Inject(method = {"isMergable()Z", "func_213857_z()Z"}, at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void soulcatcherplus$keepDeliveredLootSeparate(CallbackInfoReturnable<Boolean> callback) {
        if (RavenItemDelivery.isProtected((ItemEntity)(Object)this)) callback.setReturnValue(false);
    }
}

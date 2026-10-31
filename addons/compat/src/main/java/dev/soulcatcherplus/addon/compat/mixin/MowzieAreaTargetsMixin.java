// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.addon.compat.mixin;

import dev.soulcatcherplus.companion.CompanionBrain;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Filter before attack code applies damage, shield wear, effects, or velocity. */
@Pseudo
@Mixin(targets = "com.bobmowzie.mowziesmobs.server.entity.MowzieEntity", remap = false)
public abstract class MowzieAreaTargetsMixin {
    @Inject(method = {
        "getEntityLivingBaseNearby(DDDD)Ljava/util/List;",
        "getAttackableEntityLivingBaseNearby(DDDD)Ljava/util/List;",
        "getEntitiesNearby(Ljava/lang/Class;D)Ljava/util/List;",
        "getEntitiesNearby(Ljava/lang/Class;DDDD)Ljava/util/List;"
    }, at = @At("RETURN"), cancellable = true, remap = false, require = 1)
    private void soulcatcherplus$excludeAllies(CallbackInfoReturnable<List<Entity>> callback) {
        if ((Object) this instanceof Mob mob && CompanionBrain.isSoul(mob))
            callback.setReturnValue(callback.getReturnValue().stream()
                .filter(target -> !CompanionBrain.allied(mob, target)).collect(java.util.stream.Collectors.toList()));
    }
}

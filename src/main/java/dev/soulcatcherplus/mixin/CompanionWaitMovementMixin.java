// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin;

import dev.soulcatcherplus.companion.CompanionBrain;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.MoverType;
import net.minecraft.util.math.vector.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Native flight controllers may keep applying thrust after navigation has been stopped. */
@Mixin(value = Entity.class, remap = false)
public abstract class CompanionWaitMovementMixin {
    @ModifyVariable(method = {
        "move(Lnet/minecraft/entity/MoverType;Lnet/minecraft/util/math/vector/Vector3d;)V",
        "func_213315_a(Lnet/minecraft/entity/MoverType;Lnet/minecraft/util/math/vector/Vector3d;)V"
    }, at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private Vector3d soulcatcherplus$holdFlight(Vector3d motion, MoverType type, Vector3d originalMotion) {
        if (type != MoverType.SELF || !((Object) this instanceof MobEntity mob) || mob.level.isClientSide
                || !CompanionBrain.holdsFlightPosition(mob)) return motion;
        mob.setDeltaMovement(Vector3d.ZERO);
        return Vector3d.ZERO;
    }
}

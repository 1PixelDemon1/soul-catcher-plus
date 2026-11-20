// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin;

import dev.soulcatcherplus.companion.CompanionBrain;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Native flight controllers may keep applying thrust after navigation has been stopped. */
@Mixin(value = Entity.class, remap = false)
public abstract class CompanionWaitMovementMixin {
    @ModifyVariable(method = {
        "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
        "m_6478_(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"
    }, at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private Vec3 soulcatcherplus$holdFlight(Vec3 motion, MoverType type, Vec3 originalMotion) {
        if (type != MoverType.SELF || !((Object) this instanceof Mob mob) || mob.level().isClientSide
                || !CompanionBrain.holdsFlightPosition(mob)) return motion;
        mob.setDeltaMovement(Vec3.ZERO);
        return Vec3.ZERO;
    }
}

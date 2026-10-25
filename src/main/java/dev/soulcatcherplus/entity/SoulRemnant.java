// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.entity;

import dev.soulcatcherplus.companion.SoulService;
import dev.soulcatcherplus.data.*;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

public final class SoulRemnant extends Entity {
    private UUID owner;
    private SoulEntry soul;
    private long expires;
    public SoulRemnant(EntityType<? extends SoulRemnant> type, Level level) { super(type, level); }
    public void initialize(ServerPlayer player, SoulEntry soul) {
        this.owner = player.getUUID(); this.soul = soul;
        expires = player.server.overworld().getGameTime() + 20 * 60;
    }
    @Override protected void defineSynchedData() {}
    @Override public boolean isPickable() { return true; }
    @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) { return true; }
    @Override public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server) {
            if (owner == null || soul == null || server.getServer().overworld().getGameTime() >= expires) { discard(); return; }
            if (tickCount % 5 == 0) server.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.4, getZ(), 2, 0.15, 0.2, 0.15, 0.01);
        }
    }
    @Override public InteractionResult interact(Player player, InteractionHand hand) {
        if (!player.getItemInHand(hand).is(dev.soulcatcherplus.SoulCatcherPlus.SCYTHE.get())) return InteractionResult.PASS;
        if (player instanceof ServerPlayer server && server.getUUID().equals(owner) && soul != null
                && player.distanceToSqr(this) <= 6 * 6) {
            PlayerSouls data = SoulService.data(server);
            boolean known = data.entries.values().stream().anyMatch(e -> SoulVariants.same(e, soul));
            if (!known) {
                if (data.entries.size() >= 4096) {
                    SoulService.message(server, "collection_full");
                    return InteractionResult.FAIL;
                }
                data.entries.put(soul.id, soul);
                data.selected = soul.id;
                SoulService.message(server, "captured", soul.name);
            } else {
                data.energy = (int) Math.min(Integer.MAX_VALUE, (long) data.energy + Math.max(5, soul.cost / 2));
                SoulService.message(server, "duplicate");
            }
            SoulService.changed(server);
            discard();
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) owner = tag.getUUID("Owner");
        if (tag.contains("Soul")) soul = SoulEntry.load(tag.getCompound("Soul"));
        expires = tag.getLong("Expires");
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        if (owner != null) tag.putUUID("Owner", owner);
        if (soul != null) tag.put("Soul", soul.save());
        tag.putLong("Expires", expires);
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}

// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.entity;

import dev.soulcatcherplus.companion.SoulService;
import dev.soulcatcherplus.companion.SoulCages;
import dev.soulcatcherplus.data.*;
import dev.soulcatcherplus.advancement.SoulAdvancements;
import java.util.UUID;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.network.IPacket;
import net.minecraft.client.network.play.IClientPlayNetHandler;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkHooks;

public final class SoulRemnant extends Entity {
    private static final DataParameter<String> APPEARANCE =
        EntityDataManager.defineId(SoulRemnant.class, DataSerializers.STRING);

    public enum Appearance {
        SMALL("small"), MEDIUM("medium"), LARGE("large"), SKULL("skull");
        private final String id;
        Appearance(String id) { this.id = id; }
        public String id() { return id; }
        private static Appearance forHealth(double maxHealth) {
            if (SoulBalance.isBossSoul(maxHealth)) return SKULL;
            int energy = SoulBalance.victoryEnergy(maxHealth);
            return energy < 20 ? SMALL : energy <= 40 ? MEDIUM : LARGE;
        }
        private static Appearance byId(String id) {
            for (Appearance appearance : values()) if (appearance.id.equals(id)) return appearance;
            return SMALL;
        }
    }

    private UUID owner;
    private SoulEntry soul;
    private long expires;
    public SoulRemnant(EntityType<? extends SoulRemnant> type, World level) { super(type, level); }
    public void initialize(ServerPlayerEntity player, SoulEntry soul) {
        this.owner = player.getUUID(); this.soul = soul;
        entityData.set(APPEARANCE, Appearance.forHealth(soul.maxHealth).id());
        expires = player.server.overworld().getGameTime() + 20 * 60;
    }
    public Appearance appearance() { return Appearance.byId(entityData.get(APPEARANCE)); }
    @Override protected void defineSynchedData() { entityData.define(APPEARANCE, Appearance.SMALL.id()); }
    @Override public boolean isPickable() { return true; }
    @Override public boolean isInvulnerableTo(net.minecraft.util.DamageSource source) { return true; }
    @Override public void tick() {
        super.tick();
        if (level instanceof ServerWorld server) {
            if (owner == null || soul == null || server.getServer().overworld().getGameTime() >= expires) { remove(); return; }
            if (tickCount % 5 == 0) server.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.4, getZ(), 2, 0.15, 0.2, 0.15, 0.01);
        }
    }
    @Override public ActionResultType interact(PlayerEntity player, Hand hand) {
        if (!(player.getItemInHand(hand).getItem() instanceof dev.soulcatcherplus.item.SoulScythe)) return ActionResultType.PASS;
        if (player instanceof ServerPlayerEntity server && server.getUUID().equals(owner) && soul != null
                && player.distanceToSqr(this) <= 6 * 6) {
            if (!SoulCages.active(server).present()) {
                SoulService.message(server, "cage_needed");
                return ActionResultType.FAIL;
            }
            PlayerSouls data = SoulService.data(server);
            boolean known = data.entries.values().stream().anyMatch(e -> SoulVariants.same(e, soul));
            if (!known) {
                if (data.entries.size() >= PlayerSouls.MAX_ENTRIES) {
                    SoulService.message(server, "collection_full");
                    return ActionResultType.FAIL;
                }
                data.entries.put(soul.id, soul);
                data.selected = soul.id;
                SoulService.message(server, "captured", soul.name);
            } else {
                // Victory already awarded HP / 2. A known appearance must not pay a second time.
                SoulService.message(server, "duplicate");
            }
            SoulService.changed(server);
            SoulAdvancements.captured(server, soul.maxHealth);
            remove();
        }
        return ActionResultType.sidedSuccess(level.isClientSide);
    }
    @Override protected void readAdditionalSaveData(CompoundNBT tag) {
        if (tag.hasUUID("Owner")) owner = tag.getUUID("Owner");
        if (tag.contains("Soul")) soul = SoulEntry.load(tag.getCompound("Soul"));
        // Derive the visual from saved health, including remnants created before these textures existed.
        entityData.set(APPEARANCE, (soul == null ? Appearance.SMALL : Appearance.forHealth(soul.maxHealth)).id());
        expires = tag.getLong("Expires");
    }
    @Override protected void addAdditionalSaveData(CompoundNBT tag) {
        if (owner != null) tag.putUUID("Owner", owner);
        if (soul != null) tag.put("Soul", soul.save());
        tag.putLong("Expires", expires);
    }
    @Override public IPacket<?> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}

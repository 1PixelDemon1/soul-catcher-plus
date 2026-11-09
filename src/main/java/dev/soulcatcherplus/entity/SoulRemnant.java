// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.entity;

import dev.soulcatcherplus.companion.SoulService;
import dev.soulcatcherplus.companion.SoulCages;
import dev.soulcatcherplus.data.*;
import dev.soulcatcherplus.advancement.SoulAdvancements;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class SoulRemnant extends Entity {
    private static final EntityDataAccessor<String> APPEARANCE =
        SynchedEntityData.defineId(SoulRemnant.class, EntityDataSerializers.STRING);

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
    public SoulRemnant(EntityType<? extends SoulRemnant> type, Level level) { super(type, level); }
    public void initialize(ServerPlayer player, SoulEntry soul) {
        this.owner = player.getUUID(); this.soul = soul;
        entityData.set(APPEARANCE, Appearance.forHealth(soul.maxHealth).id());
        expires = player.level().getServer().overworld().getGameTime() + 20 * 60;
    }
    public Appearance appearance() { return Appearance.byId(entityData.get(APPEARANCE)); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(APPEARANCE, Appearance.SMALL.id()); }
    @Override public boolean isPickable() { return true; }
    @Override public boolean hurtServer(ServerLevel world, net.minecraft.world.damagesource.DamageSource source, float amount) { return false; }
    @Override public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server) {
            if (owner == null || soul == null || server.getServer().overworld().getGameTime() >= expires) { discard(); return; }
            if (tickCount % 5 == 0) server.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.4, getZ(), 2, 0.15, 0.2, 0.15, 0.01);
        }
    }
    @Override public InteractionResult interact(Player player, InteractionHand hand, net.minecraft.world.phys.Vec3 hit) {
        if (!(player.getItemInHand(hand).getItem() instanceof dev.soulcatcherplus.item.SoulScythe)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer server && availableTo(server) && !collect(server)) return InteractionResult.FAIL;
        return InteractionResult.SUCCESS;
    }

    private boolean availableTo(ServerPlayer player) {
        return isAlive() && soul != null && player.isAlive() && !player.isSpectator()
            && player.level() == level() && player.getUUID().equals(owner)
            && player.level().getServer().overworld().getGameTime() < expires;
    }

    private boolean knownBy(PlayerSouls data) {
        return data.entries.values().stream().anyMatch(entry -> SoulVariants.same(entry, soul));
    }

    /** A silent eligibility check for collectors; no scythe is required for raven delivery. */
    public boolean canCollect(ServerPlayer player) {
        if (!availableTo(player) || !SoulCages.active(player).present()) return false;
        PlayerSouls data = SoulService.data(player);
        return data.entries.size() < PlayerSouls.MAX_ENTRIES || knownBy(data);
    }

    /** Shared, server-authoritative collection path for right-click and physical raven delivery. */
    public boolean collect(ServerPlayer player) {
        if (!availableTo(player) || player.distanceToSqr(this) > 6 * 6) return false;
        if (!SoulCages.active(player).present()) { SoulService.message(player, "cage_needed"); return false; }
        PlayerSouls data = SoulService.data(player);
        boolean known = knownBy(data);
        if (!known) {
            if (data.entries.size() >= PlayerSouls.MAX_ENTRIES) {
                SoulService.message(player, "collection_full");
                return false;
            }
            data.entries.put(soul.id, soul);
            data.selected = soul.id;
            SoulService.message(player, "captured", soul.name);
        } else {
            // Victory already awarded HP / 2. A known appearance must not pay a second time.
            SoulService.message(player, "duplicate");
        }
        SoulService.changed(player);
        SoulAdvancements.captured(player, soul.maxHealth);
        discard();
        return true;
    }
    @Override protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput tag) {
        if (dev.soulcatcherplus.data.SoulNbt.hasUuid(tag, "Owner")) owner = dev.soulcatcherplus.data.SoulNbt.uuid(tag, "Owner");
        tag.read("Soul",CompoundTag.CODEC).ifPresent(data -> soul=SoulEntry.load(data));
        // Derive the visual from saved health, including remnants created before these textures existed.
        entityData.set(APPEARANCE, (soul == null ? Appearance.SMALL : Appearance.forHealth(soul.maxHealth)).id());
        expires = tag.getLongOr("Expires", 0L);
    }
    @Override protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput tag) {
        if (owner != null) dev.soulcatcherplus.data.SoulNbt.putUuid(tag, "Owner", owner);
        if (soul != null) tag.store("Soul", CompoundTag.CODEC, soul.save());
        tag.putLong("Expires", expires);
    }
}

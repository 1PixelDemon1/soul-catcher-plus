// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.pylon;

import net.minecraft.core.BlockPos;

import dev.soulcatcherplus.data.SoulEntry;
import dev.soulcatcherplus.data.SoulLedger;
import dev.soulcatcherplus.item.SoulCage;
import dev.soulcatcherplus.item.SoulScroll;
import java.util.UUID;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.level.ServerLevel;

/** Two real saved item stacks; the world ledger owns the active guardian reservation. */
public final class SoulPylonTile extends BlockEntity implements WorldlyContainer, MenuProvider {
    private static final int[] NO_AUTOMATION_SLOTS = {};
    private ItemStack cage = ItemStack.EMPTY, scroll = ItemStack.EMPTY;
    private UUID owner, bondId;
    private int guardianId = -1;
    private boolean summoning, dismantling, editing;

    public SoulPylonTile(BlockPos pos, BlockState state) { super(SoulPylons.TILE.get(), pos, state); }
    public ItemStack cage() { return cage; }
    public ItemStack scroll() { return scroll; }
    public int guardianId() { return guardianId; }
    public boolean loaded() { return !cage.isEmpty() && !scroll.isEmpty(); }
    public void setOwner(UUID owner) { this.owner = owner; changed(); }
    public boolean canEdit() { return !summoning && !dismantling && !editing && !isRemoved(); }
    boolean beginSummon() {
        if (!canEdit()) return false;
        summoning = true;
        return true;
    }
    void endSummon() { summoning = false; changed(); }

    public boolean matches(PylonBond bond) {
        return bond != null && bond.soul.id.equals(bondId) && bond.owner.equals(owner)
            && bond.position.equals(getBlockPos()) && level != null
            && bond.dimension.equals(level.dimension().identifier().toString());
    }
    boolean current(PylonBond bond) {
        return !isRemoved() && matches(bond) && level instanceof ServerLevel world
            && world.hasChunkAt(getBlockPos()) && world.getBlockEntity(getBlockPos()) == this
            && SoulLedger.get(world.getServer()).pylon(bond.soul.id) == bond;
    }

    void creditVictory(PylonBond bond, int amount) {
        if (amount <= 0 || !canEdit() || !loaded() || !current(bond)) return;
        int stored = SoulCage.energy(cage);
        int accepted = Math.min(amount, SoulCage.capacity(cage) - stored);
        if (accepted <= 0) return;
        SoulCage.setEnergy(cage, stored + accepted);
        changed();
    }
    private PylonBond currentBond() {
        if (bondId == null || !(level instanceof ServerLevel world)) return null;
        PylonBond bond = SoulLedger.get(world.getServer()).pylon(bondId);
        return matches(bond) ? bond : null;
    }

    public void open(ServerPlayer player) {
        if (!canEdit()) return;
        if (owner == null) setOwner(player.getUUID());
        player.openMenu(this, getBlockPos());
    }
    @Override public Component getDisplayName() { return Component.translatable("block.soulcatcherplus.soul_pylon"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new SoulPylonContainer(id, inventory, this);
    }
    @Override public int getContainerSize() { return 2; }
    // Hopper extraction can roll back after removeItem; that must not recall/recharge a guardian.
    // Item automation needs its own transactional contract; these slots are edited through the GUI.
    @Override public int[] getSlotsForFace(Direction direction) { return NO_AUTOMATION_SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction direction) { return false; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) { return false; }
    @Override public int getMaxStackSize() { return 1; }
    @Override public boolean isEmpty() { return cage.isEmpty() && scroll.isEmpty(); }
    @Override public ItemStack getItem(int slot) { return slot == 0 ? cage : slot == 1 ? scroll : ItemStack.EMPTY; }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (!canEdit()) return false;
        if (slot == 0) return stack.getItem() instanceof SoulCage;
        if (slot != 1 || !(stack.getItem() instanceof SoulScroll soulScroll) || !(level instanceof ServerLevel world)) return false;
        SoulEntry entry = soulScroll.readSoul(stack, world);
        return entry != null && PylonSouls.supportsGuard(entry, world);
    }
    @Override public void setItem(int slot, ItemStack stack) {
        if (!canEdit() || slot < 0 || slot >= 2) return;
        if (!stack.isEmpty() && !(slot == 0 ? stack.getItem() instanceof SoulCage : stack.getItem() instanceof SoulScroll)) return;
        ItemStack installed = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        if (!installed.isEmpty()) installed.setCount(1);
        if (slot == 0) cage = installed;
        else scroll = installed;
        contentsChanged();
    }
    @Override public ItemStack removeItem(int slot, int count) {
        if (!canEdit() || count <= 0) return ItemStack.EMPTY;
        ItemStack stored = getItem(slot);
        if (stored.isEmpty()) return ItemStack.EMPTY;
        ItemStack removed = stored.split(count);
        if (stored.isEmpty()) {
            if (slot == 0) cage = ItemStack.EMPTY;
            else scroll = ItemStack.EMPTY;
        }
        contentsChanged();
        return removed;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return removeItem(slot, 1); }
    @Override public void clearContent() {
        if (!canEdit()) return;
        cage = ItemStack.EMPTY;
        scroll = ItemStack.EMPTY;
        contentsChanged();
    }
    @Override public boolean stillValid(Player player) {
        return !dismantling && !isRemoved() && level != null && player.level() == level
            && player.distanceToSqr(getBlockPos().getX() + .5, getBlockPos().getY() + .5, getBlockPos().getZ() + .5) <= 64
            && level.hasChunkAt(getBlockPos()) && level.getBlockEntity(getBlockPos()) == this;
    }
    private void contentsChanged() {
        // Detach/replace the stack first; callbacks cannot reopen the inventory or summon during cleanup.
        editing = true;
        try { invalidateGuardian(); }
        finally { editing = false; changed(); }
        bind();
    }

    private void bind() {
        if (!loaded() || owner == null || currentBond() != null || !(level instanceof ServerLevel world)) return;
        SoulEntry entry = ((SoulScroll) scroll.getItem()).readSoul(scroll, world);
        if (entry == null || !PylonSouls.supportsGuard(entry, world)) return;
        bondId = entry.id;
        SoulLedger.get(world.getServer()).putPylon(new PylonBond(owner,
            world.dimension().identifier().toString(), getBlockPos(), entry));
        changed();
    }

    public void tick() {
        if (!(level instanceof ServerLevel world)
                || !canEdit() || Math.floorMod(world.getGameTime() + getBlockPos().asLong(), 20) != 0) return;
        if (!loaded()) return;
        if (currentBond() == null) bind();
        PylonBond bond = currentBond();
        if (bond == null) return;
        if (bond.soul.activeEntity == null && world.getServer().overworld().getGameTime() >= bond.soul.readyAt)
            PylonSouls.summon(this, bond);
        // A missing UUID may belong to an unloaded chunk. Do not replace or charge it again.
        Entity guardian = bond.soul.activeEntity == null ? null : world.getEntity(bond.soul.activeEntity);
        int nextId = guardian != null && guardian.isAlive() ? guardian.getId() : -1;
        if (nextId != guardianId) { guardianId = nextId; changed(); }
    }

    private void invalidateGuardian() {
        PylonBond bond = currentBond();
        bondId = null;
        guardianId = -1;
        if (bond != null) PylonSouls.dismiss((ServerLevel) level, bond);
    }

    public void breakPylon() {
        dismantling = true;
        ItemStack removedCage = cage, removedScroll = scroll;
        cage = ItemStack.EMPTY;
        scroll = ItemStack.EMPTY;
        invalidateGuardian();
        Block.popResource(level, getBlockPos(), removedCage);
        Block.popResource(level, getBlockPos(), removedScroll);
        setChanged();
    }

    public void changed() {
        setChanged();
        if (level != null && !level.isClientSide())
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }

    @Override protected void saveAdditional(ValueOutput tag) {
        super.saveAdditional(tag);
        tag.store("Cage", ItemStack.OPTIONAL_CODEC, cage);
        tag.store("Scroll", ItemStack.OPTIONAL_CODEC, scroll);
        if (owner != null) dev.soulcatcherplus.data.SoulNbt.putUuid(tag, "Owner", owner);
        if (bondId != null) dev.soulcatcherplus.data.SoulNbt.putUuid(tag, "Bond", bondId);
    }

    @Override protected void loadAdditional(ValueInput tag) {
        super.loadAdditional(tag);
        cage = tag.read("Cage", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        scroll = tag.read("Scroll", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        if (!(cage.getItem() instanceof SoulCage)) cage = ItemStack.EMPTY;
        if (!(scroll.getItem() instanceof SoulScroll)) scroll = ItemStack.EMPTY;
        owner = dev.soulcatcherplus.data.SoulNbt.hasUuid(tag, "Owner") ? dev.soulcatcherplus.data.SoulNbt.uuid(tag, "Owner") : null;
        bondId = dev.soulcatcherplus.data.SoulNbt.hasUuid(tag, "Bond") ? dev.soulcatcherplus.data.SoulNbt.uuid(tag, "Bond") : null;
    }

    /** Rendering needs the cage and scroll item, never the scroll's full captured-mob snapshot. */
    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        breakPylon();
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.store("Cage", ItemStack.OPTIONAL_CODEC, registries.createSerializationContext(NbtOps.INSTANCE), cage);
        if (!scroll.isEmpty()) tag.store("Scroll", ItemStack.OPTIONAL_CODEC, registries.createSerializationContext(NbtOps.INSTANCE), new ItemStack(scroll.getItem()));
        tag.putInt("Guardian", guardianId);
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onDataPacket(net.minecraft.network.Connection connection, ValueInput tag, HolderLookup.Provider holders) {
        readVisuals(tag);
    }
    @Override public void handleUpdateTag(ValueInput tag, HolderLookup.Provider holders) { readVisuals(tag); }
    private void readVisuals(ValueInput tag) {
        cage = tag.read("Cage", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        scroll = tag.read("Scroll", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        guardianId = tag.getIntOr("Guardian", 0);
    }
    @Override public AABB getRenderBoundingBox() { return new AABB(getBlockPos()).inflate(PylonSouls.RETURN_RADIUS); }
}

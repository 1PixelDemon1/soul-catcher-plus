// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.pylon;

import dev.soulcatcherplus.data.SoulEntry;
import dev.soulcatcherplus.data.SoulLedger;
import dev.soulcatcherplus.item.SoulCage;
import dev.soulcatcherplus.item.SoulScroll;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.server.ServerWorld;

/** Two real saved item stacks; the world ledger owns the active guardian reservation. */
public final class SoulPylonTile extends TileEntity implements ITickableTileEntity, ISidedInventory, INamedContainerProvider {
    private static final int[] NO_AUTOMATION_SLOTS = {};
    private ItemStack cage = ItemStack.EMPTY, scroll = ItemStack.EMPTY;
    private UUID owner, bondId;
    private int guardianId = -1;
    private boolean summoning, dismantling, editing;

    public SoulPylonTile() { super(SoulPylons.TILE.get()); }
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
            && bond.position.equals(worldPosition) && level != null
            && bond.dimension.equals(level.dimension().location().toString());
    }
    boolean current(PylonBond bond) {
        return !isRemoved() && matches(bond) && level instanceof ServerWorld world
            && world.hasChunkAt(worldPosition) && world.getBlockEntity(worldPosition) == this
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
        if (bondId == null || !(level instanceof ServerWorld world)) return null;
        PylonBond bond = SoulLedger.get(world.getServer()).pylon(bondId);
        return matches(bond) ? bond : null;
    }

    public void open(ServerPlayerEntity player) {
        if (!canEdit()) return;
        if (owner == null) setOwner(player.getUUID());
        net.minecraftforge.fml.network.NetworkHooks.openGui(player, this, worldPosition);
    }
    @Override public ITextComponent getDisplayName() { return new TranslationTextComponent("block.soulcatcherplus.soul_pylon"); }
    @Override public Container createMenu(int id, PlayerInventory inventory, PlayerEntity player) {
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
        if (slot != 1 || !(stack.getItem() instanceof SoulScroll soulScroll) || !(level instanceof ServerWorld world)) return false;
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
    @Override public boolean stillValid(PlayerEntity player) {
        return !dismantling && !isRemoved() && level != null && player.level == level
            && player.distanceToSqr(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5) <= 64
            && level.hasChunkAt(worldPosition) && level.getBlockEntity(worldPosition) == this;
    }
    private void contentsChanged() {
        // Detach/replace the stack first; callbacks cannot reopen the inventory or summon during cleanup.
        editing = true;
        try { invalidateGuardian(); }
        finally { editing = false; changed(); }
        bind();
    }

    private void bind() {
        if (!loaded() || owner == null || currentBond() != null || !(level instanceof ServerWorld world)) return;
        SoulEntry entry = ((SoulScroll) scroll.getItem()).readSoul(scroll, world);
        if (entry == null || !PylonSouls.supportsGuard(entry, world)) return;
        bondId = entry.id;
        SoulLedger.get(world.getServer()).putPylon(new PylonBond(owner,
            world.dimension().location().toString(), worldPosition, entry));
        changed();
    }

    @Override public void tick() {
        if (!(level instanceof ServerWorld world)
                || !canEdit() || Math.floorMod(world.getGameTime() + worldPosition.asLong(), 20) != 0) return;
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
        if (bond != null) PylonSouls.dismiss((ServerWorld) level, bond);
    }

    public void breakPylon() {
        dismantling = true;
        ItemStack removedCage = cage, removedScroll = scroll;
        cage = ItemStack.EMPTY;
        scroll = ItemStack.EMPTY;
        invalidateGuardian();
        Block.popResource(level, worldPosition, removedCage);
        Block.popResource(level, worldPosition, removedScroll);
        setChanged();
    }

    public void changed() {
        setChanged();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override public CompoundNBT save(CompoundNBT tag) {
        super.save(tag);
        tag.put("Cage", cage.save(new CompoundNBT()));
        tag.put("Scroll", scroll.save(new CompoundNBT()));
        if (owner != null) tag.putUUID("Owner", owner);
        if (bondId != null) tag.putUUID("Bond", bondId);
        return tag;
    }

    @Override public void load(BlockState state, CompoundNBT tag) {
        super.load(state, tag);
        cage = ItemStack.of(tag.getCompound("Cage"));
        scroll = ItemStack.of(tag.getCompound("Scroll"));
        if (!(cage.getItem() instanceof SoulCage)) cage = ItemStack.EMPTY;
        if (!(scroll.getItem() instanceof SoulScroll)) scroll = ItemStack.EMPTY;
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        bondId = tag.hasUUID("Bond") ? tag.getUUID("Bond") : null;
    }

    /** Rendering needs the cage and scroll item, never the scroll's full captured-mob snapshot. */
    @Override public CompoundNBT getUpdateTag() {
        CompoundNBT tag = super.save(new CompoundNBT());
        tag.put("Cage", cage.save(new CompoundNBT()));
        tag.put("Scroll", scroll.isEmpty() ? new CompoundNBT() : new ItemStack(scroll.getItem()).save(new CompoundNBT()));
        tag.putInt("Guardian", guardianId);
        return tag;
    }
    @Override public SUpdateTileEntityPacket getUpdatePacket() { return new SUpdateTileEntityPacket(worldPosition, 0, getUpdateTag()); }
    @Override public void handleUpdateTag(BlockState state, CompoundNBT tag) { readVisuals(tag); }
    @Override public void onDataPacket(NetworkManager network, SUpdateTileEntityPacket packet) { readVisuals(packet.getTag()); }
    private void readVisuals(CompoundNBT tag) {
        cage = ItemStack.of(tag.getCompound("Cage"));
        scroll = ItemStack.of(tag.getCompound("Scroll"));
        guardianId = tag.getInt("Guardian");
    }
    @Override public AxisAlignedBB getRenderBoundingBox() { return new AxisAlignedBB(worldPosition).inflate(PylonSouls.RETURN_RADIUS); }
}

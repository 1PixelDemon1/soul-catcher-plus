// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.item;

import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.companion.SoulService;
import dev.soulcatcherplus.data.PlayerSouls;
import dev.soulcatcherplus.data.SoulBalance;
import dev.soulcatcherplus.data.SoulEntry;
import dev.soulcatcherplus.data.SoulSnapshots;
import dev.soulcatcherplus.data.SoulVariants;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

/** Consumable collection unlock with a default soul and optional captured-form payload. */
public class SoulScroll extends Item {
    private static final String SOUL = "Soul";
    private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger();
    private final Supplier<? extends EntityType<?>> defaultSoul;

    public SoulScroll() { this(() -> EntityType.PIG, Rarity.COMMON); }

    public SoulScroll(Supplier<? extends EntityType<?>> defaultSoul, Rarity rarity) {
        this(defaultSoul, new Properties().tab(SoulCatcherPlus.SOUL_TAB).rarity(rarity));
    }

    public SoulScroll(Supplier<? extends EntityType<?>> defaultSoul, Properties properties) {
        super(properties);
        this.defaultSoul = defaultSoul;
    }

    /** Predefined creative variants need only a type, not a world or a constructed mob. */
    protected ItemStack preset(EntityType<?> type) {
        ItemStack stack = new ItemStack(this);
        CompoundTag payload = new CompoundTag();
        payload.putString("Type", ForgeRegistries.ENTITY_TYPES.getKey(type).toString());
        stack.getOrCreateTag().put(SOUL, payload);
        return stack;
    }

    /** Copies a captured form, never its collection identity, active summon or recovery timer. */
    public static ItemStack containing(SoulEntry soul) {
        Item item = SoulCatcherPlus.SOUL_SCROLL.get();
        if (ForgottenSoulScroll.contains(soul.entityType)) item = SoulCatcherPlus.FORGOTTEN_SOUL_SCROLL.get();
        else if (SoulBalance.isBossSoul(soul.maxHealth)) item = SoulCatcherPlus.BOSS_SOUL_SCROLL.get();
        ItemStack stack = new ItemStack(item);
        CompoundTag payload = new CompoundTag();
        payload.putString("Type", soul.entityType);
        payload.putString("Name", soul.name);
        payload.put("Snapshot", SoulSnapshots.sanitize(soul.snapshot()));
        stack.getOrCreateTag().put(SOUL, payload);
        return stack;
    }

    private CompoundTag payload(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(SOUL)) return stack.getTag().getCompound(SOUL);
        CompoundTag payload = new CompoundTag();
        payload.putString("Type", ForgeRegistries.ENTITY_TYPES.getKey(defaultSoul.get()).toString());
        return payload;
    }

    private static EntityType<?> type(CompoundTag payload) {
        ResourceLocation id = ResourceLocation.tryParse(payload.getString("Type"));
        return id == null || !ForgeRegistries.ENTITY_TYPES.containsKey(id) ? null : ForgeRegistries.ENTITY_TYPES.getValue(id);
    }

    private static Component soulName(CompoundTag payload, EntityType<?> type) {
        String name = payload.getString("Name");
        return name.isEmpty() ? type.getDescription() : Component.literal(name);
    }

    @Override public void appendHoverText(ItemStack stack, Level world, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag payload = payload(stack);
        EntityType<?> type = type(payload);
        if (ForgottenSoulScroll.contains(payload.getString("Type"))) tooltip.add(Component.translatable(
            "item.soulcatcherplus.forgotten_soul_scroll.category").withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable(type == null
            ? "item.soulcatcherplus.soul_scroll.invalid" : "item.soulcatcherplus.soul_scroll.contains",
            type == null ? payload.getString("Type") : soulName(payload, type)));
    }

    @Override public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (world.isClientSide) return InteractionResultHolder.success(stack);
        if (!(user instanceof ServerPlayer player)) return InteractionResultHolder.fail(stack);
        SoulEntry soul = readSoul(stack, player);
        if (soul == null) {
            SoulService.message(player, "scroll_invalid");
            return InteractionResultHolder.fail(stack);
        }
        PlayerSouls data = SoulService.data(player);
        if (data.entries.values().stream().anyMatch(existing -> SoulVariants.same(existing, soul))) {
            SoulService.message(player, "duplicate");
            return InteractionResultHolder.fail(stack);
        }
        if (data.entries.size() >= PlayerSouls.MAX_ENTRIES) {
            SoulService.message(player, "collection_full");
            return InteractionResultHolder.fail(stack);
        }
        data.entries.put(soul.id, soul);
        data.selected = soul.id;
        SoulService.changed(player);
        stack.shrink(1);
        SoulService.message(player, "captured", soul.name);
        return InteractionResultHolder.consume(stack);
    }

    /** Validate on the server using an unspawned sample; tooltip rendering never creates entities. */
    private SoulEntry readSoul(ItemStack stack, ServerPlayer player) {
        Entity sample = null;
        try {
            CompoundTag payload = payload(stack);
            EntityType<?> type = type(payload);
            if (type == null) return null;
            if (payload.contains("Snapshot") && !payload.contains("Snapshot", 10)) return null;
            sample = type.create(player.getLevel());
            if (!(sample instanceof Mob mob)) return null;
            if (payload.contains("Snapshot", 10)) mob.load(SoulSnapshots.sanitize(payload.getCompound("Snapshot")));
            return new SoulEntry(UUID.randomUUID(), ForgeRegistries.ENTITY_TYPES.getKey(type).toString(),
                soulName(payload, type).getString(), SoulSnapshots.sanitize(mob.saveWithoutId(new CompoundTag())),
                mob.getMaxHealth());
        } catch (RuntimeException error) {
            LOGGER.warn("Unable to read soul scroll", error);
            return null;
        } finally {
            if (sample != null) sample.discard();
        }
    }
}

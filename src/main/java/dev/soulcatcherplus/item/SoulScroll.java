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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

/** Consumable collection unlock with a default soul and optional captured-form payload. */
public class SoulScroll extends Item {
    private static final String SOUL = "Soul";
    private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger();
    private final Supplier<? extends EntityType<?>> defaultSoul;

    public SoulScroll() { this(() -> net.minecraft.world.entity.EntityTypes.PIG, new Properties().setId(SoulCatcherPlus.ITEMS.key("soul_scroll"))); }

    public SoulScroll(Supplier<? extends EntityType<?>> defaultSoul, Rarity rarity) {
        this(defaultSoul, new Properties().rarity(rarity));
    }

    public SoulScroll(Supplier<? extends EntityType<?>> defaultSoul, Properties properties) {
        super(properties);
        this.defaultSoul = defaultSoul;
    }

    /** Predefined creative variants need only a type, not a world or a constructed mob. */
    protected ItemStack preset(EntityType<?> type) {
        return typeStack(this, type);
    }

    private static ItemStack typeStack(Item item, EntityType<?> type) {
        ItemStack stack = new ItemStack(item);
        CompoundTag payload = new CompoundTag();
        payload.putString("Type", ForgeRegistries.ENTITY_TYPES.getKey(type).toString());
        dev.soulcatcherplus.data.SoulItemData.update(stack, tag -> tag.put(SOUL, payload));
        return stack;
    }

    /** Copies a captured form, never its collection identity, active summon or recovery timer. */
    public static ItemStack containing(SoulEntry soul) {
        ItemStack stack = new ItemStack(scrollItem(soul.entityType, soul.maxHealth));
        CompoundTag payload = new CompoundTag();
        payload.putString("Type", soul.entityType);
        payload.putString("Name", soul.name);
        payload.put("Snapshot", SoulSnapshots.sanitize(soul.snapshot()));
        dev.soulcatcherplus.data.SoulItemData.update(stack, tag -> tag.put(SOUL, payload));
        return stack;
    }

    /** A registered type's default form without a snapshot or literal name; tooltips remain localized. */
    public static ItemStack forType(EntityType<?> type, double maxHealth) {
        String id = ForgeRegistries.ENTITY_TYPES.getKey(type).toString();
        return typeStack(scrollItem(id, maxHealth), type);
    }

    private static Item scrollItem(String type, double maxHealth) {
        if (ForgottenSoulScroll.contains(type)) return SoulCatcherPlus.FORGOTTEN_SOUL_SCROLL.get();
        return SoulBalance.isBossSoul(maxHealth) ? SoulCatcherPlus.BOSS_SOUL_SCROLL.get() : SoulCatcherPlus.SOUL_SCROLL.get();
    }

    private CompoundTag payload(ItemStack stack) {
        if (!dev.soulcatcherplus.data.SoulItemData.read(stack).isEmpty() && dev.soulcatcherplus.data.SoulItemData.read(stack).contains(SOUL)) return dev.soulcatcherplus.data.SoulItemData.read(stack).getCompoundOrEmpty(SOUL);
        CompoundTag payload = new CompoundTag();
        payload.putString("Type", ForgeRegistries.ENTITY_TYPES.getKey(defaultSoul.get()).toString());
        return payload;
    }

    private static EntityType<?> type(CompoundTag payload) {
        Identifier id = Identifier.tryParse(payload.getStringOr("Type", ""));
        return id == null || !ForgeRegistries.ENTITY_TYPES.containsKey(id) ? null : ForgeRegistries.ENTITY_TYPES.getValue(id);
    }

    private static Component soulName(CompoundTag payload, EntityType<?> type) {
        String name = payload.getStringOr("Name", "");
        return name.isEmpty() ? type.getDescription() : Component.literal(name);
    }

    @Override public void appendHoverText(ItemStack stack, Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        CompoundTag payload = payload(stack);
        EntityType<?> type = type(payload);
        if (ForgottenSoulScroll.contains(payload.getStringOr("Type", ""))) tooltip.accept(Component.translatable(
            "item.soulcatcherplus.forgotten_soul_scroll.category").withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
        tooltip.accept(Component.translatable(type == null
            ? "item.soulcatcherplus.soul_scroll.invalid" : "item.soulcatcherplus.soul_scroll.contains",
            type == null ? payload.getStringOr("Type", "") : soulName(payload, type)));
    }

    @Override public InteractionResult use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (world.isClientSide()) return InteractionResult.SUCCESS;
        if (!(user instanceof ServerPlayer player)) return InteractionResult.FAIL;
        SoulEntry soul = readSoul(stack, player);
        if (soul == null) {
            SoulService.message(player, "scroll_invalid");
            return InteractionResult.FAIL;
        }
        PlayerSouls data = SoulService.data(player);
        if (data.entries.values().stream().anyMatch(existing -> SoulVariants.same(existing, soul))) {
            SoulService.message(player, "duplicate");
            return InteractionResult.FAIL;
        }
        if (data.entries.size() >= PlayerSouls.MAX_ENTRIES) {
            SoulService.message(player, "collection_full");
            return InteractionResult.FAIL;
        }
        data.entries.put(soul.id, soul);
        data.selected = soul.id;
        SoulService.changed(player);
        var name = soulName(payload(stack), type(payload(stack)));
        stack.shrink(1);
        SoulService.message(player, "captured", name);
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Validate on the server using an unspawned sample; tooltip rendering never creates entities. */
    private SoulEntry readSoul(ItemStack stack, ServerPlayer player) {
        Entity sample = null;
        try {
            CompoundTag payload = payload(stack);
            EntityType<?> type = type(payload);
            if (type == null) return null;
            if (payload.contains("Snapshot") && !(payload.get("Snapshot") instanceof CompoundTag)) return null;
            sample = type.create(player.level(), net.minecraft.world.entity.EntitySpawnReason.LOAD);
            if (!(sample instanceof Mob mob)) return null;
            if (payload.contains("Snapshot")) dev.soulcatcherplus.data.SoulNbt.restore(mob,SoulSnapshots.sanitize(payload.getCompoundOrEmpty("Snapshot")));
            if (!mob.hasCustomName() && !payload.getStringOr("Name", "").isEmpty()) mob.setCustomName(soulName(payload, type));
            return new SoulEntry(UUID.randomUUID(), ForgeRegistries.ENTITY_TYPES.getKey(type).toString(),
                soulName(payload, type).getString(), SoulSnapshots.sanitize(dev.soulcatcherplus.data.SoulNbt.snapshot(mob)),
                mob.getMaxHealth());
        } catch (RuntimeException error) {
            LOGGER.warn("Unable to read soul scroll", error);
            return null;
        } finally {
            if (sample != null) sample.discard();
        }
    }
}

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
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Rarity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
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
        CompoundNBT payload = new CompoundNBT();
        payload.putString("Type", ForgeRegistries.ENTITIES.getKey(type).toString());
        stack.getOrCreateTag().put(SOUL, payload);
        return stack;
    }

    /** Copies a captured form, never its collection identity, active summon or recovery timer. */
    public static ItemStack containing(SoulEntry soul) {
        Item item = SoulCatcherPlus.SOUL_SCROLL.get();
        if (ForgottenSoulScroll.contains(soul.entityType)) item = SoulCatcherPlus.FORGOTTEN_SOUL_SCROLL.get();
        else if (SoulBalance.isBossSoul(soul.maxHealth)) item = SoulCatcherPlus.BOSS_SOUL_SCROLL.get();
        ItemStack stack = new ItemStack(item);
        CompoundNBT payload = new CompoundNBT();
        payload.putString("Type", soul.entityType);
        payload.putString("Name", soul.name);
        payload.put("Snapshot", SoulSnapshots.sanitize(soul.snapshot()));
        stack.getOrCreateTag().put(SOUL, payload);
        return stack;
    }

    private CompoundNBT payload(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(SOUL)) return stack.getTag().getCompound(SOUL);
        CompoundNBT payload = new CompoundNBT();
        payload.putString("Type", ForgeRegistries.ENTITIES.getKey(defaultSoul.get()).toString());
        return payload;
    }

    private static EntityType<?> type(CompoundNBT payload) {
        ResourceLocation id = ResourceLocation.tryParse(payload.getString("Type"));
        return id == null || !ForgeRegistries.ENTITIES.containsKey(id) ? null : ForgeRegistries.ENTITIES.getValue(id);
    }

    private static ITextComponent soulName(CompoundNBT payload, EntityType<?> type) {
        String name = payload.getString("Name");
        return name.isEmpty() ? type.getDescription() : new StringTextComponent(name);
    }

    @Override public void appendHoverText(ItemStack stack, World world, List<ITextComponent> tooltip, ITooltipFlag flag) {
        CompoundNBT payload = payload(stack);
        EntityType<?> type = type(payload);
        if (ForgottenSoulScroll.contains(payload.getString("Type"))) tooltip.add(new TranslationTextComponent(
            "item.soulcatcherplus.forgotten_soul_scroll.category").withStyle(net.minecraft.util.text.TextFormatting.DARK_AQUA));
        tooltip.add(new TranslationTextComponent(type == null
            ? "item.soulcatcherplus.soul_scroll.invalid" : "item.soulcatcherplus.soul_scroll.contains",
            type == null ? payload.getString("Type") : soulName(payload, type)));
    }

    @Override public ActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (world.isClientSide) return ActionResult.success(stack);
        if (!(user instanceof ServerPlayerEntity player)) return ActionResult.fail(stack);
        SoulEntry soul = readSoul(stack, player);
        if (soul == null) {
            SoulService.message(player, "scroll_invalid");
            return ActionResult.fail(stack);
        }
        PlayerSouls data = SoulService.data(player);
        if (data.entries.values().stream().anyMatch(existing -> SoulVariants.same(existing, soul))) {
            SoulService.message(player, "duplicate");
            return ActionResult.fail(stack);
        }
        if (data.entries.size() >= PlayerSouls.MAX_ENTRIES) {
            SoulService.message(player, "collection_full");
            return ActionResult.fail(stack);
        }
        data.entries.put(soul.id, soul);
        data.selected = soul.id;
        SoulService.changed(player);
        stack.shrink(1);
        SoulService.message(player, "captured", soul.name);
        return ActionResult.consume(stack);
    }

    /** Validate on the server using an unspawned sample; tooltip rendering never creates entities. */
    private SoulEntry readSoul(ItemStack stack, ServerPlayerEntity player) {
        Entity sample = null;
        try {
            CompoundNBT payload = payload(stack);
            EntityType<?> type = type(payload);
            if (type == null) return null;
            if (payload.contains("Snapshot") && !payload.contains("Snapshot", 10)) return null;
            sample = type.create(player.getLevel());
            if (!(sample instanceof MobEntity mob)) return null;
            if (payload.contains("Snapshot", 10)) mob.load(SoulSnapshots.sanitize(payload.getCompound("Snapshot")));
            return new SoulEntry(UUID.randomUUID(), ForgeRegistries.ENTITIES.getKey(type).toString(),
                soulName(payload, type).getString(), SoulSnapshots.sanitize(mob.saveWithoutId(new CompoundNBT())),
                mob.getMaxHealth());
        } catch (RuntimeException error) {
            LOGGER.warn("Unable to read soul scroll", error);
            return null;
        } finally {
            if (sample != null) sample.remove();
        }
    }
}

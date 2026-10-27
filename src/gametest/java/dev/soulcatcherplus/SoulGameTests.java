// SPDX-License-Identifier: MIT
package dev.soulcatcherplus;

import dev.soulcatcherplus.api.SoulAdapters;
import dev.soulcatcherplus.companion.*;
import dev.soulcatcherplus.data.*;
import dev.soulcatcherplus.entity.SoulRemnant;
import dev.soulcatcherplus.item.SoulScythe;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(SoulCatcherPlus.ID)
@PrefixGameTestTemplate(false)
public final class SoulGameTests {
    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = new net.minecraftforge.common.util.FakePlayer(helper.getLevel(),
            new com.mojang.authlib.GameProfile(UUID.randomUUID(), "SoulTest"));
        java.util.Map<UUID, ServerPlayer> online = net.minecraftforge.fml.util.ObfuscationReflectionHelper.getPrivateValue(
            net.minecraft.server.players.PlayerList.class, player.server.getPlayerList(), "f_11197_");
        online.put(player.getUUID(), player);
        player.setGameMode(GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(new BlockPos(8, 1, 8));
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SoulCatcherPlus.NETHERITE_SCYTHE.get()));
        player.getInventory().setItem(1, new ItemStack(SoulCatcherPlus.SUPREME_CAGE.get()));
        return player;
    }
    private static SoulEntry entry(ServerPlayer player, Mob mob) {
        SoulEntry entry = new SoulEntry(UUID.randomUUID(), mob.getType().builtInRegistryHolder().key().location().toString(),
            mob.getName().getString(), SoulSnapshots.sanitize(SoulAdapters.get(mob).capture(mob)), mob.getMaxHealth());
        SoulService.data(player).entries.put(entry.id, entry);
        SoulCages.set(player, 1000);
        SoulService.changed(player);
        return entry;
    }
    @GameTest(template = "arena")
    public static void captureSummonRecall(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        var zombie = helper.spawn(EntityType.ZOMBIE, 8, 1, 10);
        zombie.setNoAi(true);
        ((SoulScythe) player.getMainHandItem().getItem()).interactLivingEntity(player.getMainHandItem(), player, zombie, InteractionHand.MAIN_HAND);
        helper.assertTrue(zombie.getPersistentData().hasUUID("scp_mark_owner"), "Scythe must mark enemy");
        zombie.hurt(player.damageSources().playerAttack(player), 1000);
        var remnants = helper.getLevel().getEntitiesOfClass(SoulRemnant.class, zombie.getBoundingBox().inflate(4));
        helper.assertTrue(remnants.size() == 1, "A marked kill must leave exactly one soul");
        remnants.get(0).interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(SoulService.data(player).entries.size() == 1, "Soul must enter collection");
        SoulEntry entry = SoulService.data(player).entries.values().iterator().next();
        SoulCages.set(player, 1000);
        SoulService.summon(player, entry.id);
        helper.assertTrue(entry.activeEntity != null, "Soul must summon successfully");
        Entity summoned = helper.getLevel().getEntity(entry.activeEntity);
        helper.assertTrue(summoned != null && summoned.getType() == EntityType.ZOMBIE, "Must restore original entity type");
        helper.assertTrue(!summoned.getUUID().equals(zombie.getUUID()), "Summon must have a new UUID");
        helper.assertTrue(SoulCages.active(player).energy() == 1000 - entry.cost, "Summon must spend energy once");
        UUID active = entry.activeEntity;
        SoulService.summon(player, entry.id);
        helper.assertTrue(entry.activeEntity.equals(active), "Cannot duplicate active soul");
        SoulService.recall(player.server, entry);
        helper.assertTrue(entry.activeEntity == null && summoned.isRemoved(), "Recall removes entity and releases entry");
        player.discard(); helper.succeed();
    }
    @GameTest(template = "arena")
    public static void foreignSoulCannotBeCollected(GameTestHelper helper) {
        ServerPlayer owner = player(helper), stranger = player(helper);
        var zombie = helper.spawn(EntityType.ZOMBIE, 8, 1, 10);
        SoulEntry entry = entry(owner, zombie);
        SoulService.data(owner).entries.clear();
        SoulRemnant remnant = SoulCatcherPlus.REMNANT.get().create(helper.getLevel());
        remnant.initialize(owner, entry); remnant.setPos(owner.position());
        helper.getLevel().addFreshEntity(remnant);
        remnant.interact(stranger, InteractionHand.MAIN_HAND);
        helper.assertTrue(SoulService.data(stranger).entries.isEmpty() && !remnant.isRemoved(), "Only owner may collect soul");
        remnant.interact(owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(SoulService.data(owner).entries.size() == 1 && remnant.isRemoved(), "Owner can collect soul");
        owner.discard(); stranger.discard(); helper.succeed();
    }
    @GameTest(template = "arena")
    public static void resourceAndCapacityChecks(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        var zombie = helper.spawn(EntityType.ZOMBIE, 8, 1, 10);
        SoulEntry entry = entry(player, zombie);
        SoulCages.set(player, 19);
        SoulService.summon(player, entry.id);
        helper.assertTrue(entry.activeEntity == null && SoulCages.active(player).energy() == 19, "Insufficient energy cannot summon");
        for (EntityType<?> type : java.util.List.of(EntityType.SKELETON, EntityType.HUSK, EntityType.COW)) {
            SoulEntry blocker = entry(player, (Mob) type.create(helper.getLevel()));
            blocker.activeEntity = UUID.randomUUID(); // Ledger reservations count even when their chunks are unloaded.
        }
        SoulService.summon(player, entry.id);
        helper.assertTrue(entry.activeEntity == null && SoulCages.active(player).energy() == 1000, "Capacity failure cannot spend energy");
        player.discard(); helper.succeed();
    }
    @GameTest(template = "arena")
    public static void alliedDamageAndStaleSummon(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        var zombie = helper.spawn(EntityType.ZOMBIE, 8, 1, 10);
        SoulEntry entry = entry(player, zombie);
        SoulService.summon(player, entry.id);
        Mob summoned = (Mob) helper.getLevel().getEntity(entry.activeEntity);
        helper.assertTrue(summoned != null, "Summon exists");
        var ally = helper.spawn(EntityType.HUSK, 8, 1, 12);
        ally.getPersistentData().putUUID(CompanionBrain.OWNER, player.getUUID());
        float health = ally.getHealth();
        ally.hurt(summoned.damageSources().mobAttack(summoned), 8);
        helper.assertTrue(ally.getHealth() == health, "Companion cannot damage allied companion");
        ally.discard();
        summoned.setTarget(player);
        helper.assertTrue(summoned.getTarget() == null, "Companion cannot target owner");
        CompoundTag stale = summoned.saveWithoutId(new CompoundTag());
        SoulService.recall(player.server, entry);
        var orphan = EntityType.ZOMBIE.create(helper.getLevel()); orphan.load(stale);
        helper.assertFalse(helper.getLevel().addFreshEntity(orphan), "A recalled entity cannot return from an unloaded chunk");
        player.discard(); helper.succeed();
    }
    @GameTest(template = "arena", timeoutTicks = 120)
    public static void skeletonKeepsRangedAttack(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        Skeleton skeleton = helper.spawn(EntityType.SKELETON, 8, 1, 10);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        SoulEntry entry = entry(player, skeleton);
        skeleton.discard();
        SoulService.summon(player, entry.id);
        Skeleton summoned = (Skeleton) helper.getLevel().getEntity(entry.activeEntity);
        helper.assertTrue(summoned != null && summoned.getMainHandItem().is(Items.BOW), "Must preserve bow and skeleton type");
        var target = helper.spawn(EntityType.HUSK, 8, 1, 14);
        target.setNoAi(true); summoned.setTarget(target);
        helper.succeedWhen(() -> {
            helper.assertTrue(!helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.Arrow.class,
                target.getBoundingBox().inflate(16)).isEmpty() || target.getHealth() < target.getMaxHealth(), "Skeleton must fire native arrow");
            SoulService.recall(player.server, entry); player.discard();
        });
    }
    @GameTest(template = "arena")
    public static void companionDefeatKeepsCollection(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        var original = helper.spawn(EntityType.HUSK, 8, 1, 10);
        original.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND));
        SoulEntry entry = entry(player, original);
        original.discard(); SoulService.summon(player, entry.id);
        Mob summoned = (Mob) helper.getLevel().getEntity(entry.activeEntity);
        helper.assertTrue(summoned != null, "Summon exists");
        int energy = SoulCages.active(player).energy();
        summoned.hurt(summoned.damageSources().genericKill(), 10000);
        helper.assertTrue(entry.activeEntity == null && SoulService.data(player).entries.containsKey(entry.id), "Defeat must retain collection entry");
        helper.assertTrue(entry.readyAt > player.server.overworld().getGameTime(), "Defeat starts cooldown");
        helper.assertTrue(SoulCages.active(player).energy() == energy, "Defeating companion cannot farm energy");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
            summoned.getBoundingBox().inflate(4)).isEmpty(), "Companion cannot drop duplicated equipment");
        SoulService.summon(player, entry.id);
        helper.assertTrue(entry.activeEntity == null && SoulCages.active(player).energy() == energy, "Cooldown must block resummoning without spending");
        player.discard(); helper.succeed();
    }
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void wroughtnautKeepsNativeAttack(GameTestHelper helper) {
        if (!ModList.get().isLoaded("mowziesmobs")) { helper.succeed(); return; }
        ServerPlayer player = player(helper);
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("mowziesmobs", "ferrous_wroughtnaut"));
        Mob original = (Mob) type.create(helper.getLevel());
        SoulEntry entry = entry(player, original);
        SoulService.summon(player, entry.id);
        Mob knight = (Mob) helper.getLevel().getEntity(entry.activeEntity);
        helper.assertTrue(knight != null, "Wroughtnaut must summon");
        var target = helper.spawn(EntityType.HUSK, 8, 1, 14);
        target.setNoAi(true); target.setPos(knight.position().add(2, 0, 0));
        knight.setTarget(target);
        helper.succeedWhen(() -> {
            helper.assertTrue(target.getHealth() < target.getMaxHealth(), "Knight must use native attacks on enemy mob");
            SoulService.recallAll(player); player.discard();
        });
    }
    @GameTest(template = "arena")
    public static void wroughtnautAdapterAndAreaFilter(GameTestHelper helper) throws Exception {
        if (!ModList.get().isLoaded("mowziesmobs")) { helper.succeed(); return; }
        ServerPlayer player = player(helper);
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("mowziesmobs", "ferrous_wroughtnaut"));
        Mob original = (Mob) type.create(helper.getLevel());
        SoulEntry entry = entry(player, original);
        SoulService.summon(player, entry.id);
        Mob knight = (Mob) helper.getLevel().getEntity(entry.activeEntity);
        helper.assertTrue(knight != null, "Wroughtnaut must summon");
        helper.assertTrue((Boolean) knight.getClass().getMethod("isAlwaysActive").invoke(knight), "Knight must remain awake");
        helper.assertTrue(knight.goalSelector.getAvailableGoals().stream().anyMatch(g -> g.getGoal().getClass().getSimpleName().equals("WroughtnautAttackAI")), "Native combat goals must remain");
        var ally = helper.spawn(EntityType.ZOMBIE, 8, 1, 12);
        SoulEntry allyEntry = entry(player, ally);
        ally.discard(); SoulService.summon(player, allyEntry.id);
        Mob alliedMob = (Mob) helper.getLevel().getEntity(allyEntry.activeEntity);
        helper.assertTrue(alliedMob != null, "Allied test creature exists");
        alliedMob.setPos(knight.position().add(1, 0, 0));
        var nearby = (java.util.List<?>) knight.getClass().getMethod("getEntityLivingBaseNearby", double.class, double.class, double.class, double.class)
            .invoke(knight, 8.0, 8.0, 8.0, 8.0);
        helper.assertFalse(nearby.contains(alliedMob), "AoE list must exclude allies before damage/knockback/shield wear");
        SoulService.recallAll(player); player.discard(); helper.succeed();
    }
}

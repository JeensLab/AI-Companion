package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.*;
import dev.aicompanion.inventory.CompanionMenu;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** What happens to the player becomes happenings, and the important ones are spoken once the owner can hear them. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionHappeningsTests {
    private static final String URGENT = "[!]";

    private record Rig(CompanionEntity companion, ServerPlayer player) { }

    @GameTest(template="tall_tree", batch="happen_sources", timeoutTicks=60)
    public static void happeningsRecordEachSourceWithImportance(GameTestHelper h) {
        CompanionHappeningEvents.clear();
        var rig = rig(h); var p = rig.player(); var c = rig.companion();
        var damage = h.getLevel().damageSources();
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(p, damage.generic()));
        NeoForge.EVENT_BUS.post(new AdvancementEvent.AdvancementEarnEvent(p, advancement("aicompanion:test/plain", AdvancementType.TASK, true, true)));
        NeoForge.EVENT_BUS.post(new AdvancementEvent.AdvancementEarnEvent(p, advancement("minecraft:story/mine_diamond", AdvancementType.TASK, true, true)));
        NeoForge.EVENT_BUS.post(new AdvancementEvent.AdvancementEarnEvent(p, advancement("aicompanion:test/hard", AdvancementType.CHALLENGE, true, true)));
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(p, Level.OVERWORLD, Level.NETHER));
        var zombie = h.spawn(EntityType.ZOMBIE, 18, 1, 16); zombie.setNoAi(true);
        p.setLastHurtByMob(zombie);
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(zombie, damage.mobAttack(c)));
        p.setHealth(3); CompanionHappeningEvents.checkDanger(p);
        p.setHealth(20); CompanionHappeningEvents.checkDanger(p);
        bonds(h).markSeen(p.getUUID(), System.currentTimeMillis() - 40 * 60_000L);
        CompanionHappeningEvents.welcome(p, System.currentTimeMillis());
        CompanionHappeningEvents.gift(p, List.of(CompanionBond.change(new ItemStack(Items.DIAMOND), 1)), "1 × Алмаз");
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(c, damage.generic()));

        Map<String, CompanionHappenings.Importance> seen = new HashMap<>();
        for (var event : bonds(h).happenings(p.getUUID()).all()) seen.put(event.key(), event.importance());
        var expected = Map.of("player_death", CompanionHappenings.Importance.HIGH,
                "advancement:aicompanion:test/plain", CompanionHappenings.Importance.NORMAL,
                "advancement:minecraft:story/mine_diamond", CompanionHappenings.Importance.HIGH,
                "advancement:aicompanion:test/hard", CompanionHappenings.Importance.HIGH,
                "dimension", CompanionHappenings.Importance.LOW, "rescue", CompanionHappenings.Importance.NORMAL,
                "survived", CompanionHappenings.Importance.NORMAL, "return", CompanionHappenings.Importance.HIGH,
                "gift", CompanionHappenings.Importance.NORMAL);
        CompanionHappeningEvents.clear();
        h.assertTrue(seen.entrySet().containsAll(expected.entrySet()), "Sources or importance wrong: " + seen);
        h.assertTrue(seen.get("companion_died") == CompanionHappenings.Importance.HIGH, "Companion death not recorded as HIGH: " + seen);
        h.succeed();
    }

    @GameTest(template="tall_tree", batch="happen_ignored", timeoutTicks=60)
    public static void recipesAndRootAdvancementsIgnored(GameTestHelper h) {
        CompanionHappeningEvents.clear();
        var p = rig(h).player();
        NeoForge.EVENT_BUS.post(new AdvancementEvent.AdvancementEarnEvent(p, advancement("minecraft:recipes/misc/stick", AdvancementType.TASK, true, false)));
        NeoForge.EVENT_BUS.post(new AdvancementEvent.AdvancementEarnEvent(p, advancement("minecraft:story/root", AdvancementType.TASK, false, true)));
        var all = bonds(h).happenings(p.getUUID()).all();
        CompanionHappeningEvents.clear();
        h.assertTrue(all.isEmpty(), "Recipe or root advancement became a happening: " + all);
        h.succeed();
    }

    @GameTest(template="empty")
    public static void urgentWaitsForSafetyAndExpires(GameTestHelper h) {
        var u = new CompanionUrgent();
        u.offer("a", 0);
        h.assertTrue(u.step(0, true, false) == null && u.step(500, true, false) == null && u.pending(), "Released while unsafe");
        h.assertTrue("a".equals(u.step(501, true, true)), "Not released once safe and heard");
        u.offer("b", 501);
        h.assertTrue(u.step(502, true, true) == null && "b".equals(u.step(501 + CompanionUrgent.GAP_TICKS, true, true)), "Two minutes between urgent lines not kept");
        u.offer("c", 3000);
        h.assertTrue(u.step(3000 + CompanionUrgent.WAIT_TICKS + 1, true, true) == null && !u.pending()
                && u.step(3000 + CompanionUrgent.WAIT_TICKS + 2, true, true) == null, "Expired line released");
        u.offer("d", 20000);
        h.assertTrue(u.step(20000, true, true) == null && u.step(20050, false, true) == null && u.step(20120, true, true) == null, "Break in earshot did not restart the five seconds");
        h.assertTrue("d".equals(u.step(20230, true, true)), "Not released after five seconds of earshot");
        h.succeed();
    }

    @GameTest(template="tall_tree", batch="happen_twice", timeoutTicks=260)
    public static void twoUrgentInARowSpeakOnce(GameTestHelper h) {
        CompanionHappeningEvents.clear();
        var rig = rig(h); var p = rig.player(); var c = rig.companion();
        NeoForge.EVENT_BUS.post(new AdvancementEvent.AdvancementEarnEvent(p, advancement("aicompanion:test/first", AdvancementType.CHALLENGE, true, true, "Первое")));
        NeoForge.EVENT_BUS.post(new AdvancementEvent.AdvancementEarnEvent(p, advancement("aicompanion:test/second", AdvancementType.CHALLENGE, true, true, "Второе")));
        h.runAfterDelay(140, () -> {
            CompanionHappeningEvents.clear();
            h.assertTrue(c.socialSequence() == 1, "Expected exactly one spoken line, got " + c.socialSequence());
            h.assertTrue(c.socialObservation().startsWith(URGENT) && c.socialObservation().contains("Второе"), "Not the latest urgent line: " + c.socialObservation());
            h.succeed();
        });
    }

    @GameTest(template="tall_tree", batch="happen_dimension", timeoutTicks=500)
    public static void urgentWaitsUntilOwnerIsNearAfterDimensionReturn(GameTestHelper h) {
        CompanionHappeningEvents.clear();
        var rig = rig(h); var p = rig.player(); var c = rig.companion();
        var home = p.serverLevel();
        var away = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(away != null, "No second dimension to test with");
        p.teleportTo(away, c.getX(), 70, c.getZ(), 0, 0);
        NeoForge.EVENT_BUS.post(new AdvancementEvent.AdvancementEarnEvent(p, advancement("aicompanion:test/far", AdvancementType.CHALLENGE, true, true)));
        h.runAfterDelay(130, () -> {
            h.assertTrue(c.socialSequence() == 0, "Spoke while the owner was in another dimension");
            p.teleportTo(home, c.getX() - 3, c.getY(), c.getZ() - 3, 0, 0);
            h.runAfterDelay(60, () -> {
                h.assertTrue(c.socialSequence() == 0, "Spoke before the owner had been near for five seconds");
                h.runAfterDelay(80, () -> {
                    CompanionHappeningEvents.clear();
                    h.assertTrue(c.socialSequence() == 1, "Not spoken after the owner returned");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template="tall_tree", batch="happen_hit", timeoutTicks=60)
    public static void hitDuringMonsterFightKeepsBond(GameTestHelper h) {
        CompanionHappeningEvents.clear();
        var rig = rig(h); var p = rig.player(); var c = rig.companion();
        c.setNoAi(true);
        var zombie = h.spawn(EntityType.ZOMBIE, 17, 1, 16); zombie.setNoAi(true); zombie.setInvulnerable(true);
        var source = h.getLevel().damageSources().playerAttack(p);
        c.hurt(source, 1);
        h.assertTrue(bonds(h).bond(p.getUUID()).score() == CompanionBond.BASE, "Hit during a monster fight cost bond: " + bonds(h).bond(p.getUUID()).score());
        zombie.discard();
        c.invulnerableTime = 0;
        c.hurt(source, 1);
        int score = bonds(h).bond(p.getUUID()).score();
        CompanionHappeningEvents.clear();
        h.assertTrue(score == CompanionBond.BASE - CompanionBond.HIT_POINTS, "Hit without a fight did not cost bond: " + score);
        h.succeed();
    }

    @GameTest(template="tall_tree", batch="happen_gift", timeoutTicks=60)
    public static void putAndTakeBackIsNoGift(GameTestHelper h) {
        CompanionHappeningEvents.clear();
        var rig = rig(h); var p = rig.player(); var c = rig.companion();
        var inventory = p.getInventory();
        inventory.setItem(9, new ItemStack(Items.IRON_INGOT, 8));
        session(p, c, CompanionMenu.PLAYER_START);
        int put = bonds(h).bond(p.getUUID()).score();
        h.assertTrue(put == CompanionBond.BASE + 5, "First gift of iron should add 5: " + put);
        session(p, c, CompanionMenu.BAG_START);
        h.assertTrue(bonds(h).bond(p.getUUID()).score() == put, "Taking the gift back changed the bond");
        session(p, c, CompanionMenu.PLAYER_START);
        h.assertTrue(bonds(h).bond(p.getUUID()).score() == put, "Putting the same gift again paid twice");
        var menu = new CompanionMenu(2, inventory, c);
        menu.quickMoveStack(p, CompanionMenu.BAG_START);
        menu.quickMoveStack(p, CompanionMenu.PLAYER_START);
        menu.removed(p);
        CompanionHappeningEvents.clear();
        h.assertTrue(bonds(h).bond(p.getUUID()).score() == put, "Put and take back in one session paid");
        h.succeed();
    }

    @GameTest(template="tall_tree", batch="happen_respawn", timeoutTicks=420)
    public static void respawnAfterEndExitIsNoDeath(GameTestHelper h) {
        CompanionHappeningEvents.clear();
        var rig = rig(h); var p = rig.player(); var c = rig.companion();
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(p, true));
        h.assertTrue(!bonds(h).happenings(p.getUUID()).has("player_death"), "Leaving the End was recorded as a death");
        h.runAfterDelay(140, () -> {
            h.assertTrue(c.socialSequence() == 0, "Spoke of a death after leaving the End");
            NeoForge.EVENT_BUS.post(new LivingDeathEvent(p, h.getLevel().damageSources().generic()));
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(p, false));
            h.assertTrue(bonds(h).happenings(p.getUUID()).has("player_death"), "Real death not recorded");
            h.runAfterDelay(140, () -> {
                CompanionHappeningEvents.clear();
                h.assertTrue(c.socialSequence() == 1 && c.socialObservation().startsWith(URGENT), "Death not told after respawn: " + c.socialObservation());
                h.succeed();
            });
        });
    }

    @GameTest(template="tall_tree", batch="happen_rescue", timeoutTicks=60)
    public static void rescueCountsOnlyKillOfOwnersAttacker(GameTestHelper h) {
        CompanionHappeningEvents.clear();
        var rig = rig(h); var p = rig.player(); var c = rig.companion();
        var attacker = h.spawn(EntityType.ZOMBIE, 18, 1, 16); attacker.setNoAi(true);
        var stranger = h.spawn(EntityType.ZOMBIE, 14, 1, 16); stranger.setNoAi(true);
        var kill = h.getLevel().damageSources().mobAttack(c);
        p.setLastHurtByMob(attacker);
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(stranger, kill));
        h.assertTrue(bonds(h).bond(p.getUUID()).score() == CompanionBond.BASE, "Unrelated kill counted as a rescue");
        p.tickCount += 500;
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(attacker, kill));
        h.assertTrue(bonds(h).bond(p.getUUID()).score() == CompanionBond.BASE, "Stale attacker counted as a rescue");
        p.setLastHurtByMob(attacker);
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(attacker, kill));
        int score = bonds(h).bond(p.getUUID()).score();
        boolean told = bonds(h).happenings(p.getUUID()).has("rescue");
        CompanionHappeningEvents.clear();
        h.assertTrue(score == CompanionBond.BASE + CompanionBond.RESCUE_POINTS && told, "Killing the owner's attacker was not a rescue: " + score);
        h.succeed();
    }

    @GameTest(template="tall_tree", batch="happen_oleg_death", timeoutTicks=260)
    public static void companionDeathIsToldByReturnedCompanion(GameTestHelper h) {
        CompanionHappeningEvents.clear();
        var rig = rig(h); var p = rig.player(); var a = rig.companion();
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(a, h.getLevel().damageSources().generic()));
        a.discard();
        var b = h.spawn(AICompanionMod.COMPANION.get(), 18, 1, 16);
        b.setOwnerId(p.getUUID());
        p.getPersistentData().putUUID("aicompanion:canonical_companion", b.getUUID());
        b.setFollowing(false); b.setLifestyle(false, true); b.tickCount = 300;
        h.runAfterDelay(140, () -> {
            CompanionHappeningEvents.clear();
            h.assertTrue(b.socialSequence() == 1 && b.socialObservation().startsWith(URGENT) && b.socialObservation().contains("Я погиб"),
                    "The returned companion did not tell of the death: " + b.socialObservation());
            h.assertTrue(a.socialSequence() == 0, "The dead companion spoke");
            h.succeed();
        });
    }

    @GameTest(template="tall_tree", batch="happen_absence", timeoutTicks=60)
    public static void absenceUsesLastSeenAfterCrash(GameTestHelper h) {
        CompanionHappeningEvents.clear();
        long now = System.currentTimeMillis();
        var long_ = h.makeMockServerPlayerInLevel();
        var short_ = h.makeMockServerPlayerInLevel();
        bonds(h).markSeen(long_.getUUID(), now - 31 * 60_000L);
        bonds(h).markSeen(short_.getUUID(), now - 29 * 60_000L);
        CompanionHappeningEvents.welcome(long_, now);
        CompanionHappeningEvents.welcome(short_, now);
        var back = bonds(h).happenings(long_.getUUID()).all();
        boolean shortTold = bonds(h).happenings(short_.getUUID()).has("return");
        CompanionHappeningEvents.clear();
        h.assertTrue(back.size() == 1 && back.getFirst().key().equals("return") && back.getFirst().importance() == CompanionHappenings.Importance.HIGH
                && back.getFirst().text().contains("31 мин"), "31 minutes away was not a HIGH return: " + back);
        h.assertTrue(!shortTold, "29 minutes away counted as an absence");
        h.succeed();
    }

    private static void session(ServerPlayer player, CompanionEntity companion, int slot) {
        var menu = new CompanionMenu(1, player.getInventory(), companion);
        menu.quickMoveStack(player, slot);
        menu.removed(player);
    }

    private static AdvancementHolder advancement(String id, AdvancementType type, boolean announce, boolean displayed) {
        return advancement(id, type, announce, displayed, "Достижение");
    }

    private static AdvancementHolder advancement(String id, AdvancementType type, boolean announce, boolean displayed, String title) {
        var builder = Advancement.Builder.advancement();
        if (displayed) builder.display(new ItemStack(Items.STONE), Component.literal(title), Component.literal("Описание"), null, type, true, announce, false);
        return builder.build(ResourceLocation.parse(id));
    }

    private static CompanionBonds bonds(GameTestHelper h) { return CompanionBonds.get(h.getLevel().getServer()); }

    private static Rig rig(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        var c = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var p = h.makeMockServerPlayerInLevel();
        p.teleportTo(c.getX() - 3, c.getY(), c.getZ() - 3);
        c.setOwnerId(p.getUUID());
        p.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(p);
        c.setFollowing(false); c.setLifestyle(false, true); c.tickCount = 300;
        return new Rig(c, p);
    }
}

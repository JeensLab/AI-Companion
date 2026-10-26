package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Regression for session 1cda5b75: logs done, no pickaxe, an empty model plan after «продолжай». */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionAutonomySessionTests {
    @GameTest(template = "empty")
    public static void continuingIsALocalModeRequest(GameTestHelper h) {
        for (String phrase : new String[]{"продолжай заниматься чем хочешь", "иди ещё чем-нибудь займись",
                "продолжай заниматься своими делами", "занимайся чем-нибудь, пока я занят"})
            h.assertTrue("companion autonomy on".equals(CompanionLocalCommands.route(phrase, "Олег")), phrase);
        for (String phrase : new String[]{"не занимайся чем-нибудь", "не делай что хочешь",
                "продолжай заниматься чем хочешь, но сначала добудь камня", "займись чем-нибудь потом"})
            h.assertTrue(!"companion autonomy on".equals(CompanionLocalCommands.route(phrase, "Олег")), phrase);
        h.succeed();
    }

    /** Exactly the relevant inventory from the game: wood and an axe, no pick. No further orders. */
    @GameTest(template = "marathon_valley", batch = "autonomy_session", timeoutTicks = 20 * 60 * 7)
    public static void afterWoodHeMakesHisMissingPickAndMinesStone(GameTestHelper h) {
        var a = yard(h);
        a.oleg.setItem(0, new ItemStack(Items.OAK_LOG, 19));
        a.oleg.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
        a.script.order("Будь самостоятельным")
                .waitUntil("a real pickaxe and stone, without another request",
                        () -> !a.oleg.autonomy().running() && a.countTag(ItemTags.PICKAXES) > 0
                                && a.countTag(ItemTags.STONE_TOOL_MATERIALS) >= 32, 20 * 60 * 6)
                .order("Стоп")
                .check("physical progress, not an answer", () -> {
                    a.expect(a.countTag(ItemTags.PICKAXES) > 0, "No crafted pickaxe: " + a.oleg.getStoredItemsSummary());
                    a.expect(a.countTag(ItemTags.STONE_TOOL_MATERIALS) >= 32, "No mined stone: " + a.oleg.getDeedsForAi());
                    a.watch.assertNoTeleport();
                    a.watch.assertNothingLeft();
                });
        finish(a, "autonomy-missing-pick", a.watch::summary);
    }

    @GameTest(template = "marathon_valley", batch = "autonomy_session", timeoutTicks = 20 * 60 * 4)
    public static void fullOwnStorageStopsHarvestingAndTakingSuppliesRestartsIt(GameTestHelper h) {
        var a = yard(h);
        a.oleg.setItem(0, new ItemStack(Items.OAK_LOG, 16));
        a.oleg.setItem(1, new ItemStack(Items.COBBLESTONE, 32));
        a.oleg.setItem(2, new ItemStack(Items.STONE_PICKAXE));
        BlockPos at = a.cell(48, 2, 51);
        h.setBlock(at, Blocks.CHEST);
        a.oleg.rememberPlaced(h.absolutePos(at));
        var chest = (ChestBlockEntity) h.getBlockEntity(at);
        for (int slot = 0; slot < 4; slot++) chest.setItem(slot, new ItemStack(Items.OAK_LOG, slot == 3 ? 48 : 64));
        for (int slot = 4; slot < 20; slot++) chest.setItem(slot, new ItemStack(Items.COBBLESTONE, slot == 19 ? 32 : 64));
        chest.setChanged();
        boolean[] worked = {false};
        h.onEachTick(() -> worked[0] |= a.oleg.autonomy().running());
        a.script.order("Будь самостоятельным")
                .check("the owner goes far away with full reserves", () -> {
                    a.player.setNoGravity(true);
                    a.player.teleportTo(a.player.getX() + 192, a.player.getY(), a.player.getZ());
                })
                .pause(20 * 90)
                .check("full reserves mean waiting at the original workplace", () -> {
                    a.expect(!worked[0], "Worked with full own reserves");
                    a.expect(a.oleg.blockPosition().distSqr(a.oleg.autonomy().workArea()) < 16, "Followed between jobs");
                })
                .then("the player takes a stack from the chest", () -> a.player.getInventory().add(chest.removeItem(4, 64)), () -> true, 20)
                .waitUntil("automatically replenishes used supplies", () -> worked[0], 20 * 90)
                .check("it restarted", () -> a.expect(worked[0], "Did not notice used supplies"))
                .order("Стой");
        finish(a, "autonomy-reserve-limit", a.watch::summary);
    }

    /** Real terrain and recipes, thirty uninterrupted game minutes, one initial request, no restocking by the test. */
    @GameTest(template = "marathon_valley", batch = "autonomy_session", timeoutTicks = 20 * 60 * 32)
    public static void halfAnHourOfSuppliesWithoutAnotherRequest(GameTestHelper h) {
        var a = yard(h);
        int[] worked = {0}, lateWork = {0}, completed = {0}, succeeded = {0};
        long[] work = {0};
        java.util.Set<Long> successful = new java.util.HashSet<>();
        h.onEachTick(() -> {
            if (a.oleg.autonomy().running()) { worked[0]++; if (h.getTick() > 20 * 60 * 25) lateWork[0]++; }
            if (work[0] != 0 && !successful.contains(work[0])) {
                var result = a.oleg.workResult(work[0]);
                if (result != null && result.success()) { successful.add(work[0]); succeeded[0]++; }
            }
            long id = a.oleg.activeWorkId();
            if (id != 0 && id != work[0]) { work[0] = id; completed[0]++; }
        });
        a.script.order("Будь самостоятельным")
                .waitUntil("a job starts", () -> a.oleg.autonomy().running(), 20 * 20)
                .check("the owner leaves for the whole session", () -> {
                    a.player.setNoGravity(true);
                    a.player.teleportTo(a.player.getX() + 192, a.player.getY(), a.player.getZ());
                })
                .pause(20 * 60 * 30)
                .waitUntil("the current batch delivered before stopping", () -> !a.oleg.autonomy().running(), 20 * 60)
                .order("Стой")
                .pause(20 * 5)
                .check("a long series, a stocked chest, no new orders", () -> {
                    int wood = stored(a, ItemTags.LOGS), stone = stored(a, ItemTags.STONE_TOOL_MATERIALS);
                    a.watch.note("30-minute session: orders=" + completed[0] + ", successes=" + succeeded[0]
                            + ", activeSeconds=" + worked[0] / 20 + ", lateActiveSeconds=" + lateWork[0] / 20
                            + ", storedWood=" + wood + ", storedStone=" + stone);
                    a.expect(completed[0] >= 12, "Only " + completed[0] + " work orders in thirty minutes");
                    a.expect(succeeded[0] >= 10, "Only " + succeeded[0] + " successful work results");
                    a.expect(worked[0] >= 20 * 60 * 15, "Worked only " + worked[0] / 20 + " seconds");
                    a.expect(lateWork[0] >= 20 * 30 || wood >= 240 && stone >= 992,
                            "Stopped early with no full stock: wood=" + wood + ", stone=" + stone + ", late work=" + lateWork[0] / 20);
                    a.expect(wood >= 48 && stone >= 96, "Nothing stocked: wood=" + wood + ", stone=" + stone);
                    a.expect(!a.oleg.autonomy().running() && a.oleg.autonomy().held(), "Stop did not stop the session");
                    a.expect(a.toPlayer() > 150, "Left work to follow the owner");
                    a.expect(a.oleg.autonomyLineSerial() <= 3, "Narrated routine jobs: " + a.oleg.autonomyLineSerial());
                    a.watch.assertAlive();
                    a.watch.assertNoTeleport();
                    a.watch.assertNoLongIdle(30);
                    a.watch.assertNothingLeft();
                });
        finish(a, "autonomy-half-hour", () -> a.watch.summary() + " orders=" + completed[0] + " successes=" + succeeded[0] + " activeSeconds=" + worked[0] / 20);
    }

    private static void finish(MarathonArena a, String name, java.util.function.Supplier<String> summary) {
        a.script.check("the completed fixture stops and releases its work chunks", () -> {
            a.oleg.setFollowing(false);
            a.oleg.setLifestyle(false, false);
            dev.aicompanion.entity.CompanionChunks.release(a.oleg.getUUID());
        });
        a.script.run(name, summary);
    }

    /** Flat forest/quarry: enough natural trees and a stone bank within the existing 24-block leash. */
    private static MarathonArena yard(GameTestHelper h) {
        for (int x = 20; x <= 76; x++) for (int z = 16; z <= 72; z++) {
            for (int y = 39; y >= 2; y--) h.setBlock(new BlockPos(x + MarathonArena.M, y, z + MarathonArena.M), Blocks.AIR);
            h.setBlock(new BlockPos(x + MarathonArena.M, 0, z + MarathonArena.M), Blocks.BEDROCK);
            h.setBlock(new BlockPos(x + MarathonArena.M, 1, z + MarathonArena.M), Blocks.DIRT);
        }
        for (int x = 30; x <= 66; x += 3) for (int z = 29; z <= 41; z += 3) {
            for (int y = 2; y <= 6; y++) h.setBlock(new BlockPos(x + MarathonArena.M, y, z + MarathonArena.M), Blocks.OAK_LOG);
            h.setBlock(new BlockPos(x + MarathonArena.M, 7, z + MarathonArena.M), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1));
        }
        for (int x = 29; x <= 67; x++) for (int z = 56; z <= 65; z++) for (int y = 2; y <= 4; y++)
            h.setBlock(new BlockPos(x + MarathonArena.M, y, z + MarathonArena.M), Blocks.STONE);
        // Clearing the original valley can dislodge flowers. Only fixture debris is removed, before Oleg exists;
        // every item dropped during his actual work remains under the usual physical-pickup assertions.
        var area = new net.minecraft.world.phys.AABB(net.minecraft.world.phys.Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(20 + MarathonArena.M, 0, 16 + MarathonArena.M))),
                net.minecraft.world.phys.Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(77 + MarathonArena.M, 40, 73 + MarathonArena.M))));
        for (var item : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area)) item.discard();
        return new MarathonArena(h, 96, 40, 80, 46, 48, 48, 48, 40);
    }

    private static int stored(MarathonArena a, net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag) {
        int count = 0;
        for (int x = 24; x <= 72; x++) for (int z = 24; z <= 72; z++) for (int y = 0; y <= 10; y++)
            if (a.h.getLevel().getBlockEntity(a.h.absolutePos(a.cell(x, y, z))) instanceof ChestBlockEntity chest)
                for (int slot = 0; slot < chest.getContainerSize(); slot++) if (chest.getItem(slot).is(tag)) count += chest.getItem(slot).getCount();
        return count;
    }
}

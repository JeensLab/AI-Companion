package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What the marathon of ordinary requests found (docs/verification-0.0.1-marathon.md), each in the smallest scene that shows it.
 * Every test here failed on the code before its fix, except toldToStayInTheWaterHeStaysPut: a guard for «стой» in the water
 * written while the drift of the «берег» arena was not yet understood (its cause is aJumpTowardsTheBankDoesNotCarryHimAcrossTheWater).
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMarathonFixTests {
    /**
     * Marathon, arena «долина», the ravine of game log 23.10: stone mined in the wall at head height drops onto the ledge below it,
     * two blocks above his feet. A player standing there picks it up (the pickup box reaches half a block over the head);
     * Oleg took only what lay within a block and a half of his feet, so every stone from the wall was «out of reach».
     */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void stoneFromTheWallAtHeadHeightIsPickedUpFromTheLedge(GameTestHelper h) {
        for (int x = 4; x <= 12; x++) for (int z = 4; z <= 12; z++) for (int y = 0; y <= 4; y++) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
        // A pit one cell wide: he stands at (8, 1, 8); in its east wall the only stone, on two blocks of bedrock.
        for (int y = 1; y <= 4; y++) h.setBlock(new BlockPos(8, y, 8), Blocks.AIR);
        h.setBlock(new BlockPos(9, 3, 8), Blocks.STONE);
        h.setBlock(new BlockPos(9, 4, 8), Blocks.AIR); // open above, so the face is exposed and the drop is not shut in
        CompanionEntity oleg = spawn(h, 8, 1, 8);
        oleg.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        h.assertTrue(CompanionEvents.executeAction(player(h, oleg), "gather_stone", "", 1).success(), "Not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!oleg.isWorking(), "Still working: " + oleg.getLastTaskResult());
            h.assertTrue(MarathonArena.count(oleg, Items.COBBLESTONE) == 1 && oleg.didLastTaskSucceed(), "Stone left on the ledge: " + oleg.getLastTaskResult());
        });
    }

    /**
     * Marathon, «долина»: after the ravine the store order stood ninety seconds at «Сейчас сложу вещи» — a stone that had
     * fallen into the water below was still «his drop to pick up», the work waited for it, and the pickup never went for it
     * (it floats, it is not on the ground, and it is far below him). Here a stone over a pond: its drop falls into the water two
     * blocks below the bank. The work goes on with the other stone instead of waiting for the one in the pond.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aStoneInThePondBelowDoesNotHoldUpTheWork(GameTestHelper h) {
        for (int x = 4; x <= 14; x++) for (int z = 4; z <= 12; z++) for (int y = 0; y <= 5; y++) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
        // The pond: water at y 1..3 in x 10..12, z 7..9, open air at y 4..5 above it, a stone «bridge» over its edge at (10, 5, 8).
        for (int x = 10; x <= 12; x++) for (int z = 7; z <= 9; z++) {
            for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(x, y, z), Blocks.WATER);
            for (int y = 4; y <= 5; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        h.setBlock(new BlockPos(10, 5, 8), Blocks.STONE);
        // The other stones stand on the bank at his feet, a little farther than the bridge.
        h.setBlock(new BlockPos(6, 6, 6), Blocks.STONE);
        h.setBlock(new BlockPos(6, 6, 10), Blocks.STONE);
        CompanionEntity oleg = spawn(h, 9, 6, 8);
        oleg.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        h.assertTrue(CompanionEvents.executeAction(player(h, oleg), "gather_stone", "", 2).success(), "Not accepted");
        long[] done = {-1};
        h.onEachTick(() -> { if (done[0] < 0 && !oleg.isWorking()) done[0] = h.getTick(); });
        h.succeedWhen(() -> {
            h.assertTrue(done[0] >= 0, "Still working: " + oleg.getLastTaskResult() + " " + oleg.watchDetail());
            h.assertTrue(oleg.didLastTaskSucceed() && MarathonArena.count(oleg, Items.COBBLESTONE) >= 2, "Not done: " + oleg.getLastTaskResult());
            h.assertTrue(done[0] < 20 * 40, "Took " + done[0] / 20 + "s for three stones beside him");
        });
    }

    /**
     * Marathon, «берег»: the only trees grew on an island four blocks of deep water off a sandbar. Oleg walked to the end of
     * the sandbar and stood there a minute and a half («Иду к дереву подальше», then «нужна опора, добуду земли» in the water),
     * then «не нашёл доступного дерева». A player swims the few blocks. Here the island is six blocks off the shore.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void theTreeOnTheIslandIsReachedBySwimming(GameTestHelper h) {
        var t = new MarathonTerrain(h, 32, 24, 32, 0);
        t.frame(8);
        for (int x = 1; x < 31; x++) for (int z = 1; z < 31; z++) {
            boolean shore = z >= 20, island = Math.hypot(x - 16, z - 9) <= 4;
            int top = shore || island ? 5 : 1;
            t.column(x, z, top, 2, (shore ? Blocks.SAND : Blocks.GRASS_BLOCK).defaultBlockState());
            for (int y = top + 1; y <= 4; y++) t.set(x, y, z, Blocks.WATER);
        }
        t.oak(16, 6, 9, 4, false);
        CompanionEntity oleg = spawn(h, 16, 6, 24);
        oleg.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_AXE));
        oleg.startGatheringLogs(2);
        h.succeedWhen(() -> {
            h.assertTrue(!oleg.isGatheringTask(), "Still gathering: " + oleg.getLastTaskResult() + " at " + oleg.blockPosition().subtract(h.absolutePos(BlockPos.ZERO)).toShortString());
            h.assertTrue(MarathonArena.count(oleg, Items.OAK_LOG) >= 2, "No wood from the island: " + oleg.getLastTaskResult());
        });
    }

    /**
     * Marathon: «иди ко мне» that could not get through ended in «Не смог добраться» — and with following switched off, so he
     * stood where he got stuck for good, even after the way opened. Like a friend he keeps following and trying.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void aComeThatFailedLeavesHimFollowing(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        // A bedrock pen round him: nothing to dig, nothing to climb.
        for (int x = 7; x <= 9; x++) for (int z = 7; z <= 9; z++) for (int y = 1; y <= 3; y++)
            if (x != 8 || z != 8) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
        h.setBlock(new BlockPos(8, 3, 8), Blocks.BEDROCK);
        CompanionEntity oleg = spawn(h, 8, 1, 8);
        var owner = player(h, oleg);
        var at = h.absoluteVec(new net.minecraft.world.phys.Vec3(20.5, 1, 20.5));
        owner.teleportTo(at.x, at.y, at.z);
        h.assertTrue(CompanionEvents.executeAction(owner, "come", "", 1).success(), "Not accepted");
        long id = oleg.activeWorkId();
        h.succeedWhen(() -> {
            var result = oleg.workResult(id);
            h.assertTrue(result != null, "Still trying: " + oleg.getLastTaskResult());
            h.assertTrue(!result.success() && result.message().contains("Не смог"), "Unexpected: " + result.message());
            h.assertTrue(oleg.isFollowing(), "Gave up following after a failed «иди ко мне»");
        });
    }

    /**
     * Marathon, night in «долина»: guarding with a stone sword, a skeleton and then a zombie — slain by the zombie. Each hit
     * knocked it out of his arm's length, he had to step in again, and it struck as often as he did. With a player's reach (three
     * blocks) and a player's pace for the weapon he beats a zombie from half his health.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void aZombieIsBeatenWithAStoneSwordLikeAPlayer(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity oleg = spawn(h, 10, 1, 10);
        var owner = player(h, oleg);
        var at = h.absoluteVec(new net.minecraft.world.phys.Vec3(13.5, 1, 10.5));
        owner.teleportTo(at.x, at.y, at.z);
        oleg.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        oleg.setHealth(10);
        oleg.setProtecting(true);
        var zombie = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, 12, 1, 12);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        zombie.setPersistenceRequired();
        StringBuilder fight = new StringBuilder();
        float[] last = {oleg.getHealth(), zombie.getHealth()};
        h.onEachTick(() -> {
            if (oleg.getHealth() != last[0] || zombie.getHealth() != last[1])
                fight.append(h.getTick()).append(":o").append(oleg.getHealth()).append("/z").append(zombie.getHealth()).append(' ');
            last[0] = oleg.getHealth(); last[1] = zombie.getHealth();
        });
        h.succeedWhen(() -> {
            h.assertTrue(oleg.isAlive(), "Slain by the zombie: " + fight);
            h.assertTrue(!zombie.isAlive(), "Zombie still alive: " + fight);
        });
    }

    /**
     * Marathon, «берег»: the trees were looked for once, from where the order came; on the island the farther trees lay beyond
     * that radius, and with them right there «рядом больше нет доступных брёвен». Here one tree twelve blocks off and two more
     * twelve blocks beyond it: twenty-four from the start, outside a search radius of sixteen.
     */
    @GameTest(template = "large_search", timeoutTicks = 2400)
    public static void treesBeyondTheFirstSearchAreSeenFromTheFirstTree(GameTestHelper h) {
        for (int x = 0; x < 128; x++) for (int z = 0; z < 128; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        for (int[] trunk : new int[][]{{52, 64}, {64, 62}, {64, 67}}) for (int y = 1; y <= 4; y++) h.setBlock(new BlockPos(trunk[0], y, trunk[1]), Blocks.OAK_LOG);
        CompanionEntity oleg = spawn(h, 40, 1, 64);
        oleg.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_AXE));
        oleg.startGatheringLogs(10);
        h.succeedWhen(() -> {
            h.assertTrue(!oleg.isGatheringTask(), "Still gathering: " + oleg.getLastTaskResult());
            h.assertTrue(MarathonArena.count(oleg, Items.OAK_LOG) >= 10, "Stopped at the first tree: " + oleg.getLastTaskResult());
        });
    }

    /**
     * Marathon, «берег»: the player swam to the island, Oleg came after him a block in two seconds and was still in the water
     * forty seconds later; over the sandbar (water to the knee) he waded as slowly. A mob's stroke in the water is its walking
     * speed (0.3) where a player's is the whole push of the key: three times weaker (in deep water the bobbing makes up for
     * it). Here twenty blocks of water to the knee to the player, which a player wades in about ten seconds.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void heWadesAsFastAsAPlayer(GameTestHelper h) {
        // A channel of water to the knee (x 4..27, z 13..17) between walls he cannot step onto; the player on the bank at its end.
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= 6; y++) {
            boolean channel = x >= 4 && x <= 27 && z >= 13 && z <= 17, bank = x > 27 && z >= 13 && z <= 17;
            h.setBlock(new BlockPos(x, y, z), y < 4 || !channel && !bank || bank && y == 4 ? Blocks.BEDROCK : channel && y == 4 ? Blocks.WATER : Blocks.AIR);
        }
        CompanionEntity oleg = spawn(h, 5, 4, 15);
        var owner = player(h, oleg);
        var at = h.absoluteVec(new net.minecraft.world.phys.Vec3(28.5, 5, 15.5));
        owner.teleportTo(at.x, at.y, at.z);
        h.assertTrue(CompanionEvents.executeAction(owner, "come", "", 1).success(), "Not accepted");
        long id = oleg.activeWorkId();
        long start = h.getTick();
        h.succeedWhen(() -> {
            var result = oleg.workResult(id);
            h.assertTrue(result != null && result.success(), "Still in the water at " + h.relativePos(oleg.blockPosition()) + ": " + oleg.getLastTaskResult());
            h.assertTrue(h.getTick() - start <= 20 * 18, "Took " + (h.getTick() - start) / 20 + " s for twenty blocks of wading");
        });
    }

    /**
     * Marathon, «долина»: «добудь мяса и пожарь» — the stone for the furnace lay down a slope; he crafted there, the bench took
     * the step of his way back, no room was left for the furnace («Не нашёл безопасное место»), the meat stayed raw and he dug
     * himself out. A player climbs back up with the stone and sets bench and furnace up there. Here the stone is only in the walls
     * of a narrow cut three blocks deep.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void theFurnaceIsSetUpAboveTheCutNotInIt(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= 8; y++)
            h.setBlock(new BlockPos(x, y, z), y == 0 ? Blocks.BEDROCK : y <= 5 ? Blocks.DIRT : Blocks.AIR);
        // The cut along x, two wide (z 15..16): a step down at x 11 and 12, its floor at y=2 for x 13..17, stone in its walls.
        for (int x = 11; x <= 17; x++) for (int z = 15; z <= 16; z++) {
            int floor = x == 11 ? 4 : x == 12 ? 3 : 2;
            for (int y = floor + 1; y <= 5; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        for (int x = 13; x <= 17; x++) for (int y = 3; y <= 4; y++) { h.setBlock(new BlockPos(x, y, 14), Blocks.STONE); h.setBlock(new BlockPos(x, y, 17), Blocks.STONE); }
        CompanionEntity oleg = spawn(h, 9, 6, 15);
        var owner = player(h, oleg);
        var at = h.absoluteVec(new net.minecraft.world.phys.Vec3(7.5, 6, 15.5));
        owner.teleportTo(at.x, at.y, at.z);
        oleg.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        oleg.setItem(0, new ItemStack(Items.OAK_PLANKS, 12));
        oleg.setItem(1, new ItemStack(Items.BEEF, 1));
        h.assertTrue(CompanionEvents.executeAction(owner, "smelt_items", "minecraft:beef", 1).success(), "Not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!oleg.isWorking(), "Still working: " + oleg.getLastTaskResult());
            h.assertTrue(MarathonArena.count(oleg, Items.COOKED_BEEF) == 1, "Not cooked: " + oleg.getLastTaskResult());
            for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 1; y <= 8; y++) {
                var state = h.getBlockState(new BlockPos(x, y, z));
                if (state.is(Blocks.CRAFTING_TABLE) || state.is(Blocks.FURNACE))
                    h.assertTrue(y >= 6, state.getBlock() + " set up down in the cut at " + x + "," + y + "," + z);
            }
        });
    }

    /**
     * Marathon, «берег»: «стой» came while he was swimming after the player; in the next minute and a half he drifted forty blocks
     * across the sea into the corner of the arena. Told to stay in the water, he stays where he is (treading water).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 700)
    public static void toldToStayInTheWaterHeStaysPut(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= 6; y++)
            h.setBlock(new BlockPos(x, y, z), y == 0 || x < 2 || x > 29 || z < 2 || z > 29 ? (y <= 5 ? Blocks.BEDROCK : Blocks.AIR) : y <= 4 ? Blocks.WATER : Blocks.AIR);
        CompanionEntity oleg = spawn(h, 16, 3, 16);
        var owner = player(h, oleg);
        var at = h.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 6, 16.5));
        owner.teleportTo(at.x, at.y, at.z);
        oleg.setFollowing(true);
        var start = new net.minecraft.world.phys.Vec3[1];
        h.runAfterDelay(60, () -> { CompanionEvents.executeAction(owner, "wait", "", 1); start[0] = oleg.position(); });
        h.runAfterDelay(660, () -> {
            double moved = Math.hypot(oleg.getX() - start[0].x, oleg.getZ() - start[0].z);
            h.assertTrue(moved < 3, "Drifted " + String.format("%.1f", moved) + " blocks after «стой»");
            h.succeed();
        });
    }

    /**
     * Marathon, «долина»: a log fell into a dip under a bush; the leaves shut every cell it could be taken from, he gave it up and
     * it lay there for good. A player breaks a leaf or two and takes it. Here the log lies in a pit under a bush of natural leaves.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void aLogUnderTheLeavesOfABushIsTaken(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= 8; y++)
            h.setBlock(new BlockPos(x, y, z), y == 0 ? Blocks.BEDROCK : y <= 4 ? Blocks.DIRT : Blocks.AIR);
        h.setBlock(new BlockPos(10, 4, 10), Blocks.AIR);
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE, 1);
        for (int x = 9; x <= 11; x++) for (int z = 9; z <= 11; z++) for (int y = 5; y <= 6; y++) h.setBlock(new BlockPos(x, y, z), leaves);
        CompanionEntity oleg = spawn(h, 14, 5, 10);
        var by = h.absoluteVec(new net.minecraft.world.phys.Vec3(14.5, 5, 12.5));
        player(h, oleg).teleportTo(by.x, by.y, by.z);
        var in = h.absoluteVec(new net.minecraft.world.phys.Vec3(10.5, 4.1, 10.5));
        var log =new net.minecraft.world.entity.item.ItemEntity(h.getLevel(), in.x, in.y, in.z, new ItemStack(Items.OAK_LOG));
        log.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        h.getLevel().addFreshEntity(log);
        oleg.claimDrops(java.util.List.of(log));
        h.succeedWhen(() -> h.assertTrue(MarathonArena.count(oleg, Items.OAK_LOG) == 1, "The log stays under the bush: " + oleg.getLastTaskResult()));
    }

    /**
     * Marathon, «берег»: at the island's bank the route's next point was higher than a step; the game's movement control then
     * «jumps» towards it and holds the stroke forward until the feet touch ground — in deep water never. He swam straight on
     * past the island, deaf to his route and to «стой», forty blocks into the corner of the arena. Here the same moment: a point
     * above him at the wall of a pool; once it is gone, he stays where he is.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void aJumpTowardsTheBankDoesNotCarryHimAcrossTheWater(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= 6; y++)
            h.setBlock(new BlockPos(x, y, z), y == 0 || x < 2 || x > 29 || z < 2 || z > 29 ? Blocks.BEDROCK : y <= 4 ? Blocks.WATER : Blocks.AIR);
        CompanionEntity oleg = spawn(h, 16, 3, 16);
        oleg.setFollowing(false);
        var start = new net.minecraft.world.phys.Vec3[1];
        h.runAfterDelay(40, () -> {
            start[0] = oleg.position();
            oleg.getMoveControl().setWantedPosition(oleg.getX() + .3, oleg.getY() + 3, oleg.getZ(), 1.15);
        });
        h.runAfterDelay(240, () -> {
            double moved = Math.hypot(oleg.getX() - start[0].x, oleg.getZ() - start[0].z);
            h.assertTrue(moved < 2, "Carried " + String.format("%.1f", moved) + " blocks across the water by one jump");
            h.succeed();
        });
    }

    /**
     * Marathon, night in «долина»: «добудь дерева» came when he had gone twelve blocks off the player driving monsters away; from
     * there the forest by the player lay two blocks beyond the search, and he said «не нашёл доступных деревьев». The trees round
     * the player count too. Here a tree fifteen blocks from the player, forty from him; the search radius is sixteen.
     */
    @GameTest(template = "large_search", timeoutTicks = 2400)
    public static void treesByThePlayerCountWhenNoneAreByHim(GameTestHelper h) {
        for (int x = 0; x < 128; x++) for (int z = 0; z < 128; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        for (int y = 1; y <= 4; y++) h.setBlock(new BlockPos(75, y, 64), Blocks.OAK_LOG);
        CompanionEntity oleg = spawn(h, 20, 1, 64);
        var by = h.absoluteVec(new net.minecraft.world.phys.Vec3(60.5, 1, 64.5));
        player(h, oleg).teleportTo(by.x, by.y, by.z);
        oleg.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_AXE));
        oleg.startGatheringLogs(4);
        h.succeedWhen(() -> {
            h.assertTrue(!oleg.isGatheringTask(), "Still gathering: " + oleg.getLastTaskResult());
            h.assertTrue(MarathonArena.count(oleg, Items.OAK_LOG) >= 4, "No wood: " + oleg.getLastTaskResult());
        });
    }

    /**
     * «Дойти до игрока отовсюду»: he is on top of a pillar of soil seven blocks high and one wide. A player called down digs the
     * pillar away under his feet, block by block, instead of jumping off seven blocks. «Иди ко мне»: down without a fall that hurts.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void fromAHighPillarHeComesDownWithoutAFall(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= 12; y++)
            h.setBlock(new BlockPos(x, y, z), y == 0 ? Blocks.BEDROCK : Blocks.AIR);
        for (int y = 1; y <= 7; y++) h.setBlock(new BlockPos(10, y, 10), Blocks.DIRT);
        CompanionEntity oleg = spawn(h, 10, 8, 10);
        var owner = player(h, oleg);
        var at = h.absoluteVec(new net.minecraft.world.phys.Vec3(18.5, 1, 10.5));
        owner.teleportTo(at.x, at.y, at.z);
        float[] lowest = {oleg.getHealth()};
        h.onEachTick(() -> lowest[0] = Math.min(lowest[0], oleg.getHealth()));
        h.runAfterDelay(20, () -> h.assertTrue(CompanionEvents.executeAction(owner, "come", "", 1).success(), "Not accepted"));
        h.succeedWhen(() -> {
            h.assertTrue(oleg.distanceToSqr(owner) < 4 * 4 && oleg.onGround() && oleg.getY() < h.absolutePos(new BlockPos(0, 2, 0)).getY(),
                    "Not down with the player: at " + oleg.blockPosition().subtract(h.absolutePos(BlockPos.ZERO)).toShortString() + " " + oleg.getLastTaskResult());
            h.assertTrue(lowest[0] >= oleg.getMaxHealth() - 1, "Hurt on the way down: " + lowest[0] + " of " + oleg.getMaxHealth());
        });
    }

    static CompanionEntity spawn(GameTestHelper h, int x, int y, int z) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), x, y, z);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(20, 6, 20));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        return companion;
    }

    static net.minecraft.server.level.ServerPlayer player(GameTestHelper h, CompanionEntity companion) {
        return h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }
}

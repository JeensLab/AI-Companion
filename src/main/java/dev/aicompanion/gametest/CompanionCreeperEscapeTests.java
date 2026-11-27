package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The way out of a lit fuse in a block of rock with a cave cut into it, no mine and no order: a lit creeper one blow from death is struck,
 * and the way never leads past the creeper.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionCreeperEscapeTests {
    private static final int SIZE = 48, CENTER = 24, GROUND = 21, TIMEOUT = 600, TUNNEL_Y = 10, STAND = 6, FUSE_TICKS = 200;
    private static final int CHAMBER_REACH = 13, CHAMBER_HEIGHT = 4, AWAY_Z = 6, BURN_TICKS = 14;
    /** Health of a creeper that one blow kills (a stone sword does five). */
    private static final float ONE_BLOW = 3;
    /** The slope by the height at which he stands on each ledge, west to east, as the run's by its row z -47: a landing, steps of one, then drops of three, one, two, two. */
    private static final int[] FEET = {15, 15, 15, 14, 13, 12, 11, 8, 7, 5, 3, 2, 2, 2, 2, 2, 2};
    private static final int WEST = 28, NORTH = 30, WIDTH = 7, ROOF = 18, HIS_LEDGE = 5, ITS_LEDGE = 6, MIDDLE = 3, WATCH_TICKS = 200, NO_LANDING = 3, WIDER = 2, BESIDE = 2;
    private static final float KEPT = 10, AS_IN_THE_RUN = 15;
    private static final double STEP_DOWN = .5;
    /** What he says when he steps away from a creeper he does not fight. */
    private static final String WARY = "Рядом крипер";

    private CompanionCreeperEscapeTests() { }

    /**
     * Stuck where he stands (water under a ledge did that in run ct36 20261031 void-4: twenty ticks of the run went nowhere), a lit creeper
     * one blow from death two blocks off with a wall at its back, its fuse already half burned when he comes in sight, so that no blow
     * before the run is left: the blow that kills it is struck before the fuse burns down, and no blast comes.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aLitCreeperOneBlowFromDeathWithinReachIsFinishedNotRunFrom(GameTestHelper h) {
        rock(h);
        CompanionEntity c = rig(h);
        BlockPos foot = chamber(h);
        for (int dy = 0; dy < 2; dy++) h.getLevel().setBlock(foot.offset(STAND + 3, dy, 0), Blocks.STONE.defaultBlockState(), 2 | 16);
        place(c, foot.offset(0, 0, -AWAY_Z));
        c.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        Creeper creeper = spawn(h, new Vec3(foot.getX() + STAND + 2.5, foot.getY(), foot.getZ() + .5));
        creeper.setHealth(ONE_BLOW);
        creeper.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        creeper.ignite();
        float health = c.getHealth();
        long begun = h.getTick();
        h.onEachTick(() -> {
            long elapsed = h.getTick() - begun;
            if (elapsed == BURN_TICKS) place(c, foot.offset(STAND, 0, 0));
            String trace = " | tick " + elapsed + ", creeper " + (creeper.isAlive() ? "fuse " + String.format("%.2f", creeper.getSwelling(0)) + "," : "gone,") + " health " + creeper.getHealth()
                    + "; he: health " + c.getHealth() + " | " + report(c);
            h.assertTrue(c.getHealth() >= health, "The blast hurt him" + trace);
            if (creeper.isAlive()) {
                h.assertTrue(elapsed < FUSE_TICKS, "The lit creeper never blew" + trace);
                return;
            }
            h.assertTrue(creeper.getHealth() <= 0, "The creeper blew up, it was not finished with the sword" + trace);
            h.succeed();
        });
    }

    /** With the creeper one ledge below him on a slope with a dead end at its top: while the fuse burns he does not go down the slope, which is past the creeper, and the blast, if any, leaves him more than half his health. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aLitCreeperOnTheLedgeBelowIsNotRunPastDownTheSlope(GameTestHelper h) { onTheLedgeAbove(h, false); }

    /** The same with the bag of the run (cobblestone, which there goes for a wall): he does not go down the slope either, and keeps more than half his health. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aLitCreeperOnTheLedgeBelowIsNotRunPastWithBlocksForAWall(GameTestHelper h) { onTheLedgeAbove(h, true); }

    private static void onTheLedgeAbove(GameTestHelper h, boolean blocks) {
        rock(h);
        CompanionEntity c = rig(h);
        if (blocks) c.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        for (int i = 0; i < FEET.length; i++) for (int dz = 0; dz < WIDTH; dz++) for (int y = FEET[i]; y <= ROOF; y++) {
            h.getLevel().setBlock(h.absolutePos(new BlockPos(WEST + i, y, NORTH + dz)), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        place(c, h.absolutePos(new BlockPos(WEST + HIS_LEDGE, FEET[HIS_LEDGE], NORTH + MIDDLE)));
        Creeper creeper = spawn(h, Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(WEST + ITS_LEDGE, FEET[ITS_LEDGE], NORTH + MIDDLE))));
        creeper.setHealth(AS_IN_THE_RUN);
        creeper.setTarget(c);
        long begun = h.getTick();
        double ledge = c.getY();
        h.onEachTick(() -> {
            long elapsed = h.getTick() - begun;
            if (creeper.isAlive() && creeper.getTarget() == null) creeper.setTarget(c);
            String trace = " | tick " + elapsed + ", he at " + c.blockPosition().toShortString() + ", health " + c.getHealth() + ", creeper "
                    + (creeper.isAlive() ? "at " + creeper.blockPosition().toShortString() + ", gap " + String.format("%.1f", c.distanceTo(creeper)) + ", fuse "
                    + String.format("%.2f", creeper.getSwelling(0)) + ", health " + creeper.getHealth() : "gone") + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(c.getHealth() >= KEPT, "The blast took him below half his health" + trace);
            h.assertTrue(!creeper.isAlive() || creeper.getSwellDir() <= 0 || c.getY() > ledge - STEP_DOWN, "He ran down the slope past the lit creeper" + trace);
            if (!creeper.isAlive() || elapsed >= WATCH_TICKS) h.succeed();
        });
    }

    /**
     * The far side before the fuse is lit: the guard judges whether he has a way out, and it must judge as the run will find it. No landing
     * behind him here, the slope is wide enough to pass the creeper, and the creeper stands still three ledges below him, after him, too far
     * for its fuse: the only way out that gets clear is past it, so that is no way out, he has no room to fight it and does not go down to
     * meet it (the player is beside him, as he meets a creeper by the player). Its fuse is never lit.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void anUnlitCreeperBelowWithNoWayOutButPastItIsNotGoneDownTo(GameTestHelper h) {
        rock(h);
        CompanionEntity c = rig(h);
        for (int i = NO_LANDING; i < FEET.length; i++) for (int dz = -WIDER; dz < WIDTH + WIDER; dz++) for (int y = FEET[i]; y <= ROOF; y++) {
            h.getLevel().setBlock(h.absolutePos(new BlockPos(WEST + i, y, NORTH + dz)), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        BlockPos top = h.absolutePos(new BlockPos(WEST + NO_LANDING, FEET[NO_LANDING], NORTH + MIDDLE));
        place(c, top);
        // The player beside him; in creative the creeper hunts him, not the player.
        ServerPlayer player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        player.setGameMode(GameType.CREATIVE);
        BlockPos beside = top.offset(top.subtract(h.absolutePos(new BlockPos(WEST + NO_LANDING, FEET[NO_LANDING], NORTH + MIDDLE - BESIDE))));
        player.teleportTo(beside.getX() + .5, beside.getY(), beside.getZ() + .5);
        Creeper creeper = creeper(h, h.absolutePos(new BlockPos(WEST + ITS_LEDGE, FEET[ITS_LEDGE], NORTH + MIDDLE)));
        creeper.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        creeper.setTarget(c);
        long begun = h.getTick();
        boolean[] wary = {false};
        h.onEachTick(() -> {
            if (creeper.isAlive() && creeper.getTarget() == null) creeper.setTarget(c);
            String trace = " | tick " + (h.getTick() - begun) + ", he at " + rel(h, c.blockPosition()) + ", health " + c.getHealth() + ", creeper "
                    + (creeper.isAlive() ? "gap " + String.format("%.1f", c.distanceTo(creeper)) + ", health " + creeper.getHealth() : "gone") + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(creeper.isAlive() && creeper.getSwellDir() <= 0, "He went down to a creeper with no way out but past it, and its fuse was lit" + trace);
            wary[0] |= c.getLastTaskResult().contains(WARY);
            if (h.getTick() - begun < WATCH_TICKS) return;
            h.assertTrue(wary[0], "He took no notice of the creeper at all" + trace);
            h.succeed();
        });
    }

    /** A chamber cut into the rock under a roof, wide enough to cross; returns its first floor cell. */
    private static BlockPos chamber(GameTestHelper h) {
        BlockPos foot = h.absolutePos(new BlockPos(CENTER - STAND, TUNNEL_Y, CENTER));
        for (int dx = 0; dx <= CHAMBER_REACH; dx++) for (int dz = -CHAMBER_REACH / 2; dz <= CHAMBER_REACH / 2; dz++) for (int dy = 0; dy < CHAMBER_HEIGHT; dy++) {
            h.getLevel().setBlock(foot.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        return foot;
    }

    /** Bedrock floor and walls, stone, dirt and grass up to the ground: the rock the caves of these tests are cut into. */
    private static void rock(GameTestHelper h) {
        var level = h.getLevel();
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) for (int y = 0; y <= GROUND; y++) {
            boolean edge = x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1;
            Block block = y == 0 || edge ? Blocks.BEDROCK : y < GROUND - 2 ? Blocks.STONE : y < GROUND ? Blocks.DIRT : Blocks.GRASS_BLOCK;
            level.setBlock(h.absolutePos(new BlockPos(x, y, z)), block.defaultBlockState(), 2 | 16);
        }
    }

    /** The companion on the rock with a stone sword in hand, and his owner by him. */
    private static CompanionEntity rig(GameTestHelper h) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), CENTER, GROUND + 1, CENTER);
        var owner = h.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - 3, companion.getY(), companion.getZ() - 3);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        return companion;
    }

    private static String report(CompanionEntity c) { return c.getLastTaskResult() + " | bag: " + c.getStoredItemsSummary(); }

    private static String rel(GameTestHelper h, BlockPos world) { return world.toShortString(); }

    private static void place(CompanionEntity c, BlockPos stand) { c.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5); }

    private static Creeper creeper(GameTestHelper h, BlockPos at) { return spawn(h, Vec3.atBottomCenterOf(at)); }

    /** At a world position, whatever way the structure is turned. */
    private static Creeper spawn(GameTestHelper h, Vec3 at) {
        Creeper creeper = EntityType.CREEPER.create(h.getLevel());
        creeper.moveTo(at.x, at.y, at.z, 0, 0);
        h.getLevel().addFreshEntity(creeper);
        creeper.setPersistenceRequired();
        return creeper;
    }
}

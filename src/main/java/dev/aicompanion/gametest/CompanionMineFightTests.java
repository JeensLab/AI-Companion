package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.carveMine;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.count;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.ended;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.mountain;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rel;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.report;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rig;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.start;

/**
 * Hostile mobs in the mine, live (nothing is switched off with NoAI and a stuck creeper is not taken away): he fights them with the sword
 * in the hand and goes on with the order. A creeper is struck with the sword too; when its fuse is lit he steps back, and he does not
 * come back to it until the fuse is out.
 */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineFightTests {
    private static final int CHAMBER_REACH = 13, CHAMBER_HEIGHT = 4, OPEN_CAVE_STAND = 3, KILL_TICKS = 1200, FUSE_TICKS = 200, CRAFT_TICKS = 1200;
    private static final float SURVIVES = 1;

    private CompanionMineFightTests() { }

    /** Three zombies come down the one-wide stairs at him, one behind another, while he digs at the foot: all three die, he lives and the order ends with the diamond. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void threeZombiesOnTheStairsAreKilledAndTheOrderGoesOn(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = armed(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        int foot = stairs.size() - 1;
        place(c, stairs.get(foot - 3));
        List<Zombie> zombies = new ArrayList<>();
        for (int k : new int[]{8, 11, 14}) zombies.add(zombie(h, stairs.get(foot - k)));
        long id = start(h, c, 1);
        float[] lowest = {c.getMaxHealth()};
        h.onEachTick(() -> {
            lowest[0] = Math.min(lowest[0], c.getHealth());
            for (Zombie zombie : zombies) if (zombie.isAlive() && zombie.getTarget() == null && h.getTick() % 20 == 0) zombie.setTarget(c);
            long alive = zombies.stream().filter(Zombie::isAlive).count();
            String trace = " | zombies alive " + alive + ", lowest health " + lowest[0] + ", he at " + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (c.isWorking() || c.isGatheringTask()) return;
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(alive == 0, "The order ended with zombies alive" + trace);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "The order did not go on to the diamond: " + result.message() + trace);
            h.succeed();
        });
    }

    /** A creeper crosses a wide chamber at the foot of the stairs towards him: it dies of the sword and he lives (an exploded one is removed, a struck one is dead). */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aCreeperInAnOpenCaveIsKilledWithTheSword(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = armed(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        BlockPos foot = chamber(h, stairs);
        place(c, foot.offset(OPEN_CAVE_STAND, 0, 0));
        Creeper creeper = creeper(h, foot.offset(CHAMBER_REACH, 0, 0));
        start(h, c, 1);
        long begun = h.getTick();
        h.onEachTick(() -> {
            String trace = " | creeper " + (creeper.isDeadOrDying() ? "killed" : creeper.isAlive() ? "alive, health " + creeper.getHealth() + ", fuse " + creeper.getSwellDir() : "gone") + ", he at "
                    + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (creeper.isDeadOrDying()) { h.assertTrue(c.getHealth() >= SURVIVES, "Too weak" + trace); h.succeed(); return; }
            h.assertTrue(creeper.isAlive(), "The creeper blew up, it was not struck" + trace);
            h.assertTrue(h.getTick() - begun < KILL_TICKS, "The creeper was not killed in time" + trace);
        });
    }

    /** The fuse is lit and cannot be put out (flint and steel): he is beside it, and gets away beyond the blast in time. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aLitFuseSendsHimBackAndHeLives(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = armed(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        BlockPos foot = chamber(h, stairs);
        place(c, foot.offset(OPEN_CAVE_STAND, 0, 0));
        Creeper creeper = creeper(h, foot.offset(OPEN_CAVE_STAND + 2, 0, 0));
        creeper.ignite();
        start(h, c, 1);
        long begun = h.getTick();
        h.onEachTick(() -> {
            String trace = " | creeper " + (creeper.isAlive() ? "alive" : "gone") + ", he at " + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (creeper.isAlive() && h.getTick() - begun < FUSE_TICKS) return;
            h.assertTrue(!creeper.isAlive(), "The creeper never blew" + trace);
            h.assertTrue(c.getHealth() >= c.getMaxHealth() - 1, "He was hurt by the blast" + trace);
            h.succeed();
        });
    }

    /** A sword is not in the bag, stone and sticks are: a real stone sword is made at a bench, as the pickaxe is, and the bench comes up again. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void makesAStoneSwordFromStoneAndSticksWhenHeHasNone(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        c.setItem(6, new ItemStack(Items.STICK, 6));
        c.setItem(7, new ItemStack(Items.CRAFTING_TABLE));
        carveMine(h, c, 0);
        start(h, c, 1);
        long begun = h.getTick();
        h.onEachTick(() -> {
            h.assertTrue(c.isAlive(), "Died | " + report(c));
            if (count(c, Items.STONE_SWORD) >= 1) h.succeed();
            h.assertTrue(h.getTick() - begun < CRAFT_TICKS, "No stone sword was made | " + report(c));
        });
    }

    private static CompanionEntity armed(GameTestHelper h) {
        CompanionEntity c = rig(h);
        c.setItem(6, new ItemStack(Items.STONE_SWORD));
        return c;
    }

    /** A chamber at the foot of the stairs, wide enough to cross; returns the foot. */
    private static BlockPos chamber(GameTestHelper h, List<BlockPos> stairs) {
        BlockPos foot = stairs.get(stairs.size() - 1);
        for (int dx = 0; dx <= CHAMBER_REACH; dx++) for (int dz = -CHAMBER_REACH / 2; dz <= CHAMBER_REACH / 2; dz++) for (int dy = 0; dy < CHAMBER_HEIGHT; dy++) {
            h.getLevel().setBlock(foot.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        return foot;
    }

    private static void place(CompanionEntity c, BlockPos stand) {
        c.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
    }

    private static Creeper creeper(GameTestHelper h, BlockPos at) {
        Creeper creeper = h.spawn(EntityType.CREEPER, rel(h, at));
        creeper.setPersistenceRequired();
        return creeper;
    }

    private static Zombie zombie(GameTestHelper h, BlockPos at) {
        Zombie zombie = h.spawn(EntityType.ZOMBIE, rel(h, at));
        zombie.setPersistenceRequired();
        zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION));
        return zombie;
    }
}

package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionExpeditionRecord;
import dev.aicompanion.entity.CompanionMineSite;
import dev.aicompanion.entity.CompanionShaftPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The mine record read back from NBT moves the base of the tunnels exactly as the one that was saved: same direction, same walk, never up to the surface. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineReloadTests {
    private static final int STAIRS = 12, WEST_LENGTH = 10, SOUTH_LENGTH = 3, NORTH_LENGTH = 2, KEPT_AHEAD = 2, SECOND_LENGTH = 9, FAR_END = 18, WALK_BACK_TICKS = 400, NEAR_FOOT = 8;

    /** Three tunnels off the foot are saved and loaded, the base moves, and the moved base is saved and loaded again with its first tunnel. */
    @GameTest(template = "marathon_cave")
    public static void movedBaseKeepsItsDirectionOverASave(GameTestHelper h) {
        BlockPos entrance = h.absolutePos(new BlockPos(2, 20, 2));
        CompanionMineSite site = new CompanionMineSite();
        site.start(entrance, Direction.NORTH);
        for (BlockPos stand : CompanionShaftPlan.stands(entrance, Direction.NORTH, STAIRS).subList(1, STAIRS + 1)) site.reached(stand);
        dig(site, Direction.WEST, WEST_LENGTH);
        dig(site, Direction.SOUTH, SOUTH_LENGTH);
        dig(site, Direction.NORTH, NORTH_LENGTH);

        CompanionMineSite loaded = reloaded(site);
        h.assertTrue(loaded.tunnels().equals(site.tunnels()) && loaded.foot().equals(site.foot()) && loaded.stairDirection() == Direction.NORTH,
                "The tunnels or the foot changed over the save: " + loaded.tunnels());
        CompanionExpeditionRecord.Base before = CompanionExpeditionRecord.moveBase(site, false), after = CompanionExpeditionRecord.moveBase(loaded, false);
        h.assertTrue(before != null && after != null && before.equals(after) && after.direction() == Direction.WEST, "The base moved differently after the load: " + after);
        for (BlockPos cell : after.path()) h.assertTrue(cell.getY() == entrance.getY() - STAIRS, "The walk to the new base leaves the foot's level: " + cell.toShortString());
        h.assertTrue(loaded.tunnels().isEmpty() && loaded.stands().size() == STAIRS + WEST_LENGTH - KEPT_AHEAD && loaded.foot().equals(site.foot()), "The base is not at the end of the long tunnel: " + loaded.foot());

        BlockPos last = loaded.stands().get(loaded.stands().size() - 1), previous = loaded.stands().get(loaded.stands().size() - 2);
        h.assertTrue(Direction.getNearest(last.getX() - previous.getX(), 0, last.getZ() - previous.getZ()) == Direction.WEST, "The last move of the stands is not the base direction");
        dig(loaded, Direction.WEST, 2);
        CompanionMineSite again = reloaded(loaded);
        h.assertTrue(again.tunnels().equals(loaded.tunnels()) && again.stands().equals(loaded.stands()) && again.foot().equals(loaded.foot()), "The moved base changed over the second save: " + again.tunnels());

        dig(again, Direction.SOUTH, SOUTH_LENGTH);
        dig(again, Direction.NORTH, NORTH_LENGTH);
        CompanionMineSite third = reloaded(again);
        dig(third, Direction.WEST, SECOND_LENGTH);
        CompanionExpeditionRecord.Base second = CompanionExpeditionRecord.moveBase(third, false);
        h.assertTrue(second != null && second.direction() == Direction.WEST, "The second move of the base: " + second);
        h.succeed();
    }

    /** A save from before the base moved has no more than the old keys: it loads with the stairs' own direction and no tunnels. */
    @GameTest(template = "marathon_cave")
    public static void oldSaveWithoutTunnelsLoadsWithTheStairsDirection(GameTestHelper h) {
        BlockPos entrance = h.absolutePos(new BlockPos(2, 20, 2));
        CompanionMineSite site = new CompanionMineSite();
        site.start(entrance, Direction.EAST);
        for (BlockPos stand : CompanionShaftPlan.stands(entrance, Direction.EAST, STAIRS).subList(1, STAIRS + 1)) site.reached(stand);
        CompoundTag tag = new CompoundTag();
        site.save(tag);
        CompoundTag record = tag.getCompound(CompanionMineSite.KEY);
        record.remove("Tunnels");
        CompanionMineSite loaded = new CompanionMineSite();
        loaded.load(tag);
        h.assertTrue(loaded.exists() && loaded.tunnels().isEmpty() && loaded.stairDirection() == Direction.EAST && loaded.stands().size() == STAIRS
                && CompanionExpeditionRecord.moveBase(loaded, false) == null, "A save without tunnels loads wrong: " + loaded.tunnels());
        h.succeed();
    }

    /** He is loaded at the far end of a tunnel the foot's side of which was refused: the next tunnel starts from the foot, after the walk back, not from where he stands. */
    @GameTest(template = "marathon_cave", timeoutTicks = 600)
    public static void reloadAtTheEndOfARefusedTunnelWalksBackToTheFoot(GameTestHelper h) {
        CompanionMineBacktrackTests.mountain(h, false);
        CompanionEntity c = CompanionMineBacktrackTests.rig(h);
        CompanionMineBacktrackTests.carveMine(h, c, FAR_END);
        BlockPos foot = c.mineSite().foot(), end = foot.relative(Direction.WEST, FAR_END);
        c.mineSite().refuse(foot, Direction.WEST);
        c.teleportTo(end.getX() + .5, end.getY(), end.getZ() + .5);
        CompoundTag saved = new CompoundTag();
        c.saveWithoutId(saved);
        c.mineSite().clear();
        c.load(saved);
        h.assertTrue(c.mineSite().refused(foot, Direction.WEST) && c.mineSite().tunnels().size() == 1, "The refused tunnel was lost over the save");
        CompanionMineBacktrackTests.start(h, c, 1);
        h.runAfterDelay(WALK_BACK_TICKS, () -> {
            h.assertTrue(c.mineSite().tunnels().size() >= 2 && c.mineSite().stands().size() >= STAIRS && c.blockPosition().distManhattan(foot) <= NEAR_FOOT,
                    "He did not walk back to the foot: at " + CompanionMineBacktrackTests.rel(h, c.blockPosition()).toShortString() + " | " + CompanionMineBacktrackTests.report(c));
            h.succeed();
        });
    }

    private static void dig(CompanionMineSite site, Direction direction, int length) {
        site.openTunnel(direction);
        for (int k = 0; k < length; k++) site.extendTunnel();
    }

    private static CompanionMineSite reloaded(CompanionMineSite site) {
        CompoundTag tag = new CompoundTag();
        site.save(tag);
        CompanionMineSite loaded = new CompanionMineSite();
        loaded.load(tag);
        return loaded;
    }
}

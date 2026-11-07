package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * While he breaks a block his head looks at it, from the first swing (#105, «взгляд при копании»): a crop beside him
 * (the head turning to a new block) and a log above him (the pitch beyond a mob's 40°).
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionDigGazeTests {
    /** What one test saw on the ticks he swung at a block: how far his head looked away from its centre. */
    private static final class Gaze {
        int swings, off; float worst, firstWorst; BlockPos last; String worstAt = "";
        String summary() { return "swings=" + swings + " off(>" + LIMIT + ")=" + off + " worst=" + Math.round(worst) + " firstWorst=" + Math.round(firstWorst) + " at " + worstAt; }
    }
    private static final float LIMIT = 10F;

    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void heLooksAtEachCropHeHarvests(GameTestHelper h) {
        var c = flat(h);
        for (int x = 19; x <= 21; x++) for (int z = 15; z <= 17; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7));
            h.setBlock(new BlockPos(x, 1, z), Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
        }
        // A ripe crop breaks in one tick and his breaker lets it go at once: the swing is the tick a ripe crop disappears.
        Gaze g = new Gaze();
        var ripe = new java.util.HashSet<BlockPos>();
        for (int x = 19; x <= 21; x++) for (int z = 15; z <= 17; z++) ripe.add(new BlockPos(x, 1, z));
        h.onEachTick(() -> {
            for (BlockPos pos : List.copyOf(ripe)) {
                var state = h.getBlockState(pos);
                if (state.is(Blocks.WHEAT) && state.getValue(net.minecraft.world.level.block.CropBlock.AGE) == 7) continue;
                ripe.remove(pos);
                record(g, c, h.absolutePos(pos));
            }
        });
        c.startWork("harvest_crops", "any", 64);
        finish(h, c, g, "harvest");
    }

    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void heLooksUpAtTheLogAboveHim(GameTestHelper h) {
        var c = flat(h);
        for (int y = 1; y <= 5; y++) h.setBlock(new BlockPos(22, y, 16), Blocks.BIRCH_LOG);
        for (int x = 20; x <= 24; x++) for (int z = 14; z <= 18; z++) for (int y = 4; y <= 6; y++)
            if (h.getBlockState(new BlockPos(x, y, z)).isAir()) h.setBlock(new BlockPos(x, y, z), Blocks.BIRCH_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1));
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_AXE));
        Gaze g = watch(h, c);
        c.startGatheringLogs(2);
        h.succeedWhen(() -> {
            h.assertTrue(!c.isGatheringTask(), "Still gathering: " + c.getLastTaskResult());
            check(h, g, "wood");
        });
    }

    private static void finish(GameTestHelper h, CompanionEntity c, Gaze g, String name) {
        h.succeedWhen(() -> {
            h.assertTrue(!c.isWorking() && !c.isGatheringTask(), "Still working: " + c.getLastTaskResult());
            check(h, g, name);
        });
    }

    private static void check(GameTestHelper h, Gaze g, String name) {
        h.assertTrue(g.swings > 0, "No swing was seen (" + name + ")");
        h.assertTrue(g.off == 0, "He struck while looking away (" + name + "): " + g.summary());
    }

    /** Every tick he swung at a block this very tick: the angle between where his head looks and the block's centre. */
    private static Gaze watch(GameTestHelper h, CompanionEntity c) {
        Gaze g = new Gaze();
        h.onEachTick(() -> {
            BlockPos target = swingTarget(c);
            if (target != null) record(g, c, target);
        });
        return g;
    }

    /** One swing at the block (absolute position): how far his head looked away from its centre. */
    private static void record(Gaze g, CompanionEntity c, BlockPos target) {
        Vec3 to = Vec3.atCenterOf(target).subtract(c.getEyePosition()).normalize();
        Vec3 look = Vec3.directionFromRotation(c.getXRot(), c.getYHeadRot());
        float angle = (float) Math.toDegrees(Math.acos(Mth.clamp(look.dot(to), -1, 1)));
        if (!target.equals(g.last)) g.firstWorst = Math.max(g.firstWorst, angle);
        g.last = target;
        g.swings++;
        if (angle > LIMIT) g.off++;
        if (angle > g.worst) {
            float yaw = (float) (Math.toDegrees(Math.atan2(to.z, to.x)) - 90), pitch = (float) -Math.toDegrees(Math.asin(to.y));
            g.worst = angle;
            g.worstAt = "yawOff=" + Math.round(Mth.wrapDegrees(c.getYHeadRot() - yaw)) + " pitchOff=" + Math.round(c.getXRot() - pitch)
                    + " (needs pitch " + Math.round(pitch) + ") swing#" + g.swings;
        }
    }

    /** The block one of his breakers swung at on this tick (his breakers are private to the entity package: reflection). */
    private static BlockPos swingTarget(CompanionEntity c) {
        try {
            for (Object breaking : breakers(c)) {
                Field last = breaking.getClass().getDeclaredField("lastTick"); last.setAccessible(true);
                if ((long) last.get(breaking) != c.level().getGameTime()) continue;
                Field target = breaking.getClass().getDeclaredField("target"); target.setAccessible(true);
                if (target.get(breaking) instanceof BlockPos pos) return pos;
            }
            return null;
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    /** The entity's block breakers and those of its parts (work, loot, recovery, pillar). */
    private static List<Object> breakers(CompanionEntity c) throws ReflectiveOperationException {
        List<Object> found = new ArrayList<>();
        for (Field field : CompanionEntity.class.getDeclaredFields()) {
            if (field.getType().isPrimitive() || java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            Object value = field.get(c);
            if (value == null) continue;
            if (value.getClass().getSimpleName().equals("CompanionBlockBreaking")) { found.add(value); continue; }
            if (!value.getClass().getName().startsWith("dev.aicompanion.entity.")) continue;
            for (Field inner : value.getClass().getDeclaredFields()) {
                if (!inner.getType().getSimpleName().equals("CompanionBlockBreaking")) continue;
                inner.setAccessible(true);
                Object breaking = inner.get(value);
                if (breaking != null) found.add(breaking);
            }
        }
        return found;
    }

    private static CompanionEntity flat(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        var c = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var p = h.makeMockServerPlayerInLevel(); p.teleportTo(c.getX() - 3, c.getY(), c.getZ() - 3);
        c.setOwnerId(p.getUUID()); p.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID()); CompanionEvents.getOwnedCompanion(p);
        c.setFollowing(false); c.setLifestyle(false, true); c.setSearchRadius(16);
        return c;
    }
}

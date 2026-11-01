package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionAgenda;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionFacts;
import dev.aicompanion.entity.CompanionMilestones;
import dev.aicompanion.entity.CompanionPlaybooks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.carveMine;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.mountain;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rel;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.report;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.start;
import static dev.aicompanion.gametest.CompanionMineFightTests.armed;
import static dev.aicompanion.gametest.CompanionMineFightTests.chamber;
import static dev.aicompanion.gametest.CompanionMineFightTests.creeper;
import static dev.aicompanion.gametest.CompanionMineFightTests.place;

/**
 * The shield against a creeper's blast: made in the preparation of the mine out of what the bag already holds (real ingot, real boards),
 * held up at a lit fuse turned towards it, and worn down by the blows it takes like a player's. Nothing is given and nothing is immortal.
 */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionShieldTests {
    private static final String BATCH = "shield";
    private static final int STAND = 3, GAP = 2, FUSE_TICKS = 200, PREPARE_TICKS = 2400, DESCENT_TICKS = 4800, RAISE_TICKS = 8, LONG_FUSE = 30000, IGNITE_AFTER = 10, SHIELD_BOARDS = 6;
    private static final double STAYED = 1.5, FACING = 45, BEHIND = 90;

    private CompanionShieldTests() { }

    @AfterBatch(batch = BATCH) public static void afterShield(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    /** The fuse is lit two blocks off, the shield is in the left hand: he does not run, the blast does not hurt him and the shield pays for it. */
    @GameTest(template = "marathon_cave", batch = BATCH, timeoutTicks = TIMEOUT)
    public static void aShieldTakesTheBlastOfALitCreeper(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = armed(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        c.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        place(c, foot.offset(STAND, 0, 0));
        Creeper creeper = creeper(h, foot.offset(STAND + GAP, 0, 0));
        creeper.ignite();
        start(h, c, 1);
        Vec3 stand = c.position();
        long begun = h.getTick();
        double[] drift = {0};
        boolean[] raised = {false};
        h.onEachTick(() -> {
            String trace = " | creeper " + (creeper.isAlive() ? "alive" : "gone") + ", he at " + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth()
                    + ", shield " + c.getOffhandItem() + " wear " + c.getOffhandItem().getDamageValue() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (creeper.isAlive() && h.getTick() - begun < FUSE_TICKS) {
                drift[0] = Math.max(drift[0], c.position().distanceTo(stand));
                raised[0] |= c.isBlocking();
                return;
            }
            h.assertTrue(!creeper.isAlive(), "The creeper never blew" + trace);
            h.assertTrue(raised[0], "The shield was never up before the blast" + trace);
            h.assertTrue(drift[0] < STAYED, "He ran " + drift[0] + " blocks instead of holding the shield" + trace);
            h.assertTrue(c.getHealth() >= c.getMaxHealth(), "He was hurt by the blast" + trace);
            h.assertTrue(c.getOffhandItem().is(Items.SHIELD) && c.getOffhandItem().getDamageValue() > 0, "The shield took no wear" + trace);
            h.succeed();
        });
    }

    /** The creeper is behind his back when its fuse is lit: head and body turn to it, the shield is up before the blast and holds. */
    @GameTest(template = "marathon_cave", batch = BATCH, timeoutTicks = TIMEOUT)
    public static void aCreeperLitBehindHimIsFacedAndHeld(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = armed(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        c.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        place(c, foot.offset(STAND, 0, 0));
        Creeper creeper = creeper(h, foot.offset(STAND + GAP, 0, 0));
        creeper.setNoAi(true);
        float away = yawTo(c, creeper) + 180;
        c.setYRot(away); c.setYHeadRot(away); c.setYBodyRot(away);
        long[] lit = {-1};
        boolean[] facedAndUp = {false};
        h.runAfterDelay(IGNITE_AFTER, () -> {
            h.assertTrue(angle(c, creeper) > BEHIND, "He was not turned away from the creeper to begin with: " + angle(c, creeper));
            creeper.setNoAi(false);
            creeper.ignite();
            lit[0] = h.getTick();
        });
        h.onEachTick(() -> {
            String trace = " | creeper " + (creeper.isAlive() ? "alive" : "gone") + ", fuse " + creeper.getSwellDir() + ", health " + c.getHealth() + ", turned off by "
                    + angle(c, creeper) + ", shield wear " + c.getOffhandItem().getDamageValue() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (lit[0] < 0) return;
            if (creeper.isAlive() && h.getTick() - lit[0] < FUSE_TICKS) {
                facedAndUp[0] |= c.isBlocking() && angle(c, creeper) < FACING;
                return;
            }
            h.assertTrue(!creeper.isAlive(), "The creeper never blew" + trace);
            h.assertTrue(facedAndUp[0], "He never stood facing the creeper with the shield up" + trace);
            h.assertTrue(c.getHealth() >= c.getMaxHealth(), "He was hurt by the blast" + trace);
            h.assertTrue(c.getOffhandItem().getDamageValue() > 0, "The shield took no wear" + trace);
            h.succeed();
        });
    }

    /** Without a shield nothing changes: a lit fuse sends him back as before, and he never stands up a shield he does not have. */
    @GameTest(template = "marathon_cave", batch = BATCH, timeoutTicks = TIMEOUT)
    public static void withoutAShieldALitFuseStillSendsHimBack(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = armed(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        place(c, foot.offset(STAND, 0, 0));
        Creeper creeper = creeper(h, foot.offset(STAND + GAP, 0, 0));
        creeper.ignite();
        start(h, c, 1);
        long begun = h.getTick();
        h.onEachTick(() -> {
            String trace = " | creeper " + (creeper.isAlive() ? "alive" : "gone") + ", he at " + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(!c.isBlocking(), "He blocks with nothing in the left hand" + trace);
            if (creeper.isAlive() && h.getTick() - begun < FUSE_TICKS) return;
            h.assertTrue(!creeper.isAlive(), "The creeper never blew" + trace);
            h.assertTrue(c.getHealth() >= c.getMaxHealth() - 1, "He was hurt by the blast" + trace);
            h.succeed();
        });
    }

    /** Wear is a player's: a blow of under 3 costs nothing, one of 3 or more costs 1 plus its damage, and a shield worn to nothing is gone, with the blow absorbed. */
    @GameTest(template = "marathon_cave", batch = BATCH, timeoutTicks = TIMEOUT)
    public static void theShieldWearsAndBreaksLikeAPlayers(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = armed(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        c.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        place(c, foot.offset(STAND, 0, 0));
        Creeper creeper = creeper(h, foot.offset(STAND + GAP, 0, 0));
        CompoundTag fuse = new CompoundTag();
        creeper.addAdditionalSaveData(fuse);
        fuse.putShort("Fuse", (short) LONG_FUSE);
        fuse.putBoolean("ignited", true);
        creeper.readAdditionalSaveData(fuse);
        int[] stage = {0};
        h.runAtTickTime(RAISE_TICKS * 10, () -> h.assertTrue(stage[0] > 0, "The shield was never up against the lit fuse"));
        h.onEachTick(() -> {
            if (!c.isBlocking()) return;
            ItemStack shield = c.getOffhandItem();
            float health = c.getHealth();
            int worn = shield.getDamageValue();
            c.invulnerableTime = 0;
            switch (stage[0]++) {
                case 0 -> {
                    c.hurt(h.getLevel().damageSources().explosion(creeper, creeper), 2);
                    h.assertTrue(c.getHealth() >= health && shield.getDamageValue() == worn, "A blow under 3 hurt him or wore the shield: health " + c.getHealth() + ", wear " + shield.getDamageValue());
                }
                case 1 -> {
                    c.hurt(h.getLevel().damageSources().explosion(creeper, creeper), 10);
                    h.assertTrue(c.getHealth() >= health, "The blow of 10 got through: health " + health + " to " + c.getHealth());
                    h.assertTrue(shield.getDamageValue() == worn + 11, "A blow of 10 cost " + (shield.getDamageValue() - worn) + " of wear, not 11");
                    shield.setDamageValue(shield.getMaxDamage() - 5);
                }
                default -> {
                    c.hurt(h.getLevel().damageSources().explosion(creeper, creeper), 10);
                    h.assertTrue(c.getHealth() >= health, "The blow that broke the shield got through: health " + health + " to " + c.getHealth());
                    h.assertTrue(c.getOffhandItem().isEmpty() && !c.isBlocking(), "The shield worn to nothing is still in the hand: " + c.getOffhandItem());
                    h.succeed();
                }
            }
        });
    }

    /** Spare iron and boards in the bag: the preparation makes a real shield (the ingot and the boards are spent) and puts it in the left hand. */
    @GameTest(template = "marathon_cave", batch = BATCH, timeoutTicks = TIMEOUT)
    public static void withSpareIronAndBoardsThePreparationMakesAndWearsAShield(GameTestHelper h) {
        CompanionDiamondGoalTests.Rig r = CompanionDiamondGoalTests.rig(h, false, kit(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.OAK_PLANKS, SHIELD_BOARDS)));
        CompanionDiamondGoalTests.start(h, r, 1);
        h.runAtTickTime(PREPARE_TICKS, () -> h.fail("No shield in the left hand in time | " + CompanionDiamondGoalTests.report(h, r)));
        h.onEachTick(() -> {
            CompanionEntity c = r.c();
            h.assertTrue(c.isAlive(), "Died | " + CompanionDiamondGoalTests.report(h, r));
            if (!c.getOffhandItem().is(Items.SHIELD)) return;
            h.assertTrue(CompanionDiamondGoalTests.count(c, Items.SHIELD) == 1, "More than one shield: " + CompanionDiamondGoalTests.report(h, r));
            h.assertTrue(CompanionDiamondGoalTests.count(c, Items.IRON_INGOT) == 0, "The ingot was not spent: " + CompanionDiamondGoalTests.report(h, r));
            h.assertTrue(planks(c) == 0, "The boards were not spent, " + planks(c) + " left: " + CompanionDiamondGoalTests.report(h, r));
            h.succeed();
        });
    }

    /** No iron in the bag (boards only): the shield is not fetched, and the descent begins as it always did. */
    @GameTest(template = "marathon_cave", batch = BATCH, timeoutTicks = TIMEOUT)
    public static void withoutIronThePreparationGoesDownAsBefore(GameTestHelper h) {
        goesDownWith(h, ItemStack.EMPTY, new ItemStack(Items.OAK_PLANKS, SHIELD_BOARDS));
    }

    /** A shield worn to nothing counts as none: with no iron to make another the goal still goes down. */
    @GameTest(template = "marathon_cave", batch = BATCH, timeoutTicks = TIMEOUT)
    public static void aBrokenShieldDoesNotStopTheGoal(GameTestHelper h) {
        ItemStack broken = new ItemStack(Items.SHIELD);
        broken.setDamageValue(broken.getMaxDamage());
        goesDownWith(h, broken);
    }

    /** What the mine step plans for a bag, as the agenda reads it: the shield is made from what is carried, never fetched, and only put on when it is there. */
    @GameTest(template = "tall_tree", batch = BATCH, timeoutTicks = 100)
    public static void theMineStepPlansAShieldOnlyFromWhatIsCarried(GameTestHelper h) {
        CompanionEntity c = CompanionDiamondGoalTests.bare(h).c();
        c.setItem(0, new ItemStack(Items.CRAFTING_TABLE));
        c.setItem(1, new ItemStack(Items.STICK, 8));
        c.setItem(2, new ItemStack(Items.STONE_SWORD));
        h.assertTrue(shieldSteps(c).isEmpty(), "Empty bag: " + shieldSteps(c));
        c.setItem(3, new ItemStack(Items.OAK_PLANKS, SHIELD_BOARDS));
        h.assertTrue(shieldSteps(c).isEmpty(), "Boards and no iron: " + shieldSteps(c));
        c.setItem(4, new ItemStack(Items.RAW_IRON));
        h.assertTrue(shieldSteps(c).isEmpty(), "Raw iron and no furnace: " + shieldSteps(c));
        c.setItem(5, new ItemStack(Items.FURNACE));
        h.assertTrue(shieldSteps(c).equals(List.of("obtain_item minecraft:shield", "equip_item minecraft:shield offhand")), "Raw iron and a furnace: " + shieldSteps(c));
        c.setItem(4, new ItemStack(Items.IRON_INGOT));
        h.assertTrue(shieldSteps(c).equals(List.of("obtain_item minecraft:shield", "equip_item minecraft:shield offhand")), "An ingot: " + shieldSteps(c));
        c.setItem(3, new ItemStack(Items.OAK_PLANKS, SHIELD_BOARDS - 1));
        h.assertTrue(shieldSteps(c).isEmpty(), "Five boards: " + shieldSteps(c));
        c.setItem(3, new ItemStack(Items.OAK_LOG, 2));
        h.assertTrue(shieldSteps(c).size() == 2, "Two logs make the boards: " + shieldSteps(c));
        c.setItem(2, ItemStack.EMPTY);
        c.setItem(4, new ItemStack(Items.IRON_INGOT, 2));
        h.assertTrue(shieldSteps(c).isEmpty(), "The sword he lacks takes both ingots: " + shieldSteps(c));
        c.setItem(4, new ItemStack(Items.IRON_INGOT, 3));
        h.assertTrue(shieldSteps(c).size() == 2, "A third ingot is spare: " + shieldSteps(c));
        c.setItem(2, new ItemStack(Items.STONE_SWORD));
        c.setItem(3, ItemStack.EMPTY);
        c.setItem(4, ItemStack.EMPTY);
        c.setItem(5, new ItemStack(Items.SHIELD));
        h.assertTrue(shieldSteps(c).equals(List.of("equip_item minecraft:shield offhand")), "A shield in the bag is only put on: " + shieldSteps(c));
        c.mineSite().start(c.blockPosition().above(20), net.minecraft.core.Direction.NORTH);
        h.assertTrue(shieldSteps(c).equals(List.of("equip_item minecraft:shield offhand")), "Down in the mine after a death the shield is put on again: " + shieldSteps(c));
        c.setItem(6, new ItemStack(Items.IRON_INGOT, 3));
        c.setItem(7, new ItemStack(Items.OAK_PLANKS, 16));
        c.setItem(5, ItemStack.EMPTY);
        h.assertTrue(shieldSteps(c).isEmpty(), "Down in the mine nothing is made: " + shieldSteps(c));
        c.mineSite().clear();
        c.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        h.assertTrue(shieldSteps(c).isEmpty(), "A shield in the left hand needs nothing: " + shieldSteps(c));
        h.succeed();
    }

    private static void goesDownWith(GameTestHelper h, ItemStack offhand, ItemStack... extra) {
        CompanionDiamondGoalTests.Rig r = CompanionDiamondGoalTests.rig(h, false, kit(extra));
        r.c().setItemSlot(EquipmentSlot.OFFHAND, offhand);
        CompanionDiamondGoalTests.start(h, r, 1);
        h.runAtTickTime(DESCENT_TICKS, () -> h.fail("Did not go down in time | " + CompanionDiamondGoalTests.report(h, r)));
        h.onEachTick(() -> {
            CompanionEntity c = r.c();
            h.assertTrue(c.isAlive(), "Died | " + CompanionDiamondGoalTests.report(h, r));
            h.assertTrue(CompanionDiamondGoalTests.state(h, r) != CompanionAgenda.State.BLOCKED, "The goal stopped | " + CompanionDiamondGoalTests.report(h, r));
            h.assertTrue(CompanionDiamondGoalTests.count(c, Items.SHIELD) <= 1 && CompanionDiamondGoalTests.count(c, Items.IRON_INGOT) == 0, "A shield out of nothing | " + CompanionDiamondGoalTests.report(h, r));
            if (c.mineSite().exists() && CompanionDiamondGoalTests.advanced(c) >= 1) h.succeed();
        });
    }

    private static ItemStack[] kit(ItemStack... extra) {
        List<ItemStack> bag = new ArrayList<>(Arrays.asList(CompanionDiamondGoalTests.supplies(32, 4)));
        bag.addAll(Arrays.asList(extra));
        return bag.toArray(new ItemStack[0]);
    }

    private static List<String> shieldSteps(CompanionEntity c) {
        var book = CompanionPlaybooks.of("find_diamonds", 3);
        List<CompanionMilestones.Step> steps = book.milestones().getLast().method(CompanionFacts.of(c), false, book.milestones());
        return steps.stream().filter(step -> step.item().equals("minecraft:shield")).map(step -> step.type() + " " + step.item() + (step.type().equals("equip_item") ? " " + step.slot() : "")).toList();
    }

    private static int planks(CompanionEntity c) {
        int n = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(net.minecraft.tags.ItemTags.PLANKS)) n += c.getItem(slot).getCount();
        return n;
    }

    /** The yaw that looks from him at the creeper. */
    private static float yawTo(CompanionEntity c, Creeper creeper) {
        return (float) (Mth.atan2(creeper.getZ() - c.getZ(), creeper.getX() - c.getX()) * Mth.RAD_TO_DEG) - 90.0F;
    }

    /** How far, in degrees, his head is turned off the creeper. */
    private static double angle(CompanionEntity c, Creeper creeper) {
        return Math.abs(Mth.wrapDegrees(c.getYHeadRot() - yawTo(c, creeper)));
    }
}

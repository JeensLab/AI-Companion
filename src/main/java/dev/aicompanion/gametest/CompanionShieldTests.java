package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionAgenda;
import dev.aicompanion.entity.CompanionAgendaData;
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
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.report;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.start;

/**
 * The shield against a creeper's blast: made in the preparation of the mine out of what the bag already holds (real ingot, real boards),
 * held up at a lit fuse turned towards it, and worn down by the blows it takes like a player's. Nothing is given and nothing is immortal.
 */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionShieldTests {
    private static final String BATCH = "shield";
    private static final int PREPARE_TICKS = 2400, DESCENT_TICKS = 4800, SHIELD_BOARDS = 6;
    /** The whole preparation from a bag without the iron pickaxe: ore, furnace, bench, trees, then the shield; and the iron outcrop's place on the grass. */
    private static final int FETCH_TICKS = 14000, ORE_X = CompanionDiamondGoalTests.CENTER + 3, ORE_Z = CompanionDiamondGoalTests.CENTER + 3, TREE_SPAN = 6, TRUNK = 5;
    /** The rig's radius of 16 makes ore "in sight" only within 8 blocks, and the trees he fells first stand up to 15 from the outcrop; the default of 64 sees 24. */
    private static final int WIDE_SEARCH = 48;

    private CompanionShieldTests() { }

    @AfterBatch(batch = BATCH) public static void afterShield(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

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

    /**
     * The bag has no iron pickaxe, the grass has four iron ores in the open and two trees: the preparation mines three ores for the pickaxe and
     * the fourth for a shield, smelts, makes both at the bench, and goes down with the shield in the left hand. Nothing is given: the ingots are spent
     * to the last one. The saved goal remembers that it went out for the iron.
     */
    @GameTest(template = "marathon_cave", batch = BATCH, timeoutTicks = FETCH_TICKS)
    public static void thePreparationFetchesTheIronOfAShieldFromOreNearAndWearsIt(GameTestHelper h) {
        CompanionDiamondGoalTests.Rig r = ironless(h, 4);
        CompanionDiamondGoalTests.start(h, r, 1);
        h.runAtTickTime(FETCH_TICKS - 5, () -> h.fail("Not down with a shield in time | " + CompanionDiamondGoalTests.report(h, r)));
        h.onEachTick(() -> {
            CompanionEntity c = r.c();
            h.assertTrue(c.isAlive(), "Died | " + CompanionDiamondGoalTests.report(h, r));
            h.assertTrue(CompanionDiamondGoalTests.state(h, r) != CompanionAgenda.State.BLOCKED, "The goal stopped | " + CompanionDiamondGoalTests.report(h, r));
            if (CompanionDiamondGoalTests.advanced(c) < 1) return;
            String bag = " | " + CompanionDiamondGoalTests.report(h, r);
            h.assertTrue(c.getOffhandItem().is(Items.SHIELD), "Went down without a shield in the left hand" + bag);
            h.assertTrue(CompanionDiamondGoalTests.count(c, Items.IRON_PICKAXE) == 1, "Went down without the iron pickaxe" + bag);
            h.assertTrue(CompanionDiamondGoalTests.count(c, Items.STONE_SWORD) + CompanionDiamondGoalTests.count(c, Items.IRON_SWORD) >= 1, "Went down without a sword" + bag);
            h.assertTrue(CompanionDiamondGoalTests.count(c, Items.IRON_INGOT) == 0 && CompanionDiamondGoalTests.count(c, Items.RAW_IRON) == 0, "Iron left over" + bag);
            h.assertTrue(CompanionDiamondGoalTests.goal(h, r).shieldTrek(), "The goal does not know it went out for the shield" + bag);
            var access = h.getLevel().registryAccess();
            var loaded = CompanionAgendaData.load(CompanionAgendaData.get(h.getLevel().getServer()).save(new CompoundTag(), access), access).active(r.p().getUUID());
            h.assertTrue(loaded != null && loaded.shieldTrek(), "A save and a load gave the trip for the iron back" + bag);
            h.succeed();
        });
    }

    /** Iron for the pickaxe and not an ore more: no shield, no second trip, no stop; the goal goes down as it did. (Green on the old code too: it guards the new one.) */
    @GameTest(template = "marathon_cave", batch = BATCH, timeoutTicks = FETCH_TICKS)
    public static void withIronOnlyForThePickaxeTheGoalGoesDownWithoutAShield(GameTestHelper h) {
        CompanionDiamondGoalTests.Rig r = ironless(h, 3);
        CompanionDiamondGoalTests.start(h, r, 1);
        h.runAtTickTime(FETCH_TICKS - 5, () -> h.fail("Did not go down in time | " + CompanionDiamondGoalTests.report(h, r)));
        h.onEachTick(() -> {
            CompanionEntity c = r.c();
            h.assertTrue(c.isAlive(), "Died | " + CompanionDiamondGoalTests.report(h, r));
            h.assertTrue(CompanionDiamondGoalTests.state(h, r) != CompanionAgenda.State.BLOCKED, "The goal stopped | " + CompanionDiamondGoalTests.report(h, r));
            h.assertTrue(CompanionDiamondGoalTests.count(c, Items.SHIELD) == 0, "A shield out of nothing | " + CompanionDiamondGoalTests.report(h, r));
            if (!c.mineSite().exists() || CompanionDiamondGoalTests.advanced(c) < 1) return;
            h.assertTrue(CompanionDiamondGoalTests.count(c, Items.IRON_PICKAXE) == 1, "Down without the iron pickaxe | " + CompanionDiamondGoalTests.report(h, r));
            h.succeed();
        });
    }

    /**
     * The run of its own that fetches the iron of a shield, which the agenda plans before the kit: only for ore lying in the open near him, only once
     * for the goal, never down in the mine, and the sword he lacks takes its ingots first. The kit of the mine step itself never fetches it: a shield
     * is made there only of what is carried.
     */
    @GameTest(template = "tall_tree", batch = BATCH, timeoutTicks = 100)
    public static void theMineStepGoesOutForShieldIronOnlyForOreInTheOpenAndOnlyOnce(GameTestHelper h) {
        CompanionEntity c = CompanionDiamondGoalTests.bare(h).c();
        BlockPos ore = new BlockPos(20, 1, 16);
        List<String> shield = List.of("obtain_item minecraft:shield", "equip_item minecraft:shield offhand");
        c.setItem(0, new ItemStack(Items.CRAFTING_TABLE));
        c.setItem(1, new ItemStack(Items.STICK, 8));
        c.setItem(2, new ItemStack(Items.STONE_SWORD));
        h.assertTrue(trekSteps(c, false).isEmpty(), "No ore anywhere: " + trekSteps(c, false));
        h.setBlock(ore, Blocks.IRON_ORE);
        h.assertTrue(trekSteps(c, false).equals(shield), "Iron ore in the open: " + trekSteps(c, false));
        h.assertTrue(shieldSteps(c, false).isEmpty(), "The kit makes nothing of ore in sight: " + shieldSteps(c, false));
        h.assertTrue(trekSteps(c, true).isEmpty(), "The goal has gone out for the shield once: " + trekSteps(c, true));
        c.setItem(3, new ItemStack(Items.IRON_INGOT, 2));
        c.setItem(2, ItemStack.EMPTY);
        h.assertTrue(trekSteps(c, false).equals(shield), "Two ingots are the sword's, the shield's iron is fetched: " + trekSteps(c, false));
        c.setItem(2, new ItemStack(Items.STONE_SWORD));
        h.assertTrue(trekSteps(c, false).isEmpty(), "A spare ingot: nothing to fetch: " + trekSteps(c, false));
        h.assertTrue(shieldSteps(c, true).isEmpty(), "A spare ingot and no boards: nothing to make: " + shieldSteps(c, true));
        c.setItem(4, new ItemStack(Items.OAK_PLANKS, SHIELD_BOARDS));
        h.assertTrue(trekSteps(c, false).isEmpty(), "A spare ingot and boards: made of what is carried, not fetched: " + trekSteps(c, false));
        h.assertTrue(shieldSteps(c, true).equals(shield), "After the trip a shield is made of what is carried: " + shieldSteps(c, true));
        c.setItem(3, ItemStack.EMPTY);
        c.setItem(4, ItemStack.EMPTY);
        h.setBlock(ore, Blocks.AIR);
        BlockPos buried = new BlockPos(25, 1, 25);
        h.setBlock(buried, Blocks.IRON_ORE);
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) if (side != net.minecraft.core.Direction.DOWN) h.setBlock(buried.relative(side), Blocks.STONE);
        h.assertTrue(trekSteps(c, false).isEmpty(), "Ore inside the rock is not seen: " + trekSteps(c, false));
        h.setBlock(ore, Blocks.IRON_ORE);
        CompanionDiamondGoalTests.underRoof(h, c, () -> {
            c.mineSite().start(c.blockPosition().above(20), net.minecraft.core.Direction.NORTH);
            h.assertTrue(trekSteps(c, false).isEmpty(), "Down in the mine nothing is fetched: " + trekSteps(c, false));
        });
    }

    /** Three ingots beyond the sword's make a second iron pickaxe on the surface, and the shield takes only what that leaves. */
    @GameTest(template = "tall_tree", batch = BATCH, timeoutTicks = 100)
    public static void threeSpareIngotsMakeASecondIronPickaxeAheadOfTheShield(GameTestHelper h) {
        CompanionEntity c = CompanionDiamondGoalTests.bare(h).c();
        c.setItem(0, new ItemStack(Items.CRAFTING_TABLE));
        c.setItem(1, new ItemStack(Items.STICK, 8));
        c.setItem(2, new ItemStack(Items.STONE_SWORD));
        c.setItem(3, new ItemStack(Items.OAK_PLANKS, SHIELD_BOARDS));
        c.setItem(4, new ItemStack(Items.IRON_PICKAXE));
        c.setItem(5, new ItemStack(Items.IRON_INGOT, 2));
        h.assertTrue(pickaxeSteps(c).isEmpty(), "Two spare ingots: no second pickaxe: " + pickaxeSteps(c));
        h.assertTrue(shieldSteps(c).size() == 2, "Two spare ingots: the shield is made: " + shieldSteps(c));
        c.setItem(5, new ItemStack(Items.IRON_INGOT, 3));
        h.assertTrue(pickaxeSteps(c).equals(List.of("obtain_item minecraft:iron_pickaxe")), "Three spare ingots: a second pickaxe: " + pickaxeSteps(c));
        h.assertTrue(shieldSteps(c).isEmpty(), "The pickaxe takes all three ingots, none is left for the shield: " + shieldSteps(c));
        c.setItem(5, new ItemStack(Items.IRON_INGOT, 4));
        h.assertTrue(pickaxeSteps(c).size() == 1 && shieldSteps(c).size() == 2, "Four ingots: the pickaxe and the shield: " + pickaxeSteps(c) + " " + shieldSteps(c));
        c.setItem(2, ItemStack.EMPTY);
        h.assertTrue(pickaxeSteps(c).isEmpty(), "The sword he lacks takes two of the four: " + pickaxeSteps(c));
        c.setItem(2, new ItemStack(Items.STONE_SWORD));
        c.setItem(6, new ItemStack(Items.IRON_PICKAXE));
        h.assertTrue(pickaxeSteps(c).isEmpty(), "Two iron pickaxes already: " + pickaxeSteps(c));
        h.succeed();
    }

    private static List<String> pickaxeSteps(CompanionEntity c) {
        var book = CompanionPlaybooks.of("find_diamonds", 3);
        return book.milestones().getLast().method(CompanionFacts.of(c), false, book.milestones()).stream()
                .filter(step -> step.item().equals("minecraft:iron_pickaxe")).map(step -> step.type() + " " + step.item()).toList();
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
        CompanionDiamondGoalTests.underRoof(h, c, () -> {
            c.mineSite().start(c.blockPosition().above(20), net.minecraft.core.Direction.NORTH);
            h.assertTrue(shieldSteps(c).equals(List.of("equip_item minecraft:shield offhand")), "Down in the mine after a death the shield is put on again: " + shieldSteps(c));
            c.setItem(6, new ItemStack(Items.IRON_INGOT, 3));
            c.setItem(7, new ItemStack(Items.OAK_PLANKS, 16));
            c.setItem(5, ItemStack.EMPTY);
            h.assertTrue(shieldSteps(c).isEmpty(), "Down in the mine nothing is made: " + shieldSteps(c));
            c.mineSite().clear();
            c.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            h.assertTrue(shieldSteps(c).isEmpty(), "A shield in the left hand needs nothing: " + shieldSteps(c));
        });
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

    /** The goal's stand without the iron pickaxe in the bag (coal for the furnace in it), with this many iron ores in a row on the grass and two trees. */
    private static CompanionDiamondGoalTests.Rig ironless(GameTestHelper h, int ores) {
        List<ItemStack> bag = new ArrayList<>(Arrays.asList(CompanionDiamondGoalTests.supplies(32, 4)));
        bag.removeIf(stack -> stack.is(Items.IRON_PICKAXE));
        bag.add(new ItemStack(Items.COAL, 4));
        CompanionDiamondGoalTests.Rig r = CompanionDiamondGoalTests.rig(h, false, bag.toArray(new ItemStack[0]));
        r.c().setSearchRadius(WIDE_SEARCH);
        for (int i = 0; i < ores; i++) h.setBlock(new BlockPos(ORE_X + i, CompanionDiamondGoalTests.GROUND + 1, ORE_Z), Blocks.IRON_ORE);
        int[][] trunks = {{CompanionDiamondGoalTests.CENTER - TREE_SPAN, CompanionDiamondGoalTests.CENTER - TREE_SPAN}, {CompanionDiamondGoalTests.CENTER - TREE_SPAN, CompanionDiamondGoalTests.CENTER + TREE_SPAN}};
        for (int[] trunk : trunks) for (int y = 1; y <= TRUNK; y++) h.setBlock(new BlockPos(trunk[0], CompanionDiamondGoalTests.GROUND + y, trunk[1]), Blocks.OAK_LOG);
        return r;
    }

    private static ItemStack[] kit(ItemStack... extra) {
        List<ItemStack> bag = new ArrayList<>(Arrays.asList(CompanionDiamondGoalTests.supplies(32, 4)));
        bag.addAll(Arrays.asList(extra));
        return bag.toArray(new ItemStack[0]);
    }

    private static List<String> shieldSteps(CompanionEntity c) { return shieldSteps(c, false); }

    private static List<String> shieldSteps(CompanionEntity c, boolean tripSpent) {
        var book = CompanionPlaybooks.of("find_diamonds", 3);
        return shieldOnly(book.milestones().getLast().method(CompanionFacts.of(c).withShieldTrekSpent(tripSpent), false, book.milestones()));
    }

    /** The steps of the agenda's run for the iron of a shield, as the agenda reads the facts of the bag and of the place. */
    private static List<String> trekSteps(CompanionEntity c, boolean tripSpent) {
        return shieldOnly(CompanionMilestones.Milestone.shieldTrekSteps(CompanionFacts.of(c).withShieldTrekSpent(tripSpent)));
    }

    private static List<String> shieldOnly(List<CompanionMilestones.Step> steps) {
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

package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionFacts;
import dev.aicompanion.entity.CompanionMilestones;
import dev.aicompanion.entity.CompanionPlaybooks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * «Построй портал» (decided 08.11): a frame without its corners near the player, its bottom set into the ground, lit with his flint
 * and steel; a lit portal near is enough; what he lacks for it is got first or he says why he cannot.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionPortalBuildTests {
    private static final int GROUND = 5;

    /**
     * Ten obsidian and a flint and steel: a lit portal near the player, ten obsidian and no corners, its bottom where the grass was
     * (both dirt in his bag, the prop under the top taken back), the flint and steel worn by one, not beside the chest, him outside it.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void anOrderBuildsALitFrameWithoutCornersSetIntoTheGround(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.OBSIDIAN, 10));
        c.setItem(1, new ItemStack(Items.FLINT_AND_STEEL));
        BlockPos chest = new BlockPos(11, GROUND + 1, 12);
        h.setBlock(chest, Blocks.CHEST);
        var future = run(h, c, "[{\"action\":\"build_portal\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Портал готов."), "Not built: " + report);
            List<BlockPos> portal = blocks(h, Blocks.NETHER_PORTAL), frame = blocks(h, Blocks.OBSIDIAN);
            h.assertTrue(portal.size() == 6 && frame.size() == 10, portal.size() + " portal blocks and " + frame.size() + " obsidian: " + report);
            int bottom = portal.stream().mapToInt(BlockPos::getY).min().orElseThrow();
            h.assertTrue(bottom == GROUND + 1 && portal.stream().filter(pos -> pos.getY() == bottom).allMatch(pos -> frame.contains(pos.below())),
                    "The way in is not level with the ground: the portal starts at " + bottom);
            h.assertTrue(count(c, Items.OBSIDIAN) == 0 && count(c, Items.DIRT) == 2, "Not all obsidian set or the ground not back in his bag: " + c.getStoredItemsSummary());
            h.assertTrue(steel(c) == 1, "The flint and steel is not worn by one: " + steel(c));
            BlockPos player = new BlockPos(8, GROUND + 1, 14);
            h.assertTrue(portal.stream().allMatch(pos -> pos.closerThan(player, 11)), "Not near the player: " + portal);
            h.assertTrue(frame.stream().allMatch(pos -> Math.abs(pos.getX() - chest.getX()) > 2 || Math.abs(pos.getZ() - chest.getZ()) > 2), "Beside the chest: " + frame);
            h.assertTrue(!portal.contains(h.relativePos(c.blockPosition())) && !portal.contains(h.relativePos(c.blockPosition().above())), "He stands in the portal");
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(BlockPos.ZERO)).expandTowards(32, 16, 32)).isEmpty(), "Something is left lying");
        });
    }

    /** A lit portal within sixteen blocks of the player: nothing built, no obsidian spent, and he says so. */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void aLitPortalNearThePlayerIsEnough(GameTestHelper h) {
        ground(h);
        litPortal(h, 20, GROUND + 1, 14);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.OBSIDIAN, 10));
        c.setItem(1, new ItemStack(Items.FLINT_AND_STEEL));
        var future = run(h, c, "[{\"action\":\"build_portal\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Рядом уже есть портал.") && !report.contains("План остановлен"), "Not said: " + report);
            h.assertTrue(count(c, Items.OBSIDIAN) == 10 && blocks(h, Blocks.NETHER_PORTAL).size() == 6 && steel(c) == 0, "He built or struck: " + report);
        });
    }

    /** No flint and steel, but an ingot and a flint: he makes one first (the plan gets it), then builds and lights. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void withoutAFlintAndSteelHeMakesOneAndBuilds(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.OBSIDIAN, 10));
        c.setItem(1, new ItemStack(Items.IRON_INGOT));
        c.setItem(2, new ItemStack(Items.FLINT));
        var future = run(h, c, "[{\"action\":\"build_portal\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Портал готов.") && blocks(h, Blocks.NETHER_PORTAL).size() == 6, "Not built: " + report);
            h.assertTrue(count(c, Items.IRON_INGOT) == 0 && count(c, Items.FLINT) == 0 && steel(c) == 1, "Not a real flint and steel: " + c.getStoredItemsSummary());
        });
    }

    /** No obsidian and no pickaxe that takes it: he says what he needs and sets nothing, the ground left as it was. */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void withoutObsidianOrItsPickaxeHeSaysWhyAndBuildsNothing(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.OBSIDIAN, 4));
        c.setItem(1, new ItemStack(Items.FLINT_AND_STEEL));
        c.setItem(2, new ItemStack(Items.IRON_PICKAXE));
        var future = run(h, c, "[{\"action\":\"build_portal\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Обсидиан берёт только алмазная кирка — дай мне её.") && !report.contains("Портал готов"), "Not why: " + report);
            h.assertTrue(blocks(h, Blocks.OBSIDIAN).isEmpty() && count(c, Items.OBSIDIAN) == 4 && count(c, Items.DIRT) == 0 && steel(c) == 0, "He set or dug: " + report);
        });
    }

    /**
     * «Построй портал» and «сделай портал в Незер» need no model (decided 08.11): the plan the model makes, build_portal with no
     * count or item of its own. Anything more is said goes to the model.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void buildingAPortalNeedsNoModel(GameTestHelper h) {
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"build_portal\"}]"));
        h.assertTrue(plan.equals(List.of(new PlannedAction("build_portal", 0, 1))), "Not one build_portal: " + plan);
        for (String spoiled : List.of("[{\"action\":\"build_portal\",\"count\":2}]", "[{\"action\":\"build_portal\",\"item\":\"minecraft:obsidian\"}]")) {
            boolean refused = false;
            try { CompanionPlanCodec.parsePlan(JsonParser.parseString(spoiled)); } catch (IllegalStateException expected) { refused = true; }
            h.assertTrue(refused, "Taken: " + spoiled);
        }
        for (String said : List.of("Олег, построй портал", "построй портал в Незер", "сделай портал в ад!", "поставь нам портал рядом", "построй портал из обсидиана")) {
            String route = CompanionLocalCommands.route(said, "Олег");
            h.assertTrue(route != null && CompanionLocalCommands.actions(route).size() == 1 && CompanionLocalCommands.actions(route).getFirst().type().equals("build_portal"),
                    "«" + said + "» is not build_portal but " + route);
        }
        for (String model : List.of("построй портал и зайди в него", "не строй портал", "сломай портал", "где портал", "построй два портала"))
            h.assertTrue(CompanionLocalCommands.route(model, "Олег") == null || !CompanionLocalCommands.route(model, "Олег").contains("build_portal"),
                    "«" + model + "» is not left to the model: " + CompanionLocalCommands.route(model, "Олег"));
        h.succeed();
    }

    /**
     * The dragon goal builds the portal now: its stage is build_portal until a lit portal stands near the player, and then the ten
     * obsidian are met too though they went into it (else the goal would go for ten more).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 100)
    public static void theDragonGoalBuildsThePortalAndAStandingOneMeetsTheObsidianToo(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        var book = CompanionPlaybooks.of("defeat_ender_dragon", 0);
        var portal = book.milestones().stream().filter(m -> m.label(false).equals("портал в Незер")).findFirst().orElseThrow();
        var obsidian = book.milestones().stream().filter(m -> m.label(false).equals("10 обсидиана")).findFirst().orElseThrow();
        h.assertTrue(portal.supported(), "He is said not to know how to build a portal");
        c.setItem(0, new ItemStack(Items.OBSIDIAN, 10));
        var facts = CompanionFacts.of(c);
        h.assertTrue(!portal.satisfied(facts, false, book.milestones()) && obsidian.satisfied(facts, false, book.milestones()), "Not the portal stage next");
        h.assertTrue(portal.method(facts, false, book.milestones()).equals(List.of(new CompanionMilestones.Step("build_portal", "", 1, "auto"))),
                "Not build_portal: " + portal.method(facts, false, book.milestones()));
        c.setItem(0, ItemStack.EMPTY);
        litPortal(h, 18, GROUND + 1, 18);
        var built = CompanionFacts.of(c);
        h.assertTrue(portal.satisfied(built, false, book.milestones()) && obsidian.satisfied(built, false, book.milestones()), "A standing portal does not meet both stages");
        h.succeed();
    }

    /**
     * A lit portal as a player leaves it, across x from {@code x}, its frame from {@code y}, at {@code z} (relative): the fire makes the
     * portal blocks and the game's record of them.
     */
    static void litPortal(GameTestHelper h, int x, int y, int z) {
        for (int dx = 0; dx <= 3; dx++) for (int dy = 0; dy <= 4; dy++)
            h.setBlock(new BlockPos(x + dx, y + dy, z), dx == 0 || dx == 3 || dy == 0 || dy == 4 ? Blocks.OBSIDIAN : Blocks.AIR);
        h.setBlock(new BlockPos(x + 1, y + 1, z), Blocks.FIRE);
        h.assertBlockPresent(Blocks.NETHER_PORTAL, new BlockPos(x + 2, y + 3, z));
    }

    /** The cells of this block inside the walls (relative). */
    private static List<BlockPos> blocks(GameTestHelper h, net.minecraft.world.level.block.Block block) {
        List<BlockPos> cells = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(30, GROUND + 6, 30)))
            if (h.getBlockState(pos).is(block)) cells.add(pos.immutable());
        return cells;
    }

    private static void ground(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= GROUND + 6; y++) {
            boolean border = x == 0 || x == 31 || z == 0 || z == 31;
            if (y <= GROUND) h.setBlock(new BlockPos(x, y, z), y == 0 || border ? Blocks.BEDROCK : y == GROUND ? Blocks.GRASS_BLOCK : Blocks.DIRT);
            else if (border) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
            else h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
    }

    private static CompanionEntity spawn(GameTestHelper h) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 10, GROUND + 1, 16);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(8, GROUND + 1, 14));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        c.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        c.setSearchRadius(16);
        return c;
    }

    private static CompletableFuture<CompanionPlanRunner.Outcome> run(GameTestHelper h, CompanionEntity c, String json) {
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString(json));
        return CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-portal-build", () -> false));
    }

    /** The damage of his flint and steel; -1 without one. */
    private static int steel(CompanionEntity c) {
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(Items.FLINT_AND_STEEL)) return c.getItem(slot).getDamageValue();
        return c.getMainHandItem().is(Items.FLINT_AND_STEEL) ? c.getMainHandItem().getDamageValue() : -1;
    }

    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        if (c.getMainHandItem().is(item)) count += c.getMainHandItem().getCount();
        return count;
    }
}

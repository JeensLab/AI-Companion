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
     * The tests that light a portal run by themselves: a lit portal is a way for the tests beside them while they run (the walk back
     * for his things across worlds, CompanionDeathRecoveryTests); each puts its portal out once it passes.
     */
    static final String PORTALS = "portal_build";

    @net.minecraft.gametest.framework.AfterBatch(batch = PORTALS)
    public static void afterPortals(net.minecraft.server.level.ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    /**
     * Ten obsidian and a flint and steel: a lit portal near the player, ten obsidian and no corners, its bottom where the grass was
     * (both dirt in his bag, the prop under the top taken back), the flint and steel worn by one, not beside the chest, him outside it.
     * The chest is the player's, his home (decided 09.11: only his blocks are kept clear of, not sixteen blocks round them); the glass
     * in the bag is no prop (it would be lost when broken, review 09.11).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400, batch = PORTALS)
    public static void anOrderBuildsALitFrameWithoutCornersSetIntoTheGround(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.OBSIDIAN, 10));
        c.setItem(1, new ItemStack(Items.FLINT_AND_STEEL));
        c.setItem(2, new ItemStack(Items.GLASS));
        BlockPos chest = new BlockPos(11, GROUND + 1, 12);
        placedByPlayer(h, c, chest, Blocks.CHEST);
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
            h.assertTrue(count(c, Items.OBSIDIAN) == 0 && count(c, Items.DIRT) == 2 && count(c, Items.GLASS) == 1, "Not all obsidian set, the ground not back or the glass gone: " + c.getStoredItemsSummary());
            h.assertTrue(steel(c) == 1, "The flint and steel is not worn by one: " + steel(c));
            BlockPos player = new BlockPos(8, GROUND + 1, 14);
            h.assertTrue(portal.stream().allMatch(pos -> pos.closerThan(player, 11)), "Not near the player: " + portal);
            h.assertTrue(frame.stream().allMatch(pos -> Math.abs(pos.getX() - chest.getX()) > 2 || Math.abs(pos.getZ() - chest.getZ()) > 2), "Beside the chest: " + frame);
            h.assertTrue(!portal.contains(h.relativePos(c.blockPosition())) && !portal.contains(h.relativePos(c.blockPosition().above())), "He stands in the portal");
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, walls(h), item -> item.getItem().is(Items.DIRT)).isEmpty(), "Dirt is left lying");
            putOut(h);
        });
    }

    /**
     * A frame left unlit, with something lying in it: the frame is his to finish with no obsidian more, and nothing is lit while a drop
     * lies inside (the portal would take it, and his pickup went in after it: in game 08.11 he walked into the Nether for his prop).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200, batch = PORTALS)
    public static void anUnlitFrameIsLitOnlyOnceWhatLiesInItIsPickedUp(GameTestHelper h) {
        ground(h);
        for (BlockPos cell : List.of(new BlockPos(15, GROUND, 20), new BlockPos(16, GROUND, 20), new BlockPos(15, GROUND + 4, 20), new BlockPos(16, GROUND + 4, 20)))
            h.setBlock(cell, Blocks.OBSIDIAN);
        for (int y = GROUND + 1; y <= GROUND + 3; y++) { h.setBlock(new BlockPos(14, y, 20), Blocks.OBSIDIAN); h.setBlock(new BlockPos(17, y, 20), Blocks.OBSIDIAN); }
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.FLINT_AND_STEEL));
        BlockPos inside = h.absolutePos(new BlockPos(16, GROUND + 1, 20));
        ItemEntity lying = new ItemEntity(h.getLevel(), inside.getX() + .5, inside.getY() + .1, inside.getZ() + .5, new ItemStack(Items.COBBLESTONE));
        lying.setDeltaMovement(0, 0, 0);
        lying.setPickUpDelay(40);
        h.getLevel().addFreshEntity(lying);
        var future = run(h, c, "[{\"action\":\"build_portal\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Портал готов.") && blocks(h, Blocks.NETHER_PORTAL).size() == 6 && blocks(h, Blocks.OBSIDIAN).size() == 10, "Not lit as it stood: " + report);
            h.assertTrue(count(c, Items.COBBLESTONE) == 1, "What lay in the frame is not in his bag: " + c.getStoredItemsSummary());
            h.assertTrue(c.level() == h.getLevel() && !blocks(h, Blocks.NETHER_PORTAL).contains(h.relativePos(c.blockPosition())), "He went into the portal");
            putOut(h);
        });
    }

    /**
     * A leaf inside a frame he is finishing (a bush of the player's, a leaf set down there): cleared as any block he breaks, with the
     * tool of the leaf — the shears from his bag, not the pickaxe in his hand — so it comes down as itself, the shears wear and no
     * pickaxe does, and the frame is lit after it. The frame is chosen while that cell is still free (a leaf is no free cell to its
     * own check): the leaf is set in it after the work began.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200, batch = PORTALS)
    public static void aLeafInsideTheFrameIsClearedWithTheShearsWhileAPickaxeIsHeld(GameTestHelper h) {
        ground(h);
        for (BlockPos cell : List.of(new BlockPos(15, GROUND, 20), new BlockPos(16, GROUND, 20), new BlockPos(15, GROUND + 4, 20), new BlockPos(16, GROUND + 4, 20)))
            h.setBlock(cell, Blocks.OBSIDIAN);
        for (int y = GROUND + 1; y <= GROUND + 3; y++) { h.setBlock(new BlockPos(14, y, 20), Blocks.OBSIDIAN); h.setBlock(new BlockPos(17, y, 20), Blocks.OBSIDIAN); }
        CompanionEntity c = spawn(h);
        // The owner's bag: the stone pickaxe in his hand, an iron pickaxe, an axe, shears and a shovel in the bag.
        c.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        c.setItem(0, new ItemStack(Items.IRON_PICKAXE));
        c.setItem(1, new ItemStack(Items.IRON_AXE));
        c.setItem(2, new ItemStack(Items.SHEARS));
        c.setItem(3, new ItemStack(Items.IRON_SHOVEL));
        c.setItem(4, new ItemStack(Items.FLINT_AND_STEEL));
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        h.assertTrue(CompanionEvents.executeAction(owner, "build_portal", "", 1).success(), "The order was refused");
        BlockPos inside = new BlockPos(16, GROUND + 2, 20);
        h.setBlock(inside, Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE, 1));
        h.succeedWhen(() -> {
            String report = c.getLastTaskResult();
            h.assertTrue(report.contains("Портал готов."), "Not built: " + report);
            h.assertBlockNotPresent(Blocks.OAK_LEAVES, inside);
            h.assertTrue(blocks(h, Blocks.NETHER_PORTAL).size() == 6, "Not a lit portal: " + blocks(h, Blocks.NETHER_PORTAL));
            h.assertTrue(count(c, Items.OAK_LEAVES) >= 1, "The leaf did not come down as itself: " + c.getStoredItemsSummary());
            h.assertTrue(damage(c, Items.SHEARS) >= 1, "The leaf was not cut with the shears: " + damage(c, Items.SHEARS) + ", " + report);
            h.assertTrue(damage(c, Items.STONE_PICKAXE) == 0 && damage(c, Items.IRON_PICKAXE) == 0,
                    "A pickaxe broke the leaf: stone " + damage(c, Items.STONE_PICKAXE) + ", iron " + damage(c, Items.IRON_PICKAXE) + ", " + report);
            putOut(h);
        });
    }

    /** A lit portal within sixteen blocks of the player: nothing built, no obsidian spent, he says so and follows on. */
    @GameTest(template = "tall_tree", timeoutTicks = 400, batch = PORTALS)
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
            // No work began: he does not stand where the order found him (review 09.11).
            h.assertTrue(c.isFollowing(), "He stopped following");
            putOut(h);
        });
    }

    /**
     * A portal he built a while ago, farther than sixteen blocks from the player now: nothing built, he leads the player to it and stops
     * before it, out of it (decided 09.11).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400, batch = PORTALS)
    public static void aPortalHeKnowsFartherOffHeLeadsThePlayerTo(GameTestHelper h) {
        ground(h);
        litPortal(h, 26, GROUND + 1, 27);
        CompanionEntity c = spawn(h);
        c.rememberPlaced(h.absolutePos(new BlockPos(27, GROUND + 2, 27)));
        var owner = h.getLevel().getPlayerByUUID(c.getOwnerId());
        BlockPos start = h.absolutePos(new BlockPos(3, GROUND + 1, 3));
        owner.teleportTo(start.getX() + .5, start.getY(), start.getZ() + .5);
        c.teleportTo(start.getX() + 1.5, start.getY(), start.getZ() + 1.5);
        c.setItem(0, new ItemStack(Items.OBSIDIAN, 10));
        c.setItem(1, new ItemStack(Items.FLINT_AND_STEEL));
        var future = run(h, c, "[{\"action\":\"build_portal\"}]");
        // The player comes after him.
        h.onEachTick(() -> { if (c.level() == h.getLevel()) owner.teleportTo(c.getX() - 1, c.getY(), c.getZ() - 1); });
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Пришли — вот портал.") && count(c, Items.OBSIDIAN) == 10 && blocks(h, Blocks.NETHER_PORTAL).size() == 6, "Not led: " + report);
            h.assertTrue(c.level() == h.getLevel() && c.blockPosition().closerThan(h.absolutePos(new BlockPos(27, GROUND + 2, 27)), 5), "Not by the portal: " + c.blockPosition());
            putOut(h);
        });
    }

    /** No flint and steel, but an ingot and a flint: he makes one first (the plan gets it), then builds and lights. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400, batch = PORTALS)
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
            putOut(h);
        });
    }

    /**
     * No obsidian and no pickaxe that takes it: he says what he needs and sets nothing, the ground left as it was. Asked for a new one,
     * the last word is still why (review 09.11: «нужно 10 обсидиана» was said after the obtain gave up).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void withoutObsidianOrItsPickaxeHeSaysWhyAndBuildsNothing(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.OBSIDIAN, 4));
        c.setItem(1, new ItemStack(Items.FLINT_AND_STEEL));
        c.setItem(2, new ItemStack(Items.IRON_PICKAXE));
        var future = run(h, c, "[{\"action\":\"build_portal\",\"item\":\"new\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Обсидиан берёт только алмазная кирка — дай мне её.") && !report.contains("Портал готов"), "Not why: " + report);
            h.assertTrue(report.contains("План остановлен: Обсидиан берёт только алмазная кирка") && !report.contains("Для портала нужно"), "Not the last word: " + report);
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
        var anew = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"build_portal\",\"item\":\"new\"}]"));
        h.assertTrue(anew.equals(List.of(new PlannedAction("build_portal", 0, 1, "new"))), "Not a new one: " + anew);
        for (String said : List.of("Олег, построй портал", "построй портал в Незер", "сделай портал в ад!", "поставь нам портал рядом", "построй портал из обсидиана",
                "построй новый портал", "не хочу идти, построй новый портал", "построй портал здесь", "сделай портал возле меня")) {
            String route = CompanionLocalCommands.route(said, "Олег");
            String wanted = said.matches(".*новый.*") ? "new" : said.matches(".*(здесь|возле меня).*") ? "here" : "resources";
            // Said, no item is "resources" in the local command (as for a house); "new" and "here" mean something to the server.
            var step = route == null ? null : CompanionLocalCommands.actions(route);
            h.assertTrue(step != null && step.size() == 1 && step.getFirst().type().equals("build_portal") && wanted.equals(step.getFirst().item()),
                    "«" + said + "» is not build_portal " + wanted + " but " + route);
        }
        for (String model : List.of("построй портал и зайди в него", "не строй портал", "сломай портал", "где портал", "построй два портала"))
            h.assertTrue(CompanionLocalCommands.route(model, "Олег") == null || !CompanionLocalCommands.route(model, "Олег").contains("build_portal"),
                    "«" + model + "» is not left to the model: " + CompanionLocalCommands.route(model, "Олег"));
        h.succeed();
    }

    /**
     * The dragon goal builds the portal now: its stage is build_portal until a lit portal he knows stands, and then the ten obsidian are
     * met too though they went into it (else the goal would go for ten more); so wherever the player and he have gone since.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 100, batch = PORTALS)
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
        // Both gone far off: the portal is known by its block still standing, not by where the player is now (decided 09.11).
        var owner = h.getLevel().getPlayerByUUID(c.getOwnerId());
        owner.teleportTo(owner.getX(), owner.getY() + 60, owner.getZ());
        c.teleportTo(c.getX(), c.getY() + 60, c.getZ());
        var away = CompanionFacts.of(c);
        h.assertTrue(portal.satisfied(away, false, book.milestones()) && obsidian.satisfied(away, false, book.milestones()), "Gone off, the portal is forgotten");
        putOut(h);
        h.succeed();
    }

    /**
     * «Построй портал» with no obsidian in his bag and none in the world, no lava about: the ten obsidian lie in a chest two steps
     * away. He walks to it, opens it, takes exactly the ten the frame lacks, shuts the lid, and builds and lights the portal — no
     * pickaxe, no lava, no order for obsidian (the owner's world, idea CHST9, issue #105).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400, batch = PORTALS)
    public static void anOrderTakesTheObsidianItNeedsFromAChestNearby(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.FLINT_AND_STEEL));
        BlockPos chest = new BlockPos(11, GROUND + 1, 12);
        placedByPlayer(h, c, chest, Blocks.CHEST);
        fillChest(h, chest, Items.OBSIDIAN, 10);
        boolean[] lidWasOpen = {false};
        tickLid(h, chest, lidWasOpen);
        var future = run(h, c, "[{\"action\":\"build_portal\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Портал готов."), "Not built: " + report);
            h.assertTrue(blocks(h, Blocks.NETHER_PORTAL).size() == 6 && blocks(h, Blocks.OBSIDIAN).size() == 10, "Not a lit frame: " + report);
            h.assertTrue(count(c, Items.OBSIDIAN) == 0, "The obsidian is still in his bag: " + c.getStoredItemsSummary());
            h.assertTrue(chestCount(h, chest, Items.OBSIDIAN) == 0, "The chest was not emptied by ten: " + chestCount(h, chest, Items.OBSIDIAN));
            h.assertTrue(lidWasOpen[0], "The lid was never opened while he took the obsidian");
            h.assertTrue(lidOpen(h, chest) == 0F, "The lid is left open: " + lidOpen(h, chest));
            // Nothing doubled and nothing vanished: the ten of the chest are the ten of the frame.
            h.assertTrue(blocks(h, Blocks.OBSIDIAN).size() + count(c, Items.OBSIDIAN) + chestCount(h, chest, Items.OBSIDIAN) == 10,
                    "Obsidian was lost or doubled: frame " + blocks(h, Blocks.OBSIDIAN).size() + ", bag " + count(c, Items.OBSIDIAN) + ", chest " + chestCount(h, chest, Items.OBSIDIAN));
            putOut(h);
        });
    }

    /**
     * A chest near the frame holds more obsidian than the frame needs: he takes the ten it lacks and leaves the rest — the whole
     * stack is not carried away (idea CHST9, issue #105).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400, batch = PORTALS)
    public static void aChestWithMoreObsidianThanTheFrameNeedsKeepsTheRest(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.FLINT_AND_STEEL));
        BlockPos chest = new BlockPos(11, GROUND + 1, 12);
        placedByPlayer(h, c, chest, Blocks.CHEST);
        fillChest(h, chest, Items.OBSIDIAN, 16);
        boolean[] lidWasOpen = {false};
        tickLid(h, chest, lidWasOpen);
        var future = run(h, c, "[{\"action\":\"build_portal\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Портал готов."), "Not built: " + report);
            h.assertTrue(blocks(h, Blocks.NETHER_PORTAL).size() == 6 && blocks(h, Blocks.OBSIDIAN).size() == 10, "Not a lit frame: " + report);
            h.assertTrue(chestCount(h, chest, Items.OBSIDIAN) == 6, "Not exactly ten taken: " + chestCount(h, chest, Items.OBSIDIAN) + " left");
            h.assertTrue(count(c, Items.OBSIDIAN) == 0, "The rest is in his bag: " + c.getStoredItemsSummary());
            h.assertTrue(lidWasOpen[0] && lidOpen(h, chest) == 0F, "The lid is left open: " + lidOpen(h, chest));
            putOut(h);
        });
    }

    /**
     * The only obsidian in the world lies in a chest farther than sixteen blocks from the frame (seventeen above it), and a chest
     * near the frame is empty: the far chest is not counted, and the shortage is named — no portal, nothing taken from either chest.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void obsidianInAChestFartherThanSixteenIsNotCountedAndTheShortageIsSaid(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.FLINT_AND_STEEL));
        BlockPos near = new BlockPos(11, GROUND + 1, 12);
        placedByPlayer(h, c, near, Blocks.CHEST);
        BlockPos far = new BlockPos(11, GROUND + 21, 12);
        placedByPlayer(h, c, far, Blocks.CHEST);
        fillChest(h, far, Items.OBSIDIAN, 10);
        boolean[] nearLidWasOpen = {false};
        tickLid(h, near, nearLidWasOpen);
        var future = run(h, c, "[{\"action\":\"build_portal\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Рядом нечего копать: ни обсидиана, ни лавы"), "Not the shortage: " + report);
            h.assertTrue(!report.contains("Портал готов.") && blocks(h, Blocks.NETHER_PORTAL).isEmpty(), "He built: " + report);
            h.assertTrue(blocks(h, Blocks.OBSIDIAN).isEmpty() && count(c, Items.OBSIDIAN) == 0, "He set or took something: " + report);
            h.assertTrue(chestCount(h, far, Items.OBSIDIAN) == 10 && chestCount(h, near, Items.OBSIDIAN) == 0, "A chest was emptied: far " + chestCount(h, far, Items.OBSIDIAN));
            h.assertTrue(!nearLidWasOpen[0], "The empty chest was opened");
        });
    }

    /**
     * The chest near the frame holds five of the ten: he takes those five and the plan gets the other five where they lie, then
     * builds and lights the portal — the chest emptied by exactly five, the frame ten (idea CHST9, issue #105).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400, batch = PORTALS)
    public static void aChestShortOfTheTenIsTakenAndTheRestIsGotWhereItLies(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.FLINT_AND_STEEL));
        BlockPos chest = new BlockPos(11, GROUND + 1, 12);
        placedByPlayer(h, c, chest, Blocks.CHEST);
        fillChest(h, chest, Items.OBSIDIAN, 5);
        // Five obsidian standing apart, free of every frame the build may choose and off the chest's site: within his search radius.
        for (int x : new int[]{10, 12, 14, 16, 18}) h.setBlock(new BlockPos(x, GROUND + 1, 29), Blocks.OBSIDIAN);
        boolean[] lidWasOpen = {false};
        tickLid(h, chest, lidWasOpen);
        var future = run(h, c, "[{\"action\":\"build_portal\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Портал готов."), "Not built: " + report);
            h.assertTrue(blocks(h, Blocks.NETHER_PORTAL).size() == 6 && blocks(h, Blocks.OBSIDIAN).size() == 10, "Not a lit frame: " + report);
            h.assertTrue(chestCount(h, chest, Items.OBSIDIAN) == 0, "The chest was not emptied by five: " + chestCount(h, chest, Items.OBSIDIAN));
            h.assertTrue(count(c, Items.OBSIDIAN) == 0, "The obsidian is still in his bag: " + c.getStoredItemsSummary());
            h.assertTrue(lidWasOpen[0] && lidOpen(h, chest) == 0F, "The lid is left open: " + lidOpen(h, chest));
            putOut(h);
        });
    }

    /**
     * A double chest near the frame holds five of the ten: the two halves are one container and counted once, so the plan gets
     * the other five and he builds — the double chest emptied by exactly five, no half counted twice (idea CHST9, issue #105).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400, batch = PORTALS)
    public static void aDoubleChestShortOfTheTenIsCountedOnceAndTheRestIsGot(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.FLINT_AND_STEEL));
        BlockPos left = new BlockPos(11, GROUND + 1, 12);
        BlockPos right = new BlockPos(12, GROUND + 1, 12);
        h.setBlock(left, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, net.minecraft.core.Direction.NORTH)
                .setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.LEFT));
        h.setBlock(right, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, net.minecraft.core.Direction.NORTH)
                .setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.RIGHT));
        fillChest(h, left, Items.OBSIDIAN, 5);
        for (int x : new int[]{10, 12, 14, 16, 18}) h.setBlock(new BlockPos(x, GROUND + 1, 29), Blocks.OBSIDIAN);
        boolean[] lidWasOpen = {false};
        tickLid(h, left, lidWasOpen);
        tickLid(h, right, lidWasOpen);
        var future = run(h, c, "[{\"action\":\"build_portal\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Портал готов."), "Not built: " + report);
            h.assertTrue(blocks(h, Blocks.NETHER_PORTAL).size() == 6 && blocks(h, Blocks.OBSIDIAN).size() == 10, "Not a lit frame: " + report);
            h.assertTrue(chestCount(h, left, Items.OBSIDIAN) == 0 && chestCount(h, right, Items.OBSIDIAN) == 0, "The double chest was not emptied by five: "
                    + chestCount(h, left, Items.OBSIDIAN) + " and " + chestCount(h, right, Items.OBSIDIAN));
            h.assertTrue(count(c, Items.OBSIDIAN) == 0, "The obsidian is still in his bag: " + c.getStoredItemsSummary());
            h.assertTrue(lidWasOpen[0] && lidOpen(h, left) == 0F, "The lid is left open: " + lidOpen(h, left));
            putOut(h);
        });
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

    /** How many of this item a container at {@code rel} holds; -1 when there is no container there. */
    private static int chestCount(GameTestHelper h, BlockPos rel, Item item) {
        if (!(h.getLevel().getBlockEntity(h.absolutePos(rel)) instanceof net.minecraft.world.Container container)) return -1;
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) if (container.getItem(slot).is(item)) count += container.getItem(slot).getCount();
        return count;
    }

    private static void fillChest(GameTestHelper h, BlockPos rel, Item item, int count) {
        if (h.getLevel().getBlockEntity(h.absolutePos(rel)) instanceof net.minecraft.world.Container container)
            container.setItem(0, new ItemStack(item, count));
    }

    /** How far the lid of the chest at {@code rel} is open, as its own controller holds it; 0 when there is no chest there. */
    private static float lidOpen(GameTestHelper h, BlockPos rel) {
        return h.getLevel().getBlockEntity(h.absolutePos(rel)) instanceof net.minecraft.world.level.block.entity.LidBlockEntity lid
                ? lid.getOpenNess(1F) : 0F;
    }

    /**
     * The lid of the chest at {@code rel} ticked as a client ticks it: the game keeps it in its own controller, which only a client
     * ticks, so the test ticks it here and records that it was opened while he took what he came for.
     */
    private static void tickLid(GameTestHelper h, BlockPos rel, boolean[] wasOpen) {
        h.onEachTick(() -> {
            var be = h.getLevel().getBlockEntity(h.absolutePos(rel));
            if (!(be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity lid)) return;
            net.minecraft.world.level.block.entity.ChestBlockEntity.lidAnimateTick(h.getLevel(), h.absolutePos(rel), h.getBlockState(rel), lid);
            if (lid.getOpenNess(1F) > 0F) wasOpen[0] = true;
        });
    }

    /** The portals of a test put out: the world outlives it, and a lit portal is a way for the tests beside (CompanionDeathRecoveryTests). */
    public static void putOut(GameTestHelper h) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(30, GROUND + 6, 30)))
            if (h.getBlockState(pos).is(Blocks.NETHER_PORTAL)) h.setBlock(pos, Blocks.AIR);
    }

    /** Inside the walls of the test (absolute). */
    private static AABB walls(GameTestHelper h) { return new AABB(h.absolutePos(new BlockPos(1, 1, 1))).minmax(new AABB(h.absolutePos(new BlockPos(30, GROUND + 6, 30)))); }

    /** A block set down by the player, as the game tells it (CompanionPlayerBuilds learns his buildings so). */
    private static void placedByPlayer(GameTestHelper h, CompanionEntity c, BlockPos cell, net.minecraft.world.level.block.Block block) {
        h.setBlock(cell, block);
        var player = (net.minecraft.server.level.ServerPlayer) h.getLevel().getPlayerByUUID(c.getOwnerId());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent(
                net.neoforged.neoforge.common.util.BlockSnapshot.create(h.getLevel().dimension(), h.getLevel(), h.absolutePos(cell)), Blocks.GRASS_BLOCK.defaultBlockState(), player));
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

    /** The wear on the stack of this item, in the bag or in either hand; -1 when he has none. */
    private static int damage(CompanionEntity c, Item item) {
        int found = -1;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) found = Math.max(found, c.getItem(slot).getDamageValue());
        for (net.minecraft.world.entity.EquipmentSlot hand : net.minecraft.world.entity.EquipmentSlot.values())
            if (hand.getType() == net.minecraft.world.entity.EquipmentSlot.Type.HAND && c.getItemBySlot(hand).is(item)) found = Math.max(found, c.getItemBySlot(hand).getDamageValue());
        return found;
    }
}

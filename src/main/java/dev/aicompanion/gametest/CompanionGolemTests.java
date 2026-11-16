package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionObtain;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * «Сделай железного голема», «сделай снеговика» (decisions 9–12 of small-use): he sets the body blocks of his bag a few blocks from
 * the player on firm free ground, the head last, and the game's own golem stands up; what he lacks the plan gets first.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionGolemTests {
    /** The top of the platform: three layers of earth (y 2..4) over empty air. */
    private static final int TOP = 4;
    private static final Pattern WHERE = Pattern.compile("голем стоит на (-?\\d+), (-?\\d+), (-?\\d+)\\.");

    private CompanionGolemTests() { }

    /**
     * Four iron blocks and a jack o'lantern in his bag, said in the player's words (no model): the lantern goes on last and a golem
     * made by a player stands within six blocks of him; nothing of the blocks is left in the bag or the world.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void anIronGolemStandsUpFromTheBlocksOfHisBag(GameTestHelper h) {
        platform(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.IRON_BLOCK, 4));
        c.setItem(1, new ItemStack(Items.JACK_O_LANTERN));
        var future = run(h, c, "сделай железного голема", "iron");
        h.succeedWhen(() -> {
            String report = report(h, future, c);
            List<IronGolem> golems = golems(h, IronGolem.class);
            h.assertTrue(golems.size() == 1 && golems.getFirst().isPlayerCreated(), "Not one player's golem: " + golems + " | " + report);
            near(h, report, golems.getFirst(), "железный");
            h.assertTrue(count(c, Items.IRON_BLOCK) == 0 && count(c, Items.JACK_O_LANTERN) == 0, "Bag: " + c.getStoredItemsSummary());
            h.assertTrue(find(h, Blocks.IRON_BLOCK).isEmpty() && find(h, Blocks.JACK_O_LANTERN).isEmpty(), "Blocks left in the world");
        });
    }

    /**
     * Two snow blocks, a pumpkin and shears: the pumpkin is set on the body and carved there with his shears (the game's carving, so
     * the golem stands up then); its four seeds are picked up, the shears lose one use.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aSnowGolemGetsThePumpkinHeCarvesOnIt(GameTestHelper h) {
        platform(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.SNOW_BLOCK, 2));
        c.setItem(1, new ItemStack(Items.PUMPKIN));
        c.setItem(2, new ItemStack(Items.SHEARS));
        var future = run(h, c, "сделай снеговика", "snow");
        h.succeedWhen(() -> {
            String report = report(h, future, c);
            List<SnowGolem> golems = golems(h, SnowGolem.class);
            h.assertTrue(golems.size() == 1, "Not one snow golem: " + golems + " | " + report);
            near(h, report, golems.getFirst(), "снежный");
            h.assertTrue(count(c, Items.SNOW_BLOCK) == 0 && count(c, Items.PUMPKIN) == 0 && count(c, Items.PUMPKIN_SEEDS) == 4, "Bag: " + c.getStoredItemsSummary());
            h.assertTrue(damage(c, Items.SHEARS) == 1, "Shears not used once: " + damage(c, Items.SHEARS));
            h.assertTrue(find(h, Blocks.SNOW_BLOCK).isEmpty() && find(h, Blocks.PUMPKIN).isEmpty() && find(h, Blocks.CARVED_PUMPKIN).isEmpty(), "Blocks left in the world");
        });
    }

    /**
     * Short of everything but iron ingots and a bench: the plan gets what the golem lacks first, one thing after another — the four
     * blocks crafted of his ingots, the wild pumpkin on the platform picked, shears made of two more ingots — and he builds.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void whatTheGolemLacksIsGotFirst(GameTestHelper h) {
        platform(h);
        h.setBlock(new BlockPos(21, TOP + 1, 21), Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.IRON_INGOT, 38));
        c.setItem(1, new ItemStack(Items.CRAFTING_TABLE));
        var future = run(h, c, "сделай железного голема", "iron");
        h.succeedWhen(() -> {
            String report = report(h, future, c);
            h.assertTrue(golems(h, IronGolem.class).size() == 1, "No golem: " + report);
            h.assertTrue(count(c, Items.IRON_INGOT) == 0 && count(c, Items.IRON_BLOCK) == 0 && count(c, Items.SHEARS) == 1 && count(c, Items.PUMPKIN_SEEDS) == 4,
                    "Bag: " + c.getStoredItemsSummary());
        });
    }

    /** With an empty bag the four blocks are the whole chain from the mine: 36 iron ore dug, smelted, crafted into blocks. */
    @GameTest(template = "empty")
    public static void fourIronBlocksAreGotFromTheMine(GameTestHelper h) {
        var plan = CompanionObtain.plan(h.getLevel(), List.of(), false, "minecraft:iron_block", 4);
        h.assertTrue(!plan.refused(), "Refused: " + plan.refusal());
        int ore = plan.steps().stream().filter(step -> step.type().equals("mine_ore") && step.item().equals("minecraft:iron_ore")).mapToInt(CompanionObtain.Step::count).sum();
        h.assertTrue(ore >= 36 && plan.steps().stream().anyMatch(step -> step.type().equals("smelt_items") && step.item().equals("minecraft:iron_ingot"))
                && plan.steps().getLast().equals(new CompanionObtain.Step("craft_item", "minecraft:iron_block", 4)), "Plan: " + plan.steps());
        h.succeed();
    }

    /**
     * In a corridor one block wide there is no free 3×3×3 for an iron golem: refused at once, nothing set, the bag kept; the snow
     * golem's column fits there and is built. The model's other kinds and counts are refused by the codec.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aNarrowPlaceTakesASnowGolemOnly(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= 12; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        for (int x = 8; x < 24; x++) for (int z = 14; z <= 18; z++) for (int y = TOP - 2; y <= TOP + 4; y++)
            h.setBlock(new BlockPos(x, y, z), y <= TOP ? Blocks.STONE : z == 16 ? Blocks.AIR : Blocks.GLASS);
        for (int x = 8; x < 24; x++) h.setBlock(new BlockPos(x, TOP + 4, 16), Blocks.GLASS);
        CompanionEntity c = spawn(h, new BlockPos(16, TOP + 1, 16), new BlockPos(11, TOP + 1, 16));
        c.setItem(0, new ItemStack(Items.IRON_BLOCK, 4));
        c.setItem(1, new ItemStack(Items.SNOW_BLOCK, 2));
        c.setItem(2, new ItemStack(Items.CARVED_PUMPKIN, 2));
        var iron = run(h, c, "сделай железного голема", "iron");
        var snow = new AtomicReference<CompletableFuture<CompanionPlanRunner.Outcome>>();
        h.succeedWhen(() -> {
            String report = report(h, iron, c);
            h.assertTrue(report.contains("Не нашёл ровного свободного места 3×3×3 рядом для голема."), "Not said there is no room: " + report);
            h.assertTrue(count(c, Items.IRON_BLOCK) == 4 && find(h, Blocks.IRON_BLOCK).isEmpty(), "Something was set: " + c.getStoredItemsSummary());
            if (snow.get() == null) snow.set(run(h, c, "построй снежного голема", "snow"));
            report(h, snow.get(), c);
            h.assertTrue(golems(h, SnowGolem.class).size() == 1 && count(c, Items.SNOW_BLOCK) == 0 && count(c, Items.CARVED_PUMPKIN) == 1, "No snow golem: " + snow.get().join().report());
        });
        for (String bad : new String[]{"[{\"action\":\"build_golem\",\"item\":\"gold\"}]", "[{\"action\":\"build_golem\",\"item\":\"iron\",\"count\":2}]", "[{\"action\":\"build_golem\"}]"})
            try { CompanionPlanCodec.parsePlan(JsonParser.parseString(bad)); h.fail("Accepted: " + bad); } catch (IllegalStateException refused) { }
    }

    /**
     * Stopped after two of the four iron blocks were set, then asked again: the same place is built on, only the two missing blocks
     * are taken from his bag, and no more iron is sent for.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void aStoppedGolemIsFinishedWhereItStands(GameTestHelper h) {
        platform(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.IRON_BLOCK, 4));
        c.setItem(1, new ItemStack(Items.CARVED_PUMPKIN));
        var cancelled = new AtomicBoolean();
        var port = new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-golem-stop", cancelled::get);
        var first = CompanionPlanRunner.run(List.of(new dev.aicompanion.client.PlannedAction("build_golem", 0, 1, "iron")), port);
        var again = new AtomicReference<CompletableFuture<CompanionPlanRunner.Outcome>>();
        h.onEachTick(() -> {
            if (!cancelled.get() && find(h, Blocks.IRON_BLOCK).size() >= 2) { cancelled.set(true); port.cancelWork(); }
        });
        h.succeedWhen(() -> {
            h.assertTrue(cancelled.get() && first.isDone(), "Not stopped half way");
            if (again.get() == null) {
                h.assertTrue(golems(h, IronGolem.class).isEmpty(), "A golem stood up before the stop settled");
                h.assertTrue(count(c, Items.IRON_BLOCK) == 4 - find(h, Blocks.IRON_BLOCK).size(), "Blocks lost or doubled: " + c.getStoredItemsSummary());
                again.set(run(h, c, "сделай железного голема", "iron"));
            }
            report(h, again.get(), c);
            h.assertTrue(golems(h, IronGolem.class).size() == 1 && find(h, Blocks.IRON_BLOCK).isEmpty() && count(c, Items.IRON_BLOCK) == 0, "Not finished: " + again.get().join().report());
        });
    }

    /** The finished plan's report; fails the tick while it still runs. */
    private static String report(GameTestHelper h, CompletableFuture<CompanionPlanRunner.Outcome> future, CompanionEntity c) {
        h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
        return future.join().report();
    }

    /** The report names where the golem stood up: by the golem (it may have stepped since) and within six blocks of the player. */
    private static void near(GameTestHelper h, String report, AbstractGolem golem, String kind) {
        h.assertTrue(report.contains("Готово, " + kind + " голем стоит на "), "Not said: " + report);
        var m = WHERE.matcher(report);
        h.assertTrue(m.find(), "No place in: " + report);
        BlockPos at = new BlockPos(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        BlockPos player = h.absolutePos(new BlockPos(11, TOP + 1, 11));
        h.assertTrue(at.distManhattan(golem.blockPosition()) <= 3 && Math.max(Math.abs(at.getX() - player.getX()), Math.abs(at.getZ() - player.getZ())) <= 6,
                "Said " + at + ", golem at " + golem.blockPosition() + ", player at " + player);
    }

    private static <T extends AbstractGolem> List<T> golems(GameTestHelper h, Class<T> kind) {
        return h.getLevel().getEntitiesOfClass(kind, new AABB(h.absolutePos(BlockPos.ZERO)).expandTowards(32, 16, 32));
    }

    /** The platform: 16 by 16, three layers over air, earth with grass on top. */
    private static void platform(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= 12; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        for (int x = 8; x < 24; x++) for (int z = 8; z < 24; z++) for (int y = TOP - 2; y <= TOP; y++) h.setBlock(new BlockPos(x, y, z), y == TOP ? Blocks.GRASS_BLOCK : Blocks.DIRT);
    }

    private static CompanionEntity spawn(GameTestHelper h) { return spawn(h, new BlockPos(16, TOP + 1, 16), new BlockPos(11, TOP + 1, 11)); }

    private static CompanionEntity spawn(GameTestHelper h, BlockPos at, BlockPos playerAt) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), at);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(playerAt);
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        owner.setInvulnerable(true);
        c.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        c.setSearchRadius(16);
        c.setLifestyle(false, false);
        return c;
    }

    /** Said in the player's words: the direct command, which must be the model's own action for the same kind. */
    private static CompletableFuture<CompanionPlanRunner.Outcome> run(GameTestHelper h, CompanionEntity c, String said, String kind) {
        String command = CompanionLocalCommands.route(said, "Олег");
        h.assertTrue(command != null, "«" + said + "» is left to the model");
        var plan = CompanionLocalCommands.actions(command);
        var model = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"build_golem\",\"item\":\"" + kind + "\",\"count\":1}]"));
        h.assertTrue(plan.size() == 1 && model.size() == 1 && plan.getFirst().type().equals(model.getFirst().type()) && plan.getFirst().item().equals(model.getFirst().item())
                && plan.getFirst().count() == 1, "Not the model's action: " + plan + " vs " + model);
        return CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-golem", () -> false));
    }

    /** The cells (relative) of the platform's area holding this block, above the ground. */
    private static List<BlockPos> find(GameTestHelper h, Block block) {
        List<BlockPos> cells = new ArrayList<>();
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = TOP + 1; y <= TOP + 4; y++)
            if (h.getBlockState(new BlockPos(x, y, z)).is(block)) cells.add(new BlockPos(x, y, z));
        return cells;
    }

    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        if (c.getMainHandItem().is(item)) count += c.getMainHandItem().getCount();
        return count;
    }

    private static int damage(CompanionEntity c, Item item) {
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) return c.getItem(slot).getDamageValue();
        return c.getMainHandItem().is(item) ? c.getMainHandItem().getDamageValue() : -1;
    }
}

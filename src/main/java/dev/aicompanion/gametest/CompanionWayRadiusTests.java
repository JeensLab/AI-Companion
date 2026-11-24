package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * The owner's own numbers (24.11): the search box across is sixty-four, a cylinder measured flat, and the way's reach — a column
 * of thirty-two steps, a foot of the column up to sixty-four blocks away — matches it. His own smaller setting cuts the box and
 * the line says so; the setting and the way's switch are kept in the save across a reload, and the switch off refuses with its
 * reason in the line, never a silent «С этим пока не справился».
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionWayRadiusTests {
    private static final int GROUND = 5, FEET = GROUND + 1, HOME = 68, SIDE = 136, TOP = 48;

    /** A pumpkin forty-eight across and twenty-five up is in the owner's box, and the way reaches it. */
    @GameTest(template = "way_wide", timeoutTicks = 4000)
    public static void theBoxIsTheOwnersSixtyFour(GameTestHelper h) {
        flat(h);
        CompanionEntity c = spawn(h);
        h.setBlock(new BlockPos(HOME + 48, FEET + 25, HOME), Blocks.PUMPKIN);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 64));
        var seen = c.obtainAvailable("minecraft:pumpkin");
        h.assertTrue(seen.some(), "A pumpkin 48 across and 25 up is not in the box: " + seen);
        String line = chosen(c, "minecraft:pumpkin");
        h.assertTrue(line != null, "No way_search line naming the chosen pumpkin: " + journal(c, "way_search"));
        h.assertTrue(line.contains("\"radius\":64") && line.contains("\"depth\":64"), "The box is not sixty-four: " + line);
        h.assertTrue(line.contains("коробка 64 вбок, 64 вверх и 64 вниз"), "The line does not say the box in words: " + line);
        // The way up: a column of the steps it asks, within the thirty-two the reach allows.
        h.assertTrue(line.contains("\"shape\":\"column\""), "A pumpkin 25 up is no column: " + line);
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:pumpkin\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.PUMPKIN) >= 1, "The pumpkin 25 up is not in the bag: " + future.join().report());
            h.assertBlockPresent(Blocks.AIR, new BlockPos(HOME + 48, FEET + 25, HOME));
        });
    }

    /** A pumpkin eighty across is outside the box, and the line says it exists just outside — not that nothing is there. */
    @GameTest(template = "way_wide", timeoutTicks = 4000)
    public static void aBlockOutsideTheBoxIsSaidOutside(GameTestHelper h) {
        flat(h);
        CompanionEntity c = spawn(h);
        BlockPos far = new BlockPos(HOME + 80, FEET + 2, HOME);
        h.setBlock(far, Blocks.PUMPKIN);
        var seen = c.obtainAvailable("minecraft:pumpkin");
        h.assertTrue(!seen.some(), "A pumpkin 80 across is in a sixty-four box: " + seen);
        List<String> lines = journal(c, "way_search").stream().filter(line -> line.contains("\"item\":\"minecraft:pumpkin\"")).toList();
        String outside = lines.stream().filter(line -> line.contains("\"outside\":")).findFirst().orElse(null);
        h.assertTrue(outside != null, "No «за коробкой» line for the pumpkin 80 across: " + lines);
        h.assertTrue(outside.contains("за коробкой 64 вбок есть 1"), "The line does not say the block is just outside the box: " + outside);
        h.assertBlockPresent(Blocks.PUMPKIN, far);
        h.succeed();
    }

    /** His own smaller setting cuts the box, and the line says so — never a silent shrink back to thirty-two. */
    @GameTest(template = "way_wide", timeoutTicks = 4000)
    public static void hisOwnSmallerRadiusCutsTheBoxAndSaysSo(GameTestHelper h) {
        flat(h);
        CompanionEntity c = spawn(h);
        c.setSearchRadius(16);
        h.setBlock(new BlockPos(HOME + 20, FEET + 4, HOME), Blocks.PUMPKIN);
        c.obtainAvailable("minecraft:pumpkin");
        List<String> lines = journal(c, "way_search").stream().filter(line -> line.contains("\"item\":\"minecraft:pumpkin\"")).toList();
        String line = lines.stream().filter(entry -> entry.contains("\"candidates\":")).findFirst().orElse(null);
        h.assertTrue(line != null, "No search line for the pumpkin 20 across with a sixteen box: " + lines);
        h.assertTrue(line.contains("\"radius\":16") && line.contains("\"depth\":64"), "The box is not cut to sixteen across: " + line);
        h.assertTrue(line.contains("коробка 16 вбок, 64 вверх и 64 вниз (урезана моим поиском 16)"), "The cut is not said in words: " + line);
        h.assertTrue(line.contains("блоков не нашёл"), "The pumpkin 20 across was found in a sixteen box: " + line);
        String outside = lines.stream().filter(entry -> entry.contains("\"outside\":")).findFirst().orElse(null);
        h.assertTrue(outside != null && outside.contains("за коробкой 16 вбок"), "The block just outside the cut box is not said: " + lines);
        h.succeed();
    }

    /** The radius and the way's switch are kept in the save across a reload; the radius is clamped to the setting's own range. */
    @GameTest(template = "empty")
    public static void theRadiusAndTheWaySwitchAreKeptInTheSave(GameTestHelper h) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), HOME, FEET, HOME);
        // The owner's default: sixty-four across, the way on.
        h.assertTrue(c.getSearchRadius() == 64, "A new companion is not sixty-four across: " + c.getSearchRadius());
        h.assertTrue(c.buildWay(), "The way is off for a new companion");
        c.setSearchRadius(96);
        c.setBuildWay(false);
        var tag = new CompoundTag();
        c.addAdditionalSaveData(tag);
        CompanionEntity loaded = h.spawn(AICompanionMod.COMPANION.get(), HOME + 2, FEET, HOME);
        loaded.readAdditionalSaveData(tag);
        h.assertTrue(loaded.getSearchRadius() == 96, "The radius did not survive the save: " + loaded.getSearchRadius());
        h.assertTrue(!loaded.buildWay(), "The way's switch did not survive the save");
        // The setting's own range: sixteen at the least, the game's hundred and twenty-eight at the most.
        loaded.setSearchRadius(4);
        h.assertTrue(loaded.getSearchRadius() == 16, "A radius below sixteen is not clamped: " + loaded.getSearchRadius());
        loaded.setSearchRadius(500);
        h.assertTrue(loaded.getSearchRadius() == 128, "A radius above the game's most is not clamped: " + loaded.getSearchRadius());
        h.succeed();
    }

    /** The way's switch off: a block up the column is refused, and the line says why — «switch off», never a silent nothing. */
    @GameTest(template = "way_wide", timeoutTicks = 4000)
    public static void theWaySwitchOffRefusesAndSaysSo(GameTestHelper h) {
        flat(h);
        CompanionEntity c = spawn(h);
        c.setBuildWay(false);
        h.setBlock(new BlockPos(HOME + 3, FEET + 8, HOME), Blocks.PUMPKIN);
        var seen = c.obtainAvailable("minecraft:pumpkin");
        h.assertTrue(!seen.some(), "A pumpkin eight up is reached with the way off: " + seen);
        List<String> lines = journal(c, "way_search").stream().filter(line -> line.contains("\"item\":\"minecraft:pumpkin\"")).toList();
        h.assertTrue(lines.stream().anyMatch(line -> line.contains("switch off")), "The refusal does not say the switch is off: " + lines);
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:pumpkin\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.PUMPKIN) == 0, "A pumpkin appeared with the way off: " + report);
            h.assertTrue(report.contains("Рядом не вижу") || report.contains("вижу, но не достать"), "The refusal is not said with its reason: " + report);
            h.assertBlockPresent(Blocks.PUMPKIN, new BlockPos(HOME + 3, FEET + 8, HOME));
        });
    }

    // ---- helpers ----

    private static CompletableFuture<CompanionPlanRunner.Outcome> run(GameTestHelper h, CompanionEntity c, String json) {
        var reply = dev.aicompanion.client.CompanionPlanRequirements.apply("добудь",
                new dev.aicompanion.client.CompanionReply("", dev.aicompanion.client.CompanionPlanCodec.parsePlan(com.google.gson.JsonParser.parseString(json))));
        return CompanionPlanRunner.run(reply.actions(),
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-way-radius", () -> false));
    }

    private static CompanionEntity spawn(GameTestHelper h) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), HOME, FEET, HOME);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(HOME - 2, FEET, HOME));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        owner.setInvulnerable(true);
        c.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        c.setSearchRadius(64);
        c.setLifestyle(false, false);
        return c;
    }

    /** The empty arena of the template, floored and walled; the air cells are left as they are. */
    private static void flat(GameTestHelper h) {
        for (int x = 0; x < SIDE; x++) for (int z = 0; z < SIDE; z++) {
            boolean border = x == 0 || x == SIDE - 1 || z == 0 || z == SIDE - 1;
            h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
            for (int y = 1; y <= GROUND; y++) {
                if (border) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
                else h.setBlock(new BlockPos(x, y, z), y == GROUND ? Blocks.GRASS_BLOCK : Blocks.DIRT);
            }
            if (border) for (int y = GROUND + 1; y < TOP; y++) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
        }
    }

    private static String chosen(CompanionEntity c, String item) {
        return journal(c, "way_search").stream().filter(entry -> entry.contains("\"item\":\"" + item + "\"") && entry.contains("\"chosen\":\"")).findFirst().orElse(null);
    }

    private static List<String> journal(CompanionEntity c, String event) {
        dev.aicompanion.CompanionDiagnostics.flush();
        var directory = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("logs").resolve("aicompanion");
        List<String> lines = new ArrayList<>();
        for (String name : List.of("companion-3.jsonl", "companion-2.jsonl", "companion-1.jsonl", "companion-latest.jsonl")) {
            try { lines.addAll(java.nio.file.Files.readAllLines(directory.resolve(name))); } catch (java.io.IOException | java.io.UncheckedIOException ignored) { }
        }
        return lines.stream().filter(line -> line.contains(c.getUUID().toString()) && line.contains("\"event\":\"" + event + "\"")).toList();
    }

    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        return count;
    }
}

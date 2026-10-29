package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRequirements;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionReply;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * the player 22.10: «чтобы он сам мог охотиться и убивать животных, жарить их мясо» (and in the game: «А ты можешь сам добыть нам
 * мясо?» — «Пока не выйдет»). Like a player: grown animals, chased and hit, the drops picked up from the ground, the last pair
 * left, calves and somebody's animals spared, the nearest on foot first.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionHuntTests {
    private static final int GROUND = 5;

    /** Cows grazing on a slope: two are taken, the meat is picked up from the grass, the rest of the herd stays. */
    @GameTest(template = "tall_tree", timeoutTicks = 1800)
    public static void cowsOnASlopeAreHuntedAndTheMeatPickedUp(GameTestHelper h) {
        ground(h);
        for (int x = 18; x <= 30; x++) for (int z = 1; z <= 30; z++) for (int y = GROUND + 1; y <= GROUND + Math.min(3, (x - 17) / 3); y++)
            h.setBlock(new BlockPos(x, y, z), y == GROUND + Math.min(3, (x - 17) / 3) ? Blocks.GRASS_BLOCK : Blocks.DIRT);
        for (int i = 0; i < 5; i++) h.spawn(EntityType.COW, 20 + i * 2, GROUND + 4, 10 + i * 3);
        CompanionEntity companion = spawn(h, 10, 16);
        long id = order(h, companion, "minecraft:cow", 2);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(result.success() && count(companion, Items.BEEF) >= 2, result.message());
            h.assertTrue(result.message().contains("2 коровы") && result.message().contains("прибавилось"), "Report: " + result.message());
            String said = dev.aicompanion.client.CompanionDialogue.summarize(plan("minecraft:cow", 2), result.message());
            h.assertTrue(said.equals("Готово, добыл 2 коровы."), "Said: " + said);
            h.assertTrue(animals(h, EntityType.COW) == 3, "Not three cows left: " + animals(h, EntityType.COW));
            h.assertTrue(lying(h).isEmpty(), "Drops left lying: " + lying(h));
        });
    }

    /**
     * Game log 22.10 (session fb39678e, 21:56–21:58): one pig grazed nearby, and «займись охотой» ended at once with «1 оставил на
     * развод». A player takes the pig: a lone animal is game, the ones to keep get a name tag.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aLonePigNearbyIsHunted(GameTestHelper h) {
        ground(h);
        h.spawn(EntityType.PIG, 20, GROUND + 1, 16);
        CompanionEntity companion = spawn(h, 12, 16);
        long id = order(h, companion, "any", 3);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(animals(h, EntityType.PIG) == 0 && count(companion, Items.PORKCHOP) >= 1, "The pig was left: " + result.message());
            h.assertTrue(!result.success() && result.message().contains("1 свинью"), result.message());
            // The count was never read («d+» for «\d+»): one pig of three was said as «Никого не добыл».
            String said = dev.aicompanion.client.CompanionDialogue.summarize(plan("any", 3), "План остановлен: " + result.message());
            h.assertTrue(said.equals("Добыл только 1 свинью — больше рядом не нашёл."), "Said: " + said);
        });
    }

    /** The same session: «пускай ищет даже если еда далеко». The cows are 50 blocks off, within his search radius. */
    @GameTest(template = "large_search", timeoutTicks = 2400)
    public static void cowsFiftyBlocksAwayAreFound(GameTestHelper h) {
        // A corridor of its own, walled off from the neighbouring tests.
        for (int x = 60; x < 124; x++) for (int z = 60; z < 69; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
            h.setBlock(new BlockPos(x, 4, z), Blocks.BEDROCK);
            if (x == 60 || x == 123 || z == 60 || z == 68) for (int y = 1; y < 4; y++) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
        }
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 64, 1, 64);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(62, 1, 62));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(64);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        for (int i = 0; i < 2; i++) h.spawn(EntityType.COW, 114 + i * 3, 1, 64);
        long id = order(h, companion, "minecraft:cow", 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(result.success() && count(companion, Items.BEEF) >= 1, result.message());
        });
    }

    /**
     * A cow on a ledge six blocks up that he cannot climb, well within the hunt radius: the pig is taken, and no walk is wasted on the
     * cow (a food run once walked into the corner of its pen for thirteen seconds towards a cow in the next test's).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void anAnimalOnALedgeOutOfReachIsNotWalkedTowards(GameTestHelper h) {
        ground(h);
        for (int x = 22; x <= 26; x++) for (int z = 14; z <= 18; z++) for (int y = GROUND + 1; y <= GROUND + 6; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        var cow = h.spawn(EntityType.COW, 24, GROUND + 7, 16);
        cow.setNoAi(true);
        h.spawn(EntityType.PIG, 10, GROUND + 1, 16).setNoAi(true);
        CompanionEntity companion = spawn(h, 8, 16);
        long id = order(h, companion, "food", 3);
        double start = companion.getX();
        double[] east = {0};
        h.onEachTick(() -> east[0] = Math.max(east[0], companion.getX() - start));
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(count(companion, Items.PORKCHOP) >= 1, "The pig was left: " + result.message());
            h.assertTrue(cow.isAlive(), "The cow on the ledge died");
            h.assertTrue(east[0] < 8, "Walked towards the cow on the ledge: " + Math.round(east[0]) + " blocks");
            h.assertTrue(result.success() && result.message().contains("пути нет"), result.message());
        });
    }

    /** Nothing to hunt: the plan stops at the hunt with its real reason, not with «жарить нечего» from the furnace. */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void anEmptyHuntDoesNotGoOnToTheFurnace(GameTestHelper h) {
        ground(h);
        CompanionEntity companion = spawn(h, 12, 16);
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString(
                "[{\"action\":\"hunt_animals\",\"item\":\"any\",\"count\":3},{\"action\":\"smelt_items\",\"item\":\"minecraft:meat\"}]"));
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), companion.getOwnerId(), "gametest-empty-hunt", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            var outcome = future.join();
            h.assertTrue(outcome.report().contains("Никого не добыл") && !outcome.report().contains("жарить нечего")
                    && outcome.skipped().stream().anyMatch(step -> step.type().equals("smelt_items")), outcome.report());
        });
    }

    /**
     * Lambs, a named sheep and a leashed one are not game; the four grown free ones are. Own batch: the hunt looks 32 blocks around him,
     * past the arena walls, so a free sheep of a neighbouring test in the grid turns "only the spared ones are left" into "no path to the rest".
     */
    @GameTest(template = "tall_tree", batch = "hunt_sheep_spared", timeoutTicks = 1600)
    public static void lambsNamedAndLeashedSheepAreSpared(GameTestHelper h) {
        ground(h);
        for (int i = 0; i < 4; i++) h.spawn(EntityType.SHEEP, 19 + i * 2, GROUND + 1, 10);
        Animal lambA = h.spawn(EntityType.SHEEP, 20, GROUND + 1, 20), lambB = h.spawn(EntityType.SHEEP, 22, GROUND + 1, 20);
        lambA.setAge(-24000); lambB.setAge(-24000);
        Animal named = h.spawn(EntityType.SHEEP, 24, GROUND + 1, 22); named.setCustomName(Component.literal("Долли"));
        CompanionEntity companion = spawn(h, 12, 16);
        BlockPos post = new BlockPos(27, GROUND + 1, 24); h.setBlock(post, Blocks.OAK_FENCE);
        Animal leashed = h.spawn(EntityType.SHEEP, 26, GROUND + 1, 22);
        leashed.setLeashedTo(net.minecraft.world.entity.decoration.LeashFenceKnotEntity.getOrCreateKnot(h.getLevel(), h.absolutePos(post)), true);
        long id = order(h, companion, "minecraft:sheep", 8);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(lambA.isAlive() && lambB.isAlive(), "He killed a lamb");
            h.assertTrue(named.isAlive(), "He killed the named sheep");
            h.assertTrue(leashed.isAlive(), "He killed the leashed sheep");
            h.assertTrue(!result.success() && result.message().contains("детёныши") && count(companion, Items.MUTTON) >= 1, result.message());
        });
    }

    /** Cows in a closed pen are out of reach; the cows in the open, farther away, are hunted instead of standing at the fence. */
    @GameTest(template = "tall_tree", timeoutTicks = 1800)
    public static void cowsBehindAFenceAreLeftForTheOnesInTheOpen(GameTestHelper h) {
        ground(h);
        for (int x = 18; x <= 22; x++) for (int z = 13; z <= 19; z++)
            if (x == 18 || x == 22 || z == 13 || z == 19) { h.setBlock(new BlockPos(x, GROUND + 1, z), Blocks.OAK_FENCE); h.setBlock(new BlockPos(x, GROUND + 2, z), Blocks.OAK_FENCE); }
        List<Animal> penned = List.of(h.spawn(EntityType.COW, 20, GROUND + 1, 15), h.spawn(EntityType.COW, 20, GROUND + 1, 17), h.spawn(EntityType.COW, 21, GROUND + 1, 16));
        for (int i = 0; i < 3; i++) h.spawn(EntityType.COW, 5 + i * 2, GROUND + 1, 26);
        CompanionEntity companion = spawn(h, 14, 16);
        long id = order(h, companion, "minecraft:beef", 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(result.success() && count(companion, Items.BEEF) >= 1, result.message());
            h.assertTrue(penned.stream().allMatch(Animal::isAlive), "A cow in the closed pen died");
        });
    }

    /** A deep trench: the chickens across it are nearer by the straight line, the ones on his side on foot. */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void chickensOnHisSideOfTheTrenchFirst(GameTestHelper h) {
        ground(h);
        for (int x = 17; x <= 19; x++) for (int z = 1; z <= 24; z++) for (int y = 1; y <= GROUND; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        List<Animal> across = List.of(h.spawn(EntityType.CHICKEN, 21, GROUND + 1, 16), h.spawn(EntityType.CHICKEN, 22, GROUND + 1, 15), h.spawn(EntityType.CHICKEN, 22, GROUND + 1, 17));
        for (int i = 0; i < 3; i++) h.spawn(EntityType.CHICKEN, 6 + i, GROUND + 1, 22);
        for (Animal chicken : h.getLevel().getEntitiesOfClass(Animal.class, new AABB(h.absolutePos(BlockPos.ZERO)).expandTowards(32, 12, 32))) chicken.setNoAi(true);
        CompanionEntity companion = spawn(h, 14, 16);
        int[] maxX = {0};
        h.onEachTick(() -> maxX[0] = Math.max(maxX[0], h.relativePos(companion.blockPosition()).getX()));
        long id = order(h, companion, "minecraft:chicken", 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(result.success() && count(companion, Items.CHICKEN) >= 1, result.message());
            h.assertTrue(across.stream().allMatch(Animal::isAlive) && maxX[0] < 17, "Went around the trench for the chickens across it");
        });
    }

    /**
     * «Добудь мяса и пожарь» as the model may send it: the kind by its meat is accepted, a missing furnace step is added, the
     * number said in words wins, an unknown kind spoils the whole plan.
     */
    @GameTest(template = "empty")
    public static void huntPlansAreCheckedAndTheMeatGoesToTheFurnace(GameTestHelper h) {
        var byMeat = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"hunt_animals\",\"item\":\"minecraft:porkchop\"}]"));
        h.assertTrue(byMeat.size() == 1 && byMeat.getFirst().item().equals("minecraft:pig") && byMeat.getFirst().count() == 3, "Plan: " + byMeat);
        var cooked = CompanionPlanRequirements.apply("Добудь нам мяса со свиней и пожарь", new CompanionReply("", byMeat)).actions();
        h.assertTrue(cooked.size() == 2 && cooked.get(1).type().equals("smelt_items") && cooked.get(1).item().equals("minecraft:porkchop")
                && cooked.get(1).count() == PlannedAction.ALL_CARRIED, "No furnace step: " + cooked);
        var any = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"hunt_animals\",\"item\":\"any\",\"count\":2}]"));
        var anyCooked = CompanionPlanRequirements.apply("добудь мяса и приготовь его", new CompanionReply("", any)).actions();
        h.assertTrue(anyCooked.size() == 2 && anyCooked.get(1).item().equals("minecraft:meat"), "Any meat not cooked: " + anyCooked);
        // Marathon 23.10: «не жарь» was not read as a «не» (a typo in the rule), and the furnace step came all the same.
        var raw = CompanionPlanRequirements.apply("добудь мяса, но не жарь", new CompanionReply("", any)).actions();
        h.assertTrue(raw.size() == 1, "Cooked though told not to: " + raw);
        var five = CompanionPlanRequirements.apply("добудь пять коров", new CompanionReply("", CompanionPlanCodec.parsePlan(
                JsonParser.parseString("[{\"action\":\"hunt_animals\",\"item\":\"minecraft:cow\",\"count\":1}]")))).actions();
        h.assertTrue(five.size() == 1 && five.getFirst().count() == 5, "Spoken number lost: " + five);
        boolean rejected = false;
        try { CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"hunt_animals\",\"item\":\"minecraft:villager\"}]")); }
        catch (IllegalStateException expected) { rejected = true; }
        h.assertTrue(rejected, "A villager was accepted as game");
        h.succeed();
    }

    /** The whole request in the world: two cows hunted, the beef cooked in the furnace nearby. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void huntThenCookAsOnePlan(GameTestHelper h) {
        ground(h);
        for (int i = 0; i < 5; i++) h.spawn(EntityType.COW, 20 + i, GROUND + 1, 12 + i * 2);
        h.setBlock(new BlockPos(13, GROUND + 1, 19), Blocks.FURNACE);
        CompanionEntity companion = spawn(h, 12, 16);
        companion.setItem(0, new ItemStack(Items.COAL, 2));
        // As in the game (radius 64–96): free furnaces are looked for within 32 blocks, and the chase may end farther than 16 from it.
        companion.setSearchRadius(32);
        var plan = CompanionPlanRequirements.apply("добудь мяса из двух коров и пожарь", new CompanionReply("", CompanionPlanCodec.parsePlan(
                JsonParser.parseString("[{\"action\":\"hunt_animals\",\"item\":\"minecraft:cow\",\"count\":2}]")))).actions();
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), companion.getOwnerId(), "gametest-hunt-plan", () -> false));
        java.util.List<String> seen = new java.util.ArrayList<>();
        h.onEachTick(() -> { String now = companion.getLastTaskResult(); if (seen.isEmpty() || !seen.getLast().endsWith(now)) seen.add(h.getTick() + "@" + companion.blockPosition().toShortString() + ":" + now); });
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            var outcome = future.join();
            h.assertTrue(outcome.skipped().isEmpty() && count(companion, Items.COOKED_BEEF) >= 2 && count(companion, Items.BEEF) == 0,
                    String.join(" / ", seen).replace('\n', ' ') + " | furnace=" + h.absolutePos(new BlockPos(13, GROUND + 1, 19)).toShortString());
        });
    }

    /**
     * In the hunt-and-cook test a cow wandered to the furnace and one blocked approach gave it up for good. Here three cows stand
     * in the only way to it for a while; he loads it from the edge, waits the cooking out, and then must still walk up for the
     * product: the waiting used to age the approach clock, and «не могу снова подойти» left the meat in the furnace.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void cowsInFrontOfTheFurnaceOnlyDelayTheCooking(GameTestHelper h) {
        ground(h);
        // A one-wide passage to the furnace, walls of bedrock.
        for (int x = 13; x <= 19; x++) for (int z = 15; z <= 17; z++) for (int y = GROUND + 1; y <= GROUND + 2; y++)
            if (z != 16 || x == 13) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
        h.setBlock(new BlockPos(14, GROUND + 1, 16), Blocks.FURNACE);
        List<Animal> cows = List.of(h.spawn(EntityType.COW, 15, GROUND + 1, 16), h.spawn(EntityType.COW, 16, GROUND + 1, 16), h.spawn(EntityType.COW, 17, GROUND + 1, 16));
        for (Animal cow : cows) { cow.setNoAi(true); cow.setCustomName(Component.literal("Бурёнка")); }
        h.runAtTickTime(130, () -> cows.forEach(cow -> cow.discard()));
        CompanionEntity companion = spawn(h, 23, 16);
        companion.setItem(0, new ItemStack(Items.BEEF, 1)); companion.setItem(1, new ItemStack(Items.COAL, 1));
        h.assertTrue(companion.startWork("smelt_items", "minecraft:beef", 0).success(), "Smelting not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!companion.isWorking() && companion.didLastTaskSucceed() && count(companion, Items.COOKED_BEEF) == 1, companion.getLastTaskResult());
            h.assertTrue(blocks(h, Blocks.FURNACE) == 1, "He set down a furnace of his own");
        });
    }

    private static int blocks(GameTestHelper h, net.minecraft.world.level.block.Block block) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 0, 0), new BlockPos(31, 12, 31))) if (h.getBlockState(pos).is(block)) count++;
        return count;
    }

    private static void ground(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= GROUND + 6; y++) {
            boolean border = x == 0 || x == 31 || z == 0 || z == 31;
            // Walls keep the herds of neighbouring tests apart.
            if (y <= GROUND) h.setBlock(new BlockPos(x, y, z), y == 0 || border ? Blocks.BEDROCK : y == GROUND ? Blocks.GRASS_BLOCK : Blocks.DIRT);
            else if (border) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
        }
    }

    private static CompanionEntity spawn(GameTestHelper h, int x, int z) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), x, GROUND + 1, z);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(x - 3, GROUND + 1, z - 3));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        return companion;
    }

    private static long order(GameTestHelper h, CompanionEntity companion, String item, int count) {
        var player = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        CompanionActionResult accepted = CompanionEvents.executeAction(player, "hunt_animals", item, count);
        h.assertTrue(accepted.success(), "Hunt not accepted: " + accepted.message());
        return companion.activeWorkId();
    }

    private static List<PlannedAction> plan(String item, int count) {
        return CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"hunt_animals\",\"item\":\"" + item + "\",\"count\":" + count + "}]"));
    }

    private static CompanionActionResult result(GameTestHelper h, CompanionEntity companion, long id) {
        CompanionActionResult result = companion.workResult(id);
        h.assertTrue(result != null && !companion.isWorking(), "Still hunting: " + companion.getLastTaskResult());
        return result;
    }

    private static int animals(GameTestHelper h, EntityType<?> type) {
        return h.getLevel().getEntitiesOfClass(Animal.class, new AABB(h.absolutePos(BlockPos.ZERO)).expandTowards(32, 16, 32), a -> a.isAlive() && a.getType() == type).size();
    }

    private static List<ItemEntity> lying(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(BlockPos.ZERO)).expandTowards(32, 16, 32), ItemEntity::isAlive);
    }

    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
}

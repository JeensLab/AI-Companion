package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.CompanionFishingInvitation;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.CompanionWorkCommands;
import dev.aicompanion.entity.CompanionBobber;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionObtain;
import dev.aicompanion.entity.CompanionObtain.Step;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The player 24.11: he fishes as a player does. A rod of his is cast from the bank, the bite is waited for, the catch is the game's
 * own (its fishing loot table), flies out of the water to him and is picked up; the rod wears. No rod: one is got first.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionFishingTests {
    private static final int GROUND = 5;

    /** «Налови 2 рыбы» by a pond with a rod in his bag: two fish more in the bag, the rod worn by its casts, no bobber left out. */
    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void twoFishAreCaughtWithHisRod(GameTestHelper h) {
        pond(h);
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.FISHING_ROD));
        var future = run(h, c, "[{\"action\":\"fish\",\"count\":2}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(fish(c) >= 2, "Fish in his bag " + fish(c) + ": " + report);
            ItemStack rod = rod(c);
            h.assertTrue(!rod.isEmpty() && rod.getDamageValue() >= 2, "The rod is not worn by two catches: " + rod.getDamageValue());
            h.assertTrue(report.contains("Наловил"), "Not said what was caught: " + report);
            h.assertTrue(bobbers(h) == 0, "A bobber is left out after the work");
        });
    }

    /** No rod, but sticks and string in his bag: the rod is made first (the plan gets what the step is short of), then he fishes. */
    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void noRodIsMadeOfHisSticksAndString(GameTestHelper h) {
        pond(h);
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.STICK, 3));
        c.setItem(1, new ItemStack(Items.STRING, 2));
        c.setItem(2, new ItemStack(Items.CRAFTING_TABLE));
        var future = run(h, c, "[{\"action\":\"fish\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(fish(c) >= 1 && !rod(c).isEmpty() && count(c, Items.STRING) == 0, "Fish " + fish(c) + ", rod " + rod(c) + ", string " + count(c, Items.STRING) + ": " + report);
        });
    }

    /** No rod and nothing to make one of: said, nothing cast. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void noRodAndNoStringIsSaid(GameTestHelper h) {
        pond(h);
        CompanionEntity c = spawn(h, 12, 16);
        var future = run(h, c, "[{\"action\":\"fish\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(fish(c) == 0 && bobbers(h) == 0 && report.contains("Рядом не вижу"), "Fished with no rod, or not said what the rod lacks: " + report);
        });
    }

    /** A rod but no water about: said as it is, the rod is back in his bag. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void noWaterAboutIsSaid(GameTestHelper h) {
        pond(h);
        for (int x = 16; x <= 24; x++) for (int z = 12; z <= 20; z++) for (int y = 1; y <= GROUND; y++) h.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.FISHING_ROD));
        var future = run(h, c, "[{\"action\":\"fish\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("не вижу воды"), "Not said that there is no water: " + report);
            h.assertTrue(rod(c).getDamageValue() == 0 && !c.getMainHandItem().is(Items.FISHING_ROD), "The rod is worn or left in his hand");
        });
    }

    /** «Порыбачь» with no number, his rod one catch from breaking: he fishes until it breaks and says what he caught, as a success. */
    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void withNoNumberHeFishesUntilTheRodBreaks(GameTestHelper h) {
        pond(h);
        CompanionEntity c = spawn(h, 12, 16);
        ItemStack worn = new ItemStack(Items.FISHING_ROD);
        worn.setDamageValue(worn.getMaxDamage() - 1);
        c.setItem(0, worn);
        var future = run(h, c, "[{\"action\":\"fish\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(rod(c).isEmpty() && report.contains("Удочка сломалась"), "Rod " + rod(c) + ": " + report);
            int caught = 0;
            for (int slot = 0; slot < c.getContainerSize(); slot++) caught += c.getItem(slot).getCount();
            h.assertTrue(caught >= 1 && bobbers(h) == 0, "Nothing of the one catch in his bag, or a bobber left out: " + report);
        });
    }

    /** His rod in his other hand and nothing else: it is his rod, taken into the hand that casts. */
    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void aRodInHisOtherHandIsCastToo(GameTestHelper h) {
        pond(h);
        CompanionEntity c = spawn(h, 12, 16);
        c.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.FISHING_ROD));
        var future = run(h, c, "[{\"action\":\"fish\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(fish(c) >= 1, "No fish with the rod in his other hand: " + future.join().report());
        });
    }

    /** «Порыбачь» with no cell free in his bag: nothing is cast and it is no success, though the order names no number. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aFullBagAtTheStartIsNoSuccess(GameTestHelper h) {
        pond(h);
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.FISHING_ROD));
        for (int slot = 1; slot < c.getContainerSize(); slot++) c.setItem(slot, new ItemStack(Items.DIRT, 64));
        var future = run(h, c, "[{\"action\":\"fish\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Сумка забита") && !c.didLastTaskSucceed() && rod(c).getDamageValue() == 0 && bobbers(h) == 0, "A full bag: " + report);
        });
    }

    /** «Стоп» while the bobber is out: the line is in at once, the rod is back in his bag, nothing is caught after it. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void stopTakesTheLineIn(GameTestHelper h) {
        pond(h);
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.FISHING_ROD));
        run(h, c, "[{\"action\":\"fish\"}]");
        boolean[] stopped = {false};
        long[] at = {0};
        h.onEachTick(() -> {
            if (stopped[0] || bobbers(h) != 1) return;
            h.assertTrue(CompanionEvents.executeAction(h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId()), "stop", "", 1).success(), "Stop not accepted");
            stopped[0] = true; at[0] = h.getTick();
        });
        h.succeedWhen(() -> {
            h.assertTrue(stopped[0] && h.getTick() >= at[0] + 100, "Not cast yet, or not waited after «стоп»");
            h.assertTrue(bobbers(h) == 0, "The bobber is still out after «стоп»");
            h.assertTrue(!c.getMainHandItem().is(Items.FISHING_ROD) && count(c, Items.FISHING_ROD) == 1, "The rod is not back in his bag");
        });
    }

    /** «Добудь треску» by a pond no fish swims in, a rod in his bag: fished for, as many casts as it takes (other fish are kept too). */
    @GameTest(template = "tall_tree", timeoutTicks = 12000)
    public static void aCodIsFishedForWhereNoneSwims(GameTestHelper h) {
        pond(h);
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.FISHING_ROD));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:cod\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.COD) >= 1 && rod(c).getDamageValue() >= 1, "Cod " + count(c, Items.COD) + ", rod worn " + rod(c).getDamageValue() + ": " + future.join().report());
        });
    }

    /** No world touched: the phrases that are his without the model, the step as the model may write it, and «добудь треску» planned. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void theOrderIsReadAndPlanned(GameTestHelper h) {
        Map<String, Integer> phrases = Map.of("порыбачь", 0, "Олег, порыбачь-ка", 0, "сходи на рыбалку", 0, "налови рыбы", 0, "налови рыбы сколько сможешь", 0,
                "налови 5 рыб", 5, "налови пять рыб", 5, "поймай рыбу", 1, "поймай 3 рыбы", 3);
        for (var phrase : phrases.entrySet()) {
            var action = CompanionWorkCommands.parse(phrase.getKey(), "Олег");
            h.assertTrue(action != null && action.type().equals("fish") && action.count() == phrase.getValue() && action.item().equals("fish"), "«" + phrase.getKey() + "»: " + action);
        }
        for (String other : List.of("порыбачь 5 минут", "налови рыбы и пожарь", "поймай курицу", "не лови рыбу", "налови 100 рыб", "налови 0 рыб", "поймай 0 рыб")) {
            var action = CompanionWorkCommands.parse(other, "Олег");
            h.assertTrue(action == null || !action.type().equals("fish"), "«" + other + "» was taken as a fishing order: " + action);
        }
        var bare = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"fish\"}]")).getFirst();
        h.assertTrue(bare.count() == 0 && bare.item().equals("fish"), "fish with no number and no item: " + bare);
        var salmon = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"fish\",\"count\":3,\"item\":\"minecraft:salmon\"}]")).getFirst();
        h.assertTrue(salmon.count() == 3 && salmon.item().equals("minecraft:salmon"), "fish 3 salmon: " + salmon);
        for (String bad : List.of("{\"action\":\"fish\",\"item\":\"minecraft:saddle\"}", "{\"action\":\"fish\",\"count\":65}", "{\"action\":\"fish\",\"count\":0}")) {
            boolean refused = false;
            try { CompanionPlanCodec.parsePlan(JsonParser.parseString("[" + bad + "]")); } catch (RuntimeException invalid) { refused = true; }
            h.assertTrue(refused, "A plan of " + bad + " was let through");
        }
        var level = h.getLevel();
        // The water about him is asked of the rod; no cod swims there.
        var rod = CompanionObtain.plan(level, List.of(new ItemStack(Items.FISHING_ROD)), false, "minecraft:cod", 2, thing -> thing == Items.FISHING_ROD, thing -> false);
        h.assertTrue(rod.steps().equals(List.of(new Step("fish", "minecraft:cod", 2))), "Cod with a rod by the water: " + rod);
        var made = CompanionObtain.plan(level, List.of(new ItemStack(Items.STICK, 3), new ItemStack(Items.STRING, 2), new ItemStack(Items.CRAFTING_TABLE)), false,
                "minecraft:salmon", 1, thing -> thing == Items.FISHING_ROD, thing -> false);
        h.assertTrue(!made.refused() && made.steps().getLast().equals(new Step("fish", "minecraft:salmon", 1))
                && made.steps().stream().anyMatch(step -> step.type().equals("craft_item") && step.item().equals("minecraft:fishing_rod")), "Salmon with sticks and string: " + made);
        var swims = CompanionObtain.plan(level, List.of(new ItemStack(Items.FISHING_ROD), new ItemStack(Items.STONE_SWORD)), false, "minecraft:cod", 2, thing -> thing == Items.FISHING_ROD, thing -> true);
        h.assertTrue(swims.steps().equals(List.of(new Step("hunt_drop", "minecraft:cod", 2))), "Cod that swims about is fought for as before: " + swims);
        var dry = CompanionObtain.plan(level, List.of(new ItemStack(Items.FISHING_ROD)), false, "minecraft:cod", 2, thing -> false, thing -> false);
        h.assertTrue(dry.refused() && dry.steps().isEmpty(), "Cod with no water and no cod about: " + dry);
        var puffer = CompanionObtain.plan(level, List.of(new ItemStack(Items.FISHING_ROD)), false, "minecraft:pufferfish", 1, thing -> thing == Items.FISHING_ROD, thing -> false);
        h.assertTrue(puffer.refused() && puffer.steps().isEmpty(), "A pufferfish is not fished for by «добудь»: " + puffer);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void casualFishingAndOneShotConsent(GameTestHelper h) {
        var invite = new CompanionFishingInvitation();
        h.assertTrue(!invite.due(100, 20, 0) && !invite.due(24099, 20, 0)
                && invite.due(24100, 20, 0), "First invitation ignored its delay");
        invite.checked(24100);
        h.assertTrue(!invite.due(24699, 20, 0) && invite.due(24700, 20, 0), "Dry bank check was repeated too soon");
        for (String text : List.of("дада емае", "да погнали тогда", "да, идём рыбачить, брат")) {
            invite.offered(24700, 20, 0);
            h.assertTrue(invite.answer(text, 24701) == CompanionFishingInvitation.Answer.ACCEPT, "Missed consent: " + text);
            h.assertTrue(invite.answer("да", 24702) == CompanionFishingInvitation.Answer.OTHER, "Reused consent");
        }
        invite.offered(24700, 20, 0);
        h.assertTrue(invite.answer("да, но не сейчас", 24701) == CompanionFishingInvitation.Answer.DECLINE, "Refusal was ignored");
        invite.offered(24700, 20, 0);
        h.assertTrue(invite.answer("да", 25901) == CompanionFishingInvitation.Answer.OTHER, "Expired consent started fishing");
        invite.offered(24700, 20, 0);
        invite.reset();
        h.assertTrue(invite.answer("да", 24701) == CompanionFishingInvitation.Answer.OTHER, "Consent survived reset");
        for (String words : List.of("а теперь порыбачим", "Олег, айда к речке, рыбки половим", "пагнали рыбалить", "давай закинем удочки")) {
            String route = CompanionLocalCommands.route(words, "Олег");
            h.assertTrue(CompanionFishingInvitation.ORDER.equals(route), "Missed informal fishing order: " + words + " -> " + route);
        }
        for (String words : List.of("вчера мы рыбачили", "не пойдём на рыбалку", "может, порыбачим потом")) {
            h.assertTrue(!CompanionFishingInvitation.proposesTrip(words), "Discussion became an order: " + words);
        }
        h.assertTrue(!CompanionLocalCommands.cancelsPlans("companion fishing_ask"), "A question cancelled an order");
        h.succeed();
    }

    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void anAutonomousFishingBreakYieldsToThePlayer(GameTestHelper h) throws ReflectiveOperationException {
        pond(h);
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.FISHING_ROD));
        c.autonomy().setEnabled(true);
        c.autonomy().configureFishing(20);
        var nextFishing = c.autonomy().getClass().getDeclaredField("nextFishing");
        nextFishing.setAccessible(true);
        nextFishing.setLong(c.autonomy(), 0); // Only the scheduling delay is skipped; the real deed has to walk and cast.
        h.startSequence().thenWaitUntil(() -> {
            h.assertTrue(c.autonomy().deedName().contains("порыбачить"), "Fishing break did not start");
            h.assertTrue(bobbers(h) > 0, "Autonomous fishing did not cast");
        }).thenExecute(() -> {
            var player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
            CompanionEvents.executeAction(player, "stop", "", 1);
        }).thenIdle(40).thenExecute(() -> {
            h.assertTrue(!c.autonomy().running() && !c.hasActiveWorkOrder() && bobbers(h) == 0, "Fishing continued after stop");
            h.assertTrue(count(c, Items.FISHING_ROD) == 1, "Stopping lost or duplicated the rod");
        }).thenSucceed();
    }

    /** A pond nine by nine and five deep in the grass, its water level with the ground. */
    private static void pond(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= GROUND + 6; y++) {
            boolean border = x == 0 || x == 31 || z == 0 || z == 31, water = x >= 16 && x <= 24 && z >= 12 && z <= 20 && y >= 1;
            if (y <= GROUND) h.setBlock(new BlockPos(x, y, z), y == 0 || border ? Blocks.BEDROCK : water ? Blocks.WATER : y == GROUND ? Blocks.GRASS_BLOCK : Blocks.DIRT);
            else h.setBlock(new BlockPos(x, y, z), border ? Blocks.BEDROCK : Blocks.AIR);
        }
    }

    private static CompanionEntity spawn(GameTestHelper h, int x, int z) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), x, GROUND + 1, z);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(x - 2, GROUND + 1, z - 2));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        c.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        // The least radius: the water of the tests beside this one, beyond its walls, is out of his way.
        c.setSearchRadius(16);
        return c;
    }

    private static CompletableFuture<CompanionPlanRunner.Outcome> run(GameTestHelper h, CompanionEntity c, String json) {
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString(json));
        return CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-fishing", () -> false));
    }

    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        if (c.getMainHandItem().is(item)) count += c.getMainHandItem().getCount();
        return count;
    }

    private static int fish(CompanionEntity c) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(ItemTags.FISHES)) count += c.getItem(slot).getCount();
        return count;
    }

    private static ItemStack rod(CompanionEntity c) {
        if (c.getMainHandItem().is(Items.FISHING_ROD)) return c.getMainHandItem();
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(Items.FISHING_ROD)) return c.getItem(slot);
        return ItemStack.EMPTY;
    }

    private static int bobbers(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(CompanionBobber.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(40)).size();
    }
}

package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPickUpCommands;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.function.Function;

/**
 * «Подбери всё кроме лазурита» (idea XCEPT of #105): the named thing stays on the ground and the rest is taken. Before,
 * «всё» was dropped, «кроме лазурита» was looked up as a name, and he took the lapis alone.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionPickUpExceptTests {
    private static final String ANY = "companion work pick_up 64 minecraft:any";

    @GameTest(template = "large_search")
    public static void thePhraseRoutesToEverythingButTheNamed(GameTestHelper h) {
        for (String phrase : new String[] {"подбери всё кроме лазурита", "Олег, подбери всё, кроме лазурита", "подбери все, но не лазурит",
                "подними все вещи кроме лазурита", "подбери кроме лазурита", "подбери всё, только не лазурит", "кроме лазурита всё подбери",
                "подбери\tвсё\tкроме\tлазурита"}) {
            h.assertTrue((ANY + "!minecraft:lapis_lazuli").equals(route(phrase)), "«" + phrase + "» → " + route(phrase));
        }
        for (String phrase : new String[] {"подбери всё кроме лазурита и алмазов", "подбери всё, кроме лазурита, алмазов",
                "подбери всё кроме лазурита, лазурита и алмаза"}) {
            h.assertTrue((ANY + "!minecraft:lapis_lazuli,minecraft:diamond").equals(route(phrase)), "«" + phrase + "» → " + route(phrase));
        }
        // What was understood before stays as it was.
        h.assertTrue(ANY.equals(route("подбери всё")), "«подбери всё» → " + route("подбери всё"));
        h.assertTrue("companion work pick_up 64 minecraft:lapis_lazuli".equals(route("подбери лазурит")), "«подбери лазурит» → " + route("подбери лазурит"));
        // An exception the registry cannot name is not «everything» and not that thing: the phrase is the model's.
        h.assertTrue(route("подбери всё кроме фигнюшки") == null, "Unknown exception → " + route("подбери всё кроме фигнюшки"));
        h.assertTrue(CompanionPickUpCommands.fallback("подбери всё кроме фигнюшки", "Олег") == null, "Unknown exception falls back on everything");
        // «Everything but things» is no order, and a second order in the phrase is no thing to leave: both are the model's.
        for (String phrase : new String[] {"подбери всё кроме вещей", "подбери всё кроме лазурита и отнеси в Chest",
                "подбери всё кроме лазурита, потом сложи в Chest"}) {
            h.assertTrue(route(phrase) == null, "«" + phrase + "» → " + route(phrase));
        }
        // A word for several things alike leaves them all; a thing named before the mark is the pickup it was.
        String swords = route("подбери всё кроме Sword");
        h.assertTrue(swords != null && swords.contains("minecraft:iron_sword") && swords.contains("minecraft:diamond_sword"), "«кроме Sword» → " + swords);
        h.assertTrue("companion work pick_up 64 minecraft:diamond".equals(route("подбери алмазы, но не спеши")), "→ " + route("подбери алмазы, но не спеши"));
        // The line that names the order to the player names the things, not the raw id.
        String shown = dev.aicompanion.client.CompanionDialogue.itemName("minecraft:any!minecraft:stick,minecraft:diamond");
        h.assertTrue("вещи, кроме Stick, Diamond".equals(shown), "Shown as: " + shown);
        h.succeed();
    }

    /** Lapis, a diamond, sticks and dirt lie around: all but the lapis is taken, and the report says so. */
    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void everythingButTheLapisIsTaken(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        ItemEntity lapis = item(h, 11, 1, 8, Items.LAPIS_LAZULI, 3);
        ItemEntity diamond = item(h, 12, 1, 9, Items.DIAMOND, 2);
        ItemEntity stick = item(h, 13, 1, 8, Items.STICK, 1);
        ItemEntity dirt = item(h, 11, 1, 9, Items.DIRT, 4);
        PlannedAction step = CompanionLocalCommands.action(route("подбери всё кроме лазурита"));
        var result = CompanionEvents.executeAction(h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId()), step.type(), step.item(), step.count());
        h.assertTrue(result.success(), "The order was refused: " + result.message());
        h.succeedWhen(() -> {
            h.assertTrue(diamond.isRemoved() && stick.isRemoved() && dirt.isRemoved() && stored(companion, Items.DIAMOND) == 2
                            && stored(companion, Items.STICK) == 1 && stored(companion, Items.DIRT) == 4,
                    "The rest was not taken: diamonds " + stored(companion, Items.DIAMOND) + ", sticks " + stored(companion, Items.STICK)
                            + ", dirt " + stored(companion, Items.DIRT) + ", phase " + companion.workPhase() + ", " + companion.getLastTaskResult());
            h.assertTrue(companion.getLastTaskResult().contains("(7 шт.)") && companion.getLastTaskResult().contains("кроме"),
                    "The report: " + companion.getLastTaskResult());
            h.assertTrue(!lapis.isRemoved() && lapis.getItem().getCount() == 3 && stored(companion, Items.LAPIS_LAZULI) == 0,
                    "The lapis was taken: " + stored(companion, Items.LAPIS_LAZULI));
        });
    }

    /** Two things named: both stay, and so does every form of them (the lapis block with the lapis). */
    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void twoNamedThingsBothStay(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        ItemEntity lapis = item(h, 11, 1, 8, Items.LAPIS_LAZULI, 3);
        ItemEntity block = item(h, 13, 1, 9, Items.LAPIS_BLOCK, 1);
        ItemEntity diamond = item(h, 12, 1, 9, Items.DIAMOND, 2);
        ItemEntity stick = item(h, 13, 1, 8, Items.STICK, 1);
        PlannedAction step = CompanionLocalCommands.action(route("подбери всё кроме лазурита и алмазов"));
        var result = CompanionEvents.executeAction(h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId()), step.type(), step.item(), step.count());
        h.assertTrue(result.success(), "The order was refused: " + result.message());
        h.succeedWhen(() -> {
            h.assertTrue(stick.isRemoved() && stored(companion, Items.STICK) == 1 && !companion.isWorking(),
                    "The stick was not taken: " + companion.workPhase() + ", " + companion.getLastTaskResult());
            h.assertTrue(!lapis.isRemoved() && !block.isRemoved() && !diamond.isRemoved(), "A named thing was taken: " + companion.getLastTaskResult());
        });
    }

    /** The lapis left lying by one order is taken by the next one that names it; before that he walks over it and leaves it. */
    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void theThingLeftIsTakenWhenAskedFor(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        ItemEntity lapis = item(h, 10, 1, 8, Items.LAPIS_LAZULI, 3);
        item(h, 13, 1, 8, Items.STICK, 1);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        PlannedAction step = CompanionLocalCommands.action(route("подбери всё кроме лазурита"));
        h.assertTrue(CompanionEvents.executeAction(player, step.type(), step.item(), step.count()).success(), "The first order was refused");
        h.runAfterDelay(200, () -> {
            h.assertTrue(stored(companion, Items.STICK) == 1 && !lapis.isRemoved(), "After the first order: " + companion.getLastTaskResult());
            var second = CompanionEvents.executeAction(player, "pick_up", "minecraft:lapis_lazuli", 64);
            h.assertTrue(second.success(), "The second order was refused: " + second.message());
        });
        h.succeedWhen(() -> h.assertTrue(stored(companion, Items.LAPIS_LAZULI) == 3, "The lapis was not taken: " + companion.getLastTaskResult()));
    }

    /** Only the named thing lies around: nothing is taken and the answer says what does lie there. */
    @GameTest(template = "large_search", timeoutTicks = 300)
    public static void nothingButTheNamedLyingIsAnsweredHonestly(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        ItemEntity lapis = item(h, 11, 1, 8, Items.LAPIS_LAZULI, 3);
        PlannedAction step = CompanionLocalCommands.action(route("подбери всё кроме лазурита"));
        var result = CompanionEvents.executeAction(h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId()), step.type(), step.item(), step.count());
        h.assertTrue(!result.success() && result.message().contains("кроме"), "The answer: " + result.message());
        h.runAfterDelay(100, () -> {
            h.assertTrue(!lapis.isRemoved() && stored(companion, Items.LAPIS_LAZULI) == 0, "The lapis was taken");
            h.succeed();
        });
    }

    private static final Map<String, String> RU_NAMES = Map.of("lapis_lazuli", "Лазурит", "lapis_block", "Лазуритовый блок",
            "lapis_ore", "Лазуритовая руда", "deepslate_lapis_ore", "Глубинносланцевая лазуритовая руда", "diamond", "Алмаз", "stick", "Палка");
    private static final Function<Item, String> RU = item -> {
        String name = RU_NAMES.get(BuiltInRegistries.ITEM.getKey(item).getPath());
        return name != null ? name : new ItemStack(item).getHoverName().getString();
    };

    private static String route(String phrase) {
        return CompanionLocalCommands.route(phrase, "Олег", RU);
    }

    private static ItemEntity item(GameTestHelper h, int x, int y, int z, Item item, int count) {
        BlockPos at = h.absolutePos(new BlockPos(x, y, z));
        ItemEntity drop = new ItemEntity(h.getLevel(), at.getX() + .5, at.getY() + .2, at.getZ() + .5, new ItemStack(item, count));
        drop.setPickUpDelay(0);
        h.getLevel().addFreshEntity(drop);
        return drop;
    }

    private static int stored(CompanionEntity companion, Item item) {
        int total = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) {
            ItemStack stack = companion.getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static CompanionEntity spawn(GameTestHelper h) {
        for (int x = 0; x < 24; x++) for (int z = 0; z < 24; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 8, 1, 8);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(5, 1, 8));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        companion.setPickUpNearby(true, 16);
        companion.setFollowing(false);
        return companion;
    }
}

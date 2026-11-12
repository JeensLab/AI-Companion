package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionCrafting;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * A full bag and the orders that need a cell in it: the chain merges half stacks, a bench is made before the chest it is
 * for, one stack is held in a free hand when the bag has no cell, the held stack comes back into the bag, and the answer
 * names what is missing — the cell, or the material — in plain words, never a «цепочка крафта» that says nothing.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionFullBagTests {

    // ---- the owner's plan: take, come to him, set down a chest, put the things into it ----

    /** 27 things that are neither logs, planks nor a chest, so a test's chest has to be made from the wood it gives him. */
    private static final Item[] FILLER = {Items.DIRT, Items.GRAVEL, Items.SAND, Items.FLINT, Items.ANDESITE, Items.DIORITE,
            Items.GRANITE, Items.TUFF, Items.CALCITE, Items.DEEPSLATE, Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT,
            Items.REDSTONE, Items.LAPIS_LAZULI, Items.COAL, Items.CHARCOAL, Items.TORCH, Items.STICK, Items.STRING,
            Items.FEATHER, Items.LEATHER, Items.BONE, Items.GUNPOWDER, Items.BRICK, Items.NETHER_BRICK, Items.AMETHYST_SHARD};

    /**
     * The owner's plan: take the things from the chest at home, come to him at the mine, set down a chest there, put the
     * things into it. After the take one cell is left and there is no bench at the mine, so the chest is made there: the
     * bench is made and set down first, its cell comes free for the planks, and the taken things end up in the new chest.
     */
    @GameTest(template = "large_search", timeoutTicks = 1600)
    public static void theOwnersPlanPlacesTheChestAndStoresTheTakenThingsWithANearlyFullBag(GameTestHelper h) {
        arena(h);
        CompanionEntity c = spawn(h);
        ServerPlayer owner = player(h, c);
        BlockPos homeChest = new BlockPos(4, 1, 6);
        h.setBlock(homeChest, Blocks.CHEST);
        ((Container) h.getBlockEntity(homeChest)).setItem(0, new ItemStack(Items.BRICK, 30));
        ((Container) h.getBlockEntity(homeChest)).setItem(1, new ItemStack(Items.NETHER_BRICK, 30));
        BlockPos mine = new BlockPos(20, 1, 20);
        owner.teleportTo(h.absolutePos(mine).getX() + .5, h.absolutePos(mine).getY(), h.absolutePos(mine).getZ() + .5);
        // 24 things and three logs take 25 cells: two are free, and the take fills both of them — the bag is full.
        for (int slot = 0; slot < 24; slot++) c.setItem(slot, new ItemStack(FILLER[slot], 2));
        c.setItem(24, new ItemStack(Items.OAK_LOG, 3));
        var plan = CompanionPlanRunner.run(List.of(
                        new PlannedAction("take_items", 0, 64, "all"),
                        new PlannedAction("come", 0, 1, ""),
                        new PlannedAction("place_block", 0, 1, "minecraft:chest"),
                        new PlannedAction("store_items", 0, 1, "resources")),
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-full-bag-five-steps", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone() && !plan.isCompletedExceptionally(), "The plan is still running: " + c.getLastTaskResult());
            var outcome = plan.join();
            h.assertTrue(outcome.skipped().isEmpty() && !outcome.report().contains("Не выполнено"), "A step was skipped: " + outcome.report());
            h.assertTrue(outcome.report().contains("Поставил minecraft:chest: 1"), "The chest was not placed: " + outcome.report());
            h.assertTrue(outcome.report().contains("Сложил"), "The things were not put away: " + outcome.report());
            BlockPos placed = blockNear(h, Blocks.CHEST, h.absolutePos(mine), 5);
            h.assertTrue(placed != null, "No new chest by the mine: " + outcome.report());
            var into = (Container) h.getLevel().getBlockEntity(placed);
            h.assertTrue(countIn(contents(into), Items.BRICK) == 30, "The taken bricks are not in the new chest: " + summary(into));
            h.assertTrue(countIn(bag(c), Items.BRICK) == 0, "Bricks left in the bag: " + c.getLastTaskResult());
            h.assertTrue(countIn(bag(c), Items.OAK_LOG) == 0 && countIn(bag(c), Items.CHEST) == 0,
                    "The chest or the logs are still in the bag: " + c.getLastTaskResult());
            // A filler stack was held in a hand to free the cell the chest needed: it must be somewhere afterwards, in the
            // bag or in the chest, and never dropped — a lost stack cannot pass this test.
            h.assertTrue(countIn(bag(c), Items.DIRT) + countIn(contents(into), Items.DIRT) == 2,
                    "The stack held in a hand was lost: " + countIn(bag(c), Items.DIRT) + " dirt in the bag, "
                            + countIn(contents(into), Items.DIRT) + " in the chest: " + outcome.report());
        });
    }

    /**
     * Two steps of one order with a full bag: the first stack goes into the free hand, and once the cell it freed is taken
     * again the next step must take the other free hand instead of claiming the bag has room. Before the fix the second
     * step returned at once, so nothing was taken and the craft refused for space although the other hand was free.
     */
    @GameTest(template = "empty")
    public static void aFullBagFindsTheOtherFreeHandWhenOneAlreadyHoldsAStack(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        for (int slot = 0; slot < 27; slot++) c.setItem(slot, new ItemStack(FILLER[slot], 2));
        h.assertTrue(freeCells(bag(c)) == 0, "The bag is not full: " + freeCells(bag(c)));
        h.assertTrue(c.holdOneStackInHand(), "No stack was taken with both hands free");
        h.assertTrue(c.heldStackHands().equals(List.of("offhand")), "The stack is not in the free offhand: " + c.heldStackHands());
        h.assertTrue(freeCells(bag(c)) == 1, "The bag cell was not freed: " + freeCells(bag(c)));
        // What the craft made takes the freed cell back: the bag is full again and the other hand is still free.
        c.setItem(0, new ItemStack(Items.CHEST, 1));
        h.assertTrue(c.holdOneStackInHand(), "The other free hand was not used: " + c.heldStackHands());
        h.assertTrue(c.heldStackHands().equals(List.of("mainhand", "offhand")), "Only one hand holds a stack: " + c.heldStackHands());
        h.assertTrue(freeCells(bag(c)) == 1, "The second cell was not freed: " + freeCells(bag(c)));
        h.succeed();
    }

    /**
     * A stack held in a hand is the work's, not equipment: a save and a reload must put it back into the bag, not leave it
     * in the hand forever (where a later order would strand a second one beside it).
     */
    @GameTest(template = "empty")
    public static void aStackHeldInAHandComesBackAfterAReload(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        for (int slot = 0; slot < 27; slot++) c.setItem(slot, new ItemStack(FILLER[slot], 2));
        h.assertTrue(c.holdOneStackInHand(), "No stack was taken with both hands free");
        h.assertTrue(c.getOffhandItem().is(Items.DIRT) && countIn(bag(c), Items.DIRT) == 0,
                "The stack is not in the free offhand: " + c.getOffhandItem());
        var saved = new CompoundTag();
        c.addAdditionalSaveData(saved);
        var loaded = AICompanionMod.COMPANION.get().create(h.getLevel());
        loaded.readAdditionalSaveData(saved);
        h.assertTrue(countIn(bag(loaded), Items.DIRT) == 2, "The held stack did not come back into the bag: " + countIn(bag(loaded), Items.DIRT));
        h.assertTrue(loaded.getMainHandItem().isEmpty() && loaded.getOffhandItem().isEmpty(),
                "A stack stayed in a hand after the reload: " + loaded.getMainHandItem() + " " + loaded.getOffhandItem());
        h.succeed();
    }

    /**
     * A torch chain is made of coal and sticks: a full bag must hold some other stack, never the coal. The plan reads the
     * bag, so a coal taken into a hand is «не хватает «Уголь»» while he holds it — the coal must stay where the plan sees it.
     */
    @GameTest(template = "empty")
    public static void aFullBagKeepsTheCoalOfATorchChainInTheBag(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        // 25 stacks that are none of the torch chain's materials: the torch stack has to be made, so its cell is missing.
        Item[] plain = {Items.DIRT, Items.GRAVEL, Items.SAND, Items.FLINT, Items.ANDESITE, Items.DIORITE, Items.GRANITE,
                Items.TUFF, Items.CALCITE, Items.DEEPSLATE, Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT,
                Items.REDSTONE, Items.LAPIS_LAZULI, Items.CHARCOAL, Items.STRING, Items.FEATHER, Items.LEATHER, Items.BONE,
                Items.GUNPOWDER, Items.BRICK, Items.NETHER_BRICK, Items.AMETHYST_SHARD, Items.SLIME_BALL};
        c.setItem(0, new ItemStack(Items.COAL, 8));
        c.setItem(1, new ItemStack(Items.STICK, 4));
        for (int slot = 0; slot < plain.length; slot++) c.setItem(slot + 2, new ItemStack(plain[slot], 2));
        h.assertTrue(freeCells(bag(c)) == 0 && countIn(bag(c), Items.TORCH) == 0, "The bag is not full and torchless: " + freeCells(bag(c)));
        var crafted = c.craftHoldingIfTheBagIsFull("minecraft:torch", 1);
        h.assertTrue(crafted.success(), "The torches were refused although the coal and sticks are carried: " + crafted.message());
        h.assertTrue(countIn(bag(c), Items.TORCH) == 4, "No torches in the bag: " + crafted.message());
        h.assertTrue(countIn(bag(c), Items.COAL) == 7, "The coal left the bag for a hand: " + countIn(bag(c), Items.COAL) + " coal, hands " + c.heldStackHands());
        h.assertTrue(c.getOffhandItem().is(Items.DIRT), "The stack for the hand was not a filler: " + c.getOffhandItem() + " " + c.heldStackHands());
        h.succeed();
    }

    /**
     * A full bag, the wood for a chest, but no cell for the planks and no free hand to hold one stack in: the answer names
     * what is missing in plain words, the plan says which step failed, lists the rest as not done, and nothing claims the
     * storing happened.
     */
    @GameTest(template = "large_search", timeoutTicks = 600)
    public static void aFullBagSaysWhatIsMissingForTheChestAndThePlanSaysWhatFailed(GameTestHelper h) {
        arena(h);
        CompanionEntity c = spawn(h);
        for (int slot = 0; slot < 26; slot++) c.setItem(slot, new ItemStack(FILLER[slot], 2));
        c.setItem(26, new ItemStack(Items.OAK_LOG, 3));
        // Both hands taken, as at a mine: a pickaxe and torches leave nothing to hold a stack in.
        c.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        c.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.TORCH, 8));
        var plan = CompanionPlanRunner.run(List.of(
                        new PlannedAction("place_block", 0, 1, "minecraft:chest"),
                        new PlannedAction("store_items", 0, 1, "resources")),
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-marker-full-bag", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone() && !plan.isCompletedExceptionally(), "The plan is still running: " + c.getLastTaskResult());
            var outcome = plan.join();
            String report = outcome.report();
            h.assertTrue(report.contains("План остановлен: Поставил 0 из 1."), "The failed step is not named: " + report);
            h.assertTrue(report.contains("нет места") && report.toLowerCase(java.util.Locale.ROOT).contains("planks"),
                    "What is missing is not named: " + report);
            h.assertTrue(!report.contains("цепочки крафта"), "The craft chain is named for a chest: " + report);
            h.assertTrue(outcome.skipped().size() == 1 && "store_items".equals(outcome.skipped().getFirst().type()),
                    "The rest of the plan is not listed as not done: " + outcome.skipped());
            h.assertTrue(report.contains("Не выполнено: сложить вещи в сундук"), "The skipped step is not said: " + report);
            h.assertTrue(!report.contains("Сложил"), "A failed plan claimed the storing: " + report);
            h.assertTrue(!c.didLastTaskSucceed(), "The failed step claimed success: " + c.getLastTaskResult());
            h.assertTrue(countIn(bag(c), Items.OAK_LOG) == 3, "The materials were spent: " + c.getLastTaskResult());
            h.assertTrue(blockNear(h, Blocks.CHEST, c.blockPosition(), 5) == null, "A chest was placed after all: " + c.getLastTaskResult());
        });
    }

    /** Two half stacks of planks take two cells: merging them frees one, and the chest is made out of what they hold. */
    @GameTest(template = "empty")
    public static void mergingTwoPlanksStacksFreesTheCellTheChestNeeds(GameTestHelper h) {
        List<ItemStack> bag = fullBag(new ItemStack(Items.OAK_PLANKS, 20), new ItemStack(Items.OAK_PLANKS, 20));
        h.assertTrue(freeCells(bag) == 0 && planksStacks(bag) == 2, "The bag is not full of two half stacks: " + freeCells(bag));
        var plan = CompanionCrafting.plan(h.getLevel(), bag, "minecraft:chest", 1, false);
        h.assertTrue(plan.success(), "The chest was refused: " + plan.message());
        h.assertTrue(countIn(plan.inventory(), Items.CHEST) == 1, "No chest made: " + plan.message());
        h.assertTrue(planksStacks(plan.inventory()) == 1, "The two half stacks were not merged: " + plan.message());
        h.assertTrue(countIn(plan.inventory(), Items.OAK_PLANKS) == 28, "The planks do not add up: " + countIn(plan.inventory(), Items.OAK_PLANKS));
        h.assertTrue(plan.placeTable(), "The bench he had to make first is not set down: " + plan.message());
        h.assertTrue(plan.message().contains("Скрафтил 1 «Chest»"), "No plain answer: " + plan.message());
        h.succeed();
    }

    /** A chest already in the bag is set down as it is: with every cell taken and no wood, nothing is crafted. */
    @GameTest(template = "large_search", timeoutTicks = 600)
    public static void aChestInTheBagIsSetDownInsteadOfBeingCrafted(GameTestHelper h) {
        arena(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.CHEST, 1));
        for (int slot = 1; slot < 27; slot++) c.setItem(slot, new ItemStack(FILLER[slot - 1], 2));
        h.assertTrue(CompanionEvents.executeAction(player(h, c), "place_block", "minecraft:chest", 1).success(), "Order not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() && c.getLastTaskResult().contains("Поставил minecraft:chest: 1"), "Not placed: " + c.getLastTaskResult());
            h.assertTrue(!c.getLastTaskResult().contains("Скрафтил"), "The chest was crafted though he carried one: " + c.getLastTaskResult());
            h.assertTrue(countIn(bag(c), Items.CHEST) == 0, "The chest is still in the bag: " + c.getLastTaskResult());
            h.assertTrue(blockNear(h, Blocks.CHEST, c.blockPosition(), 6) != null, "No chest in the world: " + c.getLastTaskResult());
        });
    }

    /** A full bag and no wood at all: the answer names the logs, not «цепочку крафта», and it is a shortage. */
    @GameTest(template = "empty")
    public static void aChestShortOfWoodNamesTheLogsInsteadOfTheCraftChain(GameTestHelper h) {
        List<ItemStack> bag = fullBag();
        h.assertTrue(countIn(bag, Items.OAK_LOG) == 0 && freeCells(bag) == 0, "The bag is not full and woodless");
        var plan = CompanionCrafting.plan(h.getLevel(), bag, "minecraft:chest", 1, false);
        h.assertTrue(!plan.success() && plan.shortage(), "The shortage was not a shortage: " + plan.message());
        h.assertTrue(plan.message().contains("«Chest»") && plan.message().contains("не хватает"), "What is missing is not named: " + plan.message());
        h.assertTrue(plan.message().toLowerCase(java.util.Locale.ROOT).contains("log"), "The logs are not named: " + plan.message());
        h.assertTrue(!plan.message().contains("цепочки крафта"), "The craft chain is named: " + plan.message());
        h.succeed();
    }

    // ---- fixtures for the bag ----


    /** A bag with every cell taken: the extras first, then filler things that are not wood, planks or a chest. */
    private static List<ItemStack> fullBag(ItemStack... extras) {
        List<ItemStack> bag = new java.util.ArrayList<>();
        for (ItemStack extra : extras) bag.add(extra.copy());
        for (int at = 0; bag.size() < 27; at++) bag.add(new ItemStack(FILLER[at], 2));
        return bag;
    }

    private static List<ItemStack> bag(CompanionEntity c) {
        List<ItemStack> stacks = new java.util.ArrayList<>();
        for (int slot = 0; slot < c.getContainerSize(); slot++) stacks.add(c.getItem(slot).copy());
        return stacks;
    }

    private static List<ItemStack> contents(Container container) {
        List<ItemStack> stacks = new java.util.ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) stacks.add(container.getItem(slot));
        return stacks;
    }

    private static String summary(Container container) {
        return contents(container).stream().filter(stack -> !stack.isEmpty())
                .map(stack -> stack.getCount() + " " + stack.getHoverName().getString()).toList().toString();
    }

    private static int countIn(List<ItemStack> stacks, Item item) {
        return stacks.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static int freeCells(List<ItemStack> stacks) {
        return (int) stacks.stream().filter(ItemStack::isEmpty).count();
    }

    private static long planksStacks(List<ItemStack> stacks) {
        return stacks.stream().filter(stack -> stack.is(Items.OAK_PLANKS)).count();
    }

    /** The first block of this kind within a box around an absolute point, or null. */
    private static BlockPos blockNear(GameTestHelper h, Block block, BlockPos center, int radius) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -2, -radius), center.offset(radius, 2, radius)))
            if (h.getLevel().hasChunkAt(pos) && h.getLevel().getBlockState(pos).is(block)) return pos.immutable();
        return null;
    }

    // ---- fixtures ----

    private static void arena(GameTestHelper h) {
        for (int x = 0; x < 24; x++) for (int z = 0; z < 24; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
    }

    private static CompanionEntity spawn(GameTestHelper h) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 8, 1, 8);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(5, 1, 8));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        companion.setFollowing(false);
        return companion;
    }

    private static ServerPlayer player(GameTestHelper h, CompanionEntity companion) {
        return h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }
}

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
     * The owner's own bag from his screenshot: all 27 cells taken, four pickaxes and two water buckets among the things,
     * and two pairs of half stacks (12+12 diamonds, 3+9 gold) that a player merges before crafting. «Поставь сундук
     * здесь» must merge them, make the bench first, set down the chest and keep every item: only the wood the chest and
     * its bench are made of is spent, and no stack is dropped or invented. He may be holding a pickaxe, which must not
     * stop the free hand from taking a stack.
     */
    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void theOwnersFullBagPlacesTheChestAndKeepsEveryItem(GameTestHelper h) {
        arena(h);
        CompanionEntity c = spawn(h);
        ItemStack[] carried = {
                new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.DIAMOND_PICKAXE),
                new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.WOODEN_PICKAXE),
                new ItemStack(Items.ROTTEN_FLESH, 3), new ItemStack(Items.WATER_BUCKET),
                new ItemStack(Items.COBBLESTONE, 45), new ItemStack(Items.DIAMOND, 12),
                new ItemStack(Items.RED_MUSHROOM, 6), new ItemStack(Items.DIAMOND, 12),
                new ItemStack(Items.PORKCHOP, 8), new ItemStack(Items.OAK_LOG, 4),
                new ItemStack(Items.ARROW, 5), new ItemStack(Items.STRING, 4),
                new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.GOLD_INGOT, 3),
                new ItemStack(Items.DIRT, 2), new ItemStack(Items.FEATHER, 1),
                new ItemStack(Items.LAPIS_LAZULI, 3), new ItemStack(Items.GOLD_INGOT, 9),
                new ItemStack(Items.BREAD, 64), new ItemStack(Items.REDSTONE, 40),
                new ItemStack(Items.BROWN_MUSHROOM, 1), new ItemStack(Items.BONE, 12),
                new ItemStack(Items.FLINT, 11), new ItemStack(Items.BEEF, 64),
                new ItemStack(Items.SAND, 1)};
        for (int slot = 0; slot < carried.length; slot++) c.setItem(slot, carried[slot].copy());
        c.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_PICKAXE));
        List<ItemStack> before = bag(c);
        h.assertTrue(freeCells(before) == 0, "The bag is not full: " + freeCells(before));
        var direct = CompanionCrafting.plan(h.getLevel(), before, "minecraft:chest", 1, false);
        h.assertTrue(direct.success(), "The plan itself refused the owner's bag: " + direct.message());
        h.assertTrue(countIn(direct.inventory(), Items.DIAMOND) == 24 && countIn(direct.inventory(), Items.GOLD_INGOT) == 12,
                "The half stacks were not merged: " + direct.message());
        var plan = CompanionPlanRunner.run(List.of(new PlannedAction("place_block", 0, 1, "minecraft:chest")),
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-owners-full-bag", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone() && !plan.isCompletedExceptionally(), "The plan is still running: " + c.getLastTaskResult());
            var outcome = plan.join();
            h.assertTrue(outcome.report().contains("Поставил minecraft:chest: 1"), "The chest was not placed: " + outcome.report());
            BlockPos placed = blockNear(h, Blocks.CHEST, c.blockPosition(), 6);
            h.assertTrue(placed != null, "No chest in the world: " + outcome.report());
            List<ItemStack> after = bag(c);
            // Nothing dropped: a stack that found no cell would lie on the ground.
            h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                            new net.minecraft.world.phys.AABB(c.blockPosition()).inflate(8)).isEmpty(),
                    "A stack was dropped: " + outcome.report());
            for (ItemStack stack : before) {
                Item item = stack.getItem();
                if (item == Items.OAK_LOG) continue; // the chest and its bench are made of three of the four logs
                h.assertTrue(countIn(after, item) == countIn(before, item),
                        "The bag lost or gained " + item + ": " + countIn(before, item) + " before, " + countIn(after, item) + " after: " + outcome.report());
            }
            h.assertTrue(countIn(after, Items.OAK_LOG) == 1, "The logs do not add up: " + countIn(after, Items.OAK_LOG));
            h.assertTrue(countIn(after, Items.CHEST) == 0, "The chest is still in the bag: " + outcome.report());
            h.assertTrue(c.getMainHandItem().is(Items.DIAMOND_PICKAXE), "The held pickaxe was taken: " + c.getMainHandItem());
        });
    }

    /**
     * The bag the owner really had when the order failed (his game log, 21:21): all 27 cells taken, both hands busy
     * with a pickaxe and a shield, four oak logs — and no pair that merging could put into one cell (the two cooked
     * porkchops are 64 and 11: the full stack takes no more). The chest still has to be made and set down, and every
     * stack must stay: the chain's planks live in the crafting grid, the chest leaves the grid into the world.
     */
    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void theOwnersRealFullBagWithBothHandsBusyStillPlacesTheChest(GameTestHelper h) {
        arena(h);
        CompanionEntity c = spawn(h);
        ItemStack[] carried = {
                new ItemStack(Items.DIAMOND_HOE), new ItemStack(Items.DIAMOND_HOE),
                new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.WOODEN_PICKAXE),
                new ItemStack(Items.INK_SAC, 3), new ItemStack(Items.WATER_BUCKET),
                new ItemStack(Items.COBBLESTONE, 45), new ItemStack(Items.ZOMBIE_SPAWN_EGG, 12),
                new ItemStack(Items.CHICKEN_SPAWN_EGG, 6), new ItemStack(Items.DIAMOND, 12),
                new ItemStack(Items.CHICKEN, 8), new ItemStack(Items.OAK_LOG, 4),
                new ItemStack(Items.ARROW, 5), new ItemStack(Items.STRING, 4),
                new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.RAW_GOLD, 3),
                new ItemStack(Items.DIRT, 2), new ItemStack(Items.FEATHER, 1),
                new ItemStack(Items.LAPIS_LAZULI, 3), new ItemStack(Items.GOLD_INGOT, 9),
                new ItemStack(Items.COOKED_PORKCHOP, 64), new ItemStack(Items.REDSTONE, 40),
                new ItemStack(Items.SPRUCE_BOAT), new ItemStack(Items.BONE, 12),
                new ItemStack(Items.COOKED_PORKCHOP, 11), new ItemStack(Items.ROTTEN_FLESH, 34),
                new ItemStack(Items.SAND, 1)};
        for (int slot = 0; slot < carried.length; slot++) c.setItem(slot, carried[slot].copy());
        // As in the log: a pickaxe in the main hand and a shield in the offhand leave no free hand to hold a stack.
        c.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        c.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        List<ItemStack> before = bag(c);
        h.assertTrue(freeCells(before) == 0, "The bag is not full: " + freeCells(before));
        h.assertTrue(!c.holdOneStackInHand(), "A hand was free after all: " + c.heldStackHands());
        var plan = CompanionPlanRunner.run(List.of(new PlannedAction("place_block", 0, 1, "minecraft:chest")),
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-owners-real-full-bag", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone() && !plan.isCompletedExceptionally(), "The plan is still running: " + c.getLastTaskResult());
            var outcome = plan.join();
            h.assertTrue(outcome.report().contains("Поставил minecraft:chest: 1"), "The chest was not placed: " + outcome.report());
            h.assertTrue(blockNear(h, Blocks.CHEST, c.blockPosition(), 6) != null, "No chest in the world: " + outcome.report());
            List<ItemStack> after = bag(c);
            h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                            new net.minecraft.world.phys.AABB(c.blockPosition()).inflate(8)).isEmpty(),
                    "A stack was dropped: " + outcome.report());
            for (ItemStack stack : before) {
                Item item = stack.getItem();
                if (item == Items.OAK_LOG) continue; // the chest and its bench are made of three of the four logs
                h.assertTrue(countIn(after, item) == countIn(before, item),
                        "The bag lost or gained " + item + ": " + countIn(before, item) + " before, " + countIn(after, item) + " after: " + outcome.report());
            }
            h.assertTrue(countIn(after, Items.OAK_LOG) == 1, "The logs do not add up: " + countIn(after, Items.OAK_LOG));
            h.assertTrue(countIn(after, Items.CHEST) == 0, "The chest is still in the bag: " + outcome.report());
            h.assertTrue(c.getMainHandItem().is(Items.IRON_PICKAXE) && c.getOffhandItem().is(Items.SHIELD),
                    "The held pickaxe or shield moved: " + c.getMainHandItem() + " " + c.getOffhandItem());
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
     * A full bag, the wood for a chest, no cell and both hands taken: the planks live in the 3×3 grid, the chest leaves
     * it into the world, and the storing goes on. Before the fix this bag refused for space, though the chain fitted
     * into the grid; every filler stack must end up in the bag or in the chest, and the held things stay in the hands.
     */
    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void aFullBagWithBothHandsBusyMakesTheChestInTheGridAndGoesOnStoring(GameTestHelper h) {
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
            h.assertTrue(report.contains("Поставил minecraft:chest: 1"), "The chest was not placed: " + report);
            h.assertTrue(outcome.skipped().isEmpty(), "A step was skipped: " + report);
            h.assertTrue(report.contains("Сложил"), "The storing did not go on: " + report);
            BlockPos placed = blockNear(h, Blocks.CHEST, c.blockPosition(), 6);
            h.assertTrue(placed != null, "No chest in the world: " + report);
            h.assertTrue(countIn(bag(c), Items.OAK_LOG) == 0 && countIn(bag(c), Items.CHEST) == 0,
                    "The logs or the chest are still in the bag: " + report);
            var chest = (Container) h.getLevel().getBlockEntity(placed);
            // Every filler stack is in the bag or in the chest: the grid left nothing behind and dropped nothing.
            for (int index = 0; index < 26; index++) {
                Item filler = FILLER[index];
                int kept = countIn(bag(c), filler) + countIn(contents(chest), filler);
                h.assertTrue(kept == 2, "The bag lost or gained " + filler + ": " + kept + " of 2: " + report);
            }
            h.assertTrue(c.getMainHandItem().is(Items.STONE_PICKAXE) && c.getOffhandItem().is(Items.TORCH) && c.getOffhandItem().getCount() == 8,
                    "The held things moved: " + c.getMainHandItem() + " " + c.getOffhandItem());
        });
    }

    /**
     * A bag with one cell free and both hands busy: the retry takes nothing (the cell was already there), so the same
     * plan runs again and refuses for the second cell a chain needs at once — planks, then sticks. The grid is the next
     * cell to try, and the torches are made there and stay as the work's block: before the fix the work stopped at
     * «нужна одна свободная ячейка».
     */
    @GameTest(template = "empty")
    public static void aCellInTheBagStillGetsTheGridTriedForAChainNeedingTwo(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        // One coal and two logs, 24 stacks that are none of the torch chain's materials: 26 cells taken, one free. The
        // second log stays in its cell, so the sticks the torch needs have no cell of their own there.
        Item[] plain = {Items.DIRT, Items.GRAVEL, Items.SAND, Items.FLINT, Items.ANDESITE, Items.DIORITE, Items.GRANITE,
                Items.TUFF, Items.CALCITE, Items.DEEPSLATE, Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT,
                Items.REDSTONE, Items.LAPIS_LAZULI, Items.STRING, Items.FEATHER, Items.LEATHER, Items.BONE,
                Items.GUNPOWDER, Items.BRICK, Items.NETHER_BRICK, Items.AMETHYST_SHARD, Items.SLIME_BALL};
        c.setItem(0, new ItemStack(Items.COAL, 1));
        c.setItem(1, new ItemStack(Items.OAK_LOG, 2));
        for (int at = 0; at < plain.length; at++) c.setItem(at + 2, new ItemStack(plain[at], 2));
        c.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        c.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        h.assertTrue(freeCells(bag(c)) == 1, "The bag is not one cell short: " + freeCells(bag(c)));
        h.assertTrue(c.holdOneStackInHand() && c.heldStackHands().isEmpty(),
                "The free cell was not seen, or a stack was taken for it: " + c.heldStackHands());
        var crafted = c.craftHoldingIfTheBagIsFull("minecraft:torch", 1);
        h.assertTrue(crafted.success(), "The torch was refused although the grid can hold the chain: " + crafted.message());
        h.assertTrue(countIn(bag(c), Items.COAL) == 0, "The coal is still in the bag: " + crafted.message());
        // The torches had no bag cell either: they are the work's, in the grid, not on the ground and not lost.
        h.assertTrue(c.gridBlockInGrid().is(Items.TORCH) && c.gridBlockInGrid().getCount() == 4,
                "The torches are not the grid block: " + c.gridBlockInGrid());
        h.assertTrue(countIn(bag(c), Items.TORCH) == 0, "The torches are in the bag after all: " + c.getStoredItemsSummary());
        h.assertTrue(c.getStoredItemsForAi().contains("minecraft:torch (в сетке крафта)"),
                "The model does not see the torches in the grid: " + c.getStoredItemsForAi());
        h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        new net.minecraft.world.phys.AABB(c.blockPosition()).inflate(8)).isEmpty(),
                "A stack was dropped: " + c.getStoredItemsSummary());
        h.succeed();
    }

    /**
     * A block a grid craft leaves in the 3×3 grid is the work's, not the bag's: the save carries it (WorkGridBlock) and
     * a reload puts it back into the bag once a cell is free — never lost with the work, never counted twice. The model
     * sees it as carried meanwhile, or it would ask for a chest it already has.
     */
    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void aGridBlockComesBackAfterAReload(GameTestHelper h) {
        arena(h);
        CompanionEntity c = spawn(h);
        fullBagWithBothHandsBusyAndLogs(c, 4);
        var crafted = c.craftHoldingIfTheBagIsFull("minecraft:chest", 1);
        h.assertTrue(crafted.success(), "The chest was refused: " + crafted.message());
        h.assertTrue(c.gridBlockInGrid().is(Items.CHEST), "The chest is not in the grid: " + c.gridBlockInGrid());
        h.assertTrue(countIn(bag(c), Items.CHEST) == 0, "The chest is in the bag, not the grid: " + c.getStoredItemsSummary());
        h.assertTrue(c.getStoredItemsForAi().contains("minecraft:chest (в сетке крафта)"),
                "The model does not see the grid block: " + c.getStoredItemsForAi());
        // A cell frees before the reload, as it does when he eats or sets something down.
        c.setItem(0, ItemStack.EMPTY);
        var saved = new CompoundTag();
        c.addAdditionalSaveData(saved);
        h.assertTrue(saved.contains("WorkGridBlock"), "The save does not carry the grid block: " + saved);
        var loaded = AICompanionMod.COMPANION.get().create(h.getLevel());
        loaded.readAdditionalSaveData(saved);
        h.assertTrue(countIn(bag(loaded), Items.CHEST) == 1,
                "The grid block did not come back into the bag: " + countIn(bag(loaded), Items.CHEST));
        h.assertTrue(loaded.gridBlockInGrid().isEmpty(), "The block stayed in the grid after the reload: " + loaded.gridBlockInGrid());
        h.succeed();
    }

    /**
     * «Стой» ends the work: the block made in the grid and not yet set down goes back into the bag with the cell it
     * finds, instead of disappearing with the work's state.
     */
    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void aCancelledWorkPutsTheGridBlockBackIntoTheBag(GameTestHelper h) {
        arena(h);
        CompanionEntity c = spawn(h);
        fullBagWithBothHandsBusyAndLogs(c, 4);
        var crafted = c.craftHoldingIfTheBagIsFull("minecraft:chest", 1);
        h.assertTrue(crafted.success() && c.gridBlockInGrid().is(Items.CHEST), "No chest in the grid: " + crafted.message());
        c.setItem(0, ItemStack.EMPTY);
        h.assertTrue(CompanionEvents.executeAction(player(h, c), "wait", "", 1).success(), "The «стой» order was not taken");
        h.assertTrue(c.gridBlockInGrid().isEmpty(), "The block stayed in the grid after the cancel: " + c.gridBlockInGrid());
        h.assertTrue(countIn(bag(c), Items.CHEST) == 1, "The cancelled work lost the grid block: " + c.getStoredItemsSummary());
        h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        new net.minecraft.world.phys.AABB(c.blockPosition()).inflate(8)).isEmpty(),
                "A stack was dropped on the cancel: " + c.getStoredItemsSummary());
        h.succeed();
    }

    /**
     * Nowhere to set the block down: the placement fails and the grid block stays the work's, whole — not spent by the
     * attempt, not dropped for want of a cell. The pocket is two cells: his own and the one the bench stands in, so the
     * craft needs no placement of its own and only the chest is left without a cell.
     */
    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void aGridBlockIsKeptWhenThereIsNowhereToSetItDown(GameTestHelper h) {
        arena(h);
        CompanionEntity c = spawn(h);
        BlockPos away = h.absolutePos(new BlockPos(2, 1, 2));
        player(h, c).teleportTo(away.getCenter().x, away.getY(), away.getCenter().z);
        pocket(h);
        fullBagWithBothHandsBusyAndLogs(c, 4);
        var crafted = c.craftHoldingIfTheBagIsFull("minecraft:chest", 1);
        h.assertTrue(crafted.success() && c.gridBlockInGrid().is(Items.CHEST), "No chest in the grid: " + crafted.message());
        var placed = c.placeBlockResult("minecraft:chest");
        h.assertTrue(!placed.success() && placed.message().contains("безопасное место"),
                "The chest was set down after all: " + placed.message());
        h.assertTrue(c.gridBlockInGrid().is(Items.CHEST) && c.gridBlockInGrid().getCount() == 1,
                "The failed placement spent the grid block: " + c.gridBlockInGrid());
        h.assertTrue(countIn(bag(c), Items.CHEST) == 0, "The chest is in the bag after all: " + c.getStoredItemsSummary());
        h.succeed();
    }

    /**
     * Two grid crafts before the first block is set down: the second must not overwrite the first in silence — the first
     * chest goes into the bag, or lies at his feet, but is never lost.
     */
    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void aSecondGridCraftKeepsTheFirstBlockInsteadOfOverwritingIt(GameTestHelper h) {
        arena(h);
        CompanionEntity c = spawn(h);
        // Eight logs in one cell: the first chest takes three and leaves five, so the chest stays in the grid.
        fullBagWithBothHandsBusyAndLogs(c, 8);
        var first = c.craftHoldingIfTheBagIsFull("minecraft:chest", 1);
        h.assertTrue(first.success() && c.gridBlockInGrid().is(Items.CHEST), "No chest in the grid: " + first.message());
        var second = c.craftHoldingIfTheBagIsFull("minecraft:chest", 1);
        h.assertTrue(second.success(), "The second chest was refused: " + second.message());
        h.assertTrue(c.gridBlockInGrid().is(Items.CHEST) && c.gridBlockInGrid().getCount() == 1,
                "The second chest is not the grid block: " + c.gridBlockInGrid());
        int onTheGround = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        new net.minecraft.world.phys.AABB(c.blockPosition()).inflate(8)).stream()
                .filter(entity -> entity.getItem().is(Items.CHEST)).mapToInt(entity -> entity.getItem().getCount()).sum();
        h.assertTrue(countIn(bag(c), Items.CHEST) + onTheGround == 1,
                "The first grid block was lost: " + countIn(bag(c), Items.CHEST) + " in the bag, " + onTheGround
                        + " on the ground: " + c.getStoredItemsSummary());
        h.succeed();
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

    /**
     * A full bag whose only free cell comes from merging two half stacks of one thing: that cell is enough for the whole
     * chain (planks, the bench, the planks for the chest, the chest), and the merged stack keeps every diamond.
     */
    @GameTest(template = "empty")
    public static void aCellFreedOnlyByMergingTwoPartialStacksIsEnoughForTheChest(GameTestHelper h) {
        List<ItemStack> bag = new java.util.ArrayList<>();
        bag.add(new ItemStack(Items.DIAMOND, 12));
        bag.add(new ItemStack(Items.DIAMOND, 12));
        bag.add(new ItemStack(Items.OAK_LOG, 4));
        for (int at = 0; bag.size() < 27; at++) bag.add(new ItemStack(FILLER[at], 2));
        h.assertTrue(freeCells(bag) == 0 && countIn(bag, Items.DIAMOND) == 24, "The bag is not full of two half stacks: " + freeCells(bag));
        var plan = CompanionCrafting.plan(h.getLevel(), bag, "minecraft:chest", 1, false);
        h.assertTrue(plan.success(), "The chest was refused although merging frees a cell: " + plan.message());
        h.assertTrue(countIn(plan.inventory(), Items.DIAMOND) == 24, "The diamonds do not add up: " + countIn(plan.inventory(), Items.DIAMOND));
        h.assertTrue(plan.inventory().stream().filter(stack -> stack.is(Items.DIAMOND)).count() == 1, "The two half stacks were not merged: " + plan.message());
        h.assertTrue(countIn(plan.inventory(), Items.CHEST) == 1 && countIn(plan.inventory(), Items.OAK_LOG) == 1,
                "No chest made, or the logs do not add up: " + plan.message());
        h.assertTrue(plan.placeTable(), "The bench he had to make first is not set down: " + plan.message());
        h.succeed();
    }

    /**
     * A full bag with no pair to merge, wood for the chest, and no cell to spare: the refusal names the item that had no
     * cell («Oak Planks») and the cell, claims no shortage, and the bag comes back untouched.
     */
    @GameTest(template = "empty")
    public static void aFullBagWithNoMergeablePairStillNamesTheItemItHasNoCellFor(GameTestHelper h) {
        List<ItemStack> bag = new java.util.ArrayList<>();
        bag.add(new ItemStack(Items.OAK_LOG, 3));
        for (int at = 0; bag.size() < 27; at++) bag.add(new ItemStack(FILLER[at], 2));
        h.assertTrue(freeCells(bag) == 0, "The bag is not full: " + freeCells(bag));
        var plan = CompanionCrafting.plan(h.getLevel(), bag, "minecraft:chest", 1, false);
        h.assertTrue(!plan.success() && !plan.shortage(), "A space refusal claimed a shortage: " + plan.message());
        h.assertTrue(plan.message().contains("нет места") && plan.message().contains("одна свободная ячейка"),
                "The missing cell is not named: " + plan.message());
        h.assertTrue(plan.message().toLowerCase(java.util.Locale.ROOT).contains("planks"), "The item without a cell is not named: " + plan.message());
        h.assertTrue(plan.message().contains("«Chest»") && plan.message().contains("верстак"), "The answer does not say what is being made: " + plan.message());
        h.assertTrue(countIn(plan.inventory(), Items.OAK_LOG) == 3 && freeCells(plan.inventory()) == 0,
                "The refused plan changed the bag: " + plan.message());
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

    /** A full bag with the wood for a chest in one cell and both hands busy, as the owner's bag was at the mine. */
    private static void fullBagWithBothHandsBusyAndLogs(CompanionEntity c, int logs) {
        for (int slot = 0; slot < 26; slot++) c.setItem(slot, new ItemStack(FILLER[slot], 2));
        c.setItem(26, new ItemStack(Items.OAK_LOG, logs));
        c.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        c.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
    }

    /** Two cells and nothing else: his own with room for his head, and the one beside it, where a bench stands. */
    private static void pocket(GameTestHelper h) {
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = -1; dy <= 2; dy++) {
            boolean hisCell = dx == 0 && dz == 0 && (dy == 0 || dy == 1);
            boolean benchCell = dx == 1 && dz == 0 && dy == 0;
            if (hisCell) continue;
            h.setBlock(new BlockPos(8 + dx, 1 + dy, 8 + dz), benchCell ? Blocks.CRAFTING_TABLE : Blocks.BEDROCK);
        }
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

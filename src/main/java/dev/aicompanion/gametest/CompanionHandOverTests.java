package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionPlanRequirements;
import dev.aicompanion.client.CompanionReply;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Things made for the player reach the player physically, and a "make" request is not padded with unasked work. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionHandOverTests {
    private static PlannedAction craft(String item) { return new PlannedAction("craft_item", 0, 1, item, "auto", "near_player"); }

    /** Game log 22.10: the model's exact plan for «сделай себе и мне каменное оружие и инструменты». */
    @GameTest(template = "empty")
    public static void forBothOfUsMakesTwoHandsOneOverAndDropsUnaskedWork(GameTestHelper h) {
        var model = new CompanionReply("Сейчас сделаю.", List.of(craft("minecraft:stone_sword"), craft("minecraft:stone_pickaxe"), craft("minecraft:stone_axe"),
                new PlannedAction("gather_stone", 0, 64), new PlannedAction("place_block", 0, 1, "minecraft:chest", "auto", "near_player"),
                new PlannedAction("store_items", 0, 1, "stone", "auto", "near_player")));
        var plan = CompanionPlanRequirements.apply("Привет, сделай себе и мне каменное оружие и инструменты", model).actions();
        h.assertTrue(plan.stream().noneMatch(step -> step.type().startsWith("gather_") || step.type().equals("store_items") || step.type().equals("place_block")),
                "Unasked dig or chest kept: " + plan);
        for (String item : List.of("minecraft:stone_sword", "minecraft:stone_pickaxe", "minecraft:stone_axe")) {
            h.assertTrue(plan.stream().anyMatch(step -> step.type().equals("craft_item") && step.item().equals(item) && step.count() == 2), "Not two of " + item + ": " + plan);
            h.assertTrue(plan.stream().anyMatch(step -> step.type().equals("give_items") && step.item().equals(item) && step.count() == 1), "Not handed over: " + item);
        }
        var forMe = CompanionPlanRequirements.apply("Сделай мне каменную кирку", new CompanionReply("Ок", List.of(craft("minecraft:stone_pickaxe")))).actions();
        h.assertTrue(forMe.size() == 2 && forMe.get(0).count() == 1 && forMe.get(1).type().equals("give_items"), "For me not handed over: " + forMe);
        var asked = new CompanionReply("Ок", List.of(new PlannedAction("gather_stone", 0, 30), craft("minecraft:stone_pickaxe"),
                new PlannedAction("store_items", 0, 1, "stone", "auto", "near_player")));
        h.assertTrue(CompanionPlanRequirements.apply("Добудь 30 камня, сделай кирку и сложи камень в сундук", asked).actions().equals(asked.actions()), "Asked work removed");
        // The second game request was a reproach with the same intent; the model again made one set and stored all of it.
        var reproach = new CompanionReply("Сейчас сделаю.", List.of(craft("minecraft:stone_sword"), craft("minecraft:stone_pickaxe"),
                new PlannedAction("place_block", 0, 1, "minecraft:chest", "auto", "near_player"),
                new PlannedAction("store_items", 0, 1, "minecraft:stone_sword", "auto", "near_player")));
        var fixed = CompanionPlanRequirements.apply("У тебя куча булыжника, почему ты не сделал себе и мне инструменты?", reproach).actions();
        h.assertTrue(fixed.stream().noneMatch(step -> step.type().equals("store_items") || step.type().equals("place_block"))
                && fixed.stream().filter(step -> step.type().equals("craft_item")).allMatch(step -> step.count() == 2)
                && fixed.stream().filter(step -> step.type().equals("give_items")).count() == 2, "Reproach not read as the same request: " + fixed);
        h.assertTrue(CompanionPlanRequirements.apply("Не делай мне кирку", new CompanionReply("Ок", List.of(craft("minecraft:stone_pickaxe")))).actions().size() == 1, "Negation rewritten");
        h.succeed();
    }

    /** Handing over is a real toss to the player's feet; Oleg never picks the gift back up. */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void handOverIsATossNotATeleport(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 10, 1, 10);
        var player = h.makeMockServerPlayerInLevel();
        var at = h.absoluteVec(new net.minecraft.world.phys.Vec3(20.5, 1, 20.5));
        player.teleportTo(at.x, at.y, at.z);
        companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        companion.setItem(0, new ItemStack(Items.STONE_PICKAXE)); companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        h.assertTrue(CompanionEvents.executeAction(owner, "give_items", "minecraft:stone_pickaxe", 1).success(), "Hand-over not accepted");
        h.assertTrue(!owner.getInventory().contains(new ItemStack(Items.STONE_PICKAXE)), "Teleported into the player's inventory");
        long[] doneAt = {-1};
        h.onEachTick(() -> { if (doneAt[0] < 0 && !companion.isWorking()) doneAt[0] = h.getTick(); });
        h.succeedWhen(() -> {
            h.assertTrue(doneAt[0] >= 0 && h.getTick() - doneAt[0] > 60, "Wait for the toss and a while after it");
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.STONE_PICKAXE) == 0, companion.getLastTaskResult());
            h.assertTrue(companion.getMainHandItem().is(Items.STONE_SWORD), "Gave away the tool in hand instead of the bag one");
            boolean onGround = !h.getLevel().getEntitiesOfClass(ItemEntity.class, owner.getBoundingBox().inflate(4),
                    item -> item.getItem().is(Items.STONE_PICKAXE)).isEmpty();
            h.assertTrue(onGround || owner.getInventory().contains(new ItemStack(Items.STONE_PICKAXE)), "Gift neither on the ground near the player nor picked up");
        });
    }

    /**
     * Game 27.10: two helmets made in a mine, the player 54 blocks off on the surface; the single direct path found no way and the
     * hand-over gave up after 40 s. Here a long wall stands between them, the way round is longer than one search of the pathfinder.
     */
    @GameTest(template = "large_search", timeoutTicks = 3000)
    public static void handOverFromFarBehindAWallGoesInLegs(GameTestHelper h) {
        for (int x = 0; x < 128; x++) for (int z = 0; z < 128; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
            if (x == 0 || x == 127 || z == 0 || z == 127 || (z == 90 && x >= 30)) for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
        }
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 64, 1, 64);
        var player = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(62, 1, 118));
        player.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        companion.setSearchRadius(60);
        companion.setItem(0, new ItemStack(Items.IRON_HELMET, 2));
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        h.assertTrue(CompanionEvents.executeAction(owner, "give_items", "minecraft:iron_helmet", 2).success(), "Hand-over not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!companion.isWorking() && companion.didLastTaskSucceed(), companion.getLastTaskResult());
            boolean onGround = h.getLevel().getEntitiesOfClass(ItemEntity.class, owner.getBoundingBox().inflate(6),
                    item -> item.getItem().is(Items.IRON_HELMET)).stream().mapToInt(item -> item.getItem().getCount()).sum() == 2;
            h.assertTrue(onGround || owner.getInventory().countItem(Items.IRON_HELMET) == 2, "Helmets not at the player");
            h.assertTrue(count(companion, Items.IRON_HELMET) == 0, "Kept the helmets");
        });
    }

    /**
     * Game 28.10, the same run after the walk in legs: from the mine there was no leg at all and the hand-over gave up in a second,
     * before the self-rescue could cut the steps up that it cut there the day before. Here he is shut in stone below the player.
     */
    @GameTest(template = "large_search", timeoutTicks = 3000)
    public static void handOverFromAMineClimbsOutAndWalks(GameTestHelper h) {
        for (int x = 0; x < 128; x++) for (int z = 0; z < 128; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
            if (x >= 54 && x <= 74 && z >= 54 && z <= 122) for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        }
        h.setBlock(new BlockPos(64, 1, 64), Blocks.AIR);
        h.setBlock(new BlockPos(64, 2, 64), Blocks.AIR);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 64, 1, 64);
        var player = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(64, 4, 118));
        player.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        companion.setSearchRadius(60);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        companion.setItem(0, new ItemStack(Items.IRON_HELMET, 2));
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        h.assertTrue(CompanionEvents.executeAction(owner, "give_items", "minecraft:iron_helmet", 2).success(), "Hand-over not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!companion.isWorking() && companion.didLastTaskSucceed(), companion.getLastTaskResult());
            boolean onGround = h.getLevel().getEntitiesOfClass(ItemEntity.class, owner.getBoundingBox().inflate(6),
                    item -> item.getItem().is(Items.IRON_HELMET)).stream().mapToInt(item -> item.getItem().getCount()).sum() == 2;
            h.assertTrue(onGround || owner.getInventory().countItem(Items.IRON_HELMET) == 2, "Helmets not at the player");
        });
    }

    /** Game 27.10: a failed hand-over was heard as «С этим пока не справился»; it must say the things are still with him. */
    @GameTest(template = "empty")
    public static void failedHandOverSaysItemsAreStillWithHim(GameTestHelper h) {
        var steps = List.of(new PlannedAction("give_items", 0, 2, "minecraft:iron_helmet"));
        String said = dev.aicompanion.client.CompanionDialogue.summarize(steps, "План остановлен: Не смог к тебе подойти, вещи остались у меня.");
        h.assertTrue(said.contains("у меня") && said.contains("дошёл") && !said.contains("не справился"), said);
        h.succeed();
    }

    /** Game log 22.10: "put the stone sword in the chest" also put away the sword Oleg uses himself. */
    @GameTest(template = "tall_tree", timeoutTicks = 500)
    public static void storingANamedToolMovesOnlyTheRequestedOne(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        BlockPos chestPos = new BlockPos(18, 1, 16); h.setBlock(chestPos, Blocks.CHEST);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var player = h.makeMockServerPlayerInLevel();
        player.teleportTo(companion.getX() - 3, companion.getY(), companion.getZ() - 3);
        companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        // As in the game: both swords in the bag (the new one and his own).
        companion.setItem(0, new ItemStack(Items.STONE_SWORD)); companion.setItem(1, new ItemStack(Items.STONE_SWORD)); companion.setItem(2, new ItemStack(Items.COBBLESTONE, 10));
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        h.assertTrue(CompanionEvents.executeAction(owner, "store_items", "minecraft:stone_sword", 1).success(), "Store not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!companion.isWorking() && companion.didLastTaskSucceed(), companion.getLastTaskResult());
            var chest = (net.minecraft.world.Container) h.getBlockEntity(chestPos);
            int inChest = 0; for (int slot = 0; slot < chest.getContainerSize(); slot++) if (chest.getItem(slot).is(Items.STONE_SWORD)) inChest++;
            h.assertTrue(inChest == 1 && count(companion, Items.STONE_SWORD) == 1,
                    "Expected one sword stored and his own kept: chest=" + inChest + " " + companion.getStoredItemsSummary());
            h.assertTrue(count(companion, Items.COBBLESTONE) == 10, "Stored other things too");
        });
    }

    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
}

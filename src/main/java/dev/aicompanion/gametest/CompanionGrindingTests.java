package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** «Почини кирку» folds two alike as the crafting grid does; «сними чары» at a grindstone, the experience left at the stone. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionGrindingTests {
    /**
     * Two worn iron pickaxes become one: 50 + 100 left and the game's 5% of 250 on top. The more worn one was in the second hand:
     * the folded one stays in the main hand, the second hand is emptied.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 100)
    public static void twoWornPickaxesAreFoldedIntoOne(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        companion.setItemSlot(EquipmentSlot.MAINHAND, worn(Items.IRON_PICKAXE, 100)); companion.setItemSlot(EquipmentSlot.OFFHAND, worn(Items.IRON_PICKAXE, 50));
        CompanionActionResult result = CompanionEvents.executeAction(owner(h, companion), "repair_item", "pickaxe", 1);
        h.assertTrue(result.success() && result.message().contains("162 из 250"), "Repair: " + result.message());
        h.assertTrue(count(companion, Items.IRON_PICKAXE) == 1 && left(companion, Items.IRON_PICKAXE) == 162, "Pickaxes after: " + companion.getStoredItemsSummary());
        h.assertTrue(companion.getMainHandItem().is(Items.IRON_PICKAXE) && companion.getOffhandItem().isEmpty(), "Hands: " + companion.getMainHandItem() + ", " + companion.getOffhandItem());
        h.succeed();
    }

    /** A whole one folded with a worn one is only the whole one again, and a named one would lose its name: neither is folded. */
    @GameTest(template = "tall_tree", timeoutTicks = 100)
    public static void aWholeOrANamedOneIsNotFolded(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        companion.setItem(0, worn(Items.IRON_PICKAXE, 249)); companion.setItem(1, new ItemStack(Items.IRON_PICKAXE));
        CompanionActionResult whole = CompanionEvents.executeAction(owner(h, companion), "repair_item", "pickaxe", 1);
        h.assertTrue(!whole.success() && count(companion, Items.IRON_PICKAXE) == 2, "A whole one was folded: " + whole.message());
        ItemStack named = worn(Items.IRON_PICKAXE, 50);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Любимая"));
        companion.setItem(1, named);
        CompanionActionResult name = CompanionEvents.executeAction(owner(h, companion), "repair_item", "pickaxe", 1);
        h.assertTrue(!name.success() && name.message().contains("особенная") && count(companion, Items.IRON_PICKAXE) == 2, "A named one was folded: " + name.message());
        h.succeed();
    }

    /** A curse is kept by the grid's repair, so two cursed worn pickaxes are folded, the curse still on. */
    @GameTest(template = "tall_tree", timeoutTicks = 100)
    public static void aCursedPairIsFolded(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        companion.setItem(0, enchanted(h, worn(Items.STONE_PICKAXE, 10), Enchantments.VANISHING_CURSE, 1));
        companion.setItem(1, enchanted(h, worn(Items.STONE_PICKAXE, 20), Enchantments.VANISHING_CURSE, 1));
        CompanionActionResult result = CompanionEvents.executeAction(owner(h, companion), "repair_item", "pickaxe", 1);
        h.assertTrue(result.success() && count(companion, Items.STONE_PICKAXE) == 1 && all(companion, Items.STONE_PICKAXE).getFirst().isEnchanted(), "Repair: " + result.message());
        h.succeed();
    }

    /** Elytra sit in the chest slot too: «сними чары с нагрудника» grinds the chestplate, though the elytra's enchantments are cheaper. */
    @GameTest(template = "tall_tree", timeoutTicks = 200)
    public static void elytraAreNoChestplate(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        h.setBlock(new BlockPos(18, 1, 16), Blocks.GRINDSTONE);
        companion.setItem(0, enchanted(h, new ItemStack(Items.ELYTRA), Enchantments.UNBREAKING, 1));
        companion.setItem(1, enchanted(h, new ItemStack(Items.IRON_CHESTPLATE), Enchantments.PROTECTION, 4));
        // The model sees which of his things are enchanted: it is what «сними чары» is about.
        h.assertTrue(companion.getStoredItemsForAi().contains("minecraft:iron_chestplate (с чарами)"), "For the model: " + companion.getStoredItemsForAi());
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "disenchant_item", "chestplate", 1).success(), "Order refused");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && companion.getItem(0).isEnchanted() && !companion.getItem(1).isEnchanted(),
                    companion.getLastTaskResult() + " | " + companion.getItem(0) + ", " + companion.getItem(1));
        });
    }

    /** Four worn stone pickaxes: the two most worn are folded; the next pair would lose durability, so it is left. */
    @GameTest(template = "tall_tree", timeoutTicks = 100)
    public static void pairsAreFoldedOnlyWhileNothingIsLost(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        companion.setItem(0, worn(Items.STONE_PICKAXE, 120)); companion.setItem(1, worn(Items.STONE_PICKAXE, 10));
        companion.setItem(2, worn(Items.STONE_PICKAXE, 100)); companion.setItem(3, worn(Items.STONE_PICKAXE, 20));
        CompanionActionResult result = CompanionEvents.executeAction(owner(h, companion), "repair_item", "minecraft:stone_pickaxe", 1);
        h.assertTrue(result.success() && count(companion, Items.STONE_PICKAXE) == 3, "Repair: " + result.message() + " | " + companion.getStoredItemsSummary());
        // 10 + 20 + 131 * 5 / 100 = 36; 36 + 100 + 6 would be more than 131.
        h.assertTrue(lefts(companion, Items.STONE_PICKAXE).equals(List.of(36, 100, 120)), "Left: " + lefts(companion, Items.STONE_PICKAXE));
        h.succeed();
    }

    /** The player asked: the first pair is folded even when some of it is lost, and the loss is said. */
    @GameTest(template = "tall_tree", timeoutTicks = 100)
    public static void theFirstPairIsFoldedWithTheLossSaid(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        companion.setItem(0, worn(Items.STONE_PICKAXE, 100)); companion.setItem(1, worn(Items.STONE_PICKAXE, 120));
        CompanionActionResult result = CompanionEvents.executeAction(owner(h, companion), "repair_item", "pickaxe", 1);
        // 100 + 120 + 6 - 131 = 95 lost.
        h.assertTrue(result.success() && result.message().contains("95 прочности пропало") && lefts(companion, Items.STONE_PICKAXE).equals(List.of(131)),
                "Repair: " + result.message() + " | " + lefts(companion, Items.STONE_PICKAXE));
        h.succeed();
    }

    /** An enchanted one is never folded: its enchantments would be lost. Nothing changes and he says why. */
    @GameTest(template = "tall_tree", timeoutTicks = 100)
    public static void anEnchantedOneIsNotFolded(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        companion.setItem(0, worn(Items.IRON_PICKAXE, 50));
        ItemStack fortune = enchanted(h, worn(Items.IRON_PICKAXE, 60), Enchantments.FORTUNE, 3);
        companion.setItemSlot(EquipmentSlot.MAINHAND, fortune);
        CompanionActionResult result = CompanionEvents.executeAction(owner(h, companion), "repair_item", "pickaxe", 1);
        h.assertTrue(!result.success() && result.message().contains("с чарами"), "Repair: " + result.message());
        h.assertTrue(count(companion, Items.IRON_PICKAXE) == 2 && companion.getMainHandItem().isEnchanted(), "Pickaxes changed: " + companion.getStoredItemsSummary());
        h.succeed();
    }

    /** A grindstone eight blocks away: he walks there, the sword loses its enchantment, the experience lies at the stone. */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void enchantmentsAreGroundOffAtTheGrindstone(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos stone = new BlockPos(24, 1, 16);
        h.setBlock(stone, Blocks.GRINDSTONE);
        companion.setItem(0, enchanted(h, new ItemStack(Items.IRON_SWORD), Enchantments.SHARPNESS, 3));
        CompanionActionResult started = CompanionEvents.executeAction(owner(h, companion), "disenchant_item", "sword", 1);
        h.assertTrue(started.success(), "Order refused: " + started.message());
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && companion.getLastTaskResult().contains("Опыт лежит у точила"), companion.getLastTaskResult());
            h.assertTrue(count(companion, Items.IRON_SWORD) == 1 && !companion.getItem(0).isEnchanted(), "The sword: " + companion.getItem(0));
            h.assertTrue(!h.getLevel().getEntitiesOfClass(ExperienceOrb.class, new AABB(h.absolutePos(stone)).inflate(2)).isEmpty(), "No experience at the grindstone");
        });
    }

    /** Of two enchanted bows the cheaper one (Power I) is ground; Infinity stays. */
    @GameTest(template = "tall_tree", timeoutTicks = 200)
    public static void theCheapestEnchantedOneIsGround(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        h.setBlock(new BlockPos(18, 1, 16), Blocks.GRINDSTONE);
        companion.setItem(0, enchanted(h, new ItemStack(Items.BOW), Enchantments.INFINITY, 1));
        companion.setItem(1, enchanted(h, new ItemStack(Items.BOW), Enchantments.POWER, 1));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "disenchant_item", "minecraft:bow", 1).success(), "Order refused");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
            h.assertTrue(companion.getItem(0).isEnchanted() && !companion.getItem(1).isEnchanted(), "Ground: " + companion.getItem(0) + ", " + companion.getItem(1));
        });
    }

    /** No grindstone near, one in his bag: he sets it down beside him and grinds there. */
    @GameTest(template = "tall_tree", timeoutTicks = 200)
    public static void hisOwnGrindstoneIsSetDown(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        companion.setItem(0, enchanted(h, new ItemStack(Items.IRON_SWORD), Enchantments.SHARPNESS, 1));
        companion.setItem(1, new ItemStack(Items.GRINDSTONE));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "disenchant_item", "sword", 1).success(), "Order refused");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && !companion.getItem(0).isEnchanted(), companion.getLastTaskResult());
            h.assertTrue(blocks(h, Blocks.GRINDSTONE) == 1 && count(companion, Items.GRINDSTONE) == 0, "The grindstone was not set down");
        });
    }

    /** No grindstone near and nothing to make one of: refused at once, the sword keeps its enchantment. */
    @GameTest(template = "tall_tree", timeoutTicks = 100)
    public static void noGrindstoneAndNothingToMakeOne(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        companion.setItem(0, enchanted(h, new ItemStack(Items.IRON_SWORD), Enchantments.SHARPNESS, 1));
        CompanionActionResult result = CompanionEvents.executeAction(owner(h, companion), "disenchant_item", "sword", 1);
        h.assertTrue(!result.success() && result.message().contains("нет точила") && companion.getItem(0).isEnchanted(), "Order: " + result.message());
        h.succeed();
    }

    /** «Сними чары с меча» is the grindstone, not «сними меч»; «почини кирку» folds; the model's steps are checked the same way. */
    @GameTest(template = "tall_tree", timeoutTicks = 100)
    public static void ordersAreReadWithoutTheModel(GameTestHelper h) {
        h.assertTrue("companion work disenchant_item 1 sword".equals(CompanionLocalCommands.route("Олег, сними чары с меча", "Олег")), CompanionLocalCommands.route("Олег, сними чары с меча", "Олег"));
        h.assertTrue("companion work disenchant_item 64 minecraft:bow".equals(CompanionLocalCommands.route("сними чары со всех луков", "Олег")), CompanionLocalCommands.route("сними чары со всех луков", "Олег"));
        h.assertTrue("companion work repair_item 1 pickaxe".equals(CompanionLocalCommands.route("почини кирку", "Олег")), CompanionLocalCommands.route("почини кирку", "Олег"));
        h.assertTrue("companion work repair_item 1 minecraft:iron_pickaxe".equals(CompanionLocalCommands.route("Почини железные кирки", "Олег")), CompanionLocalCommands.route("Почини железные кирки", "Олег"));
        h.assertTrue("companion unequip mainhand".equals(CompanionLocalCommands.route("сними меч", "Олег")), "«сними меч» is still the hand");
        // Review 10.11, round 2: «чар» in his name or in «зачарованный» is no grindstone.
        h.assertTrue("companion unequip head".equals(CompanionLocalCommands.route("Ричард, сними шлем", "Ричард")), CompanionLocalCommands.route("Ричард, сними шлем", "Ричард"));
        h.assertTrue(String.valueOf(CompanionLocalCommands.route("возьми зачарованный меч", "Олег")).startsWith("companion equip"), CompanionLocalCommands.route("возьми зачарованный меч", "Олег"));
        // Review 10.11: anything past a thing's name is the model's to read, a wrong local guess grinds off what was to be kept.
        for (String said : new String[]{"почини кирку, потом наруби дров", "сними чары с меча, а кирку не трогай", "почини железную кирку, а не алмазную",
                "сними чары с моего меча", "почини мне кирку", "сними чары с двух мечей", "сними, пожалуйста, чары с меча", "почини деревянный шлем"}) {
            String routed = CompanionLocalCommands.route(said, "Олег");
            h.assertTrue(routed == null || !routed.contains("repair_item") && !routed.contains("disenchant_item") && !routed.contains("unequip"), "«" + said + "» → " + routed);
        }
        var steps = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"repair_item\",\"item\":\"pickaxe\"},{\"action\":\"disenchant_item\",\"item\":\"minecraft:bow\"}]"));
        h.assertTrue(steps.equals(List.of(new PlannedAction("repair_item", 0, 1, "pickaxe", "auto", "near_player"), new PlannedAction("disenchant_item", 0, 1, "minecraft:bow", "auto", "near_player"))), "Decoded: " + steps);
        for (String json : new String[]{"[{\"action\":\"repair_item\",\"item\":\"кирка\"}]", "[{\"action\":\"disenchant_item\"}]"}) {
            boolean refused;
            try { CompanionPlanCodec.parsePlan(JsonParser.parseString(json)); refused = false; } catch (IllegalStateException invalid) { refused = true; }
            h.assertTrue(refused, "Decoded: " + json);
        }
        h.succeed();
    }

    private static ItemStack worn(Item item, int left) {
        ItemStack stack = new ItemStack(item);
        stack.setDamageValue(stack.getMaxDamage() - left);
        return stack;
    }
    private static ItemStack enchanted(GameTestHelper h, ItemStack stack, ResourceKey<Enchantment> enchantment, int level) {
        stack.enchant(h.getLevel().registryAccess().holderOrThrow(enchantment), level);
        return stack;
    }
    private static CompanionEntity setup(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var player = h.makeMockServerPlayerInLevel();
        player.teleportTo(companion.getX() - 3, companion.getY(), companion.getZ() - 3);
        companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        companion.setSearchRadius(16);
        return companion;
    }
    private static net.minecraft.server.level.ServerPlayer owner(GameTestHelper h, CompanionEntity companion) {
        return h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }
    private static void done(GameTestHelper h, CompanionEntity companion) {
        h.assertTrue(!companion.isGatheringTask() && !companion.isWorking(), "Work still active: " + companion.getLastTaskResult());
    }
    private static int blocks(GameTestHelper h, net.minecraft.world.level.block.Block block) {
        int result = 0;
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(31, 4, 31))) if (h.getBlockState(pos).is(block)) result++;
        return result;
    }
    private static List<ItemStack> all(CompanionEntity companion, Item item) {
        List<ItemStack> found = new java.util.ArrayList<>();
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) found.add(companion.getItem(slot));
        for (EquipmentSlot slot : EquipmentSlot.values()) if (slot != EquipmentSlot.BODY && companion.getItemBySlot(slot).is(item)) found.add(companion.getItemBySlot(slot));
        return found;
    }
    private static int count(CompanionEntity companion, Item item) { return all(companion, item).stream().mapToInt(ItemStack::getCount).sum(); }
    private static List<Integer> lefts(CompanionEntity companion, Item item) {
        return all(companion, item).stream().map(stack -> stack.getMaxDamage() - stack.getDamageValue()).sorted().toList();
    }
    private static int left(CompanionEntity companion, Item item) { return lefts(companion, item).getFirst(); }
}

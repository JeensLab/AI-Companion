package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionDialogue;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionPet;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionPetSearchTests {
    @GameTest(template = "large_search", batch = "pet_search_radius_cat")
    public static void catSearchUsesTheSharedRadius(GameTestHelper h) {
        searchesAtConfiguredDistance(h, CompanionPet.CAT);
    }

    @GameTest(template = "large_search", batch = "pet_search_radius_dog")
    public static void wolfSearchUsesTheSharedRadius(GameTestHelper h) {
        searchesAtConfiguredDistance(h, CompanionPet.DOG);
    }

    @GameTest(template = "large_search", timeoutTicks = 800)
    public static void starterBaitFitsTogetherAndIsGrantedOnceAcrossReloadAndRespawn(GameTestHelper h) {
        var companion = fixture(h, false, false);
        var owner = player(h, companion);
        companion.setDeathRecovery(false);
        h.assertTrue(companion.isEmpty(), "Spawned with items before opting in");
        for (int slot = 1; slot < companion.getContainerSize(); slot++) {
            companion.setItem(slot, new ItemStack(Items.STONE, 64));
        }
        companion.grantPetStarterBait();
        h.assertTrue(companion.getItem(0).isEmpty(), "Granted a partial kit without room for both items");

        companion.setItem(1, ItemStack.EMPTY);
        companion.grantPetStarterBait();
        companion.grantPetStarterBait();
        h.assertTrue(companion.getItem(0).is(Items.SALMON) && companion.getItem(0).getCount() == 3
                && companion.getItem(1).is(Items.BONE) && companion.getItem(1).getCount() == 3,
                "The bag must contain exactly three raw salmon and three bones");

        companion.clearContent();
        companion.grantPetStarterBait();
        h.assertTrue(companion.isEmpty(), "Granted more bait after the starter items were used");
        var saved = companion.saveWithoutId(new CompoundTag());
        var reloaded = AICompanionMod.COMPANION.get().create(h.getLevel());
        h.assertTrue(reloaded != null, "Could not create a companion to load the saved data");
        reloaded.load(saved);
        reloaded.grantPetStarterBait();
        h.assertTrue(reloaded.isEmpty(), "Granted more bait after loading the saved companion");

        var oldId = companion.getUUID();
        companion.kill();
        h.succeedWhen(() -> {
            var back = CompanionEvents.getOwnedCompanion(owner);
            h.assertTrue(back != null && back.isAlive() && !oldId.equals(back.getUUID()), "The companion has not respawned");
            back.grantPetStarterBait();
            h.assertTrue(back.isEmpty(), "Granted more bait after respawn");
        });
    }

    @GameTest(template = "large_search", timeoutTicks = 380, attempts = 2, batch = "pet_search_blocked_cat")
    public static void triesAnotherCatIfNearestIsUnreachable(GameTestHelper h) {
        findsReachableAlternative(h, CompanionPet.CAT);
    }

    @GameTest(template = "large_search", timeoutTicks = 380, attempts = 2, batch = "pet_search_blocked_dog")
    public static void triesAnotherWolfIfNearestIsUnreachable(GameTestHelper h) {
        findsReachableAlternative(h, CompanionPet.DOG);
    }

    @GameTest(template = "large_search", timeoutTicks = 650, attempts = 3, batch = "pet_search_last_location")
    public static void givesBothLastLocationsWithoutInterruptingAnOrder(GameTestHelper h) {
        var companion = nearbySearchFixture(h);
        var player = player(h, companion);
        h.assertTrue(say(h, player, "Где наш питомец?").contains("питомцев у меня пока нет"), "Invented a pet");
        var cat = h.spawn(EntityType.CAT, 10, 3, 8);
        var dog = h.spawn(EntityType.WOLF, 8, 3, 12);
        companion.setItem(0, new ItemStack(Items.COD, 12));
        say(h, player, "Приручи кота");
        String[] catPlace = new String[1];
        String dimension = h.getLevel().dimension().location().toString();
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(cat.isTame() && !companion.hasActiveWorkOrder(), companion.getLastTaskResult()))
                .thenExecute(() -> {
                    say(h, player, "Посади кота");
                    say(h, player, "Приручи пса");
                })
                .thenWaitUntil(() -> h.assertTrue(companion.petQuestion(CompanionPet.DOG) > 0, "Did not ask for bones"))
                .thenIdle(20)
                .thenExecute(() -> {
                    catPlace[0] = cat.blockPosition().toShortString();
                    cat.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                    long workId = companion.activeWorkId();
                    String report = say(h, player, "Олег, где наш кот?");
                    h.assertTrue(report.contains("видел кота") && report.contains(catPlace[0]) && report.contains(dimension),
                            "Lost the unloaded cat's last position or dimension: " + report);
                    h.assertTrue(companion.activeWorkId() == workId && companion.petQuestion(CompanionPet.DOG) == workId,
                            "Asking about the cat interrupted the dog order");
                    companion.setItem(1, new ItemStack(Items.BONE, 12));
                })
                .thenWaitUntil(() -> h.assertTrue(dog.isTame() && !companion.hasActiveWorkOrder(), companion.getLastTaskResult()))
                .thenExecute(() -> {
                    String dogPlace = dog.blockPosition().toShortString();
                    String dogReport = say(h, player, "Где наш пёс?");
                    h.assertTrue(dogReport.contains("видел собаку") && dogReport.contains(dogPlace) && dogReport.contains(dimension),
                            "Dog's answer did not include its location and dimension: " + dogReport);
                    String both = say(h, player, "Где наш питомец?");
                    h.assertTrue(both.contains("видел кота") && both.contains("видел собаку")
                                    && both.contains(catPlace[0]) && both.contains(dogPlace),
                            "The generic question did not include both pets: " + both);
                }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void validatesPetLocationPlans(GameTestHelper h) {
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"pet_location\",\"item\":\"all\"}]"));
        h.assertTrue(plan.size() == 1 && plan.getFirst().item().equals("all"), "Could not parse a pet location question");
        String report = "Последний раз я видел кота у 7, 2, 9, измерение minecraft:the_nether.";
        h.assertTrue(CompanionDialogue.summarize(plan, report).equals(report), "The model-plan reply lost coordinates or dimension");
        try {
            CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"pet_location\",\"item\":\"wolf\"}]"));
            h.fail("Accepted an unknown pet type");
        } catch (IllegalStateException expected) {
            h.succeed();
        }
    }

    private static void searchesAtConfiguredDistance(GameTestHelper h, CompanionPet kind) {
        var companion = fixture(h, false, false);
        var player = player(h, companion);
        var at = companion.blockPosition().offset(64, 0, 0);
        h.getLevel().getChunk(at);
        h.getLevel().getChunk(at.east());
        TamableAnimal stray = kind == CompanionPet.CAT ? h.spawn(EntityType.CAT, h.relativePos(at)) : h.spawn(EntityType.WOLF, h.relativePos(at));
        String action = kind == CompanionPet.CAT ? "tame_cat" : "tame_dog";
        var center = at.getCenter();
        try {
            stray.setPos(center.x + 1, center.y, center.z);
            h.assertTrue(!CompanionEvents.executeAction(player, action, "", 1).success(),
                    "The default search included a stray beyond 64 blocks");
            stray.setPos(center.x, center.y, center.z);
            var result = CompanionEvents.executeAction(player, action, "", 1);
            h.assertTrue(result.status() == CompanionActionResult.Status.ACCEPTED,
                    "The default search missed the stray at 64 blocks: " + result.message());

            CompanionEvents.answerPetQuestion(player, companion.getUUID(), kind, 0, "cancel");
            h.assertTrue(!companion.hasActiveWorkOrder(), "The previous order is still active");
            companion.setPetSearchRadius(48);
            h.assertTrue(!CompanionEvents.executeAction(player, action, "", 1).success(),
                    "The smaller search radius was ignored");
            companion.setPetSearchRadius(80);
            stray.setPos(center.x + 1, center.y, center.z);
            h.assertTrue(CompanionEvents.executeAction(player, action, "", 1).status() == CompanionActionResult.Status.ACCEPTED,
                    "The larger search radius did not find a stray beyond the default");
            h.succeed();
        } finally {
            stray.discard();
        }
    }

    private static void findsReachableAlternative(GameTestHelper h, CompanionPet kind) {
        var companion = nearbySearchFixture(h);
        boolean cat = kind == CompanionPet.CAT;
        companion.setItem(0, new ItemStack(cat ? Items.COD : Items.BONE, 12));
        for (int x = 11; x <= 13; x++) for (int z = 7; z <= 9; z++) for (int y = 3; y <= 5; y++) {
            if (x != 12 || z != 8 || y == 5) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        }
        TamableAnimal blocked = cat ? h.spawn(EntityType.CAT, 12, 3, 8) : h.spawn(EntityType.WOLF, 12, 3, 8);
        blocked.setNoAi(true);
        TamableAnimal reachable = cat ? h.spawn(EntityType.CAT, 8, 3, 14) : h.spawn(EntityType.WOLF, 8, 3, 14);
        say(h, player(h, companion), cat ? "Приручи кота" : "Приручи пса");
        h.startSequence().thenWaitUntil(() -> h.assertTrue(!companion.hasActiveWorkOrder(), "Still trying to reach a pet"))
                .thenExecute(() -> {
                    h.assertTrue(!blocked.isTame(), "Fed a pet through a solid wall");
                    h.assertTrue(reachable.isTame() && companion.getUUID().equals(reachable.getOwnerUUID()),
                            "Did not tame the reachable pet: " + companion.getLastTaskResult());
                }).thenSucceed();
    }

    static CompanionEntity fixture(GameTestHelper h, boolean water, boolean rod) {
        for (int x = 0; x < 24; x++) for (int z = 0; z < 24; z++) for (int y = 0; y <= 2; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        if (water) for (int x = 12; x <= 20; x++) for (int z = 4; z <= 14; z++) for (int y = 1; y <= 2; y++) h.setBlock(new BlockPos(x, y, z), Blocks.WATER);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 8, 3, 8);
        var player = h.makeMockServerPlayerInLevel(); BlockPos stand = h.absolutePos(new BlockPos(5, 3, 8));
        player.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(player.getUUID()); player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player); companion.setFollowing(false); companion.setSearchRadius(16);
        if (rod) companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
        return companion;
    }

    private static CompanionEntity nearbySearchFixture(GameTestHelper h) {
        var companion = fixture(h, false, false);
        companion.setPetSearchRadius(CompanionPet.MIN_SEARCH_RADIUS);
        return companion;
    }

    private static ServerPlayer player(GameTestHelper h, CompanionEntity companion) {
        return h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }

    private static String say(GameTestHelper h, ServerPlayer player, String speech) {
        String command = CompanionLocalCommands.route(speech, "Олег");
        h.assertTrue(command != null, "Did not understand: " + speech);
        var action = CompanionLocalCommands.action(command);
        var result = CompanionEvents.executeAction(player, action.type(), action.item(), action.count());
        h.assertTrue(result.success(), result.message());
        return result.message();
    }
}

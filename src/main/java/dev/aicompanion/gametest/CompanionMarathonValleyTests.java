package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Marathon of ordinary requests, arena «долина»: a recorded terrain fixture (x -100..-5, y 52..91,
 * z -100..-21; cut by scripts/arena/ArenaTool.java, bedrock floor and walls added). Forest in the north and west, a quarry pit
 * with a cliff on its west side and water at the bottom, open plains in the east and south, and the ravine of game log 23.10
 * (session 05cdd3a7) at x 58..68, z 42..52. Relative height 17–19 is the plain.
 *
 * the player: «самая большая проблема, что он начинает делать задачу и потом теряется на самых простых вещах».
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMarathonValleyTests {
    static final int X = 96, Y = 40, Z = 80;

    /** Wood, stone tools for both, the chest; then «иди за мной» across the valley, «стой», «иди ко мне». */
    @GameTest(template = "marathon_valley", batch = "marathon", timeoutTicks = 24000)
    public static void woodToolsChestAndAWalk(GameTestHelper h) {
        valley(h);
        var a = new MarathonArena(h, X, Y, Z, 73, 59, 75, 58, 40);
        var watch = a.watch;
        a.script
                .ask("Олег, добудь дерева", "[{\"action\":\"gather_wood\",\"count\":8}]", 20 * 240)
                .check("wood in the bag", () -> {
                    a.expect(a.countTag(ItemTags.LOGS) >= 8, "Wood not in the bag: " + a.oleg.getStoredItemsSummary() + " || " + a.chat.history());
                    // One small tree (4 logs) stands 20 blocks south; the forest with enough wood begins 30 blocks north.
                    watch.assertStayedNear(42);
                    watch.assertNothingLeft();
                    watch.assertNoLongIdle(10);
                })
                .waitUntil("back with the player", () -> a.toPlayer() < 8, 20 * 30)
                .ask("Сделай каменные инструменты мне и себе", "[{\"action\":\"craft_item\",\"item\":\"minecraft:stone_pickaxe\",\"count\":1},"
                        + "{\"action\":\"craft_item\",\"item\":\"minecraft:stone_axe\",\"count\":1},{\"action\":\"craft_item\",\"item\":\"minecraft:stone_shovel\",\"count\":1},"
                        + "{\"action\":\"craft_item\",\"item\":\"minecraft:stone_sword\",\"count\":1}]", 20 * 900)
                .check("tools for both", () -> {
                    for (var tool : new net.minecraft.world.item.Item[]{Items.STONE_PICKAXE, Items.STONE_AXE, Items.STONE_SHOVEL, Items.STONE_SWORD}) {
                        a.expect(a.count(tool) >= 1, "No " + tool + " for Oleg || " + a.chat.history() + " || " + watch.summary());
                        a.expect(a.given(tool) >= 1, "No " + tool + " thrown to the player || " + a.chat.history());
                    }
                    watch.assertNothingLeft();
                    watch.assertNoLongIdle(10);
                })
                .waitUntil("back with the player after the tools", () -> a.toPlayer() < 8, 20 * 60)
                .ask("Сложи вещи в сундук", null, 20 * 300)
                .check("stored", () -> {
                    a.expect(a.chat.said.getLast().report().contains("Сложил") || a.chat.said.getLast().report().contains("сложил"), "No store report || " + a.chat.history());
                    a.expect(a.countTag(ItemTags.LOGS) == 0 && a.count(Items.COBBLESTONE) == 0, "Resources still in the bag: " + a.oleg.getStoredItemsSummary());
                    watch.assertNothingLeft();
                })
                .order("Иди за мной")
                .then("the player walks through the forest to the north-west", () -> a.walkPlayer(.2, 70, 36, 50, 36, 30, 40, 12, 40, 10, 20, 12, 8),
                        a::playerArrived, 20 * 60)
                .waitUntil("caught up", () -> a.toPlayer() < 8, 20 * 40)
                .check("followed like a friend", () -> { watch.assertNoRescue(); watch.assertNoLongIdle(10); })
                .order("Стой")
                .then("the player walks off", () -> a.walkPlayer(.2, 30, 8), a::playerArrived, 20 * 20)
                .pause(200)
                .check("stayed", () -> a.expect(a.toPlayer() > 12, "Did not stay: " + a.toPlayer()))
                .ask("Иди ко мне", null, 20 * 60)
                .check("came", () -> {
                    a.expect(a.chat.said.getLast().report().contains("рядом"), "No «рядом» || " + a.chat.history());
                    watch.assertWithPlayer(4);
                });
        a.script.run("valley-wood-tools-chest-walk", watch::summary);
    }

    /**
     * Night on the plain: «охраняй меня», a zombie, a skeleton and a spider come up to the player (and a creeper farther off);
     * then «прекрати защищать», «добудь дерева» with a zombie coming to the player in the middle of it, and «стоп» in the
     * middle of the next wood order. The mock player is in creative, so the monsters are set right by him.
     */
    @GameTest(template = "marathon_valley", batch = "marathon", timeoutTicks = 16000)
    public static void nightGuardAMobInTheWorkAndStop(GameTestHelper h) {
        valley(h);
        var a = new MarathonArena(h, X, Y, Z, 73, 59, 75, 58, 40);
        var watch = a.watch;
        // Ready for the night like a player: leather armour (without it two monsters sometimes killed him, see the report).
        for (var piece : new net.minecraft.world.item.Item[]{Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS})
            a.oleg.setItemSlot(a.oleg.getEquipmentSlotForItem(new net.minecraft.world.item.ItemStack(piece)), new net.minecraft.world.item.ItemStack(piece));
        a.oleg.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(Items.STONE_SWORD));
        java.util.List<net.minecraft.world.entity.Mob> night = new java.util.ArrayList<>();
        // A real night for the spider (neutral in daylight) — the whole test server shares the clock, it is set back at the end.
        long day = h.getLevel().getDayTime();
        h.getLevel().setDayTime(18000);
        int[] logsAtStop = {0};
        a.script
                .ask("Охраняй меня", "[{\"action\":\"protect\"}]", 200)
                .then("monsters come up to the player", () -> {
                    night.add(monster(a, net.minecraft.world.entity.EntityType.ZOMBIE, 80, 60));
                    night.add(monster(a, net.minecraft.world.entity.EntityType.SKELETON, 70, 63));
                    night.add(monster(a, net.minecraft.world.entity.EntityType.SPIDER, 79, 54));
                        // The mock player is in creative and nobody goes for him: the spider stays peaceful, and a zombie that wanders
                        // off is no threat. The zombie and the skeleton near the player must fall.
                }, () -> night.stream().filter(m -> !(m instanceof net.minecraft.world.entity.monster.Spider)).noneMatch(m -> m.isAlive() && m.distanceTo(a.player) < 12), 20 * 60)
                .check("guarded like a friend", () -> {
                    watch.assertAlive();
                    // The rule is: no chase farther than 16 blocks from the player; a few more while turning back.
                    watch.assertStayedNear(24);
                    watch.assertNoRescue();
                    watch.assertNothingLeft();
                })
                .order("Прекрати защищать")
                .ask("Олег, добудь дерева", "[{\"action\":\"gather_wood\",\"count\":6}]", 20 * 240)
                .check("wood despite the zombie", () -> {
                    a.expect(a.countTag(ItemTags.LOGS) >= 6, "Wood not in the bag: " + a.oleg.getStoredItemsSummary() + " || " + a.chat.history());
                    watch.assertAlive();
                    watch.assertNothingLeft();
                    watch.assertNoLongIdle(10);
                })
                .waitUntil("back with the player", () -> a.toPlayer() < 8, 20 * 60)
                .then("the next wood order, «стоп» after two logs", () -> {
                    a.chat.say("Олег, добудь дерева", "[{\"action\":\"gather_wood\",\"count\":10}]");
                    logsAtStop[0] = a.countTag(ItemTags.LOGS);
                }, () -> a.countTag(ItemTags.LOGS) >= logsAtStop[0] + 2, 20 * 120)
                .then("«стоп»", () -> { a.chat.say("Стоп"); logsAtStop[0] = a.countTag(ItemTags.LOGS); }, () -> !a.oleg.isWorking() && (!a.oleg.isGatheringLogs() || a.oleg.isRecoveringSupports() || a.oleg.isCollectingDrops()), 20 * 5)
                .pause(200)
                .check("stopped at once, nothing lost", () -> {
                    // Logs may have gone into a tool he was making the moment «стоп» came; what matters is that nothing lies left behind.
                    a.expect(!a.oleg.isWorking() && (!a.oleg.isGatheringLogs() || a.oleg.isRecoveringSupports() || a.oleg.isCollectingDrops()), "Still working after «стоп» (climbing down his support is no work): " + a.oleg.getLastTaskResult());
                    watch.assertNothingLeft();
                    h.getLevel().setDayTime(day);
                });
        h.onEachTick(() -> { if (h.getTick() % (h.getTick() < 400 ? 10 : 100) == 0) a.watch.note("oleg hp" + (int) a.oleg.getHealth() + " " + night.stream().map(m -> m.getType().toShortString() + (m.isAlive() ? "@" + a.watch.where(m.blockPosition()) + " hp" + (int) m.getHealth() + " t=" + (m.getTarget() == null ? "-" : m.getTarget().getType().toShortString()) : " dead")).toList() + " oleg target=" + (a.oleg.getTarget() == null ? "-" : a.oleg.getTarget().getType().toShortString())); });
        boolean[] logged = {false};
        h.onEachTick(() -> {
            if (logged[0] || !a.oleg.getLastTaskResult().contains("не нашёл доступных деревьев")) return;
            logged[0] = true;
            var found = dev.aicompanion.entity.CompanionLogSearch.find(h.getLevel(), a.oleg.blockPosition(), a.oleg.getSearchRadius());
            int unloaded = 0, chunks = 0;
            var at = a.oleg.blockPosition();
            for (int cx = (at.getX() - 40) >> 4; cx <= (at.getX() + 40) >> 4; cx++) for (int cz = (at.getZ() - 40) >> 4; cz <= (at.getZ() + 40) >> 4; cz++) {
                chunks++;
                if (h.getLevel().getChunkSource().getChunkNow(cx, cz) == null) unloaded++;
            }
            a.watch.note("chunks round him not loaded: " + unloaded + " of " + chunks);
            a.watch.note("log search from " + a.watch.where(a.oleg.blockPosition()) + " r" + a.oleg.getSearchRadius() + ": " + found.size() + " logs, nearest " + (found.isEmpty() ? "-" : a.watch.where(found.getFirst())));
        });
        // The zombie during the first wood order: when the wood order starts, it comes up to the player.
        boolean[] spawned = {false};
        h.onEachTick(() -> {
            if (spawned[0] || !a.oleg.isGatheringTask() || a.countTag(ItemTags.LOGS) < 1) return;
            spawned[0] = true;
            night.add(monster(a, net.minecraft.world.entity.EntityType.ZOMBIE, 79, 61));
        });
        a.script.run("valley-night-guard-mob-stop", () -> watch.summary() + " creeper/explosions not checked");
    }

    /** «Добудь мяса и пожарь» on the plain with a few cows and pigs, then back to the player. */
    @GameTest(template = "marathon_valley", batch = "marathon", timeoutTicks = 16000)
    public static void meatHuntedAndCooked(GameTestHelper h) {
        valley(h);
        var a = new MarathonArena(h, X, Y, Z, 73, 59, 75, 58, 40);
        a.oleg.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(Items.STONE_SWORD));
        a.oleg.setItem(0, new net.minecraft.world.item.ItemStack(Items.OAK_LOG, 4));
        a.oleg.setItem(1, new net.minecraft.world.item.ItemStack(Items.STONE_PICKAXE));
        for (int[] at : new int[][]{{84, 66}, {86, 70}, {88, 64}, {82, 72}}) a.h.spawn(net.minecraft.world.entity.EntityType.COW, a.at(at[0], at[1]));
        for (int[] at : new int[][]{{70, 72}, {72, 74}}) a.h.spawn(net.minecraft.world.entity.EntityType.PIG, a.at(at[0], at[1]));
        var watch = a.watch;
        a.script
                .ask("Добудь мяса и пожарь", "[{\"action\":\"hunt_animals\",\"item\":\"any\",\"count\":3}]", 20 * 600)
                .check("cooked meat in the bag", () -> {
                    int cooked = a.count(Items.COOKED_BEEF) + a.count(Items.COOKED_PORKCHOP);
                    a.expect(cooked >= 3, "Cooked: " + cooked + " || " + a.chat.history());
                    a.expect(a.count(Items.BEEF) + a.count(Items.PORKCHOP) == 0, "Raw meat left: " + (a.count(Items.BEEF) + a.count(Items.PORKCHOP)) + " || " + a.chat.history());
                    watch.assertNothingLeft();
                    watch.assertNoLongIdle(12);
                })
                .waitUntil("back with the player", () -> a.toPlayer() < 8, 20 * 90);
        // Where the furnace could not be set up or reached: the blocks round him into the trail.
        boolean[] shot = {false};
        h.onEachTick(() -> {
            String status = a.oleg.getLastTaskResult();
            if (shot[0] || !status.contains("безопасное место") && !status.contains("подойти к ней")) return;
            shot[0] = true;
            watch.note("furnace trouble, bench/furnace near: " + java.util.stream.IntStream.rangeClosed(-4, 4).boxed().flatMap(dx -> java.util.stream.IntStream.rangeClosed(-3, 3).boxed()
                    .flatMap(dy -> java.util.stream.IntStream.rangeClosed(-4, 4).mapToObj(dz -> a.oleg.blockPosition().offset(dx, dy, dz))))
                    .filter(p -> h.getLevel().getBlockState(p).is(net.minecraft.world.level.block.Blocks.FURNACE) || h.getLevel().getBlockState(p).is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE))
                    .map(p -> h.getLevel().getBlockState(p).getBlock().getName().getString() + "@" + watch.where(p)).toList());
            watch.snapshot();
        });
        a.script.run("valley-meat-cooked", watch::summary);
    }

    /** A bag full to the last slot: «добудь дерева» is answered honestly and at once, nothing thrown away. */
    @GameTest(template = "marathon_valley", batch = "marathon", timeoutTicks = 4000)
    public static void aFullBagIsSaidAtOnce(GameTestHelper h) {
        valley(h);
        var a = new MarathonArena(h, X, Y, Z, 73, 59, 75, 58, 40);
        var junk = new net.minecraft.world.item.Item[]{Items.DIRT, Items.COBBLESTONE, Items.GRAVEL, Items.SAND, Items.ANDESITE, Items.DIORITE, Items.GRANITE,
                Items.FLINT, Items.BONE, Items.STRING, Items.FEATHER, Items.WHEAT_SEEDS, Items.CLAY_BALL};
        for (int slot = 0; slot < a.oleg.getContainerSize(); slot++) a.oleg.setItem(slot, new net.minecraft.world.item.ItemStack(junk[slot % junk.length], 64));
        int before = 0; for (int slot = 0; slot < a.oleg.getContainerSize(); slot++) before += a.oleg.getItem(slot).getCount();
        int carried = before;
        var watch = a.watch;
        a.script
                .ask("Олег, добудь дерева", "[{\"action\":\"gather_wood\",\"count\":8}]", 20 * 60)
                .check("said, nothing thrown away", () -> {
                    int now = 0; for (int slot = 0; slot < a.oleg.getContainerSize(); slot++) now += a.oleg.getItem(slot).getCount();
                    a.expect(now == carried, "The bag changed: " + carried + " -> " + now);
                    String report = a.chat.said.getLast().report();
                    a.expect(report.contains("мест") || report.contains("сумк"), "No word about the full bag: " + report);
                    watch.assertNothingLeft();
                });
        a.script.run("valley-full-bag", watch::summary);
    }

    /** The pickaxe breaks at the first stone of «добудь 6 камня»: he makes a new one and finishes. */
    @GameTest(template = "marathon_valley", batch = "marathon", timeoutTicks = 12000)
    public static void aPickaxeThatBreaksInTheMiddleIsReplaced(GameTestHelper h) {
        valley(h);
        var a = new MarathonArena(h, X, Y, Z, 44, 36, 46, 37, 40);
        var worn = new net.minecraft.world.item.ItemStack(Items.WOODEN_PICKAXE);
        worn.setDamageValue(worn.getMaxDamage() - 1);
        a.oleg.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, worn);
        a.oleg.setItem(0, new net.minecraft.world.item.ItemStack(Items.OAK_LOG, 3));
        var watch = a.watch;
        a.script
                .ask("Добудь 6 камня", null, 20 * 400)
                .check("six stones with a new pickaxe", () -> {
                    a.expect(a.count(Items.COBBLESTONE) >= 6, "Stone: " + a.count(Items.COBBLESTONE) + " || " + a.chat.history());
                    a.expect(a.countTag(ItemTags.PICKAXES) >= 1, "No pickaxe after the old one broke");
                    watch.assertNothingLeft();
                    watch.assertNoLongIdle(12);
                })
                .waitUntil("back with the player", () -> a.toPlayer() < 8, 20 * 60);
        a.script.run("valley-broken-pickaxe", watch::summary);
    }

    /** Leaving the world in the middle of «добудь дерева» and coming back: the same Oleg, the logs with him, none left lying. */
    @GameTest(template = "marathon_valley", batch = "marathon", timeoutTicks = 12000)
    public static void leavingTheWorldInTheMiddleOfWork(GameTestHelper h) {
        valley(h);
        var a = new MarathonArena(h, X, Y, Z, 73, 59, 75, 58, 40);
        a.oleg.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(Items.STONE_AXE));
        var watch = a.watch;
        int[] logs = {0};
        java.util.UUID id = a.oleg.getUUID();
        a.script
                .then("wood until three logs are in", () -> a.chat.say("Олег, добудь дерева", "[{\"action\":\"gather_wood\",\"count\":12}]"),
                        () -> a.countTag(ItemTags.LOGS) >= 3, 20 * 200)
                .then("leave and come back", () -> { logs[0] = a.countTag(ItemTags.LOGS); a.reload(() -> { }); }, () -> a.oleg.getUUID().equals(id) && !a.oleg.isRemoved() && a.oleg.tickCount > 5, 20 * 5)
                .check("the same Oleg with the logs", () -> {
                    // Not fewer (he picks up his own logs lying round him after coming back).
                    a.expect(a.countTag(ItemTags.LOGS) >= logs[0], "Logs after reload: " + logs[0] + " -> " + a.countTag(ItemTags.LOGS));
                    long companions = h.getLevel().getEntitiesOfClass(dev.aicompanion.entity.CompanionEntity.class, new net.minecraft.world.phys.AABB(h.absolutePos(net.minecraft.core.BlockPos.ZERO)).inflate(200)).stream()
                            .filter(c -> c.isAlive() && a.player.getUUID().equals(c.getOwnerId())).count();
                    a.expect(companions == 1, "Companions after reload: " + companions);
                })
                .ask("Иди за мной", null, 200)
                .waitUntil("with the player", () -> a.toPlayer() < 8, 20 * 60)
                .pause(200)
                .check("nothing of the cut tree left lying", () -> watch.assertNothingLeft());
        a.script.run("valley-reload", watch::summary);
    }

    /**
     * The cut of the world left one oak half inside the south wall (x 74..94, z 70..79): its crown logs fell on leaves against the
     * bedrock, where nobody gets — an artefact of the arena, not of the world. It is taken away.
     */
    static void valley(GameTestHelper h) {
        var t = new MarathonTerrain(h, X, Y, Z);
        for (int x = 74; x <= 94; x++) for (int z = 70; z <= 78; z++) for (int y = 12; y < Y; y++) {
            var state = t.get(x, y, z);
            if (state.is(net.minecraft.tags.BlockTags.LOGS) || state.is(net.minecraft.tags.BlockTags.LEAVES)) t.set(x, y, z, net.minecraft.world.level.block.Blocks.AIR);
        }
    }

    /** A monster at this column, persistent, with a helmet against the daylight of the test world. */
    static net.minecraft.world.entity.Mob monster(MarathonArena a, net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob> type, int x, int z) {
        var mob = a.h.spawn(type, a.at(x, z));
        mob.setPersistenceRequired();
        mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(Items.LEATHER_HELMET));
        return mob;
    }
}

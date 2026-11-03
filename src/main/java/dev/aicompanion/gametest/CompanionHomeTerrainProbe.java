package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Trips of Oleg over generated terrain in a disposable normal world: a real player on the integrated server, real chunks loading and
 * unloading, no structure holding them. Opt-in by system property; {@code scripts/verify-home-terrain.ps1} runs it over several seeds.
 */
@EventBusSubscriber(modid = AICompanionMod.MOD_ID, value = Dist.CLIENT)
public final class CompanionHomeTerrainProbe {
    private static final int SCENE_SPACING = 700, SETTLE_TICKS = 100, TIMEOUT_TICKS = 20 * 240, CLIENT_STEP = 20, MAX_UNLOADED = 30, SITE_TRIES = 3, MAX_RISE = 12;

    /** The verdict is handed the Oleg there is now: a reload of his chunk makes a new entity, and the one set up for the scene is left behind. */
    private record Plan(Runnable order, Function<CompanionEntity, String> verdict) { }
    private static final class NoGround extends IllegalStateException { NoGround(String message) { super(message); } }
    private interface Scene { Plan prepare(ServerLevel level, ServerPlayer player, CompanionEntity oleg, BlockPos base); }
    private record Named(String name, Scene scene) { }

    private static boolean started, finishing;
    private static int ticks, scene, unloaded;
    private static int stage;
    private static long readyAt, orderedAt;
    private static Plan plan;
    private static BlockPos spawn, start;
    private static final List<String> failed = new ArrayList<>();
    private static volatile String serverFailure;
    private static volatile String screenshot;

    private static final List<Named> SCENES = List.of(
            new Named("mine_chest_is_no_home", CompanionHomeTerrainProbe::mineChest),
            new Named("walk_to_chest_60", (l, p, o, b) -> walkToChest(l, p, o, b, 50, 75)),
            new Named("walk_to_chest_115", (l, p, o, b) -> walkToChest(l, p, o, b, 105, 122)),
            new Named("walk_to_furnace_70", CompanionHomeTerrainProbe::walkToFurnace),
            new Named("enter_house_with_door", CompanionHomeTerrainProbe::enterHouse),
            new Named("no_route_home", CompanionHomeTerrainProbe::noRoute),
            new Named("home_past_the_limit", CompanionHomeTerrainProbe::farHome));

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("aicompanion.homeProbe")) return;
        var mc = Minecraft.getInstance();
        if (!started && mc.screen instanceof TitleScreen) {
            started = true;
            long seed = Long.getLong("aicompanion.homeProbeSeed", 1L);
            GameRules rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("OlegHome-" + seed + "-" + System.currentTimeMillis(),
                    new LevelSettings("Home terrain QA", GameType.CREATIVE, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(seed, false, false), registry -> registry.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.NORMAL).value().createWorldDimensions(), mc.screen);
        }
        if (mc.level == null || mc.player == null) return;
        if (screenshot != null) {
            String name = screenshot; screenshot = null;
            Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> AICompanionMod.LOGGER.info("Home probe screenshot: {}", message.getString()));
        }
        if (++ticks < CLIENT_STEP) return;
        ticks = 0;
        if (serverFailure != null) {
            AICompanionMod.LOGGER.error("HOME_TERRAIN_PROBE_CRASHED: {}", serverFailure);
            mc.stop();
            return;
        }
        if (finishing) {
            AICompanionMod.LOGGER.info("HOME_TERRAIN_PROBE_DONE: seed={} scenes={} failed={}", Long.getLong("aicompanion.homeProbeSeed", 1L), SCENES.size(), failed);
            mc.stop();
            return;
        }
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        server.execute(() -> {
            try {
                var player = server.getPlayerList().getPlayer(id);
                var oleg = CompanionEvents.getOwnedCompanion(player);
                // The probe's own jumps leave an idle Oleg in a chunk that is only now loading back.
                if (oleg == null) {
                    if (++unloaded > MAX_UNLOADED) throw new IllegalStateException("No companion in the probe world");
                    return;
                }
                unloaded = 0;
                advance(player.serverLevel(), player, oleg);
            } catch (RuntimeException ex) { serverFailure = ex.toString(); }
        });
    }

    private static void advance(ServerLevel level, ServerPlayer player, CompanionEntity oleg) {
        long now = level.getGameTime();
        Named current = SCENES.get(scene);
        switch (stage) {
            case 0 -> {
                if (spawn == null) spawn = player.blockPosition();
                level.setDayTime(1000);
                level.setWeatherParameters(6000, 0, false, false);
                oleg.clearContent();
                oleg.setHealth(oleg.getMaxHealth());
                BlockPos base = null;
                plan = null;
                // Mountains or sea can leave no flat patch at the distance a scene needs: it moves on down the line.
                for (int site = 0; plan == null; site++) {
                    try {
                        base = spot(level, spawn.offset(SCENE_SPACING * (scene + 1), 0, SCENE_SPACING * site), 0, 300);
                        player.teleportTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
                        oleg.teleportTo(base.getX() + 1.5, base.getY(), base.getZ() + 0.5);
                        plan = current.scene.prepare(level, player, oleg, base);
                    } catch (NoGround ex) { if (site + 1 >= SITE_TRIES) throw ex; }
                }
                player.teleportTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
                start = base;
                readyAt = now + SETTLE_TICKS;
                stage = 1;
            }
            case 1 -> {
                if (now < readyAt) return;
                plan.order.run();
                orderedAt = now;
                stage = 2;
            }
            case 2 -> {
                boolean busy = oleg.isWorking() || oleg.isGatheringTask();
                boolean timedOut = now - orderedAt > TIMEOUT_TICKS;
                if ((busy || now - orderedAt < 40) && !timedOut) return;
                String verdict = timedOut ? "timed out at " + oleg.blockPosition() : plan.verdict.apply(oleg);
                AICompanionMod.LOGGER.info("HOME_TERRAIN_SCENE: {} {} ticks={} from={} walked={} oleg={} said=[{}]{}", current.name, verdict == null ? "PASS" : "FAIL", now - orderedAt,
                        start.toShortString(), (int)Math.sqrt(start.distSqr(oleg.blockPosition())), oleg.blockPosition().toShortString(), oleg.getLastTaskResult(), verdict == null ? "" : " why=" + verdict);
                if (verdict != null) failed.add(current.name);
                player.teleportTo(oleg.getX() + 4, oleg.getY() + 5, oleg.getZ() + 4);
                player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, oleg.position());
                readyAt = now + 40;
                stage = 3;
            }
            case 3 -> {
                if (now < readyAt) return;
                screenshot = "home-" + Long.getLong("aicompanion.homeProbeSeed", 1L) + "-" + current.name + ".png";
                stage = 0;
                if (++scene >= SCENES.size()) finishing = true;
            }
            default -> throw new IllegalStateException("Stage " + stage);
        }
    }

    /** A chest left deep in natural ground is no home. */
    private static Plan mineChest(ServerLevel level, ServerPlayer player, CompanionEntity oleg, BlockPos base) {
        BlockPos chest = base.below(14);
        place(level, player, chest, Blocks.CHEST);
        return new Plan(() -> { }, now -> now.getPlacesForAi().contains("дом на") ? "mine chest became a home: " + now.getPlacesForAi() : null);
    }

    private static Plan walkToChest(ServerLevel level, ServerPlayer player, CompanionEntity oleg, BlockPos base, int from, int to) {
        BlockPos chest = spot(level, base, from, to);
        place(level, player, chest, Blocks.CHEST);
        oleg.setItem(0, new ItemStack(Items.COBBLESTONE, 7));
        return new Plan(() -> order(player, "store_items", "stone", 1), now -> {
            int stored = level.getBlockEntity(chest) instanceof Container box ? count(box, Items.COBBLESTONE) : -1;
            return now.didLastTaskSucceed() && stored == 7 ? null : "stored " + stored + " of 7 at " + chest.toShortString();
        });
    }

    private static Plan walkToFurnace(ServerLevel level, ServerPlayer player, CompanionEntity oleg, BlockPos base) {
        BlockPos furnace = spot(level, base, 60, 85);
        level.setBlockAndUpdate(furnace, Blocks.FURNACE.defaultBlockState());
        oleg.rememberPlaced(furnace);
        oleg.setItem(0, new ItemStack(Items.RAW_IRON, 2));
        oleg.setItem(1, new ItemStack(Items.COAL, 2));
        return new Plan(() -> order(player, "smelt_items", "minecraft:raw_iron", 2), now -> {
            int ingots = 0;
            for (int i = 0; i < now.getContainerSize(); i++) if (now.getItem(i).is(Items.IRON_INGOT)) ingots += now.getItem(i).getCount();
            return now.didLastTaskSucceed() && ingots == 2 ? null : "ingots " + ingots + " of 2 from " + furnace.toShortString();
        });
    }

    /** His own finished house with a closed door: the new chest goes inside and the door stays where it was. */
    private static Plan enterHouse(ServerLevel level, ServerPlayer player, CompanionEntity oleg, BlockPos base) {
        BlockPos spot = spot(level, base, 60, 95);
        BlockPos floor = spot.offset(-2, -1, -2);
        house(level, oleg, floor);
        BlockPos door = floor.offset(2, 1, 0);
        player.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        order(player, "set_home", "", 1);
        oleg.setItem(0, new ItemStack(Items.CHEST));
        oleg.setItem(1, new ItemStack(Items.COBBLESTONE, 12));
        return new Plan(() -> order(player, "store_items", "stone", 1), now -> {
            BlockPos at = null;
            for (BlockPos pos : BlockPos.betweenClosed(floor.offset(1, 1, 1), floor.offset(3, 1, 3))) if (level.getBlockState(pos).is(Blocks.CHEST)) at = pos.immutable();
            if (at == null) return "no chest inside the house at " + floor.toShortString();
            if (!(level.getBlockState(door).getBlock() instanceof DoorBlock)) return "the door is gone";
            return level.getBlockEntity(at) instanceof Container box && count(box, Items.COBBLESTONE) == 12 ? null : "stone not stored inside";
        });
    }

    /** Home on top of a pillar taller than the radius that counts as «home all the same»: the walk fails, he says so and works where he is. */
    private static Plan noRoute(ServerLevel level, ServerPlayer player, CompanionEntity oleg, BlockPos base) {
        BlockPos foot = spot(level, base, 45, 110);
        for (int y = 0; y < 24; y++) level.setBlockAndUpdate(foot.above(y), Blocks.OBSIDIAN.defaultBlockState());
        BlockPos top = foot.above(24);
        player.teleportTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
        order(player, "set_home", "", 1);
        oleg.setItem(0, new ItemStack(Items.CHEST));
        oleg.setItem(1, new ItemStack(Items.COBBLESTONE, 4));
        return new Plan(() -> order(player, "store_items", "stone", 1), now -> {
            if (!now.getLastTaskResult().contains("дойти не смог")) return "no reason named";
            return around(level, now.blockPosition(), 8, Blocks.CHEST) == 1 ? null : "not one chest beside him";
        });
    }

    /** Home past the trip's limit: no walk at all, the work is done on the spot and the reason named. */
    private static Plan farHome(ServerLevel level, ServerPlayer player, CompanionEntity oleg, BlockPos base) {
        BlockPos far = spot(level, base, 180, 240);
        player.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
        order(player, "set_home", "", 1);
        oleg.setItem(0, new ItemStack(Items.CHEST));
        oleg.setItem(1, new ItemStack(Items.COBBLESTONE, 4));
        return new Plan(() -> order(player, "store_items", "stone", 1), now -> {
            if (!now.getLastTaskResult().contains("слишком далеко")) return "no reason named";
            if (now.blockPosition().distManhattan(base) > 24) return "he left for the far home";
            return around(level, base, 12, Blocks.CHEST) == 1 ? null : "not one chest on the spot";
        });
    }

    private static void order(ServerPlayer player, String action, String item, int count) {
        var result = CompanionEvents.executeAction(player, action, item, count);
        if (!result.success()) throw new IllegalStateException(action + " refused: " + result.message());
    }

    private static void place(ServerLevel level, ServerPlayer player, BlockPos pos, Block block) {
        level.setBlockAndUpdate(pos, block.defaultBlockState());
        NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(level.dimension(), level, pos), Blocks.AIR.defaultBlockState(), player));
    }

    /** The first open patch of dry ground in the ring round the centre, nine blocks wide and flat to within one block, at about its height: a scene is about the trip, not a climb up a mountain. */
    private static BlockPos spot(ServerLevel level, BlockPos center, int from, int to) {
        for (int radius = from; radius <= to; radius += 6) for (int angle = 0; angle < 360; angle += 20) {
            int x = center.getX() + (int)(radius * Math.cos(Math.toRadians(angle))), z = center.getZ() + (int)(radius * Math.sin(Math.toRadians(angle)));
            if (flat(level, x, z) && Math.abs(surface(level, x, z) - center.getY()) <= MAX_RISE) return new BlockPos(x, surface(level, x, z), z);
            if (radius == 0) break;
        }
        throw new NoGround("No dry flat ground round " + center.toShortString() + " in " + from + ".." + to);
    }

    private static boolean flat(ServerLevel level, int x, int z) {
        int middle = surface(level, x, z);
        for (int dx = -4; dx <= 4; dx += 2) for (int dz = -4; dz <= 4; dz += 2) {
            int y = surface(level, x + dx, z + dz);
            var ground = level.getBlockState(new BlockPos(x + dx, y - 1, z + dz));
            if (Math.abs(y - middle) > 1 || !ground.getFluidState().isEmpty() || ground.is(BlockTags.LEAVES)) return false;
        }
        return true;
    }

    private static int surface(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    private static void house(ServerLevel level, CompanionEntity oleg, BlockPos floor) {
        for (int x = -1; x < 6; x++) for (int z = -2; z < 6; z++) for (int y = 1; y <= 5; y++)
            level.setBlockAndUpdate(floor.offset(x, y, z), Blocks.AIR.defaultBlockState());
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) {
            level.setBlockAndUpdate(floor.offset(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState());
            level.setBlockAndUpdate(floor.offset(x, 4, z), Blocks.OAK_SLAB.defaultBlockState());
            if (x == 0 || x == 4 || z == 0 || z == 4) for (int y = 1; y <= 3; y++) level.setBlockAndUpdate(floor.offset(x, y, z), Blocks.OAK_PLANKS.defaultBlockState());
        }
        var door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        level.setBlock(floor.offset(2, 1, 0), door, 3);
        level.setBlock(floor.offset(2, 2, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        level.setBlockAndUpdate(floor.offset(2, 0, -1), Blocks.OAK_PLANKS.defaultBlockState());
        CompoundTag saved = new CompoundTag();
        oleg.addAdditionalSaveData(saved);
        CompoundTag house = new CompoundTag();
        house.putLong("Base", floor.asLong());
        house.putString("Wood", "oak");
        house.putString("Dimension", level.dimension().location().toString());
        house.put("Placed", new LongArrayTag(new long[]{-1, -1, -1, -1, -1, -1, -1, -1}));
        saved.put("SmallHouse", house);
        oleg.readAdditionalSaveData(saved);
    }

    private static int around(ServerLevel level, BlockPos center, int radius, Block block) {
        int found = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius)))
            if (level.getBlockState(pos).is(block)) found++;
        return found;
    }

    private static int count(Container box, Item item) {
        int total = 0;
        for (int i = 0; i < box.getContainerSize(); i++) if (box.getItem(i).is(item)) total += box.getItem(i).getCount();
        return total;
    }
}

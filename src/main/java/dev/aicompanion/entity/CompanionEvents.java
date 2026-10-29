package dev.aicompanion.entity;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.aicompanion.AICompanionMod;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class CompanionEvents {
    private static final String CANONICAL_COMPANION = "aicompanion:canonical_companion";

    private CompanionEvents() { }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) resolveCanonical(player, true);
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof net.minecraft.world.entity.monster.Creeper creeper)
            CompanionHostiles.join(creeper);
        if (!(event.getEntity() instanceof CompanionEntity companion) || companion.getOwnerId() == null) return;
        MinecraftServer server = event.getLevel().getServer();
        if (server == null) return;
        ServerPlayer owner = server.getPlayerList().getPlayer(companion.getOwnerId());
        if (owner == null) return;
        var data = owner.getPersistentData();
        if (!data.hasUUID(CANONICAL_COMPANION)) {
            data.putUUID(CANONICAL_COMPANION, companion.getUUID());
        } else if (!data.getUUID(CANONICAL_COMPANION).equals(companion.getUUID())) {
            UUID canonicalId = data.getUUID(CANONICAL_COMPANION);
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(canonicalId) instanceof CompanionEntity canonical && canonical.isAlive()) {
                    canonical.mergeStoredItemsFrom(companion);
                    // Not added at all, instead of removing it in the middle of being added (review 22.10, М4).
                    event.setCanceled(true);
                    return;
                }
            }
        }
    }

    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("companion")
                .then(Commands.literal("summon").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    CompanionEntity companion = resolveCanonical(player, true);
                    Vec3 at = companion == null ? null : arrival(player);
                    if (at != null && companion.level() != player.level()) {
                        // Game 29.10: from the Nether he went to the same numbers in his own world and fell into a forest. He comes over
                        // as with a vanilla /tp; the work of the world he leaves stops there, as on «иди за мной».
                        companion.goals().playerOrder("follow"); companion.autonomy().playerOrder("follow");
                        CompanionAgenda.playerOrder(player, companion, "follow"); companion.setFollowing(true);
                        companion.teleportTo(player.serverLevel(), at.x, at.y, at.z, Set.of(), companion.getYRot(), companion.getXRot());
                    } else if (at != null) companion.teleportTo(at.x, at.y, at.z);
                    context.getSource().sendSuccess(() -> Component.literal(companion == null
                            ? "Компаньон сейчас выгружен из мира; он не создан повторно."
                            : at == null ? "Рядом с тобой ему негде встать: нужен твёрдый пол без воды и лавы." : "Компаньон рядом."), false);
                    return at == null ? 0 : 1;
                }))
                .then(Commands.literal("follow").executes(context -> setFollowing(context.getSource().getPlayerOrException(), true)))
                .then(Commands.literal("roam").executes(context -> {
                    var result = executeAction(context.getSource().getPlayerOrException(), "wander", "", 1);
                    context.getSource().sendSuccess(() -> Component.literal(result.message()), false);
                    return result.success() ? 1 : 0;
                }))
                .then(Commands.literal("stay").executes(context -> setFollowing(context.getSource().getPlayerOrException(), false)))
                .then(Commands.literal("guard").executes(context -> setProtecting(context.getSource().getPlayerOrException(), true)))
                .then(Commands.literal("unguard").executes(context -> setProtecting(context.getSource().getPlayerOrException(), false)))
                .then(Commands.literal("gatherwood").then(Commands.argument("count", IntegerArgumentType.integer(1, 16))
                        .executes(context -> gatherWood(context.getSource().getPlayerOrException(),
                                IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("gatherdirt").then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                        .executes(context -> gatherDirt(context.getSource().getPlayerOrException(),
                                IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("craft").then(Commands.argument("item_and_count", StringArgumentType.greedyString())
                        .executes(context -> craft(context.getSource().getPlayerOrException(),
                                StringArgumentType.getString(context, "item_and_count")))))
                .then(Commands.literal("place").then(Commands.argument("block", StringArgumentType.greedyString())
                        .executes(context -> place(context.getSource().getPlayerOrException(),
                                StringArgumentType.getString(context, "block")))))
                .then(Commands.literal("equip").then(Commands.argument("equipment", StringArgumentType.greedyString())
                        .executes(context -> equip(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "equipment")))))
                .then(Commands.literal("unequip").then(Commands.argument("slot", StringArgumentType.word())
                        .executes(context -> unequip(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "slot")))))
                .then(Commands.literal("givewood").executes(context -> giveWood(context.getSource().getPlayerOrException())))
                .then(Commands.literal("stop").executes(context -> stopGathering(context.getSource().getPlayerOrException()))));
    }

    /**
     * Where a summon puts him: beside the player where his body is free, out of any fluid and on something to stand on; the player's own
     * cell last; null if there is no such place. Review 29.10: by a bridge over lava or the void he would fall, and everything he carries
     * with him; in the game 29.10 a player standing in lava (creative) had him put into it, where he died.
     */
    private static Vec3 arrival(ServerPlayer player) {
        var level = player.serverLevel();
        for (int[] side : new int[][]{{1, 1}, {1, 0}, {0, 1}, {-1, 0}, {0, -1}, {-1, -1}, {1, -1}, {-1, 1}, {0, 0}}) {
            Vec3 at = player.position().add(side[0], 0, side[1]);
            AABB body = AICompanionMod.COMPANION.get().getDimensions().makeBoundingBox(at);
            if (level.noCollision(body) && !level.containsAnyLiquid(body) && !level.noCollision(new AABB(at.x - .3, at.y - .2, at.z - .3, at.x + .3, at.y, at.z + .3)))
                return at;
        }
        return null;
    }

    private static int setFollowing(ServerPlayer player, boolean following) {
        CompanionEntity companion = resolveCanonical(player, true);
        if (companion != null) {
            companion.goals().playerOrder(following ? "follow" : "wait"); companion.autonomy().playerOrder(following ? "follow" : "wait");
            CompanionAgenda.playerOrder(player, companion, following ? "follow" : "wait"); companion.setFollowing(following);
        }
        player.sendSystemMessage(Component.literal(companion == null ? "Компаньон пока не загружен в мир."
                : following ? "Компаньон следует за тобой." : "Компаньон ждёт здесь."));
        return companion == null ? 0 : 1;
    }

    private static int setProtecting(ServerPlayer player, boolean protecting) {
        CompanionEntity companion = resolveCanonical(player, true);
        if (companion != null) {
            companion.goals().playerOrder(protecting ? "protect" : "stop_protect"); companion.autonomy().playerOrder(protecting ? "protect" : "stop_protect");
            CompanionAgenda.playerOrder(player, companion, protecting ? "protect" : "stop_protect"); companion.setProtecting(protecting);
        }
        player.sendSystemMessage(Component.literal(companion == null ? "Компаньон пока не загружен в мир."
                : protecting ? "Компаньон будет защищать тебя от монстров."
                : "Компаньон прекращает защиту и снова следует за тобой."));
        return companion == null ? 0 : 1;
    }

    private static int gatherWood(ServerPlayer player, int count) {
        CompanionEntity companion = resolveCanonical(player, true);
        if (companion == null) {
            player.sendSystemMessage(Component.literal("Компаньон пока не загружен в мир."));
            return 0;
        }
        if (companion.isGatheringLogs()) {
            player.sendSystemMessage(Component.literal("Компаньон уже выполняет сбор: " + companion.getLastTaskResult()));
            return 1;
        }
        companion.startGatheringLogs(count);
        player.sendSystemMessage(Component.literal("Компаньон ищет дерево, цель — " + count + " брёвен."));
        return 1;
    }

    private static int gatherDirt(ServerPlayer player, int count) {
        CompanionEntity companion = resolveCanonical(player, true);
        if (companion == null) {
            player.sendSystemMessage(Component.literal("Компаньон пока не загружен в мир."));
            return 0;
        }
        if (companion.isGatheringTask()) {
            player.sendSystemMessage(Component.literal("Компаньон уже выполняет задачу: " + companion.getLastTaskResult()));
            return 1;
        }
        companion.startWork("gather_dirt", "", count);
        player.sendSystemMessage(Component.literal("Компаньон ищет открытый участок земли, цель — " + count + " блоков."));
        return 1;
    }

    private static int craft(ServerPlayer player, String itemAndCount) {
        int separator = itemAndCount.lastIndexOf(' ');
        if (separator <= 0 || separator == itemAndCount.length() - 1) {
            player.sendSystemMessage(Component.literal("Формат крафта: /companion craft <ID предмета> <количество>"));
            return 0;
        }
        String item = itemAndCount.substring(0, separator).trim();
        int count;
        try {
            count = Integer.parseInt(itemAndCount.substring(separator + 1).trim());
        } catch (NumberFormatException exception) {
            player.sendSystemMessage(Component.literal("Количество для крафта должно быть числом от 1 до 64."));
            return 0;
        }
        if (count < 1 || count > 64) {
            player.sendSystemMessage(Component.literal("Количество для крафта должно быть от 1 до 64."));
            return 0;
        }
        CompanionEntity companion = resolveCanonical(player, false);
        if (companion == null) {
            player.sendSystemMessage(Component.literal("Компаньон сейчас не загружен в мир."));
            return 0;
        }
        CompanionActionResult result = executeAction(player, "craft_item", item, count);
        player.sendSystemMessage(Component.literal(result.message()));
        return result.success() ? 1 : 0;
    }

    private static int place(ServerPlayer player, String block) {
        CompanionEntity companion = resolveCanonical(player, false);
        if (companion == null) {
            player.sendSystemMessage(Component.literal("Компаньон сейчас не загружен в мир."));
            return 0;
        }
        CompanionActionResult result = executeAction(player, "place_block", block, 1);
        player.sendSystemMessage(Component.literal(result.message()));
        return result.success() ? 1 : 0;
    }

    private static int giveWood(ServerPlayer player) {
        CompanionEntity companion = resolveCanonical(player, true);
        if (companion == null) {
            player.sendSystemMessage(Component.literal("Компаньон пока не загружен в мир."));
            return 0;
        }
        CompanionActionResult result = executeAction(player, "give_wood", "logs", 64);
        player.sendSystemMessage(Component.literal(result.message()));
        return result.success() ? 1 : 0;
    }

    private static int equip(ServerPlayer player, String arguments) {
        String[] parts = arguments.trim().split("\\s+");
        if (parts.length < 1 || parts.length > 2) return 0;
        CompanionActionResult result = executeAction(player, "equip_item", parts[0], 1, parts.length == 2 ? parts[1] : "auto");
        player.sendSystemMessage(Component.literal(result.message()));
        return result.success() ? 1 : 0;
    }

    private static int unequip(ServerPlayer player, String slot) {
        CompanionActionResult result = executeAction(player, "unequip_item", "", 1, slot);
        player.sendSystemMessage(Component.literal(result.message()));
        return result.success() ? 1 : 0;
    }

    private static int stopGathering(ServerPlayer player) {
        CompanionEntity companion = resolveCanonical(player, false);
        if (companion != null) {
            companion.goals().playerOrder("stop"); companion.autonomy().playerOrder("stop");
            CompanionAgenda.playerOrder(player, companion, "stop"); companion.stopGathering();
        }
        player.sendSystemMessage(Component.literal(companion == null ? "Компаньон сейчас не загружен в мир."
                : "Компаньон остановил текущую задачу."));
        return companion == null ? 0 : 1;
    }

    /** Called on the integrated server thread; the returned result reflects completed mutations. */
    public static CompanionActionResult executeAction(ServerPlayer player, String action, String item, int count) {
        return executeAction(player, action, item, count, "auto");
    }

    public static CompanionActionResult executeAction(ServerPlayer player, String action, String item, int count, String slot) {
        return executeAction(player, action, item, count, slot, dev.aicompanion.CompanionDiagnostics.newTrace());
    }

    public static CompanionActionResult executeAction(ServerPlayer player, String action, String item, int count, String slot, String trace) {
        return executeAction(player, action, item, count, slot, trace, "near_player");
    }

    public static CompanionActionResult executeAction(ServerPlayer player, String action, String item, int count, String slot, String trace, String location) {
        dev.aicompanion.CompanionDiagnostics.event(trace, "server_dispatch", "action", action, "item", item, "count", count, "slot", slot);
        CompanionEntity companion = resolveCanonical(player, true);
        if (companion == null || companion.level() != player.level()) {
            dev.aicompanion.CompanionDiagnostics.event(trace, "server_rejected", "reason", "companion_unloaded_or_other_dimension");
            return CompanionActionResult.failed("Компаньон не загружен рядом с тобой.");
        }
        // An order of the player comes first: a deed of his own ends here, before the order is taken (CompanionAutonomy).
        if (!autonomousDispatch && !goalDispatch) {
            // The collection of a parked smelt continues his own work: it ends a deed of his, but neither pauses a goal nor undoes a «стой».
            if (action.equals("collect_smelt")) companion.autonomy().dropDeed(action);
            else {
                // A long goal asked for while the pickaxe order runs is refused below; the asking must not pause that order.
                if (!action.equals("pursue_goal")) companion.goals().playerOrder(action);
                // Asking about, cancelling or deferring a goal leaves the player's earlier «стой здесь» / «следуй» in force.
                if (!action.equals("goal_status") && !action.equals("goal_cancel") && !action.equals("goal_defer")) companion.autonomy().playerOrder(action);
            }
            // The player's request comes before a long goal, the collection of his parked smelt too (the goal resumes by itself).
            CompanionAgenda.playerOrder(player, companion, action);
        }
        String previousTrace = companion.diagnosticTrace();
        boolean wasWorking = companion.isGatheringTask();
        companion.diagnosticBind(trace);
        companion.diagnosticSnapshot("action_before");
        CompanionActionResult dispatchResult = switch (action) {
            case "pursue_goal" -> CompanionAgenda.accept(player, companion, item, count);
            case "goal_pickaxes" -> CompanionAgenda.working(player)
                    ? CompanionActionResult.blocked("Сначала закончу долгую цель. Скажи «отмени цель», если кирки важнее.")
                    : companion.goals().accept(player);
            case "goal_resume" -> {
                CompanionActionResult resumed = CompanionAgenda.resume(player, companion);
                yield resumed != null ? resumed : companion.goals().resume();
            }
            case "goal_cancel" -> {
                CompanionActionResult cancelled = CompanionAgenda.cancel(player, companion);
                if (cancelled != null) yield cancelled;
                if (!companion.goals().outstanding()) yield CompanionActionResult.failed("Нет цели, которую можно отменить.");
                companion.goals().cancel();
                yield CompanionActionResult.ok("Отменил заказ на кирки.");
            }
            case "goal_defer" -> CompanionAgenda.defer(player, companion);
            case "goal_status" -> {
                CompanionActionResult status = CompanionAgenda.status(player, companion);
                yield status != null ? status : CompanionActionResult.ok(companion.goals().status());
            }
            case "craft_item" -> {
                CompanionActionResult repeat = count == 1 && !goalDispatch ? companion.alreadyMade(item) : null;
                if (repeat != null) yield repeat;
                if (CompanionWork.supportsCraft(item, count)) yield companion.startWork("prepare_craft", item, count);
                if (companion.benchWalkNeeded(item, count)) yield companion.startWork("bench_craft", item, count);
                yield companion.craftItemResult(item, count);
            }
            case "place_block" -> companion.startWork("place_block", item, count, location);
            case "gather_stone", "gather_dirt", "store_items", "take_items", "build_house" -> companion.startWork(action, item, count, location);
            case "hunt_mobs" -> {
                companion.setFollowing(false);
                yield companion.startWork(action, item, count);
            }
            case "equip_item" -> {
                if (companion.hasActiveWorkOrder()) companion.setFollowing(false);
                yield companion.equipItem(item, slot);
            }
            case "unequip_item" -> {
                if (companion.hasActiveWorkOrder()) companion.setFollowing(false);
                yield companion.unequipItem(slot);
            }
            case "follow" -> { companion.setFollowing(true); yield CompanionActionResult.ok("Следую за тобой."); }
            case "wait" -> { companion.setFollowing(false); yield CompanionActionResult.ok("Остановился и жду."); }
            case "wander" -> { companion.startRoaming(); yield CompanionActionResult.ok("Осмотрюсь вокруг."); }
            case "stop" -> { companion.stopGathering(); yield CompanionActionResult.ok("Ладно, остановился."); }
            case "come" -> {
                companion.setFollowing(false);
                yield companion.startWork("come", "", 1);
            }
            case "protect", "stop_protect" -> {
                companion.setProtecting("protect".equals(action));
                if ("protect".equals(action)) companion.selectWeaponForCombat();
                yield CompanionActionResult.ok("protect".equals(action) ? "Защищаю тебя." : "Закончил охрану.");
            }
            case "gather_wood" -> {
                yield companion.startWork("gather_wood", "", count);
            }
            // Handed over physically: walked to the player and tossed at their feet, never put into their inventory.
            case "give_wood" -> companion.startWork("give_items", "logs", 64);
            case "give_items" -> companion.startWork("give_items", item, count);
            case "smelt_items" -> companion.startWork("smelt_items", item, count);
            case "collect_smelt" -> companion.startWork("collect_smelt", item, 1);
            case "mine_ore" -> companion.startWork("mine_ore", item, count);
            case "hunt_animals" -> companion.startWork("hunt_animals", item, count);
            case "verify_item" -> companion.carriedCheck(item, count);
            // «Будь самостоятельным» / «хватит, просто будь рядом»: a local command of the player, never a step of the model's plan.
            case "autonomy" -> companion.autonomy().setEnabled("on".equals(item));
            default -> CompanionActionResult.failed("Неподдерживаемое действие: " + action);
        };
        dev.aicompanion.CompanionDiagnostics.event(trace, "server_acknowledgement", "action", action,
                "success", dispatchResult.success(), "status", dispatchResult.status(), "message", dispatchResult.message(), "workStillActive", companion.isGatheringTask(),
                "workId", companion.activeWorkId());
        companion.diagnosticSnapshot("action_after");
        // Equipment queries and rejected requests do not take ownership of an older running job.
        if (wasWorking && companion.hasActiveWorkOrder() && (!dispatchResult.success() || action.equals("equip_item") || action.equals("unequip_item")))
            companion.diagnosticBind(previousTrace);
        return dispatchResult;
    }

    /** True while a deed of his own is being given (not an order of the player: it does not end itself). Server thread only. */
    private static boolean autonomousDispatch;
    private static boolean goalDispatch;

    static CompanionActionResult executeGoal(ServerPlayer player, String action, String item, int count) {
        return executeGoal(player, action, item, count, "auto", dev.aicompanion.CompanionDiagnostics.newTrace(), "near_player");
    }

    /** A step of a goal of his (not an order of the player), with the slot, trace and place of the plan step it runs. */
    public static CompanionActionResult executeGoal(ServerPlayer player, String action, String item, int count, String slot, String trace, String location) {
        boolean outer = goalDispatch;
        goalDispatch = true;
        try { return executeAction(player, action, item, count, slot, trace, location); }
        finally { goalDispatch = outer; }
    }

    /** A deed of his own (CompanionAutonomy), given the same way as the player's order, with its own trace and workId. */
    static CompanionActionResult executeAutonomous(ServerPlayer player, String action, String item, int count) {
        autonomousDispatch = true;
        try {
            return executeAction(player, action, item, count, "auto", dev.aicompanion.CompanionDiagnostics.newTrace(), "near_player");
        } finally { autonomousDispatch = false; }
    }

    /** What dies by Oleg's hand drops for him to pick up (the hunt's meat, leather, wool, feathers). */
    public static void onLivingDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        if (!event.getEntity().level().isClientSide && event.getSource().getEntity() instanceof CompanionEntity companion) companion.claimDrops(event.getDrops());
    }

    public static CompanionEntity getOwnedCompanion(ServerPlayer player) {
        return resolveCanonical(player, false);
    }

    /** The full sweep for duplicates runs at least this often per player; between sweeps he is found by UUID. */
    private static final int SWEEP_TICKS = 100;
    private record Sweep(UUID canonical, long tick) { }
    /** The last full sweep per player and the companion it confirmed as the only one. */
    private static final java.util.Map<UUID, Sweep> LAST_SWEEP = new java.util.concurrent.ConcurrentHashMap<>();

    /** After death the companion comes back to the player this long later, like a player respawning. */
    public static final int RETURN_TICKS = 20 * 20;

    /**
     * Called as the companion dies: its UUID is never waited for again (review 22.10: a dead Oleg blocked a new one forever),
     * its memory is kept for the one who comes back, and the owner hears where the belongings lie.
     */
    static void companionDied(CompanionEntity companion, CompoundTag memory, String where, boolean dropped) {
        UUID ownerId = companion.getOwnerId();
        MinecraftServer server = companion.level().getServer();
        if (ownerId == null || server == null) return;
        CompanionMemorial.get(server).died(ownerId, companion.getUUID(), memory, server.overworld().getGameTime() + RETURN_TICKS);
        if (dropped) CompanionAgenda.died(server, ownerId, companion.level().dimension(), companion.blockPosition());
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner == null) return;
        var data = owner.getPersistentData();
        if (data.hasUUID(CANONICAL_COMPANION) && data.getUUID(CANONICAL_COMPANION).equals(companion.getUUID())) data.remove(CANONICAL_COMPANION);
        owner.sendSystemMessage(Component.literal(companion.getName().getString() + " погиб на " + where
                + (dropped ? ". Все его вещи выпали там и не пропадут — их можно забрать." : ".")
                + " Через " + RETURN_TICKS / 20 + " секунд он вернётся к тебе."));
    }

    /** Brings a fallen companion back to an online owner once the wait is over. */
    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        var memorial = CompanionMemorial.get(server);
        for (UUID ownerId : memorial.waitingOwners()) {
            ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
            if (owner != null && memorial.mayReturn(ownerId, server.overworld().getGameTime())) resolveCanonical(owner, true);
        }
    }

    /** The player's persistent UUID prevents replacements when the companion's chunk is unloaded. */
    private static CompanionEntity resolveCanonical(ServerPlayer player, boolean spawnIfUnregistered) {
        var data = player.getPersistentData();
        var memorial = CompanionMemorial.get(player.getServer());
        if (data.hasUUID(CANONICAL_COMPANION) && memorial.isDead(data.getUUID(CANONICAL_COMPANION))) data.remove(CANONICAL_COMPANION);
        UUID canonicalId = data.hasUUID(CANONICAL_COMPANION) ? data.getUUID(CANONICAL_COMPANION) : null;
        // Polling (every second while a plan runs) finds him by UUID; the full sweep with duplicate merging is kept for
        // orders, commands and login (review 22.10, М7: all entities of all worlds were walked every second).
        long now = player.getServer().getTickCount();
        Sweep swept = LAST_SWEEP.get(player.getUUID());
        if (canonicalId != null && !spawnIfUnregistered && swept != null && canonicalId.equals(swept.canonical()) && now - swept.tick() < SWEEP_TICKS && now >= swept.tick())
            for (ServerLevel level : player.getServer().getAllLevels())
                if (level.getEntity(canonicalId) instanceof CompanionEntity found && found.isAlive() && player.getUUID().equals(found.getOwnerId())) return found;
        List<CompanionEntity> owned = new ArrayList<>();
        for (ServerLevel level : player.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof CompanionEntity companion && companion.isAlive() && !memorial.isDead(companion.getUUID())
                        && player.getUUID().equals(companion.getOwnerId())) owned.add(companion);
            }
        }

        UUID registeredId = canonicalId;
        CompanionEntity canonical = registeredId == null ? null : owned.stream()
                .filter(entity -> registeredId.equals(entity.getUUID())).findFirst().orElse(null);
        if (canonicalId == null && !owned.isEmpty()) {
            canonical = owned.stream().min(Comparator
                    .comparing(CompanionEntity::isGatheringLogs).reversed()
                    .thenComparing(entity -> entity.level() != player.level())
                    .thenComparingDouble(player::distanceToSqr)).orElse(owned.getFirst());
            canonicalId = canonical.getUUID();
            data.putUUID(CANONICAL_COMPANION, canonicalId);
        }

        if (canonical != null) {
            for (CompanionEntity duplicate : owned) {
                if (duplicate == canonical) continue;
                canonical.mergeStoredItemsFrom(duplicate);
                duplicate.discard();
            }
            LAST_SWEEP.put(player.getUUID(), new Sweep(canonical.getUUID(), now));
            return canonical;
        }

        // The registered companion may be in an unloaded chunk; never create a replacement.
        if (canonicalId != null || !spawnIfUnregistered) return null;
        // A fallen companion comes back only after the wait, with his memory.
        if (memorial.awaitsReturn(player.getUUID()) && !memorial.mayReturn(player.getUUID(), player.getServer().overworld().getGameTime())) return null;
        boolean returning = memorial.awaitsReturn(player.getUUID());
        CompanionEntity companion = spawnFor(player, player.serverLevel());
        if (companion != null && returning) {
            companion.inheritMemory(memorial.returned(player.getUUID()));
            player.sendSystemMessage(Component.literal(companion.getName().getString() + " снова с тобой. Вещи, что были у него, лежат там, где он погиб."));
        }
        return companion;
    }

    private static CompanionEntity spawnFor(ServerPlayer player, ServerLevel level) {
        CompanionEntity companion = AICompanionMod.COMPANION.get().create(level);
        if (companion == null) return null;
        companion.setOwnerId(player.getUUID());
        companion.setPos(player.getX() + 1.0D, player.getY(), player.getZ() + 1.0D);
        player.getPersistentData().putUUID(CANONICAL_COMPANION, companion.getUUID());
        if (level.addFreshEntity(companion)) return companion;
        player.getPersistentData().remove(CANONICAL_COMPANION);
        return null;
    }
}

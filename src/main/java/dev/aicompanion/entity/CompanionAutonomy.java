package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * «Порядок и запас»: continuous, quiet work at the remembered workplace while the player is busy elsewhere.
 * Off unless the player turns it on. Simple rules in the mod pick a deed, never the model; each deed is an ordinary order through
 * CompanionEvents.executeAction with its own workId, and its success is that work's result. Any order of the player ends the deed
 * at once; after «стоп» or «стой» nothing starts by itself until the next order.
 */
public final class CompanionAutonomy {
    // ---- the rules' numbers, in one place (later in the settings) ----
    /** Pause between useful jobs. */
    public static final int IDLE_TICKS = 20 * 5;
    /** Targets stay this close horizontally to the remembered workplace. */
    public static final int LEASH = 24;
    /** A deed that did not work out is not taken up again for this long. */
    public static final int FAILURE_BREAK_TICKS = 20 * 60 * 5;
    /** A collection that could not even be dispatched is tried again after this long. */
    private static final int COLLECT_RETRY_TICKS = 20 * 30;
    /** Monsters this close to him or to the player: no deed, he keeps by the player. */
    public static final int THREAT_RANGE = 16;
    /** The player hurt or hitting something this recently is in a fight. */
    public static final int COMBAT_TICKS = 20 * 10;
    public static final int WOOD_STOCK = 16;
    public static final int STONE_STOCK = 32;
    /** Supplies for a long session: count the bag AND his own nearby chests, never the player's storage. */
    public static final int WOOD_RESERVE = 64 * 4;
    public static final int STONE_RESERVE = 64 * 16;
    public static final int WOOD_BATCH = 16;
    public static final int STONE_BATCH = 32;
    public static final int COAL_STOCK = 8, COAL_RESERVE = 64, IRON_RESERVE = 32, FOOD_RESERVE = 32;
    /**
     * Free bag slots at which the bag counts as almost full. Four, not fewer: the first chest of his own is crafted from logs on
     * the spot, and planks, bench and chest each want a slot on the way (with two free the chain did not fit).
     */
    public static final int BAG_FREE_SLOTS = 4;

    enum Deed {
        STORE("store_items", "сложить лишнее в сундук"),
        WOOD("gather_wood", "нарубить дров"),
        PICK("craft_item", "сделать кирку"),
        STONE("gather_stone", "добыть камня"),
        COOK("smelt_items", "пожарить мясо"),
        COAL("mine_ore", "добыть угля"), IRON("mine_ore", "добыть железа"),
        SMELT_IRON("smelt_items", "переплавить железо"), IRON_PICK("craft_item", "сделать железную кирку"),
        HUNT("hunt_animals", "добыть еду"),
        TAKE_WOOD("take_items", "взять свои дрова"), TAKE_STONE("take_items", "взять свой камень"),
        /** A batch left in its furnaces: started by the batches themselves, with deeds switched off too. */
        COLLECT("collect_smelt", "забрать плавку из печей");
        final String action, what;
        Deed(String action, String what) { this.action = action; this.what = what; }
    }

    private final CompanionEntity owner;
    private boolean enabled;
    /** «Стоп» or «стой» came: nothing is taken up by itself until the next order or call. */
    private boolean held;
    /** The hold came from «стоп» or «стой» itself, not from following, a call or a walk: such a hold keeps even a parked collect away. */
    private boolean halted;
    /** The player's own «стоп» or «стой»: unlike {@code halted} a «wait» step inside a plan does not lift it, so it keeps even orphans away until he is called. */
    private boolean stopped;
    private BlockPos workArea;
    private String workDimension = "";
    private long nextSpeech = Long.MIN_VALUE;
    private int idleTicks;
    private Deed deed;
    private long deedWorkId;
    /** True while the deed's order is being given, before its workId exists. */
    private boolean starting;
    private boolean woodNext;
    private final Map<Deed, Long> breakUntil = new EnumMap<>(Deed.class);
    private final Map<Deed, String> reportedFailures = new EnumMap<>(Deed.class);
    private final Map<CompanionOre.Kind, CompanionOre.Search> oreSearches = new EnumMap<>(CompanionOre.Kind.class);
    private final Map<CompanionOre.Kind, List<BlockPos>> knownOre = new EnumMap<>(CompanionOre.Kind.class);
    private final Map<CompanionOre.Kind, Long> nextOreScan = new EnumMap<>(CompanionOre.Kind.class);
    /** What he said on his own, newest last (for the journal and the GameTests). */
    private final List<String> said = new ArrayList<>();

    CompanionAutonomy(CompanionEntity owner) { this.owner = owner; }

    public boolean enabled() { return enabled; }
    public boolean held() { return held; }
    public boolean halted() { return held && halted; }
    public boolean playerStopped() { return held && stopped; }
    /** A deed of his own is running (or being started): searches keep to the workplace and his own places. */
    public boolean running() { return starting || deed != null; }
    public String deedName() { return deed == null ? "" : deed.what; }
    /** Explain only the next eligible deed. Conditions are checked again when it actually starts. */
    public String taskPreview() {
        if (!enabled) return "Самостоятельные дела выключены";
        if (held) return "На паузе по твоей команде";
        if (deed != null) return "Сейчас: " + deed.what;
        if (!(owner.level() instanceof ServerLevel level)) return "";
        var player = owner.getOwnerPlayer();
        if (player == null) return "Ожидает возвращения игрока";
        if (owner.goals().outstanding()) return "После сохранённой цели — проверить запасы и порядок";
        String why = notNow(level, player);
        if (why != null) return REASONS.getOrDefault(why, "Ожидает окончания текущей работы");
        Deed next = choose(level);
        return next == null ? "Запасы в порядке; следующего дела пока нет" : "Далее: " + next.what + " (если условия сохранятся)";
    }
    public List<String> said() { return List.copyOf(said); }
    /** Work stays where this session began, even between jobs and when the owner leaves. */
    public boolean staysAtWork() { return enabled && !held; }
    public BlockPos workArea() { return workArea; }
    /** A player's temporary request still works around the saved workplace and must keep its exit. */
    boolean protectsReturn() { return enabled && workArea != null && here() && inArea(owner.blockPosition()); }
    BlockPos center() { return workArea == null ? owner.blockPosition() : workArea; }
    private void rememberArea() {
        if (workArea != null && (!here() || workArea.distSqr(owner.blockPosition()) > LEASH * LEASH)) reportedFailures.clear();
        workArea = owner.blockPosition().immutable();
        workDimension = owner.level().dimension().location().toString();
        oreSearches.clear(); knownOre.clear(); nextOreScan.clear();
    }
    private boolean here() { return workDimension.equals(owner.level().dimension().location().toString()); }

    /** «Будь самостоятельным» / «хватит, просто будь рядом», or the switch in «Поведение». */
    public CompanionActionResult setEnabled(boolean on) {
        boolean was = enabled;
        enabled = on;
        owner.syncAutonomy();
        if (on) {
            // Resume a paused workplace in the same area; a pit floor reached during an order is not a new surface.
            if (!was || workArea == null || !here() || held && !inArea(owner.blockPosition())) rememberArea();
            held = false;
            // Asked in words to find something to do: the first deed comes in a few seconds, not after a minute of waiting, and a
            // repeated «займись» does not start the wait anew (game 23.10: asked twice, he only ran about).
            idleTicks = Math.max(was ? idleTicks : 0, IDLE_TICKS - FIRST_DEED_TICKS);
            explain = true;
            // Leave roaming/waiting so the work can begin, without following the player out of this area.
            if (!owner.isProtecting() && !owner.hasActiveWorkOrder() && (owner.isRoaming() || !owner.isFollowing())) owner.endAutonomousDeed();
            event("autonomy_enabled", "again", was, "workArea", center(), "dimension", workDimension);
            var player = owner.getOwnerPlayer();
            String blocked = was && deed == null && player != null && owner.level() instanceof ServerLevel level ? danger(level, player) : null;
            return CompanionActionResult.ok(blocked == null ? "Понял, ищу полезное дело здесь. Если понадоблюсь — позови." : REASONS.get(blocked));
        }
        idleTicks = 0;
        boolean dropped = deed != null;
        if (dropped) drop("autonomy_disabled");
        event("autonomy_disabled");
        return CompanionActionResult.ok(dropped ? "Понял, бросаю своё дело и просто буду рядом." : "Понял, просто буду рядом.");
    }

    /**
     * Any order of the player comes first: a deed of his own ends at once (his harvest is still picked up). «Стоп» and «стой»
     * also keep him from taking up anything by himself until the next order.
     */
    void playerOrder(String action) {
        idleTicks = 0;
        if ("verify_item".equals(action) || "autonomy".equals(action)) return;
        if (deed != null) drop("player_order:" + action);
        boolean wasHeld = held;
        held = switch (action) {
            case "stop", "wait", "follow", "come", "protect", "wander" -> true;
            default -> false;
        };
        halted = stopped = held && ("stop".equals(action) || "wait".equals(action));
        if (held != wasHeld) { owner.syncAutonomy(); event(held ? "autonomy_held" : "autonomy_released", "action", action); }
    }

    /** A «wait» inside a plan is a pause, not the player's «стой»: he stays where he is, but nothing waits for a call to move him again. */
    public void clearHalt(boolean playerStopped) { halted = false; stopped = playerStopped; }

    /** A running deed ends for work that is no new order of the player: his hold stays as it is. */
    void dropDeed(String action) {
        idleTicks = 0;
        if (deed != null) drop("player_order:" + action);
    }

    /** Once a second on the server. */
    void tick() {
        if (owner.tickCount % 20 != 0 || !(owner.level() instanceof ServerLevel level)) return;
        if (owner.goals().outstanding()) return;
        if (deed != null) { watchDeed(level); return; }
        if (!enabled || held) { idleTicks = 0; return; }
        // Old saves had only the mode flag. Adopt the existing location once, without moving him.
        if (workArea == null) rememberArea();
        scanOre(level);
        ServerPlayer player = owner.getOwnerPlayer();
        String why = player == null ? "no_player" : notNow(level, player);
        if (why != null) {
            // Just asked to find something to do: why not now, once, in words (not for a moment's busyness).
            String reason = explain ? REASONS.get(why) : null;
            if (reason != null) { explain = false; say(reason); }
            if (!explain || reason != null) idleTicks = 0;
            return;
        }
        // His own drops still lying near: the pickup comes first (the ordinary pickup of his harvest, no order of its own).
        if (owner.reclaimOwnDrops(center(), LEASH) > 0) { idleTicks = Math.min(idleTicks, IDLE_TICKS - FIRST_DEED_TICKS); return; }
        idleTicks += 20;
        if (idleTicks < IDLE_TICKS) return;
        // His own cooked batch comes before anything new: the rule of a plan's smelt holds for his deeds too.
        if (owner.batches().ownReadyWaiting(level)) return;
        idleTicks = 0;
        Deed next = choose(level);
        if (next == null && explain) say(nothingToDo());
        explain = false;
        if (next != null) begin(player, next);
    }

    /** After «займись чем-нибудь»: the first deed this soon, if nothing keeps him from it. */
    public static final int FIRST_DEED_TICKS = 20 * 5;
    /** Asked to find something to do and not yet answered by a deed or a reason. */
    private boolean explain;
    private static final Map<String, String> REASONS = Map.of(
            "not_following", "Пока охраняю тебя. Скажи «прекрати защищать» — займусь делом.",
            "monsters", "Рядом монстры — сначала прикрою тебя, делом займусь потом.",
            "player_fighting", "Ты в бою — сначала прикрою тебя, делом займусь потом.");

    private String nothingToDo() {
        List<String> notes = new ArrayList<>();
        long now = owner.level().getGameTime();
        for (Deed candidate : Deed.values()) if (breakUntil.getOrDefault(candidate, Long.MIN_VALUE) > now) notes.add(candidate.what);
        if (!hasPickaxe() && stone() < STONE_STOCK) notes.add("камень без кирки не добыть");
        return "Сейчас доступные дела закончились: с собой и в моих сундуках брёвен " + reserve(ItemTags.LOGS)
                + ", камня " + reserve(ItemTags.STONE_TOOL_MATERIALS)
                + (notes.isEmpty() ? "" : " (пока не берусь: " + String.join(", ", notes) + ")") + ". Пока останусь здесь.";
    }

    /** Why he does not take up a deed now (null: he may). Safe work continues at night; actual threats take priority. */
    private String notNow(ServerLevel level, ServerPlayer player) {
        if (player.isSpectator() || !player.isAlive() || player.level() != owner.level()) return "player_unavailable";
        if (!here()) return "different_work_dimension";
        if (!owner.isFollowing() || owner.isProtecting() || owner.isRoaming()) return "not_following";
        String danger = danger(level, player);
        if (danger != null) return danger;
        if (owner.hasActiveWorkOrder() || owner.hasPendingLoot() || owner.isSelfRescuing() || owner.getTarget() != null || !owner.canCollectItems()) return "busy";
        return null;
    }

    private String danger(ServerLevel level, ServerPlayer player) {
        boolean nearby = owner.distanceToSqr(player) <= THREAT_RANGE * THREAT_RANGE;
        if (nearby && (player.getLastHurtByMob() != null && player.tickCount - player.getLastHurtByMobTimestamp() < COMBAT_TICKS
                || player.getLastHurtMob() != null && player.tickCount - player.getLastHurtMobTimestamp() < COMBAT_TICKS)) return "player_fighting";
        for (LivingEntity around : nearby ? List.of(player, owner) : List.of(owner))
            if (!level.getEntitiesOfClass(LivingEntity.class, around.getBoundingBox().inflate(THREAT_RANGE),
                    mob -> mob instanceof Enemy && mob.isAlive() && mob.distanceToSqr(around) <= THREAT_RANGE * THREAT_RANGE
                            && around.hasLineOfSight(mob) && (mob.distanceToSqr(around) <= 7 * 7
                            || mob instanceof net.minecraft.world.entity.Mob hostile && (hostile.getTarget() == owner || hostile.getTarget() == player))).isEmpty()) return "monsters";
        return null;
    }

    /** A session of small jobs, with tools and deliveries between batches. No unbounded gathering to fill every chest. */
    private Deed choose(ServerLevel level) {
        long now = level.getGameTime();
        boolean wood = reserve(ItemTags.LOGS) < WOOD_RESERVE && owner.hasRoomForLogs() && available(Deed.WOOD, now);
        boolean stone = reserve(ItemTags.STONE_TOOL_MATERIALS) < STONE_RESERVE && freeSlots() > 0 && available(Deed.STONE, now);
        boolean store = available(Deed.STORE, now) && CompanionWork.surplusCount(owner) > 0;
        if (store && freeSlots() <= BAG_FREE_SLOTS) return Deed.STORE;
        // An explicit delivery can empty the working bag. Use his own stored supplies before looking for another tree.
        if (logs() < 4 && reserve(ItemTags.LOGS) > logs() && available(Deed.TAKE_WOOD, now)) return Deed.TAKE_WOOD;
        if (stone() < 8 && reserve(ItemTags.STONE_TOOL_MATERIALS) > stone() && available(Deed.TAKE_STONE, now)) return Deed.TAKE_STONE;
        if (wood && logs() < WOOD_STOCK) return Deed.WOOD;
        boolean coal = available(Deed.COAL, now) && reserve(stack -> stack.is(ItemTags.COALS)) < COAL_RESERVE && oreNear(level, CompanionOre.Kind.COAL);
        boolean iron = available(Deed.IRON, now) && reserve(stack -> stack.is(Items.RAW_IRON) || stack.is(Items.IRON_INGOT)) < IRON_RESERVE && oreNear(level, CompanionOre.Kind.IRON);
        if (!hasPickaxe() && (stone || coal || iron) && available(Deed.PICK, now)) return Deed.PICK;
        if (available(Deed.COOK, now) && rawMeat() > 0 && !owner.batches().smelting(level, CompanionAutonomy::isMeat)
                && (stone() >= CompanionWork.stoneCost("minecraft:furnace") || carries(Items.FURNACE) || ownFurnaceNear(level, center()))) return Deed.COOK;
        if (coal && hasPickaxe() && CompanionTools.count(owner, ItemTags.COALS) < COAL_STOCK) return Deed.COAL;
        if (available(Deed.SMELT_IRON, now) && count(Items.RAW_IRON) > 0 && !owner.batches().smelting(level, item -> item == Items.RAW_IRON)
                && (stone() >= 8 || carries(Items.FURNACE) || ownFurnaceNear(level, center()))) return Deed.SMELT_IRON;
        if (available(Deed.IRON_PICK, now) && count(Items.IRON_INGOT) >= 3 && !hasIronPickaxe()) return Deed.IRON_PICK;
        if (available(Deed.HUNT, now) && reserve(stack -> stack.is(ItemTags.MEAT)) < FOOD_RESERVE && animalsNear(level)) return Deed.HUNT;
        if (iron && hasPickaxe()) return Deed.IRON;
        if (coal && hasPickaxe()) return Deed.COAL;
        if (stone && hasPickaxe() && stone() < STONE_STOCK) return Deed.STONE;
        // Put each batch away, keeping working supplies. A nearly empty bag must not mean «nothing left to do».
        if (store && (logs() >= WOOD_STOCK + WOOD_BATCH || stone() >= STONE_STOCK + STONE_BATCH
                || CompanionTools.count(owner, ItemTags.COALS) >= 16 || count(Items.IRON_INGOT) >= 16 || !wood && !stone)) return Deed.STORE;
        if (wood && (woodNext || !stone || !hasPickaxe())) return Deed.WOOD;
        if (stone && hasPickaxe()) return Deed.STONE;
        if (wood) return Deed.WOOD;
        return null;
    }

    private boolean available(Deed deed, long now) { return breakUntil.getOrDefault(deed, Long.MIN_VALUE) <= now; }

    private int reserve(net.minecraft.tags.TagKey<net.minecraft.world.item.Item> kind) {
        return reserve(stack -> stack.is(kind));
    }
    private int reserve(java.util.function.Predicate<ItemStack> matches) {
        int count = carried().stream().filter(matches).mapToInt(ItemStack::getCount).sum();
        for (BlockPos pos : owner.placedByMe("minecraft:chest")) {
            if (Math.hypot(pos.getX() - center().getX(), pos.getZ() - center().getZ()) > LEASH
                    || !owner.level().hasChunkAt(pos) || !(owner.level().getBlockEntity(pos) instanceof ChestBlockEntity chest)) continue;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) if (matches.test(chest.getItem(slot))) count += chest.getItem(slot).getCount();
        }
        return count;
    }

    /** Loaded, bounded section search. Empty ore searches do not become orders or repeated path failures. */
    private void scanOre(ServerLevel level) {
        if (!enabled || held || !here()) return;
        for (var kind : List.of(CompanionOre.Kind.COAL, CompanionOre.Kind.IRON)) {
            var search = oreSearches.get(kind);
            if (search == null && level.getGameTime() >= nextOreScan.getOrDefault(kind, Long.MIN_VALUE)) {
                search = new CompanionOre.Search(level, center(), LEASH, kind); oreSearches.put(kind, search);
            }
            if (search != null && search.scan(level)) {
                knownOre.put(kind, search.results(level)); oreSearches.remove(kind);
                nextOreScan.put(kind, level.getGameTime() + 20 * 30);
            }
        }
    }
    private boolean oreNear(ServerLevel level, CompanionOre.Kind kind) {
        return knownOre.getOrDefault(kind, List.of()).stream().anyMatch(pos -> inArea(pos) && pos.getY() >= center().getY() - CompanionOre.MAX_DROP
                && level.hasChunkAt(pos) && kind.is(level.getBlockState(pos)));
    }
    private static boolean isMeat(net.minecraft.world.item.Item item) { return new ItemStack(item).is(ItemTags.MEAT); }
    private boolean animalsNear(ServerLevel level) {
        return !level.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class, new net.minecraft.world.phys.AABB(center()).inflate(LEASH,12,LEASH),
                animal -> CompanionGame.Kind.of(animal) != null && inArea(animal.blockPosition())
                        && CompanionGame.huntable(animal, CompanionGame.Kind.of(animal))).isEmpty();
    }
    private int count(net.minecraft.world.item.Item item) { return carried().stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum(); }
    private boolean inArea(BlockPos pos) { return Math.hypot(pos.getX() - center().getX(), pos.getZ() - center().getZ()) <= LEASH; }
    private boolean hasIronPickaxe() { return carried().stream().anyMatch(stack -> stack.is(ItemTags.PICKAXES) && stack.isCorrectToolForDrops(Blocks.GOLD_ORE.defaultBlockState())); }

    /**
     * Takes up a batch nobody waits for as a deed of his own, even with deeds switched off: it is his smelt, left in the furnaces.
     * «Стоп» keeps him standing; a following, a call or a walk does not. False: nothing was tried.
     */
    boolean collectOrphan(long batch) {
        long now = owner.level().getGameTime();
        if (deed != null || starting || playerStopped() || !available(Deed.COLLECT, now) || !(owner.level() instanceof ServerLevel level)) return false;
        ServerPlayer player = owner.getOwnerPlayer();
        if (player == null || !player.isAlive() || player.isSpectator() || !owner.isFollowing() || owner.isProtecting() || owner.isRoaming()) return false;
        if (owner.hasActiveWorkOrder() || owner.goals().outstanding() || owner.hasPendingLoot() || owner.isSelfRescuing() || owner.getTarget() != null || !owner.canCollectItems()
                || danger(level, player) != null) return false;
        if (owner.batches().collectRefusal(String.valueOf(batch)) != null) { owner.batches().collectRefused(batch); return true; }
        begin(player, Deed.COLLECT, String.valueOf(batch));
        // Refused at dispatch for a reason that is not the batch's (the call is not heard right now): try again later, count nothing, say nothing.
        if (deed == null) breakUntil.put(Deed.COLLECT, now + COLLECT_RETRY_TICKS);
        return true;
    }

    private void begin(ServerPlayer player, Deed next) { begin(player, next, null); }

    private void begin(ServerPlayer player, Deed next, String forcedItem) {
        int count = switch (next) {
            case STORE, COLLECT -> 1;
            case PICK -> 1;
            case IRON_PICK -> 1;
            case TAKE_WOOD -> 8;
            case TAKE_STONE -> 16;
            case COAL -> 8;
            case IRON -> 4;
            case HUNT -> 6;
            case WOOD -> Math.max(1, Math.min(WOOD_BATCH, WOOD_RESERVE - reserve(ItemTags.LOGS)));
            case STONE -> Math.max(1, Math.min(STONE_BATCH, STONE_RESERVE - reserve(ItemTags.STONE_TOOL_MATERIALS)));
            case COOK, SMELT_IRON -> CompanionWork.ALL_CARRIED;
        };
        String item = forcedItem != null ? forcedItem : switch (next) {
            case STORE -> CompanionWork.SURPLUS;
            case PICK -> stone() >= 3 ? "minecraft:stone_pickaxe" : "minecraft:wooden_pickaxe";
            case IRON_PICK -> "minecraft:iron_pickaxe";
            case TAKE_WOOD -> "logs";
            case TAKE_STONE -> "stone";
            case COAL -> "minecraft:coal";
            case IRON -> "minecraft:iron";
            case SMELT_IRON -> "minecraft:raw_iron";
            case HUNT -> "food";
            case COOK -> CompanionGame.ANY_MEAT;
            default -> "";
        };
        starting = true;
        CompanionActionResult result;
        try {
            result = CompanionEvents.executeAutonomous(player, next.action, item, count);
        } finally { starting = false; }
        long id = owner.activeWorkId();
        event("autonomy_deed_started", "deed", next.action, "item", item, "count", count, "accepted", result.success(), "workId", id, "message", result.message());
        if (!result.success() || id == 0) { if (next != Deed.COLLECT) finished(next, result); return; }
        deed = next; deedWorkId = id;
        if (next == Deed.COOK || next == Deed.SMELT_IRON) owner.markDeedParkable(id);
    }

    /** Observe this workId. Distance to the owner never cancels work. */
    private void watchDeed(ServerLevel level) {
        ServerPlayer player = owner.getOwnerPlayer();
        boolean own = deed == Deed.COLLECT;
        if (player == null || player.level() != owner.level() || !own && !here()) {
            drop("player_unavailable");
            return;
        }
        if (!enabled && !own) { drop("disabled"); return; }
        String danger = danger(level, player);
        if (danger != null) { drop(danger); say(REASONS.get(danger)); return; }
        CompanionActionResult result = owner.workResult(deedWorkId);
        if (result == null) return;
        Deed was = deed;
        deed = null; deedWorkId = 0;
        finished(was, result);
    }

    private void finished(Deed was, CompanionActionResult result) {
        idleTicks = IDLE_TICKS - FIRST_DEED_TICKS;
        // Only an explicit request asks for an explanation. Automatic retries must not restart the monologue.
        explain = false;
        if (was == Deed.WOOD) woodNext = false;
        if (was == Deed.STONE) woodNext = true;
        owner.syncAutonomy();
        event("autonomy_deed_finished", "deed", was.action, "success", result.success(), "status", result.status(), "message", result.message());
        if (result.status() == CompanionActionResult.Status.CANCELLED) return;
        if (was == Deed.COLLECT) { report(result.message()); return; }
        if (result.status() == CompanionActionResult.Status.PARKED) { reportedFailures.remove(was); report(result.message()); return; }
        if (result.success()) { reportedFailures.remove(was); return; }
        breakUntil.put(was, owner.level().getGameTime() + FAILURE_BREAK_TICKS);
        String issue = dev.aicompanion.CompanionFailureMessages.kind(result.message()).name();
        String previous = reportedFailures.get(was);
        if (issue.equals(previous) || "*".equals(previous)) return;
        if (say(dev.aicompanion.CompanionFailureMessages.explain(was.action, result.message()))) reportedFailures.put(was, issue);
    }

    /** Ends the deed; his own harvest round him is still picked up, nothing is dropped or lost. */
    private void drop(String reason) {
        Deed was = deed;
        event("autonomy_deed_dropped", "deed", was == null ? "" : was.action, "reason", reason);
        // Still his deed while it is cancelled: the journal says «сам, без просьбы».
        owner.endAutonomousDeed();
        deed = null; deedWorkId = 0; idleTicks = 0;
        owner.syncAutonomy();
    }

    boolean say(String text) {
        long now = owner.level().getGameTime();
        if (now < nextSpeech) return false;
        nextSpeech = now + 20 * 60 * 3;
        report(text);
        return true;
    }

    /** What he tells on his own account, outside the pause between remarks: the outcome of a batch he went for. */
    void report(String text) {
        said.add(text);
        while (said.size() > 16) said.removeFirst();
        event("autonomy_said", "text", text);
        owner.autonomySay(text);
    }

    private void event(String name, Object... fields) {
        Object[] all = new Object[fields.length + 2];
        all[0] = "entity"; all[1] = owner.getUUID();
        System.arraycopy(fields, 0, all, 2, fields.length);
        dev.aicompanion.CompanionDiagnostics.event(owner.diagnosticTrace(), name, all);
    }

    // ---- what he carries ----
    private int freeSlots() {
        int free = 0;
        for (int slot = 0; slot < owner.getContainerSize(); slot++) if (owner.getItem(slot).isEmpty()) free++;
        return free;
    }
    private int logs() { return CompanionTools.count(owner, ItemTags.LOGS); }
    private int stone() { return CompanionTools.count(owner, ItemTags.STONE_TOOL_MATERIALS); }
    private int rawMeat() {
        int count = 0;
        for (ItemStack stack : carried()) if (stack.is(ItemTags.MEAT) && owner.level() instanceof ServerLevel level && CompanionSmelting.recipeFor(level, stack) != null) count += stack.getCount();
        return count;
    }
    private boolean hasPickaxe() {
        for (ItemStack stack : carried()) if (stack.is(ItemTags.PICKAXES) && stack.isCorrectToolForDrops(Blocks.STONE.defaultBlockState())) return true;
        return false;
    }
    private boolean carries(net.minecraft.world.item.Item item) {
        for (ItemStack stack : carried()) if (stack.is(item)) return true;
        return false;
    }
    private List<ItemStack> carried() {
        List<ItemStack> items = new ArrayList<>();
        items.add(owner.getMainHandItem()); items.add(owner.getOffhandItem());
        for (int slot = 0; slot < owner.getContainerSize(); slot++) items.add(owner.getItem(slot));
        return items;
    }
    /** A furnace he set down himself, near the player and free for his meat. */
    private boolean ownFurnaceNear(ServerLevel level, BlockPos player) {
        for (BlockPos pos : owner.placedByMe("minecraft:furnace"))
            if (pos.distSqr(player) <= LEASH * LEASH && level.hasChunkAt(pos)
                    && level.getBlockEntity(pos) instanceof FurnaceBlockEntity oven && oven.getItem(0).isEmpty()) return true;
        return false;
    }

    // ---- saved with him; an older world has none of it: off ----
    void save(CompoundTag tag) {
        tag.putBoolean("Autonomy", enabled);
        tag.putBoolean("AutonomyHeld", held);
        tag.putBoolean("AutonomyHalted", halted);
        tag.putBoolean("AutonomyStopped", stopped);
        if (workArea != null) {
            tag.putLong("AutonomyArea", workArea.asLong());
            tag.putString("AutonomyDimension", workDimension);
        }
        CompoundTag backoff = new CompoundTag();
        long now = owner.level().getGameTime();
        for (Deed candidate : Deed.values()) {
            long remaining = breakUntil.getOrDefault(candidate, now) - now;
            if (remaining > 0) backoff.putInt(candidate.name(), (int)Math.min(FAILURE_BREAK_TICKS, remaining));
        }
        tag.put("AutonomyBackoff", backoff);
        CompoundTag reported = new CompoundTag();
        reportedFailures.forEach((candidate, issue) -> reported.putString(candidate.name(), issue));
        tag.put("AutonomyReportedFailures", reported);
        tag.putInt("AutonomySpeechDelay", (int)Math.clamp(nextSpeech <= now ? 0 : nextSpeech - now, 0, 20 * 60 * 3));
    }
    void load(CompoundTag tag) {
        enabled = tag.getBoolean("Autonomy");
        held = tag.getBoolean("AutonomyHeld");
        halted = tag.getBoolean("AutonomyHalted");
        stopped = tag.contains("AutonomyStopped") ? tag.getBoolean("AutonomyStopped") : halted;
        workArea = tag.contains("AutonomyArea") ? BlockPos.of(tag.getLong("AutonomyArea")) : null;
        workDimension = tag.getString("AutonomyDimension");
        nextSpeech = owner.level().getGameTime() + Math.clamp(tag.getInt("AutonomySpeechDelay"), 0, 20 * 60 * 3);
        deed = null; deedWorkId = 0; idleTicks = 0; starting = false;
        breakUntil.clear(); reportedFailures.clear(); oreSearches.clear(); knownOre.clear(); nextOreScan.clear();
        CompoundTag backoff = tag.getCompound("AutonomyBackoff");
        CompoundTag reported = tag.getCompound("AutonomyReportedFailures");
        for (Deed candidate : Deed.values()) {
            int remaining = Math.clamp(backoff.getInt(candidate.name()), 0, FAILURE_BREAK_TICKS);
            if (remaining > 0) breakUntil.put(candidate, owner.level().getGameTime() + remaining);
            String issue = reported.getString(candidate.name());
            if (issue.equals("*") || java.util.Arrays.stream(dev.aicompanion.CompanionFailureMessages.Kind.values()).anyMatch(kind -> kind.name().equals(issue)))
                reportedFailures.put(candidate, issue);
            else if (remaining > 0 && !tag.contains("AutonomyReportedFailures")) reportedFailures.put(candidate, "*");
        }
        owner.syncAutonomy();
    }
}

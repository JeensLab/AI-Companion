package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Bounded server-side work. Prerequisites use real recipes, tools, blocks and inventories. */
public final class CompanionWork {
    enum Job { STONE, CRAFT, STORE, TAKE, PLACE_CHEST, HOUSE, DIRT, COME, HUNT, WOOD, GIVE, SMELT, ORE, GAME }
    enum Phase { CRAFT, PICKAXE, WOOD, MINE, STORE, CLOSE, BUILD, SHOVEL, DIGGING, RETURN, FIGHT, WOOD_DONE, EXIT, PACK, HAND, FURNACE, COOK, ORE_PICK, ORE, IRON, CHASE }
    final CompanionEntity owner;
    final CompanionWorkItems items = new CompanionWorkItems(this);
    final CompanionHunting hunting = new CompanionHunting(this);
    final CompanionWorkCrafting crafting = new CompanionWorkCrafting(this);
    final CompanionStoneMining stoneMining = new CompanionStoneMining(this);
    final CompanionOreMining oreMining = new CompanionOreMining(this);
    final CompanionRoute route = new CompanionRoute(this);
    final CompanionChests chests = new CompanionChests(this);
    final CompanionFurnaces furnaces = new CompanionFurnaces(this);
    final CompanionHandOver handOver = new CompanionHandOver(this);
    final CompanionHouse house;
    final CompanionBlockBreaking breaking;
    final CompanionToolSense pickaxeSense, shovelSense;
    CompanionStoneSearch stoneSearch;
    final java.util.Deque<BlockPos> quarrySoil = new java.util.ArrayDeque<>();
    final Set<BlockPos> quarrySupports = new HashSet<>();
    int recoveryReplans;
    BlockPos blockedStair;
    long blockedStairSince;
    int blockedStairReplans;
    boolean quarryTried;
    /** Top of the own staircase: the way back out once the stone is collected. Null while mining on the surface. */
    BlockPos exitCell;
    int stairs;
    static final int MAX_STAIRS = 3;
    static final int MAX_MINE_DEPTH = 14;
    boolean selectingStone;
    Job job;
    Phase phase;
    Phase afterWood;
    String requestedItem = "";
    int requestedCount;
    int wantedStone;
    int gathered;
    int stoneCollectedBefore;
    BlockPos lastHarvestStand;
    int woodAttempts;
    int woodBeforeRequest;
    int craftAttempts;
    int transferred;
    int newChests;
    int cooldown;
    long deadline;
    long lastProgressTick;
    double bestDistance;
    net.minecraft.world.phys.Vec3 approachMoved;
    long approachUntil;
    BlockPos target;
    Path path;
    Set<BlockPos> standCells = Set.of();
    List<BlockPos> stoneCandidates;
    /** The last stone checked for being a step of his way back up (see mine). */
    BlockPos stepChecked;
    final Set<BlockPos> rejected = new HashSet<>();
    final Set<BlockPos> triedChests = new HashSet<>();
    BlockPos rememberedChest;
    BlockPos packTarget;
    CompanionActionResult packResult;
    int packedBenchBefore;
    BlockPos packedBenchFrom;
    BlockPos openedChest;
    BlockPos craftingStation;
    /** The remembered bench turned out unreachable on foot: this job sets down its own from the bag instead. */
    boolean benchHere;
    static final int NEAR_BENCH = 8;
    static final int BENCH_TRIP = 20;
    BlockPos placementAnchor;
    boolean placementMoving;
    boolean insideHouse;
    final List<BlockPos> placedChests = new ArrayList<>();
    BlockPos workOrigin;
    boolean returnRouteBlocked;
    long nextReturnCheck;
    long returnBlockedSince;
    int returnMisses;
    final Set<java.util.UUID> huntTargets = new HashSet<>();
    final Set<java.util.UUID> huntDead = new HashSet<>();
    int huntKills;
    net.minecraft.world.phys.Vec3 orderProgressPosition;
    private BlockPos comeFailedAt;
    private long comeFailedTime;
    long orderProgressTime;
    /**
     * Watchdog for every work (game log 21.10: two minutes on one spot with «Проверяю подход к камню», then a faceless
     * timeout). Counted in work ticks, so paused work, wood gathering and an open bag do not age it.
     */
    public static final int STALL_TICKS = 20 * 90;
    int stallTicks;
    net.minecraft.world.phys.Vec3 stallAnchor;
    long stallSignature;
    final Set<String> stallSeen = new HashSet<>();
    /** Surface stone that is never reached: this long without stone and without walking anywhere new, then the own stairs. */
    public static final int SURFACE_STONE_TICKS = 20 * 20;
    /** Walking from face to face without a single new stone: this long, then the own stairs too. */
    public static final int SURFACE_WALK_TICKS = 20 * 60;
    int dryWalkTicks;
    /** Cells this stone order has dug: a block under one of them is never dug (no pit, no shaft). */
    final Set<BlockPos> dug = new HashSet<>();
    /** Faces whose walk failed, with where he stood then: tried again only once he has moved a few blocks. */
    final java.util.Map<BlockPos, BlockPos> offFoot = new java.util.HashMap<>();
    static final int OFF_FOOT_MOVE = 4;
    /** Choosing a face over a few ticks: the nearest on foot so far and the faces already compared. */
    CompanionRoute.Reach stoneChoice;
    final Set<BlockPos> stoneCompared = new HashSet<>();
    final List<BlockPos> stoneSingles = new ArrayList<>();
    static final int STONE_GROUP = 8;
    /** Searches that really ran (not «no stand cell») in one choice; with a face found, after this many he takes the best instead of comparing every face in reach. */
    int stoneSearches;
    static final int STONE_SEARCHES = 20;
    /** Farther than this a face with no route is walked to in legs (CompanionTravel). */
    static final int FAR_STONE = 12;
    static final int TRAVEL_SPOT = 10;
    BlockPos stoneTravel;
    int stoneTravels;
    final Set<BlockPos> travelFailed = new HashSet<>();
    int dryTicks;
    int dryGathered;
    net.minecraft.world.phys.Vec3 dryAnchor;
    /** mine_ore: one kind, counted in ore blocks broken; the drop gained is reported from the bag. */
    public static final int MAX_ORE = 16;
    CompanionOre.Kind ore;
    int oreWanted, oreMined, oreDropsBefore, oreFound, oreLiquid, oreUnreachable, orePickaxeAttempts;
    boolean oreDone, oreChoosing, oreLootRetried, stoneLootRetried;
    CompanionOre.Search oreSearch;
    List<BlockPos> oreCandidates;
    final Set<BlockPos> oreRejected = new HashSet<>();
    /** Shared obstructions across different veins. Keep their state so a changed block can be reconsidered. */
    final java.util.Map<BlockPos, BlockState> oreBlockedCells = new java.util.HashMap<>();
    /** hunt_animals: the kind (null — any), how many, kills by his own blows, the chase, and the bag before for the report. */
    public static final int MAX_GAME = 16;
    boolean gameForFood;
    /** How a hunt without a single animal begins its report; the plan then does not go on to the furnace. */
    public static final String NOTHING_HUNTED = "Никого не добыл";
    static final int CHASE_TICKS = 20 * 15;
    /** Hunting ground: his search radius, at least 32 (game log 22.10: «пускай ищет даже если еда далеко»); a chase goes 16 further. */
    int huntRadius() { return owner.autonomy().running() ? CompanionAutonomy.LEASH : Math.clamp(owner.getSearchRadius(), 32, CompanionEntity.MAX_SEARCH_RADIUS); }
    CompanionGame.Kind game, gameLastKind;
    int gameWanted, gameKills;
    java.util.UUID gameTarget;
    long gameSince;
    double gameBest;
    float gameHealth;
    BlockPos gameAnchor;
    final java.util.Map<java.util.UUID, Long> gameSkipped = new java.util.HashMap<>();
    final Set<CompanionGame.Kind> gameKinds = new HashSet<>();
    BlockPos gameTravel;
    int gameTravels;
    final java.util.Map<net.minecraft.world.item.Item, Integer> gameBagBefore = new java.util.HashMap<>();
    /** While choosing the next ore: those no walk from here reaches, and those no tunnel from here reaches. */
    final Set<BlockPos> oreOffFoot = new HashSet<>(), oreNoTunnel = new HashSet<>();
    /** Cells of the own tunnel still to dig, in order; the top of its stairs is the way back out. */
    final java.util.Deque<BlockPos> oreTunnel = new java.util.ArrayDeque<>();
    BlockPos oreExit;
    /**
     * Iron for a tool, inside one work: ingots needed, the tool, and whether the running ore or furnace stage is only
     * this preparation. The ore the order itself is about waits in the saved fields until the tool is made.
     */
    int ironNeeded, ironMines, ironSmelts;
    String ironTool = "";
    int ironToolCount;
    boolean ironPrep;
    CompanionOre.Kind goalOre;
    int goalOreWanted, goalOreMined, goalOreDropsBefore;
    BlockPos goalOreExit;
    /** Where the work goes on after stone was mined for something else (a pickaxe, a furnace). */
    Phase afterStone;
    /** bench_craft: only walk to the bench and craft, nothing is gathered for it. */
    boolean craftOnly;
    /** smelt_items without a number: everything of the raw material carried, at most a stack (client PlannedAction.ALL_CARRIED). */
    public static final int ALL_CARRIED = 0;
    /**
     * store_items of a deed of his own (CompanionAutonomy): like «resources» (tools, armour, food, bench and chest stay), and the
     * stock he keeps stays too — logs and stone up to his stock, soil for supports. Never sent by the model.
     */
    public static final String SURPLUS = "surplus";
    static final int MAX_FURNACES = 8;
    /** One coal smelts eight items: a player splits a big batch into furnaces of about that much. */
    static final int ITEMS_PER_FURNACE = 8;
    static final int FURNACE_GROUP = 10;
    /** Smelting: the raw material and the real furnaces in use; each lot remembers what in its furnace is ours. */
    CompanionSmelting.Recipe smelt;
    int smeltCount;
    boolean smeltAll;
    boolean mixedMeat;
    int mixedCooked, mixedKinds;
    final Set<net.minecraft.world.item.Item> mixedInputs = new HashSet<>();
    final List<Lot> lots = new ArrayList<>();
    final Set<BlockPos> triedFurnaces = new HashSet<>();
    BlockPos heading;
    boolean ownFurnaceFailed;
    /** Tries to get out of a cut below the bench before the order gives up on it. */
    int benchClimbs;
    /** Where the furnace goes when there was no room for it where he stood: the place of the order. */
    BlockPos furnaceRoom;
    boolean furnacesShort;
    int furnacesPlaced;
    static final class Lot {
        final BlockPos pos;
        final boolean placed;
        int count, outputBefore, ready, taken, rawBack, fuelOurs, fuelTheirs, misses;
        net.minecraft.world.item.Item fuelPut;
        boolean loaded, emptied, gaveUp, stuck, stalled;
        long progressTick;
        Lot(BlockPos pos, boolean placed) { this.pos = pos.immutable(); this.placed = placed; }
        boolean open() { return loaded && !emptied && !gaveUp; }
    }
    boolean isHunting() { return job == Job.HUNT; }
    boolean isHuntTarget(java.util.UUID id) { return huntTargets.contains(id); }
    void hitEnemy(LivingEntity enemy) {
        if (isHunting() && huntTargets.contains(enemy.getUUID())) {
            orderProgressTime = owner.level().getGameTime();
            if (!enemy.isAlive() && huntDead.add(enemy.getUUID())) huntKills++;
        }
        if (job == Job.GAME && enemy instanceof net.minecraft.world.entity.animal.Animal animal && CompanionGame.Kind.of(animal) != null) {
            orderProgressTime = owner.level().getGameTime();
            if (!animal.isAlive() && huntDead.add(animal.getUUID())) { gameKills++; gameTravels = 0; gameLastKind = CompanionGame.Kind.of(animal); gameKinds.add(gameLastKind); }
        }
    }
    long taskId;
    long completedId = -1;
    CompanionActionResult completedResult;

    public long activeTaskId() { return isActive() ? taskId : 0; }
    public CompanionActionResult taskResult(long id) {
        if (completedId == id) return completedResult;
        return isActive() && taskId == id ? null : CompanionActionResult.cancelled("Работа была прервана другой командой.");
    }

    public CompanionWork(CompanionEntity owner) {
        this.owner = owner; house = new CompanionHouse(owner); breaking = new CompanionBlockBreaking(owner);
        pickaxeSense = new CompanionToolSense(owner, "pickaxe", ItemTags.PICKAXES, Blocks.STONE.defaultBlockState());
        shovelSense = new CompanionToolSense(owner, "shovel", ItemTags.SHOVELS, Blocks.DIRT.defaultBlockState());
    }
    public boolean isActive() { return job != null; }
    boolean swinging() { return breaking.swinging(); }
    boolean allowsRouteRecovery() {
        // Inside our own stair descent, walls around and the next cell above are the plan, not a trap. The generic
        // escape used to fill those stairs with soil and break their steps. It acts only once the walk back is confirmed gone.
        if (isActive() && phase == Phase.MINE && !quarrySupports.isEmpty() && !returnRouteBlocked) return false;
        // The own ore tunnel is the same: its walls are not a trap, and the way back is the tunnel itself.
        if (isActive() && phase == Phase.ORE && oreExit != null) return false;
        // Roofs and crowns are interaction targets, not movement destinations.
        return !isActive() || job != Job.HOUSE && phase != Phase.WOOD
                || workOrigin != null && owner.getY() < workOrigin.getY() - 1.25;
    }
    String currentDescription() {
        return job == Job.COME ? "пытается добраться до игрока" : job == Job.HUNT ? "поручение: ближайшие монстры" : job == Job.GAME ? "охотится, добыл " + gameKills + " из " + gameWanted
                : job == Job.PLACE_CHEST ? "устанавливает " + requestedItem + " (" + newChests + "/" + requestedCount + ")"
                : job == Job.SMELT ? (lots.stream().anyMatch(lot -> lot.loaded) ? "плавит в печах (" + lots.size() + "), готово " + furnaces.smeltReady() + " из " + smeltCount : "готовит печь для плавки")
                : "выполняет работу " + job + ", стадия " + phase;
    }
    String taskDescription() {
        if (job == null) return "";
        String stage = phase == null ? "подготовка" : switch (phase) {
            case CRAFT -> "крафт";
            case PICKAXE, ORE_PICK, SHOVEL -> "подготовка инструмента";
            case WOOD, WOOD_DONE -> "заготовка дерева";
            case MINE, DIGGING, ORE -> "добыча";
            case STORE, CLOSE, PACK -> "работа с вещами";
            case BUILD -> "строительство";
            case RETURN, EXIT -> "возвращение";
            case FIGHT, CHASE -> "поиск и бой";
            case HAND -> "передача вещей";
            case FURNACE -> "подготовка печи";
            case COOK -> "плавка / готовка";
            case IRON -> "подготовка железа";
        };
        return "Выполняет " + jobName() + " · " + stage;
    }
    /** The work in words for the journal of deeds. */
    private String jobName() {
        String item = requestedItem.isEmpty() || requestedItem.equals("all") || requestedItem.equals("logs") ? "" : " «" + itemName(requestedItem) + "»";
        return switch (job) {
            case STONE -> "добычу камня (" + gathered + " из " + wantedStone + ")";
            case CRAFT -> "крафт" + item;
            case STORE -> "складывание вещей в сундук";
            case TAKE -> "взятие вещей из сундука";
            case PLACE_CHEST -> "установку" + item;
            case HOUSE -> "стройку дома";
            case DIRT -> "добычу земли";
            case COME -> "путь к тебе";
            case HUNT -> "бой с монстрами";
            case GAME -> "охоту (" + gameKills + " из " + gameWanted + ")";
            case WOOD -> "рубку дерева";
            case GIVE -> "передачу вещей";
            case SMELT -> "плавку" + (smelt == null ? "" : " «" + itemName(smelt.input()) + "»");
            case ORE -> "добычу руды «" + ore.oreName + "» (" + oreMined + " из " + oreWanted + ")";
        };
    }
    /** Job and phase in short, for the GameTest observer. */
    String watchPhase() { return job == null ? "" : job + "/" + phase; }
    String diagnosticState() {
        return "taskId=" + taskId + ",completedId=" + completedId + ",job=" + job + ",phase=" + phase + ",afterWood=" + afterWood + ",item=" + requestedItem
                + ",count=" + requestedCount + ",gathered=" + gathered + ",wantedStone=" + wantedStone
                + ",transferred=" + transferred + ",target=" + target + ",craftingStation=" + craftingStation
                + ",woodAttempts=" + woodAttempts + ",craftAttempts=" + craftAttempts
                + ",origin=" + workOrigin + ",returnBlocked=" + returnRouteBlocked + ",deadline=" + deadline + ",breaking=" + breaking.diagnosticState()
                + (job == Job.SMELT && smelt != null ? ",smelt=" + BuiltInRegistries.ITEM.getKey(smelt.input()) + "x" + smeltCount + ",furnaces="
                        + lots.stream().map(lot -> lot.pos.toShortString() + ":" + lot.count + "/" + lot.ready + (lot.loaded ? "L" : "") + (lot.emptied ? "E" : "") + (lot.gaveUp ? "G" : "")).toList() : "");
    }
    public void pause() { breaking.reset(); resetApproach(); }

    /**
     * Stone materials this work still needs in the bag; an escape pillar must not use them (review 22.10, В8: the furnace stone
     * of the iron chain went under his feet). A stone order keeps all its stone.
     */
    int stoneReserve() {
        if (job == null) return 0;
        if (job == Job.STONE) return Integer.MAX_VALUE;
        int need = 0;
        if (job == Job.SMELT || !ironTool.isEmpty() || "minecraft:furnace".equals(requestedItem)) {
            need += stoneCost("minecraft:furnace");
            if (smelt != null && new ItemStack(smelt.input()).is(ItemTags.STONE_CRAFTING_MATERIALS)) need += smeltCount;
        }
        if (job == Job.CRAFT || job == Job.ORE || job == Job.PLACE_CHEST) need += stoneCost(requestedItem) * Math.max(1, requestedCount);
        return need;
    }

    /** Wood in the bag is for the work (house, tools, chest, fuel) unless the work is only walking, fighting or handing over. */
    boolean woodSpareForSupports() {
        return job == null || job == Job.COME || job == Job.HUNT || job == Job.GIVE || job == Job.DIRT;
    }

    public static int stoneCost(String item) {
        return switch (item) {
            case "minecraft:stone_pickaxe", "minecraft:stone_axe" -> 3;
            case "minecraft:stone_sword", "minecraft:stone_hoe" -> 2;
            case "minecraft:stone_shovel" -> 1;
            case "minecraft:furnace" -> 8;
            default -> 0;
        };
    }

    /** Iron ingots one iron tool takes; 0 for anything else. */
    public static int ingotCost(String item) {
        return switch (item) {
            case "minecraft:iron_pickaxe", "minecraft:iron_axe" -> 3;
            case "minecraft:iron_sword", "minecraft:iron_hoe" -> 2;
            case "minecraft:iron_shovel" -> 1;
            default -> 0;
        };
    }

    public static boolean supportsCraft(String item, int count) {
        if (count < 1 || count > 16) return false;
        // Iron tools: the ingots are mined and smelted on the way, at most one ore order's worth.
        if (ingotCost(item) > 0) return ingotCost(item) * count <= MAX_ORE;
        return stoneCost(item) > 0 || Set.of("minecraft:chest", "minecraft:crafting_table", "minecraft:stick",
                "minecraft:wooden_pickaxe", "minecraft:wooden_axe", "minecraft:wooden_sword",
                "minecraft:wooden_shovel", "minecraft:wooden_hoe").contains(item);
    }

    public CompanionActionResult start(String action, String item, int count) {
        return start(action, item, count, "near_player");
    }

    public CompanionActionResult start(String action, String item, int count, String location) {
        cancel();
        if (!(owner.level() instanceof ServerLevel level)) return CompanionActionResult.failed("Для работы нужен загруженный мир.");
        if (count < 1 && !("smelt_items".equals(action) && count == ALL_CARRIED) || count > 64) return CompanionActionResult.failed("За раз можно добыть от 1 до 64 блоков.");
        insideHouse = "inside_house".equals(location);
        if (!insideHouse && !"near_player".equals(location)) return CompanionActionResult.failed("Неизвестное место выполнения.");
        if (insideHouse && !house.interiorAvailable(level)) return CompanionActionResult.blocked("Не могу подтвердить, где сейчас наш дом. Снаружи вместо этого ставить не буду.");
        switch (action) {
            case "gather_stone" -> { job = Job.STONE; phase = Phase.PICKAXE; wantedStone = count; }
            case "gather_dirt" -> { job = Job.DIRT; phase = Phase.SHOVEL; }
            case "gather_wood" -> { job = Job.WOOD; phase = Phase.WOOD; }
            case "prepare_craft" -> {
                if (!supportsCraft(item, count)) return CompanionActionResult.failed("Эту цепочку пока не умею подготавливать самостоятельно.");
                job = Job.CRAFT; phase = Phase.CRAFT;
            }
            // Any recipe whose bench is a few steps away: walk there and craft once; materials are not gathered.
            case "bench_craft" -> { job = Job.CRAFT; phase = Phase.CRAFT; }
            case "store_items" -> { job = Job.STORE; phase = Phase.STORE; }
            case "take_items" -> { job = Job.TAKE; phase = Phase.STORE; }
            case "place_chest" -> { job = Job.PLACE_CHEST; phase = Phase.STORE; }
            case "place_block" -> {
                var id = ResourceLocation.tryParse(item);
                if (id == null || BuiltInRegistries.BLOCK.getOptional(id).isEmpty() || BuiltInRegistries.BLOCK.get(id) == Blocks.AIR)
                    return CompanionActionResult.failed("Неизвестный блок для установки.");
                if (!"minecraft:chest".equals(item) && count != 1) return CompanionActionResult.failed("Эти блоки пока ставлю по одному.");
                job = Job.PLACE_CHEST; phase = Phase.STORE;
            }
            case "come" -> { job = Job.COME; phase = Phase.RETURN; }
            case "give_items" -> {
                if (!"logs".equals(item) && (ResourceLocation.tryParse(item) == null || !BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(item))))
                    return CompanionActionResult.failed("Не понял, какой предмет отдать.");
                job = Job.GIVE; phase = Phase.HAND;
            }
            case "hunt_mobs" -> { job = Job.HUNT; phase = Phase.FIGHT; }
            case "hunt_animals" -> {
                game = CompanionGame.Kind.of(item);
                if (game == null && !CompanionGame.anyKind(item)) return CompanionActionResult.failed("Не понял, на кого охотиться: корова, свинья, овца, курица или кролик.");
                if (count > MAX_GAME) return CompanionActionResult.failed("За раз добываю до " + MAX_GAME + " животных.");
                job = Job.GAME; phase = Phase.CHASE; gameWanted = count;
                gameForFood = CompanionGame.foodRun(item);
            }
            case "smelt_items" -> {
                smelt = CompanionSmelting.resolve(level, item, items.carriedItems());
                if (smelt == null && CompanionGame.ANY_MEAT.equals(item)) return CompanionActionResult.failed("У меня нет сырого мяса, жарить нечего.");
                if (smelt == null) return CompanionActionResult.failed("Это в печи не переплавляется: в рецептах мира такой плавки нет.");
                int carried = CompanionSmelting.count(items.carriedItems(), smelt.input());
                if (carried == 0) return CompanionActionResult.failed("У меня нет «" + new ItemStack(smelt.input()).getHoverName().getString() + "» для печи.");
                // Like a player: what is in the bag goes in; a shortfall is said at the end, not hidden.
                smeltAll = count == ALL_CARRIED;
                mixedMeat = smeltAll && CompanionGame.ANY_MEAT.equals(item);
                mixedCooked = mixedKinds = 0;
                mixedInputs.clear();
                if (smeltAll) count = Math.min(64, carried);
                smeltCount = Math.min(count, carried);
                job = Job.SMELT; phase = Phase.FURNACE;
            }
            case "build_house" -> {
                if (count != 1) return CompanionActionResult.failed("Пока строю один небольшой дом.");
                job = Job.HOUSE; phase = Phase.BUILD; house.begin();
            }
            case "mine_ore" -> {
                ore = CompanionOre.Kind.of(item);
                if (ore == null) return CompanionActionResult.failed("Не понял, какую руду добыть: уголь, железо, медь, лазурит, золото, редстоун, алмазы или изумруды.");
                if (count > MAX_ORE) return CompanionActionResult.failed("За раз добываю до " + MAX_ORE + " блоков руды.");
                job = Job.ORE; phase = Phase.ORE_PICK; oreWanted = count;
            }
            default -> { return CompanionActionResult.failed("Неизвестная работа."); }
        }
        taskId++;
        craftOnly = "bench_craft".equals(action);
        workOrigin = stoneMining.miningOrigin(); returnRouteBlocked = false; nextReturnCheck = returnBlockedSince = 0; returnMisses = 0;
        quarrySupports.clear(); recoveryReplans = 0;
        stepChecked = null;
        blockedStair = null; blockedStairReplans = 0;
        placementAnchor = owner.autonomy().running() ? owner.autonomy().center()
                : owner.getOwnerPlayer() == null ? owner.blockPosition() : owner.getOwnerPlayer().blockPosition();
        if (insideHouse) placementAnchor = house.interiorAnchor();
        placementMoving = false;
        placedChests.clear();
        orderProgressPosition = owner.position(); orderProgressTime = level.getGameTime(); comeFailedAt = null; huntTargets.clear(); huntDead.clear(); huntKills = 0;
        if (job == Job.HUNT) {
            for (var monster : level.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class,
                    new AABB(placementAnchor).inflate(18), monster -> monster.isAlive() && monster.distanceToSqr(placementAnchor.getCenter()) <= 18 * 18
                            && owner.fairTarget(monster, owner.provokes(monster)))) huntTargets.add(monster.getUUID());
            owner.enableProtectionForWork();
        }
        requestedItem = action.equals("place_chest") ? "minecraft:chest" : item == null ? "" : item;
        requestedCount = count;
        if (job == Job.TAKE && "all".equals(item)) requestedCount = 27 * 64;
        gathered = woodAttempts = craftAttempts = transferred = newChests = 0;
        stoneCollectedBefore = owner.collectedStone(); lastHarvestStand = null;
        // Gathering wood, finishing its crown, collecting drops, returning and crafting are
        // real prerequisite stages. A single three-minute budget used to expire before mining began.
        deadline = level.getGameTime() + 20 * 600;
        rejected.clear(); triedChests.clear(); stoneCandidates = null; stoneLootRetried = false;
        lots.clear(); triedFurnaces.clear(); heading = furnaceRoom = null; ownFurnaceFailed = furnacesShort = false; furnacesPlaced = 0; benchClimbs = 0;
        stoneSearch = null; quarrySoil.clear(); quarryTried = selectingStone = false; exitCell = null; stairs = 0;
        resetTarget();
        craftingStation = null; benchHere = false; owner.allowNewBench(false);
        stallTicks = 0; stallAnchor = owner.position(); stallSignature = progressSignature(); stallSeen.clear();
        dryTicks = dryGathered = dryWalkTicks = 0; dryAnchor = null; dug.clear(); offFoot.clear(); stoneMining.endStoneChoice(); stoneTravel = null; stoneTravels = 0; travelFailed.clear(); owner.travel().reset();
        oreOffFoot.clear(); oreNoTunnel.clear(); oreBlockedCells.clear(); pickaxeSense.reset(); shovelSense.reset();
        oreMined = oreFound = oreLiquid = oreUnreachable = orePickaxeAttempts = 0; oreDone = oreChoosing = oreLootRetried = false;
        oreSearch = null; oreCandidates = null; oreRejected.clear(); oreTunnel.clear(); oreExit = null;
        if (job == Job.ORE) {
            oreDropsBefore = items.countCarried(ore.drop);
            // A second vein must not turn the first descent into the next job's surface.
            oreExit = owner.autonomy().running() ? owner.autonomy().center() : null;
        }
        gameKills = 0; gameTarget = null; gameLastKind = null; gameKinds.clear(); gameTravel = null; gameTravels = 0; owner.travel().reset(); gameSkipped.clear(); gameBagBefore.clear();
        gameAnchor = owner.autonomy().running() ? owner.autonomy().center() : owner.blockPosition();
        if (job == Job.GAME) for (ItemStack stack : items.carriedItems()) if (!stack.isEmpty()) gameBagBefore.merge(stack.getItem(), stack.getCount(), Integer::sum);
        ironMines = ironSmelts = 0; ironPrep = false; goalOre = null; goalOreExit = null; afterStone = null; ironTool = ""; ironNeeded = 0;
        if (job == Job.CRAFT && ingotCost(requestedItem) > 0) {
            ironTool = requestedItem; ironToolCount = requestedCount; ironNeeded = ingotCost(requestedItem) * requestedCount; phase = Phase.IRON;
        }
        if (job == Job.WOOD) {
            afterWood = Phase.WOOD_DONE; woodBeforeRequest = owner.collectedLogs(); owner.startGatheringLogsForWork(count);
        }
        return CompanionActionResult.accepted(job == Job.STORE ? "Сейчас сложу вещи." : job == Job.PLACE_CHEST ? "Поставлю блок." : "Сейчас подготовлюсь.");
    }

    public void cancel() {
        owner.allowNewBench(false); benchHere = false; packTarget = null;
        if (job != null && completedId != taskId) {
            completedId = taskId;
            completedResult = CompanionActionResult.cancelled("Работа прервана. Уже полученные вещи сохранены." + furnaces.furnaceNote());
            owner.noteDeed("Прервал: " + jobName() + " — остановили или дали другое дело." + furnaces.furnaceNote(), false);
        }
        lots.clear(); heading = null;
        clearCracks();
        closeChest();
        house.cancel(); route.abandon();
        job = null; phase = null; afterWood = null;
        target = null; path = null; stoneCandidates = null;
        cooldown = 0;
        stoneSearch = null; quarrySoil.clear(); quarryTried = selectingStone = false; exitCell = null; stairs = 0;
        oreTunnel.clear(); oreSearch = null; oreCandidates = null;
        if (owner.getTarget() instanceof net.minecraft.world.entity.animal.Animal) owner.setTarget(null);
        gameTarget = null;
    }

    public void tick() {
        if (!(owner.level() instanceof ServerLevel level) || job == null) return;
        if (level.getGameTime() > deadline) { finish(CompanionActionResult.timeout("Не успел закончить работу. Остановился, вещи у меня.")); return; }
        if (stalled()) return;
        if (job != Job.HOUSE && owner.actionAnimation() != 0) return;
        if (job != Job.COME && job != Job.HUNT && owner.hasPendingLoot()) return;
        if (owner.getOwnerId() == null || level.getServer().getPlayerList().getPlayer(owner.getOwnerId()) == null) {
            finish(false, "Потерял связь с игроком, остановился."); return;
        }
        if (cooldown > 0) { cooldown--; return; }
        if (phase == Phase.WOOD) {
            // Goal selection and work run in the same server tick. Do not restart gathering before
            // the gathering goal has actually started (or while it is temporarily yielding).
            if (owner.isGatheringWoodForWork()) return;
            if (owner.collectedLogs() <= woodBeforeRequest) {
                // A full bag is said as it is, not as «no wood».
                if (CompanionEntity.FULL_BAG_FOR_LOGS.equals(owner.getLastTaskResult())) { finish(false, CompanionEntity.FULL_BAG_FOR_LOGS); return; }
                finish(false, "Не нашёл доступного дерева для подготовки. Вещи сохранил." + (job == Job.HOUSE ? " Начатый дом можно достроить позже." : "")); return;
            }
            phase = afterWood; resetTarget();
        }
        switch (phase) {
            case CRAFT -> crafting.craftRequested();
            case PICKAXE -> crafting.preparePickaxe();
            case MINE -> stoneMining.mine(level);
            case EXIT -> stoneMining.exitMine(level);
            case PACK -> handOver.packBench(level);
            case HAND -> handOver.handOver(level);
            case STORE -> chests.store(level);
            case CLOSE -> chests.afterClose();
            case BUILD -> buildHouse(level);
            case SHOVEL -> crafting.prepareShovel();
            case DIGGING -> finish(owner.didLastTaskSucceed(), owner.getLastTaskResult());
            case RETURN -> returnToPlayer(level);
            case FIGHT -> hunting.fightNearby(level);
            case CHASE -> hunting.hunt(level);
            case WOOD_DONE -> finish(owner.didLastTaskSucceed(), owner.getLastTaskResult());
            case FURNACE -> furnaces.prepareFurnace(level);
            case COOK -> furnaces.cook(level);
            case ORE_PICK -> oreMining.prepareOrePickaxe();
            case ORE -> oreMining.mineOre(level);
            case IRON -> oreMining.ironStep(level);
            default -> { }
        }
    }

    /**
     * True when the work was stopped: a minute and a half on the same spot (within two blocks), with the same bag and
     * counters and no new status. Waiting at a loaded furnace is checked by its product instead.
     */
    private boolean stalled() {
        if (phase == Phase.COOK) { stallTicks = 0; return false; }
        long signature = progressSignature();
        boolean news = stallSeen.size() < 256 && stallSeen.add(phase + "|" + owner.getLastTaskResult());
        if (news || signature != stallSignature || stallAnchor == null || owner.position().distanceToSqr(stallAnchor) >= 2 * 2) {
            stallAnchor = owner.position(); stallSignature = signature; stallTicks = 0; return false;
        }
        if (++stallTicks < STALL_TICKS) return false;
        String doing = owner.getLastTaskResult().strip();
        if (doing.endsWith(".")) doing = doing.substring(0, doing.length() - 1);
        dev.aicompanion.CompanionDiagnostics.event(owner.diagnosticTrace(), "work_stalled", "entity", owner.getUUID(), "job", job, "phase", phase,
                "status", doing, "seconds", STALL_TICKS / 20, "position", owner.blockPosition(), "state", diagnosticState());
        if (phase == Phase.PACK && packResult != null) {
            String left = packTarget == null ? "" : " Походный верстак оставил на " + packTarget.toShortString() + ".";
            packTarget = null;
            finish(new CompanionActionResult(packResult.success(), packResult.message() + left, packResult.status(), packResult.evidence())); return true;
        }
        finish(CompanionActionResult.blocked("Полторы минуты стоял без продвижения" + (doing.isEmpty() ? "" : ": «" + doing + "»")
                + ". Остановился, вещи у меня."));
        return true;
    }
    /** What the work has to show: the bag and hands item by item, and its own counters. */
    private long progressSignature() {
        long hash = 17;
        for (ItemStack stack : items.carriedItems()) hash = hash * 31 + (stack.isEmpty() ? 0 : BuiltInRegistries.ITEM.getId(stack.getItem()) * 131L + stack.getCount());
        for (int counter : new int[]{gathered, transferred, newChests, huntKills, furnacesPlaced, owner.collectedStone(), oreMined, gameKills}) hash = hash * 31 + counter;
        return hash;
    }

    boolean orderHasStalled(ServerLevel level, int limit) {
        if (orderProgressPosition == null || owner.position().distanceToSqr(orderProgressPosition) > .25) {
            orderProgressPosition = owner.position(); orderProgressTime = level.getGameTime();
        }
        return level.getGameTime() - orderProgressTime > limit;
    }

    private void returnToPlayer(ServerLevel level) {
        var player = owner.getOwnerPlayer();
        if (player == null) { finish(false, "Не вижу тебя в этом измерении."); return; }
        owner.movementIntent(player.blockPosition());
        if (owner.distanceToSqr(player) <= 9 && Math.abs(owner.getY() - player.getY()) <= 1.25 && owner.hasLineOfSight(player)) {
            finish(true, "Я рядом."); return;
        }
        owner.workStatus("Пытаюсь добраться до игрока.");
        // In legs round walls and ridges, as far as the way goes (CompanionTravel): the player may be near by the straight line and
        // far by the way. Legs that came to nothing are not started again from the same spot at once: the self-rescue takes him over
        // there (down a cave: up through the rock), and the order ends only when he stands still (stalled). Five seconds on, they
        // are tried again even from the same spot: the rescue may have opened the way a few blocks off, and he must walk it.
        if (comeFailedAt == null || level.getGameTime() - comeFailedTime >= 100 || !comeFailedAt.closerThan(owner.blockPosition(), 3)) {
            var step = route.follow(player.blockPosition(), 2);
            if (step == CompanionTravel.Step.WALKING) { if (!owner.travel().searchOnly()) orderProgressPosition = null; return; }
            owner.travel().reset();
            if (step == CompanionTravel.Step.FAILED) { comeFailedAt = owner.blockPosition(); comeFailedTime = level.getGameTime(); }
        }
        if (orderHasStalled(level, 240)) {
            // No way at all is told as such; «застрял» is for a way that exists and that he could not get on along.
            if (route.noWayTo(player.blockPosition())) { finish(false, "Не могу к тебе пройти."); return; }
            finish(false, "Не смог добраться до тебя. Застрял на " + owner.blockPosition().toShortString() + ". Иду за тобой дальше, как получится."); return;
        }
    }

    void watchChase() { hunting.watchChase(); }
    static final java.util.UUID NO_ID = new java.util.UUID(0, 0);

    private void buildHouse(ServerLevel level) {
        if (!owner.onGround()) return;
        var site = house.ensureSite(level);
        if (site != null) { finish(false, site.message()); return; }
        if (house.needsPreparation() && crafting.approachCraftingTable()) return;
        CompanionHouse.Result result = house.tick(level);
        switch (result.state()) {
            case NEED_WOOD -> crafting.requestWood(Phase.BUILD, result.logs());
            case DONE -> finish(true, result.message());
            case FAILED -> finish(false, result.message());
            default -> { }
        }
    }

    public static boolean isStone(BlockState state) { return CompanionStoneMining.isStone(state); }
    static int surplusCount(CompanionEntity owner) { return CompanionChests.surplusCount(owner); }

    boolean approach(ServerLevel level, BlockPos block, double reach) {
        double distance = owner.distanceToSqr(block.getCenter());
        if (distance + 0.05D < bestDistance) { bestDistance = distance; lastProgressTick = level.getGameTime(); }
        // A path up a slope or around a hill can lead away from the target for a while. Walking is progress too.
        if ((approachUntil == 0 || level.getGameTime() < approachUntil) && (approachMoved == null || owner.position().distanceToSqr(approachMoved) > .7 * .7)) { approachMoved = owner.position(); lastProgressTick = level.getGameTime(); }
        if (level.getGameTime() - lastProgressTick > 80) return false;
        if (path == null || owner.getNavigation().isDone() || owner.getNavigation().isStuck()) {
            Set<BlockPos> feet = new HashSet<>();
            for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                BlockPos pos = block.offset(dx, dy, dz);
                if (!level.hasChunkAt(pos) || pos.getCenter().distanceToSqr(block.getCenter()) > reach * reach) continue;
                if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) continue;
                if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) continue;
                if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() || !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) continue;
                if (!items.visibleFrom(level, Vec3.atBottomCenterOf(pos).add(0, owner.getEyeHeight(), 0), block)) continue;
                feet.add(pos);
            }
            var found = route.cells(feet, 0);
            // This tick's cells went elsewhere: he keeps walking, the cells are asked again next tick.
            if (found.busy()) return true;
            path = found.path();
            // Walking counts as progress for as long as the first route found takes at his pace, twice over, and ten seconds more.
            if (path != null && approachUntil == 0) approachUntil = level.getGameTime() + CompanionRoute.farLimit(found.length());
            standCells = feet;
        }
        if (path != null && path.canReach()) { route.walk(path, 0.95D); return true; }
        // Arrived at a chosen cell, but its view was checked from the centre: step there instead of
        // reporting the bench or chest beside us as unreachable. The progress watchdog above still bounds this.
        BlockPos here = owner.blockPosition();
        if (!owner.onGround() || !standCells.contains(here)) return false;
        owner.getMoveControl().setWantedPosition(here.getX() + .5D, here.getY(), here.getZ() + .5D, .5D);
        return true;
    }

    static String itemName(net.minecraft.world.item.Item item) { return new ItemStack(item).getHoverName().getString(); }
    static String itemName(String id) {
        var key = ResourceLocation.tryParse(id);
        return key == null || !BuiltInRegistries.ITEM.containsKey(key) ? id : itemName(BuiltInRegistries.ITEM.get(key));
    }

    void rejectTarget() { if (target != null) rejected.add(target); clearCracks(); resetTarget(); }
    void routeRecovered() {
        if (phase != Phase.MINE || !(owner.level() instanceof ServerLevel level) || recoveryReplans++ >= 2) return;
        // A rescue can close the old one-block descent. Its previous path rejections are
        // no longer evidence about the changed terrain; try a fresh, bounded access plan.
        stoneSearch = new CompanionStoneSearch(level, owner.blockPosition(), owner.getSearchRadius());
        stoneCandidates = null; rejected.clear(); quarrySoil.clear(); quarryTried = false; dryAnchor = null; offFoot.clear(); stoneMining.endStoneChoice();
        returnRouteBlocked = false; nextReturnCheck = 0; returnMisses = 0; resetTarget();
        dev.aicompanion.CompanionDiagnostics.event(owner.diagnosticTrace(), "work_replanned", "entity", owner.getUUID(),
                "reason", "terrain_changed_after_route_recovery", "attempt", recoveryReplans, "collected", gathered, "requested", wantedStone);
    }
    void resetTarget() {
        clearCracks(); target = null; resetApproach(); route.abandon();
        // The old target is no destination any more: a self-rescue must not take it for one while he looks for the next.
        owner.movementIntent(null);
    }
    void resetApproach() { path = null; standCells = Set.of(); approachMoved = null; approachUntil = 0; bestDistance = Double.MAX_VALUE; lastProgressTick = owner.level().getGameTime(); }
    void clearCracks() { breaking.reset(); }
    void closeChest() {
        if (openedChest != null && owner.level() instanceof ServerLevel level && level.hasChunkAt(openedChest)
                && level.getBlockState(openedChest).getBlock() instanceof ChestBlock) {
            animateChest(level, openedChest, 0);
            level.playSound(null, openedChest, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5F, 1.0F);
        }
        openedChest = null;
    }
    void animateChest(ServerLevel level, BlockPos pos, int open) {
        BlockState state = level.getBlockState(pos);
        level.blockEvent(pos, state.getBlock(), 1, open);
        if (state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
            if (level.hasChunkAt(other) && level.getBlockState(other).getBlock() == state.getBlock()) level.blockEvent(other, state.getBlock(), 1, open);
        }
    }
    void finish(boolean success, String message) {
        finish(new CompanionActionResult(success, message));
    }
    void finish(CompanionActionResult result) {
        owner.goals().skillCompleted(result.evidence());
        // A travelling bench is not left behind: pick it up first, then report the original result.
        if (phase != Phase.PACK && job != null && job != Job.COME && job != Job.HUNT) {
            BlockPos bench = owner.takeFieldBenchNear(16);
            if (bench != null) { packTarget = bench; packedBenchBefore = items.countCarried(Items.CRAFTING_TABLE); packedBenchFrom = null; packResult = result; phase = Phase.PACK; resetTarget(); return; }
        }
        boolean success = result.success(); String message = result.message();
        owner.allowNewBench(false);
        boolean returning = job == Job.COME;
        if (!success && job == Job.STORE && transferred > 0) message = "Сложил " + transferred + " предметов. " + message + " Остальное осталось в моей сумке.";
        message += furnaces.furnaceNote();
        completedId = taskId;
        completedResult = new CompanionActionResult(success, message, result.status(), result.evidence());
        cancel();
        // «Иди ко мне» that did not get there leaves him following, still trying, not standing where he got stuck (marathon 23.10).
        if (returning) owner.setFollowing(true); else owner.finishFollowing();
        owner.recordActionResult(completedResult);
        owner.syncWorkState();
    }

    public String storageForAi() {
        if (rememberedChest == null) return "пока не использовал";
        return rememberedChest.toShortString();
    }
    public void save(CompoundTag tag) {
        tag.putBoolean("WorkActive", isActive());
        tag.putLong("WorkSequence", taskId);
        if (completedResult != null) { tag.putLong("CompletedWorkId", completedId); tag.put("CompletedWorkResult", completedResult.save()); }
        house.save(tag);
        if (rememberedChest != null) {
            tag.putLong("StorageChest", rememberedChest.asLong());
            tag.putString("StorageDimension", owner.level().dimension().location().toString());
        }
    }
    public void load(CompoundTag tag) {
        cancel(); rememberedChest = null;
        taskId = Math.max(taskId, tag.getLong("WorkSequence"));
        completedId = tag.contains("CompletedWorkResult") ? tag.getLong("CompletedWorkId") : -1;
        completedResult = tag.contains("CompletedWorkResult") ? CompanionActionResult.load(tag.getCompound("CompletedWorkResult")) : null;
        house.load(tag);
        if (tag.contains("StorageChest") && owner.level().dimension().location().toString().equals(tag.getString("StorageDimension"))) rememberedChest = BlockPos.of(tag.getLong("StorageChest"));
        owner.storageStatus(storageForAi());
    }
}

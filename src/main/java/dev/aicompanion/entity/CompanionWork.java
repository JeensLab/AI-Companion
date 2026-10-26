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
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
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
    CompanionMiningNavigation.Choice stoneChoice;
    final Set<BlockPos> stoneCompared = new HashSet<>();
    final List<BlockPos> stoneSingles = new ArrayList<>();
    static final int STONE_GROUP = 8;
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
                : job == Job.SMELT ? (lots.stream().anyMatch(lot -> lot.loaded) ? "плавит в печах (" + lots.size() + "), готово " + smeltReady() + " из " + smeltCount : "готовит печь для плавки")
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
        orderProgressPosition = owner.position(); orderProgressTime = level.getGameTime(); huntTargets.clear(); huntDead.clear(); huntKills = 0;
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
            completedResult = CompanionActionResult.cancelled("Работа прервана. Уже полученные вещи сохранены." + furnaceNote());
            owner.noteDeed("Прервал: " + jobName() + " — остановили или дали другое дело." + furnaceNote(), false);
        }
        lots.clear(); heading = null;
        clearCracks();
        closeChest();
        house.cancel();
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
            case PACK -> packBench(level);
            case HAND -> handOver(level);
            case STORE -> store(level);
            case CLOSE -> afterClose();
            case BUILD -> buildHouse(level);
            case SHOVEL -> crafting.prepareShovel();
            case DIGGING -> finish(owner.didLastTaskSucceed(), owner.getLastTaskResult());
            case RETURN -> returnToPlayer(level);
            case FIGHT -> hunting.fightNearby(level);
            case CHASE -> hunting.hunt(level);
            case WOOD_DONE -> finish(owner.didLastTaskSucceed(), owner.getLastTaskResult());
            case FURNACE -> prepareFurnace(level);
            case COOK -> cook(level);
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
        // Far off: in legs round walls and ridges, like the following (CompanionTravel); it gives up after half a minute without
        // coming closer, and says so.
        if (owner.distanceToSqr(player) > 32 * 32 && (owner.onGround() || owner.isInWater())) {
            var step = owner.travel().follow(player.blockPosition(), 24);
            if (step == CompanionTravel.Step.FAILED) { owner.travel().reset(); finish(false, "Не смог добраться до тебя: полминуты не мог подойти ближе, остановился на " + owner.blockPosition().toShortString() + ". Иду за тобой дальше, как получится."); return; }
            if (step == CompanionTravel.Step.WALKING) { orderProgressPosition = null; return; }
            owner.travel().reset();
        }
        if (orderHasStalled(level, 240)) { finish(false, "Не смог добраться до тебя. Застрял на " + owner.blockPosition().toShortString() + ". Иду за тобой дальше, как получится."); return; }
        Path route = owner.getNavigation().createPath(player, 1);
        if (route != null && route.getNodeCount() > 1) CompanionMiningNavigation.walk(owner, route);
        // No new route this tick (bobbing in the water at a bank, marathon 23.10): keep walking the one he has.
        else if (owner.onGround() && !owner.isInWater()) owner.getNavigation().stop();
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

    boolean approach(ServerLevel level, BlockPos block, double reach) {
        double distance = owner.distanceToSqr(block.getCenter());
        if (distance + 0.05D < bestDistance) { bestDistance = distance; lastProgressTick = level.getGameTime(); }
        // A path up a slope or around a hill can lead away from the target for a while. Walking is progress too.
        if (approachMoved == null || owner.position().distanceToSqr(approachMoved) > .7 * .7) { approachMoved = owner.position(); lastProgressTick = level.getGameTime(); }
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
            path = owner.getNavigation().createPath(feet, 0);
            standCells = feet;
        }
        if (path != null && path.canReach() && owner.getNavigation().moveTo(path, 0.95D)) return true;
        // Arrived at a chosen cell, but its view was checked from the centre: step there instead of
        // reporting the bench or chest beside us as unreachable. The progress watchdog above still bounds this.
        BlockPos here = owner.blockPosition();
        if (!owner.onGround() || !standCells.contains(here)) return false;
        owner.getMoveControl().setWantedPosition(here.getX() + .5D, here.getY(), here.getZ() + .5D, .5D);
        return true;
    }

    private void store(ServerLevel level) {
        if (job == Job.TAKE) { retrieve(level); return; }
        if (job == Job.PLACE_CHEST) { placeRequestedChests(); return; }
        if (!owner.onGround() && !owner.isInWater()) return;
        if (job == Job.STORE && (eligibleCount() == 0 || storeQuota() <= 0)) { finish(true, transferred > 0 ? "Сложил " + transferred + " предметов в сундук." : "Лишних вещей для сундука сейчас нет."); return; }
        if (target == null) {
            target = findChest(level);
            resetApproach();
            if (target == null) {
                if (newChests >= 1) { finish(false, "Сложил " + transferred + " предметов, но оставшиеся вещи не помещаются. Они остались в сумке."); return; }
                if (!hasChestItem()) {
                    if (crafting.approachCraftingTable()) return;
                    if (!items.moveHandMaterialsToBag()) return;
                    CompanionActionResult crafted = owner.craftFromBag("minecraft:chest", 1);
                    if (!crafted.success()) {
                        if (crafted.message().contains("места") || crafted.message().contains("безопасное место")) finish(false, crafted.message());
                        else crafting.requestWood(Phase.STORE);
                        return;
                    }
                    return; // Let the crafting/table gesture finish before placing the chest.
                }
                CompanionActionResult placed = owner.placeBlockNearResult("minecraft:chest", insideHouse ? placementAnchor : owner.blockPosition(),
                        pos -> !insideHouse || house.inside(pos));
                if (!placed.success()) { finish(false, placed.message()); return; }
                newChests++;
                target = findChest(level);
                resetApproach();
                if (target == null) { finish(false, "Сундук поставил, но не могу его открыть. Вещи остались в сумке."); return; }
                return; // Open on the next work stage, after the placement gesture.
            }
        }
        Container chest = chestAt(level, target);
        if (chest == null) { triedChests.add(target); resetTarget(); return; }
        if (owner.distanceToSqr(target.getCenter()) > 3.2D * 3.2D || !CompanionMiningNavigation.visible(owner, owner.getEyePosition(), target)) {
            if (!approach(level, target, 2.8D)) { triedChests.add(target); resetTarget(); }
            return;
        }
        owner.getNavigation().stop();
        owner.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        openedChest = target.immutable();
        animateChest(level, openedChest, 1);
        level.playSound(null, openedChest, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 1.0F);
        owner.animateAction(3, target, ItemStack.EMPTY);
        transferred += transfer(chest);
        chest.setChanged();
        rememberedChest = target.immutable();
        owner.rememberObserved(target);
        owner.storageStatus(storageForAi());
        triedChests.add(target);
        phase = Phase.CLOSE;
        cooldown = 10;
        owner.workStatus("Складываю вещи в сундук.");
    }

    /** An explicit placement order always places new blocks, even if storage already exists. */
    private void placeRequestedChests() {
        if (!owner.onGround()) return;
        if (newChests >= requestedCount) { finishPlacedChests(); return; }
        boolean hasItem = items.carriedItems().stream().anyMatch(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(requestedItem) && !stack.isEmpty());
        if (!hasItem) {
            if (!supportsCraft(requestedItem, 1)) { finish(false, "Нет блока " + requestedItem + " в сумке, его подготовку пока не умею."); return; }
            if (crafting.needsWorkbench(requestedItem) && crafting.approachCraftingTable()) return;
            if (!items.moveHandMaterialsToBag()) return;
            CompanionActionResult crafted = owner.craftFromBag(requestedItem, 1);
            if (!crafted.success()) {
                if (crafted.message().contains("места") || crafted.message().contains("безопасное место"))
                    finish(false, "Поставил " + newChests + " из " + requestedCount + ". " + crafted.message());
                else if (stoneCost(requestedItem) > items.countStoneMaterials()) {
                    wantedStone = stoneCost(requestedItem) - items.countStoneMaterials();
                    stoneCollectedBefore = owner.collectedStone(); gathered = 0; phase = Phase.PICKAXE;
                    stoneSearch = null; stoneCandidates = null; rejected.clear(); dryAnchor = null; resetTarget();
                } else crafting.requestWood(Phase.STORE);
            }
            return;
        }
        if (owner.distanceToSqr(placementAnchor.getCenter()) > 3 * 3 || insideHouse && !house.inside(owner.blockPosition())) {
            if (!placementMoving) { resetApproach(); placementMoving = true; }
            owner.movementIntent(placementAnchor);
            double distance = owner.distanceToSqr(placementAnchor.getCenter());
            if (distance + .05 < bestDistance) { bestDistance = distance; lastProgressTick = owner.level().getGameTime(); }
            if (owner.level().getGameTime() - lastProgressTick > 160) {
                finish(false, "Блок есть в сумке, но не смог вернуться к месту установки на " + placementAnchor.toShortString() + "."); return;
            }
            Path route = owner.getNavigation().createPath(placementAnchor, insideHouse ? 0 : 1);
            if (route == null || !route.canReach()) { owner.getNavigation().stop(); return; }
            CompanionMiningNavigation.walk(owner, route); return;
        }
        if (insideHouse && !house.interiorAvailable((ServerLevel)owner.level())) { finish(false, "Дом изменился, место внутри больше не подтверждено."); return; }
        CompanionActionResult placed = owner.placeBlockNearResult(requestedItem, placementAnchor, pos -> !insideHouse || house.inside(pos));
        if (!placed.success()) { finish(false, "Поставил " + newChests + " из " + requestedCount + ". " + placed.message()); return; }
        if (owner.lastPlacedPosition() != null) placedChests.add(owner.lastPlacedPosition());
        newChests++;
        if (newChests >= requestedCount) finishPlacedChests();
        else owner.workStatus("Поставил " + newChests + " из " + requestedCount + " блоков " + requestedItem + ".");
    }

    private void finishPlacedChests() {
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(requestedItem));
        long present = placedChests.stream().filter(pos -> owner.level().hasChunkAt(pos) && owner.level().getBlockState(pos).is(block)
                && (!insideHouse || house.inside(pos))).count();
        if (present != requestedCount) {
            finish(false, "На месте установки сейчас " + present + " из " + requestedCount + " блоков " + requestedItem + ". Часть блоков исчезла или недоступна для проверки."); return;
        }
        finish(true, "Поставил " + requestedItem + ": " + newChests + (insideHouse ? " внутри дома" : "") + ". Координаты: " + placedChests.stream().map(BlockPos::toShortString).toList() + ".");
    }

    private void afterClose() {
        closeChest();
        resetTarget();
        if (job == Job.TAKE) {
            if (transferred >= requestedCount) finish(true, "Забрал " + transferred + " предметов из сундука. В сумке: " + owner.getStoredItemsSummary() + ".");
            else phase = Phase.STORE;
        }
        else if (eligibleCount() == 0) finish(true, "Сложил " + transferred + " предметов в сундук.");
        else phase = Phase.STORE;
    }

    private void retrieve(ServerLevel level) {
        if (!owner.onGround()) return;
        if (target == null) { target = findChest(level); resetApproach(); }
        if (target == null) {
            finish(transferred > 0 && (requestedItem.equals("all") || transferred >= requestedCount),
                    "Забрал " + transferred + (requestedItem.equals("all") ? " предметов" : " из " + requestedCount) + ". В доступных сундуках больше нужных вещей нет. В сумке: " + owner.getStoredItemsSummary() + "."); return;
        }
        Container chest = chestAt(level, target);
        if (chest == null) { triedChests.add(target); resetTarget(); return; }
        owner.movementIntent(target);
        if (owner.distanceToSqr(target.getCenter()) > 3.2 * 3.2 || !CompanionMiningNavigation.visible(owner, owner.getEyePosition(), target)) {
            if (!approach(level, target, 2.8)) { triedChests.add(target); resetTarget(); }
            return;
        }
        owner.getNavigation().stop();
        openedChest = target.immutable(); animateChest(level, target, 1);
        level.playSound(null, target, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, .5F, 1);
        owner.observeEvent("chest", "Открыл сундук, чтобы взять нужные вещи.");
        ItemStack shown = ItemStack.EMPTY; int movedHere = 0;
        for (int slot = 0; slot < chest.getContainerSize() && transferred < requestedCount; slot++) {
            ItemStack stack = chest.getItem(slot); if (!eligible(stack)) continue;
            ItemStack offered = stack.copyWithCount(Math.min(stack.getCount(), requestedCount - transferred));
            int offeredCount = offered.getCount(); owner.storeLoot(offered);
            int moved = offeredCount - offered.getCount();
            if (moved > 0) { shown = stack.copyWithCount(1); stack.shrink(moved); chest.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack); transferred += moved; movedHere += moved; }
        }
        owner.animateAction(3, target, shown); chest.setChanged(); owner.setChanged();
        rememberedChest = target.immutable(); owner.rememberObserved(target); owner.storageStatus(storageForAi()); triedChests.add(target);
        if (movedHere == 0) { closeChest(); finish(false, "В сумке нет места для вещей из сундука. Предметы оставил на месте."); return; }
        phase = Phase.CLOSE; cooldown = 10; owner.workStatus("Забираю вещи из сундука.");
    }

    private BlockPos findChest(ServerLevel level) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos center = owner.blockPosition();
        for (BlockPos pos : blockEntitiesNear(level)) {
            if (triedChests.contains(pos) || insideHouse && !house.inside(pos)) continue;
            // A deed of his own: only a chest he set down himself, near the player (Толик: «не трогает мои сундуки»).
            if (owner.ownPlacesOnly() && (!owner.placedByMe(pos) || !owner.leashAllows(pos))) continue;
            Container chest = chestAt(level, pos);
            if (chest != null && (job == Job.TAKE ? containsRequested(chest) : job == Job.PLACE_CHEST || hasCapacity(chest))) found.add(pos.immutable());
        }
        // The chest Oleg already uses is "ours": fill it before any other one nearby.
        found.sort(Comparator.comparing((BlockPos pos) -> !pos.equals(rememberedChest)).thenComparingDouble(pos -> pos.distSqr(center)));
        return found.isEmpty() ? null : found.getFirst();
    }

    /** Block entities of loaded chunks within the container search radius (at most 32 blocks). */
    private List<BlockPos> blockEntitiesNear(ServerLevel level) {
        List<BlockPos> found = new ArrayList<>();
        int radius = Math.min(32, owner.getSearchRadius());
        BlockPos center = owner.blockPosition();
        for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++) {
            for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockPos pos : chunk.getBlockEntities().keySet()) if (pos.distSqr(center) <= radius * radius) found.add(pos);
            }
        }
        return found;
    }

    private Container chestAt(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return null;
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof ChestBlock chest ? ChestBlock.getContainer(chest, state, level, pos, false) : null;
    }

    private int transfer(Container destination) {
        int moved = 0;
        int[] reserves = reserves(requestedItem);
        for (int source = 0; source < owner.getContainerSize(); source++) {
            ItemStack stack = owner.getItem(source);
            if (!eligible(stack)) continue;
            int keep = kept(stack, reserves, SURPLUS.equals(requestedItem));
            int available = Math.min(stack.getCount() - keep, storeQuota() - moved);
            for (int pass = 0; pass < 2 && available > 0; pass++) {
                for (int slot = 0; slot < destination.getContainerSize() && available > 0; slot++) {
                    if (!destination.canPlaceItem(slot, stack)) continue;
                    ItemStack existing = destination.getItem(slot);
                    if (pass == 0 && existing.isEmpty() || pass == 1 && !existing.isEmpty()) continue;
                    if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack)) continue;
                    int maximum = Math.min(destination.getMaxStackSize(), stack.getMaxStackSize());
                    int amount = Math.min(available, maximum - existing.getCount());
                    if (amount <= 0) continue;
                    ItemStack inserted = existing.isEmpty() ? stack.copyWithCount(amount) : existing.copyWithCount(existing.getCount() + amount);
                    destination.setItem(slot, inserted);
                    stack.shrink(amount); available -= amount; moved += amount;
                }
            }
            owner.setItem(source, stack.isEmpty() ? ItemStack.EMPTY : stack);
        }
        owner.setChanged(); destination.setChanged();
        return moved;
    }

    private boolean hasCapacity(Container chest) {
        for (int source = 0; source < owner.getContainerSize(); source++) {
            ItemStack stack = owner.getItem(source);
            if (!eligible(stack)) continue;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                ItemStack existing = chest.getItem(slot);
                if (chest.canPlaceItem(slot, stack) && (existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, stack)
                        && existing.getCount() < Math.min(stack.getMaxStackSize(), chest.getMaxStackSize()))) return true;
            }
        }
        return false;
    }

    private int eligibleCount() {
        int count = 0;
        int[] reserves = reserves(requestedItem);
        for (int slot = 0; slot < owner.getContainerSize(); slot++) {
            ItemStack stack = owner.getItem(slot);
            if (eligible(stack)) count += stack.getCount() - kept(stack, reserves, SURPLUS.equals(requestedItem));
        }
        return count;
    }
    /** What stays in the bag on a store order: soil for supports (8) and, for SURPLUS, his stock of logs and stone. */
    private static int[] reserves(String requested) {
        boolean surplus = SURPLUS.equals(requested);
        return new int[]{requested.isBlank() || requested.equals("resources") || surplus ? 8 : 0,
                surplus ? CompanionAutonomy.WOOD_STOCK : 0, surplus ? CompanionAutonomy.STONE_STOCK : 0,
                surplus ? CompanionAutonomy.COAL_STOCK : 0, surplus ? 16 : 0, surplus ? 8 : 0, surplus ? 8 : 0};
    }
    /** How much of this stack stays, counting the reserves down. */
    private static int kept(ItemStack stack, int[] reserves, boolean surplus) {
        int kind = CompanionTools.soilItem(stack) ? 0 : !surplus ? -1 : stack.is(ItemTags.LOGS) ? 1 : stack.is(ItemTags.STONE_TOOL_MATERIALS) ? 2
                : stack.is(ItemTags.COALS) ? 3 : stack.is(Items.TORCH) ? 4 : stack.is(Items.IRON_INGOT) ? 5 : stack.is(Items.RAW_IRON) ? 6 : -1;
        if (kind < 0) return 0;
        int keep = Math.min(stack.getCount(), reserves[kind]);
        reserves[kind] -= keep;
        return keep;
    }
    /** Not a tool, armour, food, bench or chest: a resource a store order of «resources» puts away. */
    private static boolean resource(ItemStack stack) {
        return stack.getMaxDamage() == 0 && !(stack.getItem() instanceof ArmorItem) && !stack.has(DataComponents.FOOD)
                && !stack.is(Items.CRAFTING_TABLE) && !stack.is(Items.CHEST);
    }
    /** What a SURPLUS store order would put away from his bag now (CompanionAutonomy: is the order worth giving). */
    static int surplusCount(CompanionEntity owner) {
        int count = 0;
        int[] reserves = reserves(SURPLUS);
        for (int slot = 0; slot < owner.getContainerSize(); slot++) {
            ItemStack stack = owner.getItem(slot);
            if (!stack.isEmpty() && resource(stack)) count += stack.getCount() - kept(stack, reserves, true);
        }
        return count;
    }
    private boolean containsRequested(Container chest) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) if (eligible(chest.getItem(slot))) return true;
        return false;
    }

    /**
     * "Put the stone sword in the chest" means one sword, not every sword he carries: for a named tool, weapon or
     * armour piece only the requested count goes in (game log 22.10 put away his own sword too). Resources move whole.
     */
    private int storeQuota() {
        if (job != Job.STORE || requestedItem.isBlank() || List.of("all", "logs", "stone", "resources", SURPLUS).contains(requestedItem)) return Integer.MAX_VALUE;
        var id = ResourceLocation.tryParse(requestedItem);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id) || new ItemStack(BuiltInRegistries.ITEM.get(id)).getMaxStackSize() != 1) return Integer.MAX_VALUE;
        return Math.max(1, requestedCount) - transferred;
    }

    private boolean eligible(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (requestedItem.equals("all")) return true;
        if (requestedItem.equals("logs")) return stack.is(ItemTags.LOGS);
        if (requestedItem.equals("stone")) return stack.is(ItemTags.STONE_TOOL_MATERIALS) || stack.is(Items.STONE);
        if (!requestedItem.isBlank() && !requestedItem.equals("resources") && !requestedItem.equals(SURPLUS)) return requestedItem.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        return resource(stack);
    }

    private boolean hasChestItem() { return items.carriedItems().stream().anyMatch(stack -> stack.is(Items.CHEST)); }

    private String furnaceNote() {
        // Smelting as its own order or as the iron step of a tool: either way the furnaces are ours.
        if (lots.isEmpty()) return "";
        List<String> open = lots.stream().filter(lot -> lot.loaded && !lot.emptied).map(lot -> lot.pos.toShortString()).toList();
        return open.isEmpty() ? "" : " Сырьё и топливо остались в " + (open.size() == 1 ? "печи на " + open.getFirst() : "печах на " + String.join("; ", open))
                + " — переплавленное можно забрать оттуда.";
    }
    static String itemName(net.minecraft.world.item.Item item) { return new ItemStack(item).getHoverName().getString(); }
    static String itemName(String id) {
        var key = ResourceLocation.tryParse(id);
        return key == null || !BuiltInRegistries.ITEM.containsKey(key) ? id : itemName(BuiltInRegistries.ITEM.get(key));
    }
    private int loadedTotal() { return lots.stream().filter(lot -> lot.loaded).mapToInt(lot -> lot.count).sum(); }
    private int smeltReady() { return lots.stream().mapToInt(lot -> lot.ready).sum(); }

    /** A big batch goes into several furnaces, about eight items each, as a player with spare furnaces would do. */
    private int wantedFurnaces() {
        int remaining = smeltCount - loadedTotal();
        if (remaining <= 0) return lots.size();
        int loaded = (int) lots.stream().filter(lot -> lot.loaded).count();
        return Math.min(MAX_FURNACES, loaded + Math.max(1, (remaining + ITEMS_PER_FURNACE - 1) / ITEMS_PER_FURNACE));
    }

    /**
     * Free furnaces nearby are used like a player would; furnaces from the bag are set down beside them for a big batch.
     * With no furnace at all, one is made from real stone and set down.
     */
    private void prepareFurnace(ServerLevel level) {
        if (!owner.onGround()) return;
        int wanted = wantedFurnaces();
        while (lots.size() < wanted) {
            BlockPos free = findFurnace(level);
            if (free == null) break;
            lots.add(new Lot(free, false));
        }
        boolean carriesFurnace = items.carriedItems().stream().anyMatch(stack -> stack.is(Items.FURNACE));
        if (lots.size() < wanted && carriesFurnace && furnacesPlaced < MAX_FURNACES + 2) {
            BlockPos group = lots.isEmpty() ? null : lots.getFirst().pos;
            if (group != null && owner.distanceToSqr(group.getCenter()) > 4.5 * 4.5) {
                // Set the new furnaces down next to the free one, not where he happens to stand.
                if (!group.equals(heading)) { heading = group; resetApproach(); }
                owner.movementIntent(group);
                if (approach(level, group, 2.8D)) { owner.workStatus("Иду к печи на " + group.toShortString() + "."); return; }
            } else {
                if (furnaceRoom != null && owner.distanceToSqr(furnaceRoom.getCenter()) > 2.5 * 2.5) {
                    owner.movementIntent(furnaceRoom);
                    if (approach(level, furnaceRoom, 2.5D)) { owner.workStatus("Здесь тесно для печи, выхожу туда, где есть место."); return; }
                }
                CompanionActionResult placed = owner.placeBlockNearResult("minecraft:furnace", owner.blockPosition());
                if (placed.success() && owner.lastPlacedPosition() != null) {
                    furnacesPlaced++;
                    lots.add(new Lot(owner.lastPlacedPosition(), true));
                    owner.workStatus(lots.size() > 1 ? "Ставлю ещё печь, чтобы плавить быстрее: печей " + lots.size() + "." : "Ставлю печь.");
                    cooldown = 10; return;
                }
                // Like a player in a narrow cut: out to where the order came, and the furnace is set down there (marathon 23.10,
                // «долина»: down the slope for the stone, the bench took the one free cell of the cut, and the meat stayed raw).
                if (lots.isEmpty() && furnaceRoom == null && placementAnchor != null && owner.distanceToSqr(placementAnchor.getCenter()) > 3 * 3) {
                    furnaceRoom = placementAnchor; resetApproach(); return;
                }
                if (lots.isEmpty()) { finish(false, placed.message()); return; }
            }
        }
        if (lots.isEmpty()) {
            if (ownFurnaceFailed) { finish(false, "Свою печь поставил, но подойти к ней и заложить сырьё не получилось. Сырьё у меня."); return; }
            if (!crafting.approachCraftingTable() && items.moveHandMaterialsToBag()) craftFurnace();
            return;
        }
        // Every furnace taken is loaded and still more raw material is left: no room for more furnaces, say so at the end.
        if (lots.stream().allMatch(lot -> lot.loaded)) { smeltCount = loadedTotal(); furnacesShort = true; }
        phase = Phase.COOK; heading = null; resetApproach();
    }

    private void craftFurnace() {
        // Smelting cobblestone itself: the new furnace must not eat the stone that was to go inside it.
        int reserved = new ItemStack(smelt.input()).is(ItemTags.STONE_CRAFTING_MATERIALS) ? smeltCount : 0;
        int missing = stoneCost("minecraft:furnace") + reserved - items.countStoneMaterials();
        if (missing > 0) {
            if (++craftAttempts > 3) { finish(false, "Для печи не хватает камня, а добыть его не вышло. Сырьё у меня."); return; }
            owner.workStatus("Свободной печи рядом нет. Добуду камня и сделаю свою.");
            wantedStone = missing; stoneCollectedBefore = owner.collectedStone(); gathered = 0; phase = Phase.PICKAXE; afterStone = Phase.FURNACE;
            stoneSearch = null; stoneCandidates = null; rejected.clear(); dryAnchor = null; resetTarget();
            return;
        }
        CompanionActionResult crafted = owner.craftFromBag("minecraft:furnace", 1);
        if (!crafted.success()) {
            if (crafted.message().contains("места") || crafted.message().contains("безопасное место")) finish(false, crafted.message());
            else crafting.requestWood(Phase.FURNACE);
        }
        // On success the crafting gesture finishes first; the furnace is set down on the next stage.
    }

    private BlockPos findFurnace(ServerLevel level) {
        BlockPos center = owner.blockPosition();
        BlockPos group = lots.isEmpty() ? null : lots.getFirst().pos;
        return blockEntitiesNear(level).stream().filter(pos -> !triedFurnaces.contains(pos)
                        && (!owner.ownPlacesOnly() || owner.placedByMe(pos) && owner.leashAllows(pos))
                        && lots.stream().noneMatch(lot -> lot.pos.equals(pos))
                        && (group == null || pos.distSqr(group) <= FURNACE_GROUP * FURNACE_GROUP)
                        && level.getBlockEntity(pos) instanceof FurnaceBlockEntity oven && freeFor(oven))
                .min(Comparator.comparingDouble(pos -> pos.distSqr(center))).map(BlockPos::immutable).orElse(null);
    }

    /** Someone else's furnace at work stays theirs: only an empty input slot, and an output our product can join. */
    private boolean freeFor(AbstractFurnaceBlockEntity oven) { return oven.getItem(0).isEmpty() && outputRoom(oven) > 0; }

    private int outputRoom(AbstractFurnaceBlockEntity oven) {
        ItemStack out = oven.getItem(2);
        if (out.isEmpty()) return 64;
        return ItemStack.isSameItemSameComponents(out, smelt.result()) ? (out.getMaxStackSize() - out.getCount()) / smelt.result().getCount() : 0;
    }

    private void dropLot(Lot lot) {
        triedFurnaces.add(lot.pos);
        if (lot.placed) ownFurnaceFailed = true;
        lots.remove(lot);
        heading = null; resetApproach();
    }

    private AbstractFurnaceBlockEntity oven(ServerLevel level, Lot lot) {
        return level.hasChunkAt(lot.pos) && level.getBlockEntity(lot.pos) instanceof FurnaceBlockEntity oven ? oven : null;
    }

    private void cook(ServerLevel level) {
        if (!owner.onGround() && !owner.isInWater()) return;
        for (Lot lot : List.copyOf(lots)) {
            if (oven(level, lot) != null) continue;
            if (!lot.loaded) dropLot(lot);
            else if (lot.open()) { lot.gaveUp = true; owner.workStatus("Печь на " + lot.pos.toShortString() + " пропала или выгрузилась, пока плавилось."); }
        }
        int remaining = smeltCount - loadedTotal();
        Lot next;
        if (remaining > 0) {
            next = lots.stream().filter(lot -> !lot.loaded).findFirst().orElse(null);
            if (next == null) { phase = Phase.FURNACE; heading = null; return; }
        } else {
            lots.removeIf(lot -> !lot.loaded); // spare furnaces are not needed any more
            for (Lot lot : lots) if (lot.open()) watch(level, lot);
            if (lots.stream().noneMatch(Lot::open)) { finishSmelt(level); return; }
            next = lots.stream().filter(lot -> lot.open() && needsHands(level, lot))
                    .min(Comparator.comparingDouble(lot -> owner.distanceToSqr(lot.pos.getCenter()))).orElse(null);
            if (next == null) { waitAtFurnaces(level); return; }
        }
        if (!next.pos.equals(heading)) { heading = next.pos; resetApproach(); }
        owner.movementIntent(next.pos);
        owner.getLookControl().setLookAt(next.pos.getX() + .5D, next.pos.getY() + .5D, next.pos.getZ() + .5D);
        if (owner.distanceToSqr(next.pos.getCenter()) > 3.2D * 3.2D || !CompanionMiningNavigation.visible(owner, owner.getEyePosition(), next.pos)) {
            if (approach(level, next.pos, 2.8D)) { owner.workStatus(next.loaded ? "Иду к печи на " + next.pos.toShortString() + "." : "Иду к печи на " + next.pos.toShortString() + " закладывать."); return; }
            // A cow at the furnace or a crowd on the way blocks for a moment, not for good: once more, then another furnace.
            if (!next.loaded && ++next.misses < 2) { resetApproach(); owner.workStatus("К печи на " + next.pos.toShortString() + " не подойти, попробую ещё раз."); return; }
            if (!next.loaded) dropLot(next);
            else { next.gaveUp = true; heading = null; owner.workStatus("Не могу снова подойти к печи на " + next.pos.toShortString() + "."); }
            return;
        }
        owner.getNavigation().stop();
        AbstractFurnaceBlockEntity oven = oven(level, next);
        if (!next.loaded) loadLot(level, next, oven, remaining); else serviceLot(level, next, oven);
    }

    /** Raw material into the top slot, fuel under it: the same two slots a player fills. */
    private void loadLot(ServerLevel level, Lot lot, AbstractFurnaceBlockEntity oven, int remaining) {
        if (!freeFor(oven)) { dropLot(lot); return; }
        int carried = CompanionSmelting.count(items.carriedItems(), smelt.input());
        if (carried <= 0) {
            smeltCount = loadedTotal();
            if (smeltCount == 0) finish(false, "Сырья для печи у меня не осталось.");
            return;
        }
        int unloaded = (int) lots.stream().filter(other -> !other.loaded).count();
        int amount = Math.min(Math.min((remaining + unloaded - 1) / unloaded, 64), Math.min(outputRoom(oven), carried));
        ItemStack fuelSlot = oven.getItem(1);
        int need = amount * smelt.cookTicks() - fuelSlot.getCount() * CompanionSmelting.burnTicks(fuelSlot);
        planksForFuel(need);
        var fuel = CompanionSmelting.chooseFuel(items.carriedItems(), smelt.input(), Math.min(remaining, carried), need, fuelSlot);
        if (need > 0 && fuel == null && fuelSlot.isEmpty()) {
            // Nothing to burn: chop wood first, the raw material stays in the bag meanwhile.
            owner.workStatus("Нечем топить печь. Схожу за дровами.");
            crafting.requestWood(Phase.COOK, CompanionSmelting.logsFor(need)); return;
        }
        ItemStack input = items.takeCarried(smelt.input(), amount);
        if (input.isEmpty()) return;
        lot.count = input.getCount();
        lot.outputBefore = oven.getItem(2).getCount();
        oven.setItem(0, input);
        if (fuel != null) addFuel(lot, oven, fuel);
        oven.setChanged();
        lot.loaded = true; lot.progressTick = level.getGameTime();
        // The recipe's own cooking time: ten seconds an item for ordinary smelting.
        deadline = Math.max(deadline, level.getGameTime() + (long) lot.count * smelt.cookTicks() + 20 * 120);
        if (!lot.placed) owner.rememberObserved(lot.pos);
        owner.animateAction(3, lot.pos, new ItemStack(smelt.input()));
        dev.aicompanion.CompanionDiagnostics.event(owner.diagnosticTrace(), "furnace_loaded", "entity", owner.getUUID(), "position", lot.pos,
                "input", BuiltInRegistries.ITEM.getKey(smelt.input()), "count", lot.count, "fuel", fuel == null ? "none" : BuiltInRegistries.ITEM.getKey(fuel.item()) + " x" + fuel.count(),
                "fuelSlot", BuiltInRegistries.ITEM.getKey(oven.getItem(1).getItem()) + " x" + oven.getItem(1).getCount(), "placedByCompanion", lot.placed,
                "furnace", lots.indexOf(lot) + 1, "furnaces", lots.size());
        owner.workStatus("Заложил в печь " + lot.count + " «" + itemName(smelt.input()) + "»"
                + (fuel == null ? "" : " и " + fuel.count() + " «" + itemName(fuel.item()) + "» на растопку")
                + (lots.size() > 1 ? " (печь " + (lots.indexOf(lot) + 1) + " из " + lots.size() + ")" : "") + ".");
        cooldown = 10;
    }

    /**
     * Like a player short of fuel: logs into planks first. A log burns for one and a half items, its four planks for six
     * (marathon 23.10: three logs went under eight pieces of meat, and he walked off for more wood halfway).
     */
    private void planksForFuel(int needTicks) {
        if (needTicks <= 0) return;
        long have = 0;
        for (ItemStack stack : items.carriedItems()) if (!stack.isEmpty() && !stack.is(smelt.input()) && CompanionSmelting.fuelRank(stack) >= 0) have += (long) stack.getCount() * CompanionSmelting.burnTicks(stack);
        for (ItemStack stack : items.carriedItems()) {
            if (have >= needTicks) return;
            if (stack.isEmpty() || !stack.is(ItemTags.LOGS) || stack.is(smelt.input())) continue;
            String type = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().replaceFirst("^stripped_", "").replaceFirst("_(log|wood|stem|hyphae)$", "");
            int burn = CompanionSmelting.burnTicks(stack);
            int logs = (int) Math.min(stack.getCount(), (needTicks - have + 4L * 300 - 1) / (4L * 300));
            if (logs <= 0 || !owner.craftFromBag("minecraft:" + type + "_planks", logs * 4).success()) continue;
            have += (long) logs * (4 * 300 - burn);
        }
    }

    private void addFuel(Lot lot, AbstractFurnaceBlockEntity oven, CompanionSmelting.Fuel fuel) {
        ItemStack slot = oven.getItem(1);
        int theirs = slot.getCount();
        ItemStack added = items.takeCarried(fuel.item(), fuel.count());
        if (added.isEmpty()) return;
        if (slot.isEmpty()) oven.setItem(1, added);
        else if (ItemStack.isSameItemSameComponents(slot, added)) slot.grow(added.getCount());
        else { owner.storeLoot(added); return; }
        // Fuel is added only to an empty slot or once at loading, so what lay there before is someone else's.
        lot.fuelPut = fuel.item(); lot.fuelOurs = added.getCount(); lot.fuelTheirs = theirs;
    }

    /** Reads a loaded furnace: new product counts as progress. */
    private void watch(ServerLevel level, Lot lot) {
        int ready = product(lot, oven(level, lot)) / smelt.result().getCount();
        if (ready > lot.ready) { lot.ready = ready; lot.progressTick = level.getGameTime(); }
    }

    private int left(Lot lot, AbstractFurnaceBlockEntity oven) { return oven.getItem(0).is(smelt.input()) ? oven.getItem(0).getCount() : 0; }
    private boolean lit(ServerLevel level, Lot lot) { return level.getBlockState(lot.pos).getValue(AbstractFurnaceBlock.LIT); }
    private boolean stalled(ServerLevel level, Lot lot) { return level.getGameTime() - lot.progressTick > smelt.cookTicks() * 2L + 20 * 30; }

    /** Done, out of fuel or stuck: the furnace needs him standing at it. */
    private boolean needsHands(ServerLevel level, Lot lot) {
        AbstractFurnaceBlockEntity oven = oven(level, lot);
        int left = left(lot, oven);
        return left == 0 || lot.ready >= lot.count || stalled(level, lot) || !lit(level, lot) && oven.getItem(1).isEmpty();
    }

    private void serviceLot(ServerLevel level, Lot lot, AbstractFurnaceBlockEntity oven) {
        int left = left(lot, oven);
        if (left == 0 || lot.ready >= lot.count) { emptyLot(level, lot, oven, "done"); return; }
        // Out of fire and fuel is no stuck furnace (marathon 23.10: back from the wood with fuel, the minute of his walk counted as
        // «печь перестала плавить», and half the meat came back raw). It gets fuel; stuck is only a furnace that has fuel and makes nothing.
        boolean outOfFuel = !lit(level, lot) && oven.getItem(1).isEmpty();
        if (!outOfFuel && stalled(level, lot)) { emptyLot(level, lot, oven, "stalled"); return; }
        if (!outOfFuel) return;
        planksForFuel(left * smelt.cookTicks());
        var fuel = CompanionSmelting.chooseFuel(items.carriedItems(), null, 0, left * smelt.cookTicks(), ItemStack.EMPTY);
        if (fuel == null) {
            owner.workStatus("Топливо кончилось, схожу за дровами. Сырьё пока в печи.");
            crafting.requestWood(Phase.COOK, CompanionSmelting.logsFor(left * smelt.cookTicks())); return;
        }
        addFuel(lot, oven, fuel); oven.setChanged();
        owner.animateAction(3, lot.pos, new ItemStack(fuel.item()));
        owner.workStatus("Подбросил в печь " + fuel.count() + " «" + itemName(fuel.item()) + "».");
        lot.progressTick = level.getGameTime(); cooldown = 10;
    }

    /** Nothing to do right now: stand by the furnaces and look at the one with the most left. */
    private void waitAtFurnaces(ServerLevel level) {
        Lot nearest = lots.stream().filter(Lot::open).min(Comparator.comparingDouble(lot -> owner.distanceToSqr(lot.pos.getCenter()))).orElseThrow();
        if (owner.distanceToSqr(nearest.pos.getCenter()) > 4.5 * 4.5) {
            if (!nearest.pos.equals(heading)) { heading = nearest.pos; resetApproach(); }
            owner.movementIntent(nearest.pos);
            if (approach(level, nearest.pos, 2.8D)) return;
        }
        // Waiting is no failed approach: the walk to take the product starts its own progress clock (test 23.10: after the
        // ten seconds of cooking the old clock had run out, and «не могу снова подойти» left the meat in the furnace).
        resetApproach();
        owner.getNavigation().stop();
        Lot busiest = lots.stream().filter(Lot::open).max(Comparator.comparingInt(lot -> lot.count - lot.ready)).orElse(nearest);
        owner.getLookControl().setLookAt(busiest.pos.getX() + .5D, busiest.pos.getY() + .5D, busiest.pos.getZ() + .5D);
        int furnaces = (int) lots.stream().filter(lot -> lot.loaded).count();
        owner.workStatus("Плавлю «" + itemName(smelt.input()) + "»" + (furnaces > 1 ? " в " + furnaces + " печах" : "") + ": готово " + smeltReady() + " из " + smeltCount + ".");
    }

    /** Our product: what the output slot gained since loading. */
    private int product(Lot lot, AbstractFurnaceBlockEntity oven) {
        ItemStack out = oven.getItem(2);
        return ItemStack.isSameItemSameComponents(out, smelt.result()) ? Math.max(0, out.getCount() - lot.outputBefore) : 0;
    }

    /** Like a player at the furnace: the product, raw material still left and his own unburnt fuel go back into the bag. */
    private void emptyLot(ServerLevel level, Lot lot, AbstractFurnaceBlockEntity oven, String reason) {
        int product = product(lot, oven);
        lot.taken = items.takeFromSlot(oven, 2, product);
        int raw = left(lot, oven);
        lot.rawBack = items.takeFromSlot(oven, 0, raw);
        int fuelBack = lot.fuelPut != null && oven.getItem(1).is(lot.fuelPut)
                ? items.takeFromSlot(oven, 1, Math.min(lot.fuelOurs, oven.getItem(1).getCount() - lot.fuelTheirs)) : 0;
        oven.setChanged();
        lot.emptied = true; lot.ready = lot.taken / smelt.result().getCount();
        lot.stuck = lot.taken < product || lot.rawBack < raw; lot.stalled = "stalled".equals(reason);
        owner.animateAction(3, lot.pos, smelt.result());
        level.playSound(null, lot.pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, .4F, 1F);
        dev.aicompanion.CompanionDiagnostics.event(owner.diagnosticTrace(), "furnace_emptied", "entity", owner.getUUID(), "position", lot.pos,
                "reason", reason, "product", product, "taken", lot.taken, "rawBack", lot.rawBack, "rawLeft", raw - lot.rawBack, "fuelBack", fuelBack);
        owner.workStatus("Забрал из печи " + lot.taken + " «" + itemName(smelt.result().getItem()) + "».");
        cooldown = 5;
    }

    private void finishSmelt(ServerLevel level) {
        int per = smelt.result().getCount();
        int taken = lots.stream().mapToInt(lot -> lot.taken).sum(), rawBack = lots.stream().mapToInt(lot -> lot.rawBack).sum();
        List<Lot> used = lots.stream().filter(lot -> lot.loaded).toList();
        String inName = itemName(smelt.input()), outName = itemName(smelt.result().getItem());
        String where = used.size() == 1 ? " Печь на " + used.getFirst().pos.toShortString() + "."
                : " Печи: " + String.join("; ", used.stream().map(lot -> lot.pos.toShortString()).toList()) + ".";
        String stuck = used.stream().anyMatch(lot -> lot.stuck) ? " Часть не влезла в сумку и осталась в печи." : "";
        int more = CompanionSmelting.count(items.carriedItems(), smelt.input()) - rawBack;
        String rest = smeltAll && more > 0 ? " В сумке ещё " + more + " «" + inName + "» — больше стопки за раз не беру." : "";
        if (ironPrep && taken > 0) {
            // Iron for a tool: the ingots are counted from the bag in the next step; furnaces stay where they are.
            owner.noteDeed("Для «" + itemName(ironTool) + "» переплавил " + taken / per + " «" + inName + "» и забрал " + taken + " «" + outName + "»." + where + stuck, true);
            oreMining.ironStageDone(); return;
        }
        if (stuck.isEmpty() && used.stream().noneMatch(lot -> lot.gaveUp) && smeltCount == requestedCount && taken >= smeltCount * per) {
            if (mixedMeat) {
                mixedCooked += taken; mixedKinds++;
                mixedInputs.add(smelt.input());
                var next = CompanionSmelting.resolve(level, CompanionGame.ANY_MEAT,
                        items.carriedItems().stream().filter(stack -> !mixedInputs.contains(stack.getItem())).toList());
                if (next != null && mixedKinds < CompanionGame.Kind.values().length) {
                    // One verified load at a time, one workId for the whole request. Never burn someone else's input.
                    smelt = next; smeltCount = requestedCount = Math.min(64, CompanionSmelting.count(items.carriedItems(), next.input()));
                    lots.clear(); triedFurnaces.clear(); heading = furnaceRoom = null;
                    ownFurnaceFailed = furnacesShort = false; furnacesPlaced = 0; benchClimbs = 0;
                    phase = Phase.FURNACE; resetTarget();
                    owner.workStatus("Пожарил одну часть мяса, готовлю остальное.");
                    return;
                }
                String excess = CompanionSmelting.resolve(level, CompanionGame.ANY_MEAT, items.carriedItems()) == null ? ""
                        : " Сырьё сверх стопки каждого вида осталось в сумке.";
                finish(true, "Пожарил и забрал " + mixedCooked + " порций мяса (видов: " + mixedKinds + ")." + excess + " В сумке: " + owner.getStoredItemsSummary() + ".");
                return;
            }
            finish(true, "Переплавил " + smeltCount + " «" + inName + "»" + (used.size() > 1 ? " в " + used.size() + " печах" : "") + " и забрал " + taken
                    + " «" + outName + "»." + where + rest + " В сумке: " + owner.getStoredItemsSummary() + "."); return;
        }
        String why = used.stream().anyMatch(lot -> lot.stalled) ? " Печь перестала плавить."
                : furnacesShort ? " Свободных печей на всё не хватило, остальное сырьё у меня."
                : smeltCount < requestedCount ? " Сырья было только " + smeltCount + " — на остальное не хватает." : "";
        finish(false, "Переплавил " + taken / per + " из " + requestedCount + " «" + inName + "», забрал из печи " + taken + " «" + outName + "»." + why
                + (rawBack > 0 ? " Непереплавленное сырьё (" + rawBack + ") забрал обратно." : "") + stuck);
    }

    private boolean giftMatches(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (owner.goals().ownsWork(taskId) && !CompanionGoals.usable(stack)) return false;
        if ("logs".equals(requestedItem)) return stack.is(ItemTags.LOGS);
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(requestedItem);
    }

    /** Walk up to the player and toss the items at their feet: a real item on the ground, picked up by the player. */
    private void handOver(ServerLevel level) {
        var player = owner.getOwnerPlayer();
        if (player == null) { finish(false, "Не вижу тебя рядом, отдать не могу."); return; }
        int carried = 0;
        for (int slot = 0; slot < owner.getContainerSize(); slot++) if (giftMatches(owner.getItem(slot))) carried += owner.getItem(slot).getCount();
        if (giftMatches(owner.getMainHandItem())) carried += owner.getMainHandItem().getCount();
        if (carried == 0) { finish(false, "У меня нет того, что нужно отдать."); return; }
        owner.movementIntent(player.blockPosition());
        owner.getLookControl().setLookAt(player, 30, 30);
        if (owner.distanceToSqr(player) > 3.2 * 3.2 || !owner.hasLineOfSight(player)) {
            if (orderHasStalled(level, 240)) { finish(false, "Не смог к тебе подойти, вещи остались у меня."); return; }
            owner.workStatus("Несу тебе вещи.");
            Path route = owner.getNavigation().createPath(player, 1);
            if (route != null && route.getNodeCount() > 1) CompanionMiningNavigation.walk(owner, route); else if (owner.onGround() && !owner.isInWater()) owner.getNavigation().stop();
            return;
        }
        owner.getNavigation().stop();
        int left = Math.min(requestedCount, carried), given = 0;
        ItemStack shown = ItemStack.EMPTY;
        // Give from the bag first; the tool in his own hand goes only if nothing else is left.
        for (int index = 0; index <= owner.getContainerSize() && left > 0; index++) {
            int slot = index == owner.getContainerSize() ? -1 : index;
            ItemStack stack = slot < 0 ? owner.getMainHandItem() : owner.getItem(slot);
            if (!giftMatches(stack)) continue;
            ItemStack gift = stack.split(Math.min(left, stack.getCount()));
            if (slot < 0 && stack.isEmpty()) owner.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            var operation = owner.goals().prepareGift(taskId, gift);
            shown = gift.copyWithCount(1);
            var eye = owner.getEyePosition();
            var toward = player.position().add(0, .3, 0).subtract(eye).normalize().scale(.3);
            var entity = new net.minecraft.world.entity.item.ItemEntity(level, eye.x, eye.y - .3, eye.z, gift);
            entity.setDeltaMovement(toward.x, .2, toward.z);
            entity.setPickUpDelay(10);
            entity.setTarget(player.getUUID()); // only the player can pick it up
            entity.getPersistentData().putBoolean("aicompanion:gift", true);
            if (!level.addFreshEntity(entity)) {
                // Spawn refusal must not consume the real item or acknowledge delivery.
                stack.grow(gift.getCount());
                if (slot < 0) owner.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, stack);
                finish(false, "Не удалось бросить предмет. Он остался у меня."); return;
            }
            left -= gift.getCount(); given += gift.getCount();
            owner.goals().giftSpawned(operation, entity.getUUID());
        }
        owner.setChanged();
        owner.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        owner.animateAction(3, player.blockPosition(), shown);
        level.playSound(null, owner.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, .4F, .8F);
        finish(CompanionActionResult.ok("Бросил тебе " + given + " «" + shown.getHoverName().getString() + "» — подбери у ног.")
                .withEvidence(new CompanionActionResult.Evidence(taskId, "give_items", requestedItem, given, player.getUUID(),
                        owner.goals().ownsWork(taskId) ? owner.goals().operation() : null, owner.goals().ownsWork(taskId) ? owner.goals().giftEntity() : null)));
    }

    private void packBench(ServerLevel level) {
        if (packTarget == null) {
            boolean pickedUp = items.countCarried(Items.CRAFTING_TABLE) > packedBenchBefore;
            String note = pickedUp ? " Походный верстак забрал с собой."
                    : " Разобранный верстак пока не подобрал" + (packedBenchFrom == null ? "." : " на " + packedBenchFrom.toShortString() + ".");
            finish(new CompanionActionResult(packResult.success(), packResult.message() + note, packResult.status(), packResult.evidence())); return;
        }
        if (!level.hasChunkAt(packTarget) || !level.getBlockState(packTarget).is(Blocks.CRAFTING_TABLE)) { packTarget = null; finish(packResult); return; }
        if (!owner.onGround()) return;
        String left = " Походный верстак оставил на " + packTarget.toShortString() + ".";
        if (orderHasStalled(level, 300)) { BlockPos at = packTarget; packTarget = null;
            finish(new CompanionActionResult(packResult.success(), packResult.message() + " Походный верстак оставил на " + at.toShortString() + ".", packResult.status(), packResult.evidence())); return; }
        owner.movementIntent(packTarget);
        owner.getLookControl().setLookAt(packTarget.getX() + .5D, packTarget.getY() + .5D, packTarget.getZ() + .5D);
        int reach = items.reachBlock(level, packTarget, true);
        if (reach < 0) { packTarget = null; finish(new CompanionActionResult(packResult.success(), packResult.message() + left, packResult.status(), packResult.evidence())); return; }
        if (reach == 0) return;
        if (!CompanionMiningNavigation.preservesAutonomousReturn(owner, packTarget)) {
            packTarget = null; finish(new CompanionActionResult(packResult.success(), packResult.message() + left + " Он нужен для обратного хода.", packResult.status(), packResult.evidence())); return;
        }
        owner.getNavigation().stop();
        BlockState state = level.getBlockState(packTarget);
        owner.selectToolForBlock(state);
        owner.workStatus("Забираю свой походный верстак.");
        if (!breaking.tick(level, packTarget)) return;
        ItemStack tool = owner.getMainHandItem();
        var drops = Block.getDrops(state, level, packTarget, level.getBlockEntity(packTarget), owner, tool);
        if (!items.canStore(drops)) { packTarget = null;
            finish(new CompanionActionResult(packResult.success(), packResult.message() + left + " В сумке нет места.", packResult.status(), packResult.evidence())); return; }
        if (!level.destroyBlock(packTarget, false, owner, 512)) return;
        tool.getItem().mineBlock(tool, level, state, packTarget, owner);
        owner.dropHarvest(packTarget, drops);
        breaking.reset();
        packedBenchFrom = packTarget;
        packTarget = null; // finished on the next tick, once the dropped bench has been picked up
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
        clearCracks(); target = null; resetApproach();
        // The old target is no destination any more: a self-rescue must not take it for one while he looks for the next.
        owner.movementIntent(null);
    }
    void resetApproach() { path = null; standCells = Set.of(); approachMoved = null; bestDistance = Double.MAX_VALUE; lastProgressTick = owner.level().getGameTime(); }
    void clearCracks() { breaking.reset(); }
    private void closeChest() {
        if (openedChest != null && owner.level() instanceof ServerLevel level && level.hasChunkAt(openedChest)
                && level.getBlockState(openedChest).getBlock() instanceof ChestBlock) {
            animateChest(level, openedChest, 0);
            level.playSound(null, openedChest, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5F, 1.0F);
        }
        openedChest = null;
    }
    private void animateChest(ServerLevel level, BlockPos pos, int open) {
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
        message += furnaceNote();
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

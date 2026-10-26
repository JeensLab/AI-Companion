package dev.aicompanion.entity;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.CompanionIdentity;
import dev.aicompanion.inventory.CompanionMenu;
import dev.aicompanion.inventory.CompanionEquipmentContainer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** The player's persistent in-world companion. */
public final class CompanionEntity extends PathfinderMob implements Container {
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData.defineId(
            CompanionEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> DATA_FOLLOWING = SynchedEntityData.defineId(
            CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_PROTECTING = SynchedEntityData.defineId(
            CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_ROAMING = SynchedEntityData.defineId(
            CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_GATHERING_LOGS = SynchedEntityData.defineId(
            CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_LOGS_REMAINING = SynchedEntityData.defineId(
            CompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_LAST_TASK_RESULT = SynchedEntityData.defineId(
            CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_INVENTORY_SUMMARY = SynchedEntityData.defineId(
            CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_INVENTORY_AI = SynchedEntityData.defineId(
            CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_STORAGE_CHEST = SynchedEntityData.defineId(
            CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_PLACES = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_DEEDS = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_ACTIVITY = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_MINING = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_ACTION_UNTIL = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<ItemStack> DATA_ACTION_ITEM = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<String> DATA_SOCIAL = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_SOCIAL_SERIAL = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_SOCIAL_TIME = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> DATA_AUTONOMY = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DATA_SAY = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_SAY_SERIAL = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_SAY_TIME = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_EXPRESSION = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_SOCIAL_SAFE = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private final dev.aicompanion.CompanionExpressionState expressions = new dev.aicompanion.CompanionExpressionState();
    private boolean nearbySocialThreat;
    private boolean nearbyExplosiveThreat;
    private static final double LOG_INTERACTION_RANGE_SQR = 16.0D;
    private UUID ownerId;
    private boolean following = true;
    private boolean protecting;
    private boolean roaming;
    private BlockPos roamOrigin;
    private int searchRadius = 64;
    private boolean gatheringLogs;
    private boolean gatheringDirt;
    private int logsRemaining;
    private int requestedLogs;
    private int woodTaskVersion;
    private int dirtTaskVersion;
    private boolean supportSoilTask;
    private int savedWoodRequested;
    private int savedWoodRemaining;
    private boolean lastTaskSucceeded;
    private int inventoryViewers;
    private final NonNullList<ItemStack> companionInventory = NonNullList.withSize(27, ItemStack.EMPTY);
    private final CompanionPillar temporaryPillar = new CompanionPillar(this);
    private final CompanionWork work = new CompanionWork(this);
    private final CompanionBlockBreaking woodBreaking = new CompanionBlockBreaking(this);
    private final CompanionBlockBreaking dirtBreaking = new CompanionBlockBreaking(this);
    private final CompanionLoot loot = new CompanionLoot(this);
    private final CompanionRecovery recovery = new CompanionRecovery(this);
    private final CompanionTravel travel = new CompanionTravel(this);
    CompanionTravel travel() { return travel; }
    private BlockPos movementIntent;
    private boolean chunkLoading = true;
    private boolean autonomousSpeech = true;
    private String preferredWeapon = "";
    private net.minecraft.world.phys.Vec3 previousActivityPosition;
    private boolean activityMoved;
    private BlockPos lastPlacedPosition;
    BlockPos lastPlacedPosition() { return lastPlacedPosition; }
    private final CompanionSocial social = new CompanionSocial(this);
    private final CompanionDiagnosticState diagnostics = new CompanionDiagnosticState(this);
    private final CompanionPlaces places = new CompanionPlaces(this);
    private final CompanionDeeds deeds = new CompanionDeeds();
    private final CompanionAutonomy autonomy = new CompanionAutonomy(this);
    private final CompanionGoals goals = new CompanionGoals(this);
    public CompanionGoals goals() { return goals; }
    private final CompanionLighting lighting = new CompanionLighting(this);
    public CompanionLighting lighting() { return lighting; }
    /** «Порядок и запас»: deeds of his own while the player asks for nothing (CompanionAutonomy). */
    public CompanionAutonomy autonomy() { return autonomy; }
    /** Client and server: the mode is on (for «Поведение» and the snapshot). */
    public boolean autonomyEnabled() { return level().isClientSide ? entityData.get(DATA_AUTONOMY) : autonomy.enabled(); }
    void syncAutonomy() { if (!level().isClientSide) entityData.set(DATA_AUTONOMY, autonomy.enabled()); }
    /** A rare autonomous notice, shown and voiced by the client. Routine progress stays in workStatus and the journal. */
    void autonomySay(String text) {
        entityData.set(DATA_SAY, text); entityData.set(DATA_SAY_SERIAL, entityData.get(DATA_SAY_SERIAL) + 1); entityData.set(DATA_SAY_TIME, level().getGameTime());
        workStatus(text);
    }
    public String autonomyLine() { return entityData.get(DATA_SAY); }
    public int autonomyLineSerial() { return entityData.get(DATA_SAY_SERIAL); }
    public long autonomyLineTime() { return entityData.get(DATA_SAY_TIME); }
    /** Finish/cancel through the usual work path; autonomous mode suppresses following while at work. */
    void endAutonomousDeed() { finishFollowing(); }
    /** His own drops lying within this distance of a spot and not on the pickup list yet are taken up again. */
    int reclaimOwnDrops(BlockPos center, int radius) { int found = loot.reclaim(center, radius); if (found > 0) syncWorkState(); return found; }
    /** A block he set down himself (his chest, furnace, bench), as remembered. */
    boolean placedByMe(BlockPos pos) { return places.placedByCompanion(pos); }
    /** Blocks of this kind he set down himself, as remembered. */
    List<BlockPos> placedByMe(String block) { return places.placed(block); }
    /** While a deed of his own runs: only his own chests and furnaces, never the player's. */
    boolean ownPlacesOnly() { return autonomy.running(); }
    /** Autonomous targets stay within the remembered work area. */
    boolean leashAllows(BlockPos pos) {
        if (!autonomy.running()) return true;
        var center = autonomy.center();
        double dx = pos.getX() - center.getX(), dz = pos.getZ() - center.getZ();
        return dx * dx + dz * dz <= (double) CompanionAutonomy.LEASH * CompanionAutonomy.LEASH;
    }
    /** The orders of the player wait for real work, not for a deed of his own (it ends at the order). */
    public boolean busyForOrders() { return (hasActiveWorkOrder() || isGatheringTask()) && !autonomy.running(); }
    public String getPlacesForAi() { return level().isClientSide ? entityData.get(DATA_PLACES) : places.summary(); }
    public String getActivityForAi() {
        if (level().isClientSide) return entityData.get(DATA_ACTIVITY);
        return (recovery.active() ? "пытается выбраться" : temporaryPillar.isRecovering() ? "спускается с опоры"
                : work.isActive() ? work.currentDescription() : gatheringLogs ? "собирает дерево" : gatheringDirt ? "собирает землю"
                : protecting ? "режим охраны" : following ? "режим следования" : roaming ? "гуляет" : "ожидает")
                + "; позиция=" + blockPosition().toShortString() + "; за последнюю секунду=" + (activityMoved ? "перемещался" : "стоял")
                + "; маршрут=" + (getNavigation().isDone() ? "сейчас не выполняется" : "выполняется")
                + "; добыча=" + (isMiningAnimation() ? "есть анимация" : "нет анимации")
                + "; здоровье=" + Math.round(getHealth()) + "/" + Math.round(getMaxHealth())
                + (inventoryViewers > 0 ? "; открыт инвентарь, действия приостановлены" : "")
                + "; самостоятельность=" + (!autonomy.enabled() ? "выкл." : autonomy.running() ? "вкл., сейчас своё дело без просьбы: " + autonomy.deedName()
                        : autonomy.held() ? "вкл., на паузе по приказу игрока" : "вкл., работает на месте")
                + (goals.state() == null ? "" : "; сохранённая цель=" + goals.status())
                + "; комментарий стадии=" + getLastTaskResult();
    }
    public String taskActivity() {
        if (getTarget() != null && getTarget().isAlive()) return "Сражается с «" + getTarget().getName().getString() + "»";
        if (recovery.active() || temporaryPillar.isRecovering()) return "Выбирается в безопасное место";
        if (work.isActive()) return work.taskDescription();
        return gatheringLogs ? "Собирает дерево · осталось " + logsRemaining
                : gatheringDirt ? "Собирает землю · осталось " + logsRemaining
                : protecting ? "Охраняет тебя" : roaming ? "Осматривается" : following ? "Следует за тобой" : "Ожидает";
    }
    public void rememberPlaced(BlockPos pos) { places.remember(pos, true); refreshPlaces(); }
    void rememberObserved(BlockPos pos) { places.remember(pos, false); refreshPlaces(); }
    void refreshPlaces() { if (!level().isClientSide) entityData.set(DATA_PLACES, places.summary()); refreshDeeds(); }
    /** Recent finished deeds with their real outcome, oldest first («12 мин назад: не вышло — …»). */
    public String getDeedsForAi() { return level().isClientSide ? entityData.get(DATA_DEEDS) : deeds.summary(level().getGameTime()); }
    public int deedCount() { return deeds.size(); }
    void noteDeed(String message, boolean ok) {
        if (level().isClientSide) return;
        deeds.add(level().getGameTime(), (autonomy.running() ? "Сам, без просьбы: " : "") + message, ok); refreshDeeds();
        expressions.react(ok ? dev.aicompanion.CompanionExpression.PLEASED : dev.aicompanion.CompanionExpression.WORRIED, level().getGameTime(), 100);
        // Failure already has its short work notice. Do not generate a second, model-written apology about it.
        if (ok) social.observe("deed:success", "Настоящий итог моего дела: " + CompanionDeeds.clean(message));
    }
    private void refreshDeeds() { if (!level().isClientSide) entityData.set(DATA_DEEDS, deeds.summary(level().getGameTime())); }
    java.util.List<BlockPos> knownCraftingTables() { places.discoverTables(); refreshPlaces(); return places.tables(true); }
    boolean hasKnownCraftingTable() { return !places.tables(false).isEmpty(); }
    /** When each item was last crafted. A request repeated while the first answer was slow must not make a second tool. */
    private final java.util.Map<String, Long> madeAt = new java.util.HashMap<>();
    static final long REPEAT_WINDOW = 20 * 120;
    /** A single tool or armour piece that was just made and is still carried: a repeat of the same order is not a new one. */
    public CompanionActionResult alreadyMade(String itemId) {
        var id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return null;
        var item = BuiltInRegistries.ITEM.get(id);
        if (new ItemStack(item).getMaxStackSize() != 1) return null;
        long at = madeAt.getOrDefault(itemId, Long.MIN_VALUE / 2);
        if (level().getGameTime() - at > REPEAT_WINDOW) return null;
        boolean carried = getMainHandItem().is(item) || getOffhandItem().is(item);
        for (ItemStack stack : companionInventory) carried |= stack.is(item);
        if (!carried) return null;
        return recordActionResult(CompanionActionResult.ok("«" + new ItemStack(item).getHoverName().getString()
                + "» я только что сделал, она у меня. Ещё одну не делаю — если нужна вторая, попроси отдельно чуть позже."));
    }
    /** Benches set down away from the base during work; the work order picks them up again. Not saved: a reload leaves them standing. */
    private final java.util.List<BlockPos> fieldBenches = new java.util.ArrayList<>();
    BlockPos takeFieldBenchNear(double blocks) {
        fieldBenches.removeIf(pos -> !level().hasChunkAt(pos) || !level().getBlockState(pos).is(Blocks.CRAFTING_TABLE));
        for (BlockPos pos : fieldBenches) if (pos.distSqr(blockPosition()) <= blocks * blocks) { fieldBenches.remove(pos); return pos; }
        return null;
    }
    /** Set by a work order whose remembered bench could not be reached on foot; never saved. */
    private boolean newBenchAllowed;
    void allowNewBench(boolean allowed) { newBenchAllowed = allowed; }
    boolean hasKnownCraftingTableWithin(int blocks) {
        return places.tables(false).stream().anyMatch(pos -> pos.distSqr(blockPosition()) <= (double) blocks * blocks);
    }

    public void diagnosticBind(String trace) { diagnostics.bind(trace); }
    public String diagnosticTrace() { return diagnostics.trace(); }
    public void diagnosticSnapshot(String event) { if (dev.aicompanion.CompanionDiagnostics.enabled()) diagnostics.snapshot(event); }
    String diagnosticState() {
        return "wood=" + gatheringLogs + ",dirt=" + gatheringDirt + ",remaining=" + logsRemaining
                + ",following=" + following + ",protecting=" + protecting + ",roaming=" + roaming
                + ",inventoryViewers=" + inventoryViewers + ",supportSoil=" + supportSoilTask
                + ",pillar=" + temporaryPillar.isActive() + ",cleanup=" + temporaryPillar.isRecovering()
                + ",recovery=" + recovery.active() + ",loot=" + loot.diagnosticState() + ",movementIntent=" + movementIntent
                + ",target=" + (getTarget() == null ? "none" : getTarget().getType().toString() + ":" + getTarget().getId())
                + ",goals=" + goalSelector.getAvailableGoals().stream().filter(goal -> goal.isRunning()).map(goal -> goal.getGoal().getClass().getSimpleName()).toList()
                + ",woodBreaking=" + woodBreaking.diagnosticState() + ",dirtBreaking=" + dirtBreaking.diagnosticState()
                + ",work=" + work.diagnosticState();
    }

    /** The game's ground navigation, with the walking search that also climbs out of water onto a bank (CompanionNodeEvaluator). */
    @Override protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) {
        var navigation = new net.minecraft.world.entity.ai.navigation.GroundPathNavigation(this, level) {
            @Override protected net.minecraft.world.level.pathfinder.PathFinder createPathFinder(int maxVisitedNodes) {
                this.nodeEvaluator = new CompanionNodeEvaluator();
                this.nodeEvaluator.setCanPassDoors(true);
                return new net.minecraft.world.level.pathfinder.PathFinder(this.nodeEvaluator, maxVisitedNodes);
            }
        };
        // Twice the game's budget of searched cells (marathon 23.10: the coal down in the cave, 45 steps by its narrow way, was
        // «out of reach» from the meadow above — the search spent its 2048 cells on the meadow first).
        navigation.setMaxVisitedNodesMultiplier(2F);
        return navigation;
    }

    public CompanionEntity(EntityType<? extends CompanionEntity> type, Level level) {
        super(type, level);
        this.setCustomName(Component.literal(CompanionIdentity.DEFAULT_NAME));
        this.setCustomNameVisible(true);
        this.setPersistenceRequired();
        this.setCanPickUpLoot(false);
        this.moveControl = new SwimmingMoveControl();
        if (getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation ground) ground.setCanOpenDoors(true);
        // Water like a player: wading and swimming are slower, not something to avoid at eight times the cost, and a shore is no
        // danger (marathon 23.10, «берег»: back from the island the route over the sandbar did not fit into one search at the
        // game's price of water, and the part of it that came out led him off into the sea).
        setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER, 2.0F);
        setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER_BORDER, 0.0F);
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(0, new net.minecraft.world.entity.ai.goal.OpenDoorGoal(this, true));
        this.goalSelector.addGoal(1, new RecoverSupportsGoal());
        this.goalSelector.addGoal(1, new RecoverRouteGoal());
        this.goalSelector.addGoal(2, new PlayerPaceAttackGoal());
        this.goalSelector.addGoal(2, new PickupLootGoal());
        this.goalSelector.addGoal(3, new GatherLogsGoal(this));
        this.goalSelector.addGoal(3, new GatherDirtGoal(this));
        this.goalSelector.addGoal(3, new WorkGoal());
        this.goalSelector.addGoal(4, new FollowPlayerGoal(this));
        this.goalSelector.addGoal(4, new RoamGoal());
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, ServerPlayer.class, 8.0F));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.65D) {
            @Override public boolean canUse() { return following && !protecting && !autonomy.staysAtWork() && inventoryViewers == 0 && super.canUse(); }
            @Override public boolean canContinueToUse() { return !autonomy.staysAtWork() && super.canContinueToUse(); }
            @Override protected net.minecraft.world.phys.Vec3 getPosition() {
                var position = super.getPosition();
                var owner = getOwnerPlayer();
                return position != null && owner != null && position.distanceToSqr(owner.position()) <= 64 ? position : null;
            }
        });
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.getNavigation().setMaxVisitedNodesMultiplier(3.0F);
        // Review 22.10, В1: vanilla «hit back whoever hurt me» also hit back the player after a stray blow.
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override public boolean canUse() { return super.canUse() && fairTarget(getLastHurtByMob(), true); }
        });
        this.targetSelector.addGoal(1, new OwnerDefenseGoal());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, 10,
                true, false, target -> protecting ? isThreateningOwner(target) : comesForOwner(target)));
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && player.getUUID().equals(getOwnerId())) {
            if (!level().isClientSide && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(new SimpleMenuProvider((containerId, inventory, ignored) ->
                        new CompanionMenu(containerId, inventory, this), Component.literal("Компаньон")), buffer -> buffer.writeVarInt(getId()));
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override public int getContainerSize() { return companionInventory.size(); }
    @Override public boolean isEmpty() { return companionInventory.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return companionInventory.get(slot); }
    @Override public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = ContainerHelper.removeItem(companionInventory, slot, amount);
        if (!stack.isEmpty()) setChanged();
        return stack;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = ContainerHelper.takeItem(companionInventory, slot);
        if (!stack.isEmpty()) setChanged();
        return stack;
    }
    @Override public void setItem(int slot, ItemStack stack) {
        companionInventory.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
    }
    @Override public void setChanged() { refreshInventorySummary(); }
    @Override public boolean stillValid(Player player) {
        return isAlive() && player.getUUID().equals(getOwnerId()) && player.distanceToSqr(this) < 64.0D;
    }
    @Override public void clearContent() { companionInventory.clear(); setChanged(); }
    @Override public void startOpen(Player player) { if (!level().isClientSide) { inventoryViewers++; getNavigation().stop(); } }
    @Override public void stopOpen(Player player) { if (!level().isClientSide) inventoryViewers = Math.max(0, inventoryViewers - 1); }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OWNER, Optional.empty());
        builder.define(DATA_FOLLOWING, true);
        builder.define(DATA_PROTECTING, false);
        builder.define(DATA_ROAMING, false);
        builder.define(DATA_GATHERING_LOGS, false);
        builder.define(DATA_LOGS_REMAINING, 0);
        builder.define(DATA_LAST_TASK_RESULT, "");
        builder.define(DATA_INVENTORY_SUMMARY, "пусто");
        builder.define(DATA_INVENTORY_AI, "пусто");
        builder.define(DATA_STORAGE_CHEST, "пока не использовал");
        builder.define(DATA_PLACES, "известных рабочих мест нет");
        builder.define(DATA_DEEDS, "пока нет");
        builder.define(DATA_ACTIVITY, "состояние ещё не обновлено");
        builder.define(DATA_MINING, false);
        builder.define(DATA_ACTION, 0); builder.define(DATA_ACTION_UNTIL, 0L); builder.define(DATA_ACTION_ITEM, ItemStack.EMPTY);
        builder.define(DATA_SOCIAL, ""); builder.define(DATA_SOCIAL_SERIAL, 0); builder.define(DATA_SOCIAL_TIME, 0L);
        builder.define(DATA_AUTONOMY, false); builder.define(DATA_SAY, ""); builder.define(DATA_SAY_SERIAL, 0); builder.define(DATA_SAY_TIME, 0L);
        builder.define(DATA_EXPRESSION, 0); builder.define(DATA_SOCIAL_SAFE, false);
    }

    public static void createAttributes(net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(AICompanionMod.COMPANION.get(), createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.FOLLOW_RANGE, 128.0D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0D)
                .build());
    }

    /**
     * Client only: whether his owner wants him outlined through walls right now (set by the client marker, CompanionMarker).
     * The server never asks it, so nothing is synced and nobody else sees the outline.
     */
    public static java.util.function.Predicate<CompanionEntity> clientOutline = companion -> false;
    @Override public boolean isCurrentlyGlowing() { return super.isCurrentlyGlowing() || level().isClientSide && clientOutline.test(this); }
    @Override public int getTeamColor() { return level().isClientSide && getTeam() == null && clientOutline.test(this) ? 0xFFD54A : super.getTeamColor(); }

    public UUID getOwnerId() {
        return entityData.get(DATA_OWNER).orElse(ownerId);
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
        entityData.set(DATA_OWNER, Optional.ofNullable(ownerId));
    }

    public void setCompanionName(String name) {
        String cleaned = CompanionIdentity.cleanName(name);
        if (getCustomName() == null || !cleaned.equals(getCustomName().getString())) setCustomName(Component.literal(cleaned));
    }

    /** Farthest search for resources (23.10: raised from 96 now that far targets are walked to in legs, CompanionTravel). */
    public static final int MAX_SEARCH_RADIUS = 128;
    public int getSearchRadius() { return searchRadius; }
    public void setSearchRadius(int radius) {
        searchRadius = Math.max(16, Math.min(MAX_SEARCH_RADIUS, radius));
        var range = getAttribute(Attributes.FOLLOW_RANGE);
        if (range != null && range.getBaseValue() != searchRadius + 16) range.setBaseValue(searchRadius + 16);
    }

    public boolean isRoaming() { return entityData.get(DATA_ROAMING); }
    public void startRoaming() {
        setFollowing(false);
        roaming = true;
        roamOrigin = blockPosition();
        entityData.set(DATA_ROAMING, true);
    }

    public boolean isFollowing() {
        return entityData.get(DATA_FOLLOWING);
    }

    public void setFollowing(boolean following) {
        // Following resumes pickup; «стой» still stops movement and pickup until the next real order.
        setFollowing(following, following);
        if (following) loot.resumeCollection();
    }
    private void setFollowing(boolean following, boolean keepHarvest) {
        woodBreaking.reset(); dirtBreaking.reset();
        if (!keepHarvest) loot.cancel();
        recovery.cancel(); movementIntent = null;
        supportSoilTask = false;
        work.cancel();
        temporaryPillar.requestCleanup();
        roaming = false;
        entityData.set(DATA_ROAMING, false);
        this.following = following;
        entityData.set(DATA_FOLLOWING, following);
        gatheringLogs = false;
        gatheringDirt = false;
        logsRemaining = 0;
        entityData.set(DATA_GATHERING_LOGS, temporaryPillar.isRecovering());
        entityData.set(DATA_LOGS_REMAINING, 0);
        protecting = false;
        entityData.set(DATA_PROTECTING, false);
        setTarget(null);
        getNavigation().stop();
    }

    public boolean isProtecting() {
        return entityData.get(DATA_PROTECTING);
    }

    public void setProtecting(boolean protecting) {
        woodBreaking.reset(); dirtBreaking.reset(); loot.cancel(); recovery.cancel(); movementIntent = null;
        supportSoilTask = false;
        work.cancel();
        temporaryPillar.requestCleanup();
        roaming = false;
        entityData.set(DATA_ROAMING, false);
        this.protecting = protecting;
        entityData.set(DATA_PROTECTING, protecting);
        if (protecting) {
            following = false;
            entityData.set(DATA_FOLLOWING, false);
            gatheringLogs = false;
            gatheringDirt = false;
            logsRemaining = 0;
            entityData.set(DATA_GATHERING_LOGS, temporaryPillar.isRecovering());
            entityData.set(DATA_LOGS_REMAINING, 0);
            getNavigation().stop();
        } else {
            setFollowing(true);
        }
        setTarget(null);
    }

    public boolean isGatheringLogs() {
        return entityData.get(DATA_GATHERING_LOGS);
    }

    public boolean isGatheringTask() { return isGatheringLogs(); }

    public int getTemporarySupportCount() { return temporaryPillar.supportCount(); }
    public boolean isRecoveringSupports() { return temporaryPillar.isRecovering(); }

    public boolean isWorking() { return work.isActive(); }
    /** Read only, for the GameTest observer: a self-rescue running, the work's job/phase, and a visible action in hand. */
    public boolean isSelfRescuing() { return recovery.active(); }
    /** Read only: his own drops still to pick up (after «стоп» too: nothing of his is left lying). */
    public boolean isCollectingDrops() { return loot.hasPending(); }
    public String workPhase() { return work.watchPhase(); }
    public String watchDetail() { return diagnosticState(); }
    public boolean isVisiblyBusy() { return isSwingingAtBlock() || isMiningAnimation() || actionAnimation() != 0 || eatTicks > 0 || temporaryPillar.isActive() || temporaryPillar.isRecovering(); }
    public void enableProtectionForWork() { protecting = true; entityData.set(DATA_PROTECTING, true); }
    public long activeWorkId() { return work.activeTaskId(); }
    public CompanionActionResult workResult(long id) { return work.taskResult(id); }
    public boolean hasActiveWorkOrder() {
        return gatheringLogs || gatheringDirt || work.isActive() || temporaryPillar.isActive() || temporaryPillar.isRecovering();
    }
    boolean isGatheringWoodForWork() { return gatheringLogs || supportSoilTask || temporaryPillar.isRecovering(); }
    boolean canWorkNow() { return inventoryViewers == 0 && !temporaryPillar.isRecovering(); }
    void syncWorkState() { entityData.set(DATA_GATHERING_LOGS, gatheringLogs || gatheringDirt || temporaryPillar.isRecovering() || work.isActive() || loot.hasPending() || recovery.active()); }
    public boolean isMiningAnimation() { return entityData.get(DATA_MINING); }
    void miningAnimation(boolean value) { entityData.set(DATA_MINING, value); }
    public int actionAnimation() { return level().getGameTime() < entityData.get(DATA_ACTION_UNTIL) ? entityData.get(DATA_ACTION) : 0; }
    public ItemStack actionItem() { return actionAnimation() == 0 ? ItemStack.EMPTY : entityData.get(DATA_ACTION_ITEM); }
    public void animateAction(int kind, BlockPos point, ItemStack item) {
        if (level().isClientSide) return;
        entityData.set(DATA_ACTION, kind); entityData.set(DATA_ACTION_UNTIL, level().getGameTime() + (kind == 2 ? 16 : 10));
        entityData.set(DATA_ACTION_ITEM, item.isEmpty() ? ItemStack.EMPTY : item.copyWithCount(1));
        getLookControl().setLookAt(point.getX() + .5, point.getY() + .5, point.getZ() + .5);
        swing(InteractionHand.MAIN_HAND);
    }
    public boolean loadsChunks() { return chunkLoading; }
    public boolean talksOnOwn() { return autonomousSpeech; }
    private boolean conversationAvailable = true;
    public void setConversationAvailable(boolean available) { conversationAvailable = available; }
    boolean conversationAvailable() { return conversationAvailable; }
    public dev.aicompanion.CompanionExpression expression() { return dev.aicompanion.CompanionExpression.fromId(entityData.get(DATA_EXPRESSION)); }
    public boolean sociallySafe() { return entityData.get(DATA_SOCIAL_SAFE); }
    public void reactToSpeech(String text) {
        if (!level().isClientSide) expressions.react(dev.aicompanion.CompanionExpression.speech(text), level().getGameTime(), 120);
    }
    private void updateExpression() {
        if (tickCount % 20 == 0) {
            var player = getOwnerPlayer();
            var threats = level().getEntitiesOfClass(Monster.class, getBoundingBox().inflate(12),
                    monster -> CompanionHostiles.observed(this, monster, 12)
                            || player != null && CompanionHostiles.observed(player, monster, 12));
            nearbySocialThreat = !threats.isEmpty() || player != null && player.level() == level()
                    && !level().getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(12),
                            monster -> CompanionHostiles.observed(player, monster, 12)).isEmpty();
            nearbyExplosiveThreat = threats.stream().anyMatch(monster -> monster instanceof net.minecraft.world.entity.monster.Creeper creeper
                    && (creeper.getSwellDir() > 0 || creeper.distanceToSqr(this) < 36));
        }
        boolean danger = getHealth() <= getMaxHealth() * .35F || isOnFire() || isInLava() || isUnderWater() && getAirSupply() < 40
                || recovery.active() || nearbyExplosiveThreat;
        boolean combat = getTarget() != null && getTarget().isAlive();
        var expression = expressions.update(level().getGameTime(), danger, combat,
                hasActiveWorkOrder() || isMiningAnimation() || actionAnimation() != 0);
        entityData.set(DATA_EXPRESSION, expression.ordinal());
        entityData.set(DATA_SOCIAL_SAFE, !danger && !combat && !nearbySocialThreat && tickCount - lastHurtTick > 200
                && (getOwnerPlayer() == null || getOwnerPlayer().getHealth() > 8 && getOwnerPlayer().hurtTime == 0));
    }
    public void setLifestyle(boolean chunks, boolean speech) { chunkLoading = chunks; autonomousSpeech = speech; }
    public String socialObservation() { return entityData.get(DATA_SOCIAL); }
    public int socialSequence() { return entityData.get(DATA_SOCIAL_SERIAL); }
    public long socialTime() { return entityData.get(DATA_SOCIAL_TIME); }
    void socialEvent(String fact) { entityData.set(DATA_SOCIAL, fact); entityData.set(DATA_SOCIAL_SERIAL, socialSequence() + 1); entityData.set(DATA_SOCIAL_TIME, level().getGameTime()); }
    public void observeEvent(String key, String fact) { social.observe(key, fact); }
    void finishFollowing() { setFollowing(true, true); loot.resumeCollection(); syncWorkState(); }
    boolean canCollectItems() { return inventoryViewers == 0; }
    /** Like a player, finish the block under the axe before walking off to a drop; walking away would reset its cracks. */
    boolean isSwingingAtBlock() { return woodBreaking.swinging() || dirtBreaking.swinging() || work.swinging(); }
    public int abandonedWoodSwings() { return woodBreaking.abandoned(); }
    boolean hasPendingLoot() { return loot.hasPending(); }
    /** What an animal or monster he killed drops is his to pick up, like his harvest (public for the GameTests that lay a drop). */
    public void claimDrops(java.util.Collection<net.minecraft.world.entity.item.ItemEntity> drops) { loot.claim(drops); }
    void storeLoot(ItemStack offered) { storeInCompanionInventory(offered); refreshInventorySummary(); }
    void dropHarvest(BlockPos pos, List<ItemStack> drops) {
        dev.aicompanion.CompanionDiagnostics.event(diagnostics.trace(), "harvest_dropped", "entity", getUUID(), "position", pos,
                "drops", drops.stream().map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()) + " x" + stack.getCount()).toList());
        loot.drop(pos, drops);
    }
    /** Our drops given up as out of reach get another round of pickup attempts (the terrain around them changed). */
    void retryHarvestPickup() { loot.beginCollection(); syncWorkState(); }
    int collectedLogs() { return loot.collectedLogs(); }
    int collectedStone() { return loot.collectedStone(); }
    /** Physical pickups that count toward a gather order; -1 when the order has no measurable stock. */
    public int planProgress(String action) {
        return switch (action) {
            case "gather_wood" -> loot.collectedLogs();
            case "gather_stone" -> loot.collectedStone();
            case "gather_dirt" -> loot.collectedSoil();
            default -> -1;
        };
    }
    void movementIntent(BlockPos pos) { movementIntent = pos == null ? null : pos.immutable(); }
    void routeRecovered() { work.routeRecovered(); }
    int workStoneReserve() { return work.stoneReserve(); }
    boolean workWoodSpare() { return work.woodSpareForSupports() && !gatheringLogs; }
    BlockPos recoveryDestination() {
        var player = getOwnerPlayer();
        if (!autonomy.staysAtWork() && (following || protecting) && player != null && (distanceToSqr(player) > 16 || player.getY() - getY() > 1.25)) return player.blockPosition();
        if (movementIntent != null && (gatheringLogs || gatheringDirt || work.isActive() || loot.hasPending())) return movementIntent;
        var path = getNavigation().getPath();
        return path == null || getNavigation().isDone() ? null : path.getTarget();
    }
    boolean canRecoverRoute() { return inventoryViewers == 0 && !gatheringLogs && !supportSoilTask && work.allowsRouteRecovery()
            && !temporaryPillar.isActive() && !temporaryPillar.isRecovering() && (!isInWater() || onGround()) && !isInLava(); }
    void workStatus(String message) {
        if (!message.equals(getLastTaskResult())) dev.aicompanion.CompanionDiagnostics.event(diagnostics.trace(),
                "work_status", "entity", getUUID(), "message", message, "state", diagnosticState());
        entityData.set(DATA_LAST_TASK_RESULT, message); syncWorkState();
    }
    public String getStorageForAi() { return entityData.get(DATA_STORAGE_CHEST); }
    void storageStatus(String location) { entityData.set(DATA_STORAGE_CHEST, location); }
    public CompanionActionResult startWork(String action, String item, int count) {
        return startWork(action, item, count, "near_player");
    }

    public CompanionActionResult startWork(String action, String item, int count, String location) {
        boolean canWaitForDescent = (action.equals("come") || action.equals("hunt_mobs")) && !work.isActive() && !gatheringLogs && !gatheringDirt;
        if (hasActiveWorkOrder() && !canWaitForDescent) return CompanionActionResult.failed("Сначала нужно завершить или отменить текущую работу.");
        places.discoverTables(); refreshPlaces();
        // Drops of the last order still on the ground are picked up first, not forgotten (review 22.10, М2).
        setFollowing(false, true);
        loot.resumeCollection();
        CompanionActionResult result = work.start(action, item, count, location);
        if (!result.success()) finishFollowing();
        recordActionResult(result);
        syncWorkState();
        return result;
    }

    public String getLastTaskResult() {
        return entityData.get(DATA_LAST_TASK_RESULT);
    }

    public boolean didLastTaskSucceed() { return lastTaskSucceeded; }

    /** obtain_item: the chain of his own skills that gets the item, planned from what he carries now (CompanionObtain). */
    public CompanionObtain.Plan obtainPlan(String item, int count) {
        if (!(level() instanceof ServerLevel serverLevel)) return new CompanionObtain.Plan(List.of(), "Для этого нужен загруженный мир.");
        return CompanionObtain.plan(serverLevel, carriedForPlan(), hasCraftingTableNearby() || hasKnownCraftingTableWithin(16), item, count);
    }
    /** The end of obtain_item: success only by the bag, never by the plan. */
    public CompanionActionResult carriedCheck(String item, int count) {
        var id = net.minecraft.resources.ResourceLocation.tryParse(item);
        var wanted = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (wanted == null) return CompanionActionResult.failed("Не понял, какой предмет проверить.");
        int have = 0;
        for (ItemStack stack : carriedForPlan()) if (stack.is(wanted)) have += stack.getCount();
        String name = new ItemStack(wanted).getHoverName().getString();
        return have >= count ? CompanionActionResult.ok("Готово: «" + name + "» у меня, " + have + " шт.")
                : CompanionActionResult.failed("Добыл не всё: «" + name + "» у меня " + have + " из " + count + ".");
    }
    private List<ItemStack> carriedForPlan() {
        List<ItemStack> carried = new java.util.ArrayList<>();
        for (int slot = 0; slot < getContainerSize(); slot++) carried.add(getItem(slot));
        carried.add(getMainHandItem()); carried.add(getOffhandItem());
        return carried;
    }
    public String getStoredItemsSummary() {
        return entityData.get(DATA_INVENTORY_SUMMARY);
    }

    public String getStoredItemsForAi() {
        return entityData.get(DATA_INVENTORY_AI);
    }

    public void mergeStoredItemsFrom(CompanionEntity other) {
        if (other == this || level().isClientSide) return;
        for (ItemStack stack : other.companionInventory) {
            if (stack.isEmpty()) continue;
            ItemStack remaining = stack.copy();
            if (!storeInCompanionInventory(remaining) && level() instanceof ServerLevel serverLevel) {
                Block.popResource(serverLevel, blockPosition(), remaining);
            }
        }
        other.companionInventory.clear();
        for (EquipmentSlot slot : CompanionEquipmentContainer.SLOTS) {
            ItemStack equipment = other.getItemBySlot(slot);
            if (equipment.isEmpty()) continue;
            if (getItemBySlot(slot).isEmpty()) setItemSlot(slot, equipment.copy());
            else {
                ItemStack remaining = equipment.copy();
                if (!storeInCompanionInventory(remaining) && level() instanceof ServerLevel serverLevel) Block.popResource(serverLevel, blockPosition(), remaining);
            }
            other.setItemSlot(slot, ItemStack.EMPTY);
        }
        other.refreshInventorySummary();
    }

    public String getEquipmentForAi() {
        List<String> equipment = new java.util.ArrayList<>();
        for (EquipmentSlot slot : CompanionEquipmentContainer.SLOTS) {
            ItemStack stack = getItemBySlot(slot);
            equipment.add(slot.getName() + "=" + (stack.isEmpty() ? "пусто" : BuiltInRegistries.ITEM.getKey(stack.getItem())
                    + " x" + stack.getCount() + (stack.isDamageableItem() ? " (прочность " + (stack.getMaxDamage() - stack.getDamageValue()) + ")" : "")));
        }
        return String.join(", ", equipment);
    }

    public CompanionActionResult equipItem(String requested, String slotName) {
        var result = equipItemInternal(requested, slotName);
        if (result.success() && ("mainhand".equals(slotName) || "auto".equals(slotName) && matchesEquipment(getMainHandItem(), requested)))
            preferCurrentWeapon();
        if (result.success()) animateAction(4, blockPosition(), getMainHandItem());
        return recordActionResult(result);
    }

    CompanionActionResult equipForSupport(String itemId) {
        return equipItemInternal(itemId, "mainhand");
    }

    CompanionActionResult equipForWork(String itemId) { return equipItemInternal(itemId, "mainhand"); }

    private CompanionActionResult equipItemInternal(String requested, String slotName) {
        if (level().isClientSide) return CompanionActionResult.failed("Экипировка меняется на сервере мира.");
        EquipmentSlot explicit = "auto".equals(slotName) ? null : equipmentSlot(slotName);
        if (explicit == null && !"auto".equals(slotName)) return CompanionActionResult.failed("Неизвестный слот экипировки.");
        for (EquipmentSlot slot : CompanionEquipmentContainer.SLOTS) {
            if ((explicit == null || explicit == slot) && matchesEquipment(getItemBySlot(slot), requested)) {
                return CompanionActionResult.ok("Уже экипирован: " + getItemBySlot(slot).getHoverName().getString() + ".");
            }
        }
        int source = -1;
        double best = -Double.MAX_VALUE;
        for (int index = 0; index < companionInventory.size(); index++) {
            ItemStack stack = companionInventory.get(index);
            if (!matchesEquipment(stack, requested)) continue;
            EquipmentSlot destination = explicit == null ? getEquipmentSlotForItem(stack) : explicit;
            if (!java.util.Arrays.asList(CompanionEquipmentContainer.SLOTS).contains(destination)) continue;
            if (destination.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && !stack.canEquip(destination, this)) continue;
            double score = equipmentScore(stack, destination);
            if (score > best) { source = index; best = score; }
        }
        if (source < 0) return CompanionActionResult.failed("В моей сумке нет подходящего предмета «" + requested + "» для этого слота.");
        ItemStack selected = companionInventory.get(source);
        EquipmentSlot destination = explicit == null ? getEquipmentSlotForItem(selected) : explicit;
        ItemStack previous = getItemBySlot(destination);
        if (destination.getType() == EquipmentSlot.Type.HUMANOID_ARMOR
                && net.minecraft.world.item.enchantment.EnchantmentHelper.has(previous,
                net.minecraft.world.item.enchantment.EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
            return CompanionActionResult.failed("Не могу снять предмет с проклятием несъёмности.");
        }
        NonNullList<ItemStack> before = copyBag();
        ItemStack incoming = selected.split(1);
        ItemStack toStore = previous.copy();
        if (!toStore.isEmpty() && !storeInCompanionInventory(toStore)) {
            restoreBag(before);
            return CompanionActionResult.failed("В сумке нет места для прежнего предмета. Освободи ячейку.");
        }
        setItemSlot(destination, incoming);
        dev.aicompanion.CompanionDiagnostics.event(diagnostics.trace(), "equipment_changed", "entity", getUUID(), "slot", destination.getName(),
                "before", previous.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(previous.getItem()),
                "after", BuiltInRegistries.ITEM.getKey(incoming.getItem()), "requested", requested);
        refreshInventorySummary();
        return CompanionActionResult.ok((destination.getType() == EquipmentSlot.Type.HUMANOID_ARMOR ? "Надел: " : "Взял в "
                + (destination == EquipmentSlot.OFFHAND ? "левую" : "правую") + " руку: ") + incoming.getHoverName().getString() + ".");
    }

    public CompanionActionResult unequipItem(String slotName) {
        var result = unequipForSupport(slotName);
        if (result.success() && (slotName.equals("mainhand") || slotName.equals("auto"))) preferredWeapon = "";
        if (result.success()) animateAction(4, blockPosition(), ItemStack.EMPTY);
        return recordActionResult(result);
    }

    CompanionActionResult unequipForSupport(String slotName) {
        EquipmentSlot slot = equipmentSlot(slotName);
        if (slot == null) return CompanionActionResult.failed("Укажи слот: mainhand, offhand, head, chest, legs или feet.");
        ItemStack equipped = getItemBySlot(slot);
        if (equipped.isEmpty()) return CompanionActionResult.ok("Эта ячейка уже пуста.");
        if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && net.minecraft.world.item.enchantment.EnchantmentHelper.has(equipped,
                net.minecraft.world.item.enchantment.EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
            return CompanionActionResult.failed("Не могу снять предмет с проклятием несъёмности.");
        }
        NonNullList<ItemStack> before = copyBag();
        ItemStack offered = equipped.copy();
        if (!storeInCompanionInventory(offered)) {
            restoreBag(before);
            return CompanionActionResult.failed("В сумке нет места для снятого предмета.");
        }
        setItemSlot(slot, ItemStack.EMPTY);
        return CompanionActionResult.ok("Убрал в сумку: " + equipped.getHoverName().getString() + ".");
    }

    private NonNullList<ItemStack> copyBag() {
        NonNullList<ItemStack> copy = NonNullList.withSize(companionInventory.size(), ItemStack.EMPTY);
        for (int index = 0; index < copy.size(); index++) copy.set(index, companionInventory.get(index).copy());
        return copy;
    }

    private void restoreBag(List<ItemStack> bag) {
        for (int index = 0; index < bag.size(); index++) companionInventory.set(index, bag.get(index).copy());
        refreshInventorySummary();
    }

    public static EquipmentSlot equipmentSlot(String name) {
        return switch (name) {
            case "mainhand", "auto" -> EquipmentSlot.MAINHAND;
            case "offhand" -> EquipmentSlot.OFFHAND;
            case "head" -> EquipmentSlot.HEAD;
            case "chest" -> EquipmentSlot.CHEST;
            case "legs" -> EquipmentSlot.LEGS;
            case "feet" -> EquipmentSlot.FEET;
            default -> null;
        };
    }

    private boolean matchesEquipment(ItemStack stack, String requested) {
        if (stack.isEmpty()) return false;
        return switch (requested) {
            case "sword" -> stack.is(ItemTags.SWORDS);
            case "axe" -> stack.is(ItemTags.AXES);
            case "pickaxe" -> stack.is(ItemTags.PICKAXES);
            case "shovel" -> stack.is(ItemTags.SHOVELS);
            case "hoe" -> stack.is(ItemTags.HOES);
            case "shield" -> stack.canPerformAction(net.neoforged.neoforge.common.ItemAbilities.SHIELD_BLOCK);
            case "helmet" -> getEquipmentSlotForItem(stack) == EquipmentSlot.HEAD;
            case "chestplate" -> getEquipmentSlotForItem(stack) == EquipmentSlot.CHEST;
            case "leggings" -> getEquipmentSlotForItem(stack) == EquipmentSlot.LEGS;
            case "boots" -> getEquipmentSlotForItem(stack) == EquipmentSlot.FEET;
            default -> requested.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        };
    }

    private double equipmentScore(ItemStack stack, EquipmentSlot slot) {
        boolean armor = slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
        var relevant = stack.getAttributeModifiers().modifiers().stream()
                .filter(entry -> entry.attribute().equals(armor ? Attributes.ARMOR : Attributes.ATTACK_DAMAGE)).toList();
        double score = new net.minecraft.world.item.component.ItemAttributeModifiers(relevant, true).compute(armor ? 0 : 1, slot);
        return score * 10000 + (stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : 0);
    }

    public void selectToolForBlock(BlockState state) {
        if (state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL)) {
            if (!CompanionTools.equip(this, ItemTags.SHOVELS, state) && !getMainHandItem().isEmpty()) unequipForSupport("mainhand");
            return;
        }
        double best = getMainHandItem().getDestroySpeed(state);
        ItemStack choice = ItemStack.EMPTY;
        for (ItemStack stack : companionInventory) if (!stack.isEmpty() && stack.getDestroySpeed(state) > best) {
            best = stack.getDestroySpeed(state);
            choice = stack;
        }
        if (!choice.isEmpty()) equipItemInternal(BuiltInRegistries.ITEM.getKey(choice.getItem()).toString(), "mainhand");
    }

    public void selectWeaponForCombat() {
        if (!preferredWeapon.isEmpty()) {
            if (matchesEquipment(getMainHandItem(), preferredWeapon)) return;
            if (equipItemInternal(preferredWeapon, "mainhand").success()) return;
        }
        double best = equipmentScore(getMainHandItem(), EquipmentSlot.MAINHAND);
        ItemStack choice = ItemStack.EMPTY;
        for (ItemStack stack : companionInventory) if ((stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES))
                && equipmentScore(stack, EquipmentSlot.MAINHAND) > best) {
            best = equipmentScore(stack, EquipmentSlot.MAINHAND);
            choice = stack;
        }
        if (!choice.isEmpty()) equipItemInternal(BuiltInRegistries.ITEM.getKey(choice.getItem()).toString(), "mainhand");
    }

    private void preferCurrentWeapon() {
        ItemStack hand = getMainHandItem();
        preferredWeapon = hand.is(ItemTags.SWORDS) || hand.is(ItemTags.AXES) ? BuiltInRegistries.ITEM.getKey(hand.getItem()).toString() : "";
    }
    public void setEquipmentByPlayer(EquipmentSlot slot, ItemStack stack) {
        setItemSlot(slot, stack);
        if (!level().isClientSide && slot == EquipmentSlot.MAINHAND) preferCurrentWeapon();
    }

    /**
     * At a bank a block over the water, like a player: hold the jump while pressing against it, until out (marathon 23.10,
     * «берег»: the game's float stops pushing once the head is out, the lift of the water reached half a block short of the bank,
     * and he bobbed at the island's edge for minutes). Only while the route really goes up onto that bank.
     */
    private void climbOutOfWater() {
        if (isInWater()) lastInWaterTick = tickCount;
        // In the water, or just lifted out of it at the bank by the water's own push (a few ticks ago).
        if (tickCount - lastInWaterTick > 3) return;
        var path = getNavigation().getPath();
        boolean routed = path != null && !path.isDone();
        // With a route he must be pressing on the bank; without one (see below) he is turned to press on it.
        if (routed && !horizontalCollision) return;
        // The bank a node or two ahead counts too: along an island's edge the route often keeps one more cell of water first
        // (marathon 23.10: he bobbed along the shore of the island and never got out).
        boolean bank = false;
        if (routed)
            for (int index = path.getNextNodeIndex(); index < Math.min(path.getNodeCount(), path.getNextNodeIndex() + 3) && !bank; index++) {
                BlockPos node = path.getNodePos(index);
                bank = node.getY() > getY() + .5 && Math.abs(node.getX() + .5 - getX()) < 1.6 && Math.abs(node.getZ() + .5 - getZ()) < 1.6;
            }
        // Bobbing at the bank, the game's navigation gives the route up as «stuck»: then the bank in front of him, if where he is
        // going (the player, the work) lies higher than the water.
        if (!bank && !routed) {
            BlockPos destination = recoveryDestination();
            if (destination == null || destination.getY() <= getY() + .5) return;
            BlockPos front = blockPosition().relative(getDirection());
            for (int up = 1; up <= 2 && !bank; up++) bank = CompanionMiningNavigation.standable(this, front.above(up));
            // Pressing on towards it, as the route would have.
            if (bank) getMoveControl().setWantedPosition(front.getX() + .5, front.getY() + 1, front.getZ() + .5, 1.0);
        }
        if (!bank) return;
        if (isInWater()) { getJumpControl().jump(); return; }
        if (!horizontalCollision) return;
        // Lifted out at the edge, the push off is a player's ordinary jump: the water's push alone stops some way short of a block.
        if (getDeltaMovement().y < .2 && tickCount >= nextWaterJump) {
            nextWaterJump = tickCount + 10;
            setDeltaMovement(getDeltaMovement().x, getJumpPower(), getDeltaMovement().z);
        }
    }
    private int nextWaterJump, lastInWaterTick = -100;

    /**
     * The game's movement control «jumps» at a point higher than a step and then holds the stroke forward, deaf to the route,
     * until the feet touch ground — in deep water never (marathon 23.10, «берег»: at the island's bank he swam straight on past
     * it, through «стой», forty blocks into the corner). In the water the jump is a stroke up and over at once: the next tick he
     * steers by his route again, or stops if there is none.
     */
    private final class SwimmingMoveControl extends net.minecraft.world.entity.ai.control.MoveControl {
        SwimmingMoveControl() { super(CompanionEntity.this); }
        @Override public void tick() {
            if (operation == Operation.JUMPING && (isInWater() || isInLava()) && !onGround()) operation = getNavigation().isDone() ? Operation.WAIT : Operation.MOVE_TO;
            super.tick();
        }
    }

    /**
     * In the water a mob's stroke is its walking speed (0.3), a player's the whole push of the key (0.98): in water to the knee,
     * where no bobbing helps, he waded a block in two seconds and fell behind the player swimming to the island (marathon
     * 23.10, «берег»). Like a player: the push of the key, as far as he means to go at a walk.
     */
    @Override public void travel(Vec3 input) {
        if (isInWater() && !isInLava() && (input.x != 0 || input.z != 0)) {
            double scale = .98 / getAttributeBaseValue(Attributes.MOVEMENT_SPEED), horizontal = Math.hypot(input.x, input.z) * scale;
            if (horizontal > .98) scale *= .98 / horizontal;
            input = new Vec3(input.x * scale, input.y, input.z * scale);
        }
        super.travel(input);
    }

    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        // In a boat or minecart vanilla switches off every walking goal: the order would hang without even a timeout.
        // Like a player, step out to work; just riding along stays allowed.
        if (isPassenger() && (work.isActive() || gatheringLogs || gatheringDirt)) {
            dev.aicompanion.CompanionDiagnostics.event(diagnostics.trace(), "left_vehicle", "entity", getUUID(), "vehicle", getVehicle().getType().toShortString());
            stopRiding();
        }
        climbOutOfWater();
        tickEating();
        loot.tickNearby();
        recovery.observe();
        work.watchChase();
        updateExpression();
        social.tick();
        lighting.tick();
        goals.tick();
        autonomy.tick();
        diagnostics.tick();
        if (tickCount % 100 == 0) refreshPlaces();
        if (tickCount % 20 == 0) {
            activityMoved = previousActivityPosition != null && position().distanceToSqr(previousActivityPosition) > .01;
            previousActivityPosition = position(); entityData.set(DATA_ACTIVITY, getActivityForAi());
        }
        // A fight for the player does not lead away from him: guard order, or any monster outside a hunt.
        var guardedOwner = protecting || !work.isHunting() && getTarget() instanceof Monster ? getOwnerPlayer() : null;
        if (guardedOwner != null && getTarget() != null && distanceToSqr(guardedOwner) > 16 * 16
                && getTarget().distanceToSqr(guardedOwner) > 14 * 14) setTarget(null);
        if (inventoryViewers == 0 && getTarget() != null && tickCount % 10 == 0) selectWeaponForCombat();
        if (inventoryViewers == 0 && getOffhandItem().canPerformAction(net.neoforged.neoforge.common.ItemAbilities.SHIELD_BLOCK)) {
            var enemy = getTarget();
            boolean defend = enemy != null && enemy.isAlive() && hasLineOfSight(enemy)
                    && (distanceToSqr(enemy) < 9 || enemy instanceof net.minecraft.world.entity.monster.AbstractSkeleton && distanceToSqr(enemy) < 144);
            if (defend && !isUsingItem()) startUsingItem(InteractionHand.OFF_HAND);
            else if (!defend && isUsingItem() && getUsedItemHand() == InteractionHand.OFF_HAND) stopUsingItem();
        }
        if (inventoryViewers == 0 && !hasActiveWorkOrder() && !recovery.active() && !preferredWeapon.isEmpty() && tickCount % 20 == 0)
            selectWeaponForCombat();
    }

    /**
     * Death like a player's: everything carried falls on the spot and stays there (review 22.10: the bag vanished with him).
     * The journal keeps how it happened, and the memory goes to the companion who comes back.
     */
    @Override public void die(net.minecraft.world.damagesource.DamageSource source) {
        if (!dead && !isRemoved() && level() instanceof ServerLevel) {
            String where = blockPosition().toShortString();
            String cause = source.getLocalizedDeathMessage(this).getString();
            work.cancel(); gatheringLogs = gatheringDirt = false; loot.cancel();
            boolean dropped = dropBelongings();
            noteDeed("Погиб на " + where + " (" + cause + ")." + (dropped ? " Все вещи выпали там." : ""), false);
            CompanionEvents.companionDied(this, memory(), where, dropped);
        }
        super.die(source);
    }

    private boolean dropBelongings() {
        boolean any = false;
        java.util.List<ItemStack> belongings = new java.util.ArrayList<>();
        for (int slot = 0; slot < companionInventory.size(); slot++) { belongings.add(companionInventory.get(slot)); companionInventory.set(slot, ItemStack.EMPTY); }
        for (EquipmentSlot slot : CompanionEquipmentContainer.SLOTS) { belongings.add(getItemBySlot(slot)); setItemSlot(slot, ItemStack.EMPTY); }
        for (ItemStack stack : belongings) if (!stack.isEmpty()) {
            var item = spawnAtLocation(stack.copy(), 0.5F);
            if (item != null) { item.setUnlimitedLifetime(); any = true; }
        }
        refreshInventorySummary();
        return any;
    }

    /** What the companion who comes back remembers: name, settings, journal of deeds, places, the house and the chest. */
    CompoundTag memory() {
        CompoundTag full = new CompoundTag(), memory = new CompoundTag();
        addAdditionalSaveData(full);
        for (String key : new String[]{"CompanionName", "SearchRadius", "ChunkLoading", "AutonomousSpeech", "KnownPlaces", "Deeds",
                "SmallHouse", "StorageChest", "StorageDimension", "Autonomy", CompanionGoals.KEY}) if (full.contains(key)) memory.put(key, full.get(key).copy());
        return memory;
    }

    void inheritMemory(CompoundTag memory) {
        if (memory.contains("CompanionName")) setCompanionName(memory.getString("CompanionName"));
        if (memory.contains("SearchRadius")) setSearchRadius(memory.getInt("SearchRadius"));
        chunkLoading = !memory.contains("ChunkLoading") || memory.getBoolean("ChunkLoading");
        autonomousSpeech = !memory.contains("AutonomousSpeech") || memory.getBoolean("AutonomousSpeech");
        deeds.load(memory); places.load(memory); work.load(memory); autonomy.load(memory); refreshPlaces();
        goals.load(memory);
        noteDeed("Вернулся к тебе после гибели. Вещи, что были при мне, остались там, где я погиб.", true);
    }

    /** Food in the bag heals, like a player who eats: nothing is conjured, the food is eaten (review 22.10: he never healed). */
    private int satiety;
    private int eatTicks;
    private ItemStack eating = ItemStack.EMPTY;
    private int lastHurtTick = -1000;
    static final int EAT_TICKS = 32;

    @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt) {
            lastHurtTick = tickCount;
            if (!level().isClientSide && amount >= 4) expressions.react(dev.aicompanion.CompanionExpression.WORRIED, level().getGameTime(), 100);
        }
        return hurt;
    }

    private void tickEating() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        if (eatTicks > 0) {
            eatTicks--;
            if (eatTicks % 4 == 0) {
                playSound(net.minecraft.sounds.SoundEvents.GENERIC_EAT, .5F, .9F + getRandom().nextFloat() * .2F);
                var mouth = getEyePosition().add(getLookAngle().scale(.3)).add(0, -.15, 0);
                serverLevel.sendParticles(new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM, eating),
                        mouth.x, mouth.y, mouth.z, 4, .08, .08, .08, .04);
            }
            if (eatTicks == 0) finishEating();
            return;
        }
        if (inventoryViewers > 0 || !isAlive()) return;
        float missing = getMaxHealth() - getHealth();
        // Fed, out of a fight for ten seconds: slowly better, as a player with a full stomach.
        if (missing >= 1 && satiety > 0 && tickCount - lastHurtTick > 200 && tickCount % 80 == 0) { heal(1); satiety--; }
        if (tickCount % 20 != 0 || missing < 4 && getHealth() > getMaxHealth() / 2) return;
        if (getTarget() != null && getTarget().isAlive() && distanceToSqr(getTarget()) < 16 && getHealth() > 6) return;
        ItemStack food = bestFood(missing);
        if (food.isEmpty()) return;
        eating = food.copyWithCount(1); eatTicks = EAT_TICKS;
        animateAction(4, blockPosition(), eating);
    }

    private void finishEating() {
        var properties = eating.get(net.minecraft.core.component.DataComponents.FOOD);
        for (ItemStack stack : companionInventory) if (ItemStack.isSameItemSameComponents(stack, eating)) {
            stack.shrink(1); setChanged();
            if (properties != null) { heal(properties.nutrition()); satiety = Math.min(40, satiety + Math.round(properties.saturation())); }
            playSound(net.minecraft.sounds.SoundEvents.PLAYER_BURP, .4F, 1F);
            dev.aicompanion.CompanionDiagnostics.event(diagnostics.trace(), "companion_ate", "entity", getUUID(),
                    "item", BuiltInRegistries.ITEM.getKey(eating.getItem()), "health", getHealth());
            observeEvent("eat", "Перекусил: " + eating.getHoverName().getString() + ", стало лучше.");
            break;
        }
        eating = ItemStack.EMPTY;
    }

    /** Safe food only (no rotten flesh, spider eyes, poison, chorus fruit); the one that best fits the missing health. */
    private ItemStack bestFood(float missing) {
        ItemStack best = ItemStack.EMPTY;
        int bestScore = Integer.MIN_VALUE;
        for (ItemStack stack : companionInventory) {
            var food = stack.get(net.minecraft.core.component.DataComponents.FOOD);
            if (stack.isEmpty() || food == null || stack.is(net.minecraft.world.item.Items.CHORUS_FRUIT) || stack.is(net.minecraft.world.item.Items.SUSPICIOUS_STEW)) continue;
            if (food.effects().stream().anyMatch(effect -> !effect.effect().getEffect().value().isBeneficial())) continue;
            int score = Math.round(Math.min(missing, food.nutrition()) * 10 + food.saturation());
            if (score > bestScore) { bestScore = score; best = stack; }
        }
        return best;
    }

    @Override public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) work.hitEnemy(living);
        ItemStack weapon = getMainHandItem();
        if (hit && target instanceof LivingEntity living && !weapon.isEmpty() && weapon.getItem().hurtEnemy(weapon, living, this)) {
            weapon.getItem().postHurtEnemy(weapon, living, this);
        }
        return hit;
    }

    private String buildInventorySummary() {
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (ItemStack stack : companionInventory) {
            if (!stack.isEmpty()) counts.merge(stack.getHoverName().getString(), stack.getCount(), Integer::sum);
        }
        return counts.isEmpty() ? "пусто" : counts.entrySet().stream()
                .map(entry -> entry.getValue() + " " + entry.getKey()).collect(java.util.stream.Collectors.joining(", "));
    }

    /** The recipe needs a bench and one is a few steps away: walk there first, like a player (any recipe, not only the known chains). */
    public boolean benchWalkNeeded(String requestedItemId, int requestedCount) {
        if (!(level() instanceof ServerLevel serverLevel) || hasCraftingTableNearby() || !hasKnownCraftingTableWithin(CompanionToolSense.BENCH_WALK)) return false;
        var atBench = CompanionCrafting.plan(serverLevel, companionInventory, requestedItemId, requestedCount, true);
        if (!atBench.success()) return false;
        // Possible at a bench, and here it either needs one set down or is impossible without one.
        var here = CompanionCrafting.plan(serverLevel, companionInventory, requestedItemId, requestedCount, false);
        return here.placeTable() || !here.success();
    }

    public CompanionActionResult craftItemResult(String requestedItemId, int requestedCount) {
        if (hasActiveWorkOrder()) return CompanionActionResult.failed("Сначала нужно завершить или отменить текущую добычу.");
        return craftFromBag(requestedItemId, requestedCount);
    }

    CompanionActionResult craftFromBag(String requestedItemId, int requestedCount) {
        if (!(level() instanceof ServerLevel serverLevel)) return CompanionActionResult.failed("Крафт доступен только в загруженном мире.");
        CompanionCrafting.Plan plan = CompanionCrafting.plan(serverLevel, companionInventory, requestedItemId,
                requestedCount, hasCraftingTableNearby());
        dev.aicompanion.CompanionDiagnostics.event(diagnostics.trace(), "craft_decision", "entity", getUUID(),
                "item", requestedItemId, "count", requestedCount, "success", plan.success(), "placeTable", plan.placeTable(), "message", plan.message());
        if (!plan.success()) return recordActionResult(CompanionActionResult.failed(plan.message()));
        // A remembered bench far away is not worth the walk: a player would set down another one from own wood.
        if (plan.placeTable() && !newBenchAllowed && hasKnownCraftingTableWithin(CompanionToolSense.BENCH_WALK))
            return recordActionResult(CompanionActionResult.failed("Верстак рядом, но я сейчас не у него. Попроси ещё раз — дойду до него и сделаю."));
        // With a bench already at the base, a bench set down out here is a travelling one: taken along after use.
        boolean fieldBench = plan.placeTable() && hasKnownCraftingTable();
        BlockPos tablePosition = plan.placeTable() ? findPlacementPosition(serverLevel, Blocks.CRAFTING_TABLE) : null;
        if (plan.placeTable() && (tablePosition == null
                || !serverLevel.setBlock(tablePosition, Blocks.CRAFTING_TABLE.defaultBlockState(), 3))) {
            return recordActionResult(CompanionActionResult.failed("Не нашёл свободное безопасное место для верстака. Материалы сохранены."));
        }
        if (tablePosition != null) rememberPlaced(tablePosition);
        if (tablePosition != null && fieldBench) fieldBenches.add(tablePosition.immutable());
        for (int slot = 0; slot < companionInventory.size(); slot++) companionInventory.set(slot, plan.inventory().get(slot).copy());
        if (tablePosition != null) dev.aicompanion.CompanionDiagnostics.event(diagnostics.trace(), "block_placed",
                "entity", getUUID(), "block", "minecraft:crafting_table", "position", tablePosition, "reason", "craft_prerequisite");
        refreshInventorySummary();
        animateAction(tablePosition == null ? 2 : 1, tablePosition == null ? blockPosition() : tablePosition,
                tablePosition == null ? new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(requestedItemId))) : new ItemStack(net.minecraft.world.item.Items.CRAFTING_TABLE));
        if (tablePosition != null) {
            var table = Blocks.CRAFTING_TABLE.defaultBlockState(); var sound = table.getSoundType();
            serverLevel.playSound(null, tablePosition, sound.getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, .7F, 1F);
        }
        madeAt.put(requestedItemId, level().getGameTime());
        return recordActionResult(CompanionActionResult.ok((plan.placeTable() ? "Сделал и поставил верстак. " : "")
                + plan.message() + " В сумке: " + getStoredItemsSummary() + "."));
    }

    public CompanionActionResult placeBlockResult(String requestedBlockId) {
        return placeBlockNearResult(requestedBlockId, blockPosition());
    }

    CompanionActionResult placeBlockNearResult(String requestedBlockId, BlockPos origin) {
        return placeBlockNearResult(requestedBlockId, origin, pos -> true);
    }

    CompanionActionResult placeBlockNearResult(String requestedBlockId, BlockPos origin, java.util.function.Predicate<BlockPos> allowed) {
        if (!(level() instanceof ServerLevel serverLevel)) return CompanionActionResult.failed("Не могу поставить блок без загруженного мира.");
        ResourceLocation blockId = ResourceLocation.tryParse(requestedBlockId.trim());
        if (blockId == null) return recordActionResult(CompanionActionResult.failed("Не понял ID блока."));
        Block block = BuiltInRegistries.BLOCK.getOptional(blockId).orElse(null);
        if (block == null || block == Blocks.AIR) return recordActionResult(CompanionActionResult.failed("Неизвестный или пустой блок: " + blockId + "."));
        ItemStack item = findBlockItem(block);
        if (item == null) return recordActionResult(CompanionActionResult.failed("У меня в сумке нет блока " + blockId + "."));
        BlockPos position = findPlacementPosition(serverLevel, block, origin, allowed);
        if (position == null || !serverLevel.setBlock(position, block.defaultBlockState(), 3)) {
            return recordActionResult(CompanionActionResult.failed("Не нашёл безопасное место для " + blockId + " рядом со мной."));
        }
        animateAction(1, position, item);
        lastPlacedPosition = position.immutable();
        rememberPlaced(position);
        dev.aicompanion.CompanionDiagnostics.event(diagnostics.trace(), "block_placed", "entity", getUUID(),
                "block", blockId, "position", position, "reason", "place_block");
        item.shrink(1);
        refreshInventorySummary();
        var placedSound = block.defaultBlockState().getSoundType();
        serverLevel.playSound(null, position, placedSound.getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, .7F, 1F);
        return recordActionResult(CompanionActionResult.ok("Поставил " + blockId + ". В сумке: " + getStoredItemsSummary() + "."));
    }

    public CompanionActionResult recordActionResult(CompanionActionResult result) {
        lastTaskSucceeded = result.success();
        entityData.set(DATA_LAST_TASK_RESULT, result.message());
        if (result.status() != CompanionActionResult.Status.ACCEPTED) noteDeed(result.message(), result.success());
        dev.aicompanion.CompanionDiagnostics.event(diagnostics.trace(), "engine_result", "entity", getUUID(),
                "success", result.success(), "status", result.status(), "message", result.message());
        return result;
    }

    public boolean hasCraftingTableNearby() {
        BlockPos origin = blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-4, -2, -4), origin.offset(4, 2, 4))) {
            if (level().hasChunkAt(pos) && level().getBlockState(pos).is(Blocks.CRAFTING_TABLE)
                    && distanceToSqr(pos.getCenter()) <= 3.2 * 3.2) {
                // Walking targets are chosen by the view from a cell's centre. Standing a few hundredths off it
                // must not turn "I am at the bench" into "cannot reach the bench" (game log 22.10, a step beside the bench).
                Vec3 centredEye = Vec3.atBottomCenterOf(blockPosition()).add(0, getEyeHeight(), 0);
                if (seesBlock(getEyePosition(), pos) || onGround() && seesBlock(centredEye, pos)) {
                    if (!level().isClientSide) rememberObserved(pos);
                    return true;
                }
            }
        }
        return false;
    }
    private boolean seesBlock(Vec3 eye, BlockPos pos) { return seesBlock(this, eye, pos); }

    /** Like a player: a bench or chest is usable when any face turned to the eye can be looked at, not only its exact centre. */
    static boolean seesBlock(net.minecraft.world.entity.Entity viewer, Vec3 eye, BlockPos pos) {
        Vec3 center = pos.getCenter();
        Vec3[] points = {center, center.add(0, .45, 0), center.add(Math.signum(eye.x - center.x) * .45, 0, 0),
                center.add(0, 0, Math.signum(eye.z - center.z) * .45), center.add(0, -.45, 0)};
        for (Vec3 point : points) {
            var hit = viewer.level().clip(new net.minecraft.world.level.ClipContext(eye, point,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, viewer));
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) return true;
        }
        return false;
    }
    private ItemStack findBlockItem(Block block) {
        for (ItemStack stack : companionInventory) {
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() == block) return stack;
        }
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            ItemStack stack = getItemBySlot(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() == block) return stack;
        }
        return null;
    }

    private BlockPos findPlacementPosition(ServerLevel serverLevel, Block block) {
        return findPlacementPosition(serverLevel, block, blockPosition());
    }

    private BlockPos findPlacementPosition(ServerLevel serverLevel, Block block, BlockPos origin) {
        return findPlacementPosition(serverLevel, block, origin, pos -> true);
    }

    private BlockPos findPlacementPosition(ServerLevel serverLevel, Block block, BlockPos origin, java.util.function.Predicate<BlockPos> allowed) {
        BlockState state = block.defaultBlockState();
        int returnHeight = CompanionMiningNavigation.provenReturnHeight(this);
        // Where he stands and a block up or down first; only then as high or as low as a player reaches (marathon 23.10, «долина»:
        // in a crack of the ravine the bench took the one cell beside him, the furnace found no room, though the top of the bench
        // was right in front of his face).
        for (int dy : new int[]{0, -1, 1, 2, -2}) for (int radius = 1; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                BlockPos pos = origin.offset(dx, dy, dz);
                if (!allowed.test(pos)) continue;
                if (!serverLevel.hasChunkAt(pos) || !serverLevel.getBlockState(pos).canBeReplaced()) continue;
                if (!serverLevel.getFluidState(pos).isEmpty() || !serverLevel.getWorldBorder().isWithinBounds(pos)) continue;
                if (!serverLevel.getBlockState(pos.below()).isFaceSturdy(serverLevel, pos.below(), Direction.UP)) continue;
                if (!state.canSurvive(serverLevel, pos)) continue;
                if (distanceToSqr(pos.getCenter()) > (Math.abs(dy) == 2 ? 3.2 * 3.2 : 16)) continue;
                var hit = serverLevel.clip(new net.minecraft.world.level.ClipContext(getEyePosition(), pos.getCenter(),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this));
                if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS && !hit.getBlockPos().equals(pos)) continue;
                if (block instanceof net.minecraft.world.level.block.ChestBlock) {
                    if (!serverLevel.getBlockState(pos.above()).getCollisionShape(serverLevel, pos.above()).isEmpty()) continue;
                    boolean adjacentChest = false;
                    for (Direction direction : Direction.Plane.HORIZONTAL) if (serverLevel.getBlockState(pos.relative(direction)).getBlock() instanceof net.minecraft.world.level.block.ChestBlock) adjacentChest = true;
                    if (adjacentChest) continue;
                }
                AABB collision = new AABB(pos);
                if (getBoundingBox().intersects(collision) || !serverLevel.getEntities(this, collision).isEmpty()) continue;
                if (!CompanionMiningNavigation.preservesReturnOnPlacement(this, pos, returnHeight)) continue;
                return pos.immutable();
            }
        }
        return null;
    }

    public void startGatheringLogs(int count) {
        work.cancel();
        startGatheringLogsForWork(count);
    }

    void startGatheringLogsForWork(int count) {
        loot.beginCollection();
        woodTaskVersion++;
        temporaryPillar.requestCleanup();
        roaming = false;
        entityData.set(DATA_ROAMING, false);
        lastTaskSucceeded = false;
        gatheringDirt = false;
        following = false;
        entityData.set(DATA_FOLLOWING, false);
        protecting = false;
        entityData.set(DATA_PROTECTING, false);
        gatheringLogs = true;
        logsRemaining = Math.max(1, Math.min(64, count));
        requestedLogs = logsRemaining;
        entityData.set(DATA_GATHERING_LOGS, true);
        entityData.set(DATA_LOGS_REMAINING, logsRemaining);
        entityData.set(DATA_LAST_TASK_RESULT, "Ищу дерево, нужно собрать " + requestedLogs + " брёвен.");
        getNavigation().stop();
    }

    void startGatheringDirtForWork(int count) {
        loot.beginCollection();
        dirtTaskVersion++;
        temporaryPillar.requestCleanup();
        roaming = false;
        entityData.set(DATA_ROAMING, false);
        lastTaskSucceeded = false;
        gatheringLogs = false;
        gatheringDirt = true;
        following = false;
        protecting = false;
        entityData.set(DATA_FOLLOWING, false);
        entityData.set(DATA_PROTECTING, false);
        logsRemaining = Math.max(1, Math.min(64, count));
        requestedLogs = logsRemaining;
        entityData.set(DATA_GATHERING_LOGS, true);
        entityData.set(DATA_LOGS_REMAINING, logsRemaining);
        entityData.set(DATA_LAST_TASK_RESULT, "Ищу землю, нужно добыть " + requestedLogs + " блоков.");
        getNavigation().stop();
    }

    public void stopGathering() {
        if (gatheringLogs && !work.isActive()) noteDeed("Прервал рубку дерева: брёвен было нужно ещё " + logsRemaining + ".", false);
        if (gatheringDirt && !work.isActive()) noteDeed("Прервал добычу земли: было нужно ещё " + logsRemaining + ".", false);
        lastTaskSucceeded = false;
        gatheringLogs = false;
        gatheringDirt = false;
        logsRemaining = 0;
        requestedLogs = 0;
        entityData.set(DATA_GATHERING_LOGS, false);
        entityData.set(DATA_LOGS_REMAINING, 0);
        entityData.set(DATA_LAST_TASK_RESULT, "Предыдущую задачу остановил. В сумке: " + getStoredItemsSummary() + ".");
        getNavigation().stop();
        setFollowing(true);
    }

    ServerPlayer getOwnerPlayer() {
        if (!(level() instanceof ServerLevel serverLevel) || ownerId == null) return null;
        ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(ownerId);
        return owner != null && owner.level() == level() ? owner : null;
    }

    static final String FULL_BAG_FOR_LOGS = "В сумке нет места для брёвен: все ячейки заняты. Освободи место или попроси сложить вещи в сундук.";
    /** Room for one more log: an empty slot, or a stack of logs not yet full. */
    boolean hasRoomForLogs() {
        for (ItemStack stack : companionInventory) if (stack.isEmpty() || stack.is(ItemTags.LOGS) && stack.getCount() < stack.getMaxStackSize()) return true;
        return false;
    }

    private boolean mineLog(BlockPos pos) {
        if (!(level() instanceof ServerLevel serverLevel)) return false;
        BlockState state = serverLevel.getBlockState(pos);
        if (!state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)) return false;
        ItemStack tool = getMainHandItem();
        var drops = Block.getDrops(state, serverLevel, pos, serverLevel.getBlockEntity(pos), this, tool);
        if (!canStoreDrops(drops)) return false;
        if (!serverLevel.destroyBlock(pos, false, this, 512)) return false;
        tool.getItem().mineBlock(tool, serverLevel, state, pos, this);
        swing(InteractionHand.MAIN_HAND);
        dropHarvest(pos, drops);
        if (state.is(BlockTags.LOGS)) observeEvent("wood", "Срубил и увидел выпавшее бревно. Добыча дерева ещё может продолжаться.");
        return true;
    }

    private boolean mineDirt(BlockPos pos) {
        if (!(level() instanceof ServerLevel serverLevel)) return false;
        BlockState state = serverLevel.getBlockState(pos);
        if (!isDirtBlock(state) || CompanionMiningNavigation.supportsLivingEntity(this, pos) || !CompanionQuarry.safeCell(serverLevel, pos)) return false;
        if (!CompanionMiningNavigation.preservesAutonomousReturn(this, pos)) return false;
        selectToolForBlock(state);
        ItemStack tool = getMainHandItem();
        var drops = Block.getDrops(state, serverLevel, pos, serverLevel.getBlockEntity(pos), this, tool);
        if (!canStoreDrops(drops)) return false;
        if (!serverLevel.destroyBlock(pos, false, this, 512)) return false;
        tool.getItem().mineBlock(tool, serverLevel, state, pos, this);
        swing(InteractionHand.MAIN_HAND);
        dropHarvest(pos, drops);
        return true;
    }

    boolean canStoreDrops(List<ItemStack> drops) {
        var bag = copyBag();
        for (ItemStack offered : drops) {
            ItemStack remaining = offered.copy();
            for (ItemStack stored : bag) if (!stored.isEmpty() && ItemStack.isSameItemSameComponents(stored, remaining)) {
                int moved = Math.min(remaining.getCount(), stored.getMaxStackSize() - stored.getCount());
                if (moved > 0) { stored.grow(moved); remaining.shrink(moved); }
            }
            for (int slot = 0; slot < bag.size() && !remaining.isEmpty(); slot++) if (bag.get(slot).isEmpty()) {
                int moved = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                bag.set(slot, remaining.copyWithCount(moved)); remaining.shrink(moved);
            }
            if (!remaining.isEmpty()) return false;
        }
        return true;
    }

    private static boolean isDirtBlock(BlockState state) {
        return CompanionQuarry.soil(state); // one definition of natural soil for digging
    }

    private final class GatherLogsGoal extends Goal {
        private BlockPos target;
        private Path approachPath;
        private int retryDelay;
        private int noLogsTicks;
        private int elapsedTicks;
        private int gathered;
        private int inventoryTarget;
        private final CompanionToolSense axeSense = new CompanionToolSense(CompanionEntity.this, "axe", ItemTags.AXES, Blocks.OAK_LOG.defaultBlockState());
        private int soilAttempts;
        private int pathFailTicks;
        private int lastProgressTick;
        private int targetStartedTick;
        private double bestTargetDistance;
        private boolean resumeAfterCleanup;
        private int taskVersion;
        private String accessIssue = "";
        private static final int CROWN_ATTEMPTS = 2;
        /** Farther than this a tree with no route yet is walked to, nearer it is climbed or skipped as before. */
        private static final int FAR_TREE = 12;
        private int crownFailures;
        private final Set<BlockPos> unreachableLogs = new HashSet<>();
        /** Logs of a player's building (no living crown, touching planks, doors, glass…): never chopped. */
        private final Set<BlockPos> builtLogs = new HashSet<>();
        private final Set<BlockPos> currentTreeLogs = new HashSet<>();
        /** Trunks no walk reached from where he stood: not compared on foot again during this request. */
        private final Set<BlockPos> walkTried = new HashSet<>();
        private List<BlockPos> searchCandidates;
        private BlockPos searchOrigin;
        /** The trees round the player were looked at too (once per request). */
        private boolean searchedByPlayer;

        private GatherLogsGoal(CompanionEntity ignored) {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public boolean canUse() {
            return gatheringLogs && inventoryViewers == 0 && !temporaryPillar.isRecovering();
        }

        @Override
        public boolean canContinueToUse() {
            return gatheringLogs && taskVersion == woodTaskVersion && inventoryViewers == 0 && !temporaryPillar.isRecovering();
        }

        @Override
        public void start() {
            woodBreaking.reset();
            target = null;
            approachPath = null;
            retryDelay = 0;
            if (resumeAfterCleanup && taskVersion == woodTaskVersion) {
                resumeAfterCleanup = false;
                return;
            }
            taskVersion = woodTaskVersion;
            resumeAfterCleanup = false;
            accessIssue = "";
            noLogsTicks = 0;
            elapsedTicks = 0;
            gathered = Math.max(0, requestedLogs - logsRemaining);
            // What is still missing: after a reload the logs already chopped are not asked for again (review 22.10, М1).
            inventoryTarget = CompanionTools.count(CompanionEntity.this, ItemTags.LOGS) + logsRemaining;
            axeSense.reset(); soilAttempts = 0;
            pathFailTicks = 0;
            unreachableLogs.clear(); builtLogs.clear(); walkTried.clear();
            currentTreeLogs.clear();
            searchOrigin = blockPosition();
            searchCandidates = null; searchedByPlayer = false;
        }

        @Override
        public void tick() {
            if (!gatheringLogs) return;
            elapsedTicks++;
            if (actionAnimation() != 0 && !temporaryPillar.isActive() && !temporaryPillar.isRecovering()) return;
            if (elapsedTicks > 20 * 180) {
                finish("Не успел закончить сбор древесины, попробуем позже.");
                return;
            }
            ServerPlayer owner = getOwnerPlayer();
            if (owner == null) {
                finish("Я потерял связь с тобой и остановил сбор.");
                return;
            }
            // A just-spawned or falling mob cannot build a ground path yet; this is temporary, not an unreachable tree.
            if (!onGround() && !isInWater() && !temporaryPillar.isActive()) return;
            // Marathon 23.10: with the bag full to the last slot he went from log to log, «не удалось сломать, пропускаю», and never
            // said why. A player sees the full bag first: said at once, nothing thrown away.
            if (!hasRoomForLogs()) { finish(FULL_BAG_FOR_LOGS); return; }
            if (retryDelay > 0) {
                retryDelay--;
                return;
            }
            logsRemaining = Math.max(0, inventoryTarget - CompanionTools.count(CompanionEntity.this, ItemTags.LOGS));
            entityData.set(DATA_LOGS_REMAINING, logsRemaining);
            // On a pillar the hand alternates soil and axe per jump/swing by itself; forcing the axe every tick only flickered.
            if (logsRemaining > 0 && !temporaryPillar.isActive() && axeSense.tick()) { woodBreaking.reset(); return; }
            if (logsRemaining > 0 && !temporaryPillar.isActive()) CompanionTools.equip(CompanionEntity.this, ItemTags.AXES, Blocks.OAK_LOG.defaultBlockState());
            if (target == null || !level().getBlockState(target).is(BlockTags.LOGS)) {
                woodBreaking.reset();
                if (target != null) currentTreeLogs.remove(target);
                target = selectNextLog();
                approachPath = null;
                pathFailTicks = 0;
                lastProgressTick = targetStartedTick = tickCount;
                bestTargetDistance = Double.MAX_VALUE;
            }
            if (target == null) {
                if (temporaryPillar.isRecovering()) return;
                if (loot.hasPending()) return;
                if (logsRemaining == 0 && currentTreeLogs.isEmpty()) {
                    String accessNote = unreachableLogs.isEmpty() ? "Текущее дерево закончил." : "До части брёвен не удалось добраться, поэтому оставил их.";
                    finish("Закончил сбор: срубил " + gathered + " брёвен (цель была " + requestedLogs + "). "
                            + accessNote + " У меня в сумке: " + getStoredItemsSummary() + ".");
                } else if (++noLogsTicks >= 100) finish(gathered == 0
                        ? autonomy.running() ? "В " + CompanionAutonomy.LEASH + " блоках от рабочего места не нашёл доступных деревьев."
                        : "Сбор завершён: в пределах " + searchRadius + " блоков не нашёл доступных деревьев."
                        : "Сбор завершён раньше цели: срубил " + gathered + " из " + requestedLogs + " брёвен; "
                        + (unreachableLogs.isEmpty() ? "рядом больше нет доступных брёвен. "
                        : "оставшиеся брёвна не удалось достать по пути. ")
                        + "У меня в сумке: " + getStoredItemsSummary() + ".");
                return;
            }
            noLogsTicks = 0;
            getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
            movementIntent(target);
            if (temporaryPillar.isActive()) {
                CompanionPillar.Outcome outcome = temporaryPillar.tickToward(target);
                if (switchToBranch()) return;
                if (outcome == CompanionPillar.Outcome.FAILED) {
                    rejectTarget(true);
                    return;
                }
                if (outcome == CompanionPillar.Outcome.WORKING) return;
            }
            double distance = distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
            BlockPos leaves = CompanionMiningNavigation.obstructingLeaves(CompanionEntity.this, target);
            if (leaves != null && distanceToSqr(leaves.getCenter()) <= LOG_INTERACTION_RANGE_SQR) {
                getNavigation().stop();
                if (woodBreaking.tick((ServerLevel)level(), leaves)) {
                    mineLog(leaves); woodBreaking.reset();
                }
                return;
            }
            if (distance <= LOG_INTERACTION_RANGE_SQR && CompanionMiningNavigation.visible(CompanionEntity.this, getEyePosition(), target)) {
                getNavigation().stop();
                // With no axe, put away held building supplies and use a real empty hand.
                if (getOffhandItem().is(ItemTags.AXES)) unequipForSupport("offhand");
                selectToolForBlock(level().getBlockState(target));
                if (!getMainHandItem().isEmpty() && !getMainHandItem().is(ItemTags.AXES)) {
                    CompanionActionResult emptyHand = unequipForSupport("mainhand");
                    if (!emptyHand.success()) { finish("В сумке нет места, чтобы освободить руку для рубки."); return; }
                }
                if (!woodBreaking.tick((ServerLevel)level(), target)) return;
                if (mineLog(target)) {
                    gathered++;
                    logsRemaining = Math.max(0, inventoryTarget - CompanionTools.count(CompanionEntity.this, ItemTags.LOGS));
                    entityData.set(DATA_LOGS_REMAINING, logsRemaining);
                    entityData.set(DATA_LAST_TASK_RESULT, gathered >= requestedLogs
                            ? "Цель по брёвнам выполнена; заканчиваю текущее дерево."
                            : "Срубил " + gathered + " из " + requestedLogs + " брёвен.");
                } else {
                    unreachableLogs.add(target.immutable());
                    currentTreeLogs.remove(target);
                    entityData.set(DATA_LAST_TASK_RESULT, "Не удалось сломать это бревно, пропускаю его и ищу доступное.");
                }
                woodBreaking.reset();
                currentTreeLogs.remove(target);
                target = null;
                approachPath = null;
                retryDelay = 4;
                pathFailTicks = 0;
            } else {
                woodBreaking.reset();
                if (distance + 0.1D < bestTargetDistance) {
                    bestTargetDistance = distance;
                    lastProgressTick = tickCount;
                }
                // Accepting a path is not evidence that the mob is actually getting closer.
                if (tickCount - targetStartedTick > 20 * 30) { rejectTarget(false); return; }
                boolean noProgress = tickCount - lastProgressTick >= 60;
                // Navigation considers a waypoint reached before the mob is exactly centered.
                // Give the movement controller time to line up a visible face before trying a pillar.
                if (approachPath != null && (getNavigation().isStuck() || noProgress)) {
                    tryClimbing();
                    return;
                }
                if (approachPath == null) approachPath = findApproachPath(target);
                // A tree far off over hills: walked to in legs first, not declared out of reach (game log 23.10, aa07e605).
                if (approachPath == null && Math.hypot(target.getX() + .5 - getX(), target.getZ() + .5 - getZ()) > FAR_TREE) {
                    // To the foot of the trunk, as a player walks to a tree: a route to a log up in the crown is never found, and its parts
                    // led him off swimming round an island (marathon 23.10, «берег»).
                    BlockPos foot = target;
                    while (foot.getY() > level().getMinBuildHeight() && level().getBlockState(foot.below()).is(BlockTags.LOGS)) foot = foot.below();
                    var step = travel.toward(foot, FAR_TREE - 4);
                    if (step == CompanionTravel.Step.WALKING) {
                        targetStartedTick = lastProgressTick = tickCount;
                        entityData.set(DATA_LAST_TASK_RESULT, "Иду к дереву подальше.");
                        return;
                    }
                    travel.reset();
                    if (step == CompanionTravel.Step.FAILED) { rejectTarget(false); return; }
                    approachPath = findApproachPath(target);
                }
                if (approachPath == null) { tryClimbing(); return; }
                CompanionMiningNavigation.walk(CompanionEntity.this, approachPath);
            }
        }

        private void tryClimbing() {
            CompanionPillar.Outcome outcome = temporaryPillar.tickToward(target);
            if (switchToBranch()) return;
            if (outcome == CompanionPillar.Outcome.FAILED && temporaryPillar.failureReason().contains("земли") && soilAttempts++ < 2) {
                savedWoodRequested = requestedLogs; savedWoodRemaining = logsRemaining;
                supportSoilTask = true;
                startGatheringDirtForWork(Math.max(1, 6 - CompanionTools.soil(CompanionEntity.this)));
                workStatus("Для верхушки нужна опора. Сначала добуду немного земли.");
            }
            else if (outcome == CompanionPillar.Outcome.FAILED) rejectTarget(true);
            else if (outcome == CompanionPillar.Outcome.READY && !CompanionMiningNavigation.visible(CompanionEntity.this, getEyePosition(), target)) rejectTarget(false);
            else entityData.set(DATA_LAST_TASK_RESULT, "Поднимаюсь на временной опоре, чтобы достать верхние брёвна.");
        }

        /** A crown log over the pillar is chopped from where Oleg already stands, instead of abandoning the crown. */
        private boolean switchToBranch() {
            BlockPos branch = temporaryPillar.takeBranch();
            if (branch == null || unreachableLogs.contains(branch)) return false;
            woodBreaking.reset();
            currentTreeLogs.add(branch);
            target = branch;
            approachPath = null;
            lastProgressTick = targetStartedTick = tickCount;
            bestTargetDistance = Double.MAX_VALUE;
            entityData.set(DATA_LAST_TASK_RESULT, "Над опорой ветка, срублю её отсюда.");
            return true;
        }

        private void rejectTarget(boolean skipHigherCrown) {
            woodBreaking.reset();
            // One failed climb is not proof that the whole crown is out of reach: try another log of it once more.
            // A crown that is simply too tall, or a second failure on the same tree, still ends with the honest skip.
            String reason = temporaryPillar.failureReason();
            if (skipHigherCrown && ++crownFailures < CROWN_ATTEMPTS && !reason.contains("слишком высокая")) skipHigherCrown = false;
            if (skipHigherCrown) accessIssue = reason;
            if (target != null) {
                unreachableLogs.add(target.immutable());
                if (skipHigherCrown) {
                    for (BlockPos log : currentTreeLogs) if (log.getY() >= target.getY()) unreachableLogs.add(log);
                }
                currentTreeLogs.removeIf(unreachableLogs::contains);
            }
            temporaryPillar.requestCleanup();
            entityData.set(DATA_GATHERING_LOGS, true);
            entityData.set(DATA_LAST_TASK_RESULT, "До этой части дерева безопасно не добраться, ищу другую.");
            target = null;
            approachPath = null;
            getNavigation().stop();
            retryDelay = 2;
        }

        @Override
        public void stop() {
            woodBreaking.reset();
            getNavigation().stop();
            target = null;
            temporaryPillar.requestCleanup();
            resumeAfterCleanup = gatheringLogs || supportSoilTask;
        }

        private BlockPos findNearestLog() {
            if (!(level() instanceof ServerLevel serverLevel)) return null;
            if (searchCandidates == null) searchCandidates = CompanionLogSearch.find(serverLevel, searchOrigin, searchRadius);
            List<BlockPos> nearest = searchCandidates.stream().filter(candidate -> !unreachableLogs.contains(candidate) && !builtLogs.contains(candidate)
                    && leashAllows(candidate) && level().hasChunkAt(candidate) && level().getBlockState(candidate).is(BlockTags.LOGS))
                    .sorted(Comparator.comparingDouble(candidate -> candidate.distSqr(blockPosition()) + Math.max(0, candidate.getY() - getY()) * 4))
                    .limit(64).toList();
            // Marathon 23.10, «берег»: the trees were looked for once, from the mainland; on the island the two farther ones were beyond
            // that radius and «рядом больше нет доступных брёвен» with them in sight. Walked far from where he looked: look again here.
            if (nearest.isEmpty() && searchOrigin != null && searchOrigin.distSqr(blockPosition()) > 4 * 4) {
                // What was seen from the first place stays on the list; what is seen from here is added.
                searchOrigin = blockPosition();
                Set<BlockPos> merged = new java.util.LinkedHashSet<>(searchCandidates);
                merged.addAll(CompanionLogSearch.find(serverLevel, searchOrigin, searchRadius));
                searchCandidates = new java.util.ArrayList<>(merged);
                return findNearestLog();
            }
            // The order was «near the player»: the trees round him count too (marathon 23.10, night in «долина»: driving monsters off
            // he had gone twelve blocks from the player, and from there the forest by the player was two blocks beyond the radius).
            var player = getOwnerPlayer();
            if (nearest.isEmpty() && !searchedByPlayer && !autonomy.running() && player != null && player.level() == level() && player.blockPosition().distSqr(blockPosition()) > 4 * 4) {
                searchedByPlayer = true;
                Set<BlockPos> merged = new java.util.LinkedHashSet<>(searchCandidates);
                merged.addAll(CompanionLogSearch.find(serverLevel, player.blockPosition(), searchRadius));
                searchCandidates = new java.util.ArrayList<>(merged);
                return findNearestLog();
            }
            if (nearest.isEmpty()) return null;
            // Like stone (game log 22.10): of the nearest trunks, the one nearest on foot, not across a ravine by the straight line.
            // None reachable on foot: the nearest, for the climbing and support logic, as before.
            Set<Long> trunks = new HashSet<>();
            List<BlockPos> group = nearest.stream().filter(candidate -> !walkTried.contains(candidate)
                    && trunks.add(BlockPos.asLong(candidate.getX(), 0, candidate.getZ()))).limit(8).toList();
            var walk = group.isEmpty() ? null : CompanionMiningNavigation.nearestOnFoot(CompanionEntity.this, group, 3.7, true, false);
            if (walk == null) walkTried.addAll(group);
            return walk == null ? nearest.get(0) : walk.block();
        }
        /** Keep working on the connected tree after the requested minimum is reached. */
        private BlockPos selectNextLog() {
            currentTreeLogs.removeIf(pos -> unreachableLogs.contains(pos) || !level().hasChunkAt(pos)
                    || !level().getBlockState(pos).is(BlockTags.LOGS));
            if (currentTreeLogs.isEmpty()) {
                if (temporaryPillar.hasSupports()) { temporaryPillar.requestCleanup(); return null; }
                // Do not start another tree and its climbing prerequisites while the last
                // already harvested log is still waiting for its ground pickup delay.
                if (logsRemaining > 0 && loot.incomingLogs() >= logsRemaining) return null;
                if (CompanionTools.count(CompanionEntity.this, ItemTags.LOGS) >= inventoryTarget) return null;
                // Review 22.10, К2: a log cabin is not a tree. A few groups a tick, nearest first.
                for (int tries = 0; tries < 8 && currentTreeLogs.isEmpty(); tries++) {
                    BlockPos seed = findNearestLog();
                    if (seed == null) return null;
                    collectConnectedLogs(seed);
                    if (!CompanionBuildings.builtLogs((ServerLevel) level(), currentTreeLogs)
                            && !(ownPlacesOnly() && CompanionBuildings.decorated((ServerLevel) level(), currentTreeLogs))) break;
                    dev.aicompanion.CompanionDiagnostics.event(diagnostics.trace(), "building_logs_skipped", "entity", getUUID(), "seed", seed, "logs", currentTreeLogs.size());
                    builtLogs.addAll(currentTreeLogs); currentTreeLogs.clear();
                }
                if (currentTreeLogs.isEmpty()) return null;
            }
            BlockPos best = null;
            double bestScore = Double.MAX_VALUE;
            BlockPos origin = blockPosition();
            for (BlockPos log : currentTreeLogs) {
                double score = distanceToSqr(log.getX() + 0.5D, log.getY() + 0.5D, log.getZ() + 0.5D)
                        + Math.max(0, log.getY() - origin.getY()) * 4.0D;
                if (score < bestScore) { bestScore = score; best = log; }
            }
            return best;
        }

        private void collectConnectedLogs(BlockPos seed) {
            crownFailures = 0;
            Deque<BlockPos> queue = new ArrayDeque<>();
            Set<BlockPos> visited = new HashSet<>();
            queue.add(seed.immutable());
            visited.add(seed.immutable());
            while (!queue.isEmpty() && currentTreeLogs.size() < 512) {
                BlockPos pos = queue.removeFirst();
                if (!level().hasChunkAt(pos) || !level().getBlockState(pos).is(BlockTags.LOGS)) continue;
                currentTreeLogs.add(pos.immutable());
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            BlockPos next = pos.offset(dx, dy, dz);
                            if (Math.abs(next.getX() - seed.getX()) > 12 || Math.abs(next.getZ() - seed.getZ()) > 12
                                    || Math.abs(next.getY() - seed.getY()) > 24 || !visited.add(next)) continue;
                            if (level().hasChunkAt(next) && level().getBlockState(next).is(BlockTags.LOGS)) queue.addLast(next);
                        }
                    }
                }
            }
        }

        /** Find a reachable standing spot next to the log; never path into the solid log itself. */
        private Path findApproachPath(BlockPos log) {
            Path path = CompanionMiningNavigation.pathTo(CompanionEntity.this, log, 3.7, true);
            return path != null && path.canReach() ? path : null;
        }

        private void finish(String message) {
            woodBreaking.reset();
            if (!accessIssue.isBlank()) message += " " + accessIssue + ".";
            lastTaskSucceeded = CompanionTools.count(CompanionEntity.this, ItemTags.LOGS) >= inventoryTarget;
            gatheringLogs = false;
            logsRemaining = 0;
            entityData.set(DATA_GATHERING_LOGS, false);
            entityData.set(DATA_LOGS_REMAINING, 0);
            entityData.set(DATA_LAST_TASK_RESULT, message);
            target = null;
            // As a step of a work the work's own result is the deed.
            if (work.isActive()) syncWorkState();
            else { noteDeed(message, lastTaskSucceeded); finishFollowing(); }
        }
    }

    private final class GatherDirtGoal extends Goal {
        private BlockPos dirtOrigin;
        private BlockPos target;
        private Path approachPath;
        private final Set<BlockPos> unreachable = new HashSet<>();
        private int retryDelay;
        private int noDirtTicks;
        private int elapsedTicks;
        private int gathered;
        private int pathFailTicks;
        private int inventoryTarget;
        private int taskVersion;
        private boolean resume;
        private final CompanionToolSense shovelSense = new CompanionToolSense(CompanionEntity.this, "shovel", ItemTags.SHOVELS, Blocks.DIRT.defaultBlockState());
        private int targetSince, targetProgress;
        private double targetBest;

        private GatherDirtGoal(CompanionEntity ignored) { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public boolean canUse() { return gatheringDirt && inventoryViewers == 0; }
        @Override public boolean canContinueToUse() { return gatheringDirt && inventoryViewers == 0; }

        @Override public void start() {
            dirtBreaking.reset();
            target = null;
            approachPath = null;
            if (resume && taskVersion == dirtTaskVersion) { resume = false; return; }
            resume = false; taskVersion = dirtTaskVersion;
            dirtOrigin = blockPosition();
            retryDelay = noDirtTicks = elapsedTicks = pathFailTicks = 0;
            gathered = Math.max(0, requestedLogs - logsRemaining);
            inventoryTarget = CompanionTools.soil(CompanionEntity.this) + logsRemaining;
            shovelSense.reset();
            unreachable.clear();
        }

        @Override public void tick() {
            if (!gatheringDirt) return;
            if (actionAnimation() != 0) return;
            if (++elapsedTicks > 20 * 180) { finish("Остановил добычу земли по тайм-ауту."); return; }
            if (getOwnerPlayer() == null) { finish("Я потерял связь с тобой и остановил добычу земли."); return; }
            logsRemaining = Math.max(0, inventoryTarget - CompanionTools.soil(CompanionEntity.this));
            entityData.set(DATA_LOGS_REMAINING, logsRemaining);
            if (logsRemaining <= 0 && !loot.hasPending()) { finish("Добыча земли завершена: добыл " + gathered + " блоков. В сумке: " + getStoredItemsSummary() + "."); return; }
            if (logsRemaining > 0 && loot.incomingSoil() >= logsRemaining) { dirtBreaking.reset(); getNavigation().stop(); return; }
            // Soil for a support under a crown: by hand, a shovel would eat the wood just cut (marathon 23.10: «добудь дерева» ended
            // with fewer logs than asked, one gone into a shovel for six blocks of dirt).
            if (!supportSoilTask && shovelSense.tick()) { dirtBreaking.reset(); approachPath = null; return; }
            CompanionTools.equip(CompanionEntity.this, ItemTags.SHOVELS, Blocks.DIRT.defaultBlockState());
            if (retryDelay > 0) { retryDelay--; return; }

            if (target != null && (CompanionMiningNavigation.supportsLivingEntity(CompanionEntity.this, target)
                    || !CompanionQuarry.safeCell((ServerLevel)level(), target))) {
                unreachable.add(target); dirtBreaking.reset(); target = null;
            }
            if (target == null || !level().hasChunkAt(target) || !isDirtBlock(level().getBlockState(target))) {
                dirtBreaking.reset();
                target = findNearestDirt();
                approachPath = null;
                pathFailTicks = 0;
                targetSince = targetProgress = tickCount; targetBest = Double.MAX_VALUE;
            }
            if (target == null) {
                if (++noDirtTicks >= 80) finish("Нашёл только " + gathered + " из " + requestedLogs
                        + " блоков земли поблизости. Поставь меня у открытого участка земли и попроси снова.");
                return;
            }
            noDirtTicks = 0;
            movementIntent(target);
            getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
            double distance = distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
            if (!CompanionMiningNavigation.preservesAutonomousReturn(CompanionEntity.this, target)) {
                unreachable.add(target.immutable()); dirtBreaking.reset(); target = null; approachPath = null; getNavigation().stop(); return;
            }
            if (distance + .05 < targetBest) { targetBest = distance; targetProgress = tickCount; }
            if (tickCount - targetSince > 20 * 30 || tickCount - targetProgress > 160) {
                unreachable.add(target.immutable()); dirtBreaking.reset(); target = null; approachPath = null; getNavigation().stop(); return;
            }
            if (distance <= LOG_INTERACTION_RANGE_SQR && CompanionMiningNavigation.visible(CompanionEntity.this, getEyePosition(), target)
                    && CompanionMiningNavigation.dropCollectable((ServerLevel)level(), target, blockPosition())) {
                getNavigation().stop();
                selectToolForBlock(level().getBlockState(target));
                if (!dirtBreaking.tick((ServerLevel)level(), target)) return;
                if (mineDirt(target)) {
                    gathered++;
                    logsRemaining = Math.max(0, inventoryTarget - CompanionTools.soil(CompanionEntity.this));
                    entityData.set(DATA_LOGS_REMAINING, logsRemaining);
                    entityData.set(DATA_LAST_TASK_RESULT, "Добыл " + gathered + " из " + requestedLogs + " блоков земли.");
                } else {
                    unreachable.add(target.immutable());
                    entityData.set(DATA_LAST_TASK_RESULT, "Не удалось сломать этот блок земли, ищу другой.");
                }
                dirtBreaking.reset();
                target = null;
                approachPath = null;
                retryDelay = 8;
                pathFailTicks = 0;
                return;
            }

            dirtBreaking.reset();

            if (getNavigation().isStuck()) {
                unreachable.add(target.immutable());
                target = null;
                approachPath = null;
                getNavigation().stop();
                retryDelay = 10;
                pathFailTicks = 0;
                return;
            }
            if (approachPath == null || getNavigation().isDone()) approachPath = findDirtApproachPath(target);
            if (approachPath == null || !getNavigation().moveTo(approachPath, 1.0D)) {
                if (++pathFailTicks >= 20) {
                    unreachable.add(target.immutable());
                    target = null;
                    approachPath = null;
                    getNavigation().stop();
                    retryDelay = 10;
                    pathFailTicks = 0;
                } else approachPath = null;
            } else pathFailTicks = 0;
        }

        private BlockPos findNearestDirt() {
            BlockPos origin = blockPosition();
            BlockPos best = null;
            double bestScore = Double.MAX_VALUE;
            MutableBlockPos cursor = new MutableBlockPos();
            for (int x = origin.getX() - 12; x <= origin.getX() + 12; x++) {
                for (int z = origin.getZ() - 12; z <= origin.getZ() + 12; z++) {
                    for (int y = Math.max(origin.getY() - 1, dirtOrigin.getY() - 1); y <= origin.getY() + 5; y++) {
                        cursor.set(x, y, z);
                        if (!level().hasChunkAt(cursor) || !isDirtBlock(level().getBlockState(cursor)) || !leashAllows(cursor)
                                || unreachable.contains(cursor) || (x == origin.getX() && z == origin.getZ() && y <= origin.getY())) continue;
                        double score = distanceToSqr(x + 0.5D, y + 0.5D, z + 0.5D);
                        if (score >= bestScore) continue;
                        boolean exposed = false;
                        for (Direction direction : Direction.values()) {
                            BlockPos adjacent = cursor.relative(direction);
                            if (level().hasChunkAt(adjacent)
                                    && level().getBlockState(adjacent).getCollisionShape(level(), adjacent).isEmpty()) {
                                exposed = true;
                                break;
                            }
                        }
                        if (!exposed) continue;
                        if (CompanionMiningNavigation.supportsLivingEntity(CompanionEntity.this, cursor)
                                || !CompanionQuarry.safeCell((ServerLevel)level(), cursor)) continue;
                        if (score < bestScore) { bestScore = score; best = cursor.immutable(); }
                    }
                }
            }
            return best;
        }

        private Path findDirtApproachPath(BlockPos block) {
            // The previous adjacent-cell search could end behind a ledge with no view of the block, endlessly alternating
            // a single swing and the same walk. Use the common visible approach with a collectable physical drop.
            return CompanionMiningNavigation.pathTo(CompanionEntity.this, block, 3.5, false, true, true);
        }

        private void finish(String message) {
            lastTaskSucceeded = CompanionTools.soil(CompanionEntity.this) >= inventoryTarget;
            if (supportSoilTask) {
                supportSoilTask = false; gatheringDirt = false; gatheringLogs = true;
                requestedLogs = savedWoodRequested; logsRemaining = savedWoodRemaining;
                workStatus(lastTaskSucceeded ? "Земля для опоры есть, возвращаюсь к дереву." : "Доступной земли не нашёл, попробую другое дерево.");
                getNavigation().stop(); return;
            }
            gatheringDirt = false;
            gatheringLogs = false;
            logsRemaining = 0;
            entityData.set(DATA_GATHERING_LOGS, false);
            entityData.set(DATA_LOGS_REMAINING, 0);
            entityData.set(DATA_LAST_TASK_RESULT, message);
            target = null;
            if (work.isActive()) syncWorkState(); else { noteDeed(message, lastTaskSucceeded); finishFollowing(); }
        }

        @Override public void stop() { resume = gatheringDirt; dirtBreaking.reset(); getNavigation().stop(); target = null; }
    }

    private boolean storeInCompanionInventory(ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (ItemStack stored : companionInventory) {
            if (stored.isEmpty() || !ItemStack.isSameItemSameComponents(stored, remaining)) continue;
            int move = Math.min(remaining.getCount(), stored.getMaxStackSize() - stored.getCount());
            if (move > 0) {
                stored.grow(move);
                remaining.shrink(move);
            }
            if (remaining.isEmpty()) {
                refreshInventorySummary();
                stack.setCount(0);
                return true;
            }
        }
        for (int slot = 0; slot < companionInventory.size() && !remaining.isEmpty(); slot++) {
            if (companionInventory.get(slot).isEmpty()) {
                companionInventory.set(slot, remaining.copy());
                remaining.setCount(0);
            }
        }
        refreshInventorySummary();
        stack.setCount(remaining.getCount());
        return remaining.isEmpty();
    }

    private void refreshInventorySummary() {
        entityData.set(DATA_INVENTORY_SUMMARY, buildInventorySummary());
        java.util.Map<String, Integer> items = new java.util.LinkedHashMap<>();
        for (ItemStack stack : companionInventory) {
            if (!stack.isEmpty()) items.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
        }
        entityData.set(DATA_INVENTORY_AI, items.isEmpty() ? "пусто" : items.entrySet().stream()
                .map(entry -> entry.getValue() + "x " + entry.getKey()).collect(java.util.stream.Collectors.joining(", ")));
    }

    /**
     * Who may be fought (review 22.10, В1–В2): never the player, his pets or another companion; never the warden. Neutral mobs
     * — endermen, (zombified) piglins, spiders — only once they attack (provoked): hitting a zombified piglin raises the whole group.
     */
    boolean fairTarget(LivingEntity entity, boolean provoked) {
        if (entity == null || !entity.isAlive() || entity == this || entity instanceof Player || entity instanceof CompanionEntity) return false;
        if (ownerId != null && entity instanceof net.minecraft.world.entity.OwnableEntity pet && ownerId.equals(pet.getOwnerUUID())) return false;
        if (entity instanceof net.minecraft.world.entity.monster.warden.Warden) return false;
        boolean neutral = entity instanceof net.minecraft.world.entity.NeutralMob || entity instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin
                || entity instanceof net.minecraft.world.entity.monster.Spider;
        return !neutral || provoked;
    }

    /** The neutral mob is going for the player or for Oleg himself. */
    boolean provokes(LivingEntity entity) {
        if (!(entity instanceof net.minecraft.world.entity.Mob mob) || mob.getTarget() == null) return false;
        return mob.getTarget() == this || ownerId != null && ownerId.equals(mob.getTarget().getUUID());
    }

    /** A last guard: whatever goal picked them, the player and his pets are never a target. */
    @Override public void setTarget(LivingEntity target) {
        if (target != null && !level().isClientSide && (target instanceof Player || target instanceof CompanionEntity
                || ownerId != null && target instanceof net.minecraft.world.entity.OwnableEntity pet && ownerId.equals(pet.getOwnerUUID()))) target = null;
        super.setTarget(target);
    }

    /**
     * Without a guard order, like a friend (Толик 22.10: «когда рядом со мной монстр, он его не бьёт, только если он уже на меня
     * напал»): a monster at the player's side, or one going for the player or for Oleg, is met first. Only while Oleg is near the
     * player; never a creeper (a hit beside the player may end in its blast — wolves leave them alone too), never his own hunt.
     */
    private boolean comesForOwner(net.minecraft.world.entity.LivingEntity target) {
        if (!(level() instanceof ServerLevel serverLevel) || ownerId == null || inventoryViewers != 0 || work.isHunting()
                || !(target instanceof Monster monster) || monster instanceof net.minecraft.world.entity.monster.Creeper) return false;
        if (!fairTarget(monster, provokes(monster))) return false;
        ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(ownerId);
        if (owner == null || owner.level() != level() || owner.isSpectator() || distanceToSqr(owner) > GUARD_RANGE * GUARD_RANGE) return false;
        return monster.distanceToSqr(owner) <= 6 * 6 || monster.distanceToSqr(this) <= 4 * 4
                || (monster.getTarget() == owner || monster.getTarget() == this) && monster.distanceToSqr(owner) <= GUARD_RANGE * GUARD_RANGE;
    }
    /** How far from the player Oleg still steps in for him, and how far a fight may take him. */
    private static final int GUARD_RANGE = 16;

    private boolean isThreateningOwner(net.minecraft.world.entity.LivingEntity target) {
        if (!(level() instanceof ServerLevel serverLevel) || ownerId == null || !(target instanceof Monster monster)) return false;
        if (work.isHunting()) return work.isHuntTarget(target.getUUID());
        if (!fairTarget(monster, provokes(monster))) return false;
        ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(ownerId);
        return owner != null && owner.level() == level()
                && (monster.getTarget() == owner || monster.getTarget() == this || monster.distanceToSqr(owner) <= 100.0D || monster.distanceToSqr(this) < 36);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        tag.putBoolean("Following", following);
        tag.putBoolean("Protecting", protecting);
        tag.putBoolean("ChunkLoading", chunkLoading); tag.putBoolean("AutonomousSpeech", autonomousSpeech);
        tag.putString("PreferredWeapon", preferredWeapon);
        tag.putString("CompanionName", getName().getString());
        tag.putInt("SearchRadius", searchRadius);
        tag.putBoolean("Roaming", roaming);
        if (roamOrigin != null) tag.putLong("RoamOrigin", roamOrigin.asLong());
        tag.putBoolean("GatheringLogs", gatheringLogs);
        tag.putBoolean("GatheringDirt", gatheringDirt);
        tag.putBoolean("SupportSoilTask", supportSoilTask);
        tag.putInt("LogsRemaining", logsRemaining);
        tag.putInt("RequestedLogs", requestedLogs);
        tag.putString("LastTaskResult", getLastTaskResult());
        tag.putInt("Satiety", satiety);
        ContainerHelper.saveAllItems(tag, companionInventory, level().registryAccess());
        temporaryPillar.save(tag);
        work.save(tag);
        places.save(tag);
        deeds.save(tag);
        autonomy.save(tag);
        goals.save(tag);
        social.save(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        chunkLoading = !tag.contains("ChunkLoading") || tag.getBoolean("ChunkLoading");
        autonomousSpeech = !tag.contains("AutonomousSpeech") || tag.getBoolean("AutonomousSpeech");
        preferredWeapon = tag.getString("PreferredWeapon");
        if (tag.contains("CompanionName")) setCompanionName(tag.getString("CompanionName"));
        setSearchRadius(tag.contains("SearchRadius") ? tag.getInt("SearchRadius") : 64);
        roaming = tag.getBoolean("Roaming");
        roamOrigin = tag.contains("RoamOrigin") ? BlockPos.of(tag.getLong("RoamOrigin")) : blockPosition();
        entityData.set(DATA_ROAMING, roaming);
        if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
        entityData.set(DATA_OWNER, Optional.ofNullable(ownerId));
        following = !tag.contains("Following") || tag.getBoolean("Following");
        entityData.set(DATA_FOLLOWING, following);
        protecting = tag.getBoolean("Protecting");
        entityData.set(DATA_PROTECTING, protecting);
        gatheringLogs = tag.getBoolean("GatheringLogs");
        gatheringDirt = tag.getBoolean("GatheringDirt");
        entityData.set(DATA_GATHERING_LOGS, gatheringLogs || gatheringDirt);
        logsRemaining = Math.max(0, Math.min(64, tag.getInt("LogsRemaining")));
        entityData.set(DATA_LOGS_REMAINING, logsRemaining);
        requestedLogs = Math.max(0, Math.min(64, tag.getInt("RequestedLogs")));
        satiety = Math.max(0, Math.min(40, tag.getInt("Satiety"))); // absent in older saves: 0
        ContainerHelper.loadAllItems(tag, companionInventory, level().registryAccess());
        deeds.load(tag); places.load(tag); autonomy.load(tag); refreshPlaces();
        social.load(tag);
        entityData.set(DATA_LAST_TASK_RESULT, tag.getString("LastTaskResult"));
        refreshInventorySummary();
        if (protecting || roaming) following = false;
        if (gatheringLogs || gatheringDirt) {
            following = false;
            protecting = false;
        }
        entityData.set(DATA_FOLLOWING, following);
        entityData.set(DATA_PROTECTING, protecting);
        entityData.set(DATA_GATHERING_LOGS, gatheringLogs || gatheringDirt);
        temporaryPillar.load(tag);
        work.load(tag);
        setCanPickUpLoot(false);
        if (temporaryPillar.isRecovering() || tag.getBoolean("WorkActive") || tag.getBoolean("SupportSoilTask")) {
            setFollowing(true);
            lastTaskSucceeded = false;
            entityData.set(DATA_LAST_TASK_RESULT, temporaryPillar.isRecovering()
                    ? "Добыча была прервана выходом из мира. Сначала спущусь и уберу свою опору."
                    : "Работа прервалась выходом из мира. Вещи остались у меня.");
            // Pickup/navigation statuses soon replace the visible line. Keep the interruption in saved factual memory too.
            noteDeed(entityData.get(DATA_LAST_TASK_RESULT), false);
        }
        // His own harvest still lying round him (it carries his UUID) is picked up after the world is entered again, like a player
        // who comes back to the tree he was cutting (marathon 23.10: a log stayed on the ground after the reload).
        if (following || tag.getBoolean("WorkActive") || gatheringLogs) loot.resumeCollection();
        goals.load(tag);
        // Interrupted gathering belongs to the outcome now: old remaining counts are not a new order.
        if (goals.outstanding()) {
            boolean savedFollowing = tag.getBoolean("Following"), savedProtecting = tag.getBoolean("Protecting");
            setFollowing(savedFollowing);
            if (savedProtecting) setProtecting(true);
            goals.restorePlayerBehavior();
        }
        if (!level().isClientSide) updateExpression();
    }

    /**
     * Swings as often as the weapon in hand lets a player (a stone sword 1.6 a second, an axe 0.8), not the mob's once a second
     * (marathon 23.10, night: a zombie and a skeleton killed him with a stone sword — the zombie struck as often as he did and
     * he fell a single swing short). The damage is the weapon's, as before.
     */
    private final class PlayerPaceAttackGoal extends MeleeAttackGoal {
        PlayerPaceAttackGoal() { super(CompanionEntity.this, 1.15D, true); }
        private int nextSwing;
        @Override protected void checkAndPerformAttack(LivingEntity target) {
            if (tickCount < nextSwing || !isWithinMeleeAttackRange(target) || !getSensing().hasLineOfSight(target)) return;
            nextSwing = tickCount + weaponCooldownTicks();
            swing(InteractionHand.MAIN_HAND);
            doHurtTarget(target);
        }
    }
    /**
     * A player's reach for a blow: three blocks from the eyes to the nearest point of the body (marathon 23.10: with a zombie's
     * arm's length every hit knocked it out of reach, he had to step in again, and the zombie struck as often as he did).
     */
    @Override public boolean isWithinMeleeAttackRange(LivingEntity target) {
        var box = target.getBoundingBox();
        var eye = getEyePosition();
        return eye.distanceToSqr(Math.clamp(eye.x, box.minX, box.maxX), Math.clamp(eye.y, box.minY, box.maxY), Math.clamp(eye.z, box.minZ, box.maxZ)) <= 3.0 * 3.0;
    }

    int weaponCooldownTicks() {
        double speed = 4.0;
        for (var entry : getMainHandItem().getAttributeModifiers().modifiers())
            if (entry.attribute().is(Attributes.ATTACK_SPEED) && entry.slot().test(EquipmentSlot.MAINHAND)
                    && entry.modifier().operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE) speed += entry.modifier().amount();
        return Math.max(6, Math.min(40, (int) Math.ceil(20 / Math.max(.5, speed))));
    }

    /** A visible recent attacker of the owner interrupts work without waiting for an LLM. */
    private final class OwnerDefenseGoal extends Goal {
        private int handledTimestamp = Integer.MIN_VALUE;
        private LivingEntity attacker;
        private int timestamp;
        OwnerDefenseGoal() { setFlags(EnumSet.of(Flag.TARGET)); }
        @Override public boolean canUse() {
            ServerPlayer player = getOwnerPlayer();
            if (player == null || inventoryViewers != 0) return false;
            timestamp = player.getLastHurtByMobTimestamp();
            attacker = player.getLastHurtByMob();
            return timestamp != handledTimestamp && player.tickCount - timestamp < 100
                    && attacker instanceof Monster && fairTarget(attacker, true) && distanceToSqr(attacker) <= 18 * 18 && hasLineOfSight(attacker);
        }
        @Override public void start() {
            handledTimestamp = timestamp; setTarget(attacker); selectWeaponForCombat();
            dev.aicompanion.CompanionDiagnostics.event(diagnosticTrace(), "owner_defense", "entity", getUUID(), "attacker", attacker.getUUID(), "reason", "recent_visible_attack");
        }
        @Override public boolean canContinueToUse() {
            var player = getOwnerPlayer();
            return player != null && attacker != null && attacker.isAlive() && getTarget() == attacker
                    && distanceToSqr(player) <= 24 * 24 && attacker.distanceToSqr(player) <= 18 * 18;
        }
        @Override public void stop() { if (getTarget() == attacker) setTarget(null); attacker = null; }
    }

    /** Descent survives command changes, interrupted mining and world reloads. */
    private final class RecoverSupportsGoal extends Goal {
        private RecoverSupportsGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }
        @Override public boolean canUse() { return temporaryPillar.isRecovering() && inventoryViewers == 0; }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void start() { getNavigation().stop(); }
        @Override public void tick() {
            if (temporaryPillar.tickCleanup()) syncWorkState();
        }
    }

    private final class WorkGoal extends Goal {
        private WorkGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }
        @Override public boolean canUse() { return work.isActive() && !gatheringLogs && !gatheringDirt && canWorkNow(); }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void tick() { if (canUse()) work.tick(); }
        @Override public void stop() { work.pause(); getNavigation().stop(); }
    }

    private final class PickupLootGoal extends Goal {
        private PickupLootGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { return loot.canChase(); }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void tick() { loot.tickChase(); }
        @Override public void stop() { loot.pause(); getNavigation().stop(); }
    }

    private final class RecoverRouteGoal extends Goal {
        private RecoverRouteGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }
        @Override public boolean canUse() { return recovery.canStart(); }
        @Override public boolean canContinueToUse() { return recovery.active() && canCollectItems(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void start() { recovery.begin(); }
        @Override public void tick() { recovery.tick(); }
        @Override public void stop() { recovery.cancel(); getNavigation().stop(); }
    }

    private final class RoamGoal extends Goal {
        private int nextAttempt;
        private RoamGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { return roaming && inventoryViewers == 0; }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public void start() { nextAttempt = 0; }
        @Override public void tick() {
            if (!(level() instanceof ServerLevel serverLevel)) return;
            if (!onGround() && !isInWater()) return;
            if (!getNavigation().isDone() && !getNavigation().isStuck()) return;
            if (tickCount < nextAttempt) return;
            nextAttempt = tickCount + 60;
            if (roamOrigin == null) roamOrigin = blockPosition();
            Set<BlockPos> destinations = new HashSet<>();
            Set<BlockPos> nearbyDestinations = new HashSet<>();
            double preferredDistance = Math.max(8.0D, searchRadius * 0.7D);
            for (int attempt = 0; attempt < 12; attempt++) {
                int x = roamOrigin.getX() + random.nextInt(searchRadius * 2 + 1) - searchRadius;
                int z = roamOrigin.getZ() + random.nextInt(searchRadius * 2 + 1) - searchRadius;
                if (serverLevel.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) continue;
                BlockPos feet = findStandingPosition(serverLevel, x, z);
                if (feet == null) continue;
                if (feet.distSqr(blockPosition()) < 64 || feet.distSqr(roamOrigin) > (double) searchRadius * searchRadius) continue;
                nearbyDestinations.add(feet);
                if (feet.distSqr(blockPosition()) >= preferredDistance * preferredDistance) destinations.add(feet);
            }
            Path path = getNavigation().createPath(destinations, 0);
            if (path == null || !path.canReach()) path = getNavigation().createPath(nearbyDestinations, 0);
            if (path != null && path.canReach()) getNavigation().moveTo(path, 0.9D);
        }

        private BlockPos findStandingPosition(ServerLevel serverLevel, int x, int z) {
            // Prefer the current ground level. A heightmap can point to an inaccessible
            // tree canopy or a roof while there is a perfectly walkable route underneath.
            for (int dy = 0; dy <= 12; dy++) {
                BlockPos above = new BlockPos(x, blockPosition().getY() + dy, z);
                if (isWalkable(serverLevel, above)) return above;
                if (dy > 0) {
                    BlockPos below = new BlockPos(x, blockPosition().getY() - dy, z);
                    if (isWalkable(serverLevel, below)) return below;
                }
            }
            return null;
        }

        private boolean isWalkable(ServerLevel serverLevel, BlockPos feet) {
            return serverLevel.getFluidState(feet).isEmpty() && serverLevel.getFluidState(feet.above()).isEmpty()
                    && serverLevel.getBlockState(feet.below()).isFaceSturdy(serverLevel, feet.below(), Direction.UP)
                    && serverLevel.getBlockState(feet).getCollisionShape(serverLevel, feet).isEmpty()
                    && serverLevel.getBlockState(feet.above()).getCollisionShape(serverLevel, feet.above()).isEmpty();
        }
        @Override public void stop() { getNavigation().stop(); }
    }

    private static final class FollowPlayerGoal extends Goal {
        private final CompanionEntity companion;
        private ServerPlayer owner;

        private FollowPlayerGoal(CompanionEntity companion) {
            this.companion = companion;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (companion.autonomy.staysAtWork() || (!companion.following && !companion.protecting) || companion.inventoryViewers > 0 || companion.ownerId == null || !(companion.level() instanceof ServerLevel serverLevel)) {
                return false;
            }
            owner = serverLevel.getServer().getPlayerList().getPlayer(companion.ownerId);
            return owner != null && owner.level() == companion.level()
                    && companion.distanceToSqr(owner) > (companion.protecting ? 16 : 64);
        }

        @Override
        public boolean canContinueToUse() {
            return !companion.autonomy.staysAtWork() && (companion.following || companion.protecting) && companion.inventoryViewers == 0 && owner != null && owner.isAlive()
                    && owner.level() == companion.level() && companion.distanceToSqr(owner) > (companion.protecting ? 6.25 : 16.0D);
        }

        @Override
        public void tick() {
            if (owner == null) return;
            companion.getLookControl().setLookAt(owner, 20.0F, 30.0F);
            // Far off, like a player who lost sight of a friend: walked to in legs, round walls and over ridges, never a teleport
            // (marathon 23.10: beyond 128 blocks he used to appear beside the player). Near, the game's own following.
            if (companion.distanceToSqr(owner) > FAR_FOLLOW * FAR_FOLLOW) {
                if (!companion.onGround() && !companion.isInWater()) return;
                var step = companion.travel().follow(owner.blockPosition(), FAR_FOLLOW - 8);
                // Half a minute without coming closer: start the walk anew (the self-rescue takes over where he is stuck).
                if (step != CompanionTravel.Step.WALKING) companion.travel().reset();
                travelling = step == CompanionTravel.Step.WALKING;
                return;
            }
            if (travelling) { companion.travel().reset(); travelling = false; }
            companion.getNavigation().moveTo(owner, 1.15D);
        }

        @Override
        public void stop() {
            owner = null;
            if (travelling) { companion.travel().reset(); travelling = false; }
            companion.getNavigation().stop();
        }
        private boolean travelling;
        /** Farther than this the following goes in legs (CompanionTravel). */
        private static final int FAR_FOLLOW = 32;
    }
}

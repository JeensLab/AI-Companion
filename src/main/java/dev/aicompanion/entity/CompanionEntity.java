package dev.aicompanion.entity;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.CompanionIdentity;
import dev.aicompanion.inventory.CompanionMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The player's persistent in-world companion. */
public final class CompanionEntity extends PathfinderMob implements Container {
    static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData.defineId(
            CompanionEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    static final EntityDataAccessor<Boolean> DATA_FOLLOWING = SynchedEntityData.defineId(
            CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Boolean> DATA_PROTECTING = SynchedEntityData.defineId(
            CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Boolean> DATA_ROAMING = SynchedEntityData.defineId(
            CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Boolean> DATA_GATHERING_LOGS = SynchedEntityData.defineId(
            CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Integer> DATA_LOGS_REMAINING = SynchedEntityData.defineId(
            CompanionEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<String> DATA_LAST_TASK_RESULT = SynchedEntityData.defineId(
            CompanionEntity.class, EntityDataSerializers.STRING);
    static final EntityDataAccessor<String> DATA_INVENTORY_SUMMARY = SynchedEntityData.defineId(
            CompanionEntity.class, EntityDataSerializers.STRING);
    static final EntityDataAccessor<String> DATA_INVENTORY_AI = SynchedEntityData.defineId(
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
    static final EntityDataAccessor<String> DATA_SOCIAL = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    static final EntityDataAccessor<Integer> DATA_SOCIAL_SERIAL = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Long> DATA_SOCIAL_TIME = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> DATA_AUTONOMY = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DATA_SAY = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_SAY_SERIAL = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_SAY_TIME = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.LONG);
    static final EntityDataAccessor<Integer> DATA_EXPRESSION = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Boolean> DATA_SOCIAL_SAFE = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Boolean> DATA_FIGHTING = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    final dev.aicompanion.CompanionExpressionState expressions = new dev.aicompanion.CompanionExpressionState();
    boolean nearbySocialThreat;
    boolean nearbyExplosiveThreat;
    static final double LOG_INTERACTION_RANGE_SQR = 16.0D;
    UUID ownerId;
    boolean following = true;
    boolean protecting;
    boolean roaming;
    BlockPos roamOrigin;
    int searchRadius = 64;
    boolean gatheringLogs;
    boolean gatheringDirt;
    int logsRemaining;
    int requestedLogs;
    int woodTaskVersion;
    int dirtTaskVersion;
    boolean supportSoilTask;
    int savedWoodRequested;
    int savedWoodRemaining;
    boolean lastTaskSucceeded;
    int inventoryViewers;
    final NonNullList<ItemStack> companionInventory = NonNullList.withSize(27, ItemStack.EMPTY);
    final CompanionPillar temporaryPillar = new CompanionPillar(this);
    final CompanionWork work = new CompanionWork(this);
    final CompanionGathering gathering = new CompanionGathering(this);
    final CompanionEquipment equipment = new CompanionEquipment(this);
    final CompanionPlacement placement = new CompanionPlacement(this);
    private final CompanionSwimming swimming = new CompanionSwimming(this);
    final CompanionCombat combat = new CompanionCombat(this);
    private final CompanionEating meals = new CompanionEating(this);
    private final CompanionBag bag = new CompanionBag(this);
    final CompanionBlockBreaking woodBreaking = new CompanionBlockBreaking(this);
    final CompanionBlockBreaking dirtBreaking = new CompanionBlockBreaking(this);
    final CompanionLoot loot = new CompanionLoot(this);
    final CompanionRecovery recovery = new CompanionRecovery(this);
    final CompanionTravel travel = new CompanionTravel(this);
    CompanionTravel travel() { return travel; }
    BlockPos movementIntent;
    boolean chunkLoading = true;
    boolean autonomousSpeech = true;
    String preferredWeapon = "";
    private net.minecraft.world.phys.Vec3 previousActivityPosition;
    private boolean activityMoved;
    BlockPos lastPlacedPosition;
    BlockPos lastPlacedPosition() { return lastPlacedPosition; }
    final CompanionSocial social = new CompanionSocial(this);
    final CompanionDiagnosticState diagnostics = new CompanionDiagnosticState(this);
    private final CompanionEntityNbt nbt = new CompanionEntityNbt(this);
    final CompanionPlaces places = new CompanionPlaces(this);
    final CompanionDeeds deeds = new CompanionDeeds();
    final CompanionAutonomy autonomy = new CompanionAutonomy(this);
    final CompanionGoals goals = new CompanionGoals(this);
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
        if (ok) CompanionHappeningEvents.sharedDeed(this);
    }
    private void refreshDeeds() { if (!level().isClientSide) entityData.set(DATA_DEEDS, deeds.summary(level().getGameTime())); }
    java.util.List<BlockPos> knownCraftingTables() { places.discoverTables(); refreshPlaces(); return places.tables(true); }
    boolean hasKnownCraftingTable() { return !places.tables(false).isEmpty(); }
    /** When each item was last crafted. A request repeated while the first answer was slow must not make a second tool. */
    final java.util.Map<String, Long> madeAt = new java.util.HashMap<>();
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
    final java.util.List<BlockPos> fieldBenches = new java.util.ArrayList<>();
    BlockPos takeFieldBenchNear(double blocks) {
        fieldBenches.removeIf(pos -> !level().hasChunkAt(pos) || !level().getBlockState(pos).is(Blocks.CRAFTING_TABLE));
        for (BlockPos pos : fieldBenches) if (pos.distSqr(blockPosition()) <= blocks * blocks) { fieldBenches.remove(pos); return pos; }
        return null;
    }
    /** Set by a work order whose remembered bench could not be reached on foot; never saved. */
    boolean newBenchAllowed;
    void allowNewBench(boolean allowed) { newBenchAllowed = allowed; }
    boolean hasKnownCraftingTableWithin(int blocks) {
        return places.tables(false).stream().anyMatch(pos -> pos.distSqr(blockPosition()) <= (double) blocks * blocks);
    }

    public void diagnosticBind(String trace) { diagnostics.bind(trace); }
    public String diagnosticTrace() { return diagnostics.trace(); }
    public void diagnosticSnapshot(String event) { if (dev.aicompanion.CompanionDiagnostics.enabled()) diagnostics.snapshot(event); }
    String diagnosticState() { return diagnostics.diagnosticState(); }
    net.minecraft.world.entity.ai.goal.GoalSelector goalSelector() { return goalSelector; }

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
        this.moveControl = new CompanionSwimming.SwimmingMoveControl(this);
        if (getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation ground) ground.setCanOpenDoors(true);
        // Water like a player: wading and swimming are slower, not something to avoid at eight times the cost, and a shore is no
        // danger (marathon 23.10, «берег»: back from the island the route over the sandbar did not fit into one search at the
        // game's price of water, and the part of it that came out led him off into the sea).
        setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER, 2.0F);
        setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER_BORDER, 0.0F);
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(0, new net.minecraft.world.entity.ai.goal.OpenDoorGoal(this, true));
        this.goalSelector.addGoal(1, new CompanionEntityGoals.RecoverSupportsGoal(this));
        this.goalSelector.addGoal(1, new CompanionEntityGoals.RecoverRouteGoal(this));
        this.goalSelector.addGoal(2, new CompanionCombat.PlayerPaceAttackGoal(this));
        this.goalSelector.addGoal(2, new CompanionEntityGoals.PickupLootGoal(this));
        this.goalSelector.addGoal(3, new CompanionGatherGoals.GatherLogsGoal(this));
        this.goalSelector.addGoal(3, new CompanionGatherGoals.GatherDirtGoal(this));
        this.goalSelector.addGoal(3, new CompanionEntityGoals.WorkGoal(this));
        this.goalSelector.addGoal(4, new CompanionEntityGoals.FollowPlayerGoal(this));
        this.goalSelector.addGoal(4, new CompanionEntityGoals.RoamGoal(this));
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
        this.targetSelector.addGoal(1, new CompanionCombat.OwnerDefenseGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, 10,
                true, false, target -> protecting ? combat.isThreateningOwner(target) : combat.comesForOwner(target)));
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
        builder.define(DATA_EXPRESSION, 0); builder.define(DATA_SOCIAL_SAFE, false); builder.define(DATA_FIGHTING, false);
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
    public boolean sociallySafe() { return social.sociallySafe(); }
    /** A fight with monsters at him or at the player: no talk then («Помолчи, я тут занят мобом»). Synced to the client. */
    public boolean fighting() { return entityData.get(DATA_FIGHTING); }
    public void reactToSpeech(String text) { social.reactToSpeech(text); }
    public void setLifestyle(boolean chunks, boolean speech) { chunkLoading = chunks; autonomousSpeech = speech; }
    public String socialObservation() { return social.socialObservation(); }
    public int socialSequence() { return social.socialSequence(); }
    public long socialTime() { return social.socialTime(); }
    void socialEvent(String fact) { social.socialEvent(fact); }
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

    public CompanionObtain.Plan obtainPlan(String item, int count) { return bag.obtainPlan(item, count); }
    public int carriedCount(String item) { return bag.carriedCount(item); }
    public CompanionActionResult carriedCheck(String item, int count) { return bag.carriedCheck(item, count); }
    public String getStoredItemsSummary() { return bag.getStoredItemsSummary(); }
    public String getStoredItemsForAi() { return bag.getStoredItemsForAi(); }
    public void mergeStoredItemsFrom(CompanionEntity other) { bag.mergeStoredItemsFrom(other); }

    public String getEquipmentForAi() { return equipment.getEquipmentForAi(); }
    public CompanionActionResult equipItem(String requested, String slotName) { return equipment.equipItem(requested, slotName); }
    CompanionActionResult equipForSupport(String itemId) { return equipment.equipForSupport(itemId); }
    CompanionActionResult equipForWork(String itemId) { return equipment.equipForWork(itemId); }
    public CompanionActionResult unequipItem(String slotName) { return equipment.unequipItem(slotName); }
    CompanionActionResult unequipForSupport(String slotName) { return equipment.unequipForSupport(slotName); }
    NonNullList<ItemStack> copyBag() { return equipment.copyBag(); }
    public static EquipmentSlot equipmentSlot(String name) { return CompanionEquipment.equipmentSlot(name); }
    public void selectToolForBlock(BlockState state) { equipment.selectToolForBlock(state); }
    public void selectWeaponForCombat() { equipment.selectWeaponForCombat(); }
    public void setEquipmentByPlayer(EquipmentSlot slot, ItemStack stack) { equipment.setEquipmentByPlayer(slot, stack); }

    int nextWaterJump, lastInWaterTick = -100;
    float jumpPower() { return getJumpPower(); }
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
        swimming.stroke();
        swimming.climbOutOfWater();
        meals.tickEating();
        loot.tickNearby();
        recovery.observe();
        work.watchChase();
        social.updateExpression();
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
            boolean dropped = bag.dropBelongings();
            noteDeed("Погиб на " + where + " (" + cause + ")." + (dropped ? " Все вещи выпали там." : ""), false);
            CompanionEvents.companionDied(this, nbt.memory(), where, dropped);
        }
        super.die(source);
    }


    /** Food in the bag heals, like a player who eats: nothing is conjured, the food is eaten (review 22.10: he never healed). */
    int satiety;
    int eatTicks;
    ItemStack eating = ItemStack.EMPTY;
    int lastHurtTick = -1000;
    static final int EAT_TICKS = 32;

    @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt) {
            lastHurtTick = tickCount;
            CompanionHappeningEvents.playerHit(this, source);
            if (!level().isClientSide && amount >= 4) expressions.react(dev.aicompanion.CompanionExpression.WORRIED, level().getGameTime(), 100);
        }
        return hurt;
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

    public boolean benchWalkNeeded(String requestedItemId, int requestedCount) { return placement.benchWalkNeeded(requestedItemId, requestedCount); }
    public CompanionActionResult craftItemResult(String requestedItemId, int requestedCount) { return placement.craftItemResult(requestedItemId, requestedCount); }
    CompanionActionResult craftFromBag(String requestedItemId, int requestedCount) { return placement.craftFromBag(requestedItemId, requestedCount); }
    public CompanionActionResult placeBlockResult(String requestedBlockId) { return placement.placeBlockResult(requestedBlockId); }
    CompanionActionResult placeBlockNearResult(String requestedBlockId, BlockPos origin) { return placement.placeBlockNearResult(requestedBlockId, origin); }
    CompanionActionResult placeBlockNearResult(String requestedBlockId, BlockPos origin, java.util.function.Predicate<BlockPos> allowed) { return placement.placeBlockNearResult(requestedBlockId, origin, allowed); }
    public CompanionActionResult recordActionResult(CompanionActionResult result) { return placement.recordActionResult(result); }
    public boolean hasCraftingTableNearby() { return placement.hasCraftingTableNearby(); }
    static boolean seesBlock(net.minecraft.world.entity.Entity viewer, Vec3 eye, BlockPos pos) { return CompanionPlacement.seesBlock(viewer, eye, pos); }

    public void startGatheringLogs(int count) { gathering.startGatheringLogs(count); }
    void startGatheringLogsForWork(int count) { gathering.startGatheringLogsForWork(count); }
    void startGatheringDirtForWork(int count) { gathering.startGatheringDirtForWork(count); }
    public void stopGathering() { gathering.stopGathering(); }

    ServerPlayer getOwnerPlayer() {
        if (!(level() instanceof ServerLevel serverLevel) || ownerId == null) return null;
        ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(ownerId);
        return owner != null && owner.level() == level() ? owner : null;
    }

    static final String FULL_BAG_FOR_LOGS = "В сумке нет места для брёвен: все ячейки заняты. Освободи место или попроси сложить вещи в сундук.";
    boolean hasRoomForLogs() { return gathering.hasRoomForLogs(); }
    boolean canStoreDrops(List<ItemStack> drops) { return gathering.canStoreDrops(drops); }


    boolean storeInCompanionInventory(ItemStack stack) { return bag.storeInCompanionInventory(stack); }
    void refreshInventorySummary() { bag.refreshInventorySummary(); }
    boolean fairTarget(LivingEntity entity, boolean provoked) { return combat.fairTarget(entity, provoked); }
    boolean provokes(LivingEntity entity) { return combat.provokes(entity); }

    /** A last guard: whatever goal picked them, the player and his pets are never a target. */
    @Override public void setTarget(LivingEntity target) {
        if (target != null && !level().isClientSide && (target instanceof Player || target instanceof CompanionEntity
                || ownerId != null && target instanceof net.minecraft.world.entity.OwnableEntity pet && ownerId.equals(pet.getOwnerUUID()))) target = null;
        super.setTarget(target);
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); nbt.save(tag); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); nbt.load(tag); }
    void inheritMemory(CompoundTag memory) { nbt.inheritMemory(memory); }

    /**
     * A player's reach for a blow: three blocks from the eyes to the nearest point of the body (marathon 23.10: with a zombie's
     * arm's length every hit knocked it out of reach, he had to step in again, and the zombie struck as often as he did).
     */
    @Override public boolean isWithinMeleeAttackRange(LivingEntity target) {
        var box = target.getBoundingBox();
        var eye = getEyePosition();
        return eye.distanceToSqr(Math.clamp(eye.x, box.minX, box.maxX), Math.clamp(eye.y, box.minY, box.maxY), Math.clamp(eye.z, box.minZ, box.maxZ)) <= 3.0 * 3.0;
    }
}

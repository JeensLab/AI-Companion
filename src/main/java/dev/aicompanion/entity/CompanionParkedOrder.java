package dev.aicompanion.entity;

import java.util.Set;
import net.minecraft.nbt.CompoundTag;

/**
 * The rest of a dig that an urgent order of the player (come, wait, protect) put aside, issue #105. He does the order; once he follows
 * the player again and is free, he digs what was left, once. A stop, a cancel or another work order drops it. The job that was
 * interrupted ends as always; what is left is server work without a task of its own.
 */
final class CompanionParkedOrder {
    /** {@code picked}: the pickup counter of the action when the rest was counted; what he picks up after that is no longer left to dig. */
    record Parked(String action, String item, int remaining, String location, String dimension, int picked) { }

    private static final Set<String> ACTIONS = Set.of("gather_stone", "gather_dirt", "gather_loose", "gather_wood", "mine_ore");
    private static final int MAX_REMAINING = 64;
    /** Ticks after the park before he resumes: the urgent order that caused it is on its way to the server (up to a second in play) and must find him free of the dig. */
    private static final int PARK_GRACE_TICKS = 60;
    private final CompanionEntity owner;
    private Parked parked;
    /** The work this order was resumed as, whose end he tells; 0 when none. */
    private long resumedTask;
    /** Game time of the park; a loaded park has none to wait for. */
    private long parkedAt;

    CompanionParkedOrder(CompanionEntity owner) { this.owner = owner; }
    private boolean enabled = true;
    boolean enabled() { return enabled; }
    void enable(boolean on) { enabled = on; }

    /** Puts the active job aside; false when there is none or it cannot be put aside (the caller then stops it as before). */
    boolean park() {
        CompanionWork work = owner.work;
        if (!enabled || !work.isActive() || owner.goals.ownsWork(owner.activeWorkId()) || owner.autonomy.running()) return false;
        Parked rest = restOf(work);
        if (rest == null) return false;
        String line = "Отложил " + work.jobName() + " — вернусь после.";
        // Set before the cancel: it keeps a result that is already there, and the task of the job reads this one.
        work.completedId = work.taskId;
        work.completedResult = CompanionActionResult.cancelled(line);
        owner.noteDeed(line, false);
        parked = rest;
        parkedAt = owner.level().getGameTime();
        owner.gathering.stopGathering();
        owner.autonomySay(line);
        return true;
    }

    /** What is left of the job, counted from the bag and the world as they are now; null for a job that is not put aside. */
    private Parked restOf(CompanionWork work) {
        boolean digging = owner.gatheringLogs || owner.gatheringDirt;
        String location = work.insideHouse ? "inside_house" : "near_player";
        return switch (work.job) {
            case STONE -> rest("gather_stone", "", work.wantedStone - (owner.collectedStone() - work.stoneCollectedBefore), location);
            case ORE -> rest("mine_ore", work.requestedItem, work.oreWanted - work.oreMined, location);
            case WOOD -> rest("gather_wood", "", digging ? owner.logsRemaining : 0, location);
            case DIRT -> rest(work.ground.isLoose() ? "gather_loose" : "gather_dirt", work.ground.id(),
                    digging ? owner.logsRemaining : work.phase == CompanionWork.Phase.SHOVEL ? work.requestedCount : 0, location);
            case CRAFT, STORE, TAKE, PLACE_CHEST, HOUSE, COME, HUNT, GIVE, SMELT, GAME, SORT, PEN, BREED, FARM, COURSE, WATER, OBSIDIAN, PORTAL, PICK_UP -> null;
        };
    }

    private Parked rest(String action, String item, int remaining, String location) {
        return remaining < 1 ? null : new Parked(action, item, Math.min(remaining, MAX_REMAINING), location, dimension(), owner.planProgress(action));
    }

    /** What is left to dig now: the rest counted at the park less what he has picked up since (a drop that lay on the ground at the order). */
    private int leftOf(Parked rest) {
        int now = owner.planProgress(rest.action());
        return rest.remaining() - (now < 0 ? 0 : Math.max(0, now - rest.picked()));
    }

    private String dimension() { return owner.level().dimension().location().toString(); }

    void tick() {
        if (resumedTask != 0 && !owner.work.isActive()) tellEnd();
        // The rest of a dig belongs to the world it was made in: a summon, a portal or a save loaded elsewhere drops it, silently.
        if (parked != null && (!enabled || !parked.dimension().equals(dimension()))) parked = null;
        if (parked != null && free()) resume();
    }

    /** Every hold ends in following the player, so this is also what «за мной», the end of a walk to the player and of a guard mean. */
    private boolean free() {
        return owner.level().getGameTime() - parkedAt >= PARK_GRACE_TICKS && owner.isFollowing() && !owner.isProtecting()
                && !owner.hasActiveWorkOrder() && owner.canWorkNow() && !owner.isSelfRescuing() && owner.getTarget() == null
                && owner.getOwnerPlayer() != null && !owner.deathTrip.active() && !owner.autonomy.running() && !goalHoldsHim()
                && !CompanionAgenda.suppressesDeeds(owner);
    }

    /** A goal that is running owns him; one that waits for the player (paused, blocked, unsupported) does not hold the rest back. */
    private boolean goalHoldsHim() {
        CompanionGoals.State state = owner.goals.state();
        return state == CompanionGoals.State.ACTIVE || state == CompanionGoals.State.PAUSED_COMBAT;
    }

    /** Starts the rest as a new order, once: the record is consumed first, a second park stores what is then left. */
    CompanionActionResult resume() {
        if (parked == null) return null;
        if (owner.hasActiveWorkOrder()) return CompanionActionResult.blocked("Сначала закончу текущую работу.");
        Parked rest = parked;
        parked = null;
        int left = leftOf(rest);
        if (left < 1) {
            String line = "Отложенную " + name(rest, false) + " уже не нужно: остаток подобрал по дороге.";
            owner.autonomySay(line, false);
            return CompanionActionResult.ok(line);
        }
        CompanionActionResult result = owner.startWork(rest.action(), rest.item(), left, rest.location());
        if (!result.success()) {
            owner.autonomySay(result.message(), false);
            return result;
        }
        resumedTask = owner.activeWorkId();
        String line = "Возвращаюсь к " + name(rest, true) + ", осталось " + left + ".";
        owner.autonomySay(line, false);
        return CompanionActionResult.accepted(line);
    }

    private void tellEnd() {
        CompanionActionResult result = owner.work.taskResult(resumedTask);
        resumedTask = 0;
        if (result != null && result.status() != CompanionActionResult.Status.CANCELLED) owner.autonomySay(result.message(), false);
    }

    void drop() { parked = null; }

    /** Any work order but the walk to the player replaces the rest: said once, not kept for later. */
    void replacedBy(String action) {
        // Only a player's work order replaces it: a step of a goal or a deed of his own leaves it for later.
        if (parked == null || action.equals("come") || action.equals("collect_smelt") || CompanionEvents.goalDispatching() || owner.autonomy.running()) return;
        owner.autonomySay("Отложенную " + name(parked, false) + " отменяю.", false);
        parked = null;
    }

    /** The refusal of a second work order: it names the deed he finishes first; with the setting off it is the plain refusal as before. */
    String refusal() {
        if (!enabled) return "Сначала нужно завершить или отменить текущую работу.";
        String deed = owner.work.isActive() ? owner.work.jobName()
                : owner.gatheringLogs ? "рубку дерева" : owner.gatheringDirt ? "добычу " + owner.gathering.ground.genitive() : "спуск с опоры";
        return "Погоди, доделаю " + deed + " — потом.";
    }

    /** «добычу камня» ({@code toward} false) or «добыче камня» (after «к»). */
    private static String name(Parked rest, boolean toward) {
        String dig = toward ? "добыче " : "добычу ";
        return switch (rest.action()) {
            case "gather_stone" -> dig + "камня";
            case "gather_wood" -> toward ? "рубке дерева" : "рубку дерева";
            case "mine_ore" -> dig + "руды «" + CompanionOre.Kind.of(rest.item()).oreName + "»";
            default -> dig + (CompanionGround.of(rest.item()) == null ? CompanionGround.SOIL : CompanionGround.of(rest.item())).genitive();
        };
    }

    void save(CompoundTag tag) {
        tag.putBoolean("UrgentOrder", enabled);
        if (parked == null) return;
        int left = Math.min(leftOf(parked), MAX_REMAINING);
        if (left < 1) return;
        tag.putString("ParkedAction", parked.action());
        tag.putString("ParkedItem", parked.item());
        tag.putInt("ParkedCount", left);
        tag.putString("ParkedLocation", parked.location());
        tag.putString("ParkedDimension", parked.dimension());
    }

    /** A save from before this existed has none of the tags: nothing is parked. */
    void load(CompoundTag tag) {
        enabled = !tag.contains("UrgentOrder") || tag.getBoolean("UrgentOrder");
        parked = null;
        resumedTask = 0;
        String action = tag.getString("ParkedAction");
        int count = tag.getInt("ParkedCount");
        String item = tag.getString("ParkedItem");
        boolean known = ACTIONS.contains(action) && count >= 1 && count <= MAX_REMAINING
                && (!action.equals("mine_ore") || CompanionOre.Kind.of(item) != null);
        // A save from before the dimension was kept is taken as made here.
        String dimension = tag.contains("ParkedDimension") ? tag.getString("ParkedDimension") : dimension();
        if (known) parked = new Parked(action, item, count, tag.getString("ParkedLocation"), dimension, owner.planProgress(action));
        parkedAt = owner.level().getGameTime() - PARK_GRACE_TICKS;
    }
}

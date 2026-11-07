package dev.aicompanion.entity;

import java.util.Set;
import net.minecraft.nbt.CompoundTag;

/**
 * The rest of a dig that an urgent order of the player (come, wait, protect) put aside, issue #105. He does the order; once he follows
 * the player again and is free, he digs what was left, once. A stop, a cancel or another work order drops it. The job that was
 * interrupted ends as always; what is left is server work without a task of its own.
 */
final class CompanionParkedOrder {
    record Parked(String action, String item, int remaining, String location) { }

    private static final Set<String> ACTIONS = Set.of("gather_stone", "gather_dirt", "gather_loose", "gather_wood", "mine_ore");
    private static final int MAX_REMAINING = 64;
    private final CompanionEntity owner;
    private Parked parked;
    /** The work this order was resumed as, whose end he tells; 0 when none. */
    private long resumedTask;

    CompanionParkedOrder(CompanionEntity owner) { this.owner = owner; }

    /** Puts the active job aside; false when there is none or it cannot be put aside (the caller then stops it as before). */
    boolean park() {
        CompanionWork work = owner.work;
        if (!work.isActive()) return false;
        Parked rest = restOf(work);
        if (rest == null) return false;
        String line = "Отложил " + work.jobName() + " — вернусь после.";
        // Set before the cancel: it keeps a result that is already there, and the task of the job reads this one.
        work.completedId = work.taskId;
        work.completedResult = CompanionActionResult.cancelled(line);
        owner.noteDeed(line, false);
        parked = rest;
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
            case CRAFT, STORE, TAKE, PLACE_CHEST, HOUSE, COME, HUNT, GIVE, SMELT, GAME, SORT, PEN, BREED, FARM, COURSE, WATER -> null;
        };
    }

    private static Parked rest(String action, String item, int remaining, String location) {
        return remaining < 1 ? null : new Parked(action, item, Math.min(remaining, MAX_REMAINING), location);
    }

    void tick() {
        if (resumedTask != 0 && !owner.work.isActive()) tellEnd();
        if (parked != null && free()) resume();
    }

    /** Every hold ends in following the player, so this is also what «за мной», the end of a walk to the player and of a guard mean. */
    private boolean free() {
        return owner.isFollowing() && !owner.isProtecting() && !owner.hasActiveWorkOrder() && owner.canWorkNow() && !owner.isSelfRescuing()
                && owner.getTarget() == null && owner.getOwnerPlayer() != null;
    }

    /** Starts the rest as a new order, once: the record is consumed first, a second park stores what is then left. */
    CompanionActionResult resume() {
        if (parked == null) return null;
        if (owner.hasActiveWorkOrder()) return CompanionActionResult.blocked("Сначала закончу текущую работу.");
        Parked rest = parked;
        parked = null;
        CompanionActionResult result = owner.startWork(rest.action(), rest.item(), rest.remaining(), rest.location());
        if (!result.success()) {
            owner.autonomySay(result.message(), false);
            return result;
        }
        resumedTask = owner.activeWorkId();
        String line = "Возвращаюсь к " + name(rest, true) + ", осталось " + rest.remaining() + ".";
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
        if (parked == null || action.equals("come")) return;
        owner.autonomySay("Отложенную " + name(parked, false) + " отменяю.", false);
        parked = null;
    }

    /** The deed that keeps him from a new order, for the refusal. */
    String currentDeed() {
        if (owner.work.isActive()) return owner.work.jobName();
        return owner.gatheringLogs ? "рубку дерева" : owner.gatheringDirt ? "добычу " + owner.gathering.ground.genitive() : "спуск с опоры";
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
        if (parked == null) return;
        tag.putString("ParkedAction", parked.action());
        tag.putString("ParkedItem", parked.item());
        tag.putInt("ParkedCount", parked.remaining());
        tag.putString("ParkedLocation", parked.location());
    }

    /** A save from before this existed has none of the tags: nothing is parked. */
    void load(CompoundTag tag) {
        parked = null;
        resumedTask = 0;
        String action = tag.getString("ParkedAction");
        int count = tag.getInt("ParkedCount");
        String item = tag.getString("ParkedItem");
        boolean known = ACTIONS.contains(action) && count >= 1 && count <= MAX_REMAINING
                && (!action.equals("mine_ore") || CompanionOre.Kind.of(item) != null);
        if (known) parked = new Parked(action, item, count, tag.getString("ParkedLocation"));
    }
}

package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The errand into the Nether (obtain_item for quartz, netherrack, basalt: nether_go, the digging, nether_back). He crosses on foot by a
 * lit portal; the game then makes a new entity of him in the other world out of his saved data. The record kept here goes with that
 * data: the portal at home, the exit portal in the Nether, when it began and when it must be over. A save without it is no errand.
 * <p>
 * The work that walks into the portal is not lost with the old entity: the crossing leaves a mark in memory and a flag in the saved data,
 * and the new entity, finding both, finishes that very work with a success (the plan polls it by its id). The flag found with no mark is
 * a world loaded, not a portal taken: that work failed with the world, and the record stays for the walk back.
 */
final class CompanionErrand {
    static final String NO_PORTAL = "Рядом нет портала в Незер — построй его или скажи «построй портал».";
    /** In the Nether for this long at most, looking for what he digs no farther than this from the exit portal, and leaving at half his health. */
    static final long MOST_TICKS = 20 * 60 * 5;
    private static final int SEARCH_WITHIN = 48;
    /** Ticks with no work in the Nether before he walks out by himself (the plan's next step may be on its way), and the walks he tries. */
    private static final int IDLE_BEFORE_WALK = 60, MOST_WALKS = 3;
    private static final String KEY = "Errand", TRANSIT = "ErrandTransit";
    /** Crossings in the making: the entity the game is about to recreate is saved once more, and the new one takes its mark here. */
    private static final Set<Long> CROSSING = ConcurrentHashMap.newKeySet();
    private static final AtomicLong TOKENS = new AtomicLong(1);

    private final CompanionEntity c;
    /** The overworld portal he left by, the portal he came out of in the Nether (null until seen), and the game ticks it began and must be over. */
    private BlockPos home, exit;
    private long since, deadline;
    /** The portal the running step walks to; not saved. */
    private BlockPos gate;
    private boolean going;
    /** On the old entity, while the game moves him: the mark, the work that walked in and the way it went. */
    private long token, crossingWork;
    private boolean crossingToNether;
    /** On the new entity, until its first tick: the work it finishes and the way it went. */
    private long arrivedWork;
    private boolean arrivedToNether;
    /** The watch, not saved: ticks with no work, walks out tried, what he has said (the walk's reason, that he cannot get out). */
    private int idle, walks;
    private boolean told, stuck;

    CompanionErrand(CompanionEntity c) { this.c = c; }

    /** The errand is on: he went in and is not back. */
    boolean active() { return home != null; }
    /** On the errand and in the Nether now: the plan steps of the errand run there with the player in the overworld. */
    boolean away() { return home != null && c.level().dimension() == Level.NETHER; }
    BlockPos home() { return home; }
    BlockPos exit() { return exit; }
    long deadline() { return deadline; }

    /** In the Nether on the errand he takes nothing farther from the exit portal than this; a record with no exit portal in it has no limit. */
    boolean within(BlockPos pos) {
        if (!away() || exit == null) return true;
        long dx = pos.getX() - exit.getX(), dz = pos.getZ() - exit.getZ();
        return dx * dx + dz * dz <= (long) SEARCH_WITHIN * SEARCH_WITHIN;
    }

    /** The steps a plan may run on him while he is in the Nether and the player is not: the digging there and the way back. */
    boolean runs(String action) {
        return away() && (action.equals("nether_go") || action.equals("nether_back") || action.equals("mine_ore") || action.equals("gather_block"));
    }

    /** This work's result is still to be made by the arrival: the poll must not take it for lost. */
    boolean arriving(long workId) { return arrivedWork != 0 && arrivedWork == workId; }

    String jobName() { return going ? "поход в Незер" : "возвращение из Незера"; }

    /** The first word of the work's acceptance: the player hears which portal. */
    String announce() { return going ? "Схожу в Незер через портал у " + gate.toShortString() + "." : "Возвращаюсь из Незера через портал у " + gate.toShortString() + "."; }

    /** nether_go / nether_back: null when the walk to the portal began; otherwise the answer, a refusal or «already there». */
    CompanionActionResult begin(String action, ServerLevel level) {
        going = action.equals("nether_go");
        if (going) {
            if (level.dimension() != Level.OVERWORLD) return CompanionActionResult.failed("В Незер хожу из верхнего мира.");
            gate = CompanionPortals.litNear(level, c.blockPosition());
            if (gate == null) return CompanionActionResult.failed(NO_PORTAL);
        } else {
            if (!active() || level.dimension() != Level.NETHER) return CompanionActionResult.ok("Я уже в верхнем мире.");
            gate = exit != null && level.getBlockState(exit).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL) ? exit : CompanionPortals.litNear(level, c.blockPosition());
            if (gate == null) return CompanionActionResult.failed("Не вижу портала, чтобы вернуться в верхний мир.");
        }
        c.portals.goTo(level, gate);
        return null;
    }

    /** The work's own tick: the walk to the portal, until the game takes him (the entity is then another one) or the walk is given up. */
    void step(ServerLevel level) {
        if (!c.portals.walking()) { c.work.finish(false, going ? "Не смог дойти до портала в Незер." : "Не смог дойти до портала, чтобы вернуться."); return; }
        c.portals.walk();
    }

    /** The work ended some other way (a stop, a new order): the walk to the portal ends with it. */
    void cancelled() { c.portals.stop(); }

    /** CompanionEntity.changeDimension, before the game saves him to make the entity of the other world: leaves the mark if it is the errand's walk. */
    boolean crossing(ServerLevel to) {
        if (c.work.job != CompanionWork.Job.ERRAND || gate == null) return false;
        token = TOKENS.getAndIncrement();
        CROSSING.add(token);
        crossingWork = c.work.taskId;
        crossingToNether = to.dimension() == Level.NETHER;
        return true;
    }

    /** After the game is done with the crossing, however it went: the mark is not left over. */
    void crossed(boolean marked) {
        if (!marked) return;
        CROSSING.remove(token);
        token = 0;
    }

    void save(CompoundTag tag) {
        BlockPos homeOut = home;
        long sinceOut = since, deadlineOut = deadline;
        if (homeOut == null && token != 0 && crossingToNether) {
            // The portal he is walking into is the one at home; the errand is recorded with the crossing, not before it.
            homeOut = gate; sinceOut = c.level().getGameTime(); deadlineOut = sinceOut + MOST_TICKS;
        }
        if (homeOut != null) {
            CompoundTag errand = new CompoundTag();
            errand.putLong("Home", homeOut.asLong());
            if (exit != null) errand.putLong("Exit", exit.asLong());
            errand.putLong("Since", sinceOut);
            errand.putLong("Deadline", deadlineOut);
            tag.put(KEY, errand);
        }
        if (token != 0) {
            CompoundTag transit = new CompoundTag();
            transit.putLong("Token", token);
            transit.putLong("Work", crossingWork);
            transit.putBoolean("ToNether", crossingToNether);
            tag.put(TRANSIT, transit);
        }
    }

    /** True when this entity is the one the game made out of the crossing: its work is finished by the arrival, not marked as lost with a world. */
    boolean load(CompoundTag tag) {
        home = exit = gate = null; since = deadline = 0; arrivedWork = 0; token = 0;
        if (tag.contains(KEY)) {
            CompoundTag errand = tag.getCompound(KEY);
            if (errand.contains("Home")) {
                home = BlockPos.of(errand.getLong("Home"));
                exit = errand.contains("Exit") ? BlockPos.of(errand.getLong("Exit")) : null;
                since = errand.getLong("Since"); deadline = errand.getLong("Deadline");
            }
        }
        if (!tag.contains(TRANSIT)) return false;
        CompoundTag transit = tag.getCompound(TRANSIT);
        if (!CROSSING.remove(transit.getLong("Token"))) return false;
        arrivedWork = transit.getLong("Work"); arrivedToNether = transit.getBoolean("ToNether");
        return true;
    }

    /** Every tick of the entity: the arrival after a crossing is finished once; a record found in the overworld is lifted. */
    void tick(ServerLevel level) {
        if (arrivedWork != 0) arrive(level);
        else if (home != null && level.dimension() == Level.OVERWORLD) lift();
        // He is there for the player's errand with the player in the overworld: the world with only him in it must go on ticking.
        if (away()) { level.resetEmptyTime(); watch(level); }
    }

    /** On the errand in the Nether: the player's coming ends it; danger or the time cuts the digging short; with no work he walks out. */
    private void watch(ServerLevel level) {
        ServerPlayer owner = c.getOwnerId() == null ? null : level.getServer().getPlayerList().getPlayer(c.getOwnerId());
        if (owner != null && owner.level() == level) { join(); return; }
        // A record that has no exit portal (a save, a portal not seen at the arrival) finds the nearest one as soon as there is one.
        if (exit == null && level.getGameTime() % 20 == 0) exit = CompanionPortals.litNear(level, c.blockPosition());
        String why = c.getHealth() <= c.getMaxHealth() / 2 ? "Опасно, возвращаюсь с тем, что есть"
                : level.getGameTime() >= deadline ? "Время вышло, возвращаюсь с тем, что есть" : null;
        if (c.work.isActive()) {
            idle = 0;
            // The walk back (the plan's own step) goes on; anything else he is digging stops with an honest count, and the plan stops with it.
            if (why != null && c.work.job != CompanionWork.Job.ERRAND) {
                c.work.finish(CompanionActionResult.cancelled(why + ": " + gathered()));
                idle = IDLE_BEFORE_WALK; told = true;
            }
            return;
        }
        if (++idle < IDLE_BEFORE_WALK || c.portals.walking()) return;
        walkOut(level, owner, why);
    }

    private String gathered() {
        if (c.work.job == CompanionWork.Job.BLOCK) return c.work.picking.progress();
        if (c.work.job == CompanionWork.Job.ORE)
            return "в сумке прибавилось " + Math.max(0, c.work.items.countCarried(c.work.ore.drop) - c.work.oreDropsBefore) + " «" + CompanionWork.itemName(c.work.ore.drop) + "».";
        return "ничего не добыто.";
    }

    /** No work: by the exit portal, or the nearest lit one, on foot as he went in; none, or no way to it, and he stays and says so once. */
    private void walkOut(ServerLevel level, ServerPlayer owner, String why) {
        BlockPos gate = exit != null && level.hasChunkAt(exit) && level.getBlockState(exit).is(Blocks.NETHER_PORTAL) ? exit : CompanionPortals.litNear(level, c.blockPosition());
        if (gate == null || walks >= MOST_WALKS) {
            if (!stuck) say(owner, gate == null ? "Не вижу портала, чтобы вернуться в верхний мир. Стою здесь." : "Не могу дойти до портала. Стою здесь.");
            stuck = true;
            return;
        }
        walks++;
        if (!told) say(owner, why == null ? "Возвращаюсь из Незера." : why + ".");
        told = true;
        c.portals.goTo(level, gate);
    }

    /** What he says is sent to the player, who is not near enough to hear him (as CompanionPortals does). */
    private void say(ServerPlayer owner, String text) {
        if (owner != null) owner.sendSystemMessage(Component.literal(c.getName().getString() + ": " + text));
    }

    /** The player came to the Nether: no errand any more, he is with the player; what he digs there goes on as any gathering. */
    private void join() {
        if (c.work.isActive() && c.work.job != CompanionWork.Job.ERRAND) { home = exit = null; since = deadline = 0; }
        else lift();
    }

    private void arrive(ServerLevel level) {
        long work = arrivedWork;
        arrivedWork = 0;
        CompanionActionResult result;
        if (arrivedToNether) {
            exit = CompanionPortals.litNear(level, c.blockPosition());
            since = level.getGameTime(); deadline = since + MOST_TICKS;
            result = CompanionActionResult.ok("Я в Незере" + (exit == null ? "." : ": вышел из портала у " + exit.toShortString() + "."));
        } else {
            lift();
            result = CompanionActionResult.ok("Вернулся из Незера в верхний мир.");
        }
        c.work.completedId = work;
        c.work.completedResult = result;
        c.recordActionResult(result);
        c.syncWorkState();
    }

    /** Back in the overworld: the errand is over, and he follows the player again. */
    private void lift() {
        home = exit = null; since = deadline = 0;
        c.setFollowing(true);
    }
}

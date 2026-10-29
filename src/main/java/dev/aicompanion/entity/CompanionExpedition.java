package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * The mine expedition of one work order: finds or reuses the entrance, walks down his own staircase, digs on down to diamond
 * depth, then 2x2 tunnels, and mines the ore he sees on the way. Every break, step and torch is a real one on the server tick;
 * it ends honestly with what was found, why it stopped and what is left undone.
 */
final class CompanionExpedition {
    private enum Stage { LOCATE, CHECK_NEAR, WALK_DOWN, DESCEND, BRANCH, CLIMB }

    private enum Ending { FOUND, SUPPLIES, BLOCKED, TIMEOUT }

    private static final int STEP_TICKS = 20 * 540, NEAR_RADIUS = 12, CRAFT_EVERY = 200;
    private static final int MAX_TURNS = 8, FAR_ENTRANCE = 64, SAME_SPOT = 12, TORCHES_LOW = 6;
    private static final double ENTRANCE_NEAR = 2.5;

    private final CompanionWork w;
    private final CompanionEntity owner;
    private final CompanionExpeditionDig dig;
    private final CompanionExpeditionClimb climb;
    private final CompanionExpeditionWalk lead;
    private final CompanionExpeditionOre ore;
    private final CompanionExpeditionCave cave;
    private List<BlockPos> route = List.of();
    private CompanionExpeditionStep plan;
    private Stage stage = Stage.LOCATE;
    private boolean active, hadStone, unheld;
    private long stepEnd, nextCraft;
    private int startDiamonds, progress, stairSteps, turns, routeIndex;
    private BlockPos stand, head;
    private Direction dir, tunnel;
    private String tunnelWhy = "";

    CompanionExpedition(CompanionWork w) {
        this.w = w;
        this.owner = w.owner;
        this.dig = new CompanionExpeditionDig(w);
        this.climb = new CompanionExpeditionClimb(w, dig);
        this.lead = new CompanionExpeditionWalk(dig);
        this.ore = new CompanionExpeditionOre(w, dig);
        this.cave = new CompanionExpeditionCave(w, dig, ore, () -> gained() >= w.requestedCount);
    }

    /** What the stall watchdog sees move: blocks broken, steps finished, plugs placed in a cave. */
    int progress() { return progress + dig.broken + dig.walked + cave.progress(); }

    void begin(ServerLevel level) {
        reset();
        active = true;
        stepEnd = level.getGameTime() + STEP_TICKS;
        w.deadline = stepEnd + 40;
        startDiamonds = w.items.countCarried(Items.DIAMOND);
        hadStone = w.items.countCarried(Items.STONE_PICKAXE) > 0;
        unheld = !CompanionChunkTicket.hold(owner);
        if (unheld) owner.workStatus("Не смог удержать чанки вокруг шахты загруженными: если уйти далеко, могу встать.");
    }

    void reset() {
        dig.reset();
        climb.reset();
        lead.clear();
        CompanionChunkTicket.release(owner);
        active = hadStone = unheld = false;
        ore.reset();
        cave.reset();
        tunnelWhy = "";
        stage = Stage.LOCATE;
        plan = null;
        route = List.of();
        stand = head = null;
        dir = tunnel = null;
        progress = stairSteps = turns = routeIndex = 0;
    }

    void tick(ServerLevel level) {
        if (!active) return;
        CompanionChunkTicket.follow(owner);
        if (level.getGameTime() >= stepEnd) { end(Ending.TIMEOUT, "Время вылазки вышло, остановился на месте."); return; }
        boolean digging = stage == Stage.DESCEND || stage == Stage.BRANCH;
        if (cave.tick(level, plan == null ? List.of() : plan.cells(), digging)) {
            if (cave.failure() != null) end(Ending.BLOCKED, cave.failure());
            return;
        }
        if (gained() >= w.requestedCount && !cave.busy()) { end(Ending.FOUND, ""); return; }
        if (plan == null && digging && ore.pending() && mineOre(level, CompanionExpeditionOre.Mode.NEAR)) return;
        if (plan == null) craftTorches(level);
        switch (stage) {
            case LOCATE -> locate(level);
            case CHECK_NEAR -> { if (!mineOre(level, CompanionExpeditionOre.Mode.WALK)) stage = Stage.WALK_DOWN; }
            case WALK_DOWN -> walkDown(level);
            case DESCEND -> descend();
            case BRANCH -> branch();
            case CLIMB -> { if (climb.tick() == CompanionExpeditionClimb.Result.DONE) end(Ending.SUPPLIES, climb.text()); }
        }
    }

    private boolean mineOre(ServerLevel level, CompanionExpeditionOre.Mode mode) {
        CompanionExpeditionOre.Result result = ore.step(level, mode);
        if (result == CompanionExpeditionOre.Result.FULL) end(Ending.BLOCKED, "Сумка полна, добытое некуда класть.");
        return result != CompanionExpeditionOre.Result.NONE;
    }

    private int gained() { return w.items.countCarried(Items.DIAMOND) - startDiamonds; }

    private void locate(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) { end(Ending.BLOCKED, "Шахту копаю только в Верхнем мире."); return; }
        CompanionMineSite mine = w.mine;
        BlockPos here = owner.blockPosition();
        if (mine.exists() && level.hasChunkAt(mine.entrance())) {
            mine.verify(level);
            route = new ArrayList<>(List.of(mine.entrance()));
            route.addAll(mine.stands());
            routeIndex = nearest(here);
            if (routeIndex < 0 && atTunnel(here)) routeIndex = route.size() - 1;
            if (routeIndex >= 0 || here.distSqr(mine.entrance()) <= FAR_ENTRANCE * FAR_ENTRANCE) { stage = Stage.CHECK_NEAR; return; }
        }
        CompanionShaftPlan.Entrance entrance = CompanionShaftPlan.chooseEntrance(level, here, NEAR_RADIUS);
        if (entrance == null) { end(Ending.BLOCKED, "Рядом нет места для входа в шахту: нужна твёрдая земля без построек и воды."); return; }
        mine.start(entrance.feet(), entrance.direction());
        route = List.of(entrance.feet());
        routeIndex = -1;
        stage = Stage.CHECK_NEAR;
    }

    private int nearest(BlockPos here) {
        int best = -1;
        double bestDistance = SAME_SPOT;
        for (int i = 0; i < route.size(); i++) {
            double distance = route.get(i).distSqr(here);
            if (distance <= bestDistance) { best = i; bestDistance = distance; }
        }
        return best;
    }

    /** Standing in one of the dug tunnels, away from the stairs: the route's last stand leads on into it. */
    private boolean atTunnel(BlockPos here) {
        for (CompanionMineSite.Tunnel t : w.mine.tunnels()) {
            for (int k = 0; k <= t.length(); k++) {
                if (w.mine.foot().relative(t.direction(), k).distSqr(here) <= SAME_SPOT) return true;
            }
        }
        return false;
    }

    private void walkDown(ServerLevel level) {
        if (routeIndex < 0) {
            CompanionTravel.Step walk = w.route.legs(route.get(0), ENTRANCE_NEAR);
            if (walk == CompanionTravel.Step.FAILED) end(Ending.BLOCKED, "Не смог дойти до входа в шахту.");
            else if (walk == CompanionTravel.Step.NEAR) routeIndex = 0;
            return;
        }
        if (routeIndex + 1 < route.size()) {
            if (dig.walkTo(route.get(routeIndex + 1))) routeIndex++;
            else if (dig.walkStuck()) end(Ending.BLOCKED, "Не смог спуститься по своей лестнице.");
            return;
        }
        stand = route.get(routeIndex);
        dir = facing();
        ore.scan = true;
        stage = reachedDepth(level) ? Stage.BRANCH : Stage.DESCEND;
        if (stage == Stage.BRANCH) enterBranch();
    }

    /** The direction of the last way down: the last move of the route, the stairs' own while there is none. */
    private Direction facing() {
        if (route.size() < 2) return w.mine.stairDirection();
        BlockPos before = route.get(route.size() - 2), last = route.get(route.size() - 1);
        if (before.getX() == last.getX() && before.getZ() == last.getZ()) return w.mine.stairDirection();
        return Direction.getNearest(last.getX() - before.getX(), 0, last.getZ() - before.getZ());
    }

    private boolean reachedDepth(ServerLevel level) {
        return stand.getY() <= CompanionShaftPlan.targetFeetY(level.getMinBuildHeight());
    }

    private void descend() {
        if (plan == null) plan = CompanionExpeditionStep.stair(w, dig, stand, dir);
        advance();
    }

    private void branch() {
        if (plan == null) {
            if (tunnel == null && !startTunnel()) return;
            if (!lead.done()) {
                if (lead.tick() == CompanionExpeditionWalk.Result.STUCK) end(Ending.BLOCKED, "Не смог вернуться в свой тоннель.");
                return;
            }
            List<CompanionMineSite.Tunnel> tunnels = w.mine.tunnels();
            if (tunnels.get(tunnels.size() - 1).length() >= CompanionShaftPlan.MAX_TUNNEL_CELLS) { tunnel = null; return; }
            if (cave.crossesSeal(CompanionShaftPlan.tunnelCells(head, tunnel))) { tunnel = null; tunnelWhy = "дальше пещера"; return; }
            plan = CompanionExpeditionStep.tunnel(w, dig, head, tunnel);
        }
        advance();
    }

    /** Back into the last tunnel of a mine from before, or none yet. */
    private void enterBranch() {
        List<CompanionMineSite.Tunnel> tunnels = w.mine.tunnels();
        if (tunnels.isEmpty()) { tunnel = null; return; }
        CompanionMineSite.Tunnel last = tunnels.get(tunnels.size() - 1);
        tunnel = last.direction();
        head = w.mine.foot().relative(tunnel, last.length());
        lead.inTunnel(w.mine.foot(), last, true);
    }

    /** The next direction from the stairs' foot; the way back is skipped, it would break through the step above. */
    private boolean startTunnel() {
        List<Direction> order = CompanionShaftPlan.tunnelOrder(dir);
        int used = w.mine.tunnels().size();
        if (used >= order.size() - 1) { end(Ending.BLOCKED, "Все тоннели от лестницы пройдены" + (tunnelWhy.isEmpty() ? "" : " (" + tunnelWhy + ")") + "."); return false; }
        tunnel = order.get(used);
        if (used > 0) lead.inTunnel(w.mine.foot(), w.mine.tunnels().get(used - 1), false);
        w.mine.openTunnel(tunnel);
        head = w.mine.foot();
        return true;
    }

    private void advance() {
        switch (plan.tick((ServerLevel) owner.level())) {
            case WORKING -> { }
            case DONE -> arrived();
            case BLOCKED -> blocked();
            case NO_TOOL -> {
                plan = null;
                stage = Stage.CLIMB;
                climb.begin(CompanionExpeditionClimb.shortage(dig.lackedOre, hadStone), owner.blockPosition());
            }
            case FULL -> end(Ending.BLOCKED, "Сумка полна, добытое некуда класть.");
        }
    }

    private void arrived() {
        CompanionExpeditionStep done = plan;
        plan = null;
        progress++;
        ore.scan = true;
        switch (done.kind) {
            case STAIR -> {
                stand = done.to;
                w.mine.reached(stand);
                stairSteps++;
                owner.workStatus("Спускаюсь по лестнице: ступень " + stairSteps + ", ноги на высоте " + stand.getY() + ".");
                if (reachedDepth((ServerLevel) owner.level())) { stage = Stage.BRANCH; enterBranch(); }
            }
            case LANDING -> {
                dir = done.turn;
                stand = done.to;
                w.mine.reached(stand);
                turns++;
                owner.workStatus("Лестницу пришлось повернуть на " + name(dir) + ".");
            }
            case TUNNEL -> {
                w.mine.extendTunnel();
                head = done.to;
                owner.workStatus("Копаю тоннель на " + name(tunnel) + ", прошёл " + w.mine.tunnels().get(w.mine.tunnels().size() - 1).length() + ".");
            }
        }
    }

    private void blocked() {
        CompanionExpeditionStep failed = plan;
        plan = null;
        switch (failed.kind) {
            case STAIR -> {
                Direction side = turns >= MAX_TURNS ? null : CompanionShaftPlan.chooseTurn((ServerLevel) owner.level(), stand, dir, w.mine.seals());
                if (side == null) end(Ending.BLOCKED, "Дальше лестницу не прокопать: " + failed.why + ".");
                else plan = CompanionExpeditionStep.landing(w, dig, stand, side);
            }
            case LANDING -> end(Ending.BLOCKED, "Свернуть в сторону тоже не вышло: " + failed.why + ".");
            case TUNNEL -> { tunnel = null; tunnelWhy = failed.why; }
        }
    }

    /** Torches are laid by the lighting as he works; here only the supply is topped up from coal and sticks. */
    private void craftTorches(ServerLevel level) {
        if (level.getGameTime() < nextCraft) return;
        nextCraft = level.getGameTime() + CRAFT_EVERY;
        if (w.items.countCarried(Items.TORCH) >= TORCHES_LOW) return;
        boolean coal = false, handle = false;
        for (ItemStack stack : w.items.carriedItems()) {
            coal |= stack.is(ItemTags.COALS);
            handle |= stack.is(Items.STICK) || stack.is(ItemTags.PLANKS) || stack.is(ItemTags.LOGS);
        }
        if (coal && handle) owner.craftFromBag("minecraft:torch", 4);
    }

    private void end(Ending ending, String why) {
        dig.flush();
        String text = report() + (cave.notes().isEmpty() ? "" : " " + cave.notes()) + " " + why;
        if (ore.missedDiamond && gained() == 0) text += " Видел алмазную руду, но взять её нечем: нужна железная кирка.";
        if (unheld) text += " Чанки вокруг шахты удержать не удалось.";
        text = text.strip();
        w.finish(switch (ending) {
            case FOUND -> CompanionActionResult.ok(text);
            case SUPPLIES -> CompanionActionResult.shortage(text);
            case BLOCKED -> CompanionActionResult.blocked(text);
            case TIMEOUT -> CompanionActionResult.timeout(text);
        });
    }

    private String report() {
        StringBuilder text = new StringBuilder("Алмазов в сумке: " + w.items.countCarried(Items.DIAMOND) + " (добыто за вылазку: " + gained()
                + ", нужно " + w.requestedCount + "). Ноги на высоте " + owner.blockPosition().getY() + ", ступеней вниз " + stairSteps + ".");
        List<CompanionMineSite.Tunnel> tunnels = w.mine.tunnels();
        if (tunnels.isEmpty()) return text.toString();
        text.append(tunnels.size() == 1 ? " Тоннель " : " Тоннели ");
        for (int i = 0; i < tunnels.size(); i++) {
            text.append(i == 0 ? "" : ", ").append("на ").append(name(tunnels.get(i).direction())).append(' ').append(tunnels.get(i).length());
        }
        return text.append('.').toString();
    }

    private static String name(Direction direction) {
        return switch (direction) {
            case NORTH -> "север";
            case EAST -> "восток";
            case SOUTH -> "юг";
            case WEST -> "запад";
            default -> direction.getName();
        };
    }
}

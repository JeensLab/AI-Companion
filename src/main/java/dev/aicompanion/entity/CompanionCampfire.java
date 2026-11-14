package dev.aicompanion.entity;

import dev.aicompanion.CompanionDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * Music with his guitar (the owner asked, 09.11): he sits down on the ground, takes out his guitar and plays the recordings
 * ({@link CompanionMusicLibrary}: the player's own folder config/aicompanion/music; while that folder has none, standard Minecraft
 * music sounds instead) until something else needs him. A fire is not needed, it only makes the evening: on «сыграй»
 * ({@code campfire_song}) he sits across a lit campfire near the player from him, else by a real campfire he sets down from his bag,
 * else simply beside the player. By himself he begins only by a lit fire the player has stayed at for half a minute while nothing
 * else is going on, and only while «Олег играет у костра» is on in «Поведение». The guitar is his own thing, drawn by the client:
 * no item is made, carried or dropped. Nothing is saved: after a reload he simply stands up. The tune is played by the client from
 * the sound id synced with his pose (client/CompanionCampfireAudio), so a recording from the folder sounds without the server
 * knowing it as a registered sound event.
 */
public final class CompanionCampfire {
    /** The player by one lit fire this long, nothing else going on: he sits down and plays on his own. */
    static final int LINGER_TICKS = 20 * 30;
    /** The player's own fire is looked for this close to the player. */
    static final int NEAR_FIRE = 4;
    /** Asked to play: a lit fire this close to the player is taken before he sets down his own. */
    static final int FIRE_SEARCH = 8;
    /** The player this far from the fire (or from where he sat down by him): the evening is over, and he goes with him. */
    static final double PLAYER_GONE = 12;
    /** A monster this close to the fire or to him ends the music (and keeps it from beginning). */
    static final int MONSTER_RANGE = 12;
    /** A walk to the seat that takes longer than this: he sits down where he is if the fire is near, or gives up. */
    static final int APPROACH_TICKS = 20 * 20;
    static final int SETTLE_TICKS = 30, REST_TICKS = 60, REST_SPREAD = 80;
    /** What the client draws: standing, sitting with the guitar on his knee, playing it. */
    public static final int STANDING = 0, SITTING = 1, PLAYING = 2;
    /** What «сыграй» may ask for: anything, a calm or sad tune, a cheerful one, simply another one, or standard Minecraft music. */
    public static final java.util.Set<String> MOODS = java.util.Set.of("", "calm", "cheerful", "next", "vanilla");

    enum Phase { APPROACH, SETTLE, PLAY, REST }

    private final CompanionEntity c;
    // What the evening is about: the fire, or without one the place where the player stood when he asked; and the cell to sit in.
    private BlockPos spot, seat;
    private boolean byFire;
    // Where his eyes rest while he sits: the fire, or the ground before him.
    private Vec3 gaze;
    private Phase phase;
    private long approachUntil;
    // The last steps onto the seat are being walked by an exact path (the long walk gets only within two blocks of a goal).
    private boolean lastSteps;
    private CompanionMusicLibrary.Track tune;
    private int timer, tunesStarted;
    // The kind of tune the player asked for, taken by the next tune that starts.
    private String wanted = "";
    // The lit fire the player has stood by, and since when (game time).
    private BlockPos lingerFire;
    private long lingerSince;
    // An evening that ended by this fire is not begun again by itself until the player has left it.
    private BlockPos spentFire;
    private String ended = "";

    CompanionCampfire(CompanionEntity c) { this.c = c; }

    public boolean active() { return spot != null; }
    /** The fire of the evening, or null (none, or he plays without one). */
    public BlockPos fire() { return byFire ? spot : null; }
    /** Recordings started this evening (the GameTests listen for the music by this). */
    public int tunesStarted() { return tunesStarted; }
    /** How long the tune playing now has sounded, in ticks; 0 between tunes. */
    public int playedTicks() { return phase == Phase.PLAY ? timer : 0; }
    /** The tune playing or last played this evening, or empty. */
    public String tuneTitle() { return tune == null ? "" : tune.title(); }
    /** Why the last evening ended (fire_out, player_left, player_order:…), or empty. */
    public String ended() { return ended; }

    /**
     * «Сыграй», «поставь костёр и сыграй»: by a lit fire near the player, by his own one set down from the bag, or beside the player.
     * Asked while he plays («сыграй грустное», «давай другую», «сыграй из майнкрафта»), only the tune changes: he stays on his seat
     * (owner 09.11: every new request sent him walking to another seat, still drawn sitting).
     *
     * <p>Such a request reads the folder afresh first, so a recording put there while the game runs is picked up without a reload,
     * and it changes the tune only to a recording that is really another one — of the kind asked for when a mood was asked, any
     * other for «другую», standard Minecraft music for «из майнкрафта». When the folder has nothing else of that kind the one
     * playing simply goes on, and the answer says so plainly: «Таких песен нет.» when the folder holds other recordings but none of
     * the kind asked for, «Других песен не знаю.» only when there is no other recording at all. The same recording is never
     * restarted and called a change. The answer is built from the pick that actually started, never from an intention.
     */
    public CompanionActionResult request(ServerPlayer player, String mood) {
        if (!(c.level() instanceof ServerLevel level)) return CompanionActionResult.failed("Для музыки нужен загруженный мир.");
        if (!MOODS.contains(mood)) return CompanionActionResult.failed("Не понял, какую мелодию сыграть.");
        if (!c.playsByTheFire()) return CompanionActionResult.failed("Играть у костра выключено в настройках поведения.");
        if (active()) {
            // The folder is read again for the request: what was put there since the last scan is heard without a reload.
            java.util.List<CompanionMusicLibrary.Track> tracks = CompanionMusicLibrary.rescan();
            if (phase == Phase.PLAY) {
                CompanionMusicLibrary.Pick next = CompanionMusicLibrary.change(tracks, mood, tune.soundId(), c.getRandom());
                // Nothing else of the asked kind (or nothing else at all): he plays the one he is playing on, and says which of the
                // two it is — no recording of that kind while there are others, or nothing else at all.
                if (next == null) return c.placement.recordActionResult(CompanionActionResult.ok(noChange(mood, tracks)));
                silence();
                start(next);
                return c.placement.recordActionResult(CompanionActionResult.ok(whatStarts(mood)));
            }
            // Still sitting down or resting: the kind asked for is taken by the tune that starts next (now, while resting).
            wanted = mood;
            if (phase == Phase.REST) startTune();
            boolean missing = !mood.isEmpty() && !"vanilla".equals(mood) && !CompanionMusicLibrary.hasMood(tracks, mood);
            // No recording of that kind: the tune that starts is any, and the answer says so instead of promising the kind.
            return c.placement.recordActionResult(CompanionActionResult.ok(missing ? missingMood(mood) : whatStarts(mood)));
        }
        if (c.getTarget() != null || danger(level, player.blockPosition()))
            return c.placement.recordActionResult(CompanionActionResult.blocked("Тут неспокойно, монстры рядом. Сыграю, когда отобьёмся."));
        if (c.isInWater()) return c.placement.recordActionResult(CompanionActionResult.blocked("Из воды не сыграешь. Давай на берег."));
        BlockPos lit = litFire(level, player.blockPosition(), FIRE_SEARCH);
        if (lit == null) lit = litFire(level, c.blockPosition(), NEAR_FIRE);
        boolean own = false;
        String campfire = carries(Items.CAMPFIRE) ? "minecraft:campfire" : carries(Items.SOUL_CAMPFIRE) ? "minecraft:soul_campfire" : null;
        // His own campfire from the bag makes the evening; with no room for it he plays without one.
        if (lit == null && campfire != null && c.placement.placeBlockNearResult(campfire, c.blockPosition()).success()) {
            lit = c.lastPlacedPosition;
            own = true;
        }
        // No fire at all: none is needed for the music, he sits down beside the player.
        begin(level, lit != null ? lit : player.blockPosition(), lit != null, player, "request");
        wanted = mood;
        String missing = mood.isEmpty() || "vanilla".equals(mood) || CompanionMusicLibrary.hasMood(CompanionMusicLibrary.available(), mood)
                ? "" : missingMood(mood) + " ";
        return c.placement.recordActionResult(CompanionActionResult.ok(missing + (own ? "Поставил костёр. Сейчас сяду и сыграю."
                : lit != null ? "Сяду у костра и сыграю." : "Сяду рядом и сыграю.")));
    }

    /** Said plainly when the folder has nothing other than the recording playing now: he plays on rather than start it over. */
    private static final String NO_OTHER = "Других песен не знаю.";
    /** Said plainly when the folder has other recordings but none of the kind asked for: the one playing goes on. */
    private static final String NO_SUCH_KIND = "Таких песен нет.";

    /**
     * The honest answer to a request that could not change the tune: «Таких песен нет.» when the folder holds other recordings but
     * none of the kind asked for, «Других песен не знаю.» only when there is no other recording at all.
     */
    private String noChange(String mood, List<CompanionMusicLibrary.Track> tracks) {
        boolean moodAsked = "calm".equals(mood) || "cheerful".equals(mood);
        return moodAsked && CompanionMusicLibrary.hasOther(tracks, tune.soundId()) ? NO_SUCH_KIND : NO_OTHER;
    }

    /** What he says of a tune that really starts: short and plain, from the kind that was asked for. */
    private static String whatStarts(String mood) {
        return switch (mood) {
            case "calm" -> "Сыграю спокойную.";
            case "cheerful" -> "Сыграю повеселее.";
            case "vanilla" -> "Сыграю из майнкрафта.";
            default -> "Другую.";
        };
    }

    /** Said plainly when no recording of the kind asked for is there, before a tune starts: he will play any. */
    private static String missingMood(String mood) {
        return "calm".equals(mood) ? "Спокойных и грустных записей нет — сыграю любую." : "Весёлых записей нет — сыграю другую.";
    }

    /** The player gave an order: the guitar goes away at once. True if he was playing. */
    public boolean playerOrder(String action) {
        if (!active()) return false;
        end("player_order:" + action);
        return true;
    }

    /** Every tick on the server: the evening's watch and, once a second, whether one should begin by itself. */
    void tick() {
        if (!(c.level() instanceof ServerLevel level)) return;
        if (active()) { if (c.tickCount % 10 == 0) watch(level); return; }
        if (c.tickCount % 20 != 0) return;
        // «Олег играет у костра» off: no evening begins by itself (CompanionEntity.playsByTheFire, the player's switch).
        if (!c.playsByTheFire()) { lingerFire = null; return; }
        ServerPlayer player = c.getOwnerPlayer();
        if (player == null || player.level() != c.level() || player.isSpectator() || !player.isAlive()) { lingerFire = null; return; }
        if (spentFire != null && (!isLit(level, spentFire) || player.distanceToSqr(spentFire.getCenter()) > PLAYER_GONE * PLAYER_GONE)) spentFire = null;
        BlockPos near = litFire(level, player.blockPosition(), NEAR_FIRE);
        if (near == null || !near.equals(lingerFire)) { lingerFire = near; lingerSince = level.getGameTime(); }
        if (near == null || near.equals(spentFire) || level.getGameTime() - lingerSince < LINGER_TICKS || !free(level, player, near)) return;
        begin(level, near, true, player, "lingered");
        if (c.talksOnOwn()) c.autonomySay(OPENERS.get(c.getRandom().nextInt(OPENERS.size())), false);
    }

    private static final List<String> OPENERS = List.of("Хорошо сидим. Достану-ка гитару.", "Огонь, тишина… Самое время что-нибудь сыграть.",
            "Посидим? Я тут одну мелодию придумал.");

    /** Nothing else wants him, and the place is calm: the conditions for beginning by himself. */
    private boolean free(ServerLevel level, ServerPlayer player, BlockPos near) {
        boolean recentFight = player.getLastHurtByMob() != null && player.tickCount - player.getLastHurtByMobTimestamp() < CompanionAutonomy.COMBAT_TICKS
                || player.getLastHurtMob() != null && player.tickCount - player.getLastHurtMobTimestamp() < CompanionAutonomy.COMBAT_TICKS;
        boolean nearEnough = c.isFollowing() || c.distanceToSqr(near.getCenter()) <= 6 * 6;
        return nearEnough && !c.isProtecting() && !c.isRoaming() && !recentFight
                && !c.hasActiveWorkOrder() && !c.isGatheringTask() && !c.hasPendingLoot() && !c.isSelfRescuing() && c.getTarget() == null
                && c.canCollectItems() && !c.autonomy().running() && !c.goals().outstanding() && !CompanionAgenda.suppressesDeeds(c)
                && !c.deathTrip.active() && !c.isInWater() && !c.isPassenger() && c.onGround() && c.tickCount - c.lastHurtTick > 20 * 10
                && c.distanceToSqr(near.getCenter()) <= 32 * 32 && !danger(level, near);
    }

    private void begin(ServerLevel level, BlockPos at, boolean fire, ServerPlayer player, String why) {
        spot = at.immutable();
        byFire = fire;
        seat = seat(level, spot, player);
        gaze = null;
        phase = Phase.APPROACH;
        approachUntil = level.getGameTime() + APPROACH_TICKS;
        timer = 0;
        tunesStarted = 0;
        lastSteps = false;
        wanted = "";
        lingerFire = null;
        c.getNavigation().stop();
        c.travel().reset();
        CompanionDiagnostics.event(c.diagnosticTrace(), "campfire_begin", "entity", c.getUUID(), "spot", spot, "byFire", byFire, "seat", seat, "why", why);
    }

    /** The evening is over: he stands up and the guitar goes away. */
    void end(String why) {
        if (!active()) return;
        CompanionDiagnostics.event(c.diagnosticTrace(), "campfire_end", "entity", c.getUUID(), "spot", spot, "why", why, "tunes", tunesStarted, "tune", tuneTitle());
        spentFire = byFire ? spot : null;
        ended = why;
        if (phase == Phase.APPROACH) { c.travel().reset(); c.getNavigation().stop(); }
        if (phase == Phase.PLAY) silence();
        spot = seat = null;
        gaze = null;
        phase = null;
        pose(STANDING);
    }

    /** What ends the evening, checked twice a second whether or not the goal runs. */
    private void watch(ServerLevel level) {
        ServerPlayer player = c.getOwnerPlayer();
        String why = byFire && !isLit(level, spot) ? "fire_out"
                : player == null || player.level() != c.level() || !player.isAlive() ? "player_away"
                : player.distanceToSqr(spot.getCenter()) > PLAYER_GONE * PLAYER_GONE ? "player_left"
                : c.hasActiveWorkOrder() || c.isGatheringTask() || c.autonomy().running() ? "work"
                : c.getTarget() != null || c.tickCount - c.lastHurtTick < 20 || danger(level, spot) ? "danger"
                : c.isInWater() || c.isPassenger() || c.deathTrip.active() ? "moved"
                : c.distanceToSqr(spot.getCenter()) > 40 * 40 ? "too_far" : null;
        if (why != null) end(why);
    }

    /** One tick of the evening while its goal runs: the walk to the seat, sitting down, the tunes and the rests between them. */
    private void tickSession() {
        if (!(c.level() instanceof ServerLevel level) || !active()) return;
        switch (phase) {
            case APPROACH -> approach(level);
            case SETTLE -> {
                hold();
                if (--timer <= 0) startTune();
            }
            case PLAY -> {
                hold();
                if (timer % 40 == 0) notes(level);
                if (++timer >= tune.ticks()) rest();
            }
            case REST -> {
                hold();
                if (--timer <= 0) startTune();
            }
        }
    }

    /**
     * The walk to the seat: the long way in legs (CompanionTravel), then the last steps by an exact path onto the cell. Without a seat,
     * a way or in time he sits down where he got to if that is by the spot, else the evening is given up.
     */
    private void approach(ServerLevel level) {
        boolean near = c.distanceToSqr(spot.getCenter()) <= 3.5 * 3.5;
        boolean late = level.getGameTime() > approachUntil;
        Vec3 at = seat == null ? null : Vec3.atBottomCenterOf(seat);
        boolean arrived = seat != null && (c.blockPosition().equals(seat)
                || Math.pow(c.getX() - at.x, 2) + Math.pow(c.getZ() - at.z, 2) <= .5 * .5 && Math.abs(c.getY() - at.y) < .6);
        if (arrived || late && near) { c.travel().reset(); sitDown(); return; }
        if (late) { end("no_way"); return; }
        if (seat == null || !lastSteps && c.distanceToSqr(at) > 3 * 3) {
            var walk = c.travel().toward(seat == null ? spot : seat, 2.5);
            if (walk == CompanionTravel.Step.FAILED) { if (near) { c.travel().reset(); sitDown(); } else end("no_way"); }
            else if (walk == CompanionTravel.Step.NEAR && seat == null) { c.travel().reset(); sitDown(); }
            return;
        }
        if (lastSteps && !c.work.route.legOver()) return;
        if (!lastSteps) c.travel().reset();
        int busy = c.work.route.busyAnswers();
        var path = c.work.route.toCells(java.util.Set.of(seat));
        if (path != null && path.getNodeCount() > 1) { c.work.route.walk(path, 1); lastSteps = true; }
        else if (c.work.route.busyAnswers() == busy) { if (near) sitDown(); else end("no_way"); }
    }

    private void sitDown() {
        c.getNavigation().stop();
        // Facing the fire, or the player without one: the body keeps this direction while he sits (CompanionEntity's body control).
        var player = c.getOwnerPlayer();
        Vec3 toward = byFire || player == null ? Vec3.atBottomCenterOf(spot) : player.position();
        float yaw = (float) (net.minecraft.util.Mth.atan2(toward.z - c.getZ(), toward.x - c.getX()) * net.minecraft.util.Mth.RAD_TO_DEG) - 90F;
        c.setYRot(yaw); c.setYBodyRot(yaw); c.setYHeadRot(yaw);
        // The eyes on the fire; without one on the ground before him, not following the player about.
        gaze = byFire ? Vec3.atBottomCenterOf(spot).add(0, .3, 0) : c.position().add(Vec3.directionFromRotation(0, yaw).scale(2));
        face();
        phase = Phase.SETTLE;
        timer = SETTLE_TICKS;
        pose(SITTING);
        CompanionDiagnostics.event(c.diagnosticTrace(), "campfire_sat_down", "entity", c.getUUID(), "at", c.blockPosition(), "spot", spot, "byFire", byFire);
    }

    /**
     * The next tune: of the kind asked for if the recordings carry it, and never the same one twice running — a tune of the asked
     * kind that is playing now starts over when it is the only one of that kind; «из майнкрафта» is standard Minecraft music.
     * Null when there is nothing to play at all.
     */
    private CompanionMusicLibrary.Pick startTune() {
        if (phase == Phase.PLAY) silence();
        var pick = CompanionMusicLibrary.pick(CompanionMusicLibrary.available(), wanted, tune == null ? "" : tune.soundId(), c.getRandom());
        wanted = "";
        if (pick == null) { rest(); return null; } // nothing to play at all: he rests and looks again later
        start(pick);
        return pick;
    }

    /** Starts a tune that was picked: the client plays it from this id, and the start counter makes it begin from its beginning. */
    private void start(CompanionMusicLibrary.Pick pick) {
        tune = pick.track();
        timer = 0;
        phase = Phase.PLAY;
        pose(PLAYING);
        // The client plays it from this id (client/CompanionCampfireAudio): a folder's file is no registered sound event. The start
        // counter makes it start the recording over even when the same one is asked for again.
        c.setCampfireTune(tune.soundId());
        tunesStarted++;
        CompanionDiagnostics.event(c.diagnosticTrace(), "campfire_tune", "entity", c.getUUID(), "tune", tuneTitle(), "sound", tune.soundId());
    }

    private void rest() {
        silence();
        phase = Phase.REST;
        timer = REST_TICKS + c.getRandom().nextInt(REST_SPREAD);
        pose(SITTING);
    }

    /** A note rising over the guitar now and then while he plays. */
    private void notes(ServerLevel level) {
        Vec3 guitar = c.position().add(Vec3.directionFromRotation(0, c.yBodyRot).scale(.35)).add(0, 1.2, 0);
        level.sendParticles(ParticleTypes.NOTE, guitar.x, guitar.y, guitar.z, 0, c.getRandom().nextInt(25) / 24.0, 0, 0, 1);
    }

    /** The tune that was playing stops for the client that played it: a new tune, a rest, or the end of the evening. */
    private void silence() {
        if (tune == null) return;
        c.setCampfireTune("");
    }

    /** For the model's picture of the moment: what he plays and where, said as the background it is. */
    public String forAi() {
        String where = byFire ? "у костра" : "рядом с игроком";
        String kind = tune == null ? "" : tune.mood() == CompanionMusicLibrary.Mood.CHEERFUL ? "весёлая"
                : tune.mood() == CompanionMusicLibrary.Mood.CALM ? "спокойная" : "";
        String what = phase == Phase.PLAY && tune != null ? " «" + tuneTitle() + "»" + (kind.isEmpty() ? "" : " (" + kind + ")") : "";
        return (phase == Phase.APPROACH ? "идёт сесть " + where + ", чтобы сыграть на гитаре" : "сидит " + where + " и играет на гитаре" + what)
                + " — это фон, разговору он не мешает";
    }

    /** The face while a tune sounds (the owner asked, 09.11): a smile for a cheerful one, a plain face for a calm, sad or untagged one. */
    public dev.aicompanion.CompanionExpression mood() {
        return tune != null && tune.mood() == CompanionMusicLibrary.Mood.CHEERFUL ? dev.aicompanion.CompanionExpression.PLEASED : dev.aicompanion.CompanionExpression.CALM;
    }

    /** Sitting: he stays where he sat, his eyes on the fire (or the ground before him) and nowhere else (owner 09.11: not on the player). */
    private void hold() {
        c.getNavigation().stop();
        face();
    }

    private void face() { if (gaze != null) c.getLookControl().setLookAt(gaze.x, gaze.y, gaze.z, 10, 30); }

    private void pose(int pose) { c.getEntityData().set(CompanionEntity.DATA_CAMPFIRE, pose); }

    /**
     * A cell to sit in, standable and about two blocks from the spot: by a fire away from the player, so the two sit across it from
     * each other; without one the free cell beside the player nearest to him. Null if there is none (he then sits down where he gets to).
     */
    private BlockPos seat(ServerLevel level, BlockPos center, ServerPlayer player) {
        BlockPos best = null;
        double bestScore = -Double.MAX_VALUE;
        for (int dy : new int[]{0, -1, 1}) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (dx == 0 && dz == 0) continue;
            BlockPos pos = center.offset(dx, dy, dz);
            if (!standable(level, pos)) continue;
            Vec3 at = Vec3.atBottomCenterOf(pos);
            if (at.distanceToSqr(player.position()) < 1.2 * 1.2) continue;
            double score = -Math.abs(Math.sqrt(dx * dx + dz * dz) - 2) * 2 - Math.abs(dy)
                    + (byFire ? Math.sqrt(at.distanceToSqr(player.position())) * .6 - Math.sqrt(at.distanceToSqr(c.position())) * .05
                            : -Math.sqrt(at.distanceToSqr(c.position())) * .3);
            if (score > bestScore) { bestScore = score; best = pos.immutable(); }
        }
        return best;
    }

    private static boolean standable(ServerLevel level, BlockPos feet) {
        if (!level.hasChunkAt(feet)) return false;
        var below = level.getBlockState(feet.below());
        return level.getFluidState(feet).isEmpty() && level.getFluidState(feet.above()).isEmpty()
                && below.isFaceSturdy(level, feet.below(), Direction.UP) && !below.is(BlockTags.CAMPFIRES) && !below.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() && !level.getBlockState(feet).is(BlockTags.FIRE)
                && !level.getBlockState(feet).is(BlockTags.CAMPFIRES)
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }

    /** The nearest lit campfire within this distance of a place (two blocks up and down), or null. */
    static BlockPos litFire(ServerLevel level, BlockPos around, int radius) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(around.offset(-radius, -2, -radius), around.offset(radius, 2, radius))) {
            if (!level.hasChunkAt(pos) || !CampfireBlock.isLitCampfire(level.getBlockState(pos))) continue;
            double distance = pos.distSqr(around);
            if (distance <= (double) radius * radius + 4 && distance < bestDistance) { bestDistance = distance; best = pos.immutable(); }
        }
        return best;
    }

    private static boolean isLit(ServerLevel level, BlockPos pos) { return level.hasChunkAt(pos) && CampfireBlock.isLitCampfire(level.getBlockState(pos)); }

    /** A live monster near the place or near him. */
    private boolean danger(ServerLevel level, BlockPos place) {
        AABB around = new AABB(place).inflate(MONSTER_RANGE).minmax(c.getBoundingBox().inflate(MONSTER_RANGE));
        return !level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, around,
                mob -> mob instanceof Enemy && mob.isAlive() && (mob.distanceToSqr(place.getCenter()) <= MONSTER_RANGE * MONSTER_RANGE
                        || mob.distanceToSqr(c) <= MONSTER_RANGE * MONSTER_RANGE)).isEmpty();
    }

    private boolean carries(net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item) && !c.carriedForPlayer(c.getItem(slot))) return true;
        return false;
    }

    /** Sitting and playing: above following and strolling, below fights, pickups and getting out of trouble. */
    static final class SitGoal extends Goal {
        private final CompanionEntity c;
        SitGoal(CompanionEntity c) { this.c = c; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }
        @Override public boolean canUse() { return c.campfire.active(); }
        @Override public boolean canContinueToUse() { return c.campfire.active(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void tick() { c.campfire.tickSession(); }
        /** Something more urgent took him (his drops, getting his breath): he stands up and sits down again after it. */
        @Override public void stop() {
            c.getNavigation().stop();
            if (c.campfire.active()) c.campfire.standUp();
        }
    }

    private void standUp() {
        if (phase == Phase.APPROACH) c.travel().reset();
        if (phase == Phase.PLAY) silence();
        phase = Phase.APPROACH;
        lastSteps = false;
        approachUntil = c.level().getGameTime() + APPROACH_TICKS;
        pose(STANDING);
    }
}

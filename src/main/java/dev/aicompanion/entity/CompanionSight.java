package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * What he has seen with his own eyes, like a player without x-ray: ore with an open face in a clear line from his eyes, and
 * chests in sight. A look round every two seconds, a few sections and glances a tick. Seen ore is remembered (saved, bounded),
 * so «добудь железо» can lead back to the iron he passed in a cave; chests he saw join the known places.
 */
final class CompanionSight {
    static final int RADIUS = 16;
    static final int LOOK_TICKS = 40;
    static final int SECTIONS_PER_TICK = 2;
    static final int GLANCES_PER_TICK = 12;
    static final int MAX_ORE = 256;
    private record Section(int cx, int cz, int index) { }
    private record Seen(String dimension, BlockPos pos, CompanionOre.Kind kind) { }
    private final CompanionEntity owner;
    private final LinkedHashMap<String, Seen> ore = new LinkedHashMap<>();
    private final Deque<Section> sections = new ArrayDeque<>();
    private final Deque<BlockPos> glances = new ArrayDeque<>();
    private ServerLevel looking;
    private Vec3 center = Vec3.ZERO;
    private long nextLook;

    CompanionSight(CompanionEntity owner) { this.owner = owner; }
    private String dimension() { return owner.level().dimension().location().toString(); }
    private static String key(String dimension, BlockPos pos) { return dimension + ":" + pos.asLong(); }

    void tick() {
        if (!(owner.level() instanceof ServerLevel level) || !owner.isAlive()) return;
        if (looking != level) { sections.clear(); glances.clear(); looking = level; }
        if (!glances.isEmpty()) {
            for (int i = 0; i < GLANCES_PER_TICK && !glances.isEmpty(); i++) glance(level, glances.removeFirst());
            return;
        }
        if (!sections.isEmpty()) {
            for (int i = 0; i < SECTIONS_PER_TICK && !sections.isEmpty(); i++) scan(level, sections.removeFirst());
            return;
        }
        if (level.getGameTime() < nextLook) return;
        nextLook = level.getGameTime() + LOOK_TICKS;
        lookRound(level);
    }

    private void lookRound(ServerLevel level) {
        center = owner.getEyePosition();
        String dimension = dimension();
        // Remembered ore in sight range that is gone now (mined since) is forgotten, as he would see the hole.
        ore.values().removeIf(seen -> seen.dimension.equals(dimension) && seen.pos.distToCenterSqr(center) <= RADIUS * RADIUS
                && level.hasChunkAt(seen.pos) && seen.kind != CompanionOre.Kind.of(level.getBlockState(seen.pos)));
        int x = (int) Math.floor(center.x), y = (int) Math.floor(center.y), z = (int) Math.floor(center.z);
        int minY = Math.max(level.getMinBuildHeight(), y - RADIUS), maxY = Math.min(level.getMaxBuildHeight() - 1, y + RADIUS);
        for (int cx = (x - RADIUS) >> 4; cx <= (x + RADIUS) >> 4; cx++) for (int cz = (z - RADIUS) >> 4; cz <= (z + RADIUS) >> 4; cz++) {
            var chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) continue;
            for (int index = level.getSectionIndex(minY); index <= level.getSectionIndex(maxY); index++) {
                var section = chunk.getSection(index);
                if (!section.hasOnlyAir() && section.maybeHas(CompanionSight::noticed)) sections.add(new Section(cx, cz, index));
            }
        }
    }

    /** Ore or chests of the section near him with an open face: worth a glance. Already known ones are skipped. */
    private void scan(ServerLevel level, Section next) {
        var chunk = level.getChunkSource().getChunkNow(next.cx, next.cz);
        if (chunk == null) return;
        var section = chunk.getSection(next.index);
        int bottom = level.getSectionYFromSectionIndex(next.index) << 4;
        String dimension = dimension();
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            BlockState state = section.getBlockState(x, y, z);
            if (!noticed(state)) continue;
            BlockPos pos = new BlockPos((next.cx << 4) + x, bottom + y, (next.cz << 4) + z);
            if (pos.distToCenterSqr(center) > RADIUS * RADIUS || !openFace(level, pos)) continue;
            if (state.getBlock() instanceof ChestBlock ? owner.places.knows(pos) : ore.containsKey(key(dimension, pos))) continue;
            glances.add(pos);
        }
    }

    private void glance(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !inSight(owner, pos)) return;
        BlockState state = level.getBlockState(pos);
        CompanionOre.Kind kind = CompanionOre.Kind.of(state);
        if (kind != null) saw(pos, kind);
        else if (state.getBlock() instanceof ChestBlock) owner.rememberObserved(pos);
    }

    private static boolean noticed(BlockState state) { return CompanionOre.Kind.of(state) != null || state.getBlock() instanceof ChestBlock; }

    /**
     * He sees this block now: one of its faces is open (air, plants, glass, water), turned to him, and the line from his eyes
     * to it is clear, as a player looking round would see it. Whatever is buried in stone stays unseen.
     */
    static boolean inSight(CompanionEntity owner, BlockPos pos) {
        Level level = owner.level();
        Vec3 eye = owner.getEyePosition(), middle = Vec3.atCenterOf(pos);
        for (Direction side : Direction.values()) {
            Vec3 normal = Vec3.atLowerCornerOf(side.getNormal());
            if (eye.subtract(middle).dot(normal) <= .5 || !open(level, pos.relative(side))) continue;
            // A point just inside this face: the ray meets the face itself, not the edge of the block in front of it.
            var hit = level.clip(new ClipContext(eye, middle.add(normal.scale(.3)), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, owner));
            if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) return true;
        }
        return false;
    }

    private static boolean openFace(Level level, BlockPos pos) {
        for (Direction side : Direction.values()) if (open(level, pos.relative(side))) return true;
        return false;
    }

    private static boolean open(Level level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockState(pos).getVisualShape(level, pos, CollisionContext.empty()).isEmpty();
    }

    void saw(BlockPos pos, CompanionOre.Kind kind) {
        String key = key(dimension(), pos);
        // Seen again: the freshest memory, the last to be dropped when the memory is full.
        boolean known = ore.remove(key) != null;
        ore.put(key, new Seen(dimension(), pos.immutable(), kind));
        if (!known) dev.aicompanion.CompanionDiagnostics.event(owner.diagnosticTrace(), "ore_seen", "entity", owner.getUUID(),
                "dimension", dimension(), "position", pos, "ore", kind.key());
        while (ore.size() > MAX_ORE) ore.remove(ore.keySet().iterator().next());
    }

    void forget(BlockPos pos) { ore.remove(key(dimension(), pos)); }
    boolean remembers(BlockPos pos) { return ore.containsKey(key(dimension(), pos)); }

    /** Ore of this kind he saw in this dimension, within the radius around the origin (horizontally). */
    List<BlockPos> ore(CompanionOre.Kind kind, BlockPos origin, int radius) {
        String dimension = dimension();
        return ore.values().stream().filter(seen -> seen.kind == kind && seen.dimension.equals(dimension)).map(Seen::pos).filter(pos -> {
            long dx = pos.getX() - origin.getX(), dz = pos.getZ() - origin.getZ();
            return dx * dx + dz * dz <= (long) radius * radius;
        }).toList();
    }

    void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Seen seen : ore.values()) {
            CompoundTag entry = new CompoundTag(); entry.putString("Dimension", seen.dimension); entry.putLong("Pos", seen.pos.asLong());
            entry.putString("Kind", seen.kind.name()); list.add(entry);
        }
        tag.put("SeenOre", list);
    }

    void load(CompoundTag tag) {
        ore.clear();
        ListTag list = tag.getList("SeenOre", 10);
        for (int i = 0; i < Math.min(MAX_ORE, list.size()); i++) {
            CompoundTag entry = list.getCompound(i);
            CompanionOre.Kind kind;
            try { kind = CompanionOre.Kind.valueOf(entry.getString("Kind")); } catch (IllegalArgumentException unknown) { continue; }
            BlockPos pos = BlockPos.of(entry.getLong("Pos"));
            ore.put(key(entry.getString("Dimension"), pos), new Seen(entry.getString("Dimension"), pos, kind));
        }
    }
}

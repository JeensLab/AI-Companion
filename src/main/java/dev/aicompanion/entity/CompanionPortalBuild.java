package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * «Построй портал» (decided 08.11): a Nether portal's frame without its corners, ten obsidian, on the nearest level free place within
 * {@link #SITE} blocks of the player, its two bottom blocks set into the ground in place of what was there (he picks that up), so the
 * way in has no step; lit at once with his flint and steel, which wears as a player's does. A lit portal within {@link #NEAR} blocks of
 * the player is his way to the Nether already: none is built; one he knows farther off he leads the player to (decided 09.11), unless
 * the player asked for a new one. A portal is known by its own block: one he built or saw, wherever the player has gone since. Short
 * of obsidian or of a flint and steel the order is refused at once as a shortage, and the plan gets them first (CompanionPlanRunner).
 * What stands of the frame is read from the world, so an order stopped half way goes on with the same frame; the obsidian he set is
 * not mined again for it (CompanionObsidian). He digs only natural ground and sets the frame in air: the player's blocks stay. The top
 * of a frame without corners hangs in the air: as a player does, he props it with a block of his under it and takes it away after.
 * He never steps into the portal himself (CompanionNodeEvaluator); its way in faces the player.
 */
final class CompanionPortalBuild {
    static final String THERE = "Рядом уже есть портал.";
    static final String DONE = "Портал готов.";
    static final String LED = "Пришли — вот портал.";
    static final String END = "В Энде портал не зажечь.";
    static final String ELSEWHERE = "Здесь портал не зажечь.";
    static final String NO_PLACE = "Не нашёл ровного свободного места под портал рядом с тобой.";
    static final String NO_WAY = "Не могу подойти туда, где строю портал.";
    static final String TAKEN = "Место под портал заняли — дострою по новой просьбе.";
    static final String NO_FLINT = "Нечем зажечь портал — нужно огниво.";
    static final String NO_PROP = "Нечем подпереть верх рамки — нужен блок земли, булыжника или досок.";
    static final String NOT_LIT = "Рамка не зажглась.";
    static final String LYING = "Не могу забрать то, что лежит в рамке, — убери, и я зажгу портал.";
    static final String CROWDED = "В рамке кто-то стоит — отойди, и я зажгу портал.";
    static final String FLUID = "К рамке подтекает вода или лава — место не годится.";
    static final String PORTAL = "minecraft:nether_portal";
    /** A frame without its corners. */
    static final int OBSIDIAN = 10;
    /** A lit portal this near the player is his way to the Nether already; the frame goes up this near him (blocks). */
    private static final int NEAR = 16, SITE = 10;
    /** A portal he knows this far from the player is led to instead of built; a player this far behind is waited for, this long. */
    private static final int FAR = 150, LAG = 16, LEAD_WAIT = 20 * 60;
    /** Ground levels looked at below and above the ground under the player. */
    private static final int BELOW = 3, ABOVE = 3;
    /** The hardest ground he digs out for the bottom of the frame (deepslate); harder is no place for it. */
    private static final float HARDEST = 3;
    /** No chest this near the frame: the portal would stand in the way to it (blocks). */
    private static final int CHESTS = 2;
    /** A player's reach for a block; walks to the place before he gives it up; ticks a block he set takes; how long he waits for someone in the way. */
    private static final double REACH = 4.5;
    private static final int LEGS = 8, PLACE_TICKS = 4, WAIT_TICKS = 100;
    /** How long he tries to pick up what lies in the frame before he says he cannot (ticks). */
    private static final int PICK_TICKS = 200;
    /** What he props the top with besides planks and logs: blocks that come back as they were when broken (glass would not, review 09.11). */
    private static final Set<Item> PROPS = Set.of(Items.DIRT, Items.COARSE_DIRT, Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.NETHERRACK);

    private enum Kind { DIG, PROP, OBSIDIAN, CLEAR, PICK, LIGHT }
    private record Step(Kind kind, BlockPos cell) { }

    /** The frame: its column 0 stands on {@code base}, it runs {@code along}, the way in is on the {@code front} side. */
    record Frame(BlockPos base, Direction along, Direction front) {
        BlockPos cell(int i, int j) { return base.relative(along, i).above(j); }
        /** In the order he sets them: the bottom in the ground, the columns from below, the top. */
        List<BlockPos> obsidian() {
            return List.of(cell(1, 0), cell(2, 0), cell(0, 1), cell(0, 2), cell(0, 3), cell(3, 1), cell(3, 2), cell(3, 3), cell(1, 4), cell(2, 4));
        }
        List<BlockPos> inside() { return List.of(cell(1, 1), cell(2, 1), cell(1, 2), cell(2, 2), cell(1, 3), cell(2, 3)); }
        AABB opening() { return new AABB(cell(1, 1)).minmax(new AABB(cell(2, 3))); }
        BlockPos fire() { return cell(1, 1); }
        AABB box() { return new AABB(cell(0, 0)).minmax(new AABB(cell(3, 4))); }
    }

    private final CompanionWork w;
    private final CompanionEntity c;
    private Frame frame;
    /** The portal he leads the player to, and since when he waits for the player. */
    private BlockPos leadTo;
    private long leadWait;
    /** The obsidian he set for a frame not lit yet: no obsidian order mines it (kept over orders, not over a reload). */
    private final Set<BlockPos> laid = new HashSet<>();
    private Set<BlockPos> stands = Set.of(), walkingTo = Set.of();
    private Path path;
    private int legs;
    private long waitingSince, pickingSince;
    private boolean retried;

    CompanionPortalBuild(CompanionWork w) { this.w = w; this.c = w.owner; }

    /** Where the portal is for: the player when he is in this world, else where he himself stands. */
    static BlockPos center(CompanionEntity c) {
        ServerPlayer player = c.getOwnerPlayer();
        return player != null && player.level() == c.level() ? player.blockPosition() : c.blockPosition();
    }

    /**
     * The nearest lit portal he knows in this world within {@code within} blocks of the cell: one lit near the cell or near him is seen
     * now and remembered; others are remembered (built or seen), the block still a portal where it is loaded, trusted where it is not.
     */
    static BlockPos known(CompanionEntity c, ServerLevel level, BlockPos center, int within) {
        for (BlockPos near : List.of(center, c.blockPosition())) {
            var seen = level.getPoiManager().findClosest(type -> type.is(PoiTypes.NETHER_PORTAL),
                    pos -> level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.NETHER_PORTAL), near, NEAR, PoiManager.Occupancy.ANY);
            // One block of a portal is enough to know it by: not one more for each of its six.
            if (seen.isPresent() && c.knownPlaces(PORTAL).stream().noneMatch(pos -> pos.closerThan(seen.get(), 4))) c.rememberObserved(seen.get());
        }
        return c.knownPlaces(PORTAL).stream().filter(pos -> pos.closerThan(center, within))
                .filter(pos -> !level.hasChunkAt(pos) || level.getBlockState(pos).is(Blocks.NETHER_PORTAL))
                .min(Comparator.comparingDouble(pos -> pos.distSqr(center))).orElse(null);
    }

    /** Obsidian he set for a frame still being built: not his to mine for it. */
    boolean laid(BlockPos pos) { return laid.contains(pos); }

    /**
     * build_portal: null when the work may start (a build, or the walk to a portal he knows), else the answer: a portal near already
     * (a success), a world no portal lights in, no place, or a shortage of obsidian or of a flint and steel, its evidence naming what
     * to get and how many he must carry. {@code item} "here" («построй здесь»): built here, not led; "new" («построй новый»): built
     * though a portal stands near too; an item id: the plan got what the step lacked (CompanionPlanRunner), the portal he could lead
     * to was looked for before, one near is looked for again (the player may have lit one meanwhile).
     */
    CompanionActionResult begin(String item) {
        if (!(c.level() instanceof ServerLevel level)) return CompanionActionResult.failed("Для работы нужен загруженный мир.");
        reset();
        if (level.dimension() == Level.END) return CompanionActionResult.failed(END);
        if (level.dimension() != Level.OVERWORLD && level.dimension() != Level.NETHER) return CompanionActionResult.failed(ELSEWHERE);
        BlockPos center = center(c);
        if (!"new".equals(item)) {
            boolean lead = !"here".equals(item) && !item.contains(":");
            BlockPos portal = known(c, level, center, lead ? FAR : NEAR);
            if (portal != null && portal.closerThan(center, NEAR)) return CompanionActionResult.ok(THERE);
            if (portal != null && lead) {
                // The foot of its column: a portal seen from above was remembered by its top, three blocks over the floor before it.
                while (level.hasChunkAt(portal) && level.getBlockState(portal.below()).is(Blocks.NETHER_PORTAL)) portal = portal.below();
                leadTo = portal;
                int far = (int) Math.round(Math.sqrt(portal.distSqr(center)));
                c.autonomySay("Портал уже есть — " + CompanionWorkItems.blocks(far) + " " + where(center, portal)
                        + ". Пойдём, отведу. Нужен здесь — скажи «построй новый портал».", true);
                event("portal_lead", "portal", portal.toShortString(), "far", far);
                return null;
            }
        }
        ServerPlayer player = c.getOwnerPlayer();
        long started = System.nanoTime();
        List<BlockPos> chests = w.chests.blockEntitiesNear(level, center, SITE + 4 + CHESTS).stream()
                .filter(pos -> level.getBlockEntity(pos) instanceof net.minecraft.world.Container).toList();
        Predicate<BlockPos> site = c.getOwnerId() == null ? pos -> false : CompanionPlayerBuilds.get(level.getServer()).sites(c.getOwnerId(), level);
        frame = choose(level, center, chests, site, player == null || player.level() != level ? null : player.getBoundingBox());
        if (frame == null) return CompanionActionResult.failed(NO_PLACE);
        int need = (int) frame.obsidian().stream().filter(cell -> !level.getBlockState(cell).is(Blocks.OBSIDIAN)).count();
        int have = w.items.countCarried(Items.OBSIDIAN);
        if (have < need) return shortage("Для портала нужно " + need + " обсидиана, у меня " + have + ".", "minecraft:obsidian", need);
        if (!lighter()) return shortage(NO_FLINT, "minecraft:flint_and_steel", 1);
        // In sand there is no block to prop the top with: said now, not with eight obsidian set.
        if (!propped(level)) return CompanionActionResult.failed(NO_PROP);
        stands = standsFor(level, frame);
        if (stands.isEmpty()) return CompanionActionResult.failed(NO_WAY);
        event("portal_site", "frame", frame.base().toShortString(), "along", frame.along(), "front", frame.front(), "need", need, "searchMs", (System.nanoTime() - started) / 1_000_000);
        return null;
    }

    private static CompanionActionResult shortage(String message, String item, int count) {
        return CompanionActionResult.shortage(message).withEvidence(new CompanionActionResult.Evidence(0, "build_portal", item, count, null, null, null));
    }

    String jobName() {
        if (leadTo != null) return "дорогу к порталу";
        if (frame == null || !(c.level() instanceof ServerLevel level)) return "постройку портала";
        return "постройку портала (" + frame.obsidian().stream().filter(cell -> level.getBlockState(cell).is(Blocks.OBSIDIAN)).count() + " из " + OBSIDIAN + ")";
    }

    void cancel() { reset(); }

    private void reset() { frame = null; leadTo = null; leadWait = 0; stands = walkingTo = Set.of(); path = null; legs = 0; waitingSince = pickingSince = 0; retried = false; }

    /** «На север», «на юго-запад»: where the cell lies from the other, as he says it (north is −z). */
    private static String where(BlockPos from, BlockPos to) {
        String[] names = {"на юг", "на юго-запад", "на запад", "на северо-запад", "на север", "на северо-восток", "на восток", "на юго-восток"};
        double angle = Math.toDegrees(Math.atan2(-(to.getX() - from.getX()), to.getZ() - from.getZ()));
        return names[Math.floorMod(Math.round(angle / 45), 8)];
    }

    /** One tick: to the portal he knows, or the next thing the frame lacks from where he can reach it; lit once whole. */
    void tick(ServerLevel level) {
        if (leadTo != null) { lead(level); return; }
        if (frame == null) { w.finish(false, NO_PLACE); return; }
        if (level.getBlockState(frame.fire()).is(Blocks.NETHER_PORTAL)) { done(level); return; }
        Step step = next(level);
        if (step == null) return;
        if (step.kind() != Kind.PICK) { pickingSince = 0; retried = false; }
        // Out of the frame, with the cell in his reach and sight: from inside it the fire would take him to the Nether. What lies in
        // the frame he steps in for, the frame not lit yet.
        Vec3 eyes = c.getEyePosition();
        if (step.kind() != Kind.PICK && (!c.onGround() || frame.box().intersects(c.getBoundingBox())
                || new AABB(step.cell()).distanceToSqr(eyes) > REACH * REACH || !visible(level, eyes, step.cell()))) {
            c.workStatus("Иду строить портал.");
            // Come to a place it is still not seen from (a column set since hides it): that place is no good any more.
            if (path != null && w.route.legOver() && stands.remove(c.blockPosition())) path = null;
            if (!walk(stands)) { event("portal_no_way", "step", step); w.finish(false, NO_WAY); }
            return;
        }
        if (step.kind() != Kind.PICK) w.route.stop();
        switch (step.kind()) {
            case DIG, CLEAR -> dig(level, step.cell());
            case PROP -> prop(level, step.cell());
            case OBSIDIAN -> set(level, step.cell(), Items.OBSIDIAN);
            case PICK -> pick(level, step.cell());
            case LIGHT -> light(level);
        }
    }

    /**
     * To the portal he knows, the player after him: a player left behind is waited for, a minute at most, as on a walk along a heading
     * (CompanionCourse). He stops before it: a portal is a wall to his own feet.
     */
    private void lead(ServerLevel level) {
        if (level.hasChunkAt(leadTo) && !level.getBlockState(leadTo).is(Blocks.NETHER_PORTAL)) { w.finish(false, "Портала там больше нет."); return; }
        // From the server's list: the entity's own lookup has no player in another world.
        ServerPlayer player = c.getOwnerId() == null ? null : level.getServer().getPlayerList().getPlayer(c.getOwnerId());
        // The player knew the way and went through first: the walk after him through it is CompanionPortals', as when he follows. Gone
        // from anywhere else (a command, death, another portal), it was not this way: he stays (review 09.11).
        if (player != null && player.level() != level) {
            // Seen every tick by CompanionPortals: the lead does not tick while he fights or picks up drops (review 09.11).
            BlockPos last = c.portals.lastHere();
            if (last != null && last.closerThan(leadTo, 4)) { c.portals.playerWentThrough(leadTo); w.finish(true, "Ты прошёл первым."); }
            else w.finish(false, "Ты ушёл не через этот портал — я остался здесь.");
            return;
        }
        long now = level.getGameTime();
        if (player != null && player.distanceTo(c) > LAG && player.distanceToSqr(leadTo.getCenter()) > c.distanceToSqr(leadTo.getCenter())) {
            // The walk is given up after half a minute without coming closer (CompanionTravel): it starts anew after the wait.
            if (leadWait == 0) { leadWait = now; w.route.legsEnd(false); }
            w.route.stop();
            c.getLookControl().setLookAt(player, 30, 30);
            c.workStatus("Жду тебя — веду к порталу.");
            if (now - leadWait > LEAD_WAIT) w.finish(false, "Ты отстал. Портал вон там: " + leadTo.toShortString() + ".");
            return;
        }
        leadWait = 0;
        c.workStatus("Веду к порталу.");
        var step = w.route.legs(leadTo, 3);
        if (step == CompanionTravel.Step.FAILED) {
            w.route.legsEnd(false);
            w.finish(false, "Не могу пройти к порталу. Он вон там: " + leadTo.toShortString() + ".");
        } else if (step == CompanionTravel.Step.NEAR) {
            w.route.legsEnd(true);
            event("portal_led", "portal", leadTo.toShortString());
            w.finish(true, LED);
        }
    }

    /**
     * What the frame lacks first, read from the world: the ground where its bottom goes, then the obsidian in order (a prop under a
     * top block that has nothing to rest on), then whatever is inside it (the prop too), then what lies in it, then the fire. Null
     * when he cannot go on.
     */
    private Step next(ServerLevel level) {
        // Water or lava come in (a hidden spring, a lake behind): no frame here, and no swinging at the water.
        for (BlockPos cell : frame.inside()) if (!level.getFluidState(cell).isEmpty()) { w.finish(false, FLUID); return null; }
        for (BlockPos ground : List.of(frame.cell(1, 0), frame.cell(2, 0))) {
            if (!level.getFluidState(ground).isEmpty()) { w.finish(false, FLUID); return null; }
            if (!level.getBlockState(ground).isAir() && !level.getBlockState(ground).is(Blocks.OBSIDIAN)) return new Step(Kind.DIG, ground);
        }
        for (BlockPos cell : frame.obsidian()) {
            if (level.getBlockState(cell).is(Blocks.OBSIDIAN)) continue;
            if (!free(level, cell)) { w.finish(false, TAKEN); return null; }
            if (supported(level, cell)) return new Step(Kind.OBSIDIAN, cell);
            BlockPos under = cell.below();
            if (frame.inside().contains(under) && level.getBlockState(under).isAir()) return new Step(Kind.PROP, under);
            w.finish(false, TAKEN);
            return null;
        }
        for (BlockPos cell : frame.inside()) {
            BlockState state = level.getBlockState(cell);
            if (state.isAir()) continue;
            if (!free(level, cell) && !diggable(state, level, cell)) { w.finish(false, TAKEN); return null; }
            return new Step(Kind.CLEAR, cell);
        }
        List<ItemEntity> lying = lying(level);
        if (!lying.isEmpty()) return new Step(Kind.PICK, lying.getFirst().blockPosition());
        return new Step(Kind.LIGHT, frame.fire());
    }

    /** The ground under the bottom, a prop or a plant inside the frame: broken as he breaks blocks, what it drops picked up. */
    private void dig(ServerLevel level, BlockPos cell) {
        BlockState state = level.getBlockState(cell);
        // Taken once: a tool put in his hand again every tick would start the swing over.
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL) && !c.getMainHandItem().is(ItemTags.SHOVELS)) CompanionTools.equip(c, ItemTags.SHOVELS, state);
        else if (state.is(BlockTags.MINEABLE_WITH_PICKAXE) && !c.getMainHandItem().is(ItemTags.PICKAXES)) CompanionTools.equip(c, ItemTags.PICKAXES, state);
        c.workStatus("Расчищаю место под портал.");
        if (!w.breaking.tick(level, cell)) return;
        ItemStack tool = c.getMainHandItem();
        var drops = CompanionTools.drops(state, level, cell, level.getBlockEntity(cell), c, tool);
        if (!level.destroyBlock(cell, false, c, 512)) { w.finish(false, TAKEN); return; }
        tool.getItem().mineBlock(tool, level, state, cell, c);
        c.dropHarvest(cell, drops);
        event("portal_dug", "cell", cell.toShortString(), "block", BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    /** What lies inside the frame: lit, the portal would take it, and his pickup would follow it in (in game 08.11, the prop's drop). */
    private List<ItemEntity> lying(ServerLevel level) { return level.getEntitiesOfClass(ItemEntity.class, frame.opening(), ItemEntity::isAlive); }

    /**
     * What lies inside the frame is picked up before it is lit: once it lies, his own drop is chased again (falling, it was given up as
     * out of reach), and he steps into the frame, not lit yet, onto it. Not in his bag after {@link #PICK_TICKS}: he says so, unlit.
     */
    private void pick(ServerLevel level, BlockPos cell) {
        if (pickingSince == 0) pickingSince = level.getGameTime();
        if (level.getGameTime() - pickingSince > PICK_TICKS) { event("portal_items_left", "cell", cell.toShortString()); w.finish(false, LYING); return; }
        c.workStatus("Подбираю то, что упало в рамку.");
        if (lying(level).stream().anyMatch(item -> !item.onGround())) return;
        if (!retried) { retried = true; c.retryHarvestPickup(); return; }
        if (c.blockPosition().equals(cell)) w.route.stop();
        else walk(Set.of(cell));
    }

    /** A block of his own under a top block, so it has a face to be set against; taken away with the rest of what is inside. */
    private void prop(ServerLevel level, BlockPos cell) {
        ItemStack best = ItemStack.EMPTY;
        for (ItemStack stack : w.items.carriedItems()) if (propping(stack, level, cell) && (best.isEmpty() || hardness(stack) < hardness(best))) best = stack;
        if (best.isEmpty()) { w.finish(false, NO_PROP); return; }
        set(level, cell, best.getItem());
    }

    /** A block that comes back as it was when he breaks it with what he carries: dirt, cobblestone, netherrack, planks, logs. */
    private boolean propping(ItemStack stack, ServerLevel level, BlockPos cell) {
        return stack.getItem() instanceof BlockItem item && prop(stack) && diggable(item.getBlock().defaultBlockState(), level, cell);
    }

    private static boolean prop(ItemStack stack) { return PROPS.contains(stack.getItem()) || stack.is(ItemTags.PLANKS) || stack.is(ItemTags.LOGS); }

    private static float hardness(ItemStack stack) { return ((BlockItem) stack.getItem()).getBlock().defaultDestroyTime(); }

    /** The top has something to rest on, or he can prop it: a prop in his bag, or one the ground dug out for the bottom gives him. */
    private boolean propped(ServerLevel level) {
        BlockPos prop = frame.cell(1, 3);
        // (1, 4) is set first (next): only what it rests on spares the prop.
        if (level.getBlockState(frame.cell(1, 4)).is(Blocks.OBSIDIAN) || supported(level, frame.cell(1, 4))) return true;
        if (w.items.carriedItems().stream().anyMatch(stack -> propping(stack, level, prop))) return true;
        for (BlockPos ground : List.of(frame.cell(1, 0), frame.cell(2, 0))) {
            BlockState state = level.getBlockState(ground);
            ItemStack tool = w.items.carriedItems().stream().filter(stack -> stack.isCorrectToolForDrops(state)).findFirst().orElse(ItemStack.EMPTY);
            if (CompanionTools.drops(state, level, ground, null, c, tool).stream().anyMatch(drop -> propping(drop, level, prop))) return true;
        }
        return false;
    }

    /** The block in his hand set in the cell as a player sets it: against a face, with no one in it, one from the stack. */
    private void set(ServerLevel level, BlockPos cell, Item item) {
        BlockState state = ((BlockItem) item).getBlock().defaultBlockState();
        if (!level.isUnobstructed(state, cell, CollisionContext.empty())) {
            if (waitingSince == 0) waitingSince = level.getGameTime();
            if (level.getGameTime() - waitingSince > WAIT_TICKS) { w.finish(false, TAKEN); return; }
            c.workStatus("Жду, пока отойдут от рамки портала.");
            return;
        }
        waitingSince = 0;
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        if (!c.getMainHandItem().is(item) && !c.equipForWork(id).success()) { w.finish(false, "Не могу взять в руку «" + CompanionWork.itemName(item) + "»."); return; }
        ItemStack held = c.getMainHandItem();
        if (!held.is(item) || !level.setBlock(cell, state, 3)) { w.finish(false, TAKEN); return; }
        if (item == Items.OBSIDIAN) laid.add(cell.immutable());
        c.animateAction(1, cell, held);
        held.shrink(1);
        c.setChanged();
        c.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, cell, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, .65F, 1F);
        level.gameEvent(c, GameEvent.BLOCK_PLACE, cell);
        w.cooldown = PLACE_TICKS;
        c.workStatus("Строю портал: " + frame.obsidian().stream().filter(at -> level.getBlockState(at).is(Blocks.OBSIDIAN)).count() + " из " + OBSIDIAN + ".");
    }

    /** His flint and steel on the bottom of the frame, as a player strikes it: the fire there lights the frame, the steel wears by one. */
    private void light(ServerLevel level) {
        if (!lighter() || !c.getMainHandItem().is(Items.FLINT_AND_STEEL) && !c.equipForWork("minecraft:flint_and_steel").success()
                || !CompanionFacts.usable(c.getMainHandItem())) { w.finish(false, NO_FLINT); return; }
        // Somebody in the frame would go to the Nether with the fire: waited for, as for a block (set).
        if (!level.getEntitiesOfClass(LivingEntity.class, frame.opening(), LivingEntity::isAlive).isEmpty()) {
            if (waitingSince == 0) waitingSince = level.getGameTime();
            if (level.getGameTime() - waitingSince > WAIT_TICKS) w.finish(false, CROWDED);
            else c.workStatus("Жду, пока из рамки выйдут.");
            return;
        }
        waitingSince = 0;
        ItemStack steel = c.getMainHandItem();
        BlockPos fire = frame.fire();
        if (!BaseFireBlock.canBePlacedAt(level, fire, frame.front().getOpposite())) { w.finish(false, NOT_LIT); return; }
        level.playSound(null, fire, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1F, level.getRandom().nextFloat() * .4F + .8F);
        level.setBlock(fire, BaseFireBlock.getState(level, fire), 11);
        level.gameEvent(c, GameEvent.BLOCK_PLACE, fire.below());
        c.animateAction(1, fire, steel);
        c.swing(InteractionHand.MAIN_HAND);
        steel.hurtAndBreak(1, c, EquipmentSlot.MAINHAND);
        if (!level.getBlockState(fire).is(Blocks.NETHER_PORTAL)) { event("portal_not_lit", "fire", fire.toShortString()); w.finish(false, NOT_LIT); return; }
        done(level);
    }

    private void done(ServerLevel level) {
        c.rememberPlaced(frame.fire());
        laid.removeAll(frame.obsidian());
        event("portal_lit", "fire", frame.fire().toShortString());
        reset();
        w.finish(true, DONE);
    }

    /** He carries a flint and steel he can still strike, in the bag or a hand. */
    private boolean lighter() {
        for (ItemStack stack : w.items.carriedItems()) if (stack.is(Items.FLINT_AND_STEEL) && CompanionFacts.usable(stack)) return true;
        return false;
    }

    /**
     * The nearest frame that fits, the one most of whose obsidian already stands first (his own, stopped half way): both ways round,
     * its ground a few levels above or below the player's, the way in on the player's side (behind it, the way to him led through it).
     */
    private Frame choose(ServerLevel level, BlockPos center, List<BlockPos> chests, Predicate<BlockPos> site, AABB player) {
        Frame best = null;
        int bestPlaced = -1;
        double bestFar = 0;
        boolean bestFacing = false;
        for (int dy = -BELOW; dy <= ABOVE; dy++) for (int dx = -SITE; dx <= SITE; dx++) for (int dz = -SITE; dz <= SITE; dz++) {
            BlockPos base = center.offset(dx, dy - 1, dz);
            for (Direction along : new Direction[]{Direction.EAST, Direction.SOUTH}) for (Direction front : new Direction[]{along.getClockWise(), along.getCounterClockWise()}) {
                Frame f = new Frame(base, along, front);
                Vec3 middle = Vec3.atCenterOf(f.cell(1, 2)).add(Vec3.atLowerCornerOf(along.getNormal()).scale(.5));
                double far = middle.distanceToSqr(Vec3.atBottomCenterOf(center));
                if (far > SITE * SITE) continue;
                // His frame stopped half way (its bottom set first) is finished whichever way it faces now.
                if (Vec3.atBottomCenterOf(center).subtract(middle).dot(Vec3.atLowerCornerOf(front.getNormal())) < 0
                        && !level.getBlockState(f.cell(1, 0)).is(Blocks.OBSIDIAN) && !level.getBlockState(f.cell(2, 0)).is(Blocks.OBSIDIAN)) continue;
                int placed = fits(level, f, chests, site, player);
                boolean facing = Vec3.atBottomCenterOf(center).subtract(middle).dot(Vec3.atLowerCornerOf(front.getNormal())) >= 0;
                if (placed > bestPlaced || placed >= 0 && placed == bestPlaced && (far < bestFar - 1e-6 || far < bestFar + 1e-6 && facing && !bestFacing)) {
                    best = f; bestPlaced = placed; bestFar = far; bestFacing = facing;
                }
            }
        }
        return best;
    }

    /**
     * How many of the frame's obsidian stand already, or -1 when it does not fit: its columns on firm ground, the ground for its bottom
     * natural and his to dig with no water or lava beside it to run in, the rest free and dry (his prop may still stand under the top),
     * a level way in on its front, no chest near, no obsidian right outside it (a bigger frame of the player's), not where the player
     * stands (null: he is not in this world). Air is all it takes of the player's: none of his blocks is dug (decided 09.11).
     */
    private int fits(ServerLevel level, Frame f, List<BlockPos> chests, Predicate<BlockPos> site, AABB player) {
        if (player != null && f.box().intersects(player)) return -1;
        AABB near = f.box().inflate(CHESTS);
        if (chests.stream().anyMatch(chest -> near.contains(Vec3.atCenterOf(chest)))) return -1;
        int placed = 0;
        boolean propped = false;
        for (int i = 0; i < 4; i++) {
            BlockPos ground = f.cell(i, 0);
            if (!level.hasChunkAt(ground)) return -1;
            BlockState state = level.getBlockState(ground);
            boolean column = i == 0 || i == 3;
            if (column) { if (!state.isFaceSturdy(level, ground, Direction.UP) || !state.getFluidState().isEmpty()) return -1; }
            else if (state.is(Blocks.OBSIDIAN)) placed++;
            else if (!free(level, ground) && !(natural(state, site.test(ground)) && diggable(state, level, ground))) return -1;
            if (!column) {
                BlockPos before = ground.relative(f.front());
                if (!level.getBlockState(before).isFaceSturdy(level, before, Direction.UP) || !free(level, before.above()) || !free(level, before.above(2))) return -1;
                for (BlockPos beside : List.of(before, ground.relative(f.front().getOpposite()), ground.below()))
                    if (!level.getFluidState(beside).isEmpty()) return -1;
            }
            for (int j = 1; j <= 4; j++) {
                if (column && j == 4) continue;
                BlockPos cell = f.cell(i, j);
                BlockState here = level.getBlockState(cell);
                boolean prop = cell.equals(f.cell(1, 3)) && prop(new ItemStack(here.getBlock().asItem())) && diggable(here, level, cell);
                if (prop && !free(level, cell)) propped = true;
                if (!column && j <= 3) { if (!free(level, cell) && !prop) return -1; }
                else if (here.is(Blocks.OBSIDIAN)) placed++;
                else if (!free(level, cell)) return -1;
            }
        }
        for (int j = 0; j <= 4; j++) if (level.getBlockState(f.cell(-1, j)).is(Blocks.OBSIDIAN) || level.getBlockState(f.cell(4, j)).is(Blocks.OBSIDIAN)) return -1;
        for (int i = 0; i <= 3; i++) if (level.getBlockState(f.cell(i, 5)).is(Blocks.OBSIDIAN)) return -1;
        // A block there of a frame not begun is the world's or the player's (a plank of a bridge, a branch): not his prop to take away.
        if (propped && placed == 0) return -1;
        // Inside a bigger empty frame of the player's, his bottom row standing in this frame's: not finished as a small one.
        if (placed > 0 && placed < OBSIDIAN && net.minecraft.world.level.portal.PortalShape.findEmptyPortalShape(level, f.fire(), Direction.Axis.X).isPresent()) return -1;
        return placed;
    }

    /**
     * Ground as the world made it: earth, sand, gravel, stone, netherrack and the like; the player's floors are not dug. Stone,
     * sandstone and terracotta are floors too: where the player builds ({@code site}), they are taken for his (review 09.11).
     */
    private static boolean natural(BlockState state, boolean site) {
        if (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE) || state.is(BlockTags.TERRACOTTA)) return !site;
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(BlockTags.NYLIUM) || state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL) || state.is(Blocks.CLAY);
    }

    /** Air or a plant a block is set over, without water or lava. */
    private static boolean free(ServerLevel level, BlockPos cell) {
        BlockState state = level.getBlockState(cell);
        return state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    /** A plain block he breaks in a few seconds and gets back with what he carries: no chest or furnace, no fluid. */
    private boolean diggable(BlockState state, ServerLevel level, BlockPos cell) {
        float hardness = state.getDestroySpeed(level, cell);
        if (hardness < 0 || hardness > HARDEST || state.hasBlockEntity() || !state.getFluidState().isEmpty()) return false;
        if (!state.requiresCorrectToolForDrops()) return true;
        for (ItemStack stack : w.items.carriedItems()) if (stack.isCorrectToolForDrops(state)) return true;
        return false;
    }

    /** A face of a block beside it to set a block against, as the house builder asks. */
    private static boolean supported(ServerLevel level, BlockPos cell) {
        for (Direction side : Direction.values()) if (!level.getBlockState(cell.relative(side)).getCollisionShape(level, cell.relative(side)).isEmpty()) return true;
        return false;
    }

    /** Cells before the frame from which his hand reaches all of it, out of the frame. */
    private Set<BlockPos> standsFor(ServerLevel level, Frame f) {
        List<BlockPos> all = new ArrayList<>(f.obsidian());
        all.addAll(f.inside());
        Set<BlockPos> cells = new HashSet<>();
        for (int i = -1; i <= 4; i++) for (int d = 1; d <= 3; d++) for (int dy = 0; dy <= 2; dy++) {
            BlockPos stand = f.cell(i, dy).relative(f.front(), d).immutable();
            if (!CompanionMiningNavigation.standable(c, stand)) continue;
            Vec3 eyes = Vec3.atBottomCenterOf(stand).add(0, c.getEyeHeight(), 0);
            if (all.stream().allMatch(cell -> new AABB(cell).distanceToSqr(eyes) <= REACH * REACH)) cells.add(stand);
        }
        return cells;
    }

    /** One tick of the walk to these cells: false when there is no way (or no progress after {@link #LEGS} walks). */
    private boolean walk(Set<BlockPos> cells) {
        CompanionRoute route = w.route;
        if (!cells.equals(walkingTo)) { walkingTo = Set.copyOf(cells); path = null; legs = 0; }
        if (path != null && !route.legOver()) return true;
        if (cells.isEmpty() || ++legs > LEGS) return false;
        CompanionRoute.Reach reach = route.cells(cells, 0);
        if (reach.busy() || CompanionRoute.NOT_SEARCHED.equals(reach.reason())) { legs--; return true; }
        if (!reach.found()) return false;
        path = reach.path();
        route.walk(path);
        return true;
    }

    /**
     * As a player aims at a block: its nearest point or the middle of a face turned to him, with nothing between. The ray to the nearest
     * edge grazes what is beside it: the ground of the way in for the ground he digs, the prop under a top block of the frame.
     */
    private boolean visible(ServerLevel level, Vec3 eyes, BlockPos cell) {
        List<Vec3> aims = new ArrayList<>(List.of(new Vec3(Math.clamp(eyes.x, cell.getX() + .01, cell.getX() + .99),
                Math.clamp(eyes.y, cell.getY() + .01, cell.getY() + .99), Math.clamp(eyes.z, cell.getZ() + .01, cell.getZ() + .99))));
        for (Direction side : Direction.values()) {
            Vec3 face = Vec3.atCenterOf(cell).add(Vec3.atLowerCornerOf(side.getNormal()).scale(.49));
            if (eyes.subtract(face).dot(Vec3.atLowerCornerOf(side.getNormal())) > 0) aims.add(face);
        }
        for (Vec3 aim : aims) {
            HitResult hit = level.clip(new ClipContext(eyes, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c));
            if (hit.getType() == HitResult.Type.MISS || hit instanceof BlockHitResult block && block.getBlockPos().equals(cell)) return true;
        }
        return false;
    }

    private void event(String name, Object... fields) {
        Object[] head = {"at", c.blockPosition().toShortString()};
        Object[] all = java.util.Arrays.copyOf(head, head.length + fields.length);
        System.arraycopy(fields, 0, all, head.length, fields.length);
        dev.aicompanion.CompanionDiagnostics.event(c.diagnosticTrace(), name, all);
    }
}

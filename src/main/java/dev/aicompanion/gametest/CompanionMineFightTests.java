package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static dev.aicompanion.gametest.CompanionMineArena.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineArena.carveMine;
import static dev.aicompanion.gametest.CompanionMineArena.count;
import static dev.aicompanion.gametest.CompanionMineArena.ended;
import static dev.aicompanion.gametest.CompanionMineArena.mountain;
import static dev.aicompanion.gametest.CompanionMineArena.rel;
import static dev.aicompanion.gametest.CompanionMineArena.report;
import static dev.aicompanion.gametest.CompanionMineArena.rig;
import static dev.aicompanion.gametest.CompanionMineArena.start;

/**
 * Hostile mobs in the mine, live (nothing is switched off with NoAI and a stuck creeper is not taken away): he fights them with the sword
 * in the hand and goes on with the order. A creeper is struck with the sword too; when its fuse is lit he steps back, and he does not
 * come back to it until the fuse is out.
 */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineFightTests {
    private static final int SHELF_DROP = 3, SHELF_GAP = 4, SHELF_HIT_AT = 40, SHELF_ZOMBIE_HEALTH = 11;
    private static final int CHAMBER_REACH = 13, CHAMBER_HEIGHT = 4, OPEN_CAVE_STAND = 3, KILL_TICKS = 1200, FUSE_TICKS = 200, CRAFT_TICKS = 1200, BRIDGE_FROM = 4, BRIDGE_TO = 10, BRIDGE_SIDE = 5, CAVERN_DEPTH = 7, PASSAGE_LENGTH = 4, PASSAGE_OFFSET = 2, PASSAGE_HEIGHT = 2, STRIKE_TICKS = 60, FAR_STAND = 10;
    private static final float SURVIVES = 1, RETREAT_HEALTH = 8, BLOW_ABOVE_RETREAT = 10, WEAK_ZOMBIE_BLOW = 1, CLIMB_HEALTH = 4, WOUNDED = 5;
    private static final double THROWN = 0.6, SAFE_GAP = 4;
    private static final int BLOW_GAP = 20, ANSWER_TICKS = 40, ANSWERS = 2, DROP_STEP = 12, UNARMED_TICKS = 100, MEAL_WINDOW = 40, BREAD = 8, PILLAR_AT = 6, PILLAR_HEIGHT = 5, TALL_CHAMBER = 9, HANGING_TICKS = 200, ARROW_EVERY = 60;

    private CompanionMineFightTests() { }

    /** Two zombies come down the one-wide stairs at him, one behind another, while he digs at the foot: both die, he lives and the order ends with the diamond. */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void twoZombiesOnTheStairsAreKilledAndTheOrderGoesOn(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = armed(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        int foot = stairs.size() - 1;
        place(c, stairs.get(foot - 3));
        List<Zombie> zombies = new ArrayList<>();
        for (int k : new int[]{8, 11}) zombies.add(zombie(h, stairs.get(foot - k)));
        long id = start(h, c, 1);
        float[] lowest = {c.getMaxHealth()};
        h.onEachTick(() -> {
            lowest[0] = Math.min(lowest[0], c.getHealth());
            for (Zombie zombie : zombies) if (zombie.isAlive() && zombie.getTarget() == null && h.getTick() % 20 == 0) zombie.setTarget(c);
            long alive = zombies.stream().filter(Zombie::isAlive).count();
            String trace = " | zombies alive " + alive + ", lowest health " + lowest[0] + ", he at " + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (c.isWorking() || c.isGatheringTask()) return;
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(alive == 0, "The order ended with zombies alive" + trace);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "The order did not go on to the diamond: " + result.message() + trace);
            h.succeed();
        });
    }

    /**
     * Wounded on his stairs with no bread, two zombies come down the one-wide stairs at him, on his way up: the retreat cannot pass them, and
     * a zombie that has struck him is struck back (run ct68 20261031 descent: sixty ticks of the retreat with a zombie in reach and his swing
     * ready, four blows taken and none dealt). Each blow he takes is answered within {@link #ANSWER_TICKS}.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aZombieThatCatchesHimOnTheRetreatIsStruckBack(GameTestHelper h) { struckBackOnTheRetreat(h, WOUNDED, false, false); }

    /**
     * One zombie on his way up, and the order is the way up itself (the climb to the surface, which no retreat follows): at four health he
     * lets it go for the climb as anywhere, and when its blows keep landing it is struck back all the same (its blow is one health, so
     * that he lives to answer). He may get out past it as well.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aZombieThatCatchesHimOnTheClimbToTheSurfaceIsStruckBack(GameTestHelper h) { struckBackOnTheRetreat(h, CLIMB_HEALTH, false, true); }

    /**
     * The same two zombies, a wound a blow lighter, and a drop of his lies on the stairs above them, where he cannot go for it: the work
     * waits for the pickup, the fight has let the zombies go for the climb, and the climb must begin all the same, or he neither fights
     * nor leaves (series p 20261031 d1 descent: four zombies killed on the way down his stairs, their drops behind the rest of them, then
     * eighty ticks in a corner with «Подбираю добытые вещи», seven blows taken and none dealt).
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aDropBehindTheZombiesDoesNotKeepHimFromTheClimb(GameTestHelper h) { struckBackOnTheRetreat(h, BLOW_ABOVE_RETREAT, true, false); }

    private static void struckBackOnTheRetreat(GameTestHelper h, float wound, boolean dropBehind, boolean climb) {
        mountain(h);
        CompanionEntity c = armed(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        int foot = stairs.size() - 1;
        place(c, stairs.get(foot - 3));
        if (dropBehind) {
            BlockPos behind = stairs.get(foot - DROP_STEP);
            ItemEntity drop = new ItemEntity(h.getLevel(), behind.getX() + .5, behind.getY(), behind.getZ() + .5, new ItemStack(Items.ROTTEN_FLESH));
            drop.setDeltaMovement(Vec3.ZERO);
            drop.setNoPickUpDelay();
            h.getLevel().addFreshEntity(drop);
            c.claimDrops(List.of(drop));
        }
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(Items.BREAD)) c.setItem(slot, ItemStack.EMPTY);
        c.setHealth(wound);
        List<Zombie> zombies = new ArrayList<>();
        for (int k : climb ? new int[]{6} : new int[]{6, 8}) zombies.add(zombie(h, stairs.get(foot - k)));
        // A blow of one health: that he is struck back is what is asked, not who wins, and with three a blow two zombies end him in three blows.
        if (!dropBehind) for (Zombie zombie : zombies) zombie.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(WEAK_ZOMBIE_BLOW);
        if (climb) h.assertTrue(c.startWork("dig_for_diamonds", "surface", 1).success(), "The climb was not accepted: " + c.getLastTaskResult());
        else start(h, c, 1);
        float[] health = {c.getHealth()}, theirs = {0};
        long[] unanswered = {-1};
        int[] answers = {0};
        h.onEachTick(() -> {
            for (Zombie zombie : zombies) if (zombie.isAlive() && zombie.getTarget() == null) zombie.setTarget(c);
            float left = 0;
            for (Zombie zombie : zombies) left += zombie.isAlive() ? zombie.getHealth() : 0;
            if (c.getHealth() < health[0] && unanswered[0] < 0) unanswered[0] = h.getTick();
            if (left < theirs[0] && unanswered[0] >= 0) { unanswered[0] = -1; answers[0]++; }
            health[0] = c.getHealth();
            theirs[0] = left;
            String trace = " | stage " + c.expeditionStage() + ", answered " + answers[0] + ", zombies' health " + left + ", he at " + rel(h, c.blockPosition()).toShortString()
                    + ", health " + c.getHealth() + " | " + report(c);
            // Both of them dead is the end as well: with a sword that reaches farther than their arms he may take no second blow at all.
            if (answers[0] >= ANSWERS || left == 0) { h.succeed(); return; }
            h.assertTrue(c.isAlive(), "Died before he struck back" + trace);
            if (climb && !c.isWorking() && !c.isGatheringTask()) { h.succeed(); return; }
            h.assertTrue(unanswered[0] < 0 || h.getTick() - unanswered[0] <= ANSWER_TICKS, "A blow he took was not answered in " + ANSWER_TICKS + " ticks" + trace);
        });
    }

    /** A creeper crosses a wide chamber at the foot of the stairs towards him: it dies of the sword and he lives (an exploded one is removed, a struck one is dead). */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aCreeperInAnOpenCaveIsKilledWithTheSword(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = armed(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        BlockPos foot = chamber(h, stairs);
        place(c, foot.offset(OPEN_CAVE_STAND, 0, 0));
        Creeper creeper = creeper(h, foot.offset(CHAMBER_REACH, 0, 0));
        start(h, c, 1);
        long begun = h.getTick();
        h.onEachTick(() -> {
            String trace = " | creeper " + (creeper.isDeadOrDying() ? "killed" : creeper.isAlive() ? "alive, health " + creeper.getHealth() + ", fuse " + creeper.getSwellDir() : "gone") + ", he at "
                    + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (creeper.isDeadOrDying()) { h.assertTrue(c.getHealth() >= SURVIVES, "Too weak" + trace); h.succeed(); return; }
            h.assertTrue(creeper.isAlive(), "The creeper blew up, it was not struck" + trace);
            h.assertTrue(h.getTick() - begun < KILL_TICKS, "The creeper was not killed in time" + trace);
        });
    }

    /** The fuse is lit and cannot be put out (flint and steel): he is beside it, and gets away beyond the blast in time. */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aLitFuseSendsHimBackAndHeLives(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = armed(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        BlockPos foot = chamber(h, stairs);
        place(c, foot.offset(OPEN_CAVE_STAND, 0, 0));
        Creeper creeper = creeper(h, foot.offset(OPEN_CAVE_STAND + 2, 0, 0));
        creeper.ignite();
        start(h, c, 1);
        long begun = h.getTick();
        h.onEachTick(() -> {
            String trace = " | creeper " + (creeper.isAlive() ? "alive" : "gone") + ", he at " + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (creeper.isAlive() && h.getTick() - begun < FUSE_TICKS) return;
            h.assertTrue(!creeper.isAlive(), "The creeper never blew" + trace);
            h.assertTrue(c.getHealth() >= c.getMaxHealth() - 1, "He was hurt by the blast" + trace);
            h.succeed();
        });
    }

    /** A sword is not in the bag, stone and sticks are: a real stone sword is made at a bench, as the pickaxe is, and the bench comes up again. */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void makesAStoneSwordFromStoneAndSticksWhenHeHasNone(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        c.setItem(6, new ItemStack(Items.STICK, 6));
        c.setItem(7, new ItemStack(Items.CRAFTING_TABLE));
        carveMine(h, c, 0);
        start(h, c, 1);
        long begun = h.getTick();
        h.onEachTick(() -> {
            h.assertTrue(c.isAlive(), "Died | " + report(c));
            if (count(c, Items.STONE_SWORD) >= 1) h.succeed();
            h.assertTrue(h.getTick() - begun < CRAFT_TICKS, "No stone sword was made | " + report(c));
        });
    }

    /** The stairs cross a wide cavern on single blocks and three creepers wait on its floor, deeper than a fall he takes lightly: they are not foes, he walks on down and does not step off after them. */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void creepersOnTheFloorOfACavernUnderTheStairsAreNotDivedAfter(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = armed(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        BlockPos top = stairs.get(BRIDGE_FROM), bottom = stairs.get(BRIDGE_TO);
        Set<BlockPos> cavern = new HashSet<>();
        for (int x = top.getX() - BRIDGE_SIDE; x <= top.getX() + BRIDGE_SIDE; x++) for (int z = bottom.getZ(); z <= top.getZ() + 1; z++) for (int y = bottom.getY() - CAVERN_DEPTH; y <= top.getY() + 2; y++) {
            BlockPos cell = new BlockPos(x, y, z);
            h.getLevel().setBlock(cell, Blocks.AIR.defaultBlockState(), 2 | 16);
            cavern.add(cell);
        }
        for (int k = BRIDGE_FROM; k <= BRIDGE_TO; k++) h.getLevel().setBlock(stairs.get(k).below(), Blocks.COBBLESTONE.defaultBlockState(), 2 | 16);
        place(c, stairs.get(BRIDGE_FROM));
        List<Creeper> creepers = new ArrayList<>();
        for (int dz : new int[]{0, 1, 2}) creepers.add(creeper(h, new BlockPos(top.getX() - 2, bottom.getY() - CAVERN_DEPTH, top.getZ() - BRIDGE_FROM / 2 - dz)));
        long id = start(h, c, 1);
        h.onEachTick(() -> {
            String trace = " | creepers alive " + creepers.stream().filter(Creeper::isAlive).count() + ", he at " + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + ", target " + c.getTarget() + ", ground " + c.onGround() + ", tick " + h.getTick() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(c.getBlockY() > bottom.getY() || c.getBlockZ() <= bottom.getZ(), "He stepped off the bridge after the creepers" + trace);
            if (c.isWorking() || c.isGatheringTask()) return;
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "The order did not go on to the diamond: " + result.message() + trace);
            h.succeed();
        });
    }

    /**
     * A zombie on the same shelf, no way to it or from it (hurt, it takes no fall of more than three blocks), and then a floor joins the shelf to his
     * bridge and it comes for him: it is his foe again and is killed. No way to a foe is not for ever.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aZombieAcrossAGapIsFoughtOnceAFloorLeadsToIt(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = armed(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        List<BlockPos> shelf = shelfAcrossAGap(h, c, stairs);
        Zombie zombie = zombie(h, shelf.get(0));
        zombie.setHealth(SHELF_ZOMBIE_HEALTH);
        start(h, c, 1);
        h.onEachTick(() -> {
            if (h.getTick() < SHELF_HIT_AT) h.assertTrue(zombie.getBlockY() == shelf.get(0).getY(), "The zombie left its shelf before a floor led from it | " + report(c));
            if (h.getTick() == SHELF_HIT_AT) for (int k = 1; k < SHELF_GAP; k++) h.getLevel().setBlock(stairs.get(BRIDGE_FROM + SHELF_DROP).below().west(k), Blocks.COBBLESTONE.defaultBlockState(), 2 | 16);
            h.assertTrue(c.isAlive(), "Died | " + report(c));
            if (!zombie.isAlive()) h.succeed();
            h.assertTrue(h.getTick() <= SHELF_HIT_AT + KILL_TICKS, "The zombie that came over the floor was not killed, it at " + rel(h, zombie.blockPosition()).toShortString() + ", he at " + rel(h, c.blockPosition()).toShortString() + ", target " + c.getTarget() + " | " + report(c));
        });
    }

    /**
     * The cavern of {@link #creepersOnTheFloorOfACavernUnderTheStairsAreNotDivedAfter} with his bridge across it, him at its top, and a shelf two
     * blocks wide across a gap west of the bridge, {@link #SHELF_DROP} lower than its top; the stands of the shelf, those by the gap first.
     */
    private static List<BlockPos> shelfAcrossAGap(GameTestHelper h, CompanionEntity c, List<BlockPos> stairs) {
        BlockPos top = stairs.get(BRIDGE_FROM), bottom = stairs.get(BRIDGE_TO);
        for (int x = top.getX() - BRIDGE_SIDE; x <= top.getX() + BRIDGE_SIDE; x++) for (int z = bottom.getZ(); z <= top.getZ() + 1; z++) for (int y = bottom.getY() - CAVERN_DEPTH; y <= top.getY() + 2; y++) {
            h.getLevel().setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        for (int k = BRIDGE_FROM; k <= BRIDGE_TO; k++) h.getLevel().setBlock(stairs.get(k).below(), Blocks.COBBLESTONE.defaultBlockState(), 2 | 16);
        place(c, top);
        List<BlockPos> stands = new ArrayList<>();
        for (int dx = 0; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos stand = stairs.get(BRIDGE_FROM + SHELF_DROP).west(SHELF_GAP + dx).north(dz);
            h.getLevel().setBlock(stand.below(), Blocks.STONE.defaultBlockState(), 2 | 16);
            stands.add(stand);
        }
        return stands;
    }

    /** It stands where it is, as the creepers of the run did: with a target a mob takes a fall of twelve blocks, and the floor of the cavern is ten under the shelf. */
    private static <M extends Mob> M still(M mob) {
        mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        return mob;
    }

    /**
     * The breach of a cave is being sealed and he is on the cave side, in a passage one block wide, with two zombies that wake just then and
     * the stone sword in the bag: the sealing does not take the turn from the fight, the sword is in his hand and a zombie is struck within three seconds.
     * (Winning it is not asked: a stone sword and bare skin cost about nine health a zombie.)
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void zombiesOnHimAreStruckAtOnceWhileTheCaveIsBeingSealed(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        BlockPos foot = stairs.get(stairs.size() - 1);
        for (int dx = -PASSAGE_LENGTH; dx <= 1; dx++) for (int dy = 0; dy < PASSAGE_HEIGHT; dy++) {
            h.getLevel().setBlock(foot.offset(dx, dy, -PASSAGE_OFFSET), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        place(c, stairs.get(stairs.size() - 3));
        List<Zombie> zombies = List.of(zombie(h, foot.offset(-3, 0, -PASSAGE_OFFSET)), zombie(h, foot.offset(-4, 0, -PASSAGE_OFFSET)));
        for (Zombie zombie : zombies) zombie.setNoAi(true);
        long begun = h.getTick();
        long[] sealing = {-1};
        start(h, c, 1);
        h.onEachTick(() -> {
            String phase = c.expeditionCavePhase();
            if (sealing[0] < 0 && phase.equals("SEAL")) {
                sealing[0] = h.getTick();
                place(c, foot.offset(0, 0, -PASSAGE_OFFSET));
                for (Zombie zombie : zombies) zombie.setNoAi(false);
            }
            for (Zombie zombie : zombies) if (zombie.isAlive() && zombie.getTarget() == null && h.getTick() % 20 == 0) zombie.setTarget(c);
            float struck = 0;
            for (Zombie zombie : zombies) struck += zombie.getMaxHealth() - zombie.getHealth();
            String trace = " | zombies struck for " + struck + ", cave phase " + phase + ", he at " + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth()
                    + ", hand " + c.getMainHandItem().getItem() + ", target " + c.getTarget() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (sealing[0] < 0) { h.assertTrue(h.getTick() - begun < KILL_TICKS, "SETUP: the cave was never sealed" + trace); return; }
            if (struck > 0 && c.getMainHandItem().is(Items.STONE_SWORD)) h.succeed();
            h.assertTrue(h.getTick() - sealing[0] < STRIKE_TICKS, "No zombie was struck with the sword while the cave was being sealed" + trace);
        });
    }

    /**
     * Two zombies are on him at once and a skeleton hits him from afar, the stone sword is all he has: both zombies die and he lives,
     * whoever hit him last. The arrows are dealt by the test, one health every three seconds, and the zombies' blows are one health: how
     * much a fight costs him is the luck of the fight (CompanionFootworkTests and CompanionCrowdFightTests hold that), not this scene's.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void twoZombiesOnHimAreKilledUnderASkeletonsArrowsAndHeLives(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        BlockPos foot = chamber(h, stairs);
        place(c, foot.offset(OPEN_CAVE_STAND, 0, 0));
        Skeleton skeleton = still(skeleton(h, foot.offset(CHAMBER_REACH - 1, 0, 0)));
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        List<Mob> foes = List.of(zombie(h, foot.offset(OPEN_CAVE_STAND + 1, 0, 0)), zombie(h, foot.offset(OPEN_CAVE_STAND, 0, 1)), skeleton);
        for (Mob foe : foes) if (foe instanceof Zombie) foe.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(WEAK_ZOMBIE_BLOW);
        long begun = h.getTick();
        float[] lowest = {c.getMaxHealth()};
        start(h, c, 1);
        h.onEachTick(() -> {
            lowest[0] = Math.min(lowest[0], c.getHealth());
            for (Mob foe : foes) if (foe.isAlive() && foe.getTarget() == null) foe.setTarget(c);
            if ((h.getTick() - begun) % ARROW_EVERY == ARROW_EVERY - 1 && skeleton.isAlive()) c.hurt(h.getLevel().damageSources().mobAttack(skeleton), 1);
            String trace = " | foes alive " + foes.stream().filter(Mob::isAlive).count() + ", lowest health " + lowest[0] + ", he at " + rel(h, c.blockPosition()).toShortString()
                    + ", health " + c.getHealth() + ", target " + c.getTarget() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (foes.stream().noneMatch(foe -> foe instanceof Zombie && foe.isAlive())) { h.succeed(); return; }
            h.assertTrue(h.getTick() - begun < KILL_TICKS, "The zombies were not killed in time" + trace);
        });
    }

    /**
     * In his mine a blow at a run throws a zombie back as a running player's does (0.4 from any hit, 0.5 more from the run); a blow from a
     * standstill only by the hit's own 0.4, as a player's. With no expedition under way a blow at a run is as it was: the hit's own push.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void hisBlowThrowsAZombieBackFartherOnlyAtARunInHisMine(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        place(c, foot.offset(OPEN_CAVE_STAND, 0, 0));
        Zombie zombie = zombie(h, foot.offset(OPEN_CAVE_STAND + 1, 0, 0));
        zombie.setNoAi(true);
        double[] thrown = new double[2];
        h.runAfterDelay(2, () -> thrown[0] = blow(h, c, zombie, true));
        // Past the zombie's moment of invulnerability after each blow.
        h.runAfterDelay(2 + BLOW_GAP, () -> { start(h, c, 1); thrown[1] = blow(h, c, zombie, false); });
        h.runAfterDelay(2 + 2 * BLOW_GAP, () -> {
            double running = blow(h, c, zombie, true);
            h.assertTrue(thrown[0] < THROWN, "With no expedition a blow at a run threw the zombie back by " + thrown[0]);
            h.assertTrue(thrown[1] < THROWN, "A blow from a standstill threw the zombie back by " + thrown[1]);
            h.assertTrue(running >= THROWN, "In his mine a blow at a run threw the zombie back by only " + running);
            h.succeed();
        });
    }

    /** One blow at the zombie put back beside him; how far it is thrown. */
    private static double blow(GameTestHelper h, CompanionEntity c, Zombie zombie, boolean running) {
        zombie.teleportTo(c.getX() + 1, c.getY(), c.getZ());
        zombie.setDeltaMovement(Vec3.ZERO);
        c.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, zombie.position());
        c.setSprinting(running);
        h.assertTrue(c.doHurtTarget(zombie), "The blow did not land");
        c.setSprinting(false);
        return zombie.getDeltaMovement().horizontalDistance();
    }

    /** Eight health, a zombie on him, bread in the bag: the meal is eaten under the zombie's blows, not after it (a loaf is five health in 32 ticks, a zombie dies in 65). */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void woundedInAFightHeEatsWithTheZombieOnHim(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        place(c, foot.offset(FAR_STAND, 0, 0));
        c.setHealth(RETREAT_HEALTH);
        Zombie zombie = zombie(h, foot.offset(FAR_STAND + 1, 0, 0));
        zombie.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(WEAK_ZOMBIE_BLOW);
        long begun = h.getTick();
        start(h, c, 1);
        h.onEachTick(() -> {
            if (zombie.isAlive() && zombie.getTarget() == null) zombie.setTarget(c);
            String trace = " | bread " + count(c, Items.BREAD) + ", zombie " + (zombie.isAlive() ? "health " + zombie.getHealth() : "dead") + ", he at " + rel(h, c.blockPosition()).toShortString()
                    + ", health " + c.getHealth() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (h.getTick() - begun < MEAL_WINDOW) return;
            h.assertTrue(count(c, Items.BREAD) < BREAD, "No bread was eaten" + trace);
            h.assertTrue(c.getHealth() > RETREAT_HEALTH, "The meal did not bring him above where he began" + trace);
            h.succeed();
        });
    }

    /** No sword or axe in the bag and an expedition under way: a zombie on him is no fist fight. He does not strike it once, and goes off from it at least four blocks. */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void withNoWeaponHeStepsOffAZombieOnHimAndDoesNotPunchIt(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = rig(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        place(c, foot.offset(FAR_STAND, 0, 0));
        Zombie zombie = zombie(h, foot.offset(FAR_STAND + 1, 0, 0));
        long begun = h.getTick();
        double[] widest = {0};
        start(h, c, 1);
        h.onEachTick(() -> {
            if (zombie.isAlive() && zombie.getTarget() == null) zombie.setTarget(c);
            widest[0] = Math.max(widest[0], Math.sqrt(c.distanceToSqr(zombie)));
            String trace = " | zombie health " + zombie.getHealth() + ", widest gap " + String.format("%.1f", widest[0]) + ", he at " + rel(h, c.blockPosition()).toShortString()
                    + ", health " + c.getHealth() + ", target " + c.getTarget() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(zombie.getHealth() >= zombie.getMaxHealth(), "He struck the zombie with his bare hand" + trace);
            if (h.getTick() - begun < UNARMED_TICKS) return;
            h.assertTrue(widest[0] >= SAFE_GAP, "He did not get away from the zombie" + trace);
            h.succeed();
        });
    }

    /**
     * A zombie on a pillar in a chamber at the foot of the stairs: in sight, near, and out of his reach. He does not stand facing it for
     * the rest of the order; the foe he cannot get at is let go and the order goes on to the diamond.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void doesNotStandForeverFacingAFoeHeCannotReach(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = armed(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0), TALL_CHAMBER);
        BlockPos pillar = foot.offset(PILLAR_AT, 0, 0);
        for (int dy = 0; dy < PILLAR_HEIGHT; dy++) h.getLevel().setBlock(pillar.above(dy), Blocks.STONE.defaultBlockState(), 2 | 16);
        place(c, foot.offset(OPEN_CAVE_STAND, 0, 0));
        Zombie zombie = still(zombie(h, pillar.above(PILLAR_HEIGHT)));
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "He did not get past the foe he cannot reach: " + result.message() + " | zombie " + (zombie.isAlive() ? "alive" : "gone") + " | " + report(c));
        });
    }

    /** A skeleton shoots him from across the chamber at the foot of the stairs and the stone sword is all he has: he walks up to it and kills it, and lives. */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aSkeletonThatShootsHimAcrossAChamberIsKilled(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        place(c, foot.offset(OPEN_CAVE_STAND, 0, 0));
        Skeleton skeleton = skeleton(h, foot.offset(CHAMBER_REACH - 1, 0, 0));
        long begun = h.getTick();
        start(h, c, 1);
        h.onEachTick(() -> {
            if (!skeleton.isAlive()) { h.succeed(); return; }
            if (skeleton.getTarget() == null) skeleton.setTarget(c);
            String trace = " | skeleton health " + skeleton.getHealth() + " at " + rel(h, skeleton.blockPosition()).toShortString() + ", in his sight " + c.hasLineOfSight(skeleton) + ", he at "
                    + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + ", target " + c.getTarget() + ", stage " + c.expeditionStage() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(h.getTick() - begun < KILL_TICKS, "The skeleton that shoots him was not killed in time" + trace);
        });
    }

    /**
     * «Стой» in the middle of a fight on his stairs: the order is over at once, the sword and the pickaxes are all still his, and the zombie
     * is not left his target for good: in ten seconds it is dead or let go.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void stoppedInTheMiddleOfAFightHeKeepsHisThingsAndNoTargetIsLeftHanging(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = armed(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        int foot = stairs.size() - 1;
        place(c, stairs.get(foot - 3));
        Zombie zombie = zombie(h, stairs.get(foot - 6));
        start(h, c, 1);
        long[] stopped = {-1};
        h.onEachTick(() -> {
            if (zombie.isAlive() && zombie.getTarget() == null) zombie.setTarget(c);
            String trace = " | zombie health " + zombie.getHealth() + ", target " + c.getTarget() + ", he at " + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (stopped[0] < 0) {
                if (zombie.getHealth() >= zombie.getMaxHealth()) return;
                c.stopGatheringForPlayer();
                stopped[0] = h.getTick();
                h.assertTrue(!c.isWorking() && !c.isGatheringTask(), "The order goes on after the stop" + trace);
            }
            h.assertTrue(c.expeditionStage().isEmpty(), "The expedition is under way after the stop: stage " + c.expeditionStage() + trace);
            h.assertTrue(count(c, Items.STONE_SWORD) == 1 && count(c, Items.STONE_PICKAXE) == 2 && count(c, Items.IRON_PICKAXE) == 1, "A sword or a pickaxe of his is gone" + trace);
            if (h.getTick() - stopped[0] < HANGING_TICKS) return;
            h.assertTrue(!zombie.isAlive() || c.getTarget() == null, "The zombie is still his target ten seconds after the stop" + trace);
            h.succeed();
        });
    }

    static CompanionEntity armed(GameTestHelper h) {
        CompanionEntity c = rig(h);
        c.setItem(6, new ItemStack(Items.STONE_SWORD));
        return c;
    }

    /** A chamber at the foot of the stairs, wide enough to cross; returns the foot. */
    static BlockPos chamber(GameTestHelper h, List<BlockPos> stairs) { return chamber(h, stairs, CHAMBER_HEIGHT); }

    static BlockPos chamber(GameTestHelper h, List<BlockPos> stairs, int height) {
        BlockPos foot = stairs.get(stairs.size() - 1);
        for (int dx = 0; dx <= CHAMBER_REACH; dx++) for (int dz = -CHAMBER_REACH / 2; dz <= CHAMBER_REACH / 2; dz++) for (int dy = 0; dy < height; dy++) {
            h.getLevel().setBlock(foot.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        return foot;
    }

    static void place(CompanionEntity c, BlockPos stand) {
        c.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
    }

    static Creeper creeper(GameTestHelper h, BlockPos at) {
        Creeper creeper = h.spawn(EntityType.CREEPER, rel(h, at));
        creeper.setPersistenceRequired();
        return creeper;
    }

    static Zombie zombie(GameTestHelper h, BlockPos at) {
        Zombie zombie = h.spawn(EntityType.ZOMBIE, rel(h, at));
        zombie.setPersistenceRequired();
        zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION));
        return zombie;
    }

    static Skeleton skeleton(GameTestHelper h, BlockPos at) {
        Skeleton skeleton = h.spawn(EntityType.SKELETON, rel(h, at));
        skeleton.setPersistenceRequired();
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        skeleton.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION));
        return skeleton;
    }
}

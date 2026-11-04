package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionArrowFlight;
import dev.aicompanion.entity.CompanionEndCrystals;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The crystals on the End's pillars, which Oleg's arrows break while the dragon lives: the dragon itself takes no harm from anyone but a
 * player (EnderDragon.hurt), the crystals heal it. A pillar of obsidian with a crystal on bedrock at its top, as the game builds them.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionEndCrystalTests {
    private static final int GROUND = 0, TOP = 14;

    /** The dragons hang over the arenas, out of the reach of the structure's own clearing: no other batch is to meet one. */
    @AfterBatch(batch = "end_crystals")
    public static void afterCrystals(ServerLevel level) {
        CompanionFixtureCleanup.afterAtomicTests(level);
        for (EnderDragon dragon : level.getDragons()) dragon.discard();
    }

    @AfterBatch(batch = "end_crystals_quiet")
    public static void afterQuiet(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    /** The fight is on and the crystal in the open: his arrow breaks it, a real one out of his bag. */
    @GameTest(template = "tall_tree", timeoutTicks = 600, batch = "end_crystals")
    public static void whileTheDragonLivesHeShootsTheCrystalOffItsPillar(GameTestHelper h) {
        arena(h);
        EndCrystal crystal = onPillar(h, 1);
        dragon(h);
        CompanionEntity companion = archer(h, 16, 4);
        h.succeedWhen(() -> {
            h.assertTrue(crystal.isRemoved(), "The crystal still stands; arrows left " + count(companion, Items.ARROW));
            h.assertTrue(count(companion, Items.ARROW) < 16, "Broken with no arrow of his spent");
            h.assertTrue(said(companion, "Сбиваю кристаллы.") == 1 && said(companion, "Кристаллы на колоннах сбил.") == 1,
                    "Said " + CompanionEndCrystals.said(companion));
        });
    }

    /** The dragon struck him: a foe of a kind no arrow of his can hurt, so no arrow goes to it and it is not his target. */
    @GameTest(template = "tall_tree", timeoutTicks = 200, batch = "end_crystals")
    public static void theDragonThatStrikesHimIsNoTargetOfHis(GameTestHelper h) {
        arena(h);
        EnderDragon dragon = dragon(h, GROUND + 8, 12.5);
        CompanionEntity companion = archer(h, 16, 4);
        // Not at his first tick: the game's «hit back» goal takes a blow dealt at tick 0 for no new one (HurtByTargetGoal.timestamp).
        h.runAtTickTime(5, () -> companion.hurt(h.getLevel().damageSources().mobAttack(dragon), 1F));
        h.runAtTickTime(120, () -> h.succeedIf(() -> {
            h.assertTrue(companion.getTarget() != dragon, "The dragon is his target");
            h.assertTrue(count(companion, Items.ARROW) == 16, "Arrows sent at the dragon: " + (16 - count(companion, Items.ARROW)));
        }));
    }

    /** The player stands by the crystal, inside its blast: not one arrow until he is away from it, then it is shot. */
    @GameTest(template = "tall_tree", timeoutTicks = 700, batch = "end_crystals")
    public static void notAnArrowWhileThePlayerStandsByTheCrystal(GameTestHelper h) {
        arena(h);
        EndCrystal crystal = onPillar(h, 1);
        dragon(h);
        CompanionEntity companion = archer(h, 16, 4);
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        h.setBlock(new BlockPos(21, TOP + 1, 26), Blocks.BEDROCK);
        Vec3 beside = h.absoluteVec(new Vec3(21.5, TOP + 2, 26.5));
        owner.teleportTo(beside.x, beside.y, beside.z);
        h.runAtTickTime(200, () -> {
            h.assertTrue(crystal.isAlive(), "Shot with the player five blocks from it");
            h.assertTrue(count(companion, Items.ARROW) == 16, "Arrows sent while the player stood by it");
            Vec3 away = h.absoluteVec(new Vec3(13.5, GROUND + 1, 4.5));
            owner.teleportTo(away.x, away.y, away.z);
        });
        h.runAfterDelay(201, () -> h.succeedWhen(() -> h.assertTrue(crystal.isRemoved(), "Not shot once the player was away")));
    }

    /** Iron bars round it, as the game guards two of the ten: no arrow gets in, so none is spent; he tells the player once. */
    @GameTest(template = "tall_tree", timeoutTicks = 300, batch = "end_crystals")
    public static void aCagedCrystalIsLeftToThePlayerAndHeSaysSo(GameTestHelper h) {
        arena(h);
        EndCrystal crystal = onPillar(h, 1);
        for (int x = 14; x <= 18; x++) for (int z = 24; z <= 28; z++) for (int y = TOP + 1; y <= TOP + 4; y++)
            if (x == 14 || x == 18 || z == 24 || z == 28 || y == TOP + 4) h.setBlock(new BlockPos(x, y, z), Blocks.IRON_BARS);
        dragon(h);
        CompanionEntity companion = archer(h, 16, 4);
        h.runAtTickTime(240, () -> h.succeedIf(() -> {
            h.assertTrue(crystal.isAlive() && count(companion, Items.ARROW) == 16, "Arrows at a caged crystal: " + (16 - count(companion, Items.ARROW)));
            h.assertTrue(said(companion, "Кристалл в клетке — его только ты: залезть и разбить.") == 1, "Said " + CompanionEndCrystals.said(companion));
        }));
    }

    /** «Стой»: under a roof no arrow gets out to the crystal, and he does not step out from under it either. */
    @GameTest(template = "tall_tree", timeoutTicks = 300, batch = "end_crystals")
    public static void toldToStayHeShootsOnlyFromWhereHeStands(GameTestHelper h) {
        arena(h);
        EndCrystal crystal = onPillar(h, 1);
        roof(h);
        dragon(h);
        CompanionEntity companion = archer(h, 16, 4);
        companion.setFollowing(false);
        Vec3 start = companion.position();
        h.runAtTickTime(240, () -> h.succeedIf(() -> {
            h.assertTrue(crystal.isAlive() && count(companion, Items.ARROW) == 16, "Shot from under the roof");
            h.assertTrue(companion.position().distanceTo(start) < 1.5, "Told to stay, he went " + companion.position().distanceTo(start));
        }));
    }

    /** Following the player, he steps out from under the roof to where an arrow gets to the crystal, and shoots it. */
    @GameTest(template = "tall_tree", timeoutTicks = 900, batch = "end_crystals")
    public static void followingHeGoesWhereAnArrowGetsToTheCrystal(GameTestHelper h) {
        arena(h);
        EndCrystal crystal = onPillar(h, 1);
        roof(h);
        dragon(h);
        CompanionEntity companion = archer(h, 16, 4);
        h.succeedWhen(() -> h.assertTrue(crystal.isRemoved(), "Still under the roof at " + companion.position()));
    }

    /** No bow: nothing to shoot with, and he says so once, not every tick of the fight. */
    @GameTest(template = "tall_tree", timeoutTicks = 200, batch = "end_crystals")
    public static void withoutABowHeSaysOnceThatHeHasNothingToShootWith(GameTestHelper h) {
        arena(h);
        onPillar(h, 1);
        dragon(h);
        CompanionEntity companion = archer(h, 16, 4);
        companion.setItem(0, ItemStack.EMPTY);
        h.runAtTickTime(160, () -> h.succeedIf(() -> h.assertTrue(said(companion, "Нечем сбивать кристаллы: нужны лук и стрелы.") == 1,
                "Said " + CompanionEndCrystals.said(companion))));
    }

    /** No dragon: the crystals are no business of his. A batch of its own, so no dragon of another test hangs near. */
    @GameTest(template = "tall_tree", timeoutTicks = 200, batch = "end_crystals_quiet")
    public static void withNoDragonTheCrystalsAreLeftAlone(GameTestHelper h) {
        arena(h);
        EndCrystal crystal = onPillar(h, 1);
        CompanionEntity companion = archer(h, 16, 4);
        h.runAtTickTime(160, () -> h.succeedIf(() -> h.assertTrue(crystal.isAlive() && count(companion, Items.ARROW) == 16,
                "Shot with no dragon about")));
    }

    /** A crystal the player set down himself (no base: EndCrystalItem) is his, to bring the dragon back: never shot. */
    @GameTest(template = "tall_tree", timeoutTicks = 200, batch = "end_crystals")
    public static void theCrystalThePlayerSetDownIsLeftAlone(GameTestHelper h) {
        arena(h);
        EndCrystal crystal = onPillar(h, 1);
        crystal.setShowBottom(false);
        dragon(h);
        CompanionEntity companion = archer(h, 16, 4);
        h.runAtTickTime(160, () -> h.succeedIf(() -> h.assertTrue(crystal.isAlive() && count(companion, Items.ARROW) == 16,
                "Shot the player's own crystal")));
    }

    /** In the open a straight shot does it: the game's own arrow, sent the way the flight was worked out without any spread, breaks it. */
    @GameTest(template = "tall_tree", timeoutTicks = 200, batch = "end_crystals")
    public static void theWorkedOutFlightHitsACrystalInTheOpen(GameTestHelper h) {
        arena(h);
        EndCrystal crystal = onPillar(h, 1);
        shootTheWorkedOutFlight(h, crystal);
    }

    /** A wide pillar hides the crystal from below: only an arrow sent high over its edge comes down on it. */
    @GameTest(template = "tall_tree", timeoutTicks = 200, batch = "end_crystals")
    public static void overAWidePillarTheArrowGoesHighAndComesDownOnIt(GameTestHelper h) {
        arena(h);
        EndCrystal crystal = onPillar(h, 3);
        Vec3 from = h.absoluteVec(new Vec3(16.5, GROUND + 2.5, 4.5));
        Vec3 at = crystal.getBoundingBox().getCenter();
        h.assertTrue(h.getLevel().clip(new net.minecraft.world.level.ClipContext(from, at, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, crystal)).getType() == net.minecraft.world.phys.HitResult.Type.BLOCK,
                "The pillar does not hide the crystal: the test proves nothing");
        shootTheWorkedOutFlight(h, crystal);
    }

    /** Higher than any arrow climbs (some 66 blocks straight up): no flight at all, so he would not waste one. */
    @GameTest(template = "tall_tree", timeoutTicks = 100, batch = "end_crystals")
    public static void aCrystalOutOfAnyArrowsReachHasNoFlight(GameTestHelper h) {
        arena(h);
        Vec3 from = h.absoluteVec(new Vec3(16.5, GROUND + 2.5, 16.5));
        var high = net.minecraft.world.phys.AABB.ofSize(from.add(1, 75, 1), 2, 2, 2);
        h.assertTrue(CompanionArrowFlight.toward(h.getLevel(), from, high) == null, "A flight to a crystal 75 blocks up");
        h.succeed();
    }

    private static void shootTheWorkedOutFlight(GameTestHelper h, EndCrystal crystal) {
        Vec3 from = h.absoluteVec(new Vec3(16.5, GROUND + 2.5, 4.5));
        CompanionArrowFlight.Flight flight = CompanionArrowFlight.toward(h.getLevel(), from, crystal.getBoundingBox());
        h.assertTrue(flight != null, "No flight to the crystal");
        Arrow arrow = new Arrow(h.getLevel(), from.x, from.y, from.z, new ItemStack(Items.ARROW), null);
        Vec3 way = flight.aim().subtract(from);
        arrow.shoot(way.x, way.y, way.z, 3F, 0F);
        h.getLevel().addFreshEntity(arrow);
        h.succeedWhen(() -> h.assertTrue(crystal.isRemoved(), "The arrow missed: it is at " + arrow.position()));
    }

    /** A dragon without a mind high over the arena, out of the arrows' way: it neither flies nor breaks blocks (EnderDragon.aiStep). */
    private static EnderDragon dragon(GameTestHelper h) { return dragon(h, GROUND + 44, 16.5); }

    private static EnderDragon dragon(GameTestHelper h, int y, double z) {
        Vec3 at = h.absoluteVec(new Vec3(16.5, y, z));
        EnderDragon dragon = EntityType.ENDER_DRAGON.create(h.getLevel());
        dragon.setNoAi(true);
        dragon.moveTo(at.x, at.y, at.z, 0, 0);
        h.getLevel().addFreshEntity(dragon);
        return dragon;
    }

    /** Oleg with a bow and 16 arrows, following his player, who stands three blocks to the side. */
    private static CompanionEntity archer(GameTestHelper h, int x, int z) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), x, GROUND + 1, z);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(x - 3, GROUND + 1, z));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setItem(0, new ItemStack(Items.BOW));
        companion.setItem(1, new ItemStack(Items.ARROW, 16));
        return companion;
    }

    /** A roof three blocks over him from x 10 to 22 and z 0 to 10: every flight to the crystal from under it meets the roof. */
    private static void roof(GameTestHelper h) {
        for (int x = 10; x <= 22; x++) for (int z = 0; z <= 10; z++) h.setBlock(new BlockPos(x, GROUND + 4, z), Blocks.OBSIDIAN);
    }

    private static long said(CompanionEntity companion, String line) {
        return CompanionEndCrystals.said(companion).stream().filter(line::equals).count();
    }

    private static int count(CompanionEntity companion, Item item) {
        int count = companion.getItemBySlot(EquipmentSlot.MAINHAND).is(item) ? companion.getItemBySlot(EquipmentSlot.MAINHAND).getCount() : 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        return count;
    }

    /** Bedrock under everything and air over it, whatever the template held. */
    private static void arena(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y < 24; y++)
            h.setBlock(new BlockPos(x, y, z), y <= GROUND ? Blocks.BEDROCK : Blocks.AIR);
    }

    /** A pillar of obsidian round x 16, z 26, {@code radius} blocks from its middle, bedrock on top and a crystal on that. */
    private static EndCrystal onPillar(GameTestHelper h, int radius) {
        for (int x = 16 - radius; x <= 16 + radius; x++) for (int z = 26 - radius; z <= 26 + radius; z++)
            for (int y = GROUND + 1; y <= TOP; y++) h.setBlock(new BlockPos(x, y, z), Blocks.OBSIDIAN);
        h.setBlock(new BlockPos(16, TOP + 1, 26), Blocks.BEDROCK);
        Vec3 at = h.absoluteVec(new Vec3(16.5, TOP + 2, 26.5));
        EndCrystal crystal = new EndCrystal(h.getLevel(), at.x, at.y, at.z);
        h.getLevel().addFreshEntity(crystal);
        return crystal;
    }
}

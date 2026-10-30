package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The bow as a player has it: real arrows from the bag, wear on the bow, a sword for what comes close, and no arrow past someone
 * who is no enemy. The targets stand still on a pillar across open ground, so what kills them can only be an arrow.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionBowTests {
    private static final int GROUND = 5;

    @AfterBatch(batch = "bow")
    public static void afterBow(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    /** A zombie out of reach on a pillar: arrows kill it, each one gone from the bag, the bow worn, and an arrow lies there to be picked up. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200, batch = "bow")
    public static void aZombieOutOfReachIsShot(GameTestHelper h) {
        ground(h);
        Zombie zombie = onPillar(h, 16, 24);
        CompanionEntity companion = spawn(h, 16, 8);
        companion.setItem(0, new ItemStack(Items.BOW));
        companion.setItem(1, new ItemStack(Items.ARROW, 16));
        companion.setTarget(zombie);
        h.succeedWhen(() -> {
            h.assertTrue(!zombie.isAlive(), "Alive with " + zombie.getHealth() + ", arrows left " + count(companion, Items.ARROW));
            h.assertTrue(count(companion, Items.ARROW) < 16, "He killed it with no arrow spent");
            h.assertTrue(companion.getMainHandItem().is(Items.BOW) && companion.getMainHandItem().getDamageValue() == 16 - count(companion, Items.ARROW),
                    "Wear " + companion.getMainHandItem().getDamageValue() + " for " + (16 - count(companion, Items.ARROW)) + " arrows");
            h.assertTrue(arrows(h).stream().allMatch(arrow -> arrow.pickup == AbstractArrow.Pickup.ALLOWED), "An arrow that cannot be picked up");
        });
    }

    /** A bow and an empty quiver: nothing flies. */
    @GameTest(template = "tall_tree", timeoutTicks = 300, batch = "bow")
    public static void noArrowsNoShot(GameTestHelper h) {
        ground(h);
        Zombie zombie = onPillar(h, 16, 24);
        CompanionEntity companion = spawn(h, 16, 8);
        companion.setItem(0, new ItemStack(Items.BOW));
        companion.setTarget(zombie);
        h.runAtTickTime(120, () -> h.succeedIf(() -> {
            h.assertTrue(arrows(h).isEmpty(), "Something flew without an arrow");
            h.assertTrue(!companion.isUsingItem(), "He draws an empty bow");
        }));
    }

    /** The player on the line: the string stays drawn or he goes in, but no arrow leaves and the player is not hurt. */
    @GameTest(template = "tall_tree", timeoutTicks = 300, batch = "bow")
    public static void noArrowPastThePlayer(GameTestHelper h) {
        ground(h);
        Zombie zombie = onTheGround(h, 16, 24);
        CompanionEntity companion = spawn(h, 16, 8);
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        BlockPos between = h.absolutePos(new BlockPos(16, GROUND + 1, 13));
        owner.teleportTo(between.getX() + .5, between.getY(), between.getZ() + .5);
        companion.setItem(0, new ItemStack(Items.BOW));
        companion.setItem(1, new ItemStack(Items.ARROW, 16));
        companion.setTarget(zombie);
        h.runAtTickTime(100, () -> h.succeedIf(() -> {
            h.assertTrue(arrows(h).isEmpty() && count(companion, Items.ARROW) == 16, "An arrow left with the player on the line");
            h.assertTrue(owner.getHealth() == owner.getMaxHealth(), "The player was hurt");
        }));
    }

    /** A cow on the line is spared the same way. */
    @GameTest(template = "tall_tree", timeoutTicks = 300, batch = "bow")
    public static void noArrowPastACow(GameTestHelper h) {
        ground(h);
        Zombie zombie = onTheGround(h, 16, 24);
        Cow cow = h.spawn(EntityType.COW, 16, GROUND + 1, 16);
        cow.setNoAi(true);
        CompanionEntity companion = spawn(h, 16, 8);
        companion.setItem(0, new ItemStack(Items.BOW));
        companion.setItem(1, new ItemStack(Items.ARROW, 16));
        companion.setTarget(zombie);
        h.runAtTickTime(100, () -> h.succeedIf(() -> {
            h.assertTrue(arrows(h).isEmpty() && cow.getHealth() == cow.getMaxHealth(), "An arrow past the cow");
        }));
    }

    /** What stands next to him gets the sword from the bag, not an arrow. */
    @GameTest(template = "tall_tree", timeoutTicks = 400, batch = "bow")
    public static void anEnemyAtHandGetsTheSword(GameTestHelper h) {
        ground(h);
        CompanionEntity companion = spawn(h, 16, 16);
        companion.setItem(0, new ItemStack(Items.BOW));
        companion.setItem(1, new ItemStack(Items.ARROW, 16));
        companion.setItem(2, new ItemStack(Items.IRON_SWORD));
        Zombie zombie = h.spawn(EntityType.ZOMBIE, 16, GROUND + 1, 18);
        zombie.setNoAi(true);
        companion.setTarget(zombie);
        h.succeedWhen(() -> {
            h.assertTrue(!zombie.isAlive(), "Alive with " + zombie.getHealth() + ", in hand " + companion.getMainHandItem());
            h.assertTrue(count(companion, Items.ARROW) == 16 && arrows(h).isEmpty(), "He shot what stood next to him");
        });
    }

    /** A ghast near the player is taken up with a bow, and left alone without one. */
    @GameTest(template = "tall_tree", timeoutTicks = 1500, batch = "bow")
    public static void aGhastOverThePlayerIsShot(GameTestHelper h) {
        ground(h);
        CompanionEntity companion = spawn(h, 16, 8);
        companion.setItem(0, new ItemStack(Items.BOW));
        companion.setItem(1, new ItemStack(Items.ARROW, 32));
        Ghast ghast = h.spawn(EntityType.GHAST, 16, GROUND + 12, 16);
        ghast.setNoAi(true);
        h.succeedWhen(() -> h.assertTrue(!ghast.isAlive(), "Alive with " + ghast.getHealth() + ", target " + companion.getTarget() + ", arrows " + count(companion, Items.ARROW)));
    }

    @GameTest(template = "tall_tree", timeoutTicks = 300, batch = "bow")
    public static void aGhastIsNoTargetWithoutABow(GameTestHelper h) {
        ground(h);
        CompanionEntity companion = spawn(h, 16, 8);
        companion.setItem(0, new ItemStack(Items.IRON_SWORD));
        Ghast ghast = h.spawn(EntityType.GHAST, 16, GROUND + 12, 16);
        ghast.setNoAi(true);
        h.runAtTickTime(120, () -> h.succeedIf(() -> h.assertTrue(companion.getTarget() == null, "Target " + companion.getTarget())));
    }

    private static void ground(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= GROUND + 16; y++) {
            boolean border = x == 0 || x == 31 || z == 0 || z == 31;
            h.setBlock(new BlockPos(x, y, z), y <= GROUND ? Blocks.BEDROCK : border ? Blocks.BEDROCK : Blocks.AIR);
        }
    }

    /** Three blocks up, nothing leads there; no AI, so it neither walks off nor comes down to him. */
    private static Zombie onPillar(GameTestHelper h, int x, int z) {
        for (int y = GROUND + 1; y <= GROUND + 3; y++) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
        Zombie zombie = h.spawn(EntityType.ZOMBIE, x, GROUND + 4, z);
        zombie.setNoAi(true);
        return zombie;
    }

    private static Zombie onTheGround(GameTestHelper h, int x, int z) {
        Zombie zombie = h.spawn(EntityType.ZOMBIE, x, GROUND + 1, z);
        zombie.setNoAi(true);
        return zombie;
    }

    private static CompanionEntity spawn(GameTestHelper h, int x, int z) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), x, GROUND + 1, z);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(x - 3, GROUND + 1, z));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        return companion;
    }

    private static java.util.List<Arrow> arrows(GameTestHelper h) {
        // Corner to corner: an arena may be turned, and the next test's arrows fly next door.
        return h.getLevel().getEntitiesOfClass(Arrow.class, new AABB(net.minecraft.world.phys.Vec3.atLowerCornerOf(h.absolutePos(BlockPos.ZERO)),
                net.minecraft.world.phys.Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(31, 24, 31)))));
    }

    private static int count(CompanionEntity companion, Item item) {
        int count = companion.getItemBySlot(EquipmentSlot.MAINHAND).is(item) ? companion.getItemBySlot(EquipmentSlot.MAINHAND).getCount() : 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        return count;
    }
}

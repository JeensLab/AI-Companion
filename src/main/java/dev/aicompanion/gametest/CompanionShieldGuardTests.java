package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The shield against a creeper's blast on open ground, with no mine and no order: held up at a lit fuse turned towards it, worn down by the
 * blows it takes like a player's, and with none in the hand a lit fuse sends him back as before.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionShieldGuardTests {
    private static final int FUSE_TICKS = 200, RAISE_TICKS = 8, LONG_FUSE = 30000, IGNITE_AFTER = 10;
    private static final double STAYED = 1.5, FACING = 45, BEHIND = 90, LIT_AT = 2, UNSHIELDED_AT = 3;
    /** A blast at three blocks costs about 17 of 20 health to one who stands; a dash of a few blocks leaves most of it. */
    private static final float UNSHIELDED_LOSS = 8;

    private CompanionShieldGuardTests() { }

    /** The fuse is lit two blocks off, the shield is in the left hand: he does not run, the blast does not hurt him and the shield pays for it. */
    @GameTest(template = "tall_tree", timeoutTicks = 300)
    public static void onOpenGroundAShieldTakesTheBlastOfALitCreeper(GameTestHelper h) {
        CompanionEntity c = setup(h, true);
        Creeper creeper = creeper(h, LIT_AT);
        creeper.ignite();
        Vec3 stand = c.position();
        long begun = h.getTick();
        double[] drift = {0};
        boolean[] raised = {false};
        h.onEachTick(() -> {
            String trace = " | creeper " + (creeper.isAlive() ? "alive" : "gone") + ", health " + c.getHealth() + ", shield wear " + c.getOffhandItem().getDamageValue();
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (creeper.isAlive() && h.getTick() - begun < FUSE_TICKS) {
                drift[0] = Math.max(drift[0], c.position().distanceTo(stand));
                raised[0] |= c.isBlocking();
                return;
            }
            h.assertTrue(!creeper.isAlive(), "The creeper never blew" + trace);
            h.assertTrue(raised[0], "The shield was never up before the blast" + trace);
            h.assertTrue(drift[0] < STAYED, "He ran " + drift[0] + " blocks instead of holding the shield" + trace);
            h.assertTrue(c.getHealth() >= c.getMaxHealth(), "He was hurt by the blast" + trace);
            h.assertTrue(c.getOffhandItem().is(Items.SHIELD) && c.getOffhandItem().getDamageValue() > 0, "The shield took no wear" + trace);
            h.succeed();
        });
    }

    /** The creeper is behind his back when its fuse is lit: head and body turn to it, the shield is up before the blast and holds. */
    @GameTest(template = "tall_tree", timeoutTicks = 300)
    public static void onOpenGroundACreeperLitBehindHimIsFacedAndHeld(GameTestHelper h) {
        CompanionEntity c = setup(h, true);
        Creeper creeper = creeper(h, LIT_AT);
        creeper.setNoAi(true);
        float away = yawTo(c, creeper) + 180;
        c.setYRot(away); c.setYHeadRot(away); c.setYBodyRot(away);
        long[] lit = {-1};
        boolean[] facedAndUp = {false};
        h.runAfterDelay(IGNITE_AFTER, () -> {
            h.assertTrue(angle(c, creeper) > BEHIND, "He was not turned away from the creeper to begin with: " + angle(c, creeper));
            creeper.setNoAi(false);
            creeper.ignite();
            lit[0] = h.getTick();
        });
        h.onEachTick(() -> {
            String trace = " | creeper " + (creeper.isAlive() ? "alive" : "gone") + ", fuse " + creeper.getSwellDir() + ", health " + c.getHealth() + ", turned off by "
                    + angle(c, creeper) + ", shield wear " + c.getOffhandItem().getDamageValue();
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (lit[0] < 0) { c.setYRot(away); c.setYHeadRot(away); c.setYBodyRot(away); return; }
            if (creeper.isAlive() && h.getTick() - lit[0] < FUSE_TICKS) {
                facedAndUp[0] |= c.isBlocking() && angle(c, creeper) < FACING;
                return;
            }
            h.assertTrue(!creeper.isAlive(), "The creeper never blew" + trace);
            h.assertTrue(facedAndUp[0], "He never stood facing the creeper with the shield up" + trace);
            h.assertTrue(c.getHealth() >= c.getMaxHealth(), "He was hurt by the blast" + trace);
            h.assertTrue(c.getOffhandItem().getDamageValue() > 0, "The shield took no wear" + trace);
            h.succeed();
        });
    }

    /**
     * Without a shield nothing changes: a lit fuse sends him back, and he never stands up a shield he does not have. Counted against standing
     * still, which at three blocks leaves him with next to nothing, not against the edge of what a dash saves.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 300)
    public static void onOpenGroundWithoutAShieldALitFuseStillSendsHimBack(GameTestHelper h) {
        CompanionEntity c = setup(h, false);
        Creeper creeper = creeper(h, UNSHIELDED_AT);
        creeper.ignite();
        long begun = h.getTick();
        h.onEachTick(() -> {
            String trace = " | creeper " + (creeper.isAlive() ? "alive" : "gone") + ", gap " + String.format("%.1f", c.distanceTo(creeper)) + ", health " + c.getHealth();
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(!c.isBlocking(), "He blocks with nothing in the left hand" + trace);
            if (creeper.isAlive() && h.getTick() - begun < FUSE_TICKS) return;
            h.assertTrue(!creeper.isAlive(), "The creeper never blew" + trace);
            h.assertTrue(c.getHealth() >= c.getMaxHealth() - UNSHIELDED_LOSS, "The blast cost him more than a dash should" + trace);
            h.succeed();
        });
    }

    /** Wear is a player's: a blow of under 3 costs nothing, one of 3 or more costs 1 plus its damage, and a shield worn to nothing is gone, with the blow absorbed. */
    @GameTest(template = "tall_tree", timeoutTicks = 300)
    public static void onOpenGroundTheShieldWearsAndBreaksLikeAPlayers(GameTestHelper h) {
        CompanionEntity c = setup(h, true);
        Creeper creeper = creeper(h, LIT_AT);
        CompoundTag fuse = new CompoundTag();
        creeper.addAdditionalSaveData(fuse);
        fuse.putShort("Fuse", (short) LONG_FUSE);
        fuse.putBoolean("ignited", true);
        creeper.readAdditionalSaveData(fuse);
        int[] stage = {0};
        h.runAtTickTime(RAISE_TICKS * 10, () -> h.assertTrue(stage[0] > 0, "The shield was never up against the lit fuse"));
        h.onEachTick(() -> {
            if (!c.isBlocking()) return;
            ItemStack shield = c.getOffhandItem();
            float health = c.getHealth();
            int worn = shield.getDamageValue();
            c.invulnerableTime = 0;
            switch (stage[0]++) {
                case 0 -> {
                    c.hurt(h.getLevel().damageSources().explosion(creeper, creeper), 2);
                    h.assertTrue(c.getHealth() >= health && shield.getDamageValue() == worn, "A blow under 3 hurt him or wore the shield: health " + c.getHealth() + ", wear " + shield.getDamageValue());
                }
                case 1 -> {
                    c.hurt(h.getLevel().damageSources().explosion(creeper, creeper), 10);
                    h.assertTrue(c.getHealth() >= health, "The blow of 10 got through: health " + health + " to " + c.getHealth());
                    h.assertTrue(shield.getDamageValue() == worn + 11, "A blow of 10 cost " + (shield.getDamageValue() - worn) + " of wear, not 11");
                    shield.setDamageValue(shield.getMaxDamage() - 5);
                }
                default -> {
                    c.hurt(h.getLevel().damageSources().explosion(creeper, creeper), 10);
                    h.assertTrue(c.getHealth() >= health, "The blow that broke the shield got through: health " + health + " to " + c.getHealth());
                    h.assertTrue(c.getOffhandItem().isEmpty() && !c.isBlocking(), "The shield worn to nothing is still in the hand: " + c.getOffhandItem());
                    h.succeed();
                }
            }
        });
    }

    /** The companion stands at x 16.5, z 16.5 of the test: the creeper is put this many blocks east of him. */
    private static Creeper creeper(GameTestHelper h, double gap) {
        Creeper creeper = h.spawn(EntityType.CREEPER, new Vec3(16.5 + gap, 1, 16.5));
        creeper.setPersistenceRequired();
        return creeper;
    }

    private static float yawTo(CompanionEntity c, Creeper creeper) {
        return (float) (Mth.atan2(creeper.getZ() - c.getZ(), creeper.getX() - c.getX()) * Mth.RAD_TO_DEG) - 90.0F;
    }

    /** How far, in degrees, his head is turned off the creeper. */
    private static double angle(CompanionEntity c, Creeper creeper) {
        return Math.abs(Mth.wrapDegrees(c.getYHeadRot() - yawTo(c, creeper)));
    }

    private static CompanionEntity setup(GameTestHelper h, boolean shield) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos stand = h.absolutePos(new BlockPos(14, 1, 15));
        player.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        companion.setSearchRadius(16);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        if (shield) companion.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        return companion;
    }
}

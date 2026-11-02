package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionAgenda;
import dev.aicompanion.entity.CompanionAgendaData;
import dev.aicompanion.entity.CompanionAgendaView;
import dev.aicompanion.entity.CompanionAgendaView.Mark;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionGoals;
import dev.aicompanion.entity.CompanionTaskBoard;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** A long goal as the model and the task screen are told of it, and the screen's buttons against the phrases. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionAgendaBoardTests {
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** CompanionAgenda.MAX_ATTEMPTS: a milestone that spent them stops the goal at its next second. */
    private static final int ATTEMPT_LIMIT = 12;

    @AfterBatch(batch = "agenda_board") public static void afterBoard(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    /** Diamond gear is done, so the tree shows done, current and later marks but no obstacle and nothing he cannot do; then the goal stops and the obstacle replaces the current one. */
    @GameTest(template = "tall_tree", batch = "agenda_board", timeoutTicks = 400)
    public static void goalLinesInSnapshot(GameTestHelper h) {
        var f = fixture(h);
        f.c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE)); f.c.setItem(1, new ItemStack(Items.DIAMOND_SWORD));
        Item[] armor = {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
        for (int i = 0; i < ARMOR_SLOTS.length; i++) f.c.setItemSlot(ARMOR_SLOTS[i], new ItemStack(armor[i]));
        h.assertTrue(f.order("pursue_goal", "prepare_nether").status() == CompanionActionResult.Status.ACCEPTED, "Goal not accepted");

        String line = CompanionAgendaView.line(f.c);
        h.assertTrue(line.startsWith("Долгая цель: «подготовка к Незеру»; сейчас: запас готовой еды: 16; сделано: ")
                && line.contains("; дальше: 64 булыжника для мостов и столбов, огниво") && !line.contains("не умею") && !line.contains("помеха") && !line.contains("отложена"),
                "Goal line of the model: " + line);
        var tree = CompanionTaskBoard.read(f.p).agenda();
        h.assertTrue(tree != null && tree.goal().equals(f.goal().id()) && tree.state() == CompanionAgenda.State.ACTIVE, "No goal in the snapshot");
        h.assertTrue(marks(tree, Mark.DONE) == 8 && marks(tree, Mark.CURRENT) == 1 && marks(tree, Mark.LATER) == 2
                && marks(tree, Mark.CANNOT) == 0 && marks(tree, Mark.OBSTACLE) == 0, "Milestone tree: " + tree.lines());
        h.assertTrue(tree.lines().stream().filter(l -> l.mark() == Mark.CURRENT).allMatch(l -> l.label().equals("запас готовой еды: 16")), "Wrong current milestone: " + tree.lines());

        // The engine meets the spent milestone at its first second; the line the client reads is the synced one, set once a second.
        f.goal().counter("food")[0] = ATTEMPT_LIMIT;
        h.succeedWhen(() -> {
            h.assertTrue(f.goal().state() == CompanionAgenda.State.BLOCKED, "The goal did not stop: " + f.goal().state());
            String synced = f.c.getGoalForAi();
            h.assertTrue(synced.startsWith("Долгая цель: «подготовка к Незеру»; сейчас: стою, нужна помощь игрока (этап: запас готовой еды: 16); сделано: ")
                    && synced.contains("; помеха: запас готовой еды: 16 — попытки исчерпаны."), "Synced goal line: " + synced);
            var stopped = CompanionTaskBoard.read(f.p).agenda();
            h.assertTrue(marks(stopped, Mark.OBSTACLE) == 1 && marks(stopped, Mark.CURRENT) == 0 && stopped.obstacle().contains("попытки исчерпаны")
                    && stopped.canResume(), "Stopped tree: " + stopped.lines() + " " + stopped.obstacle());
        });
    }

    /** Defer, resume and cancel from the screen leave the agenda exactly as the phrases do, and an old screen changes nothing. */
    @GameTest(template = "tall_tree", batch = "agenda_board", timeoutTicks = 100)
    public static void taskBoardGoalButtonsMatchPhrases(GameTestHelper h) {
        var f = fixture(h);
        f.order("pursue_goal", "gear_up");
        var shown = CompanionTaskBoard.read(f.p);
        h.assertTrue(shown.agenda().canDefer() && !shown.agenda().canResume(), "Buttons of a working goal: " + shown.agenda());

        CompanionTaskBoard.control(f.p, shown, "defer_goal");
        String deferredByScreen = f.agenda();
        h.assertTrue(deferredByScreen.contains("active=- ") && deferredByScreen.contains("deferred=HELD"), "Screen did not defer: " + deferredByScreen);
        var waiting = CompanionTaskBoard.read(f.p).agenda();
        h.assertTrue(waiting.deferredOnly() && waiting.canResume() && !waiting.canDefer(), "Buttons of a deferred goal: " + waiting);
        f.order("goal_resume");
        f.order("goal_defer");
        h.assertTrue(deferredByScreen.equals(f.agenda()), "Defer differs: screen " + deferredByScreen + " phrase " + f.agenda());

        CompanionTaskBoard.control(f.p, CompanionTaskBoard.read(f.p), "resume_goal");
        String resumedByScreen = f.agenda();
        h.assertTrue(resumedByScreen.contains("active=ACTIVE/") && resumedByScreen.contains("deferred=-"), "Screen did not resume: " + resumedByScreen);
        f.order("goal_defer");
        f.order("goal_resume");
        h.assertTrue(resumedByScreen.equals(f.agenda()), "Resume differs: screen " + resumedByScreen + " phrase " + f.agenda());

        var old = CompanionTaskBoard.read(f.p);
        CompanionTaskBoard.control(f.p, old, "cancel_goal");
        String cancelledByScreen = f.agenda();
        h.assertTrue(cancelledByScreen.contains("active=CANCELLED/") && cancelledByScreen.contains("deferred=-"), "Screen did not cancel: " + cancelledByScreen);
        f.order("pursue_goal", "gear_up");
        f.order("goal_cancel");
        h.assertTrue(cancelledByScreen.equals(f.agenda()), "Cancel differs: screen " + cancelledByScreen + " phrase " + f.agenda());

        f.order("pursue_goal", "gear_up");
        CompanionTaskBoard.control(f.p, old, "cancel_goal");
        h.assertTrue(f.goal().state() == CompanionAgenda.State.ACTIVE, "A screen that showed an older goal cancelled the new one");

        // The pickaxe order beside a held long goal: its own buttons never reach the long goal.
        f.order("stop");
        f.c.setItem(0, new ItemStack(Items.COBBLESTONE, 6)); f.c.setItem(1, new ItemStack(Items.STICK, 4));
        h.assertTrue(f.order("goal_pickaxes").status() == CompanionActionResult.Status.ACCEPTED, "Pickaxe order refused beside a held goal");
        CompanionTaskBoard.control(f.p, CompanionTaskBoard.read(f.p), "cancel_pickaxes");
        h.assertTrue(f.c.goals().state() == CompanionGoals.State.CANCELLED && f.goal().state() == CompanionAgenda.State.HELD,
                "Pickaxe row cancelled the wrong goal: " + f.c.goals().state() + " / " + f.goal().state());

        // The goal row beside an outstanding pickaxe order acts on the long goal only, never on the order by falling through.
        h.assertTrue(f.order("goal_pickaxes").status() == CompanionActionResult.Status.ACCEPTED, "Second pickaxe order refused");
        f.order("stop");
        var beside = CompanionTaskBoard.read(f.p);
        h.assertTrue(f.c.goals().state() == CompanionGoals.State.PAUSED_PLAYER && !beside.agenda().canResume(),
                "«Продолжить» is offered on the goal row while the pickaxe order goes first: " + f.c.goals().state() + " / " + beside.agenda());
        CompanionTaskBoard.control(f.p, beside, "resume_goal");
        h.assertTrue(f.c.goals().state() == CompanionGoals.State.PAUSED_PLAYER && f.goal().state() == CompanionAgenda.State.HELD,
                "Goal row «Продолжить» reached the pickaxe order: " + f.c.goals().state() + " / " + f.goal().state());
        f.order("goal_defer");
        CompanionTaskBoard.control(f.p, CompanionTaskBoard.read(f.p), "cancel_goal");
        h.assertTrue(f.c.goals().state() == CompanionGoals.State.PAUSED_PLAYER && f.agenda().contains("deferred=-"),
                "Goal row «Отменить цель» on a deferred goal: pickaxes " + f.c.goals().state() + ", agenda " + f.agenda());
        h.succeed();
    }

    private static long marks(CompanionAgendaView.View view, Mark mark) { return view.lines().stream().filter(l -> l.mark() == mark).count(); }

    private static Fixture fixture(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        var c = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        var p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, p, cookie); p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); p.setInvulnerable(true);
        p.teleportTo(c.getX() - 5, c.getY(), c.getZ() - 5); c.setOwnerId(p.getUUID()); c.setSearchRadius(16); c.setLifestyle(false, false);
        p.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID()); CompanionEvents.getOwnedCompanion(p);
        return new Fixture(h, c, p);
    }

    private record Fixture(GameTestHelper h, CompanionEntity c, ServerPlayer p) {
        CompanionActionResult order(String action) { return order(action, ""); }
        CompanionActionResult order(String action, String item) { return CompanionEvents.executeAction(p, action, item, 1); }
        CompanionAgendaData.Goal goal() { return CompanionAgenda.goal(h.getLevel().getServer(), p.getUUID()); }

        /** The agenda of the player as the buttons and the phrases leave it; the ids differ between runs, so they stay out. */
        String agenda() {
            var data = CompanionAgendaData.get(h.getLevel().getServer());
            var active = data.active(p.getUUID()); var deferred = data.deferred(p.getUUID());
            return "active=" + (active == null ? "- " : active.state() + "/" + active.reason() + "/" + active.playbook())
                    + " deferred=" + (deferred == null ? "-" : deferred.state() + "/" + deferred.reason() + "/" + deferred.playbook());
        }
    }
}

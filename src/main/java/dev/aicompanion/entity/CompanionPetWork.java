package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** One order owns the target and its bait search until completion or cancellation. */
final class CompanionPetWork {
    private enum Stage { BAIT, ANSWER, GIFT, PERMISSION, VILLAGE_PERMISSION, VILLAGE_SEARCH, FEED, FIND, HOME }
    private static final int GROUND_BAIT_RADIUS = 16;
    private static final int FEED_LIMIT = 12;
    private static final int ANSWER_WAIT_SECONDS = 20;
    private static final int GIFT_WAIT_SECONDS = 45;
    private static final int VILLAGE_SEARCH_SECONDS = 60;
    private static final String[] CAT_PRAISE = {
            "Так у нас уже есть кот. Ты посмотри, какие у него усы!",
            "У нас свой есть, домашний. Я бы нашего мурлыку ни на кого не променял.",
            "А наш чем плох? По-моему, самый красивый кот во всей деревне.",
            "Есть у нас кот. Лучший, между прочим. Можешь у него самого спросить."
    };
    private static final String[] DOG_PRAISE = {
            "У нас уже есть свой пёс. Стоит мне позвать, и он тут как тут.",
            "А наш пёс чем плох? Я бы его ни на кого не променял.",
            "Есть у нас собака. Отличный напарник для прогулок!"
    };
    private static final String[] NO_CAT = {
            "Рядом не вижу бездомной кошки. Если встретишь одну, позови меня.",
            "Тут тихо, даже мяуканья не слышно. Бездомных кошек рядом не нашёл."
    };

    private final CompanionWork work;
    private final CompanionEntity owner;
    final CompanionPet kind;
    private final Set<UUID> skippedStrays = new HashSet<>();
    private final Set<UUID> skippedBait = new HashSet<>();
    private int searchRadius;
    private Stage stage;
    private UUID target, baitDrop;
    private String villageCourse;
    private BlockPos searchOrigin, lastSeen, home;
    private Lure lure;
    private long stageUntil, nextFeed, nextBaitScan, nextVillageScan;
    private int fed, wrongBaitBefore, praiseLine, emptyLine;
    private boolean enabled = true;
    private boolean canFindOwnBait;

    CompanionPetWork(CompanionWork work, CompanionPet kind) {
        this.work = work;
        this.owner = work.owner;
        this.kind = kind;
        this.searchRadius = CompanionPet.DEFAULT_SEARCH_RADIUS;
    }

    void setSearchRadius(int radius) {
        searchRadius = Math.clamp(radius, CompanionPet.MIN_SEARCH_RADIUS, CompanionPet.MAX_SEARCH_RADIUS);
    }

    void setEnabled(boolean value) {
        enabled = value;
        if (!value && work.activePet == this) finish(false, "Поручение о " + kind.about + " остановил. " + keptBait());
    }

    String cancelLine() {
        return cat() ? "Ладно, займёмся котом потом. Рыбу приберегу." : "Ладно, займёмся собакой потом. Кости приберегу.";
    }

    private boolean cat() { return kind == CompanionPet.CAT; }

    private String keptBait() { return cat() ? "Рыбу сохранил." : "Кости сохранил."; }

    private String noPet() { return cat() ? "Своего кота у меня пока нет." : "Своей собаки у меня пока нет."; }

    private int baitCount() {
        return cat() ? work.items.countCarried(Items.COD) + work.items.countCarried(Items.SALMON) : work.items.countCarried(Items.BONE);
    }

    private int wrongBaitCount() {
        return cat() ? work.items.countCarried(Items.COOKED_COD) + work.items.countCarried(Items.COOKED_SALMON)
                : work.items.countCarried(Items.BONE_MEAL);
    }

    private CompanionPetBonds bonds(ServerLevel level) { return CompanionPetBonds.get(level.getServer(), kind); }

    CompanionActionResult status(ServerLevel level) {
        var pet = bonds(level).pet(owner);
        return CompanionActionResult.ok(pet == null ? noPet() : praise(level, pet));
    }

    private String praise(ServerLevel level, CompanionPetBonds.Pet pet) {
        var lines = cat() ? CAT_PRAISE : DOG_PRAISE;
        int reply = Math.floorMod(praiseLine++, lines.length + 1);
        if (reply == lines.length) {
            if (level.getServer().overworld().getGameTime() - pet.fedAt() < 600)
                return cat() ? "У нас свой красавец уже есть. И рыбой я его только что угостил!"
                        : "У нас уже есть пёс. Только что угостил его косточкой!";
            return cat() ? "Свой мурлыка у нас уже есть. Вот бы и мне так уметь хвост трубой держать."
                    : "Свой пёс уже есть. Как меня увидит, сразу хвостом виляет.";
        }
        return lines[reply];
    }

    /** Null means the shared work queue should track the active order. */
    CompanionActionResult begin(ServerLevel level, String action, String item, int count) {
        boolean sit = action.equals("sit_" + kind.id);
        boolean taming = action.equals("tame_" + kind.id);
        String proposedCourse = taming ? CompanionCourse.petVillageCourse(item) : null;
        if (count != 1 || item != null && !item.isEmpty() && !item.equals("resources") && !(sit && item.equals("home")) && proposedCourse == null)
            return CompanionActionResult.failed("Не понял поручение о " + kind.about + ".");
        if (!enabled) return CompanionActionResult.failed(cat() ? "«Свой кот» выключен в настройках поведения."
                : "«Своя собака» выключена в настройках поведения.");
        var pet = bonds(level).pet(owner);
        if (!taming) return beginPetOrder(level, action, sit, item, pet);
        if (pet != null) return CompanionActionResult.ok(praise(level, pet));
        if (proposedCourse != null && CompanionCourse.atVillage(level, owner)) {
            beginVillageSearch(level);
            return null;
        }
        var animal = nearestStray(level, owner.blockPosition());
        if (animal == null) {
            if (proposedCourse != null) {
                villageCourse = proposedCourse;
                enter(level, Stage.VILLAGE_PERMISSION, ANSWER_WAIT_SECONDS);
                return null;
            }
            boolean onlyPets = !level.getEntitiesOfClass(kind.animal, new AABB(owner.blockPosition()).inflate(searchRadius),
                    other -> other.isAlive() && other.isTame() && other.distanceToSqr(owner) <= searchRadius * searchRadius).isEmpty();
            if (cat()) return CompanionActionResult.failed(onlyPets ? "Тут усатые есть, но у каждого уже есть дом."
                    : NO_CAT[Math.floorMod(emptyLine++, NO_CAT.length)]);
            return CompanionActionResult.failed(onlyPets ? "Тут есть собаки, но все уже с хозяевами."
                    : "Рядом не нашёл дикого волка, которого можно приручить.");
        }
        beginTaming(level, animal, owner.blockPosition());
        return null;
    }

    private void beginTaming(ServerLevel level, TamableAnimal animal, BlockPos origin) {
        searchOrigin = origin;
        remember(animal);
        canFindOwnBait = false;
        fed = 0;
        if (baitCount() > 0) enter(level, Stage.FEED, 55);
        else enter(level, Stage.ANSWER, ANSWER_WAIT_SECONDS);
    }

    void beginVillageSearch(ServerLevel level) {
        searchOrigin = owner.blockPosition();
        enter(level, Stage.VILLAGE_SEARCH, VILLAGE_SEARCH_SECONDS);
    }

    private CompanionActionResult beginPetOrder(ServerLevel level, String action, boolean sit, String item, CompanionPetBonds.Pet pet) {
        if (pet == null) return CompanionActionResult.failed(noPet());
        if (!pet.lastSeen().dimension().equals(level.dimension()))
            return CompanionActionResult.failed("В последний раз я видел " + kind.whom + " в измерении " + pet.lastSeen().dimension().location()
                    + ", у " + pet.lastSeen().pos().toShortString() + ".");
        if (action.equals("find_" + kind.id)) {
            target = pet.id();
            lastSeen = pet.lastSeen().pos();
            enter(level, Stage.FIND, 60);
            return null;
        }
        var animal = loadedPet(level, pet.id());
        if (animal == null)
            return CompanionActionResult.failed("Сейчас не вижу " + (cat() ? "кота" : "собаки") + ". Последнее место — " + pet.lastSeen().pos().toShortString() + ".");
        if (sit && "home".equals(item)) {
            var place = owner.places.home();
            if (place == null) return CompanionActionResult.failed("Сначала покажи мне, где у нас дом.");
            home = place.pos();
            remember(animal);
            animal.setOrderedToSit(false);
            enter(level, Stage.HOME, 75);
            return null;
        }
        setSitting(animal, sit);
        return CompanionActionResult.ok((sit ? "Оставил " : "Позвал ") + (cat() ? "нашего кота" : "нашу собаку")
                + (sit ? " сидеть здесь." : " за собой."));
    }

    private TamableAnimal loadedPet(ServerLevel level, UUID id) {
        return level.getEntity(id) instanceof TamableAnimal animal && animal.isAlive() && kind.animal.isInstance(animal)
                && CompanionPetBonds.belongsTo(owner, animal) ? animal : null;
    }

    private void setSitting(TamableAnimal animal, boolean sit) {
        animal.setOrderedToSit(sit);
        if (sit && !cat()) animal.setTarget(null);
        animal.getNavigation().stop();
    }

    private TamableAnimal nearestStray(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(kind.animal, new AABB(pos).inflate(searchRadius),
                        animal -> kind.isStray(animal) && !skippedStrays.contains(animal.getUUID())
                                && animal.distanceToSqr(pos.getCenter()) <= searchRadius * searchRadius)
                .stream().min(Comparator.comparingDouble(owner::distanceToSqr)).orElse(null);
    }

    private boolean tryAnotherStray(ServerLevel level) {
        skippedStrays.add(target);
        releaseLure();
        owner.getNavigation().stop();
        work.resetTarget();
        var replacement = nearestStray(level, searchOrigin);
        if (replacement == null) return false;
        remember(replacement);
        return true;
    }

    private void remember(TamableAnimal animal) {
        target = animal.getUUID();
        lastSeen = animal.blockPosition();
    }

    private void enter(ServerLevel level, Stage next, int seconds) {
        stage = next;
        stageUntil = level.getGameTime() + 20L * seconds;
        nextFeed = 0;
        owner.workStatus(stageText());
    }

    String stageText() {
        return switch (stage) {
            case BAIT -> baitDrop == null
                    ? (cat() ? "Ищу сырую рыбу для кота рядом и в сундуках." : "Ищу кости для собаки рядом и в сундуках.")
                    : (cat() ? "Иду подобрать сырую рыбу для кота." : "Иду подобрать кости для собаки.");
            case ANSWER, GIFT -> cat() ? "Жду рыбу для кота." : "Жду кости для собаки.";
            case PERMISSION -> "Жду ответа, поискать ли приманку рядом и в сундуках.";
            case VILLAGE_PERMISSION -> "Жду ответа, идти ли искать деревню.";
            case VILLAGE_SEARCH -> cat() ? "Ищу бездомную кошку в деревне." : "Ищу дикого волка у деревни.";
            case FEED -> cat() ? "Подманиваю кота рыбой." : "Подманиваю волка костью.";
            case FIND -> "Ищу " + (cat() ? "нашего кота." : "нашу собаку.");
            case HOME -> "Веду " + (cat() ? "нашего кота" : "нашу собаку") + " домой.";
            case null -> "Поручение о " + kind.about + ".";
        };
    }

    void tick(ServerLevel level) {
        if (stage == null) return;
        if (owner.getOwnerPlayer() == null) {
            finish(false, "Мы разошлись по измерениям. Остановился. " + keptBait());
            return;
        }
        if (level.getGameTime() >= stageUntil) {
            if (stage == Stage.BAIT) finishWithoutBait();
            else finish(false, switch (stage) {
                case FIND -> "Не нашёл " + kind.whom + ". Последнее известное место — " + lastSeen.toShortString() + ".";
                case HOME -> "Так и не смог привести " + kind.whom + " домой.";
                case ANSWER, PERMISSION -> "Не дождался ответа. Приручение отложим.";
                case VILLAGE_PERMISSION -> "Не дождался ответа. Останемся здесь.";
                case VILLAGE_SEARCH -> cat() ? "В деревне бездомной кошки пока не нашёл." : "У деревни дикого волка пока не нашёл.";
                case GIFT -> cat() ? "Рыбы так и не дождался. Займёмся котом в другой раз."
                        : "Костей так и не дождался. Займёмся собакой в другой раз.";
                case FEED -> (cat() ? "Кот" : "Волк") + " так и не подошёл. " + keptBait();
                case BAIT -> throw new IllegalStateException("Bait search timeout already handled");
            });
            return;
        }
        if (waitingForVillage()) {
            if (owner.petQuestion(kind) == 0) {
                enter(level, Stage.VILLAGE_PERMISSION, ANSWER_WAIT_SECONDS);
                owner.petQuestion(kind, work.activeTaskId(), CompanionPet.Question.VILLAGE_PERMISSION);
                String nearby = cat() ? "Рядом кошек не вижу." : "Рядом волков не вижу.";
                owner.petSay(nearby + " Можем пойти " + CompanionCourse.where(villageCourse) + " поискать деревню. Пойдём?");
            }
            return;
        }
        if (stage == Stage.VILLAGE_SEARCH) { searchVillage(level); return; }
        if (stage == Stage.BAIT || waitingForPlayer()) {
            if (baitCount() > 0) { returnToPet(level); return; }
            if (stage == Stage.BAIT) { findBait(level); return; }
            // The queue assigns this order's ID after begin returns.
            if (stage == Stage.ANSWER && owner.petQuestion(kind) == 0) { askForBait(level); return; }
            if (wrongBaitCount() > wrongBaitBefore) {
                wrongBaitBefore = wrongBaitCount();
                owner.petSay(cat() ? "Спасибо! Для кота нужна сырая треска или сырой лосось."
                        : "Для волка нужны целые кости. Костную муку он не возьмёт.");
            }
            return;
        }
        switch (stage) {
            case FEED -> feed(level);
            case FIND -> findPet(level);
            case HOME -> goHome(level);
            case BAIT, ANSWER, GIFT, PERMISSION, VILLAGE_PERMISSION, VILLAGE_SEARCH -> throw new IllegalStateException("Search and replies already handled");
        }
    }

    private void searchVillage(ServerLevel level) {
        long now = level.getGameTime();
        if (owner.petQuestion(kind) == 0) {
            owner.petQuestion(kind, work.activeTaskId(), CompanionPet.Question.VILLAGE_SEARCH);
            String whom = cat() ? "кота" : "собаку";
            owner.petSay("Ну вот, мы в деревне. Давай искать " + whom + " здесь. Когда найдёшь, скажи.");
            nextVillageScan = now + 20;
        } else if (now >= nextVillageScan) {
            nextVillageScan = now + 20;
            lookForVillagePet(level, owner.blockPosition(), false);
        }
    }

    private void lookForVillagePet(ServerLevel level, BlockPos origin, boolean playerReported) {
        var animal = nearestStray(level, origin);
        if (animal == null) {
            if (playerReported) owner.petSay(cat() ? "Покажи, где. Я пока не вижу бездомную кошку."
                    : "Покажи, где. Я пока не вижу дикого волка.");
            return;
        }
        owner.petQuestion(kind, 0);
        String found = playerReported ? (cat() ? "Ага, вижу кошку!" : "Ага, вижу волка!")
                : (cat() ? "О, сам нашёл бездомную кошку!" : "О, сам нашёл дикого волка!");
        beginTaming(level, animal, origin);
        if (stage == Stage.ANSWER) askForBait(level, found + " ");
        else owner.petSay(found);
    }

    private void startBaitSearch(ServerLevel level) {
        work.resetTarget();
        skippedBait.clear();
        nextBaitScan = 0;
        enter(level, Stage.BAIT, 55);
    }

    private void findBait(ServerLevel level) {
        if (baitDrop != null) {
            if (level.getEntity(baitDrop) instanceof ItemEntity drop && availableBait(drop) && approachBait(level, drop)) return;
            skippedBait.add(baitDrop);
            baitDrop = null;
            work.resetTarget();
            owner.workStatus(stageText());
        }
        if (work.target == null || level.getGameTime() >= nextBaitScan) {
            nextBaitScan = level.getGameTime() + 10;
            var drop = level.getEntitiesOfClass(ItemEntity.class, owner.getBoundingBox().inflate(GROUND_BAIT_RADIUS),
                            item -> availableBait(item) && !skippedBait.contains(item.getUUID())
                                    && item.distanceToSqr(owner) <= GROUND_BAIT_RADIUS * GROUND_BAIT_RADIUS)
                    .stream().min(Comparator.comparingDouble(owner::distanceToSqr)).orElse(null);
            if (drop != null) {
                owner.getNavigation().stop();
                work.resetTarget();
                baitDrop = drop.getUUID();
                owner.workStatus(stageText());
                return;
            }
        }
        checkChest(level);
    }

    private void checkChest(ServerLevel level) {
        if (work.target == null) {
            work.target = work.chests.petBaitChest(level, kind);
            work.resetApproach();
        }
        BlockPos pos = work.target;
        if (pos == null) { finishWithoutBait(); return; }
        owner.movementIntent(pos);
        if (owner.distanceToSqr(pos.getCenter()) > 3.2 * 3.2 || !work.items.visibleFrom(level, owner.getEyePosition(), pos)) {
            if (!level.hasChunkAt(pos) || canWalk() && !work.approach(level, pos, 2.8)) skipChest(pos);
            return;
        }
        owner.getNavigation().stop();
        if (work.chests.takePetBait(level, pos, kind)) {
            work.cooldown = 10;
        } else skipChest(pos);
    }

    private void skipChest(BlockPos pos) {
        work.triedChests.add(pos);
        work.resetTarget();
    }

    private boolean availableBait(ItemEntity drop) {
        return drop.isAlive() && kind.accepts(drop.getItem()) && owner.loot.isLoose(drop)
                && !drop.getPersistentData().getBoolean(CompanionPlayersThings.MARK)
                && !drop.getPersistentData().getBoolean("aicompanion:gift")
                && owner.canStore(drop.getItem().copyWithCount(1));
    }

    private boolean approachBait(ServerLevel level, ItemEntity drop) {
        if (owner.loot.touches(owner.position(), drop)) { owner.getNavigation().stop(); return true; }
        BlockPos pos = drop.blockPosition();
        if (!pos.equals(work.target)) {
            work.resetApproach();
            work.target = pos;
        }
        owner.movementIntent(pos);
        owner.getLookControl().setLookAt(drop.getX(), drop.getY(), drop.getZ());
        return !canWalk() || work.approachLooseItem(level, drop);
    }

    private void finishWithoutBait() {
        finish(false, fed > 0 ? (cat() ? "Угощение кот принял, а дружить пока думает. Рыба кончилась, отложим."
                : "Кости кончились, а волк пока не приручился. Попробуем в другой раз.")
                : (cat() ? "Сырой рыбы так и не нашёл. Займёмся котом в другой раз."
                : "Костей так и не нашёл. Займёмся собакой в другой раз."));
    }

    private void askForBait(ServerLevel level) { askForBait(level, ""); }

    private void askForBait(ServerLevel level, String preface) {
        work.resetTarget();
        owner.getNavigation().stop();
        wrongBaitBefore = wrongBaitCount();
        enter(level, Stage.ANSWER, ANSWER_WAIT_SECONDS);
        owner.petQuestion(kind, work.activeTaskId());
        String question = cat() ? "У тебя сырой трески или лосося не найдётся? Хочу кота угостить."
                : "У тебя есть кости? Хочу волка приручить.";
        if (fed > 0) question = cat() ? "Кот пока не приручился. У тебя есть ещё сырая рыба?"
                : "Волк пока не приручился. У тебя есть ещё кости?";
        owner.petSay(preface + question);
    }

    void answer(long workId, String answer) {
        if (!(owner.level() instanceof ServerLevel level) || work.activeTaskId() != workId
                || workId <= 0 || owner.petQuestion(kind) != workId || !waitingForPlayer()
                || level.getGameTime() >= stageUntil) return;
        if ("cancel".equals(answer)) {
            owner.petQuestion(kind, 0);
            work.finish(CompanionActionResult.cancelled(waitingForVillage() ? "Ладно, останемся здесь."
                    : cat() ? "Ладно, займёмся котом в другой раз." : "Ладно, займёмся собакой в другой раз."));
            return;
        }
        if (waitingForVillage()) {
            String direction = CompanionCourse.where(villageCourse);
            if ("go".equals(answer)) {
                var refusal = work.beginCourse(villageCourse, 0);
                if (refusal != null) { finish(false, refusal.message()); return; }
                owner.petQuestion(kind, 0);
                stage = null;
                owner.workStatus("Идём " + direction + " искать деревню.");
                owner.petSay("Пойдём " + direction + ". Посмотрим, есть ли там деревня.");
            } else if ("clarify".equals(answer)) {
                owner.petSay("Пока не знаю, где деревня. Предлагаю пойти " + direction + ". Пойдём или останемся здесь?");
            }
            return;
        }
        if (stage == Stage.VILLAGE_SEARCH) {
            if ("found".equals(answer)) {
                var player = owner.getOwnerPlayer();
                if (player == null) return;
                if (player.distanceToSqr(searchOrigin.getCenter()) > searchRadius * searchRadius) {
                    owner.petSay("Я пока осматриваю деревню. Подойди ближе и покажи, где нашёл.");
                } else lookForVillagePet(level, player.blockPosition(), true);
            }
            return;
        }
        if (baitCount() > 0) { returnToPet(level); return; }
        if (stage == Stage.PERMISSION) {
            if ("search".equals(answer)) {
                owner.petQuestion(kind, 0);
                canFindOwnBait = true;
                startBaitSearch(level);
                owner.petSay(stageText());
            }
            return;
        }
        switch (answer) {
            case "no" -> {
                enter(level, Stage.PERMISSION, ANSWER_WAIT_SECONDS);
                owner.petQuestion(kind, workId, CompanionPet.Question.BAIT_PERMISSION);
                owner.petSay("Могу посмотреть рядом и в сундуках. Сходить?");
            }
            case "yes", "gift" -> {
                if (stage == Stage.ANSWER) enter(level, Stage.GIFT, GIFT_WAIT_SECONDS);
                owner.petSay(cat() ? "Кинь мне пару сырых рыбок или положи их в мою сумку."
                        : "Кинь мне пару костей или положи их в мою сумку.");
            }
        }
    }

    boolean waitingForVillage() { return stage == Stage.VILLAGE_PERMISSION; }

    private boolean waitingForPlayer() {
        return stage == Stage.ANSWER || stage == Stage.GIFT || stage == Stage.PERMISSION || waitingForVillage() || stage == Stage.VILLAGE_SEARCH;
    }

    void dismissQuestion() {
        if (!waitingForPlayer()) return;
        owner.petQuestion(kind, 0);
        work.finish(CompanionActionResult.cancelled(cat() ? "Отложил поручение о коте." : "Отложил поручение о собаке."));
    }

    private void returnToPet(ServerLevel level) {
        owner.petQuestion(kind, 0);
        work.closeChest();
        work.resetTarget();
        baitDrop = null;
        enter(level, Stage.FEED, 55);
    }

    private void feed(ServerLevel level) {
        if (!(level.getEntity(target) instanceof TamableAnimal animal) || !kind.isStray(animal)
                || animal.distanceToSqr(lastSeen.getCenter()) > searchRadius * searchRadius) {
            releaseLure();
            if (!walkToLastSeen(level)) return;
            if (!tryAnotherStray(level)) finish(false, (cat() ? "Кота" : "Волка") + " на прежнем месте уже нет. " + keptBait());
            return;
        }
        lastSeen = animal.blockPosition();
        if (baitCount() == 0) {
            releaseLure();
            if (canFindOwnBait) startBaitSearch(level);
            else askForBait(level);
            return;
        }
        if (!owner.hasLineOfSight(animal) || owner.distanceToSqr(animal) > 6 * 6) {
            if (work.target == null || work.target.distSqr(lastSeen) >= 4) {
                work.resetApproach();
                work.target = lastSeen;
            }
            owner.movementIntent(lastSeen);
            if (canWalk() && !work.approachPet(level, animal, 3.5) && !tryAnotherStray(level))
                finish(false, "Не нашёл, к кому могу подойти. " + keptBait());
            return;
        }
        owner.getNavigation().stop();
        if (!kind.accepts(owner.getMainHandItem())) {
            if (!owner.equipForWork(BuiltInRegistries.ITEM.getKey(kind.bait(work.items)).toString()).success()) {
                finish(false, cat() ? "Рыба есть, но достать её не смог." : "Кости есть, но достать их не смог.");
                return;
            }
        }
        if (lure != null && lure.animal != animal) releaseLure();
        if (lure == null) {
            lure = new Lure(animal);
            animal.goalSelector.addGoal(3, lure);
        }
        owner.getLookControl().setLookAt(animal, 30, 30);
        if (owner.distanceToSqr(animal) > 2 * 2 || level.getGameTime() < nextFeed) return;
        ItemStack held = owner.getMainHandItem();
        held.shrink(1);
        owner.setItemInHand(InteractionHand.MAIN_HAND, held.isEmpty() ? ItemStack.EMPTY : held);
        owner.refreshInventorySummary();
        owner.swing(InteractionHand.MAIN_HAND);
        fed++;
        nextFeed = level.getGameTime() + 18;
        if (animal.getRandom().nextInt(3) == 0 && bonds(level).adopt(level, owner, animal)) {
            owner.petPresent(kind, true);
            owner.observeEvent(kind.id, cat() ? "Приручил бездомную кошку." : "Приручил дикого волка.");
            finish(true, cat() ? "Получилось! Теперь у нас свой кот." : "Получилось! Теперь у нас своя собака.");
        } else {
            level.broadcastEntityEvent(animal, (byte) 6);
            if (fed == FEED_LIMIT) finish(false, (cat() ? "Кот" : "Волк") + " угощение принял, а дружить пока думает. Дадим ему время.");
        }
    }

    private boolean walkToLastSeen(ServerLevel level) {
        if (owner.distanceToSqr(lastSeen.getCenter()) <= 6 * 6) return true;
        owner.movementIntent(lastSeen);
        if (canWalk() && !work.approachPlace(level, lastSeen, 4) && !tryAnotherStray(level))
            finish(false, "Не смог добраться до места, где видел " + (cat() ? "кота. " : "волка. ") + keptBait());
        return false;
    }

    private boolean canWalk() { return owner.onGround() || owner.isInWater(); }

    private void findPet(ServerLevel level) {
        var animal = loadedPet(level, target);
        if (animal != null) {
            lastSeen = animal.blockPosition();
            if (owner.distanceToSqr(animal) <= 4 * 4 && owner.hasLineOfSight(animal)) {
                finish(true, cat() ? "Вот наш мурлыка, я его нашёл!" : "Вот наша собака, я её нашёл!");
                return;
            }
            owner.movementIntent(lastSeen);
            if (canWalk() && !work.approachPet(level, animal, 3)) finish(false, "Вижу " + kind.whom + ", но подойти не могу.");
            return;
        } else if (owner.distanceToSqr(lastSeen.getCenter()) <= 6 * 6) {
            finish(false, "В последний раз видел " + kind.whom + " у " + lastSeen.toShortString() + ". Сейчас здесь никого.");
            return;
        }
        owner.movementIntent(lastSeen);
        if (canWalk() && !work.approachPlace(level, lastSeen, 3)) finish(false, "Не смог добраться. Последний раз видел " + kind.whom
                + " у " + lastSeen.toShortString() + ".");
    }

    private void goHome(ServerLevel level) {
        if (owner.distanceToSqr(home.getCenter()) > 4 * 4) {
            owner.movementIntent(home);
            if (canWalk() && !work.approachPlace(level, home, 3.5)) finish(false, "Не смог добраться домой " + (cat() ? "с котом." : "с собакой."));
            return;
        }
        owner.getNavigation().stop();
        var animal = loadedPet(level, target);
        if (animal != null && animal.distanceToSqr(home.getCenter()) <= 6 * 6) {
            setSitting(animal, true);
            finish(true, "Довёл " + (cat() ? "нашего кота" : "нашу собаку") + " домой. Пусть тут посидит.");
        }
    }

    private void finish(boolean success, String message) {
        releaseLure();
        owner.petQuestion(kind, 0);
        work.finish(success, message);
    }

    private void releaseLure() {
        if (lure == null) return;
        lure.animal.goalSelector.removeGoal(lure);
        lure = null;
    }

    void cancel() {
        releaseLure();
        if (stage != null) owner.petQuestion(kind, 0);
        stage = null;
        target = baitDrop = null;
        villageCourse = null;
        searchOrigin = lastSeen = home = null;
        skippedStrays.clear();
        skippedBait.clear();
    }

    private final class Lure extends Goal {
        private final TamableAnimal animal;

        Lure(TamableAnimal animal) {
            this.animal = animal;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override public boolean canUse() {
            return stage == Stage.FEED && kind.isStray(animal) && owner.isAlive() && owner.level() == animal.level()
                    && kind.accepts(owner.getMainHandItem()) && animal.distanceToSqr(owner) <= 8 * 8 && animal.hasLineOfSight(owner);
        }

        @Override public void tick() {
            animal.getLookControl().setLookAt(owner, 30, 30);
            if (animal.distanceToSqr(owner) <= 1.5 * 1.5) animal.getNavigation().stop();
            else if (animal.tickCount % 10 == 0 || animal.getNavigation().isDone()) animal.getNavigation().moveTo(owner, .6);
        }

        @Override public void stop() { animal.getNavigation().stop(); }
    }
}

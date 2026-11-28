package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
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
    private enum Stage { BAIT, ANSWER, GIFT, PERMISSION, HABITAT_PERMISSION, HABITAT_SEARCH, FEED, FIND, HOME }
    private static final int GROUND_BAIT_RADIUS = 16;
    private static final int PARROT_LURE_RADIUS = 12;
    private static final int FEED_LIMIT = 12;
    private static final int ANSWER_WAIT_SECONDS = 20;
    private static final int GIFT_WAIT_SECONDS = 45;
    private static final int HABITAT_SEARCH_SECONDS = 60;
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
    private static final String[] PARROT_PRAISE = {
            "У нас уже есть свой попугай. Видел, какое у него оперение?",
            "Наш пернатый уже с нами. И слух у него получше моего!",
            "Есть у нас попугай. Отличный попутчик, особенно когда дорога длинная."
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
    private String habitatCourse;
    private BlockPos searchOrigin, lastSeen, home;
    private Lure lure;
    private long stageUntil, nextFeed, nextBaitScan, nextHabitatScan;
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
        return switch (kind) {
            case CAT -> "Ладно, займёмся котом потом. Рыбу приберегу.";
            case DOG -> "Ладно, займёмся собакой потом. Кости приберегу.";
            case PARROT -> "Ладно, займёмся попугаем потом. Семена приберегу.";
        };
    }

    private String keptBait() {
        return switch (kind) {
            case CAT -> "Рыбу сохранил.";
            case DOG -> "Кости сохранил.";
            case PARROT -> "Семена сохранил.";
        };
    }

    private String noPet() {
        return switch (kind) {
            case CAT -> "Своего кота у меня пока нет.";
            case DOG -> "Своей собаки у меня пока нет.";
            case PARROT -> "Своего попугая у меня пока нет.";
        };
    }

    private String ourPet() { return (kind == CompanionPet.DOG ? "нашу " : "нашего ") + kind.whom; }

    String destination() { return kind == CompanionPet.PARROT ? "джунгли" : "деревню"; }

    private String foundWild() {
        return switch (kind) {
            case CAT -> "бездомную кошку";
            case DOG -> "дикого волка";
            case PARROT -> "дикого попугая";
        };
    }

    private String baitForPet() {
        return switch (kind) {
            case CAT -> "сырую рыбу для кота";
            case DOG -> "кости для собаки";
            case PARROT -> "семена для попугая";
        };
    }

    private int baitCount() {
        return work.items.carriedItems().stream().filter(kind::accepts).mapToInt(ItemStack::getCount).sum();
    }

    private int wrongBaitCount() {
        return switch (kind) {
            case CAT -> work.items.countCarried(Items.COOKED_COD) + work.items.countCarried(Items.COOKED_SALMON);
            case DOG -> work.items.countCarried(Items.BONE_MEAL);
            case PARROT -> work.items.carriedItems().stream().filter(stack -> stack.is(ItemTags.PARROT_POISONOUS_FOOD))
                    .mapToInt(ItemStack::getCount).sum();
        };
    }

    private CompanionPetBonds bonds(ServerLevel level) { return CompanionPetBonds.get(level.getServer(), kind); }

    CompanionActionResult status(ServerLevel level) {
        var pet = bonds(level).pet(owner);
        return CompanionActionResult.ok(pet == null ? noPet() : praise(level, pet));
    }

    private String praise(ServerLevel level, CompanionPetBonds.Pet pet) {
        var lines = switch (kind) {
            case CAT -> CAT_PRAISE;
            case DOG -> DOG_PRAISE;
            case PARROT -> PARROT_PRAISE;
        };
        int reply = Math.floorMod(praiseLine++, lines.length + 1);
        if (reply == lines.length) {
            boolean justFed = level.getServer().overworld().getGameTime() - pet.fedAt() < 600;
            return switch (kind) {
                case CAT -> justFed ? "У нас свой красавец уже есть. И рыбой я его только что угостил!"
                        : "Свой мурлыка у нас уже есть. Вот бы и мне так уметь хвост трубой держать.";
                case DOG -> justFed ? "У нас уже есть пёс. Только что угостил его косточкой!"
                        : "Свой пёс уже есть. Как меня увидит, сразу хвостом виляет.";
                case PARROT -> justFed ? "У нас свой красавец уже есть. Только что семечки у меня клевал!"
                        : "Есть у нас попугай. Никто так здорово не передразнивает мобов.";
            };
        }
        return lines[reply];
    }

    /** Null means the shared work queue should track the active order. */
    CompanionActionResult begin(ServerLevel level, String action, String item, int count) {
        boolean sit = action.equals("sit_" + kind.id);
        boolean taming = action.equals("tame_" + kind.id);
        String proposedCourse = taming ? CompanionCourse.petSearchCourse(item, kind) : null;
        if (count != 1 || item != null && !item.isEmpty() && !item.equals("resources") && !(sit && item.equals("home")) && proposedCourse == null)
            return CompanionActionResult.failed("Не понял поручение о " + kind.about + ".");
        if (!enabled) return CompanionActionResult.failed(switch (kind) {
            case CAT -> "«Свой кот» выключен в настройках поведения.";
            case DOG -> "«Своя собака» выключена в настройках поведения.";
            case PARROT -> "«Свой попугай» выключен в настройках поведения.";
        });
        var pet = bonds(level).pet(owner);
        if (!taming) return beginPetOrder(level, action, sit, item, pet);
        if (pet != null) return CompanionActionResult.ok(praise(level, pet));
        if (proposedCourse != null && CompanionCourse.atPetHabitat(level, owner, kind)) {
            beginHabitatSearch(level);
            return null;
        }
        var animal = nearestStray(level, owner.blockPosition());
        if (animal == null) {
            if (proposedCourse != null) {
                habitatCourse = proposedCourse;
                enter(level, Stage.HABITAT_PERMISSION, ANSWER_WAIT_SECONDS);
                return null;
            }
            boolean onlyPets = !level.getEntitiesOfClass(kind.animal, new AABB(owner.blockPosition()).inflate(searchRadius),
                    other -> other.isAlive() && other.isTame() && other.distanceToSqr(owner) <= searchRadius * searchRadius).isEmpty();
            return CompanionActionResult.failed(switch (kind) {
                case CAT -> onlyPets ? "Тут усатые есть, но у каждого уже есть дом." : NO_CAT[Math.floorMod(emptyLine++, NO_CAT.length)];
                case DOG -> onlyPets ? "Тут есть собаки, но все уже с хозяевами." : "Рядом не нашёл дикого волка, которого можно приручить.";
                case PARROT -> onlyPets ? "Все попугаи поблизости уже ручные." : "Диких попугаев рядом не вижу. Они водятся в джунглях.";
            });
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

    void beginHabitatSearch(ServerLevel level) {
        searchOrigin = owner.blockPosition();
        enter(level, Stage.HABITAT_SEARCH, HABITAT_SEARCH_SECONDS);
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
            return CompanionActionResult.failed("Сейчас не вижу " + (kind == CompanionPet.DOG ? "собаки" : kind.whom)
                    + ". Последнее место — " + pet.lastSeen().pos().toShortString() + ".");
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
        return CompanionActionResult.ok((sit ? "Оставил " : "Позвал ") + ourPet()
                + (sit ? " сидеть здесь." : " за собой."));
    }

    private TamableAnimal loadedPet(ServerLevel level, UUID id) {
        return level.getEntity(id) instanceof TamableAnimal animal && animal.isAlive() && kind.animal.isInstance(animal)
                && CompanionPetBonds.belongsTo(owner, animal) ? animal : null;
    }

    private void setSitting(TamableAnimal animal, boolean sit) {
        if (sit && kind == CompanionPet.PARROT && animal.getVehicle() == owner) animal.stopRiding();
        animal.setOrderedToSit(sit);
        if (sit && kind == CompanionPet.DOG) animal.setTarget(null);
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
            case BAIT -> baitDrop == null ? "Ищу " + baitForPet() + " рядом и в сундуках." : "Иду подобрать " + baitForPet() + ".";
            case ANSWER, GIFT -> "Жду " + baitForPet() + ".";
            case PERMISSION -> "Жду ответа, поискать ли приманку рядом и в сундуках.";
            case HABITAT_PERMISSION -> "Жду ответа, идти ли искать " + destination() + ".";
            case HABITAT_SEARCH -> switch (kind) {
                case CAT -> "Ищу бездомную кошку в деревне.";
                case DOG -> "Ищу дикого волка у деревни.";
                case PARROT -> "Ищу дикого попугая в джунглях.";
            };
            case FEED -> switch (kind) {
                case CAT -> "Подманиваю кота рыбой.";
                case DOG -> "Подманиваю волка костью.";
                case PARROT -> "Подманиваю попугая семенами.";
            };
            case FIND -> "Ищу " + ourPet() + ".";
            case HOME -> "Веду " + ourPet() + " домой.";
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
                case HABITAT_PERMISSION -> "Не дождался ответа. Останемся здесь.";
                case HABITAT_SEARCH -> switch (kind) {
                    case CAT -> "В деревне бездомной кошки пока не нашёл.";
                    case DOG -> "У деревни дикого волка пока не нашёл.";
                    case PARROT -> "Осмотрелся в джунглях, но попугая пока не нашёл.";
                };
                case GIFT -> switch (kind) {
                    case CAT -> "Рыбы так и не дождался. Займёмся котом в другой раз.";
                    case DOG -> "Костей так и не дождался. Займёмся собакой в другой раз.";
                    case PARROT -> "Семян так и не дождался. Займёмся попугаем в другой раз.";
                };
                case FEED -> "Подманить " + kind.whom + " так и не получилось. " + keptBait();
                case BAIT -> throw new IllegalStateException("Bait search timeout already handled");
            });
            return;
        }
        if (waitingForHabitat()) {
            if (owner.petQuestion(kind) == 0) {
                enter(level, Stage.HABITAT_PERMISSION, ANSWER_WAIT_SECONDS);
                owner.petQuestion(kind, work.activeTaskId(), CompanionPet.Question.HABITAT_PERMISSION);
                String nearby = switch (kind) {
                    case CAT -> "Рядом кошек не вижу.";
                    case DOG -> "Рядом волков не вижу.";
                    case PARROT -> "Рядом попугаев не вижу.";
                };
                owner.petSay(nearby + " Можем пойти " + CompanionCourse.where(habitatCourse) + " поискать " + destination() + ". Пойдём?");
            }
            return;
        }
        if (stage == Stage.HABITAT_SEARCH) { searchHabitat(level); return; }
        if (stage == Stage.BAIT || waitingForPlayer()) {
            if (baitCount() > 0) { returnToPet(level); return; }
            if (stage == Stage.BAIT) { findBait(level); return; }
            // The queue assigns this order's ID after begin returns.
            if (stage == Stage.ANSWER && owner.petQuestion(kind) == 0) { askForBait(level); return; }
            if (wrongBaitCount() > wrongBaitBefore) {
                wrongBaitBefore = wrongBaitCount();
                owner.petSay(switch (kind) {
                    case CAT -> "Спасибо! Для кота нужна сырая треска или сырой лосось.";
                    case DOG -> "Для волка нужны целые кости. Костную муку он не возьмёт.";
                    case PARROT -> "Для попугая нужны семена. Печеньем его кормить опасно.";
                });
            }
            return;
        }
        switch (stage) {
            case FEED -> feed(level);
            case FIND -> findPet(level);
            case HOME -> goHome(level);
            case BAIT, ANSWER, GIFT, PERMISSION, HABITAT_PERMISSION, HABITAT_SEARCH -> throw new IllegalStateException("Search and replies already handled");
        }
    }

    private void searchHabitat(ServerLevel level) {
        long now = level.getGameTime();
        if (owner.petQuestion(kind) == 0) {
            owner.petQuestion(kind, work.activeTaskId(), CompanionPet.Question.HABITAT_SEARCH);
            String place = kind == CompanionPet.PARROT ? "в джунглях" : "в деревне";
            owner.petSay("Ну вот, мы " + place + ". Давай искать " + kind.whom + " здесь. Когда найдёшь, скажи.");
            nextHabitatScan = now + 20;
        } else if (now >= nextHabitatScan) {
            nextHabitatScan = now + 20;
            lookForHabitatPet(level, owner.blockPosition(), false);
        }
    }

    private void lookForHabitatPet(ServerLevel level, BlockPos origin, boolean playerReported) {
        var animal = nearestStray(level, origin);
        if (animal == null) {
            if (playerReported) owner.petSay("Покажи, где. Я пока не вижу " + foundWild() + ".");
            return;
        }
        owner.petQuestion(kind, 0);
        String found = playerReported ? switch (kind) {
            case CAT -> "Ага, вижу кошку!";
            case DOG -> "Ага, вижу волка!";
            case PARROT -> "Ага, вижу попугая!";
        } : "О, сам нашёл " + foundWild() + "!";
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
        finish(false, switch (kind) {
            case CAT -> fed > 0 ? "Угощение кот принял, а дружить пока думает. Рыба кончилась, отложим."
                    : "Сырой рыбы так и не нашёл. Займёмся котом в другой раз.";
            case DOG -> fed > 0 ? "Кости кончились, а волк пока не приручился. Попробуем в другой раз."
                    : "Костей так и не нашёл. Займёмся собакой в другой раз.";
            case PARROT -> fed > 0 ? "Семена кончились, а попугай ещё дичится. Попробуем в другой раз."
                    : "Семян так и не нашёл. Займёмся попугаем в другой раз.";
        });
    }

    private void askForBait(ServerLevel level) { askForBait(level, ""); }

    private void askForBait(ServerLevel level, String preface) {
        work.resetTarget();
        owner.getNavigation().stop();
        wrongBaitBefore = wrongBaitCount();
        enter(level, Stage.ANSWER, ANSWER_WAIT_SECONDS);
        owner.petQuestion(kind, work.activeTaskId());
        String question = switch (kind) {
            case CAT -> fed > 0 ? "Кот пока не приручился. У тебя есть ещё сырая рыба?" : "У тебя сырой трески или лосося не найдётся? Хочу кота угостить.";
            case DOG -> fed > 0 ? "Волк пока не приручился. У тебя есть ещё кости?" : "У тебя есть кости? Хочу волка приручить.";
            case PARROT -> fed > 0 ? "Попугай пока дичится. У тебя ещё семян не осталось?" : "У тебя есть семена? Хочу попугая угостить.";
        };
        owner.petSay(preface + question);
    }

    void answer(long workId, String answer) {
        if (!(owner.level() instanceof ServerLevel level) || work.activeTaskId() != workId
                || workId <= 0 || owner.petQuestion(kind) != workId || !waitingForPlayer()
                || level.getGameTime() >= stageUntil) return;
        if ("cancel".equals(answer)) {
            owner.petQuestion(kind, 0);
            work.finish(CompanionActionResult.cancelled(waitingForHabitat() ? "Ладно, останемся здесь." : switch (kind) {
                case CAT -> "Ладно, займёмся котом в другой раз.";
                case DOG -> "Ладно, займёмся собакой в другой раз.";
                case PARROT -> "Ладно, займёмся попугаем в другой раз.";
            }));
            return;
        }
        if (waitingForHabitat()) {
            String direction = CompanionCourse.where(habitatCourse);
            if ("go".equals(answer)) {
                var refusal = work.beginCourse(habitatCourse, 0);
                if (refusal != null) { finish(false, refusal.message()); return; }
                owner.petQuestion(kind, 0);
                stage = null;
                owner.workStatus("Идём " + direction + " искать " + destination() + ".");
                String place = kind == CompanionPet.PARROT ? "джунгли" : "деревня";
                owner.petSay("Пойдём " + direction + ". Посмотрим, есть ли там " + place + ".");
            } else if ("clarify".equals(answer)) {
                String place = kind == CompanionPet.PARROT ? "джунгли" : "деревня";
                owner.petSay("Пока не знаю, где " + place + ". Предлагаю пойти " + direction + ". Пойдём или останемся здесь?");
            }
            return;
        }
        if (stage == Stage.HABITAT_SEARCH) {
            if ("found".equals(answer)) {
                var player = owner.getOwnerPlayer();
                if (player == null) return;
                if (player.distanceToSqr(searchOrigin.getCenter()) > searchRadius * searchRadius) {
                    owner.petSay("Я пока осматриваю " + destination() + ". Подойди ближе и покажи, где нашёл.");
                } else lookForHabitatPet(level, player.blockPosition(), true);
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
                owner.petSay(switch (kind) {
                    case CAT -> "Кинь мне пару сырых рыбок или положи их в мою сумку.";
                    case DOG -> "Кинь мне пару костей или положи их в мою сумку.";
                    case PARROT -> "Кинь мне горсть семян или положи их в мою сумку.";
                });
            }
        }
    }

    boolean waitingForHabitat() { return stage == Stage.HABITAT_PERMISSION; }

    private boolean waitingForPlayer() {
        return stage == Stage.ANSWER || stage == Stage.GIFT || stage == Stage.PERMISSION || waitingForHabitat() || stage == Stage.HABITAT_SEARCH;
    }

    void dismissQuestion() {
        if (!waitingForPlayer()) return;
        owner.petQuestion(kind, 0);
        work.finish(CompanionActionResult.cancelled("Отложил поручение о " + kind.about + "."));
    }

    private void returnToPet(ServerLevel level) {
        owner.petQuestion(kind, 0);
        work.closeChest();
        work.resetTarget();
        baitDrop = null;
        enter(level, Stage.FEED, 55);
    }

    private InteractionHand baitHand() {
        if (kind.accepts(owner.getMainHandItem())) return InteractionHand.MAIN_HAND;
        return kind.accepts(owner.getOffhandItem()) ? InteractionHand.OFF_HAND : null;
    }

    private void feed(ServerLevel level) {
        if (!(level.getEntity(target) instanceof TamableAnimal animal) || !kind.isStray(animal)
                || animal.distanceToSqr(lastSeen.getCenter()) > searchRadius * searchRadius) {
            releaseLure();
            if (!walkToLastSeen(level)) return;
            if (!tryAnotherStray(level)) finish(false, "Там, где я видел " + foundWild() + ", уже никого нет. " + keptBait());
            return;
        }
        lastSeen = animal.blockPosition();
        if (baitCount() == 0) {
            releaseLure();
            if (canFindOwnBait) startBaitSearch(level);
            else askForBait(level);
            return;
        }
        int lureRadius = kind == CompanionPet.PARROT ? PARROT_LURE_RADIUS : 6;
        if (!owner.hasLineOfSight(animal) || owner.distanceToSqr(animal) > lureRadius * lureRadius) {
            double reach = kind == CompanionPet.PARROT ? lureRadius : 3.5;
            walkTowardStray(level, animal, reach);
            return;
        }
        var hand = baitHand();
        if (hand == null) {
            if (!owner.equipForWork(BuiltInRegistries.ITEM.getKey(kind.bait(work.items)).toString()).success()) {
                finish(false, "Не смог достать приманку из сумки.");
                return;
            }
            hand = InteractionHand.MAIN_HAND;
        }
        if (lure != null && lure.animal != animal) releaseLure();
        if (lure == null) {
            owner.getNavigation().stop();
            lure = new Lure(animal);
            // Parrots wander even when airborne. Let an interested bird fly down to the seeds.
            animal.goalSelector.addGoal(kind == CompanionPet.PARROT ? 1 : 3, lure);
        }
        owner.getLookControl().setLookAt(animal, 30, 30);
        if (owner.distanceToSqr(animal) > 2 * 2) {
            // The bird can land short of the hand. Meet it on a reachable cell with a clear line of sight.
            if (kind == CompanionPet.PARROT && lure.needsHelp(level.getGameTime())) walkTowardStray(level, animal, 1.5);
            else owner.getNavigation().stop();
            return;
        }
        owner.getNavigation().stop();
        if (level.getGameTime() < nextFeed) return;
        ItemStack held = owner.getItemInHand(hand);
        held.shrink(1);
        owner.setItemInHand(hand, held.isEmpty() ? ItemStack.EMPTY : held);
        owner.refreshInventorySummary();
        owner.swing(hand);
        fed++;
        nextFeed = level.getGameTime() + 18;
        if (kind == CompanionPet.PARROT) animal.playSound(SoundEvents.PARROT_EAT, 1, 1);
        if (animal.getRandom().nextInt(kind == CompanionPet.PARROT ? 10 : 3) == 0 && bonds(level).adopt(level, owner, animal)) {
            owner.petPresent(kind, true);
            owner.observeEvent(kind.id, "Приручил " + foundWild() + ".");
            finish(true, switch (kind) {
                case CAT -> "Получилось! Теперь у нас свой кот.";
                case DOG -> "Получилось! Теперь у нас своя собака.";
                case PARROT -> "Смотри, уже берёт семечки с руки! Теперь у нас свой попугай.";
            });
        } else {
            level.broadcastEntityEvent(animal, (byte) 6);
            if (fed == FEED_LIMIT) finish(false, "Угощение принял, а дружить пока думает. Дадим ему время.");
        }
    }

    private void walkTowardStray(ServerLevel level, TamableAnimal animal, double reach) {
        if (work.target == null || work.target.distSqr(lastSeen) >= 4) {
            work.resetApproach();
            work.target = lastSeen;
        }
        owner.movementIntent(lastSeen);
        if (canWalk() && !work.approachPet(level, animal, reach) && !tryAnotherStray(level))
            finish(false, "Не нашёл, к кому могу подойти. " + keptBait());
    }

    private boolean walkToLastSeen(ServerLevel level) {
        if (owner.distanceToSqr(lastSeen.getCenter()) <= 6 * 6) return true;
        owner.movementIntent(lastSeen);
        if (canWalk() && !work.approachPlace(level, lastSeen, 4) && !tryAnotherStray(level))
            finish(false, "Не смог добраться до места, где видел " + foundWild() + ". " + keptBait());
        return false;
    }

    private boolean canWalk() { return owner.onGround() || owner.isInWater(); }

    private void findPet(ServerLevel level) {
        var animal = loadedPet(level, target);
        if (animal != null) {
            lastSeen = animal.blockPosition();
            if (owner.distanceToSqr(animal) <= 4 * 4 && owner.hasLineOfSight(animal)) {
                finish(true, switch (kind) {
                    case CAT -> "Вот наш мурлыка, я его нашёл!";
                    case DOG -> "Вот наша собака, я её нашёл!";
                    case PARROT -> animal.getVehicle() == owner ? "Наш попугай у меня на плече!" : "А вот и наш попугай!";
                });
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
            if (canWalk() && !work.approachPlace(level, home, 3.5)) finish(false, "Не смог привести " + kind.whom + " домой.");
            return;
        }
        owner.getNavigation().stop();
        var animal = loadedPet(level, target);
        if (animal != null && animal.distanceToSqr(home.getCenter()) <= 6 * 6) {
            setSitting(animal, true);
            finish(true, "Довёл " + ourPet() + " домой. Пусть тут посидит.");
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
        habitatCourse = null;
        searchOrigin = lastSeen = home = null;
        skippedStrays.clear();
        skippedBait.clear();
    }

    private final class Lure extends Goal {
        private final TamableAnimal animal;
        private double closest = Double.POSITIVE_INFINITY;
        private long lastProgress;
        private boolean closingGap;

        Lure(TamableAnimal animal) {
            this.animal = animal;
            setFlags(kind == CompanionPet.PARROT ? EnumSet.of(Flag.MOVE) : EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        boolean needsHelp(long now) {
            if (closingGap) return true;
            double distance = animal.distanceTo(owner);
            if (distance < closest - .5) {
                closest = distance;
                lastProgress = now;
            }
            if (now - lastProgress >= 20 * 4) {
                closingGap = true;
                work.resetTarget();
            }
            return closingGap;
        }

        @Override public boolean canUse() {
            int radius = kind == CompanionPet.PARROT ? PARROT_LURE_RADIUS : 8;
            return stage == Stage.FEED && kind.isStray(animal) && owner.isAlive() && owner.level() == animal.level()
                    && baitHand() != null && animal.distanceToSqr(owner) <= radius * radius && animal.hasLineOfSight(owner);
        }

        @Override public boolean requiresUpdateEveryTick() { return kind == CompanionPet.PARROT; }

        @Override public void tick() {
            animal.getLookControl().setLookAt(owner, 30, 30);
            if (animal.distanceToSqr(owner) <= 1.5 * 1.5) animal.getNavigation().stop();
            else if (kind == CompanionPet.PARROT) {
                // Flying paths may end in a treetop. Fly toward the visible food with the bird's own movement controller.
                var hand = owner.getEyePosition().subtract(0, .45, 0);
                if (animal.level().noBlockCollision(animal, animal.getBoundingBox().move(hand.subtract(animal.position())))) {
                    animal.getNavigation().stop();
                    animal.getMoveControl().setWantedPosition(hand.x, hand.y, hand.z, .8);
                }
            } else if (animal.tickCount % 10 == 0 || animal.getNavigation().isDone()) {
                animal.getNavigation().moveTo(owner, .6);
            }
        }

        @Override public void stop() { animal.getNavigation().stop(); }
    }
}

package dev.aicompanion.entity;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum CompanionPet {
    CAT("cat", Cat.class, "village", "коте", "кота"),
    DOG("dog", Wolf.class, "village", "собаке", "собаку"),
    PARROT("parrot", Parrot.class, "jungle", "попугае", "попугая");

    public enum Question { BAIT, BAIT_PERMISSION, HABITAT_PERMISSION, HABITAT_SEARCH }

    public static final int MIN_SEARCH_RADIUS = 32, DEFAULT_SEARCH_RADIUS = 64, MAX_SEARCH_RADIUS = 128;

    public final String id;
    public final String habitat;
    final Class<? extends TamableAnimal> animal;
    final String about;
    final String whom;

    CompanionPet(String id, Class<? extends TamableAnimal> animal, String habitat, String about, String whom) {
        this.id = id;
        this.habitat = habitat;
        this.animal = animal;
        this.about = about;
        this.whom = whom;
    }

    public static CompanionPet byId(String id) {
        for (CompanionPet kind : values()) if (kind.id.equals(id)) return kind;
        return null;
    }

    public static CompanionPet forTamingAction(String action) {
        return action != null && action.startsWith("tame_") ? byId(action.substring("tame_".length())) : null;
    }

    boolean accepts(ItemStack stack) {
        return switch (this) {
            case CAT -> stack.is(Items.COD) || stack.is(Items.SALMON);
            case DOG -> stack.is(Items.BONE);
            case PARROT -> stack.is(ItemTags.PARROT_FOOD);
        };
    }

    Item bait(CompanionWorkItems items) {
        return switch (this) {
            case CAT -> items.countCarried(Items.COD) > 0 ? Items.COD : Items.SALMON;
            case DOG -> Items.BONE;
            case PARROT -> items.carriedItems().stream().filter(this::accepts)
                    .map(ItemStack::getItem).findFirst().orElse(Items.AIR);
        };
    }

    boolean isStray(TamableAnimal pet) {
        return animal.isInstance(pet) && pet.isAlive() && !pet.isTame() && pet.getOwnerUUID() == null
                && (!(pet instanceof Wolf wolf) || !wolf.isAngry());
    }
}

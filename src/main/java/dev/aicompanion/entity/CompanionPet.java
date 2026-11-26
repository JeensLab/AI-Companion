package dev.aicompanion.entity;

import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum CompanionPet {
    CAT("cat", Cat.class, "коте", "кота"),
    DOG("dog", Wolf.class, "собаке", "собаку");

    public enum Question { BAIT, BAIT_PERMISSION, VILLAGE_PERMISSION, VILLAGE_SEARCH }

    public static final int MIN_SEARCH_RADIUS = 32, DEFAULT_SEARCH_RADIUS = 64, MAX_SEARCH_RADIUS = 128;

    public final String id;
    final Class<? extends TamableAnimal> animal;
    final String about;
    final String whom;

    CompanionPet(String id, Class<? extends TamableAnimal> animal, String about, String whom) {
        this.id = id;
        this.animal = animal;
        this.about = about;
        this.whom = whom;
    }

    boolean accepts(ItemStack stack) {
        return switch (this) {
            case CAT -> stack.is(Items.COD) || stack.is(Items.SALMON);
            case DOG -> stack.is(Items.BONE);
        };
    }

    Item bait(CompanionWorkItems items) {
        return switch (this) {
            case CAT -> items.countCarried(Items.COD) > 0 ? Items.COD : Items.SALMON;
            case DOG -> Items.BONE;
        };
    }

    boolean isStray(TamableAnimal pet) {
        return animal.isInstance(pet) && pet.isAlive() && !pet.isTame() && pet.getOwnerUUID() == null
                && (!(pet instanceof Wolf wolf) || !wolf.isAngry());
    }
}

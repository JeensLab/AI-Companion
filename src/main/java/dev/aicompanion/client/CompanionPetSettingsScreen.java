package dev.aicompanion.client;

import dev.aicompanion.OpenRouterConfig;
import dev.aicompanion.entity.CompanionPet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class CompanionPetSettingsScreen extends Screen {
    private final Screen parent;

    CompanionPetSettingsScreen(Screen parent) {
        super(Component.literal("Свой питомец"));
        this.parent = parent;
    }

    @Override protected void init() {
        int rowWidth = Math.min(300, width - 16);
        int left = (width - rowWidth) / 2;
        int top = Math.max(32, (height - 100) / 2);
        addRenderableWidget(Button.builder(catLabel(), button -> {
            OpenRouterConfig.PET_CAT.set(!OpenRouterConfig.PET_CAT.get());
            OpenRouterConfig.PET_CAT.save();
            button.setMessage(catLabel());
            CompanionPreferences.sync();
        }).bounds(left, top, rowWidth, 20).build());
        addRenderableWidget(Button.builder(dogLabel(), button -> {
            OpenRouterConfig.PET_DOG.set(!OpenRouterConfig.PET_DOG.get());
            OpenRouterConfig.PET_DOG.save();
            button.setMessage(dogLabel());
            CompanionPreferences.sync();
        }).bounds(left, top + 24, rowWidth, 20).build());
        addRenderableWidget(new SearchRadiusSlider(left, top + 48, rowWidth));
        addRenderableWidget(Button.builder(starterBaitLabel(), button -> {
            OpenRouterConfig.PET_STARTER_BAIT.set(!OpenRouterConfig.PET_STARTER_BAIT.get());
            OpenRouterConfig.PET_STARTER_BAIT.save();
            button.setMessage(starterBaitLabel());
            CompanionPreferences.sync();
        }).bounds(left, top + 72, rowWidth, 20).tooltip(Tooltip.create(Component.literal(
                "Один раз за мир Олег получит в сумку 3 сырых лосося и 3 кости."))).build());
        addRenderableWidget(Button.builder(Component.literal("Назад"), button -> onClose())
                .bounds(width / 2 - 50, height - 35, 100, 20).build());
    }

    private static Component catLabel() { return Component.literal("Свой кот: " + (OpenRouterConfig.PET_CAT.get() ? "вкл." : "выкл.")); }
    private static Component dogLabel() { return Component.literal("Своя собака: " + (OpenRouterConfig.PET_DOG.get() ? "вкл." : "выкл.")); }
    private static Component starterBaitLabel() { return Component.literal("Начальная приманка: " + (OpenRouterConfig.PET_STARTER_BAIT.get() ? "вкл." : "выкл.")); }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }

    private static final class SearchRadiusSlider extends AbstractSliderButton {
        private static final int MIN = CompanionPet.MIN_SEARCH_RADIUS;
        private static final int SPAN = CompanionPet.MAX_SEARCH_RADIUS - MIN;

        SearchRadiusSlider(int x, int y, int width) {
            super(x, y, width, 20, Component.empty(), (OpenRouterConfig.PET_SEARCH_RADIUS.get() - MIN) / (double) SPAN);
            updateMessage();
            setTooltip(Tooltip.create(Component.literal("Расстояние от Олега до бездомной кошки или дикого волка в загруженных чанках. От 32 до 128 блоков.")));
        }

        private int radius() { return MIN + (int) Math.round(value * SPAN); }

        @Override protected void updateMessage() {
            setMessage(Component.literal("Радиус поиска: " + radius() + " бл."));
        }

        @Override protected void applyValue() {
            int radius = radius();
            if (radius == OpenRouterConfig.PET_SEARCH_RADIUS.get()) return;
            OpenRouterConfig.PET_SEARCH_RADIUS.set(radius);
            OpenRouterConfig.PET_SEARCH_RADIUS.save();
            CompanionPreferences.sync();
        }
    }
}

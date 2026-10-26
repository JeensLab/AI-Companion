package dev.aicompanion.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The keys of the schematic placement in the mod's own settings: click a key, press the new one (or a mouse button). The same
 * bindings as in Controls → «ИИ-компаньон», saved to options.txt the same way.
 */
final class CompanionSchematicKeysScreen extends Screen {
    private final Screen parent;
    private final List<Button> buttons = new ArrayList<>();
    private KeyMapping waiting;

    CompanionSchematicKeysScreen(Screen parent) { super(Component.literal("Клавиши схем домов")); this.parent = parent; }

    @Override protected void init() {
        buttons.clear();
        List<KeyMapping> keys = CompanionSchematicPlacement.KEYS;
        int rows = (keys.size() + 1) / 2, columnWidth = Math.min(300, (width - 40) / 2), gap = 24;
        int top = Math.max(36, (height - rows * gap) / 2 - 10);
        for (int index = 0; index < keys.size(); index++) {
            KeyMapping key = keys.get(index);
            int column = index / rows, row = index % rows;
            int x = width / 2 - columnWidth - 5 + column * (columnWidth + 10);
            buttons.add(addRenderableWidget(Button.builder(label(key), button -> { waiting = key; refresh(); })
                    .bounds(x, top + row * gap, columnWidth, 20).build()));
        }
        addRenderableWidget(Button.builder(Component.literal("Сброс"), button -> {
            for (KeyMapping key : keys) { key.setToDefault(); minecraft.options.setKey(key, key.getDefaultKey()); }
            changed();
        }).bounds(width / 2 - 104, height - 35, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Готово"), button -> onClose()).bounds(width / 2 + 4, height - 35, 100, 20).build());
    }

    private Component label(KeyMapping key) {
        String name = Component.translatable(key.getName()).getString().replaceFirst("^Схема дома: ", "");
        if (!name.isEmpty()) name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        if (key == waiting) return Component.literal(name + ": > ? <").withStyle(ChatFormatting.YELLOW);
        Component bound = key.getTranslatedKeyMessage();
        return Component.literal(name + ": ").append(conflicts(key) ? bound.copy().withStyle(ChatFormatting.RED) : bound);
    }

    /** Another binding of the game on the same key: shown red, as in the game's own controls. */
    private boolean conflicts(KeyMapping key) {
        if (key.isUnbound()) return false;
        for (KeyMapping other : minecraft.options.keyMappings) if (other != key && other.same(key)) return true;
        return false;
    }

    private void refresh() {
        List<KeyMapping> keys = CompanionSchematicPlacement.KEYS;
        for (int index = 0; index < buttons.size(); index++) buttons.get(index).setMessage(label(keys.get(index)));
    }

    private void bind(InputConstants.Key input) {
        waiting.setKeyModifierAndCode(net.neoforged.neoforge.client.settings.KeyModifier.NONE, input);
        minecraft.options.setKey(waiting, input);
        waiting = null;
        changed();
    }

    private void changed() {
        KeyMapping.resetMapping();
        minecraft.options.save();
        refresh();
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (waiting == null) return super.keyPressed(keyCode, scanCode, modifiers);
        // Escape gives up the change, Delete leaves the action without a key; anything else becomes its key.
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { waiting = null; refresh(); return true; }
        bind(keyCode == GLFW.GLFW_KEY_DELETE ? InputConstants.UNKNOWN : InputConstants.getKey(keyCode, scanCode));
        return true;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (waiting == null) return super.mouseClicked(mouseX, mouseY, button);
        bind(InputConstants.Type.MOUSE.getOrCreate(button));
        return true;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        String[] lines = waiting != null ? new String[]{"Нажми новую клавишу или кнопку мыши.", "Escape — отмена, Delete — без клавиши."}
                : new String[]{"Работают, пока выбираешь место для дома.", "Красным — клавиша занята другим действием."};
        graphics.drawCenteredString(font, lines[0], width / 2, height - 66, 0xB8C3D1);
        graphics.drawCenteredString(font, lines[1], width / 2, height - 54, 0xB8C3D1);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}

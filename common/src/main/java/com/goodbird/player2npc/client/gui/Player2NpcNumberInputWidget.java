package com.goodbird.player2npc.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Integer-only text field that commits a clamped value on Enter or focus loss.
 */
public final class Player2NpcNumberInputWidget extends EditBox {
    private final int minimum;
    private final int maximum;
    private final IntConsumer onCommit;
    private UiLayoutRect clip;
    private int committedValue;
    private boolean dirty;
    private boolean updatingValue;

    public Player2NpcNumberInputWidget(Font font, int x, int y, int width, int height,
                                       int minimum, int maximum, int initialValue,
                                       Component label, Consumer<String> onDraftChanged,
                                       IntConsumer onCommit) {
        super(font, x, y, width, height, label);
        if (maximum < minimum || minimum < 0) {
            throw new IllegalArgumentException("Invalid numeric input range");
        }
        this.minimum = minimum;
        this.maximum = maximum;
        this.onCommit = onCommit;
        this.committedValue = clamp(initialValue);
        setMaxLength(String.valueOf(maximum).length());
        setFilter(Player2NpcNumberInputWidget::isUnsignedIntegerText);
        replaceValue(String.valueOf(committedValue));
        setTextColor(Player2NpcUiTheme.TEXT);
        setTextColorUneditable(Player2NpcUiTheme.TEXT_DIM);
        setResponder(value -> {
            if (!updatingValue) {
                dirty = true;
                onDraftChanged.accept(value);
            }
        });
    }

    public Player2NpcNumberInputWidget withClip(UiLayoutRect clip) {
        this.clip = clip;
        return this;
    }

    public void setDraftValue(String value) {
        if (value == null || !isUnsignedIntegerText(value)
                || value.length() > String.valueOf(maximum).length()) {
            return;
        }
        replaceValue(value);
        dirty = true;
    }

    public void setCommittedValue(int value) {
        committedValue = clamp(value);
        dirty = false;
        replaceValue(String.valueOf(committedValue));
    }

    public void setPreviewValue(int value) {
        replaceValue(String.valueOf(clamp(value)));
    }

    public boolean commit() {
        if (!dirty) {
            return false;
        }
        int value = parseOrCommitted(getValue());
        committedValue = value;
        dirty = false;
        replaceValue(String.valueOf(value));
        onCommit.accept(value);
        return true;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (clip != null) {
            graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
        }
        super.renderWidget(graphics, mouseX, mouseY, delta);
        if (clip != null) {
            graphics.disableScissor();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return isInsideClip(mouseX, mouseY) && super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isFocused() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            setFocused(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void setFocused(boolean focused) {
        boolean wasFocused = isFocused();
        super.setFocused(focused);
        if (wasFocused && !isFocused()) {
            commit();
        }
    }

    private void replaceValue(String value) {
        updatingValue = true;
        setValue(value);
        updatingValue = false;
    }

    private int parseOrCommitted(String raw) {
        if (raw == null || raw.isEmpty()) {
            return committedValue;
        }
        try {
            return clamp(Integer.parseInt(raw));
        } catch (NumberFormatException ignored) {
            return maximum;
        }
    }

    private int clamp(int value) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static boolean isUnsignedIntegerText(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private boolean isInsideClip(double mouseX, double mouseY) {
        return clip == null || mouseX >= clip.x() && mouseX < clip.right()
                && mouseY >= clip.y() && mouseY < clip.bottom();
    }
}

package com.goodbird.player2npc.client.gui.settings;

import com.goodbird.player2npc.client.gui.Player2NpcUiTheme;
import com.goodbird.player2npc.client.gui.UiLayoutRect;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Bounded, clipped, single-line text editor used by declarative settings controls. */
public final class Player2NpcTextInputWidget extends EditBox {
    private UiLayoutRect clip;
    private boolean replacingValue;

    public Player2NpcTextInputWidget(
            Font font,
            int x,
            int y,
            int width,
            int height,
            int maxLength,
            Component narration,
            Consumer<String> onDraftChanged) {
        super(font, x, y, width, height, narration);
        if (maxLength <= 0) {
            throw new IllegalArgumentException("Text input length cap must be positive");
        }
        setMaxLength(maxLength);
        setFilter(Player2NpcTextInputWidget::isSingleLine);
        setTextColor(Player2NpcUiTheme.TEXT);
        setTextColorUneditable(Player2NpcUiTheme.TEXT_DIM);
        setResponder(value -> {
            if (!replacingValue) {
                onDraftChanged.accept(value);
            }
        });
    }

    public Player2NpcTextInputWidget withClip(UiLayoutRect clip) {
        this.clip = clip;
        return this;
    }

    public void setDraftValue(String value) {
        replacingValue = true;
        setValue(value == null ? "" : value);
        replacingValue = false;
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

    private static boolean isSingleLine(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\n' || c == '\r') {
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

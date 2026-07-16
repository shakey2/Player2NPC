package com.goodbird.player2npc.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public final class Player2NpcToggleWidget extends AbstractWidget {
    private final boolean on;
    private final Runnable onPress;
    private UiLayoutRect clip;

    public Player2NpcToggleWidget(int x, int y, int width, int height,
                                  Component message, boolean on, Runnable onPress) {
        super(x, y, width, height, message);
        this.on = on;
        this.onPress = onPress;
    }

    public Player2NpcToggleWidget withClip(UiLayoutRect clip) {
        this.clip = clip;
        return this;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (clip != null) {
            graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
        }
        UiRenderCompat.drawToggle(graphics, getX(), getY(), getWidth(), getHeight(), on,
                active, isHoveredOrFocused());
        if (clip != null) {
            graphics.disableScissor();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isInsideClip(mouseX, mouseY)) {
            return false;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (active && visible && isInsideClip(mouseX, mouseY)) {
            onPress.run();
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        builder.add(NarratedElementType.TITLE, getMessage());
    }

    private boolean isInsideClip(double mouseX, double mouseY) {
        return clip == null || mouseX >= clip.x() && mouseX < clip.right()
                && mouseY >= clip.y() && mouseY < clip.bottom();
    }
}

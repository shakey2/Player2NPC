package com.goodbird.player2npc.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;

public class Player2NpcPromptBoxWidget extends MultiLineEditBox {
    private UiLayoutRect clip;

    public Player2NpcPromptBoxWidget(Font font, int x, int y, int width, int height,
                                     Component placeholder, Component message) {
        super(font, x, y, width, height, placeholder, message);
    }

    public Player2NpcPromptBoxWidget withClip(UiLayoutRect clip) {
        this.clip = clip;
        return this;
    }

    public void setClip(UiLayoutRect clip) {
        this.clip = clip;
    }

    public void setHeight(int height) {
        this.height = height;
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
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return isInsideClip(mouseX, mouseY) && super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return isInsideClip(mouseX, mouseY) && super.mouseScrolled(mouseX, mouseY, delta);
    }

    private boolean isInsideClip(double mouseX, double mouseY) {
        return clip == null || mouseX >= clip.x() && mouseX < clip.right()
                && mouseY >= clip.y() && mouseY < clip.bottom();
    }
}

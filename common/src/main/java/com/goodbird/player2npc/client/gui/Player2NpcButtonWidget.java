package com.goodbird.player2npc.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class Player2NpcButtonWidget extends AbstractWidget {
    private final Runnable onPress;
    private final boolean primary;
    private final ResourceLocation icon;
    private UiLayoutRect clip;

    public Player2NpcButtonWidget(int x, int y, int width, int height, Component message, boolean primary, Runnable onPress) {
        this(x, y, width, height, message, primary, null, onPress);
    }

    public Player2NpcButtonWidget(int x, int y, int width, int height, Component message, boolean primary,
                                  ResourceLocation icon, Runnable onPress) {
        super(x, y, width, height, message);
        this.primary = primary;
        this.icon = icon;
        this.onPress = onPress;
    }

    public Player2NpcButtonWidget withClip(UiLayoutRect clip) {
        this.clip = clip;
        return this;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (clip != null) {
            graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
        }
        UiRenderCompat.drawButton(graphics, getX(), getY(), getWidth(), getHeight(), active, isHoveredOrFocused(), primary);
        if (icon == null) {
            int color = active ? Player2NpcUiTheme.TEXT : Player2NpcUiTheme.TEXT_DIM;
            var gameFont = Minecraft.getInstance().font;
            Component visualMessage = getMessage();
            int maxTextWidth = Math.max(1, getWidth() - 8);
            if (gameFont.width(visualMessage) > maxTextWidth) {
                String ellipsis = "...";
                int bodyWidth = Math.max(0, maxTextWidth - gameFont.width(ellipsis));
                visualMessage = Component.literal(
                        gameFont.plainSubstrByWidth(visualMessage.getString(), bodyWidth) + ellipsis);
            }
            graphics.drawCenteredString(gameFont, visualMessage, getX() + getWidth() / 2,
                    getY() + (getHeight() - 8) / 2, color);
        } else {
            int iconSize = Math.max(8, Math.min(16, Math.min(getWidth(), getHeight()) - 2));
            UiRenderCompat.drawIcon(graphics, icon, getX() + (getWidth() - iconSize) / 2, getY() + (getHeight() - iconSize) / 2, iconSize);
        }
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!active || !visible || !CommonInputs.selected(keyCode)) {
            return false;
        }
        playDownSound(Minecraft.getInstance().getSoundManager());
        onPress.run();
        return true;
    }

    private boolean isInsideClip(double mouseX, double mouseY) {
        return clip == null || mouseX >= clip.x() && mouseX < clip.right() && mouseY >= clip.y() && mouseY < clip.bottom();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        defaultButtonNarrationText(builder);
    }
}

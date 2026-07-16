package com.goodbird.player2npc.client.gui;

import com.goodbird.player2npc.Player2NPC;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class UiRenderCompat {
    private static final int PANEL_SOURCE_SIZE = 512;
    private static final int BUTTON_SOURCE_WIDTH = 512;
    private static final int BUTTON_SOURCE_HEIGHT = 96;
    private static final int ICON_SOURCE_SIZE = 16;
    private static final int LOGO_SOURCE_WIDTH = 1024;
    private static final int LOGO_SOURCE_HEIGHT = 256;
    private static final ResourceLocation WINDOW = Player2NPC.id("textures/gui/sprites/panel/window.png");
    private static final ResourceLocation CARD = Player2NPC.id("textures/gui/sprites/panel/card.png");
    private static final ResourceLocation LOGO = Player2NPC.id("textures/gui/sprites/brand/player2_ai_npcs.png");
    private static final ResourceLocation BUTTON_AUTH = Player2NPC.id("textures/gui/sprites/widget/button_auth_normal.png");
    private static final ResourceLocation BUTTON_AUTH_HOVER = Player2NPC.id("textures/gui/sprites/widget/button_auth_hover.png");
    private static final ResourceLocation BUTTON_AUTH_DISABLED = Player2NPC.id("textures/gui/sprites/widget/button_auth_disabled.png");
    private static final ResourceLocation BUTTON_PRIMARY = Player2NPC.id("textures/gui/sprites/widget/button_primary_normal.png");
    private static final ResourceLocation BUTTON_PRIMARY_HOVER = Player2NPC.id("textures/gui/sprites/widget/button_primary_hover.png");
    private static final ResourceLocation BUTTON_PRIMARY_DISABLED = Player2NPC.id("textures/gui/sprites/widget/button_primary_disabled.png");
    private static final ResourceLocation BUTTON_SECONDARY = Player2NPC.id("textures/gui/sprites/widget/button_secondary_normal.png");
    private static final ResourceLocation BUTTON_SECONDARY_HOVER = Player2NPC.id("textures/gui/sprites/widget/button_secondary_hover.png");
    private static final ResourceLocation BUTTON_SECONDARY_DISABLED = Player2NPC.id("textures/gui/sprites/widget/button_secondary_disabled.png");

    private UiRenderCompat() {
    }

    public static void fillPanel(GuiGraphics graphics, int x, int y, int width, int height, int fillColor) {
        blit(graphics, CARD, x, y, width, height, PANEL_SOURCE_SIZE, PANEL_SOURCE_SIZE);
        graphics.fill(x, y, x + width, y + height, fillColor);
        graphics.fill(x, y, x + width, y + 1, Player2NpcUiTheme.PANEL_BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, Player2NpcUiTheme.PANEL_BORDER);
        graphics.fill(x, y, x + 1, y + height, Player2NpcUiTheme.PANEL_BORDER);
        graphics.fill(x + width - 1, y, x + width, y + height, Player2NpcUiTheme.PANEL_BORDER);
    }

    public static void fillWindow(GuiGraphics graphics, int screenWidth, int screenHeight, int x, int y, int width, int height) {
        graphics.fill(0, 0, screenWidth, screenHeight, Player2NpcUiTheme.OVERLAY);
        blit(graphics, WINDOW, x, y, width, height, PANEL_SOURCE_SIZE, PANEL_SOURCE_SIZE);
        graphics.fill(x, y, x + width, y + height, Player2NpcUiTheme.WINDOW);
        graphics.fill(x, y, x + width, y + 1, Player2NpcUiTheme.WINDOW_BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, Player2NpcUiTheme.WINDOW_BORDER);
        graphics.fill(x, y, x + 1, y + height, Player2NpcUiTheme.WINDOW_BORDER);
        graphics.fill(x + width - 1, y, x + width, y + height, Player2NpcUiTheme.WINDOW_BORDER);
    }

    public static void drawButton(GuiGraphics graphics, int x, int y, int width, int height, boolean active, boolean hovered, boolean primary) {
        ResourceLocation texture = !active
                ? (primary ? BUTTON_PRIMARY_DISABLED : BUTTON_SECONDARY_DISABLED)
                : hovered
                ? (primary ? BUTTON_PRIMARY_HOVER : BUTTON_SECONDARY_HOVER)
                : (primary ? BUTTON_PRIMARY : BUTTON_SECONDARY);
        blit(graphics, texture, x, y, width, height, BUTTON_SOURCE_WIDTH, BUTTON_SOURCE_HEIGHT);
    }

    public static void drawToggle(GuiGraphics graphics, int x, int y, int width, int height,
                                  boolean on, boolean active, boolean hovered) {
        int border = !active
                ? 0x6673838F
                : hovered ? Player2NpcUiTheme.ACCENT : Player2NpcUiTheme.PANEL_BORDER;
        int background = on && active ? 0xFF31536A : 0xCC12172A;
        graphics.fill(x, y, x + width, y + height, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, background);
        int knobSize = Math.max(8, height - 6);
        int knobX = on ? x + width - knobSize - 3 : x + 3;
        int knobY = y + (height - knobSize) / 2;
        int knobColor = active ? Player2NpcUiTheme.TEXT : Player2NpcUiTheme.TEXT_DIM;
        graphics.fill(knobX, knobY, knobX + knobSize, knobY + knobSize, knobColor);
    }

    public static void drawSlider(GuiGraphics graphics, int x, int y, int width, int height,
                                  double value, boolean active, boolean hovered) {
        int trackLeft = x + 4;
        int trackRight = x + Math.max(5, width - 4);
        int trackY = y + height / 2 - 2;
        graphics.fill(trackLeft, trackY, trackRight, trackY + 4, 0xAA12172A);
        double clamped = Math.max(0.0D, Math.min(value, 1.0D));
        int handleCenter = trackLeft + (int) Math.round((trackRight - trackLeft) * clamped);
        int progressColor = active ? Player2NpcUiTheme.ACCENT : Player2NpcUiTheme.TEXT_DIM;
        graphics.fill(trackLeft, trackY, Math.max(trackLeft + 1, handleCenter), trackY + 4, progressColor);
        int handleWidth = 8;
        int handleHeight = Math.max(10, height - 4);
        int handleX = Math.max(x, Math.min(handleCenter - handleWidth / 2, x + width - handleWidth));
        int handleY = y + (height - handleHeight) / 2;
        int handleBorder = active && hovered ? Player2NpcUiTheme.TEXT : Player2NpcUiTheme.PANEL_BORDER;
        graphics.fill(handleX, handleY, handleX + handleWidth, handleY + handleHeight, handleBorder);
        graphics.fill(handleX + 1, handleY + 1, handleX + handleWidth - 1, handleY + handleHeight - 1,
                active ? Player2NpcUiTheme.ACCENT : Player2NpcUiTheme.TEXT_DIM);
    }

    public static void drawAuthButton(GuiGraphics graphics, int x, int y, int width, int height, boolean active, boolean hovered) {
        ResourceLocation texture = !active
                ? BUTTON_AUTH_DISABLED
                : hovered
                ? BUTTON_AUTH_HOVER
                : BUTTON_AUTH;
        blit(graphics, texture, x, y, width, height, BUTTON_SOURCE_WIDTH, BUTTON_SOURCE_HEIGHT);
    }

    public static void drawIcon(GuiGraphics graphics, ResourceLocation texture, int x, int y, int size) {
        blit(graphics, texture, x, y, size, size, ICON_SOURCE_SIZE, ICON_SOURCE_SIZE);
    }

    public static void drawLogo(GuiGraphics graphics, int x, int y, int maxWidth, int maxHeight) {
        if (maxWidth <= 0 || maxHeight <= 0) {
            return;
        }
        int width = maxWidth;
        int height = Math.max(1, Math.round((float) width * LOGO_SOURCE_HEIGHT / LOGO_SOURCE_WIDTH));
        if (height > maxHeight) {
            height = maxHeight;
            width = Math.max(1, Math.round((float) height * LOGO_SOURCE_WIDTH / LOGO_SOURCE_HEIGHT));
        }
        blit(graphics, LOGO, x, y, width, height, LOGO_SOURCE_WIDTH, LOGO_SOURCE_HEIGHT);
    }

    public static void drawScrollbar(GuiGraphics graphics, int x, int y, int height, int contentHeight, int viewportHeight, int scrollOffset) {
        if (height <= 0 || viewportHeight <= 0 || contentHeight <= viewportHeight) {
            return;
        }
        int trackWidth = 3;
        int thumbHeight = Math.max(12, height * viewportHeight / contentHeight);
        int maxScroll = Math.max(1, contentHeight - viewportHeight);
        int thumbTravel = Math.max(1, height - thumbHeight);
        int clampedScroll = Math.max(0, Math.min(scrollOffset, maxScroll));
        int thumbY = y + (int) ((long) thumbTravel * clampedScroll / maxScroll);
        graphics.fill(x, y, x + trackWidth, y + height, 0x553E6F8F);
        graphics.fill(x, thumbY, x + trackWidth, thumbY + thumbHeight, 0xCC6FD0FF);
    }

    private static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height, int sourceWidth, int sourceHeight) {
        graphics.blit(texture, x, y, width, height, 0.0F, 0.0F, sourceWidth, sourceHeight, sourceWidth, sourceHeight);
    }
}

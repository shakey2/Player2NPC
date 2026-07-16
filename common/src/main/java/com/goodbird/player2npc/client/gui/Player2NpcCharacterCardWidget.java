package com.goodbird.player2npc.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.goodbird.player2npc.client.util.SkinManager;
import com.player2.playerengine.player2api.Character;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class Player2NpcCharacterCardWidget extends AbstractWidget {
    private static final ResourceLocation HEART_CONTAINER_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/container");
    private static final ResourceLocation HEART_FULL_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final ResourceLocation HEART_HALF_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/half");
    private static final ResourceLocation FOOD_EMPTY_SPRITE = ResourceLocation.withDefaultNamespace("hud/food_empty");
    private static final ResourceLocation FOOD_FULL_SPRITE = ResourceLocation.withDefaultNamespace("hud/food_full");
    private static final ResourceLocation FOOD_HALF_SPRITE = ResourceLocation.withDefaultNamespace("hud/food_half");

    private final Character character;
    private final boolean connected;
    private final Player2NpcHubScreen.CompanionVitals vitals;
    private UiLayoutRect clip;

    public Player2NpcCharacterCardWidget(int x, int y, int width, int height, Character character, boolean connected,
                                         Player2NpcHubScreen.CompanionVitals vitals) {
        super(x, y, width, height, Component.nullToEmpty(character.shortName()));
        this.character = character;
        this.connected = connected;
        this.vitals = vitals;
        this.active = false;
    }

    public Player2NpcCharacterCardWidget withClip(UiLayoutRect clip) {
        this.clip = clip;
        return this;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (clip != null) {
            graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
        }
        UiRenderCompat.fillPanel(graphics, getX(), getY(), getWidth(), getHeight(), 0xAA22313D);
        ResourceLocation skinId = SkinManager.getSkinIdentifier(character.skinURL());
        SkinManager.renderSkinHead(graphics, getX() + 6, getY() + 6, 28, skinId);
        Minecraft client = Minecraft.getInstance();
        graphics.drawString(client.font, Component.nullToEmpty(clamp(character.shortName(), 22)), getX() + 40, getY() + 5, Player2NpcUiTheme.TEXT, false);
        graphics.drawString(client.font, Component.nullToEmpty(clamp(character.name(), 28)), getX() + 40, getY() + 17, Player2NpcUiTheme.TEXT_MUTED, false);
        graphics.drawString(client.font, Player2NpcHubScreen.companionStatus(connected), getX() + 40, getY() + 29,
                connected ? Player2NpcUiTheme.GOOD : Player2NpcUiTheme.BAD, false);
        if (connected) {
            renderVitals(graphics, getX() + getWidth() - 6, getY() + getHeight() - 1, vitals);
        }
        if (clip != null) {
            graphics.disableScissor();
        }
    }

    static void renderVitals(GuiGraphics graphics, int right, int bottom, Player2NpcHubScreen.CompanionVitals vitals) {
        if (vitals == null || vitals.maxHealth() <= 0) {
            return;
        }
        int hearts = Math.max(1, Math.min(10, (vitals.maxHealth() + 1) / 2));
        int heartStartX = right - (hearts * 8 + 1);
        int health = Math.max(0, Math.min(vitals.health(), vitals.maxHealth()));
        int food = Math.max(0, Math.min(vitals.foodLevel(), 20));
        RenderSystem.enableBlend();
        for (int i = 0; i < hearts; i++) {
            int x = heartStartX + i * 8;
            int y = bottom - 20;
            graphics.blitSprite(HEART_CONTAINER_SPRITE, x, y, 9, 9);
            if (i * 2 + 1 < health) {
                graphics.blitSprite(HEART_FULL_SPRITE, x, y, 9, 9);
            } else if (i * 2 + 1 == health) {
                graphics.blitSprite(HEART_HALF_SPRITE, x, y, 9, 9);
            }
        }
        for (int i = 0; i < 10; i++) {
            int x = right - i * 8 - 9;
            int y = bottom - 10;
            graphics.blitSprite(FOOD_EMPTY_SPRITE, x, y, 9, 9);
            if (i * 2 + 1 < food) {
                graphics.blitSprite(FOOD_FULL_SPRITE, x, y, 9, 9);
            } else if (i * 2 + 1 == food) {
                graphics.blitSprite(FOOD_HALF_SPRITE, x, y, 9, 9);
            }
        }
        RenderSystem.disableBlend();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
    }

    private String clamp(String raw, int max) {
        if (raw == null) {
            return "";
        }
        return raw.length() <= max ? raw : raw.substring(0, max - 1) + "...";
    }
}

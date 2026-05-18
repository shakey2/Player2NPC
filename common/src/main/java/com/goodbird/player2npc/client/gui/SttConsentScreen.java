package com.goodbird.player2npc.client.gui;

import com.player2.playerengine.player2api.ChatclefConfigPersistantState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Contextual STT privacy screen.
 *
 * - No consent yet: shows disclosure text and Accept / Decline buttons.
 * - Consent already granted: shows current status and a Revoke Consent / Close option.
 *
 * Open from Player2NPCClient (hold-V edge) or from CharacterSelectionScreen (Voice Settings button).
 */
public class SttConsentScreen extends Screen {

    private static final int DIALOG_W = 400;
    private static final int CONSENT_DIALOG_H = 175;
    private static final int SETTINGS_DIALOG_H = 150;
    private static final int TITLE_H = 22;
    private static final int TEXT_PAD = 16;
    private static final int LINE_GAP = 2;
    private static final int TEXT_COLOR = 0xDDDDDD;
    private static final int TITLE_COLOR = 0xFFFFFF;
    private static final int PANEL_BG = 0xFF2A2A2A;
    private static final int TITLE_BG = 0xFF1A1A1A;

    private final Screen parent;

    public SttConsentScreen(Screen parent) {
        super(Component.translatable("screen.player2npc.stt_consent.title"));
        this.parent = parent;
    }

    private int dialogHeight() {
        return ChatclefConfigPersistantState.hasSttConsent() ? SETTINGS_DIALOG_H : CONSENT_DIALOG_H;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // Suppress the default background; we draw our own below.
    }

    @Override
    protected void init() {
        super.init();
        int x = (this.width - DIALOG_W) / 2;
        int y = (this.height - dialogHeight()) / 2;
        int btnY = y + dialogHeight() - 28;
        int btnW = 120;
        int btnGap = 16;

        if (!ChatclefConfigPersistantState.hasSttConsent()) {
            int leftX = x + (DIALOG_W - btnW * 2 - btnGap) / 2;
            addRenderableWidget(Button.builder(
                    Component.translatable("screen.player2npc.stt_consent.accept"),
                    btn -> {
                        ChatclefConfigPersistantState.grantConsentAndEnable();
                        minecraft.player.sendSystemMessage(
                                Component.translatable("screen.player2npc.stt_consent.enabled_msg"));
                        minecraft.setScreen(parent);
                    }).bounds(leftX, btnY, btnW, 20).build());

            addRenderableWidget(Button.builder(
                    Component.translatable("screen.player2npc.stt_consent.decline"),
                    btn -> minecraft.setScreen(parent)
            ).bounds(leftX + btnW + btnGap, btnY, btnW, 20).build());
        } else {
            int leftX = x + (DIALOG_W - btnW * 2 - btnGap) / 2;
            addRenderableWidget(Button.builder(
                    Component.translatable("screen.player2npc.stt_consent.revoke"),
                    btn -> {
                        ChatclefConfigPersistantState.revokeSttConsent();
                        com.player2.playerengine.player2api.utils.STTUtils.abortSession();
                        minecraft.player.sendSystemMessage(
                                Component.translatable("screen.player2npc.stt_consent.revoked_msg"));
                        minecraft.setScreen(parent);
                    }).bounds(leftX, btnY, btnW, 20).build());

            addRenderableWidget(Button.builder(
                    Component.translatable("screen.player2npc.stt_consent.close"),
                    btn -> minecraft.setScreen(parent)
            ).bounds(leftX + btnW + btnGap, btnY, btnW, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);

        int h = dialogHeight();
        int x = (this.width - DIALOG_W) / 2;
        int y = (this.height - h) / 2;

        graphics.fill(x, y, x + DIALOG_W, y + h, PANEL_BG);
        graphics.fill(x, y, x + DIALOG_W, y + TITLE_H, TITLE_BG);

        if (!ChatclefConfigPersistantState.hasSttConsent()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.player2npc.stt_consent.title"),
                    x + DIALOG_W / 2, y + 7, TITLE_COLOR);

            int textY = y + TITLE_H + 8;
            textY = drawWrappedParagraph(graphics,
                    Component.translatable("screen.player2npc.stt_consent.line1"),
                    x, textY, TEXT_COLOR);
            textY += 4;
            textY = drawWrappedParagraph(graphics,
                    Component.translatable("screen.player2npc.stt_consent.line2"),
                    x, textY, TEXT_COLOR);
            textY += 4;
            textY = drawWrappedParagraph(graphics,
                    Component.translatable("screen.player2npc.stt_consent.line3"),
                    x, textY, TEXT_COLOR);
            textY += 4;
            drawWrappedParagraph(graphics,
                    Component.translatable("screen.player2npc.stt_consent.line4"),
                    x, textY, 0xAAAAAA);
        } else {
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.player2npc.stt_settings.title"),
                    x + DIALOG_W / 2, y + 7, TITLE_COLOR);

            int textY = y + TITLE_H + 10;
            textY = drawWrappedParagraph(graphics,
                    Component.translatable("screen.player2npc.stt_settings.status_on"),
                    x, textY, 0x55FF55);
            textY += 6;
            textY = drawWrappedParagraph(graphics,
                    Component.translatable("screen.player2npc.stt_settings.revoke_hint"),
                    x, textY, TEXT_COLOR);
            textY += 4;
            drawWrappedParagraph(graphics,
                    Component.translatable("screen.player2npc.stt_settings.revoke_hint2"),
                    x, textY, TEXT_COLOR);
        }

        super.render(graphics, mouseX, mouseY, delta);
    }

    /** Draws wrapped lines centered in the panel; returns Y below the last line. */
    private int drawWrappedParagraph(GuiGraphics graphics, Component text, int panelX, int startY, int color) {
        int maxWidth = DIALOG_W - TEXT_PAD * 2;
        int y = startY;
        for (FormattedCharSequence line : this.font.split(text, maxWidth)) {
            int lineW = this.font.width(line);
            int drawX = panelX + (DIALOG_W - lineW) / 2;
            graphics.drawString(this.font, line, drawX, y, color, false);
            y += this.font.lineHeight + LINE_GAP;
        }
        return y;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

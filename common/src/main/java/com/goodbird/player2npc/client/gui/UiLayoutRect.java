package com.goodbird.player2npc.client.gui;

public record UiLayoutRect(int x, int y, int width, int height) {
    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public UiLayoutRect inset(int amount) {
        return new UiLayoutRect(x + amount, y + amount, Math.max(0, width - amount * 2), Math.max(0, height - amount * 2));
    }
}

package com.goodbird.player2npc.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

public final class Player2NpcSliderWidget extends AbstractSliderButton {
    private final int minimum;
    private final int maximum;
    private final int step;
    private final boolean logarithmic;
    private final Component label;
    private final IntFunction<Component> valueLabel;
    private final IntConsumer onCommit;
    private IntConsumer onValueChanged = value -> {
    };
    private UiLayoutRect clip;
    private int currentValue;
    private int committedValue;
    private int keyboardStepDirection;

    public Player2NpcSliderWidget(int x, int y, int width, int height,
                                  int minimum, int maximum, int step, int initialValue,
                                  Component label, IntFunction<Component> valueLabel,
                                  IntConsumer onCommit) {
        this(x, y, width, height, minimum, maximum, step, initialValue, false,
                label, valueLabel, onCommit);
    }

    public Player2NpcSliderWidget(int x, int y, int width, int height,
                                  int minimum, int maximum, int step, int initialValue,
                                  boolean logarithmic, Component label,
                                  IntFunction<Component> valueLabel, IntConsumer onCommit) {
        super(x, y, width, height, Component.empty(),
                normalized(initialValue, minimum, maximum, logarithmic));
        if (maximum <= minimum || step <= 0 || (maximum - minimum) % step != 0) {
            throw new IllegalArgumentException("Invalid slider range");
        }
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
        this.logarithmic = logarithmic;
        this.label = label;
        this.valueLabel = valueLabel;
        this.onCommit = onCommit;
        this.currentValue = quantize(initialValue);
        this.committedValue = currentValue;
        this.value = normalized(currentValue, minimum, maximum, logarithmic);
        updateMessage();
    }

    public Player2NpcSliderWidget withClip(UiLayoutRect clip) {
        this.clip = clip;
        return this;
    }

    public int currentValue() {
        return currentValue;
    }

    public Player2NpcSliderWidget onValueChanged(IntConsumer listener) {
        onValueChanged = listener == null ? value -> {
        } : listener;
        return this;
    }

    public void setCurrentValue(int rawValue) {
        currentValue = quantize(rawValue);
        committedValue = currentValue;
        value = normalized(currentValue, minimum, maximum, logarithmic);
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        Component renderedValue = valueLabel.apply(currentValue);
        setMessage(Component.translatable("screen.player2npc.ui.ai_memory.control.narration", label, renderedValue));
    }

    @Override
    protected void applyValue() {
        currentValue = keyboardStepDirection == 0
                ? valueFromNormalized()
                : quantize(currentValue + keyboardStepDirection * step);
        value = normalized(currentValue, minimum, maximum, logarithmic);
        onValueChanged.accept(currentValue);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (clip != null) {
            graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
        }
        UiRenderCompat.drawSlider(graphics, getX(), getY(), getWidth(), getHeight(), value,
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int before = currentValue;
        keyboardStepDirection = keyCode == 263 ? -1 : keyCode == 262 ? 1 : 0;
        boolean handled;
        try {
            handled = super.keyPressed(keyCode, scanCode, modifiers);
        } finally {
            keyboardStepDirection = 0;
        }
        if (handled && before != currentValue) {
            commitIfChanged();
        }
        return handled;
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        super.onRelease(mouseX, mouseY);
        commitIfChanged();
    }

    private void commitIfChanged() {
        if (active && currentValue != committedValue) {
            committedValue = currentValue;
            onCommit.accept(currentValue);
        }
    }

    private int valueFromNormalized() {
        int stepCount = (maximum - minimum) / step;
        double rawOffset = logarithmic
                ? Math.expm1(value * Math.log1p(maximum - minimum))
                : value * (maximum - minimum);
        int stepIndex = (int) Math.round(rawOffset / step);
        return minimum + Math.max(0, Math.min(stepIndex, stepCount)) * step;
    }

    private int quantize(int rawValue) {
        int clamped = Math.max(minimum, Math.min(rawValue, maximum));
        int stepIndex = (int) Math.round((double) (clamped - minimum) / step);
        return minimum + stepIndex * step;
    }

    private static double normalized(int rawValue, int minimum, int maximum, boolean logarithmic) {
        if (maximum <= minimum) {
            return 0.0D;
        }
        int clamped = Math.max(minimum, Math.min(rawValue, maximum));
        int offset = clamped - minimum;
        return logarithmic
                ? Math.log1p(offset) / Math.log1p(maximum - minimum)
                : (double) offset / (maximum - minimum);
    }

    private boolean isInsideClip(double mouseX, double mouseY) {
        return clip == null || mouseX >= clip.x() && mouseX < clip.right()
                && mouseY >= clip.y() && mouseY < clip.bottom();
    }
}

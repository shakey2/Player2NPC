package com.goodbird.player2npc.client.gui.settings;

import com.goodbird.player2npc.client.gui.Player2NpcButtonWidget;
import com.goodbird.player2npc.client.gui.Player2NpcNumberInputWidget;
import com.goodbird.player2npc.client.gui.Player2NpcSliderWidget;
import com.goodbird.player2npc.client.gui.Player2NpcToggleWidget;
import com.goodbird.player2npc.client.gui.Player2NpcUiTheme;
import com.goodbird.player2npc.client.gui.UiLayoutRect;
import com.goodbird.player2npc.client.gui.UiRenderCompat;
import com.goodbird.player2npc.companion.gui.settings.GuiSettingDefinition;
import com.goodbird.player2npc.companion.gui.settings.GuiSettingOption;
import com.goodbird.player2npc.companion.gui.settings.GuiSettingSection;
import com.goodbird.player2npc.companion.gui.settings.GuiSettingSource;
import com.goodbird.player2npc.companion.gui.settings.GuiSettingType;
import com.goodbird.player2npc.companion.gui.settings.GuiSettingZeroMeaning;
import com.goodbird.player2npc.companion.gui.settings.GuiSettingsCatalog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

/**
 * Reusable virtualized settings surface driven exclusively by {@link GuiSettingsCatalog}. The host owns
 * screen widget registration and packet transport; this panel owns view state, drafts, and validation.
 */
public final class Player2NpcSettingsPanel {
    private static final int STATUS_HEIGHT = 18;
    private static final int SCROLLBAR_WIDTH = 4;
    private static final int SECTION_HEIGHT = 22;
    private static final int SETTING_HEIGHT = 82;
    private static final int ROW_GAP = 4;
    private static final int CONTROL_HEIGHT = 18;
    private static final int CONTROL_Y_OFFSET = 46;
    private static final int WHEEL_STEP = 36;
    private static final int NUMBER_INPUT_WIDTH = 56;
    private static final int CONTROL_GAP = 6;
    private static final int TEXT_SAVE_WIDTH = 58;
    private static final int ITEM_EDITOR_HEADER_HEIGHT = 54;
    private static final int ITEM_RESULT_HEIGHT = 20;
    private static final int ITEM_RESULT_GAP = 2;
    private static final int ITEM_SEARCH_MAX_LENGTH = 128;

    private final Font font;
    private final Player2NpcSettingsActionSink actionSink;
    private final Runnable requestWidgetRebuild;
    private final Map<String, Map<String, String>> snapshots = new HashMap<>();
    private final Map<String, Integer> tabScroll = new HashMap<>();
    private final Map<String, Boolean> collapsedSections = new HashMap<>();
    private final Map<String, String> numericDrafts = new HashMap<>();
    private final Map<String, String> textDrafts = new HashMap<>();
    private final Map<String, ItemListDraft> itemListDrafts = new HashMap<>();
    private final Map<String, LocalStatus> localStatuses = new HashMap<>();
    private final Map<String, Player2NpcSliderWidget> numericSliders = new HashMap<>();
    private final Map<String, Player2NpcNumberInputWidget> numericInputs = new HashMap<>();
    private final Map<String, Player2NpcTextInputWidget> textInputs = new HashMap<>();
    private Player2NpcTextInputWidget itemSearchInput;
    private UiLayoutRect bounds = new UiLayoutRect(0, 0, 0, 0);
    private String tabWireName = "";
    private String activeItemEditorId = "";
    private String focusedControlId = "";
    private boolean widgetReferencesPrepared;
    private boolean draggingScrollbar;
    private int scrollbarGrabOffset;
    private boolean rebuildQueued;

    public Player2NpcSettingsPanel(
            Font font,
            Player2NpcSettingsActionSink actionSink,
            Runnable requestWidgetRebuild) {
        this.font = Objects.requireNonNull(font, "font");
        this.actionSink = Objects.requireNonNull(actionSink, "actionSink");
        this.requestWidgetRebuild = Objects.requireNonNull(requestWidgetRebuild, "requestWidgetRebuild");
    }

    /** Selects the catalog tab and the complete panel rectangle supplied by the host screen. */
    public void updateContext(String tabWireName, UiLayoutRect bounds) {
        this.tabWireName = tabWireName == null ? "" : tabWireName;
        this.bounds = bounds == null ? new UiLayoutRect(0, 0, 0, 0) : bounds;
        clampCurrentScroll();
    }

    /** Stores a defensive copy without replacing any active numeric, text, search, or item-list draft. */
    public void updateSnapshot(String tabWireName, Map<String, String> fields) {
        if (tabWireName == null || tabWireName.isBlank()) {
            return;
        }
        Map<String, String> clean = fields == null ? Map.of() : Map.copyOf(fields);
        snapshots.put(tabWireName, clean);
        if (!clean.getOrDefault("actionResult", "").isBlank()) {
            localStatuses.remove(tabWireName);
        }
        if (tabWireName.equals(this.tabWireName)) {
            clampCurrentScroll();
        }
    }

    public boolean hasSettingsForCurrentContext() {
        return !GuiSettingsCatalog.forTab(tabWireName).isEmpty();
    }

    public boolean isItemEditorOpen() {
        return currentItemEditorDefinition() != null;
    }

    /**
     * Captures the focused control before the host calls Screen#setFocused(null) and Screen#clearWidgets.
     * Call this first in the host's rebuild path.
     */
    public void clearWidgetReferences() {
        captureFocusedControl();
        numericSliders.clear();
        numericInputs.clear();
        textInputs.clear();
        itemSearchInput = null;
        widgetReferencesPrepared = true;
    }

    /** Registers only controls intersecting the current clipped viewport. */
    public void buildWidgets(
            Consumer<AbstractWidget> addWidget,
            Consumer<AbstractWidget> focusWidget) {
        Objects.requireNonNull(addWidget, "addWidget");
        Objects.requireNonNull(focusWidget, "focusWidget");
        if (!widgetReferencesPrepared) {
            clearWidgetReferences();
        }
        widgetReferencesPrepared = false;
        if (bounds.width() <= 0 || bounds.height() <= STATUS_HEIGHT || tabWireName.isBlank()) {
            return;
        }

        GuiSettingDefinition editorDefinition = currentItemEditorDefinition();
        if (editorDefinition != null) {
            buildItemEditorWidgets(editorDefinition, addWidget, focusWidget);
            return;
        }

        UiLayoutRect viewport = mainViewport();
        int scroll = currentMainScroll();
        for (LayoutRow row : layoutRows()) {
            UiLayoutRect rowRect = screenRect(viewport, row, scroll);
            if (!intersects(rowRect, viewport)) {
                continue;
            }
            if (row.section() != null) {
                buildSectionWidget(row.section(), rowRect, viewport, addWidget);
            } else if (row.definition() != null) {
                buildSettingWidgets(row.definition(), rowRect, viewport, addWidget, focusWidget);
            }
        }
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (bounds.width() <= 0 || bounds.height() <= STATUS_HEIGHT || tabWireName.isBlank()) {
            return;
        }
        renderStatus(graphics);
        GuiSettingDefinition editorDefinition = currentItemEditorDefinition();
        if (editorDefinition != null) {
            renderItemEditor(graphics, editorDefinition);
        } else {
            renderSettingsRows(graphics);
        }
    }

    public boolean isInputFocused() {
        if (itemSearchInput != null && itemSearchInput.isFocused()) {
            return true;
        }
        for (Player2NpcNumberInputWidget input : numericInputs.values()) {
            if (input.isFocused()) {
                return true;
            }
        }
        for (Player2NpcTextInputWidget input : textInputs.values()) {
            if (input.isFocused()) {
                return true;
            }
        }
        return false;
    }

    /** Commits visible numeric inputs; text and item lists retain their explicit Save semantics. */
    public boolean commitInputs() {
        boolean committed = false;
        for (Player2NpcNumberInputWidget input : new ArrayList<>(numericInputs.values())) {
            committed |= input.commit();
        }
        return committed;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0.0D) {
            return false;
        }
        UiLayoutRect viewport = activeScrollViewport();
        if (!isInside(viewport, mouseX, mouseY)) {
            return false;
        }
        int maxScroll = activeMaxScroll();
        if (maxScroll <= 0) {
            return false;
        }
        int amount = (int) Math.round(scrollY * WHEEL_STEP);
        if (amount == 0) {
            amount = scrollY > 0.0D ? 1 : -1;
        }
        setActiveScroll(clamp(activeScroll() - amount, 0, maxScroll));
        requestWidgetRebuild.run();
        return true;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        UiLayoutRect viewport = activeScrollViewport();
        int maxScroll = activeMaxScroll();
        if (maxScroll <= 0) {
            return false;
        }
        UiLayoutRect track = scrollbarTrack(viewport);
        if (!isInside(track, mouseX, mouseY)) {
            return false;
        }
        UiLayoutRect thumb = scrollbarThumb(viewport);
        draggingScrollbar = true;
        scrollbarGrabOffset = isInside(thumb, mouseX, mouseY)
                ? (int) Math.round(mouseY - thumb.y())
                : thumb.height() / 2;
        updateScrollFromThumb(mouseY);
        return true;
    }

    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY) {
        if (button != 0 || !draggingScrollbar) {
            return false;
        }
        updateScrollFromThumb(mouseY);
        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0 || !draggingScrollbar) {
            return false;
        }
        draggingScrollbar = false;
        return true;
    }

    /** Clears transient and persistent panel state, for example when the Hub itself is discarded. */
    public void resetAllState() {
        clearWidgetReferences();
        numericDrafts.clear();
        textDrafts.clear();
        itemListDrafts.clear();
        localStatuses.clear();
        collapsedSections.clear();
        tabScroll.clear();
        snapshots.clear();
        activeItemEditorId = "";
        focusedControlId = "";
        draggingScrollbar = false;
    }

    /** Clears view/draft state for one tab while retaining snapshots and other tabs. */
    public void resetTabState(String tabWireName) {
        if (tabWireName == null || tabWireName.isBlank()) {
            return;
        }
        tabScroll.remove(tabWireName);
        localStatuses.remove(tabWireName);
        String prefix = tabWireName + ":";
        collapsedSections.keySet().removeIf(key -> key.startsWith(prefix));
        for (GuiSettingDefinition definition : GuiSettingsCatalog.forTab(tabWireName)) {
            numericDrafts.remove(definition.id());
            textDrafts.remove(definition.id());
            itemListDrafts.remove(definition.id());
            if (definition.id().equals(activeItemEditorId)) {
                activeItemEditorId = "";
            }
        }
        clampCurrentScroll();
    }

    private void buildSectionWidget(
            GuiSettingSection section,
            UiLayoutRect row,
            UiLayoutRect viewport,
            Consumer<AbstractWidget> addWidget) {
        boolean collapsed = isCollapsed(section);
        Component label = Component.translatable(
                collapsed
                        ? "screen.player2npc.ui.settings.section.expand"
                        : "screen.player2npc.ui.settings.section.collapse",
                Component.translatable(section.translationKey()));
        Player2NpcButtonWidget button = new Player2NpcButtonWidget(
                row.x(), row.y(), row.width(), row.height(), label, false, () -> {
            collapsedSections.put(sectionStateKey(section), !collapsed);
            clampCurrentScroll();
            requestWidgetRebuild.run();
        }).withClip(viewport);
        addWidget.accept(button);
    }

    private void buildSettingWidgets(
            GuiSettingDefinition definition,
            UiLayoutRect row,
            UiLayoutRect viewport,
            Consumer<AbstractWidget> addWidget,
            Consumer<AbstractWidget> focusWidget) {
        switch (definition.type()) {
            case INTEGER, PERCENT, PERMILLE -> buildNumericWidgets(
                    definition, row, viewport, addWidget, focusWidget);
            case BOOLEAN -> buildBooleanWidget(definition, row, viewport, addWidget);
            case ENUM -> buildEnumWidget(definition, row, viewport, addWidget);
            case TEXT -> buildTextWidgets(definition, row, viewport, addWidget, focusWidget);
            case ITEM_LIST -> buildItemListWidget(definition, row, viewport, addWidget);
            case BLOCK_POS -> buildBlockPosWidget(definition, row, viewport, addWidget);
        }
    }

    private void buildNumericWidgets(
            GuiSettingDefinition definition,
            UiLayoutRect row,
            UiLayoutRect viewport,
            Consumer<AbstractWidget> addWidget,
            Consumer<AbstractWidget> focusWidget) {
        int snapshotValue = parseNumericValue(definition, snapshotValue(definition));
        String draft = numericDrafts.get(definition.id());
        int currentValue = parseDraftOrValue(definition, draft, snapshotValue);
        int inputX = row.right() - 8 - NUMBER_INPUT_WIDTH;
        int controlY = row.y() + CONTROL_Y_OFFSET;
        int sliderWidth = Math.max(24, inputX - CONTROL_GAP - (row.x() + 8));
        Component title = Component.translatable(definition.titleKey());

        Player2NpcSliderWidget slider = new Player2NpcSliderWidget(
                row.x() + 8,
                controlY,
                sliderWidth,
                CONTROL_HEIGHT,
                definition.minimum(),
                definition.maximum(),
                definition.step(),
                currentValue,
                definition.logarithmicHint(),
                title,
                value -> Component.translatable(
                        "screen.player2npc.ui.settings.narration.number",
                        value,
                        Component.translatable(definition.unitKey())),
                value -> commitNumeric(definition, value));
        slider.onValueChanged(value -> {
            Player2NpcNumberInputWidget input = numericInputs.get(definition.id());
            if (input != null) {
                input.setPreviewValue(value);
            }
        });
        slider.withClip(viewport);
        slider.active = canEdit(definition);
        numericSliders.put(definition.id(), slider);
        addWidget.accept(slider);

        Player2NpcNumberInputWidget input = new Player2NpcNumberInputWidget(
                font,
                inputX,
                controlY,
                NUMBER_INPUT_WIDTH,
                CONTROL_HEIGHT,
                definition.minimum(),
                definition.maximum(),
                currentValue,
                title,
                value -> recordNumericDraft(definition, value),
                value -> commitNumeric(definition, value))
                .withClip(viewport);
        input.active = canEdit(definition);
        input.setEditable(canEdit(definition));
        if (draft != null) {
            input.setDraftValue(draft);
        }
        numericInputs.put(definition.id(), input);
        addWidget.accept(input);
        restoreFocus(numberFocusId(definition.id()), input, focusWidget);
    }

    private void buildBooleanWidget(
            GuiSettingDefinition definition,
            UiLayoutRect row,
            UiLayoutRect viewport,
            Consumer<AbstractWidget> addWidget) {
        boolean current = Boolean.parseBoolean(snapshotValue(definition));
        Component state = Component.translatable(current
                ? "screen.player2npc.ui.settings.value.on"
                : "screen.player2npc.ui.settings.value.off");
        Component narration = Component.translatable(
                "screen.player2npc.ui.settings.narration.value",
                Component.translatable(definition.titleKey()),
                state);
        Player2NpcToggleWidget toggle = new Player2NpcToggleWidget(
                row.x() + 8,
                row.y() + CONTROL_Y_OFFSET,
                38,
                CONTROL_HEIGHT,
                narration,
                current,
                () -> {
                    emit(definition, String.valueOf(!current));
                    requestWidgetRebuild.run();
                }).withClip(viewport);
        toggle.active = canEdit(definition);
        addWidget.accept(toggle);
    }

    private void buildEnumWidget(
            GuiSettingDefinition definition,
            UiLayoutRect row,
            UiLayoutRect viewport,
            Consumer<AbstractWidget> addWidget) {
        GuiSettingOption current = currentOption(definition);
        int width = Math.min(190, Math.max(70, row.width() - 16));
        Player2NpcButtonWidget button = new Player2NpcButtonWidget(
                row.x() + 8,
                row.y() + CONTROL_Y_OFFSET,
                width,
                CONTROL_HEIGHT,
                Component.translatable(current.translationKey()),
                false,
                () -> {
                    List<GuiSettingOption> options = definition.options();
                    int index = Math.max(0, options.indexOf(current));
                    GuiSettingOption next = options.get((index + 1) % options.size());
                    emit(definition, next.token());
                    requestWidgetRebuild.run();
                }).withClip(viewport);
        button.active = canEdit(definition);
        addWidget.accept(button);
    }

    private void buildTextWidgets(
            GuiSettingDefinition definition,
            UiLayoutRect row,
            UiLayoutRect viewport,
            Consumer<AbstractWidget> addWidget,
            Consumer<AbstractWidget> focusWidget) {
        int controlY = row.y() + CONTROL_Y_OFFSET;
        int inputWidth = Math.max(36, row.width() - 16 - TEXT_SAVE_WIDTH - CONTROL_GAP);
        Player2NpcTextInputWidget input = new Player2NpcTextInputWidget(
                font,
                row.x() + 8,
                controlY,
                inputWidth,
                CONTROL_HEIGHT,
                definition.maxTextLength(),
                Component.translatable(definition.titleKey()),
                value -> textDrafts.put(definition.id(), value))
                .withClip(viewport);
        input.setDraftValue(textDrafts.getOrDefault(definition.id(), snapshotValue(definition)));
        input.active = canEdit(definition);
        input.setEditable(canEdit(definition));
        textInputs.put(definition.id(), input);
        addWidget.accept(input);
        restoreFocus(textFocusId(definition.id()), input, focusWidget);

        Player2NpcButtonWidget save = new Player2NpcButtonWidget(
                row.right() - 8 - TEXT_SAVE_WIDTH,
                controlY,
                TEXT_SAVE_WIDTH,
                CONTROL_HEIGHT,
                Component.translatable("screen.player2npc.ui.settings.action.save"),
                true,
                () -> saveText(definition)).withClip(viewport);
        save.active = canEdit(definition);
        addWidget.accept(save);
    }

    private void buildItemListWidget(
            GuiSettingDefinition definition,
            UiLayoutRect row,
            UiLayoutRect viewport,
            Consumer<AbstractWidget> addWidget) {
        Player2NpcButtonWidget edit = new Player2NpcButtonWidget(
                row.x() + 8,
                row.y() + CONTROL_Y_OFFSET,
                Math.min(190, Math.max(90, row.width() - 16)),
                CONTROL_HEIGHT,
                Component.translatable("screen.player2npc.ui.settings.action.edit"),
                false,
                () -> openItemEditor(definition)).withClip(viewport);
        edit.active = canEdit(definition);
        addWidget.accept(edit);
    }

    private void buildBlockPosWidget(
            GuiSettingDefinition definition,
            UiLayoutRect row,
            UiLayoutRect viewport,
            Consumer<AbstractWidget> addWidget) {
        int width = Math.min(190, Math.max(100, row.width() / 2));
        Player2NpcButtonWidget button = new Player2NpcButtonWidget(
                row.right() - 8 - width,
                row.y() + CONTROL_Y_OFFSET,
                width,
                CONTROL_HEIGHT,
                Component.translatable("screen.player2npc.ui.settings.action.set_current_position"),
                true,
                () -> emit(definition, "")).withClip(viewport);
        button.active = canEdit(definition);
        addWidget.accept(button);
    }

    private void buildItemEditorWidgets(
            GuiSettingDefinition definition,
            Consumer<AbstractWidget> addWidget,
            Consumer<AbstractWidget> focusWidget) {
        ItemListDraft draft = itemListDrafts.get(definition.id());
        if (draft == null) {
            return;
        }
        UiLayoutRect viewport = mainViewport();
        UiLayoutRect resultsViewport = itemResultsViewport(viewport);
        int buttonWidth = 58;
        int controlsY = viewport.y() + 30;
        int cancelX = viewport.right() - SCROLLBAR_WIDTH - 4 - buttonWidth;
        int saveX = cancelX - CONTROL_GAP - buttonWidth;
        int searchWidth = Math.max(30, saveX - CONTROL_GAP - (viewport.x() + 4));

        itemSearchInput = new Player2NpcTextInputWidget(
                font,
                viewport.x() + 4,
                controlsY,
                searchWidth,
                CONTROL_HEIGHT,
                ITEM_SEARCH_MAX_LENGTH,
                Component.translatable("screen.player2npc.ui.settings.item_search.placeholder"),
                value -> {
                    draft.query = value;
                    draft.scroll = 0;
                    draft.invalidateResults();
                    localStatuses.remove(tabWireName);
                    scheduleWidgetRebuild();
                }).withClip(viewport);
        itemSearchInput.setDraftValue(draft.query);
        itemSearchInput.setHint(
                Component.translatable("screen.player2npc.ui.settings.item_search.placeholder"));
        itemSearchInput.active = canEdit(definition);
        itemSearchInput.setEditable(canEdit(definition));
        addWidget.accept(itemSearchInput);
        restoreFocus(itemSearchFocusId(definition.id()), itemSearchInput, focusWidget);

        Player2NpcButtonWidget save = new Player2NpcButtonWidget(
                saveX,
                controlsY,
                buttonWidth,
                CONTROL_HEIGHT,
                Component.translatable("screen.player2npc.ui.settings.action.save"),
                true,
                () -> saveItemEditor(definition)).withClip(viewport);
        save.active = canEdit(definition);
        addWidget.accept(save);

        Player2NpcButtonWidget cancel = new Player2NpcButtonWidget(
                cancelX,
                controlsY,
                buttonWidth,
                CONTROL_HEIGHT,
                Component.translatable("screen.player2npc.ui.settings.action.cancel"),
                false,
                () -> cancelItemEditor(definition)).withClip(viewport);
        addWidget.accept(cancel);

        List<String> results = editorResults(draft);
        draft.scroll = clamp(draft.scroll, 0, itemEditorMaxScroll(resultsViewport, results.size()));
        int stride = ITEM_RESULT_HEIGHT + ITEM_RESULT_GAP;
        int first = Math.max(0, draft.scroll / stride);
        for (int index = first; index < results.size(); index++) {
            int y = resultsViewport.y() + index * stride - draft.scroll;
            UiLayoutRect resultRect = new UiLayoutRect(
                    resultsViewport.x() + 2,
                    y,
                    Math.max(1, resultsViewport.width() - SCROLLBAR_WIDTH - 6),
                    ITEM_RESULT_HEIGHT);
            if (resultRect.y() >= resultsViewport.bottom()) {
                break;
            }
            if (!intersects(resultRect, resultsViewport)) {
                continue;
            }
            String itemId = results.get(index);
            boolean adding = !draft.query.isBlank();
            Player2NpcButtonWidget result = new Player2NpcButtonWidget(
                    resultRect.x(),
                    resultRect.y(),
                    resultRect.width(),
                    resultRect.height(),
                    Component.literal(itemId),
                    adding,
                    () -> toggleEditorItem(definition, itemId, adding)).withClip(resultsViewport);
            result.active = canEdit(definition);
            addWidget.accept(result);
        }
    }

    private void renderSettingsRows(GuiGraphics graphics) {
        UiLayoutRect viewport = mainViewport();
        int scroll = currentMainScroll();
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        for (LayoutRow row : layoutRows()) {
            if (row.definition() == null) {
                continue;
            }
            UiLayoutRect rowRect = screenRect(viewport, row, scroll);
            if (!intersects(rowRect, viewport)) {
                continue;
            }
            renderSettingRow(graphics, row.definition(), rowRect);
        }
        graphics.disableScissor();
        renderScrollbar(graphics, viewport);
    }

    private void renderSettingRow(
            GuiGraphics graphics,
            GuiSettingDefinition definition,
            UiLayoutRect row) {
        UiRenderCompat.fillPanel(
                graphics, row.x(), row.y(), row.width(), row.height(), 0xAA1B2935);
        Component effect = Component.translatable(definition.effectKey());
        int effectWidth = Math.min(126, Math.max(56, row.width() / 3));
        drawFirstLine(
                graphics,
                Component.translatable(definition.titleKey()),
                row.x() + 8,
                row.y() + 6,
                Math.max(20, row.width() - effectWidth - 24),
                Player2NpcUiTheme.TEXT);
        drawFirstLine(
                graphics,
                effect,
                row.right() - 8 - effectWidth,
                row.y() + 6,
                effectWidth,
                Player2NpcUiTheme.TEXT_DIM);
        drawWrapped(
                graphics,
                Component.translatable(definition.descriptionKey()),
                row.x() + 8,
                row.y() + 20,
                Math.max(20, row.width() - 16),
                Player2NpcUiTheme.TEXT_MUTED,
                2);

        if (definition.type() == GuiSettingType.BOOLEAN) {
            boolean current = Boolean.parseBoolean(snapshotValue(definition));
            drawFirstLine(
                    graphics,
                    Component.translatable(current
                            ? "screen.player2npc.ui.settings.value.on"
                            : "screen.player2npc.ui.settings.value.off"),
                    row.x() + 54,
                    row.y() + CONTROL_Y_OFFSET + 5,
                    Math.max(20, row.width() - 62),
                    Player2NpcUiTheme.TEXT);
        } else if (definition.type() == GuiSettingType.BLOCK_POS) {
            String position = snapshotValue(definition);
            if (!position.isBlank()) {
                drawFirstLine(
                        graphics,
                        Component.literal(position),
                        row.x() + 8,
                        row.y() + CONTROL_Y_OFFSET + 5,
                        Math.max(20, row.width() / 2 - 16),
                        Player2NpcUiTheme.TEXT);
            }
        }

        if (isNumeric(definition.type())) {
            int metadataY = row.y() + 69;
            Component unit = Component.translatable(definition.unitKey());
            drawFirstLine(
                    graphics,
                    unit,
                    row.x() + 8,
                    metadataY,
                    Math.max(20, row.width() / 2),
                    Player2NpcUiTheme.TEXT_DIM);
            if (definition.zeroMeaning() != GuiSettingZeroMeaning.NONE) {
                Component zero = Component.translatable(definition.zeroMeaning() == GuiSettingZeroMeaning.OFF
                        ? "screen.player2npc.ui.settings.zero.off"
                        : "screen.player2npc.ui.settings.zero.unlimited");
                drawFirstLine(
                        graphics,
                        zero,
                        row.x() + Math.max(70, row.width() / 2),
                        metadataY,
                        Math.max(20, row.width() / 2 - 8),
                        Player2NpcUiTheme.TEXT_DIM);
            }
        }
    }

    private void renderItemEditor(GuiGraphics graphics, GuiSettingDefinition definition) {
        ItemListDraft draft = itemListDrafts.get(definition.id());
        if (draft == null) {
            return;
        }
        UiLayoutRect viewport = mainViewport();
        UiLayoutRect resultsViewport = itemResultsViewport(viewport);
        List<String> results = editorResults(draft);
        draft.scroll = clamp(draft.scroll, 0, itemEditorMaxScroll(resultsViewport, results.size()));

        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        drawFirstLine(
                graphics,
                Component.translatable(definition.titleKey()),
                viewport.x() + 4,
                viewport.y() + 3,
                Math.max(20, viewport.width() - 150),
                Player2NpcUiTheme.TEXT);
        drawFirstLine(
                graphics,
                Component.translatable(
                        "screen.player2npc.ui.settings.item_list.count",
                        draft.selected.size(),
                        Math.min(64, definition.maxListEntries())),
                viewport.right() - 142,
                viewport.y() + 3,
                132,
                Player2NpcUiTheme.ACCENT);
        drawFirstLine(
                graphics,
                Component.translatable(draft.query.isBlank()
                        ? "screen.player2npc.ui.settings.item_list.selected"
                        : "screen.player2npc.ui.settings.item_list.available"),
                viewport.x() + 4,
                viewport.y() + 16,
                Math.max(20, viewport.width() - 12),
                Player2NpcUiTheme.TEXT_MUTED);
        if (results.isEmpty()) {
            drawFirstLine(
                    graphics,
                    Component.translatable(draft.query.isBlank()
                            ? "screen.player2npc.ui.settings.item_list.empty"
                            : "screen.player2npc.ui.settings.item_list.no_matches"),
                    resultsViewport.x() + 4,
                    resultsViewport.y() + 4,
                    Math.max(20, resultsViewport.width() - 8),
                    Player2NpcUiTheme.TEXT_DIM);
        }
        graphics.disableScissor();
        renderScrollbar(graphics, resultsViewport);
    }

    private void renderStatus(GuiGraphics graphics) {
        Component status;
        int color;
        Map<String, String> fields = currentSnapshot();
        String result = fields.getOrDefault("actionResult", "");
        if (!result.isBlank()) {
            status = actionResultComponent(result);
            color = "ok".equals(result) ? Player2NpcUiTheme.GOOD : Player2NpcUiTheme.WARN;
        } else if (settingsLoadFailed()) {
            status = Component.translatable("screen.player2npc.ui.settings.status.load_failed");
            color = Player2NpcUiTheme.WARN;
        } else if ("failed".equals(playerEngineLiveState())) {
            status = Component.translatable("screen.player2npc.ui.settings.status.saved_live_apply_failed");
            color = Player2NpcUiTheme.WARN;
        } else if ("pending".equals(playerEngineLiveState())) {
            status = Component.translatable("screen.player2npc.ui.settings.status.saved_live_pending");
            color = Player2NpcUiTheme.WARN;
        } else if (fields.isEmpty()) {
            status = Component.translatable("screen.player2npc.ui.settings.panel.subtitle");
            color = Player2NpcUiTheme.TEXT_DIM;
        } else if (!canManageGlobal()) {
            status = Component.translatable("screen.player2npc.ui.settings.read_only");
            color = Player2NpcUiTheme.WARN;
        } else {
            LocalStatus local = localStatuses.getOrDefault(tabWireName, LocalStatus.READY);
            status = Component.translatable(local.translationKey);
            color = local.color;
        }
        drawFirstLine(
                graphics,
                status,
                bounds.x() + 2,
                bounds.y() + 4,
                Math.max(20, bounds.width() - 4),
                color);
    }

    private Component actionResultComponent(String result) {
        return switch (result) {
            case "ok" -> Component.translatable("screen.player2npc.ui.settings.status.saved");
            case "op_required" -> Component.translatable("screen.player2npc.ui.settings.read_only");
            case "invalid_value" -> Component.translatable("screen.player2npc.ui.settings.status.invalid");
            case "load_failed" -> Component.translatable("screen.player2npc.ui.settings.status.load_failed");
            case "saved_live_pending" -> Component.translatable(
                    "screen.player2npc.ui.settings.status.saved_live_pending");
            case "saved_live_apply_failed" -> Component.translatable(
                    "screen.player2npc.ui.settings.status.saved_live_apply_failed");
            case "save_failed" -> Component.translatable("screen.player2npc.ui.settings.status.save_failed");
            default -> Component.translatable("screen.player2npc.ui.settings.status.save_failed");
        };
    }

    private void commitNumeric(GuiSettingDefinition definition, int rawValue) {
        int value = clamp(rawValue, definition.minimum(), definition.maximum());
        Player2NpcNumberInputWidget input = numericInputs.get(definition.id());
        if (input != null) {
            input.setCommittedValue(value);
        }
        Player2NpcSliderWidget slider = numericSliders.get(definition.id());
        if (slider != null) {
            slider.setCurrentValue(value);
        }
        numericDrafts.remove(definition.id());
        emit(definition, String.valueOf(value));
    }

    private void recordNumericDraft(GuiSettingDefinition definition, String draft) {
        numericDrafts.put(definition.id(), draft);
        if (draft == null || draft.isEmpty()) {
            return;
        }
        try {
            int value = clamp(Integer.parseInt(draft), definition.minimum(), definition.maximum());
            Player2NpcSliderWidget slider = numericSliders.get(definition.id());
            if (slider != null) {
                slider.setCurrentValue(value);
            }
        } catch (NumberFormatException ignored) {
            localStatuses.put(tabWireName, LocalStatus.INVALID);
        }
    }

    private void saveText(GuiSettingDefinition definition) {
        Player2NpcTextInputWidget input = textInputs.get(definition.id());
        String value = input == null
                ? textDrafts.getOrDefault(definition.id(), snapshotValue(definition))
                : input.getValue();
        if (value.length() > definition.maxTextLength() || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            localStatuses.put(tabWireName, LocalStatus.INVALID);
            requestWidgetRebuild.run();
            return;
        }
        textDrafts.remove(definition.id());
        focusedControlId = "";
        emit(definition, value);
        requestWidgetRebuild.run();
    }

    private void emit(GuiSettingDefinition definition, String value) {
        if (!canEdit(definition)) {
            localStatuses.put(tabWireName, LocalStatus.READ_ONLY);
            return;
        }
        LinkedHashMap<String, String> updated = new LinkedHashMap<>(currentSnapshot());
        if (definition.type() != GuiSettingType.BLOCK_POS) {
            updated.put(definition.snapshotField(), value == null ? "" : value);
        }
        updated.remove("actionResult");
        snapshots.put(tabWireName, Map.copyOf(updated));
        localStatuses.put(tabWireName, LocalStatus.SAVING);
        actionSink.send(definition.action(), value == null ? "" : value);
    }

    private void openItemEditor(GuiSettingDefinition definition) {
        ItemListDraft existing = itemListDrafts.get(definition.id());
        if (existing == null) {
            List<String> registryIds = registryItemIds();
            LinkedHashSet<String> selected = parseItemIds(
                    snapshotValue(definition), registryIds, definition.maxListEntries());
            existing = new ItemListDraft(selected, registryIds);
            itemListDrafts.put(definition.id(), existing);
        }
        activeItemEditorId = definition.id();
        focusedControlId = itemSearchFocusId(definition.id());
        localStatuses.remove(tabWireName);
        requestWidgetRebuild.run();
    }

    private void cancelItemEditor(GuiSettingDefinition definition) {
        itemListDrafts.remove(definition.id());
        activeItemEditorId = "";
        focusedControlId = "";
        localStatuses.remove(tabWireName);
        requestWidgetRebuild.run();
    }

    private void saveItemEditor(GuiSettingDefinition definition) {
        ItemListDraft draft = itemListDrafts.get(definition.id());
        if (draft == null) {
            return;
        }
        if (draft.selected.size() > Math.min(64, definition.maxListEntries())) {
            localStatuses.put(tabWireName, LocalStatus.LIST_LIMIT);
            requestWidgetRebuild.run();
            return;
        }
        String serialized = serializeItemIds(draft.selected);
        if (serialized.length() > definition.maxSerializedLength()) {
            localStatuses.put(tabWireName, LocalStatus.LIST_SERIALIZED_LIMIT);
            requestWidgetRebuild.run();
            return;
        }
        emit(definition, serialized);
        itemListDrafts.remove(definition.id());
        activeItemEditorId = "";
        focusedControlId = "";
        requestWidgetRebuild.run();
    }

    private void toggleEditorItem(GuiSettingDefinition definition, String itemId, boolean adding) {
        ItemListDraft draft = itemListDrafts.get(definition.id());
        if (draft == null) {
            return;
        }
        if (!adding) {
            draft.selected.remove(itemId);
            draft.invalidateResults();
            localStatuses.remove(tabWireName);
            requestWidgetRebuild.run();
            return;
        }
        int limit = Math.min(64, definition.maxListEntries());
        if (draft.selected.size() >= limit) {
            localStatuses.put(tabWireName, LocalStatus.LIST_LIMIT);
            requestWidgetRebuild.run();
            return;
        }
        LinkedHashSet<String> candidate = new LinkedHashSet<>(draft.selected);
        candidate.add(itemId);
        if (serializeItemIds(candidate).length() > definition.maxSerializedLength()) {
            localStatuses.put(tabWireName, LocalStatus.LIST_SERIALIZED_LIMIT);
            requestWidgetRebuild.run();
            return;
        }
        draft.selected.add(itemId);
        draft.invalidateResults();
        localStatuses.remove(tabWireName);
        requestWidgetRebuild.run();
    }

    private List<String> editorResults(ItemListDraft draft) {
        if (draft.cachedRevision == draft.revision) {
            return draft.cachedResults;
        }
        List<String> results;
        if (draft.query.isBlank()) {
            results = List.copyOf(draft.selected);
        } else {
            String query = draft.query.toLowerCase(Locale.ROOT);
            results = draft.registryIds.stream()
                    .filter(id -> !draft.selected.contains(id))
                    .filter(id -> id.toLowerCase(Locale.ROOT).contains(query))
                    .toList();
        }
        draft.cachedResults = results;
        draft.cachedRevision = draft.revision;
        return results;
    }

    private GuiSettingDefinition currentItemEditorDefinition() {
        if (activeItemEditorId.isBlank()) {
            return null;
        }
        return GuiSettingsCatalog.find(activeItemEditorId)
                .filter(definition -> definition.type() == GuiSettingType.ITEM_LIST)
                .filter(definition -> definition.tabWireName().equals(tabWireName))
                .orElse(null);
    }

    private List<LayoutRow> layoutRows() {
        LinkedHashMap<GuiSettingSection, List<GuiSettingDefinition>> grouped = new LinkedHashMap<>();
        for (GuiSettingDefinition definition : GuiSettingsCatalog.forTab(tabWireName)) {
            grouped.computeIfAbsent(definition.section(), ignored -> new ArrayList<>()).add(definition);
        }
        ArrayList<LayoutRow> rows = new ArrayList<>();
        int y = 2;
        for (Map.Entry<GuiSettingSection, List<GuiSettingDefinition>> group : grouped.entrySet()) {
            rows.add(new LayoutRow(group.getKey(), null, y, SECTION_HEIGHT));
            y += SECTION_HEIGHT + ROW_GAP;
            if (!isCollapsed(group.getKey())) {
                for (GuiSettingDefinition definition : group.getValue()) {
                    rows.add(new LayoutRow(null, definition, y, SETTING_HEIGHT));
                    y += SETTING_HEIGHT + ROW_GAP;
                }
            }
        }
        return List.copyOf(rows);
    }

    private int mainContentHeight() {
        List<LayoutRow> rows = layoutRows();
        if (rows.isEmpty()) {
            return 0;
        }
        LayoutRow last = rows.get(rows.size() - 1);
        return last.y() + last.height() + 2;
    }

    private UiLayoutRect mainViewport() {
        return new UiLayoutRect(
                bounds.x(),
                bounds.y() + STATUS_HEIGHT,
                Math.max(0, bounds.width()),
                Math.max(0, bounds.height() - STATUS_HEIGHT));
    }

    private UiLayoutRect itemResultsViewport(UiLayoutRect viewport) {
        return new UiLayoutRect(
                viewport.x(),
                viewport.y() + ITEM_EDITOR_HEADER_HEIGHT,
                viewport.width(),
                Math.max(0, viewport.height() - ITEM_EDITOR_HEADER_HEIGHT));
    }

    private UiLayoutRect screenRect(UiLayoutRect viewport, LayoutRow row, int scroll) {
        return new UiLayoutRect(
                viewport.x() + 2,
                viewport.y() + row.y() - scroll,
                Math.max(1, viewport.width() - SCROLLBAR_WIDTH - 6),
                row.height());
    }

    private int currentMainScroll() {
        return tabScroll.getOrDefault(tabWireName, 0);
    }

    private int mainMaxScroll() {
        return Math.max(0, mainContentHeight() - mainViewport().height());
    }

    private int activeScroll() {
        GuiSettingDefinition editor = currentItemEditorDefinition();
        if (editor == null) {
            return currentMainScroll();
        }
        ItemListDraft draft = itemListDrafts.get(editor.id());
        return draft == null ? 0 : draft.scroll;
    }

    private void setActiveScroll(int value) {
        GuiSettingDefinition editor = currentItemEditorDefinition();
        if (editor == null) {
            tabScroll.put(tabWireName, value);
            return;
        }
        ItemListDraft draft = itemListDrafts.get(editor.id());
        if (draft != null) {
            draft.scroll = value;
        }
    }

    private UiLayoutRect activeScrollViewport() {
        GuiSettingDefinition editor = currentItemEditorDefinition();
        return editor == null ? mainViewport() : itemResultsViewport(mainViewport());
    }

    private int activeContentHeight() {
        GuiSettingDefinition editor = currentItemEditorDefinition();
        if (editor == null) {
            return mainContentHeight();
        }
        ItemListDraft draft = itemListDrafts.get(editor.id());
        int count = draft == null ? 0 : editorResults(draft).size();
        return count * (ITEM_RESULT_HEIGHT + ITEM_RESULT_GAP);
    }

    private int activeMaxScroll() {
        UiLayoutRect viewport = activeScrollViewport();
        return Math.max(0, activeContentHeight() - viewport.height());
    }

    private int itemEditorMaxScroll(UiLayoutRect viewport, int resultCount) {
        return Math.max(0,
                resultCount * (ITEM_RESULT_HEIGHT + ITEM_RESULT_GAP) - viewport.height());
    }

    private void clampCurrentScroll() {
        if (tabWireName.isBlank()) {
            return;
        }
        setActiveScroll(clamp(activeScroll(), 0, activeMaxScroll()));
    }

    private void renderScrollbar(GuiGraphics graphics, UiLayoutRect viewport) {
        UiRenderCompat.drawScrollbar(
                graphics,
                viewport.right() - SCROLLBAR_WIDTH,
                viewport.y(),
                viewport.height(),
                activeContentHeight(),
                viewport.height(),
                activeScroll());
    }

    private UiLayoutRect scrollbarTrack(UiLayoutRect viewport) {
        return new UiLayoutRect(
                viewport.right() - SCROLLBAR_WIDTH,
                viewport.y(),
                SCROLLBAR_WIDTH,
                viewport.height());
    }

    private UiLayoutRect scrollbarThumb(UiLayoutRect viewport) {
        UiLayoutRect track = scrollbarTrack(viewport);
        int contentHeight = activeContentHeight();
        if (contentHeight <= viewport.height() || viewport.height() <= 0) {
            return new UiLayoutRect(track.x(), track.y(), track.width(), 0);
        }
        int thumbHeight = Math.max(12, track.height() * viewport.height() / contentHeight);
        int maxScroll = Math.max(1, contentHeight - viewport.height());
        int thumbTravel = Math.max(1, track.height() - thumbHeight);
        int thumbY = track.y() + (int) ((long) thumbTravel * activeScroll() / maxScroll);
        return new UiLayoutRect(track.x(), thumbY, track.width(), thumbHeight);
    }

    private void updateScrollFromThumb(double mouseY) {
        UiLayoutRect viewport = activeScrollViewport();
        UiLayoutRect track = scrollbarTrack(viewport);
        int maxScroll = activeMaxScroll();
        if (maxScroll <= 0) {
            setActiveScroll(0);
            return;
        }
        int thumbHeight = scrollbarThumb(viewport).height();
        int thumbTravel = Math.max(1, track.height() - thumbHeight);
        int thumbTop = clamp(
                (int) Math.round(mouseY) - scrollbarGrabOffset,
                track.y(),
                track.y() + thumbTravel);
        int scroll = (int) Math.round((double) (thumbTop - track.y()) * maxScroll / thumbTravel);
        setActiveScroll(clamp(scroll, 0, maxScroll));
        requestWidgetRebuild.run();
    }

    private Map<String, String> currentSnapshot() {
        return snapshots.getOrDefault(tabWireName, Map.of());
    }

    private String snapshotValue(GuiSettingDefinition definition) {
        return currentSnapshot().getOrDefault(definition.snapshotField(), "");
    }

    private boolean canManageGlobal() {
        return Boolean.parseBoolean(currentSnapshot().getOrDefault("canManageGlobal", "false"));
    }

    private boolean playerEngineLoadFailed() {
        return Boolean.parseBoolean(currentSnapshot().getOrDefault(
                "settingsLoadFailed.pe",
                currentSnapshot().getOrDefault("settingsLoadFailed", "false")));
    }

    private boolean serverSettingsLoadFailed() {
        return Boolean.parseBoolean(currentSnapshot().getOrDefault(
                "settingsLoadFailed.server", "false"));
    }

    private boolean settingsLoadFailed() {
        return playerEngineLoadFailed() || serverSettingsLoadFailed();
    }

    private String playerEngineLiveState() {
        return currentSnapshot().getOrDefault("settingsLiveState.pe", "applied");
    }

    private boolean canEdit(GuiSettingDefinition definition) {
        if (!canManageGlobal()) {
            return false;
        }
        return switch (definition.source()) {
            case PLAYER_ENGINE -> !playerEngineLoadFailed();
            case PLAYER2_SERVER -> !serverSettingsLoadFailed();
        };
    }

    private GuiSettingOption currentOption(GuiSettingDefinition definition) {
        String token = snapshotValue(definition);
        for (GuiSettingOption option : definition.options()) {
            if (option.token().equals(token)) {
                return option;
            }
        }
        return definition.options().get(0);
    }

    private int parseNumericValue(GuiSettingDefinition definition, String raw) {
        if (raw != null && !raw.isBlank()) {
            try {
                return clamp(Integer.parseInt(raw), definition.minimum(), definition.maximum());
            } catch (NumberFormatException ignored) {
                localStatuses.put(tabWireName, LocalStatus.INVALID);
            }
        }
        return definition.minimum();
    }

    private int parseDraftOrValue(GuiSettingDefinition definition, String draft, int fallback) {
        if (draft == null || draft.isEmpty()) {
            return fallback;
        }
        try {
            return clamp(Integer.parseInt(draft), definition.minimum(), definition.maximum());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private List<String> registryItemIds() {
        ArrayList<String> ids = new ArrayList<>();
        var airId = BuiltInRegistries.ITEM.getKey(Items.AIR);
        BuiltInRegistries.ITEM.keySet().forEach(id -> {
            if (!id.equals(airId)) {
                ids.add(id.toString());
            }
        });
        Collections.sort(ids);
        return List.copyOf(ids);
    }

    private LinkedHashSet<String> parseItemIds(String raw, List<String> registryIds, int maxEntries) {
        Set<String> registry = Set.copyOf(registryIds);
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return selected;
        }
        for (String token : raw.split(",")) {
            String id = token.trim();
            if (!id.isEmpty() && registry.contains(id)) {
                selected.add(id);
                if (selected.size() >= Math.min(64, maxEntries)) {
                    break;
                }
            }
        }
        return selected;
    }

    private String serializeItemIds(LinkedHashSet<String> selected) {
        return String.join(",", selected);
    }

    private boolean isCollapsed(GuiSettingSection section) {
        return collapsedSections.computeIfAbsent(
                sectionStateKey(section), ignored -> section.collapsedByDefault());
    }

    private String sectionStateKey(GuiSettingSection section) {
        return tabWireName + ":" + section.key();
    }

    private void captureFocusedControl() {
        String focused = "";
        if (itemSearchInput != null && itemSearchInput.isFocused() && !activeItemEditorId.isBlank()) {
            focused = itemSearchFocusId(activeItemEditorId);
        }
        for (Map.Entry<String, Player2NpcNumberInputWidget> entry : numericInputs.entrySet()) {
            if (entry.getValue().isFocused()) {
                focused = numberFocusId(entry.getKey());
                break;
            }
        }
        for (Map.Entry<String, Player2NpcTextInputWidget> entry : textInputs.entrySet()) {
            if (entry.getValue().isFocused()) {
                focused = textFocusId(entry.getKey());
                break;
            }
        }
        focusedControlId = focused;
    }

    private void restoreFocus(
            String controlId,
            AbstractWidget widget,
            Consumer<AbstractWidget> focusWidget) {
        if (controlId.equals(focusedControlId)) {
            focusWidget.accept(widget);
        }
    }

    private String numberFocusId(String definitionId) {
        return "number:" + definitionId;
    }

    private String textFocusId(String definitionId) {
        return "text:" + definitionId;
    }

    private String itemSearchFocusId(String definitionId) {
        return "item-search:" + definitionId;
    }

    private void scheduleWidgetRebuild() {
        if (rebuildQueued) {
            return;
        }
        rebuildQueued = true;
        Minecraft.getInstance().execute(() -> {
            rebuildQueued = false;
            requestWidgetRebuild.run();
        });
    }

    private void drawFirstLine(
            GuiGraphics graphics,
            Component component,
            int x,
            int y,
            int width,
            int color) {
        if (width <= 0) {
            return;
        }
        var lines = font.split(component, width);
        if (!lines.isEmpty()) {
            graphics.drawString(font, lines.get(0), x, y, color, false);
        }
    }

    private void drawWrapped(
            GuiGraphics graphics,
            Component component,
            int x,
            int y,
            int width,
            int color,
            int maxLines) {
        if (width <= 0 || maxLines <= 0) {
            return;
        }
        var lines = font.split(component, width);
        for (int index = 0; index < Math.min(maxLines, lines.size()); index++) {
            graphics.drawString(font, lines.get(index), x, y + index * 10, color, false);
        }
    }

    private static boolean isNumeric(GuiSettingType type) {
        return type == GuiSettingType.INTEGER
                || type == GuiSettingType.PERCENT
                || type == GuiSettingType.PERMILLE;
    }

    private static boolean intersects(UiLayoutRect first, UiLayoutRect second) {
        return first.right() > second.x()
                && first.x() < second.right()
                && first.bottom() > second.y()
                && first.y() < second.bottom();
    }

    private static boolean isInside(UiLayoutRect rect, double x, double y) {
        return rect.width() > 0 && rect.height() > 0
                && x >= rect.x() && x < rect.right()
                && y >= rect.y() && y < rect.bottom();
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private record LayoutRow(
            GuiSettingSection section,
            GuiSettingDefinition definition,
            int y,
            int height) {
    }

    private static final class ItemListDraft {
        private final LinkedHashSet<String> selected;
        private final List<String> registryIds;
        private String query = "";
        private int scroll;
        private int revision;
        private int cachedRevision = -1;
        private List<String> cachedResults = List.of();

        private ItemListDraft(LinkedHashSet<String> selected, List<String> registryIds) {
            this.selected = selected;
            this.registryIds = registryIds;
        }

        private void invalidateResults() {
            revision++;
        }
    }

    private enum LocalStatus {
        READY("screen.player2npc.ui.settings.panel.subtitle", Player2NpcUiTheme.TEXT_DIM),
        SAVING("screen.player2npc.ui.settings.panel.subtitle", Player2NpcUiTheme.ACCENT),
        READ_ONLY("screen.player2npc.ui.settings.read_only", Player2NpcUiTheme.WARN),
        INVALID("screen.player2npc.ui.settings.status.invalid", Player2NpcUiTheme.BAD),
        LIST_LIMIT("screen.player2npc.ui.settings.status.invalid", Player2NpcUiTheme.WARN),
        LIST_SERIALIZED_LIMIT(
                "screen.player2npc.ui.settings.status.invalid",
                Player2NpcUiTheme.WARN);

        private final String translationKey;
        private final int color;

        LocalStatus(String translationKey, int color) {
            this.translationKey = translationKey;
            this.color = color;
        }
    }
}

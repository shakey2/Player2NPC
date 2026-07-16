package com.goodbird.player2npc.client.gui;

import com.google.gson.JsonElement;
import com.goodbird.player2npc.Player2NPC;
import com.goodbird.player2npc.client.gui.settings.Player2NpcSettingsPanel;
import com.goodbird.player2npc.client.util.SkinManager;
import com.goodbird.player2npc.companion.gui.settings.GuiSettingsCatalog;
import com.goodbird.player2npc.network.AutomatoneDespawnRequestPacket;
import com.goodbird.player2npc.network.AutomatoneSpawnRequestPacket;
import com.goodbird.player2npc.network.GuiSnapshotPacket;
import com.goodbird.player2npc.network.GuiSnapshotRequestPacket;
import com.goodbird.player2npc.network.GuiUpdatePacket;
import com.player2.playerengine.player2api.Character;
import com.player2.playerengine.player2api.auth.AuthenticationManager;
import com.player2.playerengine.player2api.utils.CharacterUtils;
import com.player2.playerengine.player2api.utils.Player2HTTPUtils;
import dev.architectury.networking.NetworkManager;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class Player2NpcHubScreen extends Screen {
    private static final String PLAYER2_GAME_ID = "player2-ai-npc-minecraft";
    private static final String PLAYER2_DISCORD_URL = "https://discord.gg/D5RykZHJUG";
    private static final String PLAYER2_PURCHASE_URL = "https://player2.game/profile/ai-power?tab=0";
    private static final String PLAYER2_FREE_JS_URL = "https://player2.game/profile/ai-power?tab=1";
    private static final ResourceLocation ICON_CHECK = Player2NPC.id("textures/gui/sprites/icon/check.png");
    private static final ResourceLocation ICON_INFO = Player2NPC.id("textures/gui/sprites/icon/info.png");
    private static final ResourceLocation ICON_LOCK = Player2NPC.id("textures/gui/sprites/icon/lock.png");
    private static final ResourceLocation ICON_PLUG_GREEN = Player2NPC.id("textures/gui/sprites/icon/plug_green.png");
    private static final ResourceLocation ICON_PLUG_RED = Player2NPC.id("textures/gui/sprites/icon/plug_red.png");
    private static final ResourceLocation ICON_WHISTLE = Player2NPC.id("textures/gui/sprites/icon/whistle.png");
    private static final ResourceLocation ICON_WHISTLE_DISABLED = Player2NPC.id("textures/gui/sprites/icon/whistle_disabled.png");
    private static final int NAV_BUTTON_HEIGHT = 20;
    private static final int NAV_BUTTON_GAP = 8;
    private static final int NAV_SCROLL_WHEEL_STEP = 3;
    private static final int NAV_SCROLLBAR_WIDTH = 6;
    private static final int GETTING_STARTED_SCROLL_WHEEL_STEP = 3;
    private static final int GETTING_STARTED_SCROLLBAR_WIDTH = 6;
    private static final int GETTING_STARTED_CARD_GAP = 7;
    private static final int GETTING_STARTED_ACTION_BUTTON_WIDTH = 112;
    private static final int GETTING_STARTED_ACTION_BUTTON_HEIGHT = 18;
    private static final int GETTING_STARTED_ACTION_BUTTON_GAP = 8;
    private static final int TABLE_ROW_HEIGHT = 18;
    private static final int TABLE_ACTION_WIDTH = 54;
    private static final int TABLE_ACTION_BUTTON_HEIGHT = 14;
    private static final int BUDGET_SCROLL_WHEEL_STEP = 22;
    private static final int BUDGET_SCROLLBAR_WIDTH = 6;
    private static final int BUDGET_ROW_COUNT = 10;
    private static final int BUDGET_ROW_HEIGHT = 30;
    private static final int BUDGET_RESET_GAP = 10;
    private static final int BUDGET_RESET_HEIGHT = 20;
    private static final int BUDGET_CALLS_MAX = 999_999;
    private static final int BUDGET_JOULES_MAX = 999_999_999;
    private static final int BUDGET_WINDOW_MINUTES_MAX = 1_440;
    private static final int BUDGET_REFRESH_SECONDS_MIN = 60;
    private static final int BUDGET_REFRESH_SECONDS_MAX = 86_400;
    private static final int NUMERIC_CONTROL_HEIGHT = 18;
    private static final int NUMERIC_CONTROL_GAP = 6;
    private static final int NUMERIC_INPUT_WIDTH = 64;
    private static final int BUDGET_NUMERIC_CONTROL_MAX_WIDTH = 200;
    private static final int COMPANION_CARD_TARGET_WIDTH = 260;
    private static final int COMPANION_CARD_MIN_WIDTH = 220;
    private static final int COMPANION_CARD_HEIGHT = 62;
    private static final int COMPANION_CARD_GAP = 8;
    private static final int COMPANION_CARD_BUTTON_SIZE = 18;
    private static final int COMPANION_CARD_BUTTON_GAP = 4;
    private static final int COMPANION_SCROLL_WHEEL_STEP = 22;
    private static final int COMPANION_SCROLLBAR_WIDTH = 6;
    private static final int COMPANION_DETAIL_SCROLL_WHEEL_STEP = 22;
    private static final int COMPANION_DETAIL_SCROLLBAR_WIDTH = 6;
    private static final int COMPANION_DETAIL_BUTTON_AREA_HEIGHT = 38;
    private static final int COMPANION_DETAIL_DESCRIPTION_Y = 92;
    private static final int COMPANION_DETAIL_TASK_BOX_GAP = 12;
    private static final int COMPANION_DETAIL_TASK_BOX_MIN_HEIGHT = 58;
    private static final int COMPANION_DETAIL_INVENTORY_BOX_GAP = 12;
    private static final int COMPANION_DETAIL_INVENTORY_BOX_MIN_HEIGHT = 126;
    private static final int COMPANION_DETAIL_MEMORY_BOX_GAP = 12;
    private static final int COMPANION_DETAIL_MEMORY_BOX_MIN_HEIGHT = 92;
    private static final int COMPANION_DETAIL_ADDITIONAL_PROMPT_BOX_GAP = 12;
    private static final int COMPANION_DETAIL_ADDITIONAL_PROMPT_BOX_MIN_HEIGHT = 118;
    private static final int ADDITIONAL_PROMPT_MAX_CHARS = 300;
    private static final int ADDITIONAL_PROMPT_INPUT_HEIGHT = 54;
    private static final int ADDITIONAL_PROMPT_SAVE_WIDTH = 70;
    private static final int ADDITIONAL_PROMPT_SAVE_HEIGHT = 18;
    private static final int ADDITIONAL_PROMPT_FEEDBACK_TICKS = 60;
    private static final int ACCESS_LIST_TEXT_MAX_CHARS = 900;
    private static final int ACCESS_LIST_BOX_MIN_HEIGHT = 28;
    private static final int ACCESS_LIST_BOX_MAX_HEIGHT = 44;
    private static final int ACCESS_LIST_BOX_GAP = 8;
    private static final int ACCESS_LIST_SAVE_WIDTH = 70;
    private static final int ACCESS_LIST_SAVE_HEIGHT = 18;
    private static final int ACCESS_LIST_SAVE_TOP_GAP = 12;
    private static final int ACCESS_LIST_SCROLL_WHEEL_STEP = 22;
    private static final int ACCESS_LIST_SCROLLBAR_WIDTH = 6;
    private static final int ACCESS_LIST_MODE_ROW_COUNT = 2;
    private static final int ACCESS_LIST_MODE_BOTTOM_GAP = 12;
    private static final int MOD_INTELLIGENCE_SCROLL_WHEEL_STEP = 22;
    private static final int MOD_INTELLIGENCE_SCROLLBAR_WIDTH = 6;
    private static final int MOD_INTELLIGENCE_REFRESH_TICKS = 40;
    private static final int MOD_INTELLIGENCE_QUERY_MAX_CHARS = 120;
    private static final int MOD_INTELLIGENCE_ACTION_HEIGHT = 20;
    private static final int MOD_INTELLIGENCE_QUERY_HEIGHT = 26;
    private static final int MOD_INTELLIGENCE_STAT_HEIGHT = 36;
    private static final int MOD_INTELLIGENCE_STAT_GAP = 6;
    private static final int MOD_INTELLIGENCE_SETTING_HEIGHT = 48;
    private static final int MOD_INTELLIGENCE_SETTING_GAP = 6;
    private static final int MOD_INTELLIGENCE_RESULT_HEIGHT = 36;
    private static final int MOD_INTELLIGENCE_MAX_ENRICHMENT_CALLS = 500;
    private static final int MOD_INTELLIGENCE_NUMERIC_CONTROL_MAX_WIDTH = 180;
    private static final int AI_MEMORY_SCROLL_WHEEL_STEP = 22;
    private static final int AI_MEMORY_SCROLLBAR_WIDTH = 6;
    private static final int AI_MEMORY_ROW_COUNT = 7;
    private static final int AI_MEMORY_ROW_HEIGHT = 48;
    private static final int AI_MEMORY_CALLS_MAX = 1_000;
    private static final int AI_MEMORY_COMPACT_ROW_HEIGHT = 64;
    private static final int AI_MEMORY_ROW_GAP = 6;
    private static final int AI_MEMORY_HEADER_HEIGHT = 30;
    private static final int AI_MEMORY_CONTROL_HEIGHT = 18;
    private static final int AI_MEMORY_TOGGLE_WIDTH = 38;
    private static final int AI_MEMORY_SLIDER_WIDTH = 92;
    private static final int AI_MEMORY_VALUE_WIDTH = 58;
    private static final int AI_MEMORY_CONTROL_GAP = 6;
    private static final int AI_MEMORY_FOOTER_HEIGHT = 20;
    private static final int PROFILES_SCROLL_WHEEL_STEP = 22;
    private static final int PROFILES_SCROLLBAR_WIDTH = 6;
    private static final int PROFILES_MAX_ENTRIES = 16;
    private static final int PROFILE_NAME_MAX_LENGTH = 80;
    private static final int PROFILE_BASE_URL_MAX_LENGTH = 2_048;
    private static final int PROFILE_CARD_HEIGHT = 72;
    private static final int PROFILE_CARD_GAP = 8;
    private static final int PROFILE_SECTION_GAP = 10;
    private static final int PROFILE_STATUS_HEIGHT = 62;
    private static final int PROFILE_BEHAVIOR_HEIGHT = 88;
    private static final int PROFILE_FALLBACK_HEIGHT = 68;
    private static final int PROFILE_NARROW_FALLBACK_HEIGHT = 100;
    private static final int PROFILE_INFO_HEIGHT = 72;
    private static final int PROFILE_FOOTER_HEIGHT = 16;
    private static final int PROFILE_BUTTON_HEIGHT = 20;
    private static final int PROFILE_BUTTON_GAP = 6;
    private static final int PROFILE_BUTTON_MAX_WIDTH = 112;
    private static final String DEFAULT_PROFILE_NAME = "Default";
    private static final String[] ACCESS_LIST_KEYS = {
            "userWhitelist",
            "userBlacklist",
            "botWhitelist",
            "botBlacklist"
    };
    private static final String[] ACCESS_LIST_LABEL_KEYS = {
            "screen.player2npc.ui.access.user_whitelist",
            "screen.player2npc.ui.access.user_blacklist",
            "screen.player2npc.ui.access.bot_whitelist",
            "screen.player2npc.ui.access.bot_blacklist"
    };
    private static final int COMPANION_INVENTORY_SLOT_GAP = 3;
    private static final int COMPANION_INVENTORY_HOTBAR_GAP = 8;
    private static final int COMPANION_INVENTORY_EQUIPMENT_GAP = 4;
    private static final int COMPANION_TASK_HISTORY_MAX_ENTRIES = 8;
    private static final int COMPANION_DETAIL_REFRESH_TICKS = 20;

    private Character[] characters = new Character[0];
    private boolean loadingCharacters;
    private String characterStateKey = "screen.player2npc.ui.companions.loading";
    private Character selectedCharacter;
    private Player2NpcTab selectedTab = Player2NpcTab.GETTING_STARTED;
    private int companionScroll;
    private boolean draggingCompanionScrollbar;
    private int companionScrollbarGrabOffset;
    private int companionDetailScroll;
    private boolean draggingCompanionDetailScrollbar;
    private int companionDetailScrollbarGrabOffset;
    private int gettingStartedScroll;
    private boolean draggingGettingStartedScrollbar;
    private int gettingStartedScrollbarGrabOffset;
    private int navScroll;
    private boolean draggingNavScrollbar;
    private int navScrollbarGrabOffset;
    private int budgetScroll;
    private boolean draggingBudgetScrollbar;
    private int budgetScrollbarGrabOffset;
    private int accessListScroll;
    private boolean draggingAccessListScrollbar;
    private int accessListScrollbarGrabOffset;
    private int modIntelligenceScroll;
    private boolean draggingModIntelligenceScrollbar;
    private int modIntelligenceScrollbarGrabOffset;
    private int aiMemoryScroll;
    private boolean draggingAiMemoryScrollbar;
    private int aiMemoryScrollbarGrabOffset;
    private int profilesScroll;
    private boolean draggingProfilesScrollbar;
    private int profilesScrollbarGrabOffset;
    private ProfileDiscoveryState profileDiscoveryState = ProfileDiscoveryState.IDLE;
    private int profileDiscoveryRequestId;
    private String profilePatronTier = "";
    private List<String> discoveredProfileNames = List.of();
    private int modIntelligenceRefreshTicks;
    private AuthButtonState authorizeModState = AuthButtonState.CHECKING;
    private int authorizeModRequestId;
    private int companionDetailRefreshTicks;
    private int lastMouseX;
    private int lastMouseY;
    private Map<String, String> globalSnapshotState = Map.of();
    private final Map<Player2NpcTab, Map<String, String>> snapshots = new LinkedHashMap<>();
    private final Map<Player2NpcTab, Map<String, CompoundTag>> snapshotItemFields = new LinkedHashMap<>();
    private final Map<String, Deque<String>> companionTaskHistory = new LinkedHashMap<>();
    private final Map<String, String> companionLastTaskStatus = new LinkedHashMap<>();
    private final Map<String, String> additionalPromptDrafts = new LinkedHashMap<>();
    private Player2NpcPromptBoxWidget additionalPromptInput;
    private Player2NpcButtonWidget additionalPromptSaveButton;
    private String additionalPromptInputCharacterId = "";
    private boolean updatingAdditionalPromptInput;
    private String additionalPromptActionResult = "";
    private int additionalPromptActionResultTicks;
    private final Map<String, String> accessListDrafts = new LinkedHashMap<>();
    private final Map<String, Player2NpcPromptBoxWidget> accessListInputs = new LinkedHashMap<>();
    private Player2NpcButtonWidget accessListSaveButton;
    private boolean updatingAccessListInput;
    private String modIntelligenceQueryDraft = "";
    private Player2NpcPromptBoxWidget modIntelligenceQueryInput;
    private Player2NpcButtonWidget modIntelligenceEnrichButton;
    private Player2NpcButtonWidget modIntelligenceRebuildButton;
    private Player2NpcButtonWidget modIntelligenceSearchButton;
    private boolean updatingModIntelligenceQueryInput;
    private final Map<String, String> numericInputDrafts = new LinkedHashMap<>();
    private final Map<String, Player2NpcNumberInputWidget> numericInputs = new LinkedHashMap<>();
    private final Map<String, Player2NpcSliderWidget> numericSliders = new LinkedHashMap<>();
    private final Set<Player2NpcTab> settingsSubviewTabs = EnumSet.noneOf(Player2NpcTab.class);
    private Player2NpcSettingsPanel settingsPanel;
    private Player2NpcSliderWidget aiMemoryToolTopKSlider;
    private Player2NpcSliderWidget aiMemoryCallsSlider;

    private enum AuthButtonState {
        CHECKING,
        CONNECTED,
        NEEDS_AUTH,
        AUTHORIZING
    }

    private enum ProfileDiscoveryState {
        IDLE,
        LOADING,
        AUTH_REQUIRED,
        READY,
        FAILED
    }

    private record ProfileDiscovery(boolean connected, String patronTier, List<String> profileNames) {
    }

    private enum GettingStartedAction {
        AUTHORIZE("screen.player2npc.ui.action.authorize_mod", GETTING_STARTED_ACTION_BUTTON_WIDTH),
        DISCORD("screen.player2npc.ui.action.player2_discord", 104),
        PURCHASE("screen.player2npc.ui.action.purchase", 78),
        FREE_JS("screen.player2npc.ui.action.free_js", 78);

        private final String labelKey;
        private final int width;

        GettingStartedAction(String labelKey, int width) {
            this.labelKey = labelKey;
            this.width = width;
        }
    }

    public Player2NpcHubScreen() {
        super(Component.translatable("screen.player2npc.ui.title"));
    }

    public static void acceptSnapshot(GuiSnapshotPacket packet) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.screen instanceof Player2NpcHubScreen screen) {
                boolean keepTextFocus = screen.isAdditionalPromptInputFocused()
                        || screen.isAccessListInputFocused()
                        || screen.isModIntelligenceQueryInputFocused()
                        || screen.isNumericInputFocused()
                        || screen.isSettingsInputFocused();
                if (packet.tab() == Player2NpcTab.UNKNOWN) {
                    screen.globalSnapshotState = packet.fields();
                } else {
                    screen.globalSnapshotState = Map.of();
                    Map<String, String> snapshotFields = packet.tab() == Player2NpcTab.MOD_INTELLIGENCE
                            ? screen.mergeModIntelligenceSnapshotFields(packet.fields())
                            : packet.fields();
                    screen.snapshots.put(packet.tab(), snapshotFields);
                    screen.snapshotItemFields.put(packet.tab(), packet.itemFields());
                    if (screen.settingsPanel != null) {
                        screen.settingsPanel.updateSnapshot(packet.tab().wireName(), snapshotFields);
                    }
                    if (packet.tab() == Player2NpcTab.COMPANIONS) {
                        screen.ingestCompanionTaskStatuses(snapshotFields);
                        screen.captureCompanionActionResult(snapshotFields);
                    } else if (packet.tab() == Player2NpcTab.ACCESS_LISTS && !keepTextFocus) {
                        screen.syncAccessListDrafts(snapshotFields);
                    } else if (packet.tab() == Player2NpcTab.MOD_INTELLIGENCE && !keepTextFocus) {
                        screen.syncModIntelligenceQueryDraft(snapshotFields);
                    }
                    if (packet.tab() == Player2NpcTab.MOD_INTELLIGENCE
                            && screen.isModIntelligenceQueryInputFocused()) {
                        screen.refreshModIntelligenceControlStates(snapshotFields);
                    }
                }
                if (!keepTextFocus) {
                    screen.rebuildHubWidgets();
                }
            }
        });
    }

    @Override
    protected void init() {
        super.init();
        if (settingsPanel == null) {
            settingsPanel = new Player2NpcSettingsPanel(
                    font, this::sendSettingsUpdate, this::rebuildHubWidgets);
        }
        refreshAuthorizeModState();
        rebuildHubWidgets();
        if (selectedTab == Player2NpcTab.COMPANIONS && characters.length == 0 && !loadingCharacters) {
            fetchCharacters();
        }
        requestSnapshot(selectedTab);
        requestHeaderSnapshot();
    }

    @Override
    public void removed() {
        if (settingsPanel != null) {
            settingsPanel.commitInputs();
        }
        for (Player2NpcNumberInputWidget input : new ArrayList<>(numericInputs.values())) {
            input.commit();
        }
        super.removed();
    }

    @Override
    public void tick() {
        super.tick();
        if (additionalPromptActionResultTicks > 0) {
            additionalPromptActionResultTicks--;
            if (additionalPromptActionResultTicks == 0) {
                additionalPromptActionResult = "";
            }
        }
        if (selectedTab == Player2NpcTab.MOD_INTELLIGENCE) {
            modIntelligenceRefreshTicks++;
            if (modIntelligenceRefreshTicks >= MOD_INTELLIGENCE_REFRESH_TICKS) {
                modIntelligenceRefreshTicks = 0;
                requestSnapshot(Player2NpcTab.MOD_INTELLIGENCE);
            }
        } else {
            modIntelligenceRefreshTicks = 0;
        }
        if (isAdditionalPromptInputFocused()) {
            companionDetailRefreshTicks = 0;
            return;
        }
        if (selectedTab == Player2NpcTab.COMPANIONS && selectedCharacter != null) {
            companionDetailRefreshTicks++;
            if (companionDetailRefreshTicks >= COMPANION_DETAIL_REFRESH_TICKS) {
                companionDetailRefreshTicks = 0;
                requestSnapshot(Player2NpcTab.COMPANIONS);
            }
        } else {
            companionDetailRefreshTicks = 0;
        }
    }

    private void rebuildHubWidgets() {
        boolean restoreAdditionalPromptFocus = isAdditionalPromptInputFocused();
        boolean restoreModIntelligenceFocus = isModIntelligenceQueryInputFocused();
        String restoreAccessListFocus = focusedAccessListKey();
        String restoreNumericFocus = focusedNumericInputKey();
        if (settingsPanel != null) {
            settingsPanel.clearWidgetReferences();
        }
        setFocused(null);
        clearWidgets();
        additionalPromptInput = null;
        additionalPromptSaveButton = null;
        additionalPromptInputCharacterId = "";
        accessListInputs.clear();
        accessListSaveButton = null;
        modIntelligenceQueryInput = null;
        modIntelligenceEnrichButton = null;
        modIntelligenceRebuildButton = null;
        modIntelligenceSearchButton = null;
        numericInputs.clear();
        numericSliders.clear();
        aiMemoryToolTopKSlider = null;
        aiMemoryCallsSlider = null;
        UiLayoutRect frame = frame();
        UiLayoutRect nav = navPane(frame);
        UiLayoutRect navViewport = navViewport(nav);
        Player2NpcTab[] tabs = Player2NpcTab.visibleTabs();
        navScroll = Math.min(navScroll, navScrollMax(navViewport, tabs.length));
        int navButtonWidth = Math.max(72, navViewport.width() - NAV_SCROLLBAR_WIDTH - 8);
        int navX = navViewport.x() + (navViewport.width() - navButtonWidth) / 2;
        for (int i = 0; i < tabs.length; i++) {
            Player2NpcTab tab = tabs[i];
            int tabY = navButtonY(navViewport, i) - navScroll;
            addRenderableWidget(new Player2NpcButtonWidget(navX, tabY, navButtonWidth, NAV_BUTTON_HEIGHT,
                    Component.translatable(tab.translationKey()), tab == selectedTab, () -> {
                selectedTab = tab;
                selectedCharacter = null;
                companionScroll = 0;
                companionDetailScroll = 0;
                accessListScroll = 0;
                modIntelligenceScroll = 0;
                aiMemoryScroll = 0;
                profilesScroll = 0;
                modIntelligenceRefreshTicks = 0;
                clearAdditionalPromptFeedback();
                if (tab == Player2NpcTab.GETTING_STARTED) {
                    refreshAuthorizeModState();
                }
                if (tab == Player2NpcTab.COMPANIONS && characters.length == 0 && !loadingCharacters) {
                    fetchCharacters();
                }
                if (tab == Player2NpcTab.PROFILES_MODELS) {
                    refreshProfilesAndModels();
                }
                requestSnapshot(tab);
                requestHeaderSnapshot();
                rebuildHubWidgets();
            }).withClip(navViewport));
        }

        int topRight = frame.right() - 82;
        if (hasAlternateSettingsView()) {
            addRenderableWidget(new Player2NpcButtonWidget(
                    frame.right() - 176,
                    frame.y() + 7,
                    86,
                    18,
                    Component.translatable(isSettingsView()
                            ? "screen.player2npc.ui.settings.action.overview"
                            : "screen.player2npc.ui.settings.action.show"),
                    false,
                    this::toggleSettingsView));
        }
        addRenderableWidget(new Player2NpcButtonWidget(topRight, frame.y() + 7, 72, 18,
                Component.translatable("screen.player2npc.ui.action.refresh"), true, () -> {
            if (selectedTab == Player2NpcTab.COMPANIONS) {
                fetchCharacters();
            }
            if (selectedTab == Player2NpcTab.GETTING_STARTED) {
                refreshAuthorizeModState();
            }
            if (selectedTab == Player2NpcTab.PROFILES_MODELS) {
                profilesScroll = 0;
                refreshProfilesAndModels();
                rebuildHubWidgets();
            }
            requestSnapshot(selectedTab);
            requestHeaderSnapshot();
        }));

        addTabControls();

        if (selectedTab == Player2NpcTab.COMPANIONS) {
            addCompanionButtons();
        }
        if (restoreAdditionalPromptFocus && additionalPromptInput != null) {
            setFocused(additionalPromptInput);
        } else if (restoreModIntelligenceFocus && modIntelligenceQueryInput != null) {
            setFocused(modIntelligenceQueryInput);
        } else if (restoreAccessListFocus != null) {
            Player2NpcPromptBoxWidget input = accessListInputs.get(restoreAccessListFocus);
            if (input != null) {
                setFocused(input);
            }
        } else if (restoreNumericFocus != null) {
            Player2NpcNumberInputWidget input = numericInputs.get(restoreNumericFocus);
            if (input != null) {
                setFocused(input);
            }
        }
    }

    private void addTabControls() {
        if (!globalSnapshotState.isEmpty()) {
            return;
        }
        Map<String, String> fields = snapshots.getOrDefault(selectedTab, Map.of());
        if (isSettingsView() && settingsPanel != null) {
            settingsPanel.updateSnapshot(selectedTab.wireName(), fields);
            settingsPanel.updateContext(selectedTab.wireName(), content().inset(14));
            settingsPanel.buildWidgets(
                    widget -> addRenderableWidget(widget),
                    widget -> setFocused(widget));
            return;
        }
        if (!canShowControls(fields)) {
            return;
        }
        if (selectedTab == Player2NpcTab.BEHAVIOR) {
            addBehaviorControls(fields);
        } else if (selectedTab == Player2NpcTab.BUDGET) {
            addBudgetControls(fields);
        } else if (selectedTab == Player2NpcTab.ACCESS_LISTS) {
            addAccessListControls(fields);
        } else if (selectedTab == Player2NpcTab.MOD_INTELLIGENCE) {
            addModIntelligenceControls(fields);
        } else if (selectedTab == Player2NpcTab.AI_MEMORY) {
            addAiMemoryControls(fields);
        } else if (selectedTab == Player2NpcTab.PROFILES_MODELS) {
            addProfilesControls(fields);
        }
    }

    private boolean hasAlternateSettingsView() {
        return selectedTab != Player2NpcTab.AUTOMATION_ITEMS
                && !GuiSettingsCatalog.forTab(selectedTab.wireName()).isEmpty();
    }

    private boolean isSettingsView() {
        if (GuiSettingsCatalog.forTab(selectedTab.wireName()).isEmpty()) {
            return false;
        }
        return selectedTab == Player2NpcTab.AUTOMATION_ITEMS
                || settingsSubviewTabs.contains(selectedTab);
    }

    private void toggleSettingsView() {
        if (!hasAlternateSettingsView()) {
            return;
        }
        if (settingsPanel != null) {
            settingsPanel.commitInputs();
        }
        if (!settingsSubviewTabs.add(selectedTab)) {
            settingsSubviewTabs.remove(selectedTab);
        }
        rebuildHubWidgets();
    }

    private void addBehaviorControls(Map<String, String> fields) {
        UiLayoutRect c = content().inset(14);
        int x = tableActionX(c);
        int row = 0;
        addActionButton(x, tableActionY(c, row++), TABLE_ACTION_WIDTH, TABLE_ACTION_BUTTON_HEIGHT,
                boolValue(fields.get("autoEquip")), true, () -> sendUpdate(Player2NpcTab.BEHAVIOR, "auto_equip", 0));
        boolean canEditLifecycle = Boolean.parseBoolean(fields.getOrDefault("canEditServerLifecycle", "true"));
        addActionButton(x, tableActionY(c, row++), TABLE_ACTION_WIDTH, TABLE_ACTION_BUTTON_HEIGHT,
                boolValue(fields.get("autoRespawn")), canEditLifecycle, () -> sendUpdate(Player2NpcTab.BEHAVIOR, "auto_respawn", 0));
        addActionButton(x, tableActionY(c, row), TABLE_ACTION_WIDTH, TABLE_ACTION_BUTTON_HEIGHT,
                boolValue(fields.get("botPermadeath")), canEditLifecycle, () -> sendUpdate(Player2NpcTab.BEHAVIOR, "bot_permadeath", 0));
    }

    private void addBudgetControls(Map<String, String> fields) {
        UiLayoutRect c = content().inset(14);
        UiLayoutRect viewport = budgetViewport(c);
        budgetScroll = Math.min(budgetScroll, budgetScrollMax(viewport));
        boolean canManage = Boolean.parseBoolean(fields.getOrDefault("canManage", "false"));
        UiLayoutRect table = budgetTable(c);
        addBudgetNumericControl(table, 1, "softCalls", "screen.player2npc.ui.budget.soft_calls",
                intValue(fields.get("softCalls")), 0, BUDGET_CALLS_MAX, true, canManage, viewport);
        addBudgetNumericControl(table, 2, "hardCalls", "screen.player2npc.ui.budget.hard_calls",
                intValue(fields.get("hardCalls")), 0, BUDGET_CALLS_MAX, true, canManage, viewport);
        addBudgetNumericControl(table, 3, "windowMinutes", "screen.player2npc.ui.budget.window_minutes",
                intValue(fields.get("windowMinutes")), 1, BUDGET_WINDOW_MINUTES_MAX, false, canManage, viewport);
        addBudgetNumericControl(table, 5, "softJoules", "screen.player2npc.ui.budget.soft_joules",
                intValue(fields.get("softJoules")), 0, BUDGET_JOULES_MAX, true, canManage, viewport);
        addBudgetNumericControl(table, 6, "hardJoules", "screen.player2npc.ui.budget.hard_joules",
                intValue(fields.get("hardJoules")), 0, BUDGET_JOULES_MAX, true, canManage, viewport);
        addBudgetNumericControl(table, 7, "joulesRefreshSeconds", "screen.player2npc.ui.budget.joules_refresh",
                intValue(fields.get("joulesRefreshSeconds")), BUDGET_REFRESH_SECONDS_MIN,
                BUDGET_REFRESH_SECONDS_MAX, true, canManage, viewport);
        addActionButton(table.x() + 6, budgetResetY(table), 130, BUDGET_RESET_HEIGHT,
                Component.translatable("screen.player2npc.ui.action.reset_usage"), true,
                () -> sendUpdate(Player2NpcTab.BUDGET, "resetUsage", 0), viewport);
    }

    private void addAccessListControls(Map<String, String> fields) {
        UiLayoutRect c = content().inset(14);
        int x = tableActionX(c);
        addActionButton(x, tableActionY(c, 0), TABLE_ACTION_WIDTH, TABLE_ACTION_BUTTON_HEIGHT,
                listModeValue(fields.get("userListMode")), true, () -> sendUpdate(Player2NpcTab.ACCESS_LISTS, "user_list_mode", 0));
        addActionButton(x, tableActionY(c, 1), TABLE_ACTION_WIDTH, TABLE_ACTION_BUTTON_HEIGHT,
                listModeValue(fields.get("botListMode")), true, () -> sendUpdate(Player2NpcTab.ACCESS_LISTS, "bot_list_mode", 0));
        UiLayoutRect viewport = accessListViewport(c);
        accessListScroll = Math.min(accessListScroll, accessListScrollMax(viewport));
        for (int i = 0; i < ACCESS_LIST_KEYS.length; i++) {
            String key = ACCESS_LIST_KEYS[i];
            UiLayoutRect box = accessListBox(c, i, viewport);
            String initial = accessListDrafts.getOrDefault(key, fields.getOrDefault(key, ""));
            Player2NpcPromptBoxWidget input = new Player2NpcPromptBoxWidget(font, box.x(), box.y(), box.width(), box.height(),
                    Component.translatable("screen.player2npc.ui.access.placeholder"),
                    Component.translatable(ACCESS_LIST_LABEL_KEYS[i]));
            input.setClip(viewport);
            input.setCharacterLimit(ACCESS_LIST_TEXT_MAX_CHARS);
            updatingAccessListInput = true;
            input.setValue(cleanAccessListDraft(initial));
            updatingAccessListInput = false;
            input.setValueListener(value -> {
                if (!updatingAccessListInput) {
                    accessListDrafts.put(key, cleanAccessListDraft(value));
                }
            });
            accessListInputs.put(key, input);
            addRenderableWidget(input);
        }
        UiLayoutRect save = accessListSaveButtonRect(c, viewport);
        accessListSaveButton = new Player2NpcButtonWidget(save.x(), save.y(), save.width(), save.height(),
                Component.translatable("screen.player2npc.ui.action.save"), true, this::saveAccessLists);
        accessListSaveButton.withClip(viewport);
        addRenderableWidget(accessListSaveButton);
    }

    private UiLayoutRect accessListViewport(UiLayoutRect content) {
        int y = content.y() + 24 + ACCESS_LIST_MODE_ROW_COUNT * TABLE_ROW_HEIGHT + ACCESS_LIST_MODE_BOTTOM_GAP;
        return new UiLayoutRect(content.x(), y, content.width(), Math.max(0, content.bottom() - y - 4));
    }

    private UiLayoutRect accessListBox(UiLayoutRect c, int index, UiLayoutRect viewport) {
        int x = viewport.x() + 6;
        int y = viewport.y() - accessListScroll;
        int boxHeight = ACCESS_LIST_BOX_MAX_HEIGHT;
        int rowHeight = 10 + boxHeight + ACCESS_LIST_BOX_GAP;
        return new UiLayoutRect(x, y + index * rowHeight + 12,
                Math.max(80, viewport.width() - ACCESS_LIST_SCROLLBAR_WIDTH - 18), boxHeight);
    }

    private int accessListContentHeight() {
        int rowHeight = 10 + ACCESS_LIST_BOX_MAX_HEIGHT + ACCESS_LIST_BOX_GAP;
        return ACCESS_LIST_KEYS.length * rowHeight + ACCESS_LIST_SAVE_TOP_GAP + ACCESS_LIST_SAVE_HEIGHT + 12;
    }

    private int accessListScrollMax(UiLayoutRect viewport) {
        return Math.max(0, accessListContentHeight() - viewport.height());
    }

    private UiLayoutRect accessListScrollbarTrack(UiLayoutRect viewport) {
        return new UiLayoutRect(viewport.right() - ACCESS_LIST_SCROLLBAR_WIDTH, viewport.y(),
                ACCESS_LIST_SCROLLBAR_WIDTH, viewport.height());
    }

    private UiLayoutRect accessListScrollbarThumb(UiLayoutRect viewport) {
        UiLayoutRect track = accessListScrollbarTrack(viewport);
        int contentHeight = accessListContentHeight();
        if (contentHeight <= viewport.height() || track.height() <= 0) {
            return new UiLayoutRect(track.x(), track.y(), track.width(), 0);
        }
        int thumbHeight = Math.max(12, track.height() * viewport.height() / contentHeight);
        int maxScroll = Math.max(1, contentHeight - viewport.height());
        int thumbTravel = Math.max(1, track.height() - thumbHeight);
        int clampedScroll = clampInt(accessListScroll, 0, maxScroll);
        int thumbY = track.y() + (int) ((long) thumbTravel * clampedScroll / maxScroll);
        return new UiLayoutRect(track.x(), thumbY, track.width(), thumbHeight);
    }

    private UiLayoutRect accessListSaveButtonRect(UiLayoutRect c, UiLayoutRect viewport) {
        int contentWidth = Math.max(80, viewport.width() - ACCESS_LIST_SCROLLBAR_WIDTH - 12);
        int rowHeight = 10 + ACCESS_LIST_BOX_MAX_HEIGHT + ACCESS_LIST_BOX_GAP;
        int y = viewport.y() - accessListScroll + ACCESS_LIST_KEYS.length * rowHeight + ACCESS_LIST_SAVE_TOP_GAP;
        return new UiLayoutRect(viewport.x() + contentWidth - ACCESS_LIST_SAVE_WIDTH,
                y,
                ACCESS_LIST_SAVE_WIDTH, ACCESS_LIST_SAVE_HEIGHT);
    }

    private void saveAccessLists() {
        StringBuilder payload = new StringBuilder();
        for (int i = 0; i < ACCESS_LIST_KEYS.length; i++) {
            if (i > 0) {
                payload.append('\n');
            }
            String key = ACCESS_LIST_KEYS[i];
            String value = accessListInputs.containsKey(key)
                    ? accessListInputs.get(key).getValue()
                    : accessListDrafts.getOrDefault(key, "");
            String clean = cleanAccessListDraft(value);
            accessListDrafts.put(key, clean);
            payload.append(clean);
        }
        sendUpdateText(Player2NpcTab.ACCESS_LISTS, "save_access_lists", payload.toString());
    }

    private void syncAccessListDrafts(Map<String, String> fields) {
        for (String key : ACCESS_LIST_KEYS) {
            accessListDrafts.put(key, cleanAccessListDraft(fields.getOrDefault(key, "")));
        }
    }

    private String cleanAccessListDraft(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace('\r', ',').replace('\n', ',').trim();
    }

    private void addModIntelligenceControls(Map<String, String> fields) {
        UiLayoutRect c = content().inset(14);
        UiLayoutRect viewport = modIntelligenceViewport(c);
        modIntelligenceScroll = Math.min(modIntelligenceScroll, modIntelligenceScrollMax(viewport, fields));
        boolean canManage = Boolean.parseBoolean(fields.getOrDefault("canManage", "false"));
        boolean canManageSettings = Boolean.parseBoolean(
                fields.getOrDefault("canManageSettings", String.valueOf(canManage)));
        boolean enabled = Boolean.parseBoolean(fields.getOrDefault("enabled", "false"));
        boolean enrichmentEnabled = Boolean.parseBoolean(fields.getOrDefault("enrichmentEnabled", "false"));
        boolean enriching = Boolean.parseBoolean(fields.getOrDefault("enriching", "false"));
        boolean inspecting = Boolean.parseBoolean(fields.getOrDefault("inspecting", "false"));
        boolean bypassSupported = Boolean.parseBoolean(fields.getOrDefault("bypassSupported", "false"));
        int queued = intValue(fields.get("queuedEntries"));

        UiLayoutRect enrich = modIntelligenceEnrichButtonRect(viewport);
        modIntelligenceEnrichButton = addModIntelligenceButton(enrich,
                Component.translatable("screen.player2npc.ui.mod_intelligence.action.enrich"),
                canManage && enabled && enrichmentEnabled && queued > 0 && !enriching && !inspecting,
                true, () -> sendUpdate(Player2NpcTab.MOD_INTELLIGENCE, "enrich_now", 0), viewport);
        UiLayoutRect rebuild = modIntelligenceRebuildButtonRect(viewport);
        modIntelligenceRebuildButton = addModIntelligenceButton(rebuild,
                Component.translatable("screen.player2npc.ui.mod_intelligence.action.rebuild"),
                canManage && enabled && !inspecting && !enriching,
                false, () -> sendUpdate(Player2NpcTab.MOD_INTELLIGENCE, "rebuild_scan", 0), viewport);

        UiLayoutRect query = modIntelligenceQueryRect(viewport);
        modIntelligenceQueryInput = new Player2NpcPromptBoxWidget(font, query.x(), query.y(), query.width(), query.height(),
                Component.translatable("screen.player2npc.ui.mod_intelligence.query.placeholder"),
                Component.translatable("screen.player2npc.ui.mod_intelligence.query.label"));
        modIntelligenceQueryInput.setClip(viewport);
        modIntelligenceQueryInput.setCharacterLimit(MOD_INTELLIGENCE_QUERY_MAX_CHARS);
        updatingModIntelligenceQueryInput = true;
        modIntelligenceQueryInput.setValue(normalizeModIntelligenceQuery(modIntelligenceQueryDraft));
        updatingModIntelligenceQueryInput = false;
        modIntelligenceQueryInput.setValueListener(value -> {
            if (updatingModIntelligenceQueryInput) {
                return;
            }
            String normalized = normalizeModIntelligenceQuery(value);
            modIntelligenceQueryDraft = normalized;
            if (!normalized.equals(value)) {
                updatingModIntelligenceQueryInput = true;
                modIntelligenceQueryInput.setValue(normalized);
                updatingModIntelligenceQueryInput = false;
            }
            if (modIntelligenceSearchButton != null) {
                modIntelligenceSearchButton.active = canManage && !normalized.isBlank();
            }
        });
        addRenderableWidget(modIntelligenceQueryInput);

        UiLayoutRect search = modIntelligenceSearchButtonRect(viewport);
        modIntelligenceSearchButton = new Player2NpcButtonWidget(search.x(), search.y(), search.width(), search.height(),
                Component.translatable("screen.player2npc.ui.mod_intelligence.action.search"), true,
                this::submitModIntelligenceQuery).withClip(viewport);
        modIntelligenceSearchButton.active = canManage && !modIntelligenceQueryDraft.isBlank();
        addRenderableWidget(modIntelligenceSearchButton);

        for (int row = 0; row < 4; row++) {
            UiLayoutRect setting = modIntelligenceSettingRect(viewport, fields, row);
            if (row == 2) {
                int current = clampInt(intValue(fields.get("maxEnrichmentCalls")),
                        0, MOD_INTELLIGENCE_MAX_ENRICHMENT_CALLS);
                addNumericSliderInput(Player2NpcTab.MOD_INTELLIGENCE, "maxEnrichmentCalls",
                        "max_enrichment_calls",
                        Component.translatable("screen.player2npc.ui.mod_intelligence.setting.max_calls"),
                        modIntelligenceNumericControlRect(viewport, setting), current,
                        0, MOD_INTELLIGENCE_MAX_ENRICHMENT_CALLS, false, canManageSettings, viewport);
                continue;
            }
            String field = row == 0 ? "enabled" : row == 1 ? "enrichmentEnabled" : "bypassBudgetGate";
            String action = row == 0 ? "toggle_enabled" : row == 1 ? "toggle_enrichment" : "toggle_budget_bypass";
            boolean on = Boolean.parseBoolean(fields.getOrDefault(field, "false"));
            boolean active = canManageSettings && (row != 3 || bypassSupported);
            UiLayoutRect toggle = new UiLayoutRect(setting.right() - 58,
                    modIntelligenceSettingControlY(viewport, setting), 56, 18);
            addModIntelligenceButton(toggle, boolValue(String.valueOf(on)), active, on,
                    () -> sendUpdate(Player2NpcTab.MOD_INTELLIGENCE, action, 0), viewport);
        }
    }

    private Player2NpcButtonWidget addModIntelligenceButton(UiLayoutRect rect, Component label, boolean active,
                                                             boolean primary, Runnable onPress,
                                                             UiLayoutRect viewport) {
        Player2NpcButtonWidget button = new Player2NpcButtonWidget(rect.x(), rect.y(), rect.width(), rect.height(),
                label, primary, onPress).withClip(viewport);
        button.active = active;
        addRenderableWidget(button);
        return button;
    }

    private void refreshModIntelligenceControlStates(Map<String, String> fields) {
        boolean canManage = Boolean.parseBoolean(fields.getOrDefault("canManage", "false"));
        boolean enabled = Boolean.parseBoolean(fields.getOrDefault("enabled", "false"));
        boolean enrichmentEnabled = Boolean.parseBoolean(fields.getOrDefault("enrichmentEnabled", "false"));
        boolean enriching = Boolean.parseBoolean(fields.getOrDefault("enriching", "false"));
        boolean inspecting = Boolean.parseBoolean(fields.getOrDefault("inspecting", "false"));
        if (modIntelligenceEnrichButton != null) {
            modIntelligenceEnrichButton.active = canManage && enabled && enrichmentEnabled
                    && intValue(fields.get("queuedEntries")) > 0 && !enriching && !inspecting;
        }
        if (modIntelligenceRebuildButton != null) {
            modIntelligenceRebuildButton.active = canManage && enabled && !inspecting && !enriching;
        }
        if (modIntelligenceSearchButton != null) {
            modIntelligenceSearchButton.active = canManage && !modIntelligenceQueryDraft.isBlank();
        }
    }

    private void submitModIntelligenceQuery() {
        String query = normalizeModIntelligenceQuery(modIntelligenceQueryInput == null
                ? modIntelligenceQueryDraft
                : modIntelligenceQueryInput.getValue());
        modIntelligenceQueryDraft = query;
        if (!query.isBlank()) {
            sendUpdateText(Player2NpcTab.MOD_INTELLIGENCE, "query", query);
        }
    }

    private void syncModIntelligenceQueryDraft(Map<String, String> fields) {
        String query = fields.get("queryText");
        if (query != null) {
            modIntelligenceQueryDraft = normalizeModIntelligenceQuery(query);
        }
    }

    private Map<String, String> mergeModIntelligenceSnapshotFields(Map<String, String> incoming) {
        if (incoming.containsKey("queryText")) {
            return incoming;
        }
        Map<String, String> previous = snapshots.getOrDefault(Player2NpcTab.MOD_INTELLIGENCE, Map.of());
        if (previous.isEmpty()) {
            return incoming;
        }
        LinkedHashMap<String, String> merged = new LinkedHashMap<>(incoming);
        previous.forEach((key, value) -> {
            if (key.startsWith("query")) {
                merged.putIfAbsent(key, value);
            }
        });
        return Map.copyOf(merged);
    }

    private String normalizeModIntelligenceQuery(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String clean = value.replace('\r', ' ').replace('\n', ' ').trim();
        return clean.length() <= MOD_INTELLIGENCE_QUERY_MAX_CHARS
                ? clean
                : clean.substring(0, MOD_INTELLIGENCE_QUERY_MAX_CHARS);
    }

    private UiLayoutRect modIntelligenceViewport(UiLayoutRect content) {
        int y = content.y() + 24;
        return new UiLayoutRect(content.x(), y, content.width(), Math.max(0, content.bottom() - y - 4));
    }

    private int modIntelligenceBodyWidth(UiLayoutRect viewport) {
        return Math.max(96, viewport.width() - MOD_INTELLIGENCE_SCROLLBAR_WIDTH - 10);
    }

    private int modIntelligenceBodyX(UiLayoutRect viewport) {
        return viewport.x() + 4;
    }

    private int modIntelligenceBodyY(UiLayoutRect viewport) {
        return viewport.y() + 2 - modIntelligenceScroll;
    }

    private int modIntelligenceStatColumns(UiLayoutRect viewport) {
        return modIntelligenceBodyWidth(viewport) >= 420 ? 4 : 2;
    }

    private int modIntelligenceStatsHeight(UiLayoutRect viewport) {
        int rows = (4 + modIntelligenceStatColumns(viewport) - 1) / modIntelligenceStatColumns(viewport);
        return rows * MOD_INTELLIGENCE_STAT_HEIGHT + Math.max(0, rows - 1) * MOD_INTELLIGENCE_STAT_GAP;
    }

    private int modIntelligenceStatsY(UiLayoutRect viewport) {
        return modIntelligenceBodyY(viewport) + 38;
    }

    private int modIntelligenceActionsY(UiLayoutRect viewport) {
        return modIntelligenceStatsY(viewport) + modIntelligenceStatsHeight(viewport) + 10;
    }

    private int modIntelligenceQueryY(UiLayoutRect viewport) {
        return modIntelligenceActionsY(viewport) + MOD_INTELLIGENCE_ACTION_HEIGHT + 8;
    }

    private int modIntelligenceResultsY(UiLayoutRect viewport) {
        return modIntelligenceQueryY(viewport) + MOD_INTELLIGENCE_QUERY_HEIGHT + 10;
    }

    private int modIntelligenceResultBlockHeight(Map<String, String> fields) {
        int resultRows = Math.max(1, modIntelligenceQueryResultCount(fields));
        return 18 + resultRows * (MOD_INTELLIGENCE_RESULT_HEIGHT + 4);
    }

    private int modIntelligenceSettingsY(UiLayoutRect viewport, Map<String, String> fields) {
        return modIntelligenceResultsY(viewport) + modIntelligenceResultBlockHeight(fields) + 8;
    }

    private int modIntelligenceSettingsHeight(UiLayoutRect viewport) {
        return 4 * modIntelligenceSettingHeight(viewport) + 3 * MOD_INTELLIGENCE_SETTING_GAP;
    }

    private int modIntelligenceFooterY(UiLayoutRect viewport, Map<String, String> fields) {
        return modIntelligenceSettingsY(viewport, fields) + modIntelligenceSettingsHeight(viewport) + 8;
    }

    private UiLayoutRect modIntelligenceStatRect(UiLayoutRect viewport, int index) {
        int columns = modIntelligenceStatColumns(viewport);
        int width = (modIntelligenceBodyWidth(viewport) - (columns - 1) * MOD_INTELLIGENCE_STAT_GAP) / columns;
        int column = index % columns;
        int row = index / columns;
        return new UiLayoutRect(modIntelligenceBodyX(viewport) + column * (width + MOD_INTELLIGENCE_STAT_GAP),
                modIntelligenceStatsY(viewport) + row * (MOD_INTELLIGENCE_STAT_HEIGHT + MOD_INTELLIGENCE_STAT_GAP),
                width, MOD_INTELLIGENCE_STAT_HEIGHT);
    }

    private UiLayoutRect modIntelligenceEnrichButtonRect(UiLayoutRect viewport) {
        int width = Math.max(1, Math.min(100, (modIntelligenceBodyWidth(viewport) - 8) / 2));
        return new UiLayoutRect(modIntelligenceBodyX(viewport), modIntelligenceActionsY(viewport),
                width, MOD_INTELLIGENCE_ACTION_HEIGHT);
    }

    private UiLayoutRect modIntelligenceRebuildButtonRect(UiLayoutRect viewport) {
        UiLayoutRect enrich = modIntelligenceEnrichButtonRect(viewport);
        int available = modIntelligenceBodyWidth(viewport) - enrich.width() - 8;
        int width = Math.max(1, Math.min(104, available));
        return new UiLayoutRect(enrich.right() + 8, enrich.y(), width, MOD_INTELLIGENCE_ACTION_HEIGHT);
    }

    private UiLayoutRect modIntelligenceSearchButtonRect(UiLayoutRect viewport) {
        int width = Math.min(72, Math.max(56, modIntelligenceBodyWidth(viewport) / 4));
        return new UiLayoutRect(modIntelligenceBodyX(viewport) + modIntelligenceBodyWidth(viewport) - width,
                modIntelligenceQueryY(viewport), width, MOD_INTELLIGENCE_QUERY_HEIGHT);
    }

    private UiLayoutRect modIntelligenceQueryRect(UiLayoutRect viewport) {
        UiLayoutRect search = modIntelligenceSearchButtonRect(viewport);
        return new UiLayoutRect(modIntelligenceBodyX(viewport), modIntelligenceQueryY(viewport),
                Math.max(1, search.x() - modIntelligenceBodyX(viewport) - 6), MOD_INTELLIGENCE_QUERY_HEIGHT);
    }

    private UiLayoutRect modIntelligenceSettingRect(UiLayoutRect viewport, Map<String, String> fields, int row) {
        int height = modIntelligenceSettingHeight(viewport);
        return new UiLayoutRect(modIntelligenceBodyX(viewport),
                modIntelligenceSettingsY(viewport, fields) + row * (height + MOD_INTELLIGENCE_SETTING_GAP),
                modIntelligenceBodyWidth(viewport), height);
    }

    private int modIntelligenceSettingHeight(UiLayoutRect viewport) {
        return modIntelligenceBodyWidth(viewport) < 240 ? 72 : MOD_INTELLIGENCE_SETTING_HEIGHT;
    }

    private int modIntelligenceSettingControlY(UiLayoutRect viewport, UiLayoutRect setting) {
        return modIntelligenceBodyWidth(viewport) < 240
                ? setting.bottom() - 22
                : setting.y() + (setting.height() - 18) / 2;
    }

    private UiLayoutRect modIntelligenceNumericControlRect(UiLayoutRect viewport, UiLayoutRect setting) {
        int available = Math.max(1, setting.width() - 16);
        int width = modIntelligenceBodyWidth(viewport) < 240
                ? Math.min(MOD_INTELLIGENCE_NUMERIC_CONTROL_MAX_WIDTH, available)
                : Math.min(MOD_INTELLIGENCE_NUMERIC_CONTROL_MAX_WIDTH,
                Math.min(available, Math.max(120, setting.width() / 2)));
        return new UiLayoutRect(setting.right() - width - 8,
                modIntelligenceSettingControlY(viewport, setting), width, NUMERIC_CONTROL_HEIGHT);
    }

    private int modIntelligenceQueryResultCount(Map<String, String> fields) {
        return clampInt(intValue(fields.get("queryResultCount")), 0, 8);
    }

    private int modIntelligenceContentHeight(UiLayoutRect viewport, Map<String, String> fields) {
        return (modIntelligenceFooterY(viewport, fields) + 22)
                - (viewport.y() - modIntelligenceScroll);
    }

    private int modIntelligenceScrollMax(UiLayoutRect viewport, Map<String, String> fields) {
        return Math.max(0, modIntelligenceContentHeight(viewport, fields) - viewport.height());
    }

    private UiLayoutRect modIntelligenceScrollbarTrack(UiLayoutRect viewport) {
        return new UiLayoutRect(viewport.right() - MOD_INTELLIGENCE_SCROLLBAR_WIDTH, viewport.y(),
                MOD_INTELLIGENCE_SCROLLBAR_WIDTH, viewport.height());
    }

    private UiLayoutRect modIntelligenceScrollbarThumb(UiLayoutRect viewport, Map<String, String> fields) {
        UiLayoutRect track = modIntelligenceScrollbarTrack(viewport);
        int contentHeight = modIntelligenceContentHeight(viewport, fields);
        if (contentHeight <= viewport.height() || track.height() <= 0) {
            return new UiLayoutRect(track.x(), track.y(), track.width(), 0);
        }
        int thumbHeight = Math.max(12, track.height() * viewport.height() / contentHeight);
        int maxScroll = Math.max(1, contentHeight - viewport.height());
        int thumbTravel = Math.max(1, track.height() - thumbHeight);
        int clampedScroll = clampInt(modIntelligenceScroll, 0, maxScroll);
        int thumbY = track.y() + (int) ((long) thumbTravel * clampedScroll / maxScroll);
        return new UiLayoutRect(track.x(), thumbY, track.width(), thumbHeight);
    }

    private void addAiMemoryControls(Map<String, String> fields) {
        UiLayoutRect c = content().inset(14);
        UiLayoutRect viewport = aiMemoryViewport(c);
        aiMemoryScroll = Math.min(aiMemoryScroll, aiMemoryScrollMax(viewport));
        boolean canManageDeepsearch = Boolean.parseBoolean(fields.getOrDefault("canManageDeepsearch", "false"));

        for (int row = 0; row < AI_MEMORY_ROW_COUNT; row++) {
            UiLayoutRect setting = aiMemorySettingRect(viewport, row);
            if (row == 3 || row == 6) {
                boolean calls = row == 6;
                int initialValue = intValue(fields.get(calls ? "memoryCallsPerWindow" : "toolRetrievalTopK"));
                UiLayoutRect sliderRect = aiMemorySliderRect(viewport, setting);
                String titleKey = aiMemoryTitleKey(row);
                String action = calls ? "memory_calls_per_window" : "tool_retrieval_top_k";
                Player2NpcSliderWidget slider = new Player2NpcSliderWidget(
                        sliderRect.x(), sliderRect.y(), sliderRect.width(), sliderRect.height(),
                        calls ? 0 : 1, calls ? AI_MEMORY_CALLS_MAX : 50, 1, initialValue,
                        Component.translatable(titleKey),
                        value -> calls
                                ? Component.translatable("screen.player2npc.ui.ai_memory.value.calls", value)
                                : Component.literal(String.valueOf(value)),
                        value -> sendUpdate(Player2NpcTab.AI_MEMORY, action, value))
                        .withClip(viewport);
                slider.active = canManageDeepsearch;
                addRenderableWidget(slider);
                if (calls) {
                    aiMemoryCallsSlider = slider;
                } else {
                    aiMemoryToolTopKSlider = slider;
                }
                continue;
            }

            String field = switch (row) {
                case 0 -> "deepsearchRetry";
                case 1 -> "aliasLearning";
                case 2 -> "deepsearchChatNotice";
                case 4 -> "graphMemory";
                case 5 -> "companionMood";
                default -> "";
            };
            String action = switch (row) {
                case 0 -> "toggle_deepsearch_retry";
                case 1 -> "toggle_alias_learning";
                case 2 -> "toggle_deepsearch_notice";
                case 4 -> "toggle_graph_memory";
                case 5 -> "toggle_companion_mood";
                default -> "";
            };
            boolean on = Boolean.parseBoolean(fields.getOrDefault(field, "false"));
            Component label = Component.translatable(aiMemoryTitleKey(row));
            Component narration = Component.translatable("screen.player2npc.ui.ai_memory.control.narration",
                    label, boolValue(String.valueOf(on)));
            UiLayoutRect toggleRect = aiMemoryToggleRect(viewport, setting);
            Player2NpcToggleWidget toggle = new Player2NpcToggleWidget(
                    toggleRect.x(), toggleRect.y(), toggleRect.width(), toggleRect.height(),
                    narration, on, () -> sendUpdate(Player2NpcTab.AI_MEMORY, action, 0))
                    .withClip(viewport);
            toggle.active = canManageDeepsearch;
            addRenderableWidget(toggle);
        }
    }

    private UiLayoutRect aiMemoryViewport(UiLayoutRect content) {
        int y = content.y() + 34;
        return new UiLayoutRect(content.x(), y, content.width(), Math.max(0, content.bottom() - y - 4));
    }

    private int aiMemoryBodyWidth(UiLayoutRect viewport) {
        return Math.max(96, viewport.width() - AI_MEMORY_SCROLLBAR_WIDTH - 10);
    }

    private int aiMemoryBodyX(UiLayoutRect viewport) {
        return viewport.x() + 4;
    }

    private int aiMemoryBodyY(UiLayoutRect viewport) {
        return viewport.y() + 2 - aiMemoryScroll;
    }

    private boolean aiMemoryCompact(UiLayoutRect viewport) {
        return aiMemoryBodyWidth(viewport) < 360;
    }

    private int aiMemoryRowHeight(UiLayoutRect viewport) {
        return aiMemoryCompact(viewport) ? AI_MEMORY_COMPACT_ROW_HEIGHT : AI_MEMORY_ROW_HEIGHT;
    }

    private int aiMemorySettingsY(UiLayoutRect viewport) {
        return aiMemoryBodyY(viewport) + AI_MEMORY_HEADER_HEIGHT;
    }

    private UiLayoutRect aiMemorySettingRect(UiLayoutRect viewport, int row) {
        int height = aiMemoryRowHeight(viewport);
        return new UiLayoutRect(aiMemoryBodyX(viewport),
                aiMemorySettingsY(viewport) + row * (height + AI_MEMORY_ROW_GAP),
                aiMemoryBodyWidth(viewport), height);
    }

    private int aiMemoryFooterY(UiLayoutRect viewport) {
        int rowsHeight = AI_MEMORY_ROW_COUNT * aiMemoryRowHeight(viewport)
                + (AI_MEMORY_ROW_COUNT - 1) * AI_MEMORY_ROW_GAP;
        return aiMemorySettingsY(viewport) + rowsHeight + 8;
    }

    private int aiMemoryContentHeight(UiLayoutRect viewport) {
        return AI_MEMORY_HEADER_HEIGHT
                + AI_MEMORY_ROW_COUNT * aiMemoryRowHeight(viewport)
                + (AI_MEMORY_ROW_COUNT - 1) * AI_MEMORY_ROW_GAP
                + 8 + AI_MEMORY_FOOTER_HEIGHT;
    }

    private int aiMemoryScrollMax(UiLayoutRect viewport) {
        return Math.max(0, aiMemoryContentHeight(viewport) - viewport.height());
    }

    private int aiMemoryControlY(UiLayoutRect viewport, UiLayoutRect setting) {
        return aiMemoryCompact(viewport)
                ? setting.bottom() - AI_MEMORY_CONTROL_HEIGHT - 6
                : setting.y() + (setting.height() - AI_MEMORY_CONTROL_HEIGHT) / 2;
    }

    private UiLayoutRect aiMemoryToggleRect(UiLayoutRect viewport, UiLayoutRect setting) {
        return new UiLayoutRect(setting.right() - AI_MEMORY_TOGGLE_WIDTH - 8,
                aiMemoryControlY(viewport, setting), AI_MEMORY_TOGGLE_WIDTH, AI_MEMORY_CONTROL_HEIGHT);
    }

    private UiLayoutRect aiMemorySliderRect(UiLayoutRect viewport, UiLayoutRect setting) {
        int totalWidth = AI_MEMORY_SLIDER_WIDTH + AI_MEMORY_CONTROL_GAP + AI_MEMORY_VALUE_WIDTH;
        return new UiLayoutRect(setting.right() - totalWidth - 8,
                aiMemoryControlY(viewport, setting), AI_MEMORY_SLIDER_WIDTH, AI_MEMORY_CONTROL_HEIGHT);
    }

    private UiLayoutRect aiMemoryValueRect(UiLayoutRect viewport, UiLayoutRect setting) {
        UiLayoutRect slider = aiMemorySliderRect(viewport, setting);
        return new UiLayoutRect(slider.right() + AI_MEMORY_CONTROL_GAP, slider.y(),
                AI_MEMORY_VALUE_WIDTH, AI_MEMORY_CONTROL_HEIGHT);
    }

    private int aiMemoryControlBlockWidth(int row) {
        return row == 3 || row == 6
                ? AI_MEMORY_SLIDER_WIDTH + AI_MEMORY_CONTROL_GAP + AI_MEMORY_VALUE_WIDTH
                : AI_MEMORY_TOGGLE_WIDTH;
    }

    private int aiMemoryTextWidth(UiLayoutRect viewport, UiLayoutRect setting, int row) {
        return aiMemoryCompact(viewport)
                ? Math.max(40, setting.width() - 16)
                : Math.max(40, setting.width() - aiMemoryControlBlockWidth(row) - 26);
    }

    private UiLayoutRect aiMemoryHelpRect(UiLayoutRect viewport, UiLayoutRect setting, int row) {
        Component title = Component.translatable(aiMemoryTitleKey(row));
        Component op = Component.translatable("screen.player2npc.ui.ai_memory.op_badge");
        int reserved = 12 + (aiMemoryOperatorOnly(row) ? font.width(op) + 5 : 0);
        int markerX = setting.x() + 8
                + Math.min(font.width(title) + 5,
                Math.max(0, aiMemoryTextWidth(viewport, setting, row) - reserved));
        if (aiMemoryOperatorOnly(row)) {
            markerX += font.width(op) + 5;
        }
        return new UiLayoutRect(markerX, setting.y() + 5, 11, 11);
    }

    private UiLayoutRect aiMemoryScrollbarTrack(UiLayoutRect viewport) {
        return new UiLayoutRect(viewport.right() - AI_MEMORY_SCROLLBAR_WIDTH, viewport.y(),
                AI_MEMORY_SCROLLBAR_WIDTH, viewport.height());
    }

    private UiLayoutRect aiMemoryScrollbarThumb(UiLayoutRect viewport) {
        UiLayoutRect track = aiMemoryScrollbarTrack(viewport);
        int contentHeight = aiMemoryContentHeight(viewport);
        if (contentHeight <= viewport.height() || track.height() <= 0) {
            return new UiLayoutRect(track.x(), track.y(), track.width(), 0);
        }
        int thumbHeight = Math.max(12, track.height() * viewport.height() / contentHeight);
        int maxScroll = Math.max(1, contentHeight - viewport.height());
        int thumbTravel = Math.max(1, track.height() - thumbHeight);
        int clampedScroll = clampInt(aiMemoryScroll, 0, maxScroll);
        int thumbY = track.y() + (int) ((long) thumbTravel * clampedScroll / maxScroll);
        return new UiLayoutRect(track.x(), thumbY, track.width(), thumbHeight);
    }

    private boolean aiMemoryOperatorOnly(int row) {
        return row >= 0 && row < AI_MEMORY_ROW_COUNT;
    }

    private String aiMemoryTitleKey(int row) {
        return switch (row) {
            case 0 -> "screen.player2npc.ui.ai_memory.deepsearch_retry";
            case 1 -> "screen.player2npc.ui.ai_memory.alias_learning";
            case 2 -> "screen.player2npc.ui.ai_memory.deepsearch_notice";
            case 3 -> "screen.player2npc.ui.ai_memory.tool_top_k";
            case 4 -> "screen.player2npc.ui.ai_memory.graph_memory";
            case 5 -> "screen.player2npc.ui.ai_memory.companion_mood";
            default -> "screen.player2npc.ui.ai_memory.memory_calls";
        };
    }

    private String aiMemoryDescriptionKey(int row) {
        return aiMemoryTitleKey(row) + ".description";
    }



    private void addProfilesControls(Map<String, String> fields) {
        UiLayoutRect viewport = profilesViewport(content().inset(14));
        profilesScroll = Math.min(profilesScroll, profilesScrollMax(viewport));
        boolean canManage = Boolean.parseBoolean(fields.getOrDefault("canManage", "false"));
        boolean switching = "switch_profile".equals(fields.getOrDefault("fallbackBehavior", ""));
        String fallback = configuredFallback(fields);
        if (!profilesFeatureEligible()) {
            if (canManage) {
                addProfilesButton(profilesStatusHardStopButtonRect(viewport),
                        Component.translatable("screen.player2npc.ui.profiles.hard_stop"),
                        true, !switching,
                        () -> sendUpdate(Player2NpcTab.PROFILES_MODELS,
                                "fallback_behavior_stop", 0), viewport);
                addProfilesButton(profilesStatusDefaultButtonRect(viewport),
                        Component.translatable("screen.player2npc.ui.profiles.step_down"),
                        true, switching && fallback.isBlank(),
                        () -> sendUpdateText(Player2NpcTab.PROFILES_MODELS,
                                "fallback_behavior_switch", ""), viewport);
            }
            return;
        }

        boolean namedSwitchSupported = Boolean.parseBoolean(
                fields.getOrDefault("namedProfileSwitchSupported", "true"));
        boolean fallbackSupported = fallback.isBlank()
                || (namedSwitchSupported && configuredFallbackAvailable(fields));

        addProfilesButton(profilesBehaviorHardStopButtonRect(viewport),
                Component.translatable("screen.player2npc.ui.profiles.hard_stop"),
                canManage, !switching,
                () -> sendUpdate(Player2NpcTab.PROFILES_MODELS, "fallback_behavior_stop", 0), viewport);
        addProfilesButton(profilesBehaviorStepDownButtonRect(viewport),
                Component.translatable("screen.player2npc.ui.profiles.step_down"),
                canManage && fallbackSupported, switching,
                () -> sendUpdateText(Player2NpcTab.PROFILES_MODELS,
                        "fallback_behavior_switch", fallback), viewport);

        UiLayoutRect selector = profilesFallbackSelectorRect(viewport);
        addProfilesButton(selector, profileDisplayName(fallback),
                canManage && (namedSwitchSupported || !fallback.isBlank()), true,
                () -> cycleProfileFallback(fields), viewport);
    }

    private void addProfilesButton(UiLayoutRect rect, Component label, boolean active, boolean selected,
                                   Runnable onPress, UiLayoutRect viewport) {
        Player2NpcButtonWidget button = new Player2NpcButtonWidget(
                rect.x(), rect.y(), rect.width(), rect.height(), label, selected, onPress).withClip(viewport);
        button.active = active;
        button.visible = rect.bottom() > viewport.y() && rect.y() < viewport.bottom();
        addRenderableWidget(button);
    }

    private void cycleProfileFallback(Map<String, String> fields) {
        boolean namedSwitchSupported = Boolean.parseBoolean(
                fields.getOrDefault("namedProfileSwitchSupported", "true"));
        List<String> options = profileFallbackOptions(namedSwitchSupported);
        String current = configuredFallback(fields);
        int currentIndex = options.indexOf(current);
        int nextIndex = currentIndex < 0 ? 0 : (currentIndex + 1) % options.size();
        sendUpdateText(Player2NpcTab.PROFILES_MODELS, "set_fallback_profile", options.get(nextIndex));
    }

    private String configuredFallback(Map<String, String> fields) {
        String raw = fields.getOrDefault("fallbackProfile", "");
        if (raw.isBlank() || DEFAULT_PROFILE_NAME.equalsIgnoreCase(raw)) {
            return "";
        }
        return safeProfileName(raw);
    }

    private boolean configuredFallbackAvailable(Map<String, String> fields) {
        String fallback = configuredFallback(fields);
        if (fallback.isBlank()) {
            return true;
        }
        for (String name : namedProfileNames()) {
            if (name.equals(fallback)) {
                return true;
            }
        }
        return false;
    }

    private List<String> profileFallbackOptions(boolean includeNamedProfiles) {
        List<String> options = new ArrayList<>();
        options.add("");
        if (includeNamedProfiles) {
            options.addAll(namedProfileNames());
        }
        return List.copyOf(options);
    }

    private Component profileDisplayName(String profileName) {
        return profileName == null || profileName.isBlank()
                ? Component.translatable("screen.player2npc.ui.profiles.default_name")
                : Component.literal(profileName);
    }

    private boolean profileIsPatron() {
        return profileDiscoveryState == ProfileDiscoveryState.READY && !profilePatronTier.isBlank();
    }

    private boolean profilesFeatureEligible() {
        return profileIsPatron() && !namedProfileNames().isEmpty();
    }

    private Map<String, String> profilesFields() {
        return snapshots.getOrDefault(Player2NpcTab.PROFILES_MODELS, Map.of());
    }

    private boolean profilesCanManage() {
        return Boolean.parseBoolean(profilesFields().getOrDefault("canManage", "false"));
    }

    private int profileWrappedHeight(String key, int width, int maxLines) {
        int lines = font.split(Component.translatable(key), Math.max(20, width)).size();
        return Math.max(10, Math.min(Math.max(1, maxLines), Math.max(1, lines)) * 10);
    }

    private int profilesSubtitleHeight(UiLayoutRect content) {
        return Math.max(20, profileWrappedHeight("screen.player2npc.ui.profiles.body",
                content.width() - 12, 12));
    }

    private List<String> namedProfileNames() {
        List<String> names = new ArrayList<>();
        for (String raw : discoveredProfileNames) {
            String name = safeProfileName(raw);
            if (name.isEmpty() || DEFAULT_PROFILE_NAME.equalsIgnoreCase(name)
                    || containsProfileNameIgnoreCase(names, name)) {
                continue;
            }
            names.add(name);
        }
        return List.copyOf(names);
    }

    private boolean containsProfileNameIgnoreCase(List<String> names, String candidate) {
        for (String name : names) {
            if (name.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    private List<String> profileCardNames() {
        if (profileDiscoveryState != ProfileDiscoveryState.READY) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        names.add("");
        if (profileIsPatron()) {
            names.addAll(namedProfileNames());
        }
        return List.copyOf(names);
    }

    private UiLayoutRect profilesViewport(UiLayoutRect content) {
        int y = content.y() + 14 + profilesSubtitleHeight(content) + 4;
        return new UiLayoutRect(content.x(), y, content.width(), Math.max(0, content.bottom() - y - 4));
    }

    private int profilesBodyWidth(UiLayoutRect viewport) {
        return Math.max(96, viewport.width() - PROFILES_SCROLLBAR_WIDTH - 10);
    }

    private int profilesBodyX(UiLayoutRect viewport) {
        return viewport.x() + 4;
    }

    private int profilesBodyY(UiLayoutRect viewport) {
        return viewport.y() + 2 - profilesScroll;
    }

    private int profileColumns(UiLayoutRect viewport) {
        return profilesBodyWidth(viewport) >= 360 ? 2 : 1;
    }

    private int profileCardWidth(UiLayoutRect viewport) {
        int columns = profileColumns(viewport);
        return Math.max(1,
                (profilesBodyWidth(viewport) - (columns - 1) * PROFILE_CARD_GAP) / columns);
    }

    private UiLayoutRect profileCardRect(UiLayoutRect viewport, int index) {
        int columns = profileColumns(viewport);
        int width = profileCardWidth(viewport);
        int height = profileCardHeight(viewport);
        int column = index % columns;
        int row = index / columns;
        return new UiLayoutRect(profilesBodyX(viewport) + column * (width + PROFILE_CARD_GAP),
                profilesBodyY(viewport) + row * (height + PROFILE_CARD_GAP),
                width, height);
    }

    private int profileCardHeight(UiLayoutRect viewport) {
        int textWidth = Math.max(20, profileCardWidth(viewport) - 16);
        int descriptionHeight = Math.max(
                profileWrappedHeight("screen.player2npc.ui.profiles.default_description", textWidth, 12),
                profileWrappedHeight("screen.player2npc.ui.profiles.named_description", textWidth, 12));
        return Math.max(PROFILE_CARD_HEIGHT, 27 + descriptionHeight + 8);
    }

    private int profileCardsHeight(UiLayoutRect viewport) {
        int count = profileCardNames().size();
        if (count == 0) {
            return 0;
        }
        int rows = (count + profileColumns(viewport) - 1) / profileColumns(viewport);
        return rows * profileCardHeight(viewport) + (rows - 1) * PROFILE_CARD_GAP;
    }

    private int profilesSectionStartY(UiLayoutRect viewport) {
        int cardsHeight = profileCardsHeight(viewport);
        return profilesBodyY(viewport) + cardsHeight
                + (cardsHeight > 0 ? PROFILE_SECTION_GAP : 0);
    }

    private UiLayoutRect profilesStatusRect(UiLayoutRect viewport) {
        return new UiLayoutRect(profilesBodyX(viewport), profilesSectionStartY(viewport),
                profilesBodyWidth(viewport), profilesStatusHeight(viewport));
    }

    private boolean profilesNarrow(UiLayoutRect viewport) {
        return profilesBodyWidth(viewport) < 220;
    }

    private int profilesStatusBodyHeight(UiLayoutRect viewport) {
        return profileWrappedHeight(profilesStatusBodyKey(), profilesBodyWidth(viewport) - 16, 12);
    }

    private int profilesStatusHeight(UiLayoutRect viewport) {
        int controlsHeight = profilesCanManage()
                ? (profilesNarrow(viewport)
                ? PROFILE_BUTTON_HEIGHT * 2 + PROFILE_BUTTON_GAP : PROFILE_BUTTON_HEIGHT)
                : 0;
        int height = 25 + profilesStatusBodyHeight(viewport) + 8;
        if (controlsHeight > 0) {
            height += 7 + controlsHeight + 7;
        }
        return Math.max(PROFILE_STATUS_HEIGHT, height);
    }

    private UiLayoutRect profilesStatusHardStopButtonRect(UiLayoutRect viewport) {
        UiLayoutRect status = profilesStatusRect(viewport);
        int available = Math.max(40, status.width() - 16);
        if (profilesNarrow(viewport)) {
            int width = Math.min(PROFILE_BUTTON_MAX_WIDTH, available);
            int x = status.x() + Math.max(8, (status.width() - width) / 2);
            int y = status.bottom() - PROFILE_BUTTON_HEIGHT * 2 - PROFILE_BUTTON_GAP - 7;
            return new UiLayoutRect(x, y, width, PROFILE_BUTTON_HEIGHT);
        }
        int totalWidth = Math.min(PROFILE_BUTTON_MAX_WIDTH * 2 + PROFILE_BUTTON_GAP, available);
        int width = Math.max(20, (totalWidth - PROFILE_BUTTON_GAP) / 2);
        int x = status.x() + Math.max(8, (status.width() - totalWidth) / 2);
        return new UiLayoutRect(x, status.bottom() - PROFILE_BUTTON_HEIGHT - 7,
                width, PROFILE_BUTTON_HEIGHT);
    }

    private UiLayoutRect profilesStatusDefaultButtonRect(UiLayoutRect viewport) {
        UiLayoutRect hardStop = profilesStatusHardStopButtonRect(viewport);
        if (profilesNarrow(viewport)) {
            return new UiLayoutRect(hardStop.x(), hardStop.bottom() + PROFILE_BUTTON_GAP,
                    hardStop.width(), PROFILE_BUTTON_HEIGHT);
        }
        return new UiLayoutRect(hardStop.right() + PROFILE_BUTTON_GAP, hardStop.y(),
                hardStop.width(), PROFILE_BUTTON_HEIGHT);
    }

    private UiLayoutRect profilesBehaviorRect(UiLayoutRect viewport) {
        return new UiLayoutRect(profilesBodyX(viewport), profilesSectionStartY(viewport),
                profilesBodyWidth(viewport), profilesBehaviorHeight(viewport));
    }

    private int profilesBehaviorDescriptionHeight(UiLayoutRect viewport) {
        return profileWrappedHeight("screen.player2npc.ui.profiles.soft_limit.description",
                profilesBodyWidth(viewport) - 16, 12);
    }

    private int profilesOperatorNoteHeight(UiLayoutRect viewport) {
        return profilesCanManage() ? 0
                : profileWrappedHeight("screen.player2npc.ui.profiles.operator_only",
                profilesBodyWidth(viewport) - 16, 8);
    }

    private int profilesBehaviorHeight(UiLayoutRect viewport) {
        int height = 22 + profilesBehaviorDescriptionHeight(viewport);
        int operatorHeight = profilesOperatorNoteHeight(viewport);
        if (operatorHeight > 0) {
            height += 4 + operatorHeight;
        }
        int controlsHeight = profilesNarrow(viewport)
                ? PROFILE_BUTTON_HEIGHT * 2 + PROFILE_BUTTON_GAP : PROFILE_BUTTON_HEIGHT;
        return Math.max(PROFILE_BEHAVIOR_HEIGHT,
                height + 7 + controlsHeight + 7);
    }

    private UiLayoutRect profilesBehaviorHardStopButtonRect(UiLayoutRect viewport) {
        UiLayoutRect behavior = profilesBehaviorRect(viewport);
        int available = Math.max(40, behavior.width() - 16);
        if (profilesNarrow(viewport)) {
            int width = Math.min(PROFILE_BUTTON_MAX_WIDTH, available);
            int x = behavior.x() + Math.max(8, (behavior.width() - width) / 2);
            int y = behavior.bottom() - PROFILE_BUTTON_HEIGHT * 2 - PROFILE_BUTTON_GAP - 7;
            return new UiLayoutRect(x, y, width, PROFILE_BUTTON_HEIGHT);
        }
        int totalWidth = Math.min(PROFILE_BUTTON_MAX_WIDTH * 2 + PROFILE_BUTTON_GAP, available);
        int width = Math.max(20, (totalWidth - PROFILE_BUTTON_GAP) / 2);
        int x = behavior.x() + Math.max(8, (behavior.width() - totalWidth) / 2);
        return new UiLayoutRect(x, behavior.bottom() - PROFILE_BUTTON_HEIGHT - 7,
                width, PROFILE_BUTTON_HEIGHT);
    }

    private UiLayoutRect profilesBehaviorStepDownButtonRect(UiLayoutRect viewport) {
        UiLayoutRect hardStop = profilesBehaviorHardStopButtonRect(viewport);
        if (profilesNarrow(viewport)) {
            return new UiLayoutRect(hardStop.x(), hardStop.bottom() + PROFILE_BUTTON_GAP,
                    hardStop.width(), PROFILE_BUTTON_HEIGHT);
        }
        return new UiLayoutRect(hardStop.right() + PROFILE_BUTTON_GAP, hardStop.y(),
                hardStop.width(), PROFILE_BUTTON_HEIGHT);
    }

    private UiLayoutRect profilesFallbackRect(UiLayoutRect viewport) {
        UiLayoutRect behavior = profilesBehaviorRect(viewport);
        return new UiLayoutRect(profilesBodyX(viewport), behavior.bottom() + PROFILE_SECTION_GAP,
                profilesBodyWidth(viewport), profilesFallbackHeight(viewport));
    }

    private int profilesFallbackSelectorWidth(UiLayoutRect viewport) {
        return Math.min(PROFILE_BUTTON_MAX_WIDTH, Math.max(60, profilesBodyWidth(viewport) - 16));
    }

    private int profilesFallbackDescriptionWidth(UiLayoutRect viewport) {
        if (profilesNarrow(viewport)) {
            return profilesBodyWidth(viewport) - 16;
        }
        return Math.max(40,
                profilesBodyWidth(viewport) - profilesFallbackSelectorWidth(viewport) - 24);
    }

    private String profilesFallbackDescriptionKey() {
        Map<String, String> fields = profilesFields();
        String fallback = configuredFallback(fields);
        if (!fallback.isBlank() && !configuredFallbackAvailable(fields)) {
            return "screen.player2npc.ui.profiles.fallback_unavailable";
        }
        if (!Boolean.parseBoolean(fields.getOrDefault("namedProfileSwitchSupported", "true"))) {
            return "screen.player2npc.ui.profiles.unsupported_proxy";
        }
        return "screen.player2npc.ui.profiles.fallback.description";
    }

    private int profilesFallbackDescriptionHeight(UiLayoutRect viewport) {
        return profileWrappedHeight(profilesFallbackDescriptionKey(),
                profilesFallbackDescriptionWidth(viewport), 12);
    }

    private int profilesFallbackHeight(UiLayoutRect viewport) {
        int descriptionBottom = 22 + profilesFallbackDescriptionHeight(viewport) + 8;
        if (profilesNarrow(viewport)) {
            return Math.max(PROFILE_NARROW_FALLBACK_HEIGHT,
                    descriptionBottom + 7 + PROFILE_BUTTON_HEIGHT + 7);
        }
        return Math.max(PROFILE_FALLBACK_HEIGHT, descriptionBottom);
    }

    private UiLayoutRect profilesFallbackSelectorRect(UiLayoutRect viewport) {
        UiLayoutRect fallback = profilesFallbackRect(viewport);
        int width = profilesFallbackSelectorWidth(viewport);
        int x = profilesNarrow(viewport)
                ? fallback.x() + Math.max(8, (fallback.width() - width) / 2)
                : fallback.right() - width - 8;
        return new UiLayoutRect(x, fallback.bottom() - PROFILE_BUTTON_HEIGHT - 7,
                width, PROFILE_BUTTON_HEIGHT);
    }

    private UiLayoutRect profilesRoutingRect(UiLayoutRect viewport) {
        int y = profilesFeatureEligible()
                ? profilesFallbackRect(viewport).bottom() + PROFILE_SECTION_GAP
                : profilesStatusRect(viewport).bottom() + PROFILE_SECTION_GAP;
        return new UiLayoutRect(profilesBodyX(viewport), y,
                profilesBodyWidth(viewport), profilesRoutingHeight(viewport));
    }

    private int profilesRoutingHeight(UiLayoutRect viewport) {
        int bodyHeight = profileWrappedHeight("screen.player2npc.ui.profiles.routing.body",
                profilesBodyWidth(viewport) - 16, 16);
        return Math.max(PROFILE_INFO_HEIGHT, 20 + bodyHeight + 8);
    }

    private int profilesFooterY(UiLayoutRect viewport) {
        return profilesRoutingRect(viewport).bottom() + PROFILE_SECTION_GAP;
    }

    private int profilesContentHeight(UiLayoutRect viewport) {
        return profilesFooterY(viewport) + PROFILE_FOOTER_HEIGHT - profilesBodyY(viewport) + 2;
    }

    private int profilesScrollMax(UiLayoutRect viewport) {
        return Math.max(0, profilesContentHeight(viewport) - viewport.height());
    }

    private UiLayoutRect profilesScrollbarTrack(UiLayoutRect viewport) {
        return new UiLayoutRect(viewport.right() - PROFILES_SCROLLBAR_WIDTH, viewport.y(),
                PROFILES_SCROLLBAR_WIDTH, viewport.height());
    }

    private UiLayoutRect profilesScrollbarThumb(UiLayoutRect viewport) {
        UiLayoutRect track = profilesScrollbarTrack(viewport);
        int contentHeight = profilesContentHeight(viewport);
        if (contentHeight <= viewport.height() || track.height() <= 0) {
            return new UiLayoutRect(track.x(), track.y(), track.width(), 0);
        }
        int thumbHeight = Math.max(12, track.height() * viewport.height() / contentHeight);
        int maxScroll = Math.max(1, contentHeight - viewport.height());
        int thumbTravel = Math.max(1, track.height() - thumbHeight);
        int clampedScroll = clampInt(profilesScroll, 0, maxScroll);
        int thumbY = track.y() + (int) ((long) thumbTravel * clampedScroll / maxScroll);
        return new UiLayoutRect(track.x(), thumbY, track.width(), thumbHeight);
    }

    private String profilesStatusTitleKey() {
        return switch (profileDiscoveryState) {
            case IDLE, LOADING -> "screen.player2npc.ui.profiles.loading.title";
            case AUTH_REQUIRED -> "screen.player2npc.ui.profiles.auth_required.title";
            case FAILED -> "screen.player2npc.ui.profiles.load_failed.title";
            case READY -> profileIsPatron()
                    ? "screen.player2npc.ui.profiles.second_profile_required.title"
                    : "screen.player2npc.ui.profiles.patron_required.title";
        };
    }

    private String profilesStatusBodyKey() {
        return switch (profileDiscoveryState) {
            case IDLE, LOADING -> "screen.player2npc.ui.profiles.loading.body";
            case AUTH_REQUIRED -> "screen.player2npc.ui.profiles.auth_required.body";
            case FAILED -> "screen.player2npc.ui.profiles.load_failed.body";
            case READY -> profileIsPatron()
                    ? "screen.player2npc.ui.profiles.second_profile_required.body"
                    : "screen.player2npc.ui.profiles.patron_required.body";
        };
    }


    private void addBudgetNumericControl(UiLayoutRect table, int row, String field, String labelKey,
                                         int current, int minimum, int maximum, boolean logarithmic, boolean active,
                                         UiLayoutRect viewport) {
        addNumericSliderInput(Player2NpcTab.BUDGET, field, field, Component.translatable(labelKey),
                budgetNumericControlRect(table, row), current, minimum, maximum,
                logarithmic, active, viewport);
    }

    private void addNumericSliderInput(Player2NpcTab tab, String field, String action,
                                       Component label, UiLayoutRect control, int current,
                                       int minimum, int maximum, boolean logarithmic,
                                       boolean active, UiLayoutRect clip) {
        int safeCurrent = clampInt(current, minimum, maximum);
        String key = numericInputKey(tab, field);
        UiLayoutRect sliderRect = numericSliderRect(control);
        Player2NpcSliderWidget slider = new Player2NpcSliderWidget(
                sliderRect.x(), sliderRect.y(), sliderRect.width(), sliderRect.height(),
                minimum, maximum, 1, safeCurrent, logarithmic, label,
                value -> Component.literal(String.valueOf(value)),
                value -> applyNumericSetting(tab, field, action, value, minimum, maximum));
        slider.onValueChanged(value -> {
            Player2NpcNumberInputWidget input = numericInputs.get(key);
            if (input != null) {
                input.setPreviewValue(value);
            }
        });
        slider.withClip(clip);
        slider.active = active;
        numericSliders.put(key, slider);
        addRenderableWidget(slider);

        UiLayoutRect inputRect = numericInputRect(control);
        Player2NpcNumberInputWidget input = new Player2NpcNumberInputWidget(font,
                inputRect.x(), inputRect.y(), inputRect.width(), inputRect.height(),
                minimum, maximum, safeCurrent, label,
                draft -> recordNumericDraft(key, draft, minimum, maximum),
                value -> applyNumericSetting(tab, field, action, value, minimum, maximum))
                .withClip(clip);
        input.active = active;
        input.setEditable(active);
        if (numericInputDrafts.containsKey(key)) {
            input.setDraftValue(numericInputDrafts.get(key));
        }
        numericInputs.put(key, input);
        addRenderableWidget(input);
    }

    private void applyNumericSetting(Player2NpcTab tab, String field, String action,
                                     int value, int minimum, int maximum) {
        int safeValue = clampInt(value, minimum, maximum);
        String key = numericInputKey(tab, field);
        Player2NpcNumberInputWidget input = numericInputs.get(key);
        if (input != null) {
            input.setCommittedValue(safeValue);
        }
        Player2NpcSliderWidget slider = numericSliders.get(key);
        if (slider != null) {
            slider.setCurrentValue(safeValue);
        }
        numericInputDrafts.remove(key);

        Map<String, String> current = snapshots.get(tab);
        if (current != null && !current.isEmpty()) {
            LinkedHashMap<String, String> updated = new LinkedHashMap<>(current);
            updated.put(field, String.valueOf(safeValue));
            snapshots.put(tab, Map.copyOf(updated));
        }
        sendUpdate(tab, action, safeValue);
    }

    private void recordNumericDraft(String key, String draft, int minimum, int maximum) {
        numericInputDrafts.put(key, draft);
        if (draft == null || draft.isEmpty()) {
            return;
        }
        try {
            Player2NpcSliderWidget slider = numericSliders.get(key);
            if (slider != null) {
                slider.setCurrentValue(clampInt(Integer.parseInt(draft), minimum, maximum));
            }
        } catch (NumberFormatException ignored) {
            // The input widget already limits length; keep the last valid slider preview.
        }
    }

    private String numericInputKey(Player2NpcTab tab, String field) {
        return tab.name() + ':' + field;
    }

    private UiLayoutRect numericSliderRect(UiLayoutRect control) {
        int inputWidth = numericInputWidth(control);
        return new UiLayoutRect(control.x(), control.y(),
                Math.max(1, control.width() - inputWidth - NUMERIC_CONTROL_GAP), control.height());
    }

    private UiLayoutRect numericInputRect(UiLayoutRect control) {
        int inputWidth = numericInputWidth(control);
        return new UiLayoutRect(control.right() - inputWidth, control.y(), inputWidth, control.height());
    }

    private int numericInputWidth(UiLayoutRect control) {
        return Math.min(NUMERIC_INPUT_WIDTH, Math.max(42, control.width() / 3));
    }

    private void addActionButton(int x, int y, int width, Component label, boolean active, Runnable onPress) {
        addActionButton(x, y, width, 18, label, active, onPress);
    }

    private void addActionButton(int x, int y, int width, int height, Component label, boolean active, Runnable onPress) {
        addActionButton(x, y, width, height, label, active, onPress, null);
    }

    private void addActionButton(int x, int y, int width, int height, Component label, boolean active, Runnable onPress, UiLayoutRect clip) {
        UiLayoutRect c = content().inset(8);
        int safeX = Math.max(c.x(), Math.min(x, c.right() - width));
        int safeY = clip == null ? Math.max(c.y(), Math.min(y, c.bottom() - height)) : y;
        int safeWidth = Math.min(width, Math.max(20, c.right() - safeX));
        Player2NpcButtonWidget widget = new Player2NpcButtonWidget(safeX, safeY, safeWidth, height, label, false, onPress);
        if (clip != null) {
            widget.withClip(clip);
        }
        widget.active = active;
        addRenderableWidget(widget);
    }

    private void addCompanionButtons() {
        UiLayoutRect content = content().inset(12);
        if (selectedCharacter == null) {
            UiLayoutRect list = companionListPane(content);
            int maxScroll = companionScrollMax(list);
            companionScroll = Math.min(companionScroll, maxScroll);
            int columns = companionColumns(list);
            int cardWidth = companionCardWidth(list, columns);
            Map<String, CompanionVitals> activeVitals = activeCompanionVitals();
            for (int i = 0; i < characters.length; i++) {
                Character character = characters[i];
                int column = i % columns;
                int row = i / columns;
                int bx = list.x() + column * (cardWidth + COMPANION_CARD_GAP);
                int by = list.y() + row * (COMPANION_CARD_HEIGHT + COMPANION_CARD_GAP) - companionScroll;
                if (by + COMPANION_CARD_HEIGHT < list.y() || by > list.bottom()) {
                    continue;
                }
                boolean active = activeVitals.containsKey(character.name());
                addRenderableWidget(new Player2NpcCharacterCardWidget(bx, by, cardWidth, COMPANION_CARD_HEIGHT,
                        character, active, activeVitals.get(character.name())).withClip(list));
                addCompanionCardButtons(list, bx, by, cardWidth, character, active);
            }
        } else {
            int buttonY = content.bottom() - 28;
            addRenderableWidget(new Player2NpcButtonWidget(content.x(), buttonY, 80, 20,
                    Component.translatable("screen.player2npc.ui.action.summon"), true, () -> {
                sendCompanionSpawn(selectedCharacter, true);
            }));
            addRenderableWidget(new Player2NpcButtonWidget(content.x() + 88, buttonY, 80, 20,
                    Component.translatable("screen.player2npc.ui.action.despawn"), false, () -> {
                sendCompanionDespawn(selectedCharacter, true);
            }));
            addRenderableWidget(new Player2NpcButtonWidget(content.x() + 176, buttonY, 70, 20,
                    Component.translatable("screen.player2npc.ui.action.back"), false, () -> {
                selectedCharacter = null;
                companionDetailScroll = 0;
                clearAdditionalPromptFeedback();
                rebuildHubWidgets();
            }));
            addAdditionalPromptControls(content);
        }
    }

    private void addAdditionalPromptControls(UiLayoutRect content) {
        if (selectedCharacter == null || selectedCharacter.id() == null || selectedCharacter.id().isBlank()) {
            return;
        }
        String characterId = selectedCharacter.id();
        String initial = additionalPromptDrafts.getOrDefault(characterId, additionalPromptForCharacter(selectedCharacter));
        int inputWidth = Math.max(80, companionTaskChainBoxWidth(content) - 16);
        additionalPromptInputCharacterId = characterId;
        additionalPromptInput = new Player2NpcPromptBoxWidget(font, content.x(), content.y(), inputWidth, ADDITIONAL_PROMPT_INPUT_HEIGHT,
                Component.translatable("screen.player2npc.ui.companion_detail.additional_prompt.placeholder"),
                Component.translatable("screen.player2npc.ui.companion_detail.additional_prompt"));
        additionalPromptInput.setCharacterLimit(ADDITIONAL_PROMPT_MAX_CHARS);
        updatingAdditionalPromptInput = true;
        additionalPromptInput.setValue(clampAdditionalPromptDraft(initial));
        updatingAdditionalPromptInput = false;
        additionalPromptInput.setValueListener(value -> {
            if (!updatingAdditionalPromptInput) {
                additionalPromptDrafts.put(characterId, clampAdditionalPromptDraft(value));
            }
        });
        addRenderableWidget(additionalPromptInput);

        additionalPromptSaveButton = new Player2NpcButtonWidget(content.x(), content.y(),
                ADDITIONAL_PROMPT_SAVE_WIDTH, ADDITIONAL_PROMPT_SAVE_HEIGHT,
                Component.translatable("screen.player2npc.ui.action.save"), true, this::saveAdditionalPrompt);
        addRenderableWidget(additionalPromptSaveButton);
    }

    private void addCompanionCardButtons(UiLayoutRect clip, int bx, int by, int cardWidth, Character character, boolean active) {
        int buttonY = by + 6;
        int buttonX = bx + cardWidth - COMPANION_CARD_BUTTON_SIZE - 6;
        addCompanionCardIconButton(clip, buttonX, buttonY,
                Component.translatable("screen.player2npc.ui.companion_detail.title"), true, ICON_INFO, () -> {
                    selectedCharacter = character;
                    companionScroll = 0;
                    companionDetailScroll = 0;
                    clearAdditionalPromptFeedback();
                    rebuildHubWidgets();
                });

        buttonX -= COMPANION_CARD_BUTTON_SIZE + COMPANION_CARD_BUTTON_GAP;
        addCompanionCardIconButton(clip, buttonX, buttonY,
                Component.translatable(active
                        ? "screen.player2npc.ui.action.disconnect_companion"
                        : "screen.player2npc.ui.action.connect_companion"),
                true, active ? ICON_PLUG_RED : ICON_PLUG_GREEN, () -> {
                    if (active) {
                        sendCompanionDespawn(character, false);
                    } else {
                        sendCompanionSpawn(character, false);
                    }
                });

        buttonX -= COMPANION_CARD_BUTTON_SIZE + COMPANION_CARD_BUTTON_GAP;
        addCompanionCardIconButton(clip, buttonX, buttonY,
                Component.translatable("screen.player2npc.ui.action.whistle"),
                active, active ? ICON_WHISTLE : ICON_WHISTLE_DISABLED, () -> sendCompanionSpawn(character, false));
    }

    private void addCompanionCardIconButton(UiLayoutRect clip, int x, int y, Component label, boolean active,
                                            ResourceLocation icon, Runnable onPress) {
        Player2NpcButtonWidget button = new Player2NpcButtonWidget(
                x, y, COMPANION_CARD_BUTTON_SIZE, COMPANION_CARD_BUTTON_SIZE,
                label, false, icon, onPress).withClip(clip);
        button.active = active;
        addRenderableWidget(button);
    }

    private void refreshProfilesAndModels() {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        int requestId = ++profileDiscoveryRequestId;
        profilePatronTier = "";
        discoveredProfileNames = List.of();
        if (player == null) {
            profileDiscoveryState = ProfileDiscoveryState.FAILED;
            return;
        }
        profileDiscoveryState = ProfileDiscoveryState.LOADING;
        CompletableFuture.supplyAsync(() -> loadProfileDiscovery(player))
                .whenCompleteAsync((discovery, error) -> {
                    if (requestId != profileDiscoveryRequestId) {
                        return;
                    }
                    if (error != null || discovery == null) {
                        profileDiscoveryState = ProfileDiscoveryState.FAILED;
                        profilePatronTier = "";
                        discoveredProfileNames = List.of();
                    } else if (!discovery.connected()) {
                        profileDiscoveryState = ProfileDiscoveryState.AUTH_REQUIRED;
                        profilePatronTier = "";
                        discoveredProfileNames = List.of();
                    } else {
                        profileDiscoveryState = ProfileDiscoveryState.READY;
                        profilePatronTier = discovery.patronTier();
                        discoveredProfileNames = discovery.profileNames();
                    }
                    if (selectedTab == Player2NpcTab.PROFILES_MODELS && client.screen == this) {
                        profilesScroll = Math.min(profilesScroll,
                                profilesScrollMax(profilesViewport(content().inset(14))));
                        rebuildHubWidgets();
                    }
                }, client);
    }

    private ProfileDiscovery loadProfileDiscovery(Player player) {
        try {
            if (!AuthenticationManager.getInstance().hasApiConnection(player, PLAYER2_GAME_ID)) {
                return new ProfileDiscovery(false, "", List.of());
            }
            Map<String, JsonElement> joules = Player2HTTPUtils.sendRequest(
                    player, PLAYER2_GAME_ID, "/v1/joules", "GET", null);
            String patronTier = safeDynamicLabel(jsonString(joules.get("patron_tier")), 64);
            if (patronTier.isBlank()) {
                return new ProfileDiscovery(true, "", List.of());
            }

            JsonElement response = Player2HTTPUtils.sendRequestElement(
                    player, PLAYER2_GAME_ID, "/v1/ai_profiles", "GET", null);
            if (response == null || !response.isJsonArray()) {
                throw new IllegalStateException("Profile response was not an array");
            }
            LinkedHashSet<String> names = new LinkedHashSet<>();
            for (JsonElement entry : response.getAsJsonArray()) {
                if (names.size() >= PROFILES_MAX_ENTRIES) {
                    break;
                }
                if (!entry.isJsonObject()) {
                    continue;
                }
                JsonElement rawName = entry.getAsJsonObject().get("name");
                String baseUrl = jsonString(entry.getAsJsonObject().get("base_url"));
                if (baseUrl.isBlank() || baseUrl.length() > PROFILE_BASE_URL_MAX_LENGTH) {
                    continue;
                }
                String name = safeProfileName(jsonString(rawName));
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
            return new ProfileDiscovery(true, patronTier, List.copyOf(names));
        } catch (Exception ignored) {
            throw new IllegalStateException("Player2 profile discovery failed");
        }
    }

    private String jsonString(JsonElement element) {
        return element != null && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isString() ? element.getAsString() : "";
    }

    private String safeProfileName(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > PROFILE_NAME_MAX_LENGTH) {
            return "";
        }
        int first = raw.codePointAt(0);
        int last = raw.codePointBefore(raw.length());
        if (isProfileWhitespace(first) || isProfileWhitespace(last)) {
            return "";
        }
        for (int offset = 0; offset < raw.length(); ) {
            int codePoint = raw.codePointAt(offset);
            if (java.lang.Character.isISOControl(codePoint)
                    || java.lang.Character.getType(codePoint) == java.lang.Character.FORMAT) {
                return "";
            }
            offset += java.lang.Character.charCount(codePoint);
        }
        return raw;
    }

    private boolean isProfileWhitespace(int codePoint) {
        return java.lang.Character.isWhitespace(codePoint)
                || java.lang.Character.isSpaceChar(codePoint);
    }

    private String safeDynamicLabel(String raw, int maxLength) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        StringBuilder clean = new StringBuilder(Math.min(raw.length(), maxLength));
        for (int i = 0; i < raw.length() && clean.length() < maxLength; i++) {
            char c = raw.charAt(i);
            if (!java.lang.Character.isISOControl(c)) {
                clean.append(c);
            }
        }
        return clean.toString().trim();
    }

    private void fetchCharacters() {
        loadingCharacters = true;
        characterStateKey = "screen.player2npc.ui.companions.loading";
        CompletableFuture.supplyAsync(() -> CharacterUtils.requestCharacters(Minecraft.getInstance().player, PLAYER2_GAME_ID))
                .whenCompleteAsync((result, error) -> {
                    if (error != null) {
                        characters = new Character[0];
                        characterStateKey = "screen.player2npc.ui.companions.load_failed";
                    } else {
                        characters = result == null ? new Character[0] : result;
                        characterStateKey = characters.length == 0
                                ? "screen.player2npc.ui.companions.empty"
                                : "screen.player2npc.ui.companions.ready";
                    }
                    loadingCharacters = false;
                    if (minecraft != null) {
                        minecraft.execute(this::rebuildHubWidgets);
                    }
                }, Minecraft.getInstance());
    }

    private void requestSnapshot(Player2NpcTab tab) {
        if (minecraft != null && minecraft.level != null) {
            NetworkManager.sendToServer(Player2NPC.GUI_SNAPSHOT_REQUEST_PACKET_ID, GuiSnapshotRequestPacket.create(minecraft.level.registryAccess(), tab));
        }
    }

    private void requestHeaderSnapshot() {
        if (selectedTab != Player2NpcTab.BUDGET) {
            requestSnapshot(Player2NpcTab.BUDGET);
        }
    }

    private void sendUpdate(Player2NpcTab tab, String action, int value) {
        if (minecraft != null && minecraft.level != null) {
            NetworkManager.sendToServer(Player2NPC.GUI_UPDATE_PACKET_ID, GuiUpdatePacket.create(minecraft.level.registryAccess(), tab, action, value));
        }
    }

    private void sendUpdateText(Player2NpcTab tab, String action, String text) {
        if (minecraft != null && minecraft.level != null) {
            NetworkManager.sendToServer(Player2NPC.GUI_UPDATE_PACKET_ID, GuiUpdatePacket.createText(minecraft.level.registryAccess(), tab, action, text));
        }
    }

    private void sendSettingsUpdate(String action, String value) {
        GuiSettingsCatalog.all().stream()
                .filter(definition -> definition.action().equals(action))
                .findFirst()
                .ifPresent(definition -> {
                    Player2NpcTab tab = Player2NpcTab.fromWireName(definition.tabWireName());
                    if (tab != Player2NpcTab.UNKNOWN) {
                        sendUpdateText(tab, action, value);
                    }
                });
    }

    private void saveAdditionalPrompt() {
        if (selectedCharacter == null || selectedCharacter.id() == null || selectedCharacter.id().isBlank()) {
            return;
        }
        String characterId = selectedCharacter.id();
        String prompt = clampAdditionalPromptDraft(additionalPromptInput == null
                ? additionalPromptDrafts.getOrDefault(characterId, "")
                : additionalPromptInput.getValue());
        additionalPromptDrafts.put(characterId, prompt);
        clearAdditionalPromptFeedback();
        sendUpdateText(Player2NpcTab.COMPANIONS, "additional_prompt", characterId + "\n" + prompt);
    }

    private void sendCompanionSpawn(Character character, boolean closeScreen) {
        if (character == null || minecraft == null || minecraft.level == null) {
            return;
        }
        NetworkManager.sendToServer(Player2NPC.SPAWN_REQUEST_PACKET_ID,
                AutomatoneSpawnRequestPacket.create(minecraft.level.registryAccess(), character));
        afterCompanionAction(closeScreen);
    }

    private void sendCompanionDespawn(Character character, boolean closeScreen) {
        if (character == null || minecraft == null || minecraft.level == null) {
            return;
        }
        NetworkManager.sendToServer(Player2NPC.DESPAWN_REQUEST_PACKET_ID,
                AutomatoneDespawnRequestPacket.create(minecraft.level.registryAccess(), character));
        afterCompanionAction(closeScreen);
    }

    private void afterCompanionAction(boolean closeScreen) {
        if (closeScreen) {
            minecraft.setScreen(null);
            return;
        }
        requestSnapshot(Player2NpcTab.COMPANIONS);
        requestHeaderSnapshot();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        UiLayoutRect frame = frame();
        UiLayoutRect content = content();
        UiRenderCompat.fillWindow(graphics, width, height, frame.x(), frame.y(), frame.width(), frame.height());
        renderHeader(graphics, frame);
        renderNav(graphics, frame);
        UiRenderCompat.fillPanel(graphics, content.x(), content.y(), content.width(), content.height(), Player2NpcUiTheme.PANEL);
        UiLayoutRect clip = content.inset(4);
        graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
        renderContent(graphics);
        graphics.disableScissor();
        super.render(graphics, mouseX, mouseY, delta);
        renderAiMemoryTooltip(graphics);
    }

    private void renderHeader(GuiGraphics graphics, UiLayoutRect frame) {
        int logoX = frame.x() + 8;
        int logoMaxWidth = Math.max(0, Math.min(Player2NpcUiTheme.NAV_WIDTH - 2,
                frame.right() - 286 - logoX - 12));
        UiRenderCompat.drawLogo(graphics, logoX, frame.y() + 2, logoMaxWidth, Player2NpcUiTheme.TOP_HEIGHT - 4);
        Map<String, String> budget = snapshots.getOrDefault(Player2NpcTab.BUDGET, Map.of());
        String joules = budget.getOrDefault("joules", "");
        Component joulesText = joules.isBlank()
                ? Component.translatable("screen.player2npc.ui.joules.unfetched")
                : Component.translatable("screen.player2npc.ui.joules.value", joules);
        graphics.drawString(font, joulesText, frame.right() - 286, frame.y() + 11,
                Player2NpcUiTheme.ACCENT, false);
    }

    private void renderNav(GuiGraphics graphics, UiLayoutRect frame) {
        UiLayoutRect nav = navPane(frame);
        UiRenderCompat.fillPanel(graphics, nav.x(), nav.y(), nav.width(), nav.height(), 0x9916202A);
        UiLayoutRect navViewport = navViewport(nav);
        UiLayoutRect track = navScrollbarTrack(navViewport);
        UiRenderCompat.drawScrollbar(graphics, track.x(), track.y(), track.height(),
                navListContentHeight(Player2NpcTab.visibleTabs().length), navViewport.height(), navScroll);
    }

    private void renderContent(GuiGraphics graphics) {
        if (!globalSnapshotState.isEmpty()) {
            renderGlobalSnapshotState(graphics);
            return;
        }
        if (isSettingsView() && settingsPanel != null) {
            settingsPanel.updateContext(selectedTab.wireName(), content().inset(14));
            settingsPanel.render(graphics, lastMouseX, lastMouseY, 0.0F);
            return;
        }
        switch (selectedTab) {
            case GETTING_STARTED -> renderGettingStarted(graphics);
            case COMPANIONS -> renderCompanions(graphics);
            case BEHAVIOR -> renderBehavior(graphics);
            case BUDGET -> renderBudget(graphics);
            case ACCESS_LISTS -> renderAccessLists(graphics);
            case MOD_INTELLIGENCE -> renderModIntelligence(graphics);
            case AI_MEMORY -> renderAiMemory(graphics);
            case PROFILES_MODELS -> renderProfiles(graphics);
            case AUTOMATION_ITEMS -> renderUnavailable(graphics, "screen.player2npc.ui.state.unsupported_tab");
            case UNKNOWN -> renderUnavailable(graphics, "screen.player2npc.ui.state.unsupported_tab");
        }
    }

    private void renderGlobalSnapshotState(GuiGraphics graphics) {
        UiLayoutRect c = content().inset(14);
        UiRenderCompat.drawLogo(graphics, c.x(), c.y(), Player2NpcUiTheme.NAV_WIDTH - 2, 20);
        drawSnapshotState(graphics, c, globalSnapshotState);
    }

    private void renderGettingStarted(GuiGraphics graphics) {
        UiLayoutRect c = content().inset(14);
        drawCenteredText(graphics, Component.translatable("screen.player2npc.ui.getting_started.title"), c.x(), c.right(), c.y(), Player2NpcUiTheme.TEXT);
        drawCenteredText(graphics, Component.translatable("screen.player2npc.ui.getting_started.body"), c.x(), c.right(), c.y() + 14, Player2NpcUiTheme.TEXT_MUTED);

        UiLayoutRect viewport = gettingStartedViewport(c);
        gettingStartedScroll = Math.min(gettingStartedScroll, gettingStartedScrollMax(viewport));
        UiLayoutRect cards = gettingStartedCardArea(c);
        Component talkBody = gettingStartedTalkBody();
        Component[] botTroubleshooting = gettingStartedTroubleshootingLines();
        Component[] joulesHelp = gettingStartedJoulesLines();
        int cardY = cards.y();
        int talkHeight = gettingStartedCardHeight(cards.width(), talkBody);
        int troubleshootingHeight = gettingStartedCardHeight(cards.width(), botTroubleshootingActions(), botTroubleshooting);
        int joulesHeight = gettingStartedCardHeight(cards.width(), joulesActions(), joulesHelp);

        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        drawGettingStartedCardIfVisible(graphics, viewport, new UiLayoutRect(cards.x(), cardY, cards.width(), talkHeight),
                "screen.player2npc.ui.getting_started.talk_title", new GettingStartedAction[0], talkBody);
        cardY += talkHeight + GETTING_STARTED_CARD_GAP;
        drawGettingStartedCardIfVisible(graphics, viewport, new UiLayoutRect(cards.x(), cardY, cards.width(), troubleshootingHeight),
                "screen.player2npc.ui.getting_started.troubleshooting_title", botTroubleshootingActions(), botTroubleshooting);
        cardY += troubleshootingHeight + GETTING_STARTED_CARD_GAP;
        drawGettingStartedCardIfVisible(graphics, viewport, new UiLayoutRect(cards.x(), cardY, cards.width(), joulesHeight),
                "screen.player2npc.ui.getting_started.joules_title", joulesActions(), joulesHelp);
        graphics.disableScissor();

        UiLayoutRect track = gettingStartedScrollbarTrack(viewport);
        UiRenderCompat.drawScrollbar(graphics, track.x(), track.y(), track.height(),
                gettingStartedContentHeight(cards.width()), viewport.height(), gettingStartedScroll);
    }

    private void renderCompanions(GuiGraphics graphics) {
        UiLayoutRect c = content().inset(12);
        if (selectedCharacter == null) {
            drawTitle(graphics, c.x(), c.y(), "screen.player2npc.ui.companions.title");
            Map<String, String> snap = snapshots.getOrDefault(Player2NpcTab.COMPANIONS, Map.of());
            Component counts = Component.translatable("screen.player2npc.ui.companions.counts",
                    snap.getOrDefault("storedCount", "0"), snap.getOrDefault("liveCount", "0"));
            graphics.drawString(font, counts, c.x(), c.y() + 14, Player2NpcUiTheme.TEXT_MUTED, false);
            if (loadingCharacters || characters.length == 0) {
                graphics.drawString(font, Component.translatable(characterStateKey), c.x(), c.y() + 42, Player2NpcUiTheme.TEXT_MUTED, false);
            }
            UiLayoutRect list = companionListPane(c);
            int contentHeight = companionListContentHeight(list);
            companionScroll = Math.min(companionScroll, companionScrollMax(list));
            UiLayoutRect track = companionScrollbarTrack(list);
            UiRenderCompat.drawScrollbar(graphics, track.x(), track.y(), track.height(), contentHeight, list.height(), companionScroll);
        } else {
            renderCompanionDetail(graphics, c);
        }
    }

    private void renderCompanionDetail(GuiGraphics graphics, UiLayoutRect c) {
        recordCompanionTaskStatus(selectedCharacter.name(), companionTaskStatus(selectedCharacter));
        drawTitle(graphics, c.x(), c.y(), "screen.player2npc.ui.companion_detail.title");
        UiLayoutRect viewport = companionDetailViewport(c);
        companionDetailScroll = Math.min(companionDetailScroll, companionDetailScrollMax(c, viewport));
        int contentY = viewport.y() - companionDetailScroll;
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        ResourceLocation skinId = SkinManager.getSkinIdentifier(selectedCharacter.skinURL());
        SkinManager.renderSkinHead(graphics, c.x(), contentY + 8, 58, skinId);
        int textX = c.x() + 70;
        graphics.drawString(font, Component.nullToEmpty(clamp(selectedCharacter.name(), 40)), textX, contentY + 10, Player2NpcUiTheme.TEXT, false);
        graphics.drawString(font, Component.nullToEmpty(clamp(selectedCharacter.shortName(), 32)), textX, contentY + 23, Player2NpcUiTheme.TEXT_MUTED, false);
        boolean active = isCompanionActive(selectedCharacter);
        graphics.drawString(font, companionStatus(active), textX, contentY + 38,
                active ? Player2NpcUiTheme.GOOD : Player2NpcUiTheme.BAD, false);
        if (active) {
            Player2NpcCharacterCardWidget.renderVitals(graphics, textX + 83, contentY + 70, companionVitals(selectedCharacter));
        }
        int textY = contentY + COMPANION_DETAIL_DESCRIPTION_Y;
        String description = selectedCharacter.description() == null ? "" : selectedCharacter.description();
        for (FormattedText line : font.getSplitter().splitLines(description, companionDetailTextWidth(c), Style.EMPTY)) {
            graphics.drawString(font, line.getString(), textX, textY, Player2NpcUiTheme.TEXT_MUTED, false);
            textY += 10;
            if (textY > viewport.bottom() + 10) {
                break;
            }
        }
        renderCompanionTaskChainBox(graphics, c, viewport, contentY + companionTaskChainBoxOffset(c));
        renderCompanionInventoryBox(graphics, c, viewport, contentY + companionInventoryBoxOffset(c));
        renderCompanionMemoryBox(graphics, c, viewport, contentY + companionMemoryBoxOffset(c));
        renderCompanionAdditionalPromptBox(graphics, c, viewport, contentY + companionAdditionalPromptBoxOffset(c));
        graphics.disableScissor();
        UiLayoutRect track = companionDetailScrollbarTrack(viewport);
        UiRenderCompat.drawScrollbar(graphics, track.x(), track.y(), track.height(),
                companionDetailContentHeight(c), viewport.height(), companionDetailScroll);
    }

    private void renderCompanionTaskChainBox(GuiGraphics graphics, UiLayoutRect content, UiLayoutRect viewport, int y) {
        int height = companionTaskChainBoxHeight(content);
        if (y + height <= viewport.y() || y >= viewport.bottom()) {
            return;
        }
        int x = content.x();
        int width = companionTaskChainBoxWidth(content);
        UiRenderCompat.fillPanel(graphics, x, y, width, height, 0xAA22313D);
        graphics.drawString(font, Component.translatable("screen.player2npc.ui.companion_detail.task_chain").withStyle(ChatFormatting.BOLD),
                x + 8, y + 8, Player2NpcUiTheme.TEXT, false);
        int textY = y + 24;
        int textWidth = Math.max(24, width - 16);
        int bottom = y + height - 8;
        for (Component entry : companionTaskChainDisplayLines(selectedCharacter)) {
            for (FormattedCharSequence line : font.split(entry, textWidth)) {
                if (textY + 9 > bottom) {
                    return;
                }
                graphics.drawString(font, line, x + 8, textY, Player2NpcUiTheme.TEXT_MUTED, false);
                textY += 10;
            }
        }
    }

    private void renderCompanionInventoryBox(GuiGraphics graphics, UiLayoutRect content, UiLayoutRect viewport, int y) {
        int height = companionInventoryBoxHeight(content);
        if (y + height <= viewport.y() || y >= viewport.bottom()) {
            return;
        }
        int x = content.x();
        int width = companionTaskChainBoxWidth(content);
        UiRenderCompat.fillPanel(graphics, x, y, width, height, 0xAA22313D);
        graphics.drawString(font, Component.translatable("screen.player2npc.ui.companion_detail.inventory").withStyle(ChatFormatting.BOLD),
                x + 8, y + 8, Player2NpcUiTheme.TEXT, false);

        int activeIndex = activeCompanionIndex(selectedCharacter);
        int selectedSlot = activeIndex < 0 ? 0 : clampInt(intValue(companionSnapshot().get("activeSelectedSlot." + activeIndex)), 0, 8);
        int slot = companionInventorySlotSize(content);
        int innerX = x + 8;
        int innerY = y + 24;
        int bottom = y + height - 8;
        int labelWidth = companionInventoryEquipmentLabelWidth(content, slot);
        int equipmentX = innerX;
        int equipmentLabelX = equipmentX + slot + 4;
        int gridX = equipmentX + slot + labelWidth + 12;
        int gridWidth = 9 * slot + 8 * COMPANION_INVENTORY_SLOT_GAP;
        if (gridX + gridWidth > x + width - 8) {
            labelWidth = 0;
            gridX = equipmentX + slot + 8;
        }
        int gridY = innerY;

        drawEquipmentInventorySlot(graphics, activeIndex, "head", "screen.player2npc.ui.companion_detail.inventory.head",
                equipmentX, innerY, slot, labelWidth, equipmentLabelX);
        drawEquipmentInventorySlot(graphics, activeIndex, "chest", "screen.player2npc.ui.companion_detail.inventory.chest",
                equipmentX, innerY + (slot + COMPANION_INVENTORY_EQUIPMENT_GAP), slot, labelWidth, equipmentLabelX);
        drawEquipmentInventorySlot(graphics, activeIndex, "legs", "screen.player2npc.ui.companion_detail.inventory.legs",
                equipmentX, innerY + (slot + COMPANION_INVENTORY_EQUIPMENT_GAP) * 2, slot, labelWidth, equipmentLabelX);
        drawEquipmentInventorySlot(graphics, activeIndex, "feet", "screen.player2npc.ui.companion_detail.inventory.feet",
                equipmentX, innerY + (slot + COMPANION_INVENTORY_EQUIPMENT_GAP) * 3, slot, labelWidth, equipmentLabelX);
        int shieldY = innerY + (slot + COMPANION_INVENTORY_EQUIPMENT_GAP) * 4 + 4;
        drawLabeledInventorySlot(graphics, equipmentX, shieldY, slot,
                activeIndex < 0 ? ItemStack.EMPTY : companionInventoryItem("activeInventory.offhand." + activeIndex),
                false, labelWidth == 0 ? "screen.player2npc.ui.companion_detail.inventory.shield" : null);
        if (labelWidth > 0) {
            drawClampedText(graphics, Component.translatable("screen.player2npc.ui.companion_detail.inventory.shield"),
                    equipmentLabelX, shieldY + (slot - 8) / 2, labelWidth, Player2NpcUiTheme.TEXT_DIM);
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int mainSlot = 9 + row * 9 + col;
                drawLabeledInventorySlot(graphics,
                        gridX + col * (slot + COMPANION_INVENTORY_SLOT_GAP),
                        gridY + row * (slot + COMPANION_INVENTORY_SLOT_GAP),
                        slot,
                        activeIndex < 0 ? ItemStack.EMPTY : companionInventoryItem("activeInventory.main." + activeIndex + "." + mainSlot),
                        false,
                        null);
            }
        }

        int hotbarY = gridY + 3 * slot + 2 * COMPANION_INVENTORY_SLOT_GAP + COMPANION_INVENTORY_HOTBAR_GAP;
        if (hotbarY + slot <= bottom) {
            for (int col = 0; col < 9; col++) {
                boolean held = col == selectedSlot;
                drawLabeledInventorySlot(graphics,
                        gridX + col * (slot + COMPANION_INVENTORY_SLOT_GAP),
                        hotbarY,
                        slot,
                        activeIndex < 0 ? ItemStack.EMPTY : companionInventoryItem("activeInventory.main." + activeIndex + "." + col),
                        held,
                        held ? "screen.player2npc.ui.companion_detail.inventory.held" : null);
            }
        }
    }

    private void renderCompanionMemoryBox(GuiGraphics graphics, UiLayoutRect content, UiLayoutRect viewport, int y) {
        int height = companionMemoryBoxHeight(content);
        if (y + height <= viewport.y() || y >= viewport.bottom()) {
            return;
        }
        int x = content.x();
        int width = companionTaskChainBoxWidth(content);
        UiRenderCompat.fillPanel(graphics, x, y, width, height, 0xAA22313D);
        graphics.drawString(font, Component.translatable("screen.player2npc.ui.companion_detail.memory").withStyle(ChatFormatting.BOLD),
                x + 8, y + 8, Player2NpcUiTheme.TEXT, false);

        int textY = y + 24;
        int textWidth = Math.max(24, width - 16);
        int bottom = y + height - 8;
        textY = drawMemoryBoxLines(graphics, Component.translatable("screen.player2npc.ui.companion_detail.memory_intro"),
                x + 8, textY, textWidth, bottom, Player2NpcUiTheme.TEXT_MUTED);
        textY += 4;
        for (Component entry : companionMemoryFactDisplayLines(selectedCharacter)) {
            textY = drawMemoryBoxLines(graphics, entry, x + 8, textY, textWidth, bottom, Player2NpcUiTheme.TEXT);
            if (textY + 9 > bottom) {
                return;
            }
        }
    }

    private void renderCompanionAdditionalPromptBox(GuiGraphics graphics, UiLayoutRect content, UiLayoutRect viewport, int y) {
        int height = companionAdditionalPromptBoxHeight(content);
        layoutAdditionalPromptControls(content, viewport, y, height);
        if (y + height <= viewport.y() || y >= viewport.bottom()) {
            return;
        }
        int x = content.x();
        int width = companionTaskChainBoxWidth(content);
        UiRenderCompat.fillPanel(graphics, x, y, width, height, 0xAA22313D);
        graphics.drawString(font, Component.translatable("screen.player2npc.ui.companion_detail.additional_prompt").withStyle(ChatFormatting.BOLD),
                x + 8, y + 8, Player2NpcUiTheme.TEXT, false);
        int count = additionalPromptInput == null ? 0 : additionalPromptInput.getValue().length();
        Component counter = Component.translatable("screen.player2npc.ui.companion_detail.additional_prompt.counter",
                count, ADDITIONAL_PROMPT_MAX_CHARS);
        graphics.drawString(font, counter, x + width - 8 - font.width(counter), y + 8,
                count >= ADDITIONAL_PROMPT_MAX_CHARS ? Player2NpcUiTheme.WARN : Player2NpcUiTheme.TEXT_DIM, false);
        drawAdditionalPromptFeedback(graphics, x, y, width, height);
    }

    private void layoutAdditionalPromptControls(UiLayoutRect content, UiLayoutRect viewport, int panelY, int panelHeight) {
        boolean visible = selectedCharacter != null
                && additionalPromptInput != null
                && additionalPromptSaveButton != null
                && panelY + panelHeight > viewport.y()
                && panelY < viewport.bottom();
        if (additionalPromptInput == null || additionalPromptSaveButton == null) {
            return;
        }
        int x = content.x();
        int width = companionTaskChainBoxWidth(content);
        int inputX = x + 8;
        int inputY = panelY + 24;
        int inputWidth = Math.max(80, width - 16);
        additionalPromptInput.setX(inputX);
        additionalPromptInput.setY(inputY);
        additionalPromptInput.setWidth(inputWidth);
        additionalPromptInput.setHeight(ADDITIONAL_PROMPT_INPUT_HEIGHT);
        additionalPromptInput.setClip(viewport);
        additionalPromptInput.visible = visible;
        additionalPromptInput.active = visible;

        int saveX = x + width - 8 - ADDITIONAL_PROMPT_SAVE_WIDTH;
        int saveY = panelY + panelHeight - 8 - ADDITIONAL_PROMPT_SAVE_HEIGHT;
        additionalPromptSaveButton.setX(saveX);
        additionalPromptSaveButton.setY(saveY);
        additionalPromptSaveButton.setWidth(ADDITIONAL_PROMPT_SAVE_WIDTH);
        additionalPromptSaveButton.setHeight(ADDITIONAL_PROMPT_SAVE_HEIGHT);
        additionalPromptSaveButton.withClip(viewport);
        additionalPromptSaveButton.visible = visible;
        additionalPromptSaveButton.active = visible;
    }

    private int drawMemoryBoxLines(GuiGraphics graphics, Component text, int x, int y, int width, int bottom, int color) {
        int textY = y;
        for (FormattedCharSequence line : font.split(text, width)) {
            if (textY + 9 > bottom) {
                return textY;
            }
            graphics.drawString(font, line, x, textY, color, false);
            textY += 10;
        }
        return textY;
    }

    private void drawEquipmentInventorySlot(GuiGraphics graphics, int activeIndex, String slotKey, String labelKey,
                                            int x, int y, int slot, int labelWidth, int labelX) {
        ItemStack stack = activeIndex < 0 ? ItemStack.EMPTY : companionInventoryItem("activeInventory.armor." + activeIndex + "." + armorInventorySlot(slotKey));
        drawLabeledInventorySlot(graphics, x, y, slot, stack, false, labelWidth == 0 ? labelKey : null);
        if (labelWidth > 0) {
            drawClampedText(graphics, Component.translatable(labelKey), labelX, y + (slot - 8) / 2, labelWidth, Player2NpcUiTheme.TEXT_DIM);
        }
    }

    private void drawLabeledInventorySlot(GuiGraphics graphics, int x, int y, int size, ItemStack stack, boolean selected, String emptyLabelKey) {
        int border = selected ? Player2NpcUiTheme.ACCENT : Player2NpcUiTheme.PANEL_BORDER;
        graphics.fill(x, y, x + size, y + size, 0xCC101923);
        graphics.fill(x, y, x + size, y + 1, border);
        graphics.fill(x, y + size - 1, x + size, y + size, border);
        graphics.fill(x, y, x + 1, y + size, border);
        graphics.fill(x + size - 1, y, x + size, y + size, border);
        if (stack != null && !stack.isEmpty()) {
            int itemX = x + Math.max(0, (size - 16) / 2);
            int itemY = y + Math.max(0, (size - 16) / 2);
            graphics.renderItem(stack, itemX, itemY);
            graphics.renderItemDecorations(font, stack, itemX, itemY);
            return;
        }
        if (emptyLabelKey != null && size >= 18) {
            Component label = Component.translatable(emptyLabelKey);
            int labelWidth = font.width(label);
            if (labelWidth <= size - 2) {
                graphics.drawString(font, label, x + (size - labelWidth) / 2, y + (size - 8) / 2, Player2NpcUiTheme.TEXT_DIM, false);
            }
        }
    }

    private void renderBehavior(GuiGraphics graphics) {
        UiLayoutRect c = content().inset(14);
        drawTitle(graphics, c.x(), c.y(), "screen.player2npc.ui.behavior.title");
        Map<String, String> f = snapshots.getOrDefault(Player2NpcTab.BEHAVIOR, Map.of());
        if (drawSnapshotState(graphics, c, f)) {
            return;
        }
        drawActionResult(graphics, c, f);
        drawTableRow(graphics, c, 0, "screen.player2npc.ui.behavior.auto_equip", boolValue(f.get("autoEquip")), true);
        drawTableRow(graphics, c, 1, "screen.player2npc.ui.behavior.auto_respawn", boolValue(f.get("autoRespawn")), true);
        drawTableRow(graphics, c, 2, "screen.player2npc.ui.behavior.bot_permadeath", boolValue(f.get("botPermadeath")), true);
        drawTableRow(graphics, c, 3, "screen.player2npc.ui.behavior.source", sourceValue(f.get("source")), false);
    }

    private void renderBudget(GuiGraphics graphics) {
        UiLayoutRect c = content().inset(14);
        drawTitle(graphics, c.x(), c.y(), "screen.player2npc.ui.budget.title");
        Map<String, String> f = snapshots.getOrDefault(Player2NpcTab.BUDGET, Map.of());
        if (drawSnapshotState(graphics, c, f)) {
            return;
        }
        drawActionResult(graphics, c, f);
        UiLayoutRect viewport = budgetViewport(c);
        budgetScroll = Math.min(budgetScroll, budgetScrollMax(viewport));
        UiLayoutRect table = budgetTable(c);
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        drawBudgetRow(graphics, table, 0, "screen.player2npc.ui.budget.source",
                Component.literal(f.getOrDefault("source", "-")), false, false);
        drawBudgetRow(graphics, table, 1, "screen.player2npc.ui.budget.soft_calls", Component.empty(), true, true);
        drawBudgetRow(graphics, table, 2, "screen.player2npc.ui.budget.hard_calls", Component.empty(), true, true);
        drawBudgetRow(graphics, table, 3, "screen.player2npc.ui.budget.window_minutes", Component.empty(), true, false);
        drawBudgetRow(graphics, table, 4, "screen.player2npc.ui.budget.window_usage",
                Component.translatable("screen.player2npc.ui.budget.window_value",
                        valueOrDash(f.get("callsThisWindow")), valueOrDash(f.get("windowRemainingSeconds"))), false, false);
        drawBudgetDivider(graphics, table, 5);
        drawBudgetRow(graphics, table, 5, "screen.player2npc.ui.budget.soft_joules", Component.empty(), true, true);
        drawBudgetRow(graphics, table, 6, "screen.player2npc.ui.budget.hard_joules", Component.empty(), true, true);
        drawBudgetRow(graphics, table, 7, "screen.player2npc.ui.budget.joules_refresh", Component.empty(), true, false);
        drawBudgetRow(graphics, table, 8, "screen.player2npc.ui.budget.joules",
                Component.literal(f.getOrDefault("joules", "")), false, false);
        drawBudgetRow(graphics, table, 9, "screen.player2npc.ui.budget.patron",
                Component.literal(f.getOrDefault("patronTier", "")), false, false);
        graphics.disableScissor();
        UiLayoutRect track = budgetScrollbarTrack(viewport);
        UiRenderCompat.drawScrollbar(graphics, track.x(), track.y(), track.height(), budgetContentHeight(), viewport.height(), budgetScroll);
    }

    private void renderAccessLists(GuiGraphics graphics) {
        UiLayoutRect c = content().inset(14);
        drawTitle(graphics, c.x(), c.y(), "screen.player2npc.ui.access.title");
        Map<String, String> f = snapshots.getOrDefault(Player2NpcTab.ACCESS_LISTS, Map.of());
        if (drawSnapshotState(graphics, c, f)) {
            return;
        }
        drawActionResult(graphics, c, f);
        drawTableRow(graphics, c, 0, "screen.player2npc.ui.behavior.user_list_mode", listModeValue(f.get("userListMode")), true);
        drawTableRow(graphics, c, 1, "screen.player2npc.ui.behavior.bot_list_mode", listModeValue(f.get("botListMode")), true);
        UiLayoutRect viewport = accessListViewport(c);
        accessListScroll = Math.min(accessListScroll, accessListScrollMax(viewport));
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        for (int i = 0; i < ACCESS_LIST_LABEL_KEYS.length; i++) {
            UiLayoutRect box = accessListBox(c, i, viewport);
            graphics.drawString(font, Component.translatable(ACCESS_LIST_LABEL_KEYS[i]), box.x(), box.y() - 11,
                    Player2NpcUiTheme.TEXT_MUTED, false);
        }
        graphics.disableScissor();
        UiLayoutRect track = accessListScrollbarTrack(viewport);
        UiRenderCompat.drawScrollbar(graphics, track.x(), track.y(), track.height(),
                accessListContentHeight(), viewport.height(), accessListScroll);
    }

    private void renderModIntelligence(GuiGraphics graphics) {
        UiLayoutRect c = content().inset(14);
        drawTitle(graphics, c.x(), c.y(), "screen.player2npc.ui.mod_intelligence.title");
        Map<String, String> fields = snapshots.getOrDefault(Player2NpcTab.MOD_INTELLIGENCE, Map.of());
        if (drawSnapshotState(graphics, c, fields)) {
            return;
        }
        drawActionResult(graphics, c, fields);
        UiLayoutRect viewport = modIntelligenceViewport(c);
        modIntelligenceScroll = Math.min(modIntelligenceScroll, modIntelligenceScrollMax(viewport, fields));
        int bodyX = modIntelligenceBodyX(viewport);
        int bodyY = modIntelligenceBodyY(viewport);
        int bodyWidth = modIntelligenceBodyWidth(viewport);

        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        drawParagraph(graphics, "screen.player2npc.ui.mod_intelligence.subtitle",
                bodyX, bodyY, bodyWidth, Player2NpcUiTheme.TEXT_MUTED, 20);
        Component status = modIntelligenceStatus(fields);
        graphics.drawString(font, status, bodyX, bodyY + 25,
                modIntelligenceStatusColor(fields), false);
        if (!Boolean.parseBoolean(fields.getOrDefault("canManage", "false"))) {
            Component op = Component.translatable("screen.player2npc.ui.mod_intelligence.operator_only");
            graphics.drawString(font, op, bodyX + bodyWidth - font.width(op), bodyY + 25,
                    Player2NpcUiTheme.WARN, false);
        }

        String[] statFields = {"processedEntries", "partialEntries", "unknownEntries", "queuedEntries"};
        String[] statKeys = {
                "screen.player2npc.ui.mod_intelligence.stat.processed",
                "screen.player2npc.ui.mod_intelligence.stat.partial",
                "screen.player2npc.ui.mod_intelligence.stat.unknown",
                "screen.player2npc.ui.mod_intelligence.stat.queued"
        };
        int[] statColors = {Player2NpcUiTheme.GOOD, Player2NpcUiTheme.ACCENT,
                Player2NpcUiTheme.TEXT_MUTED, Player2NpcUiTheme.WARN};
        for (int i = 0; i < statFields.length; i++) {
            UiLayoutRect stat = modIntelligenceStatRect(viewport, i);
            UiRenderCompat.fillPanel(graphics, stat.x(), stat.y(), stat.width(), stat.height(), 0xAA22313D);
            Component value = Component.literal(fields.getOrDefault(statFields[i], "0"));
            graphics.drawString(font, value, stat.x() + 7, stat.y() + 7, statColors[i], false);
            drawClampedText(graphics, Component.translatable(statKeys[i]), stat.x() + 7, stat.y() + 21,
                    stat.width() - 14, Player2NpcUiTheme.TEXT_DIM);
        }

        for (int row = 0; row < 4; row++) {
            UiLayoutRect setting = modIntelligenceSettingRect(viewport, fields, row);
            UiRenderCompat.fillPanel(graphics, setting.x(), setting.y(), setting.width(), setting.height(), 0xAA1B2935);
            String titleKey = switch (row) {
                case 0 -> "screen.player2npc.ui.mod_intelligence.setting.enabled";
                case 1 -> "screen.player2npc.ui.mod_intelligence.setting.enrichment";
                case 2 -> "screen.player2npc.ui.mod_intelligence.setting.max_calls";
                default -> "screen.player2npc.ui.mod_intelligence.setting.bypass";
            };
            String descriptionKey = titleKey + ".description";
            boolean compactSetting = bodyWidth < 240;
            int controlWidth = row == 2 ? modIntelligenceNumericControlRect(viewport, setting).width() : 70;
            drawClampedText(graphics, Component.translatable(titleKey), setting.x() + 8, setting.y() + 7,
                    Math.max(30, setting.width() - (compactSetting ? 64 : controlWidth) - 18), Player2NpcUiTheme.TEXT);
            drawParagraph(graphics, descriptionKey, setting.x() + 8, setting.y() + 21,
                    Math.max(40, setting.width() - (compactSetting ? 16 : controlWidth + 18)),
                    Player2NpcUiTheme.TEXT_MUTED, 20);
            Component op = Component.translatable("screen.player2npc.ui.mod_intelligence.op_badge");
            int opX = compactSetting
                    ? setting.right() - font.width(op) - 8
                    : setting.right() - controlWidth - font.width(op) - 7;
            graphics.drawString(font, op, Math.max(setting.x() + 8, opX), setting.y() + 7,
                    Player2NpcUiTheme.WARN, false);
        }

        int resultsY = modIntelligenceResultsY(viewport);
        String queryText = fields.get("queryText");
        int resultCount = modIntelligenceQueryResultCount(fields);
        Component resultsTitle = queryText == null
                ? Component.translatable("screen.player2npc.ui.mod_intelligence.query.results")
                : Component.translatable("screen.player2npc.ui.mod_intelligence.query.results_count", resultCount);
        graphics.drawString(font, resultsTitle, bodyX, resultsY, Player2NpcUiTheme.TEXT, false);
        if (queryText == null || resultCount == 0) {
            UiLayoutRect result = new UiLayoutRect(bodyX, resultsY + 16, bodyWidth, MOD_INTELLIGENCE_RESULT_HEIGHT);
            UiRenderCompat.fillPanel(graphics, result.x(), result.y(), result.width(), result.height(), 0xAA1B2935);
            String key = queryText == null
                    ? "screen.player2npc.ui.mod_intelligence.query.hint"
                    : "screen.player2npc.ui.mod_intelligence.query.empty";
            drawParagraph(graphics, key, result.x() + 8, result.y() + 8,
                    result.width() - 16, Player2NpcUiTheme.TEXT_MUTED, result.height() - 12);
        } else {
            for (int i = 0; i < resultCount; i++) {
                UiLayoutRect result = new UiLayoutRect(bodyX,
                        resultsY + 16 + i * (MOD_INTELLIGENCE_RESULT_HEIGHT + 4),
                        bodyWidth, MOD_INTELLIGENCE_RESULT_HEIGHT);
                UiRenderCompat.fillPanel(graphics, result.x(), result.y(), result.width(), result.height(), 0xAA1B2935);
                drawClampedText(graphics, Component.literal(fields.getOrDefault("querySubject." + i, "-")),
                        result.x() + 8, result.y() + 6, result.width() - 16, Player2NpcUiTheme.TEXT);
                String meta = fields.getOrDefault("queryKind." + i, "") + "  |  "
                        + fields.getOrDefault("queryStatus." + i, "");
                String capabilities = fields.getOrDefault("queryCapabilities." + i, "");
                String detail = capabilities.isBlank() ? meta : meta + "  |  " + capabilities;
                drawClampedText(graphics, Component.literal(detail), result.x() + 8, result.y() + 20,
                        result.width() - 16, Player2NpcUiTheme.TEXT_MUTED);
            }
        }

        int footerY = modIntelligenceFooterY(viewport, fields);
        drawClampedText(graphics,
                Component.translatable("screen.player2npc.ui.mod_intelligence.footer"),
                bodyX, footerY, bodyWidth, Player2NpcUiTheme.TEXT_DIM);
        graphics.disableScissor();

        UiLayoutRect track = modIntelligenceScrollbarTrack(viewport);
        UiRenderCompat.drawScrollbar(graphics, track.x(), track.y(), track.height(),
                modIntelligenceContentHeight(viewport, fields), viewport.height(), modIntelligenceScroll);
    }

    private Component modIntelligenceStatus(Map<String, String> fields) {
        if (!Boolean.parseBoolean(fields.getOrDefault("enabled", "false"))) {
            return Component.translatable("screen.player2npc.ui.mod_intelligence.status.disabled");
        }
        if (Boolean.parseBoolean(fields.getOrDefault("inspecting", "false"))) {
            return Component.translatable("screen.player2npc.ui.mod_intelligence.status.scanning");
        }
        if (Boolean.parseBoolean(fields.getOrDefault("enriching", "false"))) {
            return Component.translatable("screen.player2npc.ui.mod_intelligence.status.enriching");
        }
        return switch (fields.getOrDefault("lastOperationCode", "idle")) {
            case "ingestion_complete" -> Component.translatable("screen.player2npc.ui.mod_intelligence.status.scan_complete");
            case "ingestion_failed" -> Component.translatable("screen.player2npc.ui.mod_intelligence.status.scan_failed");
            case "ingestion_executor_unavailable", "enrichment_executor_unavailable" ->
                    Component.translatable("screen.player2npc.ui.mod_intelligence.status.worker_unavailable");
            case "enrichment_complete" -> Component.translatable("screen.player2npc.ui.mod_intelligence.status.enrichment_complete");
            case "enrichment_partial" -> Component.translatable("screen.player2npc.ui.mod_intelligence.status.enrichment_partial");
            case "enrichment_deferred" -> Component.translatable("screen.player2npc.ui.mod_intelligence.status.enrichment_deferred");
            case "enrichment_empty" -> Component.translatable("screen.player2npc.ui.mod_intelligence.status.queue_empty");
            case "enrichment_failed" -> Component.translatable("screen.player2npc.ui.mod_intelligence.status.enrichment_failed");
            default -> Component.translatable("screen.player2npc.ui.mod_intelligence.status.idle");
        };
    }

    private int modIntelligenceStatusColor(Map<String, String> fields) {
        if (!Boolean.parseBoolean(fields.getOrDefault("enabled", "false"))) {
            return Player2NpcUiTheme.TEXT_DIM;
        }
        if (Boolean.parseBoolean(fields.getOrDefault("inspecting", "false"))
                || Boolean.parseBoolean(fields.getOrDefault("enriching", "false"))) {
            return Player2NpcUiTheme.WARN;
        }
        return switch (fields.getOrDefault("lastOperationCode", "idle")) {
            case "ingestion_failed", "enrichment_failed", "ingestion_executor_unavailable",
                    "enrichment_executor_unavailable" -> Player2NpcUiTheme.BAD;
            case "enrichment_partial", "enrichment_deferred" -> Player2NpcUiTheme.WARN;
            default -> Player2NpcUiTheme.GOOD;
        };
    }

    private void renderAiMemory(GuiGraphics graphics) {
        UiLayoutRect c = content().inset(14);
        drawTitle(graphics, c.x(), c.y(), "screen.player2npc.ui.ai_memory.title");
        Map<String, String> fields = snapshots.getOrDefault(Player2NpcTab.AI_MEMORY, Map.of());
        if (drawSnapshotState(graphics, c, fields)) {
            return;
        }
        drawActionResult(graphics, c, fields);

        UiLayoutRect viewport = aiMemoryViewport(c);
        aiMemoryScroll = Math.min(aiMemoryScroll, aiMemoryScrollMax(viewport));
        int bodyX = aiMemoryBodyX(viewport);
        int bodyY = aiMemoryBodyY(viewport);
        int bodyWidth = aiMemoryBodyWidth(viewport);
        boolean canManageDeepsearch = Boolean.parseBoolean(
                fields.getOrDefault("canManageDeepsearch", "false"));

        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        drawParagraph(graphics, "screen.player2npc.ui.ai_memory.subtitle",
                bodyX, bodyY, bodyWidth, Player2NpcUiTheme.TEXT_MUTED, 20);

        for (int row = 0; row < AI_MEMORY_ROW_COUNT; row++) {
            UiLayoutRect setting = aiMemorySettingRect(viewport, row);
            UiRenderCompat.fillPanel(graphics, setting.x(), setting.y(), setting.width(), setting.height(),
                    0xAA1B2935);
            Component title = Component.translatable(aiMemoryTitleKey(row));
            UiLayoutRect help = aiMemoryHelpRect(viewport, setting, row);
            int titleX = setting.x() + 8;
            int titleRight = help.x() - 4;
            if (aiMemoryOperatorOnly(row)) {
                Component op = Component.translatable("screen.player2npc.ui.ai_memory.op_badge");
                int opX = help.x() - font.width(op) - 5;
                graphics.drawString(font, op, opX, setting.y() + 7, Player2NpcUiTheme.BAD, false);
                titleRight = opX - 4;
            }
            drawClampedText(graphics, title, titleX, setting.y() + 7,
                    Math.max(24, titleRight - titleX), Player2NpcUiTheme.TEXT);

            graphics.fill(help.x(), help.y(), help.right(), help.bottom(), Player2NpcUiTheme.PANEL_BORDER);
            graphics.fill(help.x() + 1, help.y() + 1, help.right() - 1, help.bottom() - 1, 0xCC12172A);
            drawCenteredText(graphics,
                    Component.translatable("screen.player2npc.ui.ai_memory.help_marker"),
                    help.x(), help.right(), help.y() + 2, Player2NpcUiTheme.TEXT_DIM);

            drawParagraph(graphics, aiMemoryDescriptionKey(row), setting.x() + 8, setting.y() + 22,
                    aiMemoryTextWidth(viewport, setting, row), Player2NpcUiTheme.TEXT_MUTED,
                    aiMemoryCompact(viewport) ? 20 : 18);

            if (!canManageDeepsearch) {
                UiLayoutRect toggle = row == 3 || row == 6
                        ? aiMemorySliderRect(viewport, setting)
                        : aiMemoryToggleRect(viewport, setting);
                UiRenderCompat.drawIcon(graphics, ICON_LOCK,
                        toggle.x() + (toggle.width() - 12) / 2,
                        toggle.y() + (toggle.height() - 12) / 2, 12);
            }

            if (row == 3 || row == 6) {
                boolean calls = row == 6;
                int value = calls
                        ? (aiMemoryCallsSlider == null
                        ? intValue(fields.get("memoryCallsPerWindow"))
                        : aiMemoryCallsSlider.currentValue())
                        : (aiMemoryToolTopKSlider == null
                        ? intValue(fields.get("toolRetrievalTopK"))
                        : aiMemoryToolTopKSlider.currentValue());
                UiLayoutRect valueBox = aiMemoryValueRect(viewport, setting);
                graphics.fill(valueBox.x(), valueBox.y(), valueBox.right(), valueBox.bottom(),
                        Player2NpcUiTheme.PANEL_BORDER);
                graphics.fill(valueBox.x() + 1, valueBox.y() + 1, valueBox.right() - 1, valueBox.bottom() - 1,
                        0xCC12172A);
                Component valueText = calls
                        ? Component.translatable("screen.player2npc.ui.ai_memory.value.calls", value)
                        : Component.literal(String.valueOf(value));
                drawCenteredText(graphics, valueText, valueBox.x(), valueBox.right(),
                        valueBox.y() + 5, Player2NpcUiTheme.TEXT);
            }
        }

        int footerY = aiMemoryFooterY(viewport);
        Component source = Component.translatable("screen.player2npc.ui.ai_memory.source");
        int sourceWidth = font.width(source);
        drawClampedText(graphics, Component.translatable("screen.player2npc.ui.ai_memory.footer"),
                bodyX, footerY, Math.max(20, bodyWidth - sourceWidth - 10), Player2NpcUiTheme.TEXT_DIM);
        drawClampedText(graphics, source, bodyX + Math.max(0, bodyWidth - sourceWidth), footerY,
                sourceWidth, Player2NpcUiTheme.ACCENT);
        graphics.disableScissor();

        UiLayoutRect track = aiMemoryScrollbarTrack(viewport);
        UiRenderCompat.drawScrollbar(graphics, track.x(), track.y(), track.height(),
                aiMemoryContentHeight(viewport), viewport.height(), aiMemoryScroll);
    }

    private void renderAiMemoryTooltip(GuiGraphics graphics) {
        if (selectedTab != Player2NpcTab.AI_MEMORY || isSettingsView() || !globalSnapshotState.isEmpty()) {
            return;
        }
        Map<String, String> fields = snapshots.getOrDefault(Player2NpcTab.AI_MEMORY, Map.of());
        if (!canShowControls(fields)) {
            return;
        }
        UiLayoutRect viewport = aiMemoryViewport(content().inset(14));
        if (!isInside(viewport, lastMouseX, lastMouseY)) {
            return;
        }
        for (int row = 0; row < AI_MEMORY_ROW_COUNT; row++) {
            UiLayoutRect setting = aiMemorySettingRect(viewport, row);
            UiLayoutRect help = aiMemoryHelpRect(viewport, setting, row);
            if (isInside(help, lastMouseX, lastMouseY)) {
                graphics.renderTooltip(font,
                        font.split(Component.translatable(aiMemoryDescriptionKey(row)), 220),
                        lastMouseX, lastMouseY);
                return;
            }
        }
    }


    private void renderProfiles(GuiGraphics graphics) {
        UiLayoutRect c = content().inset(14);
        drawTitle(graphics, c.x(), c.y(), "screen.player2npc.ui.profiles.title");
        Map<String, String> fields = snapshots.getOrDefault(Player2NpcTab.PROFILES_MODELS, Map.of());
        if (drawSnapshotState(graphics, c, fields)) {
            return;
        }
        drawActionResult(graphics, c, fields);
        drawParagraph(graphics, "screen.player2npc.ui.profiles.body",
                c.x(), c.y() + 14, c.width() - 12, Player2NpcUiTheme.TEXT_MUTED,
                profilesSubtitleHeight(c));

        UiLayoutRect viewport = profilesViewport(c);
        profilesScroll = Math.min(profilesScroll, profilesScrollMax(viewport));
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());

        List<String> cardNames = profileCardNames();
        for (int i = 0; i < cardNames.size(); i++) {
            drawProfileCard(graphics, profileCardRect(viewport, i), cardNames.get(i));
        }

        if (profilesFeatureEligible()) {
            drawProfileSettings(graphics, viewport, fields);
        } else {
            drawProfileStatus(graphics, viewport);
        }
        drawProfileRouting(graphics, profilesRoutingRect(viewport));

        int footerY = profilesFooterY(viewport);
        Component source = Component.translatable("screen.player2npc.ui.profiles.source");
        int sourceWidth = font.width(source);
        drawClampedText(graphics, Component.translatable("screen.player2npc.ui.profiles.footer"),
                profilesBodyX(viewport), footerY,
                Math.max(20, profilesBodyWidth(viewport) - sourceWidth - 10),
                Player2NpcUiTheme.TEXT_DIM);
        drawClampedText(graphics, source,
                profilesBodyX(viewport) + Math.max(0, profilesBodyWidth(viewport) - sourceWidth),
                footerY, sourceWidth, Player2NpcUiTheme.ACCENT);
        graphics.disableScissor();

        UiLayoutRect track = profilesScrollbarTrack(viewport);
        UiRenderCompat.drawScrollbar(graphics, track.x(), track.y(), track.height(),
                profilesContentHeight(viewport), viewport.height(), profilesScroll);
    }

    private void drawProfileCard(GuiGraphics graphics, UiLayoutRect card, String profileName) {
        boolean defaultProfile = profileName == null || profileName.isBlank();
        UiRenderCompat.fillPanel(graphics, card.x(), card.y(), card.width(), card.height(),
                defaultProfile ? 0xCC233947 : 0xAA22313D);

        Component badge = defaultProfile
                ? Component.translatable("screen.player2npc.ui.profiles.active")
                : Component.literal(profilePatronTier);
        int badgeWidth = badge.getString().isBlank() ? 0 : Math.min(font.width(badge), card.width() / 3);
        int titleRight = card.right() - 8 - (badgeWidth > 0 ? badgeWidth + 10 : 0);
        drawClampedText(graphics, profileDisplayName(profileName),
                card.x() + 8, card.y() + 8,
                Math.max(30, titleRight - card.x() - 8), Player2NpcUiTheme.TEXT);

        if (badgeWidth > 0) {
            int badgeX = card.right() - badgeWidth - 10;
            graphics.fill(badgeX - 2, card.y() + 5, card.right() - 6, card.y() + 17,
                    Player2NpcUiTheme.PANEL_BORDER);
            drawClampedText(graphics, badge, badgeX, card.y() + 7, badgeWidth,
                    defaultProfile ? Player2NpcUiTheme.GOOD : Player2NpcUiTheme.ACCENT);
        }

        drawParagraph(graphics,
                defaultProfile
                        ? "screen.player2npc.ui.profiles.default_description"
                        : "screen.player2npc.ui.profiles.named_description",
                card.x() + 8, card.y() + 27, card.width() - 16,
                Player2NpcUiTheme.TEXT_MUTED, card.height() - 34);
    }

    private void drawProfileStatus(GuiGraphics graphics, UiLayoutRect viewport) {
        UiLayoutRect status = profilesStatusRect(viewport);
        UiRenderCompat.fillPanel(graphics, status.x(), status.y(), status.width(), status.height(),
                0xAA22313D);
        drawClampedText(graphics,
                Component.translatable(profilesStatusTitleKey()).withStyle(ChatFormatting.BOLD),
                status.x() + 8, status.y() + 8, status.width() - 16,
                profileDiscoveryState == ProfileDiscoveryState.FAILED
                        ? Player2NpcUiTheme.BAD : Player2NpcUiTheme.WARN);
        drawParagraph(graphics, profilesStatusBodyKey(), status.x() + 8, status.y() + 25,
                status.width() - 16, Player2NpcUiTheme.TEXT_MUTED,
                profilesStatusBodyHeight(viewport));
    }

    private void drawProfileSettings(GuiGraphics graphics, UiLayoutRect viewport,
                                     Map<String, String> fields) {
        boolean canManage = Boolean.parseBoolean(fields.getOrDefault("canManage", "false"));

        UiLayoutRect behavior = profilesBehaviorRect(viewport);
        UiRenderCompat.fillPanel(graphics, behavior.x(), behavior.y(), behavior.width(), behavior.height(),
                0xAA22313D);
        drawClampedText(graphics,
                Component.translatable("screen.player2npc.ui.profiles.soft_limit.title")
                        .withStyle(ChatFormatting.BOLD),
                behavior.x() + 8, behavior.y() + 7,
                Math.max(40, behavior.width() - 16), Player2NpcUiTheme.TEXT);
        drawParagraph(graphics, "screen.player2npc.ui.profiles.soft_limit.description",
                behavior.x() + 8, behavior.y() + 22, behavior.width() - 16,
                Player2NpcUiTheme.TEXT_MUTED, profilesBehaviorDescriptionHeight(viewport));
        if (!canManage) {
            int operatorY = behavior.y() + 22 + profilesBehaviorDescriptionHeight(viewport) + 4;
            drawParagraph(graphics, "screen.player2npc.ui.profiles.operator_only",
                    behavior.x() + 8, operatorY, behavior.width() - 16,
                    Player2NpcUiTheme.WARN, profilesOperatorNoteHeight(viewport));
        }

        UiLayoutRect fallback = profilesFallbackRect(viewport);
        UiRenderCompat.fillPanel(graphics, fallback.x(), fallback.y(), fallback.width(), fallback.height(),
                0xAA22313D);
        drawClampedText(graphics,
                Component.translatable("screen.player2npc.ui.profiles.fallback.title")
                        .withStyle(ChatFormatting.BOLD),
                fallback.x() + 8, fallback.y() + 7,
                fallback.width() - 16, Player2NpcUiTheme.TEXT);
        String fallbackDescriptionKey = profilesFallbackDescriptionKey();
        drawParagraph(graphics,
                fallbackDescriptionKey,
                fallback.x() + 8, fallback.y() + 22,
                profilesFallbackDescriptionWidth(viewport),
                "screen.player2npc.ui.profiles.fallback.description".equals(
                        fallbackDescriptionKey)
                        ? Player2NpcUiTheme.TEXT_MUTED : Player2NpcUiTheme.BAD,
                profilesFallbackDescriptionHeight(viewport));
    }

    private void drawProfileRouting(GuiGraphics graphics, UiLayoutRect routing) {
        UiRenderCompat.fillPanel(graphics, routing.x(), routing.y(), routing.width(), routing.height(),
                0x8822313D);
        drawClampedText(graphics,
                Component.translatable("screen.player2npc.ui.profiles.routing.title")
                        .withStyle(ChatFormatting.BOLD),
                routing.x() + 8, routing.y() + 6, routing.width() - 16,
                Player2NpcUiTheme.ACCENT);
        drawParagraph(graphics, "screen.player2npc.ui.profiles.routing.body",
                routing.x() + 8, routing.y() + 20, routing.width() - 16,
                Player2NpcUiTheme.TEXT_MUTED,
                profileWrappedHeight("screen.player2npc.ui.profiles.routing.body",
                        routing.width() - 16, 16));
    }


    private void renderUnavailable(GuiGraphics graphics, String titleKey) {
        UiLayoutRect c = content().inset(14);
        drawTitle(graphics, c.x(), c.y(), titleKey);
        drawParagraph(graphics, "screen.player2npc.ui.advanced.unavailable", c.x(), c.y() + 24, c.width() - 12, Player2NpcUiTheme.TEXT_MUTED);
    }

    private void drawCard(GuiGraphics graphics, int x, int y, int width, int height, String titleKey, String bodyKey) {
        UiRenderCompat.fillPanel(graphics, x, y, width, height, 0xAA22313D);
        graphics.drawString(font, Component.translatable(titleKey), x + 8, y + 8, Player2NpcUiTheme.TEXT, false);
        drawParagraph(graphics, bodyKey, x + 8, y + 24, width - 16, Player2NpcUiTheme.TEXT_MUTED, height - 32);
    }

    private void drawGettingStartedCard(GuiGraphics graphics, UiLayoutRect card, String titleKey, GettingStartedAction[] actions, Component... bodyLines) {
        UiRenderCompat.fillPanel(graphics, card.x(), card.y(), card.width(), card.height(), 0xAA22313D);
        graphics.enableScissor(card.x(), card.y(), card.right(), card.bottom());
        int textX = card.x() + 8;
        int textY = card.y() + 8;
        graphics.drawString(font, Component.translatable(titleKey).withStyle(ChatFormatting.BOLD), textX, textY, Player2NpcUiTheme.TEXT, false);
        textY += 17;
        int maxBottom = card.bottom() - 8 - (actions.length == 0 ? 0 : GETTING_STARTED_ACTION_BUTTON_HEIGHT + 9);
        for (Component bodyLine : bodyLines) {
            textY = drawFormattedParagraph(graphics, bodyLine, textX, textY, card.width() - 16, Player2NpcUiTheme.TEXT_MUTED, maxBottom - textY);
            textY += 3;
            if (textY >= maxBottom) {
                break;
            }
        }
        for (GettingStartedAction action : actions) {
            drawGettingStartedActionButton(graphics, action, gettingStartedActionButtonRect(card, actions, action));
        }
        graphics.disableScissor();
    }

    private void drawGettingStartedCardIfVisible(GuiGraphics graphics, UiLayoutRect viewport, UiLayoutRect card, String titleKey, GettingStartedAction[] actions, Component... bodyLines) {
        if (card.bottom() <= viewport.y() || card.y() >= viewport.bottom()) {
            return;
        }
        drawGettingStartedCard(graphics, card, titleKey, actions, bodyLines);
    }

    private int gettingStartedCardHeight(int width, Component... bodyLines) {
        return gettingStartedCardHeight(width, new GettingStartedAction[0], bodyLines);
    }

    private int gettingStartedCardHeight(int width, GettingStartedAction[] actions, Component... bodyLines) {
        int textWidth = Math.max(40, width - 16);
        int height = 8 + 10 + 7 + 8;
        for (Component bodyLine : bodyLines) {
            height += Math.max(1, font.split(bodyLine, textWidth).size()) * 10 + 3;
        }
        if (actions.length > 0) {
            height += GETTING_STARTED_ACTION_BUTTON_HEIGHT + 9;
        }
        return height;
    }

    private void drawGettingStartedActionButton(GuiGraphics graphics, GettingStartedAction action, UiLayoutRect button) {
        boolean active = isGettingStartedActionActive(action);
        boolean hovered = active && isInside(button, lastMouseX, lastMouseY);
        if (action == GettingStartedAction.AUTHORIZE) {
            UiRenderCompat.drawAuthButton(graphics, button.x(), button.y(), button.width(), button.height(), active, hovered);
            ResourceLocation icon = authorizeModState == AuthButtonState.CONNECTED ? ICON_CHECK : ICON_LOCK;
            UiRenderCompat.drawIcon(graphics, icon, button.x() + 8, button.y() + 5, 8);
        } else {
            UiRenderCompat.drawButton(graphics, button.x(), button.y(), button.width(), button.height(), active, hovered, false);
        }
        Component label = Component.translatable(action.labelKey);
        int textLeft = action == GettingStartedAction.AUTHORIZE ? button.x() + 22 : button.x() + 7;
        int textRight = button.right() - 7;
        int textWidth = Math.max(0, textRight - textLeft);
        int labelWidth = font.width(label);
        int textY = button.y() + (button.height() - 8) / 2;
        int color = active ? Player2NpcUiTheme.TEXT : Player2NpcUiTheme.TEXT_DIM;
        if (labelWidth <= textWidth) {
            graphics.drawString(font, label, textLeft + (textWidth - labelWidth) / 2, textY, color, false);
        } else {
            drawClampedText(graphics, label, textLeft, textY, textWidth, color);
        }
    }

    private boolean isGettingStartedActionActive(GettingStartedAction action) {
        return action != GettingStartedAction.AUTHORIZE || authorizeModState == AuthButtonState.NEEDS_AUTH;
    }

    private UiLayoutRect gettingStartedActionButtonRect(UiLayoutRect card, GettingStartedAction[] actions, GettingStartedAction action) {
        int x = card.right() - 8;
        for (int i = actions.length - 1; i >= 0; i--) {
            GettingStartedAction candidate = actions[i];
            int width = Math.min(candidate.width, Math.max(72, card.width() - 16));
            x -= width;
            if (candidate == action) {
                return new UiLayoutRect(x, card.bottom() - GETTING_STARTED_ACTION_BUTTON_HEIGHT - 8,
                        width, GETTING_STARTED_ACTION_BUTTON_HEIGHT);
            }
            x -= GETTING_STARTED_ACTION_BUTTON_GAP;
        }
        return new UiLayoutRect(card.right() - 8, card.bottom() - GETTING_STARTED_ACTION_BUTTON_HEIGHT - 8, 0, 0);
    }

    private UiLayoutRect gettingStartedTroubleshootingCard() {
        UiLayoutRect c = content().inset(14);
        UiLayoutRect cards = gettingStartedCardArea(c);
        int y = cards.y() + gettingStartedCardHeight(cards.width(), gettingStartedTalkBody()) + GETTING_STARTED_CARD_GAP;
        return new UiLayoutRect(cards.x(), y, cards.width(),
                gettingStartedCardHeight(cards.width(), botTroubleshootingActions(), gettingStartedTroubleshootingLines()));
    }

    private UiLayoutRect gettingStartedJoulesCard() {
        UiLayoutRect c = content().inset(14);
        UiLayoutRect cards = gettingStartedCardArea(c);
        int y = cards.y()
                + gettingStartedCardHeight(cards.width(), gettingStartedTalkBody())
                + GETTING_STARTED_CARD_GAP
                + gettingStartedCardHeight(cards.width(), botTroubleshootingActions(), gettingStartedTroubleshootingLines())
                + GETTING_STARTED_CARD_GAP;
        return new UiLayoutRect(cards.x(), y, cards.width(),
                gettingStartedCardHeight(cards.width(), joulesActions(), gettingStartedJoulesLines()));
    }

    private void refreshAuthorizeModState() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            authorizeModState = AuthButtonState.NEEDS_AUTH;
            return;
        }
        int requestId = ++authorizeModRequestId;
        authorizeModState = AuthButtonState.CHECKING;
        CompletableFuture.supplyAsync(() -> AuthenticationManager.getInstance().hasApiConnection(client.player, PLAYER2_GAME_ID))
                .whenCompleteAsync((connected, error) -> {
                    if (requestId != authorizeModRequestId) {
                        return;
                    }
                    authorizeModState = error == null && Boolean.TRUE.equals(connected)
                            ? AuthButtonState.CONNECTED
                            : AuthButtonState.NEEDS_AUTH;
                }, client);
    }

    private boolean clickGettingStartedActionButton(double mouseX, double mouseY) {
        if (selectedTab != Player2NpcTab.GETTING_STARTED) {
            return false;
        }
        UiLayoutRect viewport = gettingStartedViewport(content().inset(14));
        if (!isInside(viewport, mouseX, mouseY)) {
            return false;
        }
        if (clickGettingStartedAction(mouseX, mouseY, gettingStartedTroubleshootingCard(), botTroubleshootingActions())) {
            return true;
        }
        return clickGettingStartedAction(mouseX, mouseY, gettingStartedJoulesCard(), joulesActions());
    }

    private boolean clickGettingStartedAction(double mouseX, double mouseY, UiLayoutRect card, GettingStartedAction[] actions) {
        for (GettingStartedAction action : actions) {
            UiLayoutRect button = gettingStartedActionButtonRect(card, actions, action);
            if (isInside(button, mouseX, mouseY)) {
                if (!isGettingStartedActionActive(action)) {
                    return false;
                }

                runGettingStartedAction(action);
                return true;
            }
        }
        return false;
    }

    private void openAuthorizeMod() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !isGettingStartedActionActive(GettingStartedAction.AUTHORIZE)) {
            return;
        }
        int requestId = ++authorizeModRequestId;
        authorizeModState = AuthButtonState.AUTHORIZING;
        AuthenticationManager.getInstance()
                .authenticate(client.player, PLAYER2_GAME_ID, this::openUrl)
                .whenCompleteAsync((token, error) -> {
                    if (requestId != authorizeModRequestId) {
                        return;
                    }
                    authorizeModState = error == null ? AuthButtonState.CONNECTED : AuthButtonState.NEEDS_AUTH;
                }, client);
    }

    private void runGettingStartedAction(GettingStartedAction action) {
        switch (action) {
            case AUTHORIZE -> openAuthorizeMod();
            case DISCORD -> openUrl(PLAYER2_DISCORD_URL);
            case PURCHASE -> openUrl(PLAYER2_PURCHASE_URL);
            case FREE_JS -> openUrl(PLAYER2_FREE_JS_URL);
        }
    }

    private void openUrl(String url) {
        CompletableFuture.runAsync(() -> {
            try {
                Util.getPlatform().openUri(url);
            } catch (RuntimeException ignored) {
            }
        });
    }

    private GettingStartedAction[] botTroubleshootingActions() {
        return new GettingStartedAction[] { GettingStartedAction.DISCORD, GettingStartedAction.AUTHORIZE };
    }

    private GettingStartedAction[] joulesActions() {
        return new GettingStartedAction[] { GettingStartedAction.PURCHASE, GettingStartedAction.FREE_JS };
    }

    private Component gettingStartedTalkBody() {
        return Component.translatable("screen.player2npc.ui.getting_started.talk_body",
                boldText("screen.player2npc.ui.getting_started.call_by_name_chat"));
    }

    private Component[] gettingStartedTroubleshootingLines() {
        return new Component[] {
                Component.translatable("screen.player2npc.ui.getting_started.troubleshooting.app_running"),
                Component.translatable("screen.player2npc.ui.getting_started.troubleshooting.authorized"),
                Component.translatable("screen.player2npc.ui.getting_started.troubleshooting.discord")
        };
    }

    private Component[] gettingStartedJoulesLines() {
        return new Component[] {
                Component.translatable("screen.player2npc.ui.getting_started.joules.daily_spin"),
                Component.translatable("screen.player2npc.ui.getting_started.joules.free_models",
                        boldText("screen.player2npc.ui.getting_started.free_chat_model"),
                        boldText("screen.player2npc.ui.getting_started.free_voice_model")),
                Component.translatable("screen.player2npc.ui.getting_started.joules.patron",
                        boldText("screen.player2npc.ui.getting_started.patron")),
                Component.translatable("screen.player2npc.ui.getting_started.joules.paygo")
        };
    }

    private int gettingStartedContentHeight(int width) {
        return gettingStartedCardHeight(width, gettingStartedTalkBody())
                + GETTING_STARTED_CARD_GAP
                + gettingStartedCardHeight(width, botTroubleshootingActions(), gettingStartedTroubleshootingLines())
                + GETTING_STARTED_CARD_GAP
                + gettingStartedCardHeight(width, joulesActions(), gettingStartedJoulesLines());
    }

    private Component boldText(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
    }

    private void drawCenteredText(GuiGraphics graphics, Component text, int left, int right, int y, int color) {
        graphics.drawString(font, text, left + Math.max(0, right - left - font.width(text)) / 2, y, color, false);
    }

    private void drawTitle(GuiGraphics graphics, int x, int y, String key) {
        graphics.drawString(font, Component.translatable(key), x, y, Player2NpcUiTheme.TEXT, false);
    }

    private void drawParagraph(GuiGraphics graphics, String key, int x, int y, int width, int color) {
        drawParagraph(graphics, key, x, y, width, color, Integer.MAX_VALUE);
    }

    private void drawParagraph(GuiGraphics graphics, String key, int x, int y, int width, int color, int maxHeight) {
        drawFormattedParagraph(graphics, Component.translatable(key), x, y, width, color, maxHeight);
    }

    private int drawFormattedParagraph(GuiGraphics graphics, Component text, int x, int y, int width, int color, int maxHeight) {
        int textY = y;
        int bottom = y + maxHeight;
        for (FormattedCharSequence line : font.split(text, width)) {
            if (textY + 9 > bottom) {
                break;
            }
            graphics.drawString(font, line, x, textY, color, false);
            textY += 10;
        }
        return textY;
    }

    private void drawBudgetRow(GuiGraphics graphics, UiLayoutRect table, int row, String labelKey,
                               Component value, boolean editable, boolean zeroDisables) {
        int y = budgetRowY(table, row);
        int rowBottom = y + BUDGET_ROW_HEIGHT;
        if (row % 2 == 0) {
            graphics.fill(table.x(), y, table.right(), rowBottom, 0x221E2D39);
        }
        graphics.fill(table.x(), rowBottom - 1, table.right(), rowBottom, 0x224F8EAA);
        int labelX = table.x() + 6;
        if (editable) {
            UiLayoutRect control = budgetNumericControlRect(table, row);
            int labelWidth = Math.max(20, control.x() - labelX - 7);
            int titleY = zeroDisables ? y + 5 : y + (BUDGET_ROW_HEIGHT - 8) / 2;
            drawClampedText(graphics, Component.translatable(labelKey), labelX, titleY,
                    labelWidth, Player2NpcUiTheme.TEXT_MUTED);
            if (zeroDisables) {
                drawClampedText(graphics, Component.translatable("screen.player2npc.ui.budget.zero_off"),
                        labelX, y + 17, labelWidth, Player2NpcUiTheme.TEXT_DIM);
            }
            return;
        }
        int valueX = table.x() + Math.max(92, table.width() / 2);
        int textY = y + (BUDGET_ROW_HEIGHT - 8) / 2;
        drawClampedText(graphics, Component.translatable(labelKey), labelX, textY,
                valueX - labelX - 8, Player2NpcUiTheme.TEXT_MUTED);
        drawClampedText(graphics, value, valueX, textY,
                table.right() - valueX - 6, Player2NpcUiTheme.TEXT);
    }

    private void drawBudgetDivider(GuiGraphics graphics, UiLayoutRect table, int row) {
        int y = budgetRowY(table, row) - 2;
        if (y > table.y() && y < table.bottom()) {
            graphics.fill(table.x(), y, table.right(), y + 1, Player2NpcUiTheme.PANEL_BORDER);
        }
    }

    private void drawTableRow(GuiGraphics graphics, UiLayoutRect table, int row, String labelKey, Component value, boolean hasAction) {
        int y = tableRowY(table, row);
        int rowBottom = y + TABLE_ROW_HEIGHT;
        if (row % 2 == 0) {
            graphics.fill(table.x(), y, table.right(), rowBottom, 0x221E2D39);
        }
        graphics.fill(table.x(), rowBottom - 1, table.right(), rowBottom, 0x224F8EAA);
        int labelX = table.x() + 6;
        int valueX = tableValueX(table);
        int textY = y + 5;
        drawClampedText(graphics, Component.translatable(labelKey), labelX, textY, valueX - labelX - 8, Player2NpcUiTheme.TEXT_MUTED);
        int valueRight = hasAction ? tableActionX(table) - 8 : table.right() - 6;
        drawClampedText(graphics, value, valueX, textY, valueRight - valueX, Player2NpcUiTheme.TEXT);
    }

    private int tableRowY(UiLayoutRect table, int row) {
        return table.y() + 24 + row * TABLE_ROW_HEIGHT;
    }

    private int tableActionY(UiLayoutRect table, int row) {
        return tableRowY(table, row) + (TABLE_ROW_HEIGHT - TABLE_ACTION_BUTTON_HEIGHT) / 2;
    }

    private int tableActionX(UiLayoutRect table) {
        return table.right() - TABLE_ACTION_WIDTH - 6;
    }

    private int tableValueX(UiLayoutRect table) {
        return Math.min(table.x() + 150, tableActionX(table) - 92);
    }

    private int drawField(GuiGraphics graphics, int x, int y, String key, String value) {
        return drawField(graphics, x, y, key, value == null || value.isBlank() ? Component.translatable("screen.player2npc.ui.value.unavailable") : Component.literal(value));
    }

    private int drawField(GuiGraphics graphics, int x, int y, String key, Component value) {
        graphics.drawString(font, Component.translatable(key), x, y, Player2NpcUiTheme.TEXT_MUTED, false);
        int valueX = x + 150;
        int maxWidth = Math.max(30, content().inset(14).right() - valueX - 8);
        drawClampedValue(graphics, value, valueX, y, maxWidth);
        return y + 14;
    }

    private void drawClampedValue(GuiGraphics graphics, Component value, int x, int y, int maxWidth) {
        drawClampedText(graphics, value, x, y, maxWidth, Player2NpcUiTheme.TEXT);
    }

    private void drawClampedText(GuiGraphics graphics, Component value, int x, int y, int maxWidth, int color) {
        if (maxWidth <= 0) {
            return;
        }
        if (font.width(value) <= maxWidth) {
            graphics.drawString(font, value, x, y, color, false);
            return;
        }
        String ellipsis = "...";
        int bodyWidth = Math.max(0, maxWidth - font.width(ellipsis));
        graphics.drawString(font, Component.literal(font.plainSubstrByWidth(value.getString(), bodyWidth) + ellipsis), x, y, color, false);
    }

    private UiLayoutRect frame() {
        int availableWidth = Math.max(1, width - 4);
        int availableHeight = Math.max(1, height - 4);
        int w = Math.min(availableWidth,
                Math.max(Player2NpcUiTheme.MIN_WIDTH, Math.min(width - 28, 640)));
        int h = Math.min(availableHeight,
                Math.max(Player2NpcUiTheme.MIN_HEIGHT, Math.min(height - 28, 380)));
        return new UiLayoutRect((width - w) / 2, (height - h) / 2, w, h);
    }

    private UiLayoutRect navPane(UiLayoutRect frame) {
        int x = frame.x() + Player2NpcUiTheme.PAD - 4;
        int y = frame.y() + Player2NpcUiTheme.TOP_HEIGHT + Player2NpcUiTheme.PAD - 4;
        return new UiLayoutRect(x, y, Player2NpcUiTheme.NAV_WIDTH, frame.bottom() - y - Player2NpcUiTheme.PAD);
    }

    private UiLayoutRect navViewport(UiLayoutRect nav) {
        return nav.inset(8);
    }

    private int navButtonY(UiLayoutRect navViewport, int index) {
        return navViewport.y() + index * (NAV_BUTTON_HEIGHT + NAV_BUTTON_GAP);
    }

    private int navListContentHeight(int tabCount) {
        return tabCount * NAV_BUTTON_HEIGHT + Math.max(0, tabCount - 1) * NAV_BUTTON_GAP;
    }

    private int navScrollMax(UiLayoutRect navViewport, int tabCount) {
        return Math.max(0, navListContentHeight(tabCount) - navViewport.height());
    }

    private UiLayoutRect navScrollbarTrack(UiLayoutRect navViewport) {
        return new UiLayoutRect(navViewport.right() - NAV_SCROLLBAR_WIDTH, navViewport.y(), NAV_SCROLLBAR_WIDTH, navViewport.height());
    }

    private UiLayoutRect navScrollbarThumb(UiLayoutRect navViewport) {
        UiLayoutRect track = navScrollbarTrack(navViewport);
        int contentHeight = navListContentHeight(Player2NpcTab.visibleTabs().length);
        if (contentHeight <= navViewport.height() || track.height() <= 0) {
            return new UiLayoutRect(track.x(), track.y(), track.width(), 0);
        }
        int thumbHeight = Math.max(12, track.height() * navViewport.height() / contentHeight);
        int maxScroll = Math.max(1, contentHeight - navViewport.height());
        int thumbTravel = Math.max(1, track.height() - thumbHeight);
        int clampedScroll = clampInt(navScroll, 0, maxScroll);
        int thumbY = track.y() + (int) ((long) thumbTravel * clampedScroll / maxScroll);
        return new UiLayoutRect(track.x(), thumbY, track.width(), thumbHeight);
    }

    private UiLayoutRect budgetViewport(UiLayoutRect content) {
        int y = content.y() + 24;
        return new UiLayoutRect(content.x(), y, content.width(), Math.max(0, content.bottom() - y - 6));
    }

    private UiLayoutRect budgetTable(UiLayoutRect content) {
        UiLayoutRect viewport = budgetViewport(content);
        return new UiLayoutRect(viewport.x(), content.y() - budgetScroll,
                Math.max(0, viewport.width() - BUDGET_SCROLLBAR_WIDTH - 10), budgetContentHeight() + 24);
    }

    private int budgetContentHeight() {
        return BUDGET_ROW_COUNT * BUDGET_ROW_HEIGHT + BUDGET_RESET_GAP + BUDGET_RESET_HEIGHT + 8;
    }

    private int budgetResetY(UiLayoutRect table) {
        return budgetRowY(table, BUDGET_ROW_COUNT) + BUDGET_RESET_GAP;
    }

    private int budgetRowY(UiLayoutRect table, int row) {
        return table.y() + 24 + row * BUDGET_ROW_HEIGHT;
    }

    private UiLayoutRect budgetNumericControlRect(UiLayoutRect table, int row) {
        int available = Math.max(80, table.width() - 92);
        int width = Math.min(BUDGET_NUMERIC_CONTROL_MAX_WIDTH,
                Math.min(available, Math.max(120, table.width() / 2)));
        return new UiLayoutRect(table.right() - width - 6,
                budgetRowY(table, row) + (BUDGET_ROW_HEIGHT - NUMERIC_CONTROL_HEIGHT) / 2,
                width, NUMERIC_CONTROL_HEIGHT);
    }

    private int budgetScrollMax(UiLayoutRect viewport) {
        return Math.max(0, budgetContentHeight() - viewport.height());
    }

    private UiLayoutRect budgetScrollbarTrack(UiLayoutRect viewport) {
        return new UiLayoutRect(viewport.right() - BUDGET_SCROLLBAR_WIDTH, viewport.y(), BUDGET_SCROLLBAR_WIDTH, viewport.height());
    }

    private UiLayoutRect budgetScrollbarThumb(UiLayoutRect viewport) {
        UiLayoutRect track = budgetScrollbarTrack(viewport);
        int contentHeight = budgetContentHeight();
        if (contentHeight <= viewport.height() || track.height() <= 0) {
            return new UiLayoutRect(track.x(), track.y(), track.width(), 0);
        }
        int thumbHeight = Math.max(12, track.height() * viewport.height() / contentHeight);
        int maxScroll = Math.max(1, contentHeight - viewport.height());
        int thumbTravel = Math.max(1, track.height() - thumbHeight);
        int clampedScroll = clampInt(budgetScroll, 0, maxScroll);
        int thumbY = track.y() + (int) ((long) thumbTravel * clampedScroll / maxScroll);
        return new UiLayoutRect(track.x(), thumbY, track.width(), thumbHeight);
    }

    private UiLayoutRect gettingStartedViewport(UiLayoutRect content) {
        int y = content.y() + 34;
        return new UiLayoutRect(content.x(), y, content.width(), Math.max(0, content.bottom() - y - 4));
    }

    private UiLayoutRect gettingStartedCardArea(UiLayoutRect content) {
        UiLayoutRect viewport = gettingStartedViewport(content);
        int cardWidth = Math.max(0, viewport.width() - GETTING_STARTED_SCROLLBAR_WIDTH - 10);
        return new UiLayoutRect(viewport.x(), viewport.y() - gettingStartedScroll, cardWidth, gettingStartedContentHeight(cardWidth));
    }

    private int gettingStartedScrollMax(UiLayoutRect viewport) {
        int cardWidth = Math.max(0, viewport.width() - GETTING_STARTED_SCROLLBAR_WIDTH - 10);
        return Math.max(0, gettingStartedContentHeight(cardWidth) - viewport.height());
    }

    private UiLayoutRect gettingStartedScrollbarTrack(UiLayoutRect viewport) {
        return new UiLayoutRect(viewport.right() - GETTING_STARTED_SCROLLBAR_WIDTH, viewport.y(), GETTING_STARTED_SCROLLBAR_WIDTH, viewport.height());
    }

    private UiLayoutRect gettingStartedScrollbarThumb(UiLayoutRect viewport) {
        UiLayoutRect track = gettingStartedScrollbarTrack(viewport);
        int cardWidth = Math.max(0, viewport.width() - GETTING_STARTED_SCROLLBAR_WIDTH - 10);
        int contentHeight = gettingStartedContentHeight(cardWidth);
        if (contentHeight <= viewport.height() || track.height() <= 0) {
            return new UiLayoutRect(track.x(), track.y(), track.width(), 0);
        }
        int thumbHeight = Math.max(12, track.height() * viewport.height() / contentHeight);
        int maxScroll = Math.max(1, contentHeight - viewport.height());
        int thumbTravel = Math.max(1, track.height() - thumbHeight);
        int clampedScroll = clampInt(gettingStartedScroll, 0, maxScroll);
        int thumbY = track.y() + (int) ((long) thumbTravel * clampedScroll / maxScroll);
        return new UiLayoutRect(track.x(), thumbY, track.width(), thumbHeight);
    }

    private UiLayoutRect content() {
        UiLayoutRect frame = frame();
        int x = frame.x() + Player2NpcUiTheme.NAV_WIDTH + Player2NpcUiTheme.PAD;
        int y = frame.y() + Player2NpcUiTheme.TOP_HEIGHT + Player2NpcUiTheme.PAD;
        return new UiLayoutRect(x, y, frame.right() - x - Player2NpcUiTheme.PAD, frame.bottom() - y - Player2NpcUiTheme.PAD);
    }

    private UiLayoutRect companionListPane(UiLayoutRect content) {
        int y = content.y() + 34;
        return new UiLayoutRect(content.x(), y, Math.max(0, content.width() - 6), Math.max(0, content.bottom() - y - 8));
    }

    private int companionColumns(UiLayoutRect list) {
        return Math.max(1, (list.width() + COMPANION_CARD_GAP) / (COMPANION_CARD_TARGET_WIDTH + COMPANION_CARD_GAP));
    }

    private int companionCardWidth(UiLayoutRect list, int columns) {
        int available = Math.max(COMPANION_CARD_MIN_WIDTH, list.width() - 8 - (columns - 1) * COMPANION_CARD_GAP);
        return Math.max(COMPANION_CARD_MIN_WIDTH, available / columns);
    }

    private int companionListContentHeight(UiLayoutRect list) {
        if (characters.length == 0) {
            return 0;
        }
        int rows = (characters.length + companionColumns(list) - 1) / companionColumns(list);
        return rows * COMPANION_CARD_HEIGHT + Math.max(0, rows - 1) * COMPANION_CARD_GAP;
    }

    private int companionScrollMax(UiLayoutRect list) {
        return Math.max(0, companionListContentHeight(list) - list.height());
    }

    private UiLayoutRect companionScrollbarTrack(UiLayoutRect list) {
        return new UiLayoutRect(list.right() - COMPANION_SCROLLBAR_WIDTH, list.y(), COMPANION_SCROLLBAR_WIDTH, list.height());
    }

    private UiLayoutRect companionScrollbarThumb(UiLayoutRect list) {
        UiLayoutRect track = companionScrollbarTrack(list);
        int contentHeight = companionListContentHeight(list);
        if (contentHeight <= list.height() || track.height() <= 0) {
            return new UiLayoutRect(track.x(), track.y(), track.width(), 0);
        }
        int thumbHeight = Math.max(12, track.height() * list.height() / contentHeight);
        int maxScroll = Math.max(1, contentHeight - list.height());
        int thumbTravel = Math.max(1, track.height() - thumbHeight);
        int clampedScroll = clampInt(companionScroll, 0, maxScroll);
        int thumbY = track.y() + (int) ((long) thumbTravel * clampedScroll / maxScroll);
        return new UiLayoutRect(track.x(), thumbY, track.width(), thumbHeight);
    }

    private UiLayoutRect companionDetailViewport(UiLayoutRect content) {
        int y = content.y() + 24;
        return new UiLayoutRect(content.x(), y, Math.max(0, content.width() - 6),
                Math.max(0, content.bottom() - y - COMPANION_DETAIL_BUTTON_AREA_HEIGHT));
    }

    private int companionDetailTextWidth(UiLayoutRect content) {
        return Math.max(24, content.width() - 70 - COMPANION_DETAIL_SCROLLBAR_WIDTH - 12);
    }

    private int companionDetailContentHeight(UiLayoutRect content) {
        return companionAdditionalPromptBoxOffset(content) + companionAdditionalPromptBoxHeight(content) + 12;
    }

    private int companionDescriptionLineCount(UiLayoutRect content) {
        if (selectedCharacter == null) {
            return 1;
        }
        String description = selectedCharacter.description() == null ? "" : selectedCharacter.description();
        return Math.max(1, font.getSplitter().splitLines(description, companionDetailTextWidth(content), Style.EMPTY).size());
    }

    private int companionTaskChainBoxOffset(UiLayoutRect content) {
        return COMPANION_DETAIL_DESCRIPTION_Y + companionDescriptionLineCount(content) * 10 + COMPANION_DETAIL_TASK_BOX_GAP;
    }

    private int companionTaskChainBoxWidth(UiLayoutRect content) {
        return Math.max(60, content.width() - COMPANION_DETAIL_SCROLLBAR_WIDTH - 10);
    }

    private int companionTaskChainBoxHeight(UiLayoutRect content) {
        int textWidth = Math.max(24, companionTaskChainBoxWidth(content) - 16);
        int lineCount = 0;
        for (Component entry : companionTaskChainDisplayLines(selectedCharacter)) {
            lineCount += Math.max(1, font.split(entry, textWidth).size());
        }
        return Math.max(COMPANION_DETAIL_TASK_BOX_MIN_HEIGHT, 8 + 10 + 6 + lineCount * 10 + 8);
    }

    private int companionInventoryBoxOffset(UiLayoutRect content) {
        return companionTaskChainBoxOffset(content) + companionTaskChainBoxHeight(content) + COMPANION_DETAIL_INVENTORY_BOX_GAP;
    }

    private int companionInventoryBoxHeight(UiLayoutRect content) {
        int slot = companionInventorySlotSize(content);
        int equipmentHeight = slot * 5 + COMPANION_INVENTORY_EQUIPMENT_GAP * 4 + 4;
        int gridHeight = slot * 4 + COMPANION_INVENTORY_SLOT_GAP * 2 + COMPANION_INVENTORY_HOTBAR_GAP;
        return Math.max(COMPANION_DETAIL_INVENTORY_BOX_MIN_HEIGHT, 24 + Math.max(equipmentHeight, gridHeight) + 10);
    }

    private int companionMemoryBoxOffset(UiLayoutRect content) {
        return companionInventoryBoxOffset(content) + companionInventoryBoxHeight(content) + COMPANION_DETAIL_MEMORY_BOX_GAP;
    }

    private int companionMemoryBoxHeight(UiLayoutRect content) {
        int textWidth = Math.max(24, companionTaskChainBoxWidth(content) - 16);
        int lineCount = Math.max(1, font.split(Component.translatable("screen.player2npc.ui.companion_detail.memory_intro"), textWidth).size());
        lineCount += 1;
        for (Component entry : companionMemoryFactDisplayLines(selectedCharacter)) {
            lineCount += Math.max(1, font.split(entry, textWidth).size());
        }
        return Math.max(COMPANION_DETAIL_MEMORY_BOX_MIN_HEIGHT, 8 + 10 + 6 + lineCount * 10 + 8);
    }

    private int companionAdditionalPromptBoxOffset(UiLayoutRect content) {
        return companionMemoryBoxOffset(content) + companionMemoryBoxHeight(content) + COMPANION_DETAIL_ADDITIONAL_PROMPT_BOX_GAP;
    }

    private int companionAdditionalPromptBoxHeight(UiLayoutRect content) {
        return Math.max(COMPANION_DETAIL_ADDITIONAL_PROMPT_BOX_MIN_HEIGHT,
                24 + ADDITIONAL_PROMPT_INPUT_HEIGHT + 10 + ADDITIONAL_PROMPT_SAVE_HEIGHT + 8);
    }

    private int companionInventorySlotSize(UiLayoutRect content) {
        int available = Math.max(100, companionTaskChainBoxWidth(content) - 16);
        int equipmentWidth = 42;
        int gridAvailable = Math.max(80, available - equipmentWidth - 12);
        int fit = (gridAvailable - 8 * COMPANION_INVENTORY_SLOT_GAP) / 9;
        return clampInt(fit, 16, 20);
    }

    private int companionInventoryEquipmentLabelWidth(UiLayoutRect content, int slot) {
        int available = Math.max(100, companionTaskChainBoxWidth(content) - 16);
        int gridWidth = 9 * slot + 8 * COMPANION_INVENTORY_SLOT_GAP;
        int labelWidth = available - slot - 12 - gridWidth - 8;
        return labelWidth >= 28 ? Math.min(44, labelWidth) : 0;
    }

    private int companionDetailScrollMax(UiLayoutRect content, UiLayoutRect viewport) {
        return Math.max(0, companionDetailContentHeight(content) - viewport.height());
    }

    private UiLayoutRect companionDetailScrollbarTrack(UiLayoutRect viewport) {
        return new UiLayoutRect(viewport.right() - COMPANION_DETAIL_SCROLLBAR_WIDTH, viewport.y(),
                COMPANION_DETAIL_SCROLLBAR_WIDTH, viewport.height());
    }

    private UiLayoutRect companionDetailScrollbarThumb(UiLayoutRect content, UiLayoutRect viewport) {
        UiLayoutRect track = companionDetailScrollbarTrack(viewport);
        int contentHeight = companionDetailContentHeight(content);
        if (contentHeight <= viewport.height() || track.height() <= 0) {
            return new UiLayoutRect(track.x(), track.y(), track.width(), 0);
        }
        int thumbHeight = Math.max(12, track.height() * viewport.height() / contentHeight);
        int maxScroll = Math.max(1, contentHeight - viewport.height());
        int thumbTravel = Math.max(1, track.height() - thumbHeight);
        int clampedScroll = clampInt(companionDetailScroll, 0, maxScroll);
        int thumbY = track.y() + (int) ((long) thumbTravel * clampedScroll / maxScroll);
        return new UiLayoutRect(track.x(), thumbY, track.width(), thumbHeight);
    }

    private boolean isInside(UiLayoutRect rect, double mouseX, double mouseY) {
        return mouseX >= rect.x() && mouseX < rect.right() && mouseY >= rect.y() && mouseY < rect.bottom();
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private boolean scrollCompanionList(double mouseX, double mouseY, double delta) {
        if (selectedTab != Player2NpcTab.COMPANIONS || selectedCharacter != null) {
            return false;
        }
        UiLayoutRect list = companionListPane(content().inset(12));
        if (!isInside(list, mouseX, mouseY)) {
            return false;
        }
        int maxScroll = companionScrollMax(list);
        if (maxScroll <= 0) {
            return false;
        }
        int step = (int) Math.round(delta * COMPANION_SCROLL_WHEEL_STEP);
        if (step == 0) {
            step = delta > 0.0D ? 1 : -1;
        }
        companionScroll = clampInt(companionScroll - step, 0, maxScroll);
        rebuildHubWidgets();
        return true;
    }

    private boolean scrollCompanionDetail(double mouseX, double mouseY, double delta) {
        if (selectedTab != Player2NpcTab.COMPANIONS || selectedCharacter == null) {
            return false;
        }
        UiLayoutRect c = content().inset(12);
        UiLayoutRect viewport = companionDetailViewport(c);
        if (!isInside(viewport, mouseX, mouseY)) {
            return false;
        }
        int maxScroll = companionDetailScrollMax(c, viewport);
        if (maxScroll <= 0) {
            return false;
        }
        int step = (int) Math.round(delta * COMPANION_DETAIL_SCROLL_WHEEL_STEP);
        if (step == 0) {
            step = delta > 0.0D ? 1 : -1;
        }
        companionDetailScroll = clampInt(companionDetailScroll - step, 0, maxScroll);
        return true;
    }

    private boolean scrollNavList(double mouseX, double mouseY, double delta) {
        UiLayoutRect navViewport = navViewport(navPane(frame()));
        if (!isInside(navViewport, mouseX, mouseY)) {
            return false;
        }
        int maxScroll = navScrollMax(navViewport, Player2NpcTab.visibleTabs().length);
        if (maxScroll <= 0) {
            return false;
        }
        int step = (int) Math.round(delta * NAV_SCROLL_WHEEL_STEP);
        if (step == 0) {
            step = delta > 0.0D ? 1 : -1;
        }
        navScroll = clampInt(navScroll - step, 0, maxScroll);
        rebuildHubWidgets();
        return true;
    }

    private boolean scrollBudgetPane(double mouseX, double mouseY, double delta) {
        if (selectedTab != Player2NpcTab.BUDGET) {
            return false;
        }
        UiLayoutRect viewport = budgetViewport(content().inset(14));
        if (!isInside(viewport, mouseX, mouseY)) {
            return false;
        }
        int maxScroll = budgetScrollMax(viewport);
        if (maxScroll <= 0) {
            return false;
        }
        int step = (int) Math.round(delta * BUDGET_SCROLL_WHEEL_STEP);
        if (step == 0) {
            step = delta > 0.0D ? 1 : -1;
        }
        budgetScroll = clampInt(budgetScroll - step, 0, maxScroll);
        rebuildHubWidgets();
        return true;
    }

    private boolean scrollAccessListPane(double mouseX, double mouseY, double delta) {
        if (selectedTab != Player2NpcTab.ACCESS_LISTS) {
            return false;
        }
        UiLayoutRect viewport = accessListViewport(content().inset(14));
        if (!isInside(viewport, mouseX, mouseY)) {
            return false;
        }
        int maxScroll = accessListScrollMax(viewport);
        if (maxScroll <= 0) {
            return false;
        }
        int step = (int) Math.round(delta * ACCESS_LIST_SCROLL_WHEEL_STEP);
        if (step == 0) {
            step = delta > 0.0D ? 1 : -1;
        }
        accessListScroll = clampInt(accessListScroll - step, 0, maxScroll);
        rebuildHubWidgets();
        return true;
    }

    private boolean scrollModIntelligencePane(double mouseX, double mouseY, double delta) {
        if (selectedTab != Player2NpcTab.MOD_INTELLIGENCE) {
            return false;
        }
        UiLayoutRect viewport = modIntelligenceViewport(content().inset(14));
        if (!isInside(viewport, mouseX, mouseY)) {
            return false;
        }
        Map<String, String> fields = snapshots.getOrDefault(Player2NpcTab.MOD_INTELLIGENCE, Map.of());
        int maxScroll = modIntelligenceScrollMax(viewport, fields);
        if (maxScroll <= 0) {
            return false;
        }
        int step = (int) Math.round(delta * MOD_INTELLIGENCE_SCROLL_WHEEL_STEP);
        if (step == 0) {
            step = delta > 0.0D ? 1 : -1;
        }
        modIntelligenceScroll = clampInt(modIntelligenceScroll - step, 0, maxScroll);
        rebuildHubWidgets();
        return true;
    }

    private boolean scrollAiMemoryPane(double mouseX, double mouseY, double delta) {
        if (selectedTab != Player2NpcTab.AI_MEMORY) {
            return false;
        }
        UiLayoutRect viewport = aiMemoryViewport(content().inset(14));
        if (!isInside(viewport, mouseX, mouseY)) {
            return false;
        }
        int maxScroll = aiMemoryScrollMax(viewport);
        if (maxScroll <= 0) {
            return false;
        }
        int step = (int) Math.round(delta * AI_MEMORY_SCROLL_WHEEL_STEP);
        if (step == 0) {
            step = delta > 0.0D ? 1 : -1;
        }
        aiMemoryScroll = clampInt(aiMemoryScroll - step, 0, maxScroll);
        rebuildHubWidgets();
        return true;
    }


    private boolean scrollProfilesPane(double mouseX, double mouseY, double delta) {
        if (selectedTab != Player2NpcTab.PROFILES_MODELS) {
            return false;
        }
        UiLayoutRect viewport = profilesViewport(content().inset(14));
        if (!isInside(viewport, mouseX, mouseY)) {
            return false;
        }
        int maxScroll = profilesScrollMax(viewport);
        if (maxScroll <= 0) {
            return false;
        }
        int step = (int) Math.round(delta * PROFILES_SCROLL_WHEEL_STEP);
        if (step == 0) {
            step = delta > 0.0D ? 1 : -1;
        }
        profilesScroll = clampInt(profilesScroll - step, 0, maxScroll);
        rebuildHubWidgets();
        return true;
    }


    private boolean scrollGettingStartedPane(double mouseX, double mouseY, double delta) {
        if (selectedTab != Player2NpcTab.GETTING_STARTED) {
            return false;
        }
        UiLayoutRect viewport = gettingStartedViewport(content().inset(14));
        if (!isInside(viewport, mouseX, mouseY)) {
            return false;
        }
        int maxScroll = gettingStartedScrollMax(viewport);
        if (maxScroll <= 0) {
            return false;
        }
        int step = (int) Math.round(delta * GETTING_STARTED_SCROLL_WHEEL_STEP);
        if (step == 0) {
            step = delta > 0.0D ? 1 : -1;
        }
        gettingStartedScroll = clampInt(gettingStartedScroll - step, 0, maxScroll);
        return true;
    }

    private boolean beginGettingStartedScrollbarDrag(double mouseX, double mouseY) {
        if (selectedTab != Player2NpcTab.GETTING_STARTED) {
            return false;
        }
        UiLayoutRect viewport = gettingStartedViewport(content().inset(14));
        int maxScroll = gettingStartedScrollMax(viewport);
        if (maxScroll <= 0) {
            return false;
        }
        UiLayoutRect track = gettingStartedScrollbarTrack(viewport);
        if (!isInside(track, mouseX, mouseY)) {
            return false;
        }
        UiLayoutRect thumb = gettingStartedScrollbarThumb(viewport);
        draggingGettingStartedScrollbar = true;
        gettingStartedScrollbarGrabOffset = isInside(thumb, mouseX, mouseY)
                ? (int) Math.round(mouseY - thumb.y())
                : thumb.height() / 2;
        updateGettingStartedScrollFromThumb(mouseY);
        return true;
    }

    private void updateGettingStartedScrollFromThumb(double mouseY) {
        UiLayoutRect viewport = gettingStartedViewport(content().inset(14));
        UiLayoutRect track = gettingStartedScrollbarTrack(viewport);
        int maxScroll = gettingStartedScrollMax(viewport);
        if (maxScroll <= 0) {
            gettingStartedScroll = 0;
            return;
        }
        int thumbHeight = gettingStartedScrollbarThumb(viewport).height();
        int thumbTravel = Math.max(1, track.height() - thumbHeight);
        int thumbTop = clampInt((int) Math.round(mouseY) - gettingStartedScrollbarGrabOffset, track.y(), track.y() + thumbTravel);
        gettingStartedScroll = clampInt((int) Math.round((double) (thumbTop - track.y()) * maxScroll / thumbTravel), 0, maxScroll);
    }

    private boolean beginBudgetScrollbarDrag(double mouseX, double mouseY) {
        if (selectedTab != Player2NpcTab.BUDGET) {
            return false;
        }
        UiLayoutRect viewport = budgetViewport(content().inset(14));
        int maxScroll = budgetScrollMax(viewport);
        if (maxScroll <= 0) {
            return false;
        }
        UiLayoutRect track = budgetScrollbarTrack(viewport);
        if (!isInside(track, mouseX, mouseY)) {
            return false;
        }
        UiLayoutRect thumb = budgetScrollbarThumb(viewport);
        draggingBudgetScrollbar = true;
        budgetScrollbarGrabOffset = isInside(thumb, mouseX, mouseY)
                ? (int) Math.round(mouseY - thumb.y())
                : thumb.height() / 2;
        updateBudgetScrollFromThumb(mouseY);
        return true;
    }

    private void updateBudgetScrollFromThumb(double mouseY) {
        UiLayoutRect viewport = budgetViewport(content().inset(14));
        UiLayoutRect track = budgetScrollbarTrack(viewport);
        int maxScroll = budgetScrollMax(viewport);
        if (maxScroll <= 0) {
            budgetScroll = 0;
            return;
        }
        int thumbHeight = budgetScrollbarThumb(viewport).height();
        int thumbTravel = Math.max(1, track.height() - thumbHeight);
        int thumbTop = clampInt((int) Math.round(mouseY) - budgetScrollbarGrabOffset, track.y(), track.y() + thumbTravel);
        budgetScroll = clampInt((int) Math.round((double) (thumbTop - track.y()) * maxScroll / thumbTravel), 0, maxScroll);
        rebuildHubWidgets();
    }

    private boolean beginAccessListScrollbarDrag(double mouseX, double mouseY) {
        if (selectedTab != Player2NpcTab.ACCESS_LISTS) {
            return false;
        }
        UiLayoutRect viewport = accessListViewport(content().inset(14));
        int maxScroll = accessListScrollMax(viewport);
        if (maxScroll <= 0) {
            return false;
        }
        UiLayoutRect track = accessListScrollbarTrack(viewport);
        if (!isInside(track, mouseX, mouseY)) {
            return false;
        }
        UiLayoutRect thumb = accessListScrollbarThumb(viewport);
        draggingAccessListScrollbar = true;
        accessListScrollbarGrabOffset = isInside(thumb, mouseX, mouseY)
                ? (int) Math.round(mouseY - thumb.y())
                : thumb.height() / 2;
        updateAccessListScrollFromThumb(mouseY);
        return true;
    }

    private void updateAccessListScrollFromThumb(double mouseY) {
        UiLayoutRect viewport = accessListViewport(content().inset(14));
        UiLayoutRect track = accessListScrollbarTrack(viewport);
        int maxScroll = accessListScrollMax(viewport);
        if (maxScroll <= 0) {
            accessListScroll = 0;
            return;
        }
        int thumbHeight = accessListScrollbarThumb(viewport).height();
        int thumbTravel = Math.max(1, track.height() - thumbHeight);
        int thumbTop = clampInt((int) Math.round(mouseY) - accessListScrollbarGrabOffset, track.y(), track.y() + thumbTravel);
        accessListScroll = clampInt((int) Math.round((double) (thumbTop - track.y()) * maxScroll / thumbTravel), 0, maxScroll);
        rebuildHubWidgets();
    }

    private boolean beginModIntelligenceScrollbarDrag(double mouseX, double mouseY) {
        if (selectedTab != Player2NpcTab.MOD_INTELLIGENCE) {
            return false;
        }
        UiLayoutRect viewport = modIntelligenceViewport(content().inset(14));
        Map<String, String> fields = snapshots.getOrDefault(Player2NpcTab.MOD_INTELLIGENCE, Map.of());
        int maxScroll = modIntelligenceScrollMax(viewport, fields);
        if (maxScroll <= 0) {
            return false;
        }
        UiLayoutRect track = modIntelligenceScrollbarTrack(viewport);
        if (!isInside(track, mouseX, mouseY)) {
            return false;
        }
        UiLayoutRect thumb = modIntelligenceScrollbarThumb(viewport, fields);
        draggingModIntelligenceScrollbar = true;
        modIntelligenceScrollbarGrabOffset = isInside(thumb, mouseX, mouseY)
                ? (int) Math.round(mouseY - thumb.y())
                : thumb.height() / 2;
        updateModIntelligenceScrollFromThumb(mouseY);
        return true;
    }

    private void updateModIntelligenceScrollFromThumb(double mouseY) {
        UiLayoutRect viewport = modIntelligenceViewport(content().inset(14));
        Map<String, String> fields = snapshots.getOrDefault(Player2NpcTab.MOD_INTELLIGENCE, Map.of());
        UiLayoutRect track = modIntelligenceScrollbarTrack(viewport);
        int maxScroll = modIntelligenceScrollMax(viewport, fields);
        if (maxScroll <= 0) {
            modIntelligenceScroll = 0;
            return;
        }
        int thumbHeight = modIntelligenceScrollbarThumb(viewport, fields).height();
        int thumbTravel = Math.max(1, track.height() - thumbHeight);
        int thumbTop = clampInt((int) Math.round(mouseY) - modIntelligenceScrollbarGrabOffset,
                track.y(), track.y() + thumbTravel);
        modIntelligenceScroll = clampInt(
                (int) Math.round((double) (thumbTop - track.y()) * maxScroll / thumbTravel), 0, maxScroll);
        rebuildHubWidgets();
    }

    private boolean beginAiMemoryScrollbarDrag(double mouseX, double mouseY) {
        if (selectedTab != Player2NpcTab.AI_MEMORY) {
            return false;
        }
        UiLayoutRect viewport = aiMemoryViewport(content().inset(14));
        int maxScroll = aiMemoryScrollMax(viewport);
        if (maxScroll <= 0) {
            return false;
        }
        UiLayoutRect track = aiMemoryScrollbarTrack(viewport);
        if (!isInside(track, mouseX, mouseY)) {
            return false;
        }
        UiLayoutRect thumb = aiMemoryScrollbarThumb(viewport);
        draggingAiMemoryScrollbar = true;
        aiMemoryScrollbarGrabOffset = isInside(thumb, mouseX, mouseY)
                ? (int) Math.round(mouseY - thumb.y())
                : thumb.height() / 2;
        updateAiMemoryScrollFromThumb(mouseY);
        return true;
    }

    private void updateAiMemoryScrollFromThumb(double mouseY) {
        UiLayoutRect viewport = aiMemoryViewport(content().inset(14));
        UiLayoutRect track = aiMemoryScrollbarTrack(viewport);
        int maxScroll = aiMemoryScrollMax(viewport);
        if (maxScroll <= 0) {
            aiMemoryScroll = 0;
            return;
        }
        int thumbHeight = aiMemoryScrollbarThumb(viewport).height();
        int thumbTravel = Math.max(1, track.height() - thumbHeight);
        int thumbTop = clampInt((int) Math.round(mouseY) - aiMemoryScrollbarGrabOffset,
                track.y(), track.y() + thumbTravel);
        aiMemoryScroll = clampInt(
                (int) Math.round((double) (thumbTop - track.y()) * maxScroll / thumbTravel), 0, maxScroll);
        rebuildHubWidgets();
    }


    private boolean beginProfilesScrollbarDrag(double mouseX, double mouseY) {
        if (selectedTab != Player2NpcTab.PROFILES_MODELS) {
            return false;
        }
        UiLayoutRect viewport = profilesViewport(content().inset(14));
        int maxScroll = profilesScrollMax(viewport);
        if (maxScroll <= 0) {
            return false;
        }
        UiLayoutRect track = profilesScrollbarTrack(viewport);
        if (!isInside(track, mouseX, mouseY)) {
            return false;
        }
        UiLayoutRect thumb = profilesScrollbarThumb(viewport);
        draggingProfilesScrollbar = true;
        profilesScrollbarGrabOffset = isInside(thumb, mouseX, mouseY)
                ? (int) Math.round(mouseY - thumb.y())
                : thumb.height() / 2;
        updateProfilesScrollFromThumb(mouseY);
        return true;
    }

    private void updateProfilesScrollFromThumb(double mouseY) {
        UiLayoutRect viewport = profilesViewport(content().inset(14));
        UiLayoutRect track = profilesScrollbarTrack(viewport);
        int maxScroll = profilesScrollMax(viewport);
        if (maxScroll <= 0) {
            profilesScroll = 0;
            return;
        }
        int thumbHeight = profilesScrollbarThumb(viewport).height();
        int thumbTravel = Math.max(1, track.height() - thumbHeight);
        int thumbTop = clampInt((int) Math.round(mouseY) - profilesScrollbarGrabOffset,
                track.y(), track.y() + thumbTravel);
        profilesScroll = clampInt(
                (int) Math.round((double) (thumbTop - track.y()) * maxScroll / thumbTravel), 0, maxScroll);
        rebuildHubWidgets();
    }


    private boolean beginCompanionScrollbarDrag(double mouseX, double mouseY) {
        if (selectedTab != Player2NpcTab.COMPANIONS || selectedCharacter != null) {
            return false;
        }
        UiLayoutRect list = companionListPane(content().inset(12));
        int maxScroll = companionScrollMax(list);
        if (maxScroll <= 0) {
            return false;
        }
        UiLayoutRect track = companionScrollbarTrack(list);
        if (!isInside(track, mouseX, mouseY)) {
            return false;
        }
        UiLayoutRect thumb = companionScrollbarThumb(list);
        draggingCompanionScrollbar = true;
        companionScrollbarGrabOffset = isInside(thumb, mouseX, mouseY)
                ? (int) Math.round(mouseY - thumb.y())
                : thumb.height() / 2;
        updateCompanionScrollFromThumb(mouseY);
        return true;
    }

    private void updateCompanionScrollFromThumb(double mouseY) {
        UiLayoutRect list = companionListPane(content().inset(12));
        UiLayoutRect track = companionScrollbarTrack(list);
        int maxScroll = companionScrollMax(list);
        if (maxScroll <= 0) {
            companionScroll = 0;
            return;
        }
        int thumbHeight = companionScrollbarThumb(list).height();
        int thumbTravel = Math.max(1, track.height() - thumbHeight);
        int thumbTop = clampInt((int) Math.round(mouseY) - companionScrollbarGrabOffset, track.y(), track.y() + thumbTravel);
        companionScroll = clampInt((int) Math.round((double) (thumbTop - track.y()) * maxScroll / thumbTravel), 0, maxScroll);
        rebuildHubWidgets();
    }

    private boolean beginCompanionDetailScrollbarDrag(double mouseX, double mouseY) {
        if (selectedTab != Player2NpcTab.COMPANIONS || selectedCharacter == null) {
            return false;
        }
        UiLayoutRect c = content().inset(12);
        UiLayoutRect viewport = companionDetailViewport(c);
        int maxScroll = companionDetailScrollMax(c, viewport);
        if (maxScroll <= 0) {
            return false;
        }
        UiLayoutRect track = companionDetailScrollbarTrack(viewport);
        if (!isInside(track, mouseX, mouseY)) {
            return false;
        }
        UiLayoutRect thumb = companionDetailScrollbarThumb(c, viewport);
        draggingCompanionDetailScrollbar = true;
        companionDetailScrollbarGrabOffset = isInside(thumb, mouseX, mouseY)
                ? (int) Math.round(mouseY - thumb.y())
                : thumb.height() / 2;
        updateCompanionDetailScrollFromThumb(mouseY);
        return true;
    }

    private void updateCompanionDetailScrollFromThumb(double mouseY) {
        UiLayoutRect c = content().inset(12);
        UiLayoutRect viewport = companionDetailViewport(c);
        UiLayoutRect track = companionDetailScrollbarTrack(viewport);
        int maxScroll = companionDetailScrollMax(c, viewport);
        if (maxScroll <= 0) {
            companionDetailScroll = 0;
            return;
        }
        int thumbHeight = companionDetailScrollbarThumb(c, viewport).height();
        int thumbTravel = Math.max(1, track.height() - thumbHeight);
        int thumbTop = clampInt((int) Math.round(mouseY) - companionDetailScrollbarGrabOffset, track.y(), track.y() + thumbTravel);
        companionDetailScroll = clampInt((int) Math.round((double) (thumbTop - track.y()) * maxScroll / thumbTravel), 0, maxScroll);
    }

    private boolean beginNavScrollbarDrag(double mouseX, double mouseY) {
        UiLayoutRect navViewport = navViewport(navPane(frame()));
        int maxScroll = navScrollMax(navViewport, Player2NpcTab.visibleTabs().length);
        if (maxScroll <= 0) {
            return false;
        }
        UiLayoutRect track = navScrollbarTrack(navViewport);
        if (!isInside(track, mouseX, mouseY)) {
            return false;
        }
        UiLayoutRect thumb = navScrollbarThumb(navViewport);
        draggingNavScrollbar = true;
        navScrollbarGrabOffset = isInside(thumb, mouseX, mouseY)
                ? (int) Math.round(mouseY - thumb.y())
                : thumb.height() / 2;
        updateNavScrollFromThumb(mouseY);
        return true;
    }

    private void updateNavScrollFromThumb(double mouseY) {
        UiLayoutRect navViewport = navViewport(navPane(frame()));
        UiLayoutRect track = navScrollbarTrack(navViewport);
        int maxScroll = navScrollMax(navViewport, Player2NpcTab.visibleTabs().length);
        if (maxScroll <= 0) {
            navScroll = 0;
            return;
        }
        int thumbHeight = navScrollbarThumb(navViewport).height();
        int thumbTravel = Math.max(1, track.height() - thumbHeight);
        int thumbTop = clampInt((int) Math.round(mouseY) - navScrollbarGrabOffset, track.y(), track.y() + thumbTravel);
        navScroll = clampInt((int) Math.round((double) (thumbTop - track.y()) * maxScroll / thumbTravel), 0, maxScroll);
        rebuildHubWidgets();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && beginNavScrollbarDrag(mouseX, mouseY)) {
            return true;
        }
        if (isSettingsView() && settingsPanel != null
                && settingsPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (isSettingsView()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button == 0 && clickGettingStartedActionButton(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && beginGettingStartedScrollbarDrag(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && beginBudgetScrollbarDrag(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && beginAccessListScrollbarDrag(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && beginModIntelligenceScrollbarDrag(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && beginAiMemoryScrollbarDrag(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && beginProfilesScrollbarDrag(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && beginCompanionDetailScrollbarDrag(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && beginCompanionScrollbarDrag(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingNavScrollbar) {
            updateNavScrollFromThumb(mouseY);
            return true;
        }
        if (isSettingsView() && settingsPanel != null
                && settingsPanel.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (isSettingsView()) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        if (button == 0 && draggingGettingStartedScrollbar) {
            updateGettingStartedScrollFromThumb(mouseY);
            return true;
        }
        if (button == 0 && draggingBudgetScrollbar) {
            updateBudgetScrollFromThumb(mouseY);
            return true;
        }
        if (button == 0 && draggingAccessListScrollbar) {
            updateAccessListScrollFromThumb(mouseY);
            return true;
        }
        if (button == 0 && draggingModIntelligenceScrollbar) {
            updateModIntelligenceScrollFromThumb(mouseY);
            return true;
        }
        if (button == 0 && draggingAiMemoryScrollbar) {
            updateAiMemoryScrollFromThumb(mouseY);
            return true;
        }
        if (button == 0 && draggingProfilesScrollbar) {
            updateProfilesScrollFromThumb(mouseY);
            return true;
        }
        if (button == 0 && draggingCompanionDetailScrollbar) {
            updateCompanionDetailScrollFromThumb(mouseY);
            return true;
        }
        if (button == 0 && draggingCompanionScrollbar) {
            updateCompanionScrollFromThumb(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingNavScrollbar) {
            draggingNavScrollbar = false;
            return true;
        }
        if (settingsPanel != null && settingsPanel.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (isSettingsView()) {
            return super.mouseReleased(mouseX, mouseY, button);
        }
        if (button == 0 && draggingGettingStartedScrollbar) {
            draggingGettingStartedScrollbar = false;
            return true;
        }
        if (button == 0 && draggingBudgetScrollbar) {
            draggingBudgetScrollbar = false;
            return true;
        }
        if (button == 0 && draggingAccessListScrollbar) {
            draggingAccessListScrollbar = false;
            return true;
        }
        if (button == 0 && draggingModIntelligenceScrollbar) {
            draggingModIntelligenceScrollbar = false;
            return true;
        }
        if (button == 0 && draggingAiMemoryScrollbar) {
            draggingAiMemoryScrollbar = false;
            return true;
        }
        if (button == 0 && draggingProfilesScrollbar) {
            draggingProfilesScrollbar = false;
            return true;
        }
        if (button == 0 && draggingCompanionDetailScrollbar) {
            draggingCompanionDetailScrollbar = false;
            return true;
        }
        if (button == 0 && draggingCompanionScrollbar) {
            draggingCompanionScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollNavList(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (isSettingsView() && settingsPanel != null
                && settingsPanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        if (isSettingsView()) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (scrollGettingStartedPane(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (scrollBudgetPane(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (scrollAccessListPane(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (scrollModIntelligencePane(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (scrollAiMemoryPane(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (scrollProfilesPane(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (scrollCompanionDetail(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (scrollCompanionList(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private Component boolValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return Component.translatable("screen.player2npc.ui.value.unavailable");
        }
        return Component.translatable(Boolean.parseBoolean(raw) ? "screen.player2npc.ui.value.on" : "screen.player2npc.ui.value.off");
    }

    private Component listModeValue(String raw) {
        if ("blacklist".equals(raw)) {
            return Component.translatable("screen.player2npc.ui.value.blacklist");
        }
        if ("whitelist".equals(raw)) {
            return Component.translatable("screen.player2npc.ui.value.whitelist");
        }
        return Component.translatable("screen.player2npc.ui.value.unavailable");
    }

    private Component sourceValue(String raw) {
        if ("server".equals(raw)) {
            return Component.translatable("screen.player2npc.ui.value.source_server");
        }
        if ("player".equals(raw)) {
            return Component.translatable("screen.player2npc.ui.value.source_player");
        }
        return Component.translatable("screen.player2npc.ui.value.unavailable");
    }

    private boolean drawSnapshotState(GuiGraphics graphics, UiLayoutRect c, Map<String, String> fields) {
        Component state = snapshotState(fields);
        if (state == null) {
            return false;
        }
        graphics.drawString(font, state, c.x(), c.y() + 24, Player2NpcUiTheme.WARN, false);
        return true;
    }

    private boolean canShowControls(Map<String, String> fields) {
        return !fields.isEmpty() && snapshotState(fields) == null;
    }

    private void drawActionResult(GuiGraphics graphics, UiLayoutRect c, Map<String, String> fields) {
        String result = fields.get("actionResult");
        if (result == null || result.isBlank()) {
            return;
        }
        Component message = actionResultMessage(result);
        int maxWidth = Math.min(170, Math.max(60, c.width() / 2));
        drawClampedText(graphics, message, c.right() - maxWidth, c.y(), maxWidth, actionResultColor(result));
    }

    private void drawAdditionalPromptFeedback(GuiGraphics graphics, int x, int y, int width, int height) {
        if (additionalPromptActionResultTicks <= 0 || additionalPromptActionResult.isBlank()) {
            return;
        }
        Component message = actionResultMessage(additionalPromptActionResult);
        int saveX = x + width - 8 - ADDITIONAL_PROMPT_SAVE_WIDTH;
        int messageRight = saveX - 8;
        int messageX = Math.max(x + 8, messageRight - font.width(message));
        int messageY = y + height - 8 - ADDITIONAL_PROMPT_SAVE_HEIGHT + (ADDITIONAL_PROMPT_SAVE_HEIGHT - 8) / 2;
        graphics.drawString(font, message, messageX, messageY, actionResultColor(additionalPromptActionResult), false);
    }

    private Component actionResultMessage(String result) {
        return switch (result) {
            case "ok" -> Component.translatable("screen.player2npc.ui.action.saved");
            case "enrichment_started" -> Component.translatable("screen.player2npc.ui.mod_intelligence.feedback.enrichment_started");
            case "enrichment_queued" -> Component.translatable("screen.player2npc.ui.mod_intelligence.feedback.enrichment_queued");
            case "rebuild_started" -> Component.translatable("screen.player2npc.ui.mod_intelligence.feedback.rebuild_started");
            case "query_ready" -> Component.translatable("screen.player2npc.ui.mod_intelligence.feedback.query_ready");
            case "op_required" -> Component.translatable("screen.player2npc.ui.action.op_required");
            case "save_failed" -> Component.translatable("screen.player2npc.ui.action.save_failed");
            case "load_failed" -> Component.translatable("screen.player2npc.ui.settings.status.load_failed");
            case "saved_live_pending" -> Component.translatable(
                    "screen.player2npc.ui.settings.status.saved_live_pending");
            case "saved_live_apply_failed" -> Component.translatable(
                    "screen.player2npc.ui.settings.status.saved_live_apply_failed");
            case "invalid_list" -> Component.translatable("screen.player2npc.ui.action.invalid_list");
            case "invalid_value" -> Component.translatable("screen.player2npc.ui.action.invalid_value");
            case "singleplayer_only" -> Component.translatable("screen.player2npc.ui.mod_intelligence.feedback.singleplayer_only");
            case "enrichment_queue_empty" -> Component.translatable("screen.player2npc.ui.mod_intelligence.feedback.queue_empty");
            case "enrichment_queue_unavailable" -> Component.translatable("screen.player2npc.ui.mod_intelligence.feedback.queue_unavailable");
            case "mod_intelligence_disabled" -> Component.translatable("screen.player2npc.ui.mod_intelligence.feedback.disabled");
            case "billing_unavailable" -> Component.translatable("screen.player2npc.ui.mod_intelligence.feedback.billing_unavailable");
            case "hard_budget_limit" -> Component.translatable("screen.player2npc.ui.mod_intelligence.feedback.hard_budget_limit");
            case "model_blacklist_invalid" -> Component.translatable("screen.player2npc.ui.mod_intelligence.feedback.model_blacklist_invalid");
            case "budget_required" -> Component.translatable("screen.player2npc.ui.mod_intelligence.feedback.budget_required");
            case "rebuild_running" -> Component.translatable("screen.player2npc.ui.mod_intelligence.feedback.rebuild_running");
            case "executor_unavailable" -> Component.translatable("screen.player2npc.ui.mod_intelligence.feedback.executor_unavailable");
            case "invalid_query" -> Component.translatable("screen.player2npc.ui.mod_intelligence.feedback.invalid_query");
            case "query_failed" -> Component.translatable("screen.player2npc.ui.mod_intelligence.feedback.query_failed");
            default -> Component.translatable("screen.player2npc.ui.action.failed");
        };
    }

    private int actionResultColor(String result) {
        return switch (result) {
            case "ok", "enrichment_started", "enrichment_queued", "rebuild_started", "query_ready" -> Player2NpcUiTheme.GOOD;
            default -> Player2NpcUiTheme.WARN;
        };
    }

    private void captureCompanionActionResult(Map<String, String> fields) {
        String result = fields.get("actionResult");
        if (result == null || result.isBlank()) {
            return;
        }
        additionalPromptActionResult = result;
        additionalPromptActionResultTicks = ADDITIONAL_PROMPT_FEEDBACK_TICKS;
    }

    private void clearAdditionalPromptFeedback() {
        additionalPromptActionResult = "";
        additionalPromptActionResultTicks = 0;
    }

    private boolean isAdditionalPromptInputFocused() {
        return selectedTab == Player2NpcTab.COMPANIONS
                && selectedCharacter != null
                && additionalPromptInput != null
                && additionalPromptInput.isFocused();
    }

    private boolean isAccessListInputFocused() {
        if (selectedTab != Player2NpcTab.ACCESS_LISTS) {
            return false;
        }
        for (Player2NpcPromptBoxWidget input : accessListInputs.values()) {
            if (input != null && input.isFocused()) {
                return true;
            }
        }
        return false;
    }

    private String focusedAccessListKey() {
        if (selectedTab != Player2NpcTab.ACCESS_LISTS) {
            return null;
        }
        for (Map.Entry<String, Player2NpcPromptBoxWidget> entry : accessListInputs.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isFocused()) {
                return entry.getKey();
            }
        }
        return null;
    }

    private boolean isModIntelligenceQueryInputFocused() {
        return selectedTab == Player2NpcTab.MOD_INTELLIGENCE
                && modIntelligenceQueryInput != null
                && modIntelligenceQueryInput.isFocused();
    }

    private boolean isNumericInputFocused() {
        return focusedNumericInputKey() != null;
    }

    private boolean isSettingsInputFocused() {
        return isSettingsView() && settingsPanel != null && settingsPanel.isInputFocused();
    }

    private String focusedNumericInputKey() {
        for (Map.Entry<String, Player2NpcNumberInputWidget> entry : numericInputs.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isFocused()) {
                return entry.getKey();
            }
        }
        return null;
    }

    private Component snapshotState(Map<String, String> fields) {
        if (fields == null || fields.isEmpty()) {
            return Component.translatable("screen.player2npc.ui.state.loading");
        }
        String state = fields.get("state");
        if (state == null || state.isBlank() || "ready".equals(state)) {
            return null;
        }
        return switch (state) {
            case "version_mismatch" -> Component.translatable("screen.player2npc.ui.state.version_mismatch");
            case "no_server" -> Component.translatable("screen.player2npc.ui.state.no_server");
            case "unsupported_tab" -> Component.translatable("screen.player2npc.ui.state.unsupported_tab");
            case "unavailable" -> Component.translatable("screen.player2npc.ui.state.unavailable");
            default -> Component.translatable("screen.player2npc.ui.state.unavailable");
        };
    }

    private Component valueOrDash(String raw) {
        return raw == null || raw.isBlank() ? Component.translatable("screen.player2npc.ui.value.unavailable") : Component.literal(raw);
    }

    private Map<String, String> companionSnapshot() {
        return snapshots.getOrDefault(Player2NpcTab.COMPANIONS, Map.of());
    }

    private Map<String, CompoundTag> companionItemSnapshot() {
        return snapshotItemFields.getOrDefault(Player2NpcTab.COMPANIONS, Map.of());
    }

    private String additionalPromptForCharacter(Character character) {
        if (character == null || character.id() == null || character.id().isBlank()) {
            return "";
        }
        Map<String, String> snap = companionSnapshot();
        int activeIndex = activeCompanionIndex(character);
        if (activeIndex >= 0) {
            String activePrompt = snap.get("activeAdditionalPrompt." + activeIndex);
            if (activePrompt != null) {
                return clampAdditionalPromptDraft(activePrompt);
            }
        }
        int storedCount = intValue(snap.get("storedCount"));
        for (int i = 0; i < storedCount; i++) {
            if (character.id().equals(snap.get("storedCharacterId." + i))) {
                return clampAdditionalPromptDraft(snap.getOrDefault("storedAdditionalPrompt." + i, ""));
            }
        }
        return "";
    }

    private String clampAdditionalPromptDraft(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String normalized = raw.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder cleaned = new StringBuilder(Math.min(normalized.length(), ADDITIONAL_PROMPT_MAX_CHARS));
        for (int i = 0; i < normalized.length() && cleaned.length() < ADDITIONAL_PROMPT_MAX_CHARS; i++) {
            char c = normalized.charAt(i);
            if (java.lang.Character.isISOControl(c) && c != '\n' && c != '\t') {
                continue;
            }
            cleaned.append(c);
        }
        return cleaned.toString().trim();
    }

    private int activeCompanionIndex(Character character) {
        if (character == null || character.name() == null || character.name().isBlank()) {
            return -1;
        }
        Map<String, String> snap = companionSnapshot();
        int count = intValue(snap.get("activeCount"));
        for (int i = 0; i < count; i++) {
            if (character.name().equals(snap.get("activeName." + i))) {
                return i;
            }
        }
        return -1;
    }

    private ItemStack companionInventoryItem(String key) {
        CompoundTag tag = companionItemSnapshot().get(key);
        Minecraft client = Minecraft.getInstance();
        if (tag == null || tag.isEmpty() || client.level == null) {
            return ItemStack.EMPTY;
        }
        try {
            return ItemStack.parseOptional(client.level.registryAccess(), tag.copy());
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    private int armorInventorySlot(String slotKey) {
        return switch (slotKey) {
            case "head" -> 3;
            case "chest" -> 2;
            case "legs" -> 1;
            default -> 0;
        };
    }

    private boolean isCompanionActive(Character character) {
        return activeCompanionIndex(character) >= 0;
    }

    private CompanionVitals companionVitals(Character character) {
        return character == null ? null : activeCompanionVitals().get(character.name());
    }

    private Map<String, CompanionVitals> activeCompanionVitals() {
        Map<String, String> snap = snapshots.getOrDefault(Player2NpcTab.COMPANIONS, Map.of());
        Map<String, CompanionVitals> vitals = new LinkedHashMap<>();
        int count = intValue(snap.get("activeCount"));
        for (int i = 0; i < count; i++) {
            String name = snap.get("activeName." + i);
            if (name != null && !name.isBlank()) {
                vitals.put(name, new CompanionVitals(
                        intValue(snap.get("activeHealth." + i)),
                        Math.max(1, intValue(snap.get("activeMaxHealth." + i))),
                        clampInt(intValue(snap.get("activeFood." + i)), 0, 20)));
            }
        }
        return vitals;
    }

    private void ingestCompanionTaskStatuses(Map<String, String> fields) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        int count = intValue(fields.get("activeCount"));
        for (int i = 0; i < count; i++) {
            String name = fields.get("activeName." + i);
            String status = fields.get("activeTaskChain." + i);
            recordCompanionTaskStatus(name, status);
        }
    }

    private String companionTaskStatus(Character character) {
        if (character == null || character.name() == null || character.name().isBlank()) {
            return "";
        }
        Map<String, String> snap = snapshots.getOrDefault(Player2NpcTab.COMPANIONS, Map.of());
        int count = intValue(snap.get("activeCount"));
        for (int i = 0; i < count; i++) {
            if (character.name().equals(snap.get("activeName." + i))) {
                return snap.getOrDefault("activeTaskChain." + i, "");
            }
        }
        return "";
    }

    private void recordCompanionTaskStatus(String companionName, String status) {
        if (companionName == null || companionName.isBlank() || status == null || status.isBlank()) {
            return;
        }
        String previous = companionLastTaskStatus.get(companionName);
        if (status.equals(previous)) {
            return;
        }
        Deque<String> history = companionTaskHistory.computeIfAbsent(companionName, ignored -> new ArrayDeque<>());
        while (history.size() >= COMPANION_TASK_HISTORY_MAX_ENTRIES) {
            history.removeFirst();
        }
        history.addLast(status);
        companionLastTaskStatus.put(companionName, status);
    }

    private List<Component> companionTaskChainDisplayLines(Character character) {
        List<Component> lines = new ArrayList<>();
        if (character == null || character.name() == null) {
            lines.add(Component.translatable("screen.player2npc.ui.companion_detail.task_chain_empty"));
            return lines;
        }
        Deque<String> history = companionTaskHistory.get(character.name());
        if (history == null || history.isEmpty()) {
            lines.add(Component.translatable("screen.player2npc.ui.companion_detail.task_chain_empty"));
            return lines;
        }
        List<String> entries = new ArrayList<>(history);
        for (int i = entries.size() - 1; i >= 0; i--) {
            String[] rawLines = entries.get(i).split("\\n");
            for (String line : rawLines) {
                if (!line.isBlank()) {
                    lines.add(Component.literal(line));
                }
            }
        }
        return lines.isEmpty()
                ? List.of(Component.translatable("screen.player2npc.ui.companion_detail.task_chain_empty"))
                : lines;
    }

    private List<Component> companionMemoryFactDisplayLines(Character character) {
        int activeIndex = activeCompanionIndex(character);
        if (activeIndex < 0) {
            return List.of(Component.translatable("screen.player2npc.ui.companion_detail.memory_empty"));
        }
        String raw = companionSnapshot().get("activeMemoryFacts." + activeIndex);
        if (raw == null || raw.isBlank()) {
            return List.of(Component.translatable("screen.player2npc.ui.companion_detail.memory_empty"));
        }
        List<Component> lines = new ArrayList<>();
        for (String fact : raw.split("\\n")) {
            if (!fact.isBlank()) {
                lines.add(Component.literal("- " + fact.trim()));
            }
        }
        return lines.isEmpty()
                ? List.of(Component.translatable("screen.player2npc.ui.companion_detail.memory_empty"))
                : lines;
    }

    static Component companionStatus(boolean active) {
        return Component.translatable(active
                ? "screen.player2npc.ui.companions.status.connected"
                : "screen.player2npc.ui.companions.status.disconnected");
    }

    static record CompanionVitals(int health, int maxHealth, int foodLevel) {
    }

    private int intValue(String raw) {
        try {
            return raw == null || raw.isBlank() ? 0 : Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String clamp(String raw, int max) {
        if (raw == null) {
            return "";
        }
        return raw.length() <= max ? raw : raw.substring(0, max - 1) + "...";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

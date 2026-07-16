package com.goodbird.player2npc.companion.gui;

import com.goodbird.player2npc.client.gui.Player2NpcTab;
import com.goodbird.player2npc.companion.gui.settings.Player2NpcConfigService;
import com.goodbird.player2npc.companion.AutomatoneEntity;
import com.goodbird.player2npc.companion.BotBlacklistStorage;
import com.goodbird.player2npc.companion.BotWhitelistStorage;
import com.goodbird.player2npc.companion.CharacterStorageOperations;
import com.goodbird.player2npc.companion.CompanionManager;
import com.goodbird.player2npc.companion.OwnerUserSettingsStorage;
import com.goodbird.player2npc.companion.UserBlacklistStorage;
import com.goodbird.player2npc.companion.UserWhitelistStorage;
import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.agentic.AgenticRunRegistry;
import com.player2.playerengine.agentic.AgenticRunSnapshot;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.player2.playerengine.executor.BudgetFallbackBehavior;
import com.player2.playerengine.executor.StepExecution;
import com.player2.playerengine.memory.MemoryGraph;
import com.player2.playerengine.memory.MemoryDurableFactClassifier;
import com.player2.playerengine.memory.MemoryNode;
import com.player2.playerengine.memory.MemoryScope;
import com.player2.playerengine.memory.MemoryStore;
import com.player2.playerengine.memory.MemoryStoreRegistry;
import com.player2.playerengine.modintelligence.ModIntelligenceAdminService;
import com.player2.playerengine.modintelligence.ModIntelligenceService;
import com.player2.playerengine.modintelligence.ModIntelligenceStatus;
import com.player2.playerengine.modintelligence.query.CapabilityHit;
import com.player2.playerengine.player2api.Character;
import com.player2.playerengine.executor.BudgetTracker;
import com.player2.playerengine.player2api.BotLifecycleSettings;
import com.player2.playerengine.player2api.BotLifecycleSettingsResolver;
import com.player2.playerengine.player2api.BudgetThresholdsResolver;
import com.player2.playerengine.player2api.JoulesCache;
import com.player2.playerengine.player2api.PlayerBudgetConfigHolder;
import com.player2.playerengine.player2api.ProfileUrlResolver;
import com.player2.playerengine.player2api.AIPersistantData;
import com.player2.playerengine.player2api.config.BudgetThresholds;
import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import com.player2.playerengine.player2api.config.Player2ServerRuntimeConfig;
import com.player2.playerengine.player2api.config.PlayerBudgetConfig;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class Player2NpcGuiStateService {
    private static final int MEMORY_FACT_MAX_LINES = 6;
    private static final int MEMORY_FACT_MAX_CHARS = 160;
    private static final int MEMORY_FACTS_FIELD_MAX_CHARS = 900;
    private static final int ACCESS_LIST_MAX_ENTRIES = 128;
    private static final int ACCESS_LIST_MAX_TOKEN_LENGTH = 96;
    private static final int ACCESS_LIST_MAX_CHARACTER_LENGTH = 80;
    private static final int MOD_INTELLIGENCE_RESULT_TEXT_LIMIT = 180;
    private static final int BUDGET_CALLS_MAX = 999_999;
    private static final int BUDGET_JOULES_MAX = 999_999_999;
    private static final int BUDGET_WINDOW_MINUTES_MAX = 1_440;
    private static final int BUDGET_REFRESH_SECONDS_MIN = 60;
    private static final int BUDGET_REFRESH_SECONDS_MAX = 86_400;
    private static final int AI_MEMORY_CALLS_MAX = 1_000;
    private static final int PROFILE_NAME_MAX_LENGTH = 80;

    private Player2NpcGuiStateService() {
    }

    public static Player2NpcGuiSnapshot snapshot(ServerPlayer player, Player2NpcTab tab) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        LinkedHashMap<String, CompoundTag> itemFields = new LinkedHashMap<>();
        MinecraftServer server = player.getServer();
        if (server == null) {
            fields.put("state", "no_server");
            return new Player2NpcGuiSnapshot(tab, fields);
        }
        switch (tab) {
            case COMPANIONS -> companionSnapshot(server, player, fields, itemFields);
            case BEHAVIOR -> behaviorSnapshot(server, player, fields);
            case BUDGET -> budgetSnapshot(server, player, fields);
            case ACCESS_LISTS -> accessListSnapshot(server, player, fields);
            case MOD_INTELLIGENCE -> modIntelligenceSnapshot(server, player, fields);
            case AI_MEMORY -> aiMemorySnapshot(player, fields);
            case PROFILES_MODELS -> profileSnapshot(server, player, fields);
            case GETTING_STARTED -> gettingStartedSnapshot(fields);
            case AUTOMATION_ITEMS -> fields.put("state", "ready");
        }
        Player2NpcConfigService.appendSnapshot(server, player, tab, fields);
        return new Player2NpcGuiSnapshot(tab, fields, itemFields);
    }

    public static GuiActionResult applyUpdate(ServerPlayer player, Player2NpcTab tab, String action, int value) {
        return applyUpdate(player, tab, action, value, "");
    }

    public static GuiActionResult applyUpdate(ServerPlayer player, Player2NpcTab tab, String action, int value, String text) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return GuiActionResult.rejected("no_server");
        }
        if (action == null || action.isBlank()) {
            return GuiActionResult.rejected("missing_action");
        }
        if (action.startsWith("set.") || action.startsWith("set-current.")) {
            return Player2NpcConfigService.applyUpdate(server, player, tab, action, text);
        }
        return switch (tab) {
            case BEHAVIOR -> updateBehavior(server, player, action);
            case BUDGET -> updateBudget(server, player, action, value);
            case COMPANIONS -> updateCompanion(server, player, action, text);
            case ACCESS_LISTS -> updateAccessLists(server, player, action, text);
            case MOD_INTELLIGENCE -> updateModIntelligence(server, player, action, value, text);
            case AI_MEMORY -> updateAiMemory(player, action, value);
            case PROFILES_MODELS -> updateProfileSettings(player, action, text);
            case AUTOMATION_ITEMS -> GuiActionResult.rejected("unsupported_action");
            default -> GuiActionResult.rejected("unsupported_action");
        };
    }

    private static void gettingStartedSnapshot(Map<String, String> fields) {
        fields.put("state", "ready");
    }

    private static void companionSnapshot(MinecraftServer server, ServerPlayer player, Map<String, String> fields, Map<String, CompoundTag> itemFields) {
        CompanionManager manager = CompanionManager.get(player);
        List<CharacterStorageOperations.StoredCharacterRow> stored = CharacterStorageOperations.listStoredCharacters(server, player.getUUID());
        fields.put("storedCount", String.valueOf(stored.size()));
        appendStoredAdditionalPrompts(server, player, stored, fields);
        fields.put("liveCount", String.valueOf(manager.getLiveCompanionCount()));
        Map<String, CompanionStats> activeStats = activeCompanionStats(manager);
        fields.put("activeCount", String.valueOf(activeStats.size()));
        int index = 0;
        for (Map.Entry<String, CompanionStats> entry : activeStats.entrySet()) {
            CompanionStats stats = entry.getValue();
            fields.put("activeName." + index, entry.getKey());
            Character character = stats.companion().getCharacter();
            if (character != null && character.id() != null && !character.id().isBlank()) {
                fields.put("activeCharacterId." + index, character.id());
                String prompt = activeAdditionalPrompt(stats.companion());
                if (!prompt.isBlank()) {
                    fields.put("activeAdditionalPrompt." + index, prompt);
                }
            }
            fields.put("activeHealth." + index, String.valueOf(stats.health()));
            fields.put("activeMaxHealth." + index, String.valueOf(stats.maxHealth()));
            fields.put("activeFood." + index, String.valueOf(stats.foodLevel()));
            if (!stats.taskChainStatus().isBlank()) {
                fields.put("activeTaskChain." + index, stats.taskChainStatus());
            }
            appendInventorySnapshot(server.registryAccess(), index, stats.companion(), fields, itemFields);
            appendMemoryFactsSnapshot(server, index, stats.companion(), fields);
            index++;
        }
    }

    private static void appendStoredAdditionalPrompts(MinecraftServer server, ServerPlayer player,
                                                      List<CharacterStorageOperations.StoredCharacterRow> stored,
                                                      Map<String, String> fields) {
        int limit = Math.min(stored.size(), 40);
        for (int i = 0; i < limit; i++) {
            CharacterStorageOperations.StoredCharacterRow row = stored.get(i);
            if (row.characterId() == null || row.characterId().isBlank()) {
                continue;
            }
            fields.put("storedCharacterId." + i, row.characterId());
            String prompt = AIPersistantData.readAdditionalPrompt(server, player.getUUID(), row.characterId());
            if (!prompt.isBlank()) {
                fields.put("storedAdditionalPrompt." + i, prompt);
            }
        }
    }

    private static String activeAdditionalPrompt(AutomatoneEntity companion) {
        try {
            PlayerEngineController controller = companion.controller;
            return controller == null || controller.getAIPersistantData() == null
                    ? ""
                    : controller.getAIPersistantData().getAdditionalPrompt();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static Map<String, CompanionStats> activeCompanionStats(CompanionManager manager) {
        LinkedHashMap<String, CompanionStats> stats = new LinkedHashMap<>();
        for (AutomatoneEntity companion : manager.getActiveCompanions()) {
            Character character = companion.getCharacter();
            if (character != null && character.name() != null && !character.name().isBlank()) {
                int maxHealth = Math.max(1, (int) Math.ceil(companion.getMaxHealth()));
                int health = clamp((int) Math.ceil(companion.getHealth()), 0, maxHealth);
                int foodLevel = companion.getHungerManager() == null ? 0 : clamp(companion.getHungerManager().getFoodLevel(), 0, 20);
                stats.put(character.name(), new CompanionStats(companion, health, maxHealth, foodLevel, currentTaskChainStatus(companion)));
            }
        }
        return stats;
    }

    private static void appendInventorySnapshot(RegistryAccess access, int index, AutomatoneEntity companion,
                                                Map<String, String> fields, Map<String, CompoundTag> itemFields) {
        LivingEntityInventory inventory = companion.getLivingInventory();
        if (inventory == null) {
            return;
        }
        fields.put("activeSelectedSlot." + index, String.valueOf(clamp(inventory.selectedSlot, 0, 8)));
        for (int slot = 0; slot < inventory.main.size(); slot++) {
            putItemField(access, itemFields, "activeInventory.main." + index + "." + slot, inventory.main.get(slot));
        }
        for (int slot = 0; slot < inventory.armor.size(); slot++) {
            putItemField(access, itemFields, "activeInventory.armor." + index + "." + slot, inventory.armor.get(slot));
        }
        if (!inventory.offHand.isEmpty()) {
            putItemField(access, itemFields, "activeInventory.offhand." + index, inventory.offHand.get(0));
        }
    }

    private static void putItemField(RegistryAccess access, Map<String, CompoundTag> itemFields, String key, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        Tag saved = stack.save(access, new CompoundTag());
        if (saved instanceof CompoundTag tag && !tag.isEmpty()) {
            itemFields.put(key, tag);
        }
    }

    private static void appendMemoryFactsSnapshot(MinecraftServer server, int index, AutomatoneEntity companion,
                                                  Map<String, String> fields) {
        List<String> facts = currentMemoryFacts(server, companion);
        if (!facts.isEmpty()) {
            fields.put("activeMemoryFacts." + index, joinMemoryFacts(facts));
        }
    }

    private static List<String> currentMemoryFacts(MinecraftServer server, AutomatoneEntity companion) {
        List<String> facts = new ArrayList<>(MEMORY_FACT_MAX_LINES);
        MemoryScope scope = memoryScope(companion);
        try {
            MemoryStore store = scope == null ? null : MemoryStoreRegistry.getOrLoad(server, scope);
            MemoryStore.Snapshot snapshot = store == null ? null : store.snapshot();
            MemoryGraph graph = snapshot == null ? null : snapshot.graph();
            if (graph == null || graph.nodeCount() == 0) {
                return facts;
            }
            List<MemoryNode> nodes = new ArrayList<>(graph.nodes());
            nodes.removeIf(node -> !isDisplayableFactNode(node));
            nodes.sort(Player2NpcGuiStateService::compareFactNodes);
            for (MemoryNode node : nodes) {
                String text = memoryFactText(node);
                if (!text.isBlank()) {
                    addUniqueFact(facts, text);
                }
                if (facts.size() >= MEMORY_FACT_MAX_LINES) {
                    break;
                }
            }
        } catch (RuntimeException e) {
            // Read-only UI snapshot; leave facts empty when permanent memory is unavailable.
        }
        return facts;
    }

    private static MemoryScope memoryScope(AutomatoneEntity companion) {
        if (companion == null) {
            return null;
        }
        PlayerEngineController controller = companion.controller;
        Character character = controller != null && controller.getAIPersistantData() != null
                ? controller.getAIPersistantData().getCharacter()
                : companion.getCharacter();
        String companionId = character != null ? character.id() : null;
        if (companionId == null || companionId.isBlank()) {
            return null;
        }
        UUID ownerUuid = controller != null && controller.getOwner() != null
                ? controller.getOwner().getUUID()
                : companion.getOwnerUuid();
        if (ownerUuid != null) {
            return MemoryScope.of(ownerUuid, companionId);
        }
        if (controller != null && controller.getPlayer() != null) {
            return MemoryScope.ofEntityFallback(controller.getPlayer().getUUID(), companionId);
        }
        return MemoryScope.ofEntityFallback(companion.getUUID(), companionId);
    }

    private static boolean isDisplayableFactNode(MemoryNode node) {
        return MemoryDurableFactClassifier.isDisplayableFactNode(node);
    }

    private static int compareFactNodes(MemoryNode a, MemoryNode b) {
        int importance = Integer.compare(b.importance(), a.importance());
        if (importance != 0) {
            return importance;
        }
        int mentions = Integer.compare(b.mentionCount(), a.mentionCount());
        if (mentions != 0) {
            return mentions;
        }
        int recency = Long.compare(b.lastSeenTick(), a.lastSeenTick());
        if (recency != 0) {
            return recency;
        }
        return safeString(a.id()).compareTo(safeString(b.id()));
    }

    private static String memoryFactText(MemoryNode node) {
        String content = cleanMemoryLine(node.content(), MEMORY_FACT_MAX_CHARS);
        if (!content.isBlank()) {
            return content;
        }
        return cleanMemoryLine(node.canonicalName(), MEMORY_FACT_MAX_CHARS);
    }

    private static void addUniqueFact(List<String> facts, String fact) {
        String clean = cleanMemoryLine(fact, MEMORY_FACT_MAX_CHARS);
        if (clean.isBlank()) {
            return;
        }
        LinkedHashSet<String> lower = new LinkedHashSet<>();
        for (String existing : facts) {
            lower.add(existing.toLowerCase(Locale.ROOT));
        }
        if (!lower.contains(clean.toLowerCase(Locale.ROOT))) {
            facts.add(clean);
        }
    }

    private static String joinMemoryFacts(List<String> facts) {
        StringBuilder joined = new StringBuilder();
        for (String fact : facts) {
            if (fact == null || fact.isBlank()) {
                continue;
            }
            if (joined.length() > 0) {
                if (joined.length() + 1 + fact.length() > MEMORY_FACTS_FIELD_MAX_CHARS) {
                    break;
                }
                joined.append('\n');
            } else if (fact.length() > MEMORY_FACTS_FIELD_MAX_CHARS) {
                return cleanMemoryLine(fact, MEMORY_FACTS_FIELD_MAX_CHARS);
            }
            joined.append(fact);
        }
        return joined.toString();
    }

    private static String currentTaskChainStatus(AutomatoneEntity companion) {
        PlayerEngineController controller = companion.controller;
        if (controller == null || controller.getEntity() == null) {
            return "";
        }
        Optional<AgenticRunSnapshot> agentic = AgenticRunRegistry.snapshot(controller.getEntity().getUUID());
        if (agentic.isPresent()) {
            return clampTaskStatus(describeAgenticRun(agentic.get()));
        }
        Optional<StepExecution> tracked = controller.getActiveTrackedStep();
        return tracked.map(Player2NpcGuiStateService::describeTrackedStep)
                .map(Player2NpcGuiStateService::clampTaskStatus)
                .orElse("");
    }

    private static String describeAgenticRun(AgenticRunSnapshot run) {
        StringBuilder status = new StringBuilder();
        status.append("Agentic: ").append(cleanTaskStatusLine(run.goalSummary(), 160));
        if (run.activeStepKind() != null && !run.activeStepKind().isBlank()) {
            status.append("\nStep ").append(run.activeStepIndex() + 1)
                    .append(": ").append(cleanTaskStatusLine(run.activeStepKind(), 80))
                    .append(" [").append(cleanTaskStatusLine(run.state(), 40)).append("]");
        } else if (run.state() != null && !run.state().isBlank()) {
            status.append("\nState: ").append(cleanTaskStatusLine(run.state(), 40));
        }
        if (run.lastMessage() != null && !run.lastMessage().isBlank()) {
            status.append("\n").append(cleanTaskStatusLine(run.lastMessage(), 260));
        }
        if (run.storageTargetSummary() != null && !run.storageTargetSummary().isBlank()) {
            status.append("\nTarget: ").append(cleanTaskStatusLine(run.storageTargetSummary(), 120));
        }
        return status.toString();
    }

    private static String describeTrackedStep(StepExecution exec) {
        StringBuilder status = new StringBuilder();
        status.append("Tracked step: ").append(cleanTaskStatusLine(exec.getStepKind(), 80))
                .append(" [").append(exec.getState()).append("]");
        List<String> log = exec.getLog();
        int first = Math.max(0, log.size() - 4);
        for (int i = first; i < log.size(); i++) {
            status.append("\n").append(cleanTaskStatusLine(log.get(i), 160));
        }
        return status.toString();
    }

    private static String cleanTaskStatusLine(String raw, int maxLength) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replace('\r', ' ').replace('\n', ' ').trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static String cleanMemoryLine(String raw, int maxLength) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replace('\r', ' ').replace('\n', ' ').trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static String safeString(String raw) {
        return raw == null ? "" : raw;
    }

    private static String clampTaskStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.length() <= 900 ? raw : raw.substring(0, 897) + "...";
    }

    private record CompanionStats(AutomatoneEntity companion, int health, int maxHealth, int foodLevel, String taskChainStatus) {
    }

    private static GuiActionResult updateCompanion(MinecraftServer server, ServerPlayer player, String action, String text) {
        if ("additional_prompt".equals(action)) {
            return updateAdditionalPrompt(server, player, text);
        }
        return GuiActionResult.rejected("unsupported_action");
    }

    private static GuiActionResult updateAdditionalPrompt(MinecraftServer server, ServerPlayer player, String text) {
        String payload = text == null ? "" : text;
        int split = payload.indexOf('\n');
        if (split < 0) {
            return GuiActionResult.rejected("missing_character");
        }
        String characterId = payload.substring(0, split).trim();
        if (!isSafeCharacterId(characterId)) {
            return GuiActionResult.rejected("bad_character");
        }
        String prompt = AIPersistantData.cleanAdditionalPrompt(payload.substring(split + 1));
        try {
            AIPersistantData.saveAdditionalPrompt(server, player.getUUID(), characterId, prompt);
            updateLiveAdditionalPrompt(player, characterId, prompt);
            return GuiActionResult.ok();
        } catch (IOException e) {
            return GuiActionResult.rejected("save_failed");
        }
    }

    private static boolean isSafeCharacterId(String characterId) {
        return characterId != null
                && !characterId.isBlank()
                && !characterId.contains("..")
                && characterId.indexOf('/') < 0
                && characterId.indexOf('\\') < 0;
    }

    private static void updateLiveAdditionalPrompt(ServerPlayer player, String characterId, String prompt) {
        for (AutomatoneEntity companion : CompanionManager.get(player).getActiveCompanions()) {
            Character character = companion.getCharacter();
            if (character == null || !characterId.equals(character.id())) {
                continue;
            }
            PlayerEngineController controller = companion.controller;
            if (controller != null && controller.getAIPersistantData() != null) {
                controller.getAIPersistantData().updateAdditionalPrompt(prompt);
            }
        }
    }

    private static void behaviorSnapshot(MinecraftServer server, ServerPlayer player, Map<String, String> fields) {
        OwnerUserSettingsStorage.Snapshot snapshot = OwnerUserSettingsStorage.load(server, player.getUUID());
        boolean usesServer = BotLifecycleSettingsResolver.usesServerConfig(server);
        BotLifecycleSettings effective = BotLifecycleSettingsResolver.resolve(
                server, player.getUUID(), snapshot.autoRespawn(), snapshot.botPermadeath());
        fields.put("userListMode", snapshot.userListMode().toJson());
        fields.put("botListMode", snapshot.botListMode().toJson());
        fields.put("autoEquip", String.valueOf(snapshot.autoEquipArmor()));
        fields.put("autoRespawn", String.valueOf(effective.autoRespawn()));
        fields.put("botPermadeath", String.valueOf(effective.botPermadeath()));
        fields.put("source", usesServer ? "server" : "player");
        fields.put("serverOverride", String.valueOf(Player2ServerConfigHolder.get().isServerOverridesPlayerConfig()));
        fields.put("canEditServerLifecycle", String.valueOf(
                !usesServer || Player2NpcConfigService.canManageServerConfig(server, player)));
    }

    private static void budgetSnapshot(MinecraftServer server, ServerPlayer player, Map<String, String> fields) {
        String key = player.getUUID().toString();
        boolean serverFile = BudgetThresholdsResolver.usesServerBudgetStore(server);
        BudgetThresholds cfg = serverFile
                ? Player2ServerConfigHolder.get()
                : PlayerBudgetConfigHolder.load(server, player.getUUID());
        fields.put("source", serverFile ? "server_player2.json" : "player-budget.json");
        fields.put("canManage", String.valueOf(
                !serverFile || Player2NpcConfigService.canManageServerConfig(server, player)));
        fields.put("softCalls", String.valueOf(cfg.getSoftBudgetCallsPerWindow()));
        fields.put("hardCalls", String.valueOf(cfg.getHardBudgetCallsPerWindow()));
        fields.put("windowMinutes", String.valueOf(cfg.getBudgetWindowMinutes()));
        fields.put("softJoules", String.valueOf(cfg.getSoftJoulesThreshold()));
        fields.put("hardJoules", String.valueOf(cfg.getHardJoulesThreshold()));
        fields.put("joulesRefreshSeconds", String.valueOf(cfg.getJoulesRefreshIntervalSeconds()));
        BudgetTracker.WindowSnapshot window = BudgetTracker.statusSnapshot(cfg).get(key);
        if (window != null) {
            fields.put("callsThisWindow", String.valueOf(window.callCount()));
            long remaining = Math.max(0, window.windowEndMs() - System.currentTimeMillis()) / 1000L;
            fields.put("windowRemainingSeconds", String.valueOf(remaining));
        } else {
            fields.put("callsThisWindow", "0");
            fields.put("windowRemainingSeconds", "");
        }
        Optional<JoulesCache.JoulesSnapshot> joules = JoulesCache.get(key);
        fields.put("joules", joules.map(s -> String.valueOf(s.joulesDisplay())).orElse(""));
        fields.put("patronTier", joules.map(s -> s.patronTier).orElse(""));
    }

    private static void aiMemorySnapshot(ServerPlayer player, Map<String, String> fields) {
        Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
        fields.put("state", "ready");
        fields.put("canManageDeepsearch", String.valueOf(
                Player2NpcConfigService.canManageServerConfig(player.getServer(), player)));
        fields.put("deepsearchRetry", String.valueOf(cfg.isEnableDeepCheckRephrase()));
        fields.put("aliasLearning", String.valueOf(cfg.isEnableAliasLearning()));
        fields.put("deepsearchChatNotice", String.valueOf(cfg.isEnableDeepCheckMessage()));
        fields.put("toolRetrievalTopK", String.valueOf(cfg.getRagTopKClamped()));
        fields.put("graphMemory", String.valueOf(cfg.isEnableGraphRagMemory()));
        fields.put("companionMood", String.valueOf(cfg.isEnableCompanionMood()));
        fields.put("memoryCallsPerWindow", String.valueOf(clamp(cfg.getMemoryCallsPerWindowClamped(), 0, AI_MEMORY_CALLS_MAX)));
    }

    private static GuiActionResult updateAiMemory(ServerPlayer player, String action, int value) {
        if (!Player2NpcConfigService.canManageGlobal(player.getServer(), player)) {
            return GuiActionResult.rejected("op_required");
        }
        Player2ServerConfigHolder.FreshLoadResult fresh = Player2ServerConfigHolder.freshLoadResult();
        if (fresh.loadFailed()) {
            return GuiActionResult.rejected("load_failed");
        }
        Player2ServerRuntimeConfig cfg = fresh.config();
        if (cfg == null) {
            return GuiActionResult.rejected("save_failed");
        }
        switch (action) {
            case "toggle_deepsearch_retry" -> cfg.setEnableDeepCheckRephrase(!cfg.isEnableDeepCheckRephrase());
            case "toggle_alias_learning" -> cfg.setEnableAliasLearning(!cfg.isEnableAliasLearning());
            case "toggle_deepsearch_notice" -> cfg.setEnableDeepCheckMessage(!cfg.isEnableDeepCheckMessage());
            case "tool_retrieval_top_k" -> cfg.setRagTopK(clamp(value, 1, 50));
            case "toggle_graph_memory" -> cfg.setEnableGraphRagMemory(!cfg.isEnableGraphRagMemory());
            case "toggle_companion_mood" -> cfg.setEnableCompanionMood(!cfg.isEnableCompanionMood());
            case "memory_calls_per_window" -> cfg.setMemoryCallsPerWindow(clamp(value, 0, AI_MEMORY_CALLS_MAX));
            default -> {
                return GuiActionResult.rejected("unsupported_action");
            }
        }
        return Player2ServerConfigHolder.setAndSaveChecked(cfg)
                ? GuiActionResult.ok()
                : GuiActionResult.rejected("save_failed");
    }

    private static GuiActionResult updateBehavior(MinecraftServer server, ServerPlayer player, String action) {
        OwnerUserSettingsStorage.Snapshot cur = OwnerUserSettingsStorage.load(server, player.getUUID());
        try {
            switch (action) {
                case "auto_equip" -> OwnerUserSettingsStorage.saveSnapshot(server, player.getUUID(), new OwnerUserSettingsStorage.Snapshot(
                        cur.userListMode(), cur.botListMode(), !cur.autoEquipArmor(), cur.autoRespawn(), cur.botPermadeath()));
                case "user_list_mode", "bot_list_mode" -> {
                    return updateListMode(server, player, action);
                }
                case "auto_respawn" -> updateAutoRespawn(server, player, cur);
                case "bot_permadeath" -> updateBotPermadeath(server, player, cur);
                case "reset_budget_usage" -> {
                    BudgetTracker.reset(player.getUUID().toString());
                    JoulesCache.invalidate(player.getUUID().toString());
                }
                default -> {
                    return GuiActionResult.rejected("unsupported_action");
                }
            }
            return GuiActionResult.ok();
        } catch (IOException e) {
            return GuiActionResult.rejected(switch (String.valueOf(e.getMessage())) {
                case "op_required" -> "op_required";
                case "load_failed" -> "load_failed";
                default -> "save_failed";
            });
        }
    }

    private static GuiActionResult updateListMode(MinecraftServer server, ServerPlayer player, String action) {
        OwnerUserSettingsStorage.Snapshot cur = OwnerUserSettingsStorage.load(server, player.getUUID());
        try {
            switch (action) {
                case "user_list_mode" -> OwnerUserSettingsStorage.saveSnapshot(server, player.getUUID(), new OwnerUserSettingsStorage.Snapshot(
                        toggle(cur.userListMode()), cur.botListMode(), cur.autoEquipArmor(), cur.autoRespawn(), cur.botPermadeath()));
                case "bot_list_mode" -> OwnerUserSettingsStorage.saveSnapshot(server, player.getUUID(), new OwnerUserSettingsStorage.Snapshot(
                        cur.userListMode(), toggle(cur.botListMode()), cur.autoEquipArmor(), cur.autoRespawn(), cur.botPermadeath()));
                default -> {
                    return GuiActionResult.rejected("unsupported_action");
                }
            }
            return GuiActionResult.ok();
        } catch (IOException e) {
            return GuiActionResult.rejected("save_failed");
        }
    }

    private static void updateAutoRespawn(MinecraftServer server, ServerPlayer player, OwnerUserSettingsStorage.Snapshot cur) throws IOException {
        BotLifecycleSettings effective = BotLifecycleSettingsResolver.resolve(server, player.getUUID(), cur.autoRespawn(), cur.botPermadeath());
        boolean next = !effective.autoRespawn();
        if (BotLifecycleSettingsResolver.usesServerConfig(server)) {
            requireLifecyclePermission(server, player);
            Player2ServerConfigHolder.FreshLoadResult fresh = Player2ServerConfigHolder.freshLoadResult();
            if (fresh.loadFailed()) {
                throw new IOException("load_failed");
            }
            Player2ServerRuntimeConfig cfg = fresh.config();
            if (cfg == null) {
                throw new IOException("save_failed");
            }
            cfg.setServerAutoRespawn(next);
            if (!Player2ServerConfigHolder.setAndSaveChecked(cfg)) {
                throw new IOException("save_failed");
            }
            return;
        }
        OwnerUserSettingsStorage.saveSnapshot(server, player.getUUID(), new OwnerUserSettingsStorage.Snapshot(
                cur.userListMode(), cur.botListMode(), cur.autoEquipArmor(), next, cur.botPermadeath()));
    }

    private static void updateBotPermadeath(MinecraftServer server, ServerPlayer player, OwnerUserSettingsStorage.Snapshot cur) throws IOException {
        BotLifecycleSettings effective = BotLifecycleSettingsResolver.resolve(server, player.getUUID(), cur.autoRespawn(), cur.botPermadeath());
        boolean next = !effective.botPermadeath();
        if (BotLifecycleSettingsResolver.usesServerConfig(server)) {
            requireLifecyclePermission(server, player);
            Player2ServerConfigHolder.FreshLoadResult fresh = Player2ServerConfigHolder.freshLoadResult();
            if (fresh.loadFailed()) {
                throw new IOException("load_failed");
            }
            Player2ServerRuntimeConfig cfg = fresh.config();
            if (cfg == null) {
                throw new IOException("save_failed");
            }
            cfg.setServerBotPermadeath(next);
            if (!Player2ServerConfigHolder.setAndSaveChecked(cfg)) {
                throw new IOException("save_failed");
            }
            return;
        }
        OwnerUserSettingsStorage.saveSnapshot(server, player.getUUID(), new OwnerUserSettingsStorage.Snapshot(
                cur.userListMode(), cur.botListMode(), cur.autoEquipArmor(), cur.autoRespawn(), next));
    }

    private static void requireLifecyclePermission(MinecraftServer server, ServerPlayer player) throws IOException {
        if (!Player2NpcConfigService.canManageGlobal(server, player)) {
            throw new IOException("op_required");
        }
    }

    private static OwnerUserSettingsStorage.ListMode toggle(OwnerUserSettingsStorage.ListMode mode) {
        return mode == OwnerUserSettingsStorage.ListMode.WHITELIST
                ? OwnerUserSettingsStorage.ListMode.BLACKLIST
                : OwnerUserSettingsStorage.ListMode.WHITELIST;
    }

    private static GuiActionResult updateBudget(MinecraftServer server, ServerPlayer player, String action, int value) {
        String owner = player.getUUID().toString();
        if ("resetUsage".equals(action)) {
            BudgetTracker.reset(owner);
            JoulesCache.invalidate(owner);
            return GuiActionResult.ok();
        }
        boolean serverFile = BudgetThresholdsResolver.usesServerBudgetStore(server);
        try {
            if (serverFile) {
                if (!Player2NpcConfigService.canManageGlobal(server, player)) {
                    return GuiActionResult.rejected("op_required");
                }
                Player2ServerConfigHolder.FreshLoadResult fresh = Player2ServerConfigHolder.freshLoadResult();
                if (fresh.loadFailed()) {
                    return GuiActionResult.rejected("load_failed");
                }
                Player2ServerRuntimeConfig cfg = fresh.config();
                if (cfg == null) {
                    return GuiActionResult.rejected("save_failed");
                }
                applyBudgetValue(cfg, action, value);
                if (!Player2ServerConfigHolder.hasValidBudgetThresholdPairs(cfg)) {
                    return GuiActionResult.rejected("invalid_value");
                }
                if (!Player2ServerConfigHolder.setAndSaveChecked(cfg)) {
                    return GuiActionResult.rejected("save_failed");
                }
            } else {
                PlayerBudgetConfig cfg = copyBudgetConfig(
                        PlayerBudgetConfigHolder.load(server, player.getUUID()));
                applyBudgetValue(cfg, action, value);
                if (!Player2ServerConfigHolder.hasValidBudgetThresholdPairs(cfg)) {
                    return GuiActionResult.rejected("invalid_value");
                }
                PlayerBudgetConfigHolder.save(server, player.getUUID(), cfg);
            }
        } catch (IllegalArgumentException e) {
            return GuiActionResult.rejected("unsupported_action");
        }
        if ("windowMinutes".equals(action) || "resetUsage".equals(action)) {
            BudgetTracker.reset(owner);
        }
        return GuiActionResult.ok();
    }

    private static void modIntelligenceSnapshot(MinecraftServer server, ServerPlayer player, Map<String, String> fields) {
        ModIntelligenceAdminService.SettingsSnapshot settings = ModIntelligenceAdminService.settings(server);
        ModIntelligenceStatus status = ModIntelligenceService.cachedStatus();
        fields.put("state", "ready");
        fields.put("canManage", String.valueOf(Player2NpcConfigService.canManageGlobal(server, player)));
        fields.put("canManageSettings", String.valueOf(
                Player2NpcConfigService.canManageServerConfig(server, player)));
        fields.put("enabled", String.valueOf(settings.enabled()));
        fields.put("enrichmentEnabled", String.valueOf(settings.enrichmentEnabled()));
        fields.put("maxEnrichmentCalls", String.valueOf(settings.maxEnrichmentCalls()));
        fields.put("bypassBudgetGate", String.valueOf(settings.bypassLargeQueueBudgetGate()));
        fields.put("bypassSupported", String.valueOf(settings.bypassSupported()));
        fields.put("inspecting", String.valueOf(status.isInspecting()));
        fields.put("enriching", String.valueOf(status.isEnriching()));
        fields.put("processedEntries", String.valueOf(status.getTotalEntries()));
        fields.put("readyEntries", String.valueOf(status.getReadyEntries()));
        fields.put("partialEntries", String.valueOf(status.getPartialEntries()));
        fields.put("unknownEntries", String.valueOf(status.getUnknownEntries()));
        fields.put("failedEntries", String.valueOf(status.getFailedEntries()));
        fields.put("queuedEntries", String.valueOf(status.getQueuedEnrichments()));
        fields.put("enrichedEntries", String.valueOf(status.getEnrichedEntries()));
        fields.put("enrichmentFailures", String.valueOf(status.getEnrichmentFailures()));
        fields.put("lastBatchValidated", String.valueOf(status.getLastBatchValidated()));
        fields.put("lastBatchFailures", String.valueOf(status.getLastBatchFailures()));
        fields.put("lastBatchRemaining", String.valueOf(status.getLastBatchRemaining()));
        fields.put("lastOperationCode", status.getLastOperationCode());
    }

    private static GuiActionResult updateModIntelligence(MinecraftServer server, ServerPlayer player,
                                                          String action, int value, String text) {
        if (!Player2NpcConfigService.canManageGlobal(server, player)) {
            return GuiActionResult.rejected("op_required");
        }
        ModIntelligenceAdminService.SettingsSnapshot settings = ModIntelligenceAdminService.settings(server);
        return switch (action) {
            case "toggle_enabled" -> updateModIntelligenceSettingIfWritable(server,
                    ModIntelligenceAdminService.Setting.ENABLED, settings.enabled() ? 0 : 1);
            case "toggle_enrichment" -> updateModIntelligenceSettingIfWritable(server,
                    ModIntelligenceAdminService.Setting.ENRICHMENT_ENABLED,
                    settings.enrichmentEnabled() ? 0 : 1);
            case "max_enrichment_calls" -> updateModIntelligenceSettingIfWritable(server,
                    ModIntelligenceAdminService.Setting.MAX_ENRICHMENT_CALLS, value);
            case "toggle_budget_bypass" -> updateModIntelligenceSettingIfWritable(server,
                    ModIntelligenceAdminService.Setting.BYPASS_LARGE_QUEUE_BUDGET_GATE,
                    settings.bypassLargeQueueBudgetGate() ? 0 : 1);
            case "enrich_now" -> requestModIntelligenceEnrichment(server);
            case "rebuild_scan" -> requestModIntelligenceRebuild(server);
            case "query" -> queryModIntelligence(text);
            default -> GuiActionResult.rejected("unsupported_action");
        };
    }

    private static GuiActionResult updateModIntelligenceSettingIfWritable(
            MinecraftServer server,
            ModIntelligenceAdminService.Setting setting,
            int value) {
        if (Player2NpcConfigService.serverConfigLoadFailed()) {
            return GuiActionResult.rejected("load_failed");
        }
        return updateModIntelligenceSetting(server, setting, value);
    }

    private static GuiActionResult updateModIntelligenceSetting(MinecraftServer server,
                                                                 ModIntelligenceAdminService.Setting setting,
                                                                 int value) {
        return switch (ModIntelligenceAdminService.updateSetting(server, setting, value)) {
            case UPDATED -> GuiActionResult.ok();
            case INVALID_VALUE -> GuiActionResult.rejected("invalid_value");
            case SINGLEPLAYER_ONLY -> GuiActionResult.rejected("singleplayer_only");
            case LOAD_FAILED -> GuiActionResult.rejected("load_failed");
            case SAVE_FAILED -> GuiActionResult.rejected("save_failed");
        };
    }

    private static GuiActionResult requestModIntelligenceEnrichment(MinecraftServer server) {
        return switch (ModIntelligenceAdminService.requestEnrichment(server)) {
            case STARTED -> GuiActionResult.ok("enrichment_started");
            case QUEUED_AFTER_CURRENT -> GuiActionResult.ok("enrichment_queued");
            case NOTHING_QUEUED -> GuiActionResult.rejected("enrichment_queue_empty");
            case QUEUE_UNAVAILABLE -> GuiActionResult.rejected("enrichment_queue_unavailable");
            case DISABLED -> GuiActionResult.rejected("mod_intelligence_disabled");
            case BILLING_UNAVAILABLE -> GuiActionResult.rejected("billing_unavailable");
            case EXECUTOR_UNAVAILABLE -> GuiActionResult.rejected("executor_unavailable");
            case HARD_BUDGET_LIMIT -> GuiActionResult.rejected("hard_budget_limit");
            case MODEL_BLACKLIST_INVALID -> GuiActionResult.rejected("model_blacklist_invalid");
            case LARGE_QUEUE_BUDGET_REQUIRED -> GuiActionResult.rejected("budget_required");
        };
    }

    private static GuiActionResult requestModIntelligenceRebuild(MinecraftServer server) {
        return switch (ModIntelligenceAdminService.requestRebuild(server)) {
            case STARTED -> GuiActionResult.ok("rebuild_started");
            case ALREADY_RUNNING -> GuiActionResult.rejected("rebuild_running");
            case DISABLED -> GuiActionResult.rejected("mod_intelligence_disabled");
            case EXECUTOR_UNAVAILABLE -> GuiActionResult.rejected("executor_unavailable");
        };
    }

    private static GuiActionResult queryModIntelligence(String rawQuery) {
        try {
            String query = ModIntelligenceAdminService.normalizeQuery(rawQuery);
            if (query.isBlank()) {
                return GuiActionResult.rejected("invalid_query");
            }
            List<CapabilityHit> hits = ModIntelligenceAdminService.query(
                    query, Player2ServerConfigHolder.get().getModIntelligenceQueryTopKClamped());
            LinkedHashMap<String, String> response = new LinkedHashMap<>();
            response.put("queryText", query);
            response.put("queryResultCount", String.valueOf(hits.size()));
            for (int i = 0; i < hits.size(); i++) {
                CapabilityHit hit = hits.get(i);
                response.put("querySubject." + i, clampModIntelligenceResult(hit.getSubjectId()));
                response.put("queryKind." + i, hit.getSubjectKind().name().toLowerCase(Locale.ROOT));
                response.put("queryStatus." + i, hit.getStatus().name().toLowerCase(Locale.ROOT));
                response.put("queryCapabilities." + i, clampModIntelligenceResult(
                        String.join(", ", hit.getMatchedCapabilities().stream().limit(4).toList())));
            }
            return GuiActionResult.ok("query_ready", response);
        } catch (IllegalArgumentException e) {
            return GuiActionResult.rejected("invalid_query");
        } catch (RuntimeException e) {
            return GuiActionResult.rejected("query_failed");
        }
    }

    private static String clampModIntelligenceResult(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= MOD_INTELLIGENCE_RESULT_TEXT_LIMIT
                ? value
                : value.substring(0, MOD_INTELLIGENCE_RESULT_TEXT_LIMIT);
    }

    private static void applyBudgetValue(Player2ServerRuntimeConfig cfg, String action, int value) {
        switch (action) {
            case "softCalls" -> cfg.setSoftBudgetCallsPerWindow(clamp(value, 0, BUDGET_CALLS_MAX));
            case "hardCalls" -> cfg.setHardBudgetCallsPerWindow(clamp(value, 0, BUDGET_CALLS_MAX));
            case "windowMinutes" -> cfg.setBudgetWindowMinutes(clamp(value, 1, BUDGET_WINDOW_MINUTES_MAX));
            case "softJoules" -> cfg.setSoftJoulesThreshold(clamp(value, 0, BUDGET_JOULES_MAX));
            case "hardJoules" -> cfg.setHardJoulesThreshold(clamp(value, 0, BUDGET_JOULES_MAX));
            case "joulesRefreshSeconds" -> cfg.setJoulesRefreshIntervalSeconds(
                    clamp(value, BUDGET_REFRESH_SECONDS_MIN, BUDGET_REFRESH_SECONDS_MAX));
            case "resetUsage" -> {
            }
            default -> throw new IllegalArgumentException("unsupported budget action");
        }
    }

    private static void applyBudgetValue(PlayerBudgetConfig cfg, String action, int value) {
        switch (action) {
            case "softCalls" -> cfg.setSoftBudgetCallsPerWindow(clamp(value, 0, BUDGET_CALLS_MAX));
            case "hardCalls" -> cfg.setHardBudgetCallsPerWindow(clamp(value, 0, BUDGET_CALLS_MAX));
            case "windowMinutes" -> cfg.setBudgetWindowMinutes(clamp(value, 1, BUDGET_WINDOW_MINUTES_MAX));
            case "softJoules" -> cfg.setSoftJoulesThreshold(clamp(value, 0, BUDGET_JOULES_MAX));
            case "hardJoules" -> cfg.setHardJoulesThreshold(clamp(value, 0, BUDGET_JOULES_MAX));
            case "joulesRefreshSeconds" -> cfg.setJoulesRefreshIntervalSeconds(
                    clamp(value, BUDGET_REFRESH_SECONDS_MIN, BUDGET_REFRESH_SECONDS_MAX));
            case "resetUsage" -> {
            }
            default -> throw new IllegalArgumentException("unsupported budget action");
        }
    }

    private static PlayerBudgetConfig copyBudgetConfig(BudgetThresholds source) {
        PlayerBudgetConfig copy = new PlayerBudgetConfig();
        if (source == null) {
            return copy;
        }
        copy.setSoftBudgetCallsPerWindow(source.getSoftBudgetCallsPerWindow());
        copy.setHardBudgetCallsPerWindow(source.getHardBudgetCallsPerWindow());
        copy.setBudgetWindowMinutes(source.getBudgetWindowMinutes());
        copy.setSoftJoulesThreshold(source.getSoftJoulesThreshold());
        copy.setHardJoulesThreshold(source.getHardJoulesThreshold());
        copy.setJoulesRefreshIntervalSeconds(source.getJoulesRefreshIntervalSeconds());
        return copy;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void accessListSnapshot(MinecraftServer server, ServerPlayer player, Map<String, String> fields) {
        OwnerUserSettingsStorage.Snapshot snapshot = OwnerUserSettingsStorage.load(server, player.getUUID());
        List<UserWhitelistStorage.Entry> userWhitelist = UserWhitelistStorage.load(server, player.getUUID());
        List<UserBlacklistStorage.Entry> userBlacklist = UserBlacklistStorage.load(server, player.getUUID());
        List<BotWhitelistStorage.Entry> botWhitelist = BotWhitelistStorage.load(server, player.getUUID());
        List<BotBlacklistStorage.Entry> botBlacklist = BotBlacklistStorage.load(server, player.getUUID());
        fields.put("userListMode", snapshot.userListMode().toJson());
        fields.put("botListMode", snapshot.botListMode().toJson());
        fields.put("userWhitelistCount", String.valueOf(userWhitelist.size()));
        fields.put("userBlacklistCount", String.valueOf(userBlacklist.size()));
        fields.put("botWhitelistCount", String.valueOf(botWhitelist.size()));
        fields.put("botBlacklistCount", String.valueOf(botBlacklist.size()));
        fields.put("userWhitelist", formatUserWhitelistEntries(userWhitelist));
        fields.put("userBlacklist", formatUserBlacklistEntries(userBlacklist));
        fields.put("botWhitelist", formatBotWhitelistEntries(botWhitelist));
        fields.put("botBlacklist", formatBotBlacklistEntries(botBlacklist));
    }

    private static GuiActionResult updateAccessLists(MinecraftServer server, ServerPlayer player, String action, String text) {
        if ("user_list_mode".equals(action) || "bot_list_mode".equals(action)) {
            return updateListMode(server, player, action);
        }
        if (!"save_access_lists".equals(action)) {
            return GuiActionResult.rejected("unsupported_action");
        }
        String[] parts = (text == null ? "" : text).split("\n", -1);
        if (parts.length != 4) {
            return GuiActionResult.rejected("invalid_list");
        }
        try {
            List<UserWhitelistStorage.Entry> userWhitelist = parseUserWhitelist(server, parts[0]);
            List<UserBlacklistStorage.Entry> userBlacklist = parseUserBlacklist(server, parts[1]);
            List<BotWhitelistStorage.Entry> botWhitelist = parseBotWhitelist(server, parts[2]);
            List<BotBlacklistStorage.Entry> botBlacklist = parseBotBlacklist(server, parts[3]);
            UserWhitelistStorage.save(server, player.getUUID(), userWhitelist);
            UserBlacklistStorage.save(server, player.getUUID(), userBlacklist);
            BotWhitelistStorage.save(server, player.getUUID(), botWhitelist);
            BotBlacklistStorage.save(server, player.getUUID(), botBlacklist);
            return GuiActionResult.ok();
        } catch (IllegalArgumentException e) {
            return GuiActionResult.rejected("invalid_list");
        } catch (IOException e) {
            return GuiActionResult.rejected("save_failed");
        }
    }

    private static List<UserWhitelistStorage.Entry> parseUserWhitelist(MinecraftServer server, String raw) {
        List<UserWhitelistStorage.Entry> out = new ArrayList<>();
        for (String token : parseCommaEntries(raw)) {
            String username = parseMinecraftUsername(token);
            Optional<UUID> targetUuid = BotBlacklistStorage.resolveTargetUuid(server, username);
            out.add(new UserWhitelistStorage.Entry(username, targetUuid.map(UUID::toString).orElse(null)));
        }
        return out;
    }

    private static List<UserBlacklistStorage.Entry> parseUserBlacklist(MinecraftServer server, String raw) {
        List<UserBlacklistStorage.Entry> out = new ArrayList<>();
        for (String token : parseCommaEntries(raw)) {
            String username = parseMinecraftUsername(token);
            Optional<UUID> targetUuid = BotBlacklistStorage.resolveTargetUuid(server, username);
            out.add(new UserBlacklistStorage.Entry(username, targetUuid.map(UUID::toString).orElse(null)));
        }
        return out;
    }

    private static List<BotWhitelistStorage.Entry> parseBotWhitelist(MinecraftServer server, String raw) {
        List<BotWhitelistStorage.Entry> out = new ArrayList<>();
        for (String token : parseCommaEntries(raw)) {
            ParsedBotAccessToken parsed = parseBotAccessToken(server, token);
            out.add(new BotWhitelistStorage.Entry(parsed.username(), parsed.targetUuid(), parsed.allBots(), parsed.characterId(), parsed.characterName()));
        }
        return out;
    }

    private static List<BotBlacklistStorage.Entry> parseBotBlacklist(MinecraftServer server, String raw) {
        List<BotBlacklistStorage.Entry> out = new ArrayList<>();
        for (String token : parseCommaEntries(raw)) {
            ParsedBotAccessToken parsed = parseBotAccessToken(server, token);
            out.add(new BotBlacklistStorage.Entry(parsed.username(), parsed.targetUuid(), parsed.allBots(), parsed.characterId(), parsed.characterName()));
        }
        return out;
    }

    private static List<String> parseCommaEntries(String raw) {
        String normalized = raw == null ? "" : raw.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        normalized = normalized.replace('\r', ',').replace('\n', ',');
        String[] parts = normalized.split(",", -1);
        List<String> entries = new ArrayList<>();
        for (String part : parts) {
            String token = stripListToken(part);
            if (token.isBlank()) {
                continue;
            }
            if (token.length() > ACCESS_LIST_MAX_TOKEN_LENGTH || hasUnsupportedListCharacter(token)) {
                throw new IllegalArgumentException("invalid list token");
            }
            entries.add(token);
            if (entries.size() > ACCESS_LIST_MAX_ENTRIES) {
                throw new IllegalArgumentException("too many list entries");
            }
        }
        return entries;
    }

    private static String stripListToken(String raw) {
        String token = raw == null ? "" : raw.trim();
        if (token.length() >= 2) {
            char first = token.charAt(0);
            char last = token.charAt(token.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                token = token.substring(1, token.length() - 1).trim();
            }
        }
        return token;
    }

    private static boolean hasUnsupportedListCharacter(String token) {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (java.lang.Character.isISOControl(c) || c == '[' || c == ']' || c == '{' || c == '}' || c == ';') {
                return true;
            }
        }
        return false;
    }

    private static String parseMinecraftUsername(String token) {
        if (!isMinecraftUsername(token)) {
            throw new IllegalArgumentException("invalid minecraft username");
        }
        return token;
    }

    private static boolean isMinecraftUsername(String value) {
        if (value == null || value.isBlank() || value.length() > 16) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(c == '_' || c >= '0' && c <= '9' || c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z')) {
                return false;
            }
        }
        return true;
    }

    private static ParsedBotAccessToken parseBotAccessToken(MinecraftServer server, String token) {
        int colon = token.indexOf(':');
        if (colon < 0) {
            String username = parseMinecraftUsername(token);
            Optional<UUID> targetUuid = BotBlacklistStorage.resolveTargetUuid(server, username);
            return new ParsedBotAccessToken(username, targetUuid.map(UUID::toString).orElse(null), true, null, null);
        }
        if (colon == 0 || colon == token.length() - 1 || token.indexOf(':', colon + 1) >= 0) {
            throw new IllegalArgumentException("invalid bot list scope");
        }
        String username = parseMinecraftUsername(token.substring(0, colon).trim());
        String character = token.substring(colon + 1).trim();
        if (!isSafeCharacterSelector(character)) {
            throw new IllegalArgumentException("invalid character selector");
        }
        Optional<UUID> targetOwnerUuid = BotBlacklistStorage.resolveTargetUuid(server, username);
        if (targetOwnerUuid.isEmpty()) {
            throw new IllegalArgumentException("unknown bot owner");
        }
        Optional<UUID> characterUuid = CharacterStorageOperations.parseUuidLenient(character);
        if (characterUuid.isPresent()) {
            return new ParsedBotAccessToken(username, targetOwnerUuid.map(UUID::toString).orElse(null), false, characterUuid.get().toString(), null);
        }
        List<String> matches = new ArrayList<>();
        matches.addAll(CharacterStorageOperations.findCharacterIdsByFullName(server, targetOwnerUuid.get(), character, false));
        for (String match : CharacterStorageOperations.findCharacterIdsByShortName(server, targetOwnerUuid.get(), character, false)) {
            if (!matches.contains(match)) {
                matches.add(match);
            }
        }
        if (matches.size() != 1) {
            throw new IllegalArgumentException("unresolved character selector");
        }
        return new ParsedBotAccessToken(username, targetOwnerUuid.map(UUID::toString).orElse(null), false, matches.get(0), character);
    }

    private static boolean isSafeCharacterSelector(String value) {
        if (value == null || value.isBlank() || value.length() > ACCESS_LIST_MAX_CHARACTER_LENGTH) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (java.lang.Character.isISOControl(c) || c == ',' || c == ':' || c == '[' || c == ']' || c == '{' || c == '}' || c == ';') {
                return false;
            }
        }
        return true;
    }

    private static String formatUserWhitelistEntries(List<UserWhitelistStorage.Entry> entries) {
        List<String> out = new ArrayList<>();
        for (UserWhitelistStorage.Entry entry : entries) {
            if (entry.targetUsername() != null && !entry.targetUsername().isBlank()) {
                out.add(entry.targetUsername().trim());
            }
        }
        return String.join(", ", out);
    }

    private static String formatUserBlacklistEntries(List<UserBlacklistStorage.Entry> entries) {
        List<String> out = new ArrayList<>();
        for (UserBlacklistStorage.Entry entry : entries) {
            if (entry.targetUsername() != null && !entry.targetUsername().isBlank()) {
                out.add(entry.targetUsername().trim());
            }
        }
        return String.join(", ", out);
    }

    private static String formatBotWhitelistEntries(List<BotWhitelistStorage.Entry> entries) {
        List<String> out = new ArrayList<>();
        for (BotWhitelistStorage.Entry entry : entries) {
            String formatted = formatBotEntry(entry.targetUsername(), entry.allBots(), entry.characterId(), entry.characterName());
            if (!formatted.isBlank()) {
                out.add(formatted);
            }
        }
        return String.join(", ", out);
    }

    private static String formatBotBlacklistEntries(List<BotBlacklistStorage.Entry> entries) {
        List<String> out = new ArrayList<>();
        for (BotBlacklistStorage.Entry entry : entries) {
            String formatted = formatBotEntry(entry.targetUsername(), entry.allBots(), entry.characterId(), entry.characterName());
            if (!formatted.isBlank()) {
                out.add(formatted);
            }
        }
        return String.join(", ", out);
    }

    private static String formatBotEntry(String username, boolean allBots, String characterId, String characterName) {
        if (username == null || username.isBlank()) {
            return "";
        }
        String cleanUsername = username.trim();
        if (allBots) {
            return cleanUsername;
        }
        String character = characterName != null && !characterName.isBlank() ? characterName.trim() : characterId;
        return character == null || character.isBlank() ? cleanUsername : cleanUsername + ":" + character;
    }

    private record ParsedBotAccessToken(String username, String targetUuid, boolean allBots, String characterId, String characterName) {
    }

    private static void profileSnapshot(MinecraftServer server, ServerPlayer player, Map<String, String> fields) {
        Player2ServerRuntimeConfig cfg = Player2ServerConfigHolder.get();
        fields.put("state", "ready");
        fields.put("canManage", String.valueOf(
                Player2NpcConfigService.canManageServerConfig(server, player)));
        fields.put("namedProfileSwitchSupported", String.valueOf(!cfg.isDedicatedClientProxy()));
        fields.put("fallbackBehavior", cfg.getBudgetFallbackBehavior().name().toLowerCase(Locale.ROOT));
        fields.put("fallbackProfile", cfg.getFallbackProfile() == null ? "" : cfg.getFallbackProfile());
    }

    private static GuiActionResult updateProfileSettings(ServerPlayer player, String action, String text) {
        if (!Player2NpcConfigService.canManageGlobal(player.getServer(), player)) {
            return GuiActionResult.rejected("op_required");
        }
        Player2ServerConfigHolder.FreshLoadResult fresh = Player2ServerConfigHolder.freshLoadResult();
        if (fresh.loadFailed()) {
            return GuiActionResult.rejected("load_failed");
        }
        Player2ServerRuntimeConfig cfg = fresh.config();
        if (cfg == null) {
            return GuiActionResult.rejected("save_failed");
        }
        switch (action) {
            case "fallback_behavior_stop" -> cfg.setBudgetFallbackBehavior(BudgetFallbackBehavior.HARD_STOP);
            case "fallback_behavior_switch" -> {
                if (cfg.isDedicatedClientProxy() && text != null && !text.isBlank()) {
                    return GuiActionResult.rejected("unsupported_action");
                }
                cfg.setBudgetFallbackBehavior(BudgetFallbackBehavior.SWITCH_PROFILE);
                if (text == null || text.isBlank()) {
                    cfg.setFallbackProfile(null);
                } else {
                    String fallback = normalizeProfileName(text);
                    if (fallback == null) {
                        return GuiActionResult.rejected("invalid_value");
                    }
                    cfg.setFallbackProfile(fallback);
                }
            }
            case "set_fallback_profile" -> {
                if (text == null || text.isBlank()) {
                    cfg.setFallbackProfile(null);
                } else {
                    if (cfg.isDedicatedClientProxy()) {
                        return GuiActionResult.rejected("unsupported_action");
                    }
                    String fallback = normalizeProfileName(text);
                    if (fallback == null) {
                        return GuiActionResult.rejected("invalid_value");
                    }
                    cfg.setFallbackProfile(fallback);
                }
            }
            default -> {
                return GuiActionResult.rejected("unsupported_action");
            }
        }
        if (!Player2ServerConfigHolder.setAndSaveChecked(cfg)) {
            return GuiActionResult.rejected("save_failed");
        }
        ProfileUrlResolver.invalidateCache();
        return GuiActionResult.ok();
    }

    private static String normalizeProfileName(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > PROFILE_NAME_MAX_LENGTH
                || "Default".equalsIgnoreCase(raw)) {
            return null;
        }
        int first = raw.codePointAt(0);
        int last = raw.codePointBefore(raw.length());
        if (isProfileWhitespace(first) || isProfileWhitespace(last)) {
            return null;
        }
        for (int offset = 0; offset < raw.length(); ) {
            int codePoint = raw.codePointAt(offset);
            if (java.lang.Character.isISOControl(codePoint)
                    || java.lang.Character.getType(codePoint) == java.lang.Character.FORMAT) {
                return null;
            }
            offset += java.lang.Character.charCount(codePoint);
        }
        return raw;
    }

    private static boolean isProfileWhitespace(int codePoint) {
        return java.lang.Character.isWhitespace(codePoint)
                || java.lang.Character.isSpaceChar(codePoint);
    }

    private static void unavailable(Map<String, String> fields, String feature) {
        fields.put("state", "unavailable");
        fields.put("feature", feature);
    }
}

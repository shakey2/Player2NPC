package com.goodbird.player2npc.companion.gui;

import com.goodbird.player2npc.client.gui.Player2NpcTab;
import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Map;

public record Player2NpcGuiSnapshot(Player2NpcTab tab, Map<String, String> fields, Map<String, CompoundTag> itemFields) {
    public Player2NpcGuiSnapshot(Player2NpcTab tab, Map<String, String> fields) {
        this(tab, fields, Map.of());
    }

    public Player2NpcGuiSnapshot {
        Map<String, String> clean = new LinkedHashMap<>();
        if (fields != null) {
            fields.entrySet().stream().limit(GuiProtocol.MAX_FIELDS).forEach(entry -> {
                String key = entry.getKey() == null ? "" : entry.getKey();
                if (key.length() > GuiProtocol.MAX_KEY_LENGTH) {
                    key = key.substring(0, GuiProtocol.MAX_KEY_LENGTH);
                }
                clean.put(key, GuiProtocol.clampValue(entry.getValue()));
            });
        }
        fields = Map.copyOf(clean);

        Map<String, CompoundTag> cleanItemFields = new LinkedHashMap<>();
        if (itemFields != null) {
            itemFields.entrySet().stream().limit(GuiProtocol.MAX_ITEM_FIELDS).forEach(entry -> {
                String key = entry.getKey() == null ? "" : entry.getKey();
                if (key.length() > GuiProtocol.MAX_KEY_LENGTH) {
                    key = key.substring(0, GuiProtocol.MAX_KEY_LENGTH);
                }
                CompoundTag value = entry.getValue();
                if (value != null && !value.isEmpty()) {
                    cleanItemFields.put(key, value.copy());
                }
            });
        }
        itemFields = Map.copyOf(cleanItemFields);
    }

    public String get(String key) {
        return fields.getOrDefault(key, "");
    }
}

package com.goodbird.player2npc.network;

import com.goodbird.player2npc.client.gui.Player2NpcTab;
import com.goodbird.player2npc.companion.gui.GuiProtocol;
import com.goodbird.player2npc.companion.gui.Player2NpcGuiSnapshot;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.LinkedHashMap;
import java.util.Map;

public class GuiSnapshotPacket {
    private final int version;
    private final Player2NpcTab tab;
    private final Map<String, String> fields;
    private final Map<String, CompoundTag> itemFields;

    public GuiSnapshotPacket(FriendlyByteBuf buf) {
        this.version = buf.readVarInt();
        if (version != GuiProtocol.GUI_PROTOCOL_VERSION) {
            this.tab = Player2NpcTab.UNKNOWN;
            this.fields = Map.of("state", "version_mismatch");
            this.itemFields = Map.of();
            return;
        }
        this.tab = Player2NpcTab.fromId(buf.readVarInt());
        int size = Math.min(buf.readVarInt(), GuiProtocol.MAX_FIELDS);
        LinkedHashMap<String, String> decoded = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf(GuiProtocol.MAX_KEY_LENGTH);
            String value = buf.readUtf(GuiProtocol.MAX_VALUE_LENGTH);
            decoded.put(key, value);
        }
        this.fields = Map.copyOf(decoded);
        LinkedHashMap<String, CompoundTag> decodedItemFields = new LinkedHashMap<>();
        if (version >= 2 && buf.readableBytes() > 0) {
            int itemSize = Math.min(buf.readVarInt(), GuiProtocol.MAX_ITEM_FIELDS);
            for (int i = 0; i < itemSize; i++) {
                String key = buf.readUtf(GuiProtocol.MAX_KEY_LENGTH);
                CompoundTag value = buf.readNbt();
                if (value != null && !value.isEmpty()) {
                    decodedItemFields.put(key, value);
                }
            }
        }
        this.itemFields = Map.copyOf(decodedItemFields);
    }

    private GuiSnapshotPacket(Player2NpcGuiSnapshot snapshot) {
        this.version = GuiProtocol.GUI_PROTOCOL_VERSION;
        this.tab = snapshot.tab();
        this.fields = snapshot.fields();
        this.itemFields = snapshot.itemFields();
    }

    public static RegistryFriendlyByteBuf create(RegistryAccess access, Player2NpcGuiSnapshot snapshot) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        new GuiSnapshotPacket(snapshot).write(buf);
        return buf;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(version);
        buf.writeVarInt(tab.id());
        buf.writeVarInt(Math.min(fields.size(), GuiProtocol.MAX_FIELDS));
        fields.entrySet().stream().limit(GuiProtocol.MAX_FIELDS).forEach(entry -> {
            buf.writeUtf(entry.getKey(), GuiProtocol.MAX_KEY_LENGTH);
            buf.writeUtf(GuiProtocol.clampValue(entry.getValue()), GuiProtocol.MAX_VALUE_LENGTH);
        });
        buf.writeVarInt(Math.min(itemFields.size(), GuiProtocol.MAX_ITEM_FIELDS));
        itemFields.entrySet().stream().limit(GuiProtocol.MAX_ITEM_FIELDS).forEach(entry -> {
            buf.writeUtf(entry.getKey(), GuiProtocol.MAX_KEY_LENGTH);
            buf.writeNbt(entry.getValue().copy());
        });
    }

    public int version() {
        return version;
    }

    public Player2NpcTab tab() {
        return tab;
    }

    public Map<String, String> fields() {
        return fields;
    }

    public Map<String, CompoundTag> itemFields() {
        return itemFields;
    }
}

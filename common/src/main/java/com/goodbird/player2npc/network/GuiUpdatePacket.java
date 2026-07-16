package com.goodbird.player2npc.network;

import com.goodbird.player2npc.Player2NPC;
import com.goodbird.player2npc.client.gui.Player2NpcTab;
import com.goodbird.player2npc.companion.gui.GuiActionResult;
import com.goodbird.player2npc.companion.gui.GuiProtocol;
import com.goodbird.player2npc.companion.gui.Player2NpcGuiSnapshot;
import com.goodbird.player2npc.companion.gui.Player2NpcGuiStateService;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;

public class GuiUpdatePacket {
    private final int version;
    private final Player2NpcTab tab;
    private final String action;
    private final int value;
    private final String text;

    public GuiUpdatePacket(FriendlyByteBuf buf) {
        this.version = buf.readVarInt();
        this.tab = Player2NpcTab.fromId(buf.readVarInt());
        this.action = buf.readUtf(GuiProtocol.MAX_KEY_LENGTH);
        this.value = buf.readVarInt();
        this.text = buf.readableBytes() > 0 ? buf.readUtf(GuiProtocol.MAX_VALUE_LENGTH) : "";
    }

    private GuiUpdatePacket(Player2NpcTab tab, String action, int value) {
        this(tab, action, value, "");
    }

    private GuiUpdatePacket(Player2NpcTab tab, String action, int value, String text) {
        this.version = GuiProtocol.GUI_PROTOCOL_VERSION;
        this.tab = tab;
        this.action = action;
        this.value = value;
        this.text = text == null ? "" : text;
    }

    public static FriendlyByteBuf create(Player2NpcTab tab, String action, int value) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new GuiUpdatePacket(tab, action, value).write(buf);
        return buf;
    }

    public static FriendlyByteBuf createText(Player2NpcTab tab, String action, String text) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new GuiUpdatePacket(tab, action, 0, text).write(buf);
        return buf;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(version);
        buf.writeVarInt(tab.id());
        buf.writeUtf(action == null ? "" : action, GuiProtocol.MAX_KEY_LENGTH);
        buf.writeVarInt(value);
        buf.writeUtf(text == null ? "" : text, GuiProtocol.MAX_VALUE_LENGTH);
    }

    public static void handle(FriendlyByteBuf buf, NetworkManager.PacketContext context) {
        GuiUpdatePacket packet = new GuiUpdatePacket(buf);
        context.queue(() -> {
            if (!(context.getPlayer() instanceof ServerPlayer player)) {
                return;
            }
            Player2NpcGuiSnapshot snapshot;
            if (packet.version != GuiProtocol.GUI_PROTOCOL_VERSION || packet.tab == Player2NpcTab.UNKNOWN) {
                snapshot = new Player2NpcGuiSnapshot(Player2NpcTab.UNKNOWN, Map.of("state", "version_mismatch"));
            } else {
                GuiActionResult result = Player2NpcGuiStateService.applyUpdate(player, packet.tab, packet.action, packet.value, packet.text);
                Player2NpcGuiSnapshot fresh = Player2NpcGuiStateService.snapshot(player, packet.tab);
                LinkedHashMap<String, String> fields = new LinkedHashMap<>(fresh.fields());
                fields.putAll(result.responseFields());
                fields.put("actionResult", result.reasonCode());
                snapshot = new Player2NpcGuiSnapshot(packet.tab, fields, fresh.itemFields());
            }
            NetworkManager.sendToPlayer(player, Player2NPC.GUI_SNAPSHOT_PACKET_ID, GuiSnapshotPacket.create(snapshot));
        });
    }
}

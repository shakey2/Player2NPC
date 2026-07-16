package com.goodbird.player2npc.network;

import com.goodbird.player2npc.Player2NPC;
import com.goodbird.player2npc.client.gui.Player2NpcTab;
import com.goodbird.player2npc.companion.gui.GuiProtocol;
import com.goodbird.player2npc.companion.gui.Player2NpcGuiSnapshot;
import com.goodbird.player2npc.companion.gui.Player2NpcGuiStateService;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class GuiSnapshotRequestPacket {
    private final int version;
    private final int tabId;
    private final Player2NpcTab tab;

    public GuiSnapshotRequestPacket(FriendlyByteBuf buf) {
        this.version = buf.readVarInt();
        this.tabId = buf.readVarInt();
        this.tab = Player2NpcTab.fromId(tabId);
    }

    private GuiSnapshotRequestPacket(Player2NpcTab tab) {
        this.version = GuiProtocol.GUI_PROTOCOL_VERSION;
        this.tabId = tab.id();
        this.tab = tab;
    }

    public static RegistryFriendlyByteBuf create(RegistryAccess access, Player2NpcTab tab) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        new GuiSnapshotRequestPacket(tab).write(buf);
        return buf;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(version);
        buf.writeVarInt(tabId);
    }

    public static void handle(FriendlyByteBuf buf, NetworkManager.PacketContext context) {
        GuiSnapshotRequestPacket packet = new GuiSnapshotRequestPacket(buf);
        context.queue(() -> {
            if (!(context.getPlayer() instanceof ServerPlayer player)) {
                return;
            }
            Player2NpcGuiSnapshot snapshot;
            if (packet.version != GuiProtocol.GUI_PROTOCOL_VERSION) {
                snapshot = new Player2NpcGuiSnapshot(packet.tab, java.util.Map.of("state", "version_mismatch"));
            } else if (packet.tab == Player2NpcTab.UNKNOWN) {
                snapshot = new Player2NpcGuiSnapshot(Player2NpcTab.UNKNOWN, java.util.Map.of("state", "unsupported_tab"));
            } else {
                snapshot = Player2NpcGuiStateService.snapshot(player, packet.tab);
            }
            NetworkManager.sendToPlayer(player, Player2NPC.GUI_SNAPSHOT_PACKET_ID, GuiSnapshotPacket.create(player.level().registryAccess(), snapshot));
        });
    }
}

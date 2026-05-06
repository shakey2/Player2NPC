package com.goodbird.player2npc.fabric.mixin;

import com.goodbird.player2npc.fabric.debug.AgentDebugLog;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicInteger;

@Mixin(MouseHandler.class)
public class MouseHandlerDebugMixin {
    private static final AtomicInteger MOUSE_LOG_BUDGET = new AtomicInteger(0);

    @Inject(method = "onPress", at = @At("HEAD"))
    private void agentDebugOnPress(long window, int button, int action, int mods, CallbackInfo ci) {
        // #region agent log
        int n = MOUSE_LOG_BUDGET.incrementAndGet();
        if (n <= 20) {
            AgentDebugLog.log("H2", "MouseHandlerDebugMixin.onPress", "mouse_press",
                    String.format("{\"window\":%d,\"button\":%d,\"action\":%d,\"mods\":%d,\"seq\":%d}", window, button, action, mods, n));
        }
        // #endregion
    }
}

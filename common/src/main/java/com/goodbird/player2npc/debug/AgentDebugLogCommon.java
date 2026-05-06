package com.goodbird.player2npc.debug;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class AgentDebugLogCommon {
    private static final Path LOG_PATH = Path.of("C:/MCmodsstorage/debug-d3d943.log");

    private AgentDebugLogCommon() {
    }

    public static void log(String hypothesisId, String location, String message, String dataJsonObject) {
        // #region agent log
        try {
            String data = dataJsonObject == null || dataJsonObject.isEmpty() ? "{}" : dataJsonObject;
            String line = String.format(Locale.ROOT,
                    "{\"sessionId\":\"d3d943\",\"timestamp\":%d,\"hypothesisId\":\"%s\",\"location\":\"%s\",\"message\":\"%s\",\"data\":%s}%n",
                    System.currentTimeMillis(),
                    escapeJson(hypothesisId),
                    escapeJson(location),
                    escapeJson(message),
                    data);
            // Avoid blocking client tick handlers on file IO.
            CompletableFuture.runAsync(() -> {
                try {
                    Files.writeString(LOG_PATH, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
        // #endregion
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}

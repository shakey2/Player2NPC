package com.goodbird.player2npc.companion;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * In-memory hint of the last-known dimension + position of a live companion, refreshed at a throttled
 * interval from {@link AutomatoneEntity#tick()}. The tracker's own store is a JVM-session map (it holds
 * no state across a restart by itself), but it is NOT ephemeral end-to-end: {@code CompanionManager}'s
 * NBT round-trip persists a coarse per-companion hint (dimension + block position) in
 * {@code writeToNbt()} and RESTORES it back into this tracker in {@code readFromNbt()} on login, so a
 * companion's last-known location survives an unclean shutdown / crash restart (revision finding #3).
 *
 * <p><b>Why:</b> {@code ServerLevel.getEntity(UUID)} only sees LOADED entities. A canonical companion
 * whose chunk has unloaded is indistinguishable, via that API alone, from one that was discarded — so
 * "not found in any loaded level" cannot safely mean "gone -&gt; spawn a new one" without risking a
 * clone next to a healthy, sleeping companion. When a resolve comes back empty, callers consult this
 * hint and force-load that one chunk to positively confirm absence before spawning.
 *
 * <p><b>Dimension-generic:</b> records whatever {@link ResourceKey}&lt;{@link Level}&gt; the entity is
 * currently ticking in — no overworld/nether/end special-casing, so it naturally covers modded
 * dimensions. A tracker with no entry for a companion (never ticked this session, and no persisted hint
 * restored) simply degrades to the pre-existing lookup behavior for that one call; it only ever REDUCES
 * the residual clone window.
 */
public final class CompanionLocationTracker {

    /** Last-known dimension + block position of a companion entity. */
    public record LocationHint(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private static final ConcurrentHashMap<UUID, LocationHint> HINTS = new ConcurrentHashMap<>();

    private CompanionLocationTracker() {
    }

    public static void record(UUID uuid, ResourceKey<Level> dimension, BlockPos pos) {
        if (uuid == null || dimension == null || pos == null) {
            return;
        }
        HINTS.put(uuid, new LocationHint(dimension, pos.immutable()));
    }

    public static LocationHint get(UUID uuid) {
        return uuid == null ? null : HINTS.get(uuid);
    }

    public static void clear(UUID uuid) {
        if (uuid != null) {
            HINTS.remove(uuid);
        }
    }
}

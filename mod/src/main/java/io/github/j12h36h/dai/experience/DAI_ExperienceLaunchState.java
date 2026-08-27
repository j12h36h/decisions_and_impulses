package io.github.j12h36h.dai.experience;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Common-side handoff between a client title-button launch, the integrated
 * server startup, and the client session bootstrap.
 */
public final class DAI_ExperienceLaunchState {

    private static volatile Pending pending;
    private static volatile CompletableFuture<?> packReloadFuture = CompletableFuture.completedFuture(null);
    private static volatile boolean packReloadFailed;
    private static volatile boolean worldReady;

    private DAI_ExperienceLaunchState() {}

    public static void prepare(
            DAI_ExperienceDefinition definition,
            boolean firstJoin,
            Path sourcePack
    ) {
        prepare(definition, firstJoin, sourcePack, "");
    }

    public static void prepare(
            DAI_ExperienceDefinition definition,
            boolean firstJoin,
            Path sourcePack,
            String worldgenOverride
    ) {
        pending = definition == null
                ? null
                : new Pending(definition, firstJoin, sourcePack, worldgenOverride == null ? "" : worldgenOverride.trim().toLowerCase());
        packReloadFuture = CompletableFuture.completedFuture(null);
        packReloadFailed = false;
        worldReady = definition != null && !firstJoin;
    }

    /** Backwards-compatible helper for callers without a source pack. */
    public static void prepare(DAI_ExperienceDefinition definition, boolean firstJoin) {
        prepare(definition, firstJoin, null);
    }

    public static Pending pending() {
        return pending;
    }

    public static void setPackReloadFuture(CompletableFuture<?> future) {
        CompletableFuture<?> safe = future == null
                ? CompletableFuture.completedFuture(null)
                : future;
        packReloadFuture = safe;
        packReloadFailed = false;
        safe.whenComplete((ignored, error) -> {
            if (error != null) packReloadFailed = true;
        });
    }

    public static CompletableFuture<?> packReloadFuture() {
        return packReloadFuture;
    }

    public static boolean packReady() {
        CompletableFuture<?> future = packReloadFuture;
        return future == null || future.isDone();
    }

    public static boolean packReloadFailed() {
        return packReloadFailed;
    }

    /** True once first-start world bootstrap has finished on the integrated server. */
    public static boolean worldReady() {
        return worldReady;
    }

    public static void markWorldReady() {
        worldReady = true;
    }

    public static void clear() {
        pending = null;
        packReloadFuture = CompletableFuture.completedFuture(null);
        packReloadFailed = false;
        worldReady = false;
    }

    public static Pending consumeClient() {
        Pending value = pending;
        pending = null;
        return value;
    }

    public record Pending(
            DAI_ExperienceDefinition definition,
            boolean firstJoin,
            Path sourcePack,
            String worldgenOverride
    ) {}
}

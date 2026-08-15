package io.github.j12h36h.dai.client.api;

import io.github.j12h36h.dai.api.DAI_Reference;
import io.github.j12h36h.dai.api.DAI_StateStore;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DAI_ReferenceStore {

    private static final Map<String, DAI_Reference> REFERENCES =
            new ConcurrentHashMap<>();

    private DAI_ReferenceStore() {
        // Utility class.
    }

    public static void rememberEntity(
            String key,
            Entity entity
    ) {

        if (entity == null) {
            throw new IllegalArgumentException(
                    "Cannot remember a null entity."
            );
        }

        REFERENCES.put(
                requireKey(key),
                DAI_Reference.entity(
                        entity.getUUID(),
                        entity.position(),
                        currentGameTime()
                )
        );
    }

    public static void rememberBlock(
            String key,
            BlockPos position
    ) {

        REFERENCES.put(
                requireKey(key),
                DAI_Reference.block(
                        position,
                        currentGameTime()
                )
        );
    }

    public static void rememberPosition(
            String key,
            Vec3 position
    ) {

        REFERENCES.put(
                requireKey(key),
                DAI_Reference.position(
                        position,
                        currentGameTime()
                )
        );
    }

    public static @Nullable DAI_Reference get(
            String key
    ) {

        String normalized =
                normalizeKey(key);

        if (normalized.isEmpty()) {
            return null;
        }

        return REFERENCES.get(
                normalized
        );
    }

    public static boolean contains(
            String key
    ) {
        return get(key) != null;
    }

    public static @Nullable Entity resolveEntity(
            String key
    ) {

        DAI_Reference reference =
                get(key);

        if (
                reference == null
                        || reference.type()
                        != DAI_Reference.Type.ENTITY
        ) {
            return null;
        }

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft == null
                        || minecraft.level == null
        ) {
            return null;
        }

        for (
                Entity entity
                : minecraft.level.entitiesForRendering()
        ) {

            if (
                    reference.entityId()
                            .equals(
                                    entity.getUUID()
                            )
                            && !entity.isRemoved()
            ) {
                return entity;
            }
        }

        return null;
    }

    public static Vec3 resolvePosition(
            String key
    ) {

        DAI_Reference reference =
                get(key);

        if (reference == null) {
            return null;
        }

        if (
                reference.type()
                        == DAI_Reference.Type.ENTITY
        ) {

            Entity entity =
                    resolveEntity(
                            key
                    );

            if (entity != null) {
                return entity.position();
            }
        }

        return reference.position();
    }

    public static long ageTicks(
            String key
    ) {

        DAI_Reference reference =
                get(key);

        if (reference == null) {
            return Long.MAX_VALUE;
        }

        return Math.max(
                0L,
                currentGameTime()
                        - reference.capturedGameTime()
        );
    }

    public static void remove(
            String key
    ) {

        String normalized =
                normalizeKey(key);

        if (!normalized.isEmpty()) {
            REFERENCES.remove(
                    normalized
            );
        }
    }

    public static Map<String, DAI_Reference> snapshot() {
        return Map.copyOf(REFERENCES);
    }

    public static void clear() {
        REFERENCES.clear();
    }

    private static long currentGameTime() {

        Minecraft minecraft =
                Minecraft.getInstance();

        return minecraft != null
                && minecraft.level != null
                ? minecraft.level.getGameTime()
                : 0L;
    }

    private static String normalizeKey(
            String key
    ) {

        return DAI_StateStore.normalizeKey(
                key
        );
    }

    private static String requireKey(
            String key
    ) {

        String normalized =
                normalizeKey(key);

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "Reference key cannot be null or blank."
            );
        }

        return normalized;
    }
}

package io.github.j12h36h.dai.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Process-local bridge between the physical client shell launcher and the
 * integrated server. The reserved DAI shell world must never receive normal
 * standalone ADDON synchronization or experience bootstrap behavior.
 *
 * The marker handles every load after the first one. The armed bit handles the
 * first server start, before the client has had a chance to write the marker.
 */
public final class DAI_ShellSessionState {

    private static final String MARKER = "dai/shell.json";
    private static final AtomicBoolean ARMED = new AtomicBoolean(false);

    private DAI_ShellSessionState() {}

    /** Arm exactly one upcoming integrated-server start as the DAI shell. */
    public static void arm() {
        ARMED.set(true);
    }

    /** Clear an arm when a client-side create/open flow is cancelled or fails. */
    public static void clearArm() {
        ARMED.set(false);
    }

    /**
     * Returns true when this world is the reserved DAI shell.
     *
     * Existing shell worlds are identified by their persistent marker. During
     * first creation there is no marker yet, so the client-supplied arm is
     * consumed once by the integrated server start.
     */
    public static boolean consumeForWorld(Path worldRoot) {
        if (worldRoot != null) {
            try {
                if (Files.isRegularFile(worldRoot.resolve(MARKER))) {
                    ARMED.set(false);
                    return true;
                }
            } catch (Throwable ignored) {
                // Fall through to the one-shot process-local arm.
            }
        }
        return ARMED.compareAndSet(true, false);
    }
}

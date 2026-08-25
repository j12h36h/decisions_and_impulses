package io.github.j12h36h.dai.client.animations;

import io.github.j12h36h.dai.animations.DAI_AnimationDefinition;
import io.github.j12h36h.dai.animations.DAI_AnimationKeyframe;
import io.github.j12h36h.dai.animations.DAI_AnimationRegistry;
import io.github.j12h36h.dai.animations.DAI_AnimationSink;

import io.github.j12h36h.dai.client.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DAI_AnimationRuntime {

    private static final Map<Integer, Map<String, Active>> ACTIVE = new HashMap<>();
    private static final Set<String> FINISHED = new HashSet<>();
    private static final List<DAI_AnimationSink> SINKS = new ArrayList<>();

    private DAI_AnimationRuntime() {}

    public record Transform(
            double x, double y, double z,
            double pitch, double yaw, double roll,
            double scaleX, double scaleY, double scaleZ
    ) {
        public static final Transform IDENTITY = new Transform(0,0,0,0,0,0,1,1,1);
    }

    /** Samples the highest-priority full-entity/root track for native mesh rendering. */
    public static Transform sample(Entity entity, float partialTick) {
        if (entity == null) return Transform.IDENTITY;
        Map<String, Active> channels = ACTIVE.get(entity.getId());
        if (channels == null || channels.isEmpty()) return Transform.IDENTITY;
        Active best = null;
        List<DAI_AnimationKeyframe> track = null;
        for (Active active : channels.values()) {
            List<DAI_AnimationKeyframe> candidate = rootTrack(active.definition);
            if (candidate == null || candidate.isEmpty()) continue;
            if (best == null || active.definition.priority() >= best.definition.priority()) {
                best = active;
                track = candidate;
            }
        }
        if (best == null || track == null) return Transform.IDENTITY;
        double time = best.tick + Math.max(0.0D, Math.min(1.0D, partialTick));
        return interpolate(track, time);
    }

    private static List<DAI_AnimationKeyframe> rootTrack(DAI_AnimationDefinition definition) {
        if (definition == null || definition.tracks().isEmpty()) return null;
        for (String key : List.of("root", "full_body", "entity")) {
            List<DAI_AnimationKeyframe> track = definition.tracks().get(key);
            if (track != null && !track.isEmpty()) return track;
        }
        return null;
    }

    private static Transform interpolate(List<DAI_AnimationKeyframe> rawTrack, double time) {
        List<DAI_AnimationKeyframe> track = rawTrack.stream()
                .filter(frame -> frame != null)
                .sorted(java.util.Comparator.comparingInt(DAI_AnimationKeyframe::tick))
                .toList();
        if (track.isEmpty()) return Transform.IDENTITY;
        if (track.size() == 1 || time <= track.getFirst().tick()) return transform(track.getFirst());
        if (time >= track.getLast().tick()) return transform(track.getLast());
        DAI_AnimationKeyframe from = track.getFirst();
        DAI_AnimationKeyframe to = track.getLast();
        for (int i = 1; i < track.size(); i++) {
            if (time <= track.get(i).tick()) {
                from = track.get(i - 1);
                to = track.get(i);
                break;
            }
        }
        double duration = Math.max(1.0D, to.tick() - from.tick());
        double alpha = Math.max(0.0D, Math.min(1.0D, (time - from.tick()) / duration));
        if (from.interpolation().equals("step")) alpha = 0.0D;
        else {
            alpha = ease(alpha, from.easing());
            if (from.interpolation().equals("smooth") || from.interpolation().equals("cubic")) {
                alpha = alpha * alpha * (3.0D - 2.0D * alpha);
            }
        }
        return new Transform(
                lerp(from.x(), to.x(), alpha), lerp(from.y(), to.y(), alpha), lerp(from.z(), to.z(), alpha),
                lerp(from.pitch(), to.pitch(), alpha), lerp(from.yaw(), to.yaw(), alpha), lerp(from.roll(), to.roll(), alpha),
                lerp(from.scaleX(), to.scaleX(), alpha), lerp(from.scaleY(), to.scaleY(), alpha), lerp(from.scaleZ(), to.scaleZ(), alpha)
        );
    }

    private static Transform transform(DAI_AnimationKeyframe frame) {
        return new Transform(frame.x(), frame.y(), frame.z(), frame.pitch(), frame.yaw(), frame.roll(),
                frame.scaleX(), frame.scaleY(), frame.scaleZ());
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    private static double ease(double t, String raw) {
        String easing = raw == null ? "linear" : raw.trim().toLowerCase();
        return switch (easing) {
            case "ease_in", "in", "quad_in" -> t * t;
            case "ease_out", "out", "quad_out" -> 1.0D - (1.0D - t) * (1.0D - t);
            case "ease_in_out", "in_out", "smoothstep" -> t * t * (3.0D - 2.0D * t);
            case "cubic_in" -> t * t * t;
            case "cubic_out" -> 1.0D - Math.pow(1.0D - t, 3.0D);
            default -> t;
        };
    }


    public static void registerSink(DAI_AnimationSink sink) {
        if (sink != null && !SINKS.contains(sink)) SINKS.add(sink);
    }

    public static boolean play(Entity entity, String animationId) {
        DAI_AnimationDefinition definition = DAI_AnimationRegistry.get(animationId);
        if (entity == null || definition == null) return false;

        Map<String, Active> channels = ACTIVE.computeIfAbsent(entity.getId(), ignored -> new HashMap<>());
        Active existing = channels.get(definition.channel());
        if (existing != null) {
            if (!existing.definition.interruptible() && existing.definition.priority() > definition.priority()) {
                return false;
            }
            stop(entity, existing.id);
        }

        Active active = new Active(animationId.trim().toLowerCase(), definition);
        channels.put(definition.channel(), active);
        FINISHED.remove(finishKey(entity, active.id));
        for (DAI_AnimationSink sink : List.copyOf(SINKS)) sink.onPlay(entity, active.id, definition);
        fireMarkers(active, 0);
        return true;
    }

    public static boolean stop(Entity entity, String animationId) {
        if (entity == null) return false;
        Map<String, Active> channels = ACTIVE.get(entity.getId());
        if (channels == null) return false;
        String id = normalize(animationId);
        Active found = null;
        String foundChannel = null;
        for (Map.Entry<String, Active> entry : channels.entrySet()) {
            if (id.isEmpty() || entry.getValue().id.equals(id)) {
                found = entry.getValue();
                foundChannel = entry.getKey();
                break;
            }
        }
        if (found == null) return false;
        channels.remove(foundChannel);
        for (DAI_AnimationSink sink : List.copyOf(SINKS)) sink.onStop(entity, found.id, found.definition);
        FINISHED.add(finishKey(entity, found.id));
        if (channels.isEmpty()) ACTIVE.remove(entity.getId());
        return true;
    }

    public static boolean pause(Entity entity, String animationId) {
        Active active = findActive(entity, animationId);
        if (active == null) return false;
        active.paused = true;
        return true;
    }

    public static boolean resume(Entity entity, String animationId) {
        Active active = findActive(entity, animationId);
        if (active == null) return false;
        active.paused = false;
        return true;
    }

    public static boolean isPaused(Entity entity, String animationId) {
        Active active = findActive(entity, animationId);
        return active != null && active.paused;
    }

    public static boolean isPlaying(Entity entity, String animationId) {
        if (entity == null) return false;
        Map<String, Active> channels = ACTIVE.get(entity.getId());
        if (channels == null) return false;
        String id = normalize(animationId);
        return channels.values().stream().anyMatch(active -> id.isEmpty() || active.id.equals(id));
    }

    public static boolean finished(Entity entity, String animationId) {
        return entity != null && FINISHED.contains(finishKey(entity, normalize(animationId)));
    }

    public static int tickOf(Entity entity, String animationId) {
        if (entity == null) return -1;
        Map<String, Active> channels = ACTIVE.get(entity.getId());
        if (channels == null) return -1;
        String id = normalize(animationId);
        return channels.values().stream()
                .filter(active -> id.isEmpty() || active.id.equals(id))
                .mapToInt(active -> active.tick)
                .findFirst()
                .orElse(-1);
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || ACTIVE.isEmpty()) return;

        List<Runnable> completions = new ArrayList<>();
        for (Map.Entry<Integer, Map<String, Active>> entityEntry : List.copyOf(ACTIVE.entrySet())) {
            Entity entity = minecraft.level.getEntity(entityEntry.getKey());
            if (entity == null) continue;

            for (Active active : List.copyOf(entityEntry.getValue().values())) {
                if (active.paused) continue;
                active.tick++;
                fireMarkers(active, active.tick);
                for (DAI_AnimationSink sink : List.copyOf(SINKS)) {
                    sink.onTick(entity, active.id, active.definition, active.tick);
                }
                if (active.tick >= active.definition.durationTicks()) {
                    if (active.definition.loop()) {
                        active.tick = 0;
                        active.firedMarkers.clear();
                        fireMarkers(active, 0);
                    } else {
                        Entity finalEntity = entity;
                        String finalId = active.id;
                        completions.add(() -> stop(finalEntity, finalId));
                    }
                }
            }
        }
        completions.forEach(Runnable::run);
    }


    /**
     * Rebinds active animations to the definitions produced by the latest
     * datapack reload. Removed animations stop immediately; edited tracks,
     * markers, duration, priority, and looping rules become live without a
     * game restart.
     */
    public static void rebindReloadedDefinitions() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || ACTIVE.isEmpty()) return;

        int rebound = 0;
        int removed = 0;

        for (Map.Entry<Integer, Map<String, Active>> entityEntry : new ArrayList<>(ACTIVE.entrySet())) {
            Entity entity = minecraft.level.getEntity(entityEntry.getKey());
            Map<String, Active> previousChannels = entityEntry.getValue();
            if (previousChannels == null) continue;

            Map<String, Active> refreshedChannels = new HashMap<>();

            for (Active active : new ArrayList<>(previousChannels.values())) {
                DAI_AnimationDefinition refreshed = DAI_AnimationRegistry.get(active.id);
                if (refreshed == null) {
                    if (entity != null) {
                        for (DAI_AnimationSink sink : List.copyOf(SINKS)) {
                            sink.onStop(entity, active.id, active.definition);
                        }
                        FINISHED.add(finishKey(entity, active.id));
                    }
                    removed++;
                    continue;
                }

                active.definition = refreshed;
                if (active.tick >= refreshed.durationTicks()) {
                    if (refreshed.loop()) {
                        active.tick = Math.floorMod(active.tick, Math.max(1, refreshed.durationTicks()));
                        active.firedMarkers.clear();
                    } else {
                        if (entity != null) {
                            for (DAI_AnimationSink sink : List.copyOf(SINKS)) {
                                sink.onStop(entity, active.id, refreshed);
                            }
                            FINISHED.add(finishKey(entity, active.id));
                        }
                        removed++;
                        continue;
                    }
                }

                Active collision = refreshedChannels.get(refreshed.channel());
                if (collision == null || refreshed.priority() >= collision.definition.priority()) {
                    if (collision != null && entity != null) {
                        for (DAI_AnimationSink sink : List.copyOf(SINKS)) {
                            sink.onStop(entity, collision.id, collision.definition);
                        }
                        FINISHED.add(finishKey(entity, collision.id));
                        removed++;
                    }
                    refreshedChannels.put(refreshed.channel(), active);
                    rebound++;
                } else {
                    if (entity != null) {
                        for (DAI_AnimationSink sink : List.copyOf(SINKS)) {
                            sink.onStop(entity, active.id, refreshed);
                        }
                        FINISHED.add(finishKey(entity, active.id));
                    }
                    removed++;
                }
            }

            if (refreshedChannels.isEmpty()) {
                ACTIVE.remove(entityEntry.getKey());
            } else {
                ACTIVE.put(entityEntry.getKey(), refreshedChannels);
            }
        }

        if (rebound > 0 || removed > 0) {
            DAI_Core.LOGGER.info(
                    "<DAI>: Hot reload rebound {} active animation(s); {} stale/removed animation(s) were stopped.",
                    rebound,
                    removed
            );
        }
    }

    public static void clear() {
        ACTIVE.clear();
        FINISHED.clear();
    }

    private static void fireMarkers(Active active, int tick) {
        for (Map.Entry<String, Integer> marker : active.definition.markers().entrySet()) {
            if (marker.getValue() != tick || !active.firedMarkers.add(marker.getKey())) continue;
            String action = active.definition.markerActions().get(marker.getKey());
            if (action != null && !action.isBlank()) {
                DAI_ActionQueue.enqueueDeferredReference(action);
            }
        }
    }

    private static Active findActive(Entity entity, String animationId) {
        if (entity == null) return null;
        Map<String, Active> channels = ACTIVE.get(entity.getId());
        if (channels == null) return null;
        String id = normalize(animationId);
        return channels.values().stream()
                .filter(active -> id.isEmpty() || active.id.equals(id))
                .findFirst()
                .orElse(null);
    }

    private static String finishKey(Entity entity, String id) {
        return entity.getUUID() + "|" + normalize(id);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static final class Active {
        private final String id;
        private DAI_AnimationDefinition definition;
        private int tick;
        private boolean paused;
        private final Set<String> firedMarkers = new HashSet<>();

        private Active(String id, DAI_AnimationDefinition definition) {
            this.id = id;
            this.definition = definition;
        }
    }
}

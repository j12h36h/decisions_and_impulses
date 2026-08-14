package io.github.j12h36h.dai.animations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;

public record DAI_AnimationDefinition(
        int durationTicks,
        boolean loop,
        String channel,
        int priority,
        boolean interruptible,
        Map<String, Integer> markers,
        Map<String, String> markerActions,
        Map<String, List<DAI_AnimationKeyframe>> tracks
) {
    private static final Codec<Map<String, Integer>> MARKERS =
            Codec.unboundedMap(Codec.STRING, Codec.INT);
    private static final Codec<Map<String, String>> ACTIONS =
            Codec.unboundedMap(Codec.STRING, Codec.STRING);
    private static final Codec<Map<String, List<DAI_AnimationKeyframe>>> TRACKS =
            Codec.unboundedMap(Codec.STRING, DAI_AnimationKeyframe.CODEC.listOf());

    public static final Codec<DAI_AnimationDefinition> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.fieldOf("duration_ticks").forGetter(DAI_AnimationDefinition::durationTicks),
                    Codec.BOOL.optionalFieldOf("loop", false).forGetter(DAI_AnimationDefinition::loop),
                    Codec.STRING.optionalFieldOf("channel", "full_body").forGetter(DAI_AnimationDefinition::channel),
                    Codec.INT.optionalFieldOf("priority", 0).forGetter(DAI_AnimationDefinition::priority),
                    Codec.BOOL.optionalFieldOf("interruptible", true).forGetter(DAI_AnimationDefinition::interruptible),
                    MARKERS.optionalFieldOf("markers", Map.of()).forGetter(DAI_AnimationDefinition::markers),
                    ACTIONS.optionalFieldOf("marker_actions", Map.of()).forGetter(DAI_AnimationDefinition::markerActions),
                    TRACKS.optionalFieldOf("tracks", Map.of()).forGetter(DAI_AnimationDefinition::tracks)
            ).apply(instance, DAI_AnimationDefinition::new));

    public DAI_AnimationDefinition {
        if (durationTicks <= 0) throw new IllegalArgumentException("Animation duration_ticks must be positive.");
        channel = channel == null || channel.isBlank() ? "full_body" : channel.trim().toLowerCase();
        markers = markers == null ? Map.of() : Map.copyOf(markers);
        markerActions = markerActions == null ? Map.of() : Map.copyOf(markerActions);
        tracks = tracks == null ? Map.of() : Map.copyOf(tracks);
        for (Map.Entry<String, Integer> marker : markers.entrySet()) {
            if (marker.getValue() < 0 || marker.getValue() > durationTicks) {
                throw new IllegalArgumentException("Animation marker '" + marker.getKey() + "' is outside duration.");
            }
        }
    }
}

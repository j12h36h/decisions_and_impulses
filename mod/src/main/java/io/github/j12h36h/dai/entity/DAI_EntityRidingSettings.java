package io.github.j12h36h.dai.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/** Passenger/seat exposure for native DAI entities. */
public record DAI_EntityRidingSettings(
        List<String> seats,
        boolean riderSit,
        boolean riderInteract,
        boolean rideUnderFluid
) {
    public static final DAI_EntityRidingSettings DEFAULT =
            new DAI_EntityRidingSettings(List.of(), true, false, false);

    public static final Codec<DAI_EntityRidingSettings> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.listOf().optionalFieldOf("seats", List.of()).forGetter(DAI_EntityRidingSettings::seats),
                    Codec.BOOL.optionalFieldOf("rider_sit", true).forGetter(DAI_EntityRidingSettings::riderSit),
                    Codec.BOOL.optionalFieldOf("rider_interact", false).forGetter(DAI_EntityRidingSettings::riderInteract),
                    Codec.BOOL.optionalFieldOf("ride_under_fluid", false).forGetter(DAI_EntityRidingSettings::rideUnderFluid)
            ).apply(instance, DAI_EntityRidingSettings::new));

    public DAI_EntityRidingSettings {
        seats = seats == null ? List.of() : seats.stream()
                .filter(v -> v != null && !v.isBlank()).map(String::trim).toList();
    }

    public double[] seat(int index) {
        if (index < 0 || index >= seats.size()) return null;
        String[] parts = seats.get(index).trim().split("\\s+");
        if (parts.length < 3) return null;
        try {
            return new double[] {
                    Double.parseDouble(parts[0]),
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2])
            };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}

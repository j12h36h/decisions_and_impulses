package io.github.j12h36h.dai.client.logics.validation;

import io.github.j12h36h.dai.animations.DAI_AnimationDefinition;
import io.github.j12h36h.dai.animations.DAI_AnimationRegistry;
import io.github.j12h36h.dai.attributes.DAI_AttributeDefinition;
import io.github.j12h36h.dai.attributes.DAI_AttributeRegistry;
import io.github.j12h36h.dai.attributes.DAI_NativeAttributeSupport;
import io.github.j12h36h.dai.content.DAI_ContentKind;
import io.github.j12h36h.dai.content.DAI_ContentRegistry;
import io.github.j12h36h.dai.entity.DAI_EntityTemplateRegistry;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationKind;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationRegistry;
import io.github.j12h36h.dai.logics.action.DAI_ActionLibrary;
import io.github.j12h36h.dai.registry.DAI_RegistrySpec;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.Map;

/** Validation for reloadable DAI extension registries. */
public final class DAI_ExtensionValidator {

    private DAI_ExtensionValidator() {}

    public static void validate() {
        validateAttributes();
        validateAnimations();
        validateContent();
    }

    private static void validateAttributes() {
        for (String id : DAI_AttributeRegistry.ids()) {
            DAI_AttributeDefinition definition = DAI_AttributeRegistry.get(id);
            if (definition == null) continue;
            if (!definition.nativeBinding().isBlank()
                    && DAI_NativeAttributeSupport.resolve(definition.nativeBinding()) == null) {
                DAI_ValidationReport.error(
                        "dai_attributes/" + id,
                        "Unknown native_binding '" + definition.nativeBinding() + "'."
                );
            }
        }
    }

    private static void validateAnimations() {
        for (String id : DAI_AnimationRegistry.ids()) {
            DAI_AnimationDefinition definition = DAI_AnimationRegistry.get(id);
            if (definition == null) continue;

            for (Map.Entry<String, java.util.List<io.github.j12h36h.dai.animations.DAI_AnimationKeyframe>> track : definition.tracks().entrySet()) {
                for (var keyframe : track.getValue()) {
                    if (keyframe.tick() > definition.durationTicks()) {
                        DAI_ValidationReport.error(
                                "dai_animations/" + id + ".tracks." + track.getKey(),
                                "Keyframe tick " + keyframe.tick() + " exceeds duration_ticks=" + definition.durationTicks() + "."
                        );
                    }
                }
            }

            for (Map.Entry<String, String> marker : definition.markerActions().entrySet()) {
                if (!definition.markers().containsKey(marker.getKey())) {
                    DAI_ValidationReport.warning(
                            "dai_animations/" + id,
                            "marker_actions contains '" + marker.getKey() + "' but markers does not define it."
                    );
                }
                validateActionReference(
                        "dai_animations/" + id + ".marker_actions." + marker.getKey(),
                        marker.getValue()
                );
            }
        }
    }

    private static void validateContent() {
        for (String id : DAI_ContentRegistry.ids()) {
            DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(id);
            if (entry == null) continue;
            var definition = entry.definition();
            String source = entry.kind().folder() + "/" + id;

            if (!definition.carrier().isBlank()
                    && Identifier.tryParse(definition.carrier()) == null) {
                DAI_ValidationReport.error(source, "carrier must be a valid resource identifier.");
            }
            if (!definition.model().isBlank()
                    && Identifier.tryParse(definition.model()) == null) {
                DAI_ValidationReport.error(source, "model must be a valid resource identifier.");
            }

            if (definition.registryBacked()) {
                DAI_RegistrySpec.NativeRegistry nativeRegistry =
                        DAI_RegistrySpec.NativeRegistry.parse(
                                definition.nativeRegistry(),
                                entry.kind()
                        );

                if (nativeRegistry == null) {
                    DAI_ValidationReport.error(
                            source,
                            "native_registry must be 'item', 'block', or 'entity' when registry_backed=true."
                    );
                }
            }
            if (entry.kind() == DAI_ContentKind.ENTITY) {
                if (!definition.registryBacked()) {
                    DAI_ValidationReport.error(source, "DAI entities must set registry_backed=true.");
                }
                if (!DAI_EntityTemplateRegistry.supports(definition.carrier())) {
                    DAI_ValidationReport.error(
                            source,
                            "Unsupported vanilla entity template carrier '" + definition.carrier() + "'."
                    );
                }
                if (!definition.entity().behaviorSequence().isBlank()) {
                    validateActionReference(
                            source + ".entity.behavior_sequence",
                            definition.entity().behaviorSequence()
                    );
                }

                var gameplay = definition.entity().gameplay();
                validateCustomizationReference(source + ".entity.gameplay.faction", DAI_GameCustomizationKind.FACTION, gameplay.faction());
                validateCustomizationReference(source + ".entity.gameplay.dialogue", DAI_GameCustomizationKind.DIALOGUE, gameplay.dialogue());
                validateCustomizationReference(source + ".entity.gameplay.loot", DAI_GameCustomizationKind.LOOT, gameplay.loot());

                for (String equipment : gameplay.equipment()) {
                    if (equipment == null || equipment.isBlank()) continue;
                    String[] pair = equipment.trim().split("=", 2);
                    if (pair.length != 2 || !isEquipmentSlot(pair[0])) {
                        DAI_ValidationReport.error(
                                source + ".entity.gameplay.equipment",
                                "Equipment must use slot=item, got '" + equipment + "'."
                        );
                        continue;
                    }
                    Identifier itemId = Identifier.tryParse(pair[1].trim());
                    if (itemId == null || BuiltInRegistries.ITEM.getValue(itemId) == null) {
                        DAI_ValidationReport.error(
                                source + ".entity.gameplay.equipment",
                                "Unknown equipment item '" + pair[1].trim() + "'."
                        );
                    }
                }

                for (Map.Entry<String, String> event : gameplay.events().entrySet()) {
                    validateActionReference(source + ".entity.gameplay.events." + event.getKey(), event.getValue());
                }
            }

            for (String attribute : definition.attributes().keySet()) {
                if (!DAI_AttributeRegistry.contains(attribute)) {
                    DAI_ValidationReport.error(
                            source,
                            "Unknown custom attribute '" + attribute + "'."
                    );
                }
            }
            for (String attribute : definition.nativeAttributes().keySet()) {
                if (DAI_NativeAttributeSupport.resolve(attribute) == null) {
                    DAI_ValidationReport.error(
                            source,
                            "Unknown native attribute '" + attribute + "'."
                    );
                }
            }
            for (Map.Entry<String, String> event : definition.events().entrySet()) {
                validateActionReference(source + ".events." + event.getKey(), event.getValue());
            }
        }
    }

    private static void validateCustomizationReference(
            String source,
            DAI_GameCustomizationKind kind,
            String value
    ) {
        if (value == null || value.isBlank()) return;
        if (DAI_GameCustomizationRegistry.get(kind, value) == null) {
            DAI_ValidationReport.error(
                    source,
                    "Unknown " + kind.id() + " customization reference '" + value + "'."
            );
        }
    }

    private static boolean isEquipmentSlot(String raw) {
        String slot = raw == null ? "" : raw.trim().toLowerCase();
        return switch (slot) {
            case "mainhand", "main_hand", "hand", "offhand", "off_hand",
                    "head", "helmet", "chest", "chestplate",
                    "legs", "leggings", "feet", "boots" -> true;
            default -> false;
        };
    }

    private static void validateActionReference(String source, String value) {
        if (value == null || value.isBlank()) return;
        Identifier id = Identifier.tryParse(value.trim());
        if (id == null) {
            DAI_ValidationReport.error(source, "Invalid action reference '" + value + "'.");
        } else if (!DAI_ActionLibrary.contains(id)) {
            DAI_ValidationReport.error(source, "Unknown action reference '" + value + "'.");
        }
    }
}

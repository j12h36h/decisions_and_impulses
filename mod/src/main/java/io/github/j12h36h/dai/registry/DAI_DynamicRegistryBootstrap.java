package io.github.j12h36h.dai.registry;

import io.github.j12h36h.dai.entity.DAI_EntityTemplateRegistry;
import io.github.j12h36h.dai.content.DAI_BlockSettings;
import io.github.j12h36h.dai.content.DAI_JsonBlock;
import io.github.j12h36h.dai.content.DAI_JsonMobEffect;
import io.github.j12h36h.dai.attributes.DAI_NativeAttributeSupport;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.ToIntFunction;
import java.util.function.Predicate;

/**
 * Discovers DAI native ids at the earliest mod-bootstrap point and registers
 * them during NeoForge's normal static registry window.
 *
 * Definitions already installed on disk are discovered directly before world
 * selection, so they do not require a discovery restart. The persistent cache
 * is retained only for tombstones/save compatibility and for ids first added
 * while the JVM is already running.
 */
public final class DAI_DynamicRegistryBootstrap {

    private static boolean initialized;

    private static Map<String, DAI_RegistrySpec> bootSpecs = Map.of();
    private static Set<String> startupConflicts = Set.of();

    private static final Set<String> REGISTERED_KEYS = new LinkedHashSet<>();
    private static final Set<String> REGISTERED_BLOCK_KEYS = new LinkedHashSet<>();
    private static final Map<String, Block> REGISTERED_BLOCKS = new LinkedHashMap<>();
    private static final Map<String, EntityType<? extends Mob>> REGISTERED_ENTITY_TYPES = new LinkedHashMap<>();
    private static final Map<String, SimpleParticleType> REGISTERED_PARTICLE_TYPES = new LinkedHashMap<>();

    private DAI_DynamicRegistryBootstrap() {}

    public static synchronized void initialize(IEventBus modBus) {
        if (initialized) return;
        initialized = true;

        DAI_EarlyRegistryScanner.ScanResult scan = DAI_EarlyRegistryScanner.scan();
        Map<String, DAI_RegistrySpec> cached = scan.specs().isEmpty()
                ? Map.of()
                : DAI_RegistryCache.load();

        LinkedHashMap<String, DAI_RegistrySpec> plan = new LinkedHashMap<>();

        // Current on-disk definitions win over historical cache entries. This
        // allows static properties to change between JVM launches while still
        // retaining removed ids as tombstones.
        for (DAI_RegistrySpec spec : scan.specs().values()) {
            removeSameId(plan, spec.id());
            plan.put(spec.key(), spec);
        }

        for (DAI_RegistrySpec spec : cached.values()) {
            if (spec == null || containsId(plan, spec.id())) continue;
            plan.put(spec.key(), spec);
        }

        bootSpecs = Collections.unmodifiableMap(plan);
        startupConflicts = Collections.unmodifiableSet(
                new LinkedHashSet<>(scan.conflicts())
        );

        // Refresh the tombstone cache with anything found before registration.
        // Client-only generated model aliases are prepared separately by the
        // physical-client bootstrap so dedicated servers never load that path.
        DAI_RegistryCache.merge(scan.specs().values());

        modBus.addListener(DAI_DynamicRegistryBootstrap::register);

        DAI_Core.LOGGER.info(
                "<DAI>: Early registry scan prepared {} native id(s): {} active from disk, {} retained/cached.",
                bootSpecs.size(),
                scan.specs().size(),
                Math.max(0, bootSpecs.size() - scan.specs().size())
        );

        if (!scan.sources().isEmpty()) {
            DAI_Core.LOGGER.info(
                    "<DAI>: Early registry scanner inspected {} DAI content source(s).",
                    scan.sources().size()
            );
        }

        for (String conflict : startupConflicts) {
            DAI_Core.LOGGER.error(
                    "<DAI>: Early registry conflict: {}.",
                    conflict
            );
        }
    }

    public static Map<String, DAI_RegistrySpec> bootSpecs() {
        return bootSpecs;
    }

    public static boolean hasNativeContent() {
        return !bootSpecs.isEmpty();
    }

    public static Set<String> startupConflicts() {
        return startupConflicts;
    }

    public static EntityType<? extends Mob> entityType(DAI_RegistrySpec spec) {
        if (spec == null) return null;
        return REGISTERED_ENTITY_TYPES.get(spec.key());
    }

    public static SimpleParticleType particleType(DAI_RegistrySpec spec) {
        if (spec == null) return null;
        return REGISTERED_PARTICLE_TYPES.get(spec.key());
    }

    /** Returns true only when DAI itself successfully registered this id. */
    public static boolean registeredByDai(DAI_RegistrySpec spec) {
        return spec != null && REGISTERED_KEYS.contains(spec.key());
    }

    private static void register(RegisterEvent event) {
        for (DAI_RegistrySpec spec : bootSpecs.values()) {
            Identifier id = spec.identifier();
            if (id == null) continue;

            switch (spec.nativeRegistry()) {
                case BLOCK -> {
                    registerBlock(event, spec, id);
                    registerBlockItem(event, spec, id);
                }
                case ENTITY -> registerEntity(event, spec, id);
                case EFFECT -> registerEffect(event, spec, id);
                case POTION -> registerPotion(event, spec, id);
                case PARTICLE -> registerParticle(event, spec, id);
                case ITEM -> registerItem(event, spec, id);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerEntity(
            RegisterEvent event,
            DAI_RegistrySpec spec,
            Identifier id
    ) {
        if (!event.getRegistryKey().equals(Registries.ENTITY_TYPE)) return;

        if (!DAI_EntityTemplateRegistry.supports(spec.carrier())) {
            DAI_Core.LOGGER.error(
                    "<DAI>: Cannot register custom entity '{}' because carrier/model template '{}' is unsupported.",
                    id, spec.carrier()
            );
            return;
        }

        try {
            ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id);
            MobCategory category = DAI_EntityTemplateRegistry.category(spec.entityCategory(), spec.carrier());

            EntityType.Builder<Mob> builder = EntityType.Builder.<Mob>of(
                    (type, level) -> DAI_EntityTemplateRegistry.create(type, level, spec.carrier()),
                    category
            )
                    .sized(spec.entityWidth(), spec.entityHeight())
                    .clientTrackingRange(spec.entityTrackingRange())
                    .updateInterval(spec.entityUpdateInterval());

            if (spec.entityFireImmune()) builder.fireImmune();
            if (!spec.entitySummonable()) builder.noSummon();
            if (!spec.entitySaveable()) builder.noSave();

            EntityType<Mob> entityType = builder.build((ResourceKey) key);
            event.register(Registries.ENTITY_TYPE, id, () -> entityType);
            REGISTERED_ENTITY_TYPES.put(spec.key(), entityType);
            REGISTERED_KEYS.add(spec.key());
            if (DAI_EntityTemplateRegistry.isNative(spec.carrier())) {
                DAI_Core.LOGGER.info(
                        "<DAI>: Registered early native DAI entity '{}' with JSON-owned AI/hitbox.",
                        id
                );
            } else {
                DAI_Core.LOGGER.info(
                        "<DAI>: Registered early DAI entity '{}' using legacy vanilla template '{}'.",
                        id, spec.carrier()
                );
            }
        } catch (RuntimeException exception) {
            DAI_Core.LOGGER.error(
                    "<DAI>: Could not register early DAI entity '{}'. The id may already be owned by another mod.",
                    id, exception
            );
        }
    }



    private static void registerParticle(RegisterEvent event, DAI_RegistrySpec spec, Identifier id) {
        if (!event.getRegistryKey().equals(Registries.PARTICLE_TYPE)) return;
        try {
            SimpleParticleType type = new SimpleParticleType(false);
            event.register(Registries.PARTICLE_TYPE, id, () -> type);
            REGISTERED_PARTICLE_TYPES.put(spec.key(), type);
            REGISTERED_KEYS.add(spec.key());
            DAI_Core.LOGGER.info("<DAI>: Registered early DAI particle type '{}'.", id);
        } catch (RuntimeException exception) {
            DAI_Core.LOGGER.error(
                    "<DAI>: Could not register early DAI particle type '{}'. The id may already be owned by another mod.",
                    id, exception
            );
        }
    }

    private static void registerEffect(RegisterEvent event, DAI_RegistrySpec spec, Identifier id) {
        if (!event.getRegistryKey().equals(Registries.MOB_EFFECT)) return;
        try {
            MobEffectCategory category = switch (spec.effect().category()) {
                case "beneficial", "positive" -> MobEffectCategory.BENEFICIAL;
                case "harmful", "negative" -> MobEffectCategory.HARMFUL;
                default -> MobEffectCategory.NEUTRAL;
            };
            DAI_JsonMobEffect effect = new DAI_JsonMobEffect(spec.id(), category, spec.effect().color(), spec.effect().tickInterval());
            spec.nativeAttributes().forEach((attributeId, amount) -> {
                var attribute = DAI_NativeAttributeSupport.resolve(attributeId);
                Identifier modifier = Identifier.fromNamespaceAndPath(id.getNamespace(), "effect." + id.getPath() + "." + attributeId.replace(':','_').replace('/','_'));
                if (attribute != null) effect.addAttributeModifier(attribute, modifier, amount, AttributeModifier.Operation.ADD_VALUE);
            });
            event.register(Registries.MOB_EFFECT, id, () -> effect);
            REGISTERED_KEYS.add(spec.key());
            DAI_Core.LOGGER.info("<DAI>: Registered early native DAI mob effect '{}'.", id);
        } catch (RuntimeException ex) {
            DAI_Core.LOGGER.error("<DAI>: Could not register native DAI mob effect '{}'.", id, ex);
        }
    }

    private static void registerPotion(RegisterEvent event, DAI_RegistrySpec spec, Identifier id) {
        if (!event.getRegistryKey().equals(Registries.POTION)) return;
        try {
            java.util.ArrayList<MobEffectInstance> effects = new java.util.ArrayList<>();
            for (String raw : spec.potion().effects()) {
                if (raw == null || raw.isBlank()) continue;
                String[] parts = raw.trim().split("\\s+");
                Identifier effectId = Identifier.tryParse(parts[0]);
                if (effectId == null) continue;
                var holder = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.get(effectId).orElse(null);
                if (holder == null) continue;
                int duration = 200; int amplifier = 0;
                try { if (parts.length > 1) duration = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {}
                try { if (parts.length > 2) amplifier = Integer.parseInt(parts[2]); } catch (NumberFormatException ignored) {}
                effects.add(new MobEffectInstance(holder, Math.max(1,duration), Math.max(0,amplifier)));
            }
            Potion potion = new Potion(id.getPath(), effects.toArray(MobEffectInstance[]::new));
            event.register(Registries.POTION, id, () -> potion);
            REGISTERED_KEYS.add(spec.key());
            DAI_Core.LOGGER.info("<DAI>: Registered early native DAI potion '{}'.", id);
        } catch (RuntimeException ex) {
            DAI_Core.LOGGER.error("<DAI>: Could not register native DAI potion '{}'.", id, ex);
        }
    }

    private static void registerBlock(
            RegisterEvent event,
            DAI_RegistrySpec spec,
            Identifier id
    ) {
        if (!event.getRegistryKey().equals(Registries.BLOCK)) return;

        try {
            Block block = createBlock(spec, id);
            event.register(Registries.BLOCK, id, () -> block);
            REGISTERED_BLOCKS.put(spec.key(), block);
            REGISTERED_BLOCK_KEYS.add(spec.key());
            DAI_Core.LOGGER.info("<DAI>: Registered early DAI block '{}'.", id);
        } catch (RuntimeException exception) {
            DAI_Core.LOGGER.error(
                    "<DAI>: Could not register early DAI block '{}'. The id may already be owned by another mod.",
                    id,
                    exception
            );
        }
    }

    private static void registerItem(
            RegisterEvent event,
            DAI_RegistrySpec spec,
            Identifier id
    ) {
        if (!event.getRegistryKey().equals(Registries.ITEM)) return;

        try {
            event.register(
                    Registries.ITEM,
                    id,
                    () -> new Item(itemProperties(spec, id, false))
            );
            REGISTERED_KEYS.add(spec.key());
            DAI_Core.LOGGER.info("<DAI>: Registered early DAI item '{}'.", id);
        } catch (RuntimeException exception) {
            DAI_Core.LOGGER.error(
                    "<DAI>: Could not register early DAI item '{}'. The id may already be owned by another mod.",
                    id,
                    exception
            );
        }
    }

    private static void registerBlockItem(
            RegisterEvent event,
            DAI_RegistrySpec spec,
            Identifier id
    ) {
        if (!event.getRegistryKey().equals(Registries.ITEM)) return;

        if (!REGISTERED_BLOCK_KEYS.contains(spec.key())) {
            DAI_Core.LOGGER.error(
                    "<DAI>: Cannot register early block item '{}' because DAI did not successfully register its block.",
                    id
            );
            return;
        }

        Block block = REGISTERED_BLOCKS.get(spec.key());
        if (block == null) {
            DAI_Core.LOGGER.error(
                    "<DAI>: Cannot register early block item '{}' because its block instance is unavailable.",
                    id
            );
            return;
        }

        try {
            event.register(
                    Registries.ITEM,
                    id,
                    () -> new BlockItem(block, itemProperties(spec, id, true))
            );
            REGISTERED_KEYS.add(spec.key());
            DAI_Core.LOGGER.info("<DAI>: Registered early DAI block item '{}'.", id);
        } catch (RuntimeException exception) {
            DAI_Core.LOGGER.error(
                    "<DAI>: Could not register early DAI block item '{}'. The id may already be owned by another mod.",
                    id,
                    exception
            );
        }
    }

    private static Block createBlock(DAI_RegistrySpec spec, Identifier id) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        DAI_BlockSettings block = spec.block();

        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .setId(key)
                .destroyTime(block.hardness())
                .explosionResistance(block.explosionResistance())
                .sound(resolveSoundType(block.sound()));

        // The simple BlockBehaviour property surface changes far less often
        // than custom block subclasses, but reflective dispatch keeps DAI
        // source-compatible when Mojang renames/removes one optional builder
        // method in a future mapping. Missing optional properties simply fall
        // back to Minecraft defaults instead of preventing the mod from booting.
        invokeProperty(properties, "friction", block.friction());
        invokeProperty(properties, "speedFactor", block.speedFactor());
        invokeProperty(properties, "jumpFactor", block.jumpFactor());

        if (block.luminance() > 0) {
            ToIntFunction<BlockState> light = state -> block.luminance();
            invokeProperty(properties, "lightLevel", light);
        }
        if (block.requiresCorrectTool()) invokeNoArg(properties, "requiresCorrectToolForDrops");
        if (block.noOcclusion()) invokeNoArg(properties, "noOcclusion");
        if (block.noCollision()) invokeNoArg(properties, "noCollission");
        if (block.replaceable()) invokeNoArg(properties, "replaceable");
        if (block.randomTicks()) invokeNoArg(properties, "randomTicks");
        if (block.ignitedByLava()) invokeNoArg(properties, "ignitedByLava");
        if (block.emissiveRendering()) {
            Predicate<BlockState> emissive = state -> true;
            invokeProperty(properties, "emissiveRendering", emissive);
        }

        applyNamedBuilderValue(
                properties,
                "mapColor",
                "net.minecraft.world.level.material.MapColor",
                block.mapColor()
        );
        applyNamedBuilderValue(
                properties,
                "pushReaction",
                "net.minecraft.world.level.material.PushReaction",
                block.pushReaction()
        );

        return DAI_JsonBlock.create(properties, block);
    }

    private static SoundType resolveSoundType(String raw) {
        String name = normalizeNativeName(raw, "STONE");
        try {
            Field field = SoundType.class.getField(name);
            if (Modifier.isStatic(field.getModifiers()) && SoundType.class.isAssignableFrom(field.getType())) {
                Object value = field.get(null);
                if (value instanceof SoundType sound) return sound;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Fall through to the stable default.
        }
        return SoundType.STONE;
    }

    private static void invokeNoArg(Object target, String methodName) {
        if (target == null || methodName == null || methodName.isBlank()) return;
        try {
            Method method = target.getClass().getMethod(methodName);
            method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            DAI_Core.debug(
                    "<DAI>: Optional native block property method '{}' is unavailable in this Minecraft mapping.",
                    methodName
            );
        }
    }

    private static void invokeProperty(Object target, String methodName, Object value) {
        if (target == null || methodName == null || methodName.isBlank() || value == null) return;
        try {
            for (Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != 1) continue;
                Class<?> parameter = method.getParameterTypes()[0];
                if (!compatible(parameter, value.getClass())) continue;
                method.invoke(target, value);
                return;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Diagnosed by the debug message below.
        }
        DAI_Core.debug(
                "<DAI>: Optional native block property '{}' could not be applied with value '{}'.",
                methodName,
                value
        );
    }

    private static void applyNamedBuilderValue(
            Object target,
            String methodName,
            String className,
            String rawValue
    ) {
        if (rawValue == null || rawValue.isBlank()) return;
        try {
            Class<?> valueClass = Class.forName(className);
            Object value = resolveNamedValue(valueClass, rawValue);
            if (value != null) invokeProperty(target, methodName, value);
        } catch (ClassNotFoundException | LinkageError ignored) {
            DAI_Core.debug(
                    "<DAI>: Optional native block value class '{}' is unavailable.",
                    className
            );
        }
    }

    private static Object resolveNamedValue(Class<?> type, String raw) {
        if (type == null || raw == null || raw.isBlank()) return null;
        String normalized = normalizeNativeName(raw, "");

        if (type.isEnum()) {
            for (Object constant : type.getEnumConstants()) {
                if (constant instanceof Enum<?> value && value.name().equalsIgnoreCase(normalized)) {
                    return constant;
                }
            }
        }

        for (Field field : type.getFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (!type.isAssignableFrom(field.getType())) continue;
            if (!field.getName().equalsIgnoreCase(normalized)) continue;
            try {
                return field.get(null);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean compatible(Class<?> parameter, Class<?> valueType) {
        if (parameter.isAssignableFrom(valueType)) return true;
        if (!parameter.isPrimitive()) return false;
        return (parameter == float.class && valueType == Float.class)
                || (parameter == double.class && valueType == Double.class)
                || (parameter == int.class && valueType == Integer.class)
                || (parameter == long.class && valueType == Long.class)
                || (parameter == boolean.class && valueType == Boolean.class);
    }

    private static String normalizeNativeName(String raw, String fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        return raw.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(java.util.Locale.ROOT);
    }

    private static Item.Properties itemProperties(
            DAI_RegistrySpec spec,
            Identifier id,
            boolean blockItem
    ) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        Item.Properties properties = new Item.Properties().setId(key);

        if (blockItem) properties.useBlockDescriptionPrefix();

        if (spec.durability() > 0) {
            properties.durability(spec.durability());
        } else {
            properties.stacksTo(spec.stackSize());
        }

        Identifier model = Identifier.tryParse(spec.model());
        if (model != null) {
            properties.component(DataComponents.ITEM_MODEL, model);
        }

        if (!spec.displayName().isBlank()) {
            properties.component(
                    DataComponents.ITEM_NAME,
                    Component.literal(spec.displayName())
            );
        }

        DAI_NativeItemComponents.apply(properties, spec.nativeComponents());
        return properties;
    }

    private static boolean containsId(Map<String, DAI_RegistrySpec> map, String id) {
        for (DAI_RegistrySpec spec : map.values()) {
            if (spec != null && spec.id().equals(id)) return true;
        }
        return false;
    }

    private static void removeSameId(Map<String, DAI_RegistrySpec> map, String id) {
        map.entrySet().removeIf(entry ->
                entry.getValue() != null && entry.getValue().id().equals(id)
        );
    }
}

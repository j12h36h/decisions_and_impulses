package io.github.j12h36h.dai.menus.system;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.menus.DAI_MenuCategory;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class DAI_SystemLoader
        extends SimpleJsonResourceReloadListener<DAI_SystemDefinition> {

    private static final String AUTOMATION_ID =
            "automation";

    private static final String AVAILABLE_ID =
            "available";

    private static final String STOP_AUTOMATION_BUTTON_ID =
            "stop";

    private static final String STOP_AUTOMATION_ACTION =
            DAI_Core.MODID + ":stop_vanilla_gameplay";

    private final String folder;
    private final DAI_MenuCategory category;

    public DAI_SystemLoader(
            String folder,
            DAI_MenuCategory category
    ) {

        super(
                DAI_SystemDefinition.CODEC,
                FileToIdConverter.json(folder)
        );

        this.folder = folder;
        this.category = category;
    }

    @Override
    protected void apply(
            Map<Identifier, DAI_SystemDefinition> definitions,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {

        DAI_Core.LOGGER.info(
                "<DAI>: Reloading {} system definitions from '{}'.",
                category,
                folder
        );

        DAI_SystemManager.clear(category);

        List<Map.Entry<Identifier, DAI_SystemDefinition>> automationContributions =
                new ArrayList<>();

        definitions.entrySet()
                .stream()
                .sorted(
                        Comparator.comparing(
                                entry ->
                                        entry.getKey().toString()
                        )
                )
                .forEach(entry -> {

                    Identifier identifier =
                            entry.getKey();

                    DAI_SystemDefinition definition =
                            entry.getValue();

                    if (category == DAI_MenuCategory.ACTION) {

                        if (
                                isContribution(
                                        identifier,
                                        AUTOMATION_ID
                                )
                        ) {
                            automationContributions.add(entry);
                            return;
                        }

                        if (
                                isContribution(
                                        identifier,
                                        AVAILABLE_ID
                                )
                        ) {

                            registerAvailableDefinition(
                                    identifier,
                                    definition
                            );

                            return;
                        }
                    }

                    registerDefinition(
                            identifier,
                            definition
                    );
                });

        registerMergedContributions(
                AUTOMATION_ID,
                automationContributions,
                true
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Loaded {} source {} system definition(s), resulting in {} registered definition(s).",
                definitions.size(),
                category,
                DAI_SystemManager.size(category)
        );
    }

    private void registerDefinition(
            Identifier identifier,
            DAI_SystemDefinition definition
    ) {

        String id =
                identifier.getPath();

        DAI_Core.debug(
                "<DAI>: Registering {} system definition '{}' as '{}' with priority {} and {} button(s).",
                category,
                identifier,
                id,
                definition.priority(),
                definition.buttons().size()
        );

        DAI_SystemManager.register(
                category,
                id,
                definition
        );
    }

    private void registerAvailableDefinition(
            Identifier identifier,
            DAI_SystemDefinition definition
    ) {

        String path =
                identifier.getPath();

        String id;

        if (AVAILABLE_ID.equals(path)) {

            /*
             * Backward compatibility for the original single global
             * available.json definition.
             */
            id = AVAILABLE_ID;

        } else {

            id = path.substring(
                    AVAILABLE_ID.length() + 1
            );
        }

        if (id.isBlank()) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Ignoring available-action definition '{}' because it does not resolve to a menu id.",
                    identifier
            );

            return;
        }

        DAI_Core.debug(
                "<DAI>: Registering available-action definition '{}' as ACTION menu '{}'.",
                identifier,
                id
        );

        DAI_SystemManager.registerAvailableAction(
                id,
                definition
        );
    }

    private void registerMergedContributions(
            String id,
            List<Map.Entry<Identifier, DAI_SystemDefinition>> contributions,
            boolean pinStopLast
    ) {

        if (contributions.isEmpty()) {
            return;
        }

        DAI_SystemDefinition definition =
                mergeContributedDefinitions(
                        id,
                        contributions,
                        pinStopLast
                );

        DAI_SystemManager.register(
                DAI_MenuCategory.ACTION,
                id,
                definition
        );
    }

    private static boolean isContribution(
            Identifier identifier,
            String id
    ) {

        if (
                identifier == null
                        || id == null
                        || id.isBlank()
        ) {
            return false;
        }

        String path =
                identifier.getPath();

        return id.equals(path)
                || path.startsWith(
                id + "/"
        );
    }

    private static DAI_SystemDefinition mergeContributedDefinitions(
            String id,
            List<Map.Entry<Identifier, DAI_SystemDefinition>> contributions,
            boolean pinStopLast
    ) {

        List<Map.Entry<Identifier, DAI_SystemDefinition>> orderedContributions =
                contributions.stream()
                        .sorted(
                                Comparator
                                        .comparingInt(
                                                (Map.Entry<Identifier, DAI_SystemDefinition> entry) ->
                                                        entry.getValue().priority()
                                        )
                                        .thenComparingInt(
                                                entry ->
                                                        isBuiltInNamespace(
                                                                entry.getKey()
                                                        )
                                                                ? 0
                                                                : 1
                                        )
                                        .thenComparing(
                                                entry ->
                                                        entry.getKey().toString()
                                        )
                        )
                        .toList();

        List<DAI_SystemButton> mergedButtons =
                new ArrayList<>();

        for (
                Map.Entry<Identifier, DAI_SystemDefinition> contribution
                : orderedContributions
        ) {

            Identifier source =
                    contribution.getKey();

            DAI_SystemDefinition definition =
                    contribution.getValue();

            List<DAI_SystemButton> orderedButtons =
                    definition.buttons()
                            .stream()
                            .sorted(
                                    Comparator
                                            .comparingInt(
                                                    DAI_SystemButton::slot
                                            )
                                            .thenComparing(
                                                    DAI_SystemButton::id
                                            )
                            )
                            .toList();

            int firstMergedSlot =
                    mergedButtons.size();

            for (DAI_SystemButton button : orderedButtons) {

                mergedButtons.add(
                        reindexButton(
                                button,
                                mergedButtons.size()
                        )
                );
            }

            DAI_Core.LOGGER.info(
                    "<DAI>: Added {} contribution '{}' priority={} buttons={} mergedSlots={}..{}.",
                    id,
                    source,
                    definition.priority(),
                    orderedButtons.size(),
                    firstMergedSlot,
                    mergedButtons.size() - 1
            );
        }

        List<DAI_SystemButton> finalButtons =
                pinStopLast
                        ? pinStopAutomationButtonsLast(
                        mergedButtons
                )
                        : List.copyOf(
                        mergedButtons
                );

        DAI_Core.LOGGER.info(
                pinStopLast
                        ? "<DAI>: Merged {} {} contribution file(s) into {} menu entry(s); Stop Automation pinned last."
                        : "<DAI>: Merged {} {} contribution file(s) into {} menu entry(s).",
                orderedContributions.size(),
                id,
                finalButtons.size()
        );

        return new DAI_SystemDefinition(
                0,
                finalButtons
        );
    }

    private static List<DAI_SystemButton> pinStopAutomationButtonsLast(
            List<DAI_SystemButton> mergedButtons
    ) {

        List<DAI_SystemButton> ordered =
                new ArrayList<>(
                        mergedButtons.size()
                );

        for (DAI_SystemButton button : mergedButtons) {

            if (!isStopAutomationButton(button)) {
                ordered.add(button);
            }
        }

        for (DAI_SystemButton button : mergedButtons) {

            if (isStopAutomationButton(button)) {
                ordered.add(button);
            }
        }

        List<DAI_SystemButton> reindexed =
                new ArrayList<>(
                        ordered.size()
                );

        for (DAI_SystemButton button : ordered) {

            reindexed.add(
                    reindexButton(
                            button,
                            reindexed.size()
                    )
            );
        }

        return reindexed;
    }

    private static DAI_SystemButton reindexButton(
            DAI_SystemButton button,
            int slot
    ) {

        return new DAI_SystemButton(
                slot,
                button.id(),
                button.text(),
                button.action(),
                button.conditions()
        );
    }

    private static boolean isStopAutomationButton(
            DAI_SystemButton button
    ) {

        return button != null
                && (
                STOP_AUTOMATION_BUTTON_ID.equals(
                        button.id()
                )
                        || STOP_AUTOMATION_ACTION.equals(
                        button.action()
                )
        );
    }

    private static boolean isBuiltInNamespace(
            Identifier identifier
    ) {

        return identifier != null
                && DAI_Core.MODID.equals(
                identifier.getNamespace()
        );
    }
}

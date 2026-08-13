package io.github.j12h36h.dai.menus;

import io.github.j12h36h.dai.logics.condition.DAI_ConditionEvaluator;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.menus.system.DAI_SystemButton;
import io.github.j12h36h.dai.menus.system.DAI_SystemDefinition;
import io.github.j12h36h.dai.menus.system.DAI_SystemManager;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class DAI_MenuAvailable {

    private String definitionId =
            "";

    private int selectedIndex;

    private List<DAI_SystemButton> eligibleEntries =
            List.of();

    private List<String> lastEligibleIds =
            List.of();

    public DAI_MenuAvailable() {
        // Instance belongs to one menu screen.
    }

    public void open(
            DAI_MenuCore menu,
            DAI_MenuState state,
            DAI_MenuLayout layout,
            String id
    ) {

        requireMenu(menu);
        requireState(state);
        requireLayout(layout);

        if (id == null || id.isBlank()) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot open an available-action menu with an empty id."
            );

            return;
        }

        String normalizedId =
                id.trim().toLowerCase(Locale.ROOT);

        if (
                !DAI_SystemManager.isAvailableActionMenu(
                        normalizedId
                )
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: ACTION menu '{}' is not registered as an available-action menu.",
                    normalizedId
            );

            return;
        }

        DAI_MenuCategory category =
                DAI_MenuCategory.ACTION;

        menu.closeMenu(category);

        definitionId =
                normalizedId;

        selectedIndex = 0;
        eligibleEntries = List.of();
        lastEligibleIds = List.of();

        state.setActionMode(
                DAI_MenuState.ActionMode.AVAILABLE
        );

        state.setOpen(
                category,
                true
        );

        DAI_Core.debug(
                "<DAI>: Opening available-action menu '{}'.",
                definitionId
        );

        DAI_MenuSelector.create(
                menu,
                state,
                layout,
                category,
                button -> {

                    refresh(state);
                    previous();
                    updateSelectedButton(state);
                },
                button -> {

                    refresh(state);

                    DAI_SystemButton selected =
                            selected();

                    if (selected == null) {

                        DAI_Core.debug(
                                "<DAI>: Available-action selection ignored because menu '{}' has no eligible entries.",
                                definitionId
                        );

                        return;
                    }

                    DAI_Core.debug(
                            "<DAI>: Running available action '{}' from menu '{}' with action '{}'.",
                            selected.id(),
                            definitionId,
                            selected.action()
                    );

                    menu.runAction(
                            selected.action()
                    );
                },
                button -> {

                    refresh(state);
                    next();
                    updateSelectedButton(state);
                }
        );

        refresh(state);

        DAI_Core.debug(
                "<DAI>: Available-action menu '{}' opened with {} eligible entry(s).",
                definitionId,
                eligibleEntries.size()
        );
    }

    /**
     * Re-evaluates button conditions from the client menu tick.
     *
     * Available menus share the same client tick path as Queue refreshes.
     * The currently open named definition is evaluated continuously, so
     * entries appear and disappear with gameplay state without reopening.
     */
    public void refresh(
            DAI_MenuState state
    ) {

        requireState(state);

        if (
                definitionId == null
                        || definitionId.isBlank()
        ) {
            return;
        }

        String previouslySelectedId =
                selectedId();

        List<DAI_SystemButton> refreshed =
                evaluateEligibleEntries(
                        definitionId
                );

        List<String> refreshedIds =
                refreshed.stream()
                        .map(DAI_SystemButton::id)
                        .toList();

        boolean eligibilityChanged =
                !refreshedIds.equals(
                        lastEligibleIds
                );

        eligibleEntries =
                refreshed;

        restoreSelection(
                previouslySelectedId
        );

        lastEligibleIds =
                refreshedIds;

        updateSelectedButton(state);

        if (eligibilityChanged) {

            DAI_Core.debug(
                    "<DAI>: Available-action menu '{}' eligibility changed; {} action(s) currently available: {}.",
                    definitionId,
                    eligibleEntries.size(),
                    String.join(
                            ", ",
                            refreshedIds
                    )
            );
        }
    }

    private void previous() {

        if (eligibleEntries.isEmpty()) {
            selectedIndex = 0;
            return;
        }

        selectedIndex =
                Math.floorMod(
                        selectedIndex - 1,
                        eligibleEntries.size()
                );
    }

    private void next() {

        if (eligibleEntries.isEmpty()) {
            selectedIndex = 0;
            return;
        }

        selectedIndex =
                Math.floorMod(
                        selectedIndex + 1,
                        eligibleEntries.size()
                );
    }

    private DAI_SystemButton selected() {

        if (eligibleEntries.isEmpty()) {
            return null;
        }

        normalizeSelection();

        return eligibleEntries.get(
                selectedIndex
        );
    }

    private String selectedId() {

        DAI_SystemButton selected =
                selected();

        return selected == null
                ? ""
                : selected.id();
    }

    private void restoreSelection(
            String preferredId
    ) {

        if (eligibleEntries.isEmpty()) {
            selectedIndex = 0;
            return;
        }

        if (
                preferredId != null
                        && !preferredId.isBlank()
        ) {

            for (
                    int index = 0;
                    index < eligibleEntries.size();
                    index++
            ) {

                if (
                        preferredId.equals(
                                eligibleEntries.get(index).id()
                        )
                ) {
                    selectedIndex = index;
                    return;
                }
            }
        }

        normalizeSelection();
    }

    private void normalizeSelection() {

        if (eligibleEntries.isEmpty()) {
            selectedIndex = 0;
            return;
        }

        selectedIndex =
                Math.floorMod(
                        selectedIndex,
                        eligibleEntries.size()
                );
    }

    private void updateSelectedButton(
            DAI_MenuState state
    ) {

        Button selectedButton =
                state.subButton(
                        DAI_MenuCategory.ACTION,
                        1
                );

        if (selectedButton == null) {
            return;
        }

        DAI_SystemButton selected =
                selected();

        if (selected == null) {

            selectedButton.setMessage(
                    Component.literal("[ Empty ]")
            );

            if (selectedButton instanceof DAI_StyledButton styledButton) {
                styledButton.setStyle(
                        io.github.j12h36h.dai.menus.system.DAI_ButtonStyle.EMPTY
                );
                styledButton.setSelectedStyle(false);
            }

            return;
        }

        selectedButton.setMessage(
                Component.literal(
                        String.format(
                                Locale.ROOT,
                                "{%d/%d} %s",
                                selectedIndex + 1,
                                eligibleEntries.size(),
                                selected.text()
                        )
                )
        );

        if (selectedButton instanceof DAI_StyledButton styledButton) {
            styledButton.setStyle(
                    selected.style()
            );
            styledButton.setSelectedStyle(true);
        }
    }

    private static List<DAI_SystemButton> evaluateEligibleEntries(
            String definitionId
    ) {

        DAI_SystemDefinition definition =
                DAI_SystemManager.get(
                        DAI_MenuCategory.ACTION,
                        definitionId
                );

        if (definition == null) {
            return List.of();
        }

        return definition.buttons()
                .stream()
                .sorted(
                        Comparator.comparingInt(
                                DAI_SystemButton::slot
                        )
                )
                .filter(button ->
                        DAI_ConditionEvaluator.evaluateAll(
                                button.conditions()
                        )
                )
                .toList();
    }

    private static DAI_MenuCore requireMenu(
            DAI_MenuCore menu
    ) {

        if (menu == null) {
            throw new IllegalArgumentException(
                    "Menu core cannot be null."
            );
        }

        return menu;
    }

    private static DAI_MenuState requireState(
            DAI_MenuState state
    ) {

        if (state == null) {
            throw new IllegalArgumentException(
                    "Menu state cannot be null."
            );
        }

        return state;
    }

    private static DAI_MenuLayout requireLayout(
            DAI_MenuLayout layout
    ) {

        if (layout == null) {
            throw new IllegalArgumentException(
                    "Menu layout cannot be null."
            );
        }

        return layout;
    }
}

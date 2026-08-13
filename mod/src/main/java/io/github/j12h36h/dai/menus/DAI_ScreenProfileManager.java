package io.github.j12h36h.dai.menus;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.menus.DAI_ScreenState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DAI_ScreenProfileManager {

    private static final Map<
            String,
            DAI_ScreenProfile
            > PROFILES =
            new LinkedHashMap<>();

    private DAI_ScreenProfileManager() {
        // Utility class.
    }

    /*
     * ------------------------------------------------------------
     * REGISTRATION
     * ------------------------------------------------------------
     */

    public static void register(
            String id,
            DAI_ScreenProfile profile
    ) {

        if (
                id == null
                        || id.isBlank()
                        || profile == null
        ) {
            return;
        }

        String normalizedId =
                normalizeId(
                        id
                );

        PROFILES.put(
                normalizedId,
                profile
        );

        DAI_Core.debug(
                "<DAI>: Registered screen profile '{}' with {} variant(s).",
                normalizedId,
                profile.variants().size()
        );
    }

    public static void clear() {

        int removed =
                PROFILES.size();

        PROFILES.clear();

        DAI_Core.debug(
                "<DAI>: Cleared {} screen profile(s).",
                removed
        );
    }

    /*
     * ------------------------------------------------------------
     * LOOKUP
     * ------------------------------------------------------------
     */

    public static boolean contains(
            String id
    ) {

        if (
                id == null
                        || id.isBlank()
        ) {
            return false;
        }

        return PROFILES.containsKey(
                normalizeId(
                        id
                )
        );
    }

    public static DAI_ScreenProfile get(
            String id
    ) {

        if (
                id == null
                        || id.isBlank()
        ) {
            return null;
        }

        return PROFILES.get(
                normalizeId(
                        id
                )
        );
    }

    public static Map<String, DAI_ScreenProfile> profiles() {

        return Collections.unmodifiableMap(
                PROFILES
        );
    }

    public static int size() {

        return PROFILES.size();
    }

    /*
     * ------------------------------------------------------------
     * ACTIVE VARIANT
     * ------------------------------------------------------------
     */

    public static DAI_ScreenProfile.Variant resolveVariant(
            String profileId
    ) {

        DAI_ScreenProfile profile =
                get(
                        profileId
                );

        if (profile == null) {
            return null;
        }

        for (
                DAI_ScreenProfile.Variant variant
                : profile.variants()
        ) {

            if (
                    variant == null
                            || variant.match() == null
            ) {
                continue;
            }

            if (
                    matches(
                            variant.match()
                    )
            ) {

                return variant;
            }
        }

        return null;
    }

    public static boolean matchesProfile(
            String profileId
    ) {

        return resolveVariant(
                profileId
        ) != null;
    }

    /*
     * ------------------------------------------------------------
     * SLOT RESOLUTION
     * ------------------------------------------------------------
     */

    public static Integer resolveSlot(
            String profileId,
            String slotName
    ) {

        if (
                slotName == null
                        || slotName.isBlank()
        ) {
            return null;
        }

        DAI_ScreenProfile.Variant variant =
                resolveVariant(
                        profileId
                );

        if (variant == null) {
            return null;
        }

        Integer slot =
                variant.slot(
                        slotName.trim()
                );

        if (slot == null) {
            return null;
        }

        /*
         * A profile should never resolve a logical slot that does not
         * actually exist in the currently open menu.
         */
        if (
                !DAI_ScreenState.hasSlot(
                        slot
                )
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Screen profile '{}' resolved slot '{}' to {}, but the active menu only has {} slot(s).",
                    normalizeId(
                            profileId
                    ),
                    slotName,
                    slot,
                    DAI_ScreenState.slotCount()
            );

            return null;
        }

        return slot;
    }

    /*
     * ------------------------------------------------------------
     * CONTROL RESOLUTION
     * ------------------------------------------------------------
     */

    public static DAI_ScreenProfile.Control resolveControl(
            String profileId,
            String controlName
    ) {

        if (
                controlName == null
                        || controlName.isBlank()
        ) {
            return null;
        }

        DAI_ScreenProfile.Variant variant =
                resolveVariant(
                        profileId
                );

        if (variant == null) {
            return null;
        }

        return variant.control(
                controlName.trim()
        );
    }

    /*
     * ------------------------------------------------------------
     * MATCHING
     * ------------------------------------------------------------
     */

    private static boolean matches(
            DAI_ScreenProfile.Match match
    ) {

        if (match == null) {
            return false;
        }

        if (
                match.hasScreenRequirement()
                        && !DAI_ScreenState.screenIs(
                        match.screen()
                )
        ) {
            return false;
        }

        if (
                match.hasMenuRequirement()
                        && !DAI_ScreenState.menuIs(
                        match.menu()
                )
        ) {
            return false;
        }

        if (
                match.hasTitleRequirement()
                        && !matchesTitle(
                        match.title()
                )
        ) {
            return false;
        }

        int slotCount =
                DAI_ScreenState.slotCount();

        if (
                match.hasExactSlotCount()
                        && slotCount
                        != match.slotCount()
        ) {
            return false;
        }

        if (
                match.hasMinimumSlotCount()
                        && slotCount
                        < match.minimumSlotCount()
        ) {
            return false;
        }

        if (
                match.hasMaximumSlotCount()
                        && slotCount
                        > match.maximumSlotCount()
        ) {
            return false;
        }

        /*
         * An entirely empty matcher is valid as a fallback variant.
         *
         * This allows profiles to define:
         *
         * specific variant
         * specific variant
         * generic fallback
         */
        return true;
    }

    private static boolean matchesTitle(
            String requiredTitle
    ) {

        if (
                requiredTitle == null
                        || requiredTitle.isBlank()
        ) {
            return true;
        }

        String actual =
                DAI_ScreenState.screenTitle();

        if (actual == null) {
            return false;
        }

        return actual.equalsIgnoreCase(
                requiredTitle.trim()
        );
    }

    /*
     * ------------------------------------------------------------
     * DIAGNOSTICS
     * ------------------------------------------------------------
     */

    public static void logCurrentScreenState() {

        DAI_Core.LOGGER.info(
                "<DAI>: ScreenState screen='{}' menu='{}' title='{}' containerId={} slots={} dimensions={}x{}.",
                DAI_ScreenState.screenSimpleClass(),
                DAI_ScreenState.menuSimpleClass(),
                DAI_ScreenState.screenTitle(),
                DAI_ScreenState.containerId(),
                DAI_ScreenState.slotCount(),
                DAI_ScreenState.screenWidth(),
                DAI_ScreenState.screenHeight()
        );
    }

    public static void logResolvedProfile(
            String profileId
    ) {

        DAI_ScreenProfile.Variant variant =
                resolveVariant(
                        profileId
                );

        if (variant == null) {

            DAI_Core.debug(
                    "<DAI>: Screen profile '{}' has no matching variant for the current screen.",
                    normalizeId(
                            profileId
                    )
            );

            return;
        }

        DAI_Core.debug(
                "<DAI>: Screen profile '{}' matched current screen with {} named slot(s) and {} control(s).",
                normalizeId(
                        profileId
                ),
                variant.slots().size(),
                variant.controls().size()
        );
    }

    /*
     * ------------------------------------------------------------
     * HELPERS
     * ------------------------------------------------------------
     */

    private static String normalizeId(
            String id
    ) {

        if (id == null) {
            return "";
        }

        String normalized =
                id.trim()
                        .toLowerCase();

        if (
                !normalized.contains(
                        ":"
                )
        ) {

            normalized =
                    "decisions_and_impulses:"
                            + normalized;
        }

        return normalized;
    }
}
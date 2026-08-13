package io.github.j12h36h.dai.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.input.DAI_KeyInput;
import io.github.j12h36h.dai.logics.mixin.Mixin_CreativeModeInventoryScreen;
import io.github.j12h36h.dai.menus.system.DAI_TargetState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

public final class DAI_CreativeInventoryLogic {

    private static final int HOTBAR_SIZE = 9;
    private static final int HOTBAR_PACKET_OFFSET = 36;

    private DAI_CreativeInventoryLogic() {
        // Utility class.
    }

    public static void openCreativeInventory(DAI_ActionDefinition action) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null || !minecraft.player.getAbilities().instabuild) {
            fail("Creative inventory requires an active Creative-mode player.");
            return;
        }

        if (minecraft.gui.screen() instanceof CreativeModeInventoryScreen) {
            success();
            return;
        }

        DAI_KeyInput.click("inventory");
        success();
    }

    public static void closeCreativeInventory(DAI_ActionDefinition action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() == null) {
            success();
            return;
        }
        minecraft.gui.setScreen(null);
        success();
    }

    public static void selectTab(DAI_ActionDefinition action) {
        CreativeModeInventoryScreen screen = screen();
        if (screen == null || action == null || !action.hasAction()) {
            fail("creative_select_tab requires the Creative inventory and a tab name.");
            return;
        }

        CreativeModeTab tab = resolveTab(action.action());
        if (tab == null) {
            fail("Unknown or hidden Creative tab '" + action.action() + "'.");
            return;
        }

        ((Mixin_CreativeModeInventoryScreen) (Object) screen).dai$selectTab(tab);
        success();
    }

    public static void search(DAI_ActionDefinition action) {
        CreativeModeInventoryScreen screen = screen();
        if (screen == null || action == null || !action.hasAction()) {
            fail("creative_search_item requires the Creative inventory and search text.");
            return;
        }

        Mixin_CreativeModeInventoryScreen access =
                (Mixin_CreativeModeInventoryScreen) (Object) screen;

        access.dai$selectTab(CreativeModeTabs.searchTab());

        EditBox searchBox = access.dai$getSearchBox();
        if (searchBox == null) {
            fail("Creative search field is unavailable.");
            return;
        }

        searchBox.setValue(action.action());
        access.dai$refreshSearchResults();
        success();
    }

    /**
     * Takes an item only when it is present in the current Creative tab/search
     * result set, preserving human-like tab/search semantics.
     */
    public static void takeVisibleItem(DAI_ActionDefinition action) {
        CreativeModeInventoryScreen screen = screen();
        Minecraft minecraft = Minecraft.getInstance();

        if (
                screen == null
                        || minecraft.player == null
                        || minecraft.gameMode == null
                        || action == null
                        || !action.hasAction()
        ) {
            fail("creative_take_item requires an open Creative inventory and item id.");
            return;
        }

        Item item = resolveItem(action.action());
        if (item == null) return;

        boolean visible = screen.getMenu().items.stream().anyMatch(stack -> stack.is(item));
        if (!visible) {
            fail("Creative item '" + action.action() + "' is not visible in the selected tab/search results.");
            return;
        }

        putHotbar(minecraft, item, action.slot(), count(action));
    }

    /**
     * Fast semantic path used by Creative Builder; no GUI browsing required.
     *
     * The Creative Builder datapack uses zero-based hotbar indices (0..8),
     * matching Player Inventory storage directly. The old implementation
     * interpreted these values as 1..9 and silently redirected slot 0 to a
     * fallback slot, which made the builder's requested hotbar layout wrong.
     */
    public static void equipItem(DAI_ActionDefinition action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (
                minecraft.player == null
                        || minecraft.gameMode == null
                        || !minecraft.player.getAbilities().instabuild
                        || action == null
                        || !action.hasAction()
        ) {
            fail("creative_equip_item requires a Creative player and item id.");
            return;
        }

        Item item = resolveItem(action.action());
        if (item == null) return;
        putHotbar(minecraft, item, action.slot(), count(action));
    }

    public static void saveToolbar(DAI_ActionDefinition action) {
        toolbar(action, true);
    }

    public static void loadToolbar(DAI_ActionDefinition action) {
        toolbar(action, false);
    }

    private static void toolbar(DAI_ActionDefinition action, boolean save) {
        int slot = normalizeToolbarSlot(action == null ? 1 : action.slot());
        String activator = save ? "save_toolbar" : "load_toolbar";
        String hotbarKey = "hotbar_" + (slot + 1);

        DAI_KeyInput.press(activator);
        try {
            DAI_KeyInput.click(hotbarKey);
        } finally {
            DAI_KeyInput.release(activator);
        }
        success();
    }

    public static void pickBlockWithData(DAI_ActionDefinition action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.player.getAbilities().instabuild) {
            fail("creative_pick_block_nbt requires a Creative player.");
            return;
        }

        DAI_CreativeInputState.armControlModifier();
        DAI_KeyInput.pickBlock();
        success();
    }

    public static void removeSelectedBlock(DAI_ActionDefinition action) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos target = DAI_TargetState.selectedBlock();

        if (minecraft.gameMode == null || minecraft.level == null || target == null) {
            fail("creative_remove_block requires a selected block target.");
            return;
        }

        if (minecraft.level.getBlockState(target).isAir()) {
            success();
            return;
        }

        boolean destroyed = minecraft.gameMode.destroyBlock(target);
        DAI_ActionStatus.set(destroyed ? DAI_ActionResult.SUCCESS : DAI_ActionResult.FAILURE);
    }

    public static void placeSelectedBlock(DAI_ActionDefinition action) {
        equipItem(action);
        if (DAI_ActionStatus.get() != DAI_ActionResult.SUCCESS) return;
        DAI_ExactPlacementLogic.placeAtSelectedPosition(action);
    }

    public static void setSelectedBlockState(DAI_ActionDefinition action) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos target = DAI_TargetState.selectedBlock();

        if (
                minecraft.getConnection() == null
                        || target == null
                        || action == null
                        || !action.hasAction()
        ) {
            fail("creative_set_block requires a selected position and block-state string.");
            return;
        }

        String state = action.action().trim();
        minecraft.getConnection().sendCommand(
                "setblock "
                        + target.getX() + " "
                        + target.getY() + " "
                        + target.getZ() + " "
                        + state + " replace"
        );
        success();
    }

    /**
     * Installs a Creative stack into the actual local hotbar and sends the
     * matching vanilla Creative inventory packet to the server. Local state is
     * intentionally updated first so subsequent DAI actions and debug snapshots
     * can observe the item immediately rather than assuming packet submission
     * means the inventory changed.
     */
    private static void putHotbar(
            Minecraft minecraft,
            Item item,
            int requestedSlot,
            int count
    ) {
        if (minecraft.player == null || minecraft.gameMode == null) {
            fail("Cannot populate the Creative hotbar without an active player and game mode.");
            return;
        }

        int slot = normalizeCreativeHotbarSlot(requestedSlot);
        ItemStack stack = new ItemStack(item, count);
        Inventory inventory = minecraft.player.getInventory();

        /*
         * Keep the client model synchronized immediately. The vanilla Creative
         * packet remains the authoritative server notification, but DAI must not
         * report SUCCESS until its own player inventory reflects the request.
         */
        inventory.setItem(slot, stack.copy());
        inventory.setSelectedSlot(slot);

        minecraft.gameMode.handleCreativeModeItemAdd(
                stack.copy(),
                HOTBAR_PACKET_OFFSET + slot
        );

        ItemStack observed = inventory.getItem(slot);
        if (!observed.is(item) || observed.getCount() <= 0) {
            fail(
                    "Creative hotbar verification failed for slot "
                            + slot
                            + " item '"
                            + BuiltInRegistries.ITEM.getKey(item)
                            + "'."
            );
            return;
        }

        DAI_Core.debug(
                "<DAI>: Creative hotbar slot {} <- {} x{} (verified).",
                slot,
                BuiltInRegistries.ITEM.getKey(item),
                observed.getCount()
        );
        success();
    }

    /** Creative Builder slot values are intentionally zero-based: 0..8. */
    private static int normalizeCreativeHotbarSlot(int slot) {
        if (slot < 0) return 0;
        if (slot >= HOTBAR_SIZE) return HOTBAR_SIZE - 1;
        return slot;
    }

    /** Saved-toolbar keybinds remain human-facing and therefore 1..9. */
    private static int normalizeToolbarSlot(int slot) {
        if (slot < 1 || slot > HOTBAR_SIZE) return 0;
        return slot - 1;
    }

    private static int count(DAI_ActionDefinition action) {
        int requested = (int) Math.round(action.value());
        if (requested <= 0) return 64;
        return Math.min(99, requested);
    }

    private static Item resolveItem(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.contains(":")) normalized = "minecraft:" + normalized;
        Identifier id = Identifier.tryParse(normalized);
        Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
        if (item == null || item == Items.AIR) {
            fail("Unknown Creative item '" + value + "'.");
            return null;
        }
        return item;
    }

    private static CreativeModeInventoryScreen screen() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.gui.screen() instanceof CreativeModeInventoryScreen creative
                ? creative
                : null;
    }

    private static CreativeModeTab resolveTab(String requested) {
        String normalized = normalize(requested);
        if (normalized.equals("search")) return CreativeModeTabs.searchTab();

        for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
            if (!tab.shouldDisplay()) continue;
            String display = normalize(tab.getDisplayName().getString());
            if (normalized.equals(display)) return tab;
        }
        return null;
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_');
    }

    private static void success() {
        DAI_ActionStatus.set(DAI_ActionResult.SUCCESS);
    }

    private static void fail(String reason) {
        DAI_ActionStatus.set(DAI_ActionResult.FAILURE);
        DAI_Core.LOGGER.warn("<DAI>: {}", reason);
    }
}

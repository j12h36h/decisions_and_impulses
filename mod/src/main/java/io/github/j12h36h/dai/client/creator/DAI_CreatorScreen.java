package io.github.j12h36h.dai.client.creator;

import io.github.j12h36h.dai.client.title.DAI_ShellWorldRuntime;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.j12h36h.dai.client.menus.DAI_StyledButton;
import io.github.j12h36h.dai.client.menus.system.DAI_ButtonStyle;
import io.github.j12h36h.dai.client.network.DAI_ServerBridge;
import io.github.j12h36h.dai.client.entity.mesh.DAI_MeshModel;
import io.github.j12h36h.dai.client.entity.mesh.DAI_MeshModelLibrary;
import io.github.j12h36h.dai.client.presentation.scene.DAI_SceneRenderer;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseShellRenderer;
import io.github.j12h36h.dai.client.presentation.DAI_PresentationProfileService;
import io.github.j12h36h.dai.creator.DAI_CreatorPresetRegistry;
import io.github.j12h36h.dai.creator.DAI_CreatorSchemaDefinition;
import io.github.j12h36h.dai.creator.DAI_CreatorSchemaRegistry;
import io.github.j12h36h.dai.network.DAI_CreatorActionPayload;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * DAI Creator workspace. The displayed engine feature level comes from DAI_Core.
 *
 * The Creator is intentionally a thin, schema-driven visual shell:
 * - compact project bar at the top
 * - Create Asset + persistent Property Selection stacked at the left
 * - dominant live preview/canvas at the right
 * - Property Editor appears as a temporary popup over the left workspace
 *
 * Module catalogs, fields, presets, preview adapters and module tools are all
 * provided by creator_schemas / creator_presets JSON. Java owns only generic
 * editing/navigation/rendering primitives.
 */
public final class DAI_CreatorScreen extends Screen {
    private static final DAI_ButtonStyle MODULE = new DAI_ButtonStyle(
            "#B20D1320", "#E0202C48", "#F06C3FA0", "#FFF6FF", "#8758B8");
    private static final DAI_ButtonStyle ACTION = new DAI_ButtonStyle(
            "#B61C1009", "#E43A1E0D", "#EF6A2A12", "#FFF7EF", "#FF8A2A");
    private static final DAI_ButtonStyle SOFT = new DAI_ButtonStyle(
            "#A70D0B13", "#CF1B1528", "#D5302345", "#EEE7F7", "#4E3A66");
    private static final DAI_ButtonStyle DANGER = new DAI_ButtonStyle(
            "#AD261018", "#D9491628", "#E85E1F36", "#FFF2F6", "#EF5A82");

    private final String requestedSchema;
    private final int propertyPage;
    private final int presetPage;
    private final int categoryPage;
    private final int typePage;
    private final int complexity;
    private final String selectedPropertyPath;
    private final boolean propertyPickerOpen;

    private EditBox idEdit;
    private final List<FieldBinding> fieldBindings = new ArrayList<>();
    private final List<PropertyRow> propertyRows = new ArrayList<>();
    private String status = "READY";

    private int bodyTop;
    private int bodyBottom;
    private int browserX;
    private int browserW;
    private int previewX;
    private int previewW;
    private int inspectorX;
    private int inspectorY;
    private int inspectorW;
    private int inspectorH;
    private int previewControlsY;
    private int createPanelBottom;
    private int propertyPopupX;
    private int propertyPopupY;
    private int propertyPopupW;
    private int propertyPopupH;
    private float uiTextScale = 1.0F;

    private int categoryPageIndex;
    private int categoryPageCount = 1;
    private int typePageIndex;
    private int typePageCount = 1;
    private int presetPageIndex;
    private int presetPageCount = 1;
    private int browserCategoryLabelY;
    private int browserTypeLabelY;
    private int browserPresetLabelY = -1;
    private int categoryPagerY = -1;
    private int typePagerY = -1;
    private int presetPagerY = -1;
    private int propertyRailPageIndex;
    private int propertyRailPageCount = 1;

    public DAI_CreatorScreen() {
        this(DAI_CreatorRuntime.schemaId(), 0, 0, 0, 0, 0, "", false);
    }

    private DAI_CreatorScreen(
            String schemaId,
            int propertyPage,
            int presetPage,
            int categoryPage,
            int typePage,
            int complexity,
            String selectedPropertyPath,
            boolean propertyPickerOpen
    ) {
        super(Component.literal("DAI Creator"));
        this.requestedSchema = schemaId == null ? "" : schemaId;
        this.propertyPage = Math.max(0, propertyPage);
        this.presetPage = Math.max(0, presetPage);
        this.categoryPage = Math.max(0, categoryPage);
        this.typePage = Math.max(0, typePage);
        this.complexity = clamp(complexity, 0, 2);
        this.selectedPropertyPath = selectedPropertyPath == null ? "" : selectedPropertyPath;
        this.propertyPickerOpen = propertyPickerOpen;
    }

    @Override
    protected void init() {
        super.init();
        DAI_CreatorRuntime.open(Minecraft.getInstance().player);
        if (!requestedSchema.isBlank()) DAI_CreatorRuntime.selectSchema(requestedSchema);

        calculateLayout();
        buildProjectBar();
        // v17: the lower-left Property Selection panel is persistent. Picking
        // a property temporarily replaces the entire left authoring column
        // with the focused Property Editor popup. The viewport never moves.
        if (propertyPickerOpen && !selectedPropertyPath.isBlank()) {
            buildPropertyPopup();
        } else {
            buildBrowser();
            buildPropertyRail();
        }
        buildPreviewControls();
        send("open", "", "", 0, 0, 0);
    }

    private void calculateLayout() {
        uiTextScale = width < 520 ? 0.72F
                : width < 640 ? 0.78F
                : width < 820 ? 0.84F
                : 0.90F;

        int margin = 8;
        int gap = 6;
        bodyTop = 34;
        bodyBottom = Math.max(bodyTop + 150, height - 8);

        /*
         * v17 layout contract:
         *   GREEN  = Create Asset (upper-left)
         *   ORANGE = Property Selection (lower-left, always present)
         *   RED    = Property Editor (temporary popup over both left panels)
         *   YELLOW = persistent viewport
         *   BLUE   = asset-specific preview controls
         *
         * The removed old right property column is still split roughly 50/50
         * between a wider authoring column and a wider viewport.
         */
        int oldBrowserW = clamp(width / 6, 94, 180);
        int oldInspectorW = clamp(width / 4, 120, 280);
        browserW = clamp(oldBrowserW + oldInspectorW / 2, 146, 330);
        browserX = margin;

        previewX = browserX + browserW + gap;
        previewW = Math.max(180, width - margin - previewX);
        previewControlsY = Math.max(bodyTop + 86, bodyBottom - 38);

        int bodyH = bodyBottom - bodyTop;
        // Keep the Create Asset panel bounded so its last preset row cannot
        // spill into Property Selection at compact GUI scales.
        int createH = clamp((bodyH * 53) / 100, 118, Math.max(118, bodyH - 88));
        createPanelBottom = Math.min(bodyBottom - 82, bodyTop + createH);

        inspectorX = browserX;
        inspectorY = createPanelBottom + gap;
        inspectorW = browserW;
        inspectorH = Math.max(76, bodyBottom - inspectorY);

        // Focused property editing replaces the whole authoring column.
        propertyPopupX = browserX;
        propertyPopupY = bodyTop;
        propertyPopupW = browserW;
        propertyPopupH = Math.max(100, bodyBottom - bodyTop);
    }

    private void buildProjectBar() {
        int actionH = 18;
        int y = 8;
        int gap = 3;
        int smallW = width < 560 ? 34 : 40;
        int saveW = width < 560 ? 38 : 44;
        int testW = width < 560 ? 38 : 44;
        int right = width - 8;

        int closeX = right - smallW;
        int testX = closeX - gap - testW;
        int saveX = testX - gap - saveW;
        int loadX = saveX - gap - smallW;
        int newX = loadX - gap - smallW;

        int idX = width < 560 ? 116 : 142;
        int idW = Math.max(84, newX - gap - idX);
        idEdit = new EditBox(font, idX, y, idW, actionH, Component.literal("namespace:id"));
        idEdit.setValue(DAI_CreatorRuntime.id());
        addRenderableWidget(idEdit);

        button(newX, y, smallW, actionH, "NEW", SOFT, this::create);
        button(loadX, y, smallW, actionH, "LOAD", SOFT, this::load);
        button(saveX, y, saveW, actionH, "SAVE", ACTION, this::save);
        button(testX, y, testW, actionH, "TEST", MODULE, this::toggleTest);
        button(closeX, y, smallW, actionH, "X", DANGER, this::onClose);
    }

    private void buildBrowser() {
        DAI_CreatorSchemaDefinition schema = DAI_CreatorRuntime.schema();
        if (schema == null) return;

        categoryPagerY = -1;
        typePagerY = -1;
        presetPagerY = -1;
        browserPresetLabelY = -1;

        int x = browserX + 5;
        int w = browserW - 10;
        int y = bodyTop + 20;
        int gap = 3;
        int rowH = 16;
        int half = Math.max(32, (w - gap) / 2);

        DAI_StyledButton game = button(x, y, half, rowH, "GAME", SOFT,
                () -> selectRail("experience"));
        DAI_StyledButton parts = button(x + half + gap, y, w - half - gap, rowH, "PARTS", SOFT,
                () -> selectRail("standalone"));
        game.setSelectedStyle("experience".equals(schema.rail()));
        parts.setSelectedStyle("standalone".equals(schema.rail()));
        y += 19;

        // Category selector. One large value at a time keeps the green panel
        // readable while still exposing every schema category through arrows.
        browserCategoryLabelY = y;
        y += 8;
        List<CategoryBucket> categories = categories(schema.rail());
        int selectedCategory = indexOfCategory(categories, schema.category());
        categoryPageIndex = selectedCategory;
        categoryPageCount = Math.max(1, categories.size());
        if (!categories.isEmpty()) {
            int arrow = 20;
            int center = Math.max(28, w - arrow * 2 - gap * 2);
            int previous = Math.floorMod(selectedCategory - 1, categories.size());
            int next = (selectedCategory + 1) % categories.size();
            button(x, y, arrow, rowH, "<", SOFT,
                    () -> selectCategory(schema.rail(), categories.get(previous).name()));
            DAI_StyledButton selected = button(x + arrow + gap, y, center, rowH,
                    fit(prettyCategory(categories.get(selectedCategory).name()), center - 6), MODULE,
                    () -> selectCategory(schema.rail(), categories.get(selectedCategory).name()));
            selected.setSelectedStyle(true);
            button(x + arrow + gap + center + gap, y, arrow, rowH, ">", SOFT,
                    () -> selectCategory(schema.rail(), categories.get(next).name()));
            categoryPagerY = y;
        }
        y += 19;

        browserTypeLabelY = y;
        y += 8;
        List<DAI_CreatorSchemaRegistry.Entry> types = categoryTypes(schema.rail(), schema.category());
        int selectedType = indexOfType(types, DAI_CreatorRuntime.schemaId());
        typePageIndex = selectedType;
        typePageCount = Math.max(1, types.size());
        if (!types.isEmpty()) {
            int arrow = 20;
            int center = Math.max(28, w - arrow * 2 - gap * 2);
            int previous = Math.floorMod(selectedType - 1, types.size());
            int next = (selectedType + 1) % types.size();
            button(x, y, arrow, rowH, "<", SOFT,
                    () -> selectSchema(types.get(previous).id().toString()));
            DAI_StyledButton selected = button(x + arrow + gap, y, center, rowH,
                    fit(types.get(selectedType).definition().shortName(), center - 6), MODULE,
                    () -> selectSchema(types.get(selectedType).id().toString()));
            selected.setSelectedStyle(true);
            button(x + arrow + gap + center + gap, y, arrow, rowH, ">", SOFT,
                    () -> selectSchema(types.get(next).id().toString()));
            typePagerY = y;
        }
        y += 19;

        List<Variation> variations = variations();
        browserPresetLabelY = y;
        y += 8;
        if (!variations.isEmpty()) {
            presetPageIndex = Math.min(Math.max(0, presetPage), variations.size() - 1);
            presetPageCount = variations.size();
            int arrow = 20;
            int center = Math.max(28, w - arrow * 2 - gap * 2);
            int previous = Math.floorMod(presetPageIndex - 1, variations.size());
            int next = (presetPageIndex + 1) % variations.size();
            button(x, y, arrow, rowH, "<", SOFT,
                    () -> reopen(propertyPage, previous, categoryPageIndex, typePageIndex, complexity));
            Variation current = variations.get(presetPageIndex);
            button(x + arrow + gap, y, center, rowH, fit(current.label(), center - 6), SOFT,
                    () -> applyVariation(current));
            button(x + arrow + gap + center + gap, y, arrow, rowH, ">", SOFT,
                    () -> reopen(propertyPage, next, categoryPageIndex, typePageIndex, complexity));
            presetPagerY = y;
        } else {
            button(x, y, w, rowH, "NO PRESET", SOFT, () -> { });
            presetPageIndex = 0;
            presetPageCount = 1;
        }
    }

    private void buildPropertyRail() {
        DAI_CreatorSchemaDefinition schema = DAI_CreatorRuntime.schema();
        if (schema == null) return;

        fieldBindings.clear();
        propertyRows.clear();

        List<JsonObject> fields = visibleFields(schema.fields());
        int x = inspectorX + 6;
        int w = inspectorW - 12;
        // v17.1: start the property content closer to the panel header and
        // reserve a dedicated two-row footer.  The first footer row owns the
        // < BASIC > / detail controls and the second row owns the page text.
        // This prevents the detail button from drawing over the 1/N indicator.
        int y = inspectorY + 18;
        int footerY = inspectorY + inspectorH - 40;

        // v17.2: build pages from the ACTUAL rendered height rather than a
        // guessed row count. Group labels consume vertical space, so the old
        // pageSize calculation could put (for example) Background Type inside
        // page 1's logical slice and then reject it during rendering. Page 2
        // would start after that field, effectively making it disappear.
        //
        // Each page range below is therefore packed using the exact same
        // group-gap, row-height, and row-advance values used to render it.
        final int groupGap = 4;
        final int rowHeight = 17;
        final int rowAdvance = 19;
        List<int[]> pageRanges = new ArrayList<>();
        int cursor = 0;
        while (cursor < fields.size()) {
            int startIndex = cursor;
            int testY = inspectorY + 18;
            String testGroup = "";

            while (cursor < fields.size()) {
                JsonObject candidate = fields.get(cursor);
                String candidatePath = DAI_CreatorSchemaDefinition.string(candidate, "path", "");
                if (candidatePath.isBlank()) {
                    cursor++;
                    continue;
                }

                String candidateGroup = propertyGroup(candidate, candidatePath);
                int candidateY = testY;
                if (!candidateGroup.equals(testGroup)) candidateY += groupGap;

                if (candidateY + rowHeight > footerY) {
                    // Always make forward progress even at an unusually small
                    // GUI scale. A single field is preferable to dropping it.
                    if (cursor == startIndex) cursor++;
                    break;
                }

                testY = candidateY + rowAdvance;
                testGroup = candidateGroup;
                cursor++;
            }

            pageRanges.add(new int[] {startIndex, cursor});
        }
        if (pageRanges.isEmpty()) pageRanges.add(new int[] {0, 0});

        int pages = pageRanges.size();
        int actualPage = Math.min(Math.max(0, propertyPage), pages - 1);
        propertyRailPageIndex = actualPage;
        propertyRailPageCount = pages;
        int start = pageRanges.get(actualPage)[0];
        int end = pageRanges.get(actualPage)[1];

        String previousGroup = "";
        for (int i = start; i < end; i++) {
            JsonObject field = fields.get(i);
            String path = DAI_CreatorSchemaDefinition.string(field, "path", "");
            if (path.isBlank()) continue;
            String label = DAI_CreatorSchemaDefinition.string(field, "label", path);
            String group = propertyGroup(field, path);

            if (!group.equals(previousGroup)) {
                y += groupGap;
                previousGroup = group;
            }
            if (y + rowHeight > footerY) break;

            DAI_StyledButton property = button(x, y, w, rowHeight,
                    fit(label.toUpperCase(Locale.ROOT), w - 8), ACTION,
                    () -> openProperty(path));
            property.setSelectedStyle(path.equals(selectedPropertyPath));
            propertyRows.add(new PropertyRow(path, label,
                    DAI_CreatorSchemaDefinition.normalized(
                            DAI_CreatorSchemaDefinition.string(field, "type", "text")),
                    group, y));
            y += rowAdvance;
        }

        int arrow = 20;
        int center = Math.max(36, w - arrow * 2 - 6);
        if (pages > 1) {
            button(x, footerY, arrow, 18, "<", SOFT,
                    () -> reopenWithProperty(selectedPropertyPath,
                            Math.floorMod(actualPage - 1, pages), presetPageIndex,
                            categoryPageIndex, typePageIndex, complexity));
            button(x + w - arrow, footerY, arrow, 18, ">", SOFT,
                    () -> reopenWithProperty(selectedPropertyPath,
                            (actualPage + 1) % pages, presetPageIndex,
                            categoryPageIndex, typePageIndex, complexity));
        }
        button(x + arrow + 3, footerY, center, 18, detailLabel(), MODULE, this::cycleDetail);
    }

    private void buildPropertyPopup() {
        DAI_CreatorSchemaDefinition schema = DAI_CreatorRuntime.schema();
        if (schema == null) return;
        JsonObject field = fieldByPath(schema, selectedPropertyPath);
        if (field == null) {
            reopenRoot(propertyPage, presetPageIndex, categoryPageIndex, typePageIndex, complexity);
            return;
        }

        fieldBindings.clear();

        int x = propertyPopupX + 6;
        int w = propertyPopupW - 12;
        String path = DAI_CreatorSchemaDefinition.string(field, "path", "");
        String label = DAI_CreatorSchemaDefinition.string(field, "label", path);
        String fallback = valueText(field.get("default"));
        String type = DAI_CreatorSchemaDefinition.normalized(
                DAI_CreatorSchemaDefinition.string(field, "type", "text"));
        String current = DAI_CreatorRuntime.get(path, fallback);
        int controlY = propertyPopupY + 58;
        int footerY = propertyPopupY + propertyPopupH - 22;
        // Keep a dedicated help/example module at the bottom of every property
        // editor. Property tools are laid out above this boundary so the
        // definition never overlaps selectable options, swatches, or numeric
        // controls, even at compact GUI scales.
        int helpHeight = propertyHelpHeight();
        int helpTop = footerY - helpHeight - 4;
        int usableBottom = helpTop - 4;

        if (type.equals("boolean") || type.equals("bool")) {
            buildBooleanPropertyTool(x, controlY, w, path, label, fallback, current);
        } else if (type.equals("color")) {
            buildColorPropertyTool(field, x, controlY, w, usableBottom, path, label, fallback, current);
        } else {
            List<OptionChoice> choices = propertyOptions(field, path, type, current, fallback);
            if (!choices.isEmpty()) {
                buildOptionPropertyTool(x, controlY, w, usableBottom, path, label, fallback, current, choices);
            } else if (type.equals("number") || type.equals("integer")
                    || type.equals("float") || type.equals("double")) {
                buildNumberPropertyTool(field, x, controlY, w, usableBottom, path, label, fallback, current, type);
            } else {
                buildTextPropertyTool(x, controlY, w, usableBottom, path, label, fallback, current, type);
            }
        }

        int gap = 2;
        int third = Math.max(24, (w - gap * 2) / 3);
        button(x, footerY, third, 18, "RESET", SOFT, () -> resetProperty(field));
        button(x + third + gap, footerY, third, 18, "APPLY", ACTION, this::applyPropertyAndClose);
        button(x + (third + gap) * 2, footerY,
                Math.max(22, w - (third + gap) * 2), 18, "BACK", MODULE, this::openPropertySelector);
    }

    private void buildBooleanPropertyTool(
            int x, int y, int w, String path, String label, String fallback, String current
    ) {
        int gap = 3;
        int half = Math.max(30, (w - gap) / 2);
        boolean enabled = Boolean.parseBoolean(current);
        DAI_StyledButton on = button(x, y, half, 19, "ENABLED", enabled ? ACTION : MODULE,
                () -> choosePropertyValue(path, label, "true"));
        on.setSelectedStyle(enabled);
        DAI_StyledButton off = button(x + half + gap, y, Math.max(30, w - half - gap), 19,
                "DISABLED", !enabled ? ACTION : MODULE,
                () -> choosePropertyValue(path, label, "false"));
        off.setSelectedStyle(!enabled);
    }

    private void buildOptionPropertyTool(
            int x, int y, int w, int usableBottom,
            String path, String label, String fallback, String current,
            List<OptionChoice> choices
    ) {
        int columns = w >= 210 ? 3 : 2;
        int gap = 3;
        int cellW = Math.max(36, (w - gap * (columns - 1)) / columns);
        int rowH = 19;
        int rowGap = 3;
        int maxRows = Math.max(1, (usableBottom - y + rowGap) / (rowH + rowGap));
        int visible = Math.min(choices.size(), maxRows * columns);

        for (int i = 0; i < visible; i++) {
            OptionChoice choice = choices.get(i);
            int col = i % columns;
            int row = i / columns;
            int bx = x + col * (cellW + gap);
            int bw = col == columns - 1 ? Math.max(32, w - col * (cellW + gap)) : cellW;
            int by = y + row * (rowH + rowGap);
            boolean selected = choice.value().equalsIgnoreCase(current);
            DAI_StyledButton option = button(bx, by, bw, rowH,
                    fit(choice.label().toUpperCase(Locale.ROOT), bw - 6),
                    selected ? ACTION : MODULE,
                    () -> choosePropertyValue(path, label, choice.value()));
            option.setSelectedStyle(selected);
        }

        if (choices.size() > visible) {
            int by = y + maxRows * (rowH + rowGap);
            if (by + rowH <= usableBottom) {
                button(x, by, w, rowH,
                        "+ " + (choices.size() - visible) + " MORE OPTIONS", SOFT,
                        () -> status = "MORE OPTIONS REQUIRE A LARGER GUI SCALE");
            }
        }
    }

    private void buildColorPropertyTool(
            JsonObject field, int x, int y, int w, int usableBottom,
            String path, String label, String fallback, String current
    ) {
        EditBox edit = new EditBox(font, x, y, w, 19, Component.literal(label));
        edit.setValue(current);
        addRenderableWidget(edit);
        fieldBindings.add(new FieldBinding(path, label, edit, y));

        List<OptionChoice> palette = colorPalette(field);
        int paletteY = y + 24;
        int columns = w >= 220 ? 4 : 3;
        int gap = 3;
        int cellW = Math.max(34, (w - gap * (columns - 1)) / columns);
        int rowH = 19;
        int maxRows = Math.max(1, (usableBottom - paletteY) / 22);
        int visible = Math.min(palette.size(), maxRows * columns);

        for (int i = 0; i < visible; i++) {
            OptionChoice choice = palette.get(i);
            int col = i % columns;
            int row = i / columns;
            int bx = x + col * (cellW + gap);
            int bw = col == columns - 1 ? Math.max(28, w - col * (cellW + gap)) : cellW;
            int by = paletteY + row * 22;
            boolean selected = normalizeColor(choice.value()).equalsIgnoreCase(normalizeColor(current));
            DAI_StyledButton swatch = button(bx, by, bw, rowH,
                    fit(choice.label().toUpperCase(Locale.ROOT), bw - 6),
                    colorButtonStyle(choice.value(), selected),
                    () -> choosePropertyValue(path, label, choice.value()));
            swatch.setSelectedStyle(selected);
        }
    }

    private void buildNumberPropertyTool(
            JsonObject field, int x, int y, int w, int usableBottom,
            String path, String label, String fallback, String current, String type
    ) {
        EditBox edit = new EditBox(font, x, y, w, 19, Component.literal(label));
        edit.setValue(current);
        addRenderableWidget(edit);
        fieldBindings.add(new FieldBinding(path, label, edit, y));

        double step = DAI_CreatorSchemaDefinition.number(field, "step", type.equals("integer") ? 1.0 : 0.1);
        int by = y + 24;
        int gap = 3;
        int quarter = Math.max(26, (w - gap * 3) / 4);
        if (by + 19 <= usableBottom) {
            button(x, by, quarter, 19, "--", SOFT,
                    () -> adjustNumber(path, fallback, -step * 10.0, propertyPage));
            button(x + quarter + gap, by, quarter, 19, "-", MODULE,
                    () -> adjustNumber(path, fallback, -step, propertyPage));
            button(x + (quarter + gap) * 2, by, quarter, 19, "+", MODULE,
                    () -> adjustNumber(path, fallback, step, propertyPage));
            button(x + (quarter + gap) * 3, by,
                    Math.max(26, w - (quarter + gap) * 3), 19, "++", SOFT,
                    () -> adjustNumber(path, fallback, step * 10.0, propertyPage));
        }

        int presetsY = by + 24;
        if (presetsY + 19 <= usableBottom) {
            int third = Math.max(28, (w - gap * 2) / 3);
            button(x, presetsY, third, 19, "ZERO", SOFT,
                    () -> choosePropertyValue(path, label, formatNumber(0.0, type)));
            button(x + third + gap, presetsY, third, 19, "DEFAULT", MODULE,
                    () -> choosePropertyValue(path, label, fallback));
            button(x + (third + gap) * 2, presetsY,
                    Math.max(28, w - (third + gap) * 2), 19, "CURRENT", ACTION,
                    () -> choosePropertyValue(path, label, DAI_CreatorRuntime.get(path, fallback)));
        }
    }

    private void buildTextPropertyTool(
            int x, int y, int w, int usableBottom,
            String path, String label, String fallback, String current, String type
    ) {
        EditBox edit = new EditBox(font, x, y, w, 19, Component.literal(label));
        edit.setValue(current);
        addRenderableWidget(edit);
        fieldBindings.add(new FieldBinding(path, label, edit, y));

        int by = y + 24;
        if (by + 19 <= usableBottom) {
            int gap = 3;
            int half = Math.max(34, (w - gap) / 2);
            button(x, by, half, 19, "DEFAULT", MODULE,
                    () -> choosePropertyValue(path, label, fallback));
            button(x + half + gap, by, Math.max(34, w - half - gap), 19,
                    type.equals("json") ? "EMPTY JSON" : "CLEAR", SOFT,
                    () -> choosePropertyValue(path, label, type.equals("json") ? "[]" : ""));
        }
    }

    private void choosePropertyValue(String path, String label, String value) {
        String next = value == null ? "" : value;
        DAI_CreatorRuntime.set(path, next);
        send("set", path, next, 0, 0, 0);
        syncRaw();
        status = label.toUpperCase(Locale.ROOT) + " // "
                + (next.isBlank() ? "EMPTY" : next.toUpperCase(Locale.ROOT));
        reopenWithProperty(path, propertyPage, presetPageIndex, categoryPageIndex, typePageIndex, complexity);
    }

    private void openProperty(String path) {
        commitVisibleFields();
        // Selecting a property opens the RED focused editor over the complete
        // left authoring column; the ORANGE selector itself remains a panel.
        reopenScreen(path, propertyPage, presetPageIndex, categoryPageIndex, typePageIndex, complexity, true);
    }

    private void openPropertySelector() {
        commitVisibleFields();
        // BACK/APPLY returns to the normal GREEN + ORANGE left workspace.
        reopenScreen(selectedPropertyPath, propertyPage, presetPageIndex, categoryPageIndex, typePageIndex, complexity, false);
    }

    private void closePropertySelector() {
        openPropertySelector();
    }

    private void applyPropertyAndClose() {
        commitVisibleFields();
        syncRaw();
        status = "PROPERTY APPLIED";
        reopenScreen(selectedPropertyPath, propertyPage, presetPageIndex, categoryPageIndex, typePageIndex, complexity, false);
    }

    private void closePropertyPopup() {
        reopenScreen(selectedPropertyPath, propertyPage, presetPageIndex, categoryPageIndex, typePageIndex, complexity, false);
    }

    private void resetProperty(JsonObject field) {
        String path = DAI_CreatorSchemaDefinition.string(field, "path", "");
        String fallback = valueText(field.get("default"));
        if (path.isBlank()) return;
        DAI_CreatorRuntime.set(path, fallback);
        send("set", path, fallback, 0, 0, 0);
        syncRaw();
        status = "RESET // " + DAI_CreatorSchemaDefinition.string(field, "label", path).toUpperCase(Locale.ROOT);
        reopen(propertyPage, presetPageIndex, categoryPageIndex, typePageIndex, complexity);
    }

    private JsonObject fieldByPath(DAI_CreatorSchemaDefinition schema, String wantedPath) {
        if (schema == null || wantedPath == null || wantedPath.isBlank()) return null;
        for (JsonObject field : visibleFields(schema.fields())) {
            if (wantedPath.equals(DAI_CreatorSchemaDefinition.string(field, "path", ""))) return field;
        }
        return null;
    }

    private String propertyRailLabel(String label) {
        String value = label == null ? "" : label.trim();
        if (value.isBlank()) return "...";
        return fit(value.toUpperCase(Locale.ROOT), Math.max(18, inspectorW - 12));
    }

    private void buildPreviewControls() {
        DAI_CreatorSchemaDefinition schema = DAI_CreatorRuntime.schema();
        if (schema == null) return;

        int x = previewX + 8;
        int y = previewControlsY + 13;
        int w = previewW - 16;
        int gap = 3;
        String previewType = effectivePreviewType(schema);

        if (previewType.equals("entity")) {
            int modeW = Math.max(38, Math.min(54, w / 5));
            button(x, y, modeW, 18, DAI_CreatorRuntime.preview3d() ? "3D" : "2D", MODULE, () -> {
                DAI_CreatorRuntime.togglePreview3d();
                reopen(propertyPage, presetPageIndex, categoryPageIndex, typePageIndex, complexity);
            });
            if (!DAI_CreatorRuntime.preview3d()) return;

            int controlX = x + modeW + gap;
            int remaining = w - modeW - gap;
            String[] labels = {"↶", "↷", "↑", "↓", "-", "+", "RESET"};
            int resetW = Math.max(38, remaining / 4);
            int small = Math.max(18, (remaining - resetW - gap * 6) / 6);
            Runnable[] actions = {
                    () -> rotatePreview(-15, 0), () -> rotatePreview(15, 0),
                    () -> rotatePreview(0, -10), () -> rotatePreview(0, 10),
                    () -> zoomPreview(-0.15F), () -> zoomPreview(0.15F),
                    () -> { DAI_CreatorRuntime.resetPreviewCamera(); status = "CAMERA RESET"; }
            };
            for (int i = 0; i < labels.length; i++) {
                int bw = i == labels.length - 1 ? resetW : small;
                button(controlX, y, bw, 18, labels[i], SOFT, actions[i]);
                controlX += bw + gap;
            }
            return;
        }

        if (previewType.equals("animation")) {
            int half = Math.max(48, (w - gap) / 2);
            button(x, y, half, 18, DAI_CreatorRuntime.previewPlaying() ? "PAUSE" : "PLAY", MODULE, () -> {
                DAI_CreatorRuntime.setPreviewPlaying(!DAI_CreatorRuntime.previewPlaying());
                status = DAI_CreatorRuntime.previewPlaying() ? "ANIMATION // PLAYING" : "ANIMATION // PAUSED";
                reopen(propertyPage, presetPageIndex, categoryPageIndex, typePageIndex, complexity);
            });
            button(x + half + gap, y, w - half - gap, 18, "RESTART", SOFT, () -> {
                DAI_CreatorRuntime.setPreviewPlaying(false);
                status = "ANIMATION // RESET";
            });
            return;
        }

        if (previewType.equals("world")) {
            int half = Math.max(48, (w - gap) / 2);
            button(x, y, half, 18, DAI_CreatorRuntime.isTesting() ? "STOP TEST" : "TEST WORLD", MODULE, this::toggleTest);
            button(x + half + gap, y, w - half - gap, 18, "SYNC", SOFT, () -> {
                commitVisibleFields();
                syncRaw();
                status = "WORLD PREVIEW // SYNCED";
            });
            return;
        }

        int half = Math.max(48, (w - gap) / 2);
        button(x, y, half, 18, DAI_CreatorRuntime.previewPlaying() ? "PAUSE" : "TEST", MODULE, this::toggleTest);
        button(x + half + gap, y, w - half - gap, 18, "REFRESH", SOFT, () -> {
            commitVisibleFields();
            syncRaw();
            status = "PREVIEW // REFRESHED";
        });
    }

    private void rotatePreview(float yaw, float pitch) {
        DAI_CreatorRuntime.rotatePreview(yaw, pitch);
        status = "CAMERA // " + Math.round(DAI_CreatorRuntime.previewYaw()) + "°";
    }

    private void zoomPreview(float delta) {
        DAI_CreatorRuntime.zoomPreview(delta);
        status = String.format(Locale.ROOT, "ZOOM // %.2fx", DAI_CreatorRuntime.previewZoom());
    }

    private void adjustNumber(String path, String fallback, double delta, int actualPage) {
        commitVisibleFields();
        double current;
        try { current = Double.parseDouble(DAI_CreatorRuntime.get(path, fallback)); }
        catch (NumberFormatException ignored) { current = 0.0; }
        double nextNumber = current + delta;
        String next = Math.rint(nextNumber) == nextNumber
                ? Long.toString((long)nextNumber)
                : String.format(Locale.ROOT, "%.4f", nextNumber).replaceAll("0+$", "").replaceAll("\\.$", "");
        DAI_CreatorRuntime.set(path, next);
        send("set", path, next, 0, 0, 0);
        syncRaw();
        reopen(actualPage, presetPageIndex, categoryPageIndex, typePageIndex, complexity);
    }

    private void buildPager(int x, int y, int w, int page, int pages, java.util.function.IntConsumer change) {
        int arrow = 22;
        button(x, y, arrow, 17, "<", SOFT, () -> change.accept(Math.floorMod(page - 1, pages)));
        button(x + w - arrow, y, arrow, 17, ">", SOFT, () -> change.accept((page + 1) % pages));
    }

    private void selectRail(String rail) {
        commitVisibleFields();
        List<CategoryBucket> buckets = categories(rail);
        if (buckets.isEmpty() || buckets.getFirst().entries().isEmpty()) return;
        DAI_CreatorRuntime.selectSchema(buckets.getFirst().entries().getFirst().id().toString());
        reopenRoot(0, 0, 0, 0, complexity);
    }

    private void selectCategory(String rail, String category) {
        commitVisibleFields();
        List<DAI_CreatorSchemaRegistry.Entry> entries = categoryTypes(rail, category);
        if (entries.isEmpty()) return;
        DAI_CreatorRuntime.selectSchema(entries.getFirst().id().toString());
        status = prettyCategory(category);
        reopenRoot(0, 0, categoryPageIndex, 0, complexity);
    }

    private void selectSchema(String schemaId) {
        commitVisibleFields();
        DAI_CreatorRuntime.selectSchema(schemaId);
        status = "SELECTED";
        reopenRoot(0, 0, categoryPageIndex, 0, complexity);
    }

    private void create() {
        Vec3 pos = playerPos();
        DAI_CreatorRuntime.createSelected(id(), pos);
        send("create", "", "", pos.x, pos.y, pos.z);
        syncRaw();
        status = "NEW // " + id();
        reopenRoot(0, 0, categoryPageIndex, typePageIndex, complexity);
    }

    private void load() {
        boolean local = DAI_CreatorRuntime.loadSelected(id());
        if (local) {
            send("create", "", "", 0, 0, 0);
            syncRaw();
            status = "LOADED // " + id();
            reopenRoot(0, 0, categoryPageIndex, typePageIndex, complexity);
        } else {
            send("load", "", "", 0, 0, 0);
            status = "LOAD REQUEST";
        }
    }

    private void save() {
        commitVisibleFields();
        syncRaw();
        send("save", "", "", 0, 0, 0);
        status = "SAVED // " + id();
    }

    private void applyVisible() {
        commitVisibleFields();
        syncRaw();
        status = "APPLIED";
    }

    private void commitVisibleFields() {
        for (FieldBinding binding : fieldBindings) {
            DAI_CreatorRuntime.set(binding.path(), binding.edit().getValue());
            send("set", binding.path(), binding.edit().getValue(), 0, 0, 0);
        }
    }

    private void applyVariation(Variation variation) {
        DAI_CreatorRuntime.applyPatch(variation.patch());
        syncRaw();
        status = "PRESET // " + variation.label();
        reopenRoot(propertyPage, presetPageIndex, categoryPageIndex, typePageIndex, complexity);
    }

    private void toggleTest() {
        commitVisibleFields();
        syncRaw();
        DAI_CreatorSchemaDefinition schema = DAI_CreatorRuntime.schema();
        if (schema == null) return;

        JsonObject preview = DAI_CreatorSchemaDefinition.object(schema.json(), "preview");
        String adapter = DAI_CreatorSchemaDefinition.string(preview, "adapter", "");
        String action = DAI_CreatorSchemaDefinition.string(preview, "action", "");
        String previewType = effectivePreviewType(schema);

        // Pure presentation modules test inside the Creator viewport. Gameplay
        // modules continue using the existing server bridge so they can hotload
        // directly into the current authoring world.
        if (previewType.equals("entity") || previewType.equals("animation")
                || previewType.equals("document") || previewType.equals("json")) {
            DAI_CreatorRuntime.setMode(DAI_CreatorRuntime.EditorMode.PREVIEW);
            DAI_CreatorRuntime.setPreviewPlaying(!DAI_CreatorRuntime.previewPlaying());
            status = DAI_CreatorRuntime.previewPlaying() ? "PREVIEW TEST // LIVE" : "PREVIEW TEST // PAUSED";
            return;
        }

        DAI_CreatorRuntime.setMode(DAI_CreatorRuntime.isTesting()
                ? DAI_CreatorRuntime.EditorMode.PREVIEW
                : DAI_CreatorRuntime.EditorMode.SIMULATE);
        DAI_CreatorRuntime.setPreviewPlaying(DAI_CreatorRuntime.isTesting());
        send("preview_config", adapter, action, 0, 0, 0);
        send("mode", adapter, DAI_CreatorRuntime.mode().name().toLowerCase(Locale.ROOT), 0, 0, 0);
        if (DAI_CreatorRuntime.isTesting()) send("test", adapter, action, 0, 0, 0);
        status = DAI_CreatorRuntime.isTesting() ? "HOT TEST // WORLD" : "PREVIEW";
    }

    private void cycleDetail() {
        int next = (complexity + 1) % 3;
        DAI_CreatorRuntime.setMode(next == 0 ? DAI_CreatorRuntime.EditorMode.CREATE
                : next == 1 ? DAI_CreatorRuntime.EditorMode.BUILD
                : DAI_CreatorRuntime.EditorMode.CODE);
        reopen(0, presetPageIndex, categoryPageIndex, typePageIndex, next);
    }

    private void openTools() {
        Minecraft.getInstance().gui.setScreen(new DAI_CreatorToolsScreen(
                this, DAI_CreatorRuntime.schemaId(), DAI_CreatorRuntime.folder(), id()));
    }

    private void executeFirstSchemaAction() {
        DAI_CreatorSchemaDefinition schema = DAI_CreatorRuntime.schema();
        if (schema == null) return;
        for (JsonElement element : schema.rightActions()) {
            if (element != null && element.isJsonObject()) {
                executeSchemaAction(element.getAsJsonObject());
                return;
            }
        }
    }

    private void executeSchemaAction(JsonObject action) {
        String operation = DAI_CreatorSchemaDefinition.string(action, "operation", "");
        String key = DAI_CreatorSchemaDefinition.string(action, "key", "");
        String value = DAI_CreatorSchemaDefinition.string(action, "value", "");
        if (operation.isBlank()) return;
        if (operation.equals("set")) DAI_CreatorRuntime.set(key, value);
        send(operation, key, value, 0, 0, 0);
        status = "ACTION // " + operation;
    }

    private void syncRaw() {
        send("raw_json", "", DAI_CreatorRuntime.rawJson(), 0, 0, 0);
    }

    private List<CategoryBucket> categories(String rail) {
        LinkedHashMap<String, List<DAI_CreatorSchemaRegistry.Entry>> grouped = new LinkedHashMap<>();
        for (DAI_CreatorSchemaRegistry.Entry entry : DAI_CreatorSchemaRegistry.rail(rail)) {
            String category = normalizedCategory(entry.definition().category());
            grouped.computeIfAbsent(category, ignored -> new ArrayList<>()).add(entry);
        }
        List<CategoryBucket> output = new ArrayList<>();
        grouped.forEach((name, entries) -> output.add(new CategoryBucket(name, List.copyOf(entries))));
        return List.copyOf(output);
    }

    private List<DAI_CreatorSchemaRegistry.Entry> categoryTypes(String rail, String category) {
        String wanted = normalizedCategory(category);
        return DAI_CreatorSchemaRegistry.rail(rail).stream()
                .filter(entry -> normalizedCategory(entry.definition().category()).equals(wanted))
                .toList();
    }

    private int indexOfCategory(List<CategoryBucket> categories, String category) {
        String wanted = normalizedCategory(category);
        for (int i = 0; i < categories.size(); i++) {
            if (normalizedCategory(categories.get(i).name()).equals(wanted)) return i;
        }
        return 0;
    }

    private static int indexOfType(List<DAI_CreatorSchemaRegistry.Entry> types, String id) {
        for (int i = 0; i < types.size(); i++) {
            if (types.get(i).id().toString().equals(id)) return i;
        }
        return 0;
    }

    private List<Variation> variations() {
        List<Variation> output = new ArrayList<>();
        DAI_CreatorSchemaDefinition schema = DAI_CreatorRuntime.schema();
        if (schema != null) {
            for (JsonElement element : schema.variations()) {
                if (element == null || !element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                String label = DAI_CreatorSchemaDefinition.string(object, "display_name", "Preset");
                output.add(new Variation(label, DAI_CreatorSchemaDefinition.object(object, "patch").deepCopy()));
            }
            for (DAI_CreatorPresetRegistry.Entry preset : DAI_CreatorPresetRegistry.forSchema(DAI_CreatorRuntime.schemaId())) {
                output.add(new Variation(preset.definition().displayName(), preset.definition().patch()));
            }
        }
        return List.copyOf(output);
    }

    private static String propertyGroup(JsonObject field, String path) {
        String explicit = DAI_CreatorSchemaDefinition.string(field, "group", "");
        if (!explicit.isBlank()) return prettyCategory(explicit);
        int dot = path.indexOf('.');
        if (dot > 0) return prettyCategory(path.substring(0, dot));
        return "GENERAL";
    }

    private static List<String> optionValues(JsonArray options) {
        if (options == null) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonElement element : options) {
            if (element == null) continue;
            if (element.isJsonPrimitive()) {
                values.add(element.getAsString());
            } else if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                String value = DAI_CreatorSchemaDefinition.string(object, "value",
                        DAI_CreatorSchemaDefinition.string(object, "id", ""));
                if (!value.isBlank()) values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private static List<OptionChoice> propertyOptions(
            JsonObject field, String path, String type, String current, String fallback
    ) {
        JsonArray explicitArray = field != null && field.has("options") && field.get("options").isJsonArray()
                ? field.getAsJsonArray("options") : null;
        List<OptionChoice> explicit = optionChoices(explicitArray);
        if (!explicit.isEmpty()) return explicit;

        String normalized = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (normalized.equals("background.type")) {
            return List.of(
                    new OptionChoice("gradient", "Gradient"),
                    new OptionChoice("solid", "Solid"),
                    new OptionChoice("transparent", "Transparent")
            );
        }
        if (normalized.endsWith("interpolation")) {
            return List.of(
                    new OptionChoice("smooth", "Smooth"),
                    new OptionChoice("linear", "Linear"),
                    new OptionChoice("step", "Step")
            );
        }
        if (normalized.equals("scope")) {
            return List.of(
                    new OptionChoice("player", "Player"),
                    new OptionChoice("world", "World"),
                    new OptionChoice("session", "Session"),
                    new OptionChoice("global", "Global")
            );
        }
        if (normalized.endsWith(".shape") || normalized.equals("shape")) {
            return List.of(
                    new OptionChoice("box", "Box"),
                    new OptionChoice("sphere", "Sphere"),
                    new OptionChoice("cylinder", "Cylinder")
            );
        }
        if (normalized.equals("mode")) {
            return List.of(
                    new OptionChoice("replace", "Replace"),
                    new OptionChoice("overlay", "Overlay"),
                    new OptionChoice("hide", "Hide")
            );
        }
        if (normalized.equals("phase")) {
            return List.of(
                    new OptionChoice("pre", "Pre"),
                    new OptionChoice("post", "Post")
            );
        }
        if (normalized.equals("context")) {
            return List.of(
                    new OptionChoice("gameplay", "Gameplay"),
                    new OptionChoice("menu", "Menu"),
                    new OptionChoice("creator", "Creator"),
                    new OptionChoice("cinematic", "Cinematic")
            );
        }
        if (normalized.equals("viewer.layout")) {
            return List.of(
                    new OptionChoice("book", "Book"),
                    new OptionChoice("panels", "Panels"),
                    new OptionChoice("timeline", "Timeline")
            );
        }
        return List.of();
    }

    private static List<OptionChoice> optionChoices(JsonArray options) {
        if (options == null) return List.of();
        List<OptionChoice> values = new ArrayList<>();
        for (JsonElement element : options) {
            if (element == null) continue;
            if (element.isJsonPrimitive()) {
                String value = element.getAsString();
                values.add(new OptionChoice(value, prettyOption(value)));
            } else if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                String value = DAI_CreatorSchemaDefinition.string(object, "value",
                        DAI_CreatorSchemaDefinition.string(object, "id", ""));
                if (value.isBlank()) continue;
                String label = DAI_CreatorSchemaDefinition.string(object, "label",
                        DAI_CreatorSchemaDefinition.string(object, "display_name", prettyOption(value)));
                values.add(new OptionChoice(value, label));
            }
        }
        return List.copyOf(values);
    }

    private static List<OptionChoice> colorPalette(JsonObject field) {
        JsonArray custom = field != null && field.has("palette") && field.get("palette").isJsonArray()
                ? field.getAsJsonArray("palette") : null;
        List<OptionChoice> configured = optionChoices(custom);
        if (!configured.isEmpty()) return configured;
        return List.of(
                new OptionChoice("#17101f", "Deep"),
                new OptionChoice("#05070d", "Night"),
                new OptionChoice("#000000", "Black"),
                new OptionChoice("#ffffff", "White"),
                new OptionChoice("#ff5a82", "Rose"),
                new OptionChoice("#ff8a2a", "Orange"),
                new OptionChoice("#f0cf58", "Gold"),
                new OptionChoice("#55c979", "Green"),
                new OptionChoice("#4a87e8", "Blue"),
                new OptionChoice("#8758b8", "Violet"),
                new OptionChoice("#00c7d9", "Cyan"),
                new OptionChoice("#7f8794", "Slate")
        );
    }

    private static DAI_ButtonStyle colorButtonStyle(String rawColor, boolean selected) {
        String argb = argbColor(rawColor);
        String border = selected ? "#FFFFFFFF" : "#FF6C536E";
        String text = isDarkColor(rawColor) ? "#FFFFFFFF" : "#FF141118";
        return new DAI_ButtonStyle(argb, argb, argb, text, border);
    }

    private static String argbColor(String value) {
        String normalized = normalizeColor(value);
        if (normalized.length() == 7) return "#FF" + normalized.substring(1).toUpperCase(Locale.ROOT);
        if (normalized.length() == 9) return "#" + normalized.substring(1).toUpperCase(Locale.ROOT);
        return "#FF17101F";
    }

    private static String normalizeColor(String value) {
        if (value == null) return "#000000";
        String clean = value.trim();
        if (!clean.startsWith("#")) clean = "#" + clean;
        if (clean.length() == 4) {
            char r = clean.charAt(1), g = clean.charAt(2), b = clean.charAt(3);
            clean = "#" + r + r + g + g + b + b;
        }
        return clean.toLowerCase(Locale.ROOT);
    }

    private static boolean isDarkColor(String value) {
        String clean = normalizeColor(value);
        try {
            String rgb = clean.length() == 9 ? clean.substring(3) : clean.substring(1);
            int color = Integer.parseInt(rgb.substring(0, 6), 16);
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            return (r * 299 + g * 587 + b * 114) / 1000 < 145;
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    private static String prettyOption(String value) {
        if (value == null || value.isBlank()) return "Empty";
        String text = value;
        int colon = text.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < text.length()) text = text.substring(colon + 1);
        return prettyCategory(text.replace('/', ' ').replace('.', ' '));
    }

    private static String formatNumber(double value, String type) {
        if (type != null && type.equals("integer")) return Integer.toString((int) Math.round(value));
        if (Math.rint(value) == value) return Long.toString(Math.round(value));
        String formatted = String.format(Locale.ROOT, "%.4f", value);
        while (formatted.contains(".") && (formatted.endsWith("0") || formatted.endsWith("."))) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        return formatted;
    }

    private List<JsonObject> visibleFields(JsonArray source) {
        List<JsonObject> output = new ArrayList<>();
        if (source == null) return output;
        for (JsonElement element : source) {
            if (element == null || !element.isJsonObject()) continue;
            JsonObject field = element.getAsJsonObject();
            int level = DAI_CreatorSchemaDefinition.integer(field, "level", 0);
            if (level <= complexity) output.add(field);
        }
        return output;
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        DAI_UniverseShellRenderer.render(
                graphics, width, height, DAI_PresentationProfileService.selected(), System.nanoTime());
        graphics.fill(0, 0, width, height, 0x78000000);
        graphics.fillGradient(0, 0, width, bodyTop - 3, 0xE81B0E25, 0xB80C0A13);

        // YELLOW // persistent asset viewport
        panel(graphics, previewX, bodyTop, previewW, previewControlsY - bodyTop - 4, 0xC00A0C12, 0xFFF0CF58);
        // BLUE // controls are a separate, asset-specific module
        panel(graphics, previewX, previewControlsY, previewW, bodyBottom - previewControlsY, 0xC008101B, 0xFF4A87E8);

        drawText(graphics, "D.A.I. // CREATOR " + DAI_Core.FEATURE_LEVEL, 9, 7, 0xFFFF9A4D);
        if (width >= 560) drawText(graphics, "MODULAR ASSET WORKSPACE", 9, 19, 0xFFB985E0);

        DAI_CreatorSchemaDefinition schema = DAI_CreatorRuntime.schema();
        drawText(graphics, "ASSET VIEWPORT", previewX + 9, bodyTop + 8, 0xFFFFD968);
        drawAdaptiveText(graphics, status, previewX + 9, previewControlsY + 3,
                previewW - 18, 0xFF9FC2FF, 0.52F);

        if (schema == null) {
            drawCenteredText(graphics, "No Creator definitions loaded",
                    previewX + previewW / 2, bodyTop + 55, 0xFFFF758D);
        } else {
            drawAdaptiveText(graphics, schema.displayName(), previewX + 9, bodyTop + 22,
                    previewW - 18, 0xFFF5EEFF, 0.64F);
            List<String> description = wrap(schema.description(), previewW - 18, 2);
            int dy = bodyTop + 34;
            for (String line : description) {
                drawText(graphics, line, previewX + 9, dy, 0xFFAFA0BF);
                dy += 9;
            }
            int previewContentY = Math.max(bodyTop + 54, dy + 3);
            renderPreview(graphics, schema, partialTick, previewContentY);
        }

        if (propertyPickerOpen && !selectedPropertyPath.isBlank()) {
            // RED // focused Property Editor popup temporarily replaces both
            // left-side panels while leaving the viewport permanently visible.
            panel(graphics, propertyPopupX, propertyPopupY, propertyPopupW, propertyPopupH,
                    0xE0170A10, 0xFFEE546F);
            JsonObject field = schema == null ? null : fieldByPath(schema, selectedPropertyPath);
            drawText(graphics, "PROPERTY EDITOR", propertyPopupX + 7, propertyPopupY + 8, 0xFFFF8195);
            if (field != null) {
                String path = DAI_CreatorSchemaDefinition.string(field, "path", selectedPropertyPath);
                String label = DAI_CreatorSchemaDefinition.string(field, "label", path);
                String group = propertyGroup(field, path);
                drawAdaptiveText(graphics, "PROPERTY // " + group,
                        propertyPopupX + 7, propertyPopupY + 20,
                        propertyPopupW - 14, 0xFFFF8195, 0.50F);
                drawAdaptiveText(graphics, label.toUpperCase(Locale.ROOT),
                        propertyPopupX + 7, propertyPopupY + 32,
                        propertyPopupW - 14, 0xFFFFEFF3, 0.56F);
                drawAdaptiveText(graphics, path,
                        propertyPopupX + 7, propertyPopupY + 43,
                        propertyPopupW - 14, 0xFFB88D99, 0.46F);

                // Small in-context documentation module. Schemas can supply
                // `description`/`help` and `example` directly; otherwise the
                // Creator derives a useful explanation from the field type,
                // options and default value so every property has guidance.
                int footerY = propertyPopupY + propertyPopupH - 22;
                int helpHeight = propertyHelpHeight();
                int helpY = footerY - helpHeight - 4;
                int helpX = propertyPopupX + 6;
                int helpW = propertyPopupW - 12;
                graphics.fill(helpX, helpY, helpX + helpW, footerY - 4, 0xB00B0D14);
                graphics.outline(helpX, helpY, helpW, Math.max(1, footerY - 4 - helpY), 0xFF7D4352);
                drawText(graphics, "WHAT THIS DOES", helpX + 5, helpY + 4, 0xFFFFA1B0);

                int textY = helpY + 15;
                int exampleReserve = 20;
                int availableDescriptionHeight = Math.max(18, (footerY - 7 - exampleReserve) - textY);
                int descriptionLines = Math.max(2, Math.min(4, availableDescriptionHeight / 9));
                for (String line : wrap(propertyDefinition(field), helpW - 10, descriptionLines)) {
                    drawAdaptiveText(graphics, line, helpX + 5, textY, helpW - 10, 0xFFD5BDC5, 0.46F);
                    textY += 9;
                }

                String example = propertyExample(field);
                if (!example.isBlank()) {
                    int exampleY = Math.max(textY + 1, footerY - 22);
                    List<String> exampleLines = wrap("EXAMPLE // " + example, helpW - 10, 2);
                    for (String line : exampleLines) {
                        if (exampleY + 8 >= footerY - 4) break;
                        drawAdaptiveText(graphics, line, helpX + 5, exampleY,
                                helpW - 10, 0xFFFFC47A, 0.44F);
                        exampleY += 9;
                    }
                }
            }
        } else {
            // GREEN // Create Asset is strictly bounded to the upper-left.
            panel(graphics, browserX, bodyTop, browserW, createPanelBottom - bodyTop,
                    0xBD0B0B12, 0xFF55C979);
            drawText(graphics, "CREATE ASSET", browserX + 7, bodyTop + 8, 0xFF8DE7A5);
            drawText(graphics, "CATEGORY", browserX + 7, browserCategoryLabelY, 0xFF9276A8);
            drawText(graphics, "TYPE", browserX + 7, browserTypeLabelY, 0xFF9276A8);
            if (browserPresetLabelY >= 0) {
                drawText(graphics, "START FROM", browserX + 7, browserPresetLabelY, 0xFF9276A8);
            }
            if (categoryPageCount > 1 && categoryPagerY >= 0) {
                drawPageIndicator(graphics, browserX, categoryPagerY + 4, browserW,
                        categoryPageIndex, categoryPageCount);
            }
            if (typePageCount > 1 && typePagerY >= 0) {
                drawPageIndicator(graphics, browserX, typePagerY + 4, browserW,
                        typePageIndex, typePageCount);
            }
            if (presetPageCount > 1 && presetPagerY >= 0) {
                drawPageIndicator(graphics, browserX, presetPagerY + 4, browserW,
                        presetPageIndex, presetPageCount);
            }

            // ORANGE // Property Selection is the permanent lower-left panel.
            panel(graphics, inspectorX, inspectorY, inspectorW, inspectorH,
                    0xE0160C08, 0xFFFF812B);
            drawText(graphics, "PROPERTY SELECTION", inspectorX + 7, inspectorY + 7, 0xFFFFB277);
            String lastGroup = "";
            for (PropertyRow row : propertyRows) {
                if (!row.group().equals(lastGroup)) {
                    drawAdaptiveText(graphics, row.group(), inspectorX + 7, row.y() - 5,
                            inspectorW - 14, 0xFFFFC18F, 0.46F);
                    lastGroup = row.group();
                }
            }
            if (propertyRailPageCount > 1) {
                // Dedicated page-indicator row below the < BASIC > controls.
                drawPageIndicator(graphics, inspectorX, inspectorY + inspectorH - 13, inspectorW,
                        propertyRailPageIndex, propertyRailPageCount);
            }
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPreview(GuiGraphicsExtractor graphics, DAI_CreatorSchemaDefinition schema, float partialTick, int contentY) {
        int x = previewX + 9;
        int y = contentY;
        int w = Math.max(20, previewW - 18);
        int h = Math.max(28, previewControlsY - y - 5);
        String previewType = effectivePreviewType(schema);

        if (DAI_CreatorRuntime.preview3d() && previewType.equals("entity")) {
            renderEntityPreview(graphics, x, y, w, h, partialTick);
            return;
        }
        if (previewType.equals("animation")) {
            renderAnimationPreview(graphics, x, y, w, h, partialTick);
            return;
        }

        boolean rendered = false;
        if (schema.previewType().equals("scene")) {
            if (schema.previewSource().equals("draft")) {
                DAI_SceneRenderer.renderDefinition(graphics, DAI_CreatorRuntime.draft(), x, y, w, h,
                        partialTick, previewVariables(schema));
                rendered = true;
            } else if (!schema.previewScene().isBlank()) {
                rendered = DAI_SceneRenderer.render(graphics, schema.previewScene(), x, y, w, h,
                        partialTick, previewVariables(schema));
            }
        }
        if (rendered) return;

        if (previewType.equals("world")) {
            renderWorldPreview(graphics, x, y, w, h);
            return;
        }
        renderDocumentPreview(graphics, x, y, w, h);
    }

    private String propertyDefinition(JsonObject field) {
        if (field == null) return "This property changes the selected asset.";

        String explicit = firstNonBlank(
                DAI_CreatorSchemaDefinition.string(field, "description", ""),
                DAI_CreatorSchemaDefinition.string(field, "help", ""),
                DAI_CreatorSchemaDefinition.string(field, "definition", "")
        );
        if (!explicit.isBlank()) return explicit;

        String path = DAI_CreatorSchemaDefinition.string(field, "path", "property");
        String label = DAI_CreatorSchemaDefinition.string(field, "label", path);
        String type = DAI_CreatorSchemaDefinition.normalized(
                DAI_CreatorSchemaDefinition.string(field, "type", "text"));
        String lower = (label + " " + path).toLowerCase(Locale.ROOT);

        if (type.equals("boolean") || type.equals("bool")) {
            return "Turns " + label + " on or off for this asset.";
        }
        if (type.equals("color")) {
            return "Sets the color used by " + label
                    + ". Choose a swatch or enter a custom hexadecimal color.";
        }
        if (type.equals("number") || type.equals("integer")
                || type.equals("float") || type.equals("double")) {
            if (lower.contains("fov")) return "Controls the camera field of view. Lower values feel zoomed in; higher values show more of the scene.";
            if (lower.contains("opacity") || lower.contains("alpha")) return "Controls how transparent this part of the presentation is.";
            if (lower.contains("duration") || lower.contains("time") || lower.contains("tick")) return "Controls the timing value used by this asset. Timing fields are usually measured in Minecraft ticks.";
            if (lower.contains("speed")) return "Controls how quickly this behavior or animation changes over time.";
            if (lower.contains("priority") || lower.contains("layer") || lower.contains("z")) return "Controls ordering when multiple presentation elements compete or overlap.";
            return "Sets the numeric value used by " + label + ". Use the step controls for quick adjustments.";
        }
        if (type.equals("json") || lower.contains("keyframe") || lower.contains("widgets")
                || lower.contains("elements") || lower.contains("tracks") || lower.contains("clips")) {
            return "Defines structured module data for " + label
                    + ". Use this for lists or nested settings that contain multiple sub-elements.";
        }
        if (lower.contains("texture") || lower.contains("model") || lower.contains("scene")
                || lower.contains("sound") || lower.contains("animation") || lower.contains("resource")) {
            return "Selects the DAI/Minecraft resource used by " + label
                    + ". Resource identifiers normally use namespace:path.";
        }
        if (lower.contains("anchor") || lower.contains("position") || lower.contains("offset")) {
            return "Controls where " + label + " is placed relative to its parent screen, scene, or viewport.";
        }
        if (lower.contains("mode") || lower.contains("type") || field.has("options")) {
            return "Chooses the behavior mode for " + label
                    + ". Select one of the available options above.";
        }
        return "Sets " + label + " for this asset. Changes are reflected in the Creator preview when the asset type supports live preview.";
    }

    private String propertyExample(JsonObject field) {
        if (field == null) return "";
        String explicit = firstNonBlank(
                DAI_CreatorSchemaDefinition.string(field, "example", ""),
                DAI_CreatorSchemaDefinition.string(field, "usage_example", "")
        );
        if (!explicit.isBlank()) return explicit;

        String path = DAI_CreatorSchemaDefinition.string(field, "path", "property");
        String type = DAI_CreatorSchemaDefinition.normalized(
                DAI_CreatorSchemaDefinition.string(field, "type", "text"));
        String fallback = valueText(field.get("default"));

        if (field.has("options") && field.get("options").isJsonArray()
                && field.getAsJsonArray("options").size() > 0) {
            JsonElement option = field.getAsJsonArray("options").get(0);
            String value = option.isJsonObject()
                    ? DAI_CreatorSchemaDefinition.string(option.getAsJsonObject(), "value", "")
                    : option.getAsString();
            if (!value.isBlank()) return path + " = " + quotedExample(value, type);
        }
        if (!fallback.isBlank() && !fallback.equals("null")) {
            return path + " = " + quotedExample(fallback, type);
        }
        if (type.equals("boolean") || type.equals("bool")) return path + " = true";
        if (type.equals("color")) return path + " = \"#17101F\"";
        if (type.equals("number") || type.equals("integer")
                || type.equals("float") || type.equals("double")) return path + " = 1";
        if (type.equals("json")) return path + " = []";
        return path + " = \"namespace:example\"";
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private static String quotedExample(String value, String type) {
        String safe = value == null ? "" : value;
        if (type.equals("boolean") || type.equals("bool")
                || type.equals("number") || type.equals("integer")
                || type.equals("float") || type.equals("double")
                || type.equals("json")) return safe;
        return "\"" + safe.replace("\"", "\\\"") + "\"";
    }

    private String effectivePreviewType(DAI_CreatorSchemaDefinition schema) {
        if (schema == null) return "document";
        String type = schema.previewType();
        if (!type.equals("json") && !type.isBlank()) return type;
        String folder = DAI_CreatorSchemaDefinition.normalized(schema.folder());
        if (folder.equals("dai_entities")) return "entity";
        if (folder.equals("dai_animations")) return "animation";
        return type.isBlank() ? "document" : type;
    }

    private void renderEntityPreview(GuiGraphicsExtractor graphics, int x, int y, int w, int h, float partialTick) {
        graphics.fillGradient(x, y, x + w, y + h, 0xD0060A11, 0xD0130A1B);
        graphics.outline(x, y, w, h, 0xFF5D397A);
        String modelRef = DAI_CreatorRuntime.get("entity.model", "");
        String textureRef = DAI_CreatorRuntime.get("entity.texture", "");
        String namespace = creatorNamespace();
        DAI_MeshModel model = DAI_MeshModelLibrary.get(modelRef, namespace);
        int cx = x + w / 2;
        int cy = y + h / 2 + 4;

        if (model == null || model.isEmpty()) {
            drawCenteredText(graphics, "3D ENTITY VIEWPORT", cx, y + 10, 0xFFFFB277);
            drawCenteredText(graphics, modelRef.isBlank() ? "Select entity.model" : "Model not loaded", cx, cy - 4, 0xFFFF758D);
            if (!textureRef.isBlank()) drawCenteredText(graphics, "Texture // " + compact(textureRef, 26), cx, cy + 10, 0xFFBFA8D0);
            return;
        }

        float yaw = (float)Math.toRadians(DAI_CreatorRuntime.previewYaw());
        float pitch = (float)Math.toRadians(DAI_CreatorRuntime.previewPitch());
        float zoom = DAI_CreatorRuntime.previewZoom();
        if (DAI_CreatorRuntime.previewPlaying()) yaw += (System.nanoTime() % 8_000_000_000L) / 8_000_000_000.0F * (float)Math.PI * 2.0F;
        float scale = Math.min(w, h) * 0.34F * zoom;
        int color = 0xFFE3B5FF;
        int bright = 0xFFFF963E;
        int triangleBudget = 420;
        int drawn = 0;
        graphics.enableScissor(x + 2, y + 2, x + w - 2, y + h - 2);
        outer:
        for (DAI_MeshModel.Section section : model.sections()) {
            for (DAI_MeshModel.Triangle triangle : section.triangles()) {
                Point2 a = project(triangle.a(), yaw, pitch, scale, cx, cy);
                Point2 b = project(triangle.b(), yaw, pitch, scale, cx, cy);
                Point2 c = project(triangle.c(), yaw, pitch, scale, cx, cy);
                int edge = section.fullBright() ? bright : color;
                drawLine(graphics, a.x(), a.y(), b.x(), b.y(), edge);
                drawLine(graphics, b.x(), b.y(), c.x(), c.y(), edge);
                drawLine(graphics, c.x(), c.y(), a.x(), a.y(), edge);
                if (++drawn >= triangleBudget) break outer;
            }
        }
        graphics.disableScissor();
        drawText(graphics, "MODEL // " + compact(model.id().toString(), 28), x + 6, y + 6, 0xFFFFB277);
        drawText(graphics, "TRIS // " + model.triangleCount(), x + 6, y + h - 19, 0xFF9E8AAF);
        if (!textureRef.isBlank()) drawAdaptiveText(graphics, "TEXTURE // " + textureRef, x + 6, y + h - 10, w - 12, 0xFFBFA8D0, 0.48F);
    }

    private void renderAnimationPreview(GuiGraphicsExtractor graphics, int x, int y, int w, int h, float partialTick) {
        graphics.fillGradient(x, y, x + w, y + h, 0xD0080B13, 0xD0150C1C);
        graphics.outline(x, y, w, h, 0xFF5D397A);
        double duration;
        try { duration = Math.max(1.0, Double.parseDouble(DAI_CreatorRuntime.get("duration", "20"))); }
        catch (NumberFormatException ignored) { duration = 20.0; }
        boolean loop = Boolean.parseBoolean(DAI_CreatorRuntime.get("loop", "false"));
        double phase = DAI_CreatorRuntime.previewPlaying() ? (System.nanoTime() / 50_000_000.0) % duration : 0.0;
        int left = x + 12;
        int right = x + w - 12;
        int timelineY = y + h / 2;
        graphics.fill(left, timelineY, right, timelineY + 2, 0x665C456F);
        int cursor = left + (int)Math.round((right - left) * (phase / duration));
        graphics.fill(cursor - 1, timelineY - 8, cursor + 2, timelineY + 10, 0xFFFF8A2A);
        drawCenteredText(graphics, "ANIMATION TEST", x + w / 2, y + 10, 0xFFFFB277);
        drawCenteredText(graphics, String.format(Locale.ROOT, "%.1f / %.1f ticks%s", phase, duration, loop ? " // LOOP" : ""),
                x + w / 2, timelineY + 18, 0xFFD4C2E0);
        drawCenteredText(graphics, DAI_CreatorRuntime.previewPlaying() ? "PLAYING" : "PRESS TEST TO PLAY",
                x + w / 2, y + h - 16, DAI_CreatorRuntime.previewPlaying() ? 0xFF7FE7A0 : 0xFF9E8AAF);
    }

    private Point2 project(DAI_MeshModel.Vertex vertex, float yaw, float pitch, float scale, int cx, int cy) {
        double x = vertex.x();
        double y = vertex.y();
        double z = vertex.z();
        double cosy = Math.cos(yaw), siny = Math.sin(yaw);
        double x1 = x * cosy - z * siny;
        double z1 = x * siny + z * cosy;
        double cosp = Math.cos(pitch), sinp = Math.sin(pitch);
        double y1 = y * cosp - z1 * sinp;
        double z2 = y * sinp + z1 * cosp;
        double perspective = 1.0 / Math.max(0.35, 1.7 + z2 * 0.16);
        return new Point2(cx + (int)Math.round(x1 * scale * perspective), cy - (int)Math.round(y1 * scale * perspective));
    }

    private static void drawLine(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int steps = Math.max(dx, dy);
        if (steps <= 0) { graphics.fill(x0, y0, x0 + 1, y0 + 1, color); return; }
        int stride = Math.max(1, steps / 36);
        for (int i = 0; i <= steps; i += stride) {
            int x = x0 + (x1 - x0) * i / steps;
            int y = y0 + (y1 - y0) * i / steps;
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private String creatorNamespace() {
        String value = id();
        int colon = value.indexOf(':');
        return colon > 0 ? value.substring(0, colon) : "decisions_and_impulses";
    }

    private void renderDocumentPreview(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        graphics.fillGradient(x, y, x + w, y + h, 0xD00A0C13, 0xD0100B18);
        graphics.outline(x, y, w, h, 0xFF443456);
        int pad = 8;
        int lineY = y + 8;
        drawText(graphics, "DOCUMENT", x + pad, lineY, 0xFFFFB277);
        drawAdaptiveText(graphics, id(), x + pad + 48, lineY,
                Math.max(18, w - pad * 2 - 48), 0xFFD7C5E4, 0.54F);
        lineY += 14;

        String[] lines = prettyJsonPreview(DAI_CreatorRuntime.rawJson(),
                Math.max(18, w / 6), Math.max(2, (h - 29) / 10));
        for (String line : lines) {
            if (lineY + 9 >= y + h - 4) break;
            int depth = leadingSpaces(line) / 2;
            int indent = Math.min(18, depth * 5);
            String shown = line.stripLeading();
            int color = shown.startsWith("\"") ? 0xFFC7A7E0 : 0xFFB9AEC4;
            drawAdaptiveText(graphics, shown, x + pad + indent, lineY,
                    Math.max(12, w - pad * 2 - indent), color, 0.52F);
            lineY += 10;
        }
    }

    private void renderWorldPreview(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        graphics.fillGradient(x, y, x + w, y + h, 0xD00A0D16, 0xD0120C19);
        graphics.outline(x, y, w, h, 0xFF443456);
        int cx = x + w / 2;
        int cy = y + h / 2;
        for (int gx = x + 8; gx < x + w - 8; gx += 12)
            graphics.fill(gx, y + 8, gx + 1, y + h - 8, 0x223F3152);
        for (int gy = y + 8; gy < y + h - 8; gy += 12)
            graphics.fill(x + 8, gy, x + w - 8, gy + 1, 0x223F3152);
        graphics.fill(cx - 8, cy, cx + 9, cy + 1, 0xAAFF8A2A);
        graphics.fill(cx, cy - 8, cx + 1, cy + 9, 0xAAFF8A2A);
        Vec3 pos = playerPos();
        drawCenteredText(graphics, "WORLD", cx, y + 10, 0xFFFFB277);
        drawCenteredText(graphics, String.format(Locale.ROOT, "%.1f  %.1f  %.1f", pos.x, pos.y, pos.z),
                cx, y + h - 15, 0xFFBFA8D0);
    }

    private Map<String, Object> previewVariables(DAI_CreatorSchemaDefinition schema) {
        LinkedHashMap<String, Object> vars = new LinkedHashMap<>();
        vars.put("creator.id", id());
        vars.put("creator.schema", DAI_CreatorRuntime.schemaId());
        vars.put("creator.folder", schema.folder());
        vars.put("creator.mode", DAI_CreatorRuntime.mode().name().toLowerCase(Locale.ROOT));
        return Map.copyOf(vars);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void onClose() {
        send("close", "", "", 0, 0, 0);
        DAI_CreatorRuntime.close();
        Minecraft.getInstance().gui.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() { return DAI_ShellWorldRuntime.isShellActive(); }

    private void reopen(int newProperty, int newPreset, int newCategory, int newType, int newComplexity) {
        reopenScreen(selectedPropertyPath, newProperty, newPreset, newCategory, newType, newComplexity, propertyPickerOpen);
    }

    private void reopenRoot(int newProperty, int newPreset, int newCategory, int newType, int newComplexity) {
        reopenScreen("", newProperty, newPreset, newCategory, newType, newComplexity, false);
    }

    private void reopenWithProperty(
            String propertyPath, int newProperty, int newPreset, int newCategory, int newType, int newComplexity
    ) {
        reopenScreen(propertyPath, newProperty, newPreset, newCategory, newType, newComplexity, propertyPickerOpen);
    }

    private void reopenScreen(
            String propertyPath, int newProperty, int newPreset, int newCategory, int newType, int newComplexity,
            boolean pickerOpen
    ) {
        Minecraft.getInstance().gui.setScreen(new DAI_CreatorScreen(
                DAI_CreatorRuntime.schemaId(), newProperty, newPreset,
                newCategory, newType, newComplexity, propertyPath, pickerOpen));
    }

    private String id() {
        String value = idEdit == null ? "" : idEdit.getValue().trim();
        return value.isBlank() ? DAI_CreatorRuntime.id() : value;
    }

    private Vec3 playerPos() {
        var player = Minecraft.getInstance().player;
        return player == null ? Vec3.ZERO : player.position();
    }

    private void send(String operation, String key, String value, double x, double y, double z) {
        DAI_ServerBridge.send(new DAI_CreatorActionPayload(
                operation, DAI_CreatorRuntime.folder(), id(), key, value, x, y, z));
    }

    private DAI_StyledButton button(int x, int y, int w, int h, String text, DAI_ButtonStyle style, Runnable action) {
        DAI_StyledButton button = new DAI_StyledButton(x, y, Math.max(18, w), Math.max(16, h),
                Component.literal(text), ignored -> action.run(), style);
        button.setTextScale(uiTextScale);
        addRenderableWidget(button);
        return button;
    }

    private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        drawTextAtScale(graphics, text, x, y, color, uiTextScale);
    }

    private void drawCenteredText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        if (uiTextScale >= 0.999F) {
            graphics.centeredText(font, Component.literal(text), x, y, color);
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().scale(uiTextScale, uiTextScale);
        graphics.centeredText(font, Component.literal(text),
                Math.round(x / uiTextScale), Math.round(y / uiTextScale), color);
        graphics.pose().popMatrix();
    }


    private int propertyHelpHeight() {
        // Give definitions enough vertical room to be genuinely readable.
        // The previous 46-64px box forced most descriptions into two lines
        // and then ellipsized them on compact GUI scales. Reserve roughly a
        // quarter of the popup, capped so property tools still have useful
        // working space.
        return Math.min(88, Math.max(66, propertyPopupH / 4));
    }

    private void drawAdaptiveText(
            GuiGraphicsExtractor graphics, String text, int x, int y,
            int maxPixels, int color, float minimumScale
    ) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty() || maxPixels <= 0) return;
        int rawWidth = Math.max(1, font.width(value));
        float scale = Math.min(uiTextScale, maxPixels / (float)rawWidth);
        scale = Math.max(Math.min(uiTextScale, 1.0F), Math.max(0.48F, minimumScale));
        if (rawWidth * scale > maxPixels) {
            scale = Math.max(0.48F, Math.min(uiTextScale, maxPixels / (float)rawWidth));
        }
        int logicalMax = Math.max(1, (int)Math.floor(maxPixels / scale));
        drawTextAtScale(graphics, fitAtScale(value, logicalMax), x, y, color, scale);
    }

    private void drawTextAtScale(
            GuiGraphicsExtractor graphics, String text, int x, int y, int color, float scale
    ) {
        if (scale >= 0.999F) {
            graphics.text(font, Component.literal(text), x, y, color);
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);
        graphics.text(font, Component.literal(text),
                Math.round(x / scale), Math.round(y / scale), color);
        graphics.pose().popMatrix();
    }

    private void drawPageIndicator(GuiGraphicsExtractor graphics, int x, int y, int w, int page, int pages) {
        String value = (page + 1) + "/" + pages;
        drawCenteredText(graphics, value, x + w / 2, y, 0xFF7F6A93);
    }

    private String fitAtScale(String text, int logicalMax) {
        String value = text == null ? "" : text.trim();
        if (logicalMax <= 0 || font.width(value) <= logicalMax) return value;
        String ellipsis = "...";
        int allowed = Math.max(1, logicalMax - font.width(ellipsis));
        int end = value.length();
        while (end > 1 && font.width(value.substring(0, end)) > allowed) end--;
        return value.substring(0, Math.max(1, end)).trim() + ellipsis;
    }

    private String fit(String text, int maxPixels) {
        String value = text == null ? "" : text.trim();
        int logicalMax = uiTextScale <= 0.0F ? maxPixels
                : Math.max(1, (int)Math.floor(maxPixels / uiTextScale));
        return fitAtScale(value, logicalMax);
    }

    private List<String> wrap(String text, int maxPixels, int maxLines) {
        String value = text == null ? "" : text.trim();
        if (value.isBlank() || maxLines <= 0) return List.of();
        int logicalMax = uiTextScale <= 0.0F ? maxPixels
                : Math.max(1, (int)Math.floor(maxPixels / uiTextScale));
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        String[] words = value.split("\\s+");
        int cursor = 0;
        for (; cursor < words.length; cursor++) {
            String word = words[cursor];
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (font.width(candidate) <= logicalMax) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (!line.isEmpty()) {
                out.add(line.toString());
                line.setLength(0);
                if (out.size() >= maxLines) break;
            }
            if (font.width(word) > logicalMax) out.add(fit(word, maxPixels));
            else line.append(word);
            if (out.size() >= maxLines) break;
        }
        if (out.size() < maxLines && !line.isEmpty()) out.add(line.toString());
        if (cursor < words.length - 1 && !out.isEmpty()) {
            int last = out.size() - 1;
            out.set(last, fit(out.get(last) + "...", maxPixels));
        }
        return List.copyOf(out);
    }

    private String detailLabel() {
        return complexity == 0 ? "BASIC" : complexity == 1 ? "MORE" : "ALL";
    }

    private boolean compactHeight() { return height < 330; }

    private static String normalizedCategory(String value) {
        return DAI_CreatorSchemaDefinition.normalized(value == null ? "GENERAL" : value);
    }

    private static String prettyCategory(String value) {
        String normalized = normalizedCategory(value).replace('_', ' ').trim();
        return normalized.isBlank() ? "GENERAL" : normalized.toUpperCase(Locale.ROOT);
    }

    private static String valueText(JsonElement value) {
        if (value == null || value.isJsonNull()) return "";
        try { return value.isJsonPrimitive() ? value.getAsString() : value.toString(); }
        catch (RuntimeException ignored) { return ""; }
    }

    private static int leadingSpaces(String value) {
        int count = 0;
        while (count < value.length() && value.charAt(count) == ' ') count++;
        return count;
    }

    private static String[] prettyJsonPreview(String raw, int approximateWidth, int maxLines) {
        if (raw == null || raw.isBlank()) return new String[]{"{}"};
        try {
            JsonElement element = com.google.gson.JsonParser.parseString(raw);
            String pretty = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(element);
            String[] source = pretty.split("\\R");
            List<String> out = new ArrayList<>();
            for (String line : source) {
                if (out.size() >= maxLines) break;
                if (line.length() <= approximateWidth * 2) out.add(line);
                else out.add(compact(line, Math.max(8, approximateWidth * 2)));
            }
            if (source.length > out.size() && !out.isEmpty()) {
                int last = out.size() - 1;
                out.set(last, compact(out.get(last), Math.max(4, approximateWidth * 2 - 3)) + "...");
            }
            return out.toArray(String[]::new);
        } catch (RuntimeException ignored) {
            return new String[]{compact(raw, Math.max(12, approximateWidth * 2))};
        }
    }

    private static String compact(String text, int max) {
        String value = text == null ? "" : text.trim();
        if (value.length() <= max) return value;
        return value.substring(0, Math.max(1, max - 3)) + "...";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, fill);
        g.outline(x, y, w, h, border);
        if (w > 6 && h > 6) {
            g.fill(x + 2, y + 2, x + w - 2, y + 3, 0x66FF8428);
        }
    }

    private record FieldBinding(String path, String label, EditBox edit, int y) {}
    private record OptionChoice(String value, String label) {}
    private record PropertyRow(String path, String label, String type, String group, int y) {}
    private record Point2(int x, int y) {}
    private record Variation(String label, JsonObject patch) {}
    private record CategoryBucket(String name, List<DAI_CreatorSchemaRegistry.Entry> entries) {}
}

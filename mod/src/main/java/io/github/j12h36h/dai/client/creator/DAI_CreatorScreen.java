package io.github.j12h36h.dai.client.creator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.j12h36h.dai.client.menus.DAI_StyledButton;
import io.github.j12h36h.dai.client.menus.system.DAI_ButtonStyle;
import io.github.j12h36h.dai.client.network.DAI_ServerBridge;
import io.github.j12h36h.dai.client.presentation.scene.DAI_SceneRenderer;
import io.github.j12h36h.dai.creator.DAI_CreatorPresetRegistry;
import io.github.j12h36h.dai.creator.DAI_CreatorSchemaDefinition;
import io.github.j12h36h.dai.creator.DAI_CreatorSchemaRegistry;
import io.github.j12h36h.dai.network.DAI_CreatorActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * DAI 3.9 Creator workspace.
 *
 * The Creator is intentionally a thin, schema-driven visual shell:
 * - compact project bar at the top
 * - creation browser at the left
 * - dominant live preview/canvas in the center
 * - progressive property inspector at the right
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

    private EditBox idEdit;
    private final List<FieldBinding> fieldBindings = new ArrayList<>();
    private String status = "READY";

    private int bodyTop;
    private int bodyBottom;
    private int browserX;
    private int browserW;
    private int previewX;
    private int previewW;
    private int inspectorX;
    private int inspectorW;
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

    public DAI_CreatorScreen() {
        this(DAI_CreatorRuntime.schemaId(), 0, 0, 0, 0, 0);
    }

    private DAI_CreatorScreen(
            String schemaId,
            int propertyPage,
            int presetPage,
            int categoryPage,
            int typePage,
            int complexity
    ) {
        super(Component.literal("DAI Creator"));
        this.requestedSchema = schemaId == null ? "" : schemaId;
        this.propertyPage = Math.max(0, propertyPage);
        this.presetPage = Math.max(0, presetPage);
        this.categoryPage = Math.max(0, categoryPage);
        this.typePage = Math.max(0, typePage);
        this.complexity = clamp(complexity, 0, 2);
    }

    @Override
    protected void init() {
        super.init();
        DAI_CreatorRuntime.open(Minecraft.getInstance().player);
        if (!requestedSchema.isBlank()) DAI_CreatorRuntime.selectSchema(requestedSchema);

        calculateLayout();
        buildProjectBar();
        buildBrowser();
        buildInspector();
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

        browserW = clamp(width / 5, 96, 126);
        inspectorW = clamp(width / 3, 158, 210);

        int requiredPreview = width < 520 ? 155 : 205;
        int maxSides = Math.max(210, width - margin * 2 - gap * 2 - requiredPreview);
        if (browserW + inspectorW > maxSides) {
            int overflow = browserW + inspectorW - maxSides;
            int fromInspector = Math.min(overflow, Math.max(0, inspectorW - 145));
            inspectorW -= fromInspector;
            overflow -= fromInspector;
            browserW = Math.max(88, browserW - overflow);
        }

        browserX = margin;
        inspectorX = width - margin - inspectorW;
        previewX = browserX + browserW + gap;
        previewW = Math.max(120, inspectorX - gap - previewX);
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
        int y = bodyTop + 23;
        int halfGap = 3;
        int half = Math.max(34, (w - halfGap) / 2);

        DAI_StyledButton game = button(x, y, half, 18, "GAME", SOFT,
                () -> selectRail("experience"));
        DAI_StyledButton parts = button(x + half + halfGap, y, w - half - halfGap, 18, "PARTS", SOFT,
                () -> selectRail("standalone"));
        game.setSelectedStyle("experience".equals(schema.rail()));
        parts.setSelectedStyle("standalone".equals(schema.rail()));
        y += 24;

        browserCategoryLabelY = y;
        y += 12;
        List<CategoryBucket> categories = categories(schema.rail());
        int categoryRows = height < 280 ? 2 : compactHeight() ? 3 : 4;
        int catPages = Math.max(1, (categories.size() + categoryRows - 1) / categoryRows);
        int selectedCategory = indexOfCategory(categories, schema.category());
        int wantedCatPage = categoryPage;
        if (wantedCatPage >= catPages || wantedCatPage < 0) wantedCatPage = selectedCategory / categoryRows;
        categoryPageIndex = Math.min(catPages - 1, wantedCatPage);
        categoryPageCount = catPages;

        int catStart = categoryPageIndex * categoryRows;
        int catEnd = Math.min(categories.size(), catStart + categoryRows);
        for (int i = catStart; i < catEnd; i++) {
            CategoryBucket bucket = categories.get(i);
            DAI_StyledButton b = button(x, y, w, 18, fit(prettyCategory(bucket.name()), w - 8), MODULE,
                    () -> selectCategory(schema.rail(), bucket.name()));
            b.setSelectedStyle(normalizedCategory(bucket.name()).equals(normalizedCategory(schema.category())));
            y += 20;
        }
        if (catPages > 1) {
            categoryPagerY = y;
            buildPager(x, y, w, categoryPageIndex, catPages,
                    page -> reopen(propertyPage, presetPage, page, typePage, complexity));
            y += 19;
        }

        y += 5;
        browserTypeLabelY = y;
        y += 12;
        List<DAI_CreatorSchemaRegistry.Entry> types = categoryTypes(schema.rail(), schema.category());
        int remaining = Math.max(60, bodyBottom - y - 54);
        int typeRows = clamp((remaining / 2) / 20, height < 280 ? 1 : 2, compactHeight() ? 3 : 4);
        int selectedType = indexOfType(types, DAI_CreatorRuntime.schemaId());
        int typePages = Math.max(1, (types.size() + typeRows - 1) / typeRows);
        int wantedTypePage = typePage;
        if (wantedTypePage >= typePages || wantedTypePage < 0) wantedTypePage = selectedType / typeRows;
        if (selectedType / typeRows != wantedTypePage && typePage == 0) wantedTypePage = selectedType / typeRows;
        typePageIndex = Math.min(typePages - 1, wantedTypePage);
        typePageCount = typePages;

        int typeStart = typePageIndex * typeRows;
        int typeEnd = Math.min(types.size(), typeStart + typeRows);
        for (int i = typeStart; i < typeEnd; i++) {
            DAI_CreatorSchemaRegistry.Entry entry = types.get(i);
            DAI_StyledButton b = button(x, y, w, 18,
                    fit(entry.definition().shortName(), w - 8), MODULE,
                    () -> selectSchema(entry.id().toString()));
            b.setSelectedStyle(entry.id().toString().equals(DAI_CreatorRuntime.schemaId()));
            y += 20;
        }
        if (typePages > 1) {
            typePagerY = y;
            buildPager(x, y, w, typePageIndex, typePages,
                    page -> reopen(propertyPage, presetPage, categoryPageIndex, page, complexity));
            y += 19;
        }

        List<Variation> variations = variations();
        if (!variations.isEmpty() && y + 30 < bodyBottom) {
            y += 5;
            browserPresetLabelY = y;
            y += 12;
            int roomRows = Math.max(1, (bodyBottom - y - 7) / 20);
            int visible = Math.min(2, roomRows);
            int pages = Math.max(1, (variations.size() + visible - 1) / visible);
            presetPageIndex = Math.min(presetPage, pages - 1);
            presetPageCount = pages;
            int start = presetPageIndex * visible;
            int end = Math.min(variations.size(), start + visible);
            for (int i = start; i < end; i++) {
                Variation variation = variations.get(i);
                button(x, y, w, 18, fit(variation.label(), w - 8), SOFT,
                        () -> applyVariation(variation));
                y += 20;
            }
            if (pages > 1 && y + 17 < bodyBottom) {
                presetPagerY = y;
                buildPager(x, y, w, presetPageIndex, pages,
                        page -> reopen(propertyPage, page, categoryPageIndex, typePageIndex, complexity));
            }
        }
    }

    private void buildInspector() {
        DAI_CreatorSchemaDefinition schema = DAI_CreatorRuntime.schema();
        if (schema == null) return;

        int x = inspectorX + 8;
        int w = inspectorW - 16;
        int y = bodyTop + 37;
        int footerH = compactHeight() ? 50 : 62;
        int bottom = bodyBottom - footerH;

        List<JsonObject> fields = visibleFields(schema.fields());
        int rowH = compactHeight() ? 31 : 34;
        int visibleRows = Math.max(1, (bottom - y) / rowH);
        int pages = Math.max(1, (fields.size() + visibleRows - 1) / visibleRows);
        int actualPage = Math.min(propertyPage, pages - 1);
        int start = actualPage * visibleRows;
        int end = Math.min(fields.size(), start + visibleRows);
        fieldBindings.clear();

        for (int i = start; i < end; i++) {
            JsonObject field = fields.get(i);
            String path = DAI_CreatorSchemaDefinition.string(field, "path", "");
            if (path.isBlank()) continue;
            String label = DAI_CreatorSchemaDefinition.string(field, "label", path);
            String fallback = valueText(field.get("default"));
            EditBox edit = new EditBox(font, x, y + 11, w, 18, Component.literal(label));
            edit.setValue(DAI_CreatorRuntime.get(path, fallback));
            addRenderableWidget(edit);
            fieldBindings.add(new FieldBinding(path, label, edit, y));
            y += rowH;
        }

        int footerY = bodyBottom - footerH + 5;
        if (pages > 1) {
            int navW = Math.max(26, (w - 4) / 2);
            button(x, footerY, navW, 17, "<", SOFT,
                    () -> reopen(Math.floorMod(actualPage - 1, pages), presetPageIndex,
                            categoryPageIndex, typePageIndex, complexity));
            button(x + w - navW, footerY, navW, 17, ">", SOFT,
                    () -> reopen((actualPage + 1) % pages, presetPageIndex,
                            categoryPageIndex, typePageIndex, complexity));
            footerY += 20;
        }

        int gap = 3;
        int half = Math.max(34, (w - gap) / 2);
        button(x, footerY, half, 18, detailLabel(), SOFT, this::cycleDetail);
        button(x + half + gap, footerY, w - half - gap, 18, "APPLY", ACTION, this::applyVisible);
        footerY += 21;

        boolean hasTools = schema.tools().size() > 0;
        boolean hasActions = schema.rightActions().size() > 0;
        if (footerY + 18 <= bodyBottom - 4) {
            if (hasTools && hasActions) {
                button(x, footerY, half, 18, "TOOLS", SOFT, this::openTools);
                button(x + half + gap, footerY, w - half - gap, 18, "ACTION", MODULE,
                        this::executeFirstSchemaAction);
            } else if (hasTools) {
                button(x, footerY, w, 18, "TOOLS", SOFT, this::openTools);
            } else if (hasActions) {
                button(x, footerY, w, 18, "ACTION", MODULE, this::executeFirstSchemaAction);
            }
        }
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
        reopen(0, 0, 0, 0, complexity);
    }

    private void selectCategory(String rail, String category) {
        commitVisibleFields();
        List<DAI_CreatorSchemaRegistry.Entry> entries = categoryTypes(rail, category);
        if (entries.isEmpty()) return;
        DAI_CreatorRuntime.selectSchema(entries.getFirst().id().toString());
        status = prettyCategory(category);
        reopen(0, 0, categoryPageIndex, 0, complexity);
    }

    private void selectSchema(String schemaId) {
        commitVisibleFields();
        DAI_CreatorRuntime.selectSchema(schemaId);
        status = "SELECTED";
        reopen(0, 0, categoryPageIndex, 0, complexity);
    }

    private void create() {
        Vec3 pos = playerPos();
        DAI_CreatorRuntime.createSelected(id(), pos);
        send("create", "", "", pos.x, pos.y, pos.z);
        syncRaw();
        status = "NEW // " + id();
        reopen(0, 0, categoryPageIndex, typePageIndex, complexity);
    }

    private void load() {
        boolean local = DAI_CreatorRuntime.loadSelected(id());
        if (local) {
            send("create", "", "", 0, 0, 0);
            syncRaw();
            status = "LOADED // " + id();
            reopen(0, 0, categoryPageIndex, typePageIndex, complexity);
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
        reopen(propertyPage, presetPageIndex, categoryPageIndex, typePageIndex, complexity);
    }

    private void toggleTest() {
        commitVisibleFields();
        DAI_CreatorRuntime.setMode(DAI_CreatorRuntime.isTesting()
                ? DAI_CreatorRuntime.EditorMode.PREVIEW
                : DAI_CreatorRuntime.EditorMode.SIMULATE);
        DAI_CreatorSchemaDefinition schema = DAI_CreatorRuntime.schema();
        JsonObject preview = schema == null ? new JsonObject()
                : DAI_CreatorSchemaDefinition.object(schema.json(), "preview");
        String adapter = DAI_CreatorSchemaDefinition.string(preview, "adapter", "");
        String action = DAI_CreatorSchemaDefinition.string(preview, "action", "");
        send("preview_config", adapter, action, 0, 0, 0);
        send("mode", adapter, DAI_CreatorRuntime.mode().name().toLowerCase(Locale.ROOT), 0, 0, 0);
        status = DAI_CreatorRuntime.isTesting() ? "TESTING" : "PREVIEW";
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
        graphics.fillGradient(0, 0, width, height, 0xF2080910, 0xF0120719);
        graphics.fillGradient(0, 0, width, bodyTop - 3, 0xF01B0E25, 0xC00C0A13);

        panel(graphics, browserX, bodyTop, browserW, bodyBottom - bodyTop, 0xBD0B0B12, 0xFF5B3C7A);
        panel(graphics, previewX, bodyTop, previewW, bodyBottom - bodyTop, 0xC00A0C12, 0xFF7347A0);
        panel(graphics, inspectorX, bodyTop, inspectorW, bodyBottom - bodyTop, 0xC0110C16, 0xFFFF812B);

        drawText(graphics, "D.A.I. // CREATOR 3.9", 9, 7, 0xFFFF9A4D);
        if (width >= 560) drawText(graphics, "VISUAL GAME CREATOR", 9, 19, 0xFFB985E0);

        DAI_CreatorSchemaDefinition schema = DAI_CreatorRuntime.schema();
        drawText(graphics, "CREATE", browserX + 7, bodyTop + 8, 0xFFD8B6F0);
        drawText(graphics, "PREVIEW", previewX + 9, bodyTop + 8, 0xFFFFB277);
        drawText(graphics, "PROPERTIES", inspectorX + 9, bodyTop + 8, 0xFFFFB277);

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

            drawAdaptiveText(graphics, schema.shortName(), inspectorX + 9, bodyTop + 21,
                    inspectorW - 18, 0xFFD7C5E4, 0.62F);
        }

        drawText(graphics, "CATEGORY", browserX + 7, browserCategoryLabelY, 0xFF9276A8);
        drawText(graphics, "TYPE", browserX + 7, browserTypeLabelY, 0xFF9276A8);
        if (browserPresetLabelY >= 0) {
            drawText(graphics, "START FROM", browserX + 7, browserPresetLabelY, 0xFF9276A8);
        }
        if (categoryPagerY >= 0) {
            drawPageIndicator(graphics, browserX, categoryPagerY + 4, browserW,
                    categoryPageIndex, categoryPageCount);
        }
        if (typePagerY >= 0) {
            drawPageIndicator(graphics, browserX, typePagerY + 4, browserW,
                    typePageIndex, typePageCount);
        }
        if (presetPagerY >= 0) {
            drawPageIndicator(graphics, browserX, presetPagerY + 4, browserW,
                    presetPageIndex, presetPageCount);
        }

        for (FieldBinding binding : fieldBindings) {
            drawAdaptiveText(graphics, binding.label(), inspectorX + 8, binding.y(),
                    inspectorW - 16, 0xFFD7C5E4, 0.62F);
        }

        drawAdaptiveText(graphics, status, previewX + 9, bodyBottom - 12,
                previewW - 18, 0xFFFFC087, 0.58F);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPreview(GuiGraphicsExtractor graphics, DAI_CreatorSchemaDefinition schema, float partialTick, int contentY) {
        int x = previewX + 9;
        int y = contentY;
        int w = Math.max(20, previewW - 18);
        int h = Math.max(28, bodyBottom - y - 20);
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

        if (schema.previewType().equals("world")) {
            renderWorldPreview(graphics, x, y, w, h);
            return;
        }
        renderDocumentPreview(graphics, x, y, w, h);
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
    public boolean isPauseScreen() { return false; }

    private void reopen(int newProperty, int newPreset, int newCategory, int newType, int newComplexity) {
        Minecraft.getInstance().gui.setScreen(new DAI_CreatorScreen(
                DAI_CreatorRuntime.schemaId(), newProperty, newPreset,
                newCategory, newType, newComplexity));
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
    private record Variation(String label, JsonObject patch) {}
    private record CategoryBucket(String name, List<DAI_CreatorSchemaRegistry.Entry> entries) {}
}

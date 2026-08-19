package io.github.j12h36h.dai.client.entity.mesh;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Parses the browser converter's dai:model/1.x mesh JSON into render-ready triangles. */
public final class DAI_MeshModelParser {

    public static final int MAX_VERTICES = 500_000;
    public static final int MAX_UVS = 500_000;
    public static final int MAX_NORMALS = 500_000;
    public static final int MAX_TRIANGLES = 250_000;

    private static final Identifier FALLBACK_TEXTURE = Identifier.fromNamespaceAndPath(
            DAI_Core.MODID,
            "textures/misc/mesh_white.png"
    );

    private DAI_MeshModelParser() {}

    public static DAI_MeshModel parse(
            Identifier modelId,
            JsonObject root,
            ResourceManager resourceManager
    ) {
        if (modelId == null) throw new IllegalArgumentException("Mesh model id is missing.");
        if (root == null) throw new IllegalArgumentException("Mesh model JSON is empty.");

        String format = string(root, "format");
        if (!format.isBlank() && !format.toLowerCase(Locale.ROOT).startsWith("dai:model/")) {
            throw new IllegalArgumentException("Unsupported model format '" + format + "'.");
        }

        JsonObject geometry = object(root, "geometry");
        if (geometry == null) throw new IllegalArgumentException("Missing 'geometry' object.");

        String type = string(geometry, "type");
        if (!"mesh".equalsIgnoreCase(type)) {
            throw new IllegalArgumentException("geometry.type must be 'mesh'.");
        }

        String coordinateSystem = string(root, "coordinate_system");
        if (!coordinateSystem.isBlank()
                && !"minecraft_y_up".equalsIgnoreCase(coordinateSystem)
                && !"minecraft".equalsIgnoreCase(coordinateSystem)) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Mesh '{}' declares coordinate_system='{}'. DAI renders mesh coordinates as Minecraft Y-up without an implicit axis conversion.",
                    modelId,
                    coordinateSystem
            );
        }

        List<Vec3> positions = parseVec3List(geometry.get("vertices"), MAX_VERTICES, "vertices");
        List<Vec2> uvs = parseVec2List(geometry.get("uvs"), MAX_UVS, "uvs");
        List<Vec3> normals = parseVec3List(geometry.get("normals"), MAX_NORMALS, "normals");

        if (positions.isEmpty()) {
            throw new IllegalArgumentException("Mesh has no vertices.");
        }

        MaterialStyle defaultStyle = resolveStyle(
                modelId,
                root,
                geometry,
                resourceManager,
                null
        );
        Map<String, MaterialStyle> materialStyles = parseMaterialStyles(
                modelId,
                root,
                resourceManager,
                defaultStyle
        );

        JsonElement facesElement = geometry.get("faces");
        if (facesElement == null || !facesElement.isJsonArray()) {
            throw new IllegalArgumentException("Mesh is missing geometry.faces[].");
        }

        LinkedHashMap<SectionKey, ArrayList<DAI_MeshModel.Triangle>> sectionTriangles = new LinkedHashMap<>();
        int triangleCount = 0;
        int skippedFaces = 0;

        for (JsonElement faceElement : facesElement.getAsJsonArray()) {
            ParsedFace face = parseFace(faceElement);
            if (face == null || face.corners().size() < 3) {
                skippedFaces++;
                continue;
            }

            String material = face.material();
            MaterialStyle style = material.isBlank()
                    ? defaultStyle
                    : materialStyles.getOrDefault(material, defaultStyle);
            SectionKey key = new SectionKey(material, style.texture(), style.renderMode());
            ArrayList<DAI_MeshModel.Triangle> output = sectionTriangles.computeIfAbsent(
                    key,
                    ignored -> new ArrayList<>()
            );

            Corner first = face.corners().get(0);
            for (int i = 1; i < face.corners().size() - 1; i++) {
                if (triangleCount >= MAX_TRIANGLES) {
                    throw new IllegalArgumentException(
                            "Mesh exceeds the DAI safety limit of " + MAX_TRIANGLES + " triangles."
                    );
                }

                Corner second = face.corners().get(i);
                Corner third = face.corners().get(i + 1);
                DAI_MeshModel.Triangle triangle = buildTriangle(
                        first,
                        second,
                        third,
                        positions,
                        uvs,
                        normals
                );
                if (triangle == null) {
                    skippedFaces++;
                    continue;
                }

                output.add(triangle);
                triangleCount++;
            }
        }

        if (triangleCount == 0) {
            throw new IllegalArgumentException("Mesh contains no valid triangles.");
        }

        ArrayList<DAI_MeshModel.Section> sections = new ArrayList<>();
        for (Map.Entry<SectionKey, ArrayList<DAI_MeshModel.Triangle>> entry : sectionTriangles.entrySet()) {
            SectionKey key = entry.getKey();
            if (entry.getValue().isEmpty()) continue;
            sections.add(new DAI_MeshModel.Section(
                    key.material(),
                    key.texture(),
                    key.renderMode(),
                    entry.getValue()
            ));
        }

        if (skippedFaces > 0) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Mesh '{}' skipped {} invalid/degenerate face fragment(s) while loading.",
                    modelId,
                    skippedFaces
            );
        }

        return new DAI_MeshModel(modelId, sections);
    }

    private static DAI_MeshModel.Triangle buildTriangle(
            Corner c0,
            Corner c1,
            Corner c2,
            List<Vec3> positions,
            List<Vec2> uvs,
            List<Vec3> normals
    ) {
        Vec3 p0 = indexed(positions, c0.vertex());
        Vec3 p1 = indexed(positions, c1.vertex());
        Vec3 p2 = indexed(positions, c2.vertex());
        if (p0 == null || p1 == null || p2 == null) return null;

        Vec3 computedNormal = faceNormal(p0, p1, p2);
        if (computedNormal == null) return null;

        return new DAI_MeshModel.Triangle(
                renderVertex(c0, p0, uvs, normals, computedNormal),
                renderVertex(c1, p1, uvs, normals, computedNormal),
                renderVertex(c2, p2, uvs, normals, computedNormal)
        );
    }

    private static DAI_MeshModel.Vertex renderVertex(
            Corner corner,
            Vec3 position,
            List<Vec2> uvs,
            List<Vec3> normals,
            Vec3 fallbackNormal
    ) {
        Vec2 uv = indexed(uvs, corner.uv());
        if (uv == null) uv = Vec2.ZERO;

        Vec3 normal = indexed(normals, corner.normal());
        if (normal == null) normal = fallbackNormal;
        normal = normalized(normal, fallbackNormal);

        return new DAI_MeshModel.Vertex(
                position.x(), position.y(), position.z(),
                uv.x(), uv.y(),
                normal.x(), normal.y(), normal.z()
        );
    }

    private static ParsedFace parseFace(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;

        if (element.isJsonArray()) {
            List<Integer> vertices = intList(element.getAsJsonArray());
            ArrayList<Corner> corners = new ArrayList<>(vertices.size());
            for (Integer vertex : vertices) {
                if (vertex == null) continue;
                corners.add(new Corner(vertex, null, null));
            }
            return new ParsedFace(corners, "");
        }

        if (!element.isJsonObject()) return null;
        JsonObject object = element.getAsJsonObject();
        String material = string(object, "material");

        JsonElement verticesElement = object.get("vertices");
        if (verticesElement == null || !verticesElement.isJsonArray()) return null;
        JsonArray verticesArray = verticesElement.getAsJsonArray();

        // Alternate corner form: [{"v":0,"vt":0,"vn":0}, ...]
        if (!(verticesArray.size() == 0) && verticesArray.get(0).isJsonObject()) {
            ArrayList<Corner> corners = new ArrayList<>();
            for (JsonElement cornerElement : verticesArray) {
                if (!cornerElement.isJsonObject()) continue;
                JsonObject cornerObject = cornerElement.getAsJsonObject();
                Integer v = nullableInt(cornerObject.get("v"));
                if (v == null) v = nullableInt(cornerObject.get("vertex"));
                if (v == null) continue;
                Integer uv = nullableInt(cornerObject.get("vt"));
                if (uv == null) uv = nullableInt(cornerObject.get("uv"));
                Integer normal = nullableInt(cornerObject.get("vn"));
                if (normal == null) normal = nullableInt(cornerObject.get("normal"));
                corners.add(new Corner(v, uv, normal));
            }
            return new ParsedFace(corners, material);
        }

        List<Integer> vertices = intList(verticesArray);
        List<Integer> uvIndices = nullableIntList(array(object, "uvs"));
        if (uvIndices.isEmpty()) uvIndices = nullableIntList(array(object, "uv"));
        List<Integer> normalIndices = nullableIntList(array(object, "normals"));
        if (normalIndices.isEmpty()) normalIndices = nullableIntList(array(object, "normal"));

        ArrayList<Corner> corners = new ArrayList<>(vertices.size());
        for (int i = 0; i < vertices.size(); i++) {
            Integer vertex = vertices.get(i);
            if (vertex == null) continue;
            Integer uv = i < uvIndices.size() ? uvIndices.get(i) : null;
            Integer normal = i < normalIndices.size() ? normalIndices.get(i) : null;
            corners.add(new Corner(vertex, uv, normal));
        }
        return new ParsedFace(corners, material);
    }

    private static Map<String, MaterialStyle> parseMaterialStyles(
            Identifier modelId,
            JsonObject root,
            ResourceManager resourceManager,
            MaterialStyle defaultStyle
    ) {
        JsonObject materials = object(root, "materials");
        if (materials == null || materials.entrySet().isEmpty()) return Map.of();

        LinkedHashMap<String, MaterialStyle> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : materials.entrySet()) {
            String name = entry.getKey();
            JsonElement value = entry.getValue();
            if (value == null || value.isJsonNull()) continue;

            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                Identifier texture = resolveTexture(
                        modelId,
                        value.getAsString(),
                        resourceManager,
                        defaultStyle.texture()
                );
                result.put(name, new MaterialStyle(texture, defaultStyle.renderMode()));
                continue;
            }

            if (!value.isJsonObject()) continue;
            JsonObject materialObject = value.getAsJsonObject();
            String textureValue = string(materialObject, "texture");
            Identifier texture = textureValue.isBlank()
                    ? defaultStyle.texture()
                    : resolveTexture(modelId, textureValue, resourceManager, defaultStyle.texture());
            DAI_MeshModel.RenderMode renderMode = DAI_MeshModel.RenderMode.parse(
                    firstNonBlank(
                            string(materialObject, "render_type"),
                            string(materialObject, "render_mode"),
                            string(materialObject, "blend")
                    )
            );
            if (firstNonBlank(
                    string(materialObject, "render_type"),
                    string(materialObject, "render_mode"),
                    string(materialObject, "blend")
            ).isBlank()) {
                renderMode = defaultStyle.renderMode();
            }
            result.put(name, new MaterialStyle(texture, renderMode));
        }
        return Map.copyOf(result);
    }

    private static MaterialStyle resolveStyle(
            Identifier modelId,
            JsonObject root,
            JsonObject geometry,
            ResourceManager resourceManager,
            MaterialStyle fallback
    ) {
        String textureValue = firstNonBlank(
                string(root, "texture"),
                string(geometry, "texture")
        );
        Identifier fallbackTexture = fallback == null ? FALLBACK_TEXTURE : fallback.texture();
        Identifier texture;
        if (!textureValue.isBlank()) {
            texture = resolveTexture(modelId, textureValue, resourceManager, fallbackTexture);
        } else {
            Identifier conventional = Identifier.fromNamespaceAndPath(
                    modelId.getNamespace(),
                    "textures/dai/models/" + modelId.getPath() + ".png"
            );
            texture = resourceExists(resourceManager, conventional) ? conventional : fallbackTexture;
        }

        String renderValue = firstNonBlank(
                string(root, "render_type"),
                string(root, "render_mode"),
                string(geometry, "render_type"),
                string(geometry, "render_mode")
        );
        DAI_MeshModel.RenderMode mode = renderValue.isBlank() && fallback != null
                ? fallback.renderMode()
                : DAI_MeshModel.RenderMode.parse(renderValue);

        return new MaterialStyle(texture, mode);
    }

    private static Identifier resolveTexture(
            Identifier modelId,
            String raw,
            ResourceManager resourceManager,
            Identifier fallback
    ) {
        if (raw == null || raw.isBlank()) return fallback;
        String value = raw.trim().replace('\\', '/');

        Identifier parsed;
        if (value.contains(":")) {
            parsed = Identifier.tryParse(value);
        } else {
            parsed = Identifier.fromNamespaceAndPath(modelId.getNamespace(), value);
        }
        if (parsed == null) {
            DAI_Core.LOGGER.warn("<DAI>: Invalid texture id '{}' in mesh '{}'.", raw, modelId);
            return fallback;
        }

        String path = parsed.getPath();
        if (!path.startsWith("textures/")) path = "textures/" + path;
        if (!path.endsWith(".png")) path = path + ".png";
        Identifier normalized = Identifier.fromNamespaceAndPath(parsed.getNamespace(), path);

        if (!resourceExists(resourceManager, normalized)) {
            // Keep the authored identifier so Minecraft's missing-texture behavior
            // makes a bad path obvious instead of silently hiding the mistake.
            DAI_Core.LOGGER.warn(
                    "<DAI>: Mesh '{}' references texture '{}' which is not currently present in the resource manager.",
                    modelId,
                    normalized
            );
        }
        return normalized;
    }

    private static boolean resourceExists(ResourceManager resourceManager, Identifier id) {
        if (resourceManager == null || id == null) return false;
        try {
            return resourceManager.getResource(id).isPresent();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static List<Vec3> parseVec3List(JsonElement element, int limit, String field) {
        if (element == null || !element.isJsonArray()) return List.of();
        JsonArray array = element.getAsJsonArray();
        if (array.size() == 0) return List.of();

        ArrayList<Vec3> result = new ArrayList<>();
        if (array.get(0).isJsonArray()) {
            for (JsonElement item : array) {
                if (!item.isJsonArray()) continue;
                JsonArray tuple = item.getAsJsonArray();
                if (tuple.size() < 3) continue;
                Float x = finiteFloat(tuple.get(0));
                Float y = finiteFloat(tuple.get(1));
                Float z = finiteFloat(tuple.get(2));
                if (x == null || y == null || z == null) continue;
                result.add(new Vec3(x, y, z));
                if (result.size() > limit) {
                    throw new IllegalArgumentException(field + " exceeds the DAI safety limit of " + limit + ".");
                }
            }
            return List.copyOf(result);
        }

        for (int i = 0; i + 2 < array.size(); i += 3) {
            Float x = finiteFloat(array.get(i));
            Float y = finiteFloat(array.get(i + 1));
            Float z = finiteFloat(array.get(i + 2));
            if (x == null || y == null || z == null) continue;
            result.add(new Vec3(x, y, z));
            if (result.size() > limit) {
                throw new IllegalArgumentException(field + " exceeds the DAI safety limit of " + limit + ".");
            }
        }
        return List.copyOf(result);
    }

    private static List<Vec2> parseVec2List(JsonElement element, int limit, String field) {
        if (element == null || !element.isJsonArray()) return List.of();
        JsonArray array = element.getAsJsonArray();
        if (array.size() == 0) return List.of();

        ArrayList<Vec2> result = new ArrayList<>();
        if (array.get(0).isJsonArray()) {
            for (JsonElement item : array) {
                if (!item.isJsonArray()) continue;
                JsonArray tuple = item.getAsJsonArray();
                if (tuple.size() < 2) continue;
                Float x = finiteFloat(tuple.get(0));
                Float y = finiteFloat(tuple.get(1));
                if (x == null || y == null) continue;
                result.add(new Vec2(x, y));
                if (result.size() > limit) {
                    throw new IllegalArgumentException(field + " exceeds the DAI safety limit of " + limit + ".");
                }
            }
            return List.copyOf(result);
        }

        for (int i = 0; i + 1 < array.size(); i += 2) {
            Float x = finiteFloat(array.get(i));
            Float y = finiteFloat(array.get(i + 1));
            if (x == null || y == null) continue;
            result.add(new Vec2(x, y));
            if (result.size() > limit) {
                throw new IllegalArgumentException(field + " exceeds the DAI safety limit of " + limit + ".");
            }
        }
        return List.copyOf(result);
    }

    private static Vec3 faceNormal(Vec3 p0, Vec3 p1, Vec3 p2) {
        float ax = p1.x() - p0.x();
        float ay = p1.y() - p0.y();
        float az = p1.z() - p0.z();
        float bx = p2.x() - p0.x();
        float by = p2.y() - p0.y();
        float bz = p2.z() - p0.z();

        Vec3 normal = new Vec3(
                ay * bz - az * by,
                az * bx - ax * bz,
                ax * by - ay * bx
        );
        float lengthSquared = normal.x() * normal.x()
                + normal.y() * normal.y()
                + normal.z() * normal.z();
        if (!Float.isFinite(lengthSquared) || lengthSquared < 1.0E-12F) return null;
        return normalized(normal, new Vec3(0.0F, 1.0F, 0.0F));
    }

    private static Vec3 normalized(Vec3 value, Vec3 fallback) {
        float lengthSquared = value.x() * value.x()
                + value.y() * value.y()
                + value.z() * value.z();
        if (!Float.isFinite(lengthSquared) || lengthSquared < 1.0E-12F) return fallback;
        float inverse = (float) (1.0D / Math.sqrt(lengthSquared));
        return new Vec3(value.x() * inverse, value.y() * inverse, value.z() * inverse);
    }

    private static <T> T indexed(List<T> values, Integer index) {
        if (values == null || values.isEmpty() || index == null) return null;
        if (index < 0 || index >= values.size()) return null;
        return values.get(index);
    }

    private static JsonObject object(JsonObject root, String key) {
        if (root == null || key == null) return null;
        JsonElement element = root.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonObject root, String key) {
        if (root == null || key == null) return null;
        JsonElement element = root.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static String string(JsonObject root, String key) {
        if (root == null || key == null) return "";
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return "";
        try {
            return element.getAsString().trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private static List<Integer> intList(JsonArray array) {
        if (array == null || array.size() == 0) return List.of();
        ArrayList<Integer> result = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            Integer value = nullableInt(element);
            if (value != null) result.add(value);
        }
        return result;
    }

    private static List<Integer> nullableIntList(JsonArray array) {
        if (array == null || array.size() == 0) return List.of();
        ArrayList<Integer> result = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            result.add(nullableInt(element));
        }
        return result;
    }

    private static Integer nullableInt(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return null;
        try {
            int value = element.getAsInt();
            return value < 0 ? null : value;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Float finiteFloat(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return null;
        try {
            float value = element.getAsFloat();
            return Float.isFinite(value) ? value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private record Vec2(float x, float y) {
        private static final Vec2 ZERO = new Vec2(0.0F, 0.0F);
    }

    private record Vec3(float x, float y, float z) {}
    private record Corner(int vertex, Integer uv, Integer normal) {}
    private record ParsedFace(List<Corner> corners, String material) {
        private ParsedFace {
            corners = corners == null ? List.of() : List.copyOf(corners);
            material = material == null ? "" : material;
        }
    }
    private record MaterialStyle(Identifier texture, DAI_MeshModel.RenderMode renderMode) {}
    private record SectionKey(String material, Identifier texture, DAI_MeshModel.RenderMode renderMode) {}
}

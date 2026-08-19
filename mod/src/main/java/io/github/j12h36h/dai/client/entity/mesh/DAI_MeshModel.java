package io.github.j12h36h.dai.client.entity.mesh;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Immutable, render-ready representation of a DAI triangle mesh.
 *
 * Mesh coordinates are expressed in Minecraft block units using +Y as up.
 * Faces are stored as triangles. The renderer expands each triangle to a
 * degenerate quad because the standard entity RenderTypes use QUADS.
 */
public final class DAI_MeshModel {

    public enum RenderMode {
        SOLID,
        CUTOUT,
        TRANSLUCENT,
        EMISSIVE;

        public static RenderMode parse(String value) {
            if (value == null || value.isBlank()) return CUTOUT;
            return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "solid", "opaque" -> SOLID;
                case "translucent", "transparent", "blend", "blended" -> TRANSLUCENT;
                case "emissive", "fullbright", "full_bright" -> EMISSIVE;
                default -> CUTOUT;
            };
        }
    }

    public record Vertex(
            float x,
            float y,
            float z,
            float u,
            float v,
            float normalX,
            float normalY,
            float normalZ
    ) {}

    public record Triangle(Vertex a, Vertex b, Vertex c) {}

    /**
     * A section is a group of triangles that share texture and render mode.
     * This lets converted OBJ material groups opt into different textures
     * without requiring a Java renderer change later.
     */
    public record Section(
            String material,
            Identifier texture,
            RenderMode renderMode,
            List<Triangle> triangles
    ) {
        public Section {
            material = material == null ? "" : material;
            triangles = triangles == null ? List.of() : List.copyOf(triangles);
        }

        public RenderType renderType() {
            return switch (renderMode) {
                case SOLID -> RenderTypes.entitySolid(texture);
                case TRANSLUCENT -> RenderTypes.entityTranslucent(texture);
                case EMISSIVE -> RenderTypes.entityTranslucentEmissive(texture);
                case CUTOUT -> RenderTypes.entityCutout(texture);
            };
        }

        public boolean fullBright() {
            return renderMode == RenderMode.EMISSIVE;
        }
    }

    private final Identifier id;
    private final List<Section> sections;
    private final int triangleCount;

    public DAI_MeshModel(Identifier id, List<Section> sections) {
        this.id = id;
        this.sections = sections == null ? List.of() : List.copyOf(sections);
        int count = 0;
        for (Section section : this.sections) {
            count += section.triangles().size();
        }
        this.triangleCount = count;
    }

    public Identifier id() {
        return id;
    }

    public List<Section> sections() {
        return sections;
    }

    public int triangleCount() {
        return triangleCount;
    }

    public boolean isEmpty() {
        return triangleCount <= 0;
    }
}

package io.github.j12h36h.dai.comiclife.model;

import java.util.ArrayList;
import java.util.List;

public final class ComicLifePage {
    public String kind = "story";
    public String heading = "";
    public String subheading = "";
    public List<ComicLifePanel> panels = new ArrayList<>();

    public void normalize() {
        if (kind == null || kind.isBlank()) kind = "story";
        if (heading == null) heading = "";
        if (subheading == null) subheading = "";
        if (panels == null) panels = new ArrayList<>();
        panels.removeIf(v -> v == null);
        for (ComicLifePanel panel : panels) panel.normalize();
    }
}

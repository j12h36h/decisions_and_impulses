package io.github.j12h36h.dai.story.model;

import java.util.ArrayList;
import java.util.List;

public final class DAI_StoryPage {
    public String kind = "story";
    public String heading = "";
    public String subheading = "";
    public List<DAI_StoryPanel> panels = new ArrayList<>();

    public void normalize() {
        if (kind == null || kind.isBlank()) kind = "story";
        if (heading == null) heading = "";
        if (subheading == null) subheading = "";
        if (panels == null) panels = new ArrayList<>();
        panels.removeIf(p -> p == null);
        panels.forEach(DAI_StoryPanel::normalize);
    }
}

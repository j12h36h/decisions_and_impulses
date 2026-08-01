package io.github.j12h36h.dai.input;

public final class Input_Action {

    private boolean inventory;

    public boolean inventory() {
        return inventory;
    }

    public void inventory(boolean value) {
        this.inventory = value;
    }

    public void clear() {
        inventory = false;
    }
}

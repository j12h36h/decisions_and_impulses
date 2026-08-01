package io.github.j12h36h.dai.input;

public final class Input_Action {

    private boolean attack;
    private boolean use;

    private boolean inventory;

    public boolean attack() {
        return attack;
    }

    public void attack(boolean value) {
        attack = value;
    }

    public boolean use() {
        return use;
    }

    public void use(boolean value) {
        use = value;
    }

    public boolean inventory() {
        return inventory;
    }

    public void inventory(boolean value) {
        inventory = value;
    }

    public void clear() {
        attack = false;
        use = false;
        inventory = false;
    }
}

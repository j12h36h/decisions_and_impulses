package io.github.j12h36h.dai.input;

import io.github.j12h36h.dai.core.Config;
import net.minecraft.client.Options;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

import static io.github.j12h36h.dai.core.DAI.LOGGER;

public class DAI_Keyboard extends KeyboardInput {

    public DAI_Keyboard(Options options) {
        super(options);
    }

    @Override
    public void tick() {
        // Let vanilla build the default input.
        super.tick();

        if (!Config.TOGGLE_KEYBINDS.getAsBoolean()) {
            return;
        }

        Input_Movement movement = Input_Manager.movement();

        float forward = movement.forward();
        float strafe = movement.strafe();

        this.keyPresses = new Input(
                forward > 0.0F,          // forward
                forward < 0.0F,          // backward
                strafe > 0.0F,           // left
                strafe < 0.0F,           // right
                movement.jump(),
                movement.sneak(),
                movement.sprint()
        );

        this.moveVector = new Vec2(strafe, forward).normalized();
    }
}
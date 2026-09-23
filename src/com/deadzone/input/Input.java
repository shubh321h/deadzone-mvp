package com.deadzone.input;

import com.deadzone.core.InputState;

/** Bridge between Android touch views and the frame-rate input snapshot. */
public class Input {
    public final InputState state = new InputState();

    public void cameraDrag(float dx, float dy) {
        state.camDX += dx;
        state.camDY += dy;
    }

    public void endFrame() {
        state.clearEdges();
    }
}

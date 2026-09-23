package com.deadzone.core;

/** Frame snapshot of touch input, consumed by Player. Plain data so the core
 *  simulation stays Android-free (testable on the JVM). */
public class InputState {
    public float joyX, joyY;       // -1..1, y+ = forward
    public float camDX, camDY;     // camera drag deltas (px) since last frame
    public boolean fire, aim, sprint, crouch;
    public boolean reloadTap, swapTap, interactTap;

    public void clearEdges() {
        camDX = 0; camDY = 0;
        reloadTap = false; swapTap = false; interactTap = false;
    }
}

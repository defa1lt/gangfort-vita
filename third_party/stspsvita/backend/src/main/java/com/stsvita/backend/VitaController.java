package com.stsvita.backend;

import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerListener;
import com.badlogic.gdx.controllers.PovDirection;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/** Gamepad state in the Android button and axis numbering consumed by Gangfort. */
public class VitaController implements Controller {

    private final boolean[] buttons = new boolean[104];
    private final float[] axes = new float[17];
    private PovDirection pov = PovDirection.center;
    private final Array<ControllerListener> listeners = new Array<>();

    void setButton(int index, boolean down) {
        if (index >= 0 && index < buttons.length) {
            buttons[index] = down;
        }
    }

    void setAxis(int index, float value) {
        if (index >= 0 && index < axes.length) {
            axes[index] = value;
        }
    }

    void setPov(PovDirection direction) {
        pov = direction;
    }

    /** Listeners added on this controller (global ones live in Controllers). */
    Array<ControllerListener> ownListeners() {
        return listeners;
    }

    @Override
    public boolean getButton(int buttonCode) {
        return buttonCode >= 0 && buttonCode < buttons.length && buttons[buttonCode];
    }

    @Override
    public float getAxis(int axisCode) {
        return axisCode >= 0 && axisCode < axes.length ? axes[axisCode] : 0f;
    }

    @Override
    public PovDirection getPov(int povCode) {
        return povCode == 0 ? pov : PovDirection.center;
    }

    @Override
    public boolean getSliderX(int sliderCode) {
        return false;
    }

    @Override
    public boolean getSliderY(int sliderCode) {
        return false;
    }

    @Override
    public Vector3 getAccelerometer(int accelerometerCode) {
        return Vector3.Zero;
    }

    @Override
    public void setAccelerometerSensitivity(float sensitivity) {
    }

    @Override
    public String getName() {
        return "PS Vita";
    }

    @Override
    public void addListener(ControllerListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(ControllerListener listener) {
        listeners.removeValue(listener, true);
    }
}

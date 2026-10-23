package com.stsvita.backend;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;

/** Physical gamepad and stable front-touch pointers, polled before each frame. */
public class VitaInput implements Input {

    private InputProcessor processor;
    private int x, y;
    private int deltaX, deltaY;
    private boolean touched;
    private boolean justTouched;
    private final int[] touchIds = {-1, -1, -1, -1, -1, -1};
    private final int[] touchXs = new int[6], touchYs = new int[6];
    private final int[] touchDxs = new int[6], touchDys = new int[6];
    private final int[] sampleIds = new int[6], sampleXs = new int[6], sampleYs = new int[6];

    /* SceCtrl button bits (psp2/ctrl.h). */
    private static final int BTN_START    = 0x000008;
    private static final int BTN_UP       = 0x000010;
    private static final int BTN_RIGHT    = 0x000020;
    private static final int BTN_DOWN     = 0x000040;
    private static final int BTN_LEFT     = 0x000080;
    private static final int BTN_LTRIGGER = 0x000100;
    private static final int BTN_RTRIGGER = 0x000200;
    private static final int BTN_TRIANGLE = 0x001000;
    private static final int BTN_CIRCLE   = 0x002000;
    private static final int BTN_CROSS    = 0x004000;
    private static final int BTN_SQUARE   = 0x008000;

    // Gangfort Android mapping: L/up jumps, R fires, Start/Triangle opens the menu.
    private static final int[][] BUTTON_INDICES = {
        {BTN_CROSS, 96},
        {BTN_CIRCLE, 97},
        {BTN_SQUARE, 99},
        {BTN_TRIANGLE | BTN_START, 100},
        {BTN_UP | BTN_LTRIGGER, 19},
        {BTN_DOWN, 20},
        {BTN_LEFT, 21},
        {BTN_RIGHT, 22},
        {BTN_RTRIGGER, 103},
    };

    private final VitaController controller = new VitaController();
    private final float[] prevAxes = new float[17];
    private com.badlogic.gdx.controllers.PovDirection prevPov =
            com.badlogic.gdx.controllers.PovDirection.center;

    public VitaInput() {
        com.badlogic.gdx.controllers.Controllers.stsvRegister(controller);
    }

    void releaseAll() {
        if (processor instanceof com.badlogic.gdx.scenes.scene2d.Stage) {
            ((com.badlogic.gdx.scenes.scene2d.Stage) processor).cancelTouchFocus();
        }
        updateTouches(0, sampleIds, sampleXs, sampleYs);
        updateController(0, 0, 0, 0, 0);
    }

    void updateController(int btns, float lx, float ly, float rx, float ry) {
        for (int[] mapping : BUTTON_INDICES) {
            int bit = mapping[0];
            int index = mapping[1];
            boolean down = (btns & bit) != 0;
            if (down && !controller.getButton(index)) {
                controller.setButton(index, true);
                for (com.badlogic.gdx.controllers.ControllerListener l : allControllerListeners()) {
                    l.buttonDown(controller, index);
                }
            } else if (!down && controller.getButton(index)) {
                controller.setButton(index, false);
                for (com.badlogic.gdx.controllers.ControllerListener l : allControllerListeners()) {
                    l.buttonUp(controller, index);
                }
            }
        }

        com.badlogic.gdx.controllers.PovDirection pov = povOf(btns);
        if (pov != prevPov) {
            prevPov = pov;
            controller.setPov(pov);
            for (com.badlogic.gdx.controllers.ControllerListener l : allControllerListeners()) {
                l.povMoved(controller, 0, pov);
            }
        }

        // Gangfort uses Android axis IDs and positive Y for up.
        dispatchAxis(0, deadzone(lx));
        dispatchAxis(1, -deadzone(ly));
        dispatchAxis(11, deadzone(rx));
        dispatchAxis(14, -deadzone(ry));
    }

    private void dispatchAxis(int stsAxis, float value) {
        if (Math.abs(value - prevAxes[stsAxis]) < 0.01f) {
            return;
        }
        prevAxes[stsAxis] = value;
        controller.setAxis(stsAxis, value);
        for (com.badlogic.gdx.controllers.ControllerListener l : allControllerListeners()) {
            l.axisMoved(controller, stsAxis, value);
        }
    }

    private static float deadzone(float v) {
        return Math.abs(v) < 0.25f ? 0f : v;
    }

    private static com.badlogic.gdx.controllers.PovDirection povOf(int btns) {
        boolean up = (btns & BTN_UP) != 0;
        boolean down = (btns & BTN_DOWN) != 0;
        boolean left = (btns & BTN_LEFT) != 0;
        boolean right = (btns & BTN_RIGHT) != 0;
        if (up) {
            return left ? com.badlogic.gdx.controllers.PovDirection.northWest
                 : right ? com.badlogic.gdx.controllers.PovDirection.northEast
                 : com.badlogic.gdx.controllers.PovDirection.north;
        }
        if (down) {
            return left ? com.badlogic.gdx.controllers.PovDirection.southWest
                 : right ? com.badlogic.gdx.controllers.PovDirection.southEast
                 : com.badlogic.gdx.controllers.PovDirection.south;
        }
        if (left) {
            return com.badlogic.gdx.controllers.PovDirection.west;
        }
        if (right) {
            return com.badlogic.gdx.controllers.PovDirection.east;
        }
        return com.badlogic.gdx.controllers.PovDirection.center;
    }

    private java.util.ArrayList<com.badlogic.gdx.controllers.ControllerListener> allControllerListeners() {
        java.util.ArrayList<com.badlogic.gdx.controllers.ControllerListener> all =
                new java.util.ArrayList<>();
        for (com.badlogic.gdx.controllers.ControllerListener l
                : com.badlogic.gdx.controllers.Controllers.stsvGetListeners()) {
            all.add(l);
        }
        for (com.badlogic.gdx.controllers.ControllerListener l : controller.ownListeners()) {
            all.add(l);
        }
        return all;
    }

    /* sceImeDialog state: at most one dialog at a time. When a
     * TextInputListener is set the result goes to it (getTextInput path);
     * otherwise the text is injected via keyTyped (StS's custom name field,
     * opened with START). */
    private boolean imeOpen;
    private TextInputListener imeListener;

    /** Opens the on-screen keyboard; false if unavailable (host harness). */
    private boolean imeStart(String title, String initial) {
        if (imeOpen) {
            return false;
        }
        char[] t = toChars0(title);
        char[] i = toChars0(initial);
        int rc = VitaNatives.imeOpen(org.teavm.interop.Address.ofData(t),
                org.teavm.interop.Address.ofData(i), 64);
        imeOpen = rc == 0;
        return imeOpen;
    }

    private static char[] toChars0(String s) {
        if (s == null) {
            s = "";
        }
        char[] c = new char[s.length() + 1];
        s.getChars(0, s.length(), c, 0);
        return c;
    }

    private String imeFetchResult() {
        char[] out = new char[65];
        int len = VitaNatives.imeResult(org.teavm.interop.Address.ofData(out), out.length);
        return new String(out, 0, len);
    }

    private void imeUpdate() {
        if (!imeOpen) {
            return;
        }
        int status = VitaNatives.imePoll();
        if (status == 0) {
            return;
        }
        imeOpen = false;
        TextInputListener listener = imeListener;
        imeListener = null;
        if (status == 1) {
            String text = imeFetchResult();
            VitaNatives.log("[ime] entered \"" + text + "\"");
            if (listener != null) {
                listener.input(text);
            } else if (processor != null) {
                for (int i = 0; i < text.length(); i++) {
                    processor.keyTyped(text.charAt(i));
                }
            }
        } else {
            VitaNatives.log("[ime] canceled");
            if (listener != null) {
                listener.canceled();
            }
        }
    }

    public void update() {
        imeUpdate();
        int count = Math.min(VitaNatives.touchCount(), sampleIds.length);
        for (int i = 0; i < count; i++) {
            sampleIds[i] = VitaNatives.touchId(i);
            sampleXs[i] = VitaNatives.touchX(i);
            sampleYs[i] = VitaNatives.touchY(i);
        }
        updateTouches(count, sampleIds, sampleXs, sampleYs);
        int btns = VitaNatives.ctrlButtons();
        updateController(btns, VitaNatives.ctrlAxis(0), VitaNatives.ctrlAxis(1),
                VitaNatives.ctrlAxis(2), VitaNatives.ctrlAxis(3));
    }

    void updateTouches(int count, int[] ids, int[] xs, int[] ys) {
        justTouched = false;
        touched = count > 0;
        for (int slot = 0; slot < touchIds.length; slot++) {
            touchDxs[slot] = touchDys[slot] = 0;
            if (touchIds[slot] < 0) continue;
            boolean found = false;
            for (int i = 0; i < count; i++) found |= ids[i] == touchIds[slot];
            if (!found) {
                touchIds[slot] = -1;
                if (processor != null) processor.touchUp(touchXs[slot], touchYs[slot], slot, Buttons.LEFT);
            }
        }
        for (int i = 0; i < count; i++) {
            int slot = -1;
            for (int s = 0; s < touchIds.length; s++) if (touchIds[s] == ids[i]) slot = s;
            boolean fresh = slot < 0;
            if (fresh) {
                for (int s = 0; s < touchIds.length; s++) if (touchIds[s] < 0) { slot = s; break; }
            }
            if (slot < 0) continue;
            touchDxs[slot] = fresh ? 0 : xs[i] - touchXs[slot];
            touchDys[slot] = fresh ? 0 : ys[i] - touchYs[slot];
            touchXs[slot] = xs[i]; touchYs[slot] = ys[i]; touchIds[slot] = ids[i];
            if (fresh) {
                justTouched = true;
                if (processor != null) processor.touchDown(xs[i], ys[i], slot, Buttons.LEFT);
            } else if (processor != null && (touchDxs[slot] != 0 || touchDys[slot] != 0)) {
                processor.touchDragged(xs[i], ys[i], slot);
            }
        }
        x = touchXs[0]; y = touchYs[0]; deltaX = touchDxs[0]; deltaY = touchDys[0];
    }

    /** SceCtrl bitmask for the controller layer. */
    public int buttons() {
        return VitaNatives.ctrlButtons();
    }

    public float axis(int axis) {
        return VitaNatives.ctrlAxis(axis);
    }

    @Override
    public float getAccelerometerX() {
        return 0;
    }

    @Override
    public float getAccelerometerY() {
        return 0;
    }

    @Override
    public float getAccelerometerZ() {
        return 0;
    }

    @Override
    public float getGyroscopeX() {
        return 0;
    }

    @Override
    public float getGyroscopeY() {
        return 0;
    }

    @Override
    public float getGyroscopeZ() {
        return 0;
    }

    @Override
    public int getX() {
        return touchXs[0];
    }

    @Override
    public int getX(int pointer) {
        return pointer >= 0 && pointer < 6 ? touchXs[pointer] : 0;
    }

    @Override
    public int getDeltaX() {
        return touchDxs[0];
    }

    @Override
    public int getDeltaX(int pointer) {
        return pointer >= 0 && pointer < 6 ? touchDxs[pointer] : 0;
    }

    @Override
    public int getY() {
        return touchYs[0];
    }

    @Override
    public int getY(int pointer) {
        return pointer >= 0 && pointer < 6 ? touchYs[pointer] : 0;
    }

    @Override
    public int getDeltaY() {
        return touchDys[0];
    }

    @Override
    public int getDeltaY(int pointer) {
        return pointer >= 0 && pointer < 6 ? touchDys[pointer] : 0;
    }

    @Override
    public boolean isTouched() {
        return touched;
    }

    @Override
    public boolean justTouched() {
        return justTouched;
    }

    @Override
    public boolean isTouched(int pointer) {
        return pointer >= 0 && pointer < 6 && touchIds[pointer] >= 0;
    }

    @Override
    public boolean isButtonPressed(int button) {
        return button == Buttons.LEFT && touched;
    }

    @Override
    public boolean isKeyPressed(int key) {
        return false;
    }

    @Override
    public boolean isKeyJustPressed(int key) {
        return false;
    }

    @Override
    public void getTextInput(TextInputListener listener, String title, String text, String hint) {
        if (listener == null) {
            return;
        }
        // sceImeDialog on-screen keyboard; delivered asynchronously by
        // imeUpdate() once the dialog closes. Where the IME is unavailable
        // (host harness), accept the caller's supplied default so text-entry
        // screens can proceed instead of being cancelled.
        if (imeStart(title, text)) {
            imeListener = listener;
            return;
        }
        String result = (text != null && text.trim().length() > 0) ? text : "Vita";
        listener.input(result);
    }

    @Override
    public void setOnscreenKeyboardVisible(boolean visible) {
    }

    @Override
    public void vibrate(int milliseconds) {
    }

    @Override
    public void vibrate(long[] pattern, int repeat) {
    }

    @Override
    public void cancelVibrate() {
    }

    @Override
    public float getAzimuth() {
        return 0;
    }

    @Override
    public float getPitch() {
        return 0;
    }

    @Override
    public float getRoll() {
        return 0;
    }

    @Override
    public void getRotationMatrix(float[] matrix) {
    }

    @Override
    public long getCurrentEventTime() {
        return System.nanoTime();
    }

    @Override
    public void setCatchBackKey(boolean catchBack) {
    }

    @Override
    public boolean isCatchBackKey() {
        return false;
    }

    @Override
    public void setCatchMenuKey(boolean catchMenu) {
    }

    @Override
    public boolean isCatchMenuKey() {
        return false;
    }

    /**
     * Fired when the game installs its text-typing InputProcessor
     * (TypeHelper — the save-name / seed fields). VitaLauncher registers a
     * game-aware handler that opens the on-screen keyboard and writes the
     * result into the game's text field; the backend itself must not
     * reference game classes.
     */
    public interface TextFieldListener {
        void opened();
    }

    public static TextFieldListener textFieldListener;

    @Override
    public void setInputProcessor(InputProcessor processor) {
        this.processor = processor;
        if (processor != null && textFieldListener != null
                && processor.getClass().getName().endsWith("TypeHelper")) {
            textFieldListener.opened();
        }
    }

    @Override
    public InputProcessor getInputProcessor() {
        return processor;
    }

    @Override
    public boolean isPeripheralAvailable(Peripheral peripheral) {
        return peripheral == Peripheral.MultitouchScreen;
    }

    @Override
    public int getRotation() {
        return 0;
    }

    @Override
    public Orientation getNativeOrientation() {
        return Orientation.Landscape;
    }

    @Override
    public void setCursorCatched(boolean catched) {
    }

    @Override
    public boolean isCursorCatched() {
        return false;
    }

    @Override
    public void setCursorPosition(int x, int y) {
        // StS controller mode drives the pointer through this (map node
        // selection, menu focus, targeting): the virtual cursor must really
        // move, because InputHelper reads getX()/getY() every frame and the
        // confirm action clicks at the cursor. Leaving it a no-op made the
        // pad feel broken (stale hover, targeting arrow at the last touch).
        this.x = x;
        this.y = y;
    }
}

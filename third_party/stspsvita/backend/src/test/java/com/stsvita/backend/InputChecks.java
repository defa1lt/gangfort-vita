package com.stsvita.backend;

import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
import java.util.ArrayList;
import java.util.List;

public final class InputChecks {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void run() {
        VitaInput input = new VitaInput();
        Controller controller = Controllers.getControllers().peek();
        input.updateController(0x100 | 0x200 | 0x4000, 0.8f, -0.9f, -0.7f, 0.6f);
        require(controller.getButton(19) && controller.getButton(103) && controller.getButton(96), "simultaneous controls lost");
        require(controller.getAxis(0) == 0.8f && controller.getAxis(1) == 0.9f
                && controller.getAxis(11) == -0.7f && controller.getAxis(14) == -0.6f, "axes swapped/inverted");
        input.updateController(0x100 | 0x10, 0, 0, 0, 0);
        input.updateController(0x10, 0, 0, 0, 0);
        require(controller.getButton(19), "releasing L cancels held dpad up");
        input.updateController(0, 0.1f, 0.1f, 0.1f, 0.1f);
        require(!controller.getButton(19) && !controller.getButton(103) && controller.getAxis(0) == 0, "release/deadzone failed");
        List<String> events = new ArrayList<>();
        input.setInputProcessor(new InputAdapter() {
            public boolean touchDown(int x, int y, int pointer, int button) { events.add("down" + pointer); return true; }
            public boolean touchUp(int x, int y, int pointer, int button) { events.add("up" + pointer); return true; }
            public boolean touchDragged(int x, int y, int pointer) { events.add("drag" + pointer); return true; }
        });
        input.updateTouches(2, new int[] {41, 73}, new int[] {20, 900}, new int[] {250, 300});
        input.updateTouches(2, new int[] {73, 41}, new int[] {890, 25}, new int[] {300, 250});
        require(input.getX(0) == 25 && input.getX(1) == 890, "touch report reorder changed pointer IDs");
        input.updateTouches(1, new int[] {73}, new int[] {890}, new int[] {300});
        require(!input.isTouched(0) && input.isTouched(1) && input.isTouched(), "lifting one finger releases both");
        input.updateTouches(0, new int[0], new int[0], new int[0]);
        require(events.equals(List.of("down0", "down1", "drag1", "drag0", "up0", "up1")), "touch event sequence: " + events);
        input.updateTouches(1, new int[] {20}, new int[] {30}, new int[] {40});
        input.updateController(0x100 | 0x200, 1, -1, -1, 1);
        input.releaseAll();
        require(!input.isTouched() && !input.isTouched(0) && !controller.getButton(19)
                && !controller.getButton(103) && controller.getAxis(0) == 0
                && controller.getAxis(14) == 0, "suspend leaves held input");
        System.out.println("PASS input: Android mapping, simultaneous controls, release, deadzone, stable multitouch");
    }
}

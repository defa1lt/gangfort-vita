/* SceCtrl / SceTouch polling for VitaInput (via VitaNatives @Imports). */

#include <psp2/ctrl.h>
#include <psp2/touch.h>
#include <psp2/appmgr.h>
#include <psp2/power.h>
#include <psp2/kernel/threadmgr.h>

int stsv_lifecycle_poll(void) {
    SceAppMgrSystemEvent event;
    int resumed = 0;
    for (int pending = 0; pending < 8; pending++) {
        if (sceAppMgrReceiveSystemEvent(&event) != 0) break;
        if (event.systemEvent == SCE_APPMGR_SYSTEMEVENT_ON_RESUME) resumed = 1;
    }
    if (resumed) return 2;
    if (scePowerIsSuspendRequired()) {
        sceKernelDelayThread(20000);
        return 1;
    }
    return 0;
}

static SceTouchData touch;
static SceCtrlData pad;

/* Must run once at startup: sceTouchPeek returns 0 touches until the front
 * panel is sampling, and the analog sticks read 128 (centre) until ANALOG mode
 * is selected. Without this, VitaInput sees no touch and no stick input. */
void stsv_input_init(void) {
    sceCtrlSetSamplingMode(SCE_CTRL_MODE_ANALOG_WIDE);
    sceTouchSetSamplingState(SCE_TOUCH_PORT_FRONT, SCE_TOUCH_SAMPLING_STATE_START);
}

int stsv_ctrl_buttons(void) {
    if (sceCtrlPeekBufferPositive(0, &pad, 1) <= 0) {
        pad.buttons = 0;
        pad.lx = pad.ly = pad.rx = pad.ry = 128;
    }
    return (int) pad.buttons;
}

float stsv_ctrl_axis(int axis) {
    unsigned char raw;
    switch (axis) {
        case 0: raw = pad.lx; break;
        case 1: raw = pad.ly; break;
        case 2: raw = pad.rx; break;
        default: raw = pad.ry; break;
    }
    return (raw - 128) / 128.0f;
}

int stsv_touch_count(void) {
    if (sceTouchPeek(SCE_TOUCH_PORT_FRONT, &touch, 1) <= 0) touch.reportNum = 0;
    return touch.reportNum;
}

int stsv_touch_id(int finger) {
    return finger >= 0 && finger < touch.reportNum ? touch.report[finger].id : -1;
}

/* Touch panel coords are 1920x1088 regardless of the 960x544 screen. */
int stsv_touch_x(int finger) {
    if (finger >= touch.reportNum) return 0;
    return touch.report[finger].x / 2;
}

int stsv_touch_y(int finger) {
    if (finger >= touch.reportNum) return 0;
    return touch.report[finger].y / 2;
}

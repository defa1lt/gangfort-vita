/* Functions @Import-ed by the Java backend (see VitaNatives.java).
 * Keep names in sync — TeaVM resolves them at native link time. */

#include "stsvita_glue.h"

#include <vitaGL.h>
#include <psp2/ctrl.h>
#include <psp2/display.h>
#include <png.h>
#include <stdio.h>
#include <stdlib.h>

#define STBI_NO_HDR
#define STBI_NO_LINEAR
#define STBI_NO_PIC
#define STBI_NO_PNM
#define STBI_NO_PSD
#define STBI_NO_TGA
#define STBI_NO_GIF
#include "../../third_party/stb_image.h"

static int vsync_on = 1;

/* CMake links with --wrap=invoke_splashscreen so vitaGL never starts its
 * library-owned spinning-logo thread. Our branded frame takes over as soon as
 * vglInitExtended returns, without carrying a private vitaGL fork. */
void __wrap_invoke_splashscreen(void) {
}

void stsv_gfx_init(void) {
    /* vitaGL is initialized by main.c before the Java world boots. */
}

void stsv_show_loading_screen(void) {
    int width = 0, height = 0, channels = 0;
    unsigned char *pixels = stbi_load(
        "app0:/loading.png",
        &width, &height, &channels, 4);

    /* The first draw stops vitaGL's library-owned spinning splash thread.
     * This framebuffer then remains visible while the Java world preloads. */
    glViewport(0, 0, 960, 544);
    glDisable(GL_BLEND);
    glDisable(GL_CULL_FACE);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_SCISSOR_TEST);
    glClearColor(0.025f, 0.035f, 0.045f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    GLuint texture = 0;
    if (pixels && width > 0 && height > 0) {
        glGenTextures(1, &texture);
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0,
                     GL_RGBA, GL_UNSIGNED_BYTE, pixels);

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glEnable(GL_TEXTURE_2D);
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        glBegin(GL_TRIANGLE_STRIP);
        glTexCoord2f(0.0f, 1.0f); glVertex2f(-1.0f, -1.0f);
        glTexCoord2f(1.0f, 1.0f); glVertex2f( 1.0f, -1.0f);
        glTexCoord2f(0.0f, 0.0f); glVertex2f(-1.0f,  1.0f);
        glTexCoord2f(1.0f, 0.0f); glVertex2f( 1.0f,  1.0f);
        glEnd();
        fprintf(stderr, "[boot] custom loading screen ready %dx%d\n",
                width, height);
    } else {
        const char *reason = stbi_failure_reason();
        fprintf(stderr, "[boot] loading screen unavailable: %s\n",
                reason ? reason : "unknown decode error");
    }

    vglSwapBuffers(GL_FALSE);

    /* The displayed pixels live in the swapchain, so the temporary RGBA
     * texture and decode buffer can be released before the large game preload. */
    glBindTexture(GL_TEXTURE_2D, 0);
    glDisable(GL_TEXTURE_2D);
    if (texture) glDeleteTextures(1, &texture);
    if (pixels) stbi_image_free(pixels);
}

static void capture_screen(void) {
    sceGxmDisplayQueueFinish();
    SceDisplayFrameBuf frame = { .size = sizeof(frame) };
    int result = sceDisplayGetFrameBuf(&frame, SCE_DISPLAY_SETBUF_IMMEDIATE);
    if (result < 0 || !frame.base || frame.width == 0 || frame.height == 0 ||
            frame.width > 960 || frame.height > 544 || frame.pitch < frame.width ||
            frame.pixelformat != SCE_DISPLAY_PIXELFORMAT_A8B8G8R8) {
        fprintf(stderr, "[capture] framebuffer unavailable: 0x%x\n", result);
        return;
    }
    size_t stride = frame.width * 3;
    unsigned char *rgb = malloc(stride * frame.height);
    if (!rgb) {
        fprintf(stderr, "[capture] RGB allocation failed\n");
        return;
    }
    for (unsigned y = 0; y < frame.height; y++) {
        const unsigned char *source = (const unsigned char *)frame.base + y * frame.pitch * 4;
        unsigned char *row = rgb + y * stride;
        for (unsigned x = 0; x < frame.width; x++) {
            row[x * 3] = source[x * 4];
            row[x * 3 + 1] = source[x * 4 + 1];
            row[x * 3 + 2] = source[x * 4 + 2];
        }
    }
    png_image image = { .version = PNG_IMAGE_VERSION };
    image.width = frame.width;
    image.height = frame.height;
    image.format = PNG_FORMAT_RGB;
    const char *path = "ux0:data/gangfort/screenshot.png";
    if (png_image_write_to_file(&image, path, 0, rgb, stride, NULL)) {
        fprintf(stderr, "[capture] saved %s %ux%u\n", path, frame.width, frame.height);
    } else {
        fprintf(stderr, "[capture] PNG write failed: %s\n", image.message);
    }
    png_image_free(&image);
    free(rgb);
}

void stsv_swap_buffers(void) {
    /* Keep stale vitaGL errors from leaking into game code. Per-error logging
     * was useful during texture diagnosis but caused synchronous ux0 writes. */
    GLenum err;
    do {
        err = glGetError();
    } while (err != GL_NO_ERROR);
    /* While the sceImeDialog keyboard is up, the common dialog layer must be
     * composited over the frame (ime_glue.c). */
    vglSwapBuffers(stsv_ime_active() ? GL_TRUE : GL_FALSE);
    static int select_held;
    int select_down = (stsv_ctrl_buttons() & SCE_CTRL_SELECT) != 0;
    if (select_down && !select_held) capture_screen();
    select_held = select_down;
}

void stsv_set_vsync(int on) {
    vsync_on = on;
    /* vitaGL swap interval control lands with the M3 graphics work. */
}

int32_t stsv_is_host_harness(void) {
    return 0;
}

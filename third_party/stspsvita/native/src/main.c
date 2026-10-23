/* Process startup: memory + vitaGL init before the Java world boots. */

#include "gangfort_build_id.h"
#include <vitaGL.h>
#include <psp2/kernel/processmgr.h>
#include <psp2/power.h>
#include <sys/stat.h>
#include <unistd.h>
#include <stdio.h>

/* Includes the 64 MiB TeaVM heap plus native decode, audio and draw buffers. */
int _newlib_heap_size_user = 240 * 1024 * 1024;

/* TeaVM's generated main, renamed via -Dmain=stsv_java_main (CMake). */
int stsv_java_main(int argc, char **argv);

/* Enables SceCtrl analog + SceTouch front-panel sampling (input_glue.c). */
void stsv_input_init(void);

/* Replaces vitaGL's spinner with the generated title loading frame. */
void stsv_show_loading_screen(void);

/* Opens the OpenAL device/context for VitaAudio (audio_glue.c). */
void stsv_audio_init(void);

/* Ordinary game INFO output is high-volume and used to force one ux0 memory-
 * card write per println, directly inside listener.render(). Keep a static
 * buffer outside newlib so logging neither fragments the draw-buffer heap nor
 * allocates after startup. Critical Java-side and native-bridge diagnostics
 * stay on unbuffered stderr and flush this context before reporting
 * (VitaNatives.log). */
static char stsv_stdout_buffer[16 * 1024];

int main(int argc, char **argv) {
    /* The port is developed/tested at max clocks (PSVshell equivalent). */
    scePowerSetArmClockFrequency(444);
    scePowerSetBusClockFrequency(222);
    scePowerSetGpuClockFrequency(222);
    scePowerSetGpuXbarClockFrequency(166);

    /* Game data (saves and preferences) lives on ux0:.
     * The game reads/writes some of it via cwd-relative paths, so move
     * cwd there; assets are always absolute (app0:/assets/). */
    mkdir("ux0:data", 0777);
    mkdir("ux0:data/gangfort", 0777);
    chdir("ux0:data/gangfort");

    /* Capture the game's stdout + the C runtime's stderr (TeaVM prints an
     * uncaught-exception trace there right before abort()) to a file on ux0:,
     * since the Vita has no console. Both streams open in append mode (Vita
     * newlib has no dup2) so their O_APPEND writes interleave at EOF without
     * clobbering. Retain previous launches; batch ordinary stdout INFO writes;
     * stderr remains unbuffered so abort/crash diagnostics survive. */
    freopen("ux0:data/gangfort/gangfort.log", "a", stdout);
    freopen("ux0:data/gangfort/gangfort.log", "a", stderr);
    setvbuf(stdout, stsv_stdout_buffer, _IOFBF, sizeof(stsv_stdout_buffer));
    setvbuf(stderr, NULL, _IONBF, 0);
    fprintf(stderr, "\n[boot] build=%s java-heap=64MiB newlib-heap=240MiB clocks=444/222/222/166\n",
            GANGFORT_BUILD_ID);

    /* Give vitaGL large DEDICATED pools (its own memblocks) so texture
     * overflow lands there instead of spilling into the malloc heap
     * (VGL_MEM_EXTERNAL) and colliding with stb decode buffers. Capacity:
     * CDRAM 100MB (separate from the 256MB RAM budget) + PHYCONT 20MB + a
     * 64MB RAM pool + 8MB legacy = ~192MB, which with DXT5-compressed atlases
     * holds StS's full texture set. */
    /* vglInitExtended auto-grabs all available CDRAM + PHYCONT (no over-request
     * failure) and carves a RAM pool from the USER_RW left after the newlib
     * heap, leaving `ram_threshold` bytes free for GXM/stacks. Textures overflow
     * into that RAM pool instead of the malloc heap. */
    GLboolean ok = vglInitExtended(8 * 1024 * 1024, 960, 544,
                                   16 * 1024 * 1024, SCE_GXM_MULTISAMPLE_NONE);
    fprintf(stderr,
        "[boot] vglInitExtended -> %d | pools: VRAM=%dMB RAM=%dMB PHYCONT=%dMB\n",
        (int) ok,
        (int) (vglMemTotal(VGL_MEM_VRAM) >> 20),
        (int) (vglMemTotal(VGL_MEM_RAM) >> 20),
        (int) (vglMemTotal(VGL_MEM_PHYCONT) >> 20));

    stsv_show_loading_screen();

    stsv_input_init();
    stsv_audio_init();

    fprintf(stderr, "[boot] Java main begin\n");
    int rc = stsv_java_main(argc, argv);

    /* sceKernelExitProcess does not promise stdio teardown. */
    fflush(NULL);

    /* This vitaGL build exposes no vglEnd(); process teardown on
     * sceKernelExitProcess reclaims the GL context and memory pools. */
    sceKernelExitProcess(rc);
    return rc;
}

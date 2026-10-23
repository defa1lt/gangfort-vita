package com.stsvita.backend;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Audio;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.LifecycleListener;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Clipboard;
import com.badlogic.gdx.graphics.g2d.Gdx2DPixmap;

/**
 * libGDX 1.9.5 Application for the PS Vita.
 *
 * Single-threaded by construction: TeaVM's C runtime provides green-thread
 * fibers, so the render loop, postRunnable queue, and the game's async
 * helpers (AsyncSaver etc.) all cooperate on one OS thread.
 */
public class VitaApplication implements Application {

    public static class Config {
        public int width = 960;
        public int height = 544;
        public boolean vsync = true;
    }

    private final ApplicationListener listener;
    private final VitaGraphics graphics;
    private final VitaInput input;
    private final VitaAudio audio;
    private final VitaFiles files;
    private final VitaNet net;
    private final Array<Runnable> runnables = new Array<>();
    private final Array<Runnable> executedRunnables = new Array<>();
    private final Array<LifecycleListener> lifecycleListeners = new Array<>();
    private boolean paused;
    private int logLevel = LOG_INFO;
    private volatile boolean running = true;
    private final java.util.Map<String, Preferences> preferences = new java.util.HashMap<>();
    private final long[] frameHistogram = new long[251];
    private int measuredFrames;
    private long frameTotal, frameMaximum, preTotal, gameTotal, swapTotal, ioTotal;

    public VitaApplication(ApplicationListener listener, Config config) {
        VitaNatives.diag("application constructor begin");
        this.listener = listener;
        VitaNatives.diag("backend graphics construct begin");
        this.graphics = new VitaGraphics(config);
        VitaNatives.diag("backend graphics construct end");
        VitaNatives.diag("backend input construct begin");
        this.input = new VitaInput();
        VitaNatives.diag("backend input construct end");
        VitaNatives.diag("backend audio construct begin");
        this.audio = new VitaAudio();
        VitaNatives.diag("backend audio construct end");
        VitaNatives.diag("backend files construct begin");
        this.files = new VitaFiles();
        VitaNatives.diag("backend files construct end");
        VitaNatives.diag("backend net construct begin");
        this.net = new VitaNet();
        VitaNatives.diag("backend net construct end");

        Gdx.app = this;
        Gdx.graphics = graphics;
        Gdx.input = input;
        Gdx.audio = audio;
        Gdx.files = files;
        Gdx.net = net;
        Gdx.gl = Gdx.gl20 = graphics.getGL20();

        mainLoop();
    }

    private void mainLoop() {
        VitaNatives.diag("graphics.init begin");
        graphics.init();
        VitaNatives.diag("graphics.init end");
        VitaNatives.diag("listener.create begin");
        listener.create();
        VitaNatives.diag("listener.create end");
        Gdx2DPixmap.dumpStats("after-create");
        // The GL viewport is owned by the backend, exactly as LWJGL does in
        // its mainLoop (glViewport then resize): StS's create() calls
        // FitViewport.apply() before the viewport is ever update()d, leaving a
        // 0x0 viewport that clips every fragment, and its resize() is empty, so
        // nothing else ever fixes it. Set it to the full framebuffer here.
        Gdx.gl.glViewport(0, 0, graphics.getWidth(), graphics.getHeight());
        VitaNatives.diag("listener.resize begin");
        listener.resize(graphics.getWidth(), graphics.getHeight());
        VitaNatives.diag("listener.resize end");
        // Boot preload is done and the native working set is stable: from here
        // on, textures that no vitaGL pool can hold may spill (bounded) into
        // newlib instead of being dropped blank — that covers the mid-run
        // loads the player actually sees (boss atlases, scene backgrounds).
        VitaNatives.texSpillEnable();
        VitaNatives.diag("game loop begin");

        boolean firstFrame = true;
        graphics.resetClock();
        while (running) {
            // Let TeaVM run timer fibers without scheduling a zero-delay sleep
            // callback on every frame.
            Thread.yield();
            int lifecycle = VitaNatives.lifecyclePoll();
            if ((lifecycle == 1 || lifecycle == 2) && !paused) {
                input.releaseAll();
                for (LifecycleListener callback : new Array<>(lifecycleListeners)) callback.pause();
                listener.pause();
                paused = true;
                VitaNatives.diag("lifecycle pause");
            }
            if (lifecycle == 1) continue;
            if (paused) {
                for (LifecycleListener callback : new Array<>(lifecycleListeners)) callback.resume();
                listener.resume();
                graphics.resetClock();
                paused = false;
                VitaNatives.diag("lifecycle resume");
            }
            if (firstFrame) VitaNatives.diag("first frame begin");
            long hitchStart = VitaNatives.micros();
            long wallStart = VitaNatives.wallMicros();
            graphics.beginFrame();
            if (firstFrame) VitaNatives.diag("first frame input begin");
            input.update();
            if (firstFrame) VitaNatives.diag("first frame input end");
            if (firstFrame) VitaNatives.diag("first frame audio begin");
            VitaAudio.update(); // refill streaming-music buffers
            if (firstFrame) VitaNatives.diag("first frame audio end");
            executeRunnables();
            long gameStart = VitaNatives.micros();
            if (firstFrame) VitaNatives.diag("first frame render begin");
            listener.render();
            if (firstFrame) VitaNatives.diag("first frame render end");
            long swapStart = VitaNatives.micros();
            graphics.endFrame(); // swap + vsync + frame timing
            if (firstFrame) {
                VitaNatives.diag("first frame swap end");
                firstFrame = false;
            }
            long frameEnd = VitaNatives.micros();
            long frameMs = (frameEnd - hitchStart) / 1000L;
            long preMs = (gameStart - hitchStart) / 1000L;
            long gameMs = (swapStart - gameStart) / 1000L;
            long swapMs = (frameEnd - swapStart) / 1000L;
            long ft = VitaNatives.ftFrameStats();       // reset every frame so
            long subtex = VitaNatives.subtexFrameStats(); // hitches attribute
            long stb = VitaNatives.imgFrameStats();
            long dxt = VitaNatives.dxtFrameStats();
            long up = VitaNatives.uploadFrameStats();
            long io = VitaFileHandle.drainIoStats();   // ALL reads, incl. the
            long stat = VitaFileHandle.drainStatStats(); // sub-100ms tail
            frameHistogram[(int) Math.min(frameMs, frameHistogram.length - 1)]++;
            measuredFrames++;
            frameTotal += frameEnd - hitchStart;
            frameMaximum = Math.max(frameMaximum, frameEnd - hitchStart);
            preTotal += gameStart - hitchStart;
            gameTotal += swapStart - gameStart;
            swapTotal += frameEnd - swapStart;
            ioTotal += io / 1000;
            if (measuredFrames == 300) reportFrames();
            if (frameMs > 50L) {
                // wall >> process means the console was suspended/minimized
                // mid-frame, not a real stall (process time pauses, RTC not).
                long wallMs = (VitaNatives.wallMicros() - wallStart) / 1000L;
                VitaNatives.log("[hitch] frame took " + frameMs + "ms (wall "
                        + wallMs + "ms, pre " + preMs + "ms, game " + gameMs
                        + "ms, swap " + swapMs
                        + "ms, ft " + (ft / 1000) + "ms/" + (ft % 1000)
                        + ", subtex " + (subtex / 1000) + "ms/" + (subtex % 1000)
                        + ", stb " + (stb / 1000) + "ms/" + (stb % 1000)
                        + ", dxt " + (dxt / 1000) + "ms/" + (dxt % 1000)
                        + ", up " + (up / 1000) + "ms/" + (up % 1000)
                        + ", io " + (io / 1000) + "ms/" + (io % 1000)
                        + ", stat " + (stat / 1000) + "ms/" + (stat % 1000)
                        + ")");
            }
        }

        for (LifecycleListener callback : new Array<>(lifecycleListeners)) callback.pause();
        listener.pause();
        for (LifecycleListener callback : new Array<>(lifecycleListeners)) callback.dispose();
        listener.dispose();
        VitaNatives.exitProcess(0);
    }

    private String percentile(int percent) {
        long count = 0;
        for (int i = 0; i < frameHistogram.length; i++) {
            count += frameHistogram[i];
            if (count * 100 >= measuredFrames * percent) return i == frameHistogram.length - 1 ? ">=" + i : "" + i;
        }
        return ">=" + (frameHistogram.length - 1);
    }

    private void reportFrames() {
        VitaNatives.diag("frames n=" + measuredFrames + " meanUs=" + frameTotal / measuredFrames
                + " p50ms=" + percentile(50) + " p95ms=" + percentile(95)
                + " p99ms=" + percentile(99) + " maxUs=" + frameMaximum
                + " preUs=" + preTotal / measuredFrames + " gameUs=" + gameTotal / measuredFrames
                + " swapUs=" + swapTotal / measuredFrames + " ioMs=" + ioTotal
                + " physicsUs=" + com.badlogic.gdx.physics.box2d.World.drainStepMicros() / measuredFrames
                + " javaUsed=" + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
        java.util.Arrays.fill(frameHistogram, 0);
        measuredFrames = 0;
        frameTotal = frameMaximum = preTotal = gameTotal = swapTotal = ioTotal = 0;
    }

    private void executeRunnables() {
        synchronized (runnables) {
            executedRunnables.clear();
            executedRunnables.addAll(runnables);
            runnables.clear();
        }
        for (Runnable r : executedRunnables) {
            r.run();
        }
    }

    @Override
    public ApplicationListener getApplicationListener() {
        return listener;
    }

    @Override
    public Graphics getGraphics() {
        return graphics;
    }

    @Override
    public Audio getAudio() {
        return audio;
    }

    @Override
    public Input getInput() {
        return input;
    }

    @Override
    public Files getFiles() {
        return files;
    }

    @Override
    public Net getNet() {
        return net;
    }

    @Override
    public void log(String tag, String message) {
        if (logLevel >= LOG_INFO) VitaNatives.log("[I] " + tag + ": " + message);
    }

    @Override
    public void log(String tag, String message, Throwable exception) {
        log(tag, message + " :: " + exception);
    }

    @Override
    public void error(String tag, String message) {
        if (logLevel >= LOG_ERROR) VitaNatives.log("[E] " + tag + ": " + message);
    }

    @Override
    public void error(String tag, String message, Throwable exception) {
        error(tag, message + " :: " + exception);
    }

    @Override
    public void debug(String tag, String message) {
        if (logLevel >= LOG_DEBUG) VitaNatives.log("[D] " + tag + ": " + message);
    }

    @Override
    public void debug(String tag, String message, Throwable exception) {
        debug(tag, message + " :: " + exception);
    }

    @Override
    public void setLogLevel(int logLevel) {
        this.logLevel = logLevel;
    }

    @Override
    public int getLogLevel() {
        return logLevel;
    }

    @Override
    public ApplicationType getType() {
        // Reported as Desktop: the game's platform branches (file layout,
        // controller glyphs) behave best on the desktop path.
        return ApplicationType.Desktop;
    }

    @Override
    public int getVersion() {
        return 0;
    }

    @Override
    public long getJavaHeap() {
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }

    @Override
    public long getNativeHeap() {
        return getJavaHeap();
    }

    @Override
    public Preferences getPreferences(String name) {
        Preferences result = preferences.get(name);
        if (result == null) {
            result = new VitaPreferences(name);
            preferences.put(name, result);
        }
        return result;
    }

    @Override
    public Clipboard getClipboard() {
        return new Clipboard() {
            private String content = "";

            @Override
            public String getContents() {
                return content;
            }

            @Override
            public void setContents(String content) {
                this.content = content == null ? "" : content;
            }
        };
    }

    @Override
    public void postRunnable(Runnable runnable) {
        synchronized (runnables) {
            runnables.add(runnable);
        }
    }

    @Override
    public void exit() {
        running = false;
    }

    @Override
    public void addLifecycleListener(LifecycleListener listener) {
        synchronized (lifecycleListeners) {
            lifecycleListeners.add(listener);
        }
    }

    @Override
    public void removeLifecycleListener(LifecycleListener listener) {
        synchronized (lifecycleListeners) {
            lifecycleListeners.removeValue(listener, true);
        }
    }
}

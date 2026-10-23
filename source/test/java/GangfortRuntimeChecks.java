public final class GangfortRuntimeChecks {
    public static void main(String[] args) throws InterruptedException {
        Thread.yield();
        Thread.sleep(5);
        timingCaller();
        entry();
        screenOwnership();
        matchExitDuringFrame();
        require(new Thread().getStackTrace().length == 0, "foreign unstarted thread has a fabricated trace");
        System.out.println("PASS TeaVM C: real game profiler pair and current-thread caller frames");
    }

    private static void screenOwnership() {
        com.badlogic.gdx.Gdx.graphics = new com.stsvita.backend.VitaGraphics(
                new com.stsvita.backend.VitaApplication.Config());
        wl game = new wl(new GangfortPlatform());
        OwnedScreen menu = new OwnedScreen();
        OwnedScreen match = new OwnedScreen();
        OwnedScreen overlay = new OwnedScreen();
        game.setScreen(menu);
        game.setScreen(match);
        require(menu.disposals == 1 && match.disposals == 0, "replacing menu disposes it more than once");
        game.a(overlay);
        game.s();
        require(overlay.disposals == 1 && match.disposals == 0 && game.getScreen() == match,
                "popping an overlay disposes the retained match");
        OwnedScreen nextMenu = new OwnedScreen();
        game.setScreen(nextMenu);
        require(match.disposals == 1 && overlay.disposals == 1 && nextMenu.disposals == 0,
                "leaving a match disposes a screen more than once");
        game.setScreen(new OwnedScreen());
        require(nextMenu.disposals == 1, "next transition repeats disposal");
        System.out.println("PASS TeaVM C: original screen history disposes each owned screen once");
    }

    private static class OwnedScreen extends com.badlogic.gdx.ScreenAdapter {
        int disposals;
        public void dispose() { disposals++; }
    }

    private static void matchExitDuringFrame() {
        wl game = new wl(new GangfortPlatform());
        OwnedScreen menu = new OwnedScreen();
        OwnedScreen match = new OwnedScreen() {
            public void render(float delta) {
                game.setScreen(menu);
                require(disposals == 0 && game.getScreen() == this,
                        "controller callback disposed the match before its frame finished");
            }
        };
        game.setScreen(match);
        game.render();
        require(match.disposals == 1 && game.getScreen() == menu,
                "queued match exit did not return to the menu at frame end");
        OwnedScreen failing = new OwnedScreen() {
            public void render(float delta) { throw new IllegalStateException("frame failed"); }
        };
        game.setScreen(failing);
        try {
            game.render();
            throw new AssertionError("frame exception was swallowed");
        } catch (IllegalStateException expected) {
            game.setScreen(menu);
            require(failing.disposals == 1 && game.getScreen() == menu,
                    "failed frame left subsequent transitions queued");
        }
        System.out.println("PASS TeaVM C: match exit waits for frame end; immediate transitions and exception cleanup survive");
    }

    private static void timingCaller() {
        for (int i = 0; i < 32; i++) {
            wo.e();
            wo.f();
        }
    }

    private static void entry() {
        StackTraceElement[] trace = captureCaller();
        require(trace.length >= 3, "current-thread trace is empty or incomplete");
        require(trace[0].getClassName().equals("java.lang.Thread")
                && trace[0].getMethodName().equals("getStackTrace"), "capture frame is incorrect");
        require(trace[1].getClassName().equals("GangfortRuntimeChecks")
                && trace[1].getMethodName().equals("captureCaller"), "direct caller frame is incorrect");
        require(trace[2].getClassName().equals("GangfortRuntimeChecks")
                && trace[2].getMethodName().equals("entry"), "game profiler caller index is incorrect");
    }

    private static StackTraceElement[] captureCaller() {
        return Thread.currentThread().getStackTrace();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

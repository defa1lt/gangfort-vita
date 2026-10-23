import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.utils.Array;

public final class GangfortScreenTransitions {
    private static Game rendering;
    private static final Array<Screen> pending = new Array<>();

    public static boolean defer(Game game, Screen screen) {
        if (rendering != game) return false;
        pending.add(screen);
        return true;
    }

    public static void render(Game game) {
        rendering = game;
        boolean completed = false;
        try {
            Screen screen = game.getScreen();
            if (screen != null) screen.render(Gdx.graphics.getDeltaTime());
            completed = true;
        } finally {
            rendering = null;
            if (!completed) pending.clear();
        }
        // Controller callbacks run inside Stage.act; the old frame still needs its world afterward.
        try {
            for (int i = 0; i < pending.size; i++) game.setScreen(pending.get(i));
        } finally {
            pending.clear();
        }
    }
}

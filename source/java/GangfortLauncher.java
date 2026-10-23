import com.stsvita.backend.VitaApplication;
import com.stsvita.backend.VitaNatives;

public final class GangfortLauncher {
    public static void main(String[] args) {
        VitaNatives.diag("launcher.main begin");
        GangfortTypes.register();
        VitaApplication.Config config = new VitaApplication.Config();
        VitaNatives.diag("launcher.game construct begin");
        wl game = new wl(new GangfortPlatform());
        VitaNatives.diag("launcher.game construct end");
        VitaNatives.diag("application construct begin");
        try {
            new VitaApplication(game, config);
        } catch (Throwable error) {
            VitaNatives.diag("fatal: " + error);
            error.printStackTrace();
            throw error;
        }
    }
}

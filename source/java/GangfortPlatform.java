import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.pay.Transaction;

/** Vita implementation of the APK's PlatformSpecificWrapper, offline only. */
public final class GangfortPlatform implements akr {
    private final aju ads = new aju() {
        public void a(aju.a listener) { com.badlogic.gdx.Gdx.app.postRunnable(listener::a); }
        public void a(aju.b listener) { com.badlogic.gdx.Gdx.app.postRunnable(() -> listener.a(false, 0)); }
        public boolean b() { return false; }
        public void c() {}
        public void d() {}
        public boolean e() { return false; }
        public void f() {}
        public void g() {}
        public boolean h() { return false; }
        public boolean i() { return false; }
    };
    private final ajv analytics = new ajv() {
        public void a() {}
        public void a(ajv.a event, String data) {}
        public void a(Transaction transaction) {}
    };
    private final ajy social = new ajy() {
        public void a(ajy.b listener) {
            com.badlogic.gdx.Gdx.app.postRunnable(() -> listener.a((Object)
                    new UnsupportedOperationException("Facebook login is unavailable on PS Vita")));
        }
        public void a(String title, String message, ajy.a listener) {
            com.badlogic.gdx.Gdx.app.postRunnable(() -> listener.a(false, null, null));
        }
    };
    private final aky purchases = new aky() {
        public String a(String product) { return "Unavailable"; }
        public void a() {}
        public void a(Array products) {}
        public void b(String product) {}
    };
    private final akf network = new akf() {
        public void a(int score) {}
        public void a(boolean value, int type) {}
        public boolean b() { return false; }
        public boolean c() { return false; }
        public void d() {}
        public void e() {}
    };
    public void a(int id, String title, String message, int delay) {}
    public void a(String message) { System.out.println(message); }
    public void a(Throwable error) { error.printStackTrace(); }
    public void a(wl game) {
        com.badlogic.gdx.Gdx.app.postRunnable(game::e);
    }
    public void a(boolean value) {}
    public boolean a() { return false; }
    public void b() {}
    public boolean b(String remoteKey) { return false; }
    public int c(String remoteKey) { return 0; }
    public String c() { return null; }
    public ajy d() { return social; }
    public aju e() { return ads; }
    public ajv f() { return analytics; }
    public int h() { return 21; }
    public int i() { return 1; }
    public boolean j() { return false; }
    public boolean k() { return true; }
    public boolean l() { return false; }
    public float n() { return 0; }
    public boolean o() { return false; }
    public boolean p() { return true; }
    public String q() { return "PS Vita"; }
    public aky s() { return purchases; }
    public akf t() { return network; }
}

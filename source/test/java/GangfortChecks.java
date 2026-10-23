import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.actions.SequenceAction;
import com.badlogic.gdx.utils.Pools;
import com.gangfort.game.models.ControlsPositionsDataHolder;
import com.gangfort.game.models.GamepadData;
import com.gangfort.game.network.PlayerItemSelectionSettings;
import com.google.gson.Gson;
import com.stsvita.backend.VitaPreferences;
import com.stsvita.backend.VitaXmlDocumentBuilderFactory;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.util.ArrayList;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public final class GangfortChecks {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        File directory = new File(args[0]);
        if (args.length == 2) {
            VitaPreferences preferences = new VitaPreferences("prefs", directory);
            require(preferences.getInteger("A19oYqXwbS8=") == 6, "encoding seed did not survive restart");
            require(preferences.getString("name").equals("Игрок 日本語"), "Unicode did not survive restart");
            require(preferences.getLong("score") == 1234567890123L, "long type lost");
            require(preferences.getBoolean("sound"), "boolean type lost");
            require(preferences.getFloat("volume") == 0.75f, "float type lost");
            System.out.println("PASS preferences: separate-process restart");
            return;
        }
        preferences(directory);
        serialization();
        xmlMaps();
        instructionAtlas();
        pools();
        cancelTouch();
        physics();
        com.stsvita.backend.InputChecks.run();
        menuDirections();
        System.out.println("PASS Gangfort backend checks");
    }

    private static void instructionAtlas() {
        com.badlogic.gdx.files.FileHandle folder = new com.badlogic.gdx.files.FileHandle(
                "build/gangfort-assets/res/textures");
        com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData data =
                new com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData(
                        folder.child("ui.atlas"), folder, false);
        int instructions = 0;
        for (com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region region : data.getRegions()) {
            if (!region.name.equals("gamepad_info")) continue;
            instructions++;
            require(region.page.textureFile.name().equals("vita_controls.png")
                            && region.width == 188 && region.height == 104,
                    "instruction region does not reference the Vita graphic at its original size");
            require(region.page.textureFile.exists(), "Vita instruction texture is missing");
        }
        require(instructions == 1, "instruction region is missing or duplicated");
        System.out.println("PASS assets: real atlas parser resolves one Vita instruction region at 188x104");
    }

    private static void menuDirections() throws Exception {
        new wl(new GangfortPlatform());
        com.badlogic.gdx.controllers.Controllers.getControllers().clear();
        com.stsvita.backend.VitaInput input = new com.stsvita.backend.VitaInput();
        aai helper = new aai();
        java.lang.reflect.Method sample = input.getClass().getDeclaredMethod("updateController",
                int.class, float.class, float.class, float.class, float.class);
        sample.setAccessible(true);
        java.lang.reflect.Field frame = wl.class.getDeclaredField("g");
        frame.setAccessible(true);
        long tick = 10;
        for (int mask : new int[] {0x40, 0x10}) {
            aaj direction = mask == 0x40 ? aaj.d : aaj.c;
            for (int press = 0; press < 2; press++) {
                for (int held = 0; held < 10; held++) {
                    sample.invoke(input, mask, 0f, 0f, 0f, 0f);
                    frame.setLong(null, ++tick);
                    helper.e();
                    boolean menuMove = helper.a(direction) || (mask == 0x40 ? helper.d() : helper.c());
                    require(menuMove == (held == 0), "D-pad moves menu on held frame " + held);
                    require(helper.c(direction), "D-pad gameplay hold was lost");
                }
                sample.invoke(input, 0, 0f, 0f, 0f, 0f);
                frame.setLong(null, ++tick);
                helper.e();
                require(!helper.a(direction) && !helper.c(direction), "D-pad release was lost");
            }
        }
        System.out.println("PASS input: original menu predicates advance once per D-pad press and preserve gameplay hold");
    }

    private static void preferences(File directory) throws Exception {
        VitaPreferences preferences = new VitaPreferences("prefs", directory);
        preferences.putInteger("A19oYqXwbS8=", 6).putString("name", "Игрок 日本語")
                .putLong("score", 1234567890123L).putBoolean("sound", true).putFloat("volume", 0.75f);
        preferences.flush();
        String javaExecutable = new File(System.getProperty("java.home"), "bin/java").getPath();
        int status = new ProcessBuilder(javaExecutable, "-cp", System.getProperty("java.class.path"),
                "GangfortChecks", directory.getAbsolutePath(), "read").inheritIO().start().waitFor();
        require(status == 0, "restart check failed");
        require(!new VitaPreferences("other", directory).contains("name"), "namespaces mixed");
        preferences.remove("name");
        preferences.flush();
        require(!new VitaPreferences("prefs", directory).contains("name"), "removed key returned");
        preferences.clear();
        preferences.flush();
        require(new VitaPreferences("prefs", directory).get().isEmpty(), "clear not persisted");
        File recoveryDir = new File(directory, "recovery");
        VitaPreferences recovery = new VitaPreferences("data", recoveryDir);
        recovery.putInteger("value", 1).flush();
        recovery.putInteger("value", 2).flush();
        File newest = java.util.Arrays.stream(recoveryDir.listFiles()).filter(f -> f.getName().endsWith(".1")).findFirst().get();
        Files.write(newest.toPath(), new byte[] {1, 2, 3});
        require(new VitaPreferences("data", recoveryDir).getInteger("value") == 1, "valid prior snapshot not recovered");
        File failed = new File(directory, "not-a-directory");
        Files.write(failed.toPath(), new byte[] {0});
        boolean threw = false;
        try { new VitaPreferences("data", failed).putString("key", "value").flush(); }
        catch (com.badlogic.gdx.utils.GdxRuntimeException expected) { threw = true; }
        require(threw, "write failure hidden");
        System.out.println("PASS preferences: types, namespaces, remove, clear, recovery, write failure");
    }

    private static void serialization() {
        GangfortTypes.register();
        Gson gson = new Gson();
        ArrayList<Integer> ids = gson.fromJson("[2,4,9]", new aap(null).getType());
        require(ids.get(1).equals(4), "item IDs lost integer type");
        ArrayList<PlayerItemSelectionSettings> equipment = gson.fromJson(
                "[{\"classId\":2,\"hatId\":7,\"primaryWeaponId\":9}]", new akd(null).getType());
        require(equipment.get(0).hatId == 7, "equipment became untyped map");
        ControlsPositionsDataHolder layout = gson.fromJson(
                "{\"useDoubleJoystick\":true,\"arrowsModeData\":{\"walkJoystickX\":0.75}}",
                ControlsPositionsDataHolder.class);
        ControlsPositionsDataHolder again = gson.fromJson(gson.toJson(layout), ControlsPositionsDataHolder.class);
        require(again.useDoubleJoystick && again.arrowsModeData.walkJoystickX == 0.75f, "layout fields lost");
        GamepadData gamepad = gson.fromJson("{\"keycode_jump\":\"3\"}", GamepadData.class);
        require(gamepad.keycode_jump == aaj.d, "gamepad enum wire value lost");
        require(gson.toJson(gamepad).contains("\"3\""), "gamepad enum saved as obfuscated Java name");
        System.out.println("PASS JSON: original game TypeTokens, equipment, controls, enum wire values");
    }

    private static void xmlMaps() throws Exception {
        Document document;
        try (FileReader reader = new FileReader("source/test/testdata/tiled-map.xml")) {
            document = new VitaXmlDocumentBuilderFactory().newDocumentBuilder()
                    .parse(new InputSource(reader));
        }
        Element root = document.getDocumentElement();
        require(root.getAttributes().getNamedItem("width").getNodeValue().equals("120"),
                "Tiled map root attributes were lost");
        NodeList groups = document.getElementsByTagName("objectgroup");
        require(groups.getLength() == 2, "Tiled object groups were not found");
        Node collisions = groups.item(0);
        Node object = collisions.getChildNodes().item(0);
        require(object.getNodeType() == Node.ELEMENT_NODE
                        && object.getAttributes().getNamedItem("x").getNodeValue().equals("16")
                        && object.getChildNodes().item(0).getNodeName().equals("polygon"),
                "Tiled collision objects were not parsed");
        NodeList properties = document.getElementsByTagName("property");
        require(properties.getLength() == 1
                        && properties.item(0).getAttributes().getNamedItem("value").getNodeValue().equals("42"),
                "Tiled map properties were not parsed");
        try (FileReader reader = new FileReader("build/gangfort-assets/res/maps/flag_egypt/flag_egypt.tmx")) {
            document = new VitaXmlDocumentBuilderFactory().newDocumentBuilder()
                    .parse(new InputSource(reader));
        }
        groups = document.getElementsByTagName("objectgroup");
        require(groups.getLength() == 2, "real flag_egypt Tiled groups were not parsed");
        NodeList objects = document.getElementsByTagName("object");
        require(objects.getLength() > 40, "real flag_egypt objects were not parsed");
        for (int i = 0; i < objects.getLength(); i++) {
            Node attributes = objects.item(i).getAttributes().getNamedItem("x");
            require(attributes != null, "real flag_egypt object lost its x attribute");
        }
        System.out.println("PASS XML: Tiled map dimensions, groups, object attributes, nested shapes, properties");
    }

    private static void pools() {
        InputEvent event = Pools.obtain(InputEvent.class);
        Pools.free(event);
        final boolean[] ran = {false};
        SequenceAction sequence = Actions.sequence(Actions.delay(0.05f), Actions.run(() -> ran[0] = true));
        com.badlogic.gdx.scenes.scene2d.Actor actor = new com.badlogic.gdx.scenes.scene2d.Actor();
        actor.addAction(sequence);
        for (int i = 0; i < 10; i++) actor.act(0.016f);
        require(ran[0], "splash action callback never executes");
        System.out.println("PASS pools: InputEvent and delayed RunnableAction sequence");
    }

    private static void physics() {
        PolygonShape shape = new PolygonShape();
        shape.set(new float[] {999, 999, -1, -1, 1, -1, 1, 1, -1, 1}, 2, 8);
        Vector2 vertex = new Vector2();
        for (int i = 0; i < shape.getVertexCount(); i++) {
            shape.getVertex(i, vertex);
            require(Math.abs(vertex.x) <= 1 && Math.abs(vertex.y) <= 1, "polygon offset ignored");
        }
        World world = new World(new Vector2(0, -10), true);
        BodyDef definition = new BodyDef();
        Body floor = world.createBody(definition);
        shape.setAsBox(5, 0.25f);
        floor.createFixture(shape, 0).setUserData("floor");
        definition.type = BodyDef.BodyType.DynamicBody;
        definition.position.set(0, 3);
        Body player = world.createBody(definition);
        shape.setAsBox(0.25f, 0.25f);
        player.createFixture(shape, 1).setUserData("player");
        final int[] contacts = {0};
        world.setContactListener(new ContactListener() {
            public void beginContact(Contact contact) {
                require(contact.getFixtureA().getUserData() != null && contact.getFixtureB().getUserData() != null,
                        "contact lost fixture metadata");
                contacts[0]++;
            }
            public void endContact(Contact c) {}
            public void preSolve(Contact c, Manifold old) {}
            public void postSolve(Contact c, ContactImpulse impulse) {}
        });
        for (int i = 0; i < 180; i++) world.step(1f / 60f, 6, 2);
        require(contacts[0] > 0 && player.getPosition().y > 0.4f && player.getPosition().y < 0.6f,
                "player did not land on floor");
        player.setAwake(false);
        player.applyForceToCenter(10, 20, false);
        player.applyTorque(10, false);
        player.applyAngularImpulse(10, false);
        require(!player.isAwake(), "wake=false woke sleeping player");
        final boolean[] hit = {false};
        world.rayCast((fixture, point, normal, fraction) -> { hit[0] = true; return fraction; },
                new Vector2(0, 5), new Vector2(0, -1));
        require(hit[0], "raycast missed scene");
        world.destroyBody(player);
        world.step(1f / 60f, 6, 2);
        world.dispose(); shape.dispose();
        System.out.println("PASS JBox2D: offset, landing/contact, sleep, raycast, body destruction");
    }

    private static void cancelTouch() {
        com.badlogic.gdx.scenes.scene2d.Actor actor = new com.badlogic.gdx.scenes.scene2d.Actor();
        actor.setSize(100, 100);
        final int[] clicks = {0};
        com.badlogic.gdx.scenes.scene2d.utils.ClickListener listener =
                new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
                    public void clicked(InputEvent event, float x, float y) { clicks[0]++; }
                };
        actor.addListener(listener);
        InputEvent down = new InputEvent();
        down.setListenerActor(actor);
        require(listener.touchDown(down, 10, 10, 0, 0), "touchDown rejected");
        InputEvent up = new InputEvent();
        up.setType(InputEvent.Type.touchUp);
        up.setStageX(10); up.setStageY(10); up.setPointer(0); up.setButton(0);
        actor.fire(up);
        require(clicks[0] == 1, "ordinary button click missing");
        listener.touchDown(down, 10, 10, 0, 0);
        up.setStageX(Integer.MIN_VALUE); up.setStageY(Integer.MIN_VALUE);
        actor.fire(up);
        require(up.isTouchFocusCancel() && clicks[0] == 1 && !listener.isPressed(),
                "touch cancellation activated button");
        System.out.println("PASS Scene2D: cancel held button without activation");
    }
}

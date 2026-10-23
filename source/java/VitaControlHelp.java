import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.utils.Align;

public final class VitaControlHelp {
    private static final Color ORANGE = Color.valueOf("ff9429");

    public static void arrange(Group group) {
        Label.LabelStyle style = null;
        float fontScale = 1;
        int labels = 0;
        for (int i = group.getChildren().size - 1; i >= 0; i--) {
            Actor actor = group.getChildren().get(i);
            if (actor instanceof Label) {
                Label label = (Label) actor;
                style = label.getStyle();
                fontScale = label.getFontScaleX();
                actor.remove();
                labels++;
            }
        }
        if (labels != 10) throw new IllegalStateException("Unexpected Gangfort instruction layout");
        Image controller = (Image) group.getChildren().get(2);
        float scale = Gdx.graphics.getWidth() / 960f;
        float offset = (Gdx.graphics.getHeight() - 544 * scale) / 2;
        controller.setBounds(225 * scale, offset + 165 * scale, 510 * scale, 282 * scale);
        TextureRegion white = akz.a().g().findRegion("white");
        caption(group, style, fontScale, scale, offset, "ps vita", 25, 478, 300, 35, Align.left);
        callout(group, white, style, fontScale, scale, offset, "l - " + ald.a("jump"),
                25, 431, 190, 282, 395, 220, 445);
        callout(group, white, style, fontScale, scale, offset, "d-pad up\n" + ald.a("jump"),
                25, 348, 190, 282, 355, 220, 362);
        callout(group, white, style, fontScale, scale, offset, "left stick / d-pad\n" + ald.a("move") + "\nup - " + ald.a("jump"),
                25, 242, 190, 290, 276, 220, 264);
        callout(group, white, style, fontScale, scale, offset, "r - " + ald.a("shoot"),
                755, 431, 195, 681, 395, 745, 445);
        callout(group, white, style, fontScale, scale, offset, "square\nclass skill (1)",
                755, 365, 195, 656, 333, 738, 387);
        callout(group, white, style, fontScale, scale, offset, "circle\nagent: disguise",
                755, 300, 195, 705, 333, 745, 322);
        callout(group, white, style, fontScale, scale, offset, "cross\nagent: stab\ndealer: heal target",
                755, 233, 195, 681, 309, 735, 264);
        callout(group, white, style, fontScale, scale, offset, "start / triangle\n" + ald.a("menu"),
                755, 168, 195, 694, 241, 730, 190);
        bar(group, white, scale, offset, 670, 135, 2, 141);
        bar(group, white, scale, offset, 525, 135, 147, 2);
        caption(group, style, fontScale, scale, offset, "right stick - " + ald.a("aim"),
                465, 102, 270, 30, Align.center);
        caption(group, style, fontScale * .85f, scale, offset,
                "(1) square: builder - build / destroy blaster\n"
                + "dealer - rapidrush, agent - cloak\n"
                + "menus: d-pad - navigate, cross - select",
                25, 28, 690, 75, Align.left);
    }

    private static void callout(Group group, TextureRegion white, Label.LabelStyle style,
            float fontScale, float scale, float offset, String text, float x, float y, float width,
            float buttonX, float buttonY, float edgeX, float edgeY) {
        float elbowX = x < buttonX || edgeY > buttonY ? buttonX : edgeX;
        bar(group, white, scale, offset, Math.min(buttonX, elbowX), buttonY,
                Math.abs(buttonX - elbowX) + 2, 2);
        bar(group, white, scale, offset, elbowX, Math.min(buttonY, edgeY),
                2, Math.abs(buttonY - edgeY) + 2);
        bar(group, white, scale, offset, Math.min(edgeX, elbowX), edgeY,
                Math.abs(edgeX - elbowX) + 2, 2);
        caption(group, style, fontScale * .9f, scale, offset, text, x, edgeY - 28, width, 56, Align.left);
    }

    private static void caption(Group group, Label.LabelStyle style, float fontScale,
            float scale, float offset, String text, float x, float y, float width, float height, int align) {
        Label label = new Label(text, style);
        label.setFontScale(fontScale);
        float fit = Math.min(1, Math.min(width * scale / label.getPrefWidth(),
                height * scale / label.getPrefHeight()));
        label.setFontScale(fontScale * fit);
        label.setAlignment(align);
        label.setBounds(x * scale, offset + y * scale, width * scale, height * scale);
        label.setTouchable(Touchable.disabled);
        group.addActor(label);
    }

    private static void bar(Group group, TextureRegion white, float scale, float offset,
            float x, float y, float width, float height) {
        Image line = new Image(white);
        line.setColor(ORANGE);
        line.setBounds(x * scale, offset + y * scale, width * scale, height * scale);
        line.setTouchable(Touchable.disabled);
        group.addActor(line);
    }
}

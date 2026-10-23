package com.gangfort.vita;

import org.jbox2d.collision.AABB;
import org.jbox2d.collision.broadphase.Pair;
import org.jbox2d.common.Vec2;
import org.jbox2d.particle.ParticleBodyContact;
import org.jbox2d.particle.ParticleColor;
import org.jbox2d.particle.ParticleContact;
import org.jbox2d.particle.ParticleGroup;
import org.jbox2d.particle.ParticleSystem;
import java.lang.reflect.Constructor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.actions.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener.FocusEvent;
import com.badlogic.gdx.utils.Array;

public final class GangfortObjectFactory {
    private GangfortObjectFactory() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T newInstance(Class<T> type) {
        return (T) create(type == null ? "null" : type.getName());
    }

    public static Object newInstance(Constructor<?> constructor, Object[] arguments) {
        if (constructor == null) throw new IllegalArgumentException("constructor is null");
        if (arguments != null && arguments.length != 0) {
            throw new UnsupportedOperationException(
                    "Gangfort Vita: parameterized reflective constructors are unsupported");
        }
        return create(constructor.getDeclaringClass().getName());
    }

    public static Object newInstance(Class<?> type, Object[] arguments) {
        if (type == null) throw new IllegalArgumentException("type is null");
        if (arguments != null && arguments.length != 0) {
            throw new UnsupportedOperationException(
                    "Gangfort Vita: parameterized reflective constructors are unsupported");
        }
        return create(type.getName());
    }

    private static Object create(String type) {
        switch (type) {
            case "com.badlogic.gdx.graphics.g2d.GlyphLayout": return new GlyphLayout();
            case "com.badlogic.gdx.graphics.g2d.GlyphLayout$GlyphRun": return new GlyphLayout.GlyphRun();
            case "com.badlogic.gdx.graphics.Color": return new Color();
            case "com.badlogic.gdx.math.Rectangle": return new Rectangle();
            case "com.badlogic.gdx.utils.Array": return new Array<>();
            case "com.badlogic.gdx.scenes.scene2d.InputEvent": return new InputEvent();
            case "com.badlogic.gdx.scenes.scene2d.Event": return new Event();
            case "com.badlogic.gdx.scenes.scene2d.Stage$TouchFocus": return new Stage.TouchFocus();
            case "com.badlogic.gdx.scenes.scene2d.ui.Table$DebugRect": return new com.badlogic.gdx.scenes.scene2d.ui.Table.DebugRect();
            case "com.badlogic.gdx.scenes.scene2d.utils.ChangeListener$ChangeEvent": return new ChangeEvent();
            case "com.badlogic.gdx.scenes.scene2d.utils.FocusListener$FocusEvent": return new FocusEvent();
            case "com.badlogic.gdx.scenes.scene2d.actions.AddAction": return new AddAction();
            case "com.badlogic.gdx.scenes.scene2d.actions.AddListenerAction": return new AddListenerAction();
            case "com.badlogic.gdx.scenes.scene2d.actions.AfterAction": return new AfterAction();
            case "com.badlogic.gdx.scenes.scene2d.actions.AlphaAction": return new AlphaAction();
            case "com.badlogic.gdx.scenes.scene2d.actions.ColorAction": return new ColorAction();
            case "com.badlogic.gdx.scenes.scene2d.actions.DelayAction": return new DelayAction();
            case "com.badlogic.gdx.scenes.scene2d.actions.FloatAction": return new FloatAction();
            case "com.badlogic.gdx.scenes.scene2d.actions.IntAction": return new IntAction();
            case "com.badlogic.gdx.scenes.scene2d.actions.LayoutAction": return new LayoutAction();
            case "com.badlogic.gdx.scenes.scene2d.actions.MoveByAction": return new MoveByAction();
            case "com.badlogic.gdx.scenes.scene2d.actions.MoveToAction": return new MoveToAction();
            case "com.badlogic.gdx.scenes.scene2d.actions.ParallelAction": return new ParallelAction();
            case "com.badlogic.gdx.scenes.scene2d.actions.RemoveAction": return new RemoveAction();
            case "com.badlogic.gdx.scenes.scene2d.actions.RemoveActorAction": return new RemoveActorAction();
            case "com.badlogic.gdx.scenes.scene2d.actions.RemoveListenerAction": return new RemoveListenerAction();
            case "com.badlogic.gdx.scenes.scene2d.actions.RepeatAction": return new RepeatAction();
            case "com.badlogic.gdx.scenes.scene2d.actions.RotateByAction": return new RotateByAction();
            case "com.badlogic.gdx.scenes.scene2d.actions.RotateToAction": return new RotateToAction();
            case "com.badlogic.gdx.scenes.scene2d.actions.RunnableAction": return new RunnableAction();
            case "com.badlogic.gdx.scenes.scene2d.actions.ScaleByAction": return new ScaleByAction();
            case "com.badlogic.gdx.scenes.scene2d.actions.ScaleToAction": return new ScaleToAction();
            case "com.badlogic.gdx.scenes.scene2d.actions.SequenceAction": return new SequenceAction();
            case "com.badlogic.gdx.scenes.scene2d.actions.SizeByAction": return new SizeByAction();
            case "com.badlogic.gdx.scenes.scene2d.actions.SizeToAction": return new SizeToAction();
            case "com.badlogic.gdx.scenes.scene2d.actions.TimeScaleAction": return new TimeScaleAction();
            case "com.badlogic.gdx.scenes.scene2d.actions.TouchableAction": return new TouchableAction();
            case "com.badlogic.gdx.scenes.scene2d.actions.VisibleAction": return new VisibleAction();
            case "java.lang.Object": return new Object();
            case "org.jbox2d.collision.AABB": return new AABB();
            case "org.jbox2d.collision.broadphase.Pair": return new Pair();
            case "org.jbox2d.common.Vec2": return new Vec2();
            case "org.jbox2d.particle.ParticleBodyContact": return new ParticleBodyContact();
            case "org.jbox2d.particle.ParticleColor": return new ParticleColor();
            case "org.jbox2d.particle.ParticleContact": return new ParticleContact();
            case "org.jbox2d.particle.ParticleGroup": return new ParticleGroup();
            case "org.jbox2d.particle.ParticleSystem$Proxy": return new ParticleSystem.Proxy();
            case "org.jbox2d.particle.ParticleSystem$Pair": return new ParticleSystem.Pair();
            case "org.jbox2d.particle.ParticleSystem$Triad": return new ParticleSystem.Triad();
            default:
                throw new UnsupportedOperationException(
                        "Gangfort Vita: no direct constructor registered for " + type);
        }
    }
}

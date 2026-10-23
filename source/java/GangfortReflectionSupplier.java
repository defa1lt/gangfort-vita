import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.teavm.classlib.ReflectionContext;
import org.teavm.classlib.ReflectionSupplier;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodDescriptor;

/** The save DTOs read and written by Gangfort's Gson calls. */
public final class GangfortReflectionSupplier implements ReflectionSupplier {
    private static final Set<String> TYPES = Set.of(
            "com.gangfort.game.models.ControlsPositionsData",
            "com.gangfort.game.models.ControlsPositionsDataHolder",
            "com.gangfort.game.models.GamepadData",
            "com.gangfort.game.network.PlayerItemSelectionSettings",
            "com.gangfort.game.network.ServerConfig");

    @Override
    public Collection<String> getAccessibleFields(ReflectionContext context, String name) {
        if (!TYPES.contains(name)) return Collections.emptyList();
        var result = new ArrayList<String>();
        var cls = context.getClassSource().get(name);
        if (cls == null) throw new IllegalStateException("Missing save DTO: " + name);
        for (var field : cls.getFields()) {
            if (!field.hasModifier(ElementModifier.STATIC)
                    && !field.hasModifier(ElementModifier.TRANSIENT)) {
                result.add(field.getName());
            }
        }
        return result;
    }

    @Override
    public Collection<MethodDescriptor> getAccessibleMethods(ReflectionContext context, String name) {
        return Collections.emptyList();
    }
}

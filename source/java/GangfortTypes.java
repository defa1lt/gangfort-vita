import com.gangfort.game.models.ControlsPositionsData;
import com.gangfort.game.models.ControlsPositionsDataHolder;
import com.gangfort.game.models.GamepadData;
import com.gangfort.game.network.PlayerItemSelectionSettings;
import com.gangfort.game.network.ServerConfig;
import com.google.gson.Gson;
import com.google.gson.internal.ParamType;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;

/** Explicit constructors and generic types replace unsupported runtime discovery. */
public final class GangfortTypes {
    private GangfortTypes() {}

    public static void register() {
        Gson.stsvRegisterFactory(ControlsPositionsData.class.getName(), ControlsPositionsData::new);
        Gson.stsvRegisterFactory(ControlsPositionsDataHolder.class.getName(), ControlsPositionsDataHolder::new);
        Gson.stsvRegisterFactory(GamepadData.class.getName(), GamepadData::new);
        Gson.stsvRegisterFactory(PlayerItemSelectionSettings.class.getName(), PlayerItemSelectionSettings::new);
        Gson.stsvRegisterFactory(ServerConfig.class.getName(), ServerConfig::new);
        TypeToken.stsvRegister("aap", ParamType.of(ArrayList.class, Integer.class));
        TypeToken.stsvRegister("akc", ParamType.of(ArrayList.class, String.class));
        TypeToken.stsvRegister("akd", ParamType.of(ArrayList.class, PlayerItemSelectionSettings.class));
        aaj[] inputs = aaj.values();
        for (int i = 0; i < inputs.length; i++) {
            Gson.stsvRegisterEnum(aaj.class, Integer.toString(i), inputs[i]);
        }
    }
}

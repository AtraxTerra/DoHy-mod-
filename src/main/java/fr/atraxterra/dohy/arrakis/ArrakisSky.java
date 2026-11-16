package fr.atraxterra.dohy.arrakis;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Le ciel d'Arrakis : NTM Space calcule la couleur du ciel et du brouillard
 * d'apres les gaz de l'atmosphere (l'air de Duna le rend rouille). Berskar
 * passe apres lui et garde sa lumiere — jour, nuit, eclipses, tempetes — mais
 * lui donne la teinte d'un ciel de sable, pale et chaud.
 *
 * <p>NTM tient une liste de fournisseurs de couches d'environnement
 * ({@code AtmosphereLayers}) ; on s'y inscrit par un mandataire, sans
 * dependance de compilation.
 */
final class ArrakisSky {

    /** Ciel et brouillard d'Arrakis en plein jour. */
    private static final int SKY = 0xE3C796;
    private static final int FOG = 0xEBD5AA;
    /** Lever et coucher : orange brule, pas le bleu martien de Duna. */
    private static final int SUNSET = 0xF0782F;
    /** Luminance du ciel de NTM en plein jour avec l'air d'Arrakis : la ou la teinte est pleine. */
    private static final float SKY_REFERENCE = 0.56f;
    private static final float FOG_REFERENCE = 0.62f;

    private ArrakisSky() {
    }

    static void register() {
        try {
            Class<?> layers = Class.forName("com.hbm.api.environment.AtmosphereLayers");
            Class<?> provider = Class.forName("com.hbm.api.environment.AtmosphereLayers$Provider");
            Object proxy = Proxy.newProxyInstance(provider.getClassLoader(), new Class<?>[] {provider},
                    ArrakisSky::invoke);
            layers.getMethod("register", provider).invoke(null, proxy);
            DoHy.LOGGER.info("Arrakis : ciel de sable inscrit apres NTM Space");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            DoHy.LOGGER.warn("Arrakis : impossible d'inscrire le ciel de sable", e);
        }
    }

    private static Object invoke(Object self, Method method, Object[] args) {
        return switch (method.getName()) {
            case "claims" -> args != null && args[0] instanceof Level level
                    && level.dimension() == DoHyArrakis.DIMENSION;
            case "addLayers" -> {
                if (args != null && args[0] instanceof EnvironmentAttributeSystem.Builder builder) {
                    addLayers(builder);
                }
                yield null;
            }
            case "hashCode" -> System.identityHashCode(self);
            case "equals" -> args != null && self == args[0];
            case "toString" -> "Berskar : ciel d'Arrakis";
            default -> null;
        };
    }

    private static void addLayers(EnvironmentAttributeSystem.Builder builder) {
        builder.addPositionalLayer(EnvironmentAttributes.SKY_COLOR,
                (value, position, interpolator) -> tint(value, SKY, SKY_REFERENCE));
        builder.addPositionalLayer(EnvironmentAttributes.FOG_COLOR,
                (value, position, interpolator) -> tint(value, FOG, FOG_REFERENCE));
        // l'opacite dit la force de la lueur au fil du jour : on la garde, on change la couleur
        builder.addPositionalLayer(EnvironmentAttributes.SUNRISE_SUNSET_COLOR,
                (value, position, interpolator) -> value == null ? null : value & 0xFF000000 | SUNSET);
    }

    /** La teinte voulue, a la luminosite de la couleur calculee par NTM. */
    static Integer tint(Integer value, int target, float reference) {
        if (value == null) {
            return null;
        }
        int rgb = value;
        float r = (rgb >> 16 & 0xFF) / 255.0f;
        float g = (rgb >> 8 & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;
        float light = Math.min(1.25f, (0.299f * r + 0.587f * g + 0.114f * b) / reference);
        int tr = Math.min(255, Math.round((target >> 16 & 0xFF) * light));
        int tg = Math.min(255, Math.round((target >> 8 & 0xFF) * light));
        int tb = Math.min(255, Math.round((target & 0xFF) * light));
        return rgb & 0xFF000000 | tr << 16 | tg << 8 | tb;
    }
}

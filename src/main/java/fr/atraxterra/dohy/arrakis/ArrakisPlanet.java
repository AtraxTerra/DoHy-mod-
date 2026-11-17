package fr.atraxterra.dohy.arrakis;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Le corps celeste de Duna, retouche pour Arrakis, par reflexion : NTM Space
 * n'est pas une dependance de compilation.
 *
 * <p>L'air : 18 % d'oxygene dans l'air de Duna, a pression normale. NTM Space
 * laisse les joueurs puiser ou rejeter des gaz ; si l'oxygene vient a manquer,
 * il est remis a son niveau (verification au demarrage puis toutes les cinq
 * minutes), sans toucher aux autres gaz.
 */
@EventBusSubscriber(modid = DoHy.MOD_ID)
public final class ArrakisPlanet {

    private static final String BODY = "com.hbmspace.dim.CelestialBody";
    private static final String TRAIT = "com.hbmspace.dim.trait.CelestialBodyTrait";
    private static final String ATMOSPHERE = "com.hbmspace.dim.trait.CBT_Atmosphere";
    private static final String FLUIDS = "com.hbm.inventory.fluid.NTMFluids";

    /** Oxygene a l'arrivee, et seuil sous lequel on le remet (NTM Space etouffe sous 0,09). */
    private static final double OXYGEN = 0.18d;
    private static final double OXYGEN_FLOOR = 0.11d;
    private static final double DUST = 0.82d;
    private static final int CHECK_TICKS = 6000;

    private static boolean installed;
    private static boolean warned;
    private static int ticks;

    private ArrakisPlanet() {
    }

    /** A la mise en place commune : l'atmosphere et la teinte par defaut de Duna. */
    static void install() {
        // les biomes de Duna, que la source de biomes de NTM Space cherche par numero
        int[] biomes = ArrakisTerrain.Ids.resolve();
        DoHy.LOGGER.info("Arrakis : biomes de Duna numerotes {}", java.util.Arrays.toString(biomes));
        try {
            Class.forName("com.hbmspace.dim.SolarSystem").getMethod("ensureInit").invoke(null);
            Object body = body();
            if (body == null) {
                DoHy.LOGGER.warn("Arrakis : Duna introuvable dans NTM Space");
                return;
            }
            Class<?> trait = Class.forName(TRAIT);
            Object traits = Array.newInstance(trait, 1);
            Array.set(traits, 0, atmosphere());
            body.getClass().getMethod("withTraits", traits.getClass()).invoke(body, traits);
            body.getClass().getMethod("withColor", float[].class).invoke(body,
                    (Object) new float[] {0.80f, 0.62f, 0.38f});
            installed = true;
            DoHy.LOGGER.info("Arrakis : Duna respirable, couleur de sable");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            DoHy.LOGGER.error("Arrakis : impossible de retoucher Duna", e);
        }
    }

    private static Object body() throws ReflectiveOperationException {
        return Class.forName(BODY).getMethod("getBodyOrNull", String.class).invoke(null, "duna");
    }

    private static Fluid fluid(String name) throws ReflectiveOperationException {
        return (Fluid) Class.forName(FLUIDS).getMethod("byName", String.class).invoke(null, name);
    }

    private static Object atmosphere() throws ReflectiveOperationException {
        Class<?> atmosphere = Class.forName(ATMOSPHERE);
        Object air = atmosphere.getConstructor(Fluid.class, double.class).newInstance(fluid("OXYGEN"), OXYGEN);
        return atmosphere.getMethod("and", Fluid.class, double.class).invoke(air, fluid("DUNAAIR"), DUST);
    }

    /**
     * Remet l'oxygene d'Arrakis s'il manque dans l'atmosphere en vigueur (celle
     * de la sauvegarde si NTM Space en a ecrit une, sinon celle par defaut).
     */
    @SuppressWarnings("unchecked")
    static void ensureBreathable() {
        if (!installed) {
            return;
        }
        try {
            Object body = body();
            if (body == null) {
                return;
            }
            Class<?> atmosphereClass = Class.forName(ATMOSPHERE);
            Map<Class<?>, Object> traits = (Map<Class<?>, Object>) body.getClass().getMethod("getTraits").invoke(body);
            Object atmosphere = traits.get(atmosphereClass);
            Fluid oxygen = fluid("OXYGEN");
            if (atmosphere == null) {
                traits.put(atmosphereClass, atmosphere());
            } else {
                double now = (double) atmosphereClass.getMethod("getPressure", Fluid.class).invoke(atmosphere, oxygen);
                if (now >= OXYGEN_FLOOR) {
                    return;
                }
                atmosphereClass.getMethod("add", Fluid.class, double.class).invoke(atmosphere, oxygen, OXYGEN - now);
            }
            Method set = body.getClass().getMethod("setTraits", Map.class);
            set.invoke(body, traits);
            DoHy.LOGGER.info("Arrakis : oxygene remis dans l'atmosphere");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            if (!warned) {
                warned = true;
                DoHy.LOGGER.warn("Arrakis : atmosphere illisible", e);
            }
        }
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        ticks = 0;
        ensureBreathable();
        if (installed) {
            ArrakisPort.refresh(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (installed && ++ticks >= CHECK_TICKS) {
            ticks = 0;
            ensureBreathable();
        }
    }

    /** Pour les tests : l'oxygene en vigueur sur Arrakis, ou -1. */
    public static double oxygen(MinecraftServer server) {
        try {
            Object body = body();
            Class<?> atmosphereClass = Class.forName(ATMOSPHERE);
            Object traits = body.getClass().getMethod("getTraits").invoke(body);
            Object atmosphere = ((Map<?, ?>) traits).get(atmosphereClass);
            return atmosphere == null ? 0.0d
                    : (double) atmosphereClass.getMethod("getPressure", Fluid.class).invoke(atmosphere, fluid("OXYGEN"));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return -1.0d;
        }
    }
}

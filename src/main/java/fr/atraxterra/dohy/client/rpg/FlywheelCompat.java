package fr.atraxterra.dohy.client.rpg;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.palamod.dungeon.PalaDungeon;
import fr.atraxterra.dohy.palamod.dungeon.RpgSkin;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Les monstres de donjon sous Flywheel (et ses derives : Crankshaft, Vanillin).
 *
 * <p>Ces moteurs dessinent les creatures vanilla par instances, sans passer par
 * le rendu du jeu : {@code RenderLivingEvent.Pre} n'est jamais emis pour elles,
 * et {@link RpgClient} ne peut ni annuler le corps vanilla ni poser le modele
 * de Paladium. Le monstre de donjon reste alors un zombie ordinaire.
 *
 * <p>Flywheel decide par type de creature, avec un « visualiseur » qui dit,
 * creature par creature, s'il la prend en charge. On enveloppe donc le
 * visualiseur des types qui portent une apparence : pour une creature habillee,
 * il ne cree rien et rend la main au jeu ; pour les autres, il laisse faire
 * l'original. Les creatures deja prises en charge au moment ou leur apparence
 * arrive sont rendues au jeu a leur tour.
 *
 * <p>Tout passe par reflexion : Flywheel n'est pas une dependance, et sans lui
 * cette classe ne fait rien.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class FlywheelCompat {

    private static final String API = "dev.engine_room.flywheel.api.visualization.";
    private static final String LIB = "dev.engine_room.flywheel.lib.visualization.";

    /** Les creatures a plus de dix blocs de troncons ne sont pas dessinees de toute facon. */
    private static final double RANGE = 160.0d;
    private static final int PERIOD = 5;
    private static final int RECHECK = 100;

    /** Coupe l'adaptation ; ne sert qu'aux essais. */
    public static volatile boolean enabled = true;

    private static boolean ready;
    private static boolean failed;
    private static MethodHandle getVisualizer;
    private static MethodHandle setVisualizer;
    private static MethodHandle createVisual;
    private static MethodHandle skipVanillaRender;
    private static MethodHandle skipVanillaPrimary;
    private static MethodHandle managerOf;
    private static MethodHandle entitiesOf;
    private static MethodHandle queueAdd;
    private static MethodHandle queueRemove;
    private static Class<?> simpleVisualizer;
    private static Class<?> factoryType;
    private static Constructor<?> withPrimary;
    private static Constructor<?> withoutPrimary;

    /** Le visualiseur pose par cette classe, par type de creature. */
    private static final Map<EntityType<?>, Object> WRAPPERS = new IdentityHashMap<>();
    /** Ce qu'on savait de chaque creature au dernier passage : habillee ou non. */
    private static final Map<Entity, Boolean> KNOWN = new WeakHashMap<>();
    private static int ticks;

    private FlywheelCompat() {
    }

    private static boolean prepare() {
        if (ready || failed) {
            return ready;
        }
        try {
            Class<?> registry = Class.forName(API + "VisualizerRegistry");
            Class<?> visualizer = Class.forName(API + "EntityVisualizer");
            Class<?> context = Class.forName(API + "VisualizationContext");
            Class<?> manager = Class.forName(API + "VisualizationManager");
            Class<?> visuals = Class.forName(API + "VisualManager");
            simpleVisualizer = Class.forName(LIB + "SimpleEntityVisualizer");
            factoryType = Class.forName(LIB + "SimpleEntityVisualizer$Factory");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            getVisualizer = lookup.unreflect(registry.getMethod("getVisualizer", EntityType.class));
            setVisualizer = lookup.unreflect(registry.getMethod("setVisualizer", EntityType.class, visualizer));
            createVisual = lookup.unreflect(visualizer.getMethod("createVisual", context, Entity.class, float.class));
            skipVanillaRender = lookup.unreflect(visualizer.getMethod("skipVanillaRender", Entity.class));
            managerOf = lookup.unreflect(manager.getMethod("get", LevelAccessor.class));
            entitiesOf = lookup.unreflect(manager.getMethod("entities"));
            queueAdd = lookup.unreflect(visuals.getMethod("queueAdd", Object.class));
            queueRemove = lookup.unreflect(visuals.getMethod("queueRemove", Object.class));
            try {
                // Crankshaft : un second predicat, « ne dessine pas le corps ».
                skipVanillaPrimary = lookup.unreflect(simpleVisualizer.getMethod("skipVanillaPrimary", Entity.class));
                withPrimary = simpleVisualizer.getConstructor(factoryType, Predicate.class, Predicate.class);
            } catch (NoSuchMethodException absent) {
                skipVanillaPrimary = null;
                withPrimary = null;
            }
            withoutPrimary = simpleVisualizer.getConstructor(factoryType, Predicate.class);
            ready = true;
            DoHy.LOGGER.info("Flywheel detecte : les monstres de donjon habilles lui sont retires");
        } catch (ClassNotFoundException absent) {
            failed = true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            failed = true;
            DoHy.LOGGER.warn("Flywheel present mais son API a change : les monstres de donjon garderont "
                    + "leur apparence vanilla", error);
        }
        return ready;
    }

    static boolean skinned(Entity entity) {
        if (!enabled || !(entity instanceof LivingEntity)) {
            return false;
        }
        RpgSkin skin = entity.getExistingDataOrNull(PalaDungeon.SKIN.get());
        return skin != null && !skin.isNone();
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (failed || !prepare()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            KNOWN.clear();
            return;
        }
        if (++ticks % PERIOD != 0) {
            return;
        }
        try {
            if (ticks % RECHECK == 0) {
                recheck();
            }
            for (LivingEntity entity : minecraft.level.getEntitiesOfClass(LivingEntity.class,
                    minecraft.player.getBoundingBox().inflate(RANGE))) {
                boolean skinned = skinned(entity);
                Boolean before = KNOWN.put(entity, skinned);
                if (before == null ? skinned : before != skinned) {
                    wrap(entity.getType());
                    refresh(entity);
                }
            }
        } catch (Throwable error) {
            failed = true;
            DoHy.LOGGER.warn("Adaptation a Flywheel abandonnee", error);
        }
    }

    /**
     * Le moteur repose parfois son visualiseur d'origine (sa configuration le
     * fait a chaque changement) : on le reprend, et les creatures habillees
     * de ce type lui sont de nouveau retirees.
     */
    private static void recheck() throws Throwable {
        for (EntityType<?> type : new ArrayList<>(WRAPPERS.keySet())) {
            Object current = getVisualizer.invoke(type);
            if (current != null && current != WRAPPERS.get(type)) {
                wrap(type);
                for (Map.Entry<Entity, Boolean> known : new ArrayList<>(KNOWN.entrySet())) {
                    Entity entity = known.getKey();
                    if (entity != null && entity.getType() == type && known.getValue() && entity.isAlive()) {
                        refresh(entity);
                    }
                }
            }
        }
    }

    private static void wrap(EntityType<?> type) throws Throwable {
        Object original = getVisualizer.invoke(type);
        if (original == null || original == WRAPPERS.get(type)) {
            return;
        }
        Predicate<Entity> render = entity -> !skinned(entity) && test(skipVanillaRender, original, entity);
        boolean simple = skipVanillaPrimary != null && simpleVisualizer.isInstance(original);
        Predicate<Entity> primary = entity -> simple && !skinned(entity)
                && test(skipVanillaPrimary, original, entity);
        Object factory = Proxy.newProxyInstance(factoryType.getClassLoader(), new Class<?>[] {factoryType},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("create") && arguments != null && arguments.length == 3) {
                        Entity entity = (Entity) arguments[1];
                        // Rien pour une creature habillee : le jeu la dessine, et RpgClient avec lui.
                        return skinned(entity) ? null
                                : createVisual.invoke(original, arguments[0], entity, (float) arguments[2]);
                    }
                    return switch (method.getName()) {
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == arguments[0];
                        default -> "DoHy:" + original;
                    };
                });
        Object wrapper = withPrimary != null ? withPrimary.newInstance(factory, render, primary)
                : withoutPrimary.newInstance(factory, render);
        setVisualizer.invoke(type, wrapper);
        WRAPPERS.put(type, wrapper);
    }

    private static boolean test(MethodHandle predicate, Object visualizer, Entity entity) {
        try {
            return (boolean) predicate.invoke(visualizer, entity);
        } catch (Throwable error) {
            return false;
        }
    }

    /** Fait recreer ce que le moteur dessine pour cette creature. */
    private static void refresh(Entity entity) throws Throwable {
        Object manager = managerOf.invoke((LevelAccessor) entity.level());
        if (manager == null) {
            return;
        }
        Object entities = entitiesOf.invoke(manager);
        queueRemove.invoke(entities, (Object) entity);
        queueAdd.invoke(entities, (Object) entity);
    }
}

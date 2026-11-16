package fr.atraxterra.dohy.client.hud;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;

/**
 * Ce que le HUD du jeu garde pour lui : le message de la barre d'action, le nom de l'objet
 * tenu et la barre du moment au-dessus de la hotbar (experience, saut, reperes).
 *
 * <p>Ces champs sont prives ; on les lit par reflexion, une fois resolus. Un champ
 * introuvable rend {@code null} et le morceau de HUD qui en depend laisse la main au jeu :
 * une mise a jour qui renomme un champ coute le style, pas l'information.
 */
final class HudAccess {

    private static Field overlayMessage;
    private static Field overlayTime;
    private static Field overlayAnimated;
    private static Field highlightTimer;
    private static Field highlightStack;
    private static Field contextual;
    private static boolean resolved;
    private static boolean failed;

    private HudAccess() {
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            overlayMessage = field("overlayMessageString");
            overlayTime = field("overlayMessageTime");
            overlayAnimated = field("animateOverlayMessageColor");
            highlightTimer = field("toolHighlightTimer");
            highlightStack = field("lastToolHighlight");
            contextual = field("contextualInfoBar");
        } catch (ReflectiveOperationException | RuntimeException error) {
            failed = true;
            DoHy.LOGGER.warn("HUD : champs du jeu introuvables, une partie du HUD reste celle du jeu", error);
        }
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = Hud.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    static boolean available() {
        resolve();
        return !failed;
    }

    /** Le message de la barre d'action, ou {@code null}. */
    static Component overlayMessage(Hud hud) {
        return (Component) get(overlayMessage, hud);
    }

    static int overlayTime(Hud hud) {
        Object value = get(overlayTime, hud);
        return value instanceof Integer time ? time : 0;
    }

    static boolean overlayAnimated(Hud hud) {
        return get(overlayAnimated, hud) instanceof Boolean animated && animated;
    }

    static int highlightTimer(Hud hud) {
        Object value = get(highlightTimer, hud);
        return value instanceof Integer time ? time : 0;
    }

    static ItemStack highlightStack(Hud hud) {
        return get(highlightStack, hud) instanceof ItemStack stack ? stack : ItemStack.EMPTY;
    }

    /**
     * La barre que le jeu met au-dessus de la hotbar : {@code EXPERIENCE}, {@code JUMPABLE_VEHICLE},
     * {@code LOCATOR} ou {@code EMPTY} ; {@code null} si on ne sait pas la lire.
     */
    static String contextual(Hud hud) {
        Object pair = get(contextual, hud);
        if (pair instanceof com.mojang.datafixers.util.Pair<?, ?> value && value.getFirst() instanceof Enum<?> kind) {
            return kind.name();
        }
        return null;
    }

    private static Object get(Field field, Hud hud) {
        resolve();
        if (failed || field == null || hud == null) {
            return null;
        }
        try {
            return field.get(hud);
        } catch (IllegalAccessException | RuntimeException error) {
            return null;
        }
    }
}

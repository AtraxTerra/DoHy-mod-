package fr.atraxterra.dohy.client.hud;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.EnumSet;
import java.util.Set;

/**
 * Le HUD de Berskar au style Zephyr : la hotbar en carte, la sante, la faim, l'armure,
 * l'air, la monture et l'eau d'Arrakis en barres, l'experience fine sous elles, le nom de l'objet tenu et la
 * barre d'action en pastilles, les effets en puces, la carte de faction en haut a droite.
 *
 * <p>Chaque couche du jeu est enveloppee, pas remplacee : l'enveloppe garde la couche du
 * jeu et lui rend la main quand le style n'a rien a dire (spectateur, barre des reperes) ou
 * quand il echoue — une exception est notee une fois et le morceau redevient celui du jeu
 * pour la session, au lieu de faire tomber le jeu.
 *
 * <p>Une couche enveloppee perd la condition que le jeu lui avait donnee (HUD masque par F1,
 * mode de jeu) : chaque morceau la reverifie lui-meme.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class ZephyrHud {

    /** Les morceaux du HUD ; un morceau qui echoue redevient celui du jeu. */
    enum Part {
        HOTBAR, STATUS, EXPERIENCE, ITEM_NAME, ACTION_BAR, EFFECTS, CARD, BOSS, CHAT
    }

    private static final Set<Part> BROKEN = EnumSet.noneOf(Part.class);

    /** Ce que dessine un morceau. Rend {@code false} pour laisser le jeu dessiner a sa place. */
    @FunctionalInterface
    interface Drawer {
        boolean draw(GuiGraphicsExtractor graphics, DeltaTracker delta);
    }

    private ZephyrHud() {
    }

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        wrap(event, VanillaGuiLayers.HOTBAR, Part.HOTBAR, HotbarHud::draw);
        wrap(event, VanillaGuiLayers.PLAYER_HEALTH, Part.STATUS, StatusHud::health);
        wrap(event, VanillaGuiLayers.ARMOR_LEVEL, Part.STATUS, StatusHud::armor);
        wrap(event, VanillaGuiLayers.FOOD_LEVEL, Part.STATUS, StatusHud::food);
        wrap(event, VanillaGuiLayers.VEHICLE_HEALTH, Part.STATUS, StatusHud::mount);
        wrap(event, VanillaGuiLayers.AIR_LEVEL, Part.STATUS, StatusHud::air);
        // l'eau d'Arrakis : une rangee de plus a droite, entre la faim (ou la monture) et l'air
        event.registerAbove(VanillaGuiLayers.VEHICLE_HEALTH, Identifier.fromNamespaceAndPath(DoHy.MOD_ID,
                "water_level"), (graphics, delta) -> {
                    if (!BROKEN.contains(Part.STATUS)) {
                        try {
                            StatusHud.water(graphics, delta);
                        } catch (Throwable error) {
                            fail(Part.STATUS, error);
                        }
                    }
                });
        wrap(event, VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND, Part.EXPERIENCE, ExperienceHud::background);
        wrap(event, VanillaGuiLayers.EXPERIENCE_LEVEL, Part.EXPERIENCE, ExperienceHud::level);
        wrap(event, VanillaGuiLayers.CONTEXTUAL_INFO_BAR, Part.EXPERIENCE, ExperienceHud::bar);
        wrap(event, VanillaGuiLayers.SELECTED_ITEM_NAME, Part.ITEM_NAME, ItemNameHud::draw);
        wrap(event, VanillaGuiLayers.OVERLAY_MESSAGE, Part.ACTION_BAR, ActionBarHud::draw);
        wrap(event, VanillaGuiLayers.EFFECTS, Part.EFFECTS, EffectsHud::draw);
        event.registerAboveAll(Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "status_card"), (graphics, delta) -> {
            if (!BROKEN.contains(Part.CARD)) {
                try {
                    StatusCard.draw(graphics, delta);
                } catch (Throwable error) {
                    fail(Part.CARD, error);
                }
            }
        });
    }

    private static void wrap(RegisterGuiLayersEvent event, Identifier layer, Part part, Drawer drawer) {
        event.wrapLayer(layer, vanilla -> (graphics, delta) -> {
            if (BROKEN.contains(part)) {
                vanilla.render(graphics, delta);
                return;
            }
            boolean handled;
            try {
                handled = drawer.draw(graphics, delta);
            } catch (Throwable error) {
                fail(part, error);
                handled = false;
            }
            if (!handled) {
                vanilla.render(graphics, delta);
            }
        });
    }

    static boolean broken(Part part) {
        return BROKEN.contains(part);
    }

    /** Un morceau a echoue : une ligne de journal, et il redevient celui du jeu pour la session. */
    static void fail(Part part, Throwable error) {
        if (BROKEN.add(part)) {
            DoHy.LOGGER.error("HUD Zephyr : « {} » a echoue, il redevient celui du jeu pour la session",
                    part.name().toLowerCase(java.util.Locale.ROOT), error);
        }
    }
}

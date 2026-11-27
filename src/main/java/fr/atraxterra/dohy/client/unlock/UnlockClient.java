package fr.atraxterra.dohy.client.unlock;

import fr.atraxterra.dohy.client.ui.screen.MachineKitScreen.Design;
import fr.atraxterra.dohy.palamod.unlock.GrassColor;
import fr.atraxterra.dohy.palamod.unlock.PalaUnlocks;
import net.minecraft.client.color.block.BlockTintSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Le client des objets de deblocage : le bateau en bambou, la teinte des
 * herbes colorees, et l'ecran de la table d'alchimiste.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public final class UnlockClient {

    /** La table d'alchimiste : l'atelier des potions en haut, celui des fleches en bas. */
    public static final Design ALCHEMY_CREATOR = new Design(Set.of(4, 9), (g, menu, mouseX, mouseY) -> {
        g.section(16, 38, "section_craft", "POTIONS", 220);
        g.arrow(126, 64, 66, menu.progress());
        g.section(16, 90, "section_craft", "FLECHES (ARC EN PALADIUM)", 220);
        g.arrow(126, 116, 66, menu.progress2());
    }, Map.of(
            0, List.of("Ingredient", "Oeil d'araignee, poudre de blaze", "ou fragment de crane de wither"),
            1, List.of("Verrue du Nether"),
            2, List.of("Second ingredient"),
            3, List.of("Fiole vide"),
            4, List.of("Potion"),
            5, List.of("Ingredient"),
            6, List.of("Verrue du Nether"),
            7, List.of("Second ingredient"),
            8, List.of("Fleche"),
            9, List.of("Seize fleches")));

    private UnlockClient() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PalaUnlocks.BAMBOO_BOAT.get(), BambooBoatRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(BambooBoatRenderer.LAYER, BambooBoatRenderer::createLayer);
    }

    /** Une teinte par couleur : l'herbe (dessus et frange), la graine et la haute herbe. */
    @SubscribeEvent
    public static void onRegisterTints(RegisterColorHandlersEvent.BlockTintSources event) {
        for (GrassColor color : GrassColor.values()) {
            int tint = color.tint();
            BlockTintSource source = state -> tint;
            event.register(List.of(source),
                    PalaUnlocks.COLORED_GRASS.get(color).get(),
                    PalaUnlocks.COLORED_GRASS_SEEDS.get(color).get(),
                    PalaUnlocks.COLORED_TALL_GRASS.get(color).get());
        }
    }
}

package fr.atraxterra.dohy.client.lucky;

import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.lucky.PalaLucky;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Les filtres d'ecran des lucky blocks ({@code EventsManager.drawFilter} et
 * {@code renderPumpkinBlur}) : sous tout le reste de l'interface.
 *
 * <ul>
 *   <li>On voit pas le fond du bol : la vue de la citrouille, meme sans elle ;</li>
 *   <li>Vision nocturne : un voile vert ({@code greenfilter}, 47 %) ;</li>
 *   <li>Sans lunette : un voile gris presque opaque ({@code floufilter}, 87 %) ;</li>
 *   <li>I love cats : un chat au milieu de l'ecran, large de la moitie de
 *       l'ecran ({@code cat.png}, 887 x 1030 : la hauteur vaut 1,16 fois la
 *       largeur). PalaMod montrait une photo ; ici un chat dessine.</li>
 * </ul>
 */
@EventBusSubscriber(Dist.CLIENT)
public final class LuckyOverlays {

    private static final Identifier PUMPKIN = Identifier.withDefaultNamespace("textures/misc/pumpkinblur.png");
    private static final Identifier GREEN = texture("green");
    private static final Identifier FLOU = texture("flou");
    private static final Identifier CAT = texture("cat");
    /** 404 Not found : la texture manquante, a moitie transparente, sur tout l'ecran. */
    private static final Identifier MISSING = texture("missing");
    /** Les etoiles de « La tete dans les etoiles » ({@code StarRenderListener}). */
    private static final Identifier STARS = texture("stars");
    /** Les chiffres verts de « Hackerman » ({@code PLuckyBlock.H4CK3D}). */
    private static final Identifier MATRIX = texture("matrix");
    /** Les filtres sont des aplats : quelques pixels suffisent, etires sur tout l'ecran. */
    private static final int FILTER_SIZE = 16;
    /** Le chat dessine, et le rapport de l'image de PalaMod (1030 / 887). */
    private static final int CAT_WIDTH = 64;
    private static final int CAT_HEIGHT = 74;
    private static final double CAT_RATIO = 1.161217d;

    private LuckyOverlays() {
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "textures/gui/lucky/overlay/" + name + ".png");
    }

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerBelowAll(Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "lucky_filters"),
                LuckyOverlays::draw);
    }

    private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        // La citrouille portee a deja sa vue : on ne la double pas.
        if (player.hasEffect(PalaLucky.PUMPKIN_BLUR)
                && !player.getItemBySlot(EquipmentSlot.HEAD).is(Blocks.CARVED_PUMPKIN.asItem())) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, PUMPKIN, 0, 0, 0.0f, 0.0f, width, height, 256, 256,
                    256, 256);
        }
        if (player.hasEffect(PalaLucky.GREEN)) {
            stretch(graphics, GREEN, width, height);
        }
        if (player.hasEffect(PalaLucky.FLOU)) {
            stretch(graphics, FLOU, width, height);
        }
        if (player.hasEffect(PalaLucky.NO_TEXTURE)) {
            int tile = 32;
            for (int x = 0; x < width; x += tile) {
                for (int y = 0; y < height; y += tile) {
                    graphics.blit(RenderPipelines.GUI_TEXTURED, MISSING, x, y, 0.0f, 0.0f, tile, tile, FILTER_SIZE,
                            FILTER_SIZE, FILTER_SIZE, FILTER_SIZE);
                }
            }
        }
        if (player.hasEffect(fr.atraxterra.dohy.palamod.lucky.SeasonEffects.STAR)) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, STARS, 0, 0, 0.0f, 0.0f, width, height, 512, 256, 512, 256);
        }
        if (player.hasEffect(fr.atraxterra.dohy.palamod.lucky.SeasonEffects.HACKED)) {
            // Les colonnes defilent : la texture se decale d'un pixel par tic.
            float scroll = (player.tickCount % 64) * 2.0f;
            int tile = 128;
            for (int x = 0; x < width; x += tile) {
                for (int y = -tile; y < height; y += tile) {
                    graphics.blit(RenderPipelines.GUI_TEXTURED, MATRIX, x, y + (int) scroll, 0.0f, 0.0f, tile, tile,
                            64, 64, 64, 64);
                }
            }
        }
        if (player.hasEffect(PalaLucky.CAT)) {
            int catWidth = width / 2;
            int catHeight = (int) (catWidth * CAT_RATIO);
            graphics.blit(RenderPipelines.GUI_TEXTURED, CAT, width / 2 - catWidth / 2, height / 2 - catHeight / 2,
                    0.0f, 0.0f, catWidth, catHeight, CAT_WIDTH, CAT_HEIGHT, CAT_WIDTH, CAT_HEIGHT);
        }
    }

    private static void stretch(GuiGraphicsExtractor graphics, Identifier texture, int width, int height) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0.0f, 0.0f, width, height, FILTER_SIZE,
                FILTER_SIZE, FILTER_SIZE, FILTER_SIZE);
    }
}

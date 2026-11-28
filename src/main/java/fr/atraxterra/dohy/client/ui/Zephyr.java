package fr.atraxterra.dohy.client.ui;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import fr.atraxterra.dohy.DoHy;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/**
 * Le moteur Zephyr du kit : les formes de l'interface dessinees au GPU, comme le
 * moteur de Zephyr-UI (ses shaders « rounded », « shadow » et « circle »), dans le
 * rendu retenu de la 26.2.
 *
 * <p>Zephyr-UI passait le rayon et le rectangle de chaque forme en uniformes, un appel
 * de dessin par forme. Ici, une forme est un element d'interface comme un autre
 * ({@link ZephyrShape}) : ses coins sont des quads dont l'UV porte la distance au coin
 * interieur, en rayons, et le shader en tire le bord, lisse sur un pixel physique a
 * toutes les echelles ({@code fwidth}). Pas de texture par rayon ; les formes voisines
 * partagent le meme pipeline, donc le meme appel de dessin ; un degrade est porte par
 * les couleurs des sommets.
 *
 * <ul>
 *   <li>{@link #ROUNDED} : rectangles arrondis, disques, anneaux et cadres ;</li>
 *   <li>{@link #SHADOW} : ombres douces et halos.</li>
 * </ul>
 *
 * <p>Si une carte graphique refusait les shaders, {@link #active()} le voit (la
 * compilation du pipeline n'est pas valide) et {@link Paint} reprend ses textures.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class Zephyr {

    public static final RenderPipeline ROUNDED = pipeline("zephyr_rounded");
    public static final RenderPipeline SHADOW = pipeline("zephyr_shadow");

    /** 1 : actif, 0 : refuse, -1 : pas encore verifie depuis le dernier rechargement. */
    private static int state = -1;
    private static boolean forcedOff;

    private Zephyr() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(DoHy.MOD_ID, path);
    }

    /** Le pipeline d'interface du jeu (GUI_SNIPPET), avec l'UV dans le format des sommets et nos shaders. */
    private static RenderPipeline pipeline(String fragment) {
        return RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                .withVertexShader(id("core/zephyr"))
                .withFragmentShader(id("core/" + fragment))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withLocation(id("pipeline/" + fragment))
                .build();
    }

    @SubscribeEvent
    public static void onRegisterPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(ROUNDED);
        event.registerPipeline(SHADOW);
    }

    /** Les shaders se relisent avec les ressources : on reverifie au prochain dessin. */
    @SubscribeEvent
    public static void onReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(id("zephyr"), (ResourceManagerReloadListener) manager -> state = -1);
    }

    /** Coupe le moteur (essais) : le kit repasse sur ses textures. */
    public static void force(boolean off) {
        forcedOff = off;
    }

    /**
     * Le moteur est utilisable : les deux pipelines se compilent sur cette carte. Verifie
     * une fois par rechargement des ressources, sur le fil de rendu.
     */
    public static boolean active() {
        if (forcedOff) {
            return false;
        }
        if (state < 0) {
            GpuDevice device = RenderSystem.tryGetDevice();
            if (device == null) {
                // Pas encore de carte graphique : on reessaiera au prochain dessin.
                return false;
            }
            state = compile(device) ? 1 : 0;
        }
        return state == 1;
    }

    private static boolean compile(GpuDevice device) {
        try {
            boolean valid = device.precompilePipeline(ROUNDED).isValid() && device.precompilePipeline(SHADOW).isValid();
            if (!valid) {
                DoHy.LOGGER.warn("Moteur Zephyr : shaders refuses par la carte graphique, le kit garde ses textures");
            }
            return valid;
        } catch (Throwable error) {
            DoHy.LOGGER.warn("Moteur Zephyr indisponible, le kit garde ses textures", error);
            return false;
        }
    }
}

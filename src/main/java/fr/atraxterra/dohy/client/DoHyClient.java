package fr.atraxterra.dohy.client;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.client.mount.DancarokGeometry;
import fr.atraxterra.dohy.client.mount.DancarokModel;
import fr.atraxterra.dohy.client.mount.DancarokRenderer;
import fr.atraxterra.dohy.client.ui.screen.KitChestScreen;
import fr.atraxterra.dohy.client.ui.screen.MachineDesigns;
import fr.atraxterra.dohy.client.ui.screen.MachineKitScreen;
import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.fluid.PalaFluids;
import fr.atraxterra.dohy.palamod.golem.PalaGolem;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import fr.atraxterra.dohy.palamod.machine.PalaMenus;
import fr.atraxterra.dohy.palamod.mount.PalaMount;
import fr.atraxterra.dohy.palamod.pvp.PalaPvp;
import fr.atraxterra.dohy.palamod.storage.ChestTier;
import fr.atraxterra.dohy.palamod.storage.PalaChestMenu;
import fr.atraxterra.dohy.palamod.storage.PalaStorage;
import fr.atraxterra.dohy.ui.KitMenus;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSource;

/**
 * Enregistrements cote client.
 *
 * <p>En 26.2, {@code @EventBusSubscriber} n'a plus ni {@code modid} ni
 * {@code bus} : le bus est deduit du type de l'evenement (un
 * {@code IModBusEvent} part sur le bus du mod), et seule la distribution reste
 * a preciser.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class DoHyClient {

    private DoHyClient() {
    }

    /**
     * L'eau angelique : en 26.2 un fluide se dessine par un modele de fluide
     * enregistre ici, plus par les textures de son type. Les textures portent
     * leur couleur : pas de teinte, comme la lave vanilla. Le voile vu a travers
     * le verre est celui de l'eau.
     */
    @SubscribeEvent
    public static void onRegisterFluidModels(RegisterFluidModelsEvent event) {
        event.register(new FluidModel.Unbaked(
                        new Material(Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "block/fluid/angelic_water_still")),
                        new Material(Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "block/fluid/angelic_water_flow")),
                        new Material(Identifier.withDefaultNamespace("block/water_overlay")),
                        (FluidTintSource) null),
                PalaFluids.ANGELIC_WATER, PalaFluids.FLOWING_ANGELIC_WATER);
    }

    /** Le golem gardien, sur le modele du golem de fer ; le Dancarok, sur le sien. */
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PalaGolem.GUARDIAN_GOLEM.get(), GuardianGolemRenderer::new);
        event.registerEntityRenderer(PalaMount.DANCAROK.get(), DancarokRenderer::new);
    }

    /** Le modele du Dancarok, converti de son fichier Bedrock. */
    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(DancarokModel.LAYER, DancarokGeometry::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "money"),
                new MoneyHudLayer());
    }

    /**
     * Associe chaque type de menu a son ecran.
     *
     * <p>Sans cette association, ouvrir la machine n'affiche rien et le joueur
     * reste bloque, souris captee : le serveur a bien ouvert le menu, mais le
     * client n'a pas d'ecran a mettre devant. Rien ne le signale au
     * chargement — c'est le genre de faute qui ne se voit qu'en jeu.
     *
     * <p>Le nom passe en dernier argument est celui de la texture, pas du menu.
     * Les deux coincident ici, et {@code tools/check_assets.py} le verifie.
     *
     * <p><b>Les parametres de type sont ecrits a la main</b>, et il le faut :
     * {@code register} declare {@code <M extends AbstractContainerMenu, U
     * extends Screen & MenuAccess<M>>}, et {@code U} ne se deduit que du type
     * de retour de la lambda — qui est lui-meme une expression a inferer. Sans
     * le temoin, javac rend « cannot infer type-variable(s) M,U ».
     */
    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        machine(event, PalaMenus.COBBLEBREAKER.get(), MachineDesigns.COBBLEBREAKER);
        machine(event, PalaMenus.CRUSHER.get(), MachineDesigns.CRUSHER);
        machine(event, PalaMenus.VOIDSTONE.get(), MachineDesigns.VOIDSTONE);
        machine(event, PalaMenus.PALAFURNACE.get(), MachineDesigns.PALAFURNACE);
        machine(event, PalaMenus.PALAFORGE.get(), MachineDesigns.PALAFORGE);
        machine(event, PalaMenus.GRINDER.get(), MachineDesigns.GRINDER);
        machine(event, PalaGolem.FOOD_MACHINE_MENU.get(), MachineDesigns.FOOD_MACHINE);
        machine(event, PalaGolem.GOLEM_BOX_MENU.get(), MachineDesigns.GOLEM_BOX);
        machine(event, PalaPvp.PALAMACHINE_MENU.get(), MachineDesigns.PALAMACHINE);
        for (KitMenus.Use use : KitMenus.Use.values()) {
            event.<ChestMenu, KitChestScreen>register(use.type(),
                    (menu, inventory, title) -> new KitChestScreen(menu, inventory, title, use));
        }
        for (ChestTier tier : ChestTier.values()) {
            event.<PalaChestMenu, PalaChestScreen>register(PalaStorage.menuOf(tier), PalaChestScreen::new);
        }
    }

    private static void machine(RegisterMenuScreensEvent event, MenuType<MachineMenu> type,
                                MachineKitScreen.Design design) {
        event.<MachineMenu, MachineKitScreen>register(type,
                (menu, inventory, title) -> new MachineKitScreen(menu, inventory, title, design));
    }
}

package fr.atraxterra.dohy.client;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import fr.atraxterra.dohy.palamod.machine.PalaMenus;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

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
        event.<MachineMenu, MachineScreen>register(PalaMenus.COBBLEBREAKER.get(),
                (menu, inventory, title) -> new MachineScreen(menu, inventory, title, "cobblebreaker"));
        event.<MachineMenu, MachineScreen>register(PalaMenus.CRUSHER.get(),
                (menu, inventory, title) -> new MachineScreen(menu, inventory, title, "crusher"));
    }
}

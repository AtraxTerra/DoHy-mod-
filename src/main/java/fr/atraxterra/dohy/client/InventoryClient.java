package fr.atraxterra.dohy.client;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.client.ui.screen.KitInventoryScreen;
import fr.atraxterra.dohy.client.ui.vanilla.KitChests;
import fr.atraxterra.dohy.client.ui.vanilla.KitPauseScreen;
import fr.atraxterra.dohy.client.ui.vanilla.KitStations;
import fr.atraxterra.dohy.client.ui.vanilla.KitTitleScreen;
import fr.atraxterra.dohy.client.ui.vanilla.KitWorkstations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BlastFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.client.gui.screens.inventory.SmokerScreen;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Remplace des ecrans du jeu par ceux du kit, a leur ouverture : l'ecran titre
 * ({@link KitTitleScreen}), le menu pause ({@link KitPauseScreen}), l'inventaire
 * ({@link KitInventoryScreen}), les coffres, la boite de shulker, l'entonnoir et le
 * distributeur ({@link KitChests}), l'etabli et les fours ({@link KitWorkstations}), la
 * table d'enchantement, l'enclume, l'alambic, la meule, la table de forgeron et le tailleur
 * de pierre ({@link KitStations}).
 *
 * <p>Le remplacement se fait a l'ouverture, donc pour toutes les facons d'ouvrir
 * l'ecran (touche, bloc, autre mod) ; le menu reste celui que le jeu a cree. On ne
 * remplace que la classe exacte du jeu : un ecran d'un autre mod qui en herite
 * garde le sien.
 *
 * <p>L'inventaire seulement hors du mode creatif : l'ecran du jeu y bascule de
 * lui-meme sur l'inventaire creatif, qu'on garde tel quel.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class InventoryClient {

    private InventoryClient() {
    }

    @SubscribeEvent
    public static void onOpening(ScreenEvent.Opening event) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = event.getNewScreen();
        if (screen != null && screen.getClass() == TitleScreen.class) {
            // L'ecran titre s'ouvre a la fin du chargement des ressources : une erreur ici
            // ferait echouer le chargement et bloquerait le jeu. On garde alors celui du jeu.
            try {
                event.setNewScreen(new KitTitleScreen());
            } catch (RuntimeException e) {
                DoHy.LOGGER.error("Ecran titre de Berskar indisponible, on garde celui du jeu", e);
            }
            return;
        }
        if (minecraft.player == null) {
            return;
        }
        if (screen != null && screen.getClass() == PauseScreen.class && ((PauseScreen) screen).showsPauseMenu()) {
            event.setNewScreen(new KitPauseScreen());
            return;
        }
        Screen replacement = replace(screen, minecraft);
        if (replacement != null) {
            event.setNewScreen(replacement);
        }
    }

    private static Screen replace(Screen screen, Minecraft minecraft) {
        if (screen == null) {
            return null;
        }
        Inventory inventory = minecraft.player.getInventory();
        Class<?> type = screen.getClass();
        if (type == InventoryScreen.class) {
            return minecraft.player.isCreative() ? null : new KitInventoryScreen(minecraft.player);
        }
        if (type == ContainerScreen.class) {
            return new KitChests.Chest(((ContainerScreen) screen).getMenu(), inventory, screen.getTitle());
        }
        if (type == ShulkerBoxScreen.class) {
            return new KitChests.Shulker(((ShulkerBoxScreen) screen).getMenu(), inventory, screen.getTitle());
        }
        if (type == HopperScreen.class) {
            return new KitChests.Hopper(((HopperScreen) screen).getMenu(), inventory, screen.getTitle());
        }
        if (type == DispenserScreen.class) {
            return new KitChests.Dispenser(((DispenserScreen) screen).getMenu(), inventory, screen.getTitle());
        }
        if (type == CraftingScreen.class) {
            return new KitWorkstations.Crafting(((CraftingScreen) screen).getMenu(), inventory, screen.getTitle());
        }
        if (type == FurnaceScreen.class) {
            return new KitWorkstations.Furnace(((FurnaceScreen) screen).getMenu(), inventory, screen.getTitle());
        }
        if (type == BlastFurnaceScreen.class) {
            return new KitWorkstations.Blast(((BlastFurnaceScreen) screen).getMenu(), inventory, screen.getTitle());
        }
        if (type == SmokerScreen.class) {
            return new KitWorkstations.Smoker(((SmokerScreen) screen).getMenu(), inventory, screen.getTitle());
        }
        if (type == EnchantmentScreen.class) {
            return new KitStations.Enchanting(((EnchantmentScreen) screen).getMenu(), inventory, screen.getTitle());
        }
        if (type == AnvilScreen.class) {
            return new KitStations.Anvil(((AnvilScreen) screen).getMenu(), inventory, screen.getTitle());
        }
        if (type == BrewingStandScreen.class) {
            return new KitStations.Brewing(((BrewingStandScreen) screen).getMenu(), inventory, screen.getTitle());
        }
        if (type == GrindstoneScreen.class) {
            return new KitStations.Grindstone(((GrindstoneScreen) screen).getMenu(), inventory, screen.getTitle());
        }
        if (type == SmithingScreen.class) {
            return new KitStations.Smithing(((SmithingScreen) screen).getMenu(), inventory, screen.getTitle());
        }
        if (type == StonecutterScreen.class) {
            return new KitStations.Stonecutter(((StonecutterScreen) screen).getMenu(), inventory, screen.getTitle());
        }
        return null;
    }
}

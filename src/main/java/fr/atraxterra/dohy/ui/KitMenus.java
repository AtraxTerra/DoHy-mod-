package fr.atraxterra.dohy.ui;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Les inventaires en coffre de DoHy, chacun sous son propre type de menu.
 *
 * <p>Avec les types vanilla ({@code GENERIC_9x3}...), le client ouvrirait
 * l'ecran de coffre du jeu, le meme que pour un coffre en bois. Un type a nous
 * par usage permet d'ouvrir l'ecran du kit, avec le titre et la consigne qui
 * vont : « vendu a la fermeture » pour le coffre de vente, par exemple. Le
 * menu reste un {@link ChestMenu} : cases, clics et shift-clics du jeu.
 */
public final class KitMenus {

    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, DoHy.MOD_ID);

    /** Un usage : son nombre de rangees, et la consigne affichee par l'ecran. */
    public enum Use {
        SELL_CHEST("sell_chest", 6, "Vendu a la fermeture"),
        MUSEUM_DEPOSIT("museum_deposit", 3, "Expose a la fermeture"),
        MOUNT_INVENTORY("mount_inventory", 5, "Le sac de ta monture"),
        GOLEM_STORAGE("golem_storage", 2, "Le coffre du golem"),
        GOLEM_KEEPER("golem_keeper", 1, "Les croquettes du gardien"),
        PALADIUM_ENDER_CHEST("paladium_ender_chest", 4, "Partage entre tes coffres");

        private final String path;
        private final int rows;
        private final String hint;
        private DeferredHolder<MenuType<?>, MenuType<ChestMenu>> holder;

        Use(String path, int rows, String hint) {
            this.path = path;
            this.rows = rows;
            this.hint = hint;
        }

        public int rows() {
            return rows;
        }

        public String hint() {
            return hint;
        }

        public MenuType<ChestMenu> type() {
            return holder.get();
        }
    }

    static {
        for (Use use : Use.values()) {
            Supplier<MenuType<ChestMenu>> factory = () -> new MenuType<>(
                    (containerId, inventory) -> new ChestMenu(use.type(), containerId, inventory,
                            new SimpleContainer(use.rows * 9), use.rows),
                    FeatureFlags.VANILLA_SET);
            use.holder = MENUS.register(use.path, factory);
        }
    }

    private KitMenus() {
    }

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}

package fr.atraxterra.dohy.palamod.machine;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Les types de menu des machines.
 *
 * <p>Un type par machine, parce que c'est lui que le client associe a un ecran.
 * Le reste est partage : {@link MachineMenu} est generique et ne differe que
 * par sa {@link MachineLayout}.
 *
 * <p>La fabrique se reference elle-meme ({@code COBBLEBREAKER.get()} a
 * l'interieur de la lambda qui definit {@code COBBLEBREAKER}) : c'est permis
 * parce que la lambda n'est appelee qu'a l'ouverture d'un menu, longtemps apres
 * l'enregistrement. L'ecrire autrement demanderait de passer le type en
 * argument apres coup.
 */
public final class PalaMenus {

    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, PalaMod.NAMESPACE);

    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> COBBLEBREAKER =
            MENUS.register("cobblebreaker", () -> new MenuType<>(
                    (containerId, inventory) -> new MachineMenu(
                            PalaMenus.COBBLEBREAKER.get(), containerId, inventory,
                            MachineLayout.COBBLEBREAKER),
                    FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> CRUSHER =
            MENUS.register("crusher", () -> new MenuType<>(
                    (containerId, inventory) -> new MachineMenu(
                            PalaMenus.CRUSHER.get(), containerId, inventory,
                            MachineLayout.CRUSHER),
                    FeatureFlags.VANILLA_SET));

    /**
     * La voidstone n'a pas de block entity : son menu s'ouvre depuis un objet.
     * Cote client il ne differe des autres que par sa disposition.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> VOIDSTONE =
            MENUS.register("voidstone", () -> new MenuType<>(
                    (containerId, inventory) -> new MachineMenu(
                            PalaMenus.VOIDSTONE.get(), containerId, inventory,
                            MachineLayout.VOIDSTONE),
                    FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> PALAFURNACE =
            MENUS.register("palafurnace", () -> new MenuType<>(
                    (containerId, inventory) -> new MachineMenu(
                            PalaMenus.PALAFURNACE.get(), containerId, inventory,
                            MachineLayout.PALAFURNACE),
                    FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> PALAFORGE =
            MENUS.register("palaforge", () -> new MenuType<>(
                    (containerId, inventory) -> new MachineMenu(
                            PalaMenus.PALAFORGE.get(), containerId, inventory,
                            MachineLayout.PALAFORGE),
                    FeatureFlags.VANILLA_SET));

    private PalaMenus() {
    }

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}

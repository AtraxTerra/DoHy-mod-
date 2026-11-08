package fr.atraxterra.dohy.palamod;

import fr.atraxterra.dohy.palamod.farming.PalaSeedPlanters;
import fr.atraxterra.dohy.palamod.machine.PalaMachines;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * L'onglet creatif du contenu PalaMod.
 *
 * <p>Un onglet propre plutot qu'un ajout aux onglets vanilla : trente-sept
 * entrees eparpillees dans « Materiaux naturels » et « Blocs de construction »
 * seraient introuvables, et le joueur qui cherche du paladium cherche « le
 * contenu Paladium », pas « un metal parmi les metaux ».
 *
 * <p>Le contenu est liste depuis {@link PalaItems#ORDERED} et
 * {@link PalaBlocks#ORDERED}, pas enumere ici : une seule liste a tenir, et un
 * item ajoute apparait dans l'onglet sans qu'on y pense. L'oubli inverse — un
 * item enregistre, introuvable en creatif — est exactement le genre de trou qui
 * ne se voit qu'en jeu.
 *
 * <p>L'icone est un {@link Supplier} : construire un {@code ItemStack} au
 * chargement de la classe leverait {@code Components not bound yet}, les
 * composants de donnees n'etant pas encore lies a ce moment.
 */
public final class PalaTab {

    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PalaMod.NAMESPACE);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MATERIALS =
            TABS.register("materials", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + PalaMod.NAMESPACE + ".materials"))
                    .icon(() -> new ItemStack(PalaItems.PALADIUM_INGOT.get()))
                    .displayItems((parameters, output) -> {
                        PalaBlocks.ORDERED.forEach(block -> output.accept(block.get()));
                        PalaItems.ORDERED.forEach(item -> output.accept(item.get()));
                        // Graines et fruits, mais PAS les blocs de culture : on
                        // obtient une culture en semant, comme le ble vanilla,
                        // dont le bloc n'est pas dans l'inventaire non plus.
                        PalaItems.CROP_ITEMS.forEach(item -> output.accept(item.get()));
                        // Les machines et leurs ameliorations ferment l'onglet :
                        // c'est la fin de la progression, apres les matieres et
                        // les cultures qui les alimentent.
                        PalaMachines.ORDERED_BLOCKS.forEach(block -> output.accept(block.get()));
                        PalaMachines.ORDERED_ITEMS.forEach(item -> output.accept(item.get()));

                        // Les planteurs ferment l'onglet : ce sont des outils de
                        // champ, ils suivent les machines plutot que les graines.
                        PalaSeedPlanters.ORDERED.forEach(item -> output.accept(item.get()));
                    })
                    .build());

    private PalaTab() {
    }

    static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}

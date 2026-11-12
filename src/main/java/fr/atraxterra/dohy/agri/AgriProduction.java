package fr.atraxterra.dohy.agri;

import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.contract.ContractType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Ce qu'une machine de l'agriculture vient de produire, rapporte aux contrats.
 *
 * <p>Une farine moulue ou un pain cuit fait avancer les contrats
 * {@link ContractType#PRODUCE} du joueur, que la machine ait travaille sous sa
 * main ou seule : dans le second cas c'est celui qui l'a posee qui en profite,
 * s'il est en jeu. L'XP de Fermier, elle, ne vient que du travail a la main
 * ({@link MillMenu}, {@link BreadMachineMenu}) : une chaine automatique ne doit
 * pas faire monter un metier pendant que le joueur dort.
 */
public final class AgriProduction {

    private AgriProduction() {
    }

    public static void produced(ServerPlayer player, Item item, int count) {
        if (count <= 0) {
            return;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        ItemStack stack = new ItemStack(item);
        ContractApi.progress(player, ContractType.PRODUCE, target -> {
            if (!target.startsWith("#")) {
                return target.equals(id.toString());
            }
            Identifier tag = Identifier.tryParse(target.substring(1));
            return tag != null && stack.is(TagKey.create(Registries.ITEM, tag));
        }, count);
    }
}

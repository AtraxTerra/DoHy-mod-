package fr.atraxterra.dohy.palamod.press;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Le prix en niveaux d'une gravure a la table d'ecriture
 * ({@code TileEntityTypeMachine.addBookorPlate}, PalaMod 10.3.0).
 *
 * <p>La formule d'origine, telle que la lit le bytecode : pour chaque
 * enchantement du livre, un multiplicateur tire de sa rarete (le poids de
 * 1.7.10 : 1 donne 8, 2 donne 4, 3 donne 10, 4 donne 0,5, 5 donne 2, 10 donne 1,
 * tout autre poids 2), fois deux, fois le niveau ; on ajoute dix, et
 * {@code n (n + 1) / 2} pour n enchantements ; le tout plafonne a 50.
 *
 * <p>Les poids sont ceux du jeu (la rarete de chaque enchantement de 26.2), a deux
 * exceptions pres qui recalent le tableau du wiki : les enchantements de
 * PalaMod comptent comme un poids 5 (15 niveaux pour un niveau I, le prix du
 * wiki) et Frost Walker comme un poids 1 (27 et 43 niveaux, ceux du wiki).
 */
public final class TypesetCost {

    /** Le plafond d'origine : {@code Math.min(50, ...)}. */
    public static final int MAX = 50;

    private static final Identifier FROST_WALKER = Identifier.withDefaultNamespace("frost_walker");

    private TypesetCost() {
    }

    /** Le prix d'un livre enchante, ou 0 s'il ne porte aucun enchantement. */
    public static int levels(ItemStack book) {
        ItemEnchantments stored = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (stored.isEmpty()) {
            return 0;
        }
        float total = 0.0f;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : stored.entrySet()) {
            total += multiplier(weight(entry.getKey())) * 2.0f * entry.getIntValue();
        }
        int count = stored.size();
        total += count * (count + 1) / 2 + 10;
        return Math.min(MAX, (int) total);
    }

    /** Le poids de rarete retenu pour un enchantement. */
    static int weight(Holder<Enchantment> enchantment) {
        Identifier id = enchantment.unwrapKey().map(key -> key.identifier()).orElse(null);
        if (id != null && id.getNamespace().equals("palamod")) {
            return 5;
        }
        if (FROST_WALKER.equals(id)) {
            return 1;
        }
        return enchantment.value().getWeight();
    }

    /** Le multiplicateur du {@code tableswitch} d'origine. */
    static float multiplier(int weight) {
        return switch (weight) {
            case 1 -> 8.0f;
            case 2 -> 4.0f;
            case 3 -> 10.0f;
            case 4 -> 0.5f;
            case 10 -> 1.0f;
            default -> 2.0f;
        };
    }
}

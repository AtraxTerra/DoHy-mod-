package fr.atraxterra.dohy.shop;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Les six onglets du shop.
 *
 * <p>La cle correspond au champ {@code category} du catalogue de prix, genere
 * par {@code tools/gen_prices.py}. Un item dont la categorie n'est reconnue par
 * aucun onglet n'apparait nulle part : c'est volontaire, ca rend visible une
 * faute de frappe dans {@code rates.csv} au lieu de la laisser passer.
 */
public enum ShopCategory {

    FOOD("FOOD", "Nourriture", Items.BREAD),
    FOUND("FOUND", "Trouvables", Items.DIAMOND),
    FARM("FARM", "Fermes auto", Items.WHEAT),
    MOB_DROP("MOB_DROP", "Drops de mobs", Items.ROTTEN_FLESH),
    COMPONENT("COMPONENT", "Composants", Items.CRAFTING_TABLE),
    NTM("NTM", "NTM NEXT", Items.ANVIL);

    private final String key;
    private final String display;
    private final Item icon;

    ShopCategory(String key, String display, Item icon) {
        this.key = key;
        this.display = display;
        this.icon = icon;
    }

    public String key() {
        return key;
    }

    public String display() {
        return display;
    }

    public Item icon() {
        return icon;
    }

    /** Recherche par cle de catalogue, insensible a la casse. */
    public static ShopCategory byKey(String key) {
        for (ShopCategory category : values()) {
            if (category.key.equalsIgnoreCase(key)) {
                return category;
            }
        }
        return null;
    }
}

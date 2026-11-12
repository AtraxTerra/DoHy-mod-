package fr.atraxterra.dohy.shop;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.function.Supplier;

/**
 * Les onglets du shop.
 *
 * <p>La cle correspond au champ {@code category} du catalogue de prix, genere
 * par {@code tools/gen_prices.py}. Un item dont la categorie n'est reconnue par
 * aucun onglet n'apparait nulle part : c'est volontaire, ca rend visible une
 * faute de frappe dans {@code rates.csv} au lieu de la laisser passer.
 *
 * <p>Il y avait un sixieme onglet, {@code COMPONENT}, qui n'a jamais eu
 * d'items : aucune ligne de {@code rates.csv} ne portait cette categorie et
 * aucune regle de tag ne la produisait. Un onglet vide est un bug visible, donc
 * il est retire, et la place qu'il occupait sert desormais a la boutique.
 *
 * <p>{@code PALAMOD} (chapitre economique du portage) regroupe les matieres de
 * PalaMod : minerais, lingots, cultures, fleurs, bois. Il a pris la case de la
 * boutique dans le pied du shop ; la boutique reste au menu principal. Son
 * icone est un objet du mod, donc cherchee dans le registre a l'usage : une
 * constante d'enum s'initialise avant que les objets du mod existent.
 *
 * <p>{@code AGRI} regroupe l'agriculture de DoHy : cereales, farines, pains,
 * produits d'elevage. C'est la seule categorie dont les prix suivent le cours
 * de l'heure ({@code HourlyCourse}).
 */
public enum ShopCategory {

    FOOD("FOOD", "Nourriture", () -> Items.BREAD),
    FOUND("FOUND", "Trouvables", () -> Items.DIAMOND),
    FARM("FARM", "Fermes auto", () -> Items.WHEAT),
    MOB_DROP("MOB_DROP", "Drops de mobs", () -> Items.ROTTEN_FLESH),
    PALAMOD("PALAMOD", "PalaMod", () -> BuiltInRegistries.ITEM
            .getOptional(Identifier.fromNamespaceAndPath("palamod", "paladium_ingot")).orElse(Items.NETHERITE_SCRAP)),
    NTM("NTM", "NTM NEXT", () -> Items.ANVIL),
    AGRI("AGRI", "Agriculture", () -> BuiltInRegistries.ITEM
            .getOptional(Identifier.fromNamespaceAndPath("dohy", "corn")).orElse(Items.WHEAT_SEEDS));

    private final String key;
    private final String display;
    private final Supplier<Item> icon;

    ShopCategory(String key, String display, Supplier<Item> icon) {
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
        return icon.get();
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

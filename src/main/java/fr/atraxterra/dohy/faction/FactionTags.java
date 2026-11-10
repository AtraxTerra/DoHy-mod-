package fr.atraxterra.dohy.faction;

import java.util.List;

/**
 * Les tags de faction : sur Paladium, Pilleur, Farmeur, PvP, Chill,
 * Tryhardeur... En solo, ils disent comment on joue, pas avec qui.
 */
public final class FactionTags {

    public static final int MAX = 2;

    public static final List<String> ALL = List.of("Farmeur", "Mineur", "Chasseur", "Alchimiste", "Batisseur",
            "Explorateur", "Donjons", "Commercant", "Collectionneur", "Pilleur", "Chill", "Tryhardeur");

    private FactionTags() {
    }
}

package fr.atraxterra.dohy.palamod.botanist;

import net.minecraft.util.StringRepresentable;

/**
 * Ou en est le pot : vide, puis les stades de JobsReborn
 * ({@code BotanistMutationStage} : SEED, BUD, BLOOMING, WILTED).
 *
 * <p>C'est aussi une propriete du bloc ({@link BotanistPotBlock#STAGE}) : le modele
 * de la plante se choisit dessus, sans rendu d'entite.
 */
public enum PotStage implements StringRepresentable {
    EMPTY("empty"),
    /** La graine vient d'etre plantee : une pousse. */
    SEED("seed"),
    /** Le bourgeon : la plante grandit, et refleurit apres chaque recolte. */
    BUD("bud"),
    /** En fleur : elle se recolte d'un clic droit. */
    BLOOMING("blooming"),
    /** Fanee : un clic droit rend des graines, avant qu'elle ne disparaisse. */
    WILTED("wilted");

    private final String id;

    PotStage(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** Les deux stades ou la plante pousse, et donc ou elle reclame. */
    public boolean growing() {
        return this == SEED || this == BUD;
    }

    public static PotStage byId(String id) {
        for (PotStage stage : values()) {
            if (stage.id.equals(id)) {
                return stage;
            }
        }
        return EMPTY;
    }
}

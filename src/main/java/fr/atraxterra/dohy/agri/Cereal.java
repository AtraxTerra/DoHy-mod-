package fr.atraxterra.dohy.agri;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;

/**
 * Les cereales de DoHy. Le ble de Minecraft est la dixieme : il n'est pas
 * redeclare ici, mais il se moud, nourrit la mangeoire et pousse partout.
 *
 * <p>Six cereales naturelles, deux par climat ({@link Climate}) : on trouve
 * leurs semences dans l'herbe de leur climat, et elles ne poussent que la.
 * Trois OGM, qui poussent partout mais ne rendent <b>jamais de semence</b> :
 * leurs graines sortent de la machine a OGM, et il faut y retourner a chaque
 * recolte.
 *
 * @see GmoResearch
 */
public enum Cereal {
    CORN("corn", "Mais", Climate.TEMPERATE),
    BARLEY("barley", "Orge", Climate.TEMPERATE),
    OAT("oat", "Avoine", Climate.COLD),
    RYE("rye", "Seigle", Climate.COLD),
    SUNFLOWER("sunflower", "Tournesol", Climate.HOT),
    SOY("soy", "Soja", Climate.HOT),
    FONIO("fonio", "Fonio", null),
    SORGHUM("sorghum", "Sorgho", null),
    KAMUT("kamut", "Kamut", null);

    private final String key;
    private final String label;
    private final Climate climate;

    Cereal(String key, String label, Climate climate) {
        this.key = key;
        this.label = label;
        this.climate = climate;
    }

    /** Le nom du grain ; la culture est {@code <key>_crop}, la semence {@code <key>_seeds}. */
    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    /** Un OGM : pas de climat, pas de semence a la recolte. */
    public boolean gmo() {
        return climate == null;
    }

    /** Le climat ou la cereale pousse ; {@code null} pour un OGM, qui pousse partout. */
    public Climate climate() {
        return climate;
    }

    /** La part des ticks de croissance que la culture accepte : un OGM pousse moins vite. */
    public float growth() {
        return gmo() ? 0.6f : 1.0f;
    }

    /** Vrai si la cereale pousse a cet endroit. */
    public boolean growsAt(LevelReader level, BlockPos pos) {
        return climate == null || Climate.at(level, pos) == climate;
    }

    /** La cereale d'une cle, ou {@code null} ({@code wheat} n'en est pas une ici). */
    public static Cereal byKey(String key) {
        for (Cereal cereal : values()) {
            if (cereal.key.equals(key)) {
                return cereal;
            }
        }
        return null;
    }
}

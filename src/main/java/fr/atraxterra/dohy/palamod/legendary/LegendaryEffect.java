package fr.atraxterra.dohy.palamod.legendary;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Les sept pierres legendaires ({@code LegendaryStone$Effect}).
 *
 * <p>L'ordre est celui de l'enumeration d'origine : c'est aussi celui dans
 * lequel le gant fait defiler ses pierres. La couleur est celle des particules
 * ({@code LengendaryStoneFx}), relevee en flottants et convertie.
 */
public enum LegendaryEffect implements StringRepresentable {

    RANDOM("random", 0xFFFFFF),
    TELEPORTATION("teleportation", 0x80E0B3),
    INVISIBILITY("invisibility", 0xCCCCCC),
    FORTUNE("fortune", 0xFFE008),
    POWER("power", 0xCC1433),
    JOBS("jobs", 0x4DB333),
    CHAOS("chaos", 0x8014FF);

    public static final Codec<LegendaryEffect> CODEC = StringRepresentable.fromEnum(LegendaryEffect::values);

    private final String key;
    private final int color;

    LegendaryEffect(String key, int color) {
        this.key = key;
        this.color = color;
    }

    public String key() {
        return key;
    }

    public int color() {
        return color;
    }

    /**
     * Teleportation et chaos ont en plus un verrou de 10 minutes par joueur,
     * commun a toutes les pierres de ce type ({@code legendary_<EFFET>} dans les
     * donnees du joueur). Le verrou d'une heure des quatre autres n'etait
     * jamais ecrit dans l'original : il ne bloquait rien, et n'est pas porte.
     */
    public boolean playerLocked() {
        return this == TELEPORTATION || this == CHAOS;
    }

    /** Le nom court, celui de l'infobulle du gant et des messages. */
    public String nameKey() {
        return "legendary.palamod." + key;
    }

    @Override
    public String getSerializedName() {
        return key;
    }
}

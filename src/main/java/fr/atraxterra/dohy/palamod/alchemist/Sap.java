package fr.atraxterra.dohy.palamod.alchemist;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * Les quatre seves, une par arbre de PalaMod.
 *
 * <p>Releve dans {@code TileEntityExtractor} (le type vient du nom de la buche
 * vidée), {@code ExtractSeveObjective} et {@code AlchemistRegisterer} (l'XP
 * d'extraction). Une fiole pleine vaut <b>1 000 unites</b>, le volume d'un seau :
 * c'est l'unite des tanks et des portails.
 *
 * <p>XP d'extraction de PalaJobs, par fiole pleine (au prorata du remplissage) :
 * 60 pour le jacaranda des le niveau 0, 120 pour la judée au niveau 25, 240
 * pour l'érable au niveau 50 (niveaux d'ici, ceux du code x 5). <b>L'ostrya
 * n'en donne aucune</b> : {@code ExtractSeveObjective} n'a pas d'entree pour
 * lui. C'est l'original, garde tel quel.
 */
public enum Sap implements StringRepresentable {
    JACARANDA("jacaranda", ChatFormatting.LIGHT_PURPLE, 60.0d, 0),
    JUDEECERCIS("judeecercis", ChatFormatting.RED, 120.0d, 25),
    ERABLE("erable", ChatFormatting.GOLD, 240.0d, 50),
    OSTRYA("ostrya", ChatFormatting.DARK_AQUA, 0.0d, 0);

    /** Une fiole pleine, un seau : l'unite de toute la chaine. */
    public static final int FLASK_UNITS = 1000;

    public static final Codec<Sap> CODEC = StringRepresentable.fromEnum(Sap::values);
    public static final StreamCodec<ByteBuf, Sap> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(index -> values()[index], Sap::ordinal);

    private final String id;
    private final ChatFormatting color;
    private final double extractionXp;
    private final int extractionLevel;

    Sap(String id, ChatFormatting color, double extractionXp, int extractionLevel) {
        this.id = id;
        this.color = color;
        this.extractionXp = extractionXp;
        this.extractionLevel = extractionLevel;
    }

    public String id() {
        return id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** XP de PalaJobs pour une fiole pleine extraite. */
    public double extractionXp() {
        return extractionXp;
    }

    /** Niveau d'Alchimiste sous lequel l'extraction ne rapporte rien. */
    public int extractionLevel() {
        return extractionLevel;
    }

    public Component displayName() {
        return Component.translatable("sap.palamod." + id).withStyle(color);
    }

    /**
     * Les unites d'une fiole remplie a {@code level} sixiemes :
     * {@code (int)(meta / 6.0 * 100) * 10}, soit 0, 160, 330, 500, 660, 830, 1 000.
     */
    public static int units(int level) {
        return (int) (level / 6.0d * 100.0d) * 10;
    }
}

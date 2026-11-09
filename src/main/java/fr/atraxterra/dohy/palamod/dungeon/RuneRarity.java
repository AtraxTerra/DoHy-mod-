package fr.atraxterra.dohy.palamod.dungeon;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.util.StringRepresentable;

/**
 * La rarete d'une rune (wiki : « Rune commune », « Rune rare », « Rune
 * epique »). Le wiki melange rarete et tier (« runes de tier Commun ») ; ici
 * ce sont deux axes : la rarete fixe le nombre de lignes et leur force, le
 * tier (1 a 4, a l'enclume) les fait monter.
 *
 * <p>« Une rune a 2 statistiques aleatoires, sauf les communes qui peuvent
 * n'en avoir qu'une » (wiki).
 */
public enum RuneRarity implements StringRepresentable {

    COMMON("common", "commune", 1.0f, ChatFormatting.WHITE),
    RARE("rare", "rare", 1.5f, ChatFormatting.AQUA),
    EPIC("epic", "epique", 2.0f, ChatFormatting.LIGHT_PURPLE);

    public static final Codec<RuneRarity> CODEC = StringRepresentable.fromEnum(RuneRarity::values);

    private final String key;
    private final String label;
    private final float multiplier;
    private final ChatFormatting color;

    RuneRarity(String key, String label, float multiplier, ChatFormatting color) {
        this.key = key;
        this.label = label;
        this.multiplier = multiplier;
        this.color = color;
    }

    @Override
    public String getSerializedName() {
        return key;
    }

    public String label() {
        return label;
    }

    public float multiplier() {
        return multiplier;
    }

    public ChatFormatting color() {
        return color;
    }
}

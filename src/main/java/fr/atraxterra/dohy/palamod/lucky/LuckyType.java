package fr.atraxterra.dohy.palamod.lucky;

import net.minecraft.world.item.ItemStack;

/**
 * Les lucky blocks de PalaMod ({@code LuckyType} et {@code LuckyType.Util}).
 *
 * <p>Ce que dit le code d'origine : chaque type a sa couleur, son bloc et son
 * poids dans le tirage du Lucky Pass ({@code getRandomType}) : paladium 4000,
 * findium 3500, endium 500, sur un total de 10 000. Les 2 000 restants vont aux
 * lucky blocks saisonniers (noir 300, les dix mois 170 chacun), pas encore
 * portes : en attendant, les trois types se partagent le tirage dans les memes
 * proportions.
 *
 * <p>Les evenements d'un type ({@code valuesByType}) : par defaut un evenement
 * sort du lucky block en paladium, et aussi de celui en endium s'il est bon. Le
 * findium a ses propres evenements, qui ne sortent que de lui.
 *
 * @param key    le nom du bloc, sans {@code _lucky_block}
 * @param label  le nom affiche, en capitales sur l'ecran
 * @param color  la couleur de {@code LuckyType.Util} (#D33C3C, #4765D7, #F1E12C)
 * @param weight le poids dans le tirage du Lucky Pass
 */
public enum LuckyType {
    PALADIUM("paladium", "PALADIUM", 0xFFD33C3C, 4000),
    ENDIUM("endium", "ENDIUM", 0xFF4765D7, 500),
    FINDIUM("findium", "FINDIUM", 0xFFF1E12C, 3500);

    private final String key;
    private final String label;
    private final int color;
    private final int weight;

    LuckyType(String key, String label, int color, int weight) {
        this.key = key;
        this.label = label;
        this.color = color;
        this.weight = weight;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public int color() {
        return color;
    }

    public int weight() {
        return weight;
    }

    /** Un lucky block de ce type, a poser. */
    public ItemStack stack(int count) {
        return new ItemStack(PalaLucky.item(this).get(), count);
    }

    /** Le type d'apres son numero, paladium par defaut. */
    public static LuckyType of(int ordinal) {
        LuckyType[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : PALADIUM;
    }

    /** Le tirage du Lucky Pass, au poids de chaque type. */
    public static LuckyType draw(net.minecraft.util.RandomSource random) {
        int total = 0;
        for (LuckyType type : values()) {
            total += type.weight;
        }
        int roll = random.nextInt(total);
        for (LuckyType type : values()) {
            roll -= type.weight;
            if (roll < 0) {
                return type;
            }
        }
        return PALADIUM;
    }
}

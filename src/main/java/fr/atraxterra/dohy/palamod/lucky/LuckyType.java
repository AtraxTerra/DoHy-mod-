package fr.atraxterra.dohy.palamod.lucky;

import net.minecraft.world.item.ItemStack;

/**
 * Les lucky blocks de PalaMod ({@code LuckyType} et {@code LuckyType.Util}).
 *
 * <p>Ce que dit le code d'origine : chaque type a sa couleur, son bloc et son
 * poids dans le tirage du Lucky Pass ({@code getRandomType}) : paladium 4000,
 * findium 3500, endium 500, noir 300 et chacun des dix lucky blocks du mois 170,
 * sur un total de 10 000.
 *
 * <p>Les evenements d'un type ({@code valuesByType}) : par defaut un evenement
 * sort du lucky block en paladium, et aussi de celui en endium s'il est bon. Le
 * findium a ses propres evenements, qui ne sortent que de lui ; chaque lucky
 * block saisonnier aussi ({@code monthly/modules} et {@code luckyevents/<mois>}).
 *
 * <p>Les types s'ajoutent toujours a la fin : leur numero est garde dans les
 * donnees des joueurs (le Lucky Pass, l'ecran du lucky block).
 *
 * @param key    le nom du bloc, sans {@code _lucky_block}
 * @param label  le nom affiche, en capitales sur l'ecran
 * @param color  la couleur de {@code LuckyType.Util} (#D33C3C, #4765D7, #F1E12C...)
 * @param weight le poids dans le tirage du Lucky Pass
 */
public enum LuckyType {
    PALADIUM("paladium", "PALADIUM", 0xFFD33C3C, 4000),
    ENDIUM("endium", "ENDIUM", 0xFF4765D7, 500),
    FINDIUM("findium", "FINDIUM", 0xFFF1E12C, 3500),
    /** Le LuckyBlock Noir : sa couleur est le blanc ({@code Color.WHITE}). */
    BLACK("black", "NOIR", 0xFFFFFFFF, 300),
    MARCH("march", "DE MARS", 0xFFAE533F, 170),
    EASTER("easter", "DE PAQUES", 0xFFE57C45, 170),
    MAY("may", "DE MAI", 0xFF5D2B34, 170),
    JUNE("june", "DE JUIN", 0xFF0F5D70, 170),
    JULY("july", "DE JUILLET", 0xFFDFA270, 170),
    AUGUST("august", "D'AOUT", 0xFF22A5FF, 170),
    SEPTEMBER("september", "DE SEPTEMBRE", 0xFFF9EA8D, 170),
    HALLOWEEN("halloween", "D'HALLOWEEN", 0xFFF18E0C, 170),
    NOVEMBER("november", "DE NOVEMBRE", 0xFF500C6E, 170),
    CHRISTMAS("christmas", "DE NOEL", 0xFF2BA554, 170);

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

    /** Le nom court, pour les listes (« NOEL », « AOUT ») : sans le « DE » de l'ecran. */
    public String shortLabel() {
        return label.startsWith("DE ") ? label.substring(3) : label.startsWith("D'") ? label.substring(2) : label;
    }

    public int color() {
        return color;
    }

    public int weight() {
        return weight;
    }

    /** Un lucky block du mois (ou le noir) : ses evenements ne sortent que de lui. */
    public boolean seasonal() {
        return ordinal() > FINDIUM.ordinal();
    }

    /**
     * La couleur pour l'ecran : celle du type, eclaircie quand elle est trop
     * sombre pour se lire sur le fond (le violet de novembre, le bordeaux de mai).
     */
    public int accent() {
        int r = (color >> 16) & 255;
        int g = (color >> 8) & 255;
        int b = color & 255;
        double luma = (0.299d * r + 0.587d * g + 0.114d * b) / 255.0d;
        if (luma >= 0.45d) {
            return color;
        }
        double mix = (0.45d - luma) / (1.0d - luma);
        return 0xFF000000 | (int) (r + (255 - r) * mix) << 16 | (int) (g + (255 - g) * mix) << 8
                | (int) (b + (255 - b) * mix);
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

package fr.atraxterra.dohy.palamod.dungeon;

import net.minecraft.ChatFormatting;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Les trois modes d'un palier, choisis a l'entree : Facile, Normal, Difficile.
 *
 * <p>Le wiki de la V12 ne donne aucun chiffre ; tout est de DoHy. Le Normal
 * est le donjon d'avant les modes, inchange : la progression deja faite y
 * compte. Le Facile adoucit tout et paie moins ; le Difficile durcit tout,
 * paie davantage, et ses boss des paliers en 0 sont des boss « Dark ».
 *
 * <p><b>Progression.</b> Normal et Difficile partagent la progression : un
 * palier reussi dans l'un ouvre le suivant dans les deux. Le Facile a la
 * sienne, que les deux autres font aussi avancer. Le meilleur palier reussi
 * en Difficile est garde a part, pour l'ecran des paliers.
 *
 * @param suffix      la cle de progression : {@code donjon} pour le Normal,
 *                    {@code donjon:facile}, {@code donjon:difficile}
 * @param mobHealth   points de vie des monstres, facteur
 * @param mobDamage   degats des monstres et des boss, facteur
 * @param extraMobs   monstres de plus par vague (moins pour le Facile)
 * @param bossHealth  points de vie des mini-boss et des boss, facteur
 * @param rewards     XP de metier et XP RPG, facteur
 * @param extraRolls  tirages de plus dans chaque coffre (moins pour le Facile)
 * @param unique      la chance de chaque recompense unique en fin de palier
 */
public enum DungeonMode {

    FACILE("facile", "Facile", ChatFormatting.GREEN, Items.WOODEN_SWORD, 0.6d, 0.6f, -1, 0.6d, 0.5d, -1, 0.25f),
    NORMAL("", "Normal", ChatFormatting.YELLOW, Items.IRON_SWORD, 1.0d, 1.0f, 0, 1.0d, 1.0d, 0, 0.5f),
    DIFFICILE("difficile", "Difficile", ChatFormatting.RED, Items.NETHERITE_SWORD, 1.5d, 1.35f, 1, 1.6d, 1.5d,
            1, 1.0f);

    private final String suffix;
    private final String label;
    private final ChatFormatting color;
    private final Item icon;
    private final double mobHealth;
    private final float mobDamage;
    private final int extraMobs;
    private final double bossHealth;
    private final double rewards;
    private final int extraRolls;
    private final float unique;

    DungeonMode(String suffix, String label, ChatFormatting color, Item icon, double mobHealth, float mobDamage,
                int extraMobs, double bossHealth, double rewards, int extraRolls, float unique) {
        this.suffix = suffix;
        this.label = label;
        this.color = color;
        this.icon = icon;
        this.mobHealth = mobHealth;
        this.mobDamage = mobDamage;
        this.extraMobs = extraMobs;
        this.bossHealth = bossHealth;
        this.rewards = rewards;
        this.extraRolls = extraRolls;
        this.unique = unique;
    }

    public String label() {
        return label;
    }

    public ChatFormatting color() {
        return color;
    }

    public Item icon() {
        return icon;
    }

    public double mobHealth() {
        return mobHealth;
    }

    public float mobDamage() {
        return mobDamage;
    }

    public int extraMobs() {
        return extraMobs;
    }

    public double bossHealth() {
        return bossHealth;
    }

    public double rewards() {
        return rewards;
    }

    public int extraRolls() {
        return extraRolls;
    }

    public float unique() {
        return unique;
    }

    /** Les boss des paliers en 0 sont-ils des boss Dark ? */
    public boolean darkBosses() {
        return this == DIFFICILE;
    }

    /** La cle de progression d'un donjon dans ce mode. */
    public String progressKey(Dungeon dungeon) {
        return suffix.isEmpty() ? dungeon.key() : dungeon.key() + ":" + suffix;
    }

    public static DungeonMode byName(String name) {
        for (DungeonMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name) || mode.label.equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return null;
    }
}

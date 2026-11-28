package fr.atraxterra.dohy.palamod.dungeon;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.function.Supplier;

/**
 * Les quatre branches de l'arbre de competences (wiki : « Attaque »,
 * « Esquive », « Critique », « Defense ») et les statistiques RPG qu'elles
 * donnent, celles des runes aussi.
 *
 * <p>Choix du joueur : elles ne jouent <b>qu'en donjon</b>, contre ses
 * monstres. Le wiki ne definit aucune des quatre ; ici :
 * <ul>
 *   <li>attaque : degats infliges en plus ;</li>
 *   <li>esquive : chance d'eviter entierement un coup ;</li>
 *   <li>critique : chance d'un coup a 150 % ;</li>
 *   <li>defense : degats recus en moins.</li>
 * </ul>
 *
 * @param nodes    ce que vaut chaque noeud de la branche, en points de pourcentage ({@link RpgTree})
 * @param runeBase ce que vaut une ligne de rune commune de tier 1
 * @param cap      le plafond, arbre et runes compris
 */
public enum RpgStat implements StringRepresentable {

    ATTACK("attack", "Attaque", ChatFormatting.RED, () -> Items.IRON_SWORD,
            new int[] {2, 2, 3, 3, 3, 3, 4, 4, 4, 4}, 0.02f, 1.0f),
    DODGE("dodge", "Esquive", ChatFormatting.AQUA, () -> Items.FEATHER,
            new int[] {1, 1, 1, 1, 2, 2, 2, 2, 2, 2}, 0.01f, 0.35f),
    CRIT("crit", "Critique", ChatFormatting.GOLD, () -> Items.BLAZE_ROD,
            new int[] {2, 2, 2, 2, 2, 2, 3, 3, 3, 3}, 0.015f, 0.60f),
    DEFENSE("defense", "Defense", ChatFormatting.GREEN, () -> Items.SHIELD,
            new int[] {2, 2, 2, 2, 2, 2, 3, 3, 3, 3}, 0.015f, 0.50f);

    public static final Codec<RpgStat> CODEC = StringRepresentable.fromEnum(RpgStat::values);

    private final String key;
    private final String label;
    private final ChatFormatting color;
    private final Supplier<Item> icon;
    private final int[] nodes;
    private final float runeBase;
    private final float cap;

    RpgStat(String key, String label, ChatFormatting color, Supplier<Item> icon, int[] nodes, float runeBase,
            float cap) {
        this.key = key;
        this.label = label;
        this.color = color;
        this.icon = icon;
        this.nodes = nodes;
        this.runeBase = runeBase;
        this.cap = cap;
    }

    @Override
    public String getSerializedName() {
        return key;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public ChatFormatting color() {
        return color;
    }

    public Item icon() {
        return icon.get();
    }

    /** Ce que vaut ce noeud, en points de pourcentage. */
    public int node(int index) {
        return nodes[index];
    }

    /** Ce que valent les noeuds de ce masque : 0,12 pour 12 %. */
    public float value(int mask) {
        int total = 0;
        for (int index = 0; index < RpgTree.NODES; index++) {
            if (RpgTree.has(mask, index)) {
                total += nodes[index];
            }
        }
        return total / 100.0f;
    }

    public float runeBase() {
        return runeBase;
    }

    public float cap() {
        return cap;
    }

    /** Un pourcentage lisible : 0,04 -> « 4 % », 0,015 -> « 1,5 % ». */
    public static String percent(float value) {
        float percent = value * 100.0f;
        return (Math.abs(percent - Math.round(percent)) < 0.05f
                ? String.valueOf(Math.round(percent))
                : String.format(java.util.Locale.FRANCE, "%.1f", percent)) + " %";
    }
}

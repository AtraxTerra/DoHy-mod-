package fr.atraxterra.dohy.palamod.fishing;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;

/**
 * Les tables de la peche de PalaJobs ({@code FishingRegistry}) : quatre
 * raretes, trois dispositions de barre par rarete, le butin de chacune.
 *
 * <p>Largeurs des zones (en % de la barre) et durees : celles de PalaJobs.
 * Chances de rarete selon la canne : DoHy (le joueur a choisi que la canne en
 * bois reste vanilla et que seule la canne en endium ouvre le mythique).
 * XP : celle du wiki (« Evolution du metier Chasseur »), au rapport 0,357 de
 * PALAMOD.md § 1 ; niveaux requis : ceux du wiki, multiplies par cinq.
 */
public final class FishingTable {

    private FishingTable() {
    }

    public enum Rarity {
        COMMON("common", "Commun", 0xFF5ED42A, 3000, 5.5f, 15f, 2.5f, 5, 20f, 1.9f),
        RARE("rare", "Rare", 0xFF3FA9F5, 2500, 5f, 15f, 2.25f, 5, 18f, 1.7f),
        LEGENDARY("legendary", "Legendaire", 0xFFFF5555, 2000, 4.5f, 12f, 2f, 3, 16f, 1.5f),
        MYTHIC("mythic", "Mythique", 0xFFD94BF0, 2000, 5f, 10f, 0f, 0, 0f, 0f);

        public final String key;
        public final String label;
        public final int color;
        public final int durationMs;
        /** Disposition simple : la zone x2 et la zone x1. */
        final float x2;
        final float x1;
        /** Disposition « Boost » : une zone de plus, {@code boost} fois le butin. */
        final float boostWidth;
        final int boost;
        final float boostX1;
        /** Disposition « Level-Up » : la zone jaune, qui ouvre la rarete suivante. */
        final float levelUp;

        Rarity(String key, String label, int color, int durationMs, float x2, float x1, float boostWidth,
               int boost, float boostX1, float levelUp) {
            this.key = key;
            this.label = label;
            this.color = color;
            this.durationMs = durationMs;
            this.x2 = x2;
            this.x1 = x1;
            this.boostWidth = boostWidth;
            this.boost = boost;
            this.boostX1 = boostX1;
            this.levelUp = levelUp;
        }

        public Rarity next() {
            return this == MYTHIC ? null : values()[ordinal() + 1];
        }
    }

    /** Couleurs des zones (PalaJobs : COLOR_X1, X2, X5, LVLUP). */
    static final int GREEN = 0xFF5ED42A;
    static final int RED = 0xFFFF3939;
    static final int PURPLE = 0xFFB04BFF;
    static final int YELLOW = 0xFFFFD439;

    /** Un butin : l'objet et son poids dans le tirage. */
    record Loot(String item, float weight) {
        Item resolve() {
            return BuiltInRegistries.ITEM.getValue(Identifier.parse(item));
        }
    }

    static final Map<Rarity, List<Loot>> LOOT = Map.of(
            Rarity.COMMON, List.of(new Loot("minecraft:cod", 55f), new Loot("minecraft:salmon", 25f),
                    new Loot("palamod:carp", 3.5f), new Loot("minecraft:leather", 1.09f),
                    new Loot("minecraft:leather_boots", 1.09f), new Loot("minecraft:rotten_flesh", 1.09f),
                    new Loot("minecraft:bowl", 1.09f), new Loot("minecraft:bone", 1.09f),
                    new Loot("minecraft:ink_sac", 1.09f), new Loot("minecraft:tripwire_hook", 1.09f),
                    new Loot("minecraft:glass_bottle", 1.09f), new Loot("minecraft:stick", 0.54f),
                    new Loot("minecraft:string", 0.54f), new Loot("minecraft:lily_pad", 0.8f),
                    new Loot("minecraft:bow", 0.8f), new Loot("minecraft:book", 0.8f),
                    new Loot("minecraft:name_tag", 0.8f), new Loot("minecraft:saddle", 0.8f)),
            Rarity.RARE, List.of(new Loot("minecraft:tropical_fish", 30f), new Loot("minecraft:pufferfish", 30f),
                    new Loot("palamod:carp", 10f), new Loot("palamod:bass", 5f), new Loot("palamod:ray", 1.5f),
                    new Loot("palamod:exp_fish", 1.6f), new Loot("minecraft:bow", 1.6f),
                    new Loot("minecraft:book", 1.6f), new Loot("minecraft:name_tag", 1.6f),
                    new Loot("minecraft:lily_pad", 1.6f), new Loot("minecraft:saddle", 1.09f)),
            Rarity.LEGENDARY, List.of(new Loot("palamod:exp_fish", 37f), new Loot("palamod:red_tuna", 37f),
                    new Loot("palamod:moonfish", 26f)),
            Rarity.MYTHIC, List.of(new Loot("palamod:whale", 65f), new Loot("palamod:kraken", 35f)));

    /** XP de Chasseur par poisson (wiki, x 0,357). */
    public static final Map<String, Long> XP = Map.ofEntries(
            Map.entry("minecraft:cod", 9L), Map.entry("minecraft:salmon", 12L),
            Map.entry("minecraft:tropical_fish", 71L), Map.entry("minecraft:pufferfish", 27L),
            Map.entry("palamod:carp", 54L), Map.entry("palamod:bass", 80L), Map.entry("palamod:ray", 107L),
            Map.entry("palamod:red_tuna", 161L), Map.entry("palamod:moonfish", 196L),
            Map.entry("palamod:exp_fish", 268L), Map.entry("palamod:whale", 3570L),
            Map.entry("palamod:kraken", 5355L));

    /** Niveau de Chasseur requis (wiki : 2, 3 et 4, soit 10, 15 et 20 chez DoHy). */
    public static final Map<String, Integer> LEVEL = Map.of(
            "palamod:carp", 10, "palamod:bass", 10, "palamod:ray", 10, "palamod:red_tuna", 10,
            "palamod:moonfish", 10, "palamod:exp_fish", 15, "palamod:whale", 20, "palamod:kraken", 20);

    /** La rarete de depart : canne en paladium ou en endium. */
    static Rarity roll(boolean endium, RandomSource random) {
        int pick = random.nextInt(100);
        if (endium) {
            return pick < 55 ? Rarity.COMMON : pick < 87 ? Rarity.RARE : pick < 97 ? Rarity.LEGENDARY : Rarity.MYTHIC;
        }
        return pick < 75 ? Rarity.COMMON : pick < 96 ? Rarity.RARE : Rarity.LEGENDARY;
    }

    /** Le butin d'une prise : tire au poids, sans les poissons au-dessus du niveau. */
    static Item loot(Rarity rarity, int level, RandomSource random) {
        List<Loot> pool = LOOT.get(rarity).stream()
                .filter(loot -> level >= LEVEL.getOrDefault(loot.item(), 0)).toList();
        if (pool.isEmpty()) {
            return Items.COD;
        }
        float total = 0f;
        for (Loot loot : pool) {
            total += loot.weight();
        }
        float pick = random.nextFloat() * total;
        for (Loot loot : pool) {
            pick -= loot.weight();
            if (pick <= 0f) {
                return loot.resolve();
            }
        }
        return pool.get(pool.size() - 1).resolve();
    }
}

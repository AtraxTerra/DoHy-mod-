package fr.atraxterra.dohy.palamod.dungeon;

import fr.atraxterra.dohy.palamod.alchemist.PalaAlchemist;
import fr.atraxterra.dohy.palamod.alchemist.Sap;
import fr.atraxterra.dohy.palamod.alchemist.TankSap;
import fr.atraxterra.dohy.palamod.alchemist.TankTier;
import fr.atraxterra.dohy.palamod.godpickaxe.GodPickaxeData;
import fr.atraxterra.dohy.palamod.godpickaxe.GodPickaxeLevels;
import fr.atraxterra.dohy.palamod.godpickaxe.PalaGodPickaxe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.IntPredicate;

/**
 * Le butin des coffres de salle (« Recompenses de Palier », « Recompenses de
 * Boss » et « Recompenses uniques » du wiki).
 *
 * <p>Le wiki dit, palier par palier, a partir d'ou chaque objet devient
 * possible, mais « chaque objet possede un taux de drop » sans en donner
 * aucun, ni aucune quantite. Les poids et les quantites sont donc de DoHy :
 * les objets courants pesent lourd, les machines et les objets rares peu.
 *
 * <ul>
 *   <li>un coffre de salle tire {@code 2 + palier / 50} fois dans la reserve
 *       du donjon (cumulative : tout ce qui est debloque au palier ou avant) ;
 *       les quantites croissent de {@code palier / 50} ;</li>
 *   <li>le coffre d'une salle de boss tire en plus une fois dans la table des
 *       boss, deux fois apres un boss (palier en 0) ;</li>
 *   <li>les recompenses uniques ont une chance sur deux de tomber, a la fin
 *       du palier exact.</li>
 * </ul>
 *
 * <p>Les parchemins de sort (les sorts sont laisses de cote, au choix du
 * joueur) et les parchemins de craft ne sont pas portes, ni les objets du
 * wiki qui ne sont ni dans PalaMod ni dans vanilla (Obsidienne infusee, Tete
 * de thirsty, Totem fleuri, Bonbons, Jetpack...). Une entree introuvable au registre est
 * ignoree, pas une erreur.
 */
final class DungeonRewards {

    /**
     * Une entree : son palier d'apparition, son poids, l'objet et une
     * quantite, et ce qu'il faut poser dessus.
     */
    private record Entry(IntPredicate available, int weight, String id, int min, int max,
                         BiConsumer<ServerLevel, ItemStack> extra) {

        static Entry of(int from, int weight, String id, int min, int max) {
            return new Entry(palier -> palier >= from, weight, id, min, max, null);
        }

        Entry with(BiConsumer<ServerLevel, ItemStack> decoration) {
            return new Entry(available, weight, id, min, max, decoration);
        }

        Optional<ItemStack> create(ServerLevel level, RandomSource random, int palier) {
            Identifier key = Identifier.tryParse(id);
            Optional<Item> item = key == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(key);
            if (item.isEmpty()) {
                return Optional.empty();
            }
            ItemStack stack = new ItemStack(item.get());
            int count = min + (max > min ? random.nextInt(max - min + 1) : 0);
            if (max > 1) {
                count = (int) Math.round(count * (1.0d + palier / 50.0d));
            }
            stack.setCount(Math.max(1, Math.min(stack.getMaxStackSize(), count)));
            if (extra != null) {
                extra.accept(level, stack);
            }
            return Optional.of(stack);
        }
    }

    private DungeonRewards() {
    }

    // -- Reserves de palier ------------------------------------------------------

    /** Les metaux de la ligne « 0 » de trois donjons (Nimbria donne les minerais). */
    private static List<Entry> metals() {
        return List.of(
                Entry.of(0, 12, "minecraft:iron_ingot", 2, 6),
                Entry.of(0, 10, "minecraft:gold_ingot", 2, 5),
                Entry.of(0, 4, "minecraft:diamond", 1, 2),
                Entry.of(0, 5, "minecraft:emerald", 1, 3),
                Entry.of(0, 10, "minecraft:cooked_beef", 4, 10),
                Entry.of(0, 8, "palamod:amethyst_ingot", 1, 4),
                Entry.of(0, 6, "palamod:titane_ingot", 1, 3),
                Entry.of(0, 4, "palamod:paladium_ingot", 1, 2));
    }

    private static List<Entry> pool(Dungeon dungeon) {
        List<Entry> entries = new ArrayList<>();
        switch (dungeon) {
            case TALIKUS -> {
                entries.addAll(metals());
                entries.add(Entry.of(0, 6, "minecraft:glowstone_dust", 4, 10));
                entries.add(Entry.of(0, 10, "minecraft:wheat", 8, 24));
                entries.add(Entry.of(0, 8, "minecraft:potato", 6, 16));
                entries.add(Entry.of(0, 8, "minecraft:carrot", 6, 16));
                entries.add(Entry.of(10, 8, "minecraft:melon_slice", 8, 20));
                entries.add(Entry.of(40, 1, "palamod:crusher", 1, 1));
                entries.add(Entry.of(40, 6, "minecraft:pumpkin", 2, 6));
                entries.add(Entry.of(40, 4, "palamod:eggplant_seeds", 2, 5));
                entries.add(Entry.of(60, 3, "palamod:chervil_seeds", 2, 5));
                entries.add(Entry.of(65, 1, "palamod:totem_of_fertility", 1, 1));
                entries.add(Entry.of(80, 3, "palamod:kiwano_seeds", 2, 4));
                entries.add(Entry.of(100, 2, "palamod:orangeblue_seeds", 1, 3));
            }
            case NIMBRIA -> {
                entries.add(Entry.of(0, 12, "minecraft:iron_ore", 2, 6));
                entries.add(Entry.of(0, 10, "minecraft:gold_ore", 2, 5));
                entries.add(Entry.of(0, 4, "minecraft:diamond_ore", 1, 2));
                entries.add(Entry.of(0, 5, "minecraft:emerald_ore", 1, 3));
                entries.add(Entry.of(0, 10, "minecraft:cooked_beef", 4, 10));
                entries.add(Entry.of(0, 8, "palamod:amethyst_ore", 1, 4));
                entries.add(Entry.of(0, 6, "palamod:titane_ore", 1, 3));
                entries.add(Entry.of(0, 4, "palamod:paladium_ore", 1, 2));
                entries.add(Entry.of(25, 1, "palamod:cobblebreaker", 1, 1));
                entries.add(Entry.of(45, 1, "palamod:paladium_hopper", 1, 1));
                entries.add(Entry.of(50, 1, "palamod:cobblebreaker_amethyst_upgrade", 1, 1));
                entries.add(Entry.of(65, 1, "palamod:cobblebreaker_titane_upgrade", 1, 1));
                entries.add(Entry.of(90, 1, "palamod:cobblebreaker_paladium_upgrade", 1, 1));
            }
            case VITALYS -> {
                entries.addAll(metals());
                for (String meat : List.of("beef", "porkchop", "salmon", "cod", "mutton", "chicken")) {
                    entries.add(Entry.of(0, 5, "minecraft:" + meat, 3, 8));
                }
                entries.add(Entry.of(30, 1, "palamod:empty_mob_spawner", 1, 1));
            }
            case MANELIOS -> {
                entries.addAll(metals());
                for (String wood : List.of("jacaranda", "judeecercis", "erable", "ostrya")) {
                    entries.add(Entry.of(0, 4, "palamod:" + wood + "_log", 4, 12));
                }
                entries.add(Entry.of(0, 3, "palamod:lightning_potion", 1, 1));
                entries.add(Entry.of(0, 1, "palamod:extractor", 1, 1));
                entries.add(tank(10, Sap.JACARANDA));
                entries.add(tank(25, Sap.JUDEECERCIS));
                entries.add(Entry.of(25, 2, "palamod:angelic_water_bucket", 1, 1));
                entries.add(tank(50, Sap.ERABLE));
                entries.add(tank(90, Sap.OSTRYA));
            }
        }
        // Les essences de l'element du donjon, chacune a partir du palier de
        // l'equipement qu'elle repare. Le wiki ne les liste qu'une fois
        // (« Essence de tier 2 », Talikus, palier 40) mais en fait l'unique
        // moyen de reparer l'equipement de donjon : toutes les reserves en ont.
        // Les runes (wiki, ligne par ligne) : fragment commun au palier 10, rare
        // au 25 (Talikus, Nimbria) ou au 30 ; rune commune au 40 (Vitalys : 50),
        // rare au 50 ; l'epique n'est que dans les exemples du palier 60.
        boolean late = dungeon == Dungeon.VITALYS || dungeon == Dungeon.MANELIOS;
        entries.add(Entry.of(10, 4, "palamod:rune_fragment_common", 1, 3));
        entries.add(Entry.of(late ? 30 : 25, 3, "palamod:rune_fragment_rare", 1, 2));
        entries.add(rune(dungeon == Dungeon.VITALYS ? 50 : 40, 2, "palamod:rune_common"));
        entries.add(rune(50, 1, "palamod:rune_rare"));
        entries.add(rune(60, 1, "palamod:rune_epic"));
        String element = "palamod:" + dungeon.element() + "_essence";
        entries.add(Entry.of(EssenceTier.FRAGMENT.from(), 3, element + EssenceTier.FRAGMENT.suffix(), 1, 2));
        entries.add(Entry.of(EssenceTier.ESSENCE.from(), 2, element + EssenceTier.ESSENCE.suffix(), 1, 1));
        entries.add(Entry.of(EssenceTier.CONCENTRATE.from(), 1, element + EssenceTier.CONCENTRATE.suffix(), 1, 1));
        return entries;
    }

    /** Une rune tiree avec ses statistiques. */
    private static Entry rune(int from, int weight, String id) {
        return Entry.of(from, weight, id, 1, 1).with((level, stack) -> RuneItem.ensure(stack, level.getRandom()));
    }

    /** « Tank en amethyste rempli de ... » : plein. */
    private static Entry tank(int from, Sap sap) {
        return Entry.of(from, 1, "palamod:amethyst_tank", 1, 1).with((level, stack) ->
                stack.set(PalaAlchemist.TANK_SAP.get(), new TankSap(sap, TankTier.AMETHYST.capacity())));
    }

    // -- Table des boss ---------------------------------------------------------

    private static boolean x0(int palier) {
        return palier % 10 == 0;
    }

    /**
     * La table des boss : la reserve B de dix-sept objets, puis les ajouts
     * ligne par ligne. Les deux tetes de wither de PalaMod (passive et
     * supreme), les bonbons, le jetpack, la defense d'elephant, la piece de
     * lune et le double XP n'existent pas dans DoHy : seule la tete de
     * squelette wither vanilla (« Tete de wither ») reste.
     */
    private static List<Entry> bossPool() {
        List<Entry> entries = new ArrayList<>();
        // Le fragment d'endium manque aux lignes 60, 70, 80, 90 et 100.
        entries.add(new Entry(palier -> !(x0(palier) && palier >= 60), 3, "palamod:endium_fragment", 1, 1, null));
        for (String piece : List.of("helmet", "chestplate", "leggings", "boots")) {
            entries.add(Entry.of(0, 2, "palamod:paladium_" + piece, 1, 1).with(enchanted(
                    Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3)));
        }
        entries.add(Entry.of(0, 2, "palamod:paladium_sword", 1, 1).with(enchanted(
                Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3, Enchantments.FIRE_ASPECT, 2)));
        entries.add(Entry.of(0, 2, "palamod:paladium_sword", 1, 1).with(enchanted(
                Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3, Enchantments.LOOTING, 3)));
        entries.add(Entry.of(0, 2, "palamod:paladium_pickaxe", 1, 1).with(enchanted(
                Enchantments.EFFICIENCY, 5, Enchantments.UNBREAKING, 3)));
        entries.add(Entry.of(0, 4, "palamod:paladium_block", 1, 2));
        entries.add(Entry.of(0, 4, "palamod:green_paladium_ingot", 1, 3));
        entries.add(Entry.of(0, 4, "palamod:invoker_stone", 1, 2));
        entries.add(Entry.of(0, 4, "palamod:amethyst_block", 1, 3));
        entries.add(Entry.of(0, 4, "palamod:titane_block", 1, 3));
        for (String job : List.of("farmer", "hunter", "miner", "alchemist")) {
            entries.add(Entry.of(0, 3, "palamod:job_bottle_" + job, 1, 1));
        }
        // Armure d'endium mixte P4U3 : apres un boss des 30, apres tout boss des 55.
        for (String piece : List.of("helmet", "chestplate", "leggings", "boots")) {
            entries.add(new Entry(palier -> palier >= 55 || (palier >= 30 && x0(palier)), 1,
                    "palamod:mixed_endium_" + piece, 1, 1, enchanted(
                    Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3)));
        }
        entries.add(Entry.of(55, 1, "minecraft:nether_star", 1, 1));
        entries.add(new Entry(palier -> palier >= 60 && x0(palier), 1, "minecraft:wither_skeleton_skull", 1, 1, null));
        entries.add(Entry.of(85, 1, "palamod:endium_nugget", 1, 1));
        entries.add(new Entry(palier -> palier >= 90 && x0(palier), 1, "palamod:legendary_stone_random", 1, 1, null));
        entries.add(new Entry(palier -> palier >= 90 && x0(palier), 1, "palamod:god_pickaxe", 1, 1,
                (level, stack) -> stack.set(PalaGodPickaxe.DATA.get(),
                        new GodPickaxeData(GodPickaxeLevels.xpFor(60) + 1, false))));
        return entries;
    }

    private static BiConsumer<ServerLevel, ItemStack> enchanted(Object... pairs) {
        return (level, stack) -> {
            var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            for (int i = 0; i + 1 < pairs.length; i += 2) {
                @SuppressWarnings("unchecked")
                ResourceKey<Enchantment> key = (ResourceKey<Enchantment>) pairs[i];
                int enchantLevel = (Integer) pairs[i + 1];
                registry.get(key).ifPresent(holder -> stack.enchant(holder, enchantLevel));
            }
        };
    }

    // -- Tirages ----------------------------------------------------------------

    private static Optional<ItemStack> draw(List<Entry> entries, ServerLevel level, RandomSource random, int palier) {
        List<Entry> open = entries.stream().filter(entry -> entry.available().test(palier)).toList();
        int total = open.stream().mapToInt(Entry::weight).sum();
        if (total <= 0) {
            return Optional.empty();
        }
        int pick = random.nextInt(total);
        for (Entry entry : open) {
            pick -= entry.weight();
            if (pick < 0) {
                return entry.create(level, random, palier);
            }
        }
        return Optional.empty();
    }

    /** Une jarre brisee : un tirage de la reserve du donjon, une fois sur quatre. */
    static List<ItemStack> jar(ServerLevel level, RandomSource random, Dungeon dungeon, int palier) {
        List<ItemStack> loot = new ArrayList<>();
        if (random.nextInt(4) == 0) {
            draw(pool(dungeon), level, random, palier).ifPresent(loot::add);
        }
        return loot;
    }

    /** Le contenu du coffre d'une salle, tire pour un joueur. */
    static List<ItemStack> chest(ServerLevel level, RandomSource random, Dungeon dungeon, int palier, boolean boss) {
        List<ItemStack> loot = new ArrayList<>();
        List<Entry> pool = pool(dungeon);
        int rolls = 2 + palier / 50;
        for (int i = 0; i < rolls; i++) {
            draw(pool, level, random, palier).ifPresent(loot::add);
        }
        if (boss) {
            List<Entry> table = bossPool();
            int bossRolls = x0(palier) ? 2 : 1;
            for (int i = 0; i < bossRolls; i++) {
                draw(table, level, random, palier).ifPresent(loot::add);
            }
        }
        return loot;
    }

    /**
     * Les recompenses uniques du palier exact, chacune tiree a part une fois
     * sur deux par l'appelant.
     *
     * <ul>
     *   <li>« Epee de tier 1 » aux paliers 5 et 15, « tier 2 » a 35 et 45,
     *       « tier 3 » a 65 et 75 — Nimbria n'a ni la 15 ni la 35 ;</li>
     *   <li>« Armure de tier 1 » de 10 a 20, « tier 2 » de 40 a 50, « tier 3 »
     *       de 70 a 80 : une piece au hasard du tier ;</li>
     *   <li>au palier 100, la piece antique du donjon ;</li>
     *   <li>les sacs de donjon de Nimbria (40 et 50, puis 60 et 70).</li>
     * </ul>
     * Parchemins de craft et parchemins de livre enchante ne sont pas portes.
     */
    static List<ItemStack> unique(Dungeon dungeon, int palier, RandomSource random) {
        List<ItemStack> rewards = new ArrayList<>();
        DungeonGearTier sword = switch (palier) {
            case 5 -> DungeonGearTier.BASIC;
            case 15 -> dungeon == Dungeon.NIMBRIA ? null : DungeonGearTier.BASIC;
            case 35 -> dungeon == Dungeon.NIMBRIA ? null : DungeonGearTier.REINFORCED;
            case 45 -> DungeonGearTier.REINFORCED;
            case 65, 75 -> DungeonGearTier.LEGENDARY;
            default -> null;
        };
        if (sword != null) {
            rewards.add(new ItemStack(PalaDungeonGear.sword(dungeon, sword)));
        }
        DungeonGearTier armor = palier >= 10 && palier <= 20 ? DungeonGearTier.BASIC
                : palier >= 40 && palier <= 50 ? DungeonGearTier.REINFORCED
                : palier >= 70 && palier <= 80 ? DungeonGearTier.LEGENDARY : null;
        if (armor != null) {
            rewards.add(new ItemStack(PalaDungeonGear.armorPiece(dungeon, armor, random.nextInt(4))));
        }
        if (palier == 100) {
            rewards.add(new ItemStack(PalaDungeonGear.antique(dungeon)));
        }
        if (dungeon == Dungeon.NIMBRIA) {
            if (palier == 40 || palier == 50) {
                rewards.add(new ItemStack(PalaDungeon.BACKPACK_T2.get()));
            } else if (palier == 60 || palier == 70) {
                rewards.add(new ItemStack(PalaDungeon.BACKPACK_T3.get()));
            }
        }
        return rewards;
    }

    /**
     * Le coffre de la note (wiki : « une meilleure note debloque un coffre plus
     * interessant ») : des tirages en plus dans la reserve du donjon, et une
     * chance d'essence du tier du palier.
     */
    static List<ItemStack> gradeChest(ServerLevel level, RandomSource random, Dungeon dungeon, int palier,
                                      Grade grade) {
        List<ItemStack> loot = new ArrayList<>();
        List<Entry> pool = pool(dungeon);
        for (int i = 0; i < grade.extraRolls(); i++) {
            draw(pool, level, random, palier).ifPresent(loot::add);
        }
        if (random.nextFloat() < grade.essenceChance()) {
            loot.add(new ItemStack(PalaDungeonGear.essence(dungeon, EssenceTier.forPalier(palier))));
        }
        return loot;
    }
}

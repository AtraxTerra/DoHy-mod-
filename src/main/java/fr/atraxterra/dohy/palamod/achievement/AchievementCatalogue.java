package fr.atraxterra.dohy.palamod.achievement;

import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionBranch;
import fr.atraxterra.dohy.faction.FactionStats;
import fr.atraxterra.dohy.faction.FactionTree;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.palamod.achievement.Achievement.Category;
import fr.atraxterra.dohy.palamod.achievement.Achievement.Step;
import fr.atraxterra.dohy.palamod.trixium.TrixiumContest;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToLongFunction;

/**
 * Le catalogue des succes, releve au bytecode par {@code tools/extract_achievements.py}
 * dans {@code AchievementsManager.registerAchievements} (PalaMod) et
 * {@code CommonProxy} (PalaJobs) : 139 succes, 315 avec leurs etapes.
 *
 * <p>Les noms, les descriptions et les objectifs sont ceux du code. Ce qui
 * change pour une partie solo est note a cote du succes, et dans
 * {@code docs/PALAMOD.md} (§ 27) : les succes de systemes absents (serveurs,
 * mailbox, sorts, familiers, echanges entre joueurs, Palapass, blocs camouflage
 * d'un autre mod, glueballs, sac a dos de chasseur, bouteilles d'XP scellees,
 * elephants) sont retires ou remplaces par l'equivalent de DoHy.
 *
 * <p><b>Recompenses</b> (choix du joueur : de l'argent, selon la difficulte) :
 * cinq paliers, de 10 000 $ a 5 000 000 $.
 */
final class AchievementCatalogue {

    static final long R1 = 10_000L;
    static final long R2 = 50_000L;
    static final long R3 = 250_000L;
    static final long R4 = 1_000_000L;
    static final long R5 = 5_000_000L;

    private static List<Achievement> all;

    private AchievementCatalogue() {
    }

    static synchronized List<Achievement> all() {
        if (all == null) {
            all = List.copyOf(build());
        }
        return all;
    }

    static List<Achievement> of(Category category) {
        List<Achievement> list = new ArrayList<>();
        for (Achievement achievement : all()) {
            if (achievement.category() == category) {
                list.add(achievement);
            }
        }
        return list;
    }

    // -- Mesures ------------------------------------------------------------------

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    }

    private static ToLongFunction<ServerPlayer> crafted(String id) {
        Item item = item(id);
        return player -> player.getStats().getValue(Stats.ITEM_CRAFTED.get(item));
    }

    private static ToLongFunction<ServerPlayer> broken(String id) {
        Item item = item(id);
        return player -> player.getStats().getValue(Stats.ITEM_BROKEN.get(item));
    }

    private static ToLongFunction<ServerPlayer> used(String id) {
        Item item = item(id);
        return player -> player.getStats().getValue(Stats.ITEM_USED.get(item));
    }

    private static ToLongFunction<ServerPlayer> counter(String... keys) {
        return player -> {
            AchievementData data = Achievements.data(player);
            long total = 0L;
            for (String key : keys) {
                total += data.counter(key);
            }
            return total;
        };
    }

    /** La plus grosse quantite de cet objet portee a la fois ({@link Achievements} la releve). */
    private static ToLongFunction<ServerPlayer> owned(String id) {
        Achievements.WATCHED.add(id);
        return counter("own:" + id);
    }

    private static ToLongFunction<ServerPlayer> mined(String... ids) {
        String[] keys = new String[ids.length];
        for (int i = 0; i < ids.length; i++) {
            keys[i] = "mine:" + ids[i];
        }
        return counter(keys);
    }

    private static long walked(ServerPlayer player) {
        var stats = player.getStats();
        return (stats.getValue(Stats.CUSTOM.get(Stats.WALK_ONE_CM)) + stats.getValue(Stats.CUSTOM.get(Stats.SPRINT_ONE_CM))
                + stats.getValue(Stats.CUSTOM.get(Stats.CROUCH_ONE_CM))) / 100L;
    }

    private static long factionTiers(ServerPlayer player) {
        var data = FactionApi.data(player);
        if (!data.exists()) {
            return 0L;
        }
        long total = 0L;
        for (FactionBranch branch : FactionBranch.values()) {
            total += data.tier(branch.key());
        }
        return total;
    }

    private static int totalTiers() {
        int total = 0;
        for (FactionBranch branch : FactionBranch.values()) {
            total += FactionTree.tiers(branch).size();
        }
        return Math.max(1, total);
    }

    private static Step step(String label, long goal, ToLongFunction<ServerPlayer> progress) {
        return new Step(label, goal, progress);
    }

    private static Step one(String label, ToLongFunction<ServerPlayer> progress) {
        return new Step(label, 1L, progress);
    }

    private static String n(long value) {
        return MoneyFormat.exact(value);
    }

    // -- Le catalogue -----------------------------------------------------------

    private static List<Achievement> build() {
        List<Achievement> list = new ArrayList<>();
        Builder b = new Builder(list);

        // ---------------------------------------------------------------- BIEN DEBUTER
        b.add("palamod.craft.crafttools", Category.START, "Outillage", "Crafter vos premiers outils en paladium",
                "palamod:paladium_pickaxe", R1,
                one("Crafter une pioche en paladium", crafted("palamod:paladium_pickaxe")),
                one("Crafter une hache en paladium", crafted("palamod:paladium_axe")),
                one("Crafter une pelle en paladium", crafted("palamod:paladium_shovel")));
        b.add("palamod.craft.craftarmor", Category.START, "Bien s'equiper", "Crafter votre premiere armure en paladium",
                "palamod:paladium_chestplate", R1,
                one("Crafter un casque en paladium", crafted("palamod:paladium_helmet")),
                one("Crafter un plastron en paladium", crafted("palamod:paladium_chestplate")),
                one("Crafter une jambiere en paladium", crafted("palamod:paladium_leggings")),
                one("Crafter des bottes en paladium", crafted("palamod:paladium_boots")));
        // Les six serveurs de Paladium deviennent les six dimensions de DoHy.
        b.add("palamod.visitservers.allservers", Category.START, "Explorateur dimensionnel",
                "Visiter toutes les dimensions", "minecraft:compass", R2,
                one("Le monde normal", counter("dim:minecraft:overworld")),
                one("Le Nether", counter("dim:minecraft:the_nether")),
                one("L'End", counter("dim:minecraft:the_end")),
                one("La Dimension du Mineur", counter("dim:palamod:miner")),
                one("Les donjons", counter("dim:palamod:dungeon")),
                one("La Warzone des events", counter("dim:palamod:warzone")));
        b.add("palamod.breakblock.startrich", Category.START, "Debut de la richesse !", "Miner les minerais demandes",
                "palamod:paladium_pickaxe", R1,
                step("Miner 32 minerais de fer", 32, mined("minecraft:iron_ore", "minecraft:deepslate_iron_ore")),
                step("Miner 16 minerais d'or", 16, mined("minecraft:gold_ore", "minecraft:deepslate_gold_ore")),
                step("Miner 5 minerais de diamant", 5, mined("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore")),
                step("Miner 20 minerais d'amethyste", 20, mined("palamod:amethyst_ore")),
                step("Miner 12 minerais de titane", 12, mined("palamod:titane_ore")),
                step("Miner 6 minerais de paladium", 6, mined("palamod:paladium_ore")),
                step("Miner 2 minerais de findium", 2, mined("palamod:findium_ore")));
        b.add("palamod.walkdistance.30k", Category.START, "Longue marche", "Marcher sur une distance de 30 000 blocs",
                "minecraft:leather_boots", R1,
                step("Marcher 30 000 blocs", 30_000, AchievementCatalogue::walked));
        // « Enchanter une pioche en paladium » : avoir en main une pioche en paladium enchantee.
        b.add("palamod.enchant.firstenchant", Category.START, "Premier enchantement",
                "Realiser votre premier enchantement", "minecraft:enchanting_table", R1,
                step("Atteindre le niveau 30", 30, player -> player.experienceLevel),
                one("Enchanter une pioche en paladium", counter(Achievements.ENCHANTED_PICKAXE)));
        b.add("palamod.craft.craftgrinder", Category.START, "Etablissement du Grinder",
                "Crafter tous les blocs necessaires pour la creation d'un grinder", "palamod:grinder", R2,
                one("Crafter un grinder bloc", crafted("palamod:grinder")),
                step("Crafter 5 grinder casing", 5, crafted("palamod:grinder_casing")),
                step("Crafter 20 grinder frame", 20, crafted("palamod:grinder_frame")));
        b.add("palamod.craft.luckyhammer", Category.START, "Marteau de fortune", "Apprendre a dupliquer legalement",
                "palamod:paladium_hammer", R2,
                one("Crafter un hammer pattern", crafted("palamod:hammer_pattern")),
                one("Obtenir une tete de hammer", owned("palamod:hammer_head")),
                one("Obtenir un hammer en paladium", owned("palamod:paladium_hammer")),
                step("Appliquer a un hammer en paladium 2 modifieurs de fortune", 2, counter(Achievements.HAMMER_FORTUNE)),
                one("Appliquer a un hammer en paladium 1 modifieur de fusion", counter(Achievements.HAMMER_SMELT)));
        b.add("palamod.hammer.hammerduplicate", Category.START, "Duplication en cours",
                "Dupliquer avec un hammer fortune et smelt les minerais demandes", "palamod:fortune_modifier", R2,
                step("Dupliquer 5 minerais d'amethyste", 5, counter("hammer:duplicate:palamod:amethyst_ore")),
                step("Dupliquer 5 minerais de titane", 5, counter("hammer:duplicate:palamod:titane_ore")),
                step("Dupliquer 5 minerais de paladium", 5, counter("hammer:duplicate:palamod:paladium_ore")));
        b.add("palamod.shop.buysell", Category.START, "Shoppeur", "Utiliser le shop", "minecraft:emerald", R1,
                one("Effectuer une vente au shop", counter(Achievements.SALES)),
                one("Effectuer un achat au shop", counter(Achievements.PURCHASES)));
        // Le parchemin de dragon n'existe pas ici : abattre Volcania le remplace.
        b.add("palamod.egg.participate", Category.START, "Une belle omelette", "Participer a un Egg Hunt",
                "minecraft:dragon_egg", R2,
                one("Taper le dragon lors du Egg Hunt", counter("egghunt:hit")),
                one("Etre la quand Volcania tombe", counter("egghunt:kill")),
                step("Recuperer un oeuf pendant un Egg Hunt", 3, counter("egghunt:egg")));
        b.add("palamod.egg.hurtboss", Category.START, "C'est qui le boss", "Combattre un boss dans une arene",
                "minecraft:wither_skeleton_skull", R1,
                one("Frapper un boss d'event", counter("boss:hurt")));

        // ---------------------------------------------------------------- ECONOMIE
        long[] balances = {10_000L, 100_000L, 500_000L, 1_000_000L};
        String[] balanceNames = {"Le porte-monnaie en sueur", "Money money money", "Je possede des thunes", "Millionnaire"};
        String[] balanceIds = {"10k", "100k", "500k", "1m"};
        long[] balanceRewards = {R1, R1, R2, R2};
        for (int i = 0; i < balances.length; i++) {
            long goal = balances[i];
            b.add("palamod.cresus.balance." + balanceIds[i], Category.ECONOMY, balanceNames[i], "Posseder " + n(goal) + " $",
                    "minecraft:gold_ingot", balanceRewards[i],
                    step("Avoir " + n(goal) + " $ sur soi", goal, player -> Economy.balance(player)));
        }
        b.add("palamod.shop.sell.50k", Category.ECONOMY, "Shoppeur experimente",
                "Vendre pour 50 000 $ de ressources a l'admin shop", "minecraft:emerald", R2,
                step("Vendre pour 50 000 $", 50_000, counter(Achievements.SOLD)));
        long[] spent = {10_000L, 100_000L, 500_000L, 1_000_000L};
        String[] spentNames = {"Acheteur de passage", "Acheteur modere", "Acheteur regulier", "Acheteur compulsif"};
        long[] spentRewards = {R1, R1, R2, R2};
        for (int i = 0; i < spent.length; i++) {
            long goal = spent[i];
            b.add("paladium.cresus.withdraw." + (i + 1), Category.ECONOMY, spentNames[i], "Depenser " + n(goal) + " $",
                    "minecraft:gold_nugget", spentRewards[i],
                    step("Depenser " + n(goal) + " $", goal, counter(Achievements.SPENT)));
        }

        // ---------------------------------------------------------------- METIERS
        b.add("palamod.craft.extractor", Category.JOBS, "Extraire par ci, par la, partout", "Crafter 50 extracteurs",
                "palamod:extractor", R2, step("Crafter 50 extracteurs", 50, crafted("palamod:extractor")));
        b.add("palamod.craft.flasks", Category.JOBS, "Verrier", "Crafter 1000 fioles d'alchimiste",
                "palamod:flask", R2, step("Crafter 1 000 fioles", 1_000, crafted("palamod:flask")));
        // SeveExtract : 1 000 unites par fiole tiree de l'extracteur, comme ExtractSeveAchievement.
        long[] saps = {1_000_000L, 5_000_000L, 30_000_000L, 200_000_000L};
        String[] sapNames = {"Petite", "Moyenne", "Grande", "Gigantesque"};
        String[] sapIds = {"1m", "5m", "30m", "200m"};
        long[] sapRewards = {R2, R3, R4, R5};
        for (int i = 0; i < saps.length; i++) {
            long goal = saps[i];
            ToLongFunction<ServerPlayer> flasks = counter("sap:flasks");
            b.add("palamod.seve.count." + sapIds[i], Category.JOBS, sapNames[i] + " exploitation de seve",
                    "Extraire " + n(goal) + " unites de seves", "palamod:flask", sapRewards[i],
                    step("Extraire " + n(goal) + " unites (1 000 par fiole)", goal,
                            player -> flasks.applyAsLong(player) * 1_000L));
        }
        b.add("palamod.dimensionminer.all", Category.JOBS, "Transdimensionnelle", "Rentrer dans la Dimension mineur",
                "palamod:withered_obsidian", R2,
                one("Activer le portail de la Dimension mineur", counter("dimminer:portal")),
                one("Arriver dans la Dimension mineur", counter("dim:palamod:miner")),
                step("Terminer 3 sessions", 3, counter("dimminer:session")));
        b.add("palamod.seve.extractall", Category.JOBS, "Il y en a pour tous", "Extraire tous les types de seves",
                "palamod:flask", R2,
                one("Recuperer une fiole de seve de jacaranda", counter("sap:jacaranda")),
                one("Recuperer une fiole de seve de judeecercis", counter("sap:judeecercis")),
                one("Recuperer une fiole de seve d'erable", counter("sap:erable")),
                one("Recuperer une fiole de seve d'ostrya", counter("sap:ostrya")));
        b.add("palamod.cauldron.init", Category.JOBS, "Chaudron de sorcier", "Creer votre chaudron",
                "minecraft:cauldron", R1, one("Former un chaudron", counter("cauldron:init")));
        b.add("palamod.portal.init.all", Category.JOBS, "Activateur de portail", "Activer tous les types de portail",
                "palamod:portal_key", R2,
                one("Activer un portail en amethyste", counter("portal:amethyst_portal_key")),
                one("Activer un portail en titane", counter("portal:titane_portal_key")),
                one("Activer un portail en paladium", counter("portal:paladium_portal_key")),
                one("Activer un portail en endium", counter("portal:endium_portal_key")));
        b.add("palamod.craftcauldron.pollenall", Category.JOBS, "Pollinisation", "Obtenir tous les types de pollen",
                "palamod:amethyst_pollen", R2,
                one("Recuperer un pollen d'amethyste", owned("palamod:amethyst_pollen")),
                one("Recuperer un pollen de titane", owned("palamod:titane_pollen")),
                one("Recuperer un pollen de paladium", owned("palamod:paladium_pollen")),
                one("Recuperer un pollen d'endium", owned("palamod:endium_pollen")));
        b.add("palamod.useitem.cavernhammer", Category.JOBS, "Createur de spawner", "Recuperer 200 ames",
                "palamod:cavern_hammer", R2, step("Recuperer 200 ames", 200, counter("souls")));
        b.add("palamod.useitem.totem.fuel", Category.JOBS, "Totemiste", "Alimenter 1 000 fois un totem en paladium",
                "palamod:totem_of_fertility", R3, step("Alimenter 1 000 fois un totem", 1_000, counter("totem:fuel")));
        b.add("palamod.craft.bread", Category.JOBS, "Boulangerie", "Crafter 2 000 pains", "minecraft:bread", R2,
                step("Crafter 2 000 pains", 2_000, crafted("minecraft:bread")));
        b.add("palamod.breakblock.vanilla.all", Category.JOBS, "Vanilladdict",
                "Miner 10 exemplaires de chaque minerai vanilla", "minecraft:iron_pickaxe", R1,
                step("Casser 10 minerais de charbon", 10, mined("minecraft:coal_ore", "minecraft:deepslate_coal_ore")),
                step("Casser 10 minerais de fer", 10, mined("minecraft:iron_ore", "minecraft:deepslate_iron_ore")),
                step("Casser 10 minerais de lapis lazuli", 10, mined("minecraft:lapis_ore", "minecraft:deepslate_lapis_ore")),
                step("Casser 10 minerais de diamant", 10,
                        mined("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore")),
                step("Casser 10 minerais de redstone", 10,
                        mined("minecraft:redstone_ore", "minecraft:deepslate_redstone_ore")),
                step("Casser 10 minerais d'emeraude", 10,
                        mined("minecraft:emerald_ore", "minecraft:deepslate_emerald_ore")));
        b.add("palamod.breakblock.modded.all", Category.JOBS, "Minez modde !",
                "Miner 10 exemplaires de chaque minerai modde", "palamod:paladium_pickaxe", R2,
                step("Casser 10 minerais d'amethyste", 10, mined("palamod:amethyst_ore")),
                step("Casser 10 minerais de titane", 10, mined("palamod:titane_ore")),
                step("Casser 10 minerais de paladium", 10, mined("palamod:paladium_ore")),
                step("Casser 10 minerais de paladium vert", 10, mined("palamod:paladium_green_ore")),
                step("Casser 10 minerais de trixium", 10, mined("palamod:trixium_ore")),
                step("Casser 10 minerais de findium", 10, mined("palamod:findium_ore")));
        b.add("palamod.breakblock.seeds.all", Category.JOBS, "Qui seme les graines...",
                "Obtenir 10 exemplaires de chaque graine", "minecraft:wheat_seeds", R1,
                step("Crafter 10 graines de citrouilles", 10, crafted("minecraft:pumpkin_seeds")),
                step("Crafter 10 graines de melon", 10, crafted("minecraft:melon_seeds")),
                step("Avoir 10 graines d'aubergine", 10, owned("palamod:eggplant_seeds")),
                step("Avoir 10 graines de cerfeuil", 10, owned("palamod:chervil_seeds")),
                step("Avoir 10 graines de kiwano", 10, owned("palamod:kiwano_seeds")),
                step("Avoir 10 graines d'orangeblue", 10, owned("palamod:orangeblue_seeds")));
        // Ce qui sort du crusher, a la main : chaque fruit donne son propre metal.
        b.add("palamod.crusher.all", Category.JOBS, "...crushe les fruits !", "Broyez des fruits dans le crusher",
                "palamod:crusher", R2,
                one("Crusher des aubergines", counter("crusher:palamod:amethyst_ingot")),
                one("Crusher des cerfeuils", counter("crusher:palamod:titane_ingot")),
                one("Crusher des kiwanos", counter("crusher:palamod:paladium_ingot")),
                one("Crusher des orangeblue", counter("crusher:palamod:endium_nugget")));
        b.add("palamod.cobblebreaker.particle", Category.JOBS, "Tresor dans la pierre", "Recuperer 5 000 particules",
                "palamod:cobblebreaker", R3,
                step("Retirer 5 000 particules d'un cobblebreaker", 5_000, counter("cobblebreaker:particles")));
        b.add("palamod.useitem.builderwand", Category.JOBS, "Constructeurs de l'extreme !",
                "Utiliser 1 000 fois un outil de construction", "palamod:amethyst_builder_wand", R3,
                step("Utiliser 1 000 fois un outil de construction", 1_000, counter(FactionStats.BUILDER_WAND)));
        b.add("palamod.useitem.voidstone", Category.JOBS, "Disparition totale",
                "Supprimer 1 000 000 de blocs avec une minage voidstone", "palamod:voidstone_minage", R3,
                step("Supprimer 1 000 000 de blocs", 1_000_000, counter("voidstone:absorbed")));
        // Le Spawner Finder de DoHy se tient en main (une charge par seconde) : dix minutes d'usage.
        b.add("palamod.useitem.spawnerfinder", Category.JOBS, "Chercheur assiste",
                "Utiliser 10 min un spawner finder", "palamod:spawner_finder", R1,
                step("Tenir un Spawner Finder 10 min", 600, counter(Achievements.SPAWNER_FINDER_SECONDS)));
        // Crabe, escargot et elephant n'existent pas ici : le panda, le perroquet et la chevre,
        // de la meme liste de creatures capturables, les remplacent.
        b.add("palamod.capturestone.all", Category.JOBS, "Zoo original", "Capturer des creatures",
                "palamod:capture_stone", R2,
                one("Capturer un zombie", counter("capture:minecraft:zombie")),
                one("Capturer un creeper", counter("capture:minecraft:creeper")),
                one("Capturer une tortue", counter("capture:minecraft:turtle")),
                one("Capturer un dauphin", counter("capture:minecraft:dolphin")),
                one("Capturer un panda", counter("capture:minecraft:panda")),
                one("Capturer un perroquet", counter("capture:minecraft:parrot")),
                one("Capturer une chevre", counter("capture:minecraft:goat")));
        // Les mini-boss de PalaJobs ne sont pas portes : les cinq boss d'event les remplacent.
        b.add("palamod.miniboss.killall", Category.JOBS, "Petite proie, dur combat", "Vaincre les cinq boss d'event",
                "minecraft:wither_skeleton_skull", R3,
                one("Vaincre Beep Boop", counter("boss:beep_boop")),
                one("Vaincre Tobalt", counter("boss:tobalt")),
                one("Vaincre Azhur", counter("boss:azhur")),
                one("Vaincre Morsula", counter("boss:morsula")),
                one("Vaincre Arachna", counter("boss:arachna")));

        // PalaJobs
        b.add("palajobs.reachlevel.multi.all", Category.START, "Multitache", "Atteindre le niveau 2 dans tous vos metiers",
                "minecraft:experience_bottle", R1,
                step("Atteindre mineur niveau 2", 2, player -> JobApi.level(player, Job.MINER)),
                step("Atteindre chasseur niveau 2", 2, player -> JobApi.level(player, Job.HUNTER)),
                step("Atteindre alchimiste niveau 2", 2, player -> JobApi.level(player, Job.ALCHEMIST)),
                step("Atteindre fermier niveau 2", 2, player -> JobApi.level(player, Job.FARMER)));
        int[] levels = {10, 25, 50, 75, 100};
        String[] ranks = {"novice", "novice", "professionnel", "specialiste", "expert"};
        long[] levelRewards = {R1, R2, R3, R3, R4};
        Object[][] jobs = {{Job.ALCHEMIST, "alchimist", "Alchimiste", "d'alchimiste", "palamod:flask"},
                {Job.FARMER, "farmer", "Fermier", "de fermier", "minecraft:wheat"},
                {Job.MINER, "miner", "Mineur", "de mineur", "palamod:paladium_pickaxe"},
                {Job.HUNTER, "hunter", "Chasseur", "de chasseur", "palamod:paladium_sword"}};
        for (Object[] job : jobs) {
            Job kind = (Job) job[0];
            for (int i = 0; i < levels.length; i++) {
                int level = levels[i];
                b.add("palajobs.reachlevel." + job[1] + ".lvl" + level, Category.JOBS, job[2] + " " + ranks[i],
                        "Atteindre le niveau " + level + " dans le metier " + job[3], (String) job[4], levelRewards[i],
                        step("Niveau " + level, level, player -> JobApi.level(player, kind)));
            }
        }
        b.add("palajobs.reachlevel.multi100.all", Category.START, "Expert general", "Terminer ces quatre succes",
                "minecraft:nether_star", R5,
                step("Mineur expert", 100, player -> JobApi.level(player, Job.MINER)),
                step("Chasseur expert", 100, player -> JobApi.level(player, Job.HUNTER)),
                step("Alchimiste expert", 100, player -> JobApi.level(player, Job.ALCHEMIST)),
                step("Fermier expert", 100, player -> JobApi.level(player, Job.FARMER)));
        long[] plants = {5_000L, 25_000L, 150_000L, 1_000_000L};
        long[] ores = {2_500L, 12_500L, 75_000L, 500_000L};
        long[] kills = {7_500L, 37_500L, 225_000L, 1_500_000L};
        String[] sizes = {"Petite", "Moyenne", "Grande", "Gigantesque"};
        String[] plantIds = {"5k", "25k", "150k", "1m"};
        String[] oreIds = {"2k5", "12k5", "75k", "500k"};
        String[] killIds = {"7k5", "37k5", "225k", "1m5"};
        long[] scaleRewards = {R1, R2, R3, R4};
        for (int i = 0; i < 4; i++) {
            b.add("palajobs.farmingplant." + plantIds[i], Category.JOBS, sizes[i] + " exploitation agricole",
                    "Recolter " + n(plants[i]) + " plantations", "minecraft:wheat", scaleRewards[i],
                    step("Recolter " + n(plants[i]) + " plantations mures", plants[i], counter(Achievements.HARVEST)));
        }
        b.add("palajobs.fertilizeddirthoe.5k", Category.JOBS, "Un bon fertilisant",
                "Transformer 5 000 terre en terre fertilisee", "palamod:fertilized_dirt", R2,
                step("Labourer 5 000 terres fertilisees", 5_000, counter("hoe:fertilized")));
        for (int i = 0; i < 4; i++) {
            b.add("palajobs.minerore." + oreIds[i], Category.JOBS, sizes[i] + " exploitation miniere",
                    "Casser " + n(ores[i]) + " minerais", "minecraft:iron_ore", scaleRewards[i],
                    step("Casser " + n(ores[i]) + " minerais", ores[i], counter(Achievements.MINED_ORES)));
        }
        for (int i = 0; i < 4; i++) {
            b.add("palajobs.hunterkill." + killIds[i], Category.JOBS, sizes[i] + " exploitation de chasse",
                    "Tuer " + n(kills[i]) + " monstres", "minecraft:iron_sword", scaleRewards[i],
                    step("Tuer " + n(kills[i]) + " creatures", kills[i],
                            player -> player.getStats().getValue(Stats.CUSTOM.get(Stats.MOB_KILLS))));
        }

        // ---------------------------------------------------------------- ATTAQUE ET DEFENSE
        long[] finderMinutes = {60L, 200L, 800L};
        String[] finderNames = {"Chercheur de base debutant", "Chercheur de base professionnel",
                "Chercheur de base expert"};
        String[] finderIds = {"1h", "200mn", "800mn"};
        long[] finderRewards = {R1, R2, R3};
        for (int i = 0; i < 3; i++) {
            long minutes = finderMinutes[i];
            b.add("palamod.unclaimfinder." + finderIds[i], Category.ATTACK_DEFENSE, finderNames[i],
                    "Utiliser pendant " + minutes + " min un unclaim finder", "palamod:unclaim_finder", finderRewards[i],
                    step("Tenir un Unclaim Finder " + minutes + " min", minutes * 60L,
                            counter(Achievements.UNCLAIM_SECONDS)));
        }
        b.add("palamod.craft.obsiall", Category.ATTACK_DEFENSE, "Obsidienne de qualite",
                "Crafter tous les types d'obsidiennes demandes", "palamod:big_obsidian", R2,
                one("Crafter une big obsidienne", crafted("palamod:big_obsidian")),
                one("Crafter une lava obsidienne", crafted("palamod:lava_obsidian")),
                one("Crafter une sulfuric obsidienne", crafted("palamod:sulfuric_obsidian")),
                one("Crafter une boom obsidienne", crafted("palamod:boom_obsidian")),
                one("Crafter une mega boom obsidienne", crafted("palamod:mega_boom_obsidian")),
                one("Crafter une spike (paladium) obsidienne", crafted("palamod:paladium_spike_obsidian")),
                one("Crafter une slime obsidienne", crafted("palamod:slime_obsidian")),
                one("Crafter une poison obsidienne", crafted("palamod:poison_obsidian")),
                one("Crafter une wither obsidienne", crafted("palamod:wither_obsidian")),
                one("Crafter une redstone obsidienne", crafted("palamod:redstone_obsidian")),
                one("Crafter une anti aggro obsidienne", crafted("palamod:anti_aggro_obsidian")));
        b.add("palamod.craft.spikeall", Category.ATTACK_DEFENSE, "Spike de qualite",
                "Crafter tous les types de spike demandes", "palamod:paladium_spike", R2,
                one("Crafter un spike en bois", crafted("palamod:wood_spike")),
                one("Crafter un spike en fer", crafted("palamod:iron_spike")),
                one("Crafter un spike en or", crafted("palamod:gold_spike")),
                one("Crafter un spike en diamant", crafted("palamod:diamond_spike")),
                one("Crafter un spike en amethyste", crafted("palamod:amethyst_spike")),
                one("Crafter un spike en titane", crafted("palamod:titane_spike")),
                one("Crafter un spike en paladium", crafted("palamod:paladium_spike")));
        b.add("palamod.use.agrostone", Category.ATTACK_DEFENSE, "Viens la",
                "Attirer l'attention d'un wither avec une pierre d'agro", "palamod:aggro_stone", R1,
                one("Viser un wither avec la pierre d'aggro", counter("aggro:wither")));
        // Le home remover de DoHy efface les monstres du chunk au lieu de fantomes.
        b.add("palamod.use.homeremover.kill", Category.ATTACK_DEFENSE, "Extermina-home",
                "Effacer 10 monstres avec le home remover", "palamod:home_remover", R2,
                step("Effacer 10 monstres", 10, counter("home_remover:mobs")));
        b.add("palamod.use.chestexplorer", Category.ATTACK_DEFENSE, "Explorateur de coffre",
                "Utiliser un explorateur de coffre", "palamod:chest_explorer", R1,
                one("Ouvrir un coffre a distance", used("palamod:chest_explorer")));
        b.add("palamod.walkdistance.1m", Category.ATTACK_DEFENSE, "Marathon", "Marcher 1 000 000 blocs",
                "minecraft:diamond_boots", R4, step("Marcher 1 000 000 blocs", 1_000_000, AchievementCatalogue::walked));
        long[] withers = {10L, 100L};
        String[] witherNames = {"Destructeur de base", "Exterminateur de base"};
        long[] witherRewards = {R2, R3};
        ToLongFunction<ServerPlayer> summoned = counter("spawn:wither", "spawn:funny_wither", "spawn:baby_wither",
                "spawn:passive_wither", "spawn:thirsty_wither", "spawn:predator_wither", "spawn:supreme_wither");
        for (int i = 0; i < withers.length; i++) {
            b.add("paladium.wither.summon." + (i + 1), Category.ATTACK_DEFENSE, witherNames[i],
                    "Faire spawn " + withers[i] + " withers", "minecraft:wither_skeleton_skull", witherRewards[i],
                    step("Invoquer " + withers[i] + " withers", withers[i], summoned));
        }
        long[] dynamites = {1_000L, 3_000L, 10_000L};
        String[] dynamiteNames = {"Dynamiteur junior", "Dynamiteur confirme", "Dynamiteur senior"};
        String[] dynamiteIds = {"junior", "confirme", "senior"};
        long[] dynamiteRewards = {R2, R3, R4};
        ToLongFunction<ServerPlayer> thrown = counter("use:dynamite", "use:ninja_dynamite", "use:big_dynamite");
        for (int i = 0; i < dynamites.length; i++) {
            b.add("palamod.dynamyite." + dynamiteIds[i], Category.ATTACK_DEFENSE, dynamiteNames[i],
                    "Utiliser " + n(dynamites[i]) + " dynamites", "palamod:dynamite", dynamiteRewards[i],
                    step("Lancer " + n(dynamites[i]) + " dynamites", dynamites[i], thrown));
        }
        long[] caves = {100L, 500L, 1_000L};
        String[] caveNames = {"Chercheur de tresors debutant", "Chercheur de tresors chevronne",
                "Chercheur de tresors veterans"};
        long[] caveRewards = {R1, R2, R3};
        for (int i = 0; i < caves.length; i++) {
            b.add("paladium.block.place.cave." + (i + 1), Category.ATTACK_DEFENSE, caveNames[i],
                    "Poser " + caves[i] + " cave block", "palamod:cave_block", caveRewards[i],
                    step("Poser " + n(caves[i]) + " cave blocks", caves[i], used("palamod:cave_block")));
        }
        long[] hoods = {60L, 180L, 720L};
        String[] hoodNames = {"Apprentis cambrioleur", "Cambrioleur expert", "Roi des cambrioleurs"};
        long[] hoodRewards = {R1, R2, R3};
        for (int i = 0; i < hoods.length; i++) {
            b.add("paladium.equip.balaclava." + (i + 1), Category.ATTACK_DEFENSE, hoodNames[i],
                    "Mettre une cagoule pendant " + hoods[i] + " minutes", "palamod:hood_helmet", hoodRewards[i],
                    step("Porter la cagoule " + hoods[i] + " min", hoods[i] * 60L, counter(Achievements.HOOD_SECONDS)));
        }
        b.add("paladium.explode.endium.tnt", Category.ATTACK_DEFENSE, "Pilleur a tout prix",
                "Faire exploser 5 endium TNT", "palamod:endium_tnt", R2,
                step("Faire exploser 5 TNT en endium", 5, counter("explode:endium_tnt")));

        // ---------------------------------------------------------------- FACTION
        // Pas de village de faction en solo : la reserve d'or, l'autre grand chantier, le remplace.
        b.add("paladium.faction.own.town", Category.FACTION, "Doux chez soi", "Batir la reserve d'or de ta faction",
                "minecraft:gold_block", R2,
                one("Batir une reserve d'or", player -> FactionApi.data(player).stat(FactionStats.RESERVE_TIER)));
        b.add("paladium.faction.own.claim", Category.FACTION, "On est serre un peu non ?", "Posseder une base claim",
                "minecraft:white_banner", R1,
                one("Posseder un claim", player -> FactionApi.data(player).claims().size()));
        // Les niveaux de faction de Paladium deviennent la part de l'arbre de technologie debloquee.
        int total = totalTiers();
        int[] factionLevels = {10, 30, 50, 70, 100};
        String[] factionNames = {"Faction mineur", "Faction en developpement", "Faction developpe", "Faction majeur",
                "Faction legendaire"};
        long[] factionRewards = {R1, R2, R3, R4, R5};
        for (int i = 0; i < factionLevels.length; i++) {
            long goal = Math.max(1L, (long) Math.ceil(total * factionLevels[i] / 100.0d));
            b.add("paladium.faction.level." + factionLevels[i], Category.FACTION, factionNames[i],
                    "Debloquer " + factionLevels[i] + " % de l'arbre de faction", "minecraft:white_banner",
                    factionRewards[i], step("Debloquer " + goal + " paliers de l'arbre sur " + total, goal,
                            AchievementCatalogue::factionTiers));
        }
        long[] elos = {3_000L, 6_000L, 10_000L};
        String[] eloNames = {"Gloire ephemere", "Gloire persistante", "Gloire eternelle"};
        long[] eloRewards = {R1, R2, R2};
        for (int i = 0; i < elos.length; i++) {
            b.add("paladium.faction.elo." + elos[i], Category.FACTION, eloNames[i],
                    "Atteindre les " + n(elos[i]) + " points d'elo", "minecraft:golden_helmet", eloRewards[i],
                    step("Atteindre " + n(elos[i]) + " ELO", elos[i],
                            player -> FactionApi.data(player).ranking().elo()));
        }

        // ---------------------------------------------------------------- AUTRES
        b.add("palamod.useitem.legendarystone", Category.OTHERS, "Wouah une petite pierre",
                "Utiliser l'une des pierres de legende", "palamod:legendary_stone_random", R1,
                one("Utiliser une pierre de legende", counter("legendary:random", "legendary:teleportation",
                        "legendary:invisibility", "legendary:fortune", "legendary:power", "legendary:jobs",
                        "legendary:chaos")));
        b.add("palamod.useitem.legendarystone.multi.all", Category.OTHERS, "Lithophile",
                "Utiliser toutes les pierres de legendes", "palamod:legendary_stone_chaos", R3,
                one("Utiliser une pierre de legende aleatoire", counter("legendary:random")),
                one("Utiliser une pierre de legende de teleportation", counter("legendary:teleportation")),
                one("Utiliser une pierre de legende d'invisibilite", counter("legendary:invisibility")),
                one("Utiliser une pierre de legende de fortune", counter("legendary:fortune")),
                one("Utiliser une pierre de legende de pouvoir", counter("legendary:power")),
                one("Utiliser une pierre de legende de metiers", counter("legendary:jobs")),
                one("Utiliser une pierre de legende de chaos", counter("legendary:chaos")));
        // 100 000 et 500 000 trixium sur un serveur de milliers de joueurs ; seul, 5 000 et 25 000.
        b.add("palamod.useitem.trixium.deposit.100k", Category.OTHERS, "Tout pile le bon nombre",
                "Deposer 5 000 trixiums", "palamod:trixium", R3,
                step("Deposer 5 000 trixium au concours", 5_000, player -> TrixiumContest.data(player).lifetime()));
        b.add("palamod.useitem.trixium.deposit.500k", Category.OTHERS, "Oula, j'ai beaucoup farm",
                "Deposer 25 000 trixiums", "palamod:trixium_block", R4,
                step("Deposer 25 000 trixium au concours", 25_000, player -> TrixiumContest.data(player).lifetime()));
        long[] duplicates = {10_000L, 50_000L, 100_000L};
        String[] duplicateNames = {"Dupliqueur debutant", "Dupliqueur chevronne", "Dupliqueur veterans"};
        long[] duplicateRewards = {R2, R3, R4};
        for (int i = 0; i < duplicates.length; i++) {
            b.add("paladium.hammer.duplicate." + (i + 1), Category.OTHERS, duplicateNames[i],
                    "Dupliquer " + n(duplicates[i]) + " minerais", "palamod:paladium_hammer", duplicateRewards[i],
                    step("Dupliquer " + n(duplicates[i]) + " minerais", duplicates[i], counter("hammer:duplicate")));
        }
        b.add("paladium.armor.equip.full.endium", Category.OTHERS, "It's over 5000 !", "Equiper un full endium",
                "palamod:endium_chestplate", R3, one("Porter l'armure complete en endium", counter(Achievements.ENDIUM_SET)));
        long[] mounts = {10L, 50L, 100L};
        String[] mountNames = {"Dresseur debutant", "Dresseur chevronne", "Dresseur veterans"};
        long[] mountRewards = {R2, R3, R4};
        for (int i = 0; i < mounts.length; i++) {
            b.add("paladium.mount.capture." + (i + 1), Category.OTHERS, mountNames[i],
                    "Capturer " + mounts[i] + " montures", "palamod:mount_gemstone", mountRewards[i],
                    step("Capturer " + mounts[i] + " Dancaroks sauvages", mounts[i], counter("mount:capture")));
        }
        b.add("paladium.pickup.endium.nugget", Category.OTHERS, "Mon precieux !", "Recuperer 1 endium nugget",
                "palamod:endium_nugget", R2, one("Avoir une pepite d'endium", owned("palamod:endium_nugget")));
        b.add("palamod.break.paladium.multi.all", Category.OTHERS, "Utilisateur regulier",
                "Casser tous les items en paladium existants", "palamod:paladium_sword", R3,
                one("Casser une pioche en paladium", broken("palamod:paladium_pickaxe")),
                one("Casser une pelle en paladium", broken("palamod:paladium_shovel")),
                one("Casser une hache en paladium", broken("palamod:paladium_axe")),
                one("Casser une epee en paladium", broken("palamod:paladium_sword")),
                one("Casser un casque en paladium", broken("palamod:paladium_helmet")),
                one("Casser un plastron en paladium", broken("palamod:paladium_chestplate")),
                one("Casser des jambieres en paladium", broken("palamod:paladium_leggings")),
                one("Casser des bottes en paladium", broken("palamod:paladium_boots")));
        b.add("paladium.break.spawner", Category.OTHERS, "Chercheur d'ame",
                "Casser 200 spawners en ayant un Spawner Finder dans votre inventaire", "minecraft:spawner", R3,
                step("Casser 200 spawners, Spawner Finder sur soi", 200, counter(Achievements.SPAWNER_WITH_FINDER)));
        // La fleur de chance n'existe pas ici : les trois fleurs de PalaMod et les treize vanilla.
        b.add("paladium.pickup.flower.multi.all", Category.OTHERS, "Fleuriste curieux",
                "Recuperer tous les types de fleurs possibles", "minecraft:poppy", R2,
                one("Fleur de paladium", owned("palamod:paladium_flower")),
                one("Fleur d'endium", owned("palamod:endium_flower")),
                one("Fleur minerale", owned("palamod:mineral_flower")),
                one("Pissenlit", owned("minecraft:dandelion")),
                one("Coquelicot", owned("minecraft:poppy")),
                one("Orchidee bleue", owned("minecraft:blue_orchid")),
                one("Allium", owned("minecraft:allium")),
                one("Houstonie bleue", owned("minecraft:azure_bluet")),
                one("Tulipe rouge", owned("minecraft:red_tulip")),
                one("Tulipe orange", owned("minecraft:orange_tulip")),
                one("Tulipe blanche", owned("minecraft:white_tulip")),
                one("Tulipe rose", owned("minecraft:pink_tulip")),
                one("Marguerite", owned("minecraft:oxeye_daisy")),
                one("Tournesol", owned("minecraft:sunflower")),
                one("Rosier", owned("minecraft:rose_bush")),
                one("Pivoine", owned("minecraft:peony")));
        return list;
    }

    /** Ajoute un succes, son icone resolue a la demande. */
    private record Builder(List<Achievement> list) {

        void add(String id, Category category, String name, String description, String icon, long reward,
                 Step... steps) {
            Item item = item(icon);
            list.add(new Achievement(id, category, name, description,
                    () -> new ItemStack(item == Items.AIR ? Items.PAPER : item), reward, List.of(steps)));
        }
    }
}

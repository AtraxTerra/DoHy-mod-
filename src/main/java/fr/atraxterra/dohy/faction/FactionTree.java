package fr.atraxterra.dohy.faction;

import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.ToLongBiFunction;

/**
 * L'arbre de technologie ({@code Java | Arbre de technologie}) : quatre branches,
 * douze paliers (six pour l'Economie). Un palier demande, tous ensemble, un
 * cout paye par la banque de faction, des actions, le palier precedent, puis
 * une duree d'amelioration reelle.
 *
 * <p>Les couts, les durees et les recompenses sont ceux du wiki. Les
 * conditions qui parlaient des autres joueurs deviennent solo : « 5 membres »
 * se lit « 5 claims », « membres connectes 24 h » se lit « 24 h de jeu »,
 * « visiter 7 factions » se lit « visiter 7 structures », « tuer 30 joueurs
 * differents » se lit « tuer 30 especes differentes ». Les « emplacements de
 * membre » de la branche Globale deviennent deux claims chacun.
 *
 * <p>Les compteurs viennent des statistiques du jeu (blocs parcourus, temps de
 * jeu, blocs casses, objets fabriques) ou des compteurs de la faction pour ce
 * que le jeu ne compte pas (dynamites lancees, withers invoques). Un objet qui
 * n'existe pas encore dans le mod (lots 22c a 22e) affiche son lot et ne
 * progresse pas.
 */
public final class FactionTree {

    /** Une action a accomplir. */
    public record Check(String label, long goal, ToLongBiFunction<ServerPlayer, FactionData> progress,
                        String pending) {
        public long progressOf(ServerPlayer player, FactionData data) {
            return pending.isEmpty() ? Math.min(goal, progress.applyAsLong(player, data)) : 0L;
        }

        public boolean done(ServerPlayer player, FactionData data) {
            return progressOf(player, data) >= goal;
        }
    }

    /** Des objets a fournir en plus de l'argent (or liquide de la branche Economie). */
    public record ItemCost(String id, int count, String label) {
        public Optional<Item> item() {
            Identifier key = Identifier.tryParse(id);
            return key == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(key);
        }
    }

    public record Reward(String label, Consumer<FactionPerks.Builder> apply) {
    }

    public record Tier(FactionBranch branch, int number, long cost, List<ItemCost> items, List<Check> checks,
                       Duration time, List<Reward> rewards) {
    }

    private static Map<FactionBranch, List<Tier>> tiers;

    private FactionTree() {
    }

    public static synchronized List<Tier> tiers(FactionBranch branch) {
        if (tiers == null) {
            tiers = build();
        }
        return tiers.get(branch);
    }

    public static Tier tier(FactionBranch branch, int number) {
        List<Tier> list = tiers(branch);
        return number >= 1 && number <= list.size() ? list.get(number - 1) : null;
    }

    // -- Compteurs ---------------------------------------------------------------

    private static final net.minecraft.resources.Identifier[] TRAVEL = {Stats.WALK_ONE_CM, Stats.CROUCH_ONE_CM,
            Stats.SPRINT_ONE_CM, Stats.WALK_ON_WATER_ONE_CM, Stats.WALK_UNDER_WATER_ONE_CM, Stats.SWIM_ONE_CM,
            Stats.CLIMB_ONE_CM, Stats.FLY_ONE_CM, Stats.AVIATE_ONE_CM, Stats.HORSE_ONE_CM, Stats.BOAT_ONE_CM,
            Stats.MINECART_ONE_CM, Stats.PIG_ONE_CM, Stats.STRIDER_ONE_CM};

    public static long travelled(ServerPlayer player) {
        ServerStatsCounter stats = player.getStats();
        long centimeters = 0L;
        for (net.minecraft.resources.Identifier stat : TRAVEL) {
            centimeters += stats.getValue(Stats.CUSTOM.get(stat));
        }
        return centimeters / 100L;
    }

    public static long hoursPlayed(ServerPlayer player) {
        return player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) / 72_000L;
    }

    /** Especes differentes tuees, joueurs exclus. */
    public static long speciesKilled(ServerPlayer player) {
        ServerStatsCounter stats = player.getStats();
        long count = 0L;
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (type != net.minecraft.world.entity.EntityTypes.PLAYER && stats.getValue(Stats.ENTITY_KILLED.get(type)) > 0) {
                count++;
            }
        }
        return count;
    }

    /** Structures differentes visitees ({@link FactionStats}). */
    public static long structuresVisited(FactionData data) {
        long count = 0L;
        for (String key : data.stats.keySet()) {
            if (key.startsWith(FactionStats.VISIT)) {
                count++;
            }
        }
        return count;
    }

    // -- Fabriques de conditions ---------------------------------------------------

    private static String n(long value) {
        return MoneyFormat.exact(value);
    }

    private static Check travel(long blocks) {
        return new Check("Parcourir " + n(blocks) + " blocs", blocks, (player, data) -> travelled(player), "");
    }

    private static Check hours(long hours) {
        return new Check(hours + " h de jeu", hours, (player, data) -> hoursPlayed(player), "");
    }

    private static Check claims(int count) {
        return new Check("Posseder " + count + " claims", count, (player, data) -> data.claims.size(), "");
    }

    private static Check flag(String label, java.util.function.Predicate<FactionData> test) {
        return new Check(label, 1, (player, data) -> test.test(data) ? 1L : 0L, "");
    }

    private static Check mined(Block block, long count, String what) {
        return new Check("Casser " + n(count) + " " + what, count,
                (player, data) -> player.getStats().getValue(Stats.BLOCK_MINED.get(block)), "");
    }

    /** Un objet fabrique ; s'il n'est pas encore dans le mod, la condition attend son lot. */
    private static Check crafted(String id, long count, String what, String lot) {
        Identifier key = Identifier.parse(id);
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(key);
        return new Check("Fabriquer " + (count > 1 ? n(count) + " " : "") + what, count,
                (player, data) -> item.map(found -> (long) player.getStats().getValue(Stats.ITEM_CRAFTED.get(found)))
                        .orElse(0L), item.isPresent() ? "" : lot);
    }

    /** Un compteur de la faction, alimente par les objets des lots suivants. */
    private static Check counter(String key, long count, String label, String lot, String id) {
        boolean ready = id == null || BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).isPresent();
        return new Check(label, count, (player, data) -> data.stat(key), ready ? "" : lot);
    }

    private static Check species(long count) {
        return new Check("Tuer " + count + " especes differentes", count,
                (player, data) -> speciesKilled(player), "");
    }

    private static Check visits(long count) {
        return new Check("Visiter " + count + " structures (villages, temples, avant-postes...)", count,
                (player, data) -> structuresVisited(data), "");
    }

    private static Check tierOf(FactionBranch branch, int tier, String label) {
        return new Check(label, 1, (player, data) -> data.tier(branch.key()) >= tier ? 1L : 0L, "");
    }

    // -- Fabriques de recompenses ----------------------------------------------------

    private static Reward slots(int count) {
        return new Reward("+" + (count * 2) + " claims", builder -> builder.claims(count * 2));
    }

    private static Reward claimsReward(int count) {
        return new Reward("+" + count + " claims", builder -> builder.claims(count));
    }

    private static Reward raids(int percent) {
        return new Reward("-" + percent + " % de degats de raid", builder -> builder.raidReduction(percent / 100f));
    }

    private static Reward dungeon(int percent) {
        return new Reward("+" + percent + " % d'XP de donjon", builder -> builder.dungeonXp(percent / 100f));
    }

    private static Reward unlock(String key, String label) {
        return new Reward(label, builder -> builder.unlock(key));
    }

    private static Reward homes(int count) {
        return new Reward(count == 1 ? "1 F home" : count + " F homes", builder -> builder.homes(count));
    }

    private static Reward homeDelay(int seconds) {
        return new Reward("Delai du home : " + seconds + " s", builder -> builder.homeDelay(seconds));
    }

    private static Duration h(long hours) {
        return Duration.ofHours(hours);
    }

    private static Duration d(long days, long hours) {
        return Duration.ofDays(days).plusHours(hours);
    }

    private static final String P = "palamod:";
    private static final String C = "lot 22c";
    private static final String D = "lot 22d";
    /** Les withers du pillage : receptacle, gemmes, ameliorations. */
    private static final String DB = "lot 22d-bis";
    private static final String E = "lot 22e";

    // -- Les branches ----------------------------------------------------------------------

    private static Map<FactionBranch, List<Tier>> build() {
        Map<FactionBranch, List<Tier>> all = new EnumMap<>(FactionBranch.class);
        all.put(FactionBranch.GLOBAL, global());
        all.put(FactionBranch.PILLAGE, pillage());
        all.put(FactionBranch.DEFENSE, defense());
        all.put(FactionBranch.ECONOMY, economy());
        return all;
    }

    private static List<Tier> global() {
        FactionBranch b = FactionBranch.GLOBAL;
        List<Tier> list = new ArrayList<>();
        list.add(new Tier(b, 1, 5_000, List.of(), List.of(claims(3)), h(2), List.of(slots(2))));
        list.add(new Tier(b, 2, 10_000, List.of(), List.of(claims(5), hours(10)), h(6),
                List.of(slots(1), raids(5))));
        list.add(new Tier(b, 3, 15_000, List.of(), List.of(claims(10), travel(100_000)), h(12), List.of(slots(1))));
        list.add(new Tier(b, 4, 20_000, List.of(), List.of(hours(24)), h(12), List.of(slots(1), dungeon(5),
                new Reward("Blason personnalise", FactionPerks.Builder::customBanner))));
        list.add(new Tier(b, 5, 40_000, List.of(), List.of(flag("Poser un blason personnalise",
                        data -> !data.bannerItem.isEmpty()), travel(250_000)), d(1, 0),
                List.of(slots(1), homes(1), raids(10))));
        list.add(new Tier(b, 6, 80_000, List.of(), List.of(flag("Poser un F home", data -> !data.homes.isEmpty()),
                claims(20), hours(48)), d(1, 0), List.of(slots(2),
                new Reward("MOTD de faction", FactionPerks.Builder::motd))));
        list.add(new Tier(b, 7, 140_000, List.of(), List.of(flag("Ecrire un MOTD", data -> !data.motd.isEmpty()),
                travel(500_000)), d(2, 0), List.of(slots(1), homeDelay(5))));
        list.add(new Tier(b, 8, 300_000, List.of(), List.of(claims(25), travel(1_000_000)), d(3, 0),
                List.of(slots(1), raids(15), dungeon(10))));
        list.add(new Tier(b, 9, 600_000, List.of(), List.of(hours(72)), d(4, 0), List.of(slots(1), dungeon(15))));
        list.add(new Tier(b, 10, 1_250_000, List.of(), List.of(claims(30), travel(3_000_000)), d(4, 0),
                List.of(slots(2), homes(2))));
        list.add(new Tier(b, 11, 2_500_000, List.of(), List.of(hours(84)), d(5, 0),
                List.of(slots(1), homeDelay(3), raids(20))));
        list.add(new Tier(b, 12, 5_000_000, List.of(), List.of(travel(5_000_000), hours(96)), d(7, 0),
                List.of(slots(1), homeDelay(0), homes(3), dungeon(20))));
        return list;
    }

    private static List<Tier> pillage() {
        FactionBranch b = FactionBranch.PILLAGE;
        List<Tier> list = new ArrayList<>();
        list.add(new Tier(b, 1, 10_000, List.of(), List.of(crafted(P + "unclaim_finder", 1, "un Unclaim Finder vert", D),
                travel(50_000)), h(2), List.of(unlock("craft:hood_helmet", "Fabrication de la cagoule"),
                unlock("use:dynamite", "Utilisation de la dynamite"))));
        list.add(new Tier(b, 2, 30_000, List.of(), List.of(crafted(P + "dynamite", 100, "dynamites", D),
                counter(FactionStats.GOD_STICK, 2, "Utiliser 2 fois le stick of god", D, null),
                travel(200_000), crafted(P + "unclaim_finder_orange", 1, "un Unclaim Finder orange", D)), h(12),
                List.of(unlock("use:baby_wither", "Utilisation du baby wither"),
                        unlock("craft:receptacle", "Fabrication du receptacle"))));
        list.add(new Tier(b, 3, 60_000, List.of(), List.of(crafted(P + "cave_block", 100, "cave blocks", D),
                crafted(P + "receptacle", 2, "receptacles", DB), crafted(P + "baby_wither_gem", 5,
                        "gemmes de baby wither", DB), crafted(P + "unclaim_finder_red", 1, "un Unclaim Finder rouge", D)),
                h(12), List.of(unlock("use:ninja_dynamite", "Utilisation de la ninja dynamite"))));
        list.add(new Tier(b, 4, 100_000, List.of(), List.of(counter("use:ninja_dynamite", 1,
                        "Utiliser une ninja dynamite", D, P + "ninja_dynamite"),
                crafted(P + "unclaim_finder_blue", 1, "un Unclaim Finder bleu", D),
                counter("spawn:baby_wither", 3, "Invoquer 3 baby withers", DB, P + "baby_wither_gem")), d(1, 0),
                List.of(unlock("craft:chest_explorer", "Fabrication de l'explorateur de coffre"),
                        unlock("use:online_detector", "Utilisation du detecteur de presence"))));
        list.add(new Tier(b, 5, 200_000, List.of(), List.of(counter("regen:wither", 1,
                        "Regenerer un wither avec un receptacle", DB, P + "receptacle"),
                counter("wither:obsidian", 100, "Casser 100 obsidiennes avec un wither", DB, P + "baby_wither_gem"),
                travel(250_000)), d(1, 12), List.of(unlock("use:wither", "Utilisation du wither"),
                unlock("craft:paladium_bow", "Fabrication de l'arc en paladium"),
                unlock("craft:switch_arrow", "Fabrication de la fleche de Switch"))));
        list.add(new Tier(b, 6, 400_000, List.of(), List.of(counter("bow:modifiers", 1,
                        "Poser les modificateurs Speed et Range sur un arc en paladium", D, P + "paladium_bow"),
                counter("use:switch_arrow", 3, "Toucher 3 fois un monstre a la fleche de Switch", D,
                        P + "switch_arrow"),
                counter("spawn:wither", 1, "Invoquer un wither", D, P + "wither_tnt"),
                crafted(P + "baby_wither_gem", 30, "gemmes de baby wither", DB)), d(2, 0),
                List.of(unlock("craft:extrapolated_bucket", "Fabrication du seau extrapole"),
                        unlock("enchant:fake_water_protection", "Enchantement Fake Water Protection"))));
        list.add(new Tier(b, 7, 750_000, List.of(), List.of(crafted(P + "extrapolated_bucket", 100, "seaux extrapoles",
                        D), counter("use:extrapolated_bucket", 1, "Retirer un liquide au seau extrapole", D,
                        P + "extrapolated_bucket"),
                counter("protect:fake_water", 60, "Bloquer 60 s de degats avec Fake Water Protection", D,
                        P + "extrapolated_bucket"),
                counter("enchant:unclaim_helmet", 1, "Poser l'enchantement Unclaim Helmet", D, P + "hood_helmet")),
                d(3, 0), List.of(unlock("craft:wither_resistance_upgrade",
                        "Fabrication de l'amelioration de resistance de wither"),
                unlock("use:big_dynamite", "Utilisation de la big dynamite"))));
        list.add(new Tier(b, 8, 1_250_000, List.of(), List.of(crafted(P + "big_dynamite", 5_000, "big dynamites", D),
                counter("use:big_dynamite", 1, "Utiliser une big dynamite", D, P + "big_dynamite")), d(3, 12),
                List.of(unlock("use:passive_wither", "Utilisation du wither passif"))));
        list.add(new Tier(b, 9, 2_500_000, List.of(), List.of(counter("spawn:passive_wither", 3,
                        "Invoquer 3 withers passifs", DB, P + "passive_wither_gem"), travel(500_000), visits(7),
                crafted(P + "passive_wither_gem", 15, "gemmes de wither passif", DB)), d(4, 12),
                List.of(unlock("use:funny_wither", "Utilisation du wither amusant"))));
        list.add(new Tier(b, 10, 5_000_000, List.of(), List.of(crafted(P + "wither_resistance_upgrade", 10,
                        "ameliorations de resistance de wither", DB), travel(1_000_000), species(30), visits(10)),
                d(5, 12), List.of(unlock("use:predator_wither", "Utilisation du wither predateur"),
                unlock("craft:wither_slime_upgrade", "Fabrication de l'amelioration slime de wither"))));
        list.add(new Tier(b, 11, 10_000_000, List.of(), List.of(counter("spawn:predator_wither", 2,
                        "Invoquer 2 withers predateurs", DB, P + "predator_wither_gem"), species(50), visits(15),
                crafted(P + "predator_wither_gem", 7, "gemmes de wither predateur", DB),
                crafted(P + "wither_slime_upgrade", 20, "ameliorations slime de wither", DB)), d(7, 12),
                List.of(unlock("use:thirsty_wither", "Utilisation du wither assoiffe"),
                        unlock("craft:stealth_upgrade", "Fabrication de l'amelioration de discretion"))));
        list.add(new Tier(b, 12, 15_000_000, List.of(), List.of(counter("spawn:thirsty_wither", 3,
                        "Invoquer 3 withers assoiffes", DB, P + "thirsty_wither_gem"),
                crafted(P + "stealth_upgrade", 1, "une amelioration de discretion", DB), travel(3_000_000),
                crafted(P + "thirsty_wither_gem", 50, "gemmes de wither assoiffe", DB),
                crafted(P + "stealth_upgrade", 50, "ameliorations de discretion", DB)), d(14, 0),
                List.of(unlock("use:supreme_wither", "Utilisation du wither supreme"))));
        return list;
    }

    private static List<Tier> defense() {
        FactionBranch b = FactionBranch.DEFENSE;
        List<Tier> list = new ArrayList<>();
        list.add(new Tier(b, 1, 10_000, List.of(), List.of(mined(Blocks.OBSIDIAN, 2_000, "obsidiennes"),
                mined(Blocks.SANDSTONE, 2_000, "gres")), h(2),
                List.of(unlock("craft:voyance_amulet", "Fabrication de l'amulette de voyance"), claimsReward(10))));
        list.add(new Tier(b, 2, 30_000, List.of(), List.of(mined(Blocks.OBSIDIAN, 30_000, "obsidiennes"),
                mined(Blocks.SANDSTONE, 10_000, "gres")), h(12),
                List.of(unlock("craft:hardened_obsidian", "Fabrication de l'obsidienne renforcee"),
                        unlock("use:spikes_basic", "Spikes en bois, fer, or, diamant (et obsidienne)"),
                        claimsReward(25))));
        list.add(new Tier(b, 3, 60_000, List.of(), List.of(crafted(P + "amethyst_builder_wand", 10,
                        "outils de construction en amethyste", C), crafted(P + "slimepad", 500, "slimepads", C),
                counter(FactionStats.BUILDER_WAND, 1, "Utiliser un outil de construction", C, null),
                mined(Blocks.SANDSTONE, 30_000, "gres"),
                counter("magical:hardened", 1, "Retirer une obsidienne renforcee a l'outil magique", C,
                        P + "magical_tool")), h(12),
                List.of(unlock("craft:coordinate_jammer", "Fabrication du brouilleur de coordonnees"),
                        unlock("craft:lava_sponge", "Fabrication de l'eponge a lave"),
                        unlock("use:poison_obsidian", "Obsidienne empoisonnee"), claimsReward(30))));
        list.add(new Tier(b, 4, 100_000, List.of(), List.of(counter("kill:baby_wither", 1, "Tuer un baby wither", DB,
                P + "baby_wither_gem"), mined(Blocks.OBSIDIAN, 75_000, "obsidiennes")), d(1, 0),
                List.of(unlock("craft:aggro_stone", "Fabrication de la pierre d'aggro"),
                        unlock("use:spikes_amethyst", "Spikes en amethyste (et obsidienne)"), claimsReward(35))));
        list.add(new Tier(b, 5, 200_000, List.of(), List.of(mined(Blocks.SANDSTONE, 250_000, "gres"),
                crafted(P + "amethyst_spike", 100, "spikes en amethyste", C)), d(1, 0),
                List.of(unlock("use:lava_obsidian", "Obsidienne de lave"),
                        unlock("use:spikes_titane", "Spikes en titane (et obsidienne)"),
                        unlock("craft:fake_water", "Fabrication de la Fake Water"), claimsReward(30))));
        list.add(new Tier(b, 6, 400_000, List.of(), List.of(crafted(P + "fake_water_bucket", 1_000,
                "seaux de Fake Water", C), mined(Blocks.OBSIDIAN, 150_000, "obsidiennes")), d(2, 0),
                List.of(unlock("use:pistol_tank", "Utilisation du pistolet a liquide"), claimsReward(30))));
        list.add(new Tier(b, 7, 800_000, List.of(), List.of(crafted(P + "fake_water_bucket", 5_000,
                "seaux de Fake Water", C), mined(Blocks.OBSIDIAN, 150_000, "obsidiennes")), d(3, 0),
                List.of(unlock("use:home_remover", "Utilisation du home remover"),
                        unlock("use:spikes_paladium", "Spikes en paladium (et obsidienne)"), claimsReward(35))));
        list.add(new Tier(b, 8, 1_250_000, List.of(), List.of(crafted(P + "paladium_spike", 1_000,
                "spikes en paladium", C)), d(3, 12),
                List.of(unlock("use:boom_obsidian", "Boom obsidienne"),
                        unlock("use:sulfuric_obsidian", "Obsidienne sulfurique"),
                        unlock("use:big_obsidian", "Obsidienne compacte"),
                        unlock("craft:amethyst_anvil", "Fabrication de l'enclume en amethyste"), claimsReward(30))));
        list.add(new Tier(b, 9, 2_250_000, List.of(), List.of(crafted(P + "boom_obsidian", 300_000, "boom obsidiennes",
                C), crafted(P + "amethyst_anvil", 1_000, "enclumes en amethyste", C),
                crafted(P + "big_obsidian", 10_000, "obsidiennes compactes", C)), d(4, 0),
                List.of(unlock("craft:titane_anvil", "Fabrication de l'enclume en titane"),
                        unlock("use:knockback_obsidian", "Knockback obsidienne"), claimsReward(45))));
        list.add(new Tier(b, 10, 5_000_000, List.of(), List.of(crafted(P + "titane_anvil", 1_000,
                "enclumes en titane", C), crafted(P + "knockback_obsidian", 150_000, "knockback obsidiennes", C)),
                d(5, 12), List.of(unlock("use:mega_boom_obsidian", "Mega boom obsidienne"),
                unlock("use:anti_aggro_obsidian", "Obsidienne anti-aggro"), claimsReward(55))));
        list.add(new Tier(b, 11, 10_000_000, List.of(), List.of(crafted(P + "mega_boom_obsidian", 250_000,
                "mega boom obsidiennes", C), crafted(P + "anti_aggro_obsidian", 5_000, "obsidiennes anti-aggro", C)),
                d(7, 12), List.of(unlock("use:slime_obsidian", "Slime obsidienne"),
                unlock("craft:paladium_anvil", "Fabrication de l'enclume en paladium"), claimsReward(125))));
        list.add(new Tier(b, 12, 15_000_000, List.of(), List.of(crafted(P + "slime_obsidian", 100_000,
                "slime obsidiennes", C), crafted(P + "paladium_anvil", 1_000, "enclumes en paladium", C)), d(14, 0),
                List.of(unlock("use:frozen_obsidian", "Obsidienne gelee"), claimsReward(200))));
        return list;
    }

    private static List<Tier> economy() {
        FactionBranch b = FactionBranch.ECONOMY;
        String gold = P + "liquid_gold";
        List<Tier> list = new ArrayList<>();
        list.add(new Tier(b, 1, 500_000, List.of(), List.of(), Duration.ofMinutes(30),
                List.of(new Reward("Reserve d'or, palier 1", builder -> builder.reserveTier(1)))));
        list.add(new Tier(b, 2, 0, List.of(new ItemCost(gold, 3, "or liquide")), List.of(), h(1),
                List.of(new Reward("+100 000 $ par or liquide", builder -> builder.goldValue(100_000)))));
        list.add(new Tier(b, 3, 1_000_000, List.of(new ItemCost(gold, 10, "or liquide")),
                List.of(counter(FactionStats.RESERVE_INVESTS, 1, "Investir une fois dans la reserve", E, gold)), h(12),
                List.of(new Reward("Reserve d'or, palier 2", builder -> builder.reserveTier(2)),
                        new Reward("+100 000 $ par or liquide", builder -> builder.goldValue(100_000)))));
        list.add(new Tier(b, 4, 2_500_000, List.of(new ItemCost(gold, 25, "or liquide")),
                List.of(counter(FactionStats.RESERVE_TIER, 2, "Construire une reserve de palier 2", E, gold)),
                d(1, 0), List.of(new Reward("+100 000 $ par or liquide", builder -> builder.goldValue(100_000)),
                new Reward("Interets x2", builder -> builder.interest(2)))));
        list.add(new Tier(b, 5, 0, List.of(new ItemCost(gold, 50, "or liquide")),
                List.of(counter(FactionStats.RESERVE_INVESTS, 2, "Investir deux fois dans la reserve", E, gold)),
                d(2, 0), List.of(new Reward("Reserve d'or, palier 3", builder -> builder.reserveTier(3)),
                new Reward("+100 000 $ par or liquide", builder -> builder.goldValue(100_000)))));
        list.add(new Tier(b, 6, 10_000_000, List.of(new ItemCost(gold, 80, "or liquide")),
                List.of(counter(FactionStats.RESERVE_TIER, 3, "Construire une reserve de palier 3", E, gold)),
                d(3, 0), List.of(new Reward("Interets x3", builder -> builder.interest(3)))));
        return list;
    }

    // -- Ameliorer ----------------------------------------------------------------------

    private static int countItem(Inventory inventory, Item item) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void takeItem(Inventory inventory, Item item, int count) {
        int left = count;
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
    }

    /** Pourquoi le palier suivant ne peut pas commencer, ou {@code null}. */
    public static String blocker(ServerPlayer player, FactionBranch branch) {
        FactionData data = FactionApi.data(player);
        if (!data.exists()) {
            return "Tu n'as pas de faction";
        }
        if (data.upgradeEnd(branch.key()) > 0L) {
            return "Une amelioration est deja en cours dans cette branche";
        }
        Tier next = tier(branch, data.tier(branch.key()) + 1);
        if (next == null) {
            return "Branche terminee";
        }
        for (Check check : next.checks()) {
            if (!check.done(player, data)) {
                return check.pending().isEmpty() ? "Condition manquante : " + check.label()
                        : check.label() + " (" + check.pending() + ")";
            }
        }
        for (ItemCost cost : next.items()) {
            Optional<Item> item = cost.item();
            if (item.isEmpty()) {
                return cost.label() + " : " + E;
            }
            if (countItem(player.getInventory(), item.get()) < cost.count() && !player.getAbilities().instabuild) {
                return "Il faut " + cost.count() + " " + cost.label() + " sur toi";
            }
        }
        if (data.bank() < next.cost()) {
            return "La banque de faction n'a pas " + MoneyFormat.exact(next.cost()) + " $";
        }
        return null;
    }

    /** Lance l'amelioration du palier suivant : paiement, puis la duree reelle. */
    public static String start(ServerPlayer player, FactionBranch branch) {
        String refusal = blocker(player, branch);
        if (refusal != null) {
            return refusal;
        }
        FactionData data = FactionApi.data(player);
        Tier next = tier(branch, data.tier(branch.key()) + 1);
        if (next.cost() > 0 && !FactionApi.payFromBank(player, next.cost(),
                "Arbre : " + branch.label() + " " + next.number())) {
            return "La banque de faction n'a pas " + MoneyFormat.exact(next.cost()) + " $";
        }
        if (!player.getAbilities().instabuild) {
            for (ItemCost cost : next.items()) {
                cost.item().ifPresent(item -> takeItem(player.getInventory(), item, cost.count()));
            }
        }
        data.upgrades.put(branch.key(), System.currentTimeMillis() + next.time().toMillis());
        return null;
    }

    /** Termine les ameliorations echues ; {@code force} les termine toutes (operateurs). */
    public static void complete(ServerPlayer player, boolean force) {
        FactionData data = FactionApi.data(player);
        if (!data.exists() || data.upgrades.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (FactionBranch branch : FactionBranch.values()) {
            long end = data.upgradeEnd(branch.key());
            if (end > 0L && (force || end <= now)) {
                data.upgrades.remove(branch.key());
                int tier = data.tier(branch.key()) + 1;
                data.tiers.put(branch.key(), tier);
                FactionApi.tell(player, "Arbre de technologie : branche " + branch.label() + ", palier " + tier
                        + " termine.", ChatFormatting.AQUA);
                Tier done = tier(branch, tier);
                if (done != null) {
                    for (Reward reward : done.rewards()) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("  + " + reward.label())
                                .withStyle(ChatFormatting.GREEN));
                    }
                }
            }
        }
    }

    /** Fixe le palier d'une branche (operateurs, essais). */
    public static void setTier(ServerPlayer player, FactionBranch branch, int tier) {
        FactionData data = FactionApi.data(player);
        data.upgrades.remove(branch.key());
        data.tiers.put(branch.key(), Math.max(0, Math.min(tiers(branch).size(), tier)));
    }

    /** Vrai si l'objet ou l'effet est debloque par l'arbre. */
    public static boolean unlocked(ServerPlayer player, String key) {
        return FactionPerks.of(FactionApi.data(player)).unlocked(key);
    }

    /** Le palier qui debloque une cle, et le libelle de sa recompense : pour dire au joueur ou aller. */
    public record Unlock(Tier tier, String label) {
    }

    public static Optional<Unlock> unlockOf(String key) {
        for (FactionBranch branch : FactionBranch.values()) {
            for (Tier tier : tiers(branch)) {
                for (Reward reward : tier.rewards()) {
                    FactionPerks.Builder builder = new FactionPerks.Builder();
                    reward.apply().accept(builder);
                    if (builder.unlocks.contains(key)) {
                        return Optional.of(new Unlock(tier, reward.label()));
                    }
                }
            }
        }
        return Optional.empty();
    }
}

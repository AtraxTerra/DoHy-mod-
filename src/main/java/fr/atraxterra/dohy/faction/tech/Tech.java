package fr.atraxterra.dohy.faction.tech;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionBranch;
import fr.atraxterra.dohy.faction.FactionData;
import fr.atraxterra.dohy.faction.FactionTree;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Le moteur des technologies de la nation (chapitre 23, lot 23b) : les neuf domaines de
 * {@link TechConfig}, l'etat de recherche de la nation, et l'ancien arbre fondu dedans.
 *
 * <ul>
 *   <li><b>Les domaines nouveaux</b> (Energie, Industrie, Chimie, Agriculture, Nucleaire,
 *   Espace) se recherchent palier par palier : le palier d'avant, les prerequis des autres
 *   domaines, l'argent de la banque nationale et des pieces de NTM au lancement, puis les points
 *   de recherche des laboratoires ({@link ResearchLabBlockEntity}, lot 23c). Le palier I est
 *   acquis d'office, sauf dans l'Espace.</li>
 *   <li><b>Les domaines repris</b> (Gouvernement, Economie, Defense) se gagnent par les etapes
 *   de l'ancien arbre ({@link FactionTree}, intact) : un palier est acquis quand toutes ses
 *   etapes le sont et que ses prerequis tiennent. Chaque etape garde sa recompense et ses
 *   conditions ; chaque ancienne branche garde son amelioration a elle.</li>
 * </ul>
 *
 * <p>Les recherches des domaines nouveaux se menent une a la fois, puis deux et trois avec le
 * Gouvernement ({@link TechPerks#parallel()}) ; les points des laboratoires se partagent entre
 * elles a parts egales.
 */
public final class Tech {

    /** Au-dela, une chaine de prerequis est tenue pour une boucle (le controle de la CI l'interdit). */
    private static final int DEPTH = 16;

    private Tech() {
    }

    // -- Les etapes de l'ancien arbre ------------------------------------------------------------

    /** Une etape de l'ancien arbre : « global:3 », la troisieme de la branche Globale. */
    public record Step(FactionBranch branch, int number) {
        public static Optional<Step> parse(String text) {
            int colon = text.indexOf(':');
            if (colon < 0) {
                return Optional.empty();
            }
            FactionBranch branch = FactionBranch.byKey(text.substring(0, colon));
            try {
                int number = Integer.parseInt(text.substring(colon + 1));
                return branch == null || FactionTree.tier(branch, number) == null ? Optional.empty()
                        : Optional.of(new Step(branch, number));
            } catch (NumberFormatException error) {
                return Optional.empty();
            }
        }

        public boolean done(FactionData data) {
            return data.tier(branch.key()) >= number;
        }

        public FactionTree.Tier tier() {
            return FactionTree.tier(branch, number);
        }

        /** « Globale 3 ». */
        public String label() {
            return branch.label() + " " + number;
        }
    }

    public static List<Step> steps(TechConfig.Tier tier) {
        List<Step> steps = new ArrayList<>();
        for (String text : tier.legacy()) {
            Step.parse(text).ifPresent(steps::add);
        }
        return steps;
    }

    /** Les anciennes branches d'un domaine repris, dans l'ordre : la Defense en a deux, Defense et Pillage. */
    public static List<FactionBranch> tracks(TechConfig.Domain domain) {
        List<FactionBranch> tracks = new ArrayList<>();
        for (TechConfig.Tier tier : domain.tiers()) {
            for (Step step : steps(tier)) {
                if (!tracks.contains(step.branch())) {
                    tracks.add(step.branch());
                }
            }
        }
        return tracks;
    }

    /** Les etapes faites et a faire d'un domaine repris. */
    public static int stepsDone(FactionData data, TechConfig.Domain domain) {
        int done = 0;
        for (TechConfig.Tier tier : domain.tiers()) {
            for (Step step : steps(tier)) {
                if (step.done(data)) {
                    done++;
                }
            }
        }
        return done;
    }

    public static int stepsTotal(TechConfig.Domain domain) {
        int total = 0;
        for (TechConfig.Tier tier : domain.tiers()) {
            total += steps(tier).size();
        }
        return total;
    }

    // -- Les paliers atteints ----------------------------------------------------------------------

    /**
     * Le palier atteint dans ce domaine, 0 a 6 : celui des recherches (les paliers d'office compris)
     * dans un domaine nouveau, celui des etapes dans un domaine repris. Les machines et les
     * prerequis du palier I d'un domaine repris sont acquis d'office ({@link #reached}).
     */
    public static int level(FactionData data, TechConfig.Domain domain) {
        return level(data, domain, 0);
    }

    public static int level(FactionData data, String domain) {
        TechConfig.Domain found = TechConfig.domain(domain);
        return found == null ? 0 : level(data, found);
    }

    private static int level(FactionData data, TechConfig.Domain domain, int depth) {
        if (!domain.merged()) {
            return Math.min(domain.max(), data.techTier(domain.id(), domain.startLevel()));
        }
        int level = 0;
        for (TechConfig.Tier tier : domain.tiers()) {
            boolean steps = true;
            for (Step step : steps(tier)) {
                steps &= step.done(data);
            }
            if (!steps || !requiresMet(data, tier, depth + 1)) {
                break;
            }
            level = tier.tier();
        }
        return level;
    }

    /** Vrai si le palier est acquis : par la recherche, par ses etapes, ou d'office. */
    public static boolean reached(FactionData data, TechConfig.Domain domain, int tier) {
        return reached(data, domain, tier, 0);
    }

    private static boolean reached(FactionData data, TechConfig.Domain domain, int tier, int depth) {
        if (depth > DEPTH) {
            return false;
        }
        return tier <= domain.startLevel() || tier <= level(data, domain, depth);
    }

    /** Un prerequis « energie:2 ». */
    public static boolean reached(FactionData data, String need) {
        return reached(data, need, 0);
    }

    private static boolean reached(FactionData data, String need, int depth) {
        int colon = need.indexOf(':');
        TechConfig.Domain domain = colon < 0 ? null : TechConfig.domain(need.substring(0, colon));
        if (domain == null) {
            return false;
        }
        try {
            return reached(data, domain, Integer.parseInt(need.substring(colon + 1)), depth);
        } catch (NumberFormatException error) {
            return false;
        }
    }

    private static boolean requiresMet(FactionData data, TechConfig.Tier tier, int depth) {
        for (String need : tier.requires()) {
            if (!reached(data, need, depth)) {
                return false;
            }
        }
        return true;
    }

    /** « Chimie II · Petrole » pour un prerequis « chimie:2 ». */
    public static String label(String need) {
        int colon = need.indexOf(':');
        TechConfig.Domain domain = colon < 0 ? null : TechConfig.domain(need.substring(0, colon));
        if (domain == null) {
            return need;
        }
        try {
            TechConfig.Tier tier = domain.tier(Integer.parseInt(need.substring(colon + 1)));
            return tier == null ? need : tier.title(domain);
        } catch (NumberFormatException error) {
            return need;
        }
    }

    /** Le palier qui deverrouille cette machine, s'il y en a un (lot 23d s'en servira pour la pose). */
    public static Optional<TechConfig.Tier> tierOf(String id) {
        for (TechConfig.Domain domain : TechConfig.domains()) {
            for (TechConfig.Tier tier : domain.tiers()) {
                if (tier.unlocks(id)) {
                    return Optional.of(tier);
                }
            }
        }
        return Optional.empty();
    }

    // -- Le compte de la nation --------------------------------------------------------------------

    /**
     * Les paliers acquis, tous domaines confondus : les paliers d'office des domaines nouveaux
     * comptent, pas le palier I d'un domaine repris tant que ses etapes ne sont pas faites.
     */
    public static int researched(FactionData data) {
        int done = 0;
        for (TechConfig.Domain domain : TechConfig.domains()) {
            done += level(data, domain);
        }
        return done;
    }

    public static int total() {
        int total = 0;
        for (TechConfig.Domain domain : TechConfig.domains()) {
            total += domain.max();
        }
        return total;
    }

    /** Les recherches des domaines nouveaux en cours (celles que borne le Gouvernement). */
    public static int running(FactionData data) {
        int running = 0;
        for (String id : data.techRunning()) {
            if (TechConfig.domain(id) != null) {
                running++;
            }
        }
        return running;
    }

    /** Tout ce qui avance : les recherches, et les etapes de l'ancien arbre en amelioration. */
    public static int researching(FactionData data) {
        int running = running(data);
        for (FactionBranch branch : FactionBranch.values()) {
            if (data.upgradeEnd(branch.key()) > 0L) {
                running++;
            }
        }
        return running;
    }

    /** Une recherche ou une etape peut se lancer tout de suite. */
    public static boolean ready(ServerPlayer player) {
        FactionData data = FactionApi.data(player);
        if (!data.exists()) {
            return false;
        }
        for (TechConfig.Domain domain : TechConfig.domains()) {
            if (domain.merged()) {
                for (FactionBranch branch : tracks(domain)) {
                    if (data.upgradeEnd(branch.key()) <= 0L && FactionTree.blocker(player, branch) == null) {
                        return true;
                    }
                }
            } else if (blocker(player, domain) == null) {
                return true;
            }
        }
        return false;
    }

    // -- Rechercher (domaines nouveaux) --------------------------------------------------------------

    /** Le prochain palier a rechercher, {@code null} au bout du domaine. */
    public static TechConfig.Tier next(FactionData data, TechConfig.Domain domain) {
        return domain.tier(level(data, domain) + 1);
    }

    private static final TechConfig.Cost FREE = new TechConfig.Cost(0L, 1L, List.of());

    public static TechConfig.Cost cost(TechConfig.Tier tier) {
        return tier.cost().orElse(FREE);
    }

    /** Les points de recherche qu'il faut a ce palier. */
    public static long target(TechConfig.Tier tier) {
        return Math.max(1L, cost(tier).points());
    }

    /** Les points deja apportes a la recherche en cours de ce domaine, 0 sans recherche. */
    public static long progress(FactionData data, TechConfig.Domain domain) {
        return Math.max(0L, data.techPoints(domain.id()));
    }

    /** Vrai si une recherche est en cours dans ce domaine. */
    public static boolean researching(FactionData data, TechConfig.Domain domain) {
        return data.techRunning(domain.id());
    }

    /** Les domaines nouveaux ou une recherche est en cours, dans l'ordre de l'ecran. */
    public static List<TechConfig.Domain> runningDomains(FactionData data) {
        List<TechConfig.Domain> running = new ArrayList<>();
        for (TechConfig.Domain domain : TechConfig.domains()) {
            if (!domain.merged() && data.techRunning(domain.id()) && next(data, domain) != null) {
                running.add(domain);
            }
        }
        return running;
    }

    /** Pourquoi le palier suivant ne peut pas se rechercher, ou {@code null}. */
    public static String blocker(ServerPlayer player, TechConfig.Domain domain) {
        FactionData data = FactionApi.data(player);
        if (!data.exists()) {
            return "Tu n'as pas de nation";
        }
        if (domain.merged()) {
            return domain.name() + " avance par les etapes de l'ancien arbre";
        }
        if (data.techRunning(domain.id())) {
            return "Une recherche est deja en cours dans ce domaine";
        }
        TechConfig.Tier next = next(data, domain);
        if (next == null) {
            return "Domaine termine";
        }
        for (String need : next.requires()) {
            if (!reached(data, need)) {
                return "Il faut d'abord " + label(need);
            }
        }
        int slots = TechPerks.of(data).parallel();
        int running = running(data);
        if (running >= slots) {
            return slots == 1 ? "Une recherche a la fois : le Gouvernement IV en permet deux"
                    : running + " recherches en cours, " + slots + " au plus";
        }
        if (data.labs().isEmpty()) {
            return "Il faut d'abord un laboratoire de recherche, pose dans un de tes claims";
        }
        TechConfig.Cost cost = cost(next);
        boolean creative = player.getAbilities().instabuild;
        List<ResearchLabBlockEntity> labs = creative ? List.of() : loadedLabs(player, data);
        for (TechConfig.ItemCost item : cost.items()) {
            Optional<Item> found = item.item();
            if (found.isPresent() && !creative
                    && count(player.getInventory(), found.get()) + delivered(labs, found.get()) < item.count()) {
                return "Il faut " + item.count() + " " + item.label() + ", sur toi ou au labo";
            }
        }
        if (data.bank() < cost.money()) {
            return "La banque nationale n'a pas " + MoneyFormat.exact(cost.money()) + " $";
        }
        return null;
    }

    /**
     * Lance la recherche du palier suivant : l'argent et les pieces tout de suite (celles du joueur
     * d'abord, puis celles livrees aux laboratoires charges de la nation, lot 23i), puis les points
     * des laboratoires.
     */
    public static String start(ServerPlayer player, TechConfig.Domain domain) {
        String refusal = blocker(player, domain);
        if (refusal != null) {
            return refusal;
        }
        FactionData data = FactionApi.data(player);
        TechConfig.Tier next = next(data, domain);
        TechConfig.Cost cost = cost(next);
        if (cost.money() > 0L && !FactionApi.payFromBank(player, cost.money(), "Recherche : " + next.title(domain))) {
            return "La banque nationale n'a pas " + MoneyFormat.exact(cost.money()) + " $";
        }
        if (!player.getAbilities().instabuild) {
            List<ResearchLabBlockEntity> labs = loadedLabs(player, data);
            for (TechConfig.ItemCost item : cost.items()) {
                Optional<Item> found = item.item();
                if (found.isEmpty()) {
                    continue;
                }
                int left = item.count() - take(player.getInventory(), found.get(), item.count());
                for (ResearchLabBlockEntity lab : labs) {
                    if (left <= 0) {
                        break;
                    }
                    left -= lab.takePart(found.get(), left);
                }
            }
        }
        data.startTech(domain.id(), System.currentTimeMillis());
        return null;
    }

    /** Les laboratoires de la liste de la nation dont le chunk est charge : la que sont les pieces livrees. */
    public static List<ResearchLabBlockEntity> loadedLabs(ServerPlayer player, FactionData data) {
        List<ResearchLabBlockEntity> labs = new ArrayList<>();
        MinecraftServer server = player.level().getServer();
        if (server == null || !data.exists()) {
            return labs;
        }
        for (String key : data.labs()) {
            ResearchLabBlockEntity lab = ResearchLabBlockEntity.at(server, key);
            if (lab != null && !lab.isRemoved()) {
                labs.add(lab);
            }
        }
        return labs;
    }

    /** Combien de cet objet ces laboratoires gardent. */
    public static int delivered(List<ResearchLabBlockEntity> labs, Item item) {
        int total = 0;
        for (ResearchLabBlockEntity lab : labs) {
            total += lab.partCount(item);
        }
        return total;
    }

    // -- Les points des laboratoires (lot 23c) -------------------------------------------------------

    /**
     * Ce que coute en FE un travail d'une machine de DoHy (moulin, machine a pain, serveur de
     * trading) pour une nation qui en efface {@code cut} pour cent (lot 23e : Energie III -10 %,
     * V -15 %) ; jamais moins de 1 FE pour un travail qui en demandait.
     */
    public static int machineEnergy(int base, int cut) {
        if (base <= 0) {
            return base;
        }
        int kept = 100 - Math.max(0, Math.min(90, cut));
        return (int) Math.max(1L, (long) base * kept / 100L);
    }

    /**
     * Ce que dure un travail de {@code ticks} pour une machine agricole qui va {@code speed} pour cent
     * plus vite (lot 23f : Agriculture III +10 %, V +20 %) ; jamais moins d'un tick.
     */
    public static int faster(int ticks, int speed) {
        if (ticks <= 1 || speed <= 0) {
            return ticks;
        }
        return (int) Math.max(1L, Math.round(ticks * 100.0d / (100 + speed)));
    }

    /** L'energie d'un laboratoire par tick de marche, en FE, moins ce qu'effacent les technologies de l'Energie. */
    public static int labEnergy(FactionData data) {
        int base = ResearchLabConfig.config().energyPerTick();
        int cut = Math.max(0, Math.min(90, TechPerks.of(data).value("lab_energy")));
        return Math.max(1, base * (100 - cut) / 100);
    }

    /** L'apport des pays a la recherche, en pour cent : chaque pays, la capitale, un plafond, puis le Gouvernement V. */
    public static int countries(ServerPlayer player, TechPerks perks) {
        if (!fr.atraxterra.dohy.earth.EarthNations.active()) {
            return 0;
        }
        ResearchLabConfig.Config config = ResearchLabConfig.config();
        int held = fr.atraxterra.dohy.earth.EarthNations.heldBy(player).size();
        boolean capital = fr.atraxterra.dohy.earth.EarthNations.nation(player).capital() != 0;
        int base = Math.min(config.countryCap(), held * config.countryPercent()
                + (capital ? config.capitalPercent() : 0));
        return base * (100 + perks.value("research_per_country")) / 100;
    }

    /** Ce que valent les points d'un laboratoire pour cette nation : vitesse de recherche, puis apport des pays. */
    public static double multiplier(ServerPlayer player) {
        TechPerks perks = TechPerks.of(player);
        return (1.0d + perks.research() / 100.0d) * (1.0d + countries(player, perks) / 100.0d);
    }

    /** Les points par heure que font en ce moment les laboratoires charges de la nation, bonus compris. */
    public static long rate(ServerPlayer player) {
        return Math.round(ResearchLabBlockEntity.rawRate(player.getUUID()) * multiplier(player));
    }

    /**
     * Verse des points bruts d'un laboratoire aux recherches en cours, multiplies, a parts egales ;
     * une recherche remplie s'acheve aussitot.
     *
     * @return les points bruts pris ; le reste reste au laboratoire
     */
    public static long feed(ServerPlayer player, long raw, double multiplier) {
        FactionData data = FactionApi.data(player);
        if (raw <= 0L || !data.exists()) {
            return 0L;
        }
        migrate(data);
        List<TechConfig.Domain> open = runningDomains(data);
        long need = 0L;
        for (TechConfig.Domain domain : open) {
            need += Math.max(0L, target(next(data, domain)) - progress(data, domain));
        }
        if (need <= 0L) {
            return 0L;
        }
        double factor = Math.max(0.01d, multiplier);
        long used = Math.min(raw, (long) Math.ceil(need / factor));
        long left = Math.min(need, (long) Math.floor(used * factor));
        while (left > 0L && !open.isEmpty()) {
            long share = Math.max(1L, left / open.size());
            for (TechConfig.Domain domain : List.copyOf(open)) {
                long remaining = target(next(data, domain)) - progress(data, domain);
                long given = Math.min(remaining, Math.min(share, left));
                data.setTechPoints(domain.id(), progress(data, domain) + given);
                left -= given;
                if (given >= remaining) {
                    open.remove(domain);
                }
                if (left <= 0L) {
                    break;
                }
            }
        }
        complete(player, false);
        return used;
    }

    /** Les heures d'une recherche du lot 23b, au palier de cout (l'Espace paie au niveau du palier suivant). */
    private static double legacyHours(TechConfig.Domain domain, int tier) {
        int level = tier + (domain.startLevel() == 0 ? 1 : 0);
        return switch (level) {
            case 2 -> 6.0d;
            case 3 -> 12.0d;
            case 4 -> 24.0d;
            case 5 -> 48.0d;
            case 6 -> 72.0d;
            case 7 -> 96.0d;
            default -> 24.0d;
        };
    }

    /**
     * Une recherche lancee au lot 23b avait une fin prevue et pas de points : elle garde la part
     * du temps deja ecoulee, en points. Une recherche echue est remplie.
     */
    private static void migrate(FactionData data) {
        long now = System.currentTimeMillis();
        for (String id : data.techRunning()) {
            if (data.techPoints(id) >= 0L) {
                continue;
            }
            TechConfig.Domain domain = TechConfig.domain(id);
            TechConfig.Tier next = domain == null ? null : next(data, domain);
            if (next == null || domain.merged()) {
                data.stopTech(id);
                continue;
            }
            double total = legacyHours(domain, next.tier()) * 3_600_000.0d;
            double done = Math.max(0.0d, Math.min(1.0d, 1.0d - (data.techStart(id) - now) / total));
            data.resumeTech(id, now, Math.round(target(next) * done));
        }
    }

    /**
     * Termine ce qui est rempli : les etapes de l'ancien arbre, puis les recherches qui ont leurs
     * points ; {@code force} termine tout (operateurs). Un palier gagne, recherche ou par ses
     * etapes, est annonce.
     */
    public static void complete(ServerPlayer player, boolean force) {
        FactionData data = FactionApi.data(player);
        if (!data.exists()) {
            return;
        }
        migrate(data);
        Map<String, Integer> before = levels(data);
        FactionTree.complete(player, force);
        for (String id : data.techRunning()) {
            TechConfig.Domain domain = TechConfig.domain(id);
            TechConfig.Tier next = domain == null || domain.merged() ? null : next(data, domain);
            if (next == null) {
                data.stopTech(id);
                continue;
            }
            if (force || progress(data, domain) >= target(next)) {
                data.stopTech(id);
                data.setTechTier(id, next.tier());
            }
        }
        Map<String, Integer> after = levels(data);
        for (TechConfig.Domain domain : TechConfig.domains()) {
            int from = before.getOrDefault(domain.id(), 0);
            int to = after.getOrDefault(domain.id(), 0);
            for (int tier = from + 1; tier <= to; tier++) {
                TechConfig.Tier gained = domain.tier(tier);
                if (gained != null && (domain.merged() || tier > domain.startLevel())) {
                    announce(player, domain, gained);
                }
            }
        }
    }

    /** Ajoute des points aux recherches en cours (operateurs, essais), comme un laboratoire sans bonus. */
    public static long grant(ServerPlayer player, long points) {
        return feed(player, points, 1.0d);
    }

    private static Map<String, Integer> levels(FactionData data) {
        Map<String, Integer> levels = new HashMap<>();
        for (TechConfig.Domain domain : TechConfig.domains()) {
            levels.put(domain.id(), level(data, domain));
        }
        return levels;
    }

    private static void announce(ServerPlayer player, TechConfig.Domain domain, TechConfig.Tier tier) {
        FactionApi.tell(player, "Technologie acquise : " + tier.title(domain) + ".", ChatFormatting.AQUA);
        for (TechConfig.Bonus bonus : tier.bonuses()) {
            player.sendSystemMessage(Component.literal("  + " + TechPerks.label(bonus)).withStyle(ChatFormatting.GREEN));
        }
        int machines = tier.machines().size();
        if (machines > 0) {
            player.sendSystemMessage(Component.literal("  + " + machines + (machines > 1 ? " machines" : " machine")
                    + " a poser (detail : /f techno)").withStyle(ChatFormatting.GREEN));
        }
    }

    /**
     * Fixe le palier d'un domaine (operateurs, essais). Un domaine repris fixe ses anciennes
     * branches a la derniere etape du palier ; ses prerequis, eux, restent a tenir.
     */
    public static void setLevel(ServerPlayer player, TechConfig.Domain domain, int level) {
        FactionData data = FactionApi.data(player);
        int target = Math.max(0, Math.min(domain.max(), level));
        if (!domain.merged()) {
            data.stopTech(domain.id());
            data.setTechTier(domain.id(), Math.max(target, domain.startLevel()));
            return;
        }
        for (FactionBranch branch : tracks(domain)) {
            int last = 0;
            for (TechConfig.Tier tier : domain.tiers()) {
                if (tier.tier() <= target) {
                    for (Step step : steps(tier)) {
                        if (step.branch() == branch) {
                            last = Math.max(last, step.number());
                        }
                    }
                }
            }
            FactionTree.setTier(player, branch, last);
        }
    }

    // -- Petits outils --------------------------------------------------------------------------------

    public static int count(Inventory inventory, Item item) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Prend jusqu'a {@code count} de cet objet dans l'inventaire ; rend ce qu'il a pris. */
    private static int take(Inventory inventory, Item item, int count) {
        int left = count;
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
        return count - left;
    }
}

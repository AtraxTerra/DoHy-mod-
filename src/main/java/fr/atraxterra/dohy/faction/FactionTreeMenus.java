package fr.atraxterra.dohy.faction;

import fr.atraxterra.dohy.faction.tech.Tech;
import fr.atraxterra.dohy.faction.tech.TechConfig;
import fr.atraxterra.dohy.faction.tech.TechPerks;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Les technologies de la nation (lot 23b) : les neuf domaines, puis les paliers d'un domaine,
 * leurs machines, et pour les domaines repris les etapes de l'ancien arbre.
 *
 * <p>Si les domaines n'ont pas pu se charger (datapack illisible), les ecrans retombent sur
 * l'ancien arbre a quatre branches : rien ne se bloque.
 */
public final class FactionTreeMenus {

    private FactionTreeMenus() {
    }

    static String time(Duration duration) {
        long minutes = Math.max(0L, (duration.toSeconds() + 59) / 60);
        long days = minutes / 1440;
        long hours = minutes % 1440 / 60;
        long rest = minutes % 60;
        if (days > 0) {
            return days + " j" + (hours > 0 ? " " + hours + " h" : "");
        }
        if (hours > 0) {
            return hours + " h" + (rest > 0 ? " " + rest + " min" : "");
        }
        return rest + " min";
    }

    static String left(FactionData data, FactionBranch branch) {
        return until(data.upgradeEnd(branch.key()));
    }

    static String until(long end) {
        return time(Duration.ofMillis(Math.max(0L, end - System.currentTimeMillis())));
    }

    private static final int GREEN = 0xFF22E558;
    private static final int GOLD = 0xFFFAC400;

    private abstract static class Page extends MenuController {
        Page(String path) {
            super(path);
        }

        @Override
        public void open(ServerPlayer player) {
            if (!FactionApi.has(player)) {
                FactionEdit.open(player);
                return;
            }
            Tech.complete(player, false);
            super.open(player);
        }
    }

    // -- Les neuf domaines ------------------------------------------------------------------------

    public static final class Tree extends Page {
        public static final Tree INSTANCE = new Tree();

        private Tree() {
            super("faction_tree");
        }

        @Override
        protected void back(ServerPlayer player) {
            FactionMenus.Overview.INSTANCE.open(player);
        }

        @Override
        public MenuView view(ServerPlayer player) {
            if (TechConfig.domains().isEmpty()) {
                return legacy(player);
            }
            FactionData data = FactionApi.data(player);
            TechPerks bonuses = TechPerks.of(data);
            List<Card> domains = new ArrayList<>();
            for (TechConfig.Domain domain : TechConfig.domains()) {
                domains.add(domain.merged() ? mergedCard(player, data, domain) : domainCard(player, data, domain));
            }
            List<Card> running = running(data);
            List<MenuView.Section> sections = new ArrayList<>();
            sections.add(new MenuView.Section("DOMAINES", "upgrade", MenuView.GRID, domains));
            if (!running.isEmpty()) {
                sections.add(new MenuView.Section("EN COURS", "craft", MenuView.ROWS, running));
            }
            sections.add(new MenuView.Section("ACQUIS", "resources", MenuView.GRID, acquired(data, bonuses)));
            List<MenuView.Stat> stats = List.of(
                    new MenuView.Stat("PALIERS", Tech.researched(data) + " / " + Tech.total(), GREEN),
                    new MenuView.Stat("RECHERCHES", Tech.running(data) + " / " + bonuses.parallel(), 0xFF27C4E8),
                    new MenuView.Stat("BANQUE", MoneyFormat.shortForm(data.bank()), GOLD));
            return new MenuView(id(), "Technologies", "", stats, sections, true)
                    .withTabs(FactionMenus.tabs(player, FactionMenus.TECHNOLOGY));
        }

        /** Un domaine nouveau : son palier, la recherche en cours ou la suivante. */
        private static Card domainCard(ServerPlayer player, FactionData data, TechConfig.Domain domain) {
            int level = Tech.level(data, domain);
            TechConfig.Tier current = domain.tier(level);
            TechConfig.Tier next = Tech.next(data, domain);
            long end = Tech.end(data, domain);
            String first = current == null ? "Palier I a rechercher" : "Palier " + TechConfig.roman(level) + " · "
                    + current.name();
            String second;
            if (end > 0L && next != null) {
                second = "En cours : " + next.name() + ", " + until(end);
            } else if (next == null) {
                second = "Domaine termine";
            } else {
                second = "Suivant : " + next.name() + " · " + MoneyFormat.shortForm(Tech.cost(next).money()) + " $";
            }
            Card card = Card.of(domain.icon(), domain.name(), List.of(first, second)).accent(domain.accent())
                    .progress(level, domain.max()).action("domain:" + domain.id() + "|ouvrir")
                    .tooltip(domainTooltip(data, domain, level));
            if (end > 0L) {
                return card.badge("...");
            }
            if (next == null) {
                return card.state(MenuView.DONE);
            }
            return Tech.blocker(player, domain) == null ? card.badge("!") : card;
        }

        /** Un domaine repris : son palier, et les etapes de l'ancien arbre qui le font. */
        private static Card mergedCard(ServerPlayer player, FactionData data, TechConfig.Domain domain) {
            int level = Tech.level(data, domain);
            TechConfig.Tier current = domain.tier(level);
            int done = Tech.stepsDone(data, domain);
            int total = Tech.stepsTotal(domain);
            boolean running = false;
            boolean ready = false;
            for (FactionBranch branch : Tech.tracks(domain)) {
                if (data.upgradeEnd(branch.key()) > 0L) {
                    running = true;
                } else if (FactionTree.blocker(player, branch) == null) {
                    ready = true;
                }
            }
            String first = current == null ? "Palier I a gagner" : "Palier " + TechConfig.roman(level) + " · "
                    + current.name();
            String second = "Etapes " + done + " / " + total + (running ? " · en cours" : "");
            Card card = Card.of(domain.icon(), domain.name(), List.of(first, second)).accent(domain.accent())
                    .progress(done, total).action("domain:" + domain.id() + "|ouvrir")
                    .tooltip(domainTooltip(data, domain, level));
            if (running) {
                return card.badge("...");
            }
            if (done >= total && level >= domain.max()) {
                return card.state(MenuView.DONE);
            }
            return ready ? card.badge("!") : card;
        }

        private static List<String> domainTooltip(FactionData data, TechConfig.Domain domain, int level) {
            List<String> lines = new ArrayList<>();
            lines.add(domain.summary());
            lines.add("Palier " + TechConfig.roman(level) + " / " + TechConfig.roman(domain.max()));
            if (domain.merged()) {
                lines.add("Se gagne par les etapes de l'ancien arbre (" + names(Tech.tracks(domain)) + ")");
            }
            List<String> active = new ArrayList<>();
            for (TechConfig.Tier tier : domain.tiers()) {
                if (tier.tier() <= Math.max(level, domain.startLevel())) {
                    for (TechConfig.Bonus bonus : tier.bonuses()) {
                        active.add("+ " + TechPerks.label(bonus));
                    }
                }
            }
            if (!active.isEmpty()) {
                lines.add("");
                lines.add("Bonus acquis :");
                lines.addAll(active);
            }
            return lines;
        }

        /** Les recherches et les etapes en cours, avec ce qu'il leur reste. */
        private static List<Card> running(FactionData data) {
            List<Card> cards = new ArrayList<>();
            for (TechConfig.Domain domain : TechConfig.domains()) {
                long end = Tech.end(data, domain);
                TechConfig.Tier next = Tech.next(data, domain);
                if (end > 0L && next != null) {
                    cards.add(Card.of(new ItemStack(Items.CLOCK), next.title(domain), List.of(
                                    "Recherche : encore " + until(end), "Le temps court meme hors ligne"))
                            .accent(domain.accent()).badge(until(end))
                            .action("domain:" + domain.id() + "|ouvrir"));
                }
                if (domain.merged()) {
                    for (FactionBranch branch : Tech.tracks(domain)) {
                        long stepEnd = data.upgradeEnd(branch.key());
                        if (stepEnd > 0L) {
                            int number = data.tier(branch.key()) + 1;
                            cards.add(Card.of(new ItemStack(Items.CLOCK), "Etape " + branch.label() + " " + number,
                                            List.of(domain.name() + " · encore " + until(stepEnd),
                                                    "Amelioration de l'ancien arbre"))
                                    .accent(domain.accent()).badge(until(stepEnd))
                                    .action("domain:" + domain.id() + "|ouvrir"));
                        }
                    }
                }
            }
            return cards;
        }

        /** Ce que la nation a gagne : les recompenses de l'ancien arbre, puis la recherche et les bonus. */
        private static List<Card> acquired(FactionData data, TechPerks bonuses) {
            List<Card> cards = new ArrayList<>(perks(data));
            cards.add(Card.of(new ItemStack(Items.ENCHANTING_TABLE), "Recherche", List.of(
                            bonuses.parallel() + (bonuses.parallel() > 1 ? " recherches a la fois" : " recherche a la fois"),
                            "Vitesse +" + bonuses.research() + " %"))
                    .accent(0xFF27C4E8).tooltip(List.of(
                            "Les domaines nouveaux se recherchent un par un : deux a la fois au Gouvernement IV,"
                                    + " trois au VI.",
                            "Chaque etape de l'ancien arbre garde son amelioration a elle.",
                            "Laboratoires : " + bonuses.labs() + " (lot 23c)")));
            List<String> lines = new ArrayList<>();
            for (TechConfig.Domain domain : TechConfig.domains()) {
                int level = Math.max(Tech.level(data, domain), domain.startLevel());
                for (TechConfig.Tier tier : domain.tiers()) {
                    if (tier.tier() <= level) {
                        for (TechConfig.Bonus bonus : tier.bonuses()) {
                            lines.add(domain.name() + " " + TechConfig.roman(tier.tier()) + " : "
                                    + TechPerks.label(bonus));
                        }
                    }
                }
            }
            cards.add(Card.of(new ItemStack(Items.NETHER_STAR), "Bonus des technologies", List.of(
                            lines.size() + " bonus", "Detail dans la bulle")).accent(0xFF9B6BFF)
                    .tooltip(lines.isEmpty() ? List.of("Aucun pour l'instant") : lines));
            return cards;
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            if (key.startsWith("domain:")) {
                TechConfig.Domain domain = TechConfig.domain(key.substring(7));
                if (domain != null) {
                    Branch.INSTANCE.show(player, domain);
                }
            } else if (key.startsWith("branch:")) {
                FactionBranch branch = FactionBranch.byKey(key.substring(7));
                if (branch != null) {
                    Branch.INSTANCE.show(player, branch);
                }
            }
        }

        // -- L'ancien arbre, si les domaines manquent --------------------------------------------------

        private MenuView legacy(ServerPlayer player) {
            FactionData data = FactionApi.data(player);
            List<Card> branches = new ArrayList<>();
            int done = 0;
            int total = 0;
            for (FactionBranch branch : FactionBranch.values()) {
                int tier = data.tier(branch.key());
                int max = FactionTree.tiers(branch).size();
                done += tier;
                total += max;
                FactionTree.Tier next = FactionTree.tier(branch, tier + 1);
                String second;
                if (data.upgradeEnd(branch.key()) > 0L) {
                    second = "En cours : " + left(data, branch);
                } else if (next == null) {
                    second = "Branche terminee";
                } else {
                    second = "Suivant : " + (next.cost() > 0 ? MoneyFormat.shortForm(next.cost()) + " $" : "or liquide");
                }
                Card card = Card.of(new ItemStack(branch.icon()), "Branche " + branch.label(), List.of(
                                "Palier " + tier + " / " + max, second))
                        .accent(branch.color()).progress(tier, max).action("branch:" + branch.key() + "|ouvrir")
                        .tooltip(List.of(branch.summary()));
                if (data.upgradeEnd(branch.key()) > 0L) {
                    card = card.badge("...");
                } else if (next == null) {
                    card = card.state(MenuView.DONE);
                } else if (FactionTree.blocker(player, branch) == null) {
                    card = card.badge("!");
                }
                branches.add(card);
            }
            List<MenuView.Stat> stats = List.of(
                    new MenuView.Stat("PALIERS", done + " / " + total, GREEN),
                    new MenuView.Stat("BANQUE", MoneyFormat.shortForm(data.bank()), GOLD));
            return new MenuView(id(), "Technologies", "Domaines introuvables : l'ancien arbre", stats,
                    List.of(new MenuView.Section("BRANCHES", "upgrade", MenuView.GRID, branches),
                            new MenuView.Section("ACQUIS", "resources", MenuView.GRID, perks(data))), true)
                    .withTabs(FactionMenus.tabs(player, FactionMenus.TECHNOLOGY));
        }
    }

    /** Les recompenses de l'ancien arbre, telles qu'elles s'affichaient. */
    private static List<Card> perks(FactionData data) {
        FactionPerks perks = FactionPerks.of(data);
        List<String> unlocks = new ArrayList<>(perks.unlocks());
        unlocks.sort(String::compareTo);
        return List.of(
                Card.of(new ItemStack(Items.GRASS_BLOCK), "Claims", List.of(perks.claims() + " au plus",
                        "Base 5, technologies en plus")).accent(GREEN),
                Card.of(new ItemStack(Items.BED.pick(DyeColor.RED)), "F homes", List.of(
                        perks.homes() + " home(s)", "Delai " + perks.homeDelay() + " s")).accent(0xFF2F5BEA),
                Card.of(new ItemStack(Items.SHIELD), "Raids", List.of(
                        "-" + Math.round((1.0f - perks.raidDamage()) * 100) + " % de degats",
                        "XP de donjon +" + Math.round(perks.dungeonXp() * 100) + " %")).accent(0xFFEF3826),
                Card.of(new ItemStack(Items.GOLD_BLOCK), "Reserve d'or", List.of(
                        perks.reserveTier() == 0 ? "Pas encore" : "Palier " + perks.reserveTier(),
                        MoneyFormat.shortForm(perks.goldValue()) + " $ / or liquide · x" + perks.interest()))
                        .accent(GOLD),
                Card.of(new ItemStack(Items.TNT), "Objets debloques", List.of(perks.unlocks().size()
                        + " deblocage(s)", "Detail dans la bulle")).accent(0xFF55545C)
                        .tooltip(unlocks.isEmpty() ? List.of("Aucun pour l'instant") : unlocks));
    }

    private static String names(List<FactionBranch> branches) {
        List<String> names = new ArrayList<>();
        for (FactionBranch branch : branches) {
            names.add(branch.label());
        }
        return String.join(", ", names);
    }

    // -- Les paliers d'un domaine -------------------------------------------------------------------

    public static final class Branch extends Page {
        public static final Branch INSTANCE = new Branch();
        /** Le domaine ouvert (« energie »), ou l'ancienne branche (« branch:global ») si les domaines manquent. */
        private final Map<UUID, String> selected = new ConcurrentHashMap<>();
        /** Le palier dont on montre les machines ; absent, le prochain. */
        private final Map<UUID, Integer> shown = new ConcurrentHashMap<>();

        private Branch() {
            super("faction_branch");
        }

        void show(ServerPlayer player, FactionBranch branch) {
            selected.put(player.getUUID(), "branch:" + branch.key());
            shown.remove(player.getUUID());
            open(player);
        }

        public void show(ServerPlayer player, TechConfig.Domain domain) {
            selected.put(player.getUUID(), domain.id());
            shown.remove(player.getUUID());
            open(player);
        }

        private TechConfig.Domain domain(ServerPlayer player) {
            String key = selected.getOrDefault(player.getUUID(), "");
            if (key.startsWith("branch:")) {
                return null;
            }
            TechConfig.Domain domain = TechConfig.domain(key);
            if (domain == null && !TechConfig.domains().isEmpty()) {
                domain = TechConfig.domains().getFirst();
            }
            return domain;
        }

        private FactionBranch branch(ServerPlayer player) {
            String key = selected.getOrDefault(player.getUUID(), "");
            FactionBranch branch = key.startsWith("branch:") ? FactionBranch.byKey(key.substring(7)) : null;
            return branch == null ? FactionBranch.GLOBAL : branch;
        }

        @Override
        protected void back(ServerPlayer player) {
            Tree.INSTANCE.open(player);
        }

        @Override
        public MenuView view(ServerPlayer player) {
            TechConfig.Domain domain = domain(player);
            if (domain == null) {
                return legacyView(player, branch(player));
            }
            FactionData data = FactionApi.data(player);
            int level = Tech.level(data, domain);
            int show = shown.getOrDefault(player.getUUID(), Math.min(domain.max(), level + 1));
            TechConfig.Tier machinesOf = domain.tier(show);
            List<Card> rows = new ArrayList<>();
            for (TechConfig.Tier tier : domain.tiers()) {
                rows.add(domain.merged() ? mergedRow(data, domain, tier, level)
                        : tierRow(player, data, domain, tier, level));
            }
            List<MenuView.Section> sections = new ArrayList<>();
            sections.add(new MenuView.Section("PALIERS", "craft", MenuView.ROWS, rows)
                    .anchor("Paliers|" + TechConfig.roman(level) + "/" + TechConfig.roman(domain.max())));
            if (machinesOf != null && !machinesOf.machines().isEmpty()) {
                sections.add(new MenuView.Section("MACHINES · PALIER " + TechConfig.roman(machinesOf.tier()) + " · "
                        + machinesOf.name().toUpperCase(java.util.Locale.ROOT), "resources", MenuView.TILES,
                        machines(domain, machinesOf, Tech.reached(data, domain, machinesOf.tier())))
                        .anchor("Machines|" + machinesOf.machines().size()));
            }
            List<MenuView.Stat> stats = new ArrayList<>();
            stats.add(new MenuView.Stat("PALIER", TechConfig.roman(level) + " / " + TechConfig.roman(domain.max()),
                    domain.accent()));
            if (domain.merged()) {
                stats.add(new MenuView.Stat("ETAPES", Tech.stepsDone(data, domain) + " / " + Tech.stepsTotal(domain),
                        GREEN));
                for (FactionBranch branch : Tech.tracks(domain)) {
                    List<Card> steps = new ArrayList<>();
                    int tier = data.tier(branch.key());
                    boolean running = data.upgradeEnd(branch.key()) > 0L;
                    String blocker = FactionTree.blocker(player, branch);
                    for (FactionTree.Tier step : FactionTree.tiers(branch)) {
                        steps.add(stepRow(player, data, step, tier, running, blocker, domain));
                    }
                    sections.add(new MenuView.Section("ETAPES · " + branch.label().toUpperCase(java.util.Locale.ROOT),
                            "upgrade", MenuView.ROWS, steps)
                            .anchor(branch.label() + "|" + tier + "/" + FactionTree.tiers(branch).size()));
                }
            } else {
                long end = Tech.end(data, domain);
                stats.add(new MenuView.Stat("EN COURS", end > 0L ? until(end) : "-", 0xFFFFFFFF));
                stats.add(new MenuView.Stat("RECHERCHES", Tech.running(data) + " / " + TechPerks.of(data).parallel(),
                        0xFF27C4E8));
            }
            stats.add(new MenuView.Stat("BANQUE", MoneyFormat.shortForm(data.bank()), GOLD));
            return new MenuView(id(), domain.name(), domain.summary(), stats, sections, true)
                    .withTabs(FactionMenus.tabs(player, FactionMenus.TECHNOLOGY)).withFlags(MenuView.ANCHORS);
        }

        /** Un palier d'un domaine nouveau : acquis, en recherche, pret, bloque ou plus loin. */
        private static Card tierRow(ServerPlayer player, FactionData data, TechConfig.Domain domain,
                                    TechConfig.Tier tier, int level) {
            List<String> tooltip = new ArrayList<>(machineLines(domain, tier));
            bonusLines(tier, tooltip);
            boolean acquired = tier.tier() <= level;
            boolean office = tier.tier() <= domain.startLevel();
            requireLines(data, domain, tier, acquired, tooltip);
            TechConfig.Cost cost = Tech.cost(tier);
            String price = price(player, cost, acquired || office, tooltip);
            if (!acquired && !office) {
                tooltip.add("Duree : " + time(Tech.duration(data, tier)));
            }
            String title = "Palier " + TechConfig.roman(tier.tier()) + " · " + tier.name();
            String summary = summary(tier);
            Card card;
            long end = Tech.end(data, domain);
            if (acquired) {
                card = Card.of(icon(tier, new ItemStack(Items.DYE.pick(DyeColor.LIME))), title,
                        List.of(summary, office ? "Acquis d'office" : "Acquis")).state(MenuView.DONE);
            } else if (tier.tier() == level + 1) {
                String blocker = Tech.blocker(player, domain);
                if (end > 0L) {
                    card = Card.of(new ItemStack(Items.CLOCK), title, List.of(summary,
                                    "Recherche : encore " + until(end)))
                            .accent(GOLD).badge(until(end));
                } else if (blocker == null) {
                    card = Card.of(icon(tier, new ItemStack(Items.ENCHANTED_BOOK)), title, List.of(summary,
                                    price + " · " + time(Tech.duration(data, tier))))
                            .accent(GREEN).badge("PRET").action("research|rechercher")
                            .confirm("Rechercher " + tier.title(domain) + " ?|" + price + ", "
                                    + time(Tech.duration(data, tier)));
                } else {
                    tooltip.add("");
                    tooltip.add(blocker);
                    card = Card.of(icon(tier, new ItemStack(Items.BOOK)), title, List.of(summary, blocker))
                            .accent(domain.accent());
                }
            } else {
                card = Card.of(icon(tier, new ItemStack(Items.DYE.pick(DyeColor.GRAY))), title, List.of(summary,
                        price + " · " + time(Tech.duration(data, tier)))).state(MenuView.DISABLED);
            }
            return card.right("tier:" + tier.tier() + "|voir ses machines").tooltip(tooltip);
        }

        /** Un palier d'un domaine repris : ses etapes de l'ancien arbre, faites ou non. */
        private static Card mergedRow(FactionData data, TechConfig.Domain domain, TechConfig.Tier tier,
                                      int level) {
            List<String> tooltip = new ArrayList<>(machineLines(domain, tier));
            bonusLines(tier, tooltip);
            boolean acquired = tier.tier() <= level;
            requireLines(data, domain, tier, acquired, tooltip);
            List<Tech.Step> steps = Tech.steps(tier);
            int done = 0;
            tooltip.add("");
            tooltip.add("Etapes de l'ancien arbre :");
            for (Tech.Step step : steps) {
                boolean ok = step.done(data);
                if (ok) {
                    done++;
                }
                List<String> rewards = new ArrayList<>();
                FactionTree.Tier old = step.tier();
                if (old != null) {
                    for (FactionTree.Reward reward : old.rewards()) {
                        rewards.add(reward.label());
                    }
                }
                tooltip.add((ok ? "✔ " : "✘ ") + step.label() + (rewards.isEmpty() ? "" : " : "
                        + String.join(", ", rewards)));
            }
            String title = "Palier " + TechConfig.roman(tier.tier()) + " · " + tier.name();
            String summary = summary(tier);
            String stepsLine = "Etapes " + done + " / " + steps.size() + " (" + labels(steps) + ")";
            Card card;
            if (acquired) {
                card = Card.of(icon(tier, new ItemStack(Items.DYE.pick(DyeColor.LIME))), title,
                        List.of(summary, "Acquis · " + labels(steps))).state(MenuView.DONE);
            } else if (tier.tier() == level + 1) {
                String missing = null;
                for (String need : tier.requires()) {
                    if (!Tech.reached(data, need)) {
                        missing = "Il faut aussi " + Tech.label(need);
                        break;
                    }
                }
                if (missing != null && done == steps.size()) {
                    tooltip.add("");
                    tooltip.add(missing);
                    card = Card.of(icon(tier, new ItemStack(Items.BOOK)), title, List.of(summary, missing))
                            .accent(domain.accent());
                } else {
                    card = Card.of(icon(tier, new ItemStack(Items.WRITABLE_BOOK)), title, List.of(summary, stepsLine))
                            .accent(domain.accent()).progress(done, steps.size());
                }
            } else {
                card = Card.of(icon(tier, new ItemStack(Items.DYE.pick(DyeColor.GRAY))), title,
                        List.of(summary, stepsLine)).state(MenuView.DISABLED);
            }
            if (tier.tier() == domain.startLevel() && !acquired && !tier.machines().isEmpty()) {
                tooltip.add("");
                tooltip.add("Ses machines sont libres d'office.");
            }
            return card.right("tier:" + tier.tier() + "|voir ses machines").tooltip(tooltip);
        }

        /** Une etape de l'ancien arbre, comme l'affichait l'ancienne branche, dans son domaine. */
        private static Card stepRow(ServerPlayer player, FactionData data, FactionTree.Tier step, int tier,
                                    boolean running, String blocker, TechConfig.Domain domain) {
            Card card = row(player, data, step, tier, running, blocker, "step:" + step.branch().key() + "|ameliorer");
            String palier = "";
            for (TechConfig.Tier owner : domain.tiers()) {
                for (Tech.Step part : Tech.steps(owner)) {
                    if (part.branch() == step.branch() && part.number() == step.number()) {
                        palier = domain.name() + " " + TechConfig.roman(owner.tier());
                    }
                }
            }
            if (palier.isEmpty()) {
                return card;
            }
            List<String> tooltip = new ArrayList<>(card.tooltip());
            tooltip.add("");
            tooltip.add("Compte pour " + palier);
            return card.tooltip(tooltip);
        }

        private static String labels(List<Tech.Step> steps) {
            List<String> labels = new ArrayList<>();
            for (Tech.Step step : steps) {
                labels.add(step.label());
            }
            return String.join(", ", labels);
        }

        /** « 9 machines · laboratoires -10 % d'energie ». */
        private static String summary(TechConfig.Tier tier) {
            List<String> parts = new ArrayList<>();
            int machines = tier.machines().size();
            if (machines > 0) {
                parts.add(machines + (machines > 1 ? " machines" : " machine"));
            }
            for (TechConfig.Bonus bonus : tier.bonuses()) {
                parts.add(bonus.label());
            }
            return parts.isEmpty() ? "Aucune machine ni bonus" : String.join(" · ", parts);
        }

        private static void bonusLines(TechConfig.Tier tier, List<String> tooltip) {
            if (tier.bonuses().isEmpty()) {
                return;
            }
            tooltip.add("");
            tooltip.add("Bonus :");
            for (TechConfig.Bonus bonus : tier.bonuses()) {
                tooltip.add("+ " + TechPerks.label(bonus));
            }
        }

        private static void requireLines(FactionData data, TechConfig.Domain domain, TechConfig.Tier tier,
                                         boolean acquired, List<String> tooltip) {
            List<String> needs = new ArrayList<>();
            if (tier.tier() > 1) {
                needs.add(domain.id() + ":" + (tier.tier() - 1));
            }
            needs.addAll(tier.requires());
            if (needs.isEmpty() || tier.tier() <= domain.startLevel()) {
                return;
            }
            tooltip.add("");
            tooltip.add("Prerequis :");
            for (String need : needs) {
                tooltip.add((acquired || Tech.reached(data, need) ? "✔ " : "✘ ") + Tech.label(need));
            }
        }

        /** Le cout lisible (« 25 000 $ + 32 plaques d'acier »), et son detail dans la bulle. */
        private static String price(ServerPlayer player, TechConfig.Cost cost, boolean paid, List<String> tooltip) {
            if (paid || cost.money() <= 0 && cost.items().isEmpty()) {
                return "gratuit";
            }
            tooltip.add("");
            tooltip.add("Cout :");
            List<String> parts = new ArrayList<>();
            if (cost.money() > 0) {
                parts.add(MoneyFormat.shortForm(cost.money()) + " $");
                tooltip.add("- " + MoneyFormat.exact(cost.money()) + " $ de la banque nationale");
            }
            for (TechConfig.ItemCost item : cost.items()) {
                Optional<Item> found = item.item();
                if (found.isEmpty()) {
                    tooltip.add("- " + item.count() + " " + item.label() + " (NTM absent : rien a fournir)");
                    continue;
                }
                parts.add(item.count() + " " + item.label());
                int have = Tech.count(player.getInventory(), found.get());
                tooltip.add((have >= item.count() ? "✔ " : "✘ ") + item.count() + " " + item.label() + " (" + have
                        + " sur toi)");
            }
            return parts.isEmpty() ? "gratuit" : String.join(" + ", parts);
        }

        /** Les machines d'un palier, dans la bulle : douze au plus, les memes noms regroupes. */
        private static List<String> machineLines(TechConfig.Domain domain, TechConfig.Tier tier) {
            List<String> lines = new ArrayList<>();
            List<TechConfig.Machine> machines = tier.resolved();
            if (machines.isEmpty()) {
                return lines;
            }
            lines.add("Machines (" + machines.size() + ") :");
            List<String> names = new ArrayList<>();
            List<Integer> counts = new ArrayList<>();
            for (TechConfig.Machine machine : machines) {
                String name = name(machine);
                int at = names.indexOf(name);
                if (at >= 0) {
                    counts.set(at, counts.get(at) + 1);
                } else {
                    names.add(name);
                    counts.add(1);
                }
            }
            int limit = Math.min(names.size(), 12);
            for (int index = 0; index < limit; index++) {
                lines.add("- " + names.get(index) + (counts.get(index) > 1 ? " x" + counts.get(index) : ""));
            }
            if (names.size() > limit) {
                lines.add("... et " + (names.size() - limit) + " autres");
            }
            return lines;
        }

        /** Le nom d'une machine : celui du jeu, traduit chez le joueur, ou celui des donnees si le mod manque. */
        private static String name(TechConfig.Machine machine) {
            Optional<Block> block = machine.block();
            if (block.isPresent()) {
                return MenuView.text(block.get().getName());
            }
            return machine.name().isEmpty() ? machine.id() : machine.name();
        }

        /** L'objet de la premiere machine que le jeu connait, sinon l'icone d'etat. */
        private static ItemStack icon(TechConfig.Tier tier, ItemStack fallback) {
            for (TechConfig.Machine machine : tier.machines()) {
                Optional<Block> block = machine.block();
                if (block.isPresent() && block.get().asItem() != Items.AIR) {
                    return new ItemStack(block.get().asItem());
                }
            }
            return fallback;
        }

        /** Les machines du palier, en tuiles : leur objet si le mod est la. */
        private static List<Card> machines(TechConfig.Domain domain, TechConfig.Tier tier, boolean free) {
            List<Card> tiles = new ArrayList<>();
            for (TechConfig.Machine machine : tier.resolved()) {
                Optional<Block> block = machine.block();
                ItemStack icon = block.isPresent() && block.get().asItem() != Items.AIR
                        ? new ItemStack(block.get().asItem()) : domain.icon();
                List<String> lines = new ArrayList<>();
                lines.add(mod(machine.id()) + (block.isPresent() ? "" : machine.id().startsWith("dohy:") ? " (a venir)"
                        : " (absent)"));
                lines.add(free ? "Debloquee" : "Se debloque avec " + tier.title(domain));
                Card tile = Card.of(icon, name(machine), lines).accent(domain.accent())
                        .tooltip(List.of(machine.id()));
                tiles.add(free ? tile.state(MenuView.DONE) : block.isEmpty() ? tile.state(MenuView.DISABLED) : tile);
            }
            return tiles;
        }

        private static String mod(String id) {
            String namespace = id.contains(":") ? id.substring(0, id.indexOf(':')) : id;
            return switch (namespace) {
                case "hbm" -> "NTM NEXT";
                case "hbmspace" -> "NTM NEXT: Space";
                case "leafia" -> "NTM: Cursed";
                case "dohy" -> "DoHy";
                default -> namespace;
            };
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            TechConfig.Domain domain = domain(player);
            if ("research".equals(key) && domain != null) {
                FactionData data = FactionApi.data(player);
                TechConfig.Tier next = Tech.next(data, domain);
                report(player, Tech.start(player, domain), next == null ? ""
                        : "Recherche lancee : " + next.title(domain) + ", " + time(Tech.duration(data, next)) + ".");
                if (next != null) {
                    shown.remove(player.getUUID());
                }
            } else if (key.startsWith("tier:") && domain != null) {
                try {
                    int tier = Integer.parseInt(key.substring(5));
                    if (domain.tier(tier) != null) {
                        shown.put(player.getUUID(), tier);
                    }
                } catch (NumberFormatException ignored) {
                    // Une cle mal formee ne change rien.
                }
            } else if (key.startsWith("step:")) {
                FactionBranch branch = FactionBranch.byKey(key.substring(5));
                if (branch != null) {
                    upgrade(player, branch);
                }
            } else if ("upgrade".equals(key)) {
                upgrade(player, branch(player));
            }
        }

        private static void upgrade(ServerPlayer player, FactionBranch branch) {
            FactionData data = FactionApi.data(player);
            FactionTree.Tier next = FactionTree.tier(branch, data.tier(branch.key()) + 1);
            report(player, FactionTree.start(player, branch), next == null ? ""
                    : "Amelioration lancee : etape " + branch.label() + " " + next.number() + ", "
                    + time(next.time()) + ".");
        }

        // -- L'ancienne branche, si les domaines manquent ------------------------------------------------

        private MenuView legacyView(ServerPlayer player, FactionBranch branch) {
            FactionData data = FactionApi.data(player);
            int tier = data.tier(branch.key());
            boolean running = data.upgradeEnd(branch.key()) > 0L;
            String blocker = FactionTree.blocker(player, branch);
            List<Card> rows = new ArrayList<>();
            for (FactionTree.Tier step : FactionTree.tiers(branch)) {
                rows.add(row(player, data, step, tier, running, blocker, "upgrade|ameliorer"));
            }
            List<MenuView.Stat> stats = List.of(
                    new MenuView.Stat("PALIER", tier + " / " + FactionTree.tiers(branch).size(), branch.color()),
                    new MenuView.Stat("BANQUE", MoneyFormat.shortForm(data.bank()), GOLD),
                    new MenuView.Stat("EN COURS", running ? left(data, branch) : "-", 0xFFFFFFFF));
            return new MenuView(id(), "Branche " + branch.label(), "", stats,
                    List.of(new MenuView.Section("PALIERS", "craft", MenuView.ROWS, rows)), true)
                    .withTabs(FactionMenus.tabs(player, FactionMenus.TECHNOLOGY));
        }

        /** Une etape de l'ancien arbre : cout, conditions, recompenses, et l'action quand elle est prete. */
        private static Card row(ServerPlayer player, FactionData data, FactionTree.Tier step, int tier,
                                boolean running, String blocker, String action) {
            int met = 0;
            List<String> tooltip = new ArrayList<>();
            String price = step.cost() > 0 ? MoneyFormat.exact(step.cost()) + " $" : "";
            for (FactionTree.ItemCost cost : step.items()) {
                price = (price.isEmpty() ? "" : price + " + ") + cost.count() + " " + cost.label();
            }
            tooltip.add("Cout : " + (price.isEmpty() ? "gratuit" : price));
            tooltip.add("Duree : " + time(step.time()));
            if (!step.checks().isEmpty()) {
                tooltip.add("");
                tooltip.add("Conditions :");
            }
            for (FactionTree.Check check : step.checks()) {
                boolean ok = step.number() <= tier || check.done(player, data);
                if (ok) {
                    met++;
                }
                String progress = check.pending().isEmpty()
                        ? " (" + MoneyFormat.exact(check.progressOf(player, data)) + " / " + MoneyFormat.exact(check.goal())
                        + ")" : " (" + check.pending() + ")";
                tooltip.add((ok ? "✔ " : "✘ ") + check.label() + (step.number() <= tier ? "" : progress));
            }
            tooltip.add("");
            tooltip.add("Recompenses :");
            List<String> rewards = new ArrayList<>();
            for (FactionTree.Reward reward : step.rewards()) {
                tooltip.add("+ " + reward.label());
                rewards.add(reward.label());
            }
            String title = "Etape " + step.number() + " · " + (price.isEmpty() ? "gratuit" : price);
            String conditions = step.checks().isEmpty() ? "Aucune condition"
                    : "Conditions " + met + " / " + step.checks().size();
            Card card;
            if (step.number() <= tier) {
                card = Card.of(new ItemStack(Items.DYE.pick(DyeColor.LIME)), title, List.of(String.join(", ", rewards),
                        "Acquis")).state(MenuView.DONE);
            } else if (step.number() == tier + 1) {
                if (running) {
                    card = Card.of(new ItemStack(Items.CLOCK), title, List.of(String.join(", ", rewards),
                            "Amelioration : encore " + left(data, step.branch())))
                            .accent(GOLD).badge(left(data, step.branch()));
                } else if (blocker == null) {
                    card = Card.of(new ItemStack(Items.ENCHANTED_BOOK), title, List.of(String.join(", ", rewards),
                            conditions + " · " + time(step.time())))
                            .accent(GREEN).badge("PRET").action(action);
                } else {
                    tooltip.add("");
                    tooltip.add(blocker);
                    card = Card.of(new ItemStack(Items.BOOK), title, List.of(String.join(", ", rewards),
                            conditions + " · " + time(step.time()))).accent(step.branch().color());
                }
            } else {
                card = Card.of(new ItemStack(Items.DYE.pick(DyeColor.GRAY)), title, List.of(String.join(", ", rewards),
                        conditions + " · " + time(step.time()))).state(MenuView.DISABLED);
            }
            return card.tooltip(tooltip);
        }
    }
}

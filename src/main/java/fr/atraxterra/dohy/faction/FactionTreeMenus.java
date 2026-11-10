package fr.atraxterra.dohy.faction;

import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** L'arbre de technologie : les quatre branches, puis les paliers d'une branche. */
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
        long end = data.upgradeEnd(branch.key());
        return time(Duration.ofMillis(Math.max(0L, end - System.currentTimeMillis())));
    }

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
            FactionTree.complete(player, false);
            super.open(player);
        }
    }

    // -- Les quatre branches ----------------------------------------------------------

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
            FactionData data = FactionApi.data(player);
            FactionPerks perks = FactionPerks.of(data);
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
            List<String> unlocks = new ArrayList<>(perks.unlocks());
            unlocks.sort(String::compareTo);
            List<Card> acquired = List.of(
                    Card.of(new ItemStack(Items.GRASS_BLOCK), "Claims", List.of(perks.claims() + " au plus",
                            "Base 5, arbre en plus")).accent(0xFF22E558),
                    Card.of(new ItemStack(Items.BED.pick(DyeColor.RED)), "F homes", List.of(
                            perks.homes() + " home(s)", "Delai " + perks.homeDelay() + " s")).accent(0xFF2F5BEA),
                    Card.of(new ItemStack(Items.SHIELD), "Raids", List.of(
                            "-" + Math.round((1.0f - perks.raidDamage()) * 100) + " % de degats",
                            "XP de donjon +" + Math.round(perks.dungeonXp() * 100) + " %")).accent(0xFFEF3826),
                    Card.of(new ItemStack(Items.GOLD_BLOCK), "Reserve d'or", List.of(
                            perks.reserveTier() == 0 ? "Pas encore" : "Palier " + perks.reserveTier(),
                            MoneyFormat.shortForm(perks.goldValue()) + " $ / or liquide · x" + perks.interest()))
                            .accent(0xFFFAC400),
                    Card.of(new ItemStack(Items.TNT), "Objets debloques", List.of(perks.unlocks().size()
                            + " deblocage(s)", "Detail dans la bulle")).accent(0xFF55545C)
                            .tooltip(unlocks.isEmpty() ? List.of("Aucun pour l'instant") : unlocks));
            List<MenuView.Stat> stats = List.of(
                    new MenuView.Stat("PALIERS", done + " / " + total, 0xFF22E558),
                    new MenuView.Stat("BANQUE", MoneyFormat.shortForm(data.bank()), 0xFFFAC400));
            return new MenuView(id(), "Arbre de technologie", "Paye par la banque de faction, en temps reel", stats,
                    List.of(new MenuView.Section("BRANCHES", "upgrade", MenuView.GRID, branches),
                            new MenuView.Section("ACQUIS", "resources", MenuView.GRID, acquired)), true);
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            if (key.startsWith("branch:")) {
                FactionBranch branch = FactionBranch.byKey(key.substring(7));
                if (branch != null) {
                    Branch.INSTANCE.show(player, branch);
                }
            }
        }
    }

    // -- Les paliers d'une branche ------------------------------------------------------

    public static final class Branch extends Page {
        public static final Branch INSTANCE = new Branch();
        private final Map<UUID, FactionBranch> selected = new ConcurrentHashMap<>();

        private Branch() {
            super("faction_branch");
        }

        void show(ServerPlayer player, FactionBranch branch) {
            selected.put(player.getUUID(), branch);
            open(player);
        }

        @Override
        protected void back(ServerPlayer player) {
            Tree.INSTANCE.open(player);
        }

        @Override
        public MenuView view(ServerPlayer player) {
            FactionBranch branch = selected.getOrDefault(player.getUUID(), FactionBranch.GLOBAL);
            FactionData data = FactionApi.data(player);
            int tier = data.tier(branch.key());
            boolean running = data.upgradeEnd(branch.key()) > 0L;
            String blocker = FactionTree.blocker(player, branch);
            List<Card> rows = new ArrayList<>();
            for (FactionTree.Tier step : FactionTree.tiers(branch)) {
                rows.add(row(player, data, step, tier, running, blocker));
            }
            List<MenuView.Stat> stats = List.of(
                    new MenuView.Stat("PALIER", tier + " / " + FactionTree.tiers(branch).size(), branch.color()),
                    new MenuView.Stat("BANQUE", MoneyFormat.shortForm(data.bank()), 0xFFFAC400),
                    new MenuView.Stat("EN COURS", running ? left(data, branch) : "-", 0xFFFFFFFF));
            return new MenuView(id(), "Branche " + branch.label(), "Clic sur le palier suivant : ameliorer", stats,
                    List.of(new MenuView.Section("PALIERS", "craft", MenuView.ROWS, rows)), true);
        }

        private static Card row(ServerPlayer player, FactionData data, FactionTree.Tier step, int tier,
                                boolean running, String blocker) {
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
            String title = "Palier " + step.number() + " · " + (price.isEmpty() ? "gratuit" : price);
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
                            .accent(0xFFFAC400).badge(left(data, step.branch()));
                } else if (blocker == null) {
                    card = Card.of(new ItemStack(Items.ENCHANTED_BOOK), title, List.of(String.join(", ", rewards),
                            conditions + " · " + time(step.time())))
                            .accent(0xFF22E558).badge("PRET").action("upgrade|ameliorer");
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

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            if ("upgrade".equals(key)) {
                FactionBranch branch = selected.getOrDefault(player.getUUID(), FactionBranch.GLOBAL);
                FactionData data = FactionApi.data(player);
                FactionTree.Tier next = FactionTree.tier(branch, data.tier(branch.key()) + 1);
                report(player, FactionTree.start(player, branch), next == null ? ""
                        : "Amelioration lancee : palier " + next.number() + ", " + time(next.time()) + ".");
            }
        }
    }
}

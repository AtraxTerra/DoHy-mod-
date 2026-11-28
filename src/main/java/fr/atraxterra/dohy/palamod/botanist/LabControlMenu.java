package fr.atraxterra.dohy.palamod.botanist;

import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.util.MoneyFormat;
import fr.atraxterra.dohy.util.RealClock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L'ecran du centre de controle ({@code UIBotanistControl} de JobsReborn, « LABORATOIRE GENIS »),
 * dans la vue des menus du kit : un onglet par module (un climat), la fiche du module choisi a
 * gauche (son etat, sa pousse moyenne, ses pots, ses besoins en attente), et a droite le boost du
 * jour, l'amelioration, puis les pots du module, ou ils sont et ce qu'ils reclament.
 *
 * <p>Les textes sont ceux de JobsReborn quand ils existent (« BOOSTER LE MODULE », « Boost du jour
 * deja utilise », « Revient dans », « AMELIORER LE MODULE », « Amelioration en construction »,
 * « Niveau maximum atteint », « Il te manque », « Pousse moyenne », « Besoins en attente »...). Le
 * bouton « SE TELEPORTER » du laboratoire devient la liste des pots et leurs coordonnees : ici, ils
 * sont dans le monde.
 */
public final class LabControlMenu extends MenuController {

    public static final LabControlMenu INSTANCE = new LabControlMenu();

    private static final double REACH = 8.0d;
    private static final int GOLD = 0xFFFAC400;
    private static final int GREEN = 0xFF22E558;
    private static final int RED = 0xFFFF5A3C;
    private static final int PURPLE = 0xFFB07CFF;
    private static final int GREY = 0xFF55545C;
    private static final int MAX_ROWS = 40;

    private final Map<UUID, BlockPos> open = new ConcurrentHashMap<>();
    private final Map<UUID, BotanistLab.Module> selected = new ConcurrentHashMap<>();

    private LabControlMenu() {
        super("lab_control");
    }

    public void openAt(ServerPlayer player, BlockPos pos) {
        open.put(player.getUUID(), pos.immutable());
        BotanistLab.update(player);
        BotanistLab.prune(player);
        open(player);
    }

    /** Le centre de controle ouvert, s'il est encore la et a portee. */
    private boolean near(ServerPlayer player) {
        BlockPos pos = open.get(player.getUUID());
        return pos != null && player.level().getBlockEntity(pos) instanceof LabControlBlockEntity
                && player.distanceToSqr(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d) <= REACH * REACH;
    }

    private BotanistLab.Module module(ServerPlayer player) {
        return selected.getOrDefault(player.getUUID(), BotanistLab.Module.NORMAL);
    }

    @Override
    public MenuView view(ServerPlayer player) {
        BotanistLab.Module module = module(player);
        Map<String, BotanistLab.Pot> pots = BotanistLab.pots(player, module);
        int totalPots = 0;
        int totalNeeds = 0;
        List<MenuView.Tab> tabs = new ArrayList<>();
        for (BotanistLab.Module each : BotanistLab.Module.values()) {
            Map<String, BotanistLab.Pot> found = BotanistLab.pots(player, each);
            int needs = BotanistLab.needs(found);
            totalPots += found.size();
            totalNeeds += needs;
            BotanistLab.State state = BotanistLab.state(player, each);
            tabs.add(new MenuView.Tab(each.label(), "module:" + each.key(), each == module,
                    state == BotanistLab.State.ACTION ? needs + (needs > 1 ? " BESOINS" : " BESOIN") : state.label()));
        }
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("SOLDE", MoneyFormat.shortForm(Economy.balance(player)), GOLD),
                new MenuView.Stat("POTS", Integer.toString(totalPots), 0xFFFFFFFF),
                new MenuView.Stat("BESOINS EN ATTENTE", Integer.toString(totalNeeds), totalNeeds > 0 ? RED : GREEN));

        List<MenuView.Section> sections = new ArrayList<>();
        // Le boost et l'amelioration en lignes sur toute la largeur : leurs couts y tiennent en entier.
        sections.add(new MenuView.Section("BOOSTER ET AMELIORER LE MODULE", "upgrade", MenuView.ROWS,
                List.of(boostCard(player, module), upgradeCard(player, module))));
        sections.add(new MenuView.Section("TES POTS|" + pots.size(), "resources", MenuView.ROWS, potRows(player, pots,
                module)));
        return new MenuView(id(), "Laboratoire Genis", "Centre de controle", stats, sections, false, tabs)
                .withHero(hero(player, module, pots));
    }

    /** La fiche du module, a gauche. */
    private static MenuView.Hero hero(ServerPlayer player, BotanistLab.Module module,
                                      Map<String, BotanistLab.Pot> pots) {
        BotanistLab.State state = BotanistLab.state(player, module);
        int growth = BotanistLab.averageGrowth(pots);
        int level = BotanistLab.data(player).level(module);
        int speed = (int) Math.round(BotanistLab.growth(player, module.climate()) * 100.0d);
        List<String> lines = new ArrayList<>();
        lines.add("#CLIMAT " + Mutation.climateLabel(module.climate()).toUpperCase(Locale.ROOT));
        lines.add("Tes pots de ce climat, ou qu'ils soient.");
        if (state == BotanistLab.State.LOCKED) {
            lines.add("#VERROUILLE");
            lines.add("Niveau " + BotanistLab.UNLOCK_LEVEL + " de Fermier requis.");
        }
        if (BotanistLab.boosted(player, module)) {
            lines.add("#BOOST ACTIF");
            lines.add("x1,5 et +2 % de mutations, encore "
                    + BotanistLab.duration(BotanistLab.boostMinutesLeft(player, module)) + ".");
        }
        if (BotanistLab.building(player, module)) {
            lines.add("#CONSTRUCTION");
            lines.add("Niveau " + (level + 1) + " dans "
                    + BotanistLab.duration(BotanistLab.buildingMinutesLeft(player, module)) + ".");
        }
        // La pastille et le halo prennent la couleur de l'etat quand il se passe quelque chose
        // (besoin, construction, boost, verrou), celle du climat sinon.
        int accent = state == BotanistLab.State.ACTIVE || state == BotanistLab.State.FREE ? module.color()
                : state.color();
        return new MenuView.Hero(seed(module.icon()), "", module.label().toUpperCase(Locale.ROOT), state.label(),
                accent, growth, "POUSSE MOYENNE|" + growth / 10 + " %", List.of(
                new MenuView.Stat("POTS", Integer.toString(pots.size()), 0xFFFFFFFF),
                new MenuView.Stat("BESOINS EN ATTENTE", Integer.toString(BotanistLab.needs(pots)),
                        BotanistLab.needs(pots) > 0 ? RED : GREEN),
                new MenuView.Stat("FLEURS A RECOLTER", Integer.toString(BotanistLab.blooms(pots, false)), PURPLE),
                new MenuView.Stat("MUTATIONS", Integer.toString(BotanistLab.blooms(pots, true)), 0xFF6FD8FF),
                new MenuView.Stat("POUSSE", speed + " %", speed > 100 ? GREEN : 0xFFFFFFFF),
                new MenuView.Stat("NIVEAU", level + " / " + BotanistLab.MAX_LEVEL, GOLD)), lines);
    }

    private static Card boostCard(ServerPlayer player, BotanistLab.Module module) {
        int left = BotanistLab.boostsLeft(player, module);
        String missing = BotanistLab.boostMissing(player, module);
        List<String> lines = new ArrayList<>();
        String badge;
        int progress = -1;
        if (BotanistLab.boosted(player, module)) {
            long minutes = BotanistLab.boostMinutesLeft(player, module);
            lines.add("Boost actif");
            lines.add("Temps restant : " + BotanistLab.duration(minutes));
            badge = "ACTIF";
            progress = (int) (minutes * 1000L / BotanistLab.BOOST_MINUTES);
        } else if (left <= 0) {
            lines.add("Boost du jour deja utilise");
            lines.add("Revient dans " + BotanistLab.duration(RealClock.minutesUntilNextDay(BotanistLab.RESET_HOUR)));
            badge = "0 / " + BotanistLab.BOOSTS_PER_DAY;
        } else {
            lines.add("Pousse x1,5 et +2 % de mutations pendant " + BotanistLab.BOOST_MINUTES + " min");
            lines.add(BotanistLab.costLabel(BotanistLab.BOOST_MONEY, BotanistLab.boostItems()));
            badge = left + " / " + BotanistLab.BOOSTS_PER_DAY;
        }
        Card card = Card.of(new ItemStack(Items.GLOWSTONE_DUST), "Booster le module", lines).badge(badge)
                .accent(PURPLE).tooltip(List.of(
                        "Accelere la pousse du module pendant un temps.",
                        "Duree : " + BotanistLab.BOOST_MINUTES + " min (horloge reelle)",
                        "Pousse : x1,5 · Mutations : +2 %",
                        "Reste aujourd'hui : " + left + " (renouvele a " + BotanistLab.RESET_HOUR + " h)",
                        "Cout : " + BotanistLab.costLabel(BotanistLab.BOOST_MONEY,
                                BotanistLab.boostItems())));
        if (progress >= 0) {
            card = card.progress(progress, 1000);
        }
        if (BotanistLab.boosted(player, module)) {
            // En cours : la coche verte du kit, pas le cadenas d'une action refusee.
            return card.state(MenuView.DONE);
        }
        if (missing != null) {
            if (!lines.contains(missing)) {
                card = card.tooltip(withLine(card.tooltip(), missing));
            }
            return card.state(MenuView.DISABLED);
        }
        return card.action("boost|booster le module").confirm("Booster le " + module.label().toLowerCase(Locale.ROOT)
                + " ?|" + BotanistLab.costLabel(BotanistLab.BOOST_MONEY, BotanistLab.boostItems()));
    }

    private static Card upgradeCard(ServerPlayer player, BotanistLab.Module module) {
        int level = BotanistLab.data(player).level(module);
        List<String> lines = new ArrayList<>();
        List<String> tooltip = new ArrayList<>();
        int progress = -1;
        String missing = BotanistLab.upgradeMissing(player, module);
        if (level >= BotanistLab.MAX_LEVEL) {
            lines.add("Niveau maximum atteint");
            lines.add("+" + Math.round(BotanistLab.GROWTH_PER_LEVEL * level * 100) + " % de pousse");
        } else if (BotanistLab.building(player, module)) {
            int next = level + 1;
            long left = BotanistLab.buildingMinutesLeft(player, module);
            long total = BotanistLab.upgradeMinutes(next);
            lines.add("En construction");
            lines.add("encore " + BotanistLab.duration(left));
            progress = (int) Math.max(0L, (total - left) * 1000L / total);
        } else {
            int next = level + 1;
            lines.add("Niveau " + next + " : +" + Math.round(BotanistLab.GROWTH_PER_LEVEL * 100) + " % de pousse");
            lines.add(BotanistLab.costLabel(BotanistLab.upgradeMoney(next), BotanistLab.upgradeItems(next)));
            lines.add(BotanistLab.duration(BotanistLab.upgradeMinutes(next)) + " de construction");
            tooltip.add("Construction : " + BotanistLab.duration(BotanistLab.upgradeMinutes(next)) + " (horloge reelle)");
            tooltip.add("Prerequis : niveau " + BotanistLab.upgradeFarmer(next) + " de Fermier");
            tooltip.add("A la fin : +" + BotanistLab.upgradeXp(next) + " XP de Fermier");
        }
        if (missing != null && !lines.contains(missing)) {
            tooltip.add(missing);
        }
        Card card = Card.of(new ItemStack(Items.ANVIL), "Ameliorer le module", lines)
                .accent(GOLD).badge("NIV. " + level + " / " + BotanistLab.MAX_LEVEL).tooltip(tooltip);
        if (progress >= 0) {
            card = card.progress(progress, 1000);
        }
        if (level >= BotanistLab.MAX_LEVEL) {
            return card.state(MenuView.DONE);
        }
        if (BotanistLab.building(player, module)) {
            // En cours : la barre suit la construction, sans le cadenas d'une action refusee.
            return card;
        }
        if (missing != null) {
            return card.state(MenuView.DISABLED);
        }
        int next = level + 1;
        return card.action("upgrade|ameliorer le module").confirm("Ameliorer le "
                + module.label().toLowerCase(Locale.ROOT) + " ?|Niveau " + next + " : "
                + BotanistLab.costLabel(BotanistLab.upgradeMoney(next), BotanistLab.upgradeItems(next)) + ", "
                + BotanistLab.duration(BotanistLab.upgradeMinutes(next)) + " de construction");
    }

    /** Les pots du module : ceux qui reclament d'abord, puis les fleurs, puis ceux qui poussent. */
    private static List<Card> potRows(ServerPlayer player, Map<String, BotanistLab.Pot> pots,
                                      BotanistLab.Module module) {
        List<Map.Entry<String, BotanistLab.Pot>> entries = new ArrayList<>(pots.entrySet());
        entries.sort(Comparator.comparingInt((Map.Entry<String, BotanistLab.Pot> entry) -> rank(entry.getValue()))
                .thenComparing(Map.Entry::getKey));
        List<Card> cards = new ArrayList<>();
        for (Map.Entry<String, BotanistLab.Pot> entry : entries) {
            if (cards.size() >= MAX_ROWS) {
                break;
            }
            BotanistLab.Pot pot = entry.getValue();
            Mutation plant = pot.plant();
            if (plant == null) {
                continue;
            }
            String[] parts = entry.getKey().split("\\|");
            String where = parts.length == 4 ? parts[1] + " " + parts[2] + " " + parts[3] : "?";
            String distance = "";
            if (parts.length == 4 && player.level().dimension().identifier().toString().equals(parts[0])) {
                try {
                    double dx = Integer.parseInt(parts[1]) + 0.5d - player.getX();
                    double dz = Integer.parseInt(parts[3]) + 0.5d - player.getZ();
                    distance = " · a " + Math.round(Math.sqrt(dx * dx + dz * dz)) + " blocs";
                } catch (NumberFormatException ignored) {
                    distance = "";
                }
            } else if (parts.length == 4) {
                distance = " · " + parts[0].substring(parts[0].indexOf(':') + 1);
            }
            int accent;
            String status;
            if (!pot.need().isEmpty()) {
                accent = RED;
                status = "Reclame : " + pot.need();
            } else if (pot.stage().equals(PotStage.BLOOMING.getSerializedName())) {
                accent = pot.mutated() ? 0xFF6FD8FF : PURPLE;
                status = pot.mutated() ? "A mute ! A recolter" : "En fleur, a recolter";
            } else if (pot.stage().equals(PotStage.WILTED.getSerializedName())) {
                accent = GOLD;
                status = "Fanee : recuperer les graines";
            } else {
                accent = module.color();
                status = (pot.stage().equals(PotStage.SEED.getSerializedName()) ? "Graine" : "Bourgeon") + " · "
                        + pot.permille() / 10 + " %";
            }
            Card card = Card.of(seed(plant), plant.label(), List.of(status, where + distance)).accent(accent)
                    .tooltip(List.of("Coordonnees : " + where, "Clic : les envoyer dans le chat"))
                    .action("locate:" + entry.getKey().replace('|', ';') + "|envoyer les coordonnees");
            if (pot.stage().equals(PotStage.SEED.getSerializedName()) || pot.stage().equals(PotStage.BUD.getSerializedName())) {
                card = card.progress(pot.permille(), 1000);
            }
            cards.add(card);
        }
        if (cards.isEmpty()) {
            cards.add(Card.of(seed(module.icon()), "Aucun pot", List.of("Plante une graine de climat "
                    + Mutation.climateLabel(module.climate()), "dans un pot : il apparaitra ici")).accent(GREY)
                    .state(MenuView.DISABLED));
        }
        return cards;
    }

    private static int rank(BotanistLab.Pot pot) {
        if (!pot.need().isEmpty()) {
            return 0;
        }
        if (pot.stage().equals(PotStage.BLOOMING.getSerializedName())) {
            return pot.mutated() ? 1 : 2;
        }
        if (pot.stage().equals(PotStage.WILTED.getSerializedName())) {
            return 3;
        }
        return 4;
    }

    private static List<String> withLine(List<String> lines, String line) {
        List<String> copy = new ArrayList<>(lines);
        copy.add(line);
        return copy;
    }

    private static ItemStack seed(Mutation mutation) {
        return new ItemStack(PalaBotanist.MUTATION_SEEDS.get(mutation).get());
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if (!near(player)) {
            UiNet.close(player);
            return;
        }
        if (key.startsWith("module:")) {
            BotanistLab.Module module = BotanistLab.Module.byKey(key.substring("module:".length()));
            if (module != null) {
                selected.put(player.getUUID(), module);
            }
            return;
        }
        BotanistLab.Module module = module(player);
        switch (key) {
            case "boost" -> BotanistLab.boost(player, module);
            case "upgrade" -> BotanistLab.upgrade(player, module);
            default -> {
                if (key.startsWith("locate:")) {
                    String[] parts = key.substring("locate:".length()).split(";");
                    if (parts.length == 4) {
                        player.sendSystemMessage(Component.literal("[Botaniste] ").withStyle(ChatFormatting.DARK_GREEN)
                                .append(Component.literal("Pot en " + parts[1] + " " + parts[2] + " " + parts[3]
                                        + " (" + parts[0] + ")").withStyle(ChatFormatting.GRAY)));
                    }
                }
            }
        }
    }

    @Override
    protected void back(ServerPlayer player) {
        UiNet.close(player);
    }
}

package fr.atraxterra.dohy.faction.tech;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionData;
import fr.atraxterra.dohy.faction.FactionTreeMenus;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** L'ecran d'un laboratoire de recherche : son etat, son energie, ses points, les recherches qu'il nourrit. */
public final class ResearchLabMenu extends MenuController {

    public static final ResearchLabMenu INSTANCE = new ResearchLabMenu();

    /** Le laboratoire que chaque joueur regarde. */
    private static final Map<UUID, BlockPos> OPEN = new ConcurrentHashMap<>();

    /** Au-dela, le joueur s'est eloigne : l'ecran se ferme. */
    private static final double REACH = 8.0d;

    private static final int GREEN = 0xFF22E558;
    private static final int RED = 0xFFEF3826;
    private static final int GOLD = 0xFFFAC400;
    private static final int CYAN = 0xFF27C4E8;

    private ResearchLabMenu() {
        super("research_lab");
    }

    public static void open(ServerPlayer player, BlockPos pos) {
        OPEN.put(player.getUUID(), pos.immutable());
        INSTANCE.open(player);
    }

    private static ResearchLabBlockEntity lab(ServerPlayer player) {
        BlockPos pos = OPEN.get(player.getUUID());
        if (pos == null || player.distanceToSqr(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d)
                > REACH * REACH) {
            return null;
        }
        return player.level().getBlockEntity(pos) instanceof ResearchLabBlockEntity lab ? lab : null;
    }

    @Override
    public MenuView view(ServerPlayer player) {
        ResearchLabBlockEntity lab = lab(player);
        if (lab == null) {
            return new MenuView(id(), "Laboratoire de recherche", "Ce laboratoire n'est plus a portee", List.of(),
                    List.of(), true);
        }
        ResearchLabConfig.Config config = ResearchLabConfig.config();
        ResearchLabBlockEntity.Status status = lab.status();
        boolean running = status == ResearchLabBlockEntity.Status.RUNNING;
        int stored = lab.energyStored();
        int cost = lab.cost();
        boolean mine = lab.isOwner(player);
        FactionData data = FactionApi.data(player);
        double multiplier = mine ? Tech.multiplier(player) : lab.multiplier();

        List<Card> machine = new ArrayList<>();
        machine.add(Card.of(new ItemStack(running ? Items.REDSTONE_TORCH : Items.LEVER), status.label(),
                        List.of(status.hint(), "Nation : " + (lab.nationName().isEmpty() ? "aucune" : lab.nationName())
                                + " · " + (lab.ownerName().isEmpty() ? "personne" : lab.ownerName())))
                .accent(running ? GREEN : RED).action("refresh|actualiser"));
        machine.add(Card.of(new ItemStack(Items.REDSTONE), "Energie", List.of(
                        MoneyFormat.exact(stored) + " / " + MoneyFormat.exact(config.energyCapacity()) + " FE",
                        "Consomme " + cost + " FE par tick en marche"))
                .accent(GOLD).progress(stored, config.energyCapacity())
                .tooltip(List.of("Tout cable d'energie FE l'alimente, par toutes ses faces",
                        "NTM NEXT : " + (cost * 5L) + " HE par tick (5 HE = 1 FE)",
                        "Les technologies de l'Energie baissent sa consommation",
                        "Reserve pleine : " + (config.energyCapacity() / Math.max(1, cost) / 20) + " s d'autonomie")));
        machine.add(Card.of(new ItemStack(Items.COAL), "Combustible", List.of(
                        lab.fuel() > 0 ? lab.fuel() / 20 + " s de combustion" : "Aucun",
                        "Clic droit avec du charbon, du bois..."))
                .accent(0xFFFF8A3D).progress(lab.fuel(), config.fuelMaxTicks())
                .tooltip(List.of("Faute d'electricite, il brule son combustible",
                        "Chaque tick de combustion rend " + config.fuelPerTick() + " FE : il tourne a mi-regime",
                        "Un clic droit prend la pile, jusqu'a ce qu'il en ait assez",
                        "Il en garde " + config.fuelMaxTicks() / 1600 + " charbons au plus")));
        long perHour = Math.round(config.pointsPerHour() * multiplier);
        machine.add(Card.of(new ItemStack(Items.ENCHANTED_BOOK), "Production", List.of(
                        MoneyFormat.exact(perHour) + " points par heure en marche",
                        "Base " + MoneyFormat.exact(config.pointsPerHour()) + " · x" + String.format(Locale.ROOT,
                                "%.2f", multiplier).replace('.', ',')))
                .accent(CYAN).tooltip(production(player, mine, data, multiplier)));
        machine.add(Card.of(new ItemStack(Items.EXPERIENCE_BOTTLE), "Reserve", List.of(
                        MoneyFormat.exact(lab.buffer()) + " / " + MoneyFormat.exact(config.bufferPoints()) + " points",
                        MoneyFormat.exact(lab.produced()) + " produits en tout"))
                .accent(0xFF9B6BFF).progress(lab.buffer(), config.bufferPoints())
                .tooltip(List.of("Les points passent aux recherches de la nation chaque seconde,",
                        "quand son proprietaire est la. Sans recherche, la reserve se remplit,",
                        "puis le laboratoire s'arrete.")));

        List<MenuView.Section> sections = new ArrayList<>();
        sections.add(new MenuView.Section("LABORATOIRE", "resources", MenuView.GRID, machine));
        if (mine && data.exists()) {
            sections.add(new MenuView.Section("RECHERCHES EN COURS", "craft", MenuView.ROWS, research(player, data)));
            sections.add(new MenuView.Section("LES LABORATOIRES DE TA NATION", "upgrade", MenuView.ROWS,
                    labs(player, data, lab.key())));
        }
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("ETAT", status.label().toUpperCase(Locale.ROOT), running ? GREEN : RED),
                new MenuView.Stat("ENERGIE", (config.energyCapacity() <= 0 ? 0
                        : stored * 100L / config.energyCapacity()) + " %", GOLD),
                new MenuView.Stat("POINTS / H", MoneyFormat.shortForm(running ? perHour : 0L), CYAN),
                new MenuView.Stat("RESERVE", MoneyFormat.shortForm(lab.buffer()), 0xFFFFFFFF));
        return new MenuView(id(), "Laboratoire de recherche", lab.nationName().isEmpty() ? ""
                : "Nation " + lab.nationName(), stats, sections, true);
    }

    /** Le detail du multiplicateur : vitesse de recherche, pays, capitale. */
    private static List<String> production(ServerPlayer player, boolean mine, FactionData data, double multiplier) {
        List<String> lines = new ArrayList<>();
        if (!mine || !data.exists()) {
            lines.add("Bonus de la nation lus a la derniere visite de son proprietaire");
            return lines;
        }
        TechPerks perks = TechPerks.of(data);
        lines.add("Vitesse de recherche : +" + perks.research() + " % (Industrie, Nucleaire, Espace)");
        int countries = Tech.countries(player, perks);
        if (fr.atraxterra.dohy.earth.EarthNations.active()) {
            ResearchLabConfig.Config config = ResearchLabConfig.config();
            lines.add("Pays tenus : +" + countries + " % (" + config.countryPercent() + " % par pays, "
                    + config.capitalPercent() + " % la capitale, " + config.countryCap() + " % au plus"
                    + (perks.value("research_per_country") > 0 ? ", +" + perks.value("research_per_country")
                    + " % au Gouvernement V)" : ")"));
        } else {
            lines.add("Pas de carte de la Terre : pas d'apport des pays");
        }
        lines.add("Les points se partagent a parts egales entre les recherches en cours");
        return lines;
    }

    /** Les recherches de la nation : ce qu'elles ont, ce qu'il leur faut, et a quel rythme. */
    private static List<Card> research(ServerPlayer player, FactionData data) {
        List<Card> cards = new ArrayList<>();
        List<TechConfig.Domain> running = Tech.runningDomains(data);
        long rate = Tech.rate(player);
        for (TechConfig.Domain domain : running) {
            TechConfig.Tier next = Tech.next(data, domain);
            long have = Tech.progress(data, domain);
            long need = Tech.target(next);
            long share = running.isEmpty() ? 0L : rate / running.size();
            cards.add(Card.of(domain.icon(), next.title(domain), List.of(
                            MoneyFormat.exact(have) + " / " + MoneyFormat.exact(need) + " points",
                            FactionTreeMenus.eta(need - have, share)))
                    .accent(domain.accent()).progress(have, need).action("tree|ouvrir les technologies"));
        }
        if (cards.isEmpty()) {
            cards.add(Card.of(new ItemStack(Items.BOOK), "Aucune recherche en cours", List.of(
                            "Lance-en une dans les technologies", "Le laboratoire garde ses points en attendant"))
                    .accent(CYAN).action("tree|ouvrir les technologies"));
        }
        return cards;
    }

    /** Les laboratoires de la liste de la nation : celui-ci, ceux qui tournent, ceux qui dorment. */
    private static List<Card> labs(ServerPlayer player, FactionData data, String here) {
        List<Card> cards = new ArrayList<>();
        int cap = TechPerks.of(data).labs();
        int index = 0;
        for (String key : data.labs()) {
            Double rate = ResearchLabBlockEntity.loadedRate(player.getUUID(), key);
            boolean allowed = index < cap;
            String where = key.substring(key.indexOf('@') + 1).replace(',', ' ');
            String dimension = key.substring(0, Math.max(0, key.indexOf('@')));
            String state = !allowed ? "En surnombre : le Gouvernement en permet " + cap
                    : rate == null ? "Chunk decharge : a l'arret"
                    : rate > 0.0d ? "En marche · " + MoneyFormat.exact(Math.round(rate)) + " points bruts par heure"
                    : "A l'arret : energie, combustible ou reserve pleine";
            Card card = Card.of(new ItemStack(DoHyResearch.LAB_ITEM.get()), (key.equals(here) ? "Ici · " : "")
                            + where, List.of(dimension.replace("minecraft:", ""), state))
                    .accent(!allowed ? RED : rate != null && rate > 0.0d ? GREEN : 0xFF55545C);
            cards.add(key.equals(here) ? card.badge("ICI") : card);
            index++;
        }
        if (cards.isEmpty()) {
            cards.add(Card.of(new ItemStack(DoHyResearch.LAB_ITEM.get()), "Aucun laboratoire inscrit", List.of(
                    "Il s'inscrit tout seul a la prochaine seconde", "Ton Gouvernement en permet " + cap)));
        }
        return cards;
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        ResearchLabBlockEntity lab = lab(player);
        if (lab == null) {
            UiNet.close(player);
            return;
        }
        if ("tree".equals(key)) {
            OPEN.remove(player.getUUID());
            FactionTreeMenus.Tree.INSTANCE.open(player);
        }
        // « refresh » ne fait rien de plus : l'ecran se redessine apres chaque action.
    }

    @Override
    protected void back(ServerPlayer player) {
        OPEN.remove(player.getUUID());
        UiNet.close(player);
    }
}

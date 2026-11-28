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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
                .tooltip(List.of("FE, ou " + (cost * 5L) + " HE par tick (NTM)")));
        machine.add(Card.of(new ItemStack(Items.COAL), "Combustible", List.of(
                        lab.fuel() > 0 ? lab.fuel() / 20 + " s de combustion" : "Aucun",
                        "Clic droit : charbon, bois"))
                .accent(0xFFFF8A3D).progress(lab.fuel(), config.fuelMaxTicks())
                .tooltip(List.of("Sans electricite : mi-regime")));
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
                .tooltip(List.of("Pleine : le laboratoire s'arrete")));

        List<MenuView.Section> sections = new ArrayList<>();
        sections.add(new MenuView.Section("LABORATOIRE", "resources", MenuView.GRID, machine));
        sections.add(new MenuView.Section("PIECES LIVREES", "upgrade", MenuView.GRID,
                parts(player, lab, member(player, lab, data), data)));
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

    /** Le proprietaire et les membres de sa nation reprennent les pieces ; les autres peuvent seulement en livrer. */
    private static boolean member(ServerPlayer player, ResearchLabBlockEntity lab, FactionData data) {
        return lab.isOwner(player) || player.getAbilities().instabuild
                || data.exists() && !lab.nationName().isEmpty() && lab.nationName().equals(data.name());
    }

    /**
     * Les pieces livrees (lot 23i) : ce que le laboratoire garde pour les recherches de sa nation, un
     * depot de tout ce que le joueur porte, et une carte par objet garde pour en reprendre.
     */
    private static List<Card> parts(ServerPlayer player, ResearchLabBlockEntity lab, boolean member,
                                    FactionData data) {
        List<Card> cards = new ArrayList<>();
        int carried = carried(player.getInventory());
        List<String> hint = new ArrayList<>(List.of("Entonnoir, convoyeur ou tuyau : ok"));
        List<String> needs = needs(player, data);
        if (!needs.isEmpty()) {
            hint.add("");
            hint.add("Les prochains paliers :");
            hint.addAll(needs);
        }
        cards.add(Card.of(new ItemStack(Items.HOPPER), "Deposer tes pieces", List.of(
                        carried > 0 ? carried + " piece(s) des paliers sur toi" : "Aucune piece des paliers sur toi",
                        lab.freePartSlots() + " case(s) libre(s) sur " + ResearchLabBlockEntity.PART_SLOTS))
                .accent(carried > 0 ? GREEN : 0xFF55545C).action("deposit|deposer").tooltip(hint));
        for (Map.Entry<Item, Integer> entry : lab.partsByItem().entrySet()) {
            ItemStack icon = new ItemStack(entry.getKey());
            Card card = Card.of(icon, MenuView.text(icon.getHoverName()), List.of(
                            "x" + MoneyFormat.exact(entry.getValue()) + " ici",
                            member ? "Clic : une pile · Clic droit : tout" : "Seule sa nation les reprend"))
                    .accent(CYAN);
            if (member) {
                String id = BuiltInRegistries.ITEM.getKey(entry.getKey()).toString();
                card = card.action("take:" + id + "|reprendre une pile").right("takeall:" + id + "|tout reprendre");
            }
            cards.add(card);
        }
        return cards;
    }

    /** Combien de pieces des paliers le joueur porte. */
    private static int carried(Inventory inventory) {
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (TechConfig.isPart(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Ce que demandent les prochains paliers de la nation, et ce qu'elle en a (sur le joueur et livre). */
    private static List<String> needs(ServerPlayer player, FactionData data) {
        List<String> lines = new ArrayList<>();
        if (!data.exists()) {
            return lines;
        }
        List<ResearchLabBlockEntity> labs = Tech.loadedLabs(player, data);
        for (TechConfig.Domain domain : TechConfig.domains()) {
            TechConfig.Tier next = domain.merged() || Tech.researching(data, domain) ? null : Tech.next(data, domain);
            if (next == null || next.cost().isEmpty() || lines.size() >= 9) {
                continue;
            }
            List<String> wants = new ArrayList<>();
            boolean ready = true;
            for (TechConfig.ItemCost item : next.cost().get().items()) {
                Optional<Item> found = item.item();
                if (found.isPresent()) {
                    int have = Tech.count(player.getInventory(), found.get()) + Tech.delivered(labs, found.get());
                    ready &= have >= item.count();
                    wants.add(Math.min(have, item.count()) + "/" + item.count() + " " + item.label());
                }
            }
            if (!wants.isEmpty()) {
                lines.add((ready ? "✔ " : "- ") + domain.name() + " " + TechConfig.roman(next.tier()) + " : "
                        + String.join(", ", wants));
            }
        }
        return lines;
    }

    /** Depose tout ce que le joueur porte des pieces des paliers ; rend ce qui ne tient pas. */
    private static String deposit(ServerPlayer player, ResearchLabBlockEntity lab) {
        Inventory inventory = player.getInventory();
        int moved = 0;
        boolean full = false;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!TechConfig.isPart(stack)) {
                continue;
            }
            ItemStack rest = lab.storePart(stack);
            moved += stack.getCount() - rest.getCount();
            full |= !rest.isEmpty();
            inventory.setItem(slot, rest);
        }
        if (moved == 0) {
            return full ? "!Le laboratoire est plein : 18 cases au plus" : "!Tu ne portes aucune piece des paliers";
        }
        return moved + " piece(s) deposee(s)" + (full ? " ; le laboratoire est plein, le reste est sur toi" : "");
    }

    /** Rend au joueur une pile, ou tout, de cet objet ; ce qui ne tient pas tombe a ses pieds. */
    private static String takeBack(ServerPlayer player, ResearchLabBlockEntity lab, String id, boolean all) {
        Identifier key = Identifier.tryParse(id);
        Optional<Item> item = key == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(key);
        if (item.isEmpty() || lab.partCount(item.get()) <= 0) {
            return "!Le laboratoire n'en a plus";
        }
        int wanted = all ? lab.partCount(item.get()) : new ItemStack(item.get()).getMaxStackSize();
        int taken = lab.takePart(item.get(), wanted);
        int left = taken;
        while (left > 0) {
            ItemStack stack = new ItemStack(item.get(), Math.min(left, new ItemStack(item.get()).getMaxStackSize()));
            left -= stack.getCount();
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        return taken + " piece(s) reprise(s)";
    }

    /** Le detail du multiplicateur : vitesse de recherche, pays, capitale. */
    private static List<String> production(ServerPlayer player, boolean mine, FactionData data, double multiplier) {
        List<String> lines = new ArrayList<>();
        if (!mine || !data.exists()) {
            lines.add("Bonus de la nation");
            return lines;
        }
        TechPerks perks = TechPerks.of(data);
        lines.add("Recherche : +" + perks.research() + " %");
        int countries = Tech.countries(player, perks);
        if (fr.atraxterra.dohy.earth.EarthNations.active()) {
            ResearchLabConfig.Config config = ResearchLabConfig.config();
            lines.add("Pays : +" + countries + " %");
        } else {
        }
        lines.add("Partage entre les recherches");
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
                            "", ""))
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
                    : "A l'arret";
            Card card = Card.of(new ItemStack(DoHyResearch.LAB_ITEM.get()), (key.equals(here) ? "Ici · " : "")
                            + where, List.of(dimension.replace("minecraft:", ""), state))
                    .accent(!allowed ? RED : rate != null && rate > 0.0d ? GREEN : 0xFF55545C);
            cards.add(key.equals(here) ? card.badge("ICI") : card);
            index++;
        }
        if (cards.isEmpty()) {
            cards.add(Card.of(new ItemStack(DoHyResearch.LAB_ITEM.get()), "Aucun laboratoire inscrit", List.of(
                    "", "Ton Gouvernement en permet " + cap)));
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
            return;
        }
        if ("deposit".equals(key)) {
            answer(player, deposit(player, lab));
            return;
        }
        boolean all = key.startsWith("takeall:");
        if (all || key.startsWith("take:")) {
            if (!member(player, lab, FactionApi.data(player))) {
                answer(player, "!Seule la nation de ce laboratoire reprend ses pieces");
                return;
            }
            answer(player, takeBack(player, lab, key.substring(key.indexOf(':') + 1), all || button == 1));
        }
        // « refresh » ne fait rien de plus : l'ecran se redessine apres chaque action.
    }

    /** Un message commencant par « ! » est un refus. */
    private static void answer(ServerPlayer player, String message) {
        if (message.startsWith("!")) {
            report(player, message.substring(1), "");
        } else {
            report(player, null, message);
        }
    }

    @Override
    protected void back(ServerPlayer player) {
        OPEN.remove(player.getUUID());
        UiNet.close(player);
    }
}

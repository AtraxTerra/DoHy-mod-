package fr.atraxterra.dohy.earth.mine;

import fr.atraxterra.dohy.earth.EarthBodies;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionEdit;
import fr.atraxterra.dohy.faction.FactionMenus;
import fr.atraxterra.dohy.faction.FactionVault;
import fr.atraxterra.dohy.faction.FactionWeekly;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * L'onglet « Mines » de la faction : la capture en cours, les mines tenues et ce
 * qu'elles versent, les plus proches a prendre, et comment faire.
 */
public final class MineMenu extends MenuController {

    public static final MineMenu INSTANCE = new MineMenu();

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int GOLD = 0xFFFAC400;
    private static final int GREEN = 0xFF22E558;
    private static final int GREY = 0xFF55545C;
    private static final int ORANGE = 0xFFE8590C;
    /** Les mines proches a l'ecran. */
    private static final int SHOWN = 6;

    private MineMenu() {
        super("faction_mines");
    }

    @Override
    public void open(ServerPlayer player) {
        if (!FactionApi.has(player)) {
            FactionEdit.open(player);
            return;
        }
        super.open(player);
    }

    @Override
    protected void back(ServerPlayer player) {
        FactionMenus.Overview.INSTANCE.open(player);
    }

    @Override
    public MenuView view(ServerPlayer player) {
        if (!EarthMines.active()) {
            return new MenuView(id(), "Mines de la Terre", "Il faut une carte de la Terre", List.of(), List.of(
                    new MenuView.Section("MINES", "resources", MenuView.GRID, List.of(
                            Card.of(new ItemStack(Items.MAP), "Pas de carte", List.of("Ce monde n'a pas de carte",
                                    "de la Terre (datapack)")).state(MenuView.DISABLED)))), true)
                    .withTabs(FactionMenus.tabs(player, "faction_mines"));
        }
        MinecraftServer server = player.level().getServer();
        List<MineLedger.Concession> mines = EarthMines.of(player);
        FactionVault vault = FactionVault.of(player);
        long perHour = EarthMines.moneyPerHour(player);
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("MINES", String.valueOf(mines.size()), mines.isEmpty() ? 0xFFB8BCC6 : GOLD),
                new MenuView.Stat("PAR HEURE", MoneyFormat.shortForm(perHour) + " $", perHour > 0 ? GREEN : 0xFFB8BCC6),
                new MenuView.Stat("COFFRE", vault.used() + " / " + FactionVault.SLOTS,
                        vault.used() >= FactionVault.SLOTS ? 0xFFEF3826 : 0xFFFFFFFF));
        List<MenuView.Section> sections = new ArrayList<>();

        EarthMines.Capture capture = EarthMines.capture(player.getUUID());
        if (capture != null) {
            EarthBodies.Body body = EarthMines.body(capture.mine);
            boolean near = player.level().dimension().equals(capture.dimension)
                    && player.distanceToSqr(Vec3.atCenterOf(capture.beacon)) <= (double) EarthMines.RANGE
                    * EarthMines.RANGE;
            int seconds = capture.secondsLeft();
            sections.add(new MenuView.Section("CAPTURE EN COURS", "upgrade", MenuView.GRID, List.of(
                    Card.of(new ItemStack(DoHyMines.BEACON_ITEM.get()), body == null ? capture.mine : body.name(),
                                    List.of("Encore " + seconds / 60 + " min " + seconds % 60 + " s", near
                                            ? "Reste pres de la balise" : "En pause : reviens a " + EarthMines.RANGE
                                            + " blocs"))
                            .accent(near ? GOLD : GREY).progress(capture.progress, capture.need)
                            .tooltip(List.of("Balise en " + capture.beacon.getX() + " " + capture.beacon.getY() + " "
                                    + capture.beacon.getZ(), "La casser abandonne la capture")))));
        }

        List<Card> held = new ArrayList<>();
        for (MineLedger.Concession concession : mines) {
            EarthBodies.Body body = EarthMines.body(concession.mine());
            if (body == null) {
                continue;
            }
            int blocks = EarthMines.blocksPerHour(body);
            String nation = EarthMines.nationName(EarthMines.nation(server, body));
            EarthBodies.Body stake = EarthMines.stake(player, EarthMines.nation(server, body));
            List<String> tooltip = new ArrayList<>();
            tooltip.add("Capturee le " + DATE.format(Instant.ofEpochMilli(concession.since())
                    .atZone(FactionWeekly.zone())));
            tooltip.add("Verse " + EarthMines.output(body));
            tooltip.add("Tu peux toujours y miner toi-meme");
            if (stake != null && stake.id().equals(body.id())) {
                tooltip.add("En jeu : reprise si " + nation + " gagne un assaut");
            }
            tooltip.add("Clic : la rendre a " + nation);
            held.add(Card.of(EarthMines.icon(body), body.name(), List.of(
                            body.resource() + " · " + EarthMines.countryName(body),
                            "+" + MoneyFormat.exact(EarthMines.moneyPerHour(body)) + " $"
                                    + (blocks > 0 ? " et " + blocks + " bloc" + (blocks > 1 ? "s" : "") : "") + " / h"))
                    .accent(stake != null && stake.id().equals(body.id()) ? ORANGE : GREEN)
                    .badge("RANG " + EarthMines.tier(body))
                    .tooltip(tooltip)
                    .action("abandon:" + body.id() + "|la rendre")
                    .confirm("!Rendre " + body.name() + " ?|Elle revient a " + nation + " ; ses revenus s'arretent."));
        }
        if (held.isEmpty()) {
            held.add(Card.of(new ItemStack(DoHyMines.BEACON_ITEM.get()), "Aucune mine", List.of(
                            "Pose une balise de concession", "dans une mine de la Terre"))
                    .accent(GOLD).tooltip(List.of("La balise s'achete a la Boutique",
                            MoneyFormat.exact(DoHyMines.BEACON_PRICE) + " $, consommee par la capture")));
        }
        sections.add(new MenuView.Section("TES MINES|" + mines.size(), "resources", MenuView.GRID, held));

        List<Card> near = new ArrayList<>();
        for (EarthBodies.Body body : EarthMines.nearest(player, SHOWN + mines.size())) {
            if (near.size() >= SHOWN) {
                break;
            }
            if (EarthMines.ownedBy(player, body)) {
                continue;
            }
            String holder = EarthMines.holderName(player, body);
            near.add(Card.of(EarthMines.icon(body), body.name(), List.of(
                            EarthMines.label(body) + " · " + EarthMines.countryName(body),
                            EarthMines.direction(body, player.getX(), player.getZ())))
                    .accent(GREY)
                    .tooltip(List.of("Aux mains " + fr.atraxterra.dohy.earth.EarthNations.de(holder),
                            "Verserait " + EarthMines.output(body),
                            "Capture : " + EarthMines.captureSeconds(body) / 60 + " min pres de la balise")));
        }
        sections.add(new MenuView.Section("LES PLUS PROCHES", "craft", MenuView.GRID, near));

        sections.add(new MenuView.Section("COMMENT FAIRE", "", MenuView.TEXT, List.of(Card.of(ItemStack.EMPTY, "",
                List.of("1. Achete une balise de concession a la Boutique ("
                                + MoneyFormat.exact(DoHyMines.BEACON_PRICE) + " $).",
                        "2. Va dans une mine de la Terre (/gisements) et pose la balise.",
                        "3. Reste a moins de " + EarthMines.RANGE + " blocs : 2 a 5 min selon le rang de la mine.",
                        "4. Chaque heure, meme hors ligne : l'argent a ta banque de faction, les minerais au coffre"
                                + " de la banque (une semaine d'absence au plus).",
                        "La mine reste ouverte : tu peux toujours y miner toi-meme. La nation depossedee s'en"
                                + " souvient : si son assaut reussit, elle la reprend.")))));
        return new MenuView(id(), "Mines de la Terre", "Capture des mines : leurs revenus vont a ta banque de faction.",
                stats, sections, true).withTabs(FactionMenus.tabs(player, "faction_mines"));
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if (key.startsWith("abandon:")) {
            report(player, EarthMines.abandon(player, key.substring(8)), "");
        }
    }
}

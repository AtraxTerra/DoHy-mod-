package fr.atraxterra.dohy.faction.unesco;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.util.MoneyFormat;
import fr.atraxterra.dohy.util.RealClock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L'ecran du programme UNESCO ({@code /f unesco}, ou la plaque) : la plaque cliquee et son dossier,
 * les monuments inscrits, la merveille du monde du mois et la prochaine election.
 */
public final class UnescoMenu extends MenuController {

    public static final UnescoMenu INSTANCE = new UnescoMenu();

    private static final String[] CRITERIA = {"Taille", "Details", "Interieur", "Style", "Fonctions"};
    private static final int[] MAX = {25, 20, 15, 25, 15};
    /** « dans 4 j 23 h », « dans 3 h 12 min », « dans 12 min ». */
    static String until(ZonedDateTime when) {
        long seconds = Math.max(0L, Duration.between(ZonedDateTime.now(RealClock.zone()), when).getSeconds());
        long days = seconds / 86_400L;
        long hours = seconds / 3600L % 24L;
        long minutes = seconds / 60L % 60L;
        if (days > 0) {
            return "dans " + days + " j " + hours + " h";
        }
        return hours > 0 ? "dans " + hours + " h " + minutes + " min" : "dans " + Math.max(1L, minutes) + " min";
    }

    private static final String[] MONTHS = {"janvier", "fevrier", "mars", "avril", "mai", "juin", "juillet", "aout",
            "septembre", "octobre", "novembre", "decembre"};

    private final Map<UUID, BlockPos> plaques = new ConcurrentHashMap<>();

    private UnescoMenu() {
        super("unesco");
    }

    /** Ouvre l'ecran depuis une plaque. */
    public void openAt(ServerPlayer player, BlockPos plaque) {
        plaques.put(player.getUUID(), plaque.immutable());
        open(player);
    }

    /** Ouvre l'ecran sans plaque ({@code /f unesco}). */
    public void openGeneral(ServerPlayer player) {
        plaques.remove(player.getUUID());
        open(player);
    }

    private BlockPos plaque(ServerPlayer player) {
        BlockPos pos = plaques.get(player.getUUID());
        if (pos == null || player.distanceToSqr(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d) > 100.0d
                || !(player.level().getBlockEntity(pos) instanceof UnescoPlaqueBlockEntity plaque)
                || !plaque.ownedBy(player)) {
            return null;
        }
        return pos;
    }

    static String month(int key) {
        return MONTHS[Math.max(0, Math.min(11, key % 100 - 1))] + " " + key / 100;
    }

    static List<String> breakdown(UnescoData.Monument monument) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < CRITERIA.length && i < monument.criteria().size(); i++) {
            lines.add(CRITERIA[i] + " : " + monument.criteria().get(i) + " / " + MAX[i]);
        }
        lines.add(monument.built() + " blocs construits");
        return lines;
    }

    @Override
    public MenuView view(ServerPlayer player) {
        UnescoData data = Unesco.data(player.level().getServer());
        List<UnescoData.Monument> mine = Unesco.monuments(player);
        UnescoData.Wonder wonder = data.wonder();
        int used = 0;
        long granted = 0L;
        for (UnescoData.Monument monument : mine) {
            used += monument.weight();
            granted += monument.grant();
        }
        List<MenuView.Section> sections = new ArrayList<>();

        BlockPos plaque = plaque(player);
        if (plaque != null) {
            List<Card> here = new ArrayList<>();
            UnescoData.Monument monument = data.at(FactionApi.dimension(player.level()), plaque);
            if (monument == null) {
                String refusal = Unesco.cannotSubmit(player, plaque);
                Card submit = Card.of(new ItemStack(Items.WRITABLE_BOOK), "Deposer le dossier", List.of(
                                "Le jury arpente le batiment", "Il faut " + Unesco.PASS + " / 100"))
                        .tooltip(List.of("Taille (25) : 1 500 blocs construits pour tout",
                                "Details (20) : escaliers, dalles, vitres, lumieres...",
                                "Interieur (15) : de l'air sous un toit",
                                "Style (25) : une palette tenue, 20 sortes de blocs",
                                "Fonctions (15) : coffres, fours, lits, lutrins...",
                                "Zone : 33 x 33 blocs autour de la plaque"));
                here.add(refusal == null ? submit.accent(0xFF22E558).action("submit|deposer le dossier")
                        : submit.accent(0xFF55545C).state(MenuView.DISABLED).tooltip(List.of(refusal)));
            } else {
                here.add(Card.of(new ItemStack(DoHyUnesco.PLAQUE_ITEM.get()), monument.name(), List.of(
                                monument.score() + " / 100 · " + Unesco.count(monument.weight(), "emplacement",
                                        "emplacements"),
                                "Inscrit, zone protegee"))
                        .accent(0xFFFAC400).badge("UNESCO").tooltip(breakdown(monument)));
            }
            sections.add(new MenuView.Section("CETTE PLAQUE", "upgrade", MenuView.GRID, here));
        }

        List<Card> monuments = new ArrayList<>();
        for (UnescoData.Monument monument : mine) {
            boolean marvel = wonder != null && monument.id().equals(wonder.monument());
            List<String> tooltip = new ArrayList<>(breakdown(monument));
            tooltip.add("Subvention : " + MoneyFormat.exact(monument.grant()) + " $");
            tooltip.add("Guide : " + (monument.guide() ? "oui" : "non") + " (clic droit)");
            BlockPos pos = monument.plaque();
            tooltip.add("Plaque : " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
            Card card = Card.of(new ItemStack(marvel ? Items.BEACON : DoHyUnesco.PLAQUE_ITEM.get()), monument.name(),
                            List.of(monument.score() + " / 100 · " + monument.weight() + " empl.",
                                    "Clic : y aller"))
                    .accent(marvel ? 0xFFFAC400 : 0xFF27C4E8).tooltip(tooltip)
                    .action("tp:" + monument.id() + "|y aller").right("guide:" + monument.id() + "|guide");
            monuments.add(marvel ? card.badge("MERVEILLE") : card);
        }
        if (monuments.isEmpty()) {
            monuments.add(Card.of(new ItemStack(DoHyUnesco.PLAQUE_ITEM.get()), "Aucun monument", List.of(
                    "Pose une plaque UNESCO", "dans ton plus beau batiment")).accent(0xFF55545C));
        }
        sections.add(new MenuView.Section("TES MONUMENTS", "resources", MenuView.GRID, monuments));

        List<Card> marvels = new ArrayList<>();
        if (wonder != null) {
            marvels.add(Card.of(new ItemStack(Items.BEACON), wonder.name(), List.of(wonder.nation(),
                            "Merveille de " + month(wonder.month())))
                    .accent(wonder.player() && wonder.owner().equals(player.getUUID().toString()) ? 0xFFFAC400
                            : 0xFF8B5CF6).badge("MERVEILLE"));
        }
        ZonedDateTime next = Unesco.nextElection();
        marvels.add(Card.of(new ItemStack(Items.CLOCK), "Prochaine election", List.of(
                        "1er " + MONTHS[next.getMonthValue() - 1] + " a " + Unesco.ELECTION_HOUR + " h", until(next)))
                .accent(0xFF27C4E8).tooltip(List.of("Le conseil de l'ONU elit une merveille par mois",
                        "Les monuments inscrits et ceux des nations rivales",
                        "Elue : +" + MoneyFormat.exact(Unesco.WONDER_BONUS) + " $ et +" + Unesco.WONDER_POINTS
                                + " points de notation", "Heure de " + RealClock.zone().getId())));
        for (UnescoData.Wonder past : data.history()) {
            if (wonder != null && past.month() == wonder.month()) {
                continue;
            }
            marvels.add(Card.of(new ItemStack(Items.GOLD_NUGGET), past.name(), List.of(past.nation(),
                    month(past.month()))).accent(0xFF55545C));
        }
        sections.add(new MenuView.Section("LA MERVEILLE DU MONDE", "upgrade", MenuView.GRID, marvels));

        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("EMPLACEMENTS", used + " / " + Unesco.MAX_WEIGHT, 0xFFFAC400),
                new MenuView.Stat("SUBVENTIONS", MoneyFormat.exact(granted) + " $", 0xFF22E558),
                new MenuView.Stat("PRESTIGE", "+" + Unesco.prestige(player), 0xFFFFFFFF));
        return new MenuView(id(), "Patrimoine mondial", "Programme UNESCO et merveilles du monde", stats, sections,
                true);
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        String refusal;
        if ("submit".equals(key)) {
            BlockPos plaque = plaque(player);
            refusal = plaque == null ? "Retourne a ta plaque." : Unesco.submit(player, plaque);
        } else if (key.startsWith("tp:")) {
            refusal = Unesco.teleport(player, key.substring(3));
        } else if (key.startsWith("guide:")) {
            refusal = Unesco.toggleGuide(player, key.substring(6));
        } else {
            return;
        }
        if (refusal != null) {
            Unesco.tell(player, refusal, ChatFormatting.RED);
        }
    }
}

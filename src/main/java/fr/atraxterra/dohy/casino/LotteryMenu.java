package fr.atraxterra.dohy.casino;

import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** L'ecran de la loterie du spawn : la cagnotte, le tirage, tes tickets et les derniers gagnants. */
public final class LotteryMenu extends MenuController {

    public static final LotteryMenu INSTANCE = new LotteryMenu();

    private static final String[] DAYS = {"lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"};

    private LotteryMenu() {
        super("lottery");
    }

    /** « 3 j 4 h », « 2 h 05 », « 12 min ». */
    static String until(long seconds) {
        long minutes = Math.max(0L, seconds / 60L);
        long days = minutes / 1440L;
        long hours = minutes / 60L % 24L;
        if (days > 0L) {
            return days + " j " + hours + " h";
        }
        return hours > 0L ? hours + " h " + String.format(Locale.ROOT, "%02d", minutes % 60L) : minutes + " min";
    }

    /** « dimanche 29/11, 20 h ». */
    static String date(long seconds) {
        ZonedDateTime time = Lottery.at(seconds);
        return DAYS[time.getDayOfWeek().ordinal()] + " " + String.format(Locale.ROOT, "%02d/%02d", time.getDayOfMonth(),
                time.getMonthValue()) + ", " + time.getHour() + " h";
    }

    @Override
    public MenuView view(ServerPlayer player) {
        LotteryData data = Lottery.data(player.level().getServer());
        long now = Lottery.now();
        int mine = data.tickets(player.getUUID());
        int sold = data.sold();
        String odds = mine <= 0 || sold <= 0 ? "Aucun ticket"
                : String.format(Locale.ROOT, "%.1f", 100.0d * mine / sold).replace('.', ',') + " % de chances";
        List<Card> tickets = new ArrayList<>();
        for (int count : new int[] {1, 10}) {
            boolean room = mine + count <= Lottery.MAX_TICKETS;
            Card card = Card.of(new ItemStack(count == 1 ? Items.PAPER : Items.BOOK), count == 1 ? "Un ticket"
                            : "Dix tickets", List.of(MoneyFormat.exact(Lottery.PRICE * count) + " $",
                            "+" + MoneyFormat.exact(Lottery.share(count)) + " $ a la cagnotte"))
                    .accent(0xFFFAC400);
            tickets.add(room && data.draw() > 0L ? card.action("buy:" + count + "|acheter")
                    : card.state(MenuView.DISABLED).tooltip(List.of("Au plus " + Lottery.MAX_TICKETS
                    + " tickets par tirage")));
        }
        tickets.add(Card.of(new ItemStack(Items.CLOCK), "Le tirage", List.of(data.draw() > 0L ? date(data.draw())
                        : "Bientot", data.candidates() + " candidats, " + sold + " tickets"))
                .accent(0xFF27C4E8).tooltip(List.of("Un seul ticket gagne toute la cagnotte",
                        "Chaque dimanche a 20 h, heure de Paris",
                        "Absent au tirage : tu es paye a ta connexion")));
        List<Card> past = new ArrayList<>();
        for (LotteryData.Draw draw : data.history()) {
            boolean empty = draw.tickets() == 0;
            past.add(Card.of(new ItemStack(draw.player() ? Items.GOLD_BLOCK : Items.GOLD_NUGGET),
                            empty ? "Reportee" : draw.winner(), List.of(MoneyFormat.exact(draw.pot()) + " $",
                                    date(draw.time())))
                    .accent(draw.player() ? 0xFFFAC400 : 0xFF55545C)
                    .badge(empty ? "" : draw.tickets() + " TICKETS"));
        }
        if (past.isEmpty()) {
            past.add(Card.of(new ItemStack(Items.GOLD_NUGGET), "Aucun tirage", List.of("Le premier a lieu",
                    "dimanche a 20 h")).accent(0xFF55545C));
        }
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("CAGNOTTE", MoneyFormat.exact(data.pot()) + " $", 0xFFFAC400),
                new MenuView.Stat("TIRAGE", data.draw() > 0L
                        ? "DANS " + until(data.draw() - now).toUpperCase(Locale.ROOT) : "-", 0xFFFFFFFF),
                new MenuView.Stat("TES TICKETS", mine + " · " + odds.toUpperCase(Locale.ROOT), 0xFF22E558));
        return new MenuView(id(), "Loterie du spawn", "Ticket a " + MoneyFormat.exact(Lottery.PRICE) + " $, tirage le "
                + "dimanche a 20 h", stats, List.of(
                new MenuView.Section("TICKETS", "upgrade", MenuView.GRID, tickets),
                new MenuView.Section("DERNIERS TIRAGES", "resources", MenuView.GRID, past)), true);
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if (!key.startsWith("buy:")) {
            return;
        }
        int count;
        try {
            count = Integer.parseInt(key.substring(4));
        } catch (NumberFormatException wrong) {
            return;
        }
        if (count != 1 && count != 10) {
            return;
        }
        String refusal = Lottery.buy(player, count);
        if (refusal != null) {
            Lottery.tell(player, refusal, ChatFormatting.RED);
        }
    }

    /** Pour la carte de l'hotesse du spawn : la cagnotte et le tirage. */
    public static List<String> teaser(ServerPlayer player) {
        LotteryData data = Lottery.data(player.level().getServer());
        return List.of("Cagnotte " + MoneyFormat.exact(data.pot()) + " $", data.draw() > 0L
                ? "Tirage dans " + until(data.draw() - Lottery.now()) : "Tirage le dimanche a 20 h");
    }
}

package fr.atraxterra.dohy.grade;

import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * La page des grades : les acheter, voir ce que chacun donne, et lancer ses commandes d'un clic.
 *
 * <p>Les cartes d'achat sont aussi celles de la section Grades de la Boutique
 * ({@link #offers(ServerPlayer, String)}) : un seul endroit decide de leur prix et de leur etat.
 */
public final class GradesController extends MenuController {

    public static final GradesController INSTANCE = new GradesController();

    private static final int GOLD = 0xFFFAC400;

    private GradesController() {
        super("grades");
    }

    @Override
    public MenuView view(ServerPlayer player) {
        PlayerGrade grade = Grades.grade(player);
        int days = Grades.premiumDays(player);
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("TON GRADE", grade.label().toUpperCase(Locale.ROOT),
                        grade == PlayerGrade.NONE ? 0xFFB8BCC6 : grade.color()),
                new MenuView.Stat("PREMIUM", days > 0 ? days + " J" : "NON", days > 0 ? Premium.COLOR : 0xFFB8BCC6),
                new MenuView.Stat("TON ARGENT", MoneyFormat.exact(Economy.balance(player)) + " $", 0xFFFFFFFF));
        List<MenuView.Section> sections = new ArrayList<>();
        sections.add(new MenuView.Section("LES GRADES : ACHETES POUR TOUJOURS", "upgrade", MenuView.GRID,
                offers(player, "")));
        sections.add(new MenuView.Section("TES COMMANDES", "craft", MenuView.GRID, commands(player)));
        sections.add(new MenuView.Section("CE QUE DONNE CHAQUE GRADE", "resources", MenuView.ROWS, perks(player)));
        sections.add(new MenuView.Section("POUR TOUS, AVEC OU SANS GRADE", "", MenuView.TEXT, List.of(
                Card.of(ItemStack.EMPTY, "", everyone(player)))));
        return new MenuView(id(), "Grades", "Du confort, pas de puissance : un grade s'achete pour toujours, Premium "
                + "dure " + Premium.DAYS + " jours.", stats, sections, true);
    }

    /**
     * Les cartes d'achat : les trois grades et Premium. {@code prefix} precede leurs actions
     * (« grade: » dans la Boutique, rien ici) ; leur clic droit ouvre cette page.
     */
    public static List<Card> offers(ServerPlayer player, String prefix) {
        PlayerGrade current = Grades.grade(player);
        long balance = Economy.balance(player);
        List<Card> cards = new ArrayList<>();
        for (PlayerGrade grade : PlayerGrade.buyable()) {
            long cost = Grades.priceFor(player, grade);
            List<String> tooltip = new ArrayList<>(summary(grade));
            Card card;
            if (cost < 0) {
                card = Card.of(DoHyGrades.badge(grade), grade.label(), List.of(
                                current == grade ? "Ton grade" : "Contenu dans " + current.label(), headline(grade)))
                        .state(MenuView.DONE);
            } else {
                boolean upgrade = current != PlayerGrade.NONE;
                String price = MoneyFormat.exact(cost) + " $";
                card = Card.of(DoHyGrades.badge(grade), grade.label(), List.of(
                                upgrade ? "Passer : " + price : price, headline(grade)))
                        .action(prefix + "buy:" + grade.id() + "|acheter")
                        .confirm("Acheter le grade " + grade.label() + " ?|" + price + (upgrade
                                ? ", la difference avec ton grade " + current.label() : "") + ". Pour toujours.");
                if (upgrade) {
                    tooltip.add("Prix plein " + MoneyFormat.exact(grade.price())
                            + " $ : tu ne paies que la difference");
                }
                if (cost > balance) {
                    tooltip.add("Il te manque " + MoneyFormat.exact(cost - balance) + " $");
                    card = card.state(MenuView.DISABLED);
                }
            }
            if (!prefix.isEmpty()) {
                card = card.right("grades|tous les avantages");
            }
            cards.add(card.accent(grade.color()).badge(grade == current ? "TON GRADE" : "")
                    .tooltip(tooltip));
        }
        int days = Grades.premiumDays(player);
        List<String> tooltip = new ArrayList<>(premiumSummary());
        Card premium = Card.of(DoHyGrades.premiumBadge(), "Premium", List.of(
                        MoneyFormat.exact(Premium.PRICE) + " $ · " + Premium.DAYS + " jours",
                        days > 0 ? "Actif encore " + days + " j" : "/ec, /fly, /nv, +5 homes"))
                .accent(Premium.COLOR).badge(days > 0 ? days + " J" : "30 J")
                .action(prefix + "premium|" + (days > 0 ? "prolonger de 30 jours" : "acheter 30 jours"))
                .confirm((days > 0 ? "Prolonger Premium de " : "Acheter Premium pour ") + Premium.DAYS + " jours ?|"
                        + MoneyFormat.exact(Premium.PRICE) + " $. Le temps court meme partie fermee.");
        if (Premium.PRICE > balance) {
            tooltip.add("Il te manque " + MoneyFormat.exact(Premium.PRICE - balance) + " $");
            premium = premium.state(MenuView.DISABLED);
        } else if (days > 0) {
            premium = premium.state(MenuView.DONE);
        }
        if (!prefix.isEmpty()) {
            premium = premium.right("grades|tous les avantages");
        }
        cards.add(premium.tooltip(tooltip));
        return cards;
    }

    /** La ligne d'une carte d'achat : ce qui se voit d'abord. */
    private static String headline(PlayerGrade grade) {
        return switch (grade) {
            case HEROS -> grade.homes() + " homes · /craft · /minage";
            case LEGENDE -> grade.homes() + " homes · /feed · TP direct";
            case DIVINITE -> grade.homes() + " homes · /hat · /condense";
            case NONE -> "";
        };
    }

    /** La bulle d'une carte d'achat : tout ce que le grade donne. */
    static List<String> summary(PlayerGrade grade) {
        List<String> lines = new ArrayList<>();
        lines.add(grade.homes() + " homes, " + grade.rtp() + " /rtp par mois");
        lines.add("Ender Chest de " + grade.enderRows() * 9 + " cases");
        lines.add("+" + percent(grade.jobXp()) + " XP de metier, +" + percent(grade.passXp()) + " XP du pass, +"
                + percent(grade.vanillaXp()) + " XP classique");
        lines.add("+" + grade.auctionSlots() + " annonces a l'hotel des ventes, XP gardee a la mort");
        if (grade.taxCut() > 0) {
            lines.add("-" + percent(grade.taxCut()) + " de taxe au marche international");
        }
        lines.add(grade.atLeast(PlayerGrade.LEGENDE) ? "Pseudo, chat en couleur, emojis, teleportation directe"
                : "Pseudo et chat en couleur");
        StringBuilder commands = new StringBuilder("Commandes :");
        for (GradeCommand command : GradeCommand.values()) {
            if (!command.premium() && grade.atLeast(command.grade())) {
                commands.append(" /").append(command.command());
            }
        }
        lines.add(commands.toString());
        return lines;
    }

    static List<String> premiumSummary() {
        return List.of("S'ajoute a ton grade, ou a l'absence de grade",
                "+" + Premium.HOMES + " homes, +" + Premium.RTP + " /rtp par mois, une rangee d'Ender Chest",
                "+" + percent(Premium.JOB_XP) + " XP de metier, +" + percent(Premium.PASS_XP) + " XP du pass, +"
                        + percent(Premium.VANILLA_XP) + " XP classique",
                "-" + percent(Premium.TAX_CUT) + " de taxe au marche international en plus",
                "Commandes : /ec /fly (au spawn) /nv", "Ton arrivee annoncee a tous les joueurs",
                "Racheter ajoute " + Premium.DAYS + " jours");
    }

    /** Les commandes des grades : celles qu'on a se lancent d'un clic. */
    private static List<Card> commands(ServerPlayer player) {
        List<Card> cards = new ArrayList<>();
        for (GradeCommand command : GradeCommand.values()) {
            boolean allowed = Grades.can(player, command);
            int accent = command.premium() ? Premium.COLOR : command.grade().color();
            Card card = Card.of(new ItemStack(command.icon()), "/" + command.command(), List.of(
                            command.description(), allowed ? state(player, command) : command.requirement()))
                    .accent(accent);
            if (allowed) {
                card = card.action("run:" + command.command() + "|lancer");
            } else {
                card = card.state(MenuView.DISABLED).tooltip(List.of((command.premium() ? "Avec Premium"
                        : "A partir du grade " + command.grade().label())));
            }
            cards.add(card);
        }
        return cards;
    }

    /** La seconde ligne d'une commande debloquee : son etat quand elle en a un. */
    private static String state(ServerPlayer player, GradeCommand command) {
        GradeData data = Grades.data(player);
        return switch (command) {
            case FLY -> data.fly() ? "Active" : "Coupe";
            case NV -> data.nightVision() ? "Active" : "Coupee";
            case TPTOGGLE -> data.tpaClosed() ? "Demandes refusees" : "Demandes acceptees";
            default -> "Clic : lancer";
        };
    }

    /** Chaque avantage, grade par grade, avec la valeur du joueur en pastille. */
    private static List<Card> perks(ServerPlayer player) {
        List<Card> rows = new ArrayList<>();
        rows.add(perk(Items.BED.pick(net.minecraft.world.item.DyeColor.RED), "Homes (/sethome)", "homes",
                value -> String.valueOf(value.homes()),
                "+" + Premium.HOMES, String.valueOf(Grades.homes(player))));
        rows.add(perk(Items.COMPASS, "Teleportations au hasard (/rtp), par mois", "rtp",
                value -> String.valueOf(value.rtp()), "+" + Premium.RTP, String.valueOf(Grades.rtpPerMonth(player))));
        rows.add(perk(Items.ENDER_CHEST, "Cases de l'Ender Chest", "ender",
                value -> String.valueOf(value.enderRows() * 9), "+9", String.valueOf(Grades.enderRows(player) * 9)));
        rows.add(perk(Items.EXPERIENCE_BOTTLE, "XP de metier", "job", value -> "+" + percent(value.jobXp()),
                "+" + percent(Premium.JOB_XP), "+" + percent(Grades.jobXpBonus(player))));
        rows.add(perk(Items.AMETHYST_SHARD, "XP du pass", "pass", value -> "+" + percent(value.passXp()),
                "+" + percent(Premium.PASS_XP), "+" + percent(Grades.passXpBonus(player))));
        rows.add(perk(Items.LAPIS_LAZULI, "XP classique (orbes)", "xp", value -> "+" + percent(value.vanillaXp()),
                "+" + percent(Premium.VANILLA_XP), "+" + percent(Grades.vanillaXpBonus(player))));
        rows.add(perk(Items.GOLD_INGOT, "Taxe du marche international", "tax",
                value -> value.taxCut() > 0 ? "-" + percent(value.taxCut()) : "0",
                "-" + percent(Premium.TAX_CUT), Grades.marketTaxCut(player) > 0
                        ? "-" + percent(Grades.marketTaxCut(player)) : "0"));
        rows.add(perk(Items.NAME_TAG, "Annonces a l'hotel des ventes", "auction",
                value -> "+" + value.auctionSlots(), "rien", "+" + Grades.auctionSlots(player)));
        rows.add(perk(Items.TOTEM_OF_UNDYING, "XP gardee a la mort", "keep",
                value -> value == PlayerGrade.NONE ? "non" : "oui", "rien", Grades.keepsXp(player) ? "OUI" : "NON"));
        rows.add(perk(Items.ENDER_EYE, "Teleportation sans attendre 3 s", "tp",
                value -> value.atLeast(PlayerGrade.LEGENDE) ? "oui" : "non", "rien",
                Grades.instantTeleport(player) ? "OUI" : "NON"));
        rows.add(perk(Items.WRITABLE_BOOK, "Chat en couleur (&a, &l...)", "colors",
                value -> value.atLeast(PlayerGrade.HEROS) ? "oui" : "non", "rien",
                Grades.chatColors(player) ? "OUI" : "NON"));
        rows.add(perk(Items.SUNFLOWER, "Emojis dans le chat (:coeur:...)", "emojis",
                value -> value.atLeast(PlayerGrade.LEGENDE) ? "oui" : "non", "rien",
                Grades.emojis(player) ? "OUI" : "NON"));
        return rows;
    }

    private interface Value {
        String of(PlayerGrade grade);
    }

    private static Card perk(net.minecraft.world.level.ItemLike icon, String title, String key, Value value,
                             String premium, String mine) {
        StringBuilder line = new StringBuilder();
        for (PlayerGrade grade : PlayerGrade.values()) {
            if (line.length() > 0) {
                line.append("  ·  ");
            }
            line.append(grade.label()).append(" ").append(value.of(grade));
        }
        return Card.of(new ItemStack(icon), title, List.of(line.toString(), "Premium : " + premium))
                .badge("TOI : " + mine).accent(GOLD);
    }

    /** Les commandes de tous, avec ce qui reste au joueur. */
    private static List<String> everyone(ServerPlayer player) {
        int homes = Grades.data(player).homes().size();
        return List.of(
                "/sethome [nom] : pose un home la ou tu es (" + homes + " / " + Grades.homes(player) + ")",
                "/home [nom] : y retourner ; /homes : la liste ; /delhome nom : l'oublier",
                "/rtp : un endroit au hasard dans le monde (encore " + Grades.rtpLeft(player) + " ce mois-ci)",
                "/tpa joueur, /tpahere joueur, puis /tpaccept ou /tpdeny : se rejoindre entre joueurs",
                "! Sans Legende, une teleportation attend trois secondes sans bouger. Jamais en combat, ni depuis "
                        + "un donjon, une arene, Arrakis ou la dimension du Mineur.");
    }

    private static String percent(double value) {
        return Math.round(value * 100.0d) + " %";
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        handle(player, key);
    }

    /** Un clic de cette page ou de la section Grades de la Boutique ; vrai s'il etait pour nous. */
    public static boolean handle(ServerPlayer player, String key) {
        if (key.startsWith("buy:")) {
            PlayerGrade grade = PlayerGrade.byId(key.substring(4));
            report(player, Grades.buy(player, grade),
                    "Grade " + grade.label() + " achete : tes avantages sont actifs.");
            return true;
        }
        if (key.equals("premium")) {
            report(player, Grades.buyPremium(player),
                    "Premium actif encore " + Grades.premiumDays(player) + " jours.");
            return true;
        }
        if (key.startsWith("run:")) {
            for (GradeCommand command : GradeCommand.values()) {
                if (command.command().equals(key.substring(4))) {
                    // Ce qui ouvre un ecran (etabli, Ender Chest) ou teleporte ferme la page d'abord.
                    if (command == GradeCommand.CRAFT || command == GradeCommand.EC
                            || command == GradeCommand.MINAGE) {
                        UiNet.close(player);
                    }
                    GradeTools.run(player, command);
                }
            }
            return true;
        }
        if (key.equals("grades")) {
            INSTANCE.open(player);
            return true;
        }
        return false;
    }
}

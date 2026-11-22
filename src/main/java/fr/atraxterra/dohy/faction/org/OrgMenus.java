package fr.atraxterra.dohy.faction.org;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionMenus;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L'onglet Organisation de la nation (lot 24a) : le regime, les postes et les cadres PNJ qui
 * les tiennent, le recrutement du jour, les parametres. Un cadre s'ouvre sur sa fiche :
 * promotion, poste, renvoi.
 */
public final class OrgMenus {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM HH:mm")
            .withZone(ZoneId.of("Europe/Paris"));

    private OrgMenus() {
    }

    /** Une page de la nation : sans nation, on la fonde d'abord ; l'hexagone revient a l'organisation. */
    private abstract static class Page extends MenuController {
        Page(String path) {
            super(path);
        }

        @Override
        public void open(ServerPlayer player) {
            if (!FactionApi.has(player)) {
                FactionMenus.Overview.INSTANCE.open(player);
                return;
            }
            super.open(player);
        }

        @Override
        protected void back(ServerPlayer player) {
            FactionMenus.Overview.INSTANCE.open(player);
        }
    }

    static void report(ServerPlayer player, String refusal, String success) {
        if (refusal != null) {
            FactionApi.tell(player, refusal + ".", ChatFormatting.RED);
        } else if (!success.isEmpty()) {
            FactionApi.tell(player, success, ChatFormatting.GREEN);
        }
    }

    /** Le casque de son grade : cuir, maille, fer, or, diamant. */
    static ItemStack helmet(Grade grade) {
        return new ItemStack(switch (grade) {
            case RECRUE -> Items.LEATHER_HELMET;
            case MEMBRE -> Items.CHAINMAIL_HELMET;
            case LIEUTENANT -> Items.IRON_HELMET;
            case CAPITAINE -> Items.GOLDEN_HELMET;
            case CADRE -> Items.DIAMOND_HELMET;
        });
    }

    static String stars(int skill) {
        return "talent " + skill + " / 5";
    }

    /** Le nom de l'entreprise que ce cadre gere (lot 24b). */
    static String managed(ServerPlayer player, Cadre cadre) {
        fr.atraxterra.dohy.faction.enterprise.Enterprise enterprise =
                fr.atraxterra.dohy.faction.enterprise.Enterprises.find(player, cadre.manages());
        return enterprise == null ? "une entreprise fermee" : enterprise.name();
    }

    static int loyaltyColor(double loyalty) {
        return loyalty >= 60.0d ? 0xFF22E558 : loyalty >= 30.0d ? 0xFFFAC400 : 0xFFEF3826;
    }

    // -- L'organisation ---------------------------------------------------------------------------

    public static final class Org extends Page {
        public static final Org INSTANCE = new Org();

        private Org() {
            super("faction_organisation");
        }

        @Override
        public MenuView view(ServerPlayer player) {
            Organisation org = Organisations.of(player);
            long now = System.currentTimeMillis();
            Regime regime = org.regime();
            int cap = Organisations.cap(player);
            long payroll = Organisations.payroll(player);

            List<Card> regimes = new ArrayList<>();
            long ready = Organisations.regimeReady(player);
            for (Regime option : Regime.values()) {
                boolean current = option == regime && org.regimeSince() > 0L;
                List<String> lines = new ArrayList<>();
                lines.add(option.effects());
                lines.add(current ? "Ton regime" : now < ready ? "Change dans " + Organisations.duration(ready - now)
                        : "Choisir ce regime");
                Card card = Card.of(option.icon(), option.label(), lines).accent(option.color())
                        .tooltip(List.of(option.summary(), "Confiance des nations " + signed(option.trust())
                                + ", crainte " + signed(option.fear()), "Le regime ne change qu'une fois par semaine"));
                if (current) {
                    card = card.badge("ACTUEL").state(MenuView.DONE);
                } else if (now >= ready) {
                    card = card.action("regime:" + option.name() + "|choisir").confirm("Devenir "
                            + option.label().toLowerCase() + " ?|Le regime ne change ensuite qu'une fois par semaine");
                } else {
                    card = card.state(MenuView.DISABLED);
                }
                regimes.add(card);
            }

            List<Card> posts = new ArrayList<>();
            for (Post post : Post.values()) {
                Optional<Cadre> holder = Organisations.holder(player, post);
                int strength = (int) Math.round(Organisations.strength(player, post) * 100.0d);
                Card card;
                if (holder.isPresent()) {
                    Cadre cadre = holder.get();
                    card = Card.of(post.icon(), post.label(), List.of(cadre.name() + " · " + cadre.rank().label(),
                                    "Efficacite " + strength + " %"))
                            .accent(post.color()).action("cadre:" + cadre.id() + "|voir sa fiche")
                            .right("vacate:" + post.name() + "|liberer le poste");
                } else {
                    card = Card.of(post.icon(), post.label(), List.of("Vacant", "Grade " + post.grade().label()
                                    + " au moins"))
                            .accent(0xFF55545C).action("assign:" + post.name() + "|nommer le meilleur cadre");
                }
                posts.add(card.tooltip(List.of(post.effect(), "Clic droit : liberer le poste")));
            }

            List<Card> cadres = new ArrayList<>();
            for (Cadre cadre : org.cadres()) {
                Post role = cadre.role();
                String duty = role != null ? " · " + role.label()
                        : cadre.manages().isEmpty() ? "" : " · gere " + managed(player, cadre);
                cadres.add(Card.of(helmet(cadre.rank()), cadre.name(), List.of(
                                cadre.rank().label() + " · " + stars(cadre.skill()) + " · " + cadre.character().label(),
                                "Loyaute " + Math.round(cadre.loyalty()) + " · " + MoneyFormat.exact(
                                        Organisations.salary(player, cadre)) + " $ / jour" + duty))
                        .accent(loyaltyColor(cadre.loyalty())).progress(Math.round(cadre.loyalty()), 100L)
                        .badge(role != null ? "POSTE" : cadre.manages().isEmpty() ? "" : "GERANT")
                        .action("cadre:" + cadre.id() + "|voir sa fiche"));
            }
            if (cadres.isEmpty()) {
                cadres.add(Card.of(new ItemStack(Items.PLAYER_HEAD), "Aucun cadre", List.of(
                        "Recrute parmi les candidats du jour", "Ils tiennent les postes de ta nation"))
                        .state(MenuView.DISABLED));
            }

            List<Card> recruits = new ArrayList<>();
            List<Cadre> candidates = Organisations.candidates(player);
            boolean full = org.cadres().size() >= cap;
            for (Cadre candidate : candidates) {
                long signing = Organisations.signing(player, candidate);
                Card card = Card.of(helmet(candidate.rank()), candidate.name(), List.of(
                                candidate.rank().label() + " · " + stars(candidate.skill()) + " · "
                                        + candidate.character().label(),
                                MoneyFormat.exact(Organisations.salary(player, candidate)) + " $ / jour · prime "
                                        + MoneyFormat.exact(signing) + " $"))
                        .accent(0xFF27C4E8).tooltip(List.of(candidate.character().hint(),
                                "Loyaute de depart " + Math.round(candidate.loyalty())));
                recruits.add(full ? card.state(MenuView.DISABLED) : card.action("hire:" + candidate.id()
                        + "|embaucher").confirm("Embaucher " + candidate.name() + " ?|Prime de "
                        + MoneyFormat.exact(signing) + " $, puis " + MoneyFormat.exact(Organisations.salary(player,
                        candidate)) + " $ par jour depuis la banque nationale"));
            }

            List<Card> settings = List.of(Card.of(new ItemStack(Items.COMPARATOR), "Parametres", List.of(
                            "Nom, description, MOTD", "Blason, dissolution"))
                    .accent(0xFF55545C).action("settings|ouvrir"));

            List<Card> journal = new ArrayList<>();
            for (String line : org.journal()) {
                int bar = line.indexOf('|');
                if (bar < 0) {
                    continue;
                }
                long at;
                try {
                    at = Long.parseLong(line.substring(0, bar));
                } catch (NumberFormatException error) {
                    continue;
                }
                journal.add(Card.of(new ItemStack(Items.PAPER), line.substring(bar + 1), List.of(
                        WHEN.format(Instant.ofEpochMilli(at)))));
                if (journal.size() >= 6) {
                    break;
                }
            }

            List<MenuView.Stat> stats = new ArrayList<>();
            stats.add(new MenuView.Stat("REGIME", regime.label().toUpperCase(), regime.color()));
            stats.add(new MenuView.Stat("CADRES", org.cadres().size() + " / " + cap, 0xFFFFFFFF));
            stats.add(new MenuView.Stat(org.debt() > 0L ? "DETTE" : "SALAIRES", MoneyFormat.shortForm(org.debt() > 0L
                    ? org.debt() : payroll) + (org.debt() > 0L ? " $" : " $/j"), org.debt() > 0L ? 0xFFEF3826
                    : 0xFFFAC400));
            List<MenuView.Section> sections = new ArrayList<>();
            sections.add(new MenuView.Section("REGIME", "craft", MenuView.GRID, regimes));
            sections.add(new MenuView.Section("POSTES", "upgrade", MenuView.GRID, posts));
            sections.add(new MenuView.Section("TES CADRES", "resources", MenuView.ROWS, cadres));
            sections.add(new MenuView.Section("CANDIDATS DU JOUR - NOUVEAUX DANS " + Organisations.duration(
                    Math.max(60_000L, org.candidatesAt + Organisations.DAY_MS - now)).toUpperCase(), "craft",
                    MenuView.GRID, recruits));
            sections.add(new MenuView.Section("NATION", "craft", MenuView.GRID, settings));
            if (!journal.isEmpty()) {
                sections.add(new MenuView.Section("JOURNAL", "resources", MenuView.AGENDA, journal));
            }
            return new MenuView(id(), "Organisation", regime.summary(), stats, sections, true)
                    .withTabs(FactionMenus.tabs(player, FactionMenus.ORGANISATION));
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            if (key.startsWith("regime:")) {
                Regime regime = Regime.of(key.substring(7));
                report(player, Organisations.setRegime(player, regime), "Ta nation devient : " + regime.label()
                        + ".");
            } else if (key.startsWith("assign:")) {
                Post post = Post.of(key.substring(7));
                if (post != null) {
                    report(player, Organisations.assign(player, post), "");
                }
            } else if (key.startsWith("vacate:")) {
                Post post = Post.of(key.substring(7));
                if (post != null) {
                    report(player, Organisations.vacate(player, post), "");
                }
            } else if (key.startsWith("hire:")) {
                report(player, Organisations.hire(player, key.substring(5)), "Un cadre rejoint ta nation.");
            } else if (key.startsWith("cadre:")) {
                CadrePage.INSTANCE.show(player, key.substring(6));
            } else if (key.equals("settings")) {
                FactionMenus.Settings.INSTANCE.open(player);
            }
        }
    }

    private static String signed(double value) {
        long rounded = Math.round(value);
        return (rounded > 0 ? "+" : "") + rounded;
    }

    // -- La fiche d'un cadre ----------------------------------------------------------------------

    public static final class CadrePage extends Page {
        public static final CadrePage INSTANCE = new CadrePage();
        private final Map<UUID, String> selected = new ConcurrentHashMap<>();

        private CadrePage() {
            super("faction_cadre");
        }

        void show(ServerPlayer player, String id) {
            selected.put(player.getUUID(), id);
            open(player);
        }

        @Override
        protected void back(ServerPlayer player) {
            Org.INSTANCE.open(player);
        }

        private Cadre cadre(ServerPlayer player) {
            String id = selected.get(player.getUUID());
            if (id == null) {
                return null;
            }
            for (Cadre cadre : Organisations.cadres(player)) {
                if (cadre.id().equals(id)) {
                    return cadre;
                }
            }
            return null;
        }

        @Override
        public MenuView view(ServerPlayer player) {
            Cadre cadre = cadre(player);
            if (cadre == null) {
                return new MenuView(id(), "Cadre", "Il n'est plus dans ta nation", List.of(), List.of(
                        new MenuView.Section("CADRE", "craft", MenuView.GRID, List.of(Card.of(
                                new ItemStack(Items.BARRIER), "Parti", List.of("Ce cadre a quitte ta nation"))
                                .state(MenuView.DISABLED)))), true)
                        .withTabs(FactionMenus.tabs(player, FactionMenus.ORGANISATION));
            }
            long now = System.currentTimeMillis();
            Grade next = cadre.rank().next();
            List<Card> actions = new ArrayList<>();
            if (next == null) {
                actions.add(Card.of(helmet(cadre.rank()), "Au sommet", List.of("Haut cadre", "Plus de grade au-dessus"))
                        .state(MenuView.DONE));
            } else {
                long price = Organisations.promotionPrice(player, cadre);
                boolean ready = now >= cadre.promotable();
                Card promote = Card.of(helmet(next), "Promouvoir " + next.label(), List.of(
                                MoneyFormat.exact(price) + " $ (une semaine du nouveau salaire)",
                                ready ? "Loyaute +10" : "Possible dans " + Organisations.duration(
                                        cadre.promotable() - now)))
                        .accent(0xFFFAC400);
                actions.add(ready ? promote.action("promote|promouvoir").confirm("Promouvoir " + cadre.name() + " "
                        + next.label() + " ?|" + MoneyFormat.exact(price) + " $ depuis la banque nationale")
                        : promote.state(MenuView.DISABLED));
            }
            Post role = cadre.role();
            if (role != null) {
                actions.add(Card.of(role.icon(), "Liberer le poste", List.of(role.label(), "Il redevient libre"))
                        .accent(0xFF55545C).action("unpost|liberer"));
            } else if (!cadre.manages().isEmpty()) {
                actions.add(Card.of(new ItemStack(Items.EMERALD_BLOCK), "Quitter la gerance", List.of(
                                managed(player, cadre), "Il redevient libre"))
                        .accent(0xFF55545C).action("unmanage|liberer")
                        .right("enterprise|voir l'entreprise"));
            }
            actions.add(Card.of(new ItemStack(Items.BARRIER), "Renvoyer", List.of("Sans indemnite",
                            "Son poste se libere"))
                    .accent(0xFFEF3826).action("fire|renvoyer").confirm("!Renvoyer " + cadre.name() + " ?|Il quitte "
                            + "ta nation sans indemnite"));

            List<Card> posts = new ArrayList<>();
            for (Post post : Post.values()) {
                boolean held = post.name().equals(cadre.post());
                boolean able = cadre.grade() >= post.grade().ordinal();
                Optional<Cadre> holder = Organisations.holder(player, post);
                Card card = Card.of(post.icon(), post.label(), List.of(held ? "Son poste"
                                : holder.map(other -> "Tenu par " + other.name()).orElse("Vacant"),
                        able ? "Grade suffisant" : "Il faut " + post.grade().label()))
                        .accent(post.color()).tooltip(List.of(post.effect()));
                if (held) {
                    card = card.badge("ACTUEL").state(MenuView.DONE);
                } else if (able) {
                    card = card.action("post:" + post.name() + "|lui confier ce poste");
                } else {
                    card = card.state(MenuView.DISABLED);
                }
                posts.add(card);
            }

            List<MenuView.Stat> stats = List.of(
                    new MenuView.Stat("GRADE", cadre.rank().label().toUpperCase(), 0xFFFAC400),
                    new MenuView.Stat("LOYAUTE", Math.round(cadre.loyalty()) + " / 100", loyaltyColor(cadre.loyalty())),
                    new MenuView.Stat("SALAIRE", MoneyFormat.shortForm(Organisations.salary(player, cadre)) + " $/j",
                            0xFFFFFFFF));
            String subtitle = stars(cadre.skill()) + " · " + cadre.character().label() + " : "
                    + cadre.character().hint().toLowerCase();
            return new MenuView(id(), cadre.name(), subtitle, stats, List.of(
                    new MenuView.Section("ACTIONS", "craft", MenuView.GRID, actions),
                    new MenuView.Section("POSTES", "upgrade", MenuView.GRID, posts)), true)
                    .withTabs(FactionMenus.tabs(player, FactionMenus.ORGANISATION));
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            Cadre cadre = cadre(player);
            if (cadre == null) {
                return;
            }
            switch (key) {
                case "promote" -> report(player, Organisations.promote(player, cadre.id()), cadre.name()
                        + " monte en grade.");
                case "unpost" -> {
                    Post role = cadre.role();
                    if (role != null) {
                        report(player, Organisations.vacate(player, role), "");
                    }
                }
                case "unmanage" -> {
                    if (!cadre.manages().isEmpty()) {
                        report(player, Organisations.release(player, cadre.manages(), managed(player, cadre)), "");
                    }
                }
                case "enterprise" -> {
                    if (!cadre.manages().isEmpty()) {
                        fr.atraxterra.dohy.faction.enterprise.EnterpriseMenus.Detail.INSTANCE.show(player,
                                cadre.manages());
                    }
                }
                case "fire" -> {
                    report(player, Organisations.fire(player, cadre.id()), cadre.name() + " quitte ta nation.");
                    Org.INSTANCE.open(player);
                }
                default -> {
                    if (key.startsWith("post:")) {
                        Post post = Post.of(key.substring(5));
                        if (post != null) {
                            report(player, Organisations.appoint(player, cadre.id(), post), cadre.name() + " devient "
                                    + post.label() + ".");
                        }
                    }
                }
            }
        }
    }
}

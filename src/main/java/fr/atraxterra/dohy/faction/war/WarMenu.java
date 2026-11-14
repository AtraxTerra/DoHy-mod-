package fr.atraxterra.dohy.faction.war;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * L'ecran de la guerre : l'assaut en cours, l'ultimatum a repondre, l'heure a
 * choisir, puis les douze nations — leur humeur envers toi, leur force, ce
 * qu'on dit d'elles —, ce qu'elles pensent de tes defenses, et l'histoire de
 * vos guerres.
 */
public final class WarMenu extends MenuController {

    public static final WarMenu INSTANCE = new WarMenu();

    private WarMenu() {
        super("guerre");
    }

    private static ItemStack banner(NationTraits traits) {
        return new ItemStack(Items.BANNER.pick(traits.color()));
    }

    private static int tint(NationTraits traits) {
        return 0xFF000000 | traits.color().getTextColor();
    }

    /** L'humeur en mots et en couleur. */
    static String mood(double hostility) {
        return hostility >= 75.0d ? "Haineuse" : hostility >= 60.0d ? "Hostile" : hostility >= 45.0d ? "Mefiante"
                : hostility >= 25.0d ? "Neutre" : "Amicale";
    }

    private static int moodColor(double hostility) {
        return hostility >= 75.0d ? 0xFFB3261E : hostility >= 60.0d ? 0xFFE8590C : hostility >= 45.0d ? 0xFFE0A100
                : hostility >= 25.0d ? 0xFF8A8A8A : 0xFF2E7D32;
    }

    /** « il y a 5 min », « il y a 3 h », « il y a 2 j ». */
    static String ago(long millis) {
        long minutes = Math.max(0L, millis) / 60_000L;
        if (minutes < 60L) {
            return "il y a " + Math.max(1L, minutes) + " min";
        }
        long hours = minutes / 60L;
        return hours < 48L ? "il y a " + hours + " h" : "il y a " + hours / 24L + " j";
    }

    /** Un ordre de grandeur, pas un chiffre exact : ce que tes espions savent. */
    private static String estimate(long value) {
        if (value < 1000L) {
            return "~" + (value / 10L * 10L);
        }
        return "~" + MoneyFormat.compact(value / 1000L * 1000L);
    }

    @Override
    public MenuView view(ServerPlayer player) {
        WarData data = WarStrategy.data(player);
        long now = System.currentTimeMillis();
        Siege siege = Sieges.of(player.getUUID());
        Optional<WarData.Relation> conflict = data.conflict();
        int hostile = 0;
        for (WarData.Relation relation : data.relations()) {
            if (relation.hostility() >= 60.0d) {
                hostile++;
            }
        }
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("HOSTILES", hostile + " / " + NationTraits.count(), hostile > 0 ? 0xFFE8590C
                        : 0xFF55FF55),
                new MenuView.Stat("GUERRE", conflict.map(r -> r.stance() == WarData.Stance.GUERRE
                        ? r.traits().name().toUpperCase(java.util.Locale.ROOT) : "MENACE").orElse("AUCUNE"),
                        conflict.isPresent() ? 0xFFFF5555 : 0xFFAAAAAA),
                new MenuView.Stat("COFFRE", data.vault().isPresent() ? "DESIGNE" : "AUTO",
                        data.vault().isPresent() ? 0xFFFAC400 : 0xFFAAAAAA));
        List<MenuView.Section> sections = new ArrayList<>();

        // -- l'assaut en cours
        if (siege != null && !siege.finished()) {
            NationTraits traits = siege.traits;
            List<Card> cards = new ArrayList<>();
            cards.add(Card.of(banner(traits), "Assaut " + traits.de(), List.of(siege.status(),
                    "Moral " + Math.round(siege.morale) + " % · " + siege.alive().size() + " soldats"))
                    .accent(0xFFB3261E)
                    .progress(Math.round(siege.morale), 100)
                    .tooltip(List.of("Abats l'etendard du camp : plus de renforts.",
                            "Tue le commandant : leur moral s'effondre.",
                            "Tue le tresorier : ton argent revient.")));
            cards.add(Card.of(new ItemStack(siege.bannerDown ? Items.BARRIER : Items.BANNER.pick(traits.color())),
                    siege.bannerDown ? "Etendard abattu" : "L'etendard tient",
                    List.of(siege.bannerDown ? "Plus de renforts" : "Renforts : " + siege.reserveCount(),
                            siege.camp == null ? "" : "Camp " + siege.bearing(siege.camp))));
            cards.add(Card.of(new ItemStack(Items.CHEST), siege.drained > 0 ? "Ta banque pillee" : "Ton coffre",
                    List.of(siege.drained > 0 ? MoneyFormat.exact(siege.drained) + " $ emportes"
                            : "Pris a " + Math.round(siege.capture * 100.0d) + " %",
                            siege.paymasterDead ? "Tresorier abattu" : "Tresorier en vie"))
                    .accent(siege.drained > 0 ? 0xFFFAC400 : 0xFF55545C));
            sections.add(new MenuView.Section("L'ASSAUT EN COURS", "armor", MenuView.GRID, cards));
        }

        // -- l'ultimatum, ou l'heure de l'assaut
        conflict.ifPresent(relation -> {
            NationTraits traits = relation.traits();
            if (relation.stance() == WarData.Stance.MENACE) {
                List<Card> cards = new ArrayList<>();
                cards.add(Card.of(banner(traits), "Ultimatum " + traits.de(), List.of(
                        MoneyFormat.exact(relation.tribute()) + " $ de tribut",
                        "Avant " + WarStrategy.when(relation.deadline(), now)))
                        .accent(0xFFE8590C).action("pay").right("refuse").badge("PAYER")
                        .tooltip(List.of("Clic gauche : payer depuis ta banque de faction",
                                "Clic droit : refuser, c'est la guerre")));
                cards.add(Card.of(new ItemStack(Items.WRITABLE_BOOK), relation.negotiated() ? "Contre-offre faite"
                        : "Negocier", List.of(relation.negotiated() ? "Ils ont repondu" : "Proposer la moitie",
                        "Selon leur avidite et leur crainte"))
                        .action(relation.negotiated() ? "" : "negotiate")
                        .state(relation.negotiated() ? MenuView.DISABLED : MenuView.NORMAL));
                sections.add(new MenuView.Section("ON TE MENACE", "craft", MenuView.GRID, cards));
            } else if (relation.stance() == WarData.Stance.GUERRE && siege == null) {
                List<Card> cards = new ArrayList<>();
                for (long slot : WarStrategy.slots(relation, now)) {
                    boolean chosen = relation.slot() > 0L && Math.abs(relation.slot() - slot) < 60_000L;
                    cards.add(Card.of(new ItemStack(Items.CLOCK), WarStrategy.when(slot, now),
                            List.of(chosen ? "Heure choisie" : "Fixer l'assaut ici"))
                            .action("slot:" + slot)
                            .accent(chosen ? 0xFFB3261E : 0xFF55545C)
                            .state(chosen ? MenuView.DONE : MenuView.NORMAL));
                }
                String title = relation.slot() > 0L ? "ASSAUT " + WarStrategy.when(relation.slot(), now)
                        .toUpperCase(java.util.Locale.ROOT) : "CHOISIS L'HEURE DE L'ASSAUT";
                sections.add(new MenuView.Section(title, "armor", MenuView.TILES, cards));
                boolean refuses = WarStrategy.refusesPeace(relation, now);
                long price = WarStrategy.peacePrice(player, relation);
                sections.add(new MenuView.Section("LA PAIX", "craft", MenuView.GRID, List.of(
                        Card.of(new ItemStack(refuses ? Items.WITHER_ROSE : Items.POPPY),
                                refuses ? "Ils refusent la paix" : "Acheter la paix",
                                refuses ? List.of("Trop de rancune", "Il faudra te battre")
                                        : List.of(MoneyFormat.exact(price) + " $", "Treve de quelques jours"))
                                .action(refuses ? "" : "peace")
                                .state(refuses ? MenuView.DISABLED : MenuView.NORMAL))));
            }
        });

        // -- les nations
        List<Card> nations = new ArrayList<>();
        for (int i = 0; i < NationTraits.count(); i++) {
            NationTraits traits = NationTraits.of(i);
            Optional<WarData.Relation> found = data.find(i);
            if (found.isEmpty()) {
                nations.add(Card.of(banner(traits), traits.name(), List.of("Ne te connait pas encore",
                        "Armee " + estimate(traits.army()) + " · tresor " + estimate(traits.treasury())))
                        .accent(tint(traits)));
                continue;
            }
            WarData.Relation relation = found.get();
            String stance = switch (relation.stance()) {
                case MENACE -> "Te menace";
                case GUERRE -> "En guerre contre toi";
                case TREVE -> "Treve jusqu'a " + WarStrategy.when(relation.truce(), now);
                default -> mood(relation.hostility()) + " (" + Math.round(relation.hostility()) + ")";
            };
            WarData.Memory memory = relation.memory();
            List<String> tooltip = new ArrayList<>();
            tooltip.add("Doctrine " + traits.doctrine().label() + ", " + traits.reputation());
            tooltip.add("Humeur " + mood(relation.hostility()) + " · crainte " + Math.round(relation.fear()));
            if (relation.grievance(now) >= 1.0d) {
                tooltip.add("Griefs : " + Math.round(relation.grievance(now)));
            }
            if (memory.wins() + memory.losses() > 0) {
                tooltip.add("Assauts : " + memory.wins() + " reussi(s), " + memory.losses() + " brise(s)");
            }
            if (memory.looted() > 0) {
                tooltip.add("T'a pris " + MoneyFormat.exact(memory.looted()) + " $");
            }
            tooltip.add("Clic : un cadeau de 1 000 $ (pas en guerre)");
            nations.add(Card.of(banner(traits), traits.name(), List.of(stance,
                    "Armee " + estimate(relation.army()) + " · tresor " + estimate(relation.treasury())))
                    .accent(relation.stance() == WarData.Stance.PAIX ? moodColor(relation.hostility())
                            : relation.stance() == WarData.Stance.TREVE ? 0xFF2E7D32 : 0xFFB3261E)
                    .progress(Math.round(relation.hostility()), 100)
                    .action(relation.stance() == WarData.Stance.GUERRE ? "" : "gift:" + i)
                    .tooltip(tooltip));
        }
        sections.add(new MenuView.Section("LES NATIONS", "resources", MenuView.GRID, nations));

        // -- tes defenses vues par elles
        double seen = 0.0d;
        int counted = 0;
        for (WarData.Relation relation : data.relations()) {
            if (relation.memory().wins() + relation.memory().losses() > 0) {
                seen += relation.defense();
                counted++;
            }
        }
        String fortress = counted == 0 ? "Aucun assaut encore" : seen / counted >= 0.75d ? "Une forteresse"
                : seen / counted >= 0.45d ? "Une base solide" : seen / counted >= 0.2d ? "Une base percable"
                : "Une base ouverte";
        List<Card> defense = new ArrayList<>();
        defense.add(Card.of(new ItemStack(Items.CHEST), data.vault().isPresent() ? "Ton coffre" : "Coffre automatique",
                data.vault().map(v -> List.of(v.pos().getX() + " " + v.pos().getY() + " " + v.pos().getZ(),
                        "/guerre coffre pour le deplacer"))
                        .orElse(List.of("Le claim le plus garni", "Clic : le poser ou tu es")))
                .action("vault"));
        defense.add(Card.of(new ItemStack(Items.SHIELD), fortress, List.of(
                "Ta force : " + Math.round(WarStrategy.power(player) * 100.0d) + " %",
                data.assaults() + " assaut(s) subi(s)")));
        sections.add(new MenuView.Section("TA BASE VUE PAR ELLES", "upgrade", MenuView.GRID, defense));

        // -- l'histoire
        List<WarData.Event> history = data.history();
        if (!history.isEmpty()) {
            List<Card> rows = new ArrayList<>();
            for (int i = history.size() - 1; i >= 0 && rows.size() < 8; i--) {
                WarData.Event event = history.get(i);
                NationTraits traits = NationTraits.of(event.nation());
                rows.add(Card.of(banner(traits), traits.name() + " : " + event.text(),
                        List.of(ago(now - event.time()))));
            }
            sections.add(new MenuView.Section("L'HISTOIRE DE TES GUERRES", "craft", MenuView.ROWS, rows));
        }
        String subtitle = siege != null && !siege.finished() ? "Ta base est assiegee : ta banque est sous scelle"
                : conflict.map(r -> r.stance() == WarData.Stance.MENACE ? r.traits().name() + " exige un tribut"
                        : r.slot() > 0L ? "Assaut " + r.traits().de() + " " + WarStrategy.when(r.slot(), now)
                        : "Fixe l'heure de l'assaut " + r.traits().de()).orElse(FactionApi.has(player)
                        ? "Les nations t'observent" : "Fonde une faction : les nations l'observeront");
        return new MenuView(id(), "La guerre des nations", subtitle, stats, sections, true);
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        String refusal = null;
        String message = null;
        if (key.equals("pay")) {
            if (button == 1) {
                refusal = WarStrategy.refuse(player);
            } else {
                refusal = WarStrategy.pay(player);
                message = "Tribut paye.";
            }
        } else if (key.equals("refuse")) {
            refusal = WarStrategy.refuse(player);
        } else if (key.equals("negotiate")) {
            refusal = WarStrategy.negotiate(player);
        } else if (key.equals("peace")) {
            refusal = WarStrategy.peace(player);
        } else if (key.equals("vault")) {
            refusal = WarStrategy.setVault(player);
            message = "Ton coffre est ici. Defends-le.";
        } else if (key.startsWith("slot:")) {
            try {
                refusal = WarStrategy.choose(player, Long.parseLong(key.substring(5)));
            } catch (NumberFormatException invalid) {
                refusal = "Heure invalide";
            }
        } else if (key.startsWith("gift:")) {
            try {
                refusal = WarStrategy.gift(player, Integer.parseInt(key.substring(5)), 1_000L);
            } catch (NumberFormatException invalid) {
                refusal = "Nation inconnue";
            }
        } else {
            return;
        }
        if (refusal != null || message != null) {
            report(player, refusal, message == null ? "" : message);
        } else {
            success(player);
        }
    }
}

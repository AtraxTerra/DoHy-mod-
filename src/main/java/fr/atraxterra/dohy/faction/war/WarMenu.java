package fr.atraxterra.dohy.faction.war;

import fr.atraxterra.dohy.earth.EarthNations;
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
 * choisir, la place forte de la nation a raser, la coalition contre toi, ta
 * contre-offensive, puis les douze nations — leur humeur envers toi, leur
 * force, ce qu'on dit d'elles, ce qu'elles te doivent, ce qu'elles ont appris —,
 * ce qu'elles pensent de tes defenses et de ta puissance, et l'histoire de vos
 * guerres.
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
        WarStrategy.Threat threat = WarStrategy.threat(player);
        long might = Math.round(threat.total());
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("HOSTILES", hostile + " / " + NationTraits.count(), hostile > 0 ? 0xFFE8590C
                        : 0xFF55FF55),
                new MenuView.Stat("GUERRE", conflict.map(r -> r.stance() == WarData.Stance.GUERRE
                        ? r.traits().name().toUpperCase(java.util.Locale.ROOT) : "MENACE").orElse("AUCUNE"),
                        conflict.isPresent() ? 0xFFFF5555 : 0xFFAAAAAA),
                new MenuView.Stat("PUISSANCE", might + " / 100", might >= WarStrategy.COALITION_AT ? 0xFFFF5555
                        : might >= WarStrategy.DISSOLVE_AT ? 0xFFE0A100 : 0xFF55FF55),
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
            stakeCard(player, siege.nation).ifPresent(cards::add);
            mineStakeCard(player, siege.nation).ifPresent(cards::add);
            sections.add(new MenuView.Section("L'ASSAUT EN COURS", "armor", MenuView.GRID, cards));
        }

        // -- le missile en approche, ou le terrain bombarde qui attend son retour
        Strike incoming = Strikes.of(player.getUUID());
        long due = StrikeTerrain.dueFor(player.getUUID());
        if (incoming != null || due > 0L) {
            boolean alert = incoming != null;
            sections.add(new MenuView.Section(alert ? "MISSILE EN APPROCHE" : "BASE BOMBARDEE", "armor",
                    MenuView.GRID, List.of(Card.of(new ItemStack(alert ? Items.FIREWORK_ROCKET : Items.TNT),
                            alert ? incoming.warhead.title() : "Le terrain revient", List.of(
                                    alert ? incoming.status() : "Retour " + WarStrategy.when(due, now),
                                    "Coffres et machines epargnes"))
                    .accent(0xFFB3261E)
                    .tooltip(List.of("Le terrain revient 5 h apres l'impact.",
                            "Ce que tu poses ou casses d'ici la reste tel quel.",
                            "Un antimissile de NTM peut l'abattre en vol.")))));
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
                stakeCard(player, relation.nation).ifPresent(cards::add);
                mineStakeCard(player, relation.nation).ifPresent(cards::add);
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
                // Ta contre-offensive : son assaut est sa riposte.
                String what = relation.declared() ? "SA RIPOSTE" : "ASSAUT";
                String title = relation.slot() > 0L ? what + " " + WarStrategy.when(relation.slot(), now)
                        .toUpperCase(java.util.Locale.ROOT) : relation.declared() ? "SA RIPOSTE : CHOISIS L'HEURE"
                        : "CHOISIS L'HEURE DE L'ASSAUT";
                sections.add(new MenuView.Section(title, "armor", MenuView.TILES, cards));
                boolean refuses = WarStrategy.refusesPeace(relation, now);
                long price = WarStrategy.peacePrice(player, relation);
                List<Card> peace = new ArrayList<>();
                peace.add(Card.of(new ItemStack(refuses ? Items.WITHER_ROSE : Items.POPPY),
                                refuses ? "Ils refusent la paix" : "Acheter la paix",
                                refuses ? List.of("Trop de rancune", "Il faudra te battre")
                                        : List.of(MoneyFormat.exact(price) + " $", "Treve de quelques jours"))
                        .action(refuses ? "" : "peace")
                        .state(refuses ? MenuView.DISABLED : MenuView.NORMAL));
                stakeCard(player, relation.nation).ifPresent(peace::add);
                mineStakeCard(player, relation.nation).ifPresent(peace::add);
                sections.add(new MenuView.Section("LA PAIX", "craft", MenuView.GRID, peace));
            }
        });

        // -- leur place forte : la riposte
        Strongholds.Fort fort = Strongholds.of(player.getUUID());
        if (fort != null) {
            NationTraits traits = fort.traits();
            List<Card> cards = new ArrayList<>();
            cards.add(Card.of(new ItemStack(Items.TARGET), "Place forte " + traits.de(),
                            List.of(Strongholds.coordinates(fort), Strongholds.where(player, fort.center)))
                    .accent(tint(traits)).action("fort").badge("VISER")
                    .tooltip(List.of(fort.genre().title() + " (" + fort.kind().label().toLowerCase(
                                    java.util.Locale.ROOT) + ")",
                            "Un designateur de NTM sur ces coordonnees,",
                            "puis tire depuis une rampe de lancement.",
                            "Clic : une boussole vers elle")));
            int worst = Math.min(fort.integrity, fort.vault);
            cards.add(Card.of(new ItemStack(Items.TNT), "Structure " + fort.integrity + " %",
                            List.of("Salle forte " + fort.vault + " %", "Rasee sous " + Strongholds.RAZED_AT + " %"))
                    .accent(worst < 80 ? 0xFFE8590C : 0xFF55545C)
                    .progress(fort.integrity, 100)
                    .tooltip(List.of("Compte apres chaque impact de missile.",
                            "Murs de la salle forte sous " + Strongholds.VAULT_AT + " % : rasee.",
                            "Entrer dans sa salle forte la fait tomber aussi.")));
            WarData.Relation owner = data.find(fort.nation).orElse(null);
            WarData.Relation partner = owner == null ? null : WarStrategy.ally(data, owner, now);
            boolean owes = owner != null && owner.holds() || partner != null && partner.holds();
            List<String> fall = new ArrayList<>();
            if (owner != null && owner.holds()) {
                fall.add("Elle te rend " + WarStrategy.owedLine(player, owner) + ",");
                fall.add("  l'argent tant que son tresor le permet.");
            }
            if (partner != null && partner.holds()) {
                fall.add(partner.traits().name() + " aussi : " + WarStrategy.owedLine(player, partner) + ".");
            }
            fall.add("Puis 30 % de ce qui reste de son tresor,");
            fall.add("la moitie de son armee tombe, un de ses pays");
            fall.add("passe a ta faction (+1 claim), treve de 7 jours.");
            fall.add("5 minutes plus tard, elle s'efface.");
            cards.add(Card.of(new ItemStack(Items.GOLD_INGOT), "Si elle tombe",
                            owes ? List.of("Ce qu'elle t'a pris", "+ 30 % de son tresor")
                                    : List.of("30 % de leur tresor", "Treve de 7 jours"))
                    .accent(0xFFFAC400)
                    .tooltip(fall));
            sections.add(new MenuView.Section("LEUR PLACE FORTE", "armor", MenuView.GRID, cards));
        } else if (Strongholds.building(player.getUUID())) {
            sections.add(new MenuView.Section("LEUR PLACE FORTE", "armor", MenuView.GRID, List.of(
                    Card.of(new ItemStack(Items.SCAFFOLDING), "En chantier", List.of("Loin de tes claims",
                            "Ses coordonnees arrivent")).accent(0xFF55545C))));
        }

        // -- la coalition contre toi (lot 22l-2)
        WarStrategy.coalition(data, now).ifPresent(pair -> {
            WarData.Relation first = pair.get(0);
            WarData.Relation second = pair.get(1);
            List<String> tooltip = new ArrayList<>(List.of(
                    "Ta puissance (" + might + ") les inquiete : au-dela de " + Math.round(WarStrategy.COALITION_AT)
                            + ", les nations s'allient contre toi.",
                    "Allies, leur humeur s'entraine l'une l'autre.",
                    "A chaque assaut, l'allie joint 4 a 10 soldats a ses couleurs,",
                    "et la place forte de la coalition est plus grande.",
                    "Si la place forte de l'une tombe, les deux te rendent",
                    "ce qu'elles t'ont pris, et la coalition se defait.",
                    "Sous " + Math.round(WarStrategy.DISSOLVE_AT) + " de puissance, ou si l'une se radoucit (un cadeau),",
                    "elle se defait aussi."));
            List<Card> cards = new ArrayList<>();
            for (WarData.Relation member : pair) {
                WarData.Relation other = member == first ? second : first;
                NationTraits traits = member.traits();
                cards.add(Card.of(banner(traits), traits.name(), List.of("Alliee " + EarthNations.a(other.traits()
                                .name()), member.stance() == WarData.Stance.GUERRE ? "En guerre contre toi"
                                : mood(member.hostility()) + " (" + Math.round(member.hostility()) + ")"))
                        .accent(tint(traits))
                        .badge("PACTE")
                        .progress(Math.round(member.hostility()), 100)
                        .tooltip(tooltip));
            }
            boolean fighting = first.stance() == WarData.Stance.GUERRE || second.stance() == WarData.Stance.GUERRE;
            cards.add(Card.of(new ItemStack(Items.IRON_SWORD), fighting ? "Ils attaquent ensemble" : "Le pacte",
                            List.of(fighting ? "Un contingent allie" : "Fin " + WarStrategy.when(first.pact(), now),
                                    fighting ? "a chaque assaut" : "Renouvele si tu fais peur"))
                    .accent(0xFFB3261E)
                    .tooltip(tooltip));
            sections.add(new MenuView.Section("LA COALITION CONTRE TOI", "armor", MenuView.GRID, cards));
        });

        // -- ta contre-offensive (lot 22l-2) : declarer la guerre, d'abord a qui t'a pris quelque chose
        boolean calm = siege == null && conflict.isEmpty() && FactionApi.has(player)
                && !Strongholds.standing(player.getUUID());
        if (calm) {
            List<WarData.Relation> targets = new ArrayList<>();
            for (WarData.Relation relation : data.relations()) {
                if (relation.holds()) {
                    targets.add(relation);
                }
            }
            targets.sort((a, b) -> Long.compare(b.owed() + 5_000L * (b.retaken().size() + b.retakenMines().size()),
                    a.owed() + 5_000L * (a.retaken().size() + a.retakenMines().size())));
            List<WarData.Relation> hostiles = new ArrayList<>();
            for (WarData.Relation relation : data.relations()) {
                if (!relation.holds() && relation.stance() == WarData.Stance.PAIX) {
                    hostiles.add(relation);
                }
            }
            hostiles.sort((a, b) -> Double.compare(b.hostility(), a.hostility()));
            for (WarData.Relation relation : hostiles) {
                if (targets.size() >= 3) {
                    break;
                }
                targets.add(relation);
            }
            List<Card> cards = new ArrayList<>();
            for (WarData.Relation relation : targets.subList(0, Math.min(4, targets.size()))) {
                NationTraits traits = relation.traits();
                String refusal = WarStrategy.counterRefusal(player, data, relation, now);
                List<String> lines = new ArrayList<>();
                if (relation.owed() > 0L) {
                    lines.add("Te doit " + MoneyFormat.exact(relation.owed()) + " $");
                }
                int taken = relation.retaken().size() + relation.retakenMines().size();
                if (taken > 0) {
                    lines.add("T'a repris " + taken + (taken > 1 ? " terres" : " terre"));
                }
                if (lines.isEmpty()) {
                    lines.add("Ne t'a rien pris");
                }
                lines.add(refusal == null ? "Place forte : ~" + MoneyFormat.compact(relation.treasury() * 3L / 10L
                        / 1000L * 1000L) + " $ de butin" : relation.beaten(now) ? "Vaincue, elle se releve"
                        : relation.stance() == WarData.Stance.TREVE ? "En treve" : "Pas maintenant");
                List<String> tooltip = new ArrayList<>();
                tooltip.add("Tu lui declares la guerre : elle batit une place forte");
                tooltip.add("a 1 200 - 2 000 blocs, et viendra attaquer ta base");
                tooltip.add("a l'heure que tu fixes.");
                tooltip.add("Si sa place forte tombe : " + WarStrategy.spoilsLine(player, relation) + ".");
                WarData.Relation partner = WarStrategy.ally(data, relation, now);
                if (partner != null) {
                    tooltip.add(partner.traits().name() + ", " + (partner.traits().plural() ? "ses allies" : "son allie")
                            + ", la rejoindra.");
                }
                if (refusal != null) {
                    tooltip.add(refusal + ".");
                }
                cards.add(Card.of(banner(traits), traits.name(), lines)
                        .accent(refusal == null ? 0xFFB3261E : 0xFF55545C)
                        .badge(refusal == null ? "GUERRE" : "")
                        .action(refusal == null ? "declare:" + relation.nation() : "")
                        .confirm(refusal == null ? "!Declarer la guerre " + EarthNations.a(traits.name()) + " ?|"
                                + "Elle batira une place forte et viendra attaquer ta base. Si sa place forte tombe : "
                                + WarStrategy.spoilsLine(player, relation) + "." : "")
                        .state(refusal == null ? MenuView.NORMAL : MenuView.DISABLED)
                        .tooltip(tooltip));
            }
            if (!cards.isEmpty()) {
                sections.add(new MenuView.Section("TA CONTRE-OFFENSIVE", "armor", MenuView.GRID, cards));
            }
        }

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
                tooltip.add("T'a pris " + MoneyFormat.exact(memory.looted()) + " $ en tout");
            }
            if (relation.holds()) {
                tooltip.add("Te doit " + WarStrategy.owedLine(player, relation));
                tooltip.add("  (rendus si sa place forte tombe)");
            }
            WarData.Relation partner = WarStrategy.ally(data, relation, now);
            if (partner != null) {
                tooltip.add("Alliee " + EarthNations.a(partner.traits().name()) + " contre toi, fin "
                        + WarStrategy.when(relation.pact(), now));
            }
            WarData.Lessons lessons = relation.lessons();
            if (lessons.assaults() > 0) {
                String lesson = lessons.lesson();
                tooltip.add("A appris de " + lessons.assaults() + " assaut(s)" + (lesson.isEmpty() ? ""
                        : " : " + WarStrategy.lessonText(lesson)));
            }
            if (relation.beaten(now)) {
                tooltip.add("Vaincue : se releve, fin " + WarStrategy.when(relation.defeated()
                        + WarStrategy.BEATEN_MS, now));
            }
            int stake = WarStrategy.stake(player, i);
            if (stake > 0) {
                tooltip.add("Reprendrait " + EarthNations.countryName(stake) + " sur un assaut reussi");
            }
            fr.atraxterra.dohy.earth.EarthBodies.Body mineStake =
                    fr.atraxterra.dohy.earth.mine.EarthMines.stake(player, i);
            if (mineStake != null) {
                tooltip.add("Reprendrait la mine de " + mineStake.name() + " sur un assaut reussi");
            }
            tooltip.add("Clic : un cadeau de 1 000 $ (pas en guerre)");
            tooltip.add("/guerre declarer " + traits.slug() + " : lui declarer la guerre");
            nations.add(Card.of(banner(traits), traits.name(), List.of(stance,
                    "Armee " + estimate(relation.army()) + " · tresor " + estimate(relation.treasury())))
                    .accent(relation.stance() == WarData.Stance.PAIX ? moodColor(relation.hostility())
                            : relation.stance() == WarData.Stance.TREVE ? 0xFF2E7D32 : 0xFFB3261E)
                    .badge(partner != null ? "ALLIEE" : relation.holds() ? "TE DOIT" : "")
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
        // Ta puissance (lot 22l-2) : au-dela du seuil, les nations s'allient contre toi.
        List<String> parts = new ArrayList<>(threat.parts());
        parts.add("Au-dela de " + Math.round(WarStrategy.COALITION_AT) + ", les nations hostiles s'allient");
        parts.add("contre toi ; sous " + Math.round(WarStrategy.DISSOLVE_AT) + ", leur coalition se defait.");
        defense.add(Card.of(new ItemStack(Items.NETHER_STAR), "Ta puissance : " + might,
                        List.of(might >= WarStrategy.COALITION_AT ? "Elles s'allient contre toi"
                                : "Coalition au-dela de " + Math.round(WarStrategy.COALITION_AT),
                                "Banque, terres, victoires"))
                .accent(might >= WarStrategy.COALITION_AT ? 0xFFB3261E : 0xFF55545C)
                .progress(might, 100)
                .tooltip(parts));
        // Ce qu'elles ont appris de tes defenses, d'un assaut a l'autre (lot 22l-2).
        WarData.Relation keenest = null;
        List<String> learned = new ArrayList<>();
        for (WarData.Relation relation : data.relations()) {
            WarData.Lessons lessons = relation.lessons();
            if (lessons.assaults() == 0) {
                continue;
            }
            if (keenest == null || lessons.assaults() > keenest.lessons().assaults()) {
                keenest = relation;
            }
            String lesson = lessons.lesson();
            learned.add(relation.traits().name() + " : " + lessons.assaults() + " assaut(s), "
                    + lessons.deaths().size() + " morts relevees" + (lesson.isEmpty() ? ""
                    : ", " + WarStrategy.lessonText(lesson)));
        }
        if (keenest != null) {
            learned.add("Elles evitent les cases ou leurs soldats sont tombes,");
            learned.add("voient davantage de tes pieges a chaque assaut,");
            learned.add("et changent leur troupe contre ce qui les a tuees.");
            learned.add("Deplacer ton coffre efface la carte de leurs morts.");
            String lesson = keenest.lessons().lesson();
            defense.add(Card.of(new ItemStack(Items.WRITABLE_BOOK), "Ce qu'elles ont appris",
                            List.of(keenest.traits().name() + " : " + keenest.lessons().assaults() + " assaut(s)",
                                    lesson.isEmpty() ? "Aucune lecon claire" : "Contre " + WarStrategy.causeLabel(
                                            lesson)))
                    .accent(lesson.isEmpty() ? 0xFF55545C : 0xFFE8590C)
                    .tooltip(learned));
        }
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
                        : r.declared() ? "Ta contre-offensive contre " + r.traits().the() + " : rase sa place forte"
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
        } else if (key.equals("fort")) {
            refusal = Strongholds.compass(player);
            message = "Une boussole pointe vers leur place forte.";
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
        } else if (key.startsWith("declare:")) {
            try {
                refusal = WarStrategy.counter(player, Integer.parseInt(key.substring(8)));
                message = "La guerre est declaree : sa place forte se dresse bientot.";
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

    /** La carte de la mine en jeu face a cette nation, s'il y en a une. */
    private static Optional<Card> mineStakeCard(ServerPlayer player, int nation) {
        fr.atraxterra.dohy.earth.EarthBodies.Body mine = fr.atraxterra.dohy.earth.mine.EarthMines.stake(player, nation);
        if (mine == null) {
            return Optional.empty();
        }
        return Optional.of(Card.of(fr.atraxterra.dohy.earth.mine.EarthMines.icon(mine), mine.name(),
                        List.of("Mine en jeu", "Reprise s'ils gagnent"))
                .accent(0xFFE8590C)
                .tooltip(List.of("Si leur tresorier rentre au camp avec ta banque,",
                        "la nation reprend cette mine et ses revenus.",
                        "C'est la derniere que tu as capturee dans sa region.")));
    }

    /** La carte du pays en jeu face a cette nation, s'il y en a un. */
    private static Optional<Card> stakeCard(ServerPlayer player, int nation) {
        int country = WarStrategy.stake(player, nation);
        if (country <= 0) {
            return Optional.empty();
        }
        return Optional.of(Card.of(new ItemStack(Items.FILLED_MAP), EarthNations.countryName(country),
                        List.of("En jeu", "Repris s'ils gagnent"))
                .accent(0xFFE8590C)
                .tooltip(List.of("Si leur tresorier rentre au camp avec ta banque,",
                        "la nation reprend ce pays, et le claim qu'il donne.",
                        "C'est le dernier pays que tu lui as pris ;",
                        "celui de ta capitale n'est jamais en jeu.")));
    }
}

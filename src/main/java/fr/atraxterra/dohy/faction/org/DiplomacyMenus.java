package fr.atraxterra.dohy.faction.org;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionMenus;
import fr.atraxterra.dohy.faction.Nation;
import fr.atraxterra.dohy.faction.NationRank;
import fr.atraxterra.dohy.faction.war.NationTraits;
import fr.atraxterra.dohy.faction.war.WarData;
import fr.atraxterra.dohy.faction.war.WarMenu;
import fr.atraxterra.dohy.faction.war.WarStrategy;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L'onglet Diplomatie de la nation (lot 24a) : tes traites, les douze nations et ce que chacune
 * pense de toi, la guerre et le pillage. Une nation s'ouvre sur sa fiche : les trois traites
 * qu'elle signerait ou non, la rivalite, son caractere.
 */
public final class DiplomacyMenus {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM")
            .withZone(ZoneId.of("Europe/Paris"));

    private DiplomacyMenus() {
    }

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

    static ItemStack banner(NationTraits traits) {
        return new ItemStack(Items.BANNER.pick(traits.color()));
    }

    static String stance(WarData.Relation relation) {
        return switch (relation.stance()) {
            case PAIX -> "En paix";
            case MENACE -> "Ultimatum";
            case GUERRE -> "En guerre";
            case TREVE -> "En treve";
        };
    }

    /** La pastille d'une nation : son traite, sa rivalite, sa guerre. */
    static String badge(ServerPlayer player, int nation, WarData.Relation relation) {
        if (relation.stance() == WarData.Stance.GUERRE) {
            return "GUERRE";
        }
        if (relation.stance() == WarData.Stance.MENACE) {
            return "ULTIMATUM";
        }
        Treaty treaty = Diplomacy.treaty(player, nation);
        if (treaty != null) {
            return switch (treaty.kind()) {
                case PACTE -> "PACTE";
                case ACCORD -> "ACCORD";
                case ALLIANCE -> "ALLIEE";
            };
        }
        return Diplomacy.rival(player, nation) ? "RIVALE" : "";
    }

    // -- La diplomatie -----------------------------------------------------------------------------

    public static final class Hub extends Page {
        public static final Hub INSTANCE = new Hub();

        private Hub() {
            super("faction_diplomacy");
        }

        @Override
        public MenuView view(ServerPlayer player) {
            Nation nation = Nation.of(player);
            List<Treaty> treaties = Diplomacy.treaties(player);
            List<Card> own = new ArrayList<>();
            for (Treaty treaty : treaties) {
                NationTraits traits = NationTraits.of(treaty.nation());
                Treaty.Type type = treaty.kind();
                long price = Diplomacy.price(player, treaty.nation(), type);
                own.add(Card.of(type.icon(), type.label(), List.of("Avec " + traits.the(), "Jusqu'au "
                                + DAY.format(Instant.ofEpochMilli(treaty.until()))))
                        .accent(type.color()).action("nation:" + treaty.nation() + "|ouvrir sa fiche")
                        .tooltip(List.of(type.effect(), "Renouveler : " + MoneyFormat.exact(price) + " $")));
            }
            if (own.isEmpty()) {
                own.add(Card.of(new ItemStack(Items.WRITABLE_BOOK), "Aucun traite", List.of(
                        "Ouvre une nation pour en signer", Diplomacy.slots(player) + " traites au plus"))
                        .state(MenuView.DISABLED));
            }

            List<Card> nations = new ArrayList<>();
            for (int index = 0; index < NationTraits.count(); index++) {
                NationTraits traits = NationTraits.of(index);
                WarData.Relation relation = WarStrategy.relationOf(player, index);
                Treaty treaty = Diplomacy.treaty(player, index);
                String second = treaty != null ? treaty.kind().label()
                        : Diplomacy.rival(player, index) ? "Rivale declaree"
                        : Diplomacy.refusal(player, index, Treaty.Type.PACTE) == null ? "Un pacte est possible"
                        : "Aucun traite";
                nations.add(Card.of(banner(traits), traits.name(), List.of(stance(relation) + " · "
                                + WarMenu.mood(relation.hostility()) + " (" + Math.round(relation.hostility()) + ")",
                                second))
                        .accent(WarMenu.moodColor(relation.hostility())).progress(Math.round(relation.hostility()), 100L)
                        .badge(badge(player, index, relation)).action("nation:" + index + "|ouvrir sa fiche"));
            }

            List<Card> war = new ArrayList<>();
            war.add(Card.of(new ItemStack(Items.IRON_SWORD), "La guerre des nations", List.of(
                            nation.besieged() ? "Assaut en cours" : nation.conflict().isPresent() ? "Un conflit en cours"
                                    : "En paix", nation.hostiles() + " nation(s) hostile(s)"))
                    .accent(nation.besieged() || nation.conflict().isPresent() ? 0xFFEF3826 : 0xFF55545C)
                    .badge(FactionMenus.warBadge(nation)).action("war|ouvrir"));
            war.add(FactionMenus.enemyBaseCard(player));

            long rivals = NationTraits.count() == 0 ? 0 : Organisations.of(player).rivals().size();
            List<MenuView.Stat> stats = List.of(
                    new MenuView.Stat("TRAITES", treaties.size() + " / " + Diplomacy.slots(player), 0xFF27C4E8),
                    new MenuView.Stat("RIVALES", String.valueOf(rivals), 0xFFEF3826),
                    new MenuView.Stat("TAXE", Math.round(NationRank.fee(player) * 100.0d) + " %", 0xFFFAC400));
            return new MenuView(id(), "Diplomatie", "Traites et rivalites avec les douze nations", stats, List.of(
                    new MenuView.Section("TES TRAITES", "craft", MenuView.GRID, own),
                    new MenuView.Section("LES NATIONS", "resources", MenuView.GRID, nations),
                    new MenuView.Section("GUERRE ET PILLAGE", "armor", MenuView.GRID, war)), true)
                    .withTabs(FactionMenus.tabs(player, FactionMenus.DIPLOMACY));
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            if (key.startsWith("nation:")) {
                try {
                    NationPage.INSTANCE.show(player, Integer.parseInt(key.substring(7)));
                } catch (NumberFormatException ignored) {
                    // carte d'une autre version
                }
            } else {
                FactionMenus.opened(player, key);
            }
        }
    }

    // -- La fiche d'une nation --------------------------------------------------------------------

    public static final class NationPage extends Page {
        public static final NationPage INSTANCE = new NationPage();
        private final Map<UUID, Integer> selected = new ConcurrentHashMap<>();

        private NationPage() {
            super("faction_diplomacy_nation");
        }

        void show(ServerPlayer player, int nation) {
            selected.put(player.getUUID(), Math.floorMod(nation, NationTraits.count()));
            open(player);
        }

        private int nation(ServerPlayer player) {
            return selected.getOrDefault(player.getUUID(), 0);
        }

        @Override
        protected void back(ServerPlayer player) {
            Hub.INSTANCE.open(player);
        }

        @Override
        public MenuView view(ServerPlayer player) {
            int index = nation(player);
            NationTraits traits = NationTraits.of(index);
            WarData.Relation relation = WarStrategy.relationOf(player, index);
            Treaty current = Diplomacy.treaty(player, index);

            List<Card> treaties = new ArrayList<>();
            for (Treaty.Type type : Treaty.Type.values()) {
                String refusal = Diplomacy.refusal(player, index, type);
                long price = Diplomacy.price(player, index, type);
                boolean held = current != null && current.kind() == type;
                List<String> lines = new ArrayList<>();
                lines.add(MoneyFormat.exact(price) + " $ · " + type.days() + " jours");
                lines.add(refusal != null ? refusal : held ? "En cours, jusqu'au " + DAY.format(Instant.ofEpochMilli(
                        current.until())) : "Elle signerait");
                Card card = Card.of(type.icon(), type.label(), lines).accent(type.color())
                        .tooltip(List.of(type.effect(), "Elle refuse au-dessus de " + Math.round(Diplomacy.threshold(
                                player, index, type)) + " d'hostilite"));
                if (refusal == null) {
                    card = card.action("sign:" + type.name() + "|" + (held ? "renouveler" : "signer"))
                            .confirm((held ? "Renouveler " : "Signer ") + type.label().toLowerCase() + " avec "
                                    + traits.the() + " ?|" + MoneyFormat.exact(price) + " $ depuis la banque nationale");
                    if (held) {
                        card = card.badge("EN COURS");
                    }
                } else {
                    card = card.state(held ? MenuView.DONE : MenuView.DISABLED);
                }
                treaties.add(card);
            }
            if (current != null) {
                boolean betrayal = current.kind() != Treaty.Type.ACCORD;
                treaties.add(Card.of(new ItemStack(Items.BARRIER), "Rompre", List.of(current.kind().label(),
                                betrayal ? "Une trahison, qu'elle n'oubliera pas" : "Sans suite"))
                        .accent(0xFFEF3826).action("break|rompre").confirm("!Rompre " + current.kind().label()
                                .toLowerCase() + " avec " + traits.the() + " ?|" + (betrayal
                                ? "Elle t'en gardera rancune" : "Les avantages s'arretent")));
            }

            boolean rival = Diplomacy.rival(player, index);
            List<Card> rivalry = List.of(Card.of(rival ? new ItemStack(Items.BANNER.pick(net.minecraft.world.item.DyeColor.WHITE)) : new ItemStack(Items.CROSSBOW),
                            rival ? "Renoncer a la rivalite" : "Declarer la rivalite", rival
                                    ? List.of("Elle reste rivale tant que tu ne renonces pas", "Clic : y renoncer")
                                    : List.of("Ses pays : +25 % de points de conquete", "Elle s'en fache, le traite tombe"))
                    .accent(0xFFEF3826).action("rival|" + (rival ? "renoncer" : "declarer"))
                    .confirm(rival ? "Renoncer a la rivalite avec " + traits.the() + " ?"
                            : "!Declarer " + traits.the() + " rivale ?|Son hostilite monte, et un traite tombe"));

            List<Card> character = List.of(
                    trait(Items.TNT, "Agressivite", traits.aggression()),
                    trait(Items.GOLD_NUGGET, "Avidite", traits.greed()),
                    trait(Items.SHIELD, "Prudence", traits.caution()),
                    trait(Items.WITHER_ROSE, "Rancune", traits.grudge()),
                    trait(Items.BELL, "Honneur", traits.honor()),
                    trait(Items.ENDER_EYE, "Ruse", traits.cunning()));

            List<MenuView.Stat> stats = List.of(
                    new MenuView.Stat("HUMEUR", Math.round(relation.hostility()) + " · " + WarMenu.mood(
                            relation.hostility()).toUpperCase(), WarMenu.moodColor(relation.hostility())),
                    new MenuView.Stat("POSTURE", stance(relation).toUpperCase(), 0xFFFFFFFF),
                    new MenuView.Stat("TRAITE", current == null ? "AUCUN" : current.kind().label().toUpperCase(),
                            current == null ? 0xFF9AA0A6 : current.kind().color()));
            return new MenuView(id(), traits.name(), "Armee " + traits.doctrine().label() + ", voix "
                    + traits.voice(), stats, List.of(
                    new MenuView.Section("TRAITES", "craft", MenuView.GRID, treaties),
                    new MenuView.Section("RIVALITE", "armor", MenuView.GRID, rivalry),
                    new MenuView.Section("CARACTERE", "resources", MenuView.GRID, character)), true)
                    .withTabs(FactionMenus.tabs(player, FactionMenus.DIPLOMACY));
        }

        private static Card trait(net.minecraft.world.item.Item icon, String label, double value) {
            int percent = (int) Math.round(value * 100.0d);
            return Card.of(new ItemStack(icon), label, List.of(percent + " / 100")).progress(percent, 100L)
                    .accent(0xFF55545C);
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            int index = nation(player);
            if (key.startsWith("sign:")) {
                Treaty.Type type = Treaty.Type.of(key.substring(5));
                if (type != null) {
                    OrgMenus.report(player, Diplomacy.propose(player, index, type), type.label() + " signe avec "
                            + NationTraits.of(index).the() + ".");
                }
            } else if (key.equals("break")) {
                OrgMenus.report(player, Diplomacy.breakTreaty(player, index), "");
            } else if (key.equals("rival")) {
                OrgMenus.report(player, Diplomacy.toggleRival(player, index), "");
            }
        }
    }
}

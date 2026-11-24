package fr.atraxterra.dohy.faction.garrison;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionMenus;
import fr.atraxterra.dohy.faction.war.Sieges;
import fr.atraxterra.dohy.faction.war.WarMenu;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * L'ecran de la garnison ({@code /garnison}, la caserne, ou la carte de l'ecran de la guerre) : tes
 * soldats et leur etat, le recrutement, les ordres, la caserne et l'equipement.
 */
public final class GarrisonMenu extends MenuController {

    public static final GarrisonMenu INSTANCE = new GarrisonMenu();

    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFF55545C;
    private static final int GREEN = 0xFF22E558;
    private static final int RED = 0xFFEF3826;
    private static final int GOLD = 0xFFFAC400;
    private static final int STEEL = 0xFF8FA3B8;

    private GarrisonMenu() {
        super("garrison");
    }

    @Override
    protected void back(ServerPlayer player) {
        WarMenu.INSTANCE.open(player);
    }

    private static int color(GarrisonRole role) {
        return switch (role) {
            case FANTASSIN -> STEEL;
            case ARCHER -> 0xFF6FBF4A;
            case MEDECIN -> 0xFFF5F5F5;
            case CAPITAINE -> GOLD;
        };
    }

    private static ItemStack icon(ServerPlayer player, Garrison garrison, GarrisonRole role) {
        return switch (role) {
            case FANTASSIN -> new ItemStack(garrison.tier().piece("sword"));
            case ARCHER -> new ItemStack(Items.BOW);
            case MEDECIN -> new ItemStack(Items.GLISTERING_MELON_SLICE);
            case CAPITAINE -> FactionMenus.banner(Garrisons.color(player).getId());
        };
    }

    private static ItemStack icon(Garrison.Order order) {
        Item item = switch (order) {
            case GARDE -> Items.SHIELD;
            case SUIVRE -> Items.LEATHER_BOOTS;
            case REPOS -> Items.CAMPFIRE;
            case CONGE -> Items.CLOCK;
        };
        return new ItemStack(item);
    }

    private static String orderHelp(Garrison.Order order) {
        return switch (order) {
            case GARDE -> "Autour de la caserne. En assaut : au coffre.";
            case SUIVRE -> "Derriere toi, partout sauf au spawn.";
            case REPOS -> "A la caserne, sans combattre.";
            case CONGE -> "Personne en jeu.";
        };
    }

    private static String where(Optional<GlobalPos> barracks) {
        return barracks.map(at -> at.pos().getX() + " " + at.pos().getY() + " " + at.pos().getZ()).orElse("");
    }

    // -- Les cartes -------------------------------------------------------------------------------------

    private static Card member(ServerPlayer player, Garrison garrison, Garrison.Member member) {
        GarrisonRole role = member.role();
        float max = Garrisons.maxHealth(garrison, member);
        GarrisonSoldier live = Garrisons.deployed(player.getUUID(), member.id());
        float health = live != null ? live.getHealth() : member.health() < 0.0f ? max : member.health();
        int shown = (int) Math.ceil(health);
        String status = live != null ? "En jeu" : garrison.order() == Garrison.Order.CONGE ? "En conge" : "A la caserne";
        List<String> lines = List.of("Vie : " + shown + " / " + (int) Math.ceil(max), status + " · "
                + member.kills() + " abattu(s)");
        List<String> tooltip = List.of("Solde : " + MoneyFormat.exact(role.pay()) + " $ / jour",
                "Clic droit : le faire venir");
        return Card.of(icon(player, garrison, role), member.title(), lines)
                .badge(role == GarrisonRole.CAPITAINE ? "CHEF" : "")
                .accent(health < max * 0.35f ? RED : color(role))
                .progress((long) (health * 10.0f), (long) (max * 10.0f))
                .action("dismiss:" + member.id() + "|renvoyer")
                .confirm("!Renvoyer " + member.title() + " ?|Il quitte la garnison, sans remboursement.")
                .right("call:" + member.id() + "|faire venir")
                .tooltip(tooltip);
    }

    private static Card recruit(ServerPlayer player, Garrison garrison, GarrisonRole role) {
        String refusal = Garrisons.recruitRefusal(player, role);
        double health = role.health() * garrison.tier().health();
        double damage = role.damage() + garrison.tier().damage();
        List<String> tooltip = new ArrayList<>();
        tooltip.add(role.description());
        tooltip.add("Vie " + (int) Math.round(health) + " · Degats " + (int) Math.round(damage));
        if (refusal != null) {
            tooltip.add(refusal);
        }
        Card card = Card.of(icon(player, garrison, role), role.label(), List.of(MoneyFormat.exact(role.cost()) + " $",
                        "Solde : " + MoneyFormat.exact(role.pay()) + " $ / jour"))
                .accent(color(role)).tooltip(tooltip);
        if (role.max() > 0) {
            card = card.badge(garrison.count(role) + "/" + role.max());
        }
        if (refusal != null) {
            return card.state(MenuView.DISABLED);
        }
        return card.action("recruit:" + role.id() + "|recruter")
                .confirm("Recruter un " + role.label().toLowerCase(Locale.ROOT) + " ?|" + MoneyFormat.exact(role.cost())
                        + " $");
    }

    private static Card order(ServerPlayer player, Garrison garrison, Garrison.Order order) {
        boolean current = garrison.order() == order;
        String refusal = Garrisons.orderRefusal(player, order);
        Card card = Card.of(icon(order), order.label(), List.of(current ? "En cours" : "/garnison "
                + order.id(), ""))
                .accent(current ? GOLD : GRAY).tooltip(List.of(orderHelp(order)));
        if (current) {
            return card.state(MenuView.DONE);
        }
        if (refusal != null) {
            return card.state(MenuView.DISABLED).tooltip(List.of(orderHelp(order), refusal));
        }
        return card.action("order:" + order.id() + "|donner l'ordre");
    }

    private static Card level(ServerPlayer player, Garrison garrison) {
        int level = garrison.level();
        boolean top = level >= Garrison.CAPACITY.length;
        String refusal = Garrisons.upgradeLevelRefusal(player);
        Card card = Card.of(new ItemStack(Items.STONE_BRICKS), "Caserne : niveau " + level,
                        List.of(garrison.capacity() + " soldats", top ? "Le plus haut niveau"
                                : "Niveau " + (level + 1) + " : " + MoneyFormat.exact(Garrison.LEVEL_COST[level]) + " $"))
                .badge(level + "/" + Garrison.CAPACITY.length).progress(level, Garrison.CAPACITY.length);
        if (top) {
            return card.accent(GREEN).state(MenuView.DONE);
        }
        if (refusal != null) {
            return card.accent(GRAY).state(MenuView.DISABLED).tooltip(List.of(refusal));
        }
        return card.accent(GOLD).action("level|agrandir")
                .confirm("Agrandir la caserne ?|" + MoneyFormat.exact(Garrison.LEVEL_COST[level]) + " $ de la banque");
    }

    private static Card tier(ServerPlayer player, Garrison garrison) {
        GarrisonTier tier = garrison.tier();
        GarrisonTier next = tier.next();
        String refusal = Garrisons.upgradeTierRefusal(player);
        Card card = Card.of(new ItemStack(tier.piece("chestplate")), "Equipement : " + tier.label(),
                        List.of("Vie x" + format(tier.health()) + ", +" + (int) tier.damage() + " degats",
                                next == null ? "Le meilleur" : next.label() + " : " + MoneyFormat.exact(next.cost())
                                        + " $"))
                .badge((tier.ordinal() + 1) + "/" + GarrisonTier.values().length)
                .progress(tier.ordinal() + 1, GarrisonTier.values().length);
        if (next == null) {
            return card.accent(GREEN).state(MenuView.DONE);
        }
        List<String> tooltip = new ArrayList<>(List.of(next.label() + " : vie x" + format(next.health()) + ", +"
                + (int) next.damage() + " degats"));
        if (refusal != null) {
            tooltip.add(refusal);
            return card.accent(GRAY).state(MenuView.DISABLED).tooltip(tooltip);
        }
        return card.accent(GOLD).tooltip(tooltip).action("tier|equiper")
                .confirm("Equiper la garnison en " + next.label().toLowerCase(Locale.ROOT) + " ?|"
                        + MoneyFormat.exact(next.cost()) + " $ de la banque");
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((int) value) : String.valueOf(value);
    }

    // -- La vue -----------------------------------------------------------------------------------------

    @Override
    public MenuView view(ServerPlayer player) {
        Garrison garrison = Garrisons.of(player);
        boolean nation = FactionApi.has(player);
        boolean besieged = Sieges.besieged(player.getUUID());
        int kills = 0;
        for (Garrison.Member member : garrison.members()) {
            kills += member.kills();
        }
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("SOLDATS", garrison.members().size() + " / " + garrison.capacity(),
                        garrison.members().isEmpty() ? WHITE : GREEN),
                new MenuView.Stat("SOLDE", MoneyFormat.exact(garrison.dailyPay()) + " $ / jour",
                        garrison.unpaid() > 0 ? RED : WHITE),
                new MenuView.Stat("ORDRE", garrison.order().id().toUpperCase(Locale.ROOT), GOLD),
                new MenuView.Stat("BANQUE", nation ? MoneyFormat.exact(FactionApi.data(player).bank()) + " $" : "-",
                        WHITE));

        List<MenuView.Section> sections = new ArrayList<>();
        List<Card> soldiers = new ArrayList<>();
        for (Garrison.Member member : garrison.members()) {
            soldiers.add(member(player, garrison, member));
        }
        if (soldiers.isEmpty()) {
            soldiers.add(Card.of(new ItemStack(Items.ARMOR_STAND), "Aucun soldat", List.of("Recrute ci-dessous", ""))
                    .state(MenuView.DISABLED));
        }
        sections.add(new MenuView.Section(kills > 0 ? "TES SOLDATS · " + kills + " ABATTUS" : "TES SOLDATS",
                "armor", MenuView.GRID, soldiers));

        List<Card> recruits = new ArrayList<>();
        for (GarrisonRole role : GarrisonRole.values()) {
            recruits.add(recruit(player, garrison, role));
        }
        sections.add(new MenuView.Section("RECRUTER", "craft", MenuView.GRID, recruits));

        List<Card> orders = new ArrayList<>();
        for (Garrison.Order order : Garrison.Order.values()) {
            orders.add(order(player, garrison, order));
        }
        sections.add(new MenuView.Section("LES ORDRES", "armor", MenuView.GRID, orders));

        List<Card> upgrades = new ArrayList<>();
        upgrades.add(level(player, garrison));
        upgrades.add(tier(player, garrison));
        Optional<GlobalPos> barracks = garrison.barracks();
        upgrades.add(barracks.isPresent()
                ? Card.of(new ItemStack(DoHyGarrison.BARRACKS_ITEM.get()), "La caserne", List.of(where(barracks), ""))
                        .accent(STEEL)
                : Card.of(new ItemStack(DoHyGarrison.BARRACKS_ITEM.get()), "Pas de caserne", List.of(
                        "A poser dans tes claims", "")).accent(RED).state(MenuView.DISABLED));
        sections.add(new MenuView.Section("LA CASERNE", "upgrade", MenuView.GRID, upgrades));

        String subtitle = !nation ? "Il faut une nation (/f)"
                : barracks.isEmpty() ? "Pose une caserne dans tes claims"
                : besieged && garrison.order() == Garrison.Order.GARDE ? "Assaut : la garnison tient ton coffre"
                : "Caserne en " + where(barracks) + " · " + garrison.order().label().toLowerCase(Locale.ROOT);
        MenuView view = new MenuView(id(), "La garnison", subtitle, stats, sections, true);
        return nation ? view.withTabs(FactionMenus.tabs(player, FactionMenus.DIPLOMACY)) : view;
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        String refusal;
        if (key.startsWith("recruit:")) {
            refusal = Garrisons.recruit(player, GarrisonRole.byId(key.substring(8)));
            if (refusal == null) {
                success(player);
                return;
            }
        } else if (key.startsWith("dismiss:") || key.startsWith("call:")) {
            UUID id;
            try {
                id = UUID.fromString(key.substring(key.indexOf(':') + 1));
            } catch (IllegalArgumentException wrong) {
                return;
            }
            Garrison garrison = Garrisons.of(player);
            Garrison.Member member = garrison.member(id);
            if (member == null) {
                return;
            }
            if (key.startsWith("call:")) {
                report(player, Garrisons.call(player, id), member.title() + " arrive.");
                return;
            }
            Garrisons.dismiss(player, garrison, member);
            report(player, null, member.title() + " quitte la garnison.");
            return;
        } else if (key.startsWith("order:")) {
            Garrisons.order(player, Garrison.Order.byId(key.substring(6)));
            success(player);
            return;
        } else if (key.equals("level")) {
            refusal = Garrisons.upgradeLevel(player);
            if (refusal == null) {
                success(player);
                return;
            }
        } else if (key.equals("tier")) {
            refusal = Garrisons.upgradeTier(player);
            if (refusal == null) {
                success(player);
                return;
            }
        } else {
            return;
        }
        report(player, refusal, "");
    }
}

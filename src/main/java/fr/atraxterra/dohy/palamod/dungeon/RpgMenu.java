package fr.atraxterra.dohy.palamod.dungeon;

import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Le profil RPG : niveau, points, statistiques, et l'arbre — une branche par
 * statistique. Un clic reserve le noeud suivant d'une branche, un clic sur le
 * dernier reserve le rend ; « Valider » achete, « Annuler » rend tout.
 */
public final class RpgMenu extends MenuController {

    public static final long RESPEC_PRICE = 25_000L;
    public static final RpgMenu INSTANCE = new RpgMenu();

    /** Les reserves en cours, et la reinitialisation armee, par joueur. */
    private static final class State {
        final Map<RpgStat, Integer> pending = new EnumMap<>(RpgStat.class);
        boolean respecArmed;

        State() {
            for (RpgStat stat : RpgStat.values()) {
                pending.put(stat, 0);
            }
        }
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    private RpgMenu() {
        super("rpg");
    }

    @Override
    public void open(ServerPlayer player) {
        states.put(player.getUUID(), new State());
        super.open(player);
    }

    private State state(ServerPlayer player) {
        return states.computeIfAbsent(player.getUUID(), ignored -> new State());
    }

    private static int pendingCost(RpgData data, State state) {
        int total = 0;
        for (RpgStat stat : RpgStat.values()) {
            int owned = data.nodes(stat);
            total += RpgStat.costOf(owned + state.pending.get(stat)) - RpgStat.costOf(owned);
        }
        return total;
    }

    private static int color(ChatFormatting format) {
        Integer value = format.getColor();
        return value == null ? 0xFF55545C : 0xFF000000 | value;
    }

    @Override
    public MenuView view(ServerPlayer player) {
        State state = state(player);
        RpgData data = Rpg.data(player);
        Rpg.Points points = Rpg.points(player);
        int cost = pendingCost(data, state);
        int free = points.total() - data.spent() - cost;
        int level = Rpg.level(data.xp());

        List<Card> profile = new ArrayList<>();
        Card levelCard = Card.of(new ItemStack(Items.EXPERIENCE_BOTTLE), "Niveau RPG " + level, List.of(
                        level >= Rpg.MAX_LEVEL ? "Niveau maximum" : MoneyFormat.exact(Rpg.xpInLevel(data.xp())) + " / "
                                + MoneyFormat.exact(Rpg.xpToNext(level)) + " XP",
                        "Vient des salles terminees"))
                .accent(0xFF2F5BEA);
        profile.add(level >= Rpg.MAX_LEVEL ? levelCard.state(MenuView.DONE)
                : levelCard.progress(Rpg.xpInLevel(data.xp()), Rpg.xpToNext(level)));
        profile.add(Card.of(new ItemStack(Items.NETHER_STAR), free + " point(s) libre(s)", List.of(
                        "Total " + points.total() + " · depenses " + data.spent(),
                        cost > 0 ? cost + " reserve(s)" : "Aucune reserve"))
                .accent(0xFFFAC400).badge(String.valueOf(free))
                .tooltip(List.of("Niveau RPG : " + points.level() + " / 100",
                        "Paliers de donjon : " + points.paliers() + " / 80 (1 tous les 5)",
                        "Metiers : " + points.jobs() + " / 80 (1 tous les 5 niveaux)")));
        profile.add(statsCard(player, "Statistiques perso", Rpg.tree(data), null));
        profile.add(statsCard(player, "Statistiques globales", Rpg.tree(data), Rpg.runes(player)));

        List<MenuView.Section> sections = new ArrayList<>();
        sections.add(new MenuView.Section("PROFIL", "resources", MenuView.GRID, profile));
        for (RpgStat stat : RpgStat.values()) {
            int owned = data.nodes(stat);
            int reserved = state.pending.get(stat);
            List<Card> nodes = new ArrayList<>();
            for (int index = 0; index < RpgStat.NODES; index++) {
                int nodeCost = RpgStat.NODE_COSTS[index];
                List<String> lines = List.of("+" + RpgStat.percent(stat.perNode()) + " " + stat.label().toLowerCase(),
                        "Cout : " + nodeCost + " point(s)");
                Card card;
                if (index < owned) {
                    card = Card.of(new ItemStack(Items.STAINED_GLASS_PANE.pick(DyeColor.LIME)), stat.label() + " "
                            + (index + 1), lines).state(MenuView.DONE);
                } else if (index < owned + reserved) {
                    card = Card.of(new ItemStack(Items.STAINED_GLASS_PANE.pick(DyeColor.YELLOW)), stat.label() + " "
                            + (index + 1), lines).accent(0xFFFAC400)
                            .tooltip(List.of("Reserve : Valider pour l'acheter"));
                    if (index == owned + reserved - 1) {
                        card = card.action("release:" + stat.name() + "|rendre");
                    }
                } else if (index == owned + reserved) {
                    boolean affordable = nodeCost <= free;
                    card = Card.of(new ItemStack(Items.STAINED_GLASS_PANE.pick(affordable ? DyeColor.LIGHT_BLUE
                            : DyeColor.ORANGE)), stat.label() + " " + (index + 1), lines);
                    card = affordable ? card.accent(0xFF55CCFF).action("reserve:" + stat.name() + "|reserver")
                            : card.accent(0xFFF0961E).tooltip(List.of("Pas assez de points"));
                } else {
                    card = Card.of(new ItemStack(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY)), stat.label() + " "
                            + (index + 1), lines).state(MenuView.DISABLED)
                            .tooltip(List.of("Achetez d'abord les noeuds precedents"));
                }
                nodes.add(card.badge(String.valueOf(index + 1)));
            }
            String title = stat.label().toUpperCase() + "  ·  " + owned + "/" + RpgStat.NODES + "  ·  "
                    + RpgStat.percent(stat.perNode() * owned);
            sections.add(new MenuView.Section(title, "upgrade", MenuView.TILES, nodes));
        }

        List<Card> actions = List.of(
                Card.of(new ItemStack(Items.STAINED_GLASS_PANE.pick(DyeColor.LIME)), "Valider", List.of(
                                cost > 0 ? "Cout : " + cost + " point(s)" : "Rien a valider", "Achete les reserves"))
                        .accent(0xFF22E558).action(cost > 0 ? "validate|acheter les reserves" : "")
                        .state(cost > 0 ? MenuView.NORMAL : MenuView.DISABLED),
                Card.of(new ItemStack(Items.STAINED_GLASS_PANE.pick(DyeColor.RED)), "Annuler", List.of(
                        "Rend les reserves", "Rien n'est perdu")).accent(0xFFEF3826).action("cancel|annuler"),
                Card.of(new ItemStack(Items.GRINDSTONE), state.respecArmed ? "Confirmer ?" : "Reinitialiser",
                                List.of(MoneyFormat.exact(RESPEC_PRICE) + " $", "Rend tous les points"))
                        .accent(state.respecArmed ? 0xFFEF3826 : 0xFF8C8B93)
                        .action(state.respecArmed ? "respec|confirmer" : "respec|reinitialiser"),
                Card.of(new ItemStack(Items.PAPER), "Comment ca marche", List.of("Clic : reserver le suivant",
                        "Clic sur le dernier : le rendre"))
                        .tooltip(List.of("Valider achete ; Annuler rend les reserves.",
                                "Les statistiques RPG ne jouent que dans les donjons,", "contre leurs monstres.")));
        sections.add(new MenuView.Section("ACTIONS", "craft", MenuView.GRID, actions));

        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("NIVEAU", String.valueOf(level), 0xFF55CCFF),
                new MenuView.Stat("POINTS LIBRES", String.valueOf(free), 0xFFFAC400));
        return new MenuView(id(), "Profil RPG", "Arbre de competences des donjons", stats, sections, true);
    }

    private static Card statsCard(ServerPlayer player, String title, Map<RpgStat, Float> tree,
                                  Map<RpgStat, Float> runes) {
        List<String> lines = new ArrayList<>();
        List<String> tooltip = new ArrayList<>();
        for (RpgStat stat : RpgStat.values()) {
            float value = tree.get(stat) + (runes == null ? 0.0f : runes.get(stat));
            String line = stat.label() + " : " + RpgStat.percent(Math.min(stat.cap(), value));
            if (runes != null && runes.get(stat) > 0.0f) {
                line += "  (runes " + RpgStat.percent(runes.get(stat)) + ")";
            }
            tooltip.add(line);
        }
        lines.add(tooltip.get(0));
        lines.add(tooltip.get(1));
        if (runes == null) {
            tooltip.add("L'arbre seul.");
        } else {
            float reduction = 0.0f;
            for (Dungeon dungeon : Dungeon.values()) {
                reduction = Math.max(reduction, DungeonGearItem.reduction(player, dungeon));
            }
            tooltip.add("Armure de donjon : -" + RpgStat.percent(reduction) + " de degats");
            if (player.getMainHandItem().getItem() instanceof DungeonGearItem sword && !sword.isArmor()) {
                tooltip.add("Epee de donjon : +" + RpgStat.percent(sword.tier().swordBonus()));
            }
            tooltip.add("Plafonds : attaque 100 %, esquive 35 %, critique 60 %, defense 50 %.");
        }
        return Card.of(new ItemStack(runes == null ? Items.BOOK : Items.ENCHANTED_BOOK), title, lines)
                .accent(color(RpgStat.values()[0].color())).tooltip(tooltip);
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        State state = state(player);
        if (!key.equals("respec")) {
            state.respecArmed = false;
        }
        RpgData data = Rpg.data(player);
        switch (key) {
            case "cancel" -> state.pending.replaceAll((stat, count) -> 0);
            case "validate" -> {
                int cost = pendingCost(data, state);
                if (cost <= 0 || cost > Rpg.points(player).total() - data.spent()) {
                    state.pending.replaceAll((stat, count) -> 0);
                    failure(player);
                    return;
                }
                Map<RpgStat, Integer> next = new EnumMap<>(RpgStat.class);
                for (RpgStat stat : RpgStat.values()) {
                    next.put(stat, data.nodes(stat) + state.pending.get(stat));
                }
                Rpg.store(player, data.withNodes(next));
                state.pending.replaceAll((stat, count) -> 0);
                success(player);
                player.sendSystemMessage(Component.translatable("message.palamod.rpg.validated", cost)
                        .withStyle(ChatFormatting.GREEN));
            }
            case "respec" -> {
                if (data.spent() == 0) {
                    failure(player);
                    return;
                }
                if (!state.respecArmed) {
                    state.respecArmed = true;
                    return;
                }
                state.respecArmed = false;
                if (!Economy.tryDebit(player, RESPEC_PRICE)) {
                    failure(player);
                    player.sendSystemMessage(Component.translatable("message.palamod.rpg.respec_poor")
                            .withStyle(ChatFormatting.RED));
                    return;
                }
                Rpg.store(player, data.withNodes(Map.of()));
                state.pending.replaceAll((stat, count) -> 0);
                success(player);
                player.sendSystemMessage(Component.translatable("message.palamod.rpg.respec")
                        .withStyle(ChatFormatting.GOLD));
            }
            default -> {
                boolean reserve = key.startsWith("reserve:");
                if (!reserve && !key.startsWith("release:")) {
                    return;
                }
                RpgStat stat;
                try {
                    stat = RpgStat.valueOf(key.substring(8));
                } catch (IllegalArgumentException unknown) {
                    return;
                }
                int owned = data.nodes(stat);
                int reserved = state.pending.get(stat);
                int free = Rpg.points(player).total() - data.spent() - pendingCost(data, state);
                int next = owned + reserved;
                if (reserve && next < RpgStat.NODES && RpgStat.NODE_COSTS[next] <= free) {
                    state.pending.put(stat, reserved + 1);
                } else if (!reserve && reserved > 0) {
                    state.pending.put(stat, reserved - 1);
                } else {
                    failure(player);
                }
            }
        }
    }
}

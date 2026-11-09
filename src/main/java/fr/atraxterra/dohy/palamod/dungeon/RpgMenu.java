package fr.atraxterra.dohy.palamod.dungeon;

import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.ui.DisplayMenu;
import fr.atraxterra.dohy.ui.Gui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Le « Profil RPG » (wiki : « Arbre de competence ») : a gauche les
 * statistiques, onglets Perso et Global ; a droite l'arbre, dont on achete
 * les cases avec les points de competence, puis « Valider » ; « Reset »
 * annule ce qui n'est pas encore valide.
 *
 * <p>En coffre comme le reste de DoHy : une rangee par branche, huit noeuds
 * a acheter dans l'ordre. Un clic sur le noeud suivant le reserve, un clic
 * sur le dernier reserve le rend. <b>Ajout</b> : « Reinitialiser l'arbre »
 * rend tous les points valides contre 25 000 $ (le wiki ne dit pas si l'on
 * peut revenir sur un choix valide).
 */
public final class RpgMenu extends DisplayMenu {

    public static final long RESPEC_PRICE = 25_000L;

    private static final int LEVEL = 1;
    private static final int POINTS = 3;
    private static final int PERSONAL = 5;
    private static final int GLOBAL = 7;
    private static final int RESPEC = 45;
    private static final int CANCEL = 47;
    private static final int VALIDATE = 51;
    private static final int HELP = 53;

    private final Map<RpgStat, Integer> pending = new EnumMap<>(RpgStat.class);
    private boolean respecArmed;

    private RpgMenu(int containerId, Inventory inventory, ServerPlayer player) {
        super(containerId, inventory, player, 6);
        for (RpgStat stat : RpgStat.values()) {
            pending.put(stat, 0);
        }
        refresh();
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, viewer) -> new RpgMenu(id, inventory, player),
                Component.literal("Profil RPG")));
    }

    private int pendingCost(RpgData data) {
        int total = 0;
        for (RpgStat stat : RpgStat.values()) {
            int owned = data.nodes(stat);
            total += RpgStat.costOf(owned + pending.get(stat)) - RpgStat.costOf(owned);
        }
        return total;
    }

    @Override
    protected void build() {
        RpgData data = Rpg.data(player);
        Rpg.Points points = Rpg.points(player);
        int free = points.total() - data.spent() - pendingCost(data);

        for (int slot = 0; slot < 9; slot++) {
            set(slot, Gui.filler());
        }
        int level = Rpg.level(data.xp());
        long inLevel = Rpg.xpInLevel(data.xp());
        set(LEVEL, Gui.button(Items.EXPERIENCE_BOTTLE, Component.literal("Niveau RPG " + level)
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), level >= Rpg.MAX_LEVEL
                ? Gui.lore(Gui.good("Niveau maximum"), Gui.blank(), Gui.hint("L'XP RPG vient des salles terminees."))
                : Gui.lore(Gui.bar(inLevel, Rpg.xpToNext(level), 20), Gui.blank(),
                        Gui.hint("L'XP RPG vient des salles terminees"),
                        Gui.hint("dans les donjons."))));
        set(POINTS, Gui.button(Items.NETHER_STAR, Component.literal(free + " point" + (free > 1 ? "s" : "")
                + " disponible" + (free > 1 ? "s" : "")).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                Gui.lore(Gui.field("Niveau RPG", points.level() + " / 100"),
                        Gui.field("Paliers de donjon", points.paliers() + " / 80 (1 tous les 5)"),
                        Gui.field("Metiers", points.jobs() + " / 80 (1 tous les 5 niveaux)"),
                        Gui.blank(),
                        Gui.field("Total", String.valueOf(points.total())),
                        Gui.field("Depenses", String.valueOf(data.spent())),
                        pendingCost(data) > 0 ? Gui.field("Reserves", String.valueOf(pendingCost(data))) : null)));
        set(PERSONAL, statsTile(Items.BOOK, "Statistiques — Perso", Rpg.tree(data), null));
        set(GLOBAL, statsTile(Items.ENCHANTED_BOOK, "Statistiques — Global", Rpg.tree(data), Rpg.runes(player)));

        for (RpgStat stat : RpgStat.values()) {
            int row = (stat.ordinal() + 1) * 9;
            int owned = data.nodes(stat);
            set(row, Gui.button(stat.icon(), Component.literal(stat.label()).withStyle(stat.color(), ChatFormatting.BOLD),
                    Gui.lore(Gui.field("Noeuds", owned + " / " + RpgStat.NODES),
                            Gui.field("Bonus", RpgStat.percent(stat.perNode() * owned)),
                            Gui.blank(), Gui.hint(description(stat)))));
            for (int index = 0; index < RpgStat.NODES; index++) {
                set(row + 1 + index, node(stat, index, owned, pending.get(stat), free));
            }
        }

        footer(RESPEC, CANCEL, Gui.FOOTER_CLOSE, VALIDATE, HELP);
        set(RESPEC, Gui.button(Items.GRINDSTONE, Component.literal("Reinitialiser l'arbre")
                .withStyle(ChatFormatting.RED), Gui.lore(
                Gui.hint("Rend tous les points valides."),
                Gui.field("Prix", String.format("%,d $", RESPEC_PRICE).replace(',', ' ')),
                Gui.blank(),
                respecArmed ? Gui.bad("Cliquez encore pour confirmer") : Gui.action("Clic : reinitialiser"))));
        set(CANCEL, Gui.button(Items.STAINED_GLASS_PANE.pick(DyeColor.RED), Component.literal("Reset")
                .withStyle(ChatFormatting.RED), Gui.lore(Gui.hint("Annule ce qui n'est pas encore valide."))));
        set(Gui.FOOTER_CLOSE, Gui.close());
        set(VALIDATE, Gui.button(Items.STAINED_GLASS_PANE.pick(DyeColor.LIME), Component.literal("Valider")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), Gui.lore(
                Gui.hint("Achete les noeuds reserves."),
                pendingCost(data) > 0 ? Gui.field("Cout", pendingCost(data) + " points")
                        : Gui.hint("Rien a valider."))));
        set(HELP, Gui.button(Items.PAPER, "Comment ca marche", Gui.lore(
                Gui.hint("Clic sur le noeud suivant d'une branche : il est reserve."),
                Gui.hint("Clic sur le dernier reserve : il est rendu."),
                Gui.hint("Valider achete ; Reset annule les reserves."),
                Gui.blank(),
                Gui.hint("Les statistiques RPG ne jouent que dans les donjons,"),
                Gui.hint("contre leurs monstres."))));
    }

    private static String description(RpgStat stat) {
        return switch (stat) {
            case ATTACK -> "Degats infliges aux monstres en plus.";
            case DODGE -> "Chance d'eviter entierement un coup.";
            case CRIT -> "Chance de frapper a 150 %.";
            case DEFENSE -> "Degats recus des monstres en moins.";
        };
    }

    private ItemStack statsTile(Item icon, String title, Map<RpgStat, Float> tree, Map<RpgStat, Float> runes) {
        List<Component> lore = new ArrayList<>();
        for (RpgStat stat : RpgStat.values()) {
            float value = tree.get(stat) + (runes == null ? 0.0f : runes.get(stat));
            Component line = Component.literal(stat.label() + " : ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(RpgStat.percent(Math.min(stat.cap(), value))).withStyle(stat.color()));
            if (runes != null && runes.get(stat) > 0.0f) {
                line = line.copy().append(Component.literal("  (runes " + RpgStat.percent(runes.get(stat)) + ")")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            lore.add(line);
        }
        lore.add(Gui.blank());
        if (runes == null) {
            lore.add(Gui.hint("L'arbre seul."));
        } else {
            float reduction = 0.0f;
            for (Dungeon dungeon : Dungeon.values()) {
                reduction = Math.max(reduction, DungeonGearItem.reduction(player, dungeon));
            }
            lore.add(Gui.field("Armure de donjon", "-" + RpgStat.percent(reduction) + " de degats"));
            if (player.getMainHandItem().getItem() instanceof DungeonGearItem sword && !sword.isArmor()) {
                lore.add(Gui.field("Epee de donjon", "+" + RpgStat.percent(sword.tier().swordBonus())));
            }
            lore.add(Gui.blank());
            lore.add(Gui.hint("Arbre, runes de l'armure portee et"));
            lore.add(Gui.hint("equipement de donjon, dans son donjon."));
            lore.add(Gui.hint("Plafonds : attaque 100 %, esquive 35 %,"));
            lore.add(Gui.hint("critique 60 %, defense 50 %."));
        }
        return Gui.button(icon, Component.literal(title).withStyle(ChatFormatting.YELLOW), lore);
    }

    private ItemStack node(RpgStat stat, int index, int owned, int reserved, int free) {
        int cost = RpgStat.NODE_COSTS[index];
        Item item;
        List<Component> lore = new ArrayList<>();
        lore.add(Gui.field("Bonus", "+" + RpgStat.percent(stat.perNode()) + " " + stat.label().toLowerCase()));
        lore.add(Gui.field("Cout", cost + " point" + (cost > 1 ? "s" : "")));
        lore.add(Gui.blank());
        ChatFormatting color;
        if (index < owned) {
            item = Items.STAINED_GLASS_PANE.pick(DyeColor.LIME);
            color = ChatFormatting.GREEN;
            lore.add(Gui.good("Acquis"));
        } else if (index < owned + reserved) {
            item = Items.STAINED_GLASS_PANE.pick(DyeColor.YELLOW);
            color = ChatFormatting.YELLOW;
            lore.add(Gui.action("Reserve — Valider pour l'acheter"));
            if (index == owned + reserved - 1) {
                lore.add(Gui.hint("Clic : rendre"));
            }
        } else if (index == owned + reserved) {
            boolean affordable = cost <= free;
            item = Items.STAINED_GLASS_PANE.pick(affordable ? DyeColor.LIGHT_BLUE : DyeColor.ORANGE);
            color = affordable ? ChatFormatting.AQUA : ChatFormatting.GOLD;
            lore.add(affordable ? Gui.action("Clic : reserver") : Gui.bad("Pas assez de points"));
        } else {
            item = Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY);
            color = ChatFormatting.DARK_GRAY;
            lore.add(Gui.hint("Achetez d'abord les noeuds precedents."));
        }
        return Gui.button(item, Component.literal(stat.label() + " " + (index + 1)).withStyle(color), lore,
                index + 1);
    }

    @Override
    protected void onClick(int slot, int button, boolean shift) {
        if (slot != RESPEC) {
            respecArmed = false;
        }
        RpgData data = Rpg.data(player);
        if (slot == Gui.FOOTER_CLOSE) {
            player.closeContainer();
            return;
        }
        if (slot == CANCEL) {
            pending.replaceAll((stat, count) -> 0);
            click();
            return;
        }
        if (slot == VALIDATE) {
            int cost = pendingCost(data);
            if (cost <= 0) {
                failure();
                return;
            }
            if (cost > Rpg.points(player).total() - data.spent()) {
                pending.replaceAll((stat, count) -> 0);
                failure();
                return;
            }
            Map<RpgStat, Integer> next = new EnumMap<>(RpgStat.class);
            for (RpgStat stat : RpgStat.values()) {
                next.put(stat, data.nodes(stat) + pending.get(stat));
            }
            Rpg.store(player, data.withNodes(next));
            pending.replaceAll((stat, count) -> 0);
            success();
            player.sendSystemMessage(Component.translatable("message.palamod.rpg.validated", cost)
                    .withStyle(ChatFormatting.GREEN));
            return;
        }
        if (slot == RESPEC) {
            if (data.spent() == 0) {
                failure();
                return;
            }
            if (!respecArmed) {
                respecArmed = true;
                click(0.7f);
                return;
            }
            respecArmed = false;
            if (!Economy.tryDebit(player, RESPEC_PRICE)) {
                failure();
                player.sendSystemMessage(Component.translatable("message.palamod.rpg.respec_poor")
                        .withStyle(ChatFormatting.RED));
                return;
            }
            Rpg.store(player, data.withNodes(Map.of()));
            pending.replaceAll((stat, count) -> 0);
            success();
            player.sendSystemMessage(Component.translatable("message.palamod.rpg.respec")
                    .withStyle(ChatFormatting.GOLD));
            return;
        }
        int row = slot / 9;
        int column = slot % 9;
        if (row < 1 || row > RpgStat.values().length || column == 0) {
            return;
        }
        RpgStat stat = RpgStat.values()[row - 1];
        int index = column - 1;
        int owned = data.nodes(stat);
        int reserved = pending.get(stat);
        int free = Rpg.points(player).total() - data.spent() - pendingCost(data);
        if (index == owned + reserved && index < RpgStat.NODES && RpgStat.NODE_COSTS[index] <= free) {
            pending.put(stat, reserved + 1);
            click(1.2f);
        } else if (reserved > 0 && index == owned + reserved - 1) {
            pending.put(stat, reserved - 1);
            click(0.8f);
        } else {
            failure();
        }
    }
}

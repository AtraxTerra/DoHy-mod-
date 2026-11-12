package fr.atraxterra.dohy.spawn;

import fr.atraxterra.dohy.faction.event.FactionEventMenu;
import fr.atraxterra.dohy.palamod.dungeon.Dungeon;
import fr.atraxterra.dohy.palamod.dungeon.DungeonManager;
import fr.atraxterra.dohy.palamod.dungeon.DungeonMenus;
import fr.atraxterra.dohy.palamod.dungeon.PalaDungeon;
import fr.atraxterra.dohy.palamod.dungeon.Rpg;
import fr.atraxterra.dohy.palamod.dungeon.RpgMenu;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * La borne DONJONS du spawn : le depart vers les quatre donjons et les
 * evenements de faction.
 *
 * <p>Un donjon se lance toujours avec sa cle, comme partout ailleurs : la
 * borne la prend dans l'inventaire, la met en main et ouvre le choix du
 * palier. Elle n'offre aucun raccourci, seulement un endroit ou partir. Au
 * retour, le joueur est repose au spawn, d'ou il etait parti.
 */
public final class SpawnPortals extends MenuController {

    public static final SpawnPortals INSTANCE = new SpawnPortals();

    private SpawnPortals() {
        super("spawn_portals");
    }

    private static int keys(ServerPlayer player, Item key) {
        Inventory inventory = player.getInventory();
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(key)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    @Override
    public MenuView view(ServerPlayer player) {
        List<Card> dungeons = new ArrayList<>();
        int total = 0;
        for (Dungeon dungeon : Dungeon.values()) {
            Item key = PalaDungeon.key(dungeon);
            int owned = keys(player, key);
            int cleared = DungeonManager.cleared(player, dungeon);
            total += cleared;
            Card card = Card.of(new ItemStack(key), dungeon.display(), List.of(
                    cleared == 0 ? "Aucun palier reussi" : "Dernier palier reussi : " + cleared,
                    owned == 0 ? "Aucune cle sur toi" : owned + " cle(s) sur toi"))
                    .accent(MenuView.color(dungeon.color())).badge(owned > 0 ? owned + " CLE" : "");
            dungeons.add(owned > 0 ? card.action("dungeon:" + dungeon.name() + "|choisir le palier")
                    : card.state(MenuView.DISABLED).tooltip(List.of("Il te faut une cle de " + dungeon.display() + ".",
                            "Metier lie : " + dungeon.job().display())));
        }
        List<Card> others = List.of(
                Card.of(new ItemStack(Items.IRON_SWORD), "Evenements de faction", List.of("Les arenes de ta faction",
                        "Totem, chasse aux oeufs, boss")).accent(0xFFEF3826).action("events"),
                Card.of(new ItemStack(Items.TOTEM_OF_UNDYING), "Profil RPG", List.of(
                        "Niveau RPG " + Rpg.level(Rpg.data(player).xp()), "L'arbre des donjons"))
                        .accent(0xFFEC4FB5).action("rpg"));
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("PALIERS REUSSIS", String.valueOf(total), 0xFF22E558),
                new MenuView.Stat("NIVEAU RPG", String.valueOf(Rpg.level(Rpg.data(player).xp())), 0xFFFFFFFF));
        return new MenuView(id(), "Departs", "Les donjons et les evenements", stats, List.of(
                new MenuView.Section("DONJONS", "upgrade", MenuView.GRID, dungeons),
                new MenuView.Section("AUSSI", "resources", MenuView.GRID, others)), false);
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if ("events".equals(key)) {
            FactionEventMenu.INSTANCE.open(player);
        } else if ("rpg".equals(key)) {
            UiNet.forget(player);
            RpgMenu.INSTANCE.open(player);
        } else if (key.startsWith("dungeon:")) {
            for (Dungeon dungeon : Dungeon.values()) {
                if (dungeon.name().equals(key.substring(8))) {
                    depart(player, dungeon);
                    return;
                }
            }
        }
    }

    /** Met la cle du donjon en main, puis ouvre le choix du palier. */
    private static void depart(ServerPlayer player, Dungeon dungeon) {
        if (DungeonManager.inDimension(player) || DungeonManager.runOf(player) != null) {
            failure(player);
            return;
        }
        Item key = PalaDungeon.key(dungeon);
        if (!player.getMainHandItem().is(key)) {
            Inventory inventory = player.getInventory();
            int found = -1;
            for (int slot = 0; slot < inventory.getContainerSize() && found < 0; slot++) {
                if (inventory.getItem(slot).is(key)) {
                    found = slot;
                }
            }
            if (found < 0) {
                failure(player);
                player.sendSystemMessage(Component.literal("Il te faut une cle de " + dungeon.display() + ".")
                        .withStyle(ChatFormatting.RED));
                return;
            }
            // la cle et ce que tenait la main echangent leurs places : rien n'est perdu
            ItemStack held = player.getMainHandItem().copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, inventory.getItem(found).copy());
            inventory.setItem(found, held);
        }
        DungeonMenus.openPaliers(player, dungeon, InteractionHand.MAIN_HAND);
    }
}

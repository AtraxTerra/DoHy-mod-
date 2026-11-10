package fr.atraxterra.dohy.palamod.pillage;

import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.MenuView;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ce que l'explorateur de coffre a vu ({@code UIChestExplorer}) : les cases
 * pleines du conteneur, dans l'ordre, en vignettes. Une photo prise au clic :
 * rien ne s'y prend, rien ne s'y pose.
 */
public final class ChestExplorerMenu extends MenuController {

    public static final ChestExplorerMenu INSTANCE = new ChestExplorerMenu();

    /** Le plus grand coffre de PalaMod (endium, 108 cases), et pas au-dela. */
    static final int MAX_SLOTS = 108;

    private record Snapshot(Component name, List<ItemStack> slots) {
    }

    private final Map<UUID, Snapshot> snapshots = new ConcurrentHashMap<>();

    private ChestExplorerMenu() {
        super("chest_explorer");
    }

    void show(ServerPlayer player, Component name, List<ItemStack> slots) {
        snapshots.put(player.getUUID(), new Snapshot(name, List.copyOf(slots)));
        open(player);
    }

    @Override
    public MenuView view(ServerPlayer player) {
        Snapshot snapshot = snapshots.get(player.getUUID());
        List<ItemStack> slots = snapshot == null ? List.of() : snapshot.slots();
        List<Card> tiles = new ArrayList<>();
        int filled = 0;
        int items = 0;
        // Les cases pleines seules, dans l'ordre : la grille du kit a treize
        // colonnes, un coffre neuf ; recopier ses cases vides la decalerait.
        for (ItemStack stack : slots) {
            if (stack.isEmpty()) {
                continue;
            }
            filled++;
            items += stack.getCount();
            tiles.add(Card.of(stack.copy(), MenuView.text(stack.getHoverName()), List.of("x" + stack.getCount()))
                    .badge(stack.getCount() > 1 ? String.valueOf(stack.getCount()) : ""));
        }
        if (tiles.isEmpty()) {
            tiles.add(Card.of(new ItemStack(Items.BARRIER), "Vide", List.of("Rien dans ce conteneur"))
                    .state(MenuView.DISABLED));
        }
        String subtitle = snapshot == null ? "Accroupi, clic droit sur un conteneur"
                : MenuView.text(snapshot.name());
        return new MenuView(id(), "Explorateur de coffre", subtitle, List.of(
                new MenuView.Stat("CASES", filled + " / " + slots.size(), 0xFFFFFFFF),
                new MenuView.Stat("OBJETS", String.valueOf(items), 0xFF3FA9F5)),
                List.of(new MenuView.Section("", "", MenuView.TILES, tiles)), false);
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
    }
}

package fr.atraxterra.dohy.agri;

import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ce que partagent les ecrans du moulin et de la machine a pain : la machine
 * ouverte par chaque joueur, sa portee, et l'affichage de sa reserve.
 *
 * <p>Deux facons de s'en servir, sur le meme ecran. <b>A la main</b> : un clic
 * sur une recette puise dans l'inventaire du joueur et y rend le produit, tout
 * de suite. <b>En automate</b> : ce qu'on depose dans la machine (carte
 * « Charger », ou entonnoir) est travaille tout seul, au rythme de la machine.
 */
abstract class MachineMenu extends MenuController {

    private static final double REACH = 8.0d;
    protected static final int GREEN = 0xFF22E558;
    protected static final int GOLD = 0xFFFAC400;
    protected static final int RED = 0xFFE5484D;

    private final Map<UUID, BlockPos> open = new ConcurrentHashMap<>();
    private final AgriMachineBlock.Kind kind;

    protected MachineMenu(String path, AgriMachineBlock.Kind kind) {
        super(path);
        this.kind = kind;
    }

    public void openAt(ServerPlayer player, BlockPos pos) {
        open.put(player.getUUID(), pos.immutable());
        open(player);
    }

    /** La machine ouverte par ce joueur, ou {@code null} s'il s'en est eloigne ou qu'elle n'est plus la. */
    protected AgriMachineBlockEntity machine(ServerPlayer player) {
        BlockPos pos = open.get(player.getUUID());
        if (pos == null || player.distanceToSqr(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d)
                > REACH * REACH
                || !(player.level().getBlockState(pos).getBlock() instanceof AgriMachineBlock block)
                || block.kind() != kind) {
            return null;
        }
        return player.level().getBlockEntity(pos) instanceof AgriMachineBlockEntity machine ? machine : null;
    }

    protected static MenuView.Stat energyStat(AgriMachineBlockEntity machine) {
        int stored = machine == null ? 0 : machine.energyStored();
        int capacity = machine == null ? 0 : machine.capacity();
        return new MenuView.Stat("ENERGIE", stored + " / " + capacity + " FE", stored > 0 ? GREEN : RED);
    }

    // -- Ce que la machine contient ---------------------------------------------------

    /**
     * La section « dans la machine » : charger, tout reprendre, et chaque pile
     * qu'elle contient (un clic la rend). C'est par la que le joueur voit et
     * touche l'inventaire que les entonnoirs remplissent et vident.
     */
    protected static MenuView.Section stock(ServerPlayer player, AgriMachineBlockEntity machine) {
        List<Card> cards = new ArrayList<>();
        int loadable = 0;
        int held = 0;
        if (machine != null) {
            for (int slot = 0; slot < 36; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (machine.accepts(stack)) {
                    loadable += stack.getCount();
                }
            }
            for (int slot = 0; slot < machine.getContainerSize(); slot++) {
                held += machine.getItem(slot).getCount();
            }
        }
        Card load = Card.of(new ItemStack(Items.HOPPER), "Charger la machine", List.of(
                        "Depose ce qu'elle accepte", loadable + " objets dans ton sac"))
                .accent(GOLD).tooltip(List.of("La machine travaille seule tant qu'elle a",
                        "de quoi faire et de l'energie",
                        "Entonnoir dessus ou sur un cote : entree", "Entonnoir dessous : sortie"));
        cards.add(loadable > 0 ? load.action("load|charger la machine") : load.state(MenuView.DISABLED));
        Card unload = Card.of(new ItemStack(Items.CHEST), "Tout reprendre", List.of(
                "Rend tout ce qu'elle contient", held + " objets dans la machine"));
        cards.add(held > 0 ? unload.action("unload|tout reprendre") : unload.state(MenuView.DISABLED));
        if (machine != null) {
            for (int slot = 0; slot < machine.getContainerSize(); slot++) {
                ItemStack stack = machine.getItem(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                boolean output = slot >= AgriMachineBlockEntity.INPUTS;
                Card card = Card.of(stack.copy(), MenuView.text(stack.getHoverName()), List.of(
                                output ? "Sortie  ·  pret a prendre" : "Entree  ·  en attente"))
                        .accent(output ? GREEN : 0xFF55545C).badge("x" + stack.getCount())
                        .action("take:" + slot + "|reprendre");
                cards.add(card);
            }
        }
        return new MenuView.Section("DANS LA MACHINE", "resources", MenuView.GRID, cards);
    }

    /** L'etat de l'automate, pour la barre de l'ecran. */
    protected static MenuView.Stat workStat(AgriMachineBlockEntity machine) {
        int permille = machine == null ? 0 : machine.progressPermille();
        return new MenuView.Stat("AUTOMATE", permille > 0 ? "AU TRAVAIL" : "EN ATTENTE", permille > 0 ? GREEN : GOLD);
    }

    /**
     * Les cartes de la section « dans la machine ».
     *
     * @return vrai si la cle etait l'une d'elles
     */
    protected static boolean stockCard(ServerPlayer player, AgriMachineBlockEntity machine, String key) {
        if (key.equals("load")) {
            int moved = 0;
            for (int slot = 0; slot < 36; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (machine.accepts(stack)) {
                    moved += machine.insert(stack);
                }
            }
            player.getInventory().setChanged();
            if (moved == 0) {
                report(player, "La machine n'a plus de place pour ca.", "");
            } else {
                success(player);
            }
            return true;
        }
        if (key.equals("unload")) {
            for (int slot = 0; slot < machine.getContainerSize(); slot++) {
                giveBack(player, machine.removeItemNoUpdate(slot));
            }
            machine.setChanged();
            success(player);
            return true;
        }
        if (key.startsWith("take:")) {
            try {
                int slot = Integer.parseInt(key.substring("take:".length()));
                if (slot >= 0 && slot < machine.getContainerSize()) {
                    giveBack(player, machine.removeItemNoUpdate(slot));
                    machine.setChanged();
                    success(player);
                }
            } catch (NumberFormatException malformed) {
                // une cle mal formee ne fait rien
            }
            return true;
        }
        return false;
    }

    private static void giveBack(ServerPlayer player, ItemStack stack) {
        if (!stack.isEmpty() && (!player.getInventory().add(stack) || !stack.isEmpty())) {
            player.drop(stack, false);
        }
    }

    @Override
    protected void back(ServerPlayer player) {
        open.remove(player.getUUID());
        UiNet.close(player);
    }
}

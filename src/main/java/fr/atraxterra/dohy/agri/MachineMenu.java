package fr.atraxterra.dohy.agri;

import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.UiNet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ce que partagent les ecrans du moulin et de la machine a pain : la machine
 * ouverte par chaque joueur, sa portee, et l'affichage de sa reserve.
 *
 * <p>Ces machines n'ont pas de coffre : elles puisent dans l'inventaire du
 * joueur et y rendent leur produit, sur un clic. Seule l'energie est dans le
 * bloc.
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

    @Override
    protected void back(ServerPlayer player) {
        open.remove(player.getUUID());
        UiNet.close(player);
    }
}

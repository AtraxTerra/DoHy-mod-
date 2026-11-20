package fr.atraxterra.dohy.grade;

import fr.atraxterra.dohy.palamod.storage.EnderExtra;
import fr.atraxterra.dohy.palamod.storage.PalaStorage;
import fr.atraxterra.dohy.palamod.storage.PaladiumEnderChestBlock;
import fr.atraxterra.dohy.ui.KitMenus;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;

/**
 * Le coffre de l'Ender agrandi par les grades : les 27 cases du jeu, plus une rangee par grade
 * (Heros 36 cases, Legende 45, Divinite 54) et une pour Premium, 63 au plus.
 *
 * <p>Les rangees en plus sont gardees sur le joueur ({@link EnderExtra}), les memes que celle du
 * coffre de l'Ender en paladium : sa rangee est la premiere des rangees de grade. Tous les coffres
 * de l'Ender du joueur montrent donc le meme contenu, quel que soit le bloc ouvert.
 *
 * <p>Si la place diminue (fin de Premium), ce qui ne tient plus revient dans l'inventaire a la
 * prochaine ouverture, ou tombe aux pieds du joueur s'il est plein : rien ne disparait. La
 * rangee du coffre en paladium n'est jamais videe ainsi, puisque ce bloc l'ouvre toujours.
 */
public final class GradeEnder {

    public static final int MAX_ROWS = 7;

    private GradeEnder() {
    }

    /**
     * Ouvre le coffre de l'Ender du joueur.
     *
     * @param minRows au moins tant de rangees (4 pour le coffre en paladium)
     * @param chest   le bloc ouvert, pour son couvercle ; {@code null} a distance
     */
    public static void open(ServerPlayer player, int minRows, EnderChestBlockEntity chest, Component title) {
        int rows = Math.max(3, Math.min(MAX_ROWS, Math.max(minRows, Grades.enderRows(player))));
        PlayerEnderChestContainer ender = player.getEnderChestInventory();
        ender.setActiveChest(chest);
        if (rows <= 3) {
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, viewer) -> ChestMenu.threeRows(containerId, inventory, ender), title));
            return;
        }
        int extraSlots = (rows - 3) * 9;
        spill(player, extraSlots);
        ExtraContainer extra = new ExtraContainer(player, extraSlots);
        CompoundContainer container = new CompoundContainer(ender, extra);
        KitMenus.Use use = rows == 4 && minRows >= 4 && Grades.enderRows(player) < 4
                ? KitMenus.Use.PALADIUM_ENDER_CHEST : KitMenus.Use.gradeEnder(rows);
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, viewer) -> new ChestMenu(use.type(), containerId, inventory, container, rows),
                title));
    }

    /** Ce qui depasse la place du joueur revient dans son inventaire. */
    private static void spill(ServerPlayer player, int extraSlots) {
        int keepFrom = Math.max(extraSlots, PaladiumEnderChestBlock.EXTRA_SLOTS);
        NonNullList<ItemStack> stored = player.getData(PalaStorage.ENDER_EXTRA.get()).items();
        NonNullList<ItemStack> copy = NonNullList.withSize(EnderExtra.MAX_SLOTS, ItemStack.EMPTY);
        int moved = 0;
        for (int slot = 0; slot < copy.size() && slot < stored.size(); slot++) {
            ItemStack stack = stored.get(slot);
            if (slot < keepFrom || stack.isEmpty()) {
                copy.set(slot, stack.copy());
                continue;
            }
            moved += stack.getCount();
            ItemStack given = stack.copy();
            if (!player.getInventory().add(given)) {
                player.drop(given, false);
            }
        }
        if (moved > 0) {
            player.setData(PalaStorage.ENDER_EXTRA.get(), new EnderExtra(copy));
            player.sendSystemMessage(Component.literal("Ton coffre de l'Ender a rapetisse : " + moved
                    + (moved > 1 ? " objets qui n'y tenaient plus sont revenus"
                    : " objet qui n'y tenait plus est revenu")
                    + " dans ton inventaire.").withStyle(ChatFormatting.GOLD));
        }
    }

    /** Les rangees en plus, ecrites sur le joueur a chaque changement. */
    private static final class ExtraContainer extends SimpleContainer {

        private final ServerPlayer player;
        private boolean ready;

        ExtraContainer(ServerPlayer player, int size) {
            super(size);
            this.player = player;
            NonNullList<ItemStack> stored = player.getData(PalaStorage.ENDER_EXTRA.get()).items();
            for (int slot = 0; slot < size && slot < stored.size(); slot++) {
                setItem(slot, stored.get(slot).copy());
            }
            ready = true;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (!ready) {
                return;
            }
            NonNullList<ItemStack> stored = player.getData(PalaStorage.ENDER_EXTRA.get()).items();
            NonNullList<ItemStack> copy = NonNullList.withSize(EnderExtra.MAX_SLOTS, ItemStack.EMPTY);
            for (int slot = 0; slot < copy.size(); slot++) {
                if (slot < getContainerSize()) {
                    copy.set(slot, getItem(slot).copy());
                } else if (slot < stored.size()) {
                    copy.set(slot, stored.get(slot).copy());
                }
            }
            player.setData(PalaStorage.ENDER_EXTRA.get(), new EnderExtra(copy));
        }
    }
}

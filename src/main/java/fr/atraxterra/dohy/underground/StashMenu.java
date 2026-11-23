package fr.atraxterra.dohy.underground;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.ui.KitMenus;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;

/**
 * L'ecran d'une planque securisee (lot 24c) : un vrai coffre de trois rangees, les clics du
 * jeu. A la fermeture par son proprietaire, il verse aux stocks des entreprises ce qu'il peut
 * ({@link Goods#deposit}) ; rien ne sort du coffre qui ne soit entre dans les stocks.
 */
public final class StashMenu extends ChestMenu {

    private final StashChestBlockEntity stash;
    private final ServerPlayer player;
    private boolean settled;

    public StashMenu(int containerId, Inventory inventory, StashChestBlockEntity stash, ServerPlayer player) {
        super(KitMenus.Use.STASH_CHEST.type(), containerId, inventory, stash, 3);
        this.stash = stash;
        this.player = player;
    }

    @Override
    public void removed(Player candidate) {
        super.removed(candidate);
        if (settled || candidate != player || !stash.ownedBy(player)) {
            return;
        }
        settled = true;
        try {
            String text = Goods.deposit(player, stash);
            if (!text.isEmpty()) {
                player.sendSystemMessage(Component.literal("[Planque] ").withStyle(ChatFormatting.DARK_PURPLE)
                        .append(Component.literal(text).withStyle(ChatFormatting.GRAY)));
            }
        } catch (RuntimeException error) {
            // le coffre garde tout ce qui n'a pas ete verse : rien ne se perd
            DoHy.LOGGER.error("Planque : echec du versement aux stocks", error);
        }
    }
}

package fr.atraxterra.dohy.museum;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.ui.KitMenus;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Le guichet de dons : on y depose, la fermeture garde ce qui manque.
 *
 * <p>Reprend le modele du coffre de vente, pour la meme raison : le conteneur
 * est <b>reel</b> et les clics sont delegues a {@code super}, donc vanilla gere
 * tout le deplacement d'items. Reecrire cette mecanique serait l'occasion de
 * perdre un item — ici, un item que le joueur ne pourra peut-etre pas retrouver.
 *
 * <p>A la fermeture, le musee preleve <b>un seul exemplaire</b> de chaque item
 * qui lui manque, et rend tout le reste. On ne peut donc pas donner par erreur
 * une pile entiere, ni perdre le surplus.
 */
public final class MuseumMenu extends ChestMenu {

    private final ServerPlayer player;
    private final Container deposit;

    /** Garde contre un double traitement, comme pour le coffre de vente. */
    private boolean settled;

    public MuseumMenu(int containerId, Inventory inventory, ServerPlayer player) {
        this(containerId, inventory, player, new SimpleContainer(27));
    }

    private MuseumMenu(int containerId, Inventory inventory, ServerPlayer player, Container deposit) {
        super(KitMenus.Use.MUSEUM_DEPOSIT.type(), containerId, inventory, deposit, 3);
        this.player = player;
        this.deposit = deposit;
    }

    @Override
    public boolean stillValid(Player candidate) {
        return candidate == player;
    }

    @Override
    public void removed(Player candidate) {
        settle();
        super.removed(candidate);
    }

    /**
     * Preleve un exemplaire de chaque item manquant, rend le reste.
     *
     * <p>N'echoue jamais vers l'appelant : une exception a la fermeture
     * surviendrait alors que les items sont sortis du conteneur mais pas encore
     * rendus. Le rattrapage les restitue.
     */
    private void settle() {
        if (settled) {
            return;
        }
        settled = true;

        List<ItemStack> toReturn = new ArrayList<>();
        int donated = 0;

        try {
            for (int slot = 0; slot < deposit.getContainerSize(); slot++) {
                ItemStack stack = deposit.removeItemNoUpdate(slot);
                if (stack.isEmpty()) {
                    continue;
                }

                Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (MuseumApi.isWanted(player, id) && MuseumApi.donate(player, id)) {
                    donated++;
                    // Un seul exemplaire preleve : le surplus repart.
                    stack.shrink(1);
                }
                if (!stack.isEmpty()) {
                    toReturn.add(stack);
                }
            }

            giveBack(toReturn);

            if (donated == 0) {
                player.sendSystemMessage(Component.literal(
                                "Le musee a deja tout ce que tu as apporte.")
                        .withStyle(ChatFormatting.GRAY));
            } else {
                int finalDonated = donated;
                player.sendSystemMessage(Component.literal(finalDonated
                                + " piece(s) exposee(s) — " + MuseumApi.data(player).count()
                                + " au total.")
                        .withStyle(ChatFormatting.AQUA));
            }
        } catch (Throwable error) {
            DoHy.LOGGER.error("Echec du traitement des dons, restitution du contenu", error);
            giveBack(toReturn);
            for (int slot = 0; slot < deposit.getContainerSize(); slot++) {
                ItemStack stack = deposit.removeItemNoUpdate(slot);
                if (!stack.isEmpty()) {
                    giveBack(List.of(stack));
                }
            }
        }
    }

    private void giveBack(List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        stacks.clear();
    }
}

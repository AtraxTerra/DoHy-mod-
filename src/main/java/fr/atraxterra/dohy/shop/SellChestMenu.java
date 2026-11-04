package fr.atraxterra.dohy.shop;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.economy.price.PriceEntry;
import fr.atraxterra.dohy.economy.price.Prices;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Coffre de vente : on y depose ce qu'on veut vendre, et la fermeture regle la
 * transaction.
 *
 * <p>Contrairement a {@link ShopMenu}, ce conteneur est <b>reel</b> : les clics
 * sont delegues a {@code super}, donc vanilla gere tout le deplacement d'items —
 * clic, shift-clic, glisser-deposer, double-clic de regroupement. Reecrire cette
 * mecanique serait l'occasion de perdre des items, alors que vanilla la fait
 * correctement depuis dix ans.
 *
 * <p>A la fermeture, le contenu est trie : ce qui est vendable est paye, le
 * reste retourne dans l'inventaire, et ce qui ne rentre pas tombe aux pieds du
 * joueur. Rien n'est jamais detruit.
 *
 * <h2>Limite connue</h2>
 * <p>Un plantage du jeu alors que des items sont dans le coffre les perd : le
 * conteneur est transitoire et n'est pas sauvegarde. C'est le compromis de tous
 * les coffres de vente ; le reglage a la fermeture couvre la deconnexion
 * normale, qui est le cas courant.
 */
public final class SellChestMenu extends ChestMenu {

    private final ServerPlayer player;
    private final Container deposit;

    /**
     * Garde contre un double reglement : {@code removed} peut etre appele plus
     * d'une fois selon la facon dont le menu se ferme, et payer deux fois le
     * meme depot serait une machine a argent.
     */
    private boolean settled;

    public SellChestMenu(int containerId, Inventory inventory, ServerPlayer player) {
        this(containerId, inventory, player, new SimpleContainer(54));
    }

    private SellChestMenu(int containerId, Inventory inventory, ServerPlayer player, Container deposit) {
        super(MenuType.GENERIC_9x6, containerId, inventory, deposit, 6);
        this.player = player;
        this.deposit = deposit;
    }

    @Override
    public boolean stillValid(Player candidate) {
        return candidate == player;
    }

    /** Affiche le total courant dans la barre d'action a chaque changement. */
    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container == deposit) {
            showRunningTotal();
        }
    }

    @Override
    public void removed(Player candidate) {
        settle();
        // Apres le reglement : c'est super.removed qui rend au joueur l'item
        // eventuellement tenu par le curseur.
        super.removed(candidate);
    }

    /**
     * Regle le depot : vend le vendable, rend le reste.
     *
     * <p>N'echoue jamais vers l'appelant. Une exception ici serait levee au
     * moment ou le joueur ferme une interface, donc au pire moment possible :
     * les items seraient deja sortis du conteneur mais pas encore rendus.
     */
    private void settle() {
        if (settled) {
            return;
        }
        settled = true;

        long payout = 0L;
        int soldCount = 0;
        int soldKinds = 0;
        List<ItemStack> toReturn = new ArrayList<>();

        try {
            for (int slot = 0; slot < deposit.getContainerSize(); slot++) {
                ItemStack stack = deposit.removeItemNoUpdate(slot);
                if (stack.isEmpty()) {
                    continue;
                }

                PriceEntry entry = Prices.book().entry(stack).orElse(null);
                if (entry != null && entry.sellable()) {
                    payout += entry.basePrice() * stack.getCount();
                    soldCount += stack.getCount();
                    soldKinds++;
                } else {
                    toReturn.add(stack);
                }
            }

            giveBack(toReturn);

            if (payout > 0L) {
                long balance = Economy.credit(player, payout);
                play(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
                report(soldCount, soldKinds, payout, balance, toReturn.size());
            } else if (!toReturn.isEmpty()) {
                player.sendSystemMessage(Component.literal(
                                "Rien de vendable : " + toReturn.size() + " pile(s) rendue(s).")
                        .withStyle(ChatFormatting.GRAY));
            }
        } catch (Throwable error) {
            // Dernier recours : rendre ce qui n'a pas encore ete traite plutot
            // que de le laisser disparaitre avec le conteneur.
            DoHy.LOGGER.error("Echec du reglement du coffre de vente, restitution du contenu", error);
            giveBack(toReturn);
            for (int slot = 0; slot < deposit.getContainerSize(); slot++) {
                ItemStack stack = deposit.removeItemNoUpdate(slot);
                if (!stack.isEmpty()) {
                    giveBack(List.of(stack));
                }
            }
        }
    }

    /** Rend des piles au joueur, au sol si l'inventaire est plein. */
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

    private void report(int count, int kinds, long payout, long balance, int returned) {
        Component message = Component.literal("Vendu " + count + " items (" + kinds + " types) pour ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(MoneyFormat.exact(payout)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" · solde " + MoneyFormat.shortForm(balance))
                        .withStyle(ChatFormatting.DARK_GRAY));
        player.sendSystemMessage(message);

        if (returned > 0) {
            player.sendSystemMessage(Component.literal(
                            returned + " pile(s) non vendable(s) rendue(s).")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * Total courant du depot, dans la barre d'action.
     *
     * <p>Sans ca, le joueur ne decouvre le montant qu'apres avoir ferme, c'est-a-dire
     * apres que la vente est irreversible. Afficher le total pendant le depot
     * transforme une surprise en decision.
     */
    private void showRunningTotal() {
        long total = 0L;
        int unsellable = 0;

        for (int slot = 0; slot < deposit.getContainerSize(); slot++) {
            ItemStack stack = deposit.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            PriceEntry entry = Prices.book().entry(stack).orElse(null);
            if (entry != null && entry.sellable()) {
                total += entry.basePrice() * stack.getCount();
            } else {
                unsellable++;
            }
        }

        if (total == 0L && unsellable == 0) {
            return;
        }

        Component line = Component.literal(MoneyFormat.exact(total))
                .withStyle(ChatFormatting.GOLD);
        if (unsellable > 0) {
            line = line.copy().append(Component.literal(
                            "  ·  " + unsellable + " non vendable(s), rendu(s) a la fermeture")
                    .withStyle(ChatFormatting.GRAY));
        }
        player.displayClientMessage(line, true);
    }

    private void play(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        player.level().playSound(null, player.blockPosition(), sound, SoundSource.MASTER, volume, pitch);
    }
}

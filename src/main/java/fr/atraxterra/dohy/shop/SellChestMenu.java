package fr.atraxterra.dohy.shop;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.collection.CollectionApi;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.economy.market.DynamicPrice;
import fr.atraxterra.dohy.economy.price.PriceEntry;
import fr.atraxterra.dohy.economy.price.Prices;
import fr.atraxterra.dohy.ui.KitMenus;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Coffre de vente : on y depose ce qu'on veut vendre, et la fermeture regle la
 * transaction.
 *
 * <p>Contrairement a {@code ShopController}, ce conteneur est <b>reel</b> : les clics
 * sont delegues a {@code super}, donc vanilla gere tout le deplacement d'items —
 * clic, shift-clic, glisser-deposer, double-clic de regroupement. Reecrire cette
 * mecanique serait l'occasion de perdre des items, alors que vanilla la fait
 * correctement depuis dix ans.
 *
 * <p>A la fermeture, le contenu est trie : ce qui est vendable est paye, le
 * reste retourne dans l'inventaire, et ce qui ne rentre pas tombe aux pieds du
 * joueur. Rien n'est jamais detruit.
 *
 * <p>Un tiroir de Storage Drawers depose plein : son contenu vendable est vendu
 * avec le reste, et le tiroir rendu, vide ou avec ce qui ne se vend pas
 * ({@link DrawerSale}).
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
        this(containerId, inventory, player, new Deposit());
    }

    private SellChestMenu(int containerId, Inventory inventory, ServerPlayer player, Container deposit) {
        super(KitMenus.Use.SELL_CHEST.type(), containerId, inventory, deposit, 6);
        this.player = player;
        this.deposit = deposit;
        // Un SimpleContainer ne previent pas le menu de ses changements : sans
        // ce relais, le total courant n'etait jamais recalcule.
        if (deposit instanceof Deposit relay) {
            relay.menu = this;
        }
    }

    /** Le depot, qui previent le menu a chaque changement (le total courant). */
    private static final class Deposit extends SimpleContainer {

        private SellChestMenu menu;

        private Deposit() {
            super(54);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (menu != null) {
                menu.slotsChanged(this);
            }
        }
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
        int drawers = 0;
        int drawersLeft = 0;
        List<ItemStack> toReturn = new ArrayList<>();

        try {
            // Agregation par item avant tarification : le prix dynamique est une
            // integrale sur la quantite vendue, et la calculer une fois pour
            // 320 blés donne le bon montant, alors que cinq piles tarifees
            // separement partiraient toutes au prix d'avant-vente.
            Map<Item, Integer> batch = new LinkedHashMap<>();

            for (int slot = 0; slot < deposit.getContainerSize(); slot++) {
                ItemStack stack = deposit.removeItemNoUpdate(slot);
                if (stack.isEmpty()) {
                    continue;
                }

                DrawerSale.Plan drawer = DrawerSale.plan(player, stack, item -> true,
                        item -> Integer.MAX_VALUE - batch.getOrDefault(item, 0)).orElse(null);
                if (drawer != null) {
                    // Un tiroir : son contenu rejoint la vente, le tiroir revient au joueur.
                    drawer.sold().forEach((item, amount) -> batch.merge(item, amount, Integer::sum));
                    toReturn.add(drawer.apply());
                    drawers++;
                    if (drawer.leftover()) {
                        drawersLeft++;
                    }
                    continue;
                }

                PriceEntry entry = Prices.book().entry(stack).orElse(null);
                if (entry != null && entry.sellable() && unlocked(stack, entry)) {
                    batch.merge(stack.getItem(), stack.getCount(), Integer::sum);
                } else {
                    // Non vendable, ou collection pas encore debloquee : rendu.
                    toReturn.add(stack);
                }
            }

            for (Map.Entry<Item, Integer> line : batch.entrySet()) {
                PriceEntry entry = Prices.book().entry(line.getKey()).orElse(null);
                if (entry == null) {
                    continue;
                }
                payout += DynamicPrice.sell(player,
                        BuiltInRegistries.ITEM.getKey(line.getKey()), entry, line.getValue());
                soldCount += line.getValue();
                soldKinds++;
            }

            int returned = toReturn.size() - drawers;
            giveBack(toReturn);

            if (payout > 0L) {
                long balance = Economy.credit(player, payout);
                fr.atraxterra.dohy.palamod.achievement.Achievements.sold(player, payout);
                play(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
                report(soldCount, soldKinds, payout, balance, returned);
            } else if (returned > 0) {
                player.sendSystemMessage(Component.literal(
                                "Rien de vendable : " + returned + " pile(s) rendue(s).")
                        .withStyle(ChatFormatting.GRAY));
            }
            if (drawers > 0) {
                player.sendSystemMessage(Component.literal(drawers(drawers, drawersLeft))
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

    /** « 2 tiroirs rendus, dont 1 avec ce qui ne se vend pas. » */
    static String drawers(int drawers, int withLeftover) {
        String line = drawers + (drawers > 1 ? " tiroirs rendus" : " tiroir rendu");
        if (withLeftover == 0) {
            return line + (drawers > 1 ? ", vides." : ", vide.");
        }
        return line + (drawers > 1 ? ", dont " + withLeftover + " avec ce qui ne se vend pas." : " avec ce qui ne se vend pas.");
    }

    private boolean unlocked(ItemStack stack, PriceEntry entry) {
        return CollectionApi.isUnlocked(player,
                BuiltInRegistries.ITEM.getKey(stack.getItem()), entry);
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
        int drawers = 0;
        Map<Item, Integer> batch = new LinkedHashMap<>();

        for (int slot = 0; slot < deposit.getContainerSize(); slot++) {
            ItemStack stack = deposit.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            DrawerSale.Plan drawer = DrawerSale.plan(player, stack, item -> true,
                    item -> Integer.MAX_VALUE - batch.getOrDefault(item, 0)).orElse(null);
            if (drawer != null) {
                // Apercu seul : le tiroir n'est pas touche avant la fermeture.
                drawer.sold().forEach((item, amount) -> batch.merge(item, amount, Integer::sum));
                drawers++;
                continue;
            }
            PriceEntry entry = Prices.book().entry(stack).orElse(null);
            if (entry != null && entry.sellable() && unlocked(stack, entry)) {
                batch.merge(stack.getItem(), stack.getCount(), Integer::sum);
            } else {
                unsellable++;
            }
        }

        // Meme agregation que le reglement, pour que l'apercu annonce
        // exactement ce qui sera paye.
        for (Map.Entry<Item, Integer> line : batch.entrySet()) {
            PriceEntry entry = Prices.book().entry(line.getKey()).orElse(null);
            if (entry != null) {
                total += DynamicPrice.quote(player,
                        BuiltInRegistries.ITEM.getKey(line.getKey()), entry, line.getValue());
            }
        }

        if (total == 0L && unsellable == 0 && drawers == 0) {
            return;
        }

        Component line = Component.literal(MoneyFormat.exact(total))
                .withStyle(ChatFormatting.GOLD);
        if (drawers > 0) {
            line = line.copy().append(Component.literal(
                            "  ·  " + drawers + (drawers > 1 ? " tiroirs, rendus" : " tiroir, rendu")
                                    + " a la fermeture")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (unsellable > 0) {
            line = line.copy().append(Component.literal(
                            "  ·  " + unsellable + " non vendable(s), rendu(s) a la fermeture")
                    .withStyle(ChatFormatting.GRAY));
        }
        // displayClientMessage a disparu en 26.2 : on envoie directement le
        // paquet de barre d'action.
        player.connection.send(new ClientboundSetActionBarTextPacket(line));
    }

    private void play(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        player.level().playSound(null, player.blockPosition(), sound, SoundSource.MASTER, volume, pitch);
    }
}

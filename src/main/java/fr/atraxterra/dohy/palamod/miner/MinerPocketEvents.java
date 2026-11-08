package fr.atraxterra.dohy.palamod.miner;

import fr.atraxterra.dohy.collection.CollectionApi;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.economy.market.DynamicPrice;
import fr.atraxterra.dohy.economy.price.PriceEntry;
import fr.atraxterra.dohy.economy.price.Prices;
import fr.atraxterra.dohy.job.JobConfig;
import fr.atraxterra.dohy.job.JobGate;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Les objets qui agissent depuis l'inventaire : l'aimant, la Dollars Stone et la
 * Voidstone minage.
 *
 * <h2>Voidstone minage</h2>
 *
 * <p>Meme case que la Dollars Stone, memes conditions — c'est le meme
 * gestionnaire dans PalaMod. Voir {@link VoidstoneMinageItem} pour le filtre.
 *
 * <h2>Aimant ({@code ItemMagnet.onUpdate})</h2>
 *
 * <p>Actif, dans la barre d'action (cases 0 a 8) : chaque tick, chaque objet au
 * sol dans un cube de 6 blocs autour du joueur est ramene sur lui. Il marche en
 * creatif, comme l'original.
 *
 * <h2>Dollars Stone ({@code ItemMoulaStone}, {@code PlayerHandler.playerTickEvent})</h2>
 *
 * <p>Elle n'agit que dans la <b>derniere case de la barre d'action</b> (index 8),
 * hors creatif. Chaque tick, pour chaque pile de son filtre presente dans les 36
 * cases, elle retire <b>un</b> exemplaire. Les gains s'accumulent et ne sont
 * verses qu'a partir de 100 $.
 *
 * <p>Le filtre est celui de PalaMod : pierre, cobblestone, terre, gravier,
 * granite, diorite, andesite et leurs formes polies. Paladium payait la
 * cobblestone et la terre 0,10 $ et detruisait le reste pour rien. Ici c'est le
 * <b>marche de DoHy qui paie</b> : chaque exemplaire est vendu au prix dynamique,
 * avec sa pression sur le prix et ses bonus, exactement comme par {@code /sell}.
 * La Dollars Stone n'est donc pas une source d'argent de plus — elle vend a la
 * place du joueur. Un objet du filtre que DoHy ne rachete pas est detruit, comme
 * dans l'original ; un objet que DoHy rachete mais dont la vente est encore
 * verrouillee par la collection est <b>laisse</b>, parce que le detruire ferait
 * perdre une valeur reelle.
 *
 * <p>Les ventes sont regroupees : le prix dynamique est une integrale, et
 * vendre un exemplaire par appel arrondirait chaque fois au dollar superieur. On
 * vend donc toutes les secondes ce qui s'est accumule.
 *
 * <p>Les deux objets sont verrouilles par metier (Mineur 35 et 40) : un objet de
 * poche ne passe par aucun clic, le verrou est donc teste ici.
 */
@EventBusSubscriber
public final class MinerPocketEvents {

    /** {@code AxisAlignedBB(x-6, y-6, z-6, x+6, y+6, z+6)}. */
    private static final double MAGNET_RANGE = 6.0d;

    /** Index de la case : {@code mainInventory[8]}. */
    private static final int STONE_SLOT = 8;

    /** Seuil de versement : {@code pendingDollarStoneMoney >= 100.0}. */
    private static final long PAYOUT_THRESHOLD = 100L;

    /** Une vente groupee par seconde. */
    private static final int SELL_PERIOD = 20;

    private static final Set<Item> FILTER = Set.of(
            Items.STONE, Items.COBBLESTONE, Items.DIRT, Items.GRAVEL,
            Items.GRANITE, Items.POLISHED_GRANITE, Items.DIORITE, Items.POLISHED_DIORITE,
            Items.ANDESITE, Items.POLISHED_ANDESITE);

    private static final class Pending {
        final Map<Item, Integer> items = new LinkedHashMap<>();
        long money;
    }

    /** Hors sauvegarde, comme {@code pendingDollarStoneMoney} ; vide a la deconnexion. */
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private MinerPocketEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        magnet(player);
        dollarsStone(player);
        voidstoneMinage(player);
    }

    private static void voidstoneMinage(ServerPlayer player) {
        if (player.isCreative()) {
            return;
        }
        Inventory inventory = player.getInventory();
        ItemStack minage = inventory.getItem(STONE_SLOT);
        if (!(minage.getItem() instanceof VoidstoneMinageItem) || !usable(player, minage)) {
            return;
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (VoidstoneMinageItem.COUNTED.contains(stack.getItem())) {
                VoidstoneMinageItem.addStone(minage, stack.getCount());
                inventory.setItem(slot, ItemStack.EMPTY);
            } else if (VoidstoneMinageItem.DESTROYED.contains(stack.getItem())) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    private static void magnet(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.SELECTION_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof MagnetItem && MagnetItem.isActive(stack) && usable(player, stack)) {
                pull(player);
                return;
            }
        }
    }

    private static void pull(ServerPlayer player) {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        AABB box = new AABB(x - MAGNET_RANGE, y - MAGNET_RANGE, z - MAGNET_RANGE,
                x + MAGNET_RANGE, y + MAGNET_RANGE, z + MAGNET_RANGE);
        for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, box)) {
            item.setPos(x, y, z);
        }
    }

    private static void dollarsStone(ServerPlayer player) {
        if (player.isCreative()) {
            return;
        }
        Inventory inventory = player.getInventory();
        ItemStack stone = inventory.getItem(STONE_SLOT);
        if (!stone.is(PalaMiner.MOULA_STONE.get()) || !usable(player, stone)) {
            flushIfDue(player);
            return;
        }

        Pending pending = null;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !FILTER.contains(stack.getItem())) {
                continue;
            }
            Verdict verdict = verdict(player, stack);
            if (verdict == Verdict.KEEP) {
                continue;
            }
            if (verdict == Verdict.SELL) {
                if (pending == null) {
                    pending = PENDING.computeIfAbsent(player.getUUID(), uuid -> new Pending());
                }
                pending.items.merge(stack.getItem(), 1, Integer::sum);
            }
            stack.shrink(1);
        }
        flushIfDue(player);
    }

    private enum Verdict { SELL, DESTROY, KEEP }

    private static Verdict verdict(ServerPlayer player, ItemStack stack) {
        PriceEntry entry = Prices.book().entry(stack).orElse(null);
        if (entry == null || !entry.sellable()) {
            return Verdict.DESTROY;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return CollectionApi.isUnlocked(player, id, entry) ? Verdict.SELL : Verdict.KEEP;
    }

    private static void flushIfDue(ServerPlayer player) {
        if (player.tickCount % SELL_PERIOD == 0) {
            flush(player, false);
        }
    }

    /**
     * Vend ce qui s'est accumule, et verse si le seuil est atteint.
     *
     * @param force verser meme sous le seuil — a la deconnexion. Paladium perdait
     *              alors ce qui n'avait pas atteint 100 $ ; ici rien ne se perd.
     */
    private static void flush(ServerPlayer player, boolean force) {
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null) {
            return;
        }
        for (Map.Entry<Item, Integer> line : pending.items.entrySet()) {
            PriceEntry entry = Prices.book().entry(line.getKey()).orElse(null);
            if (entry != null) {
                pending.money += DynamicPrice.sell(player,
                        BuiltInRegistries.ITEM.getKey(line.getKey()), entry, line.getValue());
            }
        }
        pending.items.clear();

        if (pending.money >= PAYOUT_THRESHOLD || (force && pending.money > 0)) {
            long paid = pending.money;
            pending.money = 0L;
            long balance = Economy.credit(player, paid);
            if (!force) {
                player.connection.send(new ClientboundSetActionBarTextPacket(
                        Component.literal("Dollars Stone  +" + MoneyFormat.exact(paid))
                                .withStyle(ChatFormatting.GOLD)
                                .append(Component.literal("  · solde " + MoneyFormat.shortForm(balance))
                                        .withStyle(ChatFormatting.DARK_GRAY))));
            }
        }
        if (pending.money == 0L && pending.items.isEmpty()) {
            PENDING.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            flush(player, true);
            PENDING.remove(player.getUUID());
        }
    }

    /** Le creatif passe outre les verrous, comme partout ailleurs. */
    static boolean usable(ServerPlayer player, ItemStack stack) {
        if (player.isCreative()) {
            return true;
        }
        JobConfig.Unlock unlock = JobGate.of(stack);
        return JobGate.allows(player, unlock);
    }
}

package fr.atraxterra.dohy.agri;

import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.shop.ShopController;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L'ecran du silo : ce qu'il contient, cereale par cereale. Un clic prend une
 * pile, un clic droit en achete un lot au prix de la boutique (cours de
 * l'heure et remises compris) et le verse dans le silo ; une carte y depose
 * toutes les cereales du sac.
 */
public final class SiloMenu extends MenuController {

    public static final SiloMenu INSTANCE = new SiloMenu();

    private static final double REACH = 8.0d;
    private static final int STACK = 64;
    private static final int GREEN = 0xFF22E558;
    private static final int GOLD = 0xFFFAC400;
    private static final int RED = 0xFFE5484D;
    private static final int GREY = 0xFF55545C;

    private final Map<UUID, BlockPos> open = new ConcurrentHashMap<>();

    private SiloMenu() {
        super("silo");
    }

    public void openAt(ServerPlayer player, BlockPos pos) {
        open.put(player.getUUID(), pos.immutable());
        open(player);
    }

    private SiloBlockEntity silo(ServerPlayer player) {
        BlockPos pos = open.get(player.getUUID());
        if (pos == null || player.distanceToSqr(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d)
                > REACH * REACH) {
            return null;
        }
        return player.level().getBlockEntity(pos) instanceof SiloBlockEntity silo ? silo : null;
    }

    private static int carried(ServerPlayer player, SiloBlockEntity silo) {
        int count = 0;
        for (int slot = 0; slot < silo.kinds(); slot++) {
            count += AgriInv.count(player, silo.kind(slot));
        }
        return count;
    }

    @Override
    public MenuView view(ServerPlayer player) {
        AgriConfig.Farming config = AgriConfig.config().farming();
        SiloBlockEntity silo = silo(player);
        int total = silo == null ? 0 : silo.total();
        int batch = config.siloBuyBatch();
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("STOCK", total + " / " + config.siloCapacity(),
                        total >= config.siloCapacity() ? RED : 0xFFFFFFFF),
                new MenuView.Stat("SOLDE", MoneyFormat.shortForm(Economy.balance(player)), GOLD),
                new MenuView.Stat("TRACTEUR", "SE VIDE A " + config.siloRange() + " BLOCS", GREEN));
        List<Card> cards = new ArrayList<>();
        if (silo != null) {
            int carried = carried(player, silo);
            Card deposit = Card.of(new ItemStack(Items.HOPPER), "Tout deposer", List.of(
                            "Verse les cereales de ton sac", carried + " dans ton sac"))
                    .accent(GOLD).tooltip(List.of("Un clic sur le silo, une cereale en main,", "la depose aussi",
                            "Entonnoir ou convoyeur dessus ou sur un cote : entree",
                            "Entonnoir ou convoyeur dessous : sortie"));
            cards.add(carried > 0 && total < config.siloCapacity() ? deposit.action("deposit|tout deposer")
                    : deposit.state(MenuView.DISABLED));
            for (int slot = 0; slot < silo.kinds(); slot++) {
                Item item = silo.kind(slot);
                int stored = silo.stored(slot);
                long price = ShopController.buyPrice(player, BuiltInRegistries.ITEM.getKey(item)) * batch;
                List<String> lines = new ArrayList<>();
                lines.add(stored > 0 ? "Clic : prendre une pile" : "Aucune en stock");
                if (price > 0L) {
                    lines.add("Clic droit : " + batch + " pour " + MoneyFormat.exact(price));
                }
                Card card = Card.of(new ItemStack(item), MenuView.text(new ItemStack(item).getHoverName()), lines)
                        .accent(stored > 0 ? GREEN : GREY).badge(stored > 0 ? "x" + stored : "");
                if (stored > 0) {
                    card = card.action("take:" + slot + "|prendre une pile");
                }
                if (price > 0L) {
                    card = card.right("buy:" + slot + "|acheter " + batch + " cereales");
                }
                cards.add(stored > 0 || price > 0L ? card : card.state(MenuView.DISABLED));
            }
        }
        return new MenuView(id(), "Silo", "Clic gauche : prendre une pile  ·  clic droit : acheter un lot", stats,
                List.of(new MenuView.Section("CEREALES", "resources", MenuView.GRID, cards)), true);
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        SiloBlockEntity silo = silo(player);
        if (silo == null) {
            UiNet.close(player);
            return;
        }
        if (key.equals("deposit")) {
            int moved = 0;
            for (int slot = 0; slot < silo.kinds(); slot++) {
                Item item = silo.kind(slot);
                int taken = silo.deposit(item, AgriInv.count(player, item));
                AgriInv.take(player, item, taken);
                moved += taken;
            }
            if (moved > 0) {
                play(player, SoundEvents.COMPOSTER_FILL, 0.8f);
            } else {
                report(player, "Le silo est plein.", "");
            }
            return;
        }
        int colon = key.indexOf(':');
        if (colon < 0) {
            return;
        }
        int slot;
        try {
            slot = Integer.parseInt(key.substring(colon + 1));
        } catch (NumberFormatException malformed) {
            return;
        }
        if (slot < 0 || slot >= silo.kinds()) {
            return;
        }
        Item item = silo.kind(slot);
        if (key.startsWith("take:")) {
            int taken = silo.withdraw(slot, STACK);
            if (taken > 0) {
                AgriInv.give(player, item, taken);
                success(player);
            }
        } else if (key.startsWith("buy:")) {
            buy(player, silo, item);
        }
    }

    /** Achete un lot et le verse dans le silo : rien n'est debite si le silo ne peut pas tout prendre. */
    private static void buy(ServerPlayer player, SiloBlockEntity silo, Item item) {
        int batch = AgriConfig.config().farming().siloBuyBatch();
        long price = ShopController.buyPrice(player, BuiltInRegistries.ITEM.getKey(item)) * batch;
        if (price <= 0L) {
            report(player, "Cette cereale n'est pas en vente.", "");
        } else if (SiloBlockEntity.capacity() - silo.total() < batch) {
            report(player, "Le silo n'a plus la place pour un lot.", "");
        } else if (!Economy.tryDebit(player, price)) {
            report(player, "Il te manque " + MoneyFormat.exact(price - Economy.balance(player)) + ".", "");
        } else {
            silo.deposit(item, batch);
            success(player);
        }
    }

    @Override
    protected void back(ServerPlayer player) {
        open.remove(player.getUUID());
        UiNet.close(player);
    }
}

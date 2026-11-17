package fr.atraxterra.dohy.arrakis;

import fr.atraxterra.dohy.agri.DoHyAgri;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * La borne EPICE d'Arrakeen : le seul comptoir ou le melange se vend, au cours
 * de l'heure, et ou s'achete la moissonneuse.
 */
public final class SpiceCounterMenu extends MenuController {

    public static final SpiceCounterMenu INSTANCE = new SpiceCounterMenu();

    /** Le prix de la moissonneuse, livree avec un quart de plein. */
    public static final long HARVESTER_PRICE = 1_000_000L;
    private static final int DELIVERED_FUEL = SpiceHarvester.TANK / 4;
    private static final double REACH = 12.0d;
    private static final int STACK = 64;
    private static final int SPICE = 0xFFE8742C;
    private static final int GREEN = 0xFF22E558;
    private static final int RED = 0xFFE5484D;

    private SpiceCounterMenu() {
        super("arrakis_spice");
    }

    /** Le comptoir ne sert qu'a qui s'y tient. */
    private static boolean atCounter(ServerPlayer player) {
        BlockPos counter = PortBuilder.COUNTER;
        return DoHyArrakis.onArrakis(player)
                && player.distanceToSqr(counter.getX() + 0.5d, counter.getY(), counter.getZ() + 0.5d) <= REACH * REACH;
    }

    @Override
    public MenuView view(ServerPlayer player) {
        long balance = Economy.balance(player);
        int carried = SpiceInv.count(player, ArrakisContent.MELANGE.get());
        int percent = SpiceMarket.percent();
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("SOLDE", MoneyFormat.exact(balance) + " $", 0xFFFFFFFF),
                new MenuView.Stat("COURS", MoneyFormat.exact(SpiceMarket.unitPrice(player)) + " $ (" + percent + " %)",
                        percent >= 100 ? GREEN : RED),
                new MenuView.Stat("EN POCHE", carried + " melange", SPICE));

        Card sellAll = Card.of(new ItemStack(ArrakisContent.MELANGE.get(), Math.max(1, Math.min(64, carried))),
                "Tout vendre", List.of(carried + " melange sur toi",
                        "Rapporte ~" + MoneyFormat.exact(SpiceMarket.quote(player, carried)) + " $"))
                .accent(SPICE).badge(carried > 0 ? "x" + carried : "");
        sellAll = carried > 0 ? sellAll.action("sell_all|tout vendre") : sellAll.state(MenuView.DISABLED);
        int stack = Math.min(STACK, carried);
        Card sellStack = Card.of(new ItemStack(ArrakisContent.MELANGE.get()), "Vendre une pile", List.of(
                        "64 melange au plus", "Rapporte ~" + MoneyFormat.exact(SpiceMarket.quote(player, stack)) + " $"))
                .accent(SPICE);
        sellStack = carried > 0 ? sellStack.action("sell_stack|vendre une pile") : sellStack.state(MenuView.DISABLED);

        Card harvester = Card.of(new ItemStack(ArrakisContent.HARVESTER_ITEM.get()), "Moissonneuse d'epice",
                List.of(MoneyFormat.exact(HARVESTER_PRICE) + " $", "Livree avec un quart de plein",
                        "Deux melange par bloc, trois blocs de large"))
                .accent(0xFFCC9442).badge("ACHAT")
                .tooltip(List.of("Pose-la sur le sable : tu en es le proprietaire.",
                        "Monte dedans, la touche de saut ouvre sa bouche.",
                        "Carburant : charbon, bidons de NTM, seau de lave."));
        harvester = balance >= HARVESTER_PRICE ? harvester.action("buy|acheter la moissonneuse")
                : harvester.state(MenuView.DISABLED).tooltip(List.of("Il te manque "
                        + MoneyFormat.exact(HARVESTER_PRICE - balance) + " $."));

        Card about = Card.of(ItemStack.EMPTY, "Le cours de l'epice", List.of(
                "La Guilde ne rachete le melange qu'ici, a Arrakeen.",
                "- Le cours change toutes les heures : prochain dans " + SpiceMarket.minutesLeft() + " min",
                "- Plus tu en vends, plus le prix baisse ; il remonte avec le temps",
                "- Les champs d'epice : des taches rouille dans l'erg, loin de la ville",
                "- Les eruptions d'epice en font naitre de nouveaux : guette le grondement",
                "! A la main, une pelle n'en tire qu'un melange sur trois blocs."));
        return new MenuView(id(), "Comptoir de l'epice", "Arrakeen — la Guilde rachete le melange", stats, List.of(
                new MenuView.Section("VENTE", "resources", MenuView.GRID, List.of(sellAll, sellStack)),
                new MenuView.Section("MOISSONNEUSE", "resources", MenuView.GRID, List.of(harvester)),
                new MenuView.Section("ARRAKIS", "", MenuView.TEXT, List.of(about))), false);
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if (!atCounter(player)) {
            UiNet.close(player);
            return;
        }
        switch (key) {
            case "sell_all", "sell_stack" -> {
                int carried = SpiceInv.count(player, ArrakisContent.MELANGE.get());
                int wanted = "sell_all".equals(key) ? carried : Math.min(STACK, carried);
                int taken = SpiceInv.take(player, ArrakisContent.MELANGE.get(), wanted);
                if (taken <= 0) {
                    failure(player);
                    return;
                }
                long payout = SpiceMarket.sell(player, taken);
                success(player);
                player.sendSystemMessage(Component.literal("Vendu " + taken + " melange pour "
                        + MoneyFormat.exact(payout) + " $").withStyle(ChatFormatting.GOLD));
            }
            case "buy" -> {
                if (!Economy.tryDebit(player, HARVESTER_PRICE)) {
                    report(player, "Il te manque " + MoneyFormat.exact(HARVESTER_PRICE - Economy.balance(player))
                            + " $.", null);
                    return;
                }
                ItemStack item = new ItemStack(ArrakisContent.HARVESTER_ITEM.get());
                item.set(DoHyAgri.FUEL.get(), DELIVERED_FUEL);
                if (!player.getInventory().add(item) || !item.isEmpty()) {
                    player.drop(item, false);
                }
                report(player, null, "Moissonneuse livree : pose-la sur le sable, hors de la ville.");
            }
            default -> {
            }
        }
    }
}

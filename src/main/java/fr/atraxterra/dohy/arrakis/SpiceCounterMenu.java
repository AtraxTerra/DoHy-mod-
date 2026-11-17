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
 * de l'heure, ou la Guilde rachete les dents de ver, et ou s'achetent la
 * moissonneuse et les marteleurs.
 */
public final class SpiceCounterMenu extends MenuController {

    public static final SpiceCounterMenu INSTANCE = new SpiceCounterMenu();

    /** Le prix de la moissonneuse, livree avec un quart de plein. */
    public static final long HARVESTER_PRICE = 1_000_000L;
    /** Le prix d'un marteleur. */
    public static final long THUMPER_PRICE = 15_000L;
    /** Ce que la Guilde donne d'une dent de ver. */
    public static final long TOOTH_PRICE = 80_000L;
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

        int teeth = SpiceInv.count(player, ArrakisContent.WORM_TOOTH.get());
        Card sellTeeth = Card.of(new ItemStack(ArrakisContent.WORM_TOOTH.get(), Math.max(1, Math.min(16, teeth))),
                "Dents de ver", List.of(MoneyFormat.exact(TOOTH_PRICE) + " $ la dent",
                        teeth > 0 ? teeth + " sur toi" : "Un ver en perd parfois en refermant sa gueule"))
                .accent(0xFFE8E0C8).badge(teeth > 0 ? "x" + teeth : "");
        sellTeeth = teeth > 0 ? sellTeeth.action("sell_teeth|vendre les dents") : sellTeeth.state(MenuView.DISABLED);
        Card thumper = Card.of(new ItemStack(ArrakisContent.THUMPER_ITEM.get()), "Marteleur",
                List.of(MoneyFormat.exact(THUMPER_PRICE) + " $", "Une minute de coups sourds",
                        "Il appelle les vers, loin de toi"))
                .accent(0xFFB0B4BA).badge("ACHAT")
                .tooltip(List.of("Plante-le dans le sable de l'erg : il frappe une minute.",
                        "Un ver qui l'entend s'y rend, et le broie.",
                        "Pose-le loin de ta moissonneuse avant qu'il n'arrive."));
        thumper = balance >= THUMPER_PRICE ? thumper.action("buy_thumper|acheter un marteleur")
                : thumper.state(MenuView.DISABLED);

        Card about = Card.of(ItemStack.EMPTY, "Le cours de l'epice", List.of(
                "La Guilde ne rachete le melange qu'ici, a Arrakeen.",
                "- Le cours change toutes les heures : prochain dans " + SpiceMarket.minutesLeft() + " min",
                "- Plus tu en vends, plus le prix baisse ; il remonte avec le temps",
                "- Les champs d'epice : des taches rouille dans l'erg, loin de la ville",
                "- Les eruptions d'epice en font naitre de nouveaux : guette le grondement",
                "! A la main, une pelle n'en tire qu'un melange sur trois blocs."));
        Card worms = Card.of(ItemStack.EMPTY, "Les vers des sables", List.of(
                "Tout ce qui bat le sable de l'erg a un rythme les attire.",
                "- Moissonneuse bouche ouverte : beaucoup ; en roulant : un peu",
                "- Course : assez ; marche : a peine ; accroupi ou immobile : rien",
                "- Signe de ver : le sable bouillonne, il creuse, il vient",
                "- Le sable tremble sous toi : cours, tu as moins d'une seconde",
                "- Arrete-toi, tais-toi, ou gagne la roche : il perd ta trace",
                "! Un marteleur plante loin de toi le detourne."));
        return new MenuView(id(), "Comptoir de l'epice", "Arrakeen — la Guilde rachete le melange", stats, List.of(
                new MenuView.Section("VENTE", "resources", MenuView.GRID, List.of(sellAll, sellStack, sellTeeth)),
                new MenuView.Section("MATERIEL", "resources", MenuView.GRID, List.of(harvester, thumper)),
                new MenuView.Section("ARRAKIS", "", MenuView.TEXT, List.of(about, worms))), false);
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
            case "sell_teeth" -> {
                int taken = SpiceInv.take(player, ArrakisContent.WORM_TOOTH.get(),
                        SpiceInv.count(player, ArrakisContent.WORM_TOOTH.get()));
                if (taken <= 0) {
                    failure(player);
                    return;
                }
                long payout = TOOTH_PRICE * taken;
                Economy.credit(player, payout);
                fr.atraxterra.dohy.palamod.achievement.Achievements.sold(player, payout);
                success(player);
                player.sendSystemMessage(Component.literal("Vendu " + taken + " dent" + (taken > 1 ? "s" : "")
                        + " de ver pour " + MoneyFormat.exact(payout) + " $").withStyle(ChatFormatting.GOLD));
            }
            case "buy_thumper" -> {
                if (!Economy.tryDebit(player, THUMPER_PRICE)) {
                    failure(player);
                    return;
                }
                SpiceInv.give(player, ArrakisContent.THUMPER_ITEM.get(), 1);
                report(player, null, "Marteleur achete : plante-le dans le sable, loin de toi.");
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

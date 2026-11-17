package fr.atraxterra.dohy.arrakis;

import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * La borne EAU d'Arrakeen : le marchand d'eau. On y boit a la citerne, on y fait remplir
 * ses gourdes a la gorgee, et on y achete ce qui garde en vie dans l'erg : la gourde, le
 * distille piece a piece (ou entier, un peu moins cher) et le piege a vent.
 */
public final class WaterMenu extends MenuController {

    public static final WaterMenu INSTANCE = new WaterMenu();

    /** Boire tout son saoul a la citerne. */
    public static final long DRINK_PRICE = 500L;
    /** Une gorgee versee dans une gourde. */
    public static final long SIP_PRICE = 150L;
    public static final long CANTEEN_PRICE = 20_000L;
    public static final long MASK_PRICE = 40_000L;
    public static final long SUIT_PRICE = 90_000L;
    public static final long LEGGINGS_PRICE = 60_000L;
    public static final long BOOTS_PRICE = 30_000L;
    /** Les quatre pieces ensemble : vingt mille de moins qu'une a une. */
    public static final long STILLSUIT_PRICE = 200_000L;
    public static final long WINDTRAP_PRICE = 75_000L;
    /** Le paracompas : pour trouver un premier sietch. */
    public static final long PARACOMPASS_PRICE = 15_000L;

    private static final double REACH = 12.0d;
    private static final int WATER = 0xFF4FB8FF;
    private static final int SUIT_ACCENT = 0xFFC8A878;
    private static final int GREEN = 0xFF22E558;
    private static final int GOLD = 0xFFFFB52E;
    private static final int RED = 0xFFE5484D;

    private WaterMenu() {
        super("arrakis_water");
    }

    /** Le marchand ne sert qu'a qui se tient devant lui. */
    private static boolean atSeller(ServerPlayer player) {
        BlockPos seller = PortBuilder.WATER;
        return DoHyArrakis.onArrakis(player)
                && player.distanceToSqr(seller.getX() + 0.5d, seller.getY(), seller.getZ() + 0.5d) <= REACH * REACH;
    }

    /** Les gorgees qui manquent aux gourdes du joueur. */
    private static int missingSips(ServerPlayer player) {
        int missing = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof CanteenItem) {
                missing += CanteenItem.capacity(stack) - CanteenItem.water(stack);
            }
        }
        return missing;
    }

    /** Les gorgees que portent les gourdes du joueur. */
    private static int carriedSips(ServerPlayer player) {
        int carried = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof CanteenItem) {
                carried += CanteenItem.water(stack);
            }
        }
        return carried;
    }

    @Override
    public MenuView view(ServerPlayer player) {
        long balance = Economy.balance(player);
        float water = Hydration.water(player);
        int shown = (int) Math.ceil(water);
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("SOLDE", MoneyFormat.exact(balance) + " $", 0xFFFFFFFF),
                new MenuView.Stat("TON EAU", shown + " / " + (int) Hydration.MAX,
                        shown > Hydration.THIRSTY * 2 ? GREEN : shown > Hydration.THIRSTY ? GOLD : RED),
                new MenuView.Stat("GOURDES", carriedSips(player) + " gorgees", WATER));

        boolean thirsty = Hydration.thirsty(player);
        Card drink = Card.of(net.minecraft.world.item.alchemy.PotionContents.createItemStack(
                net.minecraft.world.item.Items.POTION, net.minecraft.world.item.alchemy.Potions.WATER),
                "Boire a la citerne",
                List.of(MoneyFormat.exact(DRINK_PRICE) + " $", "Ta reserve d'eau, pleine"))
                .accent(WATER);
        drink = !thirsty ? drink.state(MenuView.DISABLED).tooltip(List.of("Tu n'as pas soif."))
                : balance >= DRINK_PRICE ? drink.action("drink|boire") : drink.state(MenuView.DISABLED);
        int missing = missingSips(player);
        long refillCost = missing * SIP_PRICE;
        Card refill = Card.of(CanteenItem.filled(ArrakisContent.CANTEEN.get()), "Remplir mes gourdes",
                List.of(MoneyFormat.exact(SIP_PRICE) + " $ la gorgee", missing > 0
                        ? missing + " gorgees : " + MoneyFormat.exact(refillCost) + " $"
                        : "Tes gourdes sont pleines (ou tu n'en as pas)"))
                .accent(WATER).badge(missing > 0 ? "x" + missing : "");
        refill = missing > 0 && balance >= refillCost ? refill.action("refill|remplir les gourdes")
                : refill.state(MenuView.DISABLED);

        List<Card> gear = new ArrayList<>();
        gear.add(buy(CanteenItem.filled(ArrakisContent.CANTEEN.get()), "Gourde", CANTEEN_PRICE, balance,
                "buy_canteen", WATER, List.of("Huit gorgees, livree pleine", "Bois-la quand tu as soif"),
                List.of("Remplis-la a un piege a vent, ou ici.", "Une gorgee : un cinquieme de ta reserve d'eau.")));
        gear.add(buy(new ItemStack(ArrakisContent.STILLSUIT_SUIT.get()), "Distille complet", STILLSUIT_PRICE, balance,
                "buy_stillsuit", SUIT_ACCENT, List.of("Les quatre pieces", "La soif va pres de sept fois moins vite"),
                List.of("Il garde l'eau du corps et la rend a boire.",
                        "Masque 15 %, combinaison 35 %, jambieres 25 %, bottes 10 %.")));
        gear.add(buy(new ItemStack(ArrakisContent.STILLSUIT_MASK.get()), "Masque de distille", MASK_PRICE, balance,
                "buy_mask", SUIT_ACCENT, List.of("Garde 15 % de ton eau", "Le souffle ne se perd plus"), null));
        gear.add(buy(new ItemStack(ArrakisContent.STILLSUIT_SUIT.get()), "Combinaison de distille", SUIT_PRICE,
                balance, "buy_suit", SUIT_ACCENT, List.of("Garde 35 % de ton eau", "La sueur est bue a nouveau"), null));
        gear.add(buy(new ItemStack(ArrakisContent.STILLSUIT_LEGGINGS.get()), "Jambieres de distille", LEGGINGS_PRICE,
                balance, "buy_leggings", SUIT_ACCENT, List.of("Garde 25 % de ton eau", "Les pompes des cuisses"),
                null));
        gear.add(buy(new ItemStack(ArrakisContent.STILLSUIT_BOOTS.get()), "Bottes de distille", BOOTS_PRICE, balance,
                "buy_boots", SUIT_ACCENT, List.of("Garde 10 % de ton eau", "Chaque pas pompe l'eau"), null));
        gear.add(buy(new ItemStack(ArrakisContent.WINDTRAP_ITEM.get()), "Piege a vent", WINDTRAP_PRICE, balance,
                "buy_windtrap", 0xFFB0B4BA, List.of("Seize gorgees de citerne", "Il tire l'eau de l'air, la nuit surtout"),
                List.of("Pose-le a ciel ouvert : couvert, il ne recolte rien.",
                        "Une gorgee toutes les 20 s la nuit, toutes les 60 s le jour.",
                        "Une gourde ou une bouteille vide s'y remplit.")));

        gear.add(buy(new ItemStack(SietchContent.PARACOMPASS.get()), "Paracompas", PARACOMPASS_PRICE, balance,
                "buy_paracompass", 0xFFE8E0C8, List.of("Il montre le sietch le plus proche", "Les sietchs echangent l'eau"),
                List.of("Huit sietchs sont creuses au pied des falaises de l'erg.",
                        "Leurs habitants echangent l'eau et l'equipement contre du melange.")));

        int minutes = Hydration.minutesLeft(player);
        Card rules = Card.of(ItemStack.EMPTY, "L'eau d'Arrakis", List.of(
                "Hors d'Arrakeen, le desert boit ton eau.",
                "- En plein soleil, ta reserve dure huit minutes ; a l'ombre et la nuit, plus du double",
                "- En courant, deux fois moins ; dans la cabine d'une moissonneuse, tu es a l'ombre",
                "- Le distille garde l'eau du corps : chaque piece compte",
                "- Sous 6, tu ralentis ; sous 3, tu faiblis ; a sec, tu perds de la vie",
                "- Sur Arrakis, l'eau versee s'evapore : seuls la gourde et le piege a vent la gardent",
                minutes < 0 ? "! Ici, a Arrakeen, la ville a ses citernes : tu n'as pas soif."
                        : "! Au rythme d'ici, ta reserve tiendrait " + minutes + " min."));
        return new MenuView(id(), "Le marchand d'eau", "Arrakeen — l'eau vaut plus que l'epice", stats, List.of(
                new MenuView.Section("BOIRE", "resources", MenuView.GRID, List.of(drink, refill)),
                new MenuView.Section("MATERIEL", "resources", MenuView.GRID, gear),
                new MenuView.Section("ARRAKIS", "", MenuView.TEXT, List.of(rules))), false);
    }

    private static Card buy(ItemStack icon, String title, long price, long balance, String key, int accent,
                            List<String> lines, List<String> tooltip) {
        List<String> body = new ArrayList<>();
        body.add(MoneyFormat.exact(price) + " $");
        body.addAll(lines);
        Card card = Card.of(icon, title, body).accent(accent).badge("ACHAT");
        if (tooltip != null) {
            card = card.tooltip(tooltip);
        }
        return balance >= price ? card.action(key + "|acheter") : card.state(MenuView.DISABLED)
                .tooltip(List.of("Il te manque " + MoneyFormat.exact(price - balance) + " $."));
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if (!atSeller(player)) {
            UiNet.close(player);
            return;
        }
        switch (key) {
            case "drink" -> {
                if (!Hydration.thirsty(player) || !Economy.tryDebit(player, DRINK_PRICE)) {
                    failure(player);
                    return;
                }
                Hydration.drink(player, Hydration.MAX);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_DRINK,
                        SoundSource.PLAYERS, 1.0f, 1.0f);
                report(player, null, "Tu as bu tout ton saoul.");
            }
            case "refill" -> {
                int missing = missingSips(player);
                if (missing <= 0 || !Economy.tryDebit(player, missing * SIP_PRICE)) {
                    failure(player);
                    return;
                }
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    ItemStack stack = player.getInventory().getItem(slot);
                    if (stack.getItem() instanceof CanteenItem) {
                        CanteenItem.setWater(stack, CanteenItem.capacity(stack));
                    }
                }
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL,
                        SoundSource.PLAYERS, 1.0f, 0.9f);
                report(player, null, missing + " gorgees versees dans tes gourdes.");
            }
            case "buy_canteen" -> sell(player, CANTEEN_PRICE, "Gourde pleine livree.",
                    CanteenItem.filled(ArrakisContent.CANTEEN.get()));
            case "buy_stillsuit" -> sell(player, STILLSUIT_PRICE, "Distille complet livre : porte les quatre pieces.",
                    stack(ArrakisContent.STILLSUIT_MASK.get()), stack(ArrakisContent.STILLSUIT_SUIT.get()),
                    stack(ArrakisContent.STILLSUIT_LEGGINGS.get()), stack(ArrakisContent.STILLSUIT_BOOTS.get()));
            case "buy_mask" -> sell(player, MASK_PRICE, "Masque de distille livre.",
                    stack(ArrakisContent.STILLSUIT_MASK.get()));
            case "buy_suit" -> sell(player, SUIT_PRICE, "Combinaison de distille livree.",
                    stack(ArrakisContent.STILLSUIT_SUIT.get()));
            case "buy_leggings" -> sell(player, LEGGINGS_PRICE, "Jambieres de distille livrees.",
                    stack(ArrakisContent.STILLSUIT_LEGGINGS.get()));
            case "buy_boots" -> sell(player, BOOTS_PRICE, "Bottes de distille livrees.",
                    stack(ArrakisContent.STILLSUIT_BOOTS.get()));
            case "buy_paracompass" -> sell(player, PARACOMPASS_PRICE,
                    "Paracompas livre : sur Arrakis, son aiguille montre le sietch le plus proche.",
                    stack(SietchContent.PARACOMPASS.get()));
            case "buy_windtrap" -> sell(player, WINDTRAP_PRICE,
                    "Piege a vent livre : pose-le a ciel ouvert, il se remplit la nuit.",
                    stack(ArrakisContent.WINDTRAP_ITEM.get()));
            default -> {
            }
        }
    }

    private static ItemStack stack(Item item) {
        return new ItemStack(item);
    }

    private void sell(ServerPlayer player, long price, String done, ItemStack... goods) {
        if (!Economy.tryDebit(player, price)) {
            report(player, "Il te manque " + MoneyFormat.exact(price - Economy.balance(player)) + " $.", null);
            return;
        }
        for (ItemStack good : goods) {
            if (!player.getInventory().add(good) || !good.isEmpty()) {
                player.drop(good, false);
            }
        }
        report(player, null, done);
    }
}

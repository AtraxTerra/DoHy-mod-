package fr.atraxterra.dohy.ui.menu;

import fr.atraxterra.dohy.boost.Autosell;
import fr.atraxterra.dohy.boost.BoostData;
import fr.atraxterra.dohy.boost.Boosters;
import fr.atraxterra.dohy.boost.Crates;
import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.economy.Bank;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.palamod.box.BoxKind;
import fr.atraxterra.dohy.palamod.box.CrateKeys;
import fr.atraxterra.dohy.palamod.box.PalaBoxes;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * L'onglet Boutique du commerce : boosters, cles de box et leurs coffres a poser,
 * potions d'XP, assurance et autosell. Tout s'y paie en liquide.
 */
public final class BoutiqueController extends MenuController {

    public static final BoutiqueController INSTANCE = new BoutiqueController();


    private BoutiqueController() {
        super("boutique");
    }

    @Override
    public MenuView view(ServerPlayer player) {
        double active = Boosters.active(player);
        List<MenuView.Stat> stats = new ArrayList<>();
        stats.add(new MenuView.Stat("TON ARGENT", MoneyFormat.exact(Economy.balance(player)) + " $", 0xFFFFFFFF));
        if (active > 1.0d) {
            stats.add(new MenuView.Stat("BOOSTER", "x" + (long) active, 0xFF22E558));
        }

        List<Card> boosters = new ArrayList<>();
        for (Boosters.Tier tier : Boosters.TIERS) {
            boolean busy = active > 1.0d;
            long missing = tier.price() - Economy.balance(player);
            Card card = Card.of(new ItemStack(boosterIcon(tier)), "Booster " + tier.key(), List.of(
                            MoneyFormat.exact(tier.price()) + " $", "Ventes " + tier.key() + " pendant "
                                    + Boosters.DURATION_MINUTES + " min"))
                    .accent(0xFFFAC400).badge(tier.key())
                    .tooltip(List.of("Multiplie l'argent des ventes",
                            "Le montant booste ne fait pas baisser les prix",
                            "Prepare un stock avant de le declencher",
                            busy ? "Un booster est deja actif : ils ne se cumulent pas"
                                    : missing > 0 ? "Il te manque " + MoneyFormat.exact(missing) : ""));
            if (busy || missing > 0) {
                card = card.state(MenuView.DISABLED);
            } else {
                card = card.action("booster:" + tier.key() + "|declencher");
            }
            boosters.add(card);
        }

        // Les loot boxes : une carte par box, de la moins chere a la plus chere. La cle
        // s'achete ici et s'ouvre a la roulette, ou sur le coffre pose de la box.
        List<Card> crates = new ArrayList<>();
        for (String tier : Crates.ordered()) {
            Crates.Crate crate = Crates.config().crates().get(tier);
            BoxKind kind = BoxKind.byId(tier);
            int keys = CrateKeys.count(player, tier);
            ItemStack icon = kind != null ? new ItemStack(PalaBoxes.key(kind))
                    : new ItemStack(keys > 0 ? Items.ENDER_CHEST : Items.TRAPPED_CHEST);
            crates.add(Card.of(icon, Crates.title(tier), List.of("Cle : " + MoneyFormat.exact(crate.price()) + " $",
                            keys > 0 ? keys + (keys > 1 ? " cles · clic droit : ouvrir" : " cle · clic droit : ouvrir")
                                    : "Clic : acheter une cle"))
                    .accent(0xFFEF3826).badge(keys > 0 ? keys + " CLE" : "")
                    .action("key:" + tier + "|acheter une cle")
                    .right(keys > 0 ? "crate:" + tier + "|ouvrir a la roulette" : "")
                    .tooltip(crateTooltip(crate)));
        }

        // Les coffres a poser : l'autre facon d'ouvrir, avec l'animation de Paladium.
        List<Card> boxes = new ArrayList<>();
        for (String tier : Crates.ordered()) {
            BoxKind kind = BoxKind.byId(tier);
            if (kind == null) {
                continue;
            }
            ItemStack icon = new ItemStack(PalaBoxes.item(kind));
            long missing = PalaBoxes.PRICE - Economy.balance(player);
            Card card = Card.of(icon, MenuView.text(icon.getHoverName()), List.of(
                            MoneyFormat.exact(PalaBoxes.PRICE) + " $", "Deco animee, s'ouvre avec une cle"))
                    .accent(0xFF9B59E8)
                    .tooltip(List.of("Un coffre anime a poser chez soi",
                            "Clic droit avec une " + Crates.keyName(tier) + " : il s'ouvre",
                            "Accroupi, main vide : achete une cle",
                            "Meme butin qu'a la roulette, l'ouverture en plus",
                            missing > 0 ? "Il te manque " + MoneyFormat.exact(missing) : ""));
            boxes.add(missing > 0 ? card.state(MenuView.DISABLED)
                    : card.action("box:" + kind.getSerializedName() + "|acheter"));
        }

        // Les potions d'XP de metier : la Double de PalaMod, et la Triple.
        List<Card> potions = new ArrayList<>();
        for (fr.atraxterra.dohy.palamod.boost.PalaXpPotions.Offer offer
                : fr.atraxterra.dohy.palamod.boost.PalaXpPotions.SHOP) {
            String duration = fr.atraxterra.dohy.palamod.boost.PalaXpPotions.label(offer.minutes());
            long missing = offer.price() - Economy.balance(player);
            Card card = Card.of(fr.atraxterra.dohy.palamod.boost.PalaXpPotions.stack(offer.multiplier(),
                                    offer.minutes()),
                            (offer.multiplier() == 3 ? "Triple XP " : "Double XP ") + duration, List.of(
                            MoneyFormat.exact(offer.price()) + " $", "XP de metier x" + offer.multiplier() + " pendant "
                                    + duration))
                    .accent(offer.multiplier() == 3 ? 0xFFFAC400 : 0xFF22E558).badge("x" + offer.multiplier())
                    .tooltip(List.of("Toute l'XP de metier x" + offer.multiplier() + " pendant " + duration
                                    + " de jeu",
                            "Bue pendant l'effet : la duree s'ajoute",
                            "Le x3 passe avant le x2 ; ni le lait ni la mort ne l'enlevent",
                            missing > 0 ? "Il te manque " + MoneyFormat.exact(missing) : ""));
            potions.add(missing > 0 ? card.state(MenuView.DISABLED)
                    : card.action("xp:" + offer.multiplier() + ":" + offer.minutes() + "|acheter"));
        }

        int charges = Economy.of(player).insurance();
        BoostData data = Boosters.data(player);
        List<Card> services = new ArrayList<>();
        services.add(Card.of(new ItemStack(Items.TOTEM_OF_UNDYING), "Assurance", List.of(
                        MoneyFormat.exact(Bank.insurancePrice()) + " $ la charge", charges > 0
                                ? charges + (charges > 1 ? " charges : tu perds moitie moins a la mort"
                                : " charge : tu perds moitie moins a la mort") : "Tu perds moitie moins a la mort"))
                .accent(0xFF2F5BEA).badge(String.valueOf(charges)).action("insurance|acheter une charge")
                .tooltip(List.of("Divise par deux la perte a la mort", "Une charge est consommee par mort")));
        if (!data.autosellUnlocked()) {
            long missing = Autosell.UNLOCK_PRICE - Economy.balance(player);
            services.add(Card.of(new ItemStack(Items.HOPPER_MINECART), "Autosell", List.of(
                            MoneyFormat.exact(Autosell.UNLOCK_PRICE) + " $",
                            "Vend un coffre toutes les " + Autosell.INTERVAL_MINUTES + " min"))
                    .accent(0xFF2F5BEA).action("autosell|debloquer")
                    .state(missing > 0 ? MenuView.DISABLED : MenuView.NORMAL)
                    .tooltip(List.of("Ne charge jamais le chunk : un coffre non charge est saute",
                            missing > 0 ? "Il te manque " + MoneyFormat.exact(missing) : "")));
        } else {
            BlockPos linked = data.autosellPos();
            long since = Math.max(0L, ContractApi.data(player).playtimeMinutes() - data.autosellLast());
            services.add(Card.of(new ItemStack(Items.HOPPER), "Autosell", List.of(
                            linked == null ? "Aucun coffre lie"
                                    : "Coffre " + linked.getX() + " " + linked.getY() + " " + linked.getZ(),
                            "Dernier passage il y a " + since + " min"))
                    .state(MenuView.DONE).tooltip(List.of("Place-toi devant un coffre et fais /autosell link")));
        }
        // La banque et les ameliorations ont leur entree dans la barre laterale : elles
        // ne s'achetent pas ici, elles n'y ont plus de carte.
        return new MenuView(id(), fr.atraxterra.dohy.shop.Commerce.TITLE,
                fr.atraxterra.dohy.shop.Commerce.Tab.BOUTIQUE.subtitle(), stats, List.of(
                new MenuView.Section("BOOSTERS : TES VENTES RAPPORTENT PLUS", "upgrade", MenuView.GRID, boosters),
                new MenuView.Section("CLES DE BOX", "resources", MenuView.GRID, crates),
                new MenuView.Section("POTIONS D'XP DE METIER", "upgrade", MenuView.GRID, potions),
                new MenuView.Section("SERVICES", "craft", MenuView.GRID, services),
                new MenuView.Section("COFFRES A POSER", "armor", MenuView.GRID, boxes)), false)
                .withTabs(fr.atraxterra.dohy.shop.Commerce.tabs(player, fr.atraxterra.dohy.shop.Commerce.Tab.BOUTIQUE));
    }

    public static Item boosterIcon(Boosters.Tier tier) {
        return switch (tier.key()) {
            case "x2" -> Items.SUGAR;
            case "x3" -> Items.GLOWSTONE_DUST;
            default -> Items.BLAZE_POWDER;
        };
    }

/**
     * La bulle d'une box : la chance de chaque classe de lot, puis les gros
     * lots (les plus rares d'abord).
     */
    private static List<String> crateTooltip(Crates.Crate crate) {
        List<String> tooltip = new ArrayList<>();
        List<Integer> odds = crate.odds();
        tooltip.add("Chances :");
        for (int rarity = 0; rarity < odds.size(); rarity++) {
            tooltip.add("  " + Crates.rarityName(rarity) + " : " + percent(odds.get(rarity)));
        }
        tooltip.add("Gros lots :");
        crate.available().stream()
                .sorted((left, right) -> left.rarityIndex() != right.rarityIndex()
                        ? Integer.compare(right.rarityIndex(), left.rarityIndex())
                        : Integer.compare(left.weight(), right.weight()))
                .limit(4)
                .forEach(entry -> tooltip.add(MenuView.text(Component.literal("  ").append(Crates.label(entry)))));
        return tooltip;
    }

    /** 68 %, 1 %, 0,6 % : une decimale sous 1 %. */
    private static String percent(int permille) {
        if (permille <= 0) {
            return "0 %";
        }
        if (permille >= 10) {
            return Math.round(permille / 10.0f) + " %";
        }
        return "0," + permille + " %";
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if (key.startsWith("booster:")) {
            Boosters.Tier tier = Boosters.tier(key.substring(8));
            if (tier != null) {
                report(player, Boosters.buy(player, tier), "Booster x" + (long) tier.multiplier() + " actif pour "
                        + Boosters.DURATION_MINUTES + " min de jeu.");
            }
        } else if (key.startsWith("key:")) {
            String tier = key.substring(4);
            report(player, Crates.buyKey(player, tier), "Une " + Crates.keyName(tier) + " achetee.");
        } else if (key.startsWith("box:")) {
            for (BoxKind kind : BoxKind.values()) {
                if (kind.getSerializedName().equals(key.substring(4))) {
                    ItemStack box = new ItemStack(PalaBoxes.item(kind));
                    // Le nom se traduit chez le client : il reste un composant.
                    Component name = box.getHoverName();
                    if (!Economy.tryDebit(player, PalaBoxes.PRICE)) {
                        report(player, "Il te faut " + MoneyFormat.exact(PalaBoxes.PRICE) + " en liquide.", "");
                    } else {
                        if (!player.getInventory().add(box)) {
                            player.drop(box, false);
                        }
                        success(player);
                        player.sendSystemMessage(Component.empty().append(name)
                                .append(" achetee : pose-la, puis clic droit avec une cle.")
                                .withStyle(ChatFormatting.GREEN));
                    }
                }
            }
        } else if (key.startsWith("xp:")) {
            buyPotion(player, key.substring(3));
        } else if (key.startsWith("crate:")) {
            // Reussie, l'ouverture remplace la boutique par la roulette.
            String refusal = Crates.open(player, key.substring(6));
            if (refusal != null) {
                failure(player);
                player.sendSystemMessage(Component.literal(refusal).withStyle(ChatFormatting.RED));
            }
        } else {
            switch (key) {
                case "insurance" -> {
                    if (Bank.buyInsurance(player)) {
                        success(player);
                        player.sendSystemMessage(Component.literal("Assurance achetee — "
                                + Bank.insuranceCharges(player) + " charge(s).").withStyle(ChatFormatting.GREEN));
                    } else {
                        failure(player);
                        player.sendSystemMessage(Component.literal("Il te faut "
                                + MoneyFormat.exact(Bank.insurancePrice()) + " en liquide.")
                                .withStyle(ChatFormatting.RED));
                    }
                }
                case "autosell" -> report(player, Autosell.unlock(player),
                        "Autosell debloque — /autosell link devant un coffre.");
                case "bank" -> BankController.INSTANCE.open(player);
                case "upgrades" -> UpgradesController.INSTANCE.open(player);
                default -> {
                }
            }
        }
    }

    /** Une potion d'XP : {@code 2:60} pour la Double d'une heure. */
    private static void buyPotion(ServerPlayer player, String spec) {
        String[] parts = spec.split(":");
        fr.atraxterra.dohy.palamod.boost.PalaXpPotions.Offer offer = null;
        try {
            offer = fr.atraxterra.dohy.palamod.boost.PalaXpPotions.offer(Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]));
        } catch (RuntimeException malformed) {
            // un clic forge par le client : rien a vendre
        }
        if (offer == null) {
            failure(player);
            return;
        }
        if (!Economy.tryDebit(player, offer.price())) {
            report(player, "Il te faut " + MoneyFormat.exact(offer.price()) + " en liquide.", "");
            return;
        }
        ItemStack potion = fr.atraxterra.dohy.palamod.boost.PalaXpPotions.stack(offer.multiplier(), offer.minutes());
        Component name = potion.getHoverName();
        if (!player.getInventory().add(potion)) {
            player.drop(potion, false);
        }
        success(player);
        player.sendSystemMessage(Component.empty().append(name).append(" achetee : bois-la quand tu veux.")
                .withStyle(ChatFormatting.GREEN));
    }

    /**
     * Les boutons de la roulette : rouvrir (une cle en poche, sinon une cle
     * achetee d'abord) ou revenir a la boutique.
     */
    public static void handleCrate(ServerPlayer player, UiNet.Action action) {
        switch (action.verb()) {
            case "again" -> {
                String tier = action.argument();
                String refusal = null;
                if (CrateKeys.count(player, tier) <= 0) {
                    refusal = Crates.buyKey(player, tier);
                }
                if (refusal == null) {
                    refusal = Crates.open(player, tier);
                }
                if (refusal != null) {
                    failure(player);
                    player.sendSystemMessage(Component.literal(refusal).withStyle(ChatFormatting.RED));
                }
            }
            case "back" -> INSTANCE.open(player);
            default -> {
            }
        }
    }
}

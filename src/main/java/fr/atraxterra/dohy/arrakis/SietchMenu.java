package fr.atraxterra.dohy.arrakis;

import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ce qu'on echange dans un sietch, contre du melange et de l'eau ; et la confiance des
 * sietchs, qui monte avec ce qu'on leur offre et ouvre ce qu'ils gardent pour les leurs.
 *
 * <p>Un seul menu, quatre comptoirs : le porteur d'eau (boire, remplir ses gourdes, offrir de
 * l'eau), l'ancienne (la confiance, les offrandes d'epice, le cafe, les autres sietchs),
 * l'artisan (paracompas, marteleur, krys) et l'equipeur (la grande gourde, le distille de
 * sietch). On parle a celui qu'on a aborde : il faut rester pres de lui.
 */
public final class SietchMenu extends MenuController {

    public static final SietchMenu INSTANCE = new SietchMenu();

    /** Ami du sietch : la grande gourde, le distille de sietch, l'eau a moitie prix. */
    public static final int FRIEND = 100;
    /** Frere d'eau : le krys. */
    public static final int BROTHER = 400;
    /** Ce que rapporte une gorgee offerte ; une mesure de melange rapporte un point. */
    public static final int TRUST_PER_SIP = 3;

    static final int DRINK_MELANGE = 2;
    static final int COFFEE_MELANGE = 4;
    static final int PARACOMPASS_MELANGE = 12;
    static final int THUMPER_MELANGE = 20;
    static final int KRYS_MELANGE = 48;
    static final int KRYS_SIPS = 4;
    static final int CANTEEN_MELANGE = 32;
    static final int MASK_MELANGE = 24;
    static final int SUIT_MELANGE = 48;
    static final int LEGGINGS_MELANGE = 36;
    static final int BOOTS_MELANGE = 16;

    private static final double REACH = 6.0d;
    private static final int WATER = 0xFF4FB8FF;
    private static final int SPICE = 0xFFE07B39;
    private static final int TRUST = 0xFFC88BE8;
    private static final int SUIT = 0xFFC8A878;
    private static final int BONE = 0xFFE8E0C8;
    private static final int GREEN = 0xFF22E558;
    private static final int GOLD = 0xFFFFB52E;
    private static final int RED = 0xFFE5484D;

    /** A qui parle chaque joueur : l'habitant aborde. */
    private static final Map<UUID, Integer> TALKING = new HashMap<>();

    private SietchMenu() {
        super("arrakis_sietch");
    }

    /** Ouvre le comptoir de cet habitant. */
    public static void open(ServerPlayer player, SietchDweller dweller) {
        TALKING.put(player.getUUID(), dweller.getId());
        INSTANCE.open(player);
    }

    private static SietchDweller dweller(ServerPlayer player) {
        Integer id = TALKING.get(player.getUUID());
        if (id == null) {
            return null;
        }
        Entity entity = player.level().getEntity(id);
        if (entity instanceof SietchDweller dweller && dweller.isAlive()
                && player.distanceToSqr(dweller) <= REACH * REACH) {
            return dweller;
        }
        return null;
    }

    // -- La confiance ----------------------------------------------------------------------------

    public static int trust(ServerPlayer player) {
        return player.getData(DoHyArrakis.SIETCH_TRUST.get());
    }

    static void addTrust(ServerPlayer player, int amount) {
        int before = trust(player);
        int after = before + amount;
        player.setData(DoHyArrakis.SIETCH_TRUST.get(), after);
        if (before < FRIEND && after >= FRIEND) {
            player.sendSystemMessage(Component.literal("Les sietchs te tiennent pour un ami : l'equipeur te montrera "
                    + "le distille de sietch et la grande gourde.").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (before < BROTHER && after >= BROTHER) {
            player.sendSystemMessage(Component.literal("Tu es un frere d'eau : l'artisan taillera pour toi un krys.")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    public static String rank(int trust) {
        return trust >= BROTHER ? "Frere d'eau" : trust >= FRIEND ? "Ami du sietch" : "Etranger";
    }

    // -- L'inventaire ----------------------------------------------------------------------------

    private static int count(ServerPlayer player, Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static boolean take(ServerPlayer player, Item item, int amount) {
        if (count(player, item) < amount) {
            return false;
        }
        int left = amount;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && left > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                int used = Math.min(left, stack.getCount());
                stack.shrink(used);
                left -= used;
            }
        }
        return true;
    }

    private static int melange(ServerPlayer player) {
        return count(player, ArrakisContent.MELANGE.get());
    }

    /** Les gorgees que portent les gourdes du joueur. */
    private static int sips(ServerPlayer player) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof CanteenItem) {
                total += CanteenItem.water(stack);
            }
        }
        return total;
    }

    /** Les gorgees qui manquent aux gourdes du joueur. */
    private static int missing(ServerPlayer player) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof CanteenItem) {
                total += CanteenItem.capacity(stack) - CanteenItem.water(stack);
            }
        }
        return total;
    }

    /** Verse {@code amount} gorgees hors des gourdes ; faux s'il n'y en a pas assez. */
    private static boolean pour(ServerPlayer player, int amount) {
        if (sips(player) < amount) {
            return false;
        }
        int left = amount;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && left > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof CanteenItem) {
                int used = Math.min(left, CanteenItem.water(stack));
                CanteenItem.setWater(stack, CanteenItem.water(stack) - used);
                left -= used;
            }
        }
        return true;
    }

    /** Echange une piece portee ou rangee contre une autre, a la meme place (un masque porte reste porte). */
    private static boolean swap(ServerPlayer player, Item from, ItemStack to) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(from)) {
                player.getInventory().setItem(slot, to);
                return true;
            }
        }
        return false;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack) || !stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    // -- Les vues --------------------------------------------------------------------------------

    @Override
    public MenuView view(ServerPlayer player) {
        SietchDweller dweller = dweller(player);
        SietchDweller.Role role = dweller == null ? SietchDweller.Role.ELDER : dweller.role();
        int trust = trust(player);
        int spice = melange(player);
        List<MenuView.Stat> stats = new ArrayList<>();
        stats.add(new MenuView.Stat("MELANGE", Integer.toString(spice), SPICE));
        stats.add(new MenuView.Stat("GOURDES", sips(player) + " gorgees", WATER));
        stats.add(new MenuView.Stat("CONFIANCE", trust + " — " + rank(trust),
                trust >= BROTHER ? GREEN : trust >= FRIEND ? GOLD : TRUST));
        Sietches.Site site = Sietches.at(player);
        String where = site == null ? "Un sietch d'Arrakis" : "Sietch " + site.name();
        String who = dweller == null ? "Personne ne te parle" : dweller.getDisplayName().getString();
        List<MenuView.Section> sections = switch (role) {
            case WATER -> water(player, trust, spice);
            case ARTISAN -> artisan(player, trust, spice);
            case OUTFITTER -> outfitter(player, trust, spice);
            default -> elder(player, trust, spice);
        };
        return new MenuView(id(), who, where + " — on y echange l'eau et l'epice", stats, sections, false);
    }

    private static Card trade(ItemStack icon, String title, String key, int accent, List<String> lines,
                              String refusal, List<String> tooltip) {
        Card card = Card.of(icon, title, lines).accent(accent);
        if (tooltip != null) {
            card = card.tooltip(tooltip);
        }
        if (refusal != null) {
            return card.state(MenuView.DISABLED).tooltip(List.of(refusal));
        }
        return card.action(key);
    }

    private static String lacking(int have, int need, String what) {
        return have >= need ? null : "Il te manque " + (need - have) + " " + what + ".";
    }

    private List<MenuView.Section> water(ServerPlayer player, int trust, int spice) {
        boolean friend = trust >= FRIEND;
        boolean thirsty = Hydration.thirsty(player);
        Card drink = trade(net.minecraft.world.item.alchemy.PotionContents.createItemStack(
                net.minecraft.world.item.Items.POTION, net.minecraft.world.item.alchemy.Potions.WATER),
                "Boire a la citerne", "drink|boire", WATER,
                List.of(friend ? "Offert aux amis du sietch" : DRINK_MELANGE + " melange", "Ta reserve d'eau, pleine"),
                !thirsty ? "Tu n'as pas soif." : friend ? null : lacking(spice, DRINK_MELANGE, "melange"), null);
        int missing = missing(player);
        int cost = refillCost(missing, friend);
        Card refill = trade(CanteenItem.filled(ArrakisContent.CANTEEN.get()), "Remplir mes gourdes", "refill|remplir",
                WATER, List.of(friend ? "1 melange les 2 gorgees" : "1 melange la gorgee",
                        missing > 0 ? missing + " gorgees : " + cost + " melange" : "Tes gourdes sont pleines"),
                missing <= 0 ? "Gourdes pleines." : lacking(spice, cost, "melange"), null);
        int sips = sips(player);
        Card offer = trade(CanteenItem.filled(ArrakisContent.CANTEEN.get()), "Offrir 4 gorgees au sietch",
                "offer4|offrir", TRUST, List.of("+" + 4 * TRUST_PER_SIP + " de confiance", "L'eau versee a la citerne"),
                lacking(sips, 4, "gorgees dans tes gourdes"), null);
        Card offerAll = trade(CanteenItem.filled(ArrakisContent.CANTEEN.get()), "Offrir toute mon eau",
                "offer_all|offrir tout", TRUST, List.of(sips + " gorgees : +" + sips * TRUST_PER_SIP + " de confiance",
                        ""), sips <= 0 ? "Tes gourdes sont vides." : null, null);
        return List.of(new MenuView.Section("LA CITERNE", "resources", MenuView.GRID, List.of(drink, refill)),
                new MenuView.Section("OFFRANDES", "resources", MenuView.GRID, List.of(offer, offerAll)));
    }

    private static int refillCost(int missing, boolean friend) {
        return friend ? (missing + 1) / 2 : missing;
    }

    private List<MenuView.Section> elder(ServerPlayer player, int trust, int spice) {
        Card offer16 = trade(new ItemStack(ArrakisContent.MELANGE.get(), 16), "Offrir 16 melange", "spice16|offrir",
                TRUST, List.of("+16 de confiance"), lacking(spice, 16, "melange"), null);
        Card offer64 = trade(new ItemStack(ArrakisContent.MELANGE.get(), 64), "Offrir 64 melange", "spice64|offrir",
                TRUST, List.of("+64 de confiance"), lacking(spice, 64, "melange"), null);
        Card coffee = trade(new ItemStack(SietchContent.SPICE_COFFEE.get()), "Cafe d'epice", "coffee|acheter", SPICE,
                List.of(COFFEE_MELANGE + " melange", "Une gorgee, et l'epice aiguise les sens"),
                lacking(spice, COFFEE_MELANGE, "melange"), null);
        Card listen = Card.of(new ItemStack(SietchContent.PARACOMPASS.get()), "Ecouter l'ancienne",
                List.of("Ou sont les autres sietchs ?", "Gratuit")).accent(TRUST).action("listen|ecouter");
        int next = trust >= BROTHER ? 0 : trust >= FRIEND ? BROTHER : FRIEND;
        Card standing = Card.of(ItemStack.EMPTY, "Ta place parmi nous", List.of(
                "Confiance : " + trust + " — " + rank(trust),
                next > 0 ? "Prochain rang a " + next : "Plus haut rang",
                "- Melange : +1 · gorgee : +" + TRUST_PER_SIP,
                "- Ami (" + FRIEND + ") : distille, grande gourde, eau a moitie prix",
                "- Frere d'eau (" + BROTHER + ") : le krys"))
                .progress(trust, next > 0 ? next : trust);
        return List.of(new MenuView.Section("OFFRANDES", "resources", MenuView.GRID, List.of(offer16, offer64)),
                new MenuView.Section("L'ANCIENNE", "resources", MenuView.GRID, List.of(listen, coffee)),
                new MenuView.Section("CONFIANCE", "", MenuView.TEXT, List.of(standing)));
    }

    private List<MenuView.Section> artisan(ServerPlayer player, int trust, int spice) {
        Card compass = trade(new ItemStack(SietchContent.PARACOMPASS.get()), "Paracompas", "paracompass|acheter",
                BONE, List.of(PARACOMPASS_MELANGE + " melange", "Son aiguille montre le sietch le plus proche"),
                lacking(spice, PARACOMPASS_MELANGE, "melange"), null);
        Card thumper = trade(new ItemStack(ArrakisContent.THUMPER_ITEM.get()), "Marteleur", "thumper|acheter", BONE,
                List.of(THUMPER_MELANGE + " melange", "Plante dans le sable, il appelle les vers"),
                lacking(spice, THUMPER_MELANGE, "melange"), null);
        int teeth = count(player, ArrakisContent.WORM_TOOTH.get());
        String krysRefusal = trust < BROTHER ? "Reserve aux freres d'eau (" + BROTHER + " de confiance)."
                : teeth < 1 ? "Apporte une dent de ver." : lacking(spice, KRYS_MELANGE, "melange") != null
                ? lacking(spice, KRYS_MELANGE, "melange") : lacking(sips(player), KRYS_SIPS, "gorgees");
        Card krys = trade(new ItemStack(SietchContent.KRYS.get()), "Krys", "krys|faire tailler", BONE,
                List.of("1 dent de ver, " + KRYS_MELANGE + " melange, " + KRYS_SIPS + " gorgees",
                        "Une lame laiteuse, vive et sans pareille"), krysRefusal,
                List.of("", "8 de degats, plus vive qu'une epee."));
        return List.of(new MenuView.Section("L'ATELIER", "resources", MenuView.GRID, List.of(compass, thumper, krys)));
    }

    private List<MenuView.Section> outfitter(ServerPlayer player, int trust, int spice) {
        boolean friend = trust >= FRIEND;
        String gate = friend ? null : "Reserve aux amis du sietch (" + FRIEND + " de confiance).";
        List<Card> cards = new ArrayList<>();
        cards.add(upgrade(player, gate, spice, ArrakisContent.CANTEEN.get(), CanteenItem.filled(SietchContent.CANTEEN.get()),
                "Gourde de sietch", "canteen", CANTEEN_MELANGE, "ta gourde", WATER,
                "Seize gorgees au lieu de huit"));
        cards.add(upgrade(player, gate, spice, ArrakisContent.STILLSUIT_MASK.get(),
                new ItemStack(SietchContent.SIETCH_MASK.get()), "Masque de sietch", "mask", MASK_MELANGE,
                "ton masque de distille", SUIT, "Garde 17 % de ton eau (15 % avant)"));
        cards.add(upgrade(player, gate, spice, ArrakisContent.STILLSUIT_SUIT.get(),
                new ItemStack(SietchContent.SIETCH_SUIT.get()), "Combinaison de sietch", "suit", SUIT_MELANGE,
                "ta combinaison de distille", SUIT, "Garde 39 % de ton eau (35 % avant)"));
        cards.add(upgrade(player, gate, spice, ArrakisContent.STILLSUIT_LEGGINGS.get(),
                new ItemStack(SietchContent.SIETCH_LEGGINGS.get()), "Jambieres de sietch", "leggings",
                LEGGINGS_MELANGE, "tes jambieres de distille", SUIT, "Gardent 26 % de ton eau (25 % avant)"));
        cards.add(upgrade(player, gate, spice, ArrakisContent.STILLSUIT_BOOTS.get(),
                new ItemStack(SietchContent.SIETCH_BOOTS.get()), "Bottes de sietch", "boots", BOOTS_MELANGE,
                "tes bottes de distille", SUIT, "Gardent 11 % de ton eau (10 % avant)"));
        return List.of(new MenuView.Section("L'EQUIPEUR", "resources", MenuView.GRID, cards));
    }

    private static Card upgrade(ServerPlayer player, String gate, int spice, Item from, ItemStack icon, String title,
                                String key, int price, String what, int accent, String effect) {
        String refusal = gate != null ? gate : count(player, from) < 1 ? "Apporte " + what + "."
                : lacking(spice, price, "melange");
        return trade(icon, title, key + "|faire coudre", accent, List.of(what.substring(0, 1).toUpperCase(
                java.util.Locale.ROOT) + what.substring(1) + " + " + price + " melange", effect), refusal, null);
    }

    // -- Les actions -----------------------------------------------------------------------------

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        SietchDweller dweller = dweller(player);
        if (dweller == null) {
            UiNet.close(player);
            return;
        }
        int trust = trust(player);
        boolean friend = trust >= FRIEND;
        switch (key) {
            case "drink" -> {
                if (!Hydration.thirsty(player) || (!friend && !take(player, ArrakisContent.MELANGE.get(), DRINK_MELANGE))) {
                    failure(player);
                    return;
                }
                Hydration.drink(player, Hydration.MAX);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_DRINK,
                        SoundSource.PLAYERS, 1.0f, 1.0f);
                report(player, null, "Tu as bu a la citerne du sietch.");
            }
            case "refill" -> {
                int missing = missing(player);
                if (missing <= 0 || !take(player, ArrakisContent.MELANGE.get(), refillCost(missing, friend))) {
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
            case "offer4" -> offerWater(player, 4);
            case "offer_all" -> offerWater(player, sips(player));
            case "spice16" -> offerSpice(player, 16);
            case "spice64" -> offerSpice(player, 64);
            case "listen" -> listen(player);
            case "coffee" -> sell(player, COFFEE_MELANGE, 0, new ItemStack(SietchContent.SPICE_COFFEE.get()),
                    "Un cafe d'epice, brulant.");
            case "paracompass" -> sell(player, PARACOMPASS_MELANGE, 0, new ItemStack(SietchContent.PARACOMPASS.get()),
                    "Paracompas livre.");
            case "thumper" -> sell(player, THUMPER_MELANGE, 0, new ItemStack(ArrakisContent.THUMPER_ITEM.get()),
                    "Marteleur livre. Plante-le loin de toi.");
            case "krys" -> {
                if (trust < BROTHER || count(player, ArrakisContent.WORM_TOOTH.get()) < 1
                        || melange(player) < KRYS_MELANGE || sips(player) < KRYS_SIPS) {
                    failure(player);
                    return;
                }
                take(player, ArrakisContent.WORM_TOOTH.get(), 1);
                take(player, ArrakisContent.MELANGE.get(), KRYS_MELANGE);
                pour(player, KRYS_SIPS);
                give(player, new ItemStack(SietchContent.KRYS.get()));
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SMITHING_TABLE_USE,
                        SoundSource.PLAYERS, 1.0f, 1.0f);
                report(player, null, "L'artisan te tend un krys, encore humide de l'eau du sietch.");
            }
            case "canteen" -> sew(player, friend, ArrakisContent.CANTEEN.get(), CanteenItem.filled(SietchContent.CANTEEN.get()),
                    CANTEEN_MELANGE, "Gourde de sietch : seize gorgees, livree pleine.");
            case "mask" -> sew(player, friend, ArrakisContent.STILLSUIT_MASK.get(),
                    new ItemStack(SietchContent.SIETCH_MASK.get()), MASK_MELANGE, "Masque de sietch cousu.");
            case "suit" -> sew(player, friend, ArrakisContent.STILLSUIT_SUIT.get(),
                    new ItemStack(SietchContent.SIETCH_SUIT.get()), SUIT_MELANGE, "Combinaison de sietch cousue.");
            case "leggings" -> sew(player, friend, ArrakisContent.STILLSUIT_LEGGINGS.get(),
                    new ItemStack(SietchContent.SIETCH_LEGGINGS.get()), LEGGINGS_MELANGE, "Jambieres de sietch cousues.");
            case "boots" -> sew(player, friend, ArrakisContent.STILLSUIT_BOOTS.get(),
                    new ItemStack(SietchContent.SIETCH_BOOTS.get()), BOOTS_MELANGE, "Bottes de sietch cousues.");
            default -> {
            }
        }
    }

    private static void offerWater(ServerPlayer player, int amount) {
        if (amount <= 0 || !pour(player, amount)) {
            failure(player);
            return;
        }
        addTrust(player, amount * TRUST_PER_SIP);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUCKET_EMPTY,
                SoundSource.PLAYERS, 0.8f, 1.1f);
        report(player, null, amount + " gorgees versees a la citerne. Confiance : " + trust(player) + ".");
    }

    private static void offerSpice(ServerPlayer player, int amount) {
        if (!take(player, ArrakisContent.MELANGE.get(), amount)) {
            failure(player);
            return;
        }
        addTrust(player, amount);
        report(player, null, "L'ancienne accepte ton offrande. Confiance : " + trust(player) + ".");
    }

    /** L'ancienne dit ou trouver le sietch le plus proche, apres celui-ci. */
    private static void listen(ServerPlayer player) {
        Sietches.Site here = Sietches.at(player);
        Sietches.Site best = null;
        double bestD = Double.MAX_VALUE;
        for (Sietches.Site site : Sietches.sites()) {
            if (here != null && site.index() == here.index()) {
                continue;
            }
            double d = site.distanceSq(player.getX(), player.getZ());
            if (d < bestD) {
                bestD = d;
                best = site;
            }
        }
        if (best == null) {
            report(player, "« Nous sommes seuls dans ce desert. »", null);
            return;
        }
        BlockPos face = best.face();
        int distance = (int) Math.round(Math.sqrt(bestD));
        success(player);
        player.sendSystemMessage(Component.literal("L'ancienne : « Le sietch " + best.name() + " est a "
                + distance + " blocs " + toward(player, face) + ". Son entree regarde l'erg, au pied d'une falaise ("
                + face.getX() + ", " + face.getZ() + "). »").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private static final String[] TOWARD = {"au nord", "au nord-est", "a l'est", "au sud-est", "au sud",
            "au sud-ouest", "a l'ouest", "au nord-ouest"};

    /** Dans quelle direction se trouve ce point, vu du joueur. */
    private static String toward(ServerPlayer player, BlockPos target) {
        double dx = target.getX() + 0.5d - player.getX();
        double dz = target.getZ() + 0.5d - player.getZ();
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        return TOWARD[Math.floorMod((int) Math.round(degrees / 45.0d), 8)];
    }

    private static void sell(ServerPlayer player, int spice, int sips, ItemStack good, String done) {
        if (melange(player) < spice || sips(player) < sips) {
            failure(player);
            return;
        }
        take(player, ArrakisContent.MELANGE.get(), spice);
        if (sips > 0) {
            pour(player, sips);
        }
        give(player, good);
        report(player, null, done);
    }

    private static void sew(ServerPlayer player, boolean friend, Item from, ItemStack to, int spice, String done) {
        if (!friend || count(player, from) < 1 || melange(player) < spice) {
            failure(player);
            return;
        }
        take(player, ArrakisContent.MELANGE.get(), spice);
        swap(player, from, to);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                SoundSource.PLAYERS, 1.0f, 1.0f);
        report(player, null, done);
    }
}

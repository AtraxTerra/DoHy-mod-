package fr.atraxterra.dohy.underground;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.enterprise.Enterprises;
import fr.atraxterra.dohy.faction.enterprise.Line;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Ce que valent les objets des affaires illegales dans les stocks des entreprises (lot 24c) :
 * une portion de produit vaut une unite, un sachet quatre, un colis trente-deux ; une feuille
 * ou un bidon une unite de matiere premiere ; une caisse scellee une caisse. Une planque
 * securisee verse ce qu'elle contient aux stocks ; les comptes en ressortent des sachets, des
 * colis et des caisses.
 */
public final class Goods {

    /** Un sachet : quatre portions. */
    public static final int BAG = 4;
    /** Un colis : huit sachets. */
    public static final int PARCEL = BAG * 8;

    /** Ce qu'un objet vaut dans les stocks. */
    public record Good(String key, int units) {
    }

    private Goods() {
    }

    public static Optional<Good> of(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        Item item = stack.getItem();
        for (Line line : Line.values()) {
            if (item == raw(line)) {
                return Optional.of(new Good(line.rawKey(), 1));
            }
            if (item == product(line)) {
                return Optional.of(new Good(line.productKey(), 1));
            }
            if (item == bag(line)) {
                return Optional.of(new Good(line.productKey(), BAG));
            }
            if (item == parcel(line)) {
                return Optional.of(new Good(line.productKey(), PARCEL));
            }
        }
        if (item == DoHyUnderground.SYNTHETIC_CRYSTALS.get()) {
            return Optional.of(new Good(Line.SYNTHESE.productKey(), 2));
        }
        if (item == DoHyUnderground.CRYSTAL_BAG.get()) {
            return Optional.of(new Good(Line.SYNTHESE.productKey(), 2 * BAG));
        }
        if (item == DoHyUnderground.SEALED_CRATE.get()) {
            return Optional.of(new Good(Enterprises.CRATES, 1));
        }
        return Optional.empty();
    }

    /** La graine d'une drogue qui pousse (la synthese n'en a pas : celle du cannabis par defaut). */
    public static Item seed(Line line) {
        return switch (line) {
            case COCAINE -> DoHyUnderground.COCA_SEEDS.get();
            case HEROINE -> DoHyUnderground.POPPY_SEEDS.get();
            default -> DoHyUnderground.CANNABIS_SEEDS.get();
        };
    }

    /** La matiere premiere d'une drogue, en objet. */
    public static Item raw(Line line) {
        return switch (line) {
            case CANNABIS -> DoHyUnderground.CANNABIS_LEAF.get();
            case COCAINE -> DoHyUnderground.COCA_LEAF.get();
            case HEROINE -> DoHyUnderground.POPPY_POD.get();
            case SYNTHESE -> DoHyUnderground.PRECURSOR_CAN.get();
        };
    }

    /** Une portion de produit fini. */
    public static Item product(Line line) {
        return switch (line) {
            case CANNABIS -> DoHyUnderground.DRIED_HERB.get();
            case COCAINE -> DoHyUnderground.COCAINE_POWDER.get();
            case HEROINE -> DoHyUnderground.HEROIN.get();
            case SYNTHESE -> DoHyUnderground.SYNTHETIC_PILLS.get();
        };
    }

    public static Item bag(Line line) {
        return switch (line) {
            case CANNABIS -> DoHyUnderground.HERB_BAG.get();
            case COCAINE -> DoHyUnderground.POWDER_BAG.get();
            case HEROINE -> DoHyUnderground.HEROIN_BAG.get();
            case SYNTHESE -> DoHyUnderground.PILL_BAG.get();
        };
    }

    public static Item parcel(Line line) {
        return switch (line) {
            case CANNABIS -> DoHyUnderground.HERB_PARCEL.get();
            case COCAINE -> DoHyUnderground.POWDER_PARCEL.get();
            case HEROINE -> DoHyUnderground.HEROIN_PARCEL.get();
            case SYNTHESE -> DoHyUnderground.SYNTHETIC_PARCEL.get();
        };
    }

    /**
     * Verse aux stocks ce que contient une planque, autant qu'il y a de place : les objets
     * verses en sortent, le reste y demeure. Rend la phrase a dire, vide si rien n'a bouge.
     */
    public static String deposit(ServerPlayer player, Container container) {
        if (!FactionApi.has(player)) {
            return "";
        }
        Map<String, Long> moved = new LinkedHashMap<>();
        boolean full = false;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            Optional<Good> good = of(stack);
            if (good.isEmpty()) {
                continue;
            }
            int per = good.get().units();
            long room = Enterprises.room(player, good.get().key());
            int count = (int) Math.min(stack.getCount(), room / per);
            if (count < stack.getCount()) {
                full = true;
            }
            if (count <= 0) {
                continue;
            }
            long units = Enterprises.deposit(player, good.get().key(), (long) count * per);
            int used = (int) (units / per);
            if (used > 0) {
                stack.shrink(used);
                container.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
                moved.merge(good.get().key(), (long) used * per, Long::sum);
            }
        }
        container.setChanged();
        if (moved.isEmpty()) {
            return full ? "Les stocks sont pleins : rien n'a ete verse." : "";
        }
        StringBuilder text = new StringBuilder("Verse aux stocks : ");
        boolean first = true;
        for (Map.Entry<String, Long> entry : moved.entrySet()) {
            if (!first) {
                text.append(", ");
            }
            first = false;
            text.append(MoneyFormat.exact(entry.getValue())).append(' ').append(label(entry.getKey()));
        }
        text.append(full ? " (stocks pleins pour le reste)." : ".");
        Enterprises.log(player, text.toString());
        return text.toString();
    }

    /** « unites de poudre », « caisses ». */
    public static String label(String key) {
        if (key.equals(Enterprises.CRATES)) {
            return "caisse(s)";
        }
        int colon = key.indexOf(':');
        Line line = colon < 0 ? null : Line.of(key.substring(colon + 1));
        if (line == null) {
            return key;
        }
        return key.startsWith("raw:") ? line.rawLabel().toLowerCase() : "unite(s) de " + line.productLabel()
                .toLowerCase();
    }

    /**
     * Sort des stocks un objet : un colis, un sachet ou une caisse. La raison du refus, ou
     * {@code null}.
     */
    public static String withdraw(ServerPlayer player, Item item) {
        ItemStack stack = new ItemStack(item);
        Optional<Good> good = of(stack);
        if (good.isEmpty()) {
            return "Cet objet ne sort pas des stocks";
        }
        if (!Enterprises.withdraw(player, good.get().key(), good.get().units())) {
            return "Il faut " + good.get().units() + " " + label(good.get().key()) + " en stock";
        }
        if (!player.getInventory().add(stack) || !stack.isEmpty()) {
            player.drop(stack, false);
        }
        player.sendSystemMessage(Component.literal("Sorti des stocks : ").withStyle(ChatFormatting.GRAY)
                .append(new ItemStack(item).getHoverName()).append(Component.literal(".")), true);
        return null;
    }
}

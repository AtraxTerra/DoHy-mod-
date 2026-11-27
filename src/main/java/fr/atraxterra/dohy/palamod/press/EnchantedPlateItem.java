package fr.atraxterra.dohy.palamod.press;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.function.Consumer;

/**
 * La plaque d'impression enchantee ({@code ItemEnchantedPlate}).
 *
 * <p>Elle porte les enchantements du livre grave dans
 * {@code stored_enchantments}, le composant des livres enchantes : le jeu les
 * affiche dans l'infobulle, et la presse les recopie tels quels sur chaque livre.
 * L'usure est celle de l'original ({@code setItemDamage(damage + 1)} a chaque
 * livre, la plaque detruite au-dela de 2) : trois impressions, montrees par la
 * barre de durabilite.
 */
public class EnchantedPlateItem extends Item {

    /** Trois livres par plaque. */
    public static final int PRINTS = 3;

    public EnchantedPlateItem(Properties properties) {
        super(properties);
    }

    /** Une plaque gravee des enchantements de ce livre. */
    public static ItemStack engrave(ItemStack book) {
        ItemStack plate = new ItemStack(PalaPress.ENCHANTED_PLATE.get());
        plate.set(DataComponents.STORED_ENCHANTMENTS,
                book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY));
        return plate;
    }

    /** Le livre que tire cette plaque. */
    public static ItemStack print(ItemStack plate) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.set(DataComponents.STORED_ENCHANTMENTS,
                plate.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY));
        return book;
    }

    /** Une plaque qui grave encore quelque chose. */
    public static boolean engraved(ItemStack plate) {
        return plate.is(PalaPress.ENCHANTED_PLATE.get())
                && !plate.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty();
    }

    /** Impressions qui restent a cette plaque. */
    public static int printsLeft(ItemStack plate) {
        return Math.max(0, plate.getMaxDamage() - plate.getDamageValue());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.palamod.enchanted_plate.uses", printsLeft(stack), PRINTS)
                .withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable("tooltip.palamod.enchanted_plate.use").withStyle(ChatFormatting.GRAY));
    }
}

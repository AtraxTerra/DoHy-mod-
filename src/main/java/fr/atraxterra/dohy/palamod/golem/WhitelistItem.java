package fr.atraxterra.dohy.palamod.golem;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.List;
import java.util.function.Consumer;

/**
 * {@code ItemWhitelist} : des noms de joueurs que le golem epargne. On la remplit
 * par {@code /whitelistgolem add|remove <joueur>}, on la presente au golem (clic
 * droit, proprietaire) pour qu'il la retienne.
 */
public class WhitelistItem extends Item {

    public static final int MAX_NAMES = 60;

    public WhitelistItem(Properties properties) {
        super(properties);
    }

    public static List<String> names(ItemStack stack) {
        return stack.getOrDefault(PalaGolem.WHITELIST_NAMES.get(), List.of());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        List<String> names = names(stack);
        tooltip.accept(Component.translatable("item.palamod.whitelist.players").withStyle(ChatFormatting.GRAY));
        if (names.isEmpty()) {
            tooltip.accept(Component.translatable("item.palamod.whitelist.none").withStyle(ChatFormatting.DARK_GRAY));
        }
        names.forEach(name -> tooltip.accept(Component.literal("- " + name).withStyle(ChatFormatting.GREEN)));
    }
}

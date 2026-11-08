package fr.atraxterra.dohy.palamod.mount;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Une corne de Dancarok ({@code ItemHorn(hornType, miningSize, durability)}).
 *
 * <p>Posee dans la case de corne de la monture, elle change sa texture et lui
 * permet de casser une zone devant elle (voir {@link HornMining}). L'usure est
 * celle d'un outil : un coup, une utilisation. Le nom porte la taille,
 * « Corne en Amethyste 3x3 », et l'infobulle le compte d'utilisations ; ni
 * l'un ni l'autre n'avait de cle de langue dans l'original.
 */
public class MountHornItem extends Item {

    /** {@code hornType} 1 a 3 ; 0 veut dire « pas de corne » sur l'entite. */
    public enum Tier {
        AMETHYST(1, 3, 3999, ChatFormatting.LIGHT_PURPLE),
        TITANE(2, 4, 5999, ChatFormatting.GRAY),
        PALADIUM(3, 5, 9999, ChatFormatting.GOLD);

        private final int id;
        private final int size;
        private final int durability;
        private final ChatFormatting color;

        Tier(int id, int size, int durability, ChatFormatting color) {
            this.id = id;
            this.size = size;
            this.durability = durability;
            this.color = color;
        }

        public int id() {
            return id;
        }

        /** {@code miningSize} : un rayon, pas un cote (voir {@link HornMining}). */
        public int size() {
            return size;
        }

        public int durability() {
            return durability;
        }
    }

    private final Tier tier;

    public MountHornItem(Tier tier, Properties properties) {
        super(properties.durability(tier.durability()));
        this.tier = tier;
    }

    public Tier tier() {
        return tier;
    }

    /** Le palier de la corne, ou {@code null} si ce n'en est pas une. */
    public static Tier tierOf(ItemStack stack) {
        return stack.getItem() instanceof MountHornItem horn ? horn.tier : null;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId()).withStyle(tier.color)
                .append(Component.literal(" " + tier.size + "x" + tier.size)
                        .withStyle(tier.color, ChatFormatting.BOLD));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.palamod.horn.uses", stack.getDamageValue(), stack.getMaxDamage())
                .withStyle(ChatFormatting.GREEN));
    }
}

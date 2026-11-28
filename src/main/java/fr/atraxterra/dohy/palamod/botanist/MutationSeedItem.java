package fr.atraxterra.dohy.palamod.botanist;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Une graine de mutation ({@code ItemBotanistMutationSeed}) : elle se plante dans
 * un pot du botaniste, pas en terre. L'infobulle dit son climat, ses temps de
 * pousse et ses recoltes (« Temps de pousse », « Recoltable au clic droit a
 * chaque floraison » dans JobsReborn), sa rarete et le niveau de Fermier pour la
 * planter.
 */
public class MutationSeedItem extends Item {

    private final Mutation mutation;

    public MutationSeedItem(Properties properties, Mutation mutation) {
        super(properties);
        this.mutation = mutation;
    }

    public Mutation mutation() {
        return mutation;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        Rarity rarity = mutation.rarity();
        tooltip.accept(Component.translatable("tooltip.palamod.mutation_seed.rarity",
                Component.translatable("tooltip.palamod.botanist.rarity." + rarity.key())
                        .withColor(rarity.color() & 0xFFFFFF))
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.palamod.mutation_seed.climate",
                Component.translatable("tooltip.palamod.botanist.climate." + mutation.climate().key())
                        .withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.palamod.mutation_seed.growth",
                Component.literal(MineralSeedItem.duration(mutation.budTicks())).withStyle(ChatFormatting.WHITE),
                Component.literal(MineralSeedItem.duration(mutation.bloomTicks())).withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.palamod.mutation_seed.harvests",
                Component.literal(String.valueOf(Mutation.HARVESTS)).withStyle(ChatFormatting.WHITE),
                Component.literal(MineralSeedItem.duration(mutation.rebloomTicks())).withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.palamod.mutation_seed.level",
                Component.literal(String.valueOf(mutation.level())).withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.palamod.mutation_seed.pot")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
